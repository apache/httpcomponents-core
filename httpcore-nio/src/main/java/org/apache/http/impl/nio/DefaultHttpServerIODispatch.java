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
import org.apache.http.nio.NHttpConnectionFactory;
import org.apache.http.nio.NHttpServerEventHandler;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.reactor.ssl.SSLSetupHandler;
import org.apache.http.params.HttpParams;
import org.apache.http.util.Args;

/**
 * Default {@link org.apache.http.nio.reactor.IOEventDispatch} implementation
 * that supports both plain (non-encrypted) and SSL encrypted server side HTTP
 * connections.
 *
 * @since 4.2
 */
@SuppressWarnings("deprecation")
@Immutable // provided injected dependencies are immutable
public class DefaultHttpServerIODispatch
                    extends AbstractIODispatch<DefaultNHttpServerConnection> {

    private final NHttpServerEventHandler handler;
    private final NHttpConnectionFactory<? extends DefaultNHttpServerConnection> connFactory;

    public DefaultHttpServerIODispatch(
            final NHttpServerEventHandler handler,
            final NHttpConnectionFactory<? extends DefaultNHttpServerConnection> connFactory) {
        super();
        this.handler = Args.notNull(handler, "HTTP client handler");
        this.connFactory = Args.notNull(connFactory, "HTTP server connection factory");
    }

    /**
     * @deprecated (4.3) use {@link DefaultHttpServerIODispatch#DefaultHttpServerIODispatch(
     *   NHttpServerEventHandler, ConnectionConfig)}
     */
    @Deprecated
    public DefaultHttpServerIODispatch(
            final NHttpServerEventHandler handler,
            final HttpParams params) {
        this(handler, new DefaultNHttpServerConnectionFactory(params));
    }

    /**
     * @deprecated (4.3) use {@link DefaultHttpServerIODispatch#DefaultHttpServerIODispatch(
     *   NHttpServerEventHandler, SSLContext, SSLSetupHandler, ConnectionConfig)}
     */
    @Deprecated
    public DefaultHttpServerIODispatch(
            final NHttpServerEventHandler handler,
            final SSLContext sslcontext,
            final SSLSetupHandler sslHandler,
            final HttpParams params) {
        this(handler, new SSLNHttpServerConnectionFactory(sslcontext, sslHandler, params));
    }

    /**
     * @deprecated (4.3) use {@link DefaultHttpServerIODispatch#DefaultHttpServerIODispatch(
     *   NHttpServerEventHandler, SSLContext, ConnectionConfig)}
     */
    @Deprecated
    public DefaultHttpServerIODispatch(
            final NHttpServerEventHandler handler,
            final SSLContext sslcontext,
            final HttpParams params) {
        this(handler, sslcontext, null, params);
    }

    /**
     * @since 4.3
     */
    public DefaultHttpServerIODispatch(final NHttpServerEventHandler handler, final ConnectionConfig config) {
        this(handler, new DefaultNHttpServerConnectionFactory(config));
    }

    /**
     * @since 4.3
     */
    public DefaultHttpServerIODispatch(
            final NHttpServerEventHandler handler,
            final SSLContext sslcontext,
            final SSLSetupHandler sslHandler,
            final ConnectionConfig config) {
        this(handler, new SSLNHttpServerConnectionFactory(sslcontext, sslHandler, config));
    }

    /**
     * @since 4.3
     */
    public DefaultHttpServerIODispatch(
            final NHttpServerEventHandler handler,
            final SSLContext sslcontext,
            final ConnectionConfig config) {
        this(handler, new SSLNHttpServerConnectionFactory(sslcontext, null, config));
    }

    @Override
    protected DefaultNHttpServerConnection createConnection(final IOSession session) {
        return this.connFactory.createConnection(session);
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
