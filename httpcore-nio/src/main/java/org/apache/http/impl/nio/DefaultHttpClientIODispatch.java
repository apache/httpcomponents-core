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

import javax.net.ssl.SSLContext;

import org.apache.http.annotation.Contract;
import org.apache.http.annotation.ThreadingBehavior;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.impl.nio.reactor.AbstractIODispatch;
import org.apache.http.nio.NHttpClientEventHandler;
import org.apache.http.nio.NHttpConnectionFactory;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.reactor.ssl.SSLSetupHandler;
import org.apache.http.params.HttpParams;
import org.apache.http.util.Args;

/**
 * Default {@link org.apache.http.nio.reactor.IOEventDispatch} implementation
 * that supports both plain (non-encrypted) and SSL encrypted client side HTTP
 * connections.
 * @param <H> an implementation of {@link NHttpClientEventHandler}.
 *
 * @since 4.2
 */
@SuppressWarnings("deprecation")
@Contract(threading = ThreadingBehavior.IMMUTABLE_CONDITIONAL)
public class DefaultHttpClientIODispatch<H extends NHttpClientEventHandler>
                    extends AbstractIODispatch<DefaultNHttpClientConnection> {

    /**
     * Creates a new instance of this class to be used for dispatching I/O event
     * notifications to the given protocol handler.
     *
     * @param handler the client protocol handler.
     * @param sslContext an SSLContext or null (for a plain text connection.)
     * @param config a connection configuration
     * @return a new instance
     * @since 4.4.7
     */
    public static <T extends NHttpClientEventHandler> DefaultHttpClientIODispatch<T> create(final T handler,
            final SSLContext sslContext,
            final ConnectionConfig config) {
        return sslContext == null ? new DefaultHttpClientIODispatch<T>(handler, config)
                : new DefaultHttpClientIODispatch<T>(handler, sslContext, config);
    }

    /**
     * Creates a new instance of this class to be used for dispatching I/O event
     * notifications to the given protocol handler.
     *
     * @param handler the client protocol handler.
     * @param sslContext an SSLContext or null (for a plain text connection.)
     * @param sslHandler customizes various aspects of the TLS/SSL protocol.
     * @param config a connection configuration
     * @return a new instance
     * @since 4.4.7
     */
    public static <T extends NHttpClientEventHandler> DefaultHttpClientIODispatch<T> create(final T handler,
            final SSLContext sslContext,
            final SSLSetupHandler sslHandler,
            final ConnectionConfig config) {
        return sslContext == null ? new DefaultHttpClientIODispatch<T>(handler, config)
                : new DefaultHttpClientIODispatch<T>(handler, sslContext, sslHandler, config);
    }

    private final H handler;
    private final NHttpConnectionFactory<? extends DefaultNHttpClientConnection> connectionFactory;

    /**
     * Creates a new instance of this class to be used for dispatching I/O event
     * notifications to the given protocol handler.
     *
     * @param handler the client protocol handler.
     * @param connFactory HTTP client connection factory.
     */
    public DefaultHttpClientIODispatch(
            final H handler,
            final NHttpConnectionFactory<? extends DefaultNHttpClientConnection> connFactory) {
        super();
        this.handler = Args.notNull(handler, "HTTP client handler");
        this.connectionFactory = Args.notNull(connFactory, "HTTP client connection factory");
    }

    /**
     * @deprecated (4.3) use {@link DefaultHttpClientIODispatch#DefaultHttpClientIODispatch(
     *  NHttpClientEventHandler, ConnectionConfig)}
     */
    @Deprecated
    public DefaultHttpClientIODispatch(
            final H handler,
            final HttpParams params) {
        this(handler, new DefaultNHttpClientConnectionFactory(params));
    }

    /**
     * @deprecated (4.3) use {@link DefaultHttpClientIODispatch#DefaultHttpClientIODispatch(
     *  NHttpClientEventHandler, SSLContext, SSLSetupHandler, ConnectionConfig)}
     */
    @Deprecated
    public DefaultHttpClientIODispatch(
            final H handler,
            final SSLContext sslContext,
            final SSLSetupHandler sslHandler,
            final HttpParams params) {
        this(handler, new SSLNHttpClientConnectionFactory(sslContext, sslHandler, params));
    }

    /**
     * @deprecated (4.3) use {@link DefaultHttpClientIODispatch#DefaultHttpClientIODispatch(
     *   NHttpClientEventHandler, SSLContext, ConnectionConfig)}
     */
    @Deprecated
    public DefaultHttpClientIODispatch(
            final H handler,
            final SSLContext sslContext,
            final HttpParams params) {
        this(handler, sslContext, null, params);
    }

    /**
     * @since 4.3
     */
    public DefaultHttpClientIODispatch(final H handler, final ConnectionConfig config) {
        this(handler, new DefaultNHttpClientConnectionFactory(config));
    }

    /**
     * @since 4.3
     */
    public DefaultHttpClientIODispatch(
            final H handler,
            final SSLContext sslContext,
            final SSLSetupHandler sslHandler,
            final ConnectionConfig config) {
        this(handler, new SSLNHttpClientConnectionFactory(sslContext, sslHandler, config));
    }

    /**
     * @since 4.3
     */
    public DefaultHttpClientIODispatch(
            final H handler,
            final SSLContext sslContext,
            final ConnectionConfig config) {
        this(handler, new SSLNHttpClientConnectionFactory(sslContext, null, config));
    }

    @Override
    protected DefaultNHttpClientConnection createConnection(final IOSession session) {
        return this.connectionFactory.createConnection(session);
    }

    /**
     * Gets the connection factory used to construct this dispatch.
     *
     * @return the connection factory used to construct this dispatch.
     * @since 4.4.9
     */
    public NHttpConnectionFactory<? extends DefaultNHttpClientConnection> getConnectionFactory() {
        return connectionFactory;
    }

    /**
     * Gets the handler used to construct this dispatch.
     *
     * @return the handler used to construct this dispatch.
     * @since 4.4.9
     */
    public H getHandler() {
        return handler;
    }

    @Override
    protected void onConnected(final DefaultNHttpClientConnection conn) {
        final Object attachment = conn.getContext().getAttribute(IOSession.ATTACHMENT_KEY);
        try {
            this.handler.connected(conn, attachment);
        } catch (final Exception ex) {
            this.handler.exception(conn, ex);
        }
    }

    @Override
    protected void onClosed(final DefaultNHttpClientConnection conn) {
        this.handler.closed(conn);
    }

    @Override
    protected void onException(final DefaultNHttpClientConnection conn, final IOException ex) {
        this.handler.exception(conn, ex);
    }

    @Override
    protected void onInputReady(final DefaultNHttpClientConnection conn) {
        conn.consumeInput(this.handler);
    }

    @Override
    protected void onOutputReady(final DefaultNHttpClientConnection conn) {
        conn.produceOutput(this.handler);
    }

    @Override
    protected void onTimeout(final DefaultNHttpClientConnection conn) {
        try {
            this.handler.timeout(conn);
        } catch (final Exception ex) {
            this.handler.exception(conn, ex);
        }
    }

}
