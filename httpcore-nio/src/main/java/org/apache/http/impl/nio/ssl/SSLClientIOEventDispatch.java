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

package org.apache.http.impl.nio.ssl;

import java.io.IOException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;

import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseFactory;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.nio.DefaultNHttpClientConnection;
import org.apache.http.impl.nio.reactor.SSLIOSession;
import org.apache.http.impl.nio.reactor.SSLMode;
import org.apache.http.impl.nio.reactor.SSLSetupHandler;
import org.apache.http.nio.NHttpClientHandler;
import org.apache.http.nio.NHttpClientIOTarget;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.util.ByteBufferAllocator;
import org.apache.http.nio.util.HeapByteBufferAllocator;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.ExecutionContext;

/**
 * Default implementation of {@link IOEventDispatch} interface for SSL
 * (encrypted) client-side HTTP connections.
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
 * @since 4.1
 */
public class SSLClientIOEventDispatch implements IOEventDispatch {

    private static final String SSL_SESSION = "http.nio.ssl-session";

    private final NHttpClientHandler handler;
    private final SSLContext sslcontext;
    private final SSLSetupHandler sslHandler;
    private final HttpParams params;

    /**
     * Creates a new instance of this class to be used for dispatching I/O event
     * notifications to the given protocol handler using the given
     * {@link SSLContext}. This I/O dispatcher will transparently handle SSL
     * protocol aspects for HTTP connections.
     *
     * @param handler the client protocol handler.
     * @param sslcontext the SSL context.
     * @param sslHandler the SSL setup handler.
     * @param params HTTP parameters.
     */
    public SSLClientIOEventDispatch(
            final NHttpClientHandler handler,
            final SSLContext sslcontext,
            final SSLSetupHandler sslHandler,
            final HttpParams params) {
        super();
        if (handler == null) {
            throw new IllegalArgumentException("HTTP client handler may not be null");
        }
        if (sslcontext == null) {
            throw new IllegalArgumentException("SSL context may not be null");
        }
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        this.handler = handler;
        this.params = params;
        this.sslcontext = sslcontext;
        this.sslHandler = sslHandler;
    }

    /**
     * Creates a new instance of this class to be used for dispatching I/O event
     * notifications to the given protocol handler using the given
     * {@link SSLContext}. This I/O dispatcher will transparently handle SSL
     * protocol aspects for HTTP connections.
     *
     * @param handler the client protocol handler.
     * @param sslcontext the SSL context.
     * @param params HTTP parameters.
     */
    public SSLClientIOEventDispatch(
            final NHttpClientHandler handler,
            final SSLContext sslcontext,
            final HttpParams params) {
        this(handler, sslcontext, null, params);
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
     * given SSL {@link IOSession}.
     * <p>
     * This method can be overridden in a super class in order to provide
     * a different implementation of the {@link NHttpClientIOTarget} interface.
     *
     * @param session the underlying SSL I/O session.
     *
     * @return newly created HTTP connection.
     */
    protected NHttpClientIOTarget createConnection(final IOSession session) {
        return new DefaultNHttpClientConnection(
                session,
                createHttpResponseFactory(),
                createByteBufferAllocator(),
                this.params);
    }

    /**
     * Creates an instance of {@link SSLIOSession} decorating the given
     * {@link IOSession}.
     * <p>
     * This method can be overridden in a super class in order to provide
     * a different implementation of SSL I/O session.
     *
     * @param session the underlying I/O session.
     * @param sslcontext the SSL context.
     * @param sslHandler the SSL setup handler.
     * @return newly created SSL I/O session.
     */
    protected SSLIOSession createSSLIOSession(
            final IOSession session,
            final SSLContext sslcontext,
            final SSLSetupHandler sslHandler) {
        return new SSLIOSession(session, sslcontext, sslHandler);
    }

    public void connected(final IOSession session) {

        SSLIOSession sslSession = createSSLIOSession(
                session,
                this.sslcontext,
                this.sslHandler);

        NHttpClientIOTarget conn = createConnection(
                sslSession);

        session.setAttribute(ExecutionContext.HTTP_CONNECTION, conn);
        session.setAttribute(SSL_SESSION, sslSession);

        Object attachment = session.getAttribute(IOSession.ATTACHMENT_KEY);
        this.handler.connected(conn, attachment);

        try {
            sslSession.bind(SSLMode.CLIENT, this.params);
        } catch (SSLException ex) {
            this.handler.exception(conn, ex);
            sslSession.shutdown();
        }
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

    private void ensureNotNull(final SSLIOSession ssliosession) {
        if (ssliosession == null) {
            throw new IllegalStateException("SSL I/O session is null");
        }
    }

    public void inputReady(final IOSession session) {
        NHttpClientIOTarget conn =
            (NHttpClientIOTarget) session.getAttribute(ExecutionContext.HTTP_CONNECTION);
        ensureNotNull(conn);
        SSLIOSession sslSession =
            (SSLIOSession) session.getAttribute(SSL_SESSION);
        ensureNotNull(sslSession);

        try {
            if (sslSession.isAppInputReady()) {
                conn.consumeInput(this.handler);
            }
            sslSession.inboundTransport();
        } catch (IOException ex) {
            this.handler.exception(conn, ex);
            sslSession.shutdown();
        }
    }

    public void outputReady(final IOSession session) {
        NHttpClientIOTarget conn =
            (NHttpClientIOTarget) session.getAttribute(ExecutionContext.HTTP_CONNECTION);
        ensureNotNull(conn);
        SSLIOSession sslSession =
            (SSLIOSession) session.getAttribute(SSL_SESSION);
        ensureNotNull(sslSession);

        try {
            if (sslSession.isAppOutputReady()) {
                conn.produceOutput(this.handler);
            }
            sslSession.outboundTransport();
        } catch (IOException ex) {
            this.handler.exception(conn, ex);
            sslSession.shutdown();
        }
    }

    public void timeout(final IOSession session) {
        NHttpClientIOTarget conn =
            (NHttpClientIOTarget) session.getAttribute(ExecutionContext.HTTP_CONNECTION);
        ensureNotNull(conn);
        SSLIOSession sslSession =
            (SSLIOSession) session.getAttribute(SSL_SESSION);
        ensureNotNull(sslSession);

        this.handler.timeout(conn);
        synchronized (sslSession) {
            if (sslSession.isOutboundDone() && !sslSession.isInboundDone()) {
                // The session failed to terminate cleanly
                sslSession.shutdown();
            }
        }
    }

}
