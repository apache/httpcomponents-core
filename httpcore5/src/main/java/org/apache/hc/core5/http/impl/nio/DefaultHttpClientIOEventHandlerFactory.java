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

package org.apache.hc.core5.http.impl.nio;

import javax.net.ssl.SSLContext;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.config.ConnectionConfig;
import org.apache.hc.core5.http.nio.NHttpClientEventHandler;
import org.apache.hc.core5.http.nio.NHttpConnectionFactory;
import org.apache.hc.core5.reactor.AbstractIOEventHandler;
import org.apache.hc.core5.reactor.IOEventHandler;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.ssl.SSLSetupHandler;
import org.apache.hc.core5.util.Args;

/**
 * Factory for {@link DefaultHttpClientIOEventHandler}.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE_CONDITIONAL)
public class DefaultHttpClientIOEventHandlerFactory implements IOEventHandlerFactory {

    private final NHttpClientEventHandler handler;
    private final NHttpConnectionFactory<DefaultNHttpClientConnection> connFactory;

    /**
     * Creates a new instance of this class to be used for dispatching I/O event
     * notifications to the given protocol handler.
     *
     * @param handler the client protocol handler.
     * @param connFactory HTTP client connection factory.
     */
    public DefaultHttpClientIOEventHandlerFactory(
            final NHttpClientEventHandler handler,
            final NHttpConnectionFactory<DefaultNHttpClientConnection> connFactory) {
        super();
        this.handler = Args.notNull(handler, "HTTP client handler");
        this.connFactory = Args.notNull(connFactory, "HTTP client connection factory");
    }

    public DefaultHttpClientIOEventHandlerFactory(final NHttpClientEventHandler handler, final ConnectionConfig config) {
        this(handler, new DefaultNHttpClientConnectionFactory(config));
    }

    public DefaultHttpClientIOEventHandlerFactory(
            final NHttpClientEventHandler handler,
            final SSLContext sslcontext,
            final SSLSetupHandler sslHandler,
            final ConnectionConfig config) {
        this(handler, new SSLNHttpClientConnectionFactory(sslcontext, sslHandler, config));
    }

    public DefaultHttpClientIOEventHandlerFactory(
            final NHttpClientEventHandler handler,
            final SSLContext sslcontext,
            final ConnectionConfig config) {
        this(handler, new SSLNHttpClientConnectionFactory(sslcontext, null, config));
    }

    @Override
    public IOEventHandler createHandler(final IOSession ioSession) {
        final DefaultNHttpClientConnection connection = this.connFactory.createConnection(ioSession);
        ioSession.setAttribute(AbstractIOEventHandler.CONNECTION_KEY, connection);
        return new DefaultHttpClientIOEventHandler(this.handler);
    }
}
