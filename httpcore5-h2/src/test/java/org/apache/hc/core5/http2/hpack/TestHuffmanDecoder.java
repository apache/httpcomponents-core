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
import java.util.Random;

import org.apache.hc.core5.util.ByteArrayBuffer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestHuffmanDecoder {

    @Test
    void testRfc7541Examples() throws Exception {
        assertDecoded("f1e3c2e5f23a6ba0ab90f4ff", "www.example.com");
        assertDecoded("a8eb10649cbf", "no-cache");
        assertDecoded("25a849e95ba97d7f", "custom-key");
        assertDecoded("25a849e95bb8e8b4bf", "custom-value");
    }

    @Test
    void testRoundTripAllByteValues() throws Exception {
        final byte[] input = new byte[256];
        for (int i = 0; i < input.length; i++) {
            input[i] = (byte) i;
        }
        assertRoundTrip(input);
    }

    @Test
    void testRoundTripRandomSequences() throws Exception {
        final Random random = new Random(0x7541L);
        for (int i = 0; i < 2000; i++) {
            final byte[] input = new byte[random.nextInt(1025)];
            random.nextBytes(input);
            assertRoundTrip(input);
        }
    }

    @Test
    void testAppendsToExistingOutput() throws Exception {
        final ByteArrayBuffer output = new ByteArrayBuffer(2);
        output.append('x');
        Huffman.DECODER.decode(output, ByteBuffer.wrap(hex("a8eb10649cbf")));
        Assertions.assertArrayEquals("xno-cache".getBytes(StandardCharsets.US_ASCII), output.toByteArray());
    }

    @Test
    void testGrowsSmallOutputBuffer() throws Exception {
        final byte[] input = createRepeated("aaaaaaaaaaaaaaaa", 512);
        final ByteArrayBuffer output = new ByteArrayBuffer(1);
        Huffman.DECODER.decode(output, ByteBuffer.wrap(encode(input)));
        Assertions.assertArrayEquals(input, output.toByteArray());
    }

    private static void assertDecoded(final String encoded, final String expected) throws Exception {
        Assertions.assertArrayEquals(expected.getBytes(StandardCharsets.US_ASCII), decode(hex(encoded)));
    }

    private static void assertRoundTrip(final byte[] input) throws Exception {
        Assertions.assertArrayEquals(input, decode(encode(input)));
    }

    private static byte[] encode(final byte[] input) {
        final ByteArrayBuffer encoded = new ByteArrayBuffer(Math.max(16, input.length));
        Huffman.ENCODER.encode(encoded, ByteBuffer.wrap(input));
        return encoded.toByteArray();
    }

    private static byte[] decode(final byte[] encoded) throws HPackException {
        final ByteArrayBuffer decoded = new ByteArrayBuffer(Math.max(1, encoded.length));
        Huffman.DECODER.decode(decoded, ByteBuffer.wrap(encoded));
        return decoded.toByteArray();
    }

    private static byte[] createRepeated(final String value, final int length) {
        final StringBuilder buffer = new StringBuilder(length);
        while (buffer.length() < length) {
            buffer.append(value);
        }
        buffer.setLength(length);
        return buffer.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] hex(final String value) {
        final byte[] result = new byte[value.length() / 2];
        for (int i = 0; i < result.length; i++) {
            final int offset = i * 2;
            result[i] = (byte) Integer.parseInt(value.substring(offset, offset + 2), 16);
        }
        return result;
    }

}
