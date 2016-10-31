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

package org.apache.hc.core5.http.impl.nio.bootstrap;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.channels.SelectionKey;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.concurrent.BasicFuture;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ExceptionListener;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.nio.command.ShutdownCommand;
import org.apache.hc.core5.http.nio.command.ShutdownType;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.IOSessionCallback;
import org.apache.hc.core5.reactor.SessionRequest;
import org.apache.hc.core5.reactor.SessionRequestCallback;
import org.apache.hc.core5.util.Args;

/**
 * @since 5.0
 */
public class HttpAsyncRequester extends AsyncRequester {

    private final IOEventHandlerFactory handlerFactory;

    HttpAsyncRequester(
            final IOEventHandlerFactory handlerFactory,
            final IOReactorConfig ioReactorConfig,
            final ExceptionListener exceptionListener) {
        super(ioReactorConfig, exceptionListener, new IOSessionCallback() {

            @Override
            public void execute(final IOSession session) throws IOException {
                session.getCommandQueue().addFirst(new ShutdownCommand(ShutdownType.GRACEFUL));
                session.setEvent(SelectionKey.OP_WRITE);
            }

        });
        this.handlerFactory = Args.notNull(handlerFactory, "Handler factory");
    }

    public void start() throws IOException {
        execute(handlerFactory);
    }

    public Future<ClientEndpoint> connect(
            final InetSocketAddress address,
            final long timeout,
            final TimeUnit timeUnit,
            final FutureCallback<ClientEndpoint> callback) throws InterruptedException {
        Args.notNull(address, "Address");
        Args.notNull(timeUnit, "Time unit");
        final BasicFuture<ClientEndpoint> future = new BasicFuture<>(callback);
        requestSession(address, timeout, timeUnit, new SessionRequestCallback() {

            @Override
            public void completed(final SessionRequest request) {
                final IOSession session = request.getSession();
                future.completed(new ClientEndpointImpl(session));
            }

            @Override
            public void failed(final SessionRequest request) {
                future.failed(request.getException());
            }

            @Override
            public void timeout(final SessionRequest request) {
                future.failed(new SocketTimeoutException("Connect timeout"));
            }

            @Override
            public void cancelled(final SessionRequest request) {
                future.cancel();
            }
        });
        return future;
    }

    public Future<ClientEndpoint> connect(
            final InetSocketAddress address,
            final long timeout,
            final TimeUnit timeUnit) throws InterruptedException {
        return connect(address, timeout, timeUnit, null);
    }

    public Future<ClientEndpoint> connect(
            final HttpHost host,
            final long timeout,
            final TimeUnit timeUnit) throws InterruptedException {
        Args.notNull(host, "HTTP host");
        final InetSocketAddress address = host.getAddress() != null ?
                new InetSocketAddress(host.getAddress(), host.getPort()) :
                new InetSocketAddress(host.getHostName(), host.getPort());
        return connect(address, timeout, timeUnit);
    }

}
