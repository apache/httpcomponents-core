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
package org.apache.hc.core5.http2.hpack;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.apache.hc.core5.util.ByteArrayBuffer;
import org.junit.jupiter.api.Test;

class TestHPackEncoder {

    @Test
    void testMultipleTableSizeUpdatesSignalMinimumFirstThenFinal() throws Exception {
        final OutboundDynamicTable dynamicTable = new OutboundDynamicTable(4096);
        final HPackEncoder encoder = new HPackEncoder(dynamicTable, StandardCharsets.US_ASCII);
        final ByteArrayBuffer dst = new ByteArrayBuffer(128);

        encoder.setMaxTableSize(1024);
        encoder.setMaxTableSize(2048);

        // No local resize until the next header block is encoded.
        assertEquals(4096, dynamicTable.getMaxSize());
        assertEquals(2048, encoder.getMaxTableSize());

        encoder.encodeHeader(dst, "x-test", "abc", false, false, false);

        // First 6 bytes must be:
        //   1024 => 0x3f 0xe1 0x07
        //   2048 => 0x3f 0xe1 0x0f
        final byte[] actual = Arrays.copyOf(dst.array(), dst.length());
        assertArrayEquals(new byte[]{
                0x3f, (byte) 0xe1, 0x07,
                0x3f, (byte) 0xe1, 0x0f
        }, Arrays.copyOf(actual, 6));

        // Local encoder state must also end at the final advertised size.
        assertEquals(2048, dynamicTable.getMaxSize());
    }

    @Test
    void testShrinkThenRestoreOriginalStillEmitsTwoUpdates() throws Exception {
        final OutboundDynamicTable dynamicTable = new OutboundDynamicTable(4096);
        final HPackEncoder encoder = new HPackEncoder(dynamicTable, StandardCharsets.US_ASCII);
        final ByteArrayBuffer dst = new ByteArrayBuffer(128);

        encoder.setMaxTableSize(1024);
        encoder.setMaxTableSize(4096);

        encoder.encodeHeader(dst, "x-test", "abc", false, false, false);

        final byte[] actual = Arrays.copyOf(dst.array(), dst.length());
        assertArrayEquals(new byte[]{
                0x3f, (byte) 0xe1, 0x07,
                0x3f, (byte) 0xe1, 0x1f
        }, Arrays.copyOf(actual, 6));

        assertEquals(4096, dynamicTable.getMaxSize());
    }

    @Test
    void testTableSizeUpdateZeroThenRestore() throws Exception {
        final OutboundDynamicTable dynamicTable = new OutboundDynamicTable(4096);
        final HPackEncoder encoder = new HPackEncoder(dynamicTable, StandardCharsets.US_ASCII);
        final ByteArrayBuffer dst = new ByteArrayBuffer(128);

        encoder.setMaxTableSize(0);
        encoder.setMaxTableSize(4096);

        encoder.encodeHeader(dst, "x-test", "abc", false, false, false);

        final byte[] actual = Arrays.copyOf(dst.array(), dst.length());
        assertArrayEquals(new byte[]{
                0x20,
                0x3f, (byte) 0xe1, 0x1f
        }, Arrays.copyOf(actual, 4));

        assertEquals(4096, dynamicTable.getMaxSize());
    }

    @Test
    void testTableSizeUpdateIsNotRepeatedAcrossHeaderBlocks() throws Exception {
        final OutboundDynamicTable dynamicTable = new OutboundDynamicTable(4096);
        final HPackEncoder encoder = new HPackEncoder(dynamicTable, StandardCharsets.US_ASCII);

        encoder.setMaxTableSize(1024);

        final ByteArrayBuffer dst1 = new ByteArrayBuffer(128);
        encoder.encodeHeader(dst1, "x-a", "1", false, false, false);

        final ByteArrayBuffer dst2 = new ByteArrayBuffer(128);
        encoder.encodeHeader(dst2, "x-b", "2", false, false, false);

        final byte[] first = Arrays.copyOf(dst1.array(), dst1.length());
        assertArrayEquals(new byte[]{
                0x3f, (byte) 0xe1, 0x07
        }, Arrays.copyOf(first, 3));

        final byte[] second = Arrays.copyOf(dst2.array(), dst2.length());
        assertNotEquals(0x20, second[0] & 0xe0);
    }

}