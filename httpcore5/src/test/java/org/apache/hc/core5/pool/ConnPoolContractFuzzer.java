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
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

final class ConnPoolContractFuzzer {

    private static final Timeout REQUEST_TIMEOUT = Timeout.ofSeconds(30);
    private static final TimeValue IDLE_TIME = TimeValue.ofMilliseconds(1);

    private ConnPoolContractFuzzer() {
    }

    private static final class PendingLease {
        private final Object state;
        private final Future<PoolEntry<String, PoolTestSupport.DummyConn>> future;

        private PendingLease(
                final Object state,
                final Future<PoolEntry<String, PoolTestSupport.DummyConn>> future) {
            this.state = state;
            this.future = future;
        }
    }

    static void run(
            final PoolTestSupport.PoolType poolType,
            final ManagedConnPool<String, PoolTestSupport.DummyConn> pool,
            final long seed,
            final int steps,
            final int routeCount) throws Exception {

        final SplittableRandom rnd = new SplittableRandom(seed);

        final List<String> routes = new ArrayList<>(routeCount);
        for (int i = 0; i < routeCount; i++) {
            routes.add("r" + i);
        }

        final List<String> trace = new ArrayList<>(steps + 64);

        final Set<PoolEntry<String, PoolTestSupport.DummyConn>> leasedSet =
                Collections.newSetFromMap(new IdentityHashMap<>());

        final List<PoolEntry<String, PoolTestSupport.DummyConn>> leased = new ArrayList<>();
        final List<PendingLease> pending = new ArrayList<>();

        pool.setDefaultMaxPerRoute(2);
        for (final String route : routes) {
            pool.setMaxPerRoute(route, 2);
        }
        if (poolType.hasHardTotalLimit()) {
            pool.setMaxTotal(Math.max(6, routeCount * 2));
        }

        try {
            for (int step = 0; step < steps; step++) {
                drainDoneFutures(pending, leased, leasedSet);

                if (pending.size() > 80) {
                    final int idx = rnd.nextInt(pending.size());
                    trace.add(step + ": cancel(pending[" + idx + "]) (backpressure)");
                    pending.get(idx).future.cancel(true);
                }

                final int action = rnd.nextInt(12);
                switch (action) {
                    case 0:
                    case 1:
                    case 2: {
                        final String route = routes.get(rnd.nextInt(routes.size()));
                        final Object state = (rnd.nextInt(4) == 0) ? null : rnd.nextInt(3);
                        trace.add(step + ": lease(" + route + ", state=" + state + ")");
                        final Future<PoolEntry<String, PoolTestSupport.DummyConn>> f = pool.lease(route, state, REQUEST_TIMEOUT, null);
                        pending.add(new PendingLease(state, f));
                        break;
                    }
                    case 3: {
                        if (!pending.isEmpty()) {
                            final int idx = rnd.nextInt(pending.size());
                            trace.add(step + ": cancel(pending[" + idx + "])");
                            pending.get(idx).future.cancel(true);
                        } else {
                            trace.add(step + ": cancel(<none>)");
                        }
                        break;
                    }
                    case 4:
                    case 5: {
                        if (!leased.isEmpty()) {
                            final int idx = rnd.nextInt(leased.size());
                            final PoolEntry<String, PoolTestSupport.DummyConn> entry = leased.remove(idx);
                            leasedSet.remove(entry);

                            final boolean reusable = rnd.nextBoolean();
                            trace.add(step + ": release(" + entry.getRoute() + ", reusable=" + reusable + ")");

                            if (!reusable) {
                                entry.discardConnection(CloseMode.IMMEDIATE);
                            }
                            pool.release(entry, reusable);
                        } else {
                            trace.add(step + ": release(<none>)");
                        }
                        break;
                    }
                    case 6: {
                        if (!leased.isEmpty()) {
                            final int idx = rnd.nextInt(leased.size());
                            final PoolEntry<String, PoolTestSupport.DummyConn> entry = leased.get(idx);
                            trace.add(step + ": expire(" + entry.getRoute() + ")");
                            entry.updateExpiry(TimeValue.ofMilliseconds(0));
                        } else {
                            trace.add(step + ": expire(<none>)");
                        }
                        break;
                    }
                    case 7: {
                        final String route = routes.get(rnd.nextInt(routes.size()));
                        final int current = pool.getMaxPerRoute(route);
                        final int next = current + 1 + rnd.nextInt(3);
                        trace.add(step + ": setMaxPerRoute(" + route + ", " + next + ")");
                        pool.setMaxPerRoute(route, next);
                        break;
                    }
                    case 8: {
                        if (poolType.hasHardTotalLimit()) {
                            final int current = pool.getMaxTotal();
                            final int next = (current > 0 ? current : 6) + 1 + rnd.nextInt(5);
                            trace.add(step + ": setMaxTotal(" + next + ")");
                            pool.setMaxTotal(next);
                        } else {
                            trace.add(step + ": setMaxTotal(<ignored by LAX>)");
                        }
                        break;
                    }
                    case 9: {
                        trace.add(step + ": closeExpired()");
                        pool.closeExpired();
                        break;
                    }
                    case 10: {
                        trace.add(step + ": closeIdle()");
                        pool.closeIdle(IDLE_TIME);
                        break;
                    }
                    case 11: {
                        trace.add(step + ": nudge()");
                        nudge(pool, routes);
                        break;
                    }
                    default: {
                        trace.add(step + ": <noop>");
                        break;
                    }
                }

                assertCoreInvariants(pool);

                if (poolType != PoolTestSupport.PoolType.LAX) {
                    assertHardBounds(pool, routes);
                }

                assertRouteTotalsConsistent(pool);
            }

            cleanupAndAssertQuiescent(poolType, pool, routes, pending, leased, leasedSet);

        } catch (final AssertionError ex) {
            fail("Pool=" + poolType + " seed=" + seed
                    + "\nRepro: -Dhc.pool.fuzz.seed=" + seed
                    + "\nTrace(last 80):\n" + tail(trace, 80), ex);
        } finally {
            pool.close(CloseMode.IMMEDIATE);
        }
    }

    private static void drainDoneFutures(
            final List<PendingLease> pending,
            final List<PoolEntry<String, PoolTestSupport.DummyConn>> leased,
            final Set<PoolEntry<String, PoolTestSupport.DummyConn>> leasedSet) throws Exception {

        for (final Iterator<PendingLease> it = pending.iterator(); it.hasNext(); ) {
            final PendingLease p = it.next();
            if (!p.future.isDone()) {
                continue;
            }
            try {
                final PoolEntry<String, PoolTestSupport.DummyConn> entry = p.future.get();
                if (entry != null) {
                    if (!entry.hasConnection()) {
                        entry.assignConnection(new PoolTestSupport.DummyConn());
                    }
                    entry.updateState(p.state);

                    assertTrue(leasedSet.add(entry), "Same entry leased twice concurrently");
                    leased.add(entry);
                }
            } catch (final CancellationException ignore) {
                // expected
            } catch (final ExecutionException ignore) {
                // allowed
            } finally {
                it.remove();
            }
        }
    }

    private static void nudge(
            final ManagedConnPool<String, PoolTestSupport.DummyConn> pool,
            final List<String> routes) {

        for (final String route : routes) {
            final Future<PoolEntry<String, PoolTestSupport.DummyConn>> f =
                    pool.lease(route, null, Timeout.ofMilliseconds(200), null);
            try {
                final PoolEntry<String, PoolTestSupport.DummyConn> entry = f.get(200, TimeUnit.MILLISECONDS);
                if (entry != null) {
                    if (!entry.hasConnection()) {
                        entry.assignConnection(new PoolTestSupport.DummyConn());
                    }
                    pool.release(entry, true);
                }
            } catch (final TimeoutException ex) {
                f.cancel(true);
            } catch (final Exception ignore) {
                // ignore
            }
        }
    }

    /**
     * Adjustment: cleanup must not drop futures that completed concurrently, otherwise we
     * can leave a successfully leased entry unreleased and report a false “leak”.
     */
    private static void cleanupAndAssertQuiescent(
            final PoolTestSupport.PoolType poolType,
            final ManagedConnPool<String, PoolTestSupport.DummyConn> pool,
            final List<String> routes,
            final List<PendingLease> pending,
            final List<PoolEntry<String, PoolTestSupport.DummyConn>> leased,
            final Set<PoolEntry<String, PoolTestSupport.DummyConn>> leasedSet) throws Exception {

        for (final PendingLease p : pending) {
            p.future.cancel(true);
        }

        for (int i = 0; i < 10; i++) {
            drainDoneFutures(pending, leased, leasedSet);

            while (!leased.isEmpty()) {
                final PoolEntry<String, PoolTestSupport.DummyConn> e = leased.remove(leased.size() - 1);
                leasedSet.remove(e);
                pool.release(e, true);
            }

            // Drain any done futures we didn’t observe via drainDoneFutures (race window).
            for (final Iterator<PendingLease> it = pending.iterator(); it.hasNext(); ) {
                final PendingLease p = it.next();
                if (!p.future.isDone()) {
                    continue;
                }
                try {
                    final PoolEntry<String, PoolTestSupport.DummyConn> entry = p.future.get();
                    if (entry != null) {
                        if (!entry.hasConnection()) {
                            entry.assignConnection(new PoolTestSupport.DummyConn());
                        }
                        entry.updateState(p.state);
                        pool.release(entry, true);
                    }
                } catch (final Exception ignore) {
                    // cancelled / failed
                } finally {
                    it.remove();
                }
            }

            // Encourage flushing of cancelled waiters without using impl-specific APIs.
            nudge(pool, routes);
            pool.closeExpired();
            pool.closeIdle(IDLE_TIME);

            final PoolStats total = pool.getTotalStats();
            if (pending.isEmpty() && total.getLeased() == 0 && total.getPending() == 0) {
                break;
            }
        }

        final PoolStats total = pool.getTotalStats();
        assertEquals(0, total.getLeased(), "Leased entry leak detected");
        assertEquals(0, total.getPending(), "Stuck waiters detected");

        assertCoreInvariants(pool);
        assertRouteTotalsConsistent(pool);

        if (poolType != PoolTestSupport.PoolType.LAX) {
            assertHardBounds(pool, routes);
        }
    }

    private static void assertCoreInvariants(final ManagedConnPool<String, PoolTestSupport.DummyConn> pool) {
        final PoolStats total = pool.getTotalStats();
        assertTrue(total.getLeased() >= 0, "total.leased");
        assertTrue(total.getPending() >= 0, "total.pending");
        assertTrue(total.getAvailable() >= 0, "total.available");

        for (final String route : pool.getRoutes()) {
            final PoolStats rs = pool.getStats(route);
            assertTrue(rs.getLeased() >= 0, "route.leased");
            assertTrue(rs.getPending() >= 0, "route.pending");
            assertTrue(rs.getAvailable() >= 0, "route.available");
        }
    }

    private static void assertHardBounds(
            final ManagedConnPool<String, PoolTestSupport.DummyConn> pool,
            final List<String> knownRoutes) {

        final PoolStats total = pool.getTotalStats();
        final int maxTotal = pool.getMaxTotal();
        if (maxTotal > 0) {
            assertTrue(total.getLeased() + total.getAvailable() <= maxTotal, "total allocated > maxTotal");
        }

        for (final String route : knownRoutes) {
            final PoolStats rs = pool.getStats(route);
            final int maxRoute = pool.getMaxPerRoute(route);
            if (maxRoute > 0) {
                assertTrue(rs.getLeased() + rs.getAvailable() <= maxRoute, "route allocated > maxPerRoute");
            }
        }
    }

    private static void assertRouteTotalsConsistent(final ManagedConnPool<String, PoolTestSupport.DummyConn> pool) {
        int leased = 0;
        int pending = 0;
        int available = 0;

        for (final String route : pool.getRoutes()) {
            final PoolStats rs = pool.getStats(route);
            leased += rs.getLeased();
            pending += rs.getPending();
            available += rs.getAvailable();
        }

        final PoolStats total = pool.getTotalStats();
        assertEquals(total.getLeased(), leased, "total.leased mismatch");
        assertEquals(total.getPending(), pending, "total.pending mismatch");
        assertEquals(total.getAvailable(), available, "total.available mismatch");
    }

    private static String tail(final List<String> trace, final int n) {
        final int from = Math.max(0, trace.size() - n);
        final StringBuilder b = new StringBuilder();
        for (int i = from; i < trace.size(); i++) {
            b.append(trace.get(i)).append('\n');
        }
        return b.toString();
    }

}