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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Test;

final class TestConnPoolDifferentialStrictOfflock {

    @Test
    @org.junit.jupiter.api.Timeout(value = 60, unit = TimeUnit.SECONDS)
    void strictAndOfflockShouldMatchForSynchronousTrace() throws Exception {
        assumeTrue(Boolean.getBoolean("hc.pool.diff"), "diff disabled (use -Dhc.pool.diff=true)");

        final long seed = Long.parseLong(System.getProperty("hc.pool.diff.seed", "1"));
        final int steps = Integer.parseInt(System.getProperty("hc.pool.diff.steps", "200"));
        final int routeCount = Integer.parseInt(System.getProperty("hc.pool.diff.routes", "8"));

        final ManagedConnPool<String, PoolTestSupport.DummyConn> strict =
                PoolTestSupport.PoolType.STRICT.createPool(10, 200);
        final ManagedConnPool<String, PoolTestSupport.DummyConn> offlock =
                PoolTestSupport.PoolType.OFFLOCK.createPool(10, 200);

        final List<String> routes = new ArrayList<>(routeCount);
        for (int i = 0; i < routeCount; i++) {
            routes.add("r" + i);
            strict.setMaxPerRoute("r" + i, 10);
            offlock.setMaxPerRoute("r" + i, 10);
        }
        strict.setMaxTotal(200);
        offlock.setMaxTotal(200);

        final SplittableRandom rnd = new SplittableRandom(seed);

        try {
            for (int i = 0; i < steps; i++) {
                final String route = routes.get(rnd.nextInt(routes.size()));
                final Object state = (rnd.nextInt(4) == 0) ? null : Integer.valueOf(rnd.nextInt(3));

                final PoolEntry<String, PoolTestSupport.DummyConn> e1 =
                        strict.lease(route, state, Timeout.ofSeconds(2), null).get(2, TimeUnit.SECONDS);
                if (!e1.hasConnection()) {
                    e1.assignConnection(new PoolTestSupport.DummyConn());
                }
                e1.updateState(state);

                final PoolEntry<String, PoolTestSupport.DummyConn> e2 =
                        offlock.lease(route, state, Timeout.ofSeconds(2), null).get(2, TimeUnit.SECONDS);
                if (!e2.hasConnection()) {
                    e2.assignConnection(new PoolTestSupport.DummyConn());
                }
                e2.updateState(state);

                if (rnd.nextInt(6) == 0) {
                    e1.updateExpiry(TimeValue.ofMilliseconds(0));
                    e2.updateExpiry(TimeValue.ofMilliseconds(0));
                }

                if (rnd.nextInt(8) == 0) {
                    strict.closeExpired();
                    offlock.closeExpired();
                }

                strict.release(e1, true);
                offlock.release(e2, true);

                final PoolStats s1 = strict.getTotalStats();
                final PoolStats s2 = offlock.getTotalStats();

                assertEquals(s1.getLeased(), s2.getLeased(), "leased mismatch at step " + i);
                assertEquals(s1.getPending(), s2.getPending(), "pending mismatch at step " + i);
                assertEquals(s1.getAvailable(), s2.getAvailable(), "available mismatch at step " + i);
            }
        } finally {
            strict.close(CloseMode.IMMEDIATE);
            offlock.close(CloseMode.IMMEDIATE);
        }
    }

}