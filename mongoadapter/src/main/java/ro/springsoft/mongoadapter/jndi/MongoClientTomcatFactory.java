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
package ro.springsoft.mongoadapter.jndi;

import com.mongodb.MongoClient;
import com.mongodb.ServerAddress;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.naming.Context;
import javax.naming.Name;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;

/**
 *
 * @author Miroslav MARKO <miromarko@gmail.com>
 * JNDI factory for obtaining MongoClient(singleton like)
 */
public class MongoClientTomcatFactory implements ObjectFactory {

    private static final Logger LOG = Logger.getLogger(MongoClientTomcatFactory.class.getName());
    private MongoClient mongoClient = null;

    public Object getObjectInstance(Object obj, Name name, Context nameCtx, Hashtable<?, ?> environment) throws Exception {
        if (mongoClient == null) {
            Reference ref = (Reference) obj;
            Enumeration<RefAddr> props = ref.getAll();
            String[] hosts = null;
            String[] ports = null;
            while (props.hasMoreElements()) {
                RefAddr addr = (RefAddr) props.nextElement();
                String propName = addr.getType();
                String propValue = (String) addr.getContent();
                if (propName.equals("hosts")) {
                    // get space delimited hosts
                    hosts = propValue.split("\\ ");
                } else if (propName.equals("ports")) {
                    // get space delimited ports
                    ports = propValue.split("\\ ");
                }
            }
            mongoClient = new MongoClient(getMongoSeeds(hosts, ports));
        }
        return mongoClient;
    }

    private List<ServerAddress> getMongoSeeds(String[] hosts, String[] ports) {
        List<ServerAddress> seeds = new LinkedList<ServerAddress>();
        if (hosts == null || ports == null || hosts.length != ports.length) {

            LOG.severe("Different nr. of hosts and ports or parsed hosts|ports = null !!!");

        } else {
            for (int i = 0; i < hosts.length; i++) {
                try {
                    seeds.add(new ServerAddress(hosts[i], Integer.parseInt(ports[i])));
                } catch (UnknownHostException ex) {
                    LOG.log(Level.SEVERE, "Cannot parse mongo port: {0}", ports[i]);
                }
            }
        }
        return seeds;
    }
}
