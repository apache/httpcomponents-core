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
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.DeadlineTimeoutException;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

class TestStrictConnPool {

    @Test
    void testEmptyPool() {
        try (final StrictConnPool<String, HttpConnection> pool = new StrictConnPool<>(2, 10)) {
            final PoolStats totals = pool.getTotalStats();
            Assertions.assertEquals(0, totals.getAvailable());
            Assertions.assertEquals(0, totals.getLeased());
            Assertions.assertEquals(0, totals.getPending());
            Assertions.assertEquals(10, totals.getMax());
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
    void testInvalidConstruction() {
        Assertions.assertThrows(IllegalArgumentException.class, () ->
                new StrictConnPool<String, HttpConnection>(-1, 1));
        Assertions.assertThrows(IllegalArgumentException.class, () ->
                new StrictConnPool<String, HttpConnection>(1, -1));
    }

    @Test
    void testLeaseRelease() throws Exception {
        final HttpConnection conn1 = Mockito.mock(HttpConnection.class);
        final HttpConnection conn2 = Mockito.mock(HttpConnection.class);
        final HttpConnection conn3 = Mockito.mock(HttpConnection.class);

        try (final StrictConnPool<String, HttpConnection> pool = new StrictConnPool<>(2, 10)) {
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
    void testLeaseReleaseMultiThreaded() throws Exception {
        try (final StrictConnPool<String, HttpConnection> pool = new StrictConnPool<>(2, 10)) {

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
    void testLeaseInvalid() {
        try (final StrictConnPool<String, HttpConnection> pool = new StrictConnPool<>(2, 10)) {
            Assertions.assertThrows(NullPointerException.class, () ->
                    pool.lease(null, null, Timeout.ZERO_MILLISECONDS, null));
            Assertions.assertThrows(NullPointerException.class, () ->
                    pool.lease("somehost", null, null, null));
    }
    }

    @Test
    void testReleaseUnknownEntry() {
        try (final StrictConnPool<String, HttpConnection> pool = new StrictConnPool<>(2, 2)) {
                Assertions.assertThrows(IllegalStateException.class, () ->
                    pool.release(new PoolEntry<>("somehost"), true));
        }
    }

    @Test
    void testMaxLimits() throws Exception {
        final HttpConnection conn1 = Mockito.mock(HttpConnection.class);
        final HttpConnection conn2 = Mockito.mock(HttpConnection.class);
        final HttpConnection conn3 = Mockito.mock(HttpConnection.class);

        try (final StrictConnPool<String, HttpConnection> pool = new StrictConnPool<>(2, 10)) {
            pool.setMaxPerRoute("somehost", 2);
            pool.setMaxPerRoute("otherhost", 1);
            pool.setMaxTotal(3);

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
    void testConnectionRedistributionOnTotalMaxLimit() throws Exception {
        final HttpConnection conn1 = Mockito.mock(HttpConnection.class);
        final HttpConnection conn2 = Mockito.mock(HttpConnection.class);
        final HttpConnection conn3 = Mockito.mock(HttpConnection.class);
        final HttpConnection conn4 = Mockito.mock(HttpConnection.class);
        final HttpConnection conn5 = Mockito.mock(HttpConnection.class);

        try (final StrictConnPool<String, HttpConnection> pool = new StrictConnPool<>(2, 10)) {
            pool.setMaxPerRoute("somehost", 2);
            pool.setMaxPerRoute("otherhost", 2);
            pool.setMaxTotal(2);

            final Future<PoolEntry<String, HttpConnection>> future1 = pool.lease("somehost", null);
            final Future<PoolEntry<String, HttpConnection>> future2 = pool.lease("somehost", null);
            final Future<PoolEntry<String, HttpConnection>> future3 = pool.lease("otherhost", null);
            final Future<PoolEntry<String, HttpConnection>> future4 = pool.lease("otherhost", null);

            Assertions.assertTrue(future1.isDone());
            final PoolEntry<String, HttpConnection> entry1 = future1.get();
            Assertions.assertNotNull(entry1);
            Assertions.assertFalse(entry1.hasConnection());
            entry1.assignConnection(conn1);
            Assertions.assertTrue(future2.isDone());
            final PoolEntry<String, HttpConnection> entry2 = future2.get();
            Assertions.assertNotNull(entry2);
            Assertions.assertFalse(entry2.hasConnection());
            entry2.assignConnection(conn2);

            Assertions.assertFalse(future3.isDone());
            Assertions.assertFalse(future4.isDone());

            PoolStats totals = pool.getTotalStats();
            Assertions.assertEquals(0, totals.getAvailable());
            Assertions.assertEquals(2, totals.getLeased());
            Assertions.assertEquals(2, totals.getPending());

            pool.release(entry1, true);
            pool.release(entry2, true);

            Assertions.assertTrue(future3.isDone());
            final PoolEntry<String, HttpConnection> entry3 = future3.get();
            Assertions.assertNotNull(entry3);
            Assertions.assertFalse(entry3.hasConnection());
            entry3.assignConnection(conn3);
            Assertions.assertTrue(future4.isDone());
            final PoolEntry<String, HttpConnection> entry4 = future4.get();
            Assertions.assertNotNull(entry4);
            Assertions.assertFalse(entry4.hasConnection());
            entry4.assignConnection(conn4);

            totals = pool.getTotalStats();
            Assertions.assertEquals(0, totals.getAvailable());
            Assertions.assertEquals(2, totals.getLeased());
            Assertions.assertEquals(0, totals.getPending());

            final Future<PoolEntry<String, HttpConnection>> future5 = pool.lease("somehost", null);
            final Future<PoolEntry<String, HttpConnection>> future6 = pool.lease("otherhost", null);

            pool.release(entry3, true);
            pool.release(entry4, true);

            Assertions.assertTrue(future5.isDone());
            final PoolEntry<String, HttpConnection> entry5 = future5.get();
            Assertions.assertNotNull(entry5);
            Assertions.assertFalse(entry5.hasConnection());
            entry5.assignConnection(conn5);
            Assertions.assertTrue(future6.isDone());
            final PoolEntry<String, HttpConnection> entry6 = future6.get();
            Assertions.assertNotNull(entry6);
            Assertions.assertTrue(entry6.hasConnection());
            Assertions.assertSame(conn4, entry6.getConnection());

            totals = pool.getTotalStats();
            Assertions.assertEquals(0, totals.getAvailable());
            Assertions.assertEquals(2, totals.getLeased());
            Assertions.assertEquals(0, totals.getPending());

            pool.release(entry5, true);
            pool.release(entry6, true);

            totals = pool.getTotalStats();
            Assertions.assertEquals(2, totals.getAvailable());
            Assertions.assertEquals(0, totals.getLeased());
            Assertions.assertEquals(0, totals.getPending());
        }
    }

    @Test
    void testStatefulConnectionRedistributionOnPerRouteMaxLimit() throws Exception {
        final HttpConnection conn1 = Mockito.mock(HttpConnection.class);
        final HttpConnection conn2 = Mockito.mock(HttpConnection.class);

        try (final StrictConnPool<String, HttpConnection> pool = new StrictConnPool<>(2, 10)) {
            pool.setMaxPerRoute("somehost", 2);
            pool.setMaxTotal(2);

            final Future<PoolEntry<String, HttpConnection>> future1 = pool.lease("somehost", null);
            final Future<PoolEntry<String, HttpConnection>> future2 = pool.lease("somehost", null);

            Assertions.assertTrue(future1.isDone());
            final PoolEntry<String, HttpConnection> entry1 = future1.get();
            entry1.assignConnection(conn1);
            Assertions.assertNotNull(entry1);
            Assertions.assertTrue(future2.isDone());
            final PoolEntry<String, HttpConnection> entry2 = future2.get();
            Assertions.assertNotNull(entry2);
            entry2.assignConnection(conn2);

            PoolStats totals = pool.getTotalStats();
            Assertions.assertEquals(0, totals.getAvailable());
            Assertions.assertEquals(2, totals.getLeased());
            Assertions.assertEquals(0, totals.getPending());

            entry1.updateState("some-stuff");
            pool.release(entry1, true);
            entry2.updateState("some-stuff");
            pool.release(entry2, true);

            final Future<PoolEntry<String, HttpConnection>> future3 = pool.lease("somehost", "some-stuff");
            final Future<PoolEntry<String, HttpConnection>> future4 = pool.lease("somehost", "some-stuff");

            Assertions.assertTrue(future1.isDone());
            final PoolEntry<String, HttpConnection> entry3 = future3.get();
            Assertions.assertNotNull(entry3);
            Assertions.assertSame(conn2, entry3.getConnection());
            Assertions.assertTrue(future4.isDone());
            final PoolEntry<String, HttpConnection> entry4 = future4.get();
            Assertions.assertNotNull(entry4);
            Assertions.assertSame(conn1, entry4.getConnection());

            pool.release(entry3, true);
            pool.release(entry4, true);

            totals = pool.getTotalStats();
            Assertions.assertEquals(2, totals.getAvailable());
            Assertions.assertEquals(0, totals.getLeased());
            Assertions.assertEquals(0, totals.getPending());

            final Future<PoolEntry<String, HttpConnection>> future5 = pool.lease("somehost", "some-other-stuff");

            Assertions.assertTrue(future5.isDone());

            Mockito.verify(conn2).close(CloseMode.GRACEFUL);
            Mockito.verify(conn1, Mockito.never()).close(ArgumentMatchers.any());

            totals = pool.getTotalStats();
            Assertions.assertEquals(1, totals.getAvailable());
            Assertions.assertEquals(1, totals.getLeased());
        }
    }

    @Test
    void testCreateNewIfExpired() throws Exception {
        final HttpConnection conn1 = Mockito.mock(HttpConnection.class);

        try (final StrictConnPool<String, HttpConnection> pool = new StrictConnPool<>(2, 2)) {

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
    void testCloseExpired() throws Exception {
        final HttpConnection conn1 = Mockito.mock(HttpConnection.class);
        final HttpConnection conn2 = Mockito.mock(HttpConnection.class);

        try (final StrictConnPool<String, HttpConnection> pool = new StrictConnPool<>(2, 2)) {

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
    void testCloseIdle() throws Exception {
        final HttpConnection conn1 = Mockito.mock(HttpConnection.class);
        final HttpConnection conn2 = Mockito.mock(HttpConnection.class);

        try (final StrictConnPool<String, HttpConnection> pool = new StrictConnPool<>(2, 2)) {

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
        }
    }

    @Test
    void testLeaseRequestTimeout() throws Exception {
        final HttpConnection conn1 = Mockito.mock(HttpConnection.class);

        try (final StrictConnPool<String, HttpConnection> pool = new StrictConnPool<>(1, 1)) {

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

    private static class HoldInternalLockThread extends Thread {
        private HoldInternalLockThread(final StrictConnPool<String, HttpConnection> pool, final CountDownLatch lockHeld) {
            super(() -> {
                pool.lease("somehost", null); // lease a connection so we have something to enumLeased()
                pool.enumLeased(object -> {
                    try {
                        lockHeld.countDown();
                        Thread.sleep(Long.MAX_VALUE);
                    } catch (final InterruptedException ignored) {
                    }
                });
            });
        }
    }

    @Test
    void testLeaseRequestLockTimeout() throws Exception {
        final StrictConnPool<String, HttpConnection> pool = new StrictConnPool<>(1, 1);
        final CountDownLatch lockHeld = new CountDownLatch(1);
        final Thread holdInternalLock = new HoldInternalLockThread(pool, lockHeld);

        holdInternalLock.start(); // Start a thread to grab the internal conn pool lock
        lockHeld.await(); // Wait until we know the internal lock is held

        // Attempt to get a connection while lock is held
        final Future<PoolEntry<String, HttpConnection>> future2 = pool.lease("somehost", null, Timeout.ofMilliseconds(10), null);

        final ExecutionException executionException = Assertions.assertThrows(ExecutionException.class, () ->
                future2.get());
        Assertions.assertTrue(executionException.getCause() instanceof DeadlineTimeoutException);
        holdInternalLock.interrupt(); // Cleanup
    }

    @Test
    void testLeaseRequestInterrupted() throws Exception {
        final StrictConnPool<String, HttpConnection> pool = new StrictConnPool<>(1, 1);
        final CountDownLatch lockHeld = new CountDownLatch(1);
        final Thread holdInternalLock = new HoldInternalLockThread(pool, lockHeld);

        holdInternalLock.start(); // Start a thread to grab the internal conn pool lock
        lockHeld.await(); // Wait until we know the internal lock is held

        Thread.currentThread().interrupt();
        // Attempt to get a connection while lock is held and thread is interrupted
        final Future<PoolEntry<String, HttpConnection>> future2 = pool.lease("somehost", null, Timeout.ofMilliseconds(10), null);

        Assertions.assertTrue(Thread.interrupted());
        Assertions.assertThrows(CancellationException.class, () -> future2.get());
        holdInternalLock.interrupt(); // Cleanup
    }

    @Test
    void testLeaseRequestCanceled() throws Exception {
        try (final StrictConnPool<String, HttpConnection> pool = new StrictConnPool<>(1, 1)) {

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
    void testGetStatsInvalid() {
        try (final StrictConnPool<String, HttpConnection> pool = new StrictConnPool<>(2, 2)) {
            Assertions.assertThrows(NullPointerException.class, () -> pool.getStats(null));
        }
    }

    @Test
    void testSetMaxInvalid() {
        try (final StrictConnPool<String, HttpConnection> pool = new StrictConnPool<>(2, 2)) {
            Assertions.assertThrows(IllegalArgumentException.class, () ->
                    pool.setMaxTotal(-1));
            Assertions.assertThrows(NullPointerException.class, () ->
                    pool.setMaxPerRoute(null, 1));
            Assertions.assertThrows(IllegalArgumentException.class, () ->
                    pool.setDefaultMaxPerRoute(-1));
    }
    }

    @Test
    void testSetMaxPerRoute() {
        try (final StrictConnPool<String, HttpConnection> pool = new StrictConnPool<>(2, 2)) {
            pool.setMaxPerRoute("somehost", 1);
            Assertions.assertEquals(1, pool.getMaxPerRoute("somehost"));
            pool.setMaxPerRoute("somehost", 0);
            Assertions.assertEquals(0, pool.getMaxPerRoute("somehost"));
            pool.setMaxPerRoute("somehost", -1);
            Assertions.assertEquals(2, pool.getMaxPerRoute("somehost"));
        }
    }

    @Test
    void testShutdown() {
        final StrictConnPool<String, HttpConnection> pool = new StrictConnPool<>(2, 2);
        pool.close(CloseMode.GRACEFUL);
        Assertions.assertThrows(IllegalStateException.class, () -> pool.lease("somehost", null));
        // Ignored if shut down
        pool.release(new PoolEntry<>("somehost"), true);
    }

}
