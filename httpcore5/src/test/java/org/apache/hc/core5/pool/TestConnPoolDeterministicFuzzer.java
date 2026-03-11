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

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class TestConnPoolDeterministicFuzzer {

    static Stream<Arguments> params() {
        final long[] seeds = new long[]{1L, 2L, 3L, 4L};
        final List<Arguments> out = new ArrayList<>();
        for (final PoolConcurrencyPolicy policy : PoolConcurrencyPolicy.values()) {
            for (final long seed : seeds) {
                out.add(Arguments.of(policy, seed));
            }
        }
        return out.stream();
    }

    @ParameterizedTest
    @MethodSource("params")
    void fuzzSingleThreaded(final PoolConcurrencyPolicy policy, final long seed) throws Exception {
        final TestingClock clock = new TestingClock(0L);

        final int defaultMaxPerRoute = 2;
        final int maxTotal = 4;

        final ManagedConnPool<String, PoolTestSupport.DummyConn> pool =
                PoolTestSupport.createPool(policy, defaultMaxPerRoute, maxTotal, clock);

        final Timeout requestTimeout = Timeout.of(0L, TimeUnit.MILLISECONDS);

        final SplittableRandom rnd = new SplittableRandom(seed);

        final List<PoolEntry<String, PoolTestSupport.DummyConn>> leased = new ArrayList<>();
        Future<PoolEntry<String, PoolTestSupport.DummyConn>> pending = null;

        try {
            final String[] routes = new String[]{"r1", "r2", "r3"};
            final Object[] states = new Object[]{null, "s1"};

            final int steps = 10_000;

            Assertions.assertTrue(pool.getMaxTotal() >= 0);
            Assertions.assertTrue(pool.getDefaultMaxPerRoute() >= 0);
            for (final String route : routes) {
                Assertions.assertTrue(pool.getMaxPerRoute(route) >= 0);
            }

            for (int i = 0; i < steps; i++) {

                pending = drainPending(pending, leased);

                final int op = rnd.nextInt(100);

                if (op < 45) {
                    // LEASE
                    final String route = routes[rnd.nextInt(routes.length)];
                    final Object state = states[rnd.nextInt(states.length)];

                    if (pending == null) {
                        final Future<PoolEntry<String, PoolTestSupport.DummyConn>> f =
                                pool.lease(route, state, requestTimeout, null);
                        if (f.isDone()) {
                            final PoolEntry<String, PoolTestSupport.DummyConn> entry = getDone(f);
                            if (entry != null) {
                                ensureConnection(entry);
                                leased.add(entry);
                            }
                        } else {
                            pending = f;
                        }
                    } else {
                        if (rnd.nextInt(8) == 0) {
                            pending.cancel(true);
                            pending = null;
                            validatePendingRequests(pool);
                        }
                    }

                } else if (op < 75) {
                    if (!leased.isEmpty()) {
                        final int idx = rnd.nextInt(leased.size());
                        final PoolEntry<String, PoolTestSupport.DummyConn> entry = leased.remove(idx);
                        final boolean reusable = rnd.nextBoolean();
                        pool.release(entry, reusable);
                        pending = drainPending(pending, leased);
                    }

                } else if (op < 85) {
                    clock.advanceMillis(1L + rnd.nextInt(20));

                } else if (op < 92) {
                    pool.closeIdle(TimeValue.ofMilliseconds(1));

                } else if (op < 97) {
                    pool.closeExpired();

                } else {
                    if (pending != null) {
                        pending.cancel(true);
                        pending = null;
                        validatePendingRequests(pool);
                    }
                }

                // keep stats + invariants cost under control
                if ((i & 31) == 0) {
                    validatePendingRequests(pool);
                    pending = drainPending(pending, leased);
                    assertCoreInvariants(policy, pool, leased, pending, routes);
                }
            }

            // Cleanup
            if (pending != null) {
                pending.cancel(true);
                pending = null;
            }
            validatePendingRequests(pool);

            while (!leased.isEmpty()) {
                final PoolEntry<String, PoolTestSupport.DummyConn> entry = leased.remove(leased.size() - 1);
                pool.release(entry, true);
            }

            validatePendingRequests(pool);
            pending = drainPending(pending, leased);
            assertCoreInvariants(policy, pool, leased, pending, routes);

        } finally {
            pool.close(CloseMode.IMMEDIATE);
        }
    }

    private static void ensureConnection(final PoolEntry<String, PoolTestSupport.DummyConn> entry) {
        if (!entry.hasConnection()) {
            entry.assignConnection(new PoolTestSupport.DummyConn());
        }
    }

    private static PoolEntry<String, PoolTestSupport.DummyConn> getDone(
            final Future<PoolEntry<String, PoolTestSupport.DummyConn>> f) throws Exception {

        if (f.isCancelled()) {
            return null;
        }
        return f.get();
    }

    private static Future<PoolEntry<String, PoolTestSupport.DummyConn>> drainPending(
            final Future<PoolEntry<String, PoolTestSupport.DummyConn>> pending,
            final List<PoolEntry<String, PoolTestSupport.DummyConn>> leased) throws Exception {

        if (pending != null && pending.isDone()) {
            final PoolEntry<String, PoolTestSupport.DummyConn> entry = getDone(pending);
            if (entry != null) {
                ensureConnection(entry);
                leased.add(entry);
            }
            return null;
        }
        return pending;
    }

    private static void validatePendingRequests(final ManagedConnPool<String, PoolTestSupport.DummyConn> pool) {
        if (pool instanceof StrictConnPool) {
            ((StrictConnPool<?, ?>) pool).validatePendingRequests();
        } else if (pool instanceof LaxConnPool) {
            ((LaxConnPool<?, ?>) pool).validatePendingRequests();
        }
    }

    private static void assertCoreInvariants(
            final PoolConcurrencyPolicy policy,
            final ManagedConnPool<String, PoolTestSupport.DummyConn> pool,
            final List<PoolEntry<String, PoolTestSupport.DummyConn>> leased,
            final Future<PoolEntry<String, PoolTestSupport.DummyConn>> pending,
            final String[] routes) {

        final PoolStats totals = pool.getTotalStats();

        Assertions.assertTrue(pool.getMaxTotal() >= 0);
        Assertions.assertTrue(pool.getDefaultMaxPerRoute() >= 0);

        Assertions.assertTrue(totals.getAvailable() >= 0);
        Assertions.assertTrue(totals.getLeased() >= 0);
        Assertions.assertTrue(totals.getPending() >= 0);
        Assertions.assertTrue(totals.getMax() >= 0);

        final long allocated = (long) totals.getAvailable() + (long) totals.getLeased();
        Assertions.assertTrue(allocated <= (long) totals.getMax(), "allocated > max");

        if (policy != PoolConcurrencyPolicy.LAX) {
            Assertions.assertTrue(totals.getLeased() <= pool.getMaxTotal(), "leased > max total");
        }

        Assertions.assertEquals(leased.size(), totals.getLeased(), "leased count mismatch");

        final int expectedPending = pending != null && !pending.isDone() && !pending.isCancelled() ? 1 : 0;
        Assertions.assertEquals(expectedPending, totals.getPending(), "pending count mismatch");

        if (policy != PoolConcurrencyPolicy.LAX) {
            for (final String route : routes) {
                final PoolStats routeStats = pool.getStats(route);
                Assertions.assertTrue(routeStats.getAvailable() >= 0);
                Assertions.assertTrue(routeStats.getLeased() >= 0);
                Assertions.assertTrue(routeStats.getPending() >= 0);
                Assertions.assertTrue(routeStats.getMax() >= 0);
                final long routeAllocated = (long) routeStats.getAvailable() + (long) routeStats.getLeased();
                Assertions.assertTrue(routeAllocated <= (long) routeStats.getMax(), "route allocated > max");
            }
        }
    }

}