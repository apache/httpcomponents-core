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
package org.apache.hc.core5.testing.pool;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.impl.bootstrap.AsyncRequesterBootstrap;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.nio.AsyncClientEndpoint;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.pool.PoolStats;
import org.apache.hc.core5.testing.classic.ClassicTestServer;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

final class TestConnPoolAsyncRequesterIT {

    private static final TimeValue WAIT = TimeValue.ofSeconds(2);

    private static final class ServerResources {

        private final ClassicTestServer server;
        private final HttpHost target;
        private final CountDownLatch blockArrived;
        private final CountDownLatch blockRelease;

        private ServerResources(
                final ClassicTestServer server,
                final HttpHost target,
                final CountDownLatch blockArrived,
                final CountDownLatch blockRelease) {
            this.server = server;
            this.target = target;
            this.blockArrived = blockArrived;
            this.blockRelease = blockRelease;
        }

        private void releaseBlocked() {
            blockRelease.countDown();
        }
    }

    private static ServerResources startServer() throws IOException {
        final ClassicTestServer server = new ClassicTestServer(SocketConfig.DEFAULT);

        final CountDownLatch blockArrived = new CountDownLatch(1);
        final CountDownLatch blockRelease = new CountDownLatch(1);

        server.register("/ok", (request, response, context) -> {
            response.setCode(HttpStatus.SC_OK);
            response.setEntity(new StringEntity("OK", ContentType.TEXT_PLAIN));
        });

        server.register("/block", (request, response, context) -> {
            blockArrived.countDown();
            try {
                blockRelease.await(10, TimeUnit.SECONDS);
            } catch (final InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            response.setCode(HttpStatus.SC_OK);
            response.setEntity(new StringEntity("OK", ContentType.TEXT_PLAIN));
        });

        server.start();

        final HttpHost target = new HttpHost(
                "http",
                server.getInetAddress().getHostAddress(),
                server.getPort());

        return new ServerResources(server, target, blockArrived, blockRelease);
    }

    private static HttpAsyncRequester createRequester(final PoolConcurrencyPolicy policy) {
        final HttpAsyncRequester requester = AsyncRequesterBootstrap.bootstrap()
                .setPoolConcurrencyPolicy(policy)
                .setDefaultMaxPerRoute(1)
                .setMaxTotal(1)
                .setTimeToLive(Timeout.ofSeconds(30))
                .create();
        requester.start();
        return requester;
    }

    private static boolean awaitPending(
            final HttpAsyncRequester requester,
            final HttpHost route,
            final int expectedPending,
            final TimeValue maxWait) throws InterruptedException {

        final long deadline = System.currentTimeMillis() + maxWait.toMilliseconds();
        while (System.currentTimeMillis() < deadline) {
            final PoolStats stats = requester.getStats(route);
            if (stats.getPending() >= expectedPending) {
                return true;
            }
            Thread.sleep(10);
        }
        return false;
    }

    private static boolean awaitQuiescent(
            final HttpAsyncRequester requester,
            final HttpHost route,
            final TimeValue maxWait) throws InterruptedException {

        final long deadline = System.currentTimeMillis() + maxWait.toMilliseconds();
        while (System.currentTimeMillis() < deadline) {
            final PoolStats total = requester.getTotalStats();
            final PoolStats stats = requester.getStats(route);
            if (total.getLeased() == 0 && total.getPending() == 0
                    && stats.getLeased() == 0 && stats.getPending() == 0) {
                return true;
            }
            Thread.sleep(10);
        }
        return false;
    }

    @ParameterizedTest
    @EnumSource(PoolConcurrencyPolicy.class)
    @org.junit.jupiter.api.Timeout(value = 30, unit = TimeUnit.SECONDS)
    void cancelPendingConnectMustNotLeakOrBlockNextConnect(final PoolConcurrencyPolicy policy) throws Exception {
        final ServerResources srv = startServer();
        final HttpAsyncRequester requester = createRequester(policy);

        try {
            final Future<AsyncClientEndpoint> f1 = requester.connect(srv.target, Timeout.ofSeconds(2));
            final AsyncClientEndpoint ep1 = f1.get(2, TimeUnit.SECONDS);

            final Future<AsyncClientEndpoint> f2 = requester.connect(srv.target, Timeout.ofSeconds(30));
            assertTrue(awaitPending(requester, srv.target, 1, WAIT), "Second connect did not become pending");
            assertTrue(f2.cancel(true), "Cancel failed");

            ep1.releaseAndReuse();

            final AsyncClientEndpoint ep3 = requester.connect(srv.target, Timeout.ofSeconds(2)).get(2, TimeUnit.SECONDS);
            ep3.releaseAndReuse();

            assertTrue(awaitQuiescent(requester, srv.target, WAIT), "Pool did not quiesce");
        } finally {
            requester.close(CloseMode.IMMEDIATE);
            srv.server.shutdown(CloseMode.IMMEDIATE);
        }
    }

    @ParameterizedTest
    @EnumSource(PoolConcurrencyPolicy.class)
    @org.junit.jupiter.api.Timeout(value = 30, unit = TimeUnit.SECONDS)
    void connectTimeoutWhilePendingMustNotLeak(final PoolConcurrencyPolicy policy) throws Exception {
        final ServerResources srv = startServer();
        final HttpAsyncRequester requester = createRequester(policy);

        try {
            final AsyncClientEndpoint ep1 = requester.connect(srv.target, Timeout.ofSeconds(2)).get(2, TimeUnit.SECONDS);

            final Future<AsyncClientEndpoint> f2 = requester.connect(srv.target, Timeout.ofMilliseconds(200));
            assertTrue(awaitPending(requester, srv.target, 1, WAIT), "Second connect did not become pending");

            // Ensure the pending request's deadline is definitely expired before we trigger pool servicing.
            Thread.sleep(500);

            // Trigger servicing of pending connects.
            ep1.releaseAndReuse();

            try {
                final AsyncClientEndpoint ep2 = f2.get(2, TimeUnit.SECONDS);
                if (ep2 != null) {
                    ep2.releaseAndDiscard();
                }
                fail("Expected pending connect to fail due to request timeout");
            } catch (final ExecutionException ignore) {
                // expected: deadline timeout / connect request timeout
            }

            // Pool must still be usable
            final AsyncClientEndpoint ep3 = requester.connect(srv.target, Timeout.ofSeconds(2)).get(2, TimeUnit.SECONDS);
            ep3.releaseAndReuse();

            assertTrue(awaitQuiescent(requester, srv.target, WAIT), "Pool did not quiesce");

        } finally {
            requester.close(CloseMode.IMMEDIATE);
            srv.server.shutdown(CloseMode.IMMEDIATE);
        }
    }

    @ParameterizedTest
    @EnumSource(PoolConcurrencyPolicy.class)
    @org.junit.jupiter.api.Timeout(value = 30, unit = TimeUnit.SECONDS)
    void closeImmediateMidFlightMustNotHang(final PoolConcurrencyPolicy policy) throws Exception {
        final ServerResources srv = startServer();
        final HttpAsyncRequester requester = createRequester(policy);

        try {
            final Future<Message<HttpResponse, String>> f = requester.execute(
                    srv.target,
                    new BasicRequestProducer(Method.GET, srv.target, "/block"),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()),
                    Timeout.ofSeconds(30),
                    null);

            assertTrue(srv.blockArrived.await(5, TimeUnit.SECONDS), "Server did not receive /block");

            requester.close(CloseMode.IMMEDIATE);
            srv.releaseBlocked();

            try {
                f.get(5, TimeUnit.SECONDS);
            } catch (final Exception ignore) {
                // Close mid-flight may abort; we only assert: no hang.
            }
        } finally {
            srv.releaseBlocked();
            requester.close(CloseMode.IMMEDIATE);
            srv.server.shutdown(CloseMode.IMMEDIATE);
        }
    }

}