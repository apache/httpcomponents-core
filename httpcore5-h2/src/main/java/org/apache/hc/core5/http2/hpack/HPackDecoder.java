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
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.ByteArrayBuffer;

public final class HPackDecoder {

    private static final String UNEXPECTED_EOS = "Unexpected end of HPACK data";
    private static final String MAX_LIMIT_EXCEEDED = "Max integer exceeded";

    private final ByteArrayBuffer contentBuf;
    private final CharsetDecoder charsetDecoder;
    private CharBuffer tmpBuf;

    public HPackDecoder(final Charset charset) {
        Args.notNull(charset, "Charset");
        this.contentBuf = new ByteArrayBuffer(256);
        this.charsetDecoder = charset.equals(StandardCharsets.US_ASCII) || charset.equals(StandardCharsets.ISO_8859_1) ? null : charset.newDecoder();
    }

    static int readByte(final ByteBuffer src) throws HPackException {

        if (!src.hasRemaining()) {
            throw new HPackException(UNEXPECTED_EOS);
        }
        return src.get() & 0xFF;
    }

    static int peekByte(final ByteBuffer src) throws HPackException {

        if (!src.hasRemaining()) {
            throw new HPackException(UNEXPECTED_EOS);
        }
        final int pos = src.position();
        final int b = src.get() & 0xFF;
        src.position(pos);
        return b;
    }

    static int decodeInt(final ByteBuffer src, final int n) throws HPackException {

        final int nbits = 0xFF >>> (8 - n);
        int value = readByte(src) & nbits;
        if (value < nbits) {
            return value;
        } else {
            int m = 0;
            while (m < 32) {
                final int b = readByte(src);
                if ((b & 0x80) != 0) {
                    value += (b & 0x7f) << m;
                    m += 7;
                } else {
                    if (m == 28 && (b & 0xf8) != 0) {
                        break;
                    }
                    value += b << m;
                    return value;
                }
            }
            throw new HPackException(MAX_LIMIT_EXCEEDED);
        }
    }

    static void decodePlainString(final ByteArrayBuffer buffer, final ByteBuffer src) throws HPackException {

        final int strLen = decodeInt(src, 7);
        if (strLen > src.remaining()) {
            throw new HPackException(UNEXPECTED_EOS);
        }
        if (src.hasArray()) {
            final byte[] b = src.array();
            final int off = src.position();
            buffer.append(b, off, strLen);
            src.position(off + strLen);
        } else {
            while (src.hasRemaining()) {
                buffer.append(src.get());
            }
        }
    }

    static void decodeHuffman(final ByteArrayBuffer buffer, final ByteBuffer src) throws HPackException {

        final int strLen = decodeInt(src, 7);
        if (strLen > src.remaining()) {
            throw new HPackException(UNEXPECTED_EOS);
        }
        final int limit = src.limit();
        src.limit(src.position() + strLen);
        Huffman.DECODER.decode(buffer, src);
        src.limit(limit);
    }

    void decodeString(final ByteArrayBuffer buffer, final ByteBuffer src) throws HPackException {

        final int firstByte = peekByte(src);
        if ((firstByte & 0x80) == 0x80) {
            decodeHuffman(buffer, src);
        } else {
            decodePlainString(buffer, src);
        }
    }

    private void clearState() {

        if (this.tmpBuf != null) {
            this.tmpBuf.clear();
        }
        if (this.charsetDecoder != null) {
            this.charsetDecoder.reset();
        }
        this.contentBuf.clear();
    }

    private void expandCapacity(final int capacity) {

        final CharBuffer previous = this.tmpBuf;
        this.tmpBuf = CharBuffer.allocate(capacity);
        previous.flip();
        this.tmpBuf.put(previous);
    }

    private void ensureCapacity(final int extra) {

        if (this.tmpBuf == null) {
            this.tmpBuf = CharBuffer.allocate(Math.max(256, extra));
        }
        final int requiredCapacity = this.tmpBuf.remaining() + extra;
        if (requiredCapacity > this.tmpBuf.capacity()) {
            expandCapacity(requiredCapacity);
        }
    }

    String decodeString(final ByteBuffer src) throws HPackException, CharacterCodingException {

        clearState();
        decodeString(this.contentBuf, src);
        if (this.charsetDecoder == null) {
            final StringBuilder buf = new StringBuilder(this.contentBuf.length());
            for (int i = 0; i < this.contentBuf.length(); i++) {
                buf.append((char) (this.contentBuf.byteAt(i) & 0xFF));
            }
            return buf.toString();
        } else {
            final ByteBuffer in = ByteBuffer.wrap(this.contentBuf.buffer(), 0, this.contentBuf.length());
            while (in.hasRemaining()) {
                ensureCapacity(in.remaining());
                final CoderResult result = this.charsetDecoder.decode(in, this.tmpBuf, true);
                if (result.isError()) {
                    result.throwException();
                }
            }
            ensureCapacity(8);
            final CoderResult result = this.charsetDecoder.flush(this.tmpBuf);
            if (result.isError()) {
                result.throwException();
            }
            this.tmpBuf.flip();
            return this.tmpBuf.toString();
        }
    }

}
