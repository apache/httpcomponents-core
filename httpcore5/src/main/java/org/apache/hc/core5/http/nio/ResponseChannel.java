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
 * Abstract response / response promise channel.
 * <p>
 * Implementations are expected to be thread-safe.
 * </p>
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.SAFE)
public interface ResponseChannel {

    /**
     * Sends an intermediate informational HTTP response through this channel.
     *
     * @param response an intermediate (1xx) HTTP response.
     * @param context the actual execution context.
     */
    void sendInformation(HttpResponse response, HttpContext context) throws HttpException, IOException;

    /**
     * Sends a final HTTP response through this channel.
     *
     * @param response a final (non 1xx) HTTP response
     * @param entityDetails the response entity details or {@code null} if the response
     *                      does not enclose an entity.
     * @param context the actual execution context.
     */
    void sendResponse(HttpResponse response, EntityDetails entityDetails, HttpContext context) throws HttpException, IOException;

    /**
     * Pushes a request message head through this channel as a promise to deliver
     * a response message.
     *
     * @param promise the request message header used as a promise.
     * @param responseProducer the push response message producer.
     * @param context the actual execution context.
     */
    void pushPromise(HttpRequest promise, AsyncPushProducer responseProducer, HttpContext context) throws HttpException, IOException;

}
