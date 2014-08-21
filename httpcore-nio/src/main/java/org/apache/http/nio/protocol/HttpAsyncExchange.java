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

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.concurrent.Cancellable;

/**
 * {@code HttpAsyncExchange} represents a server-side HTTP message exchange
 * where an HTTP response can be deferred without blocking the I/O event thread
 * and triggered asynchronously at a later point of later time.
 *
 * @since 4.2
 */
public interface HttpAsyncExchange {

    /**
     * Returns the received HTTP request message.
     *
     * @return received HTTP request message.
     */
    HttpRequest getRequest();

    /**
     * Returns the default HTTP response message. Once ready the response
     * message can submitted using {@link #submitResponse()} method.
     *
     * @return default HTTP response message.
     */
    HttpResponse getResponse();

    /**
     * Submits the default HTTP response and completed the message exchange.
     * If the response encloses an {@link org.apache.http.HttpEntity} instance
     * the entity is also expected to implement the
     * {@link org.apache.http.nio.entity.HttpAsyncContentProducer }
     * interface for efficient content streaming to a non-blocking HTTP
     * connection.
     *
     * @throws IllegalStateException if a response has already been submitted.
     */
    void submitResponse();

    /**
     * Submits an HTTP response using a custom
     * {@link HttpAsyncResponseProducer}.
     *
     * @throws IllegalStateException if a response has already been submitted.
     */
    void submitResponse(HttpAsyncResponseProducer responseProducer);

    /**
     * Determines whether or not the message exchange has been completed.
     *
     * @return {@code true} if the message exchange has been completed,
     * {@code false} otherwise.
     */
    boolean isCompleted();

    /**
     * Sets {@link Cancellable} callback to be invoked in case the underlying
     * connection times out or gets terminated prematurely by the client. This
     * callback can be used to cancel a long running response generating
     * process if a response is no longer needed.
     */
    void setCallback(Cancellable cancellable);

    /**
     * Sets timeout for this message exchange.
     */
    void setTimeout(int timeout);

    /**
     * Returns timeout for this message exchange.
     */
    int getTimeout();

}
