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
 * Represents a collection of HTTP protocol and framework parameters
 * that can be linked with another one to form a parameter hierarchy. 
 * <br/>
 * <b>WARNING:</b> This interface includes methods for building hierarchies of
 * parameters. These methods are intended for internal use by the the HTTP Components
 * framework. Use with caution.
 *   
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 * 
 * @version $Revision$
 *
 * @since 4.0
 */
public interface HttpLinkedParams extends HttpParams {

    /**
     * <b>WARNING:</b> This method is not part of the API. 
     * It is intended for internal use by the HTTP Components framework.
     * Use with caution.
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
     * It is intended for internal use by the HTTP Components framework.
     * Use with caution.
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
     * <b>WARNING:</b> This method is not part of the API. 
     * It is intended for internal use by the HTTP Components framework.
     * Use with caution.
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
    
}
