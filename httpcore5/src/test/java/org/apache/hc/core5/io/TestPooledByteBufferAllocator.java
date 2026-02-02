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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;

class TestPooledByteBufferAllocator {

    @Test
    void testNonPooledExactCapacity() {
        final PooledByteBufferAllocator allocator = new PooledByteBufferAllocator(
                64, 1024, 16, 8);

        final int requested = 2048; // > maxCapacity → non-pooled

        final ByteBuffer buf = allocator.allocate(requested);
        assertNotNull(buf);
        assertFalse(buf.isDirect());
        assertEquals(requested, buf.capacity());
        assertEquals(requested, buf.limit());

        allocator.release(buf); // should be a no-op
    }

    @Test
    void testPooledRoundedCapacityAndLimit() {
        final PooledByteBufferAllocator allocator = new PooledByteBufferAllocator(
                64, 1024, 16, 8);

        final int requested = 100;

        final ByteBuffer buf = allocator.allocate(requested);
        assertNotNull(buf);
        assertFalse(buf.isDirect());

        // With min=64, max=1024, 100 should land in the 128 bucket.
        assertEquals(128, buf.capacity());
        assertEquals(requested, buf.limit());
        assertEquals(0, buf.position());

        allocator.release(buf);
    }

    @Test
    void testPooledReusesLocalCache() {
        final PooledByteBufferAllocator allocator = new PooledByteBufferAllocator(
                64, 1024, 16, 8);

        final int requested = 128;

        final ByteBuffer buf1 = allocator.allocate(requested);
        assertNotNull(buf1);
        assertFalse(buf1.isDirect());
        assertEquals(requested, buf1.limit());

        allocator.release(buf1);

        final ByteBuffer buf2 = allocator.allocate(requested);
        assertSame(buf1, buf2);
        assertEquals(0, buf2.position());
        assertEquals(requested, buf2.limit());
    }

    @Test
    void testReleaseResetsLimitForNextUse() {
        final PooledByteBufferAllocator allocator = new PooledByteBufferAllocator(
                64, 1024, 16, 8);

        final int requested = 200;

        final ByteBuffer buf1 = allocator.allocate(requested);
        assertEquals(requested, buf1.limit());

        // Mess with position/limit to ensure release() really resets them.
        buf1.position(requested);
        buf1.limit(requested);
        allocator.release(buf1);

        final ByteBuffer buf2 = allocator.allocate(requested);
        assertSame(buf1, buf2);
        assertEquals(0, buf2.position());
        assertEquals(requested, buf2.limit());
    }

    @Test
    void testDirectAndHeapArePooledSeparately() {
        final PooledByteBufferAllocator allocator = new PooledByteBufferAllocator(
                64, 1024, 16, 8);

        final int requested = 128;

        final ByteBuffer direct1 = allocator.allocateDirect(requested);
        assertTrue(direct1.isDirect());
        allocator.release(direct1);

        final ByteBuffer direct2 = allocator.allocateDirect(requested);
        assertTrue(direct2.isDirect());
        assertSame(direct1, direct2);

        final ByteBuffer heap = allocator.allocate(requested);
        assertFalse(heap.isDirect());
        assertNotSame(direct2, heap);
    }

    @Test
    void testToStringDoesNotThrow() {
        final PooledByteBufferAllocator allocator = new PooledByteBufferAllocator(
                64, 1024, 16, 8);

        final String s = allocator.toString();
        assertNotNull(s);
        assertTrue(s.contains("PooledByteBufferAllocator"));
    }

}
