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
import org.apache.http.nio.NHttpClientHandler;
import org.apache.http.nio.NHttpClientIOTarget;
import org.apache.http.nio.NHttpConnectionFactory;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.reactor.ssl.SSLSetupHandler;
import org.apache.http.params.HttpParams;

/**
 * Default {@link IOEventDispatch} implementation that supports both plain (non-encrypted)
 * and SSL encrypted HTTP connections.
 *
 * @since 4.2
 */
@Immutable // provided injected dependencies are immutable
public class DefaultClientIODispatch extends AbstractIODispatch<NHttpClientIOTarget> {

    private final NHttpClientHandler handler;
    private final NHttpConnectionFactory<NHttpClientIOTarget> connFactory;

    /**
     * Creates a new instance of this class to be used for dispatching I/O event
     * notifications to the given protocol handler.
     *
     * @param handler the client protocol handler.
     * @param connFactory HTTP client connection factory.
     */
    public DefaultClientIODispatch(
            final NHttpClientHandler handler,
            final NHttpConnectionFactory<NHttpClientIOTarget> connFactory) {
        super();
        if (handler == null) {
            throw new IllegalArgumentException("HTTP client handler may not be null");
        }
        if (connFactory == null) {
            throw new IllegalArgumentException("HTTP client connection factory may not null");
        }
        this.handler = handler;
        this.connFactory = connFactory;
    }

    public DefaultClientIODispatch(final NHttpClientHandler handler, final HttpParams params) {
        this(handler, new DefaultNHttpClientConnectionFactory(params));
    }

    public DefaultClientIODispatch(
            final NHttpClientHandler handler,
            final SSLContext sslcontext,
            final SSLSetupHandler sslHandler,
            final HttpParams params) {
        this(handler, new SSLNHttpClientConnectionFactory(sslcontext, sslHandler, params));
    }

    public DefaultClientIODispatch(
            final NHttpClientHandler handler,
            final SSLContext sslcontext,
            final HttpParams params) {
        this(handler, sslcontext, null, params);
    }

    @Override
    protected NHttpClientIOTarget createConnection(final IOSession session) {
        return this.connFactory.createConnection(session);
    }

    @Override
    protected void onConnected(final NHttpClientIOTarget conn) {
        Object attachment = conn.getContext().getAttribute(IOSession.ATTACHMENT_KEY);
        this.handler.connected(conn, attachment);
    }

    @Override
    protected void onClosed(final NHttpClientIOTarget conn) {
        this.handler.closed(conn);
    }

    @Override
    protected void onException(final NHttpClientIOTarget conn, IOException ex) {
        this.handler.exception(conn, ex);
    }

    @Override
    protected void onInputReady(final NHttpClientIOTarget conn) {
        conn.consumeInput(this.handler);
    }

    @Override
    protected void onOutputReady(final NHttpClientIOTarget conn) {
        conn.produceOutput(this.handler);
    }

    @Override
    protected void onTimeout(final NHttpClientIOTarget conn) {
        this.handler.timeout(conn);
    }

}
