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

package org.apache.http.nio.protocol;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;

/**
 * <tt>HttpAsyncRequestExecutionHandler</tt> represents a callback interface
 * that combines functionality of {@link HttpAsyncRequestProducer} and
 * {@link HttpAsyncResponseConsumer} and is capable of handling logically
 * related series of HTTP request / response exchanges.
 *
 * @param <T> the result type of request execution.
 * @since 4.2
 */
public interface HttpAsyncRequestExecutionHandler<T>
    extends HttpAsyncRequestProducer, HttpAsyncResponseConsumer<T> {

    /**
     * Returns shared {@link HttpContext} instance.
     *
     * @return HTTP context
     */
    HttpContext getContext();

    /**
     * Returns {@link HttpProcessor} implementation to be used to process
     * HTTP request and response messages for protocol compliance.
     *
     * @return HTTP protocol processor.
     */
    HttpProcessor getHttpProcessor();

    /**
     * Returns {@link ConnectionReuseStrategy} implementation to be used
     * to determine whether or not the underlying connection can be kept alive
     * after a particular HTTP request / response exchange.
     *
     * @return connection re-use strategy.
     */
    ConnectionReuseStrategy getConnectionReuseStrategy();

}
