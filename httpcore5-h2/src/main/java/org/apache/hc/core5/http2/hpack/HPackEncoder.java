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
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.ByteArrayBuffer;

public final class HPackEncoder {

    private final ByteArrayBuffer huffmanBuf;
    private final CharsetEncoder charsetEncoder;
    private ByteBuffer tmpBuf;

    public HPackEncoder(final Charset charset) {
        Args.notNull(charset, "Charset");
        this.huffmanBuf = new ByteArrayBuffer(128);
        this.charsetEncoder = charset.equals(StandardCharsets.US_ASCII) || charset.equals(StandardCharsets.ISO_8859_1) ? null : charset.newEncoder();
    }

    static void encodeInt(final ByteArrayBuffer buffer, final int n, final int i, final int mask) {

        final int nbits = 0xFF >>> (8 - n);
        int value = i;
        if (value < nbits) {
            buffer.append(i | mask);
        } else {
            buffer.append(nbits | mask);
            value -= nbits;

            while (value >= 0x80) {
                buffer.append((value & 0x7F) | 0x80);
                value >>>= 7;
            }
            buffer.append(value);
        }
    }

    static void encodeHuffman(final ByteArrayBuffer buffer, final ByteBuffer src) throws HPackException {

        Huffman.ENCODER.encode(buffer, src);
    }

    void encodeString(final ByteArrayBuffer buffer, final ByteBuffer src, final boolean huffman) throws HPackException {

        final int strLen = src.remaining();
        if (huffman) {
            this.huffmanBuf.clear();
            this.huffmanBuf.ensureCapacity(strLen);
            Huffman.ENCODER.encode(this.huffmanBuf, src);
            buffer.ensureCapacity(this.huffmanBuf.length() + 8);
            encodeInt(buffer, 7, this.huffmanBuf.length(), 0x80);
            buffer.append(this.huffmanBuf.buffer(), 0, this.huffmanBuf.length());
        } else {
            buffer.ensureCapacity(strLen + 8);
            encodeInt(buffer, 7, strLen, 0x0);
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
    }

    private void clearState() {

        if (this.tmpBuf != null) {
            this.tmpBuf.clear();
        }
        if (this.charsetEncoder != null) {
            this.charsetEncoder.reset();
        }
    }

    private void expandCapacity(final int capacity) {

        final ByteBuffer previous = this.tmpBuf;
        this.tmpBuf = ByteBuffer.allocate(capacity);
        previous.flip();
        this.tmpBuf.put(previous);
    }

    private void ensureCapacity(final int extra) {

        if (this.tmpBuf == null) {
            this.tmpBuf = ByteBuffer.allocate(Math.max(256, extra));
        }
        final int requiredCapacity = this.tmpBuf.remaining() + extra;
        if (requiredCapacity > this.tmpBuf.capacity()) {
            expandCapacity(requiredCapacity);
        }
    }

    void encodeString(
            final ByteArrayBuffer buffer,
            final CharSequence charSequence, final int off, final int len,
            final boolean huffman) throws HPackException, CharacterCodingException {

        clearState();
        if (this.charsetEncoder == null) {
            if (huffman) {
                this.huffmanBuf.clear();
                this.huffmanBuf.ensureCapacity(len);
                Huffman.ENCODER.encode(this.huffmanBuf, charSequence, off, len);
                buffer.ensureCapacity(this.huffmanBuf.length() + 8);
                encodeInt(buffer, 7, this.huffmanBuf.length(), 0x80);
                buffer.append(this.huffmanBuf.buffer(), 0, this.huffmanBuf.length());
            } else {
                buffer.ensureCapacity(len + 8);
                encodeInt(buffer, 7, len, 0x0);
                for (int i = 0; i < len; i++) {
                    buffer.append((int) charSequence.charAt(off + i));
                }
            }
        } else {
            final CharBuffer in = CharBuffer.wrap(charSequence, off, len);
            while (in.hasRemaining()) {
                ensureCapacity((int) (in.remaining() * this.charsetEncoder.averageBytesPerChar()) + 8);
                final CoderResult result = this.charsetEncoder.encode(in, this.tmpBuf, true);
                if (result.isError()) {
                    result.throwException();
                }
            }
            ensureCapacity(8);
            final CoderResult result = this.charsetEncoder.flush(this.tmpBuf);
            if (result.isError()) {
                result.throwException();
            }
            this.tmpBuf.flip();
            encodeString(buffer, this.tmpBuf, huffman);
        }
    }

    void encodeString(final ByteArrayBuffer buffer, final String s, final boolean huffman) throws HPackException, CharacterCodingException {

        encodeString(buffer, s, 0, s.length(), huffman);
    }

}
