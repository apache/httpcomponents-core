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

/**
 * Represents a collection of HTTP protocol and framework parameters.
 * <br/>
 * <b>WARNING:</b> This interface includes methods for building hierarchies of
 * parameters. These methods are clearly marked as <i>not part of the API</i>.
 * The handling of default parameters is currently subject to discussions and
 * may be changed signifcantly, or removed. Do not try to evaluate, build or
 * modify parameter hierarchies in your application.
 *   
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 * 
 * @version $Revision$
 *
 * @since 4.0
 */
public interface HttpParams {

    /**
     * <b>WARNING:</b> This method is not part of the API. 
     * It is exclusively for internal use by the HTTP Components framework.
     * Do not call it in your application. Do not try to evaluate, build or
     * modify parameter hierarchies in your application.
     * <br/>
     *
     * Returns the parent collection that the collection may defer to
     * for a default value if a particular parameter is not explicitly set.
     * 
     * @return the parent collection, or <code>null</code>
     * 
     * @see #setDefaults(HttpParams)
     */
    HttpParams getDefaults();

    /** 
     * <b>WARNING:</b> This method is not part of the API. 
     * It is exclusively for internal use by the HTTP Components framework.
     * Do not call it in your application. Do not try to evaluate, build or
     * modify parameter hierarchies in your application.
     * <br/>
     *
     * Provides the parent collection that this collection may defer to
     * for a default value if a particular parameter is not explicitly set.
     * 
     * @param params the parent collection, or <code>null</code>
     * 
     * @see #getDefaults()
     */
    void setDefaults(HttpParams params);
    
    /** 
     * Returns a parameter value with the given name. If the parameter is
     * not explicitly defined in this collection, its value will be drawn 
     * from a higer level collection at which this parameter is defined.
     * If the parameter is not explicitly set anywhere up the hierarchy,
     * <tt>null</tt> value is returned.  
     * 
     * @param name the parent name.
     * 
     * @return an object that represents the value of the parameter.
     * 
     * @see #setParameter(String, Object)
     */
    Object getParameter(String name);

    /**
     * Assigns the value to the parameter with the given name
     * 
     * @param name parameter name
     * @param value parameter value
     */ 
    HttpParams setParameter(String name, Object value);

    /**
     * Creates a copy of these parameters.
     *
     * @return  a new set of parameters holding the same values as this one
     */
    HttpParams copy();

    /** 
     * Returns a {@link Long} parameter value with the given name. 
     * If the parameter is not explicitly defined in this collection, its 
     * value will be drawn from a higer level collection at which this parameter 
     * is defined. If the parameter is not explicitly set anywhere up the hierarchy,
     * the default value is returned.  
     * 
     * @param name the parent name.
     * @param defaultValue the default value.
     * 
     * @return a {@link Long} that represents the value of the parameter.
     * 
     * @see #setLongParameter(String, long)
     */
    long getLongParameter(String name, long defaultValue); 
    
    /**
     * Assigns a {@link Long} to the parameter with the given name
     * 
     * @param name parameter name
     * @param value parameter value
     */ 
    HttpParams setLongParameter(String name, long value);

    /** 
     * Returns an {@link Integer} parameter value with the given name. 
     * If the parameter is not explicitly defined in this collection, its 
     * value will be drawn from a higer level collection at which this parameter 
     * is defined. If the parameter is not explicitly set anywhere up the hierarchy,
     * the default value is returned.  
     * 
     * @param name the parent name.
     * @param defaultValue the default value.
     * 
     * @return a {@link Integer} that represents the value of the parameter.
     * 
     * @see #setIntParameter(String, int)
     */
    int getIntParameter(String name, int defaultValue); 
    
    /**
     * Assigns an {@link Integer} to the parameter with the given name
     * 
     * @param name parameter name
     * @param value parameter value
     */ 
    HttpParams setIntParameter(String name, int value);

    /** 
     * Returns a {@link Double} parameter value with the given name. 
     * If the parameter is not explicitly defined in this collection, its 
     * value will be drawn from a higer level collection at which this parameter 
     * is defined. If the parameter is not explicitly set anywhere up the hierarchy,
     * the default value is returned.  
     * 
     * @param name the parent name.
     * @param defaultValue the default value.
     * 
     * @return a {@link Double} that represents the value of the parameter.
     * 
     * @see #setDoubleParameter(String, double)
     */
    double getDoubleParameter(String name, double defaultValue); 
    
    /**
     * Assigns a {@link Double} to the parameter with the given name
     * 
     * @param name parameter name
     * @param value parameter value
     */ 
    HttpParams setDoubleParameter(String name, double value);

    /** 
     * Returns a {@link Boolean} parameter value with the given name. 
     * If the parameter is not explicitly defined in this collection, its 
     * value will be drawn from a higer level collection at which this parameter 
     * is defined. If the parameter is not explicitly set anywhere up the hierarchy,
     * the default value is returned.  
     * 
     * @param name the parent name.
     * @param defaultValue the default value.
     * 
     * @return a {@link Boolean} that represents the value of the parameter.
     * 
     * @see #setBooleanParameter(String, boolean)
     */
    boolean getBooleanParameter(String name, boolean defaultValue); 
    
    /**
     * Assigns a {@link Boolean} to the parameter with the given name
     * 
     * @param name parameter name
     * @param value parameter value
     */ 
    HttpParams setBooleanParameter(String name, boolean value);

    /**
     * Returns <tt>true</tt> if the parameter is set at any level, <tt>false</tt> otherwise.
     * 
     * @param name parameter name
     * 
     * @return <tt>true</tt> if the parameter is set at any level, <tt>false</tt>
     * otherwise.
     */
    boolean isParameterSet(String name);
        
    /**
     * <b>WARNING:</b> This method is not part of the API. 
     * It is exclusively for internal use by the HTTP Components framework.
     * Do not call it in your application. Do not try to evaluate, build or
     * modify parameter hierarchies in your application.
     * <br/>
     * 
     * @param name      the parameter name
     * 
     * @return  <tt>true</tt> if the parameter is set locally,
     *          <tt>false</tt> otherwise
     *
     * @see #getDefaults()
     * @see #setDefaults(HttpParams)
     */
    boolean isParameterSetLocally(String name);

        
    /**
     * Returns <tt>true</tt> if the parameter is set and is <tt>true</tt>, <tt>false</tt>
     * otherwise.
     * 
     * @param name parameter name
     * 
     * @return <tt>true</tt> if the parameter is set and is <tt>true</tt>, <tt>false</tt>
     * otherwise.
     */
    boolean isParameterTrue(String name);
        
    /**
     * Returns <tt>true</tt> if the parameter is either not set or is <tt>false</tt>, 
     * <tt>false</tt> otherwise.
     * 
     * @param name parameter name
     * 
     * @return <tt>true</tt> if the parameter is either not set or is <tt>false</tt>, 
     * <tt>false</tt> otherwise.
     */
    boolean isParameterFalse(String name);

}
