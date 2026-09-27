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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.util.ByteArrayBuffer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestHPackDecoderRfc9113 {

    private static final int TABLE_SIZE = 4096;

    @Test
    void testHeaderListAtExactLimitIsAccepted() throws Exception {
        final Header header = new BasicHeader("x-test", "value");
        final HPackEncoder encoder = new HPackEncoder(TABLE_SIZE, StandardCharsets.US_ASCII);
        final ByteArrayBuffer block = new ByteArrayBuffer(64);
        encoder.encodeHeaders(block, Collections.singletonList(header), false);

        final HPackDecoder decoder = new HPackDecoder(TABLE_SIZE, StandardCharsets.US_ASCII);
        decoder.setMaxListSize(headerSize(header));

        final List<Header> decoded = decoder.decodeHeaders(asByteBuffer(block));

        Assertions.assertEquals(1, decoded.size());
        Assertions.assertEquals(header.getName(), decoded.get(0).getName());
        Assertions.assertEquals(header.getValue(), decoded.get(0).getValue());
    }

    @Test
    void testOversizedHeaderBlockIsFullyProcessed() throws Exception {
        final Header first = new BasicHeader("x-first", "0123456789");
        final Header second = new BasicHeader("x-second", "second-value");

        final HPackEncoder encoder = new HPackEncoder(TABLE_SIZE, StandardCharsets.US_ASCII);
        final ByteArrayBuffer oversizedBlock = new ByteArrayBuffer(128);
        encoder.encodeHeaders(oversizedBlock, Arrays.asList(first, second), false);

        final HPackDecoder decoder = new HPackDecoder(TABLE_SIZE, StandardCharsets.US_ASCII);
        decoder.setMaxListSize(headerSize(first) - 1);

        final ByteBuffer src = asByteBuffer(oversizedBlock);
        Assertions.assertThrows(HeaderListConstraintException.class, () -> decoder.decodeHeaders(src));
        Assertions.assertFalse(src.hasRemaining(), "The entire field block must be processed");

        // The encoder will represent the second header using the dynamic table.
        // Decoding it proves that the oversized block updated the decoder's
        // dynamic table even after the list-size limit was exceeded.
        final ByteArrayBuffer indexedBlock = new ByteArrayBuffer(32);
        encoder.encodeHeaders(indexedBlock, Collections.singletonList(second), false);

        decoder.setMaxListSize(Integer.MAX_VALUE);
        final List<Header> decoded = decoder.decodeHeaders(asByteBuffer(indexedBlock));

        Assertions.assertEquals(1, decoded.size());
        Assertions.assertEquals(second.getName(), decoded.get(0).getName());
        Assertions.assertEquals(second.getValue(), decoded.get(0).getValue());
    }

    @Test
    void testCompressionErrorAfterHeaderListLimitIsNotMasked() throws Exception {
        final Header header = new BasicHeader("x-test", "value");
        final HPackEncoder encoder = new HPackEncoder(TABLE_SIZE, StandardCharsets.US_ASCII);
        final ByteArrayBuffer block = new ByteArrayBuffer(64);
        encoder.encodeHeaders(block, Collections.singletonList(header), false);

        // A dynamic table size update is only valid at the beginning of a
        // field block. Appending one here must remain a compression error even
        // though the header-list limit has already been exceeded.
        block.append(0x20);

        final HPackDecoder decoder = new HPackDecoder(TABLE_SIZE, StandardCharsets.US_ASCII);
        decoder.setMaxListSize(headerSize(header) - 1);

        final HPackException exception = Assertions.assertThrows(
                HPackException.class,
                () -> decoder.decodeHeaders(asByteBuffer(block)));

        Assertions.assertFalse(exception instanceof HeaderListConstraintException);
        Assertions.assertEquals(
                "Dynamic table size update must appear at the beginning of a header block",
                exception.getMessage());
    }

    private static int headerSize(final Header header) {
        return header.getName().length() + header.getValue().length() + 32;
    }

    private static ByteBuffer asByteBuffer(final ByteArrayBuffer buffer) {
        return ByteBuffer.wrap(buffer.array(), 0, buffer.length());
    }

}
