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

package org.apache.http.nio;

/**
 * Content input/output control interface. It can be used to request or
 * temporarily suspend event notifications that are triggered when the underlying
 * channel is ready for input / output operations. 
 * 
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 */
public interface ContentIOControl {

    /**
     * Requests event notifications to be triggered when the underlying
     * channel is ready for input oprtations.
     */
    void requestInput();
    
    /**
     * Suspends event notifications about the underlying channel being 
     * ready for input operations.
     */
    void suspendInput();
    
    /**
     * Requests event notifications to be triggered when the underlying
     * channel is ready for output oprtations.
     */
    void requestOutput();
    
    /**
     * Suspends event notifications about the underlying channel being 
     * ready for output operations.
     */
    void suspendOutput();
    
}
