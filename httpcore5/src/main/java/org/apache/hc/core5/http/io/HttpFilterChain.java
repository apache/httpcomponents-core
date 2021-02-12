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
 * HttpFilterChain represents a single element in the server side request processing chain.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public interface HttpFilterChain {

    /**
     * Response trigger that can be used to generate the final HTTP response
     * and terminate HTTP request processing.
     */
    interface ResponseTrigger {

        /**
         * Sends an intermediate informational HTTP response to the client.
         *
         * @param response an intermediate (1xx) HTTP response.
         * @throws IOException in case of an I/O error
         * @throws HttpException in case of HTTP protocol violation
         */
        void sendInformation(ClassicHttpResponse response) throws HttpException, IOException;

        /**
         * Sends a final HTTP response to the client.
         *
         * @param response a final (non 1xx) HTTP response.
         * @throws IOException in case of an I/O error
         * @throws HttpException in case of HTTP protocol violation
         */
        void submitResponse(ClassicHttpResponse response) throws HttpException, IOException;

    }

    /**
     * Proceeds to the next element in the request processing chain.
     *
     * @param request the actual request.
     * @param responseTrigger the response trigger.
     * @param context the actual execution context.
     * @throws IOException in case of an I/O error
     * @throws HttpException in case of HTTP protocol violation
     */
    void proceed(
            ClassicHttpRequest request,
            ResponseTrigger responseTrigger,
            HttpContext context) throws HttpException, IOException;

}
