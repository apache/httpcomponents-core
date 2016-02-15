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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.util.ByteArrayBuffer;
import org.junit.Assert;
import org.junit.Test;

public class TestHpackCoding {

    @Test
    public void testIntegerEncodingRFC7541Examples() throws Exception {

        final ByteArrayBuffer buffer = new ByteArrayBuffer(16);
        HPackEncoder.encodeInt(buffer, 5, 10, 0x0);

        Assert.assertEquals(1, buffer.length());
        Assert.assertEquals(0b00001010, buffer.byteAt(0) & 0xFF);

        buffer.clear();
        HPackEncoder.encodeInt(buffer, 5, 1337, 0x0);

        Assert.assertEquals(3, buffer.length());
        Assert.assertEquals(0b00011111, buffer.byteAt(0) & 0xFF);
        Assert.assertEquals(0b10011010, buffer.byteAt(1) & 0xFF);
        Assert.assertEquals(0b00001010, buffer.byteAt(2) & 0xFF);

        buffer.clear();
        HPackEncoder.encodeInt(buffer, 8, 42, 0x0);
        Assert.assertEquals(1, buffer.length());
        Assert.assertEquals(0b00101010, buffer.byteAt(0) & 0xFF);
    }

    static ByteBuffer wrap(final ByteArrayBuffer src) {

        return ByteBuffer.wrap(src.buffer(), 0, src.length());
    }

    @Test
    public void testIntegerCoding() throws Exception {

        final ByteArrayBuffer buffer = new ByteArrayBuffer(16);

        for (int n = 4; n <= 8; n++) {

            buffer.clear();

            HPackEncoder.encodeInt(buffer, n, 10, 0x0);
            Assert.assertEquals(10, HPackDecoder.decodeInt(wrap(buffer), n));

            buffer.clear();

            HPackEncoder.encodeInt(buffer, n, 123456, 0x0);
            Assert.assertEquals(123456, HPackDecoder.decodeInt(wrap(buffer), n));

            buffer.clear();

            HPackEncoder.encodeInt(buffer, n, Integer.MAX_VALUE, 0x0);
            Assert.assertEquals(Integer.MAX_VALUE, HPackDecoder.decodeInt(wrap(buffer), n));
        }

    }

    @Test
    public void testIntegerCodingLimit() throws Exception {

        final ByteBuffer src1 = createByteBuffer(0x7f, 0x80, 0xff, 0xff, 0xff, 0x07);
        Assert.assertEquals(Integer.MAX_VALUE, HPackDecoder.decodeInt(src1, 7));

        final ByteBuffer src2 = createByteBuffer(0x7f, 0x80, 0xff, 0xff, 0xff, 0x08);
        try {
            HPackDecoder.decodeInt(src2, 7);
        } catch (HPackException expected) {
        }
        final ByteBuffer src3 = createByteBuffer(0x7f, 0x80, 0xff, 0xff, 0xff, 0xff, 0xff, 0x01);
        try {
            HPackDecoder.decodeInt(src3, 7);
        } catch (HPackException expected) {
        }
    }

    private static ByteBuffer createByteBuffer(final int... bytes) {

        final ByteBuffer buffer = ByteBuffer.allocate(bytes.length);
        for (int b: bytes) {
            buffer.put((byte) b);
        }
        buffer.flip();
        return buffer;
    }

    @Test
    public void testPlainStringDecoding() throws Exception {

        final ByteBuffer src = createByteBuffer(
                0x0a, 0x63, 0x75, 0x73, 0x74, 0x6f, 0x6d, 0x2d, 0x6b, 0x65, 0x79);

        final ByteArrayBuffer buffer = new ByteArrayBuffer(16);
        HPackDecoder.decodePlainString(buffer, src);
        Assert.assertEquals("custom-key", new String(buffer.buffer(), 0, buffer.length(), StandardCharsets.US_ASCII));
        Assert.assertFalse("Decoding completed", src.hasRemaining());
    }

    @Test(expected = HPackException.class)
    public void testPlainStringDecodingTruncated() throws Exception {

        final ByteBuffer src = createByteBuffer(
                0x0a, 0x63, 0x75, 0x73, 0x74, 0x6f, 0x6d, 0x2d, 0x6b, 0x65);

        final ByteArrayBuffer buffer = new ByteArrayBuffer(16);
        HPackDecoder.decodePlainString(buffer, src);
    }

    @Test
    public void testHuffmanDecodingRFC7541Examples() throws Exception {
        final ByteBuffer src = createByteBuffer(
            0x8c, 0xf1, 0xe3, 0xc2, 0xe5, 0xf2, 0x3a, 0x6b, 0xa0, 0xab, 0x90, 0xf4, 0xff);

        final ByteArrayBuffer buffer = new ByteArrayBuffer(16);
        HPackDecoder.decodeHuffman(buffer, src);
        Assert.assertEquals("www.example.com", new String(buffer.buffer(), 0, buffer.length(), StandardCharsets.US_ASCII));
        Assert.assertFalse("Decoding completed", src.hasRemaining());
    }

    private static ByteBuffer createByteBuffer(final String s, final Charset charset) {

        return ByteBuffer.wrap(s.getBytes(charset));
    }

    @Test
    public void testHuffmanEncoding() throws Exception {
        final ByteArrayBuffer buffer = new ByteArrayBuffer(16);
        HPackEncoder.encodeHuffman(buffer, createByteBuffer("www.example.com", StandardCharsets.US_ASCII));
        final ByteBuffer expected = createByteBuffer(
                0xf1, 0xe3, 0xc2, 0xe5, 0xf2, 0x3a, 0x6b, 0xa0, 0xab, 0x90, 0xf4, 0xff);
        Assert.assertEquals(expected, wrap(buffer));
    }

    @Test
    public void testBasicStringCoding() throws Exception {

        final HPackEncoder encoder = new HPackEncoder(StandardCharsets.US_ASCII);
        final HPackDecoder decoder = new HPackDecoder(StandardCharsets.US_ASCII);

        final ByteArrayBuffer buf = new ByteArrayBuffer(16);
        encoder.encodeString(buf, "this and that", false);
        Assert.assertEquals("this and that", decoder.decodeString(ByteBuffer.wrap(buf.buffer(), 0, buf.length())));

        buf.clear();
        encoder.encodeString(buf, "this and that and Huffman", true);
        Assert.assertEquals("this and that and Huffman", decoder.decodeString(ByteBuffer.wrap(buf.buffer(), 0, buf.length())));
    }

    static final int SWISS_GERMAN_HELLO [] = {
            0x47, 0x72, 0xFC, 0x65, 0x7A, 0x69, 0x5F, 0x7A, 0xE4, 0x6D, 0xE4
    };

    static final int RUSSIAN_HELLO [] = {
            0x412, 0x441, 0x435, 0x43C, 0x5F, 0x43F, 0x440, 0x438,
            0x432, 0x435, 0x442
    };

    private static String constructHelloString(final int[] raw, final int n) {
        final StringBuilder buffer = new StringBuilder();
        for (int j = 0; j < n; j++) {
            if (j > 0) {
                buffer.append("; ");
            }
            for (int i = 0; i < raw.length; i++) {
                buffer.append((char) raw[i]);
            }
        }
        return buffer.toString();
    }

    @Test
    public void testComplexStringCoding1() throws Exception {

        System.out.println((char) 252);
        System.out.println((char) -4);

        for (Charset charset: new Charset[] {StandardCharsets.ISO_8859_1, StandardCharsets.UTF_8, StandardCharsets.UTF_16}) {

            final ByteArrayBuffer buffer = new ByteArrayBuffer(16);

            final HPackEncoder encoder = new HPackEncoder(charset);
            final HPackDecoder decoder = new HPackDecoder(charset);

            for (int n = 0; n < 10; n++) {

                final String hello = constructHelloString(SWISS_GERMAN_HELLO, 1 + 10 * n);

                for (boolean b: new boolean[] {false, true}) {

                    buffer.clear();
                    encoder.encodeString(buffer, hello, b);
                    final String helloBack = decoder.decodeString(ByteBuffer.wrap(buffer.buffer(), 0, buffer.length()));
                    Assert.assertEquals("charset: " + charset + "; huffman: " + b, hello, helloBack);
                }
            }
        }
    }

    @Test
    public void testComplexStringCoding2() throws Exception {

        for (Charset charset: new Charset[] {Charset.forName("KOI8-R"), StandardCharsets.UTF_8, StandardCharsets.UTF_16}) {

            final ByteArrayBuffer buffer = new ByteArrayBuffer(16);

            final HPackEncoder encoder = new HPackEncoder(charset);
            final HPackDecoder decoder = new HPackDecoder(charset);

            for (int n = 0; n < 10; n++) {

                final String hello = constructHelloString(RUSSIAN_HELLO, 1 + 10 * n);

                for (boolean b: new boolean[] {false, true}) {

                    buffer.clear();
                    encoder.encodeString(buffer, hello, b);
                    final String helloBack = decoder.decodeString(ByteBuffer.wrap(buffer.buffer(), 0, buffer.length()));
                    Assert.assertEquals("charset: " + charset + "; huffman: " + b, hello, helloBack);
                }
            }
        }
    }
}

