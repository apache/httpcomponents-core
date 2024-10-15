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

package org.apache.hc.core5.testing.nio;

import java.net.InetSocketAddress;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.hc.core5.concurrent.CountDownLatchFutureCallback;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.impl.routing.RequestRouter;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2MultiplexingRequester;
import org.apache.hc.core5.http2.nio.command.PingCommand;
import org.apache.hc.core5.http2.nio.pool.H2ConnPool;
import org.apache.hc.core5.http2.nio.support.BasicPingHandler;
import org.apache.hc.core5.http2.ssl.H2ClientTlsStrategy;
import org.apache.hc.core5.http2.ssl.H2ServerTlsStrategy;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.apache.hc.core5.testing.SSLTestContexts;
import org.apache.hc.core5.testing.extension.nio.H2AsyncServerResource;
import org.apache.hc.core5.testing.extension.nio.H2MultiplexingRequesterResource;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class H2ConnPoolTest {

    private static final Timeout TIMEOUT = Timeout.ofSeconds(30);

    private final AtomicLong clientConnCount;
    @RegisterExtension
    private final H2AsyncServerResource serverResource;
    @RegisterExtension
    private final H2MultiplexingRequesterResource clientResource;

    public H2ConnPoolTest() throws Exception {
        this.serverResource = new H2AsyncServerResource();
        this.serverResource.configure(bootstrap -> bootstrap
                .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_2)
                .setTlsStrategy(new H2ServerTlsStrategy(SSLTestContexts.createServerSSLContext()))
                .setIOReactorConfig(
                        IOReactorConfig.custom()
                                .setSoTimeout(TIMEOUT)
                                .build())
                .setRequestRouter(RequestRouter.<Supplier<AsyncServerExchangeHandler>>builder()
                        .addRoute(RequestRouter.LOCAL_AUTHORITY, "*", () -> new EchoHandler(2048))
                        .resolveAuthority(RequestRouter.LOCAL_AUTHORITY_RESOLVER)
                        .build())
        );

        this.clientConnCount = new AtomicLong();
        this.clientResource = new H2MultiplexingRequesterResource();
        this.clientResource.configure(bootstrap -> bootstrap
                .setTlsStrategy(new H2ClientTlsStrategy(SSLTestContexts.createClientSSLContext()))
                .setIOReactorConfig(IOReactorConfig.custom()
                        .setSoTimeout(TIMEOUT)
                        .build())
                .setIOSessionListener(new LoggingIOSessionListener() {

                    @Override
                    public void connected(final IOSession session) {
                        clientConnCount.incrementAndGet();
                        super.connected(session);
                    }

                })
        );
    }

    @BeforeEach
    void resetCounts() {
        clientConnCount.set(0);
    }

    @Test
    void testManyGetSession() throws Exception {
        final int n = 200;

        final HttpAsyncServer server = serverResource.start();
        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(0), URIScheme.HTTP);
        final ListenerEndpoint listener = future.get();
        final InetSocketAddress address = (InetSocketAddress) listener.getAddress();
        final HttpHost target = new HttpHost(URIScheme.HTTP.id, "localhost", address.getPort());

        final H2MultiplexingRequester requester = clientResource.start();
        final H2ConnPool connPool = requester.getConnPool();
        final CountDownLatchFutureCallback<IOSession> latch = new CountDownLatchFutureCallback<IOSession>(n) {

            @Override
            public void completed(final IOSession session) {
                session.enqueue(new PingCommand(new BasicPingHandler(
                        result -> countDown()
                        )), Command.Priority.IMMEDIATE);
            }

        };
        for (int i = 0; i < n; i++) {
            connPool.getSession(target, TIMEOUT, latch);
        }
        Assertions.assertTrue(latch.await(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()));

        requester.initiateShutdown();
        requester.awaitShutdown(TIMEOUT);

        Assertions.assertEquals(1, clientConnCount.get());
    }

    @Test
    void testManyGetSessionFailures() throws Exception {
        final int n = 200;

        final HttpHost target = new HttpHost(URIScheme.HTTP.id, "pampa.invalid", 8888);

        final H2MultiplexingRequester requester = clientResource.start();
        final H2ConnPool connPool = requester.getConnPool();
        final CountDownLatchFutureCallback<IOSession> latch = new CountDownLatchFutureCallback<>(n);

        for (int i = 0; i < n; i++) {
            connPool.getSession(target, TIMEOUT, latch);
        }

        requester.initiateShutdown();
        requester.awaitShutdown(TIMEOUT);

        Assertions.assertEquals(0, clientConnCount.get());
    }

}
