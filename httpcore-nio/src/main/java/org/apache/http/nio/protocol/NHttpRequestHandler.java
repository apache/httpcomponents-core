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

import java.io.IOException;

import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.nio.entity.ConsumingNHttpEntity;
import org.apache.http.protocol.HttpContext;

/**
 * NHttpRequestHandler represents a routine for asynchronous processing of
 * a specific group of non-blocking HTTP requests. Protocol handlers are
 * designed to take care of protocol specific aspects, whereas individual
 * request handlers are expected to take care of application specific HTTP
 * processing. The main purpose of a request handler is to generate a response
 * object with a content entity to be sent back to the client in response to
 * the given request
 *
 * @since 4.0
 */
public interface NHttpRequestHandler {

    /**
     * Triggered when a request is received with an entity. This method should
     * return a {@link ConsumingNHttpEntity} that will be used to consume the
     * entity. <code>null</code> is a valid response value, and will indicate
     * that the entity should be silently ignored.
     * <p>
     * After the entity is fully consumed,
     * {@link #handle(HttpRequest, HttpResponse, NHttpResponseTrigger, HttpContext)}
     * is called to notify a full request & entity are ready to be processed.
     *
     * @param request the entity enclosing request.
     * @param context the execution context.
     * @return non-blocking HTTP entity.
     * @throws IOException in case of an I/O error.
     * @throws HttpException in case of HTTP protocol violation or a processing
     *   problem.
     */
    ConsumingNHttpEntity entityRequest(HttpEntityEnclosingRequest request,
            HttpContext context)
        throws HttpException, IOException;

    /**
     * Initiates processing of the request. This method does not have to submit
     * a response immediately. It can defer transmission of the HTTP response
     * back to the client without blocking the I/O thread by delegating the
     * process of handling the HTTP request to a worker thread. The worker
     * thread in its turn can use the instance of {@link NHttpResponseTrigger}
     * passed as a parameter to submit a response as at a later point of time
     * once content of the response becomes available.
     *
     * @param request the HTTP request.
     * @param response the HTTP response.
     * @param trigger the response trigger.
     * @param context the HTTP execution context.
     * @throws IOException in case of an I/O error.
     * @throws HttpException in case of HTTP protocol violation or a processing
     *   problem.
     */
    void handle(HttpRequest request, HttpResponse response,
            NHttpResponseTrigger trigger, HttpContext context)
        throws HttpException, IOException;

}
