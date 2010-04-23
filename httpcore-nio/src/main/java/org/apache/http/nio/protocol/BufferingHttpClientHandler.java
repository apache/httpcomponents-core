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
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.NHttpClientConnection;
import org.apache.http.nio.NHttpClientHandler;
import org.apache.http.nio.entity.BufferingNHttpEntity;
import org.apache.http.nio.entity.ConsumingNHttpEntity;
import org.apache.http.nio.util.ByteBufferAllocator;
import org.apache.http.nio.util.HeapByteBufferAllocator;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;

/**
 * Client protocol handler implementation that provides compatibility with the
 * blocking I/O by storing the full content of HTTP messages in memory.
 * The {@link HttpRequestExecutionHandler#handleResponse(HttpResponse, HttpContext)}
 * method will fire only when the entire message content has been read into a
 * in-memory buffer. Please note that request execution / response processing
 * take place the main I/O thread and therefore
 * {@link HttpRequestExecutionHandler} methods should not block indefinitely.
 * <p>
 * When using this protocol handler {@link HttpEntity}'s content can be
 * generated / consumed using standard {@link InputStream}/{@link OutputStream}
 * classes.
 * <p>
 * IMPORTANT: This protocol handler should be used only when dealing with HTTP
 * messages that are known to be limited in length.
 *
 *
 * @since 4.0
 */
public class BufferingHttpClientHandler implements NHttpClientHandler {

    private final AsyncNHttpClientHandler asyncHandler;

    public BufferingHttpClientHandler(
            final HttpProcessor httpProcessor,
            final HttpRequestExecutionHandler execHandler,
            final ConnectionReuseStrategy connStrategy,
            final ByteBufferAllocator allocator,
            final HttpParams params) {
        this.asyncHandler = new AsyncNHttpClientHandler(
                httpProcessor,
                new ExecutionHandlerAdaptor(execHandler),
                connStrategy,
                allocator,
                params);
    }

    public BufferingHttpClientHandler(
            final HttpProcessor httpProcessor,
            final HttpRequestExecutionHandler execHandler,
            final ConnectionReuseStrategy connStrategy,
            final HttpParams params) {
        this(httpProcessor, execHandler, connStrategy,
                new HeapByteBufferAllocator(), params);
    }

    public void setEventListener(final EventListener eventListener) {
        this.asyncHandler.setEventListener(eventListener);
    }

    public void connected(final NHttpClientConnection conn, final Object attachment) {
        this.asyncHandler.connected(conn, attachment);
    }

    public void closed(final NHttpClientConnection conn) {
        this.asyncHandler.closed(conn);
    }

    public void requestReady(final NHttpClientConnection conn) {
        this.asyncHandler.requestReady(conn);
    }

    public void inputReady(final NHttpClientConnection conn, final ContentDecoder decoder) {
        this.asyncHandler.inputReady(conn, decoder);
    }

    public void outputReady(final NHttpClientConnection conn, final ContentEncoder encoder) {
        this.asyncHandler.outputReady(conn, encoder);
    }

    public void responseReceived(final NHttpClientConnection conn) {
        this.asyncHandler.responseReceived(conn);
    }

    public void exception(final NHttpClientConnection conn, final HttpException httpex) {
        this.asyncHandler.exception(conn, httpex);
    }

    public void exception(final NHttpClientConnection conn, final IOException ioex) {
        this.asyncHandler.exception(conn, ioex);
    }

    public void timeout(final NHttpClientConnection conn) {
        this.asyncHandler.timeout(conn);
    }

    static class ExecutionHandlerAdaptor implements NHttpRequestExecutionHandler {

        private final HttpRequestExecutionHandler execHandler;

        public ExecutionHandlerAdaptor(final HttpRequestExecutionHandler execHandler) {
            super();
            this.execHandler = execHandler;
        }

        public void initalizeContext(final HttpContext context, final Object attachment) {
            this.execHandler.initalizeContext(context, attachment);
        }
        public void finalizeContext(final HttpContext context) {
            this.execHandler.finalizeContext(context);
        }

        public HttpRequest submitRequest(final HttpContext context) {
            return this.execHandler.submitRequest(context);
        }

        public ConsumingNHttpEntity responseEntity(
                final HttpResponse response,
                final HttpContext context) throws IOException {
            return new BufferingNHttpEntity(
                    response.getEntity(),
                    new HeapByteBufferAllocator());
        }

        public void handleResponse(
                final HttpResponse response,
                final HttpContext context) throws IOException {
            this.execHandler.handleResponse(response, context);
        }

    }

}
