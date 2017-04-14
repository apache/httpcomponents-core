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

import org.apache.hc.core5.concurrent.ComplexFuture;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.http.ExceptionListener;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.nio.AsyncClientEndpoint;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.command.ExecutionCommand;
import org.apache.hc.core5.http.nio.command.ShutdownCommand;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.ShutdownType;
import org.apache.hc.core5.pool.ControlledConnPool;
import org.apache.hc.core5.pool.PoolEntry;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOReactorException;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.SessionRequest;
import org.apache.hc.core5.reactor.SessionRequestCallback;
import org.apache.hc.core5.reactor.ssl.TransportSecurityLayer;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;

/**
 * @since 5.0
 */
public class HttpAsyncRequester extends AsyncRequester {

    private final ControlledConnPool<HttpHost, IOSession> connPool;
    private final TlsStrategy tlsStrategy;

    public HttpAsyncRequester(
            final IOReactorConfig ioReactorConfig,
            final IOEventHandlerFactory eventHandlerFactory,
            final ControlledConnPool<HttpHost, IOSession> connPool,
            final TlsStrategy tlsStrategy,
            final ExceptionListener exceptionListener) throws IOReactorException {
        super(eventHandlerFactory, ioReactorConfig, exceptionListener, new Callback<IOSession>() {

            @Override
            public void execute(final IOSession session) {
                session.addFirst(new ShutdownCommand(ShutdownType.GRACEFUL));
            }

        });
        this.connPool = Args.notNull(connPool, "Connection pool");
        this.tlsStrategy = tlsStrategy;
    }

    public void start() throws IOException {
        execute();
    }

    public Future<AsyncClientEndpoint> connect(
            final HttpHost host,
            final TimeValue timeout,
            final FutureCallback<AsyncClientEndpoint> callback) {
        Args.notNull(host, "Host");
        Args.notNull(timeout, "Timeout");
        final ComplexFuture<AsyncClientEndpoint> resultFuture = new ComplexFuture<>(callback);
        final Future<PoolEntry<HttpHost, IOSession>> leaseFuture = connPool.lease(
                host, null, new FutureCallback<PoolEntry<HttpHost, IOSession>>() {

            @Override
            public void completed(final PoolEntry<HttpHost, IOSession> poolEntry) {
                final PoolEntryHolder<HttpHost, IOSession> poolEntryHolder = new PoolEntryHolder<>(connPool, poolEntry);
                final IOSession ioSession = poolEntry.getConnection();
                if (ioSession != null && ioSession.isClosed()) {
                    poolEntry.discardConnection(ShutdownType.IMMEDIATE);
                }
                if (poolEntry.hasConnection()) {
                    resultFuture.completed(new InternalAsyncClientEndpoint(poolEntryHolder));
                } else {
                    final SessionRequest sessionRequest = requestSession(host, timeout, new SessionRequestCallback() {

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
                            poolEntry.assignConnection(session);
                            resultFuture.completed(new InternalAsyncClientEndpoint(poolEntryHolder));
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

    public Future<AsyncClientEndpoint> connect(final HttpHost host, final TimeValue timeout) throws InterruptedException {
        return connect(host, timeout, null);
    }

    private static class InternalAsyncClientEndpoint extends AsyncClientEndpoint {

        final PoolEntryHolder<HttpHost, IOSession> poolEntryHolder;

        InternalAsyncClientEndpoint(final PoolEntryHolder<HttpHost, IOSession> poolEntryHolder) {
            this.poolEntryHolder = poolEntryHolder;
        }

        @Override
        public void execute(final AsyncClientExchangeHandler exchangeHandler, final HttpContext context) {
            final IOSession connection = poolEntryHolder.getConnection();
            if (connection == null) {
                throw new IllegalStateException("Endpoint has already been released");
            }
            connection.addLast(new ExecutionCommand(exchangeHandler, context));
        }

        @Override
        public void releaseAndReuse() {
            poolEntryHolder.releaseConnection();
        }

        @Override
        public void releaseAndDiscard() {
            poolEntryHolder.abortConnection();
        }

    }

}
