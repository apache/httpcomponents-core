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

import org.apache.http.annotation.Immutable;
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
 *
 * @since 4.2
 */
@SuppressWarnings("deprecation")
@Immutable // provided injected dependencies are immutable
public class DefaultHttpClientIODispatch
                    extends AbstractIODispatch<DefaultNHttpClientConnection> {

    private final NHttpClientEventHandler handler;
    private final NHttpConnectionFactory<DefaultNHttpClientConnection> connFactory;

    /**
     * Creates a new instance of this class to be used for dispatching I/O event
     * notifications to the given protocol handler.
     *
     * @param handler the client protocol handler.
     * @param connFactory HTTP client connection factory.
     */
    public DefaultHttpClientIODispatch(
            final NHttpClientEventHandler handler,
            final NHttpConnectionFactory<DefaultNHttpClientConnection> connFactory) {
        super();
        this.handler = Args.notNull(handler, "HTTP client handler");
        this.connFactory = Args.notNull(connFactory, "HTTP client connection factory");
    }

    /**
     * @deprecated (4.3) use {@link DefaultHttpClientIODispatch#DefaultHttpClientIODispatch(
     *  NHttpClientEventHandler, ConnectionConfig)}
     */
    @Deprecated
    public DefaultHttpClientIODispatch(
            final NHttpClientEventHandler handler,
            final HttpParams params) {
        this(handler, new DefaultNHttpClientConnectionFactory(params));
    }

    /**
     * @deprecated (4.3) use {@link DefaultHttpClientIODispatch#DefaultHttpClientIODispatch(
     *  NHttpClientEventHandler, SSLContext, SSLSetupHandler, ConnectionConfig)}
     */
    @Deprecated
    public DefaultHttpClientIODispatch(
            final NHttpClientEventHandler handler,
            final SSLContext sslcontext,
            final SSLSetupHandler sslHandler,
            final HttpParams params) {
        this(handler, new SSLNHttpClientConnectionFactory(sslcontext, sslHandler, params));
    }

    /**
     * @deprecated (4.3) use {@link DefaultHttpClientIODispatch#DefaultHttpClientIODispatch(
     *   NHttpClientEventHandler, SSLContext, ConnectionConfig)}
     */
    @Deprecated
    public DefaultHttpClientIODispatch(
            final NHttpClientEventHandler handler,
            final SSLContext sslcontext,
            final HttpParams params) {
        this(handler, sslcontext, null, params);
    }

    /**
     * @since 4.3
     */
    public DefaultHttpClientIODispatch(final NHttpClientEventHandler handler, final ConnectionConfig config) {
        this(handler, new DefaultNHttpClientConnectionFactory(config));
    }

    /**
     * @since 4.3
     */
    public DefaultHttpClientIODispatch(
            final NHttpClientEventHandler handler,
            final SSLContext sslcontext,
            final SSLSetupHandler sslHandler,
            final ConnectionConfig config) {
        this(handler, new SSLNHttpClientConnectionFactory(sslcontext, sslHandler, config));
    }

    /**
     * @since 4.3
     */
    public DefaultHttpClientIODispatch(
            final NHttpClientEventHandler handler,
            final SSLContext sslcontext,
            final ConnectionConfig config) {
        this(handler, new SSLNHttpClientConnectionFactory(sslcontext, null, config));
    }

    @Override
    protected DefaultNHttpClientConnection createConnection(final IOSession session) {
        return this.connFactory.createConnection(session);
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
