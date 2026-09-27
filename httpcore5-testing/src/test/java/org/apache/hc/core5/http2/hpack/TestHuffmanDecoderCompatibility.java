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
import java.util.Arrays;
import java.util.Random;

import org.apache.hc.core5.util.ByteArrayBuffer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestHuffmanDecoderCompatibility {

    @Test
    void testSameBehaviorAsLegacyDecoderForValidInput() throws Exception {
        final LegacyHuffmanDecoder legacy = new LegacyHuffmanDecoder(Huffman.CODES, Huffman.LENGTHS);
        for (int value = 0; value < 256; value++) {
            assertSameResult(legacy, encode(new byte[] {(byte) value}));
        }

        final Random random = new Random(0x5a17eL);
        for (int i = 0; i < 10000; i++) {
            final byte[] input = new byte[random.nextInt(256)];
            random.nextBytes(input);
            assertSameResult(legacy, encode(input));
        }
    }

    private static byte[] encode(final byte[] input) {
        final ByteArrayBuffer encoded = new ByteArrayBuffer(Math.max(1, input.length));
        Huffman.ENCODER.encode(encoded, ByteBuffer.wrap(input));
        return Arrays.copyOf(encoded.array(), encoded.length());
    }

    private static void assertSameResult(final LegacyHuffmanDecoder legacy, final byte[] encoded) throws Exception {
        final Result expected = decodeLegacy(legacy, encoded);
        final Result actual = decodeDfa(encoded);
        Assertions.assertEquals(expected.failed, actual.failed);
        Assertions.assertArrayEquals(expected.output, actual.output);
    }

    private static Result decodeLegacy(final LegacyHuffmanDecoder legacy, final byte[] encoded) {
        final ByteArrayBuffer output = new ByteArrayBuffer(Math.max(1, encoded.length));
        try {
            legacy.decode(output, ByteBuffer.wrap(encoded));
            return new Result(false, output.toByteArray());
        } catch (final RuntimeException | HPackException ex) {
            return new Result(true, output.toByteArray());
        }
    }

    private static Result decodeDfa(final byte[] encoded) {
        final ByteArrayBuffer output = new ByteArrayBuffer(Math.max(1, encoded.length));
        try {
            Huffman.DECODER.decode(output, ByteBuffer.wrap(encoded));
            return new Result(false, output.toByteArray());
        } catch (final RuntimeException | HPackException ex) {
            return new Result(true, output.toByteArray());
        }
    }

    private static final class Result {

        private final boolean failed;
        private final byte[] output;

        Result(final boolean failed, final byte[] output) {
            this.failed = failed;
            this.output = output;
        }
    }

}
