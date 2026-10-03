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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.io.ModalCloseable;
import org.apache.hc.core5.pool.DisposalCallback;
import org.apache.hc.core5.pool.ManagedConnPool;
import org.apache.hc.core5.pool.PoolEntry;
import org.apache.hc.core5.pool.PoolReusePolicy;
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
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 8, time = 1)
@Fork(2)
@Threads(8)
public class RouteSegmentedConnPoolBenchmark {

    private static final int MAX_TOTAL = 64;
    private static final int ROUTE_COUNT = 1024;
    private static final Timeout LEASE_TIMEOUT = Timeout.ofSeconds(5);
    private static final TimeValue TTL = TimeValue.NEG_ONE_MILLISECOND;
    private static final TimeValue KEEP_ALIVE = TimeValue.ofMinutes(10);
    private static final DummyConnection CONNECTION = new DummyConnection();

    public enum PoolType {
        STRICT,
        OFFLOCK
    }

    static ManagedConnPool<Integer, DummyConnection> createPool(
            final PoolType type,
            final int maxPerRoute,
            final int maxTotal) {

        final DisposalCallback<DummyConnection> disposal = DummyConnection::close;

        switch (type) {
            case STRICT:
                return new StrictConnPool<>(
                        maxPerRoute,
                        maxTotal,
                        TTL,
                        PoolReusePolicy.LIFO,
                        disposal,
                        null);
            case OFFLOCK:
                return new RouteSegmentedConnPool<>(
                        maxPerRoute,
                        maxTotal,
                        TTL,
                        PoolReusePolicy.LIFO,
                        disposal,
                        null);
            default:
                throw new IllegalStateException("Unexpected pool type: " + type);
        }
    }

    static PoolEntry<Integer, DummyConnection> lease(
            final ManagedConnPool<Integer, DummyConnection> pool,
            final Integer route) throws Exception {
        return pool.lease(route, null, LEASE_TIMEOUT, null).get();
    }

    static void makeReusable(final PoolEntry<Integer, DummyConnection> entry) {
        if (!entry.hasConnection()) {
            entry.assignConnection(CONNECTION);
        }
        entry.updateExpiry(KEEP_ALIVE);
    }

    @State(Scope.Benchmark)
    public static class HotState {

        @Param({"STRICT", "OFFLOCK"})
        public PoolType poolType;

        ManagedConnPool<Integer, DummyConnection> pool;
        Integer route;

        @Setup(Level.Trial)
        public void setup() throws Exception {
            pool = createPool(poolType, 1, MAX_TOTAL);
            route = Integer.valueOf(0);

            final PoolEntry<Integer, DummyConnection> entry = lease(pool, route);
            makeReusable(entry);
            pool.release(entry, true);
        }

        @TearDown(Level.Trial)
        public void tearDown() throws IOException {
            pool.close(CloseMode.IMMEDIATE);
        }
    }

    @State(Scope.Benchmark)
    public static class FullHotState {

        @Param({"STRICT", "OFFLOCK"})
        public PoolType poolType;

        ManagedConnPool<Integer, DummyConnection> pool;
        Integer route;

        @Setup(Level.Trial)
        public void setup() throws Exception {
            pool = createPool(poolType, 1, MAX_TOTAL);

            for (int i = 0; i < MAX_TOTAL; i++) {
                final PoolEntry<Integer, DummyConnection> entry =
                        lease(pool, i);
                makeReusable(entry);
                pool.release(entry, true);
            }

            route = 0;
        }

        @TearDown(Level.Trial)
        public void tearDown() throws IOException {
            pool.close(CloseMode.IMMEDIATE);
        }
    }

    @State(Scope.Thread)
    public static class BacklogFairnessState {

        private static final Integer SLOW_ROUTE = 0;
        private static final Integer COLD_ROUTE = 1;

        @Param({"STRICT", "OFFLOCK"})
        public PoolType poolType;

        ManagedConnPool<Integer, DummyConnection> pool;
        PoolEntry<Integer, DummyConnection> slow1;
        PoolEntry<Integer, DummyConnection> slow2;
        Future<PoolEntry<Integer, DummyConnection>> coldWaiter;
        Future<PoolEntry<Integer, DummyConnection>> sameRouteWaiter;
        PoolEntry<Integer, DummyConnection> coldEntry;

        @Setup(Level.Trial)
        public void setupPool() throws Exception {
            pool = createPool(poolType, 2, 2);

            // Start the OFFLOCK disposal machinery once per trial, not once per
            // measured invocation.
            final CountDownLatch disposed = new CountDownLatch(1);
            final PoolEntry<Integer, DummyConnection> warmup = lease(pool, -1);
            warmup.assignConnection(new DummyConnection(disposed));
            pool.release(warmup, false);
            if (!disposed.await(1, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Disposer warmup timed out");
            }
        }

        @Setup(Level.Invocation)
        public void setupInvocation() throws Exception {
            slow1 = lease(pool, SLOW_ROUTE);
            slow2 = lease(pool, SLOW_ROUTE);

            coldWaiter = pool.lease(COLD_ROUTE, null, LEASE_TIMEOUT, null);
            sameRouteWaiter = pool.lease(SLOW_ROUTE, null, LEASE_TIMEOUT, null);

            makeReusable(slow1);
            coldEntry = null;
        }

        @TearDown(Level.Invocation)
        public void tearDownInvocation() {
            if (sameRouteWaiter != null) {
                sameRouteWaiter.cancel(true);
                sameRouteWaiter = null;
            }

            if (coldWaiter != null) {
                if (coldWaiter.isDone()) {
                    try {
                        coldEntry = coldWaiter.get();
                    } catch (final Exception ignore) {
                    }
                } else {
                    coldWaiter.cancel(true);
                }
                coldWaiter = null;
            }

            if (coldEntry != null) {
                pool.release(coldEntry, false);
                coldEntry = null;
            }
            coldWaiter = null;

            if (slow1 != null) {
                pool.release(slow1, false);
                slow1 = null;
            }

            if (slow2 != null) {
                pool.release(slow2, false);
                slow2 = null;
            }

            if (coldEntry != null) {
                pool.release(coldEntry, false);
                coldEntry = null;
            }
        }

        @TearDown(Level.Trial)
        public void tearDownPool() throws IOException {
            pool.close(CloseMode.IMMEDIATE);
        }
    }

    @State(Scope.Benchmark)
    public static class ColdFullState {

        @Param({"STRICT", "OFFLOCK"})
        public PoolType poolType;

        ManagedConnPool<Integer, DummyConnection> pool;
        Integer[] routes;
        AtomicInteger sequence;

        @Setup(Level.Trial)
        public void setup() throws Exception {
            pool = createPool(poolType, 1, MAX_TOTAL);

            routes = new Integer[ROUTE_COUNT];
            for (int i = 0; i < ROUTE_COUNT; i++) {
                routes[i] = i;
            }

            // Fill the global pool entirely with reusable idle entries.
            for (int i = 0; i < MAX_TOTAL; i++) {
                final PoolEntry<Integer, DummyConnection> entry =
                        lease(pool, routes[i]);
                makeReusable(entry);
                pool.release(entry, true);
            }

            // Start outside the initially populated route window so the first
            // lease necessarily exercises cross-route capacity reclamation.
            sequence = new AtomicInteger(MAX_TOTAL);
        }

        Integer nextColdRoute() {
            return routes[sequence.getAndIncrement() & (ROUTE_COUNT - 1)];
        }

        @TearDown(Level.Trial)
        public void tearDown() throws IOException {
            pool.close(CloseMode.IMMEDIATE);
        }
    }

    @Benchmark
    public void hotRoute(
            final HotState state,
            final Blackhole blackhole) throws Exception {
        final PoolEntry<Integer, DummyConnection> entry =
                lease(state.pool, state.route);
        blackhole.consume(entry);
        state.pool.release(entry, true);
    }

    @Benchmark
    public void hotRouteAtFullPool(
            final FullHotState state,
            final Blackhole blackhole) throws Exception {
        final PoolEntry<Integer, DummyConnection> entry =
                lease(state.pool, state.route);
        blackhole.consume(entry);
        state.pool.release(entry, true);
    }

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @Threads(1)
    public void coldRouteProgressUnderSameRouteBacklog(
            final BacklogFairnessState state,
            final Blackhole blackhole) throws Exception {
        state.pool.release(state.slow1, true);
        state.slow1 = null;

        if (!state.coldWaiter.isDone()) {
            throw new IllegalStateException("Cold waiter was not completed inline");
        }

        state.coldEntry = state.coldWaiter.get(1, TimeUnit.SECONDS);
        blackhole.consume(state.coldEntry);
    }

    @Benchmark
    public void coldRouteAtFullPoolWithIdle(
            final ColdFullState state,
            final Blackhole blackhole) throws Exception {
        final PoolEntry<Integer, DummyConnection> entry =
                lease(state.pool, state.nextColdRoute());
        blackhole.consume(entry);
        makeReusable(entry);
        state.pool.release(entry, true);
    }

    static final class DummyConnection implements ModalCloseable {

        private final CountDownLatch closed;

        DummyConnection() {
            this(null);
        }

        DummyConnection(final CountDownLatch closed) {
            this.closed = closed;
        }

        @Override
        public void close(final CloseMode closeMode) {
            signalClosed();
        }

        @Override
        public void close() {
            signalClosed();
        }

        private void signalClosed() {
            if (closed != null) {
                closed.countDown();
            }
        }
    }
}
