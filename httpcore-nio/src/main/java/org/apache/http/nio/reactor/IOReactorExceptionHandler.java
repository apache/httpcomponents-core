/*
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

package org.apache.http.nio.reactor;

import java.io.IOException;

/**
 * Abstract exception handler intended to deal with potentially recoverable
 * I/O exceptions thrown by an I/O reactor.
 *
 * @since 4.0
 */
public interface IOReactorExceptionHandler {

    /**
     * This method is expected to examine the I/O exception passed as
     * a parameter and decide whether it is safe to continue execution of
     * the I/O reactor.
     *
     * @param ex potentially recoverable I/O exception
     * @return <code>true</code> if it is safe to ignore the exception
     * and continue execution of the I/O reactor; <code>false</code> if the
     * I/O reactor must throw {@link IOReactorException} and terminate
     */
    boolean handle(IOException ex);

    /**
     * This method is expected to examine the runtime exception passed as
     * a parameter and decide whether it is safe to continue execution of
     * the I/O reactor.
     *
     * @param ex potentially recoverable runtime exception
     * @return <code>true</code> if it is safe to ignore the exception
     * and continue execution of the I/O reactor; <code>false</code> if the
     * I/O reactor must throw {@link RuntimeException} and terminate
     */
    boolean handle(RuntimeException ex);

}
