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
import org.apache.hc.core5.http.nio.NHttpConnectionFactory;
import org.apache.hc.core5.http.nio.NHttpServerEventHandler;
import org.apache.hc.core5.reactor.AbstractIOEventHandler;
import org.apache.hc.core5.reactor.IOEventHandler;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.ssl.SSLSetupHandler;
import org.apache.hc.core5.util.Args;

/**
 * Factory for {@link DefaultHttpServerIOEventHandler}.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE_CONDITIONAL)
public class DefaultHttpServerIOEventHandlerFactory implements IOEventHandlerFactory {

    private final NHttpServerEventHandler handler;
    private final NHttpConnectionFactory<? extends DefaultNHttpServerConnection> connFactory;

    public DefaultHttpServerIOEventHandlerFactory(
            final NHttpServerEventHandler handler,
            final NHttpConnectionFactory<? extends DefaultNHttpServerConnection> connFactory) {
        super();
        this.handler = Args.notNull(handler, "HTTP client handler");
        this.connFactory = Args.notNull(connFactory, "HTTP server connection factory");
    }

    public DefaultHttpServerIOEventHandlerFactory(final NHttpServerEventHandler handler, final ConnectionConfig config) {
        this(handler, new DefaultNHttpServerConnectionFactory(config));
    }

    public DefaultHttpServerIOEventHandlerFactory(
            final NHttpServerEventHandler handler,
            final SSLContext sslcontext,
            final SSLSetupHandler sslHandler,
            final ConnectionConfig config) {
        this(handler, new SSLNHttpServerConnectionFactory(sslcontext, sslHandler, config));
    }

    public DefaultHttpServerIOEventHandlerFactory(
            final NHttpServerEventHandler handler,
            final SSLContext sslcontext,
            final ConnectionConfig config) {
        this(handler, new SSLNHttpServerConnectionFactory(sslcontext, null, config));
    }

    @Override
    public IOEventHandler createHandler(final IOSession ioSession) {
        final DefaultNHttpServerConnection connection = this.connFactory.createConnection(ioSession);
        ioSession.setAttribute(AbstractIOEventHandler.CONNECTION_KEY, connection);
        return new DefaultHttpServerIOEventHandler(this.handler);
    }
}
