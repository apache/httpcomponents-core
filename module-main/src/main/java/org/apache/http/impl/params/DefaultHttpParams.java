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

package org.apache.http.impl.params;

import java.io.Serializable;
import java.util.HashMap;

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
public class DefaultHttpParams implements HttpParams, Serializable {

	static final long serialVersionUID = -8296449161405728403L;
	
    /** The set of default values to defer to */
    private HttpParams defaults = null;

    /** Hash map of HTTP parameters that this collection contains */
    private HashMap parameters = null;
    
    /**
     * Creates a new collection of parameters with the given parent. 
     * The collection will defer to its parent for a default value 
     * if a particular parameter is not explicitly set in the collection
     * itself.
     * 
     * @param defaults the parent collection to defer to, if a parameter
     * is not explictly set in the collection itself.
     */
    public DefaultHttpParams(final HttpParams defaults) {
        super();
        this.defaults = defaults; 
    }
    
    public DefaultHttpParams() {
        this(null);
    }

    public synchronized HttpParams getDefaults() {
        return this.defaults;
    }
    
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

    public long getLongParameter(final String name, long defaultValue) { 
        Object param = getParameter(name);
        if (param == null) {
            return defaultValue;
        }
        return ((Long)param).longValue();
    }
    
    public HttpParams setLongParameter(final String name, long value) {
        setParameter(name, new Long(value));
        return this;
    }

    public int getIntParameter(final String name, int defaultValue) { 
        Object param = getParameter(name);
        if (param == null) {
            return defaultValue;
        }
        return ((Integer)param).intValue();
    }
    
    public HttpParams setIntParameter(final String name, int value) {
        setParameter(name, new Integer(value));
        return this;
    }

    public double getDoubleParameter(final String name, double defaultValue) { 
        Object param = getParameter(name);
        if (param == null) {
            return defaultValue;
        }
        return ((Double)param).doubleValue();
    }
    
    public HttpParams setDoubleParameter(final String name, double value) {
        setParameter(name, new Double(value));
        return this;
    }

    public boolean getBooleanParameter(final String name, boolean defaultValue) { 
        Object param = getParameter(name);
        if (param == null) {
            return defaultValue;
        }
        return ((Boolean)param).booleanValue();
    }
    
    public HttpParams setBooleanParameter(final String name, boolean value) {
        setParameter(name, value ? Boolean.TRUE : Boolean.FALSE);
        return this;
    }

    public boolean isParameterSet(final String name) {
        return getParameter(name) != null;
    }
        
    public boolean isParameterSetLocally(final String name) {
        return this.parameters != null && this.parameters.get(name) != null;
    }
        
    public boolean isParameterTrue(final String name) {
        return getBooleanParameter(name, false);
    }
        
    public boolean isParameterFalse(final String name) {
        return !getBooleanParameter(name, false);
    }

    /**
     * Removes all parameters from this collection. 
     */
    public void clear() {
        this.parameters = null;
    }

}
