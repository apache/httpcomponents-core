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
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * AsyncFilterHandler represents a routine for handling all incoming requests
 * in the server side request processing chain.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public interface AsyncFilterHandler {

    /**
     * Processes the incoming HTTP request and if processing has been completed
     * submits a final response to the client. The handler can choose to send
     * response messages immediately inside the call or asynchronously at some later point.
     * The handler must not use the response trigger after passing control to the next filter
     * with the
     * {@link AsyncFilterChain#proceed(HttpRequest, EntityDetails, HttpContext, AsyncFilterChain.ResponseTrigger)}
     * method.
     *
     * @param request the actual request head.
     * @param entityDetails the request entity details or {@code null} if the request
     *                      does not enclose an entity.
     * @param context the actual execution context.
     * @param responseTrigger the response trigger.
     * @param chain the next element in the request processing chain.
     * @return the data consumer to be used to process incoming request data. It is
     *  expected to be {@code null} if entityDetails parameter is {@code null}.
     */
    AsyncDataConsumer handle(
            HttpRequest request,
            EntityDetails entityDetails,
            HttpContext context,
            AsyncFilterChain.ResponseTrigger responseTrigger,
            AsyncFilterChain chain) throws HttpException, IOException;

}
