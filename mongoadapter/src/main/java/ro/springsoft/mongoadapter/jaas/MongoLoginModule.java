/*
 * Copyright (C) 2013 Miroslav MARKO <miromarko@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package ro.springsoft.mongoadapter.jaas;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.sun.org.apache.xerces.internal.impl.dv.util.Base64;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.login.LoginException;
import javax.security.auth.spi.LoginModule;

/**
 * @author Miroslav MARKO <miromarko@gmail.com>
 */
public class MongoLoginModule implements LoginModule {

    MongoClient mongoClient = null;
    private static final Logger LOG = Logger.getLogger(MongoLoginModule.class.getName());
    //initialization state
    private CallbackHandler callbackHandler;
    private Subject subject;
    private Map sharedState;
    private Map options;
    //config
    private String mongoDB = "";
    private String mongoCollection = "";
    private String userCol = "";
    private String passCol = "";
    private String roleCol = "";
    private String digest = "";
    private boolean debug = false;
    //user principals
    private MongoUserPrincipal userPrincipal = null;
    private MongoPasswrodPrincipial passwdPrincipal = null;
    private List<MongoRolePrincipal> rolePrincipals = null;
    //authenticated status
    private boolean authSucceeded = false;
    private boolean commitSucceeded = false;
    //user credentials
    private String username = null;
    private String password = null;

    @Override
    public void initialize(Subject subject, CallbackHandler callbackHandler, Map<String, ?> sharedState, Map<String, ?> options) {
        this.callbackHandler = callbackHandler;
        this.subject = subject;
        this.options = options;
        this.sharedState = sharedState;

        //MongoClient JNDI lookup
        try {
            mongoClient = (MongoClient) (new InitialContext()).lookup("java:comp/env/MongoClient");
        } catch (NamingException ex) {
            Logger.getLogger(MongoLoginModule.class.getName()).log(Level.SEVERE, null, ex);
        }
        //options from jaas.config
        mongoDB = (String) options.get("mongoDB");
        mongoCollection = (String) options.get("mongoCollection");
        userCol = (String) options.get("userCol");
        passCol = (String) options.get("passCol");
        roleCol = (String) options.get("roleCol");
        digest = (String) options.get("digest");
        debug = "true".equalsIgnoreCase((String) options.get("debug"));
    }

    /**
     * Checks the login credentials must throw LoginException when login fails
     */
    public boolean login() throws LoginException {
        if (callbackHandler == null) {
            throw new LoginException("Error: no CallbackHandler available to handle authentication from the user");
        }
        Callback[] callbacks = new Callback[2];
        callbacks[0] = new NameCallback("login");
        callbacks[1] = new PasswordCallback("password", false);

        try {
            callbackHandler.handle(callbacks);
            username = ((NameCallback) callbacks[0]).getName();
            password = String.valueOf(((PasswordCallback) callbacks[1]).getPassword());

            if (username == null || password == null) {
                LOG.severe("Callback handler does not return login data properly (null values)");
                throw new LoginException("Callback handler does not return login data properly (null values)");
            }

            if (isValidUser()) {
                authSucceeded = true;
                return true;
            }
        } catch (Exception e) {
            throw new LoginException(e.getMessage());
        }
        return false;
    }

    /**
     * Clears subject from principal and credentials
     */
    public boolean logout() throws LoginException {
        try {
            subject.getPrincipals().remove(userPrincipal);
            subject.getPrincipals().remove(passwdPrincipal);
            if (rolePrincipals != null) {
                for (MongoRolePrincipal rolePrincipal : rolePrincipals) {
                    subject.getPrincipals().remove(rolePrincipal);
                }
            }

            username = null;
            password = null;
            userPrincipal = null;

            authSucceeded = commitSucceeded;
            return true;
        } catch (Exception e) {
            throw new LoginException(e.getMessage());
        }
    }

    @Override
    public boolean abort() throws LoginException {
        if (!authSucceeded) {
            return false;
        } else if (authSucceeded && !commitSucceeded) {
            authSucceeded = false;
            username = null;
            password = null;

        } else {
            logout();
        }
        return true;
    }

    /**
     * Set principial if authentication succeeds
     */
    public boolean commit() throws LoginException {
        if (!authSucceeded) {
            return false;
        } else {
            try {
                userPrincipal = new MongoUserPrincipal(username);
                if (!subject.getPrincipals().contains(userPrincipal)) {
                    subject.getPrincipals().add(userPrincipal);
                }
                passwdPrincipal = new MongoPasswrodPrincipial(password);
                if (!subject.getPrincipals().contains(passwdPrincipal)) {
                    subject.getPrincipals().add(passwdPrincipal);
                }

                //populate subject with roles
                List<String> roles;
                if ((roles = getRoles()) != null) {
                    for (String role : roles) {
                        MongoRolePrincipal rolePrincipal = new MongoRolePrincipal(role);
                        rolePrincipals.add(rolePrincipal);
                        if (!subject.getPrincipals().contains(rolePrincipal)) {
                            subject.getPrincipals().add(rolePrincipal);
                        }
                    }
                }
                commitSucceeded = true;
                return true;
            } catch (Exception e) {
                throw new LoginException(e.getMessage());
            }
        }
    }

    private String getPasswordHash(String digest, String password) {
        String passwordHash = password;
        if (digest != null && !digest.equals("")) {
            try {
                MessageDigest md = MessageDigest.getInstance(digest);

                byte[] passwordByte = password.getBytes(Charset.defaultCharset());
                byte[] hash = md.digest(passwordByte);

                passwordHash = Base64.encode(hash);
            } catch (NoSuchAlgorithmException noSuchAlgorithmException) {
                LOG.severe(noSuchAlgorithmException.getMessage());
            }
        }
        return passwordHash;
    }

    //get roles from MongoDB
    private List<String> getRoles() {

        DBCollection collection = mongoClient.getDB(mongoDB).getCollection(mongoCollection);
        DBCursor cursor = collection.find(new BasicDBObject(userCol, username), new BasicDBObject(roleCol, 1).append("_id", 0));
        if (cursor.hasNext()) {
            DBObject mongoRoles = cursor.next();
            return (List<String>) mongoRoles.get(roleCol);
        }
        return null;
    }

    private boolean isValidUser() {
        DBCollection collection = mongoClient.getDB(mongoDB).getCollection(mongoCollection);
        DBCursor cursor = collection.find(new BasicDBObject(userCol, username), new BasicDBObject(passCol, 1).append("_id", 0));
        if (cursor.hasNext()) {
            DBObject mongoPswdObject = cursor.next();
            String mongoPswd = (String) mongoPswdObject.get(passCol);
            String userEncodedPswd = getPasswordHash(digest, password);
            if (mongoPswd.equals(userEncodedPswd)) {
                return true;
            }
        }
        return false;
    }
}