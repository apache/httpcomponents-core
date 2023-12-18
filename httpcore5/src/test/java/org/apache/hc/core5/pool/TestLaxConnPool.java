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

import java.util.Collections;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

public class TestLaxConnPool {

    @Test
    public void testEmptyPool() throws Exception {
        try (final LaxConnPool<String, HttpConnection> pool = new LaxConnPool<>(2)) {
            final PoolStats totals = pool.getTotalStats();
            Assertions.assertEquals(0, totals.getAvailable());
            Assertions.assertEquals(0, totals.getLeased());
            Assertions.assertEquals(0, totals.getPending());
            Assertions.assertEquals(0, totals.getMax());
            Assertions.assertEquals(Collections.emptySet(), pool.getRoutes());
            final PoolStats stats = pool.getStats("somehost");
            Assertions.assertEquals(0, stats.getAvailable());
            Assertions.assertEquals(0, stats.getLeased());
            Assertions.assertEquals(0, stats.getPending());
            Assertions.assertEquals(2, stats.getMax());
            Assertions.assertEquals("[leased: 0][available: 0][pending: 0]", pool.toString());
        }
    }

    @Test
    public void testInvalidConstruction() throws Exception {
        Assertions.assertThrows(IllegalArgumentException.class, () ->
                new LaxConnPool<String, HttpConnection>(-1));
    }

    @Test
    public void testLeaseRelease() throws Exception {
        final HttpConnection conn1 = Mockito.mock(HttpConnection.class);
        final HttpConnection conn2 = Mockito.mock(HttpConnection.class);
        final HttpConnection conn3 = Mockito.mock(HttpConnection.class);

        try (final LaxConnPool<String, HttpConnection> pool = new LaxConnPool<>(2)) {
            final Future<PoolEntry<String, HttpConnection>> future1 = pool.lease("somehost", null);
            final Future<PoolEntry<String, HttpConnection>> future2 = pool.lease("somehost", null);
            final Future<PoolEntry<String, HttpConnection>> future3 = pool.lease("otherhost", null);

            final PoolEntry<String, HttpConnection> entry1 = future1.get();
            Assertions.assertNotNull(entry1);
            entry1.assignConnection(conn1);
            final PoolEntry<String, HttpConnection> entry2 = future2.get();
            Assertions.assertNotNull(entry2);
            entry2.assignConnection(conn2);
            final PoolEntry<String, HttpConnection> entry3 = future3.get();
            Assertions.assertNotNull(entry3);
            entry3.assignConnection(conn3);

            pool.release(entry1, true);
            pool.release(entry2, true);
            pool.release(entry3, false);
            Mockito.verify(conn1, Mockito.never()).close(ArgumentMatchers.any());
            Mockito.verify(conn2, Mockito.never()).close(ArgumentMatchers.any());
            Mockito.verify(conn3, Mockito.times(1)).close(CloseMode.GRACEFUL);

            final PoolStats totals = pool.getTotalStats();
            Assertions.assertEquals(2, totals.getAvailable());
            Assertions.assertEquals(0, totals.getLeased());
            Assertions.assertEquals(0, totals.getPending());
        }
    }

    @Test
    public void testLeaseReleaseMultiThreaded() throws Exception {
        try (final LaxConnPool<String, HttpConnection> pool = new LaxConnPool<>(2)) {

            final int c = 10;
            final CountDownLatch latch = new CountDownLatch(c);
            final AtomicInteger n = new AtomicInteger(c + 100);
            final AtomicReference<AssertionError> exRef = new AtomicReference<>();

            final ExecutorService executorService = Executors.newFixedThreadPool(c);
            try {
                final Random rnd = new Random();
                for (int i = 0; i < c; i++) {
                    executorService.execute(() -> {
                        try {
                            while (n.decrementAndGet() > 0) {
                                try {
                                    final Future<PoolEntry<String, HttpConnection>> future = pool.lease("somehost", null);
                                    final PoolEntry<String, HttpConnection> poolEntry = future.get(1, TimeUnit.MINUTES);
                                    Thread.sleep(rnd.nextInt(1));
                                    pool.release(poolEntry, false);
                                } catch (final Exception ex) {
                                    Assertions.fail(ex.getMessage(), ex);
                                }
                            }
                        } catch (final AssertionError ex) {
                            exRef.compareAndSet(null, ex);
                        } finally {
                            latch.countDown();
                        }
                    });
                }

                Assertions.assertTrue(latch.await(5, TimeUnit.MINUTES));
            } finally {
                executorService.shutdownNow();
            }

            final AssertionError assertionError = exRef.get();
            if (assertionError != null) {
                throw assertionError;
            }
        }
    }

    @Test
    public void testLeaseInvalid() throws Exception {
        try (final LaxConnPool<String, HttpConnection> pool = new LaxConnPool<>(2)) {
            Assertions.assertThrows(NullPointerException.class,
                    () -> pool.lease(null, null, Timeout.ZERO_MILLISECONDS, null));
        }}

    @Test
    public void testReleaseUnknownEntry() throws Exception {
        try (final LaxConnPool<String, HttpConnection> pool = new LaxConnPool<>(2)) {
            Assertions.assertThrows(IllegalStateException.class, () -> pool.release(new PoolEntry<>("somehost"), true));
        }
    }

    @Test
    public void testMaxLimits() throws Exception {
        final HttpConnection conn1 = Mockito.mock(HttpConnection.class);
        final HttpConnection conn2 = Mockito.mock(HttpConnection.class);
        final HttpConnection conn3 = Mockito.mock(HttpConnection.class);

        try (final LaxConnPool<String, HttpConnection> pool = new LaxConnPool<>(2)) {
            pool.setMaxPerRoute("somehost", 2);
            pool.setMaxPerRoute("otherhost", 1);

            final Future<PoolEntry<String, HttpConnection>> future1 = pool.lease("somehost", null);
            final Future<PoolEntry<String, HttpConnection>> future2 = pool.lease("somehost", null);
            final Future<PoolEntry<String, HttpConnection>> future3 = pool.lease("otherhost", null);

            final PoolEntry<String, HttpConnection> entry1 = future1.get();
            Assertions.assertNotNull(entry1);
            entry1.assignConnection(conn1);
            final PoolEntry<String, HttpConnection> entry2 = future2.get();
            Assertions.assertNotNull(entry2);
            entry2.assignConnection(conn2);
            final PoolEntry<String, HttpConnection> entry3 = future3.get();
            Assertions.assertNotNull(entry3);
            entry3.assignConnection(conn3);

            pool.release(entry1, true);
            pool.release(entry2, true);
            pool.release(entry3, true);

            final PoolStats totals = pool.getTotalStats();
            Assertions.assertEquals(3, totals.getAvailable());
            Assertions.assertEquals(0, totals.getLeased());
            Assertions.assertEquals(0, totals.getPending());

            final Future<PoolEntry<String, HttpConnection>> future4 = pool.lease("somehost", null);
            final Future<PoolEntry<String, HttpConnection>> future5 = pool.lease("somehost", null);
            final Future<PoolEntry<String, HttpConnection>> future6 = pool.lease("otherhost", null);
            final Future<PoolEntry<String, HttpConnection>> future7 = pool.lease("somehost", null);
            final Future<PoolEntry<String, HttpConnection>> future8 = pool.lease("somehost", null);
            final Future<PoolEntry<String, HttpConnection>> future9 = pool.lease("otherhost", null);

            Assertions.assertTrue(future4.isDone());
            final PoolEntry<String, HttpConnection> entry4 = future4.get();
            Assertions.assertNotNull(entry4);
            Assertions.assertSame(conn2, entry4.getConnection());

            Assertions.assertTrue(future5.isDone());
            final PoolEntry<String, HttpConnection> entry5 = future5.get();
            Assertions.assertNotNull(entry5);
            Assertions.assertSame(conn1, entry5.getConnection());

            Assertions.assertTrue(future6.isDone());
            final PoolEntry<String, HttpConnection> entry6 = future6.get();
            Assertions.assertNotNull(entry6);
            Assertions.assertSame(conn3, entry6.getConnection());

            Assertions.assertFalse(future7.isDone());
            Assertions.assertFalse(future8.isDone());
            Assertions.assertFalse(future9.isDone());

            pool.release(entry4, true);
            pool.release(entry5, false);
            pool.release(entry6, true);

            Assertions.assertTrue(future7.isDone());
            final PoolEntry<String, HttpConnection> entry7 = future7.get();
            Assertions.assertNotNull(entry7);
            Assertions.assertSame(conn2, entry7.getConnection());

            Assertions.assertTrue(future8.isDone());
            final PoolEntry<String, HttpConnection> entry8 = future8.get();
            Assertions.assertNotNull(entry8);
            Assertions.assertNull(entry8.getConnection());

            Assertions.assertTrue(future9.isDone());
            final PoolEntry<String, HttpConnection> entry9 = future9.get();
            Assertions.assertNotNull(entry9);
            Assertions.assertSame(conn3, entry9.getConnection());
        }
    }

    @Test
    public void testCreateNewIfExpired() throws Exception {
        final HttpConnection conn1 = Mockito.mock(HttpConnection.class);

        try (final LaxConnPool<String, HttpConnection> pool = new LaxConnPool<>(2)) {

            final Future<PoolEntry<String, HttpConnection>> future1 = pool.lease("somehost", null);

            Assertions.assertTrue(future1.isDone());
            final PoolEntry<String, HttpConnection> entry1 = future1.get();
            Assertions.assertNotNull(entry1);
            entry1.assignConnection(conn1);

            entry1.updateExpiry(TimeValue.of(1, TimeUnit.MILLISECONDS));
            pool.release(entry1, true);

            Thread.sleep(200L);

            final Future<PoolEntry<String, HttpConnection>> future2 = pool.lease("somehost", null);

            Assertions.assertTrue(future2.isDone());

            Mockito.verify(conn1).close(CloseMode.GRACEFUL);

            final PoolStats totals = pool.getTotalStats();
            Assertions.assertEquals(0, totals.getAvailable());
            Assertions.assertEquals(1, totals.getLeased());
            Assertions.assertEquals(Collections.singleton("somehost"), pool.getRoutes());
            final PoolStats stats = pool.getStats("somehost");
            Assertions.assertEquals(0, stats.getAvailable());
            Assertions.assertEquals(1, stats.getLeased());
        }
    }

    @Test
    public void testCloseExpired() throws Exception {
        final HttpConnection conn1 = Mockito.mock(HttpConnection.class);
        final HttpConnection conn2 = Mockito.mock(HttpConnection.class);

        try (final LaxConnPool<String, HttpConnection> pool = new LaxConnPool<>(2)) {

            final Future<PoolEntry<String, HttpConnection>> future1 = pool.lease("somehost", null);
            final Future<PoolEntry<String, HttpConnection>> future2 = pool.lease("somehost", null);

            Assertions.assertTrue(future1.isDone());
            final PoolEntry<String, HttpConnection> entry1 = future1.get();
            Assertions.assertNotNull(entry1);
            entry1.assignConnection(conn1);
            Assertions.assertTrue(future2.isDone());
            final PoolEntry<String, HttpConnection> entry2 = future2.get();
            Assertions.assertNotNull(entry2);
            entry2.assignConnection(conn2);

            entry1.updateExpiry(TimeValue.of(1, TimeUnit.MILLISECONDS));
            pool.release(entry1, true);

            Thread.sleep(200);

            entry2.updateExpiry(TimeValue.of(1000, TimeUnit.SECONDS));
            pool.release(entry2, true);

            pool.closeExpired();

            Mockito.verify(conn1).close(CloseMode.GRACEFUL);
            Mockito.verify(conn2, Mockito.never()).close(ArgumentMatchers.any());

            final PoolStats totals = pool.getTotalStats();
            Assertions.assertEquals(1, totals.getAvailable());
            Assertions.assertEquals(0, totals.getLeased());
            Assertions.assertEquals(0, totals.getPending());
            final PoolStats stats = pool.getStats("somehost");
            Assertions.assertEquals(1, stats.getAvailable());
            Assertions.assertEquals(0, stats.getLeased());
            Assertions.assertEquals(0, stats.getPending());
        }
    }

    @Test
    public void testCloseIdle() throws Exception {
        final HttpConnection conn1 = Mockito.mock(HttpConnection.class);
        final HttpConnection conn2 = Mockito.mock(HttpConnection.class);

        try (final LaxConnPool<String, HttpConnection> pool = new LaxConnPool<>(2)) {

            final Future<PoolEntry<String, HttpConnection>> future1 = pool.lease("somehost", null);
            final Future<PoolEntry<String, HttpConnection>> future2 = pool.lease("somehost", null);

            Assertions.assertTrue(future1.isDone());
            final PoolEntry<String, HttpConnection> entry1 = future1.get();
            Assertions.assertNotNull(entry1);
            entry1.assignConnection(conn1);
            Assertions.assertTrue(future2.isDone());
            final PoolEntry<String, HttpConnection> entry2 = future2.get();
            Assertions.assertNotNull(entry2);
            entry2.assignConnection(conn2);

            entry1.updateState(null);
            pool.release(entry1, true);

            Thread.sleep(200L);

            entry2.updateState(null);
            pool.release(entry2, true);

            pool.closeIdle(TimeValue.of(50, TimeUnit.MILLISECONDS));

            Mockito.verify(conn1).close(CloseMode.GRACEFUL);
            Mockito.verify(conn2, Mockito.never()).close(ArgumentMatchers.any());

            PoolStats totals = pool.getTotalStats();
            Assertions.assertEquals(1, totals.getAvailable());
            Assertions.assertEquals(0, totals.getLeased());
            Assertions.assertEquals(0, totals.getPending());
            PoolStats stats = pool.getStats("somehost");
            Assertions.assertEquals(1, stats.getAvailable());
            Assertions.assertEquals(0, stats.getLeased());
            Assertions.assertEquals(0, stats.getPending());

            pool.closeIdle(TimeValue.of(-1, TimeUnit.MILLISECONDS));

            Mockito.verify(conn2).close(CloseMode.GRACEFUL);

            totals = pool.getTotalStats();
            Assertions.assertEquals(0, totals.getAvailable());
            Assertions.assertEquals(0, totals.getLeased());
            Assertions.assertEquals(0, totals.getPending());
            stats = pool.getStats("somehost");
            Assertions.assertEquals(0, stats.getAvailable());
            Assertions.assertEquals(0, stats.getLeased());
            Assertions.assertEquals(0, stats.getPending());

            Assertions.assertFalse(pool.isShutdown());
        }
    }

    @Test
    public void testLeaseRequestTimeout() throws Exception {
        final HttpConnection conn1 = Mockito.mock(HttpConnection.class);

        try (final LaxConnPool<String, HttpConnection> pool = new LaxConnPool<>(1)) {

            final Future<PoolEntry<String, HttpConnection>> future1 = pool.lease("somehost", null, Timeout.ofMilliseconds(0), null);
            final Future<PoolEntry<String, HttpConnection>> future2 = pool.lease("somehost", null, Timeout.ofMilliseconds(0), null);
            final Future<PoolEntry<String, HttpConnection>> future3 = pool.lease("somehost", null, Timeout.ofMilliseconds(10), null);

            Assertions.assertTrue(future1.isDone());
            final PoolEntry<String, HttpConnection> entry1 = future1.get();
            Assertions.assertNotNull(entry1);
            entry1.assignConnection(conn1);
            Assertions.assertFalse(future2.isDone());
            Assertions.assertFalse(future3.isDone());

            Thread.sleep(100);

            pool.validatePendingRequests();

            Assertions.assertFalse(future2.isDone());
            Assertions.assertTrue(future3.isDone());
        }
    }

    @Test
    public void testLeaseRequestCanceled() throws Exception {
        try (final LaxConnPool<String, HttpConnection> pool = new LaxConnPool<>(1)) {

            final Future<PoolEntry<String, HttpConnection>> future1 = pool.lease("somehost", null,
                    Timeout.ofMilliseconds(0), null);

            Assertions.assertTrue(future1.isDone());
            final PoolEntry<String, HttpConnection> entry1 = future1.get();
            Assertions.assertNotNull(entry1);
            entry1.assignConnection(Mockito.mock(HttpConnection.class));

            final Future<PoolEntry<String, HttpConnection>> future2 = pool.lease("somehost", null,
                    Timeout.ofMilliseconds(0), null);
            future2.cancel(true);

            pool.release(entry1, true);

            final PoolStats totals = pool.getTotalStats();
            Assertions.assertEquals(1, totals.getAvailable());
            Assertions.assertEquals(0, totals.getLeased());
        }
    }

    @Test
    public void testGetStatsInvalid() throws Exception {
        try (final LaxConnPool<String, HttpConnection> pool = new LaxConnPool<>(2)) {
            Assertions.assertThrows(NullPointerException.class, () -> pool.getStats(null));
        }
    }

    @Test
    public void testSetMaxInvalid() throws Exception {
        try (final LaxConnPool<String, HttpConnection> pool = new LaxConnPool<>(2)) {
            Assertions.assertThrows(NullPointerException.class, () -> pool.setMaxPerRoute(null, 1));
            Assertions.assertThrows(IllegalArgumentException.class, () -> pool.setDefaultMaxPerRoute(-1));
        }
    }

    @Test
    public void testShutdown() throws Exception {
        final LaxConnPool<String, HttpConnection> pool = new LaxConnPool<>(2);
        pool.close(CloseMode.GRACEFUL);
        Assertions.assertThrows(IllegalStateException.class, () -> pool.lease("somehost", null));
        // Ignored if shut down
        pool.release(new PoolEntry<>("somehost"), true);
    }

    @Test
    public void testClose() {
        final LaxConnPool<String, HttpConnection> pool = new LaxConnPool<>(2);
        pool.setMaxPerRoute("someRoute", 2);
        pool.close();
        Assertions.assertThrows(IllegalStateException.class, () -> pool.lease("someHost", null));
        // Ignored if shut down
        pool.release(new PoolEntry<>("someHost"), true);

    }

    @Test
    public void testGetMaxPerRoute() {
        final String route = "someRoute";
        final int max = 2;
        try (final LaxConnPool<String, HttpConnection> pool = new LaxConnPool<>(2)) {
            pool.setMaxPerRoute(route, max);
            Assertions.assertEquals(max, pool.getMaxPerRoute(route));
        }
    }
}
