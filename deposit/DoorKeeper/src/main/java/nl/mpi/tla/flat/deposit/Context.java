/*
 * Copyright (C) 2015 menzowi
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
package nl.mpi.tla.flat.deposit;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmValue;
import nl.mpi.tla.flat.deposit.context.ImportPropertiesInterface;
import nl.mpi.tla.flat.deposit.util.Saxon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author menzowi
 */
public class Context {
    
    private static final Logger logger = LoggerFactory.getLogger(Context.class.getName());
    
    protected Logger actionLogger = logger;
    
    protected Map<String,XdmValue> props = new LinkedHashMap<>();
    
    protected SIP sip = null;
        
    public Context(XdmNode spec)  throws DepositException {
        loadProperties(spec);
    }
    
    // Logger
    
    public void setLogger(Logger logger) {
        this.actionLogger = logger;
    }
    
    public Logger getLogger() {
        return this.actionLogger;
    }
    
    // Properties
    
    private void loadProperties(XdmNode spec) throws DepositException {
        try {
            importProperties(spec);
            loadParameters(props,Saxon.xpath(spec, "/flow/config/property",props),"property");
        } catch(SaxonApiException e) {
            throw new DepositException(e);
        }
    }
    
    private void importProperties(XdmNode spec) throws SaxonApiException, DepositException {
        for (XdmItem imp: Saxon.xpath(spec, "/flow/config/import",props)) {
            String prefix = Saxon.xpath2string(imp,"@prefix");
            String clazz = Saxon.xpath2string(imp,"@class");
            try {
                Class<ImportPropertiesInterface> face = (Class<ImportPropertiesInterface>) Class.forName(clazz);
                ImportPropertiesInterface importer = face.newInstance();
                importer.importProperties(prefix,props);
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
                this.logger.error(" couldn't load property importer["+clazz+"]["+prefix+"]! "+e.getMessage());
                throw new DepositException(e);
            }
        }
    }
    
    public Map<String,XdmValue> getProperties() {
        return props;
    }

    public boolean hasProperty(String name) {
        return props.containsKey(name);
    }
    
    public XdmValue getProperty(String name) {
        return props.get(name);
    }
    
    // SIP
    
    public boolean hasSIP() {
        return (this.sip != null);
    }
    
    public void setSIP(SIP sip) throws DepositException {
        if (this.sip!=null)
            throw new DepositException("SIP is already specified!");
        this.sip = sip;
    }
    
    public SIP getSIP() throws DepositException {
        if (this.sip==null)
            throw new DepositException("SIP is not specified!");
        return this.sip;
    }
    
    // Utilities: general method to load properties or parameters
    
    public void loadParameters(Map<String,XdmValue> map,XdmValue params,String type) throws SaxonApiException, DepositException {
        for (XdmItem param : params) {
            String name = Saxon.xpath2string(param,"@name");
            if (Saxon.hasAttribute(param,"when")) {
                if (!Saxon.xpath2boolean(param,Saxon.xpath2string(param,"@when"),props)) {
                    continue;
                }
            }
            if (Saxon.xpath2boolean(param,"../"+type+"[@name='"+name+"']/@uniq='true'")) {
                if (props.containsKey(name)) {
                    this.logger.error(type+"["+name+"] should be unique!");
                    continue;
                }
            }
            if (Saxon.hasAttribute(param,"value")) {
                String avt = Saxon.avt(Saxon.xpath2string(param,"@value"),param,props);
                XdmValue val = new XdmAtomicValue(avt);
                if (map.containsKey(name))
                    map.put(name,map.get(name).append(val));
                else
                    map.put(name,val);
            } else if (Saxon.hasAttribute(param,"xpath")) {
                try {
                    XdmValue val = Saxon.xpath(param,Saxon.xpath2string(param,"@xpath"),props);
                    if (map.containsKey(name))
                        map.put(name,map.get(name).append(val));
                    else
                        map.put(name,val);
                } catch(SaxonApiException e) {
                    this.logger.error(type+"["+name+"] xpath["+Saxon.xpath2string(param,"@xpath")+"] couldn't be evaluated! "+e.getMessage());
                    throw new DepositException(e);
                }
            }
            this.logger.debug(type+"["+name+"]["+map.get(name)+"]");
        }
        boolean closure = true;
        int c = 0;
        do {
            c++;
            closure = true;
            for (String name:map.keySet()) {
                XdmValue vals = map.get(name);
                XdmValue nvals = null;
                for(XdmItem val:vals) {
                    String avt = Saxon.avt(val.toString(),val,props);
                    if (!val.toString().equals(avt))
                        closure = false;
                    if (nvals==null)
                        nvals = new XdmAtomicValue(avt);
                    else
                        nvals.append(new XdmAtomicValue(avt));
                }
                map.put(name,nvals);
                this.logger.debug("closure["+c+"] "+type+"["+name+"]["+map.get(name)+"]");
            }
        } while(!closure);
    }
    
}
