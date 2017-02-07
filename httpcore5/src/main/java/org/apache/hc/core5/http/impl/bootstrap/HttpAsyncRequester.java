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

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.concurrent.ComplexFuture;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.http.ExceptionListener;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.nio.command.ShutdownCommand;
import org.apache.hc.core5.http.nio.command.ShutdownType;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.pool.ControlledConnPool;
import org.apache.hc.core5.pool.PoolEntry;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.SessionRequest;
import org.apache.hc.core5.reactor.SessionRequestCallback;
import org.apache.hc.core5.reactor.ssl.TransportSecurityLayer;
import org.apache.hc.core5.util.Args;

/**
 * @since 5.0
 */
public class HttpAsyncRequester extends AsyncRequester {

    private final IOEventHandlerFactory handlerFactory;
    private final ControlledConnPool<HttpHost, ClientSessionEndpoint> connPool;
    private final TlsStrategy tlsStrategy;

    public HttpAsyncRequester(
            final IOReactorConfig ioReactorConfig,
            final IOEventHandlerFactory handlerFactory,
            final ControlledConnPool<HttpHost, ClientSessionEndpoint> connPool,
            final TlsStrategy tlsStrategy,
            final ExceptionListener exceptionListener) {
        super(ioReactorConfig, exceptionListener, new Callback<IOSession>() {

            @Override
            public void execute(final IOSession session) {
                session.addFirst(new ShutdownCommand(ShutdownType.GRACEFUL));
            }

        });
        this.handlerFactory = Args.notNull(handlerFactory, "Handler factory");
        this.connPool = Args.notNull(connPool, "Connection pool");
        this.tlsStrategy = tlsStrategy;
    }

    public void start() throws IOException {
        execute(handlerFactory);
    }

    public Future<PooledClientEndpoint> connect(
            final HttpHost host,
            final long timeout,
            final TimeUnit timeUnit,
            final FutureCallback<PooledClientEndpoint> callback) {
        Args.notNull(host, "Host");
        Args.notNull(timeUnit, "Time unit");
        final ComplexFuture<PooledClientEndpoint> resultFuture = new ComplexFuture<>(callback);
        final Future<PoolEntry<HttpHost, ClientSessionEndpoint>> leaseFuture = connPool.lease(
                host, null, new FutureCallback<PoolEntry<HttpHost, ClientSessionEndpoint>>() {

            @Override
            public void completed(final PoolEntry<HttpHost, ClientSessionEndpoint> poolEntry) {
                final PoolEntryHolder<HttpHost, ClientSessionEndpoint> poolEntryHolder = new PoolEntryHolder<>(
                        connPool,
                        poolEntry,
                        new Callback<ClientSessionEndpoint>() {

                            @Override
                            public void execute(final ClientSessionEndpoint clientEndpoint) {
                                clientEndpoint.shutdown();
                            }

                        });
                final ClientSessionEndpoint clientEndpoint = poolEntry.getConnection();
                if (clientEndpoint != null && !clientEndpoint.isOpen()) {
                    poolEntry.discardConnection();
                }
                if (poolEntry.hasConnection()) {
                    resultFuture.completed(new PooledClientEndpoint(poolEntryHolder));
                } else {
                    final SessionRequest sessionRequest = requestSession(
                            host, timeout, timeUnit,
                            new SessionRequestCallback() {

                        @Override
                        public void completed(final SessionRequest request) {
                            final IOSession session = request.getSession();
                            if (tlsStrategy != null && session instanceof TransportSecurityLayer) {
                                tlsStrategy.upgrade(
                                        (TransportSecurityLayer) session,
                                        host,
                                        session.getLocalAddress(),
                                        session.getRemoteAddress());
                            }
                            poolEntry.assignConnection(new ClientSessionEndpoint(session));
                            resultFuture.completed(new PooledClientEndpoint(poolEntryHolder));
                        }

                        @Override
                        public void failed(final SessionRequest request) {
                            try {
                                resultFuture.failed(request.getException());
                            } finally {
                                poolEntryHolder.abortConnection();
                            }
                        }

                        @Override
                        public void timeout(final SessionRequest request) {
                            try {
                                resultFuture.failed(new SocketTimeoutException("Connect timeout"));
                            } finally {
                                poolEntryHolder.abortConnection();
                            }
                        }

                        @Override
                        public void cancelled(final SessionRequest request) {
                            try {
                                resultFuture.cancel();
                            } finally {
                                poolEntryHolder.abortConnection();
                            }
                        }

                    });
                    resultFuture.setDependency(sessionRequest);
                }
            }

            @Override
            public void failed(final Exception ex) {
                resultFuture.failed(ex);
            }

            @Override
            public void cancelled() {
                resultFuture.cancel();
            }

        });
        resultFuture.setDependency(leaseFuture);
        return resultFuture;
    }

    public Future<PooledClientEndpoint> connect(
            final HttpHost host,
            final long timeout,
            final TimeUnit timeUnit) throws InterruptedException {
        return connect(host, timeout, timeUnit, null);
    }

}
