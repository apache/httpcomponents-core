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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

final class TestConnPoolClockInjection {

    static final class TestClock extends Clock {

        private final ZoneId zoneId;
        private final AtomicLong millis;

        TestClock(final long initialMillis) {
            this.zoneId = ZoneId.of("UTC");
            this.millis = new AtomicLong(initialMillis);
        }

        void advanceMillis(final long deltaMillis) {
            this.millis.addAndGet(deltaMillis);
        }

        @Override
        public ZoneId getZone() {
            return zoneId;
        }

        @Override
        public Clock withZone(final ZoneId zone) {
            return this;
        }

        @Override
        public long millis() {
            return millis.get();
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis());
        }

    }

    @ParameterizedTest
    @EnumSource(PoolConcurrencyPolicy.class)
    void closeIdleUsesInjectedClock(final PoolConcurrencyPolicy policy) throws Exception {
        final TestClock clock = new TestClock(0L);
        final ManagedConnPool<String, PoolTestSupport.DummyConn> pool =
                PoolTestSupport.createPool(policy, 1, 1, clock);

        try {
            final Future<PoolEntry<String, PoolTestSupport.DummyConn>> f1 =
                    pool.lease("r1", null, Timeout.ofSeconds(1), null);
            final PoolEntry<String, PoolTestSupport.DummyConn> e1 = f1.get(1, TimeUnit.SECONDS);
            assertNotNull(e1);

            final PoolTestSupport.DummyConn conn = new PoolTestSupport.DummyConn();
            e1.assignConnection(conn);
            pool.release(e1, true);

            pool.closeIdle(TimeValue.ofMilliseconds(1));
            assertFalse(conn.isClosed());
            assertEquals(1, pool.getTotalStats().getAvailable());
            assertEquals(0, pool.getTotalStats().getLeased());
            assertEquals(0, pool.getTotalStats().getPending());

            clock.advanceMillis(10_000L);

            pool.closeIdle(TimeValue.ofMilliseconds(1));
            assertEquals(0, pool.getTotalStats().getAvailable());
            assertEquals(0, pool.getTotalStats().getLeased());
            assertEquals(0, pool.getTotalStats().getPending());

            if (policy != PoolConcurrencyPolicy.OFFLOCK) {
                assertTrue(conn.isClosed());
            }
        } finally {
            pool.close(CloseMode.IMMEDIATE);
        }
    }

}