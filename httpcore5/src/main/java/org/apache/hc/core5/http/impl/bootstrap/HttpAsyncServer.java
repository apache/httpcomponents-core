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
package org.apache.hc.core5.http.impl.bootstrap;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.Future;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.function.Decorator;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.reactor.EndpointParameters;
import org.apache.hc.core5.http.nio.command.ShutdownCommand;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.IOSessionListener;
import org.apache.hc.core5.reactor.ListenerEndpoint;

/**
 * HTTP/1.1 server side message exchange handler.
 *
 * @since 5.0
 */
public class HttpAsyncServer extends AsyncServer {

    private final String canonicalName;

    /**
     * Use {@link AsyncServerBootstrap} to create instances of this class.
     *
     * @since 5.1
     */
    @Internal
    public HttpAsyncServer(
            final IOEventHandlerFactory eventHandlerFactory,
            final IOReactorConfig ioReactorConfig,
            final Decorator<IOSession> ioSessionDecorator,
            final Callback<Exception> exceptionCallback,
            final IOSessionListener sessionListener,
            final String canonicalName) {
        super(eventHandlerFactory, ioReactorConfig, ioSessionDecorator, exceptionCallback, sessionListener,
                        ShutdownCommand.GRACEFUL_NORMAL_CALLBACK);
        this.canonicalName = canonicalName;
    }

    /**
     * Use {@link AsyncServerBootstrap} to create instances of this class.
     */
    @Internal
    public HttpAsyncServer(
            final IOEventHandlerFactory eventHandlerFactory,
            final IOReactorConfig ioReactorConfig,
            final Decorator<IOSession> ioSessionDecorator,
            final Callback<Exception> exceptionCallback,
            final IOSessionListener sessionListener) {
        this(eventHandlerFactory, ioReactorConfig, ioSessionDecorator, exceptionCallback, sessionListener, null);
    }

    /**
     * @since 5.1
     */
    public Future<ListenerEndpoint> listen(
            final SocketAddress address,
            final URIScheme scheme,
            final Object attachment,
            final FutureCallback<ListenerEndpoint> callback) {
        final InetSocketAddress inetSocketAddress = (InetSocketAddress) address;
        final EndpointParameters parameters = new EndpointParameters(
                scheme.id,
                canonicalName != null ? canonicalName : "localhost",
                inetSocketAddress.getPort(),
                attachment);
        return super.listen(address, parameters, callback);
    }

    /**
     * @since 5.1
     */
    public Future<ListenerEndpoint> listen(
            final SocketAddress address,
            final URIScheme scheme,
            final FutureCallback<ListenerEndpoint> callback) {
        return listen(address, scheme, null, callback);
    }

    /**
     * @since 5.1
     */
    public Future<ListenerEndpoint> listen(final SocketAddress address, final URIScheme scheme) {
        return listen(address, scheme, null, null);
    }

    /**
     * @deprecated Use {@link #listen(SocketAddress, URIScheme, FutureCallback)}
     */
    @Deprecated
    @Override
    public Future<ListenerEndpoint> listen(final SocketAddress address, final FutureCallback<ListenerEndpoint> callback) {
        return super.listen(address, callback);
    }

    /**
     * @deprecated Use {@link #listen(SocketAddress, URIScheme)}
     */
    @Deprecated
    @Override
    public Future<ListenerEndpoint> listen(final SocketAddress address) {
        return super.listen(address);
    }

}
