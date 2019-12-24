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

package org.apache.hc.core5.testing.reactive;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.impl.BasicEntityDetails;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.ResponseChannel;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.reactive.ReactiveRequestProcessor;
import org.reactivestreams.Publisher;

public final class ReactiveEchoProcessor implements ReactiveRequestProcessor {
    public ReactiveEchoProcessor() {
    }

    @Override
    public void processRequest(
            final HttpRequest request,
            final EntityDetails entityDetails,
            final ResponseChannel responseChannel,
            final HttpContext context,
            final Publisher<ByteBuffer> requestBody,
            final Callback<Publisher<ByteBuffer>> responseBodyFuture
    ) throws HttpException, IOException {
        if (new BasicHeader(HttpHeaders.EXPECT, HeaderElements.CONTINUE).equals(request.getHeader(HttpHeaders.EXPECT))) {
            responseChannel.sendInformation(new BasicHttpResponse(100), context);
        }

        responseChannel.sendResponse(
                new BasicHttpResponse(200),
                new BasicEntityDetails(-1, ContentType.APPLICATION_OCTET_STREAM),
                context);
        responseBodyFuture.execute(requestBody);
    }
}