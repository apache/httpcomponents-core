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
package org.apache.hc.core5.pool;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.apache.hc.core5.http.SocketModalCloseable;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class TestConnPoolLeaseTimeout {

    private static final Timeout TIMEOUT = Timeout.ofSeconds(30);

    static final class DummyConn implements SocketModalCloseable {

        private volatile Timeout socketTimeout;

        @Override
        public Timeout getSocketTimeout() {
            return socketTimeout;
        }

        @Override
        public void setSocketTimeout(final Timeout timeout) {
            this.socketTimeout = timeout;
        }

        @Override
        public void close(final CloseMode closeMode) {
        }

        @Override
        public void close() throws IOException {
        }
    }

    static final class PoolCase {
        final String name;
        final Supplier<ManagedConnPool<String, DummyConn>> supplier;

        PoolCase(final String name, final Supplier<ManagedConnPool<String, DummyConn>> supplier) {
            this.name = name;
            this.supplier = supplier;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    static Stream<PoolCase> pools() {
        return Stream.of(
                new PoolCase("STRICT", () -> new StrictConnPool<>(1, 1)),
                new PoolCase("LAX", () -> new LaxConnPool<>(1)),
                new PoolCase("OFFLOCK", () -> new RouteSegmentedConnPool<>(
                        1,
                        1,
                        TimeValue.NEG_ONE_MILLISECOND,
                        PoolReusePolicy.LIFO,
                        new DefaultDisposalCallback<>()))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("pools")
    @org.junit.jupiter.api.Timeout(60)
    void testLeaseTimeoutDoesNotLeakLeasedEntries(final PoolCase poolCase) throws Exception {
        final ManagedConnPool<String, DummyConn> pool = poolCase.supplier.get();

        final String route = "route-1";
        final Timeout requestTimeout = Timeout.ofMicroseconds(1);

        final int concurrentThreads = 10;
        final CountDownLatch countDownLatch = new CountDownLatch(concurrentThreads);
        final AtomicLong n = new AtomicLong(concurrentThreads * 100);

        final ExecutorService executorService = Executors.newFixedThreadPool(concurrentThreads);
        final AtomicReference<Exception> unexpectedException = new AtomicReference<>();
        try {
            for (int i = 0; i < concurrentThreads; i++) {
                executorService.execute(() -> {
                    try {
                        while (n.decrementAndGet() > 0) {
                            final Future<PoolEntry<String, DummyConn>> f = pool.lease(route, null, requestTimeout, null);
                            try {
                                final PoolEntry<String, DummyConn> entry =
                                        f.get(requestTimeout.getDuration(), requestTimeout.getTimeUnit());
                                pool.release(entry, true);
                            } catch (final InterruptedException ex) {
                                Thread.currentThread().interrupt();
                                unexpectedException.compareAndSet(null, ex);
                            } catch (final TimeoutException ex) {
                                f.cancel(true);
                            } catch (final ExecutionException ex) {
                                f.cancel(true);
                                if (!(ex.getCause() instanceof TimeoutException)) {
                                    unexpectedException.compareAndSet(null, ex);
                                }
                            }
                        }
                    } finally {
                        countDownLatch.countDown();
                    }
                });
            }

            Assertions.assertTrue(countDownLatch.await(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()));
            Assertions.assertTrue(n.get() <= 0);
            Assertions.assertNull(unexpectedException.get());

            final PoolStats stats = pool.getStats(route);
            Assertions.assertEquals(0, stats.getLeased());

        } finally {
            executorService.shutdownNow();
            executorService.awaitTermination(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            pool.close(CloseMode.GRACEFUL);
        }
    }
}