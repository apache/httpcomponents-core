/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 *
 *  Copyright 1999-2006 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
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
 * This interface represents a collection of HTTP protocol parameters. Protocol parameters
 * may be linked together to form a hierarchy. If a particular parameter value has not been
 * explicitly defined in the collection itself, its value will be drawn from the parent 
 * collection of parameters.
 *   
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 * 
 * @version $Revision$
 *
 * @since 3.0
 */
public interface HttpParams {

    /** 
     * Returns the parent collection that this collection will defer to
     * for a default value if a particular parameter is not explicitly 
     * set in the collection itself
     * 
     * @return the parent collection to defer to, if a particular parameter
     * is not explictly set in the collection itself.
     * 
     * @see #setDefaults(HttpParams)
     */
    HttpParams getDefaults();

    /** 
     * Assigns the parent collection that this collection will defer to
     * for a default value if a particular parameter is not explicitly 
     * set in the collection itself
     * 
     * @param params the parent collection to defer to, if a particular 
     * parameter is not explictly set in the collection itself.
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
     * Returns <tt>true</tt> if the parameter is set locally, <tt>false</tt> otherwise.
     * 
     * @param name parameter name
     * 
     * @return <tt>true</tt> if the parameter is set locally, <tt>false</tt>
     * otherwise.
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

    /**
     * Clones this collection of parameters. This method may produce a shallow copy
     * of the parameter collection if appropriate. 
     * 
     * @see java.lang.Object#clone()
     */
    Object clone();
    
}
