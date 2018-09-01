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
package org.apache.hc.core5.reactive;

import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.nio.ResponseChannel;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.reactivestreams.Publisher;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @since 5.0
 */
public interface ReactiveRequestProcessor {

    /**
     * Processes the actual HTTP request. The handler can choose to send
     * response messages immediately inside the call or asynchronously
     * at some later point.
     *
     * @param request the actual request.
     * @param entityDetails the request entity details or {@code null} if the request
     *                      does not enclose an entity.
     * @param responseChannel the response channel.
     * @param context the actual execution context.
     * @param requestBody a reactive stream representing the request body.
     * @param responseBodyCallback a callback to invoke with the response body, if any.
     */
    void processRequest(
            HttpRequest request,
            EntityDetails entityDetails,
            ResponseChannel responseChannel,
            HttpContext context,
            Publisher<ByteBuffer> requestBody,
            Callback<Publisher<ByteBuffer>> responseBodyCallback) throws HttpException, IOException;
}
