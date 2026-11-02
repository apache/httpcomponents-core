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

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

final class TestPoolPendingRequestExpiry {

    @ParameterizedTest
    @EnumSource(PoolConcurrencyPolicy.class)
    void closeExpiredFailsExpiredPendingRequest(final PoolConcurrencyPolicy policy) throws Exception {
        final TestingClock clock = new TestingClock(0L);
        final ManagedConnPool<String, PoolTestSupport.DummyConn> pool =
                PoolTestSupport.createPool(policy, 1, 1, clock);
        try {
            final PoolEntry<String, PoolTestSupport.DummyConn> leased =
                    pool.lease("r1", null, Timeout.ofSeconds(1), null).get(1, TimeUnit.SECONDS);
            assertNotNull(leased);
            final Future<PoolEntry<String, PoolTestSupport.DummyConn>> pending =
                    pool.lease("r1", null, Timeout.ofMilliseconds(100), null);

            clock.advanceMillis(10_000L);
            pool.closeExpired();

            final ExecutionException ex = assertThrows(ExecutionException.class,
                    () -> pending.get(5, TimeUnit.SECONDS));
            assertInstanceOf(TimeoutException.class, ex.getCause());
        } finally {
            pool.close(CloseMode.IMMEDIATE);
        }
    }

    @ParameterizedTest
    @EnumSource(PoolConcurrencyPolicy.class)
    void closeIdleFailsExpiredPendingRequest(final PoolConcurrencyPolicy policy) throws Exception {
        final TestingClock clock = new TestingClock(0L);
        final ManagedConnPool<String, PoolTestSupport.DummyConn> pool =
                PoolTestSupport.createPool(policy, 1, 1, clock);
        try {
            final PoolEntry<String, PoolTestSupport.DummyConn> leased =
                    pool.lease("r1", null, Timeout.ofSeconds(1), null).get(1, TimeUnit.SECONDS);
            assertNotNull(leased);
            final Future<PoolEntry<String, PoolTestSupport.DummyConn>> pending =
                    pool.lease("r1", null, Timeout.ofMilliseconds(100), null);

            clock.advanceMillis(10_000L);
            pool.closeIdle(TimeValue.ofMilliseconds(1));

            final ExecutionException ex = assertThrows(ExecutionException.class,
                    () -> pending.get(5, TimeUnit.SECONDS));
            assertInstanceOf(TimeoutException.class, ex.getCause());
        } finally {
            pool.close(CloseMode.IMMEDIATE);
        }
    }

    @ParameterizedTest
    @EnumSource(PoolConcurrencyPolicy.class)
    void expiredPendingRequestIsDoneAfterEviction(final PoolConcurrencyPolicy policy) throws Exception {
        final TestingClock clock = new TestingClock(0L);
        final ManagedConnPool<String, PoolTestSupport.DummyConn> pool =
                PoolTestSupport.createPool(policy, 1, 1, clock);
        try {
            pool.lease("r1", null, Timeout.ofSeconds(1), null).get(1, TimeUnit.SECONDS);
            final Future<PoolEntry<String, PoolTestSupport.DummyConn>> pending =
                    pool.lease("r1", null, Timeout.ofMilliseconds(100), null);

            clock.advanceMillis(10_000L);
            pool.closeExpired();

            if (policy != PoolConcurrencyPolicy.OFFLOCK) {
                assertTrue(pending.isDone(), "Future must be completed by the eviction call itself");
            }
        } finally {
            pool.close(CloseMode.IMMEDIATE);
        }
    }

}
