/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.params;

import java.io.Serializable;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;

import org.apache.http.params.HttpParams;

/**
 * This class represents a collection of HTTP protocol parameters.
 * Protocol parameters may be linked together to form a hierarchy.
 * If a particular parameter value has not been explicitly defined
 * in the collection itself, its value will be drawn from the parent 
 * collection of parameters.
 * 
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 * 
 * @version $Revision$
 */
public class BasicHttpParams extends AbstractHttpParams
    implements HttpLinkedParams, Serializable {

    static final long serialVersionUID = 4571099216197814749L;

    /**
     * The optional set of default values to defer to.
     */
    protected HttpParams defaults;

    /** Map of HTTP parameters that this collection contains. */
    private HashMap parameters;

    /**
     * Creates a new collection of parameters with the given parent. 
     * The collection will defer to its parent for a default value 
     * if a particular parameter is not explicitly set in the collection
     * itself.
     * 
     * @param defaults the parent collection to defer to, if a parameter
     * is not explictly set in the collection itself.
     */
    public BasicHttpParams(final HttpParams defaults) {
        super();
        setDefaults(defaults); // perform ancestor check
    }

    
    public BasicHttpParams() {
        this(null);
    }

    /**
     * Obtains default parameters, if set.
     *
     * @return  the defaults, or <code>null</code>
     */
    public synchronized HttpParams getDefaults() {
        return this.defaults;
    }
    
    /**
     * Provides default parameters.
     *
     * @param params    the new defaults, or <code>null</code> to unset
     */
    public synchronized void setDefaults(final HttpParams params) {
        this.defaults = params;
    }

    public synchronized Object getParameter(final String name) {
        // See if the parameter has been explicitly defined
        Object param = null;
        if (this.parameters != null) {
            param = this.parameters.get(name);
        }    
        if (param != null) {
            // If so, return
            return param;
        } else {
            // If not, see if defaults are available
            if (this.defaults != null) {
                // Return default parameter value
                return this.defaults.getParameter(name);
            } else {
                // Otherwise, return null
                return null;
            }
        }
    }

    public synchronized HttpParams setParameter(final String name, final Object value) {
        if (this.parameters == null) {
            this.parameters = new HashMap();
        }
        this.parameters.put(name, value);
        return this;
    }
    
    /**
     * Assigns the value to all the parameter with the given names
     * 
     * @param names array of parameter name
     * @param value parameter value
     */ 
    public synchronized void setParameters(final String[] names, final Object value) {
        for (int i = 0; i < names.length; i++) {
            setParameter(names[i], value);
        }
    }

    public boolean isParameterSet(final String name) {
        return getParameter(name) != null;
    }
        
    public synchronized boolean isParameterSetLocally(final String name) {
        return this.parameters != null && this.parameters.get(name) != null;
    }
        
    /**
     * Removes all parameters from this collection.
     */
    public synchronized void clear() {
        this.parameters = null;
    }


    /**
     * Creates a copy of these parameters.
     * The implementation here instantiates {@link BasicHttpParams}
     * with the same default parameters as this object, then calls
     * {@link #copyParams(HttpParams)} to populate the copy.
     * <br/>
     * Derived classes which have to change the class that is
     * instantiated can override this method here. Derived classes
     * which have to change how the copy is populated can override
     * {@link #copyParams(HttpParams)}.
     *
     * @return  a new set of params holding a copy of the
     *          <i>local</i> parameters in this object.
     *          Defaults parameters available via {@link #getDefaults}
     *          are <i>not</i> copied.
     */
    public HttpParams copy() {
        BasicHttpParams bhp = new BasicHttpParams(this.defaults);
        copyParams(bhp);
        return bhp;
    }

    /**
     * Copies the locally defined parameters to the argument parameters.
     * Default parameters accessible via {@link #getDefaults}
     * are <i>not</i> copied.
     * This method is called from {@link #copy()}.
     *
     * @param target    the parameters to which to copy
     */
    protected void copyParams(HttpParams target) {
        if (this.parameters == null)
            return;

        Iterator iter = parameters.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry me = (Map.Entry) iter.next();
            if (me.getKey() instanceof String)
                target.setParameter((String)me.getKey(), me.getValue());
        }
    }
    
}
