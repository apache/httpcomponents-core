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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

final class TestConnPoolConcurrencyStress {

    private static int intProp(final String name, final int def) {
        final String v = System.getProperty(name);
        return v != null ? Integer.parseInt(v) : def;
    }

    private static long longProp(final String name, final long def) {
        final String v = System.getProperty(name);
        return v != null ? Long.parseLong(v) : def;
    }

    @ParameterizedTest
    @EnumSource(PoolTestSupport.PoolType.class)
    @org.junit.jupiter.api.Timeout(value = 180, unit = TimeUnit.SECONDS)
    void stress(final PoolTestSupport.PoolType poolType) throws Exception {
        assumeTrue(Boolean.getBoolean("hc.pool.stress"), "stress disabled (use -Dhc.pool.stress=true)");

        final int threads = intProp("hc.pool.stress.threads", 8);
        final int routeCount = intProp("hc.pool.stress.routes", 10);
        final int seconds = intProp("hc.pool.stress.seconds", 5);
        final long seed = longProp("hc.pool.stress.seed", 1L);

        final ManagedConnPool<String, PoolTestSupport.DummyConn> pool = poolType.createPool(5, 50);
        final List<String> routes = new ArrayList<>(routeCount);
        for (int i = 0; i < routeCount; i++) {
            routes.add("r" + i);
            pool.setMaxPerRoute("r" + i, 5);
        }
        if (poolType.hasHardTotalLimit()) {
            pool.setMaxTotal(50);
        }

        final ExecutorService executor = Executors.newFixedThreadPool(threads);
        final CountDownLatch start = new CountDownLatch(1);

        final long endAt = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(seconds);

        try {
            for (int t = 0; t < threads; t++) {
                final int tid = t;
                executor.submit(() -> {
                    final SplittableRandom rnd = new SplittableRandom(seed + tid);
                    final List<Future<PoolEntry<String, PoolTestSupport.DummyConn>>> pending = new ArrayList<>();
                    final List<PoolEntry<String, PoolTestSupport.DummyConn>> leased = new ArrayList<>();

                    start.await();

                    while (System.currentTimeMillis() < endAt) {
                        for (int i = pending.size() - 1; i >= 0; i--) {
                            final Future<PoolEntry<String, PoolTestSupport.DummyConn>> f = pending.get(i);
                            if (!f.isDone()) {
                                continue;
                            }
                            try {
                                final PoolEntry<String, PoolTestSupport.DummyConn> e = f.get();
                                if (e != null) {
                                    if (!e.hasConnection()) {
                                        e.assignConnection(new PoolTestSupport.DummyConn());
                                    }
                                    leased.add(e);
                                }
                            } catch (final Exception ignore) {
                            } finally {
                                pending.remove(i);
                            }
                        }

                        final int action = rnd.nextInt(8);
                        switch (action) {
                            case 0:
                            case 1:
                            case 2: {
                                final String route = routes.get(rnd.nextInt(routes.size()));
                                final Object state = (rnd.nextInt(5) == 0) ? null : rnd.nextInt(3);
                                pending.add(pool.lease(route, state, Timeout.ofMilliseconds(200), null));
                                break;
                            }
                            case 3: {
                                if (!pending.isEmpty()) {
                                    pending.get(rnd.nextInt(pending.size())).cancel(true);
                                }
                                break;
                            }
                            case 4:
                            case 5: {
                                if (!leased.isEmpty()) {
                                    final int idx = rnd.nextInt(leased.size());
                                    final PoolEntry<String, PoolTestSupport.DummyConn> e = leased.remove(idx);
                                    final boolean reusable = rnd.nextBoolean();
                                    if (!reusable) {
                                        e.discardConnection(CloseMode.IMMEDIATE);
                                    }
                                    pool.release(e, reusable);
                                }
                                break;
                            }
                            case 6: {
                                pool.closeExpired();
                                break;
                            }
                            case 7: {
                                pool.closeIdle(TimeValue.ofMilliseconds(1));
                                break;
                            }
                            default: {
                                break;
                            }
                        }
                    }

                    for (final Future<PoolEntry<String, PoolTestSupport.DummyConn>> f : pending) {
                        f.cancel(true);
                    }
                    for (final PoolEntry<String, PoolTestSupport.DummyConn> e : leased) {
                        pool.release(e, true);
                    }

                    return null;
                });
            }

            start.countDown();

            executor.shutdown();
            assertTrue(executor.awaitTermination(seconds + 30L, TimeUnit.SECONDS), "Executor did not terminate");

            for (int i = 0; i < 3; i++) {
                pool.closeExpired();
                pool.closeIdle(TimeValue.ofMilliseconds(1));
                for (final String r : routes) {
                    final Future<PoolEntry<String, PoolTestSupport.DummyConn>> f =
                            pool.lease(r, null, Timeout.ofMilliseconds(200), null);
                    try {
                        final PoolEntry<String, PoolTestSupport.DummyConn> e = f.get(200, TimeUnit.MILLISECONDS);
                        if (e != null) {
                            if (!e.hasConnection()) {
                                e.assignConnection(new PoolTestSupport.DummyConn());
                            }
                            pool.release(e, true);
                        }
                    } catch (final Exception ex) {
                        f.cancel(true);
                    }
                }
                if (pool.getTotalStats().getLeased() == 0 && pool.getTotalStats().getPending() == 0) {
                    break;
                }
            }

            assertEquals(0, pool.getTotalStats().getLeased(), "Leased leak");
            assertEquals(0, pool.getTotalStats().getPending(), "Stuck pending");

        } finally {
            executor.shutdownNow();
            pool.close(CloseMode.IMMEDIATE);
        }
    }

}