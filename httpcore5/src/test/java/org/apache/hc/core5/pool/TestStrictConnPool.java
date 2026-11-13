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
import org.assertj.core.api.Assertions;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

class TestStrictConnPool {

    @Test
    void testEmptyPool() {
        try (final StrictConnPool<String, HttpConnection> pool = new StrictConnPool<>(2, 10)) {
            final PoolStats totals = pool.getTotalStats();
            Assertions.assertThat(totals.getAvailable()).isEqualTo(0);
            Assertions.assertThat(totals.getLeased()).isEqualTo(0);
            Assertions.assertThat(totals.getPending()).isEqualTo(0);
            Assertions.assertThat(totals.getMax()).isEqualTo(10);
            Assertions.assertThat(pool.getRoutes()).isEqualTo(Collections.emptySet());
            final PoolStats stats = pool.getStats("somehost");
            Assertions.assertThat(stats.getAvailable()).isEqualTo(0);
            Assertions.assertThat(stats.getLeased()).isEqualTo(0);
            Assertions.assertThat(stats.getPending()).isEqualTo(0);
            Assertions.assertThat(stats.getMax()).isEqualTo(2);
            Assertions.assertThat(pool.toString()).isEqualTo("[leased: 0][available: 0][pending: 0]");
        }
    }

    @Test
    void testInvalidConstruction() {
        Assertions.assertThatThrownBy(() ->
                new StrictConnPool<String, HttpConnection>(-1, 1)).isInstanceOf(IllegalArgumentException.class);
        Assertions.assertThatThrownBy(() ->
                new StrictConnPool<String, HttpConnection>(1, -1)).isInstanceOf(IllegalArgumentException.class);
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
            Assertions.assertThat(entry1).isNotNull();
            entry1.assignConnection(conn1);
            final PoolEntry<String, HttpConnection> entry2 = future2.get();
            Assertions.assertThat(entry2).isNotNull();
            entry2.assignConnection(conn2);
            final PoolEntry<String, HttpConnection> entry3 = future3.get();
            Assertions.assertThat(entry3).isNotNull();
            entry3.assignConnection(conn3);

            pool.release(entry1, true);
            pool.release(entry2, true);
            pool.release(entry3, false);
            Mockito.verify(conn1, Mockito.never()).close(ArgumentMatchers.any());
            Mockito.verify(conn2, Mockito.never()).close(ArgumentMatchers.any());
            Mockito.verify(conn3, Mockito.times(1)).close(CloseMode.GRACEFUL);

            final PoolStats totals = pool.getTotalStats();
            Assertions.assertThat(totals.getAvailable()).isEqualTo(2);
            Assertions.assertThat(totals.getLeased()).isEqualTo(0);
            Assertions.assertThat(totals.getPending()).isEqualTo(0);
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

                Assertions.assertThat(latch.await(5, TimeUnit.MINUTES)).isTrue();
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
            Assertions.assertThatThrownBy(() ->
                    pool.lease(null, null, Timeout.ZERO_MILLISECONDS, null)).isInstanceOf(NullPointerException.class);
            Assertions.assertThatThrownBy(() ->
                    pool.lease("somehost", null, null, null)).isInstanceOf(NullPointerException.class);
    }
    }

    @Test
    void testReleaseUnknownEntry() {
        try (final StrictConnPool<String, HttpConnection> pool = new StrictConnPool<>(2, 2)) {
            Assertions.assertThatThrownBy(() ->
                    pool.release(new PoolEntry<>("somehost"), true)).isInstanceOf(IllegalStateException.class);
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
            Assertions.assertThat(entry1).isNotNull();
            entry1.assignConnection(conn1);
            final PoolEntry<String, HttpConnection> entry2 = future2.get();
            Assertions.assertThat(entry2).isNotNull();
            entry2.assignConnection(conn2);
            final PoolEntry<String, HttpConnection> entry3 = future3.get();
            Assertions.assertThat(entry3).isNotNull();
            entry3.assignConnection(conn3);

            pool.release(entry1, true);
            pool.release(entry2, true);
            pool.release(entry3, true);

            final PoolStats totals = pool.getTotalStats();
            Assertions.assertThat(totals.getAvailable()).isEqualTo(3);
            Assertions.assertThat(totals.getLeased()).isEqualTo(0);
            Assertions.assertThat(totals.getPending()).isEqualTo(0);

            final Future<PoolEntry<String, HttpConnection>> future4 = pool.lease("somehost", null);
            final Future<PoolEntry<String, HttpConnection>> future5 = pool.lease("somehost", null);
            final Future<PoolEntry<String, HttpConnection>> future6 = pool.lease("otherhost", null);
            final Future<PoolEntry<String, HttpConnection>> future7 = pool.lease("somehost", null);
            final Future<PoolEntry<String, HttpConnection>> future8 = pool.lease("somehost", null);
            final Future<PoolEntry<String, HttpConnection>> future9 = pool.lease("otherhost", null);

            Assertions.assertThat(future4.isDone()).isTrue();
            final PoolEntry<String, HttpConnection> entry4 = future4.get();
            Assertions.assertThat(entry4).isNotNull();
            Assertions.assertThat(entry4.getConnection()).isSameAs(conn2);

            Assertions.assertThat(future5.isDone()).isTrue();
            final PoolEntry<String, HttpConnection> entry5 = future5.get();
            Assertions.assertThat(entry5).isNotNull();
            Assertions.assertThat(entry5.getConnection()).isSameAs(conn1);

            Assertions.assertThat(future6.isDone()).isTrue();
            final PoolEntry<String, HttpConnection> entry6 = future6.get();
            Assertions.assertThat(entry6).isNotNull();
            Assertions.assertThat(entry6.getConnection()).isSameAs(conn3);

            Assertions.assertThat(future7.isDone()).isFalse();
            Assertions.assertThat(future8.isDone()).isFalse();
            Assertions.assertThat(future9.isDone()).isFalse();

            pool.release(entry4, true);
            pool.release(entry5, false);
            pool.release(entry6, true);

            Assertions.assertThat(future7.isDone()).isTrue();
            final PoolEntry<String, HttpConnection> entry7 = future7.get();
            Assertions.assertThat(entry7).isNotNull();
            Assertions.assertThat(entry7.getConnection()).isSameAs(conn2);

            Assertions.assertThat(future8.isDone()).isTrue();
            final PoolEntry<String, HttpConnection> entry8 = future8.get();
            Assertions.assertThat(entry8).isNotNull();
            Assertions.assertThat(entry8.getConnection()).isNull();

            Assertions.assertThat(future9.isDone()).isTrue();
            final PoolEntry<String, HttpConnection> entry9 = future9.get();
            Assertions.assertThat(entry9).isNotNull();
            Assertions.assertThat(entry9.getConnection()).isSameAs(conn3);
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

            Assertions.assertThat(future1.isDone()).isTrue();
            final PoolEntry<String, HttpConnection> entry1 = future1.get();
            Assertions.assertThat(entry1).isNotNull();
            Assertions.assertThat(entry1.hasConnection()).isFalse();
            entry1.assignConnection(conn1);
            Assertions.assertThat(future2.isDone()).isTrue();
            final PoolEntry<String, HttpConnection> entry2 = future2.get();
            Assertions.assertThat(entry2).isNotNull();
            Assertions.assertThat(entry2.hasConnection()).isFalse();
            entry2.assignConnection(conn2);

            Assertions.assertThat(future3.isDone()).isFalse();
            Assertions.assertThat(future4.isDone()).isFalse();

            PoolStats totals = pool.getTotalStats();
            Assertions.assertThat(totals.getAvailable()).isEqualTo(0);
            Assertions.assertThat(totals.getLeased()).isEqualTo(2);
            Assertions.assertThat(totals.getPending()).isEqualTo(2);

            pool.release(entry1, true);
            pool.release(entry2, true);

            Assertions.assertThat(future3.isDone()).isTrue();
            final PoolEntry<String, HttpConnection> entry3 = future3.get();
            Assertions.assertThat(entry3).isNotNull();
            Assertions.assertThat(entry3.hasConnection()).isFalse();
            entry3.assignConnection(conn3);
            Assertions.assertThat(future4.isDone()).isTrue();
            final PoolEntry<String, HttpConnection> entry4 = future4.get();
            Assertions.assertThat(entry4).isNotNull();
            Assertions.assertThat(entry4.hasConnection()).isFalse();
            entry4.assignConnection(conn4);

            totals = pool.getTotalStats();
            Assertions.assertThat(totals.getAvailable()).isEqualTo(0);
            Assertions.assertThat(totals.getLeased()).isEqualTo(2);
            Assertions.assertThat(totals.getPending()).isEqualTo(0);

            final Future<PoolEntry<String, HttpConnection>> future5 = pool.lease("somehost", null);
            final Future<PoolEntry<String, HttpConnection>> future6 = pool.lease("otherhost", null);

            pool.release(entry3, true);
            pool.release(entry4, true);

            Assertions.assertThat(future5.isDone()).isTrue();
            final PoolEntry<String, HttpConnection> entry5 = future5.get();
            Assertions.assertThat(entry5).isNotNull();
            Assertions.assertThat(entry5.hasConnection()).isFalse();
            entry5.assignConnection(conn5);
            Assertions.assertThat(future6.isDone()).isTrue();
            final PoolEntry<String, HttpConnection> entry6 = future6.get();
            Assertions.assertThat(entry6).isNotNull();
            Assertions.assertThat(entry6.hasConnection()).isTrue();
            Assertions.assertThat(entry6.getConnection()).isSameAs(conn4);

            totals = pool.getTotalStats();
            Assertions.assertThat(totals.getAvailable()).isEqualTo(0);
            Assertions.assertThat(totals.getLeased()).isEqualTo(2);
            Assertions.assertThat(totals.getPending()).isEqualTo(0);

            pool.release(entry5, true);
            pool.release(entry6, true);

            totals = pool.getTotalStats();
            Assertions.assertThat(totals.getAvailable()).isEqualTo(2);
            Assertions.assertThat(totals.getLeased()).isEqualTo(0);
            Assertions.assertThat(totals.getPending()).isEqualTo(0);
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

            Assertions.assertThat(future1.isDone()).isTrue();
            final PoolEntry<String, HttpConnection> entry1 = future1.get();
            entry1.assignConnection(conn1);
            Assertions.assertThat(entry1).isNotNull();
            Assertions.assertThat(future2.isDone()).isTrue();
            final PoolEntry<String, HttpConnection> entry2 = future2.get();
            Assertions.assertThat(entry2).isNotNull();
            entry2.assignConnection(conn2);

            PoolStats totals = pool.getTotalStats();
            Assertions.assertThat(totals.getAvailable()).isEqualTo(0);
            Assertions.assertThat(totals.getLeased()).isEqualTo(2);
            Assertions.assertThat(totals.getPending()).isEqualTo(0);

            entry1.updateState("some-stuff");
            pool.release(entry1, true);
            entry2.updateState("some-stuff");
            pool.release(entry2, true);

            final Future<PoolEntry<String, HttpConnection>> future3 = pool.lease("somehost", "some-stuff");
            final Future<PoolEntry<String, HttpConnection>> future4 = pool.lease("somehost", "some-stuff");

            Assertions.assertThat(future1.isDone()).isTrue();
            final PoolEntry<String, HttpConnection> entry3 = future3.get();
            Assertions.assertThat(entry3).isNotNull();
            Assertions.assertThat(entry3.getConnection()).isSameAs(conn2);
            Assertions.assertThat(future4.isDone()).isTrue();
            final PoolEntry<String, HttpConnection> entry4 = future4.get();
            Assertions.assertThat(entry4).isNotNull();
            Assertions.assertThat(entry4.getConnection()).isSameAs(conn1);

            pool.release(entry3, true);
            pool.release(entry4, true);

            totals = pool.getTotalStats();
            Assertions.assertThat(totals.getAvailable()).isEqualTo(2);
            Assertions.assertThat(totals.getLeased()).isEqualTo(0);
            Assertions.assertThat(totals.getPending()).isEqualTo(0);

            final Future<PoolEntry<String, HttpConnection>> future5 = pool.lease("somehost", "some-other-stuff");

            Assertions.assertThat(future5.isDone()).isTrue();

            Mockito.verify(conn2).close(CloseMode.GRACEFUL);
            Mockito.verify(conn1, Mockito.never()).close(ArgumentMatchers.any());

            totals = pool.getTotalStats();
            Assertions.assertThat(totals.getAvailable()).isEqualTo(1);
            Assertions.assertThat(totals.getLeased()).isEqualTo(1);
        }
    }

    @Test
    void testCreateNewIfExpired() throws Exception {
        final HttpConnection conn1 = Mockito.mock(HttpConnection.class);

        try (final StrictConnPool<String, HttpConnection> pool = new StrictConnPool<>(2, 2)) {

            final Future<PoolEntry<String, HttpConnection>> future1 = pool.lease("somehost", null);

            Assertions.assertThat(future1.isDone()).isTrue();
            final PoolEntry<String, HttpConnection> entry1 = future1.get();
            Assertions.assertThat(entry1).isNotNull();
            entry1.assignConnection(conn1);

            entry1.updateExpiry(TimeValue.of(1, TimeUnit.MILLISECONDS));
            pool.release(entry1, true);

            Thread.sleep(200L);

            final Future<PoolEntry<String, HttpConnection>> future2 = pool.lease("somehost", null);

            Assertions.assertThat(future2.isDone()).isTrue();

            Mockito.verify(conn1).close(CloseMode.GRACEFUL);

            final PoolStats totals = pool.getTotalStats();
            Assertions.assertThat(totals.getAvailable()).isEqualTo(0);
            Assertions.assertThat(totals.getLeased()).isEqualTo(1);
            Assertions.assertThat(pool.getRoutes()).isEqualTo(Collections.singleton("somehost"));
            final PoolStats stats = pool.getStats("somehost");
            Assertions.assertThat(stats.getAvailable()).isEqualTo(0);
            Assertions.assertThat(stats.getLeased()).isEqualTo(1);
        }
    }

    @Test
    void testCloseExpired() throws Exception {
        final HttpConnection conn1 = Mockito.mock(HttpConnection.class);
        final HttpConnection conn2 = Mockito.mock(HttpConnection.class);

        try (final StrictConnPool<String, HttpConnection> pool = new StrictConnPool<>(2, 2)) {

            final Future<PoolEntry<String, HttpConnection>> future1 = pool.lease("somehost", null);
            final Future<PoolEntry<String, HttpConnection>> future2 = pool.lease("somehost", null);

            Assertions.assertThat(future1.isDone()).isTrue();
            final PoolEntry<String, HttpConnection> entry1 = future1.get();
            Assertions.assertThat(entry1).isNotNull();
            entry1.assignConnection(conn1);
            Assertions.assertThat(future2.isDone()).isTrue();
            final PoolEntry<String, HttpConnection> entry2 = future2.get();
            Assertions.assertThat(entry2).isNotNull();
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
            Assertions.assertThat(totals.getAvailable()).isEqualTo(1);
            Assertions.assertThat(totals.getLeased()).isEqualTo(0);
            Assertions.assertThat(totals.getPending()).isEqualTo(0);
            final PoolStats stats = pool.getStats("somehost");
            Assertions.assertThat(stats.getAvailable()).isEqualTo(1);
            Assertions.assertThat(stats.getLeased()).isEqualTo(0);
            Assertions.assertThat(stats.getPending()).isEqualTo(0);
        }
    }

    @Test
    void testCloseIdle() throws Exception {
        final HttpConnection conn1 = Mockito.mock(HttpConnection.class);
        final HttpConnection conn2 = Mockito.mock(HttpConnection.class);

        try (final StrictConnPool<String, HttpConnection> pool = new StrictConnPool<>(2, 2)) {

            final Future<PoolEntry<String, HttpConnection>> future1 = pool.lease("somehost", null);
            final Future<PoolEntry<String, HttpConnection>> future2 = pool.lease("somehost", null);

            Assertions.assertThat(future1.isDone()).isTrue();
            final PoolEntry<String, HttpConnection> entry1 = future1.get();
            Assertions.assertThat(entry1).isNotNull();
            entry1.assignConnection(conn1);
            Assertions.assertThat(future2.isDone()).isTrue();
            final PoolEntry<String, HttpConnection> entry2 = future2.get();
            Assertions.assertThat(entry2).isNotNull();
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
            Assertions.assertThat(totals.getAvailable()).isEqualTo(1);
            Assertions.assertThat(totals.getLeased()).isEqualTo(0);
            Assertions.assertThat(totals.getPending()).isEqualTo(0);
            PoolStats stats = pool.getStats("somehost");
            Assertions.assertThat(stats.getAvailable()).isEqualTo(1);
            Assertions.assertThat(stats.getLeased()).isEqualTo(0);
            Assertions.assertThat(stats.getPending()).isEqualTo(0);

            pool.closeIdle(TimeValue.of(-1, TimeUnit.MILLISECONDS));

            Mockito.verify(conn2).close(CloseMode.GRACEFUL);

            totals = pool.getTotalStats();
            Assertions.assertThat(totals.getAvailable()).isEqualTo(0);
            Assertions.assertThat(totals.getLeased()).isEqualTo(0);
            Assertions.assertThat(totals.getPending()).isEqualTo(0);
            stats = pool.getStats("somehost");
            Assertions.assertThat(stats.getAvailable()).isEqualTo(0);
            Assertions.assertThat(stats.getLeased()).isEqualTo(0);
            Assertions.assertThat(stats.getPending()).isEqualTo(0);
        }
    }

    @Test
    void testLeaseRequestTimeout() throws Exception {
        final HttpConnection conn1 = Mockito.mock(HttpConnection.class);

        try (final StrictConnPool<String, HttpConnection> pool = new StrictConnPool<>(1, 1)) {

            final Future<PoolEntry<String, HttpConnection>> future1 = pool.lease("somehost", null, Timeout.ofMilliseconds(0), null);
            final Future<PoolEntry<String, HttpConnection>> future2 = pool.lease("somehost", null, Timeout.ofMilliseconds(0), null);
            final Future<PoolEntry<String, HttpConnection>> future3 = pool.lease("somehost", null, Timeout.ofMilliseconds(10), null);

            Assertions.assertThat(future1.isDone()).isTrue();
            final PoolEntry<String, HttpConnection> entry1 = future1.get();
            Assertions.assertThat(entry1).isNotNull();
            entry1.assignConnection(conn1);
            Assertions.assertThat(future2.isDone()).isFalse();
            Assertions.assertThat(future3.isDone()).isFalse();

            Thread.sleep(100);

            pool.validatePendingRequests();

            Assertions.assertThat(future2.isDone()).isFalse();
            Assertions.assertThat(future3.isDone()).isTrue();
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

        final ExecutionException executionException = Assertions.assertThatThrownBy(() ->
                future2.get()).isInstanceOf(ExecutionException.class).asInstanceOf(InstanceOfAssertFactories.type(ExecutionException.class)).actual();
        Assertions.assertThat(executionException.getCause()).isInstanceOf(DeadlineTimeoutException.class);
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

        Assertions.assertThat(Thread.interrupted()).isTrue();
        Assertions.assertThatThrownBy(() ->
                        future2.get())
                .isInstanceOf(CancellationException.class);
        holdInternalLock.interrupt(); // Cleanup
    }

    @Test
    void testLeaseRequestCanceled() throws Exception {
        try (final StrictConnPool<String, HttpConnection> pool = new StrictConnPool<>(1, 1)) {

            final Future<PoolEntry<String, HttpConnection>> future1 = pool.lease("somehost", null,
                    Timeout.ofMilliseconds(0), null);

            Assertions.assertThat(future1.isDone()).isTrue();
            final PoolEntry<String, HttpConnection> entry1 = future1.get();
            Assertions.assertThat(entry1).isNotNull();
            entry1.assignConnection(Mockito.mock(HttpConnection.class));

            final Future<PoolEntry<String, HttpConnection>> future2 = pool.lease("somehost", null,
                    Timeout.ofMilliseconds(0), null);
            future2.cancel(true);

            pool.release(entry1, true);

            final PoolStats totals = pool.getTotalStats();
            Assertions.assertThat(totals.getAvailable()).isEqualTo(1);
            Assertions.assertThat(totals.getLeased()).isEqualTo(0);
        }
    }

    @Test
    void testGetStatsInvalid() {
        try (final StrictConnPool<String, HttpConnection> pool = new StrictConnPool<>(2, 2)) {
            Assertions.assertThatThrownBy(() ->
                            pool.getStats(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Test
    void testSetMaxInvalid() {
        try (final StrictConnPool<String, HttpConnection> pool = new StrictConnPool<>(2, 2)) {
            Assertions.assertThatThrownBy(() ->
                    pool.setMaxTotal(-1)).isInstanceOf(IllegalArgumentException.class);
            Assertions.assertThatThrownBy(() ->
                    pool.setMaxPerRoute(null, 1)).isInstanceOf(NullPointerException.class);
            Assertions.assertThatThrownBy(() ->
                    pool.setDefaultMaxPerRoute(-1)).isInstanceOf(IllegalArgumentException.class);
    }
    }

    @Test
    void testSetMaxPerRoute() {
        try (final StrictConnPool<String, HttpConnection> pool = new StrictConnPool<>(2, 2)) {
            pool.setMaxPerRoute("somehost", 1);
            Assertions.assertThat(pool.getMaxPerRoute("somehost")).isEqualTo(1);
            pool.setMaxPerRoute("somehost", 0);
            Assertions.assertThat(pool.getMaxPerRoute("somehost")).isEqualTo(0);
            pool.setMaxPerRoute("somehost", -1);
            Assertions.assertThat(pool.getMaxPerRoute("somehost")).isEqualTo(2);
        }
    }

    @Test
    void testShutdown() {
        final StrictConnPool<String, HttpConnection> pool = new StrictConnPool<>(2, 2);
        pool.close(CloseMode.GRACEFUL);
        Assertions.assertThatThrownBy(() ->
                        pool.lease("somehost", null))
                .isInstanceOf(IllegalStateException.class);
        // Ignored if shut down
        pool.release(new PoolEntry<>("somehost"), true);
    }

}
