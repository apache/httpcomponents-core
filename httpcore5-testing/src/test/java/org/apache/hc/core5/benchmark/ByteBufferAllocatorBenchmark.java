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

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.io.ByteBufferAllocator;
import org.apache.hc.core5.io.PooledByteBufferAllocator;
import org.apache.hc.core5.io.SimpleByteBufferAllocator;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class ByteBufferAllocatorBenchmark {

    /**
     * Per-thread state: each thread gets its own allocators.
     * This measures the best-case hot-path behaviour with no contention
     * on the pooled allocator.
     */
    @State(Scope.Thread)
    public static class ThreadLocalState {

        @Param({"1024", "8192", "65536"})
        public int bufferSize;

        @Param({"100"})
        public int iterations;

        public ByteBufferAllocator simpleAllocator;
        public ByteBufferAllocator pooledAllocator;

        public byte[] payload;

        @Setup
        public void setUp() {
            this.simpleAllocator = SimpleByteBufferAllocator.INSTANCE;
            this.pooledAllocator = new PooledByteBufferAllocator(
                    1024,        // min pooled size
                    256 * 1024,  // max pooled size
                    1024,        // maxGlobalPerBucket
                    64           // maxLocalPerBucket
            );
            this.payload = new byte[bufferSize / 2];
            for (int i = 0; i < this.payload.length; i++) {
                this.payload[i] = (byte) (i & 0xFF);
            }
        }

    }

    /**
     * Shared state: all threads share the same allocators.
     * This measures contention on the global buckets and any shared
     * structures inside the pooled allocator.
     */
    @State(Scope.Benchmark)
    public static class SharedState {

        @Param({"1024", "8192", "65536"})
        public int bufferSize;

        @Param({"100"})
        public int iterations;

        public ByteBufferAllocator simpleAllocator;
        public ByteBufferAllocator pooledAllocator;

        public byte[] payload;

        @Setup
        public void setUp() {
            this.simpleAllocator = SimpleByteBufferAllocator.INSTANCE;
            this.pooledAllocator = new PooledByteBufferAllocator(
                    1024,
                    256 * 1024,
                    1024,
                    64
            );
            this.payload = new byte[bufferSize / 2];
            for (int i = 0; i < this.payload.length; i++) {
                this.payload[i] = (byte) (i & 0xFF);
            }
        }

    }

    // --------- Per-thread allocators (your original semantics) ---------

    @Benchmark
    public void pooled_allocator_thread_local(final ThreadLocalState state, final Blackhole blackhole) {
        final int bufferSize = state.bufferSize;
        final int iterations = state.iterations;
        final ByteBufferAllocator allocator = state.pooledAllocator;
        final byte[] payload = state.payload;

        for (int i = 0; i < iterations; i++) {
            final ByteBuffer buf = allocator.allocate(bufferSize);
            buf.put(payload);
            buf.flip();
            blackhole.consume(buf.get(0));
            allocator.release(buf);
        }
    }

    @Benchmark
    public void simple_allocator_thread_local(final ThreadLocalState state, final Blackhole blackhole) {
        final int bufferSize = state.bufferSize;
        final int iterations = state.iterations;
        final ByteBufferAllocator allocator = state.simpleAllocator;
        final byte[] payload = state.payload;

        for (int i = 0; i < iterations; i++) {
            final ByteBuffer buf = allocator.allocate(bufferSize);
            buf.put(payload);
            buf.flip();
            blackhole.consume(buf.get(0));
            allocator.release(buf);
        }
    }

    // --------- Shared allocator, multi-threaded contention ---------

    /**
     * Run this with multiple threads, e.g.:
     * -t 4 or -t 8 on the JMH command line.
     */
    @Benchmark
    @Threads(4) // Override from the command line if you want: -t 8, etc.
    public void pooled_allocator_shared(final SharedState state, final Blackhole blackhole) {
        final int bufferSize = state.bufferSize;
        final int iterations = state.iterations;
        final ByteBufferAllocator allocator = state.pooledAllocator;
        final byte[] payload = state.payload;

        for (int i = 0; i < iterations; i++) {
            final ByteBuffer buf = allocator.allocate(bufferSize);
            buf.put(payload);
            buf.flip();
            blackhole.consume(buf.get(0));
            allocator.release(buf);
        }
    }

    @Benchmark
    @Threads(4)
    public void simple_allocator_shared(final SharedState state, final Blackhole blackhole) {
        final int bufferSize = state.bufferSize;
        final int iterations = state.iterations;
        final ByteBufferAllocator allocator = state.simpleAllocator;
        final byte[] payload = state.payload;

        for (int i = 0; i < iterations; i++) {
            final ByteBuffer buf = allocator.allocate(bufferSize);
            buf.put(payload);
            buf.flip();
            blackhole.consume(buf.get(0));
            allocator.release(buf);
        }
    }

}
