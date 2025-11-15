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
package org.apache.hc.core5.io;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.util.Args;

/**
 * {@link ByteBufferAllocator} implementation backed by a bucketed buffer pool
 * with global queues and per-thread caches, inspired by Netty's pooled buffer
 * allocator.
 * <p>
 * Buffer capacities are rounded up to the nearest power of two between
 * {@code minCapacity} and {@code maxCapacity}. Released buffers are cached in
 * a per-thread cache (up to {@code maxLocalPerBucket}) and then in a global
 * bucket (up to {@code maxGlobalPerBucket}).
 * <p>
 * The returned buffers may have an underlying {@link ByteBuffer#capacity()}
 * greater than the requested capacity; however, their {@link ByteBuffer#limit(int)}
 * is set to the requested capacity.
 *
 * @since 5.4
 */
@Contract(threading = ThreadingBehavior.SAFE)
public final class PooledByteBufferAllocator implements ByteBufferAllocator {

    private static final class GlobalBucket {

        final ConcurrentLinkedQueue<ByteBuffer> queue;
        final AtomicInteger size;

        GlobalBucket() {
            this.queue = new ConcurrentLinkedQueue<>();
            this.size = new AtomicInteger();
        }

    }

    private static final class LocalCache {

        final Deque<ByteBuffer>[] heapBuckets;
        final Deque<ByteBuffer>[] directBuckets;

        LocalCache(final int bucketCount) {
            @SuppressWarnings("unchecked")
            final Deque<ByteBuffer>[] heap = new Deque[bucketCount];
            @SuppressWarnings("unchecked")
            final Deque<ByteBuffer>[] direct = new Deque[bucketCount];
            for (int i = 0; i < bucketCount; i++) {
                heap[i] = new ArrayDeque<>();
                direct[i] = new ArrayDeque<>();
            }
            this.heapBuckets = heap;
            this.directBuckets = direct;
        }

    }

    private final int minCapacity;
    private final int maxCapacity;
    private final int minShift;
    private final int[] bucketCapacities;
    private final GlobalBucket[] heapBuckets;
    private final GlobalBucket[] directBuckets;
    private final int maxGlobalPerBucket;
    private final int maxLocalPerBucket;

    private final ThreadLocal<LocalCache> localCache;

    /**
     * Creates a new pooled allocator.
     *
     * @param minCapacity        minimum capacity (inclusive) to pool, in bytes.
     * @param maxCapacity        maximum capacity (inclusive) to pool, in bytes.
     * @param maxGlobalPerBucket maximum number of buffers to keep per global
     *                           bucket and memory type (heap/direct).
     * @param maxLocalPerBucket  maximum number of buffers to keep per-thread
     *                           cache bucket and memory type (heap/direct).
     */
    public PooledByteBufferAllocator(
            final int minCapacity,
            final int maxCapacity,
            final int maxGlobalPerBucket,
            final int maxLocalPerBucket) {
        Args.notNegative(minCapacity, "Min capacity");
        Args.notNegative(maxCapacity, "Max capacity");
        Args.notNegative(maxGlobalPerBucket, "Max global per bucket");
        Args.notNegative(maxLocalPerBucket, "Max local per bucket");
        Args.check(maxCapacity >= minCapacity, "Max capacity must be >= min capacity");

        this.minCapacity = normalizeCapacity(minCapacity);
        this.maxCapacity = normalizeCapacity(maxCapacity);
        this.maxGlobalPerBucket = maxGlobalPerBucket;
        this.maxLocalPerBucket = maxLocalPerBucket;

        this.minShift = Integer.numberOfTrailingZeros(this.minCapacity);
        final int maxShift = Integer.numberOfTrailingZeros(this.maxCapacity);
        final int bucketCount = maxShift - this.minShift + 1;

        this.bucketCapacities = new int[bucketCount];
        this.heapBuckets = new GlobalBucket[bucketCount];
        this.directBuckets = new GlobalBucket[bucketCount];

        int capacity = this.minCapacity;
        for (int i = 0; i < bucketCount; i++) {
            this.bucketCapacities[i] = capacity;
            this.heapBuckets[i] = new GlobalBucket();
            this.directBuckets[i] = new GlobalBucket();
            capacity <<= 1;
        }

        this.localCache = ThreadLocal.withInitial(() -> new LocalCache(bucketCount));
    }

    private static int normalizeCapacity(final int capacity) {
        if (capacity <= 0) {
            return 1;
        }
        int normalized = 1;
        while (normalized < capacity) {
            normalized <<= 1;
        }
        return normalized;
    }

    /**
     * Returns the bucket index for the given capacity, or {@code -1} if the
     * capacity is greater than {@code maxCapacity}.
     * <p>
     * This implementation runs in O(1) using bit operations. It assumes
     * {@code minCapacity} and {@code maxCapacity} are powers of two.
     */
    private int bucketIndex(final int capacity) {
        if (capacity <= minCapacity) {
            return 0;
        }
        if (capacity > maxCapacity) {
            return -1;
        }
        // Ceil(log2(capacity)) using bit length of (capacity - 1).
        final int neededShift = 32 - Integer.numberOfLeadingZeros(capacity - 1);
        final int idx = neededShift - minShift;
        if (idx < 0 || idx >= bucketCapacities.length) {
            return -1;
        }
        return idx;
    }

    private ByteBuffer allocateInternal(final int requestedCapacity, final boolean direct) {
        Args.notNegative(requestedCapacity, "Buffer capacity");
        final int idx = bucketIndex(requestedCapacity);
        if (idx < 0) {
            // Not pooled: allocate exact requested capacity.
            return direct
                    ? ByteBuffer.allocateDirect(requestedCapacity)
                    : ByteBuffer.allocate(requestedCapacity);
        }

        final LocalCache cache = localCache.get();
        final Deque<ByteBuffer>[] localBuckets = direct
                ? cache.directBuckets
                : cache.heapBuckets;
        final Deque<ByteBuffer> local = localBuckets[idx];

        ByteBuffer buf = local.pollFirst();
        if (buf != null) {
            buf.clear();
            buf.limit(requestedCapacity);
            return buf;
        }

        final GlobalBucket[] globalArray = direct ? directBuckets : heapBuckets;
        final GlobalBucket global = globalArray[idx];

        buf = global.queue.poll();
        if (buf != null) {
            global.size.decrementAndGet();
            buf.clear();
            buf.limit(requestedCapacity);
            return buf;
        }

        final int bucketCapacity = bucketCapacities[idx];
        buf = direct
                ? ByteBuffer.allocateDirect(bucketCapacity)
                : ByteBuffer.allocate(bucketCapacity);
        buf.limit(requestedCapacity);
        return buf;
    }

    @Override
    public ByteBuffer allocate(final int capacity) {
        return allocateInternal(capacity, false);
    }

    @Override
    public ByteBuffer allocateDirect(final int capacity) {
        return allocateInternal(capacity, true);
    }

    @Override
    public void release(final ByteBuffer buffer) {
        if (buffer == null) {
            return;
        }
        final int capacity = buffer.capacity();
        final int idx = bucketIndex(capacity);
        if (idx < 0) {
            return;
        }

        final boolean direct = buffer.isDirect();
        final LocalCache cache = localCache.get();
        final Deque<ByteBuffer>[] localBuckets = direct
                ? cache.directBuckets
                : cache.heapBuckets;
        final Deque<ByteBuffer> local = localBuckets[idx];

        buffer.clear();
        buffer.limit(bucketCapacities[idx]);

        if (local.size() < maxLocalPerBucket) {
            local.addFirst(buffer);
            return;
        }

        if (maxGlobalPerBucket == 0) {
            return;
        }

        final GlobalBucket[] globalArray = direct ? directBuckets : heapBuckets;
        final GlobalBucket global = globalArray[idx];
        final int current = global.size.get();
        if (current >= maxGlobalPerBucket) {
            return;
        }
        global.queue.offer(buffer);
        global.size.incrementAndGet();
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder(128);
        buf.append("PooledByteBufferAllocator[")
                .append("minCapacity=").append(minCapacity)
                .append(", maxCapacity=").append(maxCapacity)
                .append(", maxGlobalPerBucket=").append(maxGlobalPerBucket)
                .append(", maxLocalPerBucket=").append(maxLocalPerBucket)
                .append(']');
        return buf.toString();
    }

}
