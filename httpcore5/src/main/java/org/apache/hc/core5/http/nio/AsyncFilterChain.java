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
 * AsyncFilterChain represents a single element in the server side request processing chain.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public interface AsyncFilterChain {

    /**
     * Response trigger that can be used to generate the final HTTP response
     * and terminate HTTP request processing.
     */
    interface ResponseTrigger {

        /**
         * Sends an intermediate informational HTTP response to the client.
         *
         * @param response the intermediate (1xx) HTTP response.
         */
        void sendInformation(HttpResponse response) throws HttpException, IOException;

        /**
         * Sends a final HTTP response to the client.
         *
         * @param response the final (non 1xx) HTTP response.
         */
        void submitResponse(HttpResponse response, AsyncEntityProducer entityProducer) throws HttpException, IOException;

        /**
         * Pushes a request message head as a promise to deliver a response message.
         *
         * @param promise the request message header used as a promise.
         * @param responseProducer the push response message producer.
         */
        void pushPromise(HttpRequest promise, AsyncPushProducer responseProducer) throws HttpException, IOException;

    }

    /**
     * Proceeds to the next element in the request processing chain.
     *
     * @param request the actual request.
     * @param entityDetails the request entity details or {@code null} if the request
     *                      does not enclose an entity.
     * @param responseTrigger the response trigger.
     * @param context the actual execution context.
     */
    AsyncDataConsumer proceed(
            HttpRequest request,
            EntityDetails entityDetails,
            HttpContext context,
            ResponseTrigger responseTrigger) throws HttpException, IOException;

}
