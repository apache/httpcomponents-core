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

import java.io.IOException;

import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestFactory;
import org.apache.http.annotation.Immutable;
import org.apache.http.impl.DefaultHttpRequestFactory;
import org.apache.http.impl.nio.reactor.AbstractIODispatch;
import org.apache.http.nio.NHttpServerIOTarget;
import org.apache.http.nio.NHttpServiceHandler;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.util.ByteBufferAllocator;
import org.apache.http.nio.util.HeapByteBufferAllocator;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

/**
 * Default implementation of {@link IOEventDispatch} interface for plain
 * (unencrypted) server-side HTTP connections.
 * <p>
 * The following parameters can be used to customize the behavior of this
 * class:
 * <ul>
 *  <li>{@link org.apache.http.params.CoreProtocolPNames#HTTP_ELEMENT_CHARSET}</li>
 *  <li>{@link org.apache.http.params.CoreConnectionPNames#SO_TIMEOUT}</li>
 *  <li>{@link org.apache.http.params.CoreConnectionPNames#SOCKET_BUFFER_SIZE}</li>
 *  <li>{@link org.apache.http.params.CoreConnectionPNames#MAX_HEADER_COUNT}</li>
 *  <li>{@link org.apache.http.params.CoreConnectionPNames#MAX_LINE_LENGTH}</li>
 * </ul>
 *
 * @since 4.0
 *
 * @deprecated (4.2) use {@link DefaultHttpServerIODispatch}
 */
@Deprecated
@Immutable // provided injected dependencies are immutable
public class DefaultServerIOEventDispatch extends AbstractIODispatch<NHttpServerIOTarget> {

    protected final ByteBufferAllocator allocator;
    protected final NHttpServiceHandler handler;
    protected final HttpParams params;

    /**
     * Creates a new instance of this class to be used for dispatching I/O event
     * notifications to the given protocol handler.
     *
     * @param handler the server protocol handler.
     * @param params HTTP parameters.
     */
     public DefaultServerIOEventDispatch(
            final NHttpServiceHandler handler,
            final HttpParams params) {
        super();
        if (handler == null) {
            throw new IllegalArgumentException("HTTP service handler may not be null");
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
     * Creates an instance of {@link DefaultHttpRequestFactory} to be used
     * by HTTP connections for creating {@link HttpRequest} objects.
     * <p>
     * This method can be overridden in a super class in order to provide
     * a different implementation of the {@link HttpRequestFactory} interface.
     *
     * @return HTTP request factory.
     */
    protected HttpRequestFactory createHttpRequestFactory() {
        return new DefaultHttpRequestFactory();
    }

    /**
     * Creates an instance of {@link DefaultNHttpServerConnection} based on the
     * given {@link IOSession}.
     * <p>
     * This method can be overridden in a super class in order to provide
     * a different implementation of the {@link NHttpServerIOTarget} interface.
     *
     * @param session the underlying I/O session.
     *
     * @return newly created HTTP connection.
     */
    @Override
    protected NHttpServerIOTarget createConnection(final IOSession session) {
        return new DefaultNHttpServerConnection(
                session,
                createHttpRequestFactory(),
                this.allocator,
                this.params);
    }

    @Override
    protected void onConnected(final NHttpServerIOTarget conn) {
        int timeout = HttpConnectionParams.getSoTimeout(this.params);
        conn.setSocketTimeout(timeout);
        this.handler.connected(conn);
    }

    @Override
    protected void onClosed(final NHttpServerIOTarget conn) {
        this.handler.closed(conn);
    }

    @Override
    protected void onException(final NHttpServerIOTarget conn, IOException ex) {
        this.handler.exception(conn, ex);
    }

    @Override
    protected void onInputReady(final NHttpServerIOTarget conn) {
        conn.consumeInput(this.handler);
    }

    @Override
    protected void onOutputReady(final NHttpServerIOTarget conn) {
        conn.produceOutput(this.handler);
    }

    @Override
    protected void onTimeout(final NHttpServerIOTarget conn) {
        this.handler.timeout(conn);
    }

}
