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
package org.apache.hc.core5.http.io;

import java.io.IOException;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * HttpServerRequestHandler represents a routine for processing of a specific group
 * of HTTP requests. Request execution filters are designed to take care of protocol
 * specific aspects, whereas individual request handlers are expected to take
 * care of application specific HTTP processing. The main purpose of a request
 * handler is to generate a response object with a content entity to be sent
 * back to the client in response to the given request.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public interface HttpServerRequestHandler {

    /**
     * Response trigger that can be used to submit a final HTTP response
     * and terminate HTTP request processing.
     */
    interface ResponseTrigger {

        /**
         * Sends an intermediate informational HTTP response to the client.
         *
         * @param response the intermediate (1xx) HTTP response
         */
        void sendInformation(ClassicHttpResponse response) throws HttpException, IOException;

        /**
         * Sends a final HTTP response to the client.
         *
         * @param response the final (non 1xx) HTTP response
         */
        void submitResponse(ClassicHttpResponse response) throws HttpException, IOException;

    }

    /**
     * Handles the request and submits a final response to be sent back to the client.
     *
     * @param request the actual request.
     * @param responseTrigger the response trigger.
     * @param context the actual execution context.
     */
    void handle(
            ClassicHttpRequest request,
            ResponseTrigger responseTrigger,
            HttpContext context) throws HttpException, IOException;

}
