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

import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestFactory;
import org.apache.http.annotation.Contract;
import org.apache.http.annotation.ThreadingBehavior;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.impl.nio.codecs.DefaultHttpRequestParserFactory;
import org.apache.http.impl.nio.reactor.AbstractIODispatch;
import org.apache.http.nio.NHttpConnectionFactory;
import org.apache.http.nio.NHttpMessageParserFactory;
import org.apache.http.nio.NHttpServerEventHandler;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.reactor.ssl.SSLSetupHandler;
import org.apache.http.params.HttpParams;
import org.apache.http.util.Args;

/**
 * Default {@link org.apache.http.nio.reactor.IOEventDispatch} implementation
 * that supports both plain (non-encrypted) and SSL encrypted server side HTTP
 * connections.
 * @param <H> an implementation of {@link NHttpServerEventHandler}.
 *
 * @since 4.2
 */
@SuppressWarnings("deprecation")
@Contract(threading = ThreadingBehavior.IMMUTABLE_CONDITIONAL)
public class DefaultHttpServerIODispatch<H extends NHttpServerEventHandler>
                    extends AbstractIODispatch<DefaultNHttpServerConnection> {

    /**
     * Creates a new instance of this class to be used for dispatching I/O event
     * notifications to the given protocol handler.
     *
     * @param handler the server protocol handler.
     * @param sslContext an SSLContext or null (for a plain text connection.)
     * @param config a connection configuration
     * @return a new instance
     * @since 4.4.7
     */
    public static <T extends NHttpServerEventHandler> DefaultHttpServerIODispatch<T> create(final T handler,
            final SSLContext sslContext,
            final ConnectionConfig config) {
        return sslContext == null ? new DefaultHttpServerIODispatch<T>(handler, config)
                : new DefaultHttpServerIODispatch<T>(handler, sslContext, config);
    }

    /**
     * Creates a new instance of this class to be used for dispatching I/O event
     * notifications to the given protocol handler.
     *
     * @param eventHandler the server protocol handler.
     * @param sslContext an SSLContext or null (for a plain text connection.)
     * @param config a connection configuration
     * @param httpRequestFactory the request factory used by this object to generate {@link HttpRequest} instances.
     * @return a new instance
     * @since 4.4.10
     */
    public static <T extends NHttpServerEventHandler> DefaultHttpServerIODispatch<T> create(final T eventHandler,
            final SSLContext sslContext, final ConnectionConfig config, final HttpRequestFactory httpRequestFactory) {
        final NHttpMessageParserFactory<HttpRequest> httpRequestParserFactory = new DefaultHttpRequestParserFactory(
                null, httpRequestFactory);
        // @formatter:off
        return sslContext == null
                ? new DefaultHttpServerIODispatch<T>(eventHandler,
                        new DefaultNHttpServerConnectionFactory(null, httpRequestParserFactory, null, config))
                : new DefaultHttpServerIODispatch<T>(eventHandler,
                        new SSLNHttpServerConnectionFactory(sslContext, null, httpRequestParserFactory, null, config));
        // @formatter:om
    }

    /**
     * Creates a new instance of this class to be used for dispatching I/O event
     * notifications to the given protocol handler.
     *
     * @param handler the server protocol handler.
     * @param sslContext an SSLContext or null (for a plain text connection.)
     * @param sslHandler customizes various aspects of the TLS/SSL protocol.
     * @param config a connection configuration
     * @return a new instance
     * @since 4.4.7
     */
    public static <T extends NHttpServerEventHandler> DefaultHttpServerIODispatch<T> create(final T handler,
            final SSLContext sslContext,
            final SSLSetupHandler sslHandler,
            final ConnectionConfig config) {
        return sslContext == null ? new DefaultHttpServerIODispatch<T>(handler, config)
                : new DefaultHttpServerIODispatch<T>(handler, sslContext, sslHandler, config);
    }

    private final H handler;
    private final NHttpConnectionFactory<? extends DefaultNHttpServerConnection> connectionFactory;

    public DefaultHttpServerIODispatch(
            final H handler,
            final NHttpConnectionFactory<? extends DefaultNHttpServerConnection> connFactory) {
        super();
        this.handler = Args.notNull(handler, "HTTP server handler");
        this.connectionFactory = Args.notNull(connFactory, "HTTP server connection factory");
    }

    /**
     * @deprecated (4.3) use {@link DefaultHttpServerIODispatch#DefaultHttpServerIODispatch(
     *   NHttpServerEventHandler, ConnectionConfig)}
     */
    @Deprecated
    public DefaultHttpServerIODispatch(
            final H handler,
            final HttpParams params) {
        this(handler, new DefaultNHttpServerConnectionFactory(params));
    }

    /**
     * @deprecated (4.3) use {@link DefaultHttpServerIODispatch#DefaultHttpServerIODispatch(
     *   NHttpServerEventHandler, SSLContext, SSLSetupHandler, ConnectionConfig)}
     */
    @Deprecated
    public DefaultHttpServerIODispatch(
            final H handler,
            final SSLContext sslContext,
            final SSLSetupHandler sslHandler,
            final HttpParams params) {
        this(handler, new SSLNHttpServerConnectionFactory(sslContext, sslHandler, params));
    }

    /**
     * @deprecated (4.3) use {@link DefaultHttpServerIODispatch#DefaultHttpServerIODispatch(
     *   NHttpServerEventHandler, SSLContext, ConnectionConfig)}
     */
    @Deprecated
    public DefaultHttpServerIODispatch(
            final H handler,
            final SSLContext sslContext,
            final HttpParams params) {
        this(handler, sslContext, null, params);
    }

    /**
     * @since 4.3
     */
    public DefaultHttpServerIODispatch(final H handler, final ConnectionConfig config) {
        this(handler, new DefaultNHttpServerConnectionFactory(config));
    }

    /**
     * @since 4.3
     */
    public DefaultHttpServerIODispatch(
            final H handler,
            final SSLContext sslContext,
            final SSLSetupHandler sslHandler,
            final ConnectionConfig config) {
        this(handler, new SSLNHttpServerConnectionFactory(sslContext, sslHandler, config));
    }

    /**
     * @since 4.3
     */
    public DefaultHttpServerIODispatch(
            final H handler,
            final SSLContext sslContext,
            final ConnectionConfig config) {
        this(handler, new SSLNHttpServerConnectionFactory(sslContext, null, config));
    }

    @Override
    protected DefaultNHttpServerConnection createConnection(final IOSession session) {
        return this.connectionFactory.createConnection(session);
    }

    /**
     * Gets the connection factory used to construct this dispatch.
     *
     * @return the connection factory used to construct this dispatch.
     * @since 4.4.9
     */
    public NHttpConnectionFactory<? extends DefaultNHttpServerConnection> getConnectionFactory() {
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
    protected void onConnected(final DefaultNHttpServerConnection conn) {
        try {
            this.handler.connected(conn);
        } catch (final Exception ex) {
            this.handler.exception(conn, ex);
        }
    }

    @Override
    protected void onClosed(final DefaultNHttpServerConnection conn) {
        this.handler.closed(conn);
    }

    @Override
    protected void onException(final DefaultNHttpServerConnection conn, final IOException ex) {
        this.handler.exception(conn, ex);
    }

    @Override
    protected void onInputReady(final DefaultNHttpServerConnection conn) {
        conn.consumeInput(this.handler);
    }

    @Override
    protected void onOutputReady(final DefaultNHttpServerConnection conn) {
        conn.produceOutput(this.handler);
    }

    @Override
    protected void onTimeout(final DefaultNHttpServerConnection conn) {
        try {
            this.handler.timeout(conn);
        } catch (final Exception ex) {
            this.handler.exception(conn, ex);
        }
    }

}
