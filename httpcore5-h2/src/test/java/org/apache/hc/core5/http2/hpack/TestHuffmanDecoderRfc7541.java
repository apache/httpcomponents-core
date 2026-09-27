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

import org.apache.hc.core5.util.ByteArrayBuffer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestHuffmanDecoderRfc7541 {

    @Test
    void testRejectsPaddingLongerThanSevenBits() {
        Assertions.assertThrows(HPackException.class, () -> decode((byte) 0xff));
        Assertions.assertThrows(HPackException.class, () -> decode((byte) 0xff, (byte) 0xff));
    }

    @Test
    void testValidEncodedValueStillDecodes() throws Exception {
        final byte[] input = "www.example.com".getBytes(StandardCharsets.US_ASCII);
        final ByteArrayBuffer encoded = new ByteArrayBuffer(32);
        Huffman.ENCODER.encode(encoded, ByteBuffer.wrap(input));

        final ByteArrayBuffer decoded = new ByteArrayBuffer(32);
        Huffman.DECODER.decode(decoded, ByteBuffer.wrap(encoded.array(), 0, encoded.length()));

        Assertions.assertArrayEquals(input, Arrays.copyOf(decoded.array(), decoded.length()));
    }

    private static void decode(final byte... encoded) throws HPackException {
        final ByteArrayBuffer decoded = new ByteArrayBuffer(16);
        Huffman.DECODER.decode(decoded, ByteBuffer.wrap(encoded));
    }

}
