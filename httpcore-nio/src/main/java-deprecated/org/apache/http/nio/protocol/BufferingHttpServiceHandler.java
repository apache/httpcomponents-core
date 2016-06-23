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

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseFactory;
import org.apache.http.annotation.ThreadingBehavior;
import org.apache.http.annotation.Contract;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.NHttpServerConnection;
import org.apache.http.nio.NHttpServiceHandler;
import org.apache.http.nio.entity.BufferingNHttpEntity;
import org.apache.http.nio.entity.ConsumingNHttpEntity;
import org.apache.http.nio.util.ByteBufferAllocator;
import org.apache.http.nio.util.HeapByteBufferAllocator;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpExpectationVerifier;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.HttpRequestHandlerResolver;

/**
 * Service protocol handler implementations that provide compatibility with
 * the blocking I/O by storing the full content of HTTP messages in memory.
 * The {@link HttpRequestHandler#handle(HttpRequest, HttpResponse, HttpContext)}
 * method will fire only when the entire message content has been read into
 * an in-memory buffer. Please note that request processing take place the
 * main I/O thread and therefore individual HTTP request handlers should not
 * block indefinitely.
 * <p>
 * When using this protocol handler {@link org.apache.http.HttpEntity}'s content
 * can be generated / consumed using standard {@link java.io.InputStream}/
 * {@link java.io.OutputStream} classes.
 * <p>
 * IMPORTANT: This protocol handler should be used only when dealing with HTTP
 * messages that are known to be limited in length.
 *
 * @since 4.0
 *
 * @deprecated (4.2) use {@link HttpAsyncService}
 */
@Deprecated
@Contract(threading = ThreadingBehavior.IMMUTABLE_CONDITIONAL)
public class BufferingHttpServiceHandler implements NHttpServiceHandler {

    private final AsyncNHttpServiceHandler asyncHandler;

    private HttpRequestHandlerResolver handlerResolver;

    public BufferingHttpServiceHandler(
            final HttpProcessor httpProcessor,
            final HttpResponseFactory responseFactory,
            final ConnectionReuseStrategy connStrategy,
            final ByteBufferAllocator allocator,
            final HttpParams params) {
        super();
        this.asyncHandler = new AsyncNHttpServiceHandler(
                httpProcessor,
                responseFactory,
                connStrategy,
                allocator,
                params);
        this.asyncHandler.setHandlerResolver(new RequestHandlerResolverAdaptor());
    }

    public BufferingHttpServiceHandler(
            final HttpProcessor httpProcessor,
            final HttpResponseFactory responseFactory,
            final ConnectionReuseStrategy connStrategy,
            final HttpParams params) {
        this(httpProcessor, responseFactory, connStrategy,
                HeapByteBufferAllocator.INSTANCE, params);
    }

    public void setEventListener(final EventListener eventListener) {
        this.asyncHandler.setEventListener(eventListener);
    }

    public void setExpectationVerifier(final HttpExpectationVerifier expectationVerifier) {
        this.asyncHandler.setExpectationVerifier(expectationVerifier);
    }

    public void setHandlerResolver(final HttpRequestHandlerResolver handlerResolver) {
        this.handlerResolver = handlerResolver;
    }

    @Override
    public void connected(final NHttpServerConnection conn) {
        this.asyncHandler.connected(conn);
    }

    @Override
    public void closed(final NHttpServerConnection conn) {
        this.asyncHandler.closed(conn);
    }

    @Override
    public void requestReceived(final NHttpServerConnection conn) {
        this.asyncHandler.requestReceived(conn);
    }

    @Override
    public void inputReady(final NHttpServerConnection conn, final ContentDecoder decoder) {
        this.asyncHandler.inputReady(conn, decoder);
    }

    @Override
    public void responseReady(final NHttpServerConnection conn) {
        this.asyncHandler.responseReady(conn);
    }

    @Override
    public void outputReady(final NHttpServerConnection conn, final ContentEncoder encoder) {
        this.asyncHandler.outputReady(conn, encoder);
    }

    @Override
    public void exception(final NHttpServerConnection conn, final HttpException httpex) {
        this.asyncHandler.exception(conn, httpex);
    }

    @Override
    public void exception(final NHttpServerConnection conn, final IOException ioex) {
        this.asyncHandler.exception(conn, ioex);
    }

    @Override
    public void timeout(final NHttpServerConnection conn) {
        this.asyncHandler.timeout(conn);
    }

    class RequestHandlerResolverAdaptor implements NHttpRequestHandlerResolver {

        @Override
        public NHttpRequestHandler lookup(final String requestURI) {
            final HttpRequestHandler handler = handlerResolver.lookup(requestURI);
            if (handler != null) {
                return new RequestHandlerAdaptor(handler);
            } else {
                return null;
            }
        }

    }

    static class RequestHandlerAdaptor extends SimpleNHttpRequestHandler {

        private final HttpRequestHandler requestHandler;

        public RequestHandlerAdaptor(final HttpRequestHandler requestHandler) {
            super();
            this.requestHandler = requestHandler;
        }

        @Override
        public ConsumingNHttpEntity entityRequest(
                final HttpEntityEnclosingRequest request,
                final HttpContext context) throws HttpException, IOException {
            return new BufferingNHttpEntity(
                    request.getEntity(),
                    HeapByteBufferAllocator.INSTANCE);
        }

        @Override
        public void handle(
                final HttpRequest request,
                final HttpResponse response,
                final HttpContext context) throws HttpException, IOException {
            this.requestHandler.handle(request, response, context);
        }

    }

}
