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

import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseFactory;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.ProtocolVersion;
import org.apache.http.annotation.Immutable;
import org.apache.http.concurrent.Cancellable;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.nio.util.ByteBufferAllocator;
import org.apache.http.nio.util.HeapByteBufferAllocator;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;

/**
 * @since 4.2
 */
@Immutable
public class BufferingAsyncRequestHandler implements HttpAsyncRequestHandler<HttpRequest> {

    private final HttpRequestHandler handler;
    private final HttpResponseFactory responseFactory;
    private final ByteBufferAllocator allocator;

    public BufferingAsyncRequestHandler(
            final HttpRequestHandler handler,
            final HttpResponseFactory responseFactory,
            final ByteBufferAllocator allocator) {
        super();
        if (handler == null) {
            throw new IllegalArgumentException("Request handler may not be null");
        }
        if (responseFactory == null) {
            throw new IllegalArgumentException("Response factory may not be null");
        }
        if (allocator == null) {
            throw new IllegalArgumentException("Byte buffer allocator may not be null");
        }
        this.handler = handler;
        this.responseFactory = responseFactory;
        this.allocator = allocator;
    }

    public BufferingAsyncRequestHandler(final HttpRequestHandler handler) {
        this(handler, new DefaultHttpResponseFactory(), new HeapByteBufferAllocator());
    }

    public HttpAsyncRequestConsumer<HttpRequest> processRequest(final HttpRequest request,
            final HttpContext context) {
        return new BasicAsyncRequestConsumer(this.allocator);
    }

    public Cancellable handle(final HttpRequest request, final HttpAsyncResponseTrigger trigger,
            final HttpContext context) throws HttpException, IOException {
        ProtocolVersion ver = request.getRequestLine().getProtocolVersion();
        if (!ver.lessEquals(HttpVersion.HTTP_1_1)) {
            ver = HttpVersion.HTTP_1_1;
        }
        HttpResponse response = this.responseFactory.newHttpResponse(ver, HttpStatus.SC_OK, context);
        this.handler.handle(request, response, context);
        trigger.submitResponse(new BasicAsyncResponseProducer(response));
        return null;
    }

}
