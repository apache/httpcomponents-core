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
package org.apache.hc.core5.http.nio;

import java.io.IOException;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * AsyncServerRequestHandler represents a routine for processing of a specific group
 * of HTTP requests. Request execution filters are designed to take care of protocol
 * specific aspects, whereas individual request handlers are expected to take care
 * of application specific HTTP processing. The main purpose of a request handler
 * is to generate a response object with a content entity to be sent back to
 * the client in response to the given request.
 *
 * @param <T> request representation.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public interface AsyncServerRequestHandler<T> {

    /**
     * Response trigger that can be used to submit a final HTTP response
     * and terminate HTTP request processing.
     */
    interface ResponseTrigger {

        /**
         * Sends an intermediate informational HTTP response to the client.
         *
         * @param response the intermediate (1xx) HTTP response
         * @param context the actual execution context.
         */
        void sendInformation(HttpResponse response, HttpContext context) throws HttpException, IOException;

        /**
         * Sends a final HTTP response to the client.
         *
         * @param responseProducer the HTTP response message producer.
         * @param context the actual execution context.
         */
        void submitResponse(AsyncResponseProducer responseProducer, HttpContext context) throws HttpException, IOException;

        /**
         * Pushes a request message head as a promise to deliver a response message.
         *
         * @param promise the request message header used as a promise.
         * @param context the actual execution context.
         * @param responseProducer the push response message producer.
         */
        void pushPromise(HttpRequest promise, HttpContext context, AsyncPushProducer responseProducer) throws HttpException, IOException;

    }

    /**
     * Triggered to signal new incoming request. The handler can create a {@link AsyncRequestConsumer} based on
     * properties of the request head and entity details and let it process the request data stream. The request
     *  handler will be used to generate an object that represents request data.
     *
     * @param request the incoming request head.
     * @param entityDetails the request entity details or {@code null} if the request
     *                      does not enclose an entity.
     * @param context the actual execution context.
     * @return the request handler.
     */
    AsyncRequestConsumer<T> prepare(HttpRequest request, EntityDetails entityDetails, HttpContext context) throws HttpException;

    /**
     * Triggered to handles the request object produced by the {@link AsyncRequestConsumer} returned
     * from the {@link #prepare(HttpRequest, EntityDetails, HttpContext)} method. The handler can choose
     * to send response messages immediately inside the call or asynchronously at some later point.
     *
     * @param requestObject the request object.
     * @param responseTrigger the response trigger.
     * @param context the actual execution context.
     */
    void handle(T requestObject, ResponseTrigger responseTrigger, HttpContext context) throws HttpException, IOException;

}
