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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Test;

final class TestLaxConnPoolCancelDuringServiceDoesNotLeak {

    @Test
    @org.junit.jupiter.api.Timeout(value = 10, unit = TimeUnit.SECONDS)
    void cancelDuringServiceMustNotLeakEntry() throws Exception {
        final CountDownLatch onLeaseReached = new CountDownLatch(1);
        final CountDownLatch allowOnLeaseReturn = new CountDownLatch(1);
        final AtomicBoolean blockNextOnLease = new AtomicBoolean(true);

        final ConnPoolListener<String> listener = new ConnPoolListener<String>() {

            @Override
            public void onLease(final String route, final ConnPoolStats<String> stats) {
                if (blockNextOnLease.compareAndSet(true, false)) {
                    onLeaseReached.countDown();
                    try {
                        allowOnLeaseReturn.await(5, TimeUnit.SECONDS);
                    } catch (final InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }
            }

            @Override
            public void onRelease(final String route, final ConnPoolStats<String> stats) {
            }

        };

        final LaxConnPool<String, PoolTestSupport.DummyConn> pool = new LaxConnPool<>(
                1,
                TimeValue.NEG_ONE_MILLISECOND,
                PoolReusePolicy.LIFO,
                PoolTestSupport.DISPOSAL,
                listener);

        final String route = "r1";

        try {
            pool.setMaxPerRoute(route, 1);

            final PoolEntry<String, PoolTestSupport.DummyConn> leased1 =
                    pool.lease(route, null, Timeout.ofSeconds(2), null).get(2, TimeUnit.SECONDS);
            leased1.assignConnection(new PoolTestSupport.DummyConn());

            final Future<PoolEntry<String, PoolTestSupport.DummyConn>> waiter =
                    pool.lease(route, null, Timeout.ofSeconds(30), null);

            final Thread releaser = new Thread(() -> pool.release(leased1, true), "releaser");
            releaser.start();

            assertTrue(onLeaseReached.await(5, TimeUnit.SECONDS), "Did not reach onLease hook");
            assertTrue(waiter.cancel(true), "Waiter cancel failed");

            allowOnLeaseReturn.countDown();
            releaser.join(5000);
            assertFalse(releaser.isAlive(), "Releaser thread stuck");

            assertEquals(0, pool.getStats(route).getLeased(), "Entry leaked as leased");

        } finally {
            pool.close(CloseMode.IMMEDIATE);
        }
    }

}