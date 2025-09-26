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
package org.apache.hc.core5.benchmark;


import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.io.ModalCloseable;
import org.apache.hc.core5.pool.DisposalCallback;
import org.apache.hc.core5.pool.LaxConnPool;
import org.apache.hc.core5.pool.ManagedConnPool;
import org.apache.hc.core5.pool.PoolEntry;
import org.apache.hc.core5.pool.PoolReusePolicy;
import org.apache.hc.core5.pool.PoolStats;
import org.apache.hc.core5.pool.RouteSegmentedConnPool;
import org.apache.hc.core5.pool.StrictConnPool;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Compare StrictConnPool, LaxConnPool, and RouteSegmentedConnPool (“OFFLOCK”)
 * under different contention patterns and slow-disposal rates.
 */
@BenchmarkMode({Mode.Throughput, Mode.SampleTime})
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class RoutePoolsJmh {

    /**
     * Minimal connection that can simulate slow close.
     */
    public static final class FakeConn implements ModalCloseable {
        private final int closeDelayMs;

        public FakeConn(final int closeDelayMs) {
            this.closeDelayMs = closeDelayMs;
        }

        @Override
        public void close(final CloseMode closeMode) {
            if (closeDelayMs <= 0) {
                return;
            }
            try {
                Thread.sleep(closeDelayMs);
            } catch (final InterruptedException ignore) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void close() throws IOException {

        }
    }

    /**
     * All benchmark parameters & shared state live here (required by JMH).
     */
    @State(Scope.Benchmark)
    public static class BenchState {

        /**
         * Which pool to benchmark.
         * STRICT  -> StrictConnPool
         * LAX     -> LaxConnPool
         * OFFLOCK -> RouteSegmentedConnPool
         */
        @Param({"STRICT", "LAX", "OFFLOCK"})
        public String policy;

        /**
         * Number of distinct routes to spread load across.
         * 1 = hot single route; 10 = multi-route scenario.
         */
        @Param({"1", "10"})
        public int routes;

        /**
         * Percent (0..100) of releases that will be non-reusable,
         * triggering a discard (and thus a potentially slow close).
         */
        @Param({"0", "5", "20"})
        public int slowClosePct;

        /**
         * Sleep (ms) when a connection is discarded (slow close path).
         */
        @Param({"0", "200"})
        public int closeSleepMs;

        /**
         * Max total, default per-route — tuned to create contention.
         */
        @Param({"32"})
        public int maxTotal;
        @Param({"8"})
        public int defMaxPerRoute;

        /**
         * Keep-alive on reusable releases.
         */
        @Param({"5000"})
        public int keepAliveMs;

        ManagedConnPool<String, FakeConn> pool;
        String[] routeKeys;
        DisposalCallback<FakeConn> disposal;

        @Setup(Level.Trial)
        public void setUp() {
            // routes list
            routeKeys = new String[routes];
            for (int i = 0; i < routes; i++) {
                routeKeys[i] = "route-" + i;
            }

            disposal = (c, m) -> {
                if (c != null) {
                    c.close(m);
                }
            };

            final TimeValue ttl = TimeValue.NEG_ONE_MILLISECOND;

            switch (policy.toUpperCase(Locale.ROOT)) {
                case "STRICT":
                    pool = new StrictConnPool<>(
                            defMaxPerRoute,
                            maxTotal,
                            ttl,
                            PoolReusePolicy.LIFO,
                            disposal,
                            null);
                    break;
                case "LAX":
                    pool = new LaxConnPool<>(
                            defMaxPerRoute,
                            ttl,
                            PoolReusePolicy.LIFO,
                            disposal,
                            null);
                    pool.setMaxTotal(maxTotal);
                    break;
                case "OFFLOCK":
                    pool = new RouteSegmentedConnPool<>(
                            defMaxPerRoute,
                            maxTotal,
                            ttl,
                            PoolReusePolicy.LIFO,
                            disposal);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown policy: " + policy);
            }
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            if (pool != null) {
                pool.close(CloseMode.IMMEDIATE);
            }
        }

        String pickRoute() {
            final int idx = ThreadLocalRandom.current().nextInt(routeKeys.length);
            return routeKeys[idx];
        }

        boolean shouldDiscard() {
            if (slowClosePct <= 0) return false;
            return ThreadLocalRandom.current().nextInt(100) < slowClosePct;
        }
    }

    /**
     * Lease+release on a randomly chosen route.
     * Mix of reusable and non-reusable releases (to trigger discard/close).
     */
    @Benchmark
    @Threads(50)
    public void leaseReleaseMixed(final BenchState s) throws Exception {
        try {
            final Future<PoolEntry<String, FakeConn>> f = s.pool.lease(s.pickRoute(), null, Timeout.ofMilliseconds(500), null);
            final PoolEntry<String, FakeConn> e = f.get(500, TimeUnit.MILLISECONDS);
            if (!e.hasConnection()) e.assignConnection(new FakeConn(s.closeSleepMs));
            final boolean reusable = !s.shouldDiscard();
            if (reusable) {
                e.updateExpiry(TimeValue.ofMilliseconds(s.keepAliveMs));
                s.pool.release(e, true);
            } else {
                s.pool.release(e, false);
            }
        } catch (final IllegalStateException ignored) {

        }
    }


    /**
     * Optional stats probe to ensure the benchmark does "something".
     * Not a measured benchmark; use only for sanity runs.
     */
    @Benchmark
    @Threads(1)
    @OperationsPerInvocation(1)
    @BenchmarkMode(Mode.SingleShotTime)
    public void statsProbe(final BenchState s, final org.openjdk.jmh.infra.Blackhole bh) {
        final PoolStats stats = s.pool.getTotalStats();
        bh.consume(stats.getAvailable());
        bh.consume(stats.getLeased());
        bh.consume(stats.getPending());
    }
}
