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

import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * Abstract asynchronous client side message exchange handler that acts as a request producer
 * and a response consumer.
 *
 * @since 5.0
 */
public interface AsyncClientExchangeHandler extends AsyncDataExchangeHandler {

    /**
     * Triggered to signal the ability of the underlying request channel
     * to accept a request messages. The data producer can choose to send
     * a request message immediately inside the call or asynchronously
     * at some later point.
     *
     * @param channel the request channel capable to accepting a request message.
     * @param context the actual execution context.
     */
    void produceRequest(RequestChannel channel, HttpContext context) throws HttpException, IOException;

    /**
     * Triggered to signal receipt of a response message head.
     *
     * @param response the response message head.
     * @param entityDetails the response entity details or {@code null} if the response
     *                      does not enclose an entity.
     * @param context the actual execution context.
     */
    void consumeResponse(HttpResponse response, EntityDetails entityDetails, HttpContext context) throws HttpException, IOException;

    /**
     * Triggered to signal receipt of an intermediate (1xx) HTTP response.
     *
     * @param response the intermediate (1xx) HTTP response.
     * @param context the actual execution context.
     */
    void consumeInformation(HttpResponse response, HttpContext context) throws HttpException, IOException;

    /**
     * Triggered to cancel the message exchange.
     */
    void cancel();

}
