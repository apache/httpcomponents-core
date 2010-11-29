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

package org.apache.http.impl.nio;

import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseFactory;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.nio.NHttpClientIOTarget;
import org.apache.http.nio.NHttpClientHandler;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.util.ByteBufferAllocator;
import org.apache.http.nio.util.HeapByteBufferAllocator;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.ExecutionContext;

/**
 * Default implementation of {@link IOEventDispatch} interface for plain
 * (unencrypted) client-side HTTP connections.
 * <p>
 * The following parameters can be used to customize the behavior of this
 * class:
 * <ul>
 *  <li>{@link org.apache.http.params.CoreProtocolPNames#HTTP_ELEMENT_CHARSET}</li>
 *  <li>{@link org.apache.http.params.CoreConnectionPNames#SOCKET_BUFFER_SIZE}</li>
 *  <li>{@link org.apache.http.params.CoreConnectionPNames#MAX_HEADER_COUNT}</li>
 *  <li>{@link org.apache.http.params.CoreConnectionPNames#MAX_LINE_LENGTH}</li>
 * </ul>
 *
 * @since 4.0
 */
public class DefaultClientIOEventDispatch implements IOEventDispatch {

    protected final ByteBufferAllocator allocator;
    protected final NHttpClientHandler handler;
    protected final HttpParams params;

    /**
     * Creates a new instance of this class to be used for dispatching I/O event
     * notifications to the given protocol handler.
     *
     * @param handler the client protocol handler.
     * @param params HTTP parameters.
     */
    public DefaultClientIOEventDispatch(
            final NHttpClientHandler handler,
            final HttpParams params) {
        super();
        if (handler == null) {
            throw new IllegalArgumentException("HTTP client handler may not be null");
        }
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        this.allocator = createByteBufferAllocator();
        this.handler = handler;
        this.params = params;
    }

    /**
     * Creates an instance of {@link HeapByteBufferAllocator} to be used
     * by HTTP connections for allocating {@link java.nio.ByteBuffer} objects.
     * <p>
     * This method can be overridden in a super class in order to provide
     * a different implementation of the {@link ByteBufferAllocator} interface.
     *
     * @return byte buffer allocator.
     */
    protected ByteBufferAllocator createByteBufferAllocator() {
        return new HeapByteBufferAllocator();
    }

    /**
     * Creates an instance of {@link DefaultHttpResponseFactory} to be used
     * by HTTP connections for creating {@link HttpResponse} objects.
     * <p>
     * This method can be overridden in a super class in order to provide
     * a different implementation of the {@link HttpResponseFactory} interface.
     *
     * @return HTTP response factory.
     */
    protected HttpResponseFactory createHttpResponseFactory() {
        return new DefaultHttpResponseFactory();
    }

    /**
     * Creates an instance of {@link DefaultNHttpClientConnection} based on the
     * given {@link IOSession}.
     * <p>
     * This method can be overridden in a super class in order to provide
     * a different implementation of the {@link NHttpClientIOTarget} interface.
     *
     * @param session the underlying I/O session.
     *
     * @return newly created HTTP connection.
     */
    protected NHttpClientIOTarget createConnection(final IOSession session) {
        return new DefaultNHttpClientConnection(
                session,
                createHttpResponseFactory(),
                this.allocator,
                this.params);
    }

    public void connected(final IOSession session) {
        NHttpClientIOTarget conn = createConnection(session);
        Object attachment = session.getAttribute(IOSession.ATTACHMENT_KEY);
        session.setAttribute(ExecutionContext.HTTP_CONNECTION, conn);
        this.handler.connected(conn, attachment);
    }

    public void disconnected(final IOSession session) {
        NHttpClientIOTarget conn =
            (NHttpClientIOTarget) session.getAttribute(ExecutionContext.HTTP_CONNECTION);
        if (conn != null) {
            this.handler.closed(conn);
        }
    }

    private void ensureNotNull(final NHttpClientIOTarget conn) {
        if (conn == null) {
            throw new IllegalStateException("HTTP connection is null");
        }
    }

    public void inputReady(final IOSession session) {
        NHttpClientIOTarget conn =
            (NHttpClientIOTarget) session.getAttribute(ExecutionContext.HTTP_CONNECTION);
        ensureNotNull(conn);
        conn.consumeInput(this.handler);
    }

    public void outputReady(final IOSession session) {
        NHttpClientIOTarget conn =
            (NHttpClientIOTarget) session.getAttribute(ExecutionContext.HTTP_CONNECTION);
        ensureNotNull(conn);
        conn.produceOutput(this.handler);
    }

    public void timeout(final IOSession session) {
        NHttpClientIOTarget conn =
            (NHttpClientIOTarget) session.getAttribute(ExecutionContext.HTTP_CONNECTION);
        ensureNotNull(conn);
        this.handler.timeout(conn);
    }

}
