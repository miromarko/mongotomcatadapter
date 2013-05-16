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

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.logging.Logger;
import javax.naming.Context;
import javax.naming.Name;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;
import org.infinispan.manager.DefaultCacheManager;

/**
 *
 * @author Miroslav MARKO <miromarko@gmail.com>
 */
public class InfinispanTomcatFactory implements ObjectFactory {

    private static final Logger LOG = Logger.getLogger(InfinispanTomcatFactory.class.getName());

    public Object getObjectInstance(Object obj, Name name, Context nameCtx, Hashtable<?, ?> environment) throws Exception {
        Reference ref = (Reference) obj;
        Enumeration<RefAddr> props = ref.getAll();
        String cacheConfigFile = null;
        while (props.hasMoreElements()) {
            RefAddr addr = (RefAddr) props.nextElement();
            String propName = addr.getType();
            String propValue = (String) addr.getContent();
            if (propName.equals("config-file")) {
                cacheConfigFile = propValue;
                break;
            }
        }
        DefaultCacheManager cacheManager = null;
        
        if(cacheConfigFile != null){
            cacheManager = new DefaultCacheManager(cacheConfigFile);
        }else{
            cacheManager = new DefaultCacheManager();
        }
        
      
        return cacheManager;
    }
}
