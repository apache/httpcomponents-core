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

import org.apache.hc.core5.util.ByteArrayBuffer;

/**
 * This Huffman codec implementation has been derived from Twitter HPack project
 * (https://github.com/twitter/hpack)
 */
final class HuffmanEncoder {

    private final int[] codes;
    private final byte[] lengths;

    HuffmanEncoder(final int[] codes, final byte[] lengths) {
        this.codes = codes;
        this.lengths = lengths;
    }

    void encode(final ByteArrayBuffer out, final ByteBuffer src) {

        long current = 0;
        int n = 0;

        while (src.hasRemaining()) {
            final int b = src.get() & 0xFF;
            final int code = codes[b];
            final int nbits = lengths[b];

            current <<= nbits;
            current |= code;
            n += nbits;

            while (n >= 8) {
                n -= 8;
                out.append((int)(current >> n));
            }
        }

        if (n > 0) {
            current <<= (8 - n);
            current |= (0xFF >>> n); // this should be EOS symbol
            out.append((int) current);
        }
    }

    void encode(final ByteArrayBuffer out, final CharSequence src, final int off, final int len) {

        long current = 0;
        int n = 0;

        for (int i = 0; i < len; i++) {
            final int b = src.charAt(off + i) & 0xFF;
            final int code = codes[b];
            final int nbits = lengths[b];

            current <<= nbits;
            current |= code;
            n += nbits;

            while (n >= 8) {
                n -= 8;
                out.append((int)(current >> n));
            }
        }

        if (n > 0) {
            current <<= (8 - n);
            current |= (0xFF >>> n); // this should be EOS symbol
            out.append((int) current);
        }
    }

}
