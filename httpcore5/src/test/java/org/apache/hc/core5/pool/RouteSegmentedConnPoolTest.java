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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.io.ModalCloseable;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Test;

public class RouteSegmentedConnPoolTest {

    private static <R, C extends ModalCloseable> RouteSegmentedConnPool<R, C> newPool(
            final int defPerRoute, final int maxTotal, final TimeValue ttl, final PoolReusePolicy reuse,
            final DisposalCallback<C> disposal) {
        return new RouteSegmentedConnPool<>(defPerRoute, maxTotal, ttl, reuse, disposal);
    }

    @Test
    void basicLeaseReleaseAndHandoff() throws Exception {
        final DisposalCallback<FakeConnection> disposal = FakeConnection::close;
        final RouteSegmentedConnPool<String, FakeConnection> pool =
                newPool(2, 2, TimeValue.NEG_ONE_MILLISECOND, PoolReusePolicy.LIFO, disposal);

        final PoolEntry<String, FakeConnection> e1 = pool.lease("r1", null, Timeout.ofSeconds(1), null).get(1, TimeUnit.SECONDS);
        assertNotNull(e1);
        assertEquals("r1", e1.getRoute());
        assertFalse(e1.hasConnection());
        e1.assignConnection(new FakeConnection());
        e1.updateState("A");
        e1.updateExpiry(TimeValue.ofSeconds(30));
        pool.release(e1, true);

        final Future<PoolEntry<String, FakeConnection>> f2 =
                pool.lease("r1", "A", Timeout.ofSeconds(1), null);
        final PoolEntry<String, FakeConnection> e2 = f2.get(1, TimeUnit.SECONDS);
        assertSame(e1, e2, "Should receive same entry via direct hand-off");
        pool.release(e2, true);
        pool.close(CloseMode.IMMEDIATE);
    }

    @Test
    void perRouteAndTotalLimits() throws Exception {
        final DisposalCallback<FakeConnection> disposal = FakeConnection::close;
        final RouteSegmentedConnPool<String, FakeConnection> pool =
                newPool(1, 2, TimeValue.NEG_ONE_MILLISECOND, PoolReusePolicy.LIFO, disposal);

        final PoolEntry<String, FakeConnection> r1a = pool.lease("r1", null, Timeout.ofSeconds(1), null).get(1, TimeUnit.SECONDS);
        final PoolEntry<String, FakeConnection> r2a = pool.lease("r2", null, Timeout.ofSeconds(1), null).get(1, TimeUnit.SECONDS);

        final Future<PoolEntry<String, FakeConnection>> blocked = pool.lease("r1", null, Timeout.ofMilliseconds(150), null);
        final ExecutionException ex = assertThrows(
                ExecutionException.class,
                () -> blocked.get(400, TimeUnit.MILLISECONDS));
        assertInstanceOf(TimeoutException.class, ex.getCause());
        assertEquals("Lease timed out", ex.getCause().getMessage());

        r1a.assignConnection(new FakeConnection());
        r1a.updateExpiry(TimeValue.ofSeconds(5));
        pool.release(r1a, true);

        final PoolEntry<String, FakeConnection> r1b =
                pool.lease("r1", null, Timeout.ofSeconds(1), null).get(1, TimeUnit.SECONDS);
        assertNotNull(r1b);
        pool.release(r2a, false); // drop
        pool.release(r1b, false);
        pool.close(CloseMode.IMMEDIATE);
    }

    @Test
    void stateCompatibilityNullMatchesAnything() throws Exception {
        final RouteSegmentedConnPool<String, FakeConnection> pool =
                newPool(1, 1, TimeValue.NEG_ONE_MILLISECOND, PoolReusePolicy.LIFO, FakeConnection::close);

        final PoolEntry<String, FakeConnection> e = pool.lease("r", null, Timeout.ofSeconds(1), null).get(1, TimeUnit.SECONDS);
        e.assignConnection(new FakeConnection());
        e.updateState("X");
        e.updateExpiry(TimeValue.ofSeconds(30));
        pool.release(e, true);

        // waiter with null state must match
        final PoolEntry<String, FakeConnection> got =
                pool.lease("r", null, Timeout.ofSeconds(1), null).get(1, TimeUnit.SECONDS);
        assertSame(e, got);
        pool.release(got, false);
        pool.close(CloseMode.IMMEDIATE);
    }

    @Test
    void closeIdleRemovesStaleAvailable() throws Exception {
        final RouteSegmentedConnPool<String, FakeConnection> pool =
                newPool(2, 2, TimeValue.NEG_ONE_MILLISECOND, PoolReusePolicy.LIFO, FakeConnection::close);

        final PoolEntry<String, FakeConnection> e = pool.lease("r", null, Timeout.ofSeconds(1), null).get(1, TimeUnit.SECONDS);
        e.assignConnection(new FakeConnection());
        e.updateExpiry(TimeValue.ofSeconds(30));
        pool.release(e, true);

        // sleep to make it idle
        Thread.sleep(120);
        pool.closeIdle(TimeValue.ofMilliseconds(50));

        final PoolStats stats = pool.getStats("r");
        assertEquals(0, stats.getAvailable());
        pool.close(CloseMode.IMMEDIATE);
    }

    @Test
    void closeExpiredHonorsEntryExpiryOrTtl() throws Exception {
        // TTL = 100ms, so entries become past-ttl quickly
        final RouteSegmentedConnPool<String, FakeConnection> pool =
                newPool(1, 1, TimeValue.ofMilliseconds(100), PoolReusePolicy.LIFO, FakeConnection::close);

        final PoolEntry<String, FakeConnection> e = pool.lease("r", null, Timeout.ofSeconds(1), null).get(1, TimeUnit.SECONDS);
        e.assignConnection(new FakeConnection());
        // keep alive "far", TTL will still kill it
        e.updateExpiry(TimeValue.ofSeconds(10));
        pool.release(e, true);

        Thread.sleep(150);
        pool.closeExpired();

        final PoolStats stats = pool.getStats("r");
        assertEquals(0, stats.getAvailable(), "Expired/TTL entry should be gone");
        pool.close(CloseMode.IMMEDIATE);
    }

    @Test
    void waiterTimesOutAndIsFailed() throws Exception {
        final RouteSegmentedConnPool<String, FakeConnection> pool =
                newPool(1, 1, TimeValue.NEG_ONE_MILLISECOND, PoolReusePolicy.LIFO, FakeConnection::close);

        // Occupy single slot and don't release
        final PoolEntry<String, FakeConnection> e = pool.lease("r", null, Timeout.ofSeconds(1), null).get(1, TimeUnit.SECONDS);

        final Future<PoolEntry<String, FakeConnection>> waiter =
                pool.lease("r", null, Timeout.ofMilliseconds(150), null);

        final ExecutionException ex = assertThrows(
                ExecutionException.class,
                () -> waiter.get(500, TimeUnit.MILLISECONDS));
        assertInstanceOf(TimeoutException.class, ex.getCause());
        assertEquals("Lease timed out", ex.getCause().getMessage());
        // cleanup
        pool.release(e, false);
        pool.close(CloseMode.IMMEDIATE);
    }

    @Test
    void poolCloseCancelsWaitersAndDrainsAvailable() throws Exception {
        final RouteSegmentedConnPool<String, FakeConnection> pool =
                newPool(1, 1, TimeValue.NEG_ONE_MILLISECOND, PoolReusePolicy.LIFO, FakeConnection::close);

        // Consume the only slot so the next lease becomes a waiter
        final Future<PoolEntry<String, FakeConnection>> first = pool.lease("r", null, Timeout.ofSeconds(5), null);
        first.get(); // allocated immediately, not released

        // Now this one queues as a waiter
        final Future<PoolEntry<String, FakeConnection>> waiter =
                pool.lease("r", null, Timeout.ofSeconds(5), null);

        pool.close(CloseMode.IMMEDIATE);

        final ExecutionException ex = assertThrows(ExecutionException.class, waiter::get);
        assertInstanceOf(TimeoutException.class, ex.getCause());
        assertEquals("Pool closed", ex.getCause().getMessage());
    }


    @Test
    void reusePolicyLifoVsFifoIsObservable() throws Exception {
        final RouteSegmentedConnPool<String, FakeConnection> lifo =
                newPool(2, 2, TimeValue.NEG_ONE_MILLISECOND, PoolReusePolicy.LIFO, FakeConnection::close);

        final PoolEntry<String, FakeConnection> a = lifo.lease("r", null, Timeout.ofSeconds(1), null).get(1, TimeUnit.SECONDS);
        final PoolEntry<String, FakeConnection> b = lifo.lease("r", null, Timeout.ofSeconds(1), null).get(1, TimeUnit.SECONDS);
        a.assignConnection(new FakeConnection());
        a.updateExpiry(TimeValue.ofSeconds(10));
        lifo.release(a, true);
        b.assignConnection(new FakeConnection());
        b.updateExpiry(TimeValue.ofSeconds(10));
        lifo.release(b, true);

        final PoolEntry<String, FakeConnection> firstLifo =
                lifo.lease("r", null, Timeout.ofSeconds(1), null).get(1, TimeUnit.SECONDS);
        assertSame(b, firstLifo, "LIFO should return last released");
        lifo.release(firstLifo, false);
        lifo.close(CloseMode.IMMEDIATE);

        final RouteSegmentedConnPool<String, FakeConnection> fifo =
                newPool(2, 2, TimeValue.NEG_ONE_MILLISECOND, PoolReusePolicy.FIFO, FakeConnection::close);
        final PoolEntry<String, FakeConnection> a2 = fifo.lease("r", null, Timeout.ofSeconds(1), null).get(1, TimeUnit.SECONDS);
        final PoolEntry<String, FakeConnection> b2 = fifo.lease("r", null, Timeout.ofSeconds(1), null).get(1, TimeUnit.SECONDS);
        a2.assignConnection(new FakeConnection());
        a2.updateExpiry(TimeValue.ofSeconds(10));
        fifo.release(a2, true);
        b2.assignConnection(new FakeConnection());
        b2.updateExpiry(TimeValue.ofSeconds(10));
        fifo.release(b2, true);

        final PoolEntry<String, FakeConnection> firstFifo =
                fifo.lease("r", null, Timeout.ofSeconds(1), null).get(1, TimeUnit.SECONDS);
        assertSame(a2, firstFifo, "FIFO should return first released");
        fifo.release(firstFifo, false);
        fifo.close(CloseMode.IMMEDIATE);
    }

    @Test
    void disposalIsCalledOnDiscard() throws Exception {
        final List<FakeConnection> closed = new ArrayList<>();
        final DisposalCallback<FakeConnection> disposal = (c, m) -> {
            c.close(m);
            closed.add(c);
        };
        final RouteSegmentedConnPool<String, FakeConnection> pool =
                newPool(1, 1, TimeValue.NEG_ONE_MILLISECOND, PoolReusePolicy.LIFO, disposal);

        final PoolEntry<String, FakeConnection> e = pool.lease("r", null, Timeout.ofSeconds(1), null).get(1, TimeUnit.SECONDS);
        final FakeConnection conn = new FakeConnection();
        e.assignConnection(conn);
        pool.release(e, false);
        assertEquals(1, closed.size());
        assertEquals(1, closed.get(0).closeCount());
        pool.close(CloseMode.IMMEDIATE);
    }

    @Test
    void slowDisposalDoesNotBlockOtherRoutes() throws Exception {
        final DisposalCallback<FakeConnection> disposal = FakeConnection::close;
        final RouteSegmentedConnPool<String, FakeConnection> pool =
                newPool(2, 2, TimeValue.NEG_ONE_MILLISECOND, PoolReusePolicy.LIFO, disposal);

        final PoolEntry<String, FakeConnection> e1 = pool.lease("r1", null, Timeout.ofSeconds(1), null).get(1, TimeUnit.SECONDS);
        e1.assignConnection(new FakeConnection(600));
        final long startDiscard = System.nanoTime();
        pool.release(e1, false);

        final long t0 = System.nanoTime();
        final PoolEntry<String, FakeConnection> e2 = pool.lease("r2", null, Timeout.ofSeconds(1), null).get(1, TimeUnit.SECONDS);
        final long tLeaseMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
        assertTrue(tLeaseMs < 200, "Other route lease blocked by disposal: " + tLeaseMs + "ms");

        pool.release(e2, false);
        final long discardMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startDiscard);
        assertTrue(discardMs >= 600, "Discard should reflect slow close path");

        pool.close(CloseMode.IMMEDIATE);
    }

    @Test
    void getRoutesCoversAllocatedAvailableAndWaiters() throws Exception {
        final RouteSegmentedConnPool<String, FakeConnection> pool =
                newPool(1, 1, TimeValue.NEG_ONE_MILLISECOND, PoolReusePolicy.LIFO, FakeConnection::close);

        assertTrue(pool.getRoutes().isEmpty(), "Initially there should be no routes");

        final PoolEntry<String, FakeConnection> a =
                pool.lease("rA", null, Timeout.ofSeconds(1), null).get(1, TimeUnit.SECONDS);
        assertEquals(new HashSet<String>(Collections.singletonList("rA")), pool.getRoutes(),
                "rA must be listed because it is leased (allocated > 0)");

        a.assignConnection(new FakeConnection());
        a.updateExpiry(TimeValue.ofSeconds(30));
        pool.release(a, true);
        assertEquals(new HashSet<>(Collections.singletonList("rA")), pool.getRoutes(),
                "rA must be listed because it has AVAILABLE entries");

        final Future<PoolEntry<String, FakeConnection>> waiterB =
                pool.lease("rB", null, Timeout.ofMilliseconds(300), null); // enqueues immediately
        final Set<String> routesNow = pool.getRoutes();
        assertTrue(routesNow.contains("rA") && routesNow.contains("rB"),
                "Both rA (available) and rB (waiter) must be listed");

        final PoolEntry<String, FakeConnection> a2 =
                pool.lease("rA", null, Timeout.ofSeconds(1), null).get(1, TimeUnit.SECONDS);
        pool.release(a2, false); // discard
        final Set<String> afterDropA = pool.getRoutes();
        assertFalse(afterDropA.contains("rA"), "rA segment should be cleaned up");
        assertTrue(afterDropA.contains("rB"), "rB (waiter) should remain listed");

        final ExecutionException ex = assertThrows(
                ExecutionException.class,
                () -> waiterB.get(600, TimeUnit.MILLISECONDS));
        assertInstanceOf(TimeoutException.class, ex.getCause());
        assertEquals("Lease timed out", ex.getCause().getMessage());

        // Final cleanup: after close everything is cleared
        pool.close(CloseMode.IMMEDIATE);
        assertTrue(pool.getRoutes().isEmpty(), "All routes must be gone after close()");
    }


}
