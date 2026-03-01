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

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

class TestConnPoolTimeContract {

    private static Stream<Arguments> pools() {
        return Stream.of(
                Arguments.of("STRICT", (PoolTestSupport.PoolFactory<String, HttpConnection>) clock ->
                        new StrictConnPool<>(
                                2,
                                2,
                                TimeValue.NEG_ONE_MILLISECOND,
                                PoolReusePolicy.LIFO,
                                null,
                                null,
                                clock))
        );
    }

    @ParameterizedTest(name = "{0} closeExpired is deterministic")
    @MethodSource("pools")
    void closeExpired_isDeterministic(
            final String name,
            final PoolTestSupport.PoolFactory<String, HttpConnection> factory) throws Exception {

        final HttpConnection conn1 = Mockito.mock(HttpConnection.class);
        final HttpConnection conn2 = Mockito.mock(HttpConnection.class);

        final PoolTestSupport.TestClock clock = new PoolTestSupport.TestClock(0L);

        try (final ManagedConnPool<String, HttpConnection> pool = factory.create(clock)) {

            final Future<PoolEntry<String, HttpConnection>> f1 =
                    pool.lease("somehost", null, Timeout.DISABLED, null);
            final Future<PoolEntry<String, HttpConnection>> f2 =
                    pool.lease("somehost", null, Timeout.DISABLED, null);

            Assertions.assertTrue(f1.isDone());
            final PoolEntry<String, HttpConnection> e1 = f1.get();
            Assertions.assertNotNull(e1);
            e1.assignConnection(conn1);

            Assertions.assertTrue(f2.isDone());
            final PoolEntry<String, HttpConnection> e2 = f2.get();
            Assertions.assertNotNull(e2);
            e2.assignConnection(conn2);

            e1.updateExpiry(TimeValue.of(1, TimeUnit.MILLISECONDS));
            pool.release(e1, true);

            clock.advanceMillis(200L);

            e2.updateExpiry(TimeValue.of(1000, TimeUnit.SECONDS));
            pool.release(e2, true);

            pool.closeExpired();

            Mockito.verify(conn1).close(CloseMode.GRACEFUL);
            Mockito.verify(conn2, Mockito.never()).close(ArgumentMatchers.any());

            final PoolStats totals = pool.getTotalStats();
            Assertions.assertEquals(1, totals.getAvailable());
            Assertions.assertEquals(0, totals.getLeased());
            Assertions.assertEquals(0, totals.getPending());
        }
    }

    @ParameterizedTest(name = "{0} closeIdle is deterministic")
    @MethodSource("pools")
    void closeIdle_isDeterministic(
            final String name,
            final PoolTestSupport.PoolFactory<String, HttpConnection> factory) throws Exception {

        final HttpConnection conn1 = Mockito.mock(HttpConnection.class);
        final HttpConnection conn2 = Mockito.mock(HttpConnection.class);

        final PoolTestSupport.TestClock clock = new PoolTestSupport.TestClock(0L);

        try (final ManagedConnPool<String, HttpConnection> pool = factory.create(clock)) {

            final Future<PoolEntry<String, HttpConnection>> f1 =
                    pool.lease("somehost", null, Timeout.DISABLED, null);
            final Future<PoolEntry<String, HttpConnection>> f2 =
                    pool.lease("somehost", null, Timeout.DISABLED, null);

            Assertions.assertTrue(f1.isDone());
            final PoolEntry<String, HttpConnection> e1 = f1.get();
            Assertions.assertNotNull(e1);
            e1.assignConnection(conn1);

            Assertions.assertTrue(f2.isDone());
            final PoolEntry<String, HttpConnection> e2 = f2.get();
            Assertions.assertNotNull(e2);
            e2.assignConnection(conn2);

            // IMPORTANT: updateState drives "updated" timestamp.
            e1.updateState(null);
            pool.release(e1, true);

            clock.advanceMillis(200L);

            e2.updateState(null);
            pool.release(e2, true);

            pool.closeIdle(TimeValue.ofMilliseconds(50));

            Mockito.verify(conn1).close(CloseMode.GRACEFUL);
            Mockito.verify(conn2, Mockito.never()).close(ArgumentMatchers.any());

            pool.closeIdle(TimeValue.ofMilliseconds(-1));

            Mockito.verify(conn2).close(CloseMode.GRACEFUL);
        }
    }

    @ParameterizedTest(name = "{0} request timeout validation is deterministic")
    @MethodSource("pools")
    void validatePendingRequests_isDeterministic(
            final String name,
            final PoolTestSupport.PoolFactory<String, HttpConnection> factory) throws Exception {

        final HttpConnection conn1 = Mockito.mock(HttpConnection.class);
        final PoolTestSupport.TestClock clock = new PoolTestSupport.TestClock(0L);

        try (final ManagedConnPool<String, HttpConnection> pool = factory.create(clock)) {

            // IMPORTANT: force 1/1 so second lease becomes pending.
            pool.setDefaultMaxPerRoute(1);
            pool.setMaxTotal(1);

            final Future<PoolEntry<String, HttpConnection>> f1 =
                    pool.lease("somehost", null, Timeout.ofMilliseconds(0), null);
            final Future<PoolEntry<String, HttpConnection>> f2 =
                    pool.lease("somehost", null, Timeout.ofMilliseconds(0), null);
            final Future<PoolEntry<String, HttpConnection>> f3 =
                    pool.lease("somehost", null, Timeout.ofMilliseconds(10), null);

            Assertions.assertTrue(f1.isDone());
            final PoolEntry<String, HttpConnection> e1 = f1.get();
            Assertions.assertNotNull(e1);
            e1.assignConnection(conn1);

            Assertions.assertFalse(f2.isDone());
            Assertions.assertFalse(f3.isDone());

            clock.advanceMillis(100L);

            Assertions.assertInstanceOf(StrictConnPool.class, pool);
            ((StrictConnPool<String, HttpConnection>) pool).validatePendingRequests();

            // f2 has Timeout=0 (= DISABLED) so it must remain pending
            Assertions.assertFalse(f2.isDone());
            // f3 must time out
            Assertions.assertTrue(f3.isDone());
        }
    }

}