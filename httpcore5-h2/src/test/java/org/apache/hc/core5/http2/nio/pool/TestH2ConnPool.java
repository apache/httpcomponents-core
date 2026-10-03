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
package org.apache.hc.core5.http2.nio.pool;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.function.Resolver;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.nio.command.ShutdownCommand;
import org.apache.hc.core5.http.nio.command.StaleCheckCommand;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.ConnectionInitiator;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.ssl.TransportSecurityLayer;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TestH2ConnPool {

    @Test
    void testCloseSessionGracefulEnqueuesShutdown() {
        final ConnectionInitiator connectionInitiator = Mockito.mock(ConnectionInitiator.class);
        try (H2ConnPool pool = new H2ConnPool(connectionInitiator, null, null)) {
            final IOSession session = Mockito.mock(IOSession.class);

            pool.closeSession(session, CloseMode.GRACEFUL);

            Mockito.verify(session).enqueue(ShutdownCommand.GRACEFUL, Command.Priority.NORMAL);
        }
    }

    @Test
    void testCloseSessionImmediateCloses() {
        final ConnectionInitiator connectionInitiator = Mockito.mock(ConnectionInitiator.class);
        try (H2ConnPool pool = new H2ConnPool(connectionInitiator, null, null)) {
            final IOSession session = Mockito.mock(IOSession.class);

            pool.closeSession(session, CloseMode.IMMEDIATE);

            Mockito.verify(session).close(CloseMode.IMMEDIATE);
        }
    }

    @Test
    void testValidateSessionClosed() {
        final ConnectionInitiator connectionInitiator = Mockito.mock(ConnectionInitiator.class);
        try (H2ConnPool pool = new H2ConnPool(connectionInitiator, null, null)) {
            final IOSession session = Mockito.mock(IOSession.class);
            Mockito.when(session.isOpen()).thenReturn(false);

            final AtomicReference<Boolean> result = new AtomicReference<>();
            pool.validateSession(session, result::set);

            Assertions.assertEquals(Boolean.FALSE, result.get());
        }
    }

    @Test
    void testValidateSessionEnqueuesStaleCheck() {
        final ConnectionInitiator connectionInitiator = Mockito.mock(ConnectionInitiator.class);
        try (H2ConnPool pool = new H2ConnPool(connectionInitiator, null, null)) {
            pool.setValidateAfterInactivity(TimeValue.ZERO_MILLISECONDS);

            final IOSession session = Mockito.mock(IOSession.class);
            Mockito.when(session.isOpen()).thenReturn(true);
            Mockito.when(session.getLastReadTime()).thenReturn(0L);
            Mockito.when(session.getLastWriteTime()).thenReturn(0L);

            @SuppressWarnings("unchecked")
            final Callback<Boolean> callback = (Callback<Boolean>) Mockito.mock(Callback.class);
            pool.validateSession(session, callback);

            Mockito.verify(session).enqueue(Mockito.any(StaleCheckCommand.class), Mockito.eq(Command.Priority.NORMAL));
            Mockito.verifyNoInteractions(callback);
        }
    }

    @Test
    void testConnectSessionPlain() {
        final ConnectionInitiator connectionInitiator = Mockito.mock(ConnectionInitiator.class);
        final Resolver<HttpHost, InetSocketAddress> resolver = endpoint ->
                new InetSocketAddress("localhost", endpoint.getPort());
        try (H2ConnPool pool = new H2ConnPool(connectionInitiator, resolver, null)) {
            final HttpHost host = new HttpHost(URIScheme.HTTP.id, "localhost", 7443);
            final InetSocketAddress remoteAddress = new InetSocketAddress("localhost", 7443);
            final Timeout connectTimeout = Timeout.ofSeconds(1);
            final IOSession session = Mockito.mock(IOSession.class);

            Mockito.when(connectionInitiator.connect(
                    Mockito.eq(host),
                    Mockito.eq(remoteAddress),
                    Mockito.isNull(),
                    Mockito.eq(connectTimeout),
                    Mockito.isNull(),
                    Mockito.any()))
                    .thenAnswer(invocation -> {
                        final FutureCallback<IOSession> cb = invocation.getArgument(5);
                        cb.completed(session);
                        return CompletableFuture.completedFuture(session);
                    });

            final AtomicReference<IOSession> result = new AtomicReference<>();
            pool.connectSession(host, connectTimeout, new FutureCallback<IOSession>() {

                @Override
                public void completed(final IOSession ioSession) {
                    result.set(ioSession);
                }

                @Override
                public void failed(final Exception ex) {
                }

                @Override
                public void cancelled() {
                }

            });

            Assertions.assertSame(session, result.get());
        }
    }

    @Test
    void testConnectSessionUpgradesTls() {
        final ConnectionInitiator connectionInitiator = Mockito.mock(ConnectionInitiator.class);
        final Resolver<HttpHost, InetSocketAddress> resolver = endpoint ->
                new InetSocketAddress("localhost", endpoint.getPort());
        final TlsStrategy tlsStrategy = Mockito.mock(TlsStrategy.class);
        try (H2ConnPool pool = new H2ConnPool(connectionInitiator, resolver, tlsStrategy)) {
            final HttpHost host = new HttpHost(URIScheme.HTTPS.id, "localhost", 8443);
            final InetSocketAddress remoteAddress = new InetSocketAddress("localhost", 8443);
            final Timeout connectTimeout = Timeout.ofSeconds(1);
            final IOSession session = Mockito.mock(IOSession.class, Mockito.withSettings()
                    .extraInterfaces(TransportSecurityLayer.class));
            final TransportSecurityLayer tlsLayer = (TransportSecurityLayer) session;

            Mockito.when(connectionInitiator.connect(
                    Mockito.eq(host),
                    Mockito.eq(remoteAddress),
                    Mockito.isNull(),
                    Mockito.eq(connectTimeout),
                    Mockito.isNull(),
                    Mockito.any()))
                    .thenAnswer(invocation -> {
                        final FutureCallback<IOSession> cb = invocation.getArgument(5);
                        cb.completed(session);
                        return CompletableFuture.completedFuture(session);
                    });

            Mockito.doAnswer(invocation -> {
                final FutureCallback<TransportSecurityLayer> cb = invocation.getArgument(4);
                if (cb != null) {
                    cb.completed(tlsLayer);
                }
                return null;
            }).when(tlsStrategy).upgrade(
                    Mockito.eq(tlsLayer),
                    Mockito.eq(host),
                    Mockito.isNull(),
                    Mockito.eq(connectTimeout),
                    Mockito.any());

            final AtomicReference<IOSession> result = new AtomicReference<>();
            pool.connectSession(host, connectTimeout, new FutureCallback<IOSession>() {

                @Override
                public void completed(final IOSession ioSession) {
                    result.set(ioSession);
                }

                @Override
                public void failed(final Exception ex) {
                }

                @Override
                public void cancelled() {
                }

            });

            Assertions.assertSame(session, result.get());
            Mockito.verify(tlsStrategy).upgrade(
                    Mockito.eq(tlsLayer),
                    Mockito.eq(host),
                    Mockito.isNull(),
                    Mockito.eq(connectTimeout),
                    Mockito.any());
            Mockito.verify(session).setSocketTimeout(connectTimeout);
        }
    }

}
