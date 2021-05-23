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
import java.util.Arrays;
import java.util.List;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.util.ByteArrayBuffer;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Test;

public class TestHPackCoding {

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
        // Use buffers with array offsets to verify correcness in additional cases
        final byte[] originalArray = src.array();
        final byte[] newArray = new byte[originalArray.length + 2];
        System.arraycopy(originalArray, 0, newArray, 1, src.length());
        return ByteBuffer.wrap(newArray, 1, src.length()).slice();
    }

    private static byte[] toArray(final ByteBuffer buffer) {
        final byte[] result = new byte[buffer.remaining()];
        buffer.get(result);
        return result;
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
        } catch (final HPackException expected) {
        }
        final ByteBuffer src3 = createByteBuffer(0x7f, 0x80, 0xff, 0xff, 0xff, 0xff, 0xff, 0x01);
        try {
            HPackDecoder.decodeInt(src3, 7);
        } catch (final HPackException expected) {
        }
    }

    private static ByteBuffer createByteBuffer(final int... bytes) {

        final ByteBuffer buffer = ByteBuffer.allocate(bytes.length);
        for (final int b : bytes) {
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
        Assert.assertEquals("custom-key", new String(buffer.array(), 0, buffer.length(), StandardCharsets.US_ASCII));
        Assert.assertFalse("Decoding completed", src.hasRemaining());
    }

    @Test
    public void testPlainStringDecodingRemainingContent() throws Exception {

        final ByteBuffer src = createByteBuffer(
                0x0a, 0x63, 0x75, 0x73, 0x74, 0x6f, 0x6d, 0x2d, 0x6b, 0x65, 0x79, 0x01, 0x01, 0x01, 0x01);

        final ByteArrayBuffer buffer = new ByteArrayBuffer(16);
        HPackDecoder.decodePlainString(buffer, src);
        Assert.assertEquals("custom-key", new String(buffer.array(), 0, buffer.length(), StandardCharsets.US_ASCII));
        Assert.assertEquals(4, src.remaining());
    }

    @Test
    public void testPlainStringDecodingReadOnly() throws Exception {

        final ByteBuffer src = createByteBuffer(
                0x0a, 0x63, 0x75, 0x73, 0x74, 0x6f, 0x6d, 0x2d, 0x6b, 0x65, 0x79, 0x50, 0x50, 0x50, 0x50);

        final ByteBuffer srcRO = src.asReadOnlyBuffer();
        final ByteArrayBuffer buffer = new ByteArrayBuffer(16);
        HPackDecoder.decodePlainString(buffer, srcRO);
        Assert.assertEquals("custom-key", new String(buffer.array(), 0, buffer.length(), StandardCharsets.US_ASCII));
        Assert.assertEquals(4, srcRO.remaining());
    }

    @Test
    public void testPlainStringDecodingTruncated() throws Exception {

        final ByteBuffer src = createByteBuffer(
                0x0a, 0x63, 0x75, 0x73, 0x74, 0x6f, 0x6d, 0x2d, 0x6b, 0x65);

        final ByteArrayBuffer buffer = new ByteArrayBuffer(16);
        Assert.assertThrows(HPackException.class, () -> HPackDecoder.decodePlainString(buffer, src));
    }

    @Test
    public void testHuffmanDecodingRFC7541Examples() throws Exception {
        final ByteBuffer src = createByteBuffer(
                0x8c, 0xf1, 0xe3, 0xc2, 0xe5, 0xf2, 0x3a, 0x6b, 0xa0, 0xab, 0x90, 0xf4, 0xff);

        final ByteArrayBuffer buffer = new ByteArrayBuffer(16);
        HPackDecoder.decodeHuffman(buffer, src);
        Assert.assertEquals("www.example.com", new String(buffer.array(), 0, buffer.length(), StandardCharsets.US_ASCII));
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
        Assert.assertArrayEquals(toArray(expected), buffer.toByteArray());
    }

    @Test
    public void testBasicStringCoding() throws Exception {

        final HPackEncoder encoder = new HPackEncoder(StandardCharsets.US_ASCII);
        final HPackDecoder decoder = new HPackDecoder(StandardCharsets.US_ASCII);

        final ByteArrayBuffer buffer = new ByteArrayBuffer(16);
        encoder.encodeString(buffer, "this and that", false);

        final StringBuilder strBuf = new StringBuilder();
        decoder.decodeString(wrap(buffer), strBuf);
        Assert.assertEquals("this and that", strBuf.toString());

        buffer.clear();
        strBuf.setLength(0);
        encoder.encodeString(buffer, "this and that and Huffman", true);
        decoder.decodeString(wrap(buffer), strBuf);
        Assert.assertEquals("this and that and Huffman", strBuf.toString());
    }

    static final int SWISS_GERMAN_HELLO[] = {
            0x47, 0x72, 0xFC, 0x65, 0x7A, 0x69, 0x5F, 0x7A, 0xE4, 0x6D, 0xE4
    };

    static final int RUSSIAN_HELLO[] = {
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

        for (final Charset charset : new Charset[]{StandardCharsets.ISO_8859_1, StandardCharsets.UTF_8, StandardCharsets.UTF_16}) {

            final ByteArrayBuffer buffer = new ByteArrayBuffer(16);
            final StringBuilder strBuf = new StringBuilder();

            final HPackEncoder encoder = new HPackEncoder(charset);
            final HPackDecoder decoder = new HPackDecoder(charset);

            for (int n = 0; n < 10; n++) {

                final String hello = constructHelloString(SWISS_GERMAN_HELLO, 1 + 10 * n);

                for (final boolean b : new boolean[]{false, true}) {

                    buffer.clear();
                    encoder.encodeString(buffer, hello, b);
                    strBuf.setLength(0);
                    decoder.decodeString(wrap(buffer), strBuf);
                    final String helloBack = strBuf.toString();
                    Assert.assertEquals("charset: " + charset + "; huffman: " + b, hello, helloBack);
                }
            }
        }
    }

    @Test
    public void testComplexStringCoding2() throws Exception {

        for (final Charset charset : new Charset[]{Charset.forName("KOI8-R"), StandardCharsets.UTF_8, StandardCharsets.UTF_16}) {

            final ByteArrayBuffer buffer = new ByteArrayBuffer(16);
            final StringBuilder strBuf = new StringBuilder();

            final HPackEncoder encoder = new HPackEncoder(charset);
            final HPackDecoder decoder = new HPackDecoder(charset);

            for (int n = 0; n < 10; n++) {

                final String hello = constructHelloString(RUSSIAN_HELLO, 1 + 10 * n);

                for (final boolean b : new boolean[]{false, true}) {

                    buffer.clear();
                    strBuf.setLength(0);
                    encoder.encodeString(buffer, hello, b);
                    decoder.decodeString(wrap(buffer), strBuf);
                    final String helloBack = strBuf.toString();
                    Assert.assertEquals("charset: " + charset + "; huffman: " + b, hello, helloBack);
                }
            }
        }
    }

    private static void assertHeaderEquals(final Header expected, final Header actual) {

        Assert.assertNotNull(actual);
        Assert.assertEquals("Header name", expected.getName(), actual.getName());
        Assert.assertEquals("Header value", expected.getValue(), actual.getValue());
        Assert.assertEquals("Header sensitive flag", expected.isSensitive(), actual.isSensitive());
    }

    @Test
    public void testLiteralHeaderWithIndexingDecodingRFC7541Examples() throws Exception {

        final ByteBuffer src = createByteBuffer(
                0x40, 0x0a, 0x63, 0x75, 0x73, 0x74, 0x6f, 0x6d, 0x2d, 0x6b, 0x65, 0x79, 0x0d, 0x63, 0x75, 0x73,
                0x74, 0x6f, 0x6d, 0x2d, 0x68, 0x65, 0x61, 0x64, 0x65, 0x72);

        final InboundDynamicTable dynamicTable = new InboundDynamicTable();
        final HPackDecoder decoder = new HPackDecoder(dynamicTable, StandardCharsets.US_ASCII);
        final Header header = decoder.decodeLiteralHeader(src, HPackRepresentation.WITH_INDEXING);
        assertHeaderEquals(new BasicHeader("custom-key", "custom-header"), header);
        Assert.assertFalse("Decoding completed", src.hasRemaining());

        Assert.assertEquals(1, dynamicTable.dynamicLength());
        assertHeaderEquals(header, dynamicTable.getDynamicEntry(0));
    }

    @Test
    public void testLiteralHeaderWithoutIndexingDecodingRFC7541Examples() throws Exception {

        final ByteBuffer src = createByteBuffer(
                0x04, 0x0c, 0x2f, 0x73, 0x61, 0x6d, 0x70, 0x6c, 0x65, 0x2f, 0x70, 0x61, 0x74, 0x68);

        final InboundDynamicTable dynamicTable = new InboundDynamicTable();
        final HPackDecoder decoder = new HPackDecoder(dynamicTable, StandardCharsets.US_ASCII);
        final Header header = decoder.decodeLiteralHeader(src, HPackRepresentation.WITHOUT_INDEXING);
        assertHeaderEquals(new BasicHeader(":path", "/sample/path"), header);
        Assert.assertFalse("Decoding completed", src.hasRemaining());

        Assert.assertEquals(0, dynamicTable.dynamicLength());
    }

    @Test
    public void testLiteralHeaderNeverIndexedDecodingRFC7541Examples() throws Exception {

        final ByteBuffer src = createByteBuffer(
                0x10, 0x08, 0x70, 0x61, 0x73, 0x73, 0x77, 0x6f, 0x72, 0x64, 0x06, 0x73, 0x65, 0x63, 0x72, 0x65, 0x74);

        final InboundDynamicTable dynamicTable = new InboundDynamicTable();
        final HPackDecoder decoder = new HPackDecoder(dynamicTable, StandardCharsets.US_ASCII);
        final Header header = decoder.decodeLiteralHeader(src, HPackRepresentation.NEVER_INDEXED);
        assertHeaderEquals(new BasicHeader("password", "secret", true), header);
        Assert.assertFalse("Decoding completed", src.hasRemaining());

        Assert.assertEquals(0, dynamicTable.dynamicLength());
    }

    @Test
    public void testIndexedHeaderDecodingRFC7541Examples() throws Exception {

        final ByteBuffer src = createByteBuffer(0x82);

        final InboundDynamicTable dynamicTable = new InboundDynamicTable();
        final HPackDecoder decoder = new HPackDecoder(dynamicTable, StandardCharsets.US_ASCII);
        final Header header = decoder.decodeIndexedHeader(src);
        assertHeaderEquals(new BasicHeader(":method", "GET"), header);
        Assert.assertFalse("Decoding completed", src.hasRemaining());

        Assert.assertEquals(0, dynamicTable.dynamicLength());
    }

    @Test
    public void testRequestDecodingWithoutHuffmanRFC7541Examples() throws Exception {

        final ByteBuffer src1 = createByteBuffer(
                0x82, 0x86, 0x84, 0x41, 0x0f, 0x77, 0x77, 0x77, 0x2e, 0x65, 0x78, 0x61, 0x6d, 0x70, 0x6c, 0x65, 0x2e,
                0x63, 0x6f, 0x6d);

        final InboundDynamicTable dynamicTable = new InboundDynamicTable();
        final HPackDecoder decoder = new HPackDecoder(dynamicTable, StandardCharsets.US_ASCII);
        final List<Header> headers1 = decoder.decodeHeaders(src1);

        Assert.assertEquals(4, headers1.size());
        assertHeaderEquals(new BasicHeader(":method", "GET"), headers1.get(0));
        assertHeaderEquals(new BasicHeader(":scheme", "http"), headers1.get(1));
        assertHeaderEquals(new BasicHeader(":path", "/"), headers1.get(2));
        assertHeaderEquals(new BasicHeader(":authority", "www.example.com"), headers1.get(3));

        Assert.assertEquals(1, dynamicTable.dynamicLength());
        assertHeaderEquals(new BasicHeader(":authority", "www.example.com"), dynamicTable.getDynamicEntry(0));
        Assert.assertEquals(57, dynamicTable.getCurrentSize());

        final ByteBuffer src2 = createByteBuffer(
                0x82, 0x86, 0x84, 0xbe, 0x58, 0x08, 0x6e, 0x6f, 0x2d, 0x63, 0x61, 0x63, 0x68, 0x65);

        final List<Header> headers2 = decoder.decodeHeaders(src2);

        Assert.assertEquals(5, headers2.size());
        assertHeaderEquals(new BasicHeader(":method", "GET"), headers2.get(0));
        assertHeaderEquals(new BasicHeader(":scheme", "http"), headers2.get(1));
        assertHeaderEquals(new BasicHeader(":path", "/"), headers2.get(2));
        assertHeaderEquals(new BasicHeader(":authority", "www.example.com"), headers2.get(3));
        assertHeaderEquals(new BasicHeader("cache-control", "no-cache"), headers2.get(4));

        Assert.assertEquals(2, dynamicTable.dynamicLength());
        assertHeaderEquals(new BasicHeader("cache-control", "no-cache"), dynamicTable.getDynamicEntry(0));
        assertHeaderEquals(new BasicHeader(":authority", "www.example.com"), dynamicTable.getDynamicEntry(1));
        Assert.assertEquals(110, dynamicTable.getCurrentSize());

        final ByteBuffer src3 = createByteBuffer(
                0x82, 0x87, 0x85, 0xbf, 0x40, 0x0a, 0x63, 0x75, 0x73, 0x74, 0x6f, 0x6d, 0x2d, 0x6b, 0x65, 0x79,
                0x0c, 0x63, 0x75, 0x73, 0x74, 0x6f, 0x6d, 0x2d, 0x76, 0x61, 0x6c, 0x75, 0x65);

        final List<Header> headers3 = decoder.decodeHeaders(src3);

        Assert.assertEquals(5, headers3.size());
        assertHeaderEquals(new BasicHeader(":method", "GET"), headers3.get(0));
        assertHeaderEquals(new BasicHeader(":scheme", "https"), headers3.get(1));
        assertHeaderEquals(new BasicHeader(":path", "/index.html"), headers3.get(2));
        assertHeaderEquals(new BasicHeader(":authority", "www.example.com"), headers3.get(3));
        assertHeaderEquals(new BasicHeader("custom-key", "custom-value"), headers3.get(4));

        Assert.assertEquals(3, dynamicTable.dynamicLength());
        assertHeaderEquals(new BasicHeader("custom-key", "custom-value"), dynamicTable.getDynamicEntry(0));
        assertHeaderEquals(new BasicHeader("cache-control", "no-cache"), dynamicTable.getDynamicEntry(1));
        assertHeaderEquals(new BasicHeader(":authority", "www.example.com"), dynamicTable.getDynamicEntry(2));
        Assert.assertEquals(164, dynamicTable.getCurrentSize());
    }

    @Test
    public void testRequestDecodingWithHuffmanRFC7541Examples() throws Exception {

        final ByteBuffer src1 = createByteBuffer(
                0x82, 0x86, 0x84, 0x41, 0x8c, 0xf1, 0xe3, 0xc2, 0xe5, 0xf2, 0x3a, 0x6b, 0xa0, 0xab, 0x90, 0xf4, 0xff);

        final InboundDynamicTable dynamicTable = new InboundDynamicTable();
        final HPackDecoder decoder = new HPackDecoder(dynamicTable, StandardCharsets.US_ASCII);
        final List<Header> headers1 = decoder.decodeHeaders(src1);

        Assert.assertEquals(4, headers1.size());
        assertHeaderEquals(new BasicHeader(":method", "GET"), headers1.get(0));
        assertHeaderEquals(new BasicHeader(":scheme", "http"), headers1.get(1));
        assertHeaderEquals(new BasicHeader(":path", "/"), headers1.get(2));
        assertHeaderEquals(new BasicHeader(":authority", "www.example.com"), headers1.get(3));

        Assert.assertEquals(1, dynamicTable.dynamicLength());
        assertHeaderEquals(new BasicHeader(":authority", "www.example.com"), dynamicTable.getDynamicEntry(0));
        Assert.assertEquals(57, dynamicTable.getCurrentSize());

        final ByteBuffer src2 = createByteBuffer(
                0x82, 0x86, 0x84, 0xbe, 0x58, 0x86, 0xa8, 0xeb, 0x10, 0x64, 0x9c, 0xbf);

        final List<Header> headers2 = decoder.decodeHeaders(src2);

        Assert.assertEquals(5, headers2.size());
        assertHeaderEquals(new BasicHeader(":method", "GET"), headers2.get(0));
        assertHeaderEquals(new BasicHeader(":scheme", "http"), headers2.get(1));
        assertHeaderEquals(new BasicHeader(":path", "/"), headers2.get(2));
        assertHeaderEquals(new BasicHeader(":authority", "www.example.com"), headers2.get(3));
        assertHeaderEquals(new BasicHeader("cache-control", "no-cache"), headers2.get(4));

        Assert.assertEquals(2, dynamicTable.dynamicLength());
        assertHeaderEquals(new BasicHeader("cache-control", "no-cache"), dynamicTable.getDynamicEntry(0));
        assertHeaderEquals(new BasicHeader(":authority", "www.example.com"), dynamicTable.getDynamicEntry(1));
        Assert.assertEquals(110, dynamicTable.getCurrentSize());

        final ByteBuffer src3 = createByteBuffer(
                0x82, 0x87, 0x85, 0xbf, 0x40, 0x88, 0x25, 0xa8, 0x49, 0xe9, 0x5b, 0xa9, 0x7d, 0x7f, 0x89, 0x25,
                0xa8, 0x49, 0xe9, 0x5b, 0xb8, 0xe8, 0xb4, 0xbf);

        final List<Header> headers3 = decoder.decodeHeaders(src3);

        Assert.assertEquals(5, headers3.size());
        assertHeaderEquals(new BasicHeader(":method", "GET"), headers3.get(0));
        assertHeaderEquals(new BasicHeader(":scheme", "https"), headers3.get(1));
        assertHeaderEquals(new BasicHeader(":path", "/index.html"), headers3.get(2));
        assertHeaderEquals(new BasicHeader(":authority", "www.example.com"), headers3.get(3));
        assertHeaderEquals(new BasicHeader("custom-key", "custom-value"), headers3.get(4));

        Assert.assertEquals(3, dynamicTable.dynamicLength());
        assertHeaderEquals(new BasicHeader("custom-key", "custom-value"), dynamicTable.getDynamicEntry(0));
        assertHeaderEquals(new BasicHeader("cache-control", "no-cache"), dynamicTable.getDynamicEntry(1));
        assertHeaderEquals(new BasicHeader(":authority", "www.example.com"), dynamicTable.getDynamicEntry(2));
        Assert.assertEquals(164, dynamicTable.getCurrentSize());
    }

    @Test
    public void testResponseDecodingWithoutHuffmanRFC7541Examples() throws Exception {

        final ByteBuffer src1 = createByteBuffer(
                0x48, 0x03, 0x33, 0x30, 0x32, 0x58, 0x07, 0x70, 0x72, 0x69, 0x76, 0x61, 0x74, 0x65, 0x61, 0x1d, 0x4d,
                0x6f, 0x6e, 0x2c, 0x20, 0x32, 0x31, 0x20, 0x4f, 0x63, 0x74, 0x20, 0x32, 0x30, 0x31, 0x33, 0x20, 0x32,
                0x30, 0x3a, 0x31, 0x33, 0x3a, 0x32, 0x31, 0x20, 0x47, 0x4d, 0x54, 0x6e, 0x17, 0x68, 0x74, 0x74, 0x70,
                0x73, 0x3a, 0x2f, 0x2f, 0x77, 0x77, 0x77, 0x2e, 0x65, 0x78, 0x61, 0x6d, 0x70, 0x6c, 0x65, 0x2e, 0x63,
                0x6f, 0x6d);

        final InboundDynamicTable dynamicTable = new InboundDynamicTable();
        dynamicTable.setMaxSize(256);
        final HPackDecoder decoder = new HPackDecoder(dynamicTable, StandardCharsets.US_ASCII);
        final List<Header> headers1 = decoder.decodeHeaders(src1);

        Assert.assertEquals(4, headers1.size());
        assertHeaderEquals(new BasicHeader(":status", "302"), headers1.get(0));
        assertHeaderEquals(new BasicHeader("cache-control", "private"), headers1.get(1));
        assertHeaderEquals(new BasicHeader("date", "Mon, 21 Oct 2013 20:13:21 GMT"), headers1.get(2));
        assertHeaderEquals(new BasicHeader("location", "https://www.example.com"), headers1.get(3));

        Assert.assertEquals(4, dynamicTable.dynamicLength());
        assertHeaderEquals(new BasicHeader("location", "https://www.example.com"), dynamicTable.getDynamicEntry(0));
        assertHeaderEquals(new BasicHeader("date", "Mon, 21 Oct 2013 20:13:21 GMT"), dynamicTable.getDynamicEntry(1));
        assertHeaderEquals(new BasicHeader("cache-control", "private"), dynamicTable.getDynamicEntry(2));
        assertHeaderEquals(new BasicHeader(":status", "302"), dynamicTable.getDynamicEntry(3));
        Assert.assertEquals(222, dynamicTable.getCurrentSize());

        final ByteBuffer src2 = createByteBuffer(
                0x48, 0x03, 0x33, 0x30, 0x37, 0xc1, 0xc0, 0xbf);

        final List<Header> headers2 = decoder.decodeHeaders(src2);

        Assert.assertEquals(4, headers2.size());
        assertHeaderEquals(new BasicHeader(":status", "307"), headers2.get(0));
        assertHeaderEquals(new BasicHeader("cache-control", "private"), headers2.get(1));
        assertHeaderEquals(new BasicHeader("date", "Mon, 21 Oct 2013 20:13:21 GMT"), headers2.get(2));
        assertHeaderEquals(new BasicHeader("location", "https://www.example.com"), headers2.get(3));

        Assert.assertEquals(4, dynamicTable.dynamicLength());
        assertHeaderEquals(new BasicHeader(":status", "307"), dynamicTable.getDynamicEntry(0));
        assertHeaderEquals(new BasicHeader("location", "https://www.example.com"), dynamicTable.getDynamicEntry(1));
        assertHeaderEquals(new BasicHeader("date", "Mon, 21 Oct 2013 20:13:21 GMT"), dynamicTable.getDynamicEntry(2));
        assertHeaderEquals(new BasicHeader("cache-control", "private"), dynamicTable.getDynamicEntry(3));

        Assert.assertEquals(222, dynamicTable.getCurrentSize());

        final ByteBuffer src3 = createByteBuffer(
                0x88, 0xc1, 0x61, 0x1d, 0x4d, 0x6f, 0x6e, 0x2c, 0x20, 0x32, 0x31, 0x20, 0x4f, 0x63, 0x74, 0x20, 0x32,
                0x30, 0x31, 0x33, 0x20, 0x32, 0x30, 0x3a, 0x31, 0x33, 0x3a, 0x32, 0x32, 0x20, 0x47, 0x4d, 0x54, 0xc0,
                0x5a, 0x04, 0x67, 0x7a, 0x69, 0x70, 0x77, 0x38, 0x66, 0x6f, 0x6f, 0x3d, 0x41, 0x53, 0x44, 0x4a, 0x4b,
                0x48, 0x51, 0x4b, 0x42, 0x5a, 0x58, 0x4f, 0x51, 0x57, 0x45, 0x4f, 0x50, 0x49, 0x55, 0x41, 0x58, 0x51,
                0x57, 0x45, 0x4f, 0x49, 0x55, 0x3b, 0x20, 0x6d, 0x61, 0x78, 0x2d, 0x61, 0x67, 0x65, 0x3d, 0x33, 0x36,
                0x30, 0x30, 0x3b, 0x20, 0x76, 0x65, 0x72, 0x73, 0x69, 0x6f, 0x6e, 0x3d, 0x31);

        final List<Header> headers3 = decoder.decodeHeaders(src3);

        Assert.assertEquals(6, headers3.size());
        assertHeaderEquals(new BasicHeader(":status", "200"), headers3.get(0));
        assertHeaderEquals(new BasicHeader("cache-control", "private"), headers3.get(1));
        assertHeaderEquals(new BasicHeader("date", "Mon, 21 Oct 2013 20:13:22 GMT"), headers3.get(2));
        assertHeaderEquals(new BasicHeader("location", "https://www.example.com"), headers3.get(3));
        assertHeaderEquals(new BasicHeader("content-encoding", "gzip"), headers3.get(4));
        assertHeaderEquals(new BasicHeader("set-cookie", "foo=ASDJKHQKBZXOQWEOPIUAXQWEOIU; max-age=3600; version=1"), headers3.get(5));

        Assert.assertEquals(3, dynamicTable.dynamicLength());
        assertHeaderEquals(new BasicHeader("set-cookie", "foo=ASDJKHQKBZXOQWEOPIUAXQWEOIU; max-age=3600; version=1"), dynamicTable.getDynamicEntry(0));
        assertHeaderEquals(new BasicHeader("content-encoding", "gzip"), dynamicTable.getDynamicEntry(1));
        assertHeaderEquals(new BasicHeader("date", "Mon, 21 Oct 2013 20:13:22 GMT"), dynamicTable.getDynamicEntry(2));

        Assert.assertEquals(215, dynamicTable.getCurrentSize());
    }

    @Test
    public void testResponseDecodingWithHuffmanRFC7541Examples() throws Exception {

        final ByteBuffer src1 = createByteBuffer(
                0x48, 0x82, 0x64, 0x02, 0x58, 0x85, 0xae, 0xc3, 0x77, 0x1a, 0x4b, 0x61, 0x96, 0xd0, 0x7a, 0xbe, 0x94,
                0x10, 0x54, 0xd4, 0x44, 0xa8, 0x20, 0x05, 0x95, 0x04, 0x0b, 0x81, 0x66, 0xe0, 0x82, 0xa6, 0x2d, 0x1b,
                0xff, 0x6e, 0x91, 0x9d, 0x29, 0xad, 0x17, 0x18, 0x63, 0xc7, 0x8f, 0x0b, 0x97, 0xc8, 0xe9, 0xae, 0x82,
                0xae, 0x43, 0xd3);

        final InboundDynamicTable dynamicTable = new InboundDynamicTable();
        dynamicTable.setMaxSize(256);
        final HPackDecoder decoder = new HPackDecoder(dynamicTable, StandardCharsets.US_ASCII);
        final List<Header> headers1 = decoder.decodeHeaders(src1);

        Assert.assertEquals(4, headers1.size());
        assertHeaderEquals(new BasicHeader(":status", "302"), headers1.get(0));
        assertHeaderEquals(new BasicHeader("cache-control", "private"), headers1.get(1));
        assertHeaderEquals(new BasicHeader("date", "Mon, 21 Oct 2013 20:13:21 GMT"), headers1.get(2));
        assertHeaderEquals(new BasicHeader("location", "https://www.example.com"), headers1.get(3));

        Assert.assertEquals(4, dynamicTable.dynamicLength());
        assertHeaderEquals(new BasicHeader("location", "https://www.example.com"), dynamicTable.getDynamicEntry(0));
        assertHeaderEquals(new BasicHeader("date", "Mon, 21 Oct 2013 20:13:21 GMT"), dynamicTable.getDynamicEntry(1));
        assertHeaderEquals(new BasicHeader("cache-control", "private"), dynamicTable.getDynamicEntry(2));
        assertHeaderEquals(new BasicHeader(":status", "302"), dynamicTable.getDynamicEntry(3));
        Assert.assertEquals(222, dynamicTable.getCurrentSize());

        final ByteBuffer src2 = createByteBuffer(
                0x48, 0x83, 0x64, 0x0e, 0xff, 0xc1, 0xc0, 0xbf);

        final List<Header> headers2 = decoder.decodeHeaders(src2);

        Assert.assertEquals(4, headers2.size());
        assertHeaderEquals(new BasicHeader(":status", "307"), headers2.get(0));
        assertHeaderEquals(new BasicHeader("cache-control", "private"), headers2.get(1));
        assertHeaderEquals(new BasicHeader("date", "Mon, 21 Oct 2013 20:13:21 GMT"), headers2.get(2));
        assertHeaderEquals(new BasicHeader("location", "https://www.example.com"), headers2.get(3));

        Assert.assertEquals(4, dynamicTable.dynamicLength());
        assertHeaderEquals(new BasicHeader(":status", "307"), dynamicTable.getDynamicEntry(0));
        assertHeaderEquals(new BasicHeader("location", "https://www.example.com"), dynamicTable.getDynamicEntry(1));
        assertHeaderEquals(new BasicHeader("date", "Mon, 21 Oct 2013 20:13:21 GMT"), dynamicTable.getDynamicEntry(2));
        assertHeaderEquals(new BasicHeader("cache-control", "private"), dynamicTable.getDynamicEntry(3));

        Assert.assertEquals(222, dynamicTable.getCurrentSize());

        final ByteBuffer src3 = createByteBuffer(
                0x88, 0xc1, 0x61, 0x96, 0xd0, 0x7a, 0xbe, 0x94, 0x10, 0x54, 0xd4, 0x44, 0xa8, 0x20, 0x05, 0x95, 0x04,
                0x0b, 0x81, 0x66, 0xe0, 0x84, 0xa6, 0x2d, 0x1b, 0xff, 0xc0, 0x5a, 0x83, 0x9b, 0xd9, 0xab, 0x77, 0xad,
                0x94, 0xe7, 0x82, 0x1d, 0xd7, 0xf2, 0xe6, 0xc7, 0xb3, 0x35, 0xdf, 0xdf, 0xcd, 0x5b, 0x39, 0x60, 0xd5,
                0xaf, 0x27, 0x08, 0x7f, 0x36, 0x72, 0xc1, 0xab, 0x27, 0x0f, 0xb5, 0x29, 0x1f, 0x95, 0x87, 0x31, 0x60,
                0x65, 0xc0, 0x03, 0xed, 0x4e, 0xe5, 0xb1, 0x06, 0x3d, 0x50, 0x07);

        final List<Header> headers3 = decoder.decodeHeaders(src3);

        Assert.assertEquals(6, headers3.size());
        assertHeaderEquals(new BasicHeader(":status", "200"), headers3.get(0));
        assertHeaderEquals(new BasicHeader("cache-control", "private"), headers3.get(1));
        assertHeaderEquals(new BasicHeader("date", "Mon, 21 Oct 2013 20:13:22 GMT"), headers3.get(2));
        assertHeaderEquals(new BasicHeader("location", "https://www.example.com"), headers3.get(3));
        assertHeaderEquals(new BasicHeader("content-encoding", "gzip"), headers3.get(4));
        assertHeaderEquals(new BasicHeader("set-cookie", "foo=ASDJKHQKBZXOQWEOPIUAXQWEOIU; max-age=3600; version=1"), headers3.get(5));

        Assert.assertEquals(3, dynamicTable.dynamicLength());
        assertHeaderEquals(new BasicHeader("set-cookie", "foo=ASDJKHQKBZXOQWEOPIUAXQWEOIU; max-age=3600; version=1"), dynamicTable.getDynamicEntry(0));
        assertHeaderEquals(new BasicHeader("content-encoding", "gzip"), dynamicTable.getDynamicEntry(1));
        assertHeaderEquals(new BasicHeader("date", "Mon, 21 Oct 2013 20:13:22 GMT"), dynamicTable.getDynamicEntry(2));

        Assert.assertEquals(215, dynamicTable.getCurrentSize());
    }

    private static byte[] createByteArray(final int... bytes) {
        final byte[] buffer = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            buffer[i] = (byte) bytes[i];
        }
        return buffer;
    }

    @Test
    public void testLiteralHeaderWithIndexingEncodingRFC7541Examples() throws Exception {

        final OutboundDynamicTable dynamicTable = new OutboundDynamicTable();
        final HPackEncoder encoder = new HPackEncoder(dynamicTable, StandardCharsets.US_ASCII);

        final ByteArrayBuffer buf = new ByteArrayBuffer(128);

        final Header header = new BasicHeader("custom-key", "custom-header");
        encoder.encodeLiteralHeader(buf, null, header, HPackRepresentation.WITH_INDEXING, false);

        final byte[] expected = createByteArray(
                0x40, 0x0a, 0x63, 0x75, 0x73, 0x74, 0x6f, 0x6d, 0x2d, 0x6b, 0x65, 0x79, 0x0d, 0x63, 0x75, 0x73,
                0x74, 0x6f, 0x6d, 0x2d, 0x68, 0x65, 0x61, 0x64, 0x65, 0x72);

        Assert.assertArrayEquals(expected, buf.toByteArray());
    }

    @Test
    public void testLiteralHeaderWithoutIndexingEncodingRFC7541Examples() throws Exception {

        final OutboundDynamicTable dynamicTable = new OutboundDynamicTable();
        final HPackEncoder encoder = new HPackEncoder(dynamicTable, StandardCharsets.US_ASCII);

        final ByteArrayBuffer buf = new ByteArrayBuffer(128);

        final Header header = new BasicHeader(":path", "/sample/path");
        encoder.encodeLiteralHeader(buf, new HPackEntry() {
            @Override
            public int getIndex() {
                return 4;
            }

            @Override
            public HPackHeader getHeader() {
                return new HPackHeader(header);
            }
        }, header, HPackRepresentation.WITHOUT_INDEXING, false);

        final byte[] expected = createByteArray(
                0x04, 0x0c, 0x2f, 0x73, 0x61, 0x6d, 0x70, 0x6c, 0x65, 0x2f, 0x70, 0x61, 0x74, 0x68);
        Assert.assertArrayEquals(expected, buf.toByteArray());
    }

    @Test
    public void testLiteralHeaderNeverIndexedEncodingRFC7541Examples() throws Exception {

        final OutboundDynamicTable dynamicTable = new OutboundDynamicTable();
        final HPackEncoder encoder = new HPackEncoder(dynamicTable, StandardCharsets.US_ASCII);

        final ByteArrayBuffer buf = new ByteArrayBuffer(128);

        final Header header = new BasicHeader("password", "secret", true);
        encoder.encodeLiteralHeader(buf, null, header, HPackRepresentation.NEVER_INDEXED, false);

        final byte[] expected = createByteArray(
                0x10, 0x08, 0x70, 0x61, 0x73, 0x73, 0x77, 0x6f, 0x72, 0x64, 0x06, 0x73, 0x65, 0x63, 0x72, 0x65, 0x74);
        Assert.assertArrayEquals(expected, buf.toByteArray());
    }

    @Test
    public void testIndexedHeaderEncodingRFC7541Examples() throws Exception {

        final OutboundDynamicTable dynamicTable = new OutboundDynamicTable();
        final HPackEncoder encoder = new HPackEncoder(dynamicTable, StandardCharsets.US_ASCII);

        final ByteArrayBuffer buf = new ByteArrayBuffer(128);
        encoder.encodeIndex(buf, 2);

        final byte[] expected = createByteArray(0x82);
        Assert.assertArrayEquals(expected, buf.toByteArray());
    }

    @Test
    public void testRequestEncodingWithoutHuffmanRFC7541Examples() throws Exception {

        final OutboundDynamicTable dynamicTable = new OutboundDynamicTable();
        final HPackEncoder encoder = new HPackEncoder(dynamicTable, StandardCharsets.US_ASCII);

        final ByteArrayBuffer buf = new ByteArrayBuffer(256);
        final List<Header> headers1 = Arrays.asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":scheme", "http"),
                new BasicHeader(":path", "/"),
                new BasicHeader(":authority", "www.example.com"));

        encoder.encodeHeaders(buf, headers1, false, false);

        final byte[] expected1 = createByteArray(
                0x82, 0x86, 0x84, 0x41, 0x0f, 0x77, 0x77, 0x77, 0x2e, 0x65, 0x78, 0x61, 0x6d, 0x70, 0x6c, 0x65, 0x2e,
                0x63, 0x6f, 0x6d);
        Assert.assertArrayEquals(expected1, buf.toByteArray());

        Assert.assertEquals(1, dynamicTable.dynamicLength());
        assertHeaderEquals(new BasicHeader(":authority", "www.example.com"), dynamicTable.getDynamicEntry(0));
        Assert.assertEquals(57, dynamicTable.getCurrentSize());

        final List<Header> headers2 = Arrays.asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":scheme", "http"),
                new BasicHeader(":path", "/"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader("cache-control", "no-cache"));

        buf.clear();
        encoder.encodeHeaders(buf, headers2, false, false);

        final byte[] expected2 = createByteArray(
                0x82, 0x86, 0x84, 0xbe, 0x58, 0x08, 0x6e, 0x6f, 0x2d, 0x63, 0x61, 0x63, 0x68, 0x65);
        Assert.assertArrayEquals(expected2, buf.toByteArray());

        Assert.assertEquals(2, dynamicTable.dynamicLength());
        assertHeaderEquals(new BasicHeader("cache-control", "no-cache"), dynamicTable.getDynamicEntry(0));
        assertHeaderEquals(new BasicHeader(":authority", "www.example.com"), dynamicTable.getDynamicEntry(1));
        Assert.assertEquals(110, dynamicTable.getCurrentSize());

        final List<Header> headers3 = Arrays.asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":scheme", "https"),
                new BasicHeader(":path", "/index.html"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader("custom-key", "custom-value"));

        buf.clear();
        encoder.encodeHeaders(buf, headers3, false, false);

        final byte[] expected3 = createByteArray(
                0x82, 0x87, 0x85, 0xbf, 0x40, 0x0a, 0x63, 0x75, 0x73, 0x74, 0x6f, 0x6d, 0x2d, 0x6b, 0x65, 0x79,
                0x0c, 0x63, 0x75, 0x73, 0x74, 0x6f, 0x6d, 0x2d, 0x76, 0x61, 0x6c, 0x75, 0x65);
        Assert.assertArrayEquals(expected3, buf.toByteArray());

        Assert.assertEquals(3, dynamicTable.dynamicLength());
        assertHeaderEquals(new BasicHeader("custom-key", "custom-value"), dynamicTable.getDynamicEntry(0));
        assertHeaderEquals(new BasicHeader("cache-control", "no-cache"), dynamicTable.getDynamicEntry(1));
        assertHeaderEquals(new BasicHeader(":authority", "www.example.com"), dynamicTable.getDynamicEntry(2));
        Assert.assertEquals(164, dynamicTable.getCurrentSize());
    }

    @Test
    public void testRequestEncodingWithHuffmanRFC7541Examples() throws Exception {

        final OutboundDynamicTable dynamicTable = new OutboundDynamicTable();
        final HPackEncoder encoder = new HPackEncoder(dynamicTable, StandardCharsets.US_ASCII);

        final ByteArrayBuffer buf = new ByteArrayBuffer(256);
        final List<Header> headers1 = Arrays.asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":scheme", "http"),
                new BasicHeader(":path", "/"),
                new BasicHeader(":authority", "www.example.com"));

        encoder.encodeHeaders(buf, headers1, false, true);

        final byte[] expected1 = createByteArray(
                0x82, 0x86, 0x84, 0x41, 0x8c, 0xf1, 0xe3, 0xc2, 0xe5, 0xf2, 0x3a, 0x6b, 0xa0, 0xab, 0x90, 0xf4, 0xff);
        Assert.assertArrayEquals(expected1, buf.toByteArray());

        Assert.assertEquals(1, dynamicTable.dynamicLength());
        assertHeaderEquals(new BasicHeader(":authority", "www.example.com"), dynamicTable.getDynamicEntry(0));
        Assert.assertEquals(57, dynamicTable.getCurrentSize());

        final List<Header> headers2 = Arrays.asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":scheme", "http"),
                new BasicHeader(":path", "/"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader("cache-control", "no-cache"));

        buf.clear();
        encoder.encodeHeaders(buf, headers2, false, true);

        final byte[] expected2 = createByteArray(
                0x82, 0x86, 0x84, 0xbe, 0x58, 0x86, 0xa8, 0xeb, 0x10, 0x64, 0x9c, 0xbf);
        Assert.assertArrayEquals(expected2, buf.toByteArray());

        Assert.assertEquals(2, dynamicTable.dynamicLength());
        assertHeaderEquals(new BasicHeader("cache-control", "no-cache"), dynamicTable.getDynamicEntry(0));
        assertHeaderEquals(new BasicHeader(":authority", "www.example.com"), dynamicTable.getDynamicEntry(1));
        Assert.assertEquals(110, dynamicTable.getCurrentSize());

        final List<Header> headers3 = Arrays.asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":scheme", "https"),
                new BasicHeader(":path", "/index.html"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader("custom-key", "custom-value"));

        buf.clear();
        encoder.encodeHeaders(buf, headers3, false, true);

        final byte[] expected3 = createByteArray(
                0x82, 0x87, 0x85, 0xbf, 0x40, 0x88, 0x25, 0xa8, 0x49, 0xe9, 0x5b, 0xa9, 0x7d, 0x7f, 0x89, 0x25,
                0xa8, 0x49, 0xe9, 0x5b, 0xb8, 0xe8, 0xb4, 0xbf);
        Assert.assertArrayEquals(expected3, buf.toByteArray());

        Assert.assertEquals(3, dynamicTable.dynamicLength());
        assertHeaderEquals(new BasicHeader("custom-key", "custom-value"), dynamicTable.getDynamicEntry(0));
        assertHeaderEquals(new BasicHeader("cache-control", "no-cache"), dynamicTable.getDynamicEntry(1));
        assertHeaderEquals(new BasicHeader(":authority", "www.example.com"), dynamicTable.getDynamicEntry(2));
        Assert.assertEquals(164, dynamicTable.getCurrentSize());
    }

    @Test
    public void testResponseEncodingWithoutHuffmanRFC7541Examples() throws Exception {

        final OutboundDynamicTable dynamicTable = new OutboundDynamicTable();
        dynamicTable.setMaxSize(256);
        final HPackEncoder encoder = new HPackEncoder(dynamicTable, StandardCharsets.US_ASCII);

        final ByteArrayBuffer buf = new ByteArrayBuffer(256);
        final List<Header> headers1 = Arrays.asList(
                new BasicHeader(":status", "302"),
                new BasicHeader("cache-control", "private"),
                new BasicHeader("date", "Mon, 21 Oct 2013 20:13:21 GMT"),
                new BasicHeader("location", "https://www.example.com"));

        encoder.encodeHeaders(buf, headers1, false, false);

        final byte[] expected1 = createByteArray(
                0x48, 0x03, 0x33, 0x30, 0x32, 0x58, 0x07, 0x70, 0x72, 0x69, 0x76, 0x61, 0x74, 0x65, 0x61, 0x1d, 0x4d,
                0x6f, 0x6e, 0x2c, 0x20, 0x32, 0x31, 0x20, 0x4f, 0x63, 0x74, 0x20, 0x32, 0x30, 0x31, 0x33, 0x20, 0x32,
                0x30, 0x3a, 0x31, 0x33, 0x3a, 0x32, 0x31, 0x20, 0x47, 0x4d, 0x54, 0x6e, 0x17, 0x68, 0x74, 0x74, 0x70,
                0x73, 0x3a, 0x2f, 0x2f, 0x77, 0x77, 0x77, 0x2e, 0x65, 0x78, 0x61, 0x6d, 0x70, 0x6c, 0x65, 0x2e, 0x63,
                0x6f, 0x6d);
        Assert.assertArrayEquals(expected1, buf.toByteArray());

        Assert.assertEquals(4, dynamicTable.dynamicLength());
        assertHeaderEquals(new BasicHeader("location", "https://www.example.com"), dynamicTable.getDynamicEntry(0));
        assertHeaderEquals(new BasicHeader("date", "Mon, 21 Oct 2013 20:13:21 GMT"), dynamicTable.getDynamicEntry(1));
        assertHeaderEquals(new BasicHeader("cache-control", "private"), dynamicTable.getDynamicEntry(2));
        assertHeaderEquals(new BasicHeader(":status", "302"), dynamicTable.getDynamicEntry(3));
        Assert.assertEquals(222, dynamicTable.getCurrentSize());

        final List<Header> headers2 = Arrays.asList(
                new BasicHeader(":status", "307"),
                new BasicHeader("cache-control", "private"),
                new BasicHeader("date", "Mon, 21 Oct 2013 20:13:21 GMT"),
                new BasicHeader("location", "https://www.example.com"));

        buf.clear();
        encoder.encodeHeaders(buf, headers2, false, false);

        final byte[] expected2 = createByteArray(
                0x48, 0x03, 0x33, 0x30, 0x37, 0xc1, 0xc0, 0xbf);
        Assert.assertArrayEquals(expected2, buf.toByteArray());

        Assert.assertEquals(4, dynamicTable.dynamicLength());
        assertHeaderEquals(new BasicHeader(":status", "307"), dynamicTable.getDynamicEntry(0));
        assertHeaderEquals(new BasicHeader("location", "https://www.example.com"), dynamicTable.getDynamicEntry(1));
        assertHeaderEquals(new BasicHeader("date", "Mon, 21 Oct 2013 20:13:21 GMT"), dynamicTable.getDynamicEntry(2));
        assertHeaderEquals(new BasicHeader("cache-control", "private"), dynamicTable.getDynamicEntry(3));

        Assert.assertEquals(222, dynamicTable.getCurrentSize());

        final List<Header> headers3 = Arrays.asList(
        new BasicHeader(":status", "200"),
                new BasicHeader("cache-control", "private"),
                new BasicHeader("date", "Mon, 21 Oct 2013 20:13:22 GMT"),
                new BasicHeader("location", "https://www.example.com"),
                new BasicHeader("content-encoding", "gzip"),
                new BasicHeader("set-cookie", "foo=ASDJKHQKBZXOQWEOPIUAXQWEOIU; max-age=3600; version=1"));

        buf.clear();
        encoder.encodeHeaders(buf, headers3, false, false);

        final byte[] expected3 = createByteArray(
                0x88, 0xc1, 0x61, 0x1d, 0x4d, 0x6f, 0x6e, 0x2c, 0x20, 0x32, 0x31, 0x20, 0x4f, 0x63, 0x74, 0x20, 0x32,
                0x30, 0x31, 0x33, 0x20, 0x32, 0x30, 0x3a, 0x31, 0x33, 0x3a, 0x32, 0x32, 0x20, 0x47, 0x4d, 0x54, 0xc0,
                0x5a, 0x04, 0x67, 0x7a, 0x69, 0x70, 0x77, 0x38, 0x66, 0x6f, 0x6f, 0x3d, 0x41, 0x53, 0x44, 0x4a, 0x4b,
                0x48, 0x51, 0x4b, 0x42, 0x5a, 0x58, 0x4f, 0x51, 0x57, 0x45, 0x4f, 0x50, 0x49, 0x55, 0x41, 0x58, 0x51,
                0x57, 0x45, 0x4f, 0x49, 0x55, 0x3b, 0x20, 0x6d, 0x61, 0x78, 0x2d, 0x61, 0x67, 0x65, 0x3d, 0x33, 0x36,
                0x30, 0x30, 0x3b, 0x20, 0x76, 0x65, 0x72, 0x73, 0x69, 0x6f, 0x6e, 0x3d, 0x31);
        Assert.assertArrayEquals(expected3, buf.toByteArray());

        Assert.assertEquals(3, dynamicTable.dynamicLength());
        assertHeaderEquals(new BasicHeader("set-cookie", "foo=ASDJKHQKBZXOQWEOPIUAXQWEOIU; max-age=3600; version=1"), dynamicTable.getDynamicEntry(0));
        assertHeaderEquals(new BasicHeader("content-encoding", "gzip"), dynamicTable.getDynamicEntry(1));
        assertHeaderEquals(new BasicHeader("date", "Mon, 21 Oct 2013 20:13:22 GMT"), dynamicTable.getDynamicEntry(2));

        Assert.assertEquals(215, dynamicTable.getCurrentSize());
    }

    @Test
    public void testResponseEncodingWithHuffmanRFC7541Examples() throws Exception {

        final OutboundDynamicTable dynamicTable = new OutboundDynamicTable();
        dynamicTable.setMaxSize(256);
        final HPackEncoder encoder = new HPackEncoder(dynamicTable, StandardCharsets.US_ASCII);

        final ByteArrayBuffer buf = new ByteArrayBuffer(256);
        final List<Header> headers1 = Arrays.asList(
                new BasicHeader(":status", "302"),
                new BasicHeader("cache-control", "private"),
                new BasicHeader("date", "Mon, 21 Oct 2013 20:13:21 GMT"),
                new BasicHeader("location", "https://www.example.com"));

        encoder.encodeHeaders(buf, headers1, false, true);

        final byte[] expected1 = createByteArray(
                0x48, 0x82, 0x64, 0x02, 0x58, 0x85, 0xae, 0xc3, 0x77, 0x1a, 0x4b, 0x61, 0x96, 0xd0, 0x7a, 0xbe, 0x94,
                0x10, 0x54, 0xd4, 0x44, 0xa8, 0x20, 0x05, 0x95, 0x04, 0x0b, 0x81, 0x66, 0xe0, 0x82, 0xa6, 0x2d, 0x1b,
                0xff, 0x6e, 0x91, 0x9d, 0x29, 0xad, 0x17, 0x18, 0x63, 0xc7, 0x8f, 0x0b, 0x97, 0xc8, 0xe9, 0xae, 0x82,
                0xae, 0x43, 0xd3);
        Assert.assertArrayEquals(expected1, buf.toByteArray());

        Assert.assertEquals(4, dynamicTable.dynamicLength());
        assertHeaderEquals(new BasicHeader("location", "https://www.example.com"), dynamicTable.getDynamicEntry(0));
        assertHeaderEquals(new BasicHeader("date", "Mon, 21 Oct 2013 20:13:21 GMT"), dynamicTable.getDynamicEntry(1));
        assertHeaderEquals(new BasicHeader("cache-control", "private"), dynamicTable.getDynamicEntry(2));
        assertHeaderEquals(new BasicHeader(":status", "302"), dynamicTable.getDynamicEntry(3));
        Assert.assertEquals(222, dynamicTable.getCurrentSize());

        final List<Header> headers2 = Arrays.asList(
                new BasicHeader(":status", "307"),
                new BasicHeader("cache-control", "private"),
                new BasicHeader("date", "Mon, 21 Oct 2013 20:13:21 GMT"),
                new BasicHeader("location", "https://www.example.com"));

        buf.clear();
        encoder.encodeHeaders(buf, headers2, false, true);

        final byte[] expected2 = createByteArray(
                0x48, 0x83, 0x64, 0x0e, 0xff, 0xc1, 0xc0, 0xbf);
        Assert.assertArrayEquals(expected2, buf.toByteArray());

        Assert.assertEquals(4, dynamicTable.dynamicLength());
        assertHeaderEquals(new BasicHeader(":status", "307"), dynamicTable.getDynamicEntry(0));
        assertHeaderEquals(new BasicHeader("location", "https://www.example.com"), dynamicTable.getDynamicEntry(1));
        assertHeaderEquals(new BasicHeader("date", "Mon, 21 Oct 2013 20:13:21 GMT"), dynamicTable.getDynamicEntry(2));
        assertHeaderEquals(new BasicHeader("cache-control", "private"), dynamicTable.getDynamicEntry(3));

        Assert.assertEquals(222, dynamicTable.getCurrentSize());

        final List<Header> headers3 = Arrays.asList(
                new BasicHeader(":status", "200"),
                new BasicHeader("cache-control", "private"),
                new BasicHeader("date", "Mon, 21 Oct 2013 20:13:22 GMT"),
                new BasicHeader("location", "https://www.example.com"),
                new BasicHeader("content-encoding", "gzip"),
                new BasicHeader("set-cookie", "foo=ASDJKHQKBZXOQWEOPIUAXQWEOIU; max-age=3600; version=1"));

        buf.clear();
        encoder.encodeHeaders(buf, headers3, false, true);

        final byte[] expected3 = createByteArray(
                0x88, 0xc1, 0x61, 0x96, 0xd0, 0x7a, 0xbe, 0x94, 0x10, 0x54, 0xd4, 0x44, 0xa8, 0x20, 0x05, 0x95, 0x04,
                0x0b, 0x81, 0x66, 0xe0, 0x84, 0xa6, 0x2d, 0x1b, 0xff, 0xc0, 0x5a, 0x83, 0x9b, 0xd9, 0xab, 0x77, 0xad,
                0x94, 0xe7, 0x82, 0x1d, 0xd7, 0xf2, 0xe6, 0xc7, 0xb3, 0x35, 0xdf, 0xdf, 0xcd, 0x5b, 0x39, 0x60, 0xd5,
                0xaf, 0x27, 0x08, 0x7f, 0x36, 0x72, 0xc1, 0xab, 0x27, 0x0f, 0xb5, 0x29, 0x1f, 0x95, 0x87, 0x31, 0x60,
                0x65, 0xc0, 0x03, 0xed, 0x4e, 0xe5, 0xb1, 0x06, 0x3d, 0x50, 0x07);
        Assert.assertArrayEquals(expected3, buf.toByteArray());

        Assert.assertEquals(3, dynamicTable.dynamicLength());
        assertHeaderEquals(new BasicHeader("set-cookie", "foo=ASDJKHQKBZXOQWEOPIUAXQWEOIU; max-age=3600; version=1"), dynamicTable.getDynamicEntry(0));
        assertHeaderEquals(new BasicHeader("content-encoding", "gzip"), dynamicTable.getDynamicEntry(1));
        assertHeaderEquals(new BasicHeader("date", "Mon, 21 Oct 2013 20:13:22 GMT"), dynamicTable.getDynamicEntry(2));

        Assert.assertEquals(215, dynamicTable.getCurrentSize());
    }

    @Test
    public void testHeaderEntrySizeNonAscii() throws Exception {

        final ByteArrayBuffer buffer = new ByteArrayBuffer(128);
        final Header header = new BasicHeader("hello", constructHelloString(SWISS_GERMAN_HELLO, 1));

        final OutboundDynamicTable outboundTable1 = new OutboundDynamicTable();
        final HPackEncoder encoder1 = new HPackEncoder(outboundTable1, StandardCharsets.ISO_8859_1);
        final InboundDynamicTable inboundTable1 = new InboundDynamicTable();
        final HPackDecoder decoder1 = new HPackDecoder(inboundTable1, StandardCharsets.ISO_8859_1);

        encoder1.setMaxTableSize(48);
        decoder1.setMaxTableSize(48);

        encoder1.encodeHeader(buffer, header);
        assertHeaderEquals(header, decoder1.decodeHeader(wrap(buffer)));

        Assert.assertEquals(1, outboundTable1.dynamicLength());
        Assert.assertEquals(1, inboundTable1.dynamicLength());

        assertHeaderEquals(header, outboundTable1.getDynamicEntry(0));
        assertHeaderEquals(header, inboundTable1.getDynamicEntry(0));

        buffer.clear();

        final OutboundDynamicTable outboundTable2 = new OutboundDynamicTable();
        final HPackEncoder encoder2 = new HPackEncoder(outboundTable2, StandardCharsets.UTF_8);
        final InboundDynamicTable inboundTable2 = new InboundDynamicTable();
        final HPackDecoder decoder2 = new HPackDecoder(inboundTable2, StandardCharsets.UTF_8);

        encoder2.setMaxTableSize(48);
        decoder2.setMaxTableSize(48);

        encoder2.encodeHeader(buffer, header);
        assertHeaderEquals(header, decoder2.decodeHeader(wrap(buffer)));

        Assert.assertEquals(0, outboundTable2.dynamicLength());
        Assert.assertEquals(0, inboundTable2.dynamicLength());
    }

    @Test
    public void testHeaderSizeLimit() throws Exception {

        final HPackEncoder encoder = new HPackEncoder(StandardCharsets.US_ASCII);
        final HPackDecoder decoder = new HPackDecoder(StandardCharsets.US_ASCII);

        final ByteArrayBuffer buf = new ByteArrayBuffer(128);

        encoder.encodeHeaders(buf,
                Arrays.asList(
                        new BasicHeader("regular-header", "blah"),
                        new BasicHeader("big-f-header", "12345678901234567890123456789012345678901234567890" +
                                "123456789012345678901234567890123456789012345678901234567890")),
                false);

        MatcherAssert.assertThat(decoder.decodeHeaders(wrap(buf)).size(), CoreMatchers.equalTo(2));

        decoder.setMaxListSize(1000000);
        MatcherAssert.assertThat(decoder.decodeHeaders(wrap(buf)).size(), CoreMatchers.equalTo(2));

        decoder.setMaxListSize(200);
        Assert.assertThrows(HeaderListConstraintException.class, () ->
                decoder.decodeHeaders(wrap(buf)));
    }

}

