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
import org.assertj.core.api.Assertions;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

class TestHPackCoding {

    @Test
    void testIntegerEncodingRFC7541Examples() {

        final ByteArrayBuffer buffer = new ByteArrayBuffer(16);
        HPackEncoder.encodeInt(buffer, 5, 10, 0x0);

        Assertions.assertThat(buffer.length()).isEqualTo(1);
        Assertions.assertThat(buffer.byteAt(0) & 0xFF).isEqualTo(0b00001010);

        buffer.clear();
        HPackEncoder.encodeInt(buffer, 5, 1337, 0x0);

        Assertions.assertThat(buffer.length()).isEqualTo(3);
        Assertions.assertThat(buffer.byteAt(0) & 0xFF).isEqualTo(0b00011111);
        Assertions.assertThat(buffer.byteAt(1) & 0xFF).isEqualTo(0b10011010);
        Assertions.assertThat(buffer.byteAt(2) & 0xFF).isEqualTo(0b00001010);

        buffer.clear();
        HPackEncoder.encodeInt(buffer, 8, 42, 0x0);
        Assertions.assertThat(buffer.length()).isEqualTo(1);
        Assertions.assertThat(buffer.byteAt(0) & 0xFF).isEqualTo(0b00101010);
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
    void testIntegerCoding() throws Exception {

        final ByteArrayBuffer buffer = new ByteArrayBuffer(16);

        for (int n = 4; n <= 8; n++) {

            buffer.clear();

            HPackEncoder.encodeInt(buffer, n, 10, 0x0);
            Assertions.assertThat(HPackDecoder.decodeInt(wrap(buffer), n)).isEqualTo(10);

            buffer.clear();

            HPackEncoder.encodeInt(buffer, n, 123456, 0x0);
            Assertions.assertThat(HPackDecoder.decodeInt(wrap(buffer), n)).isEqualTo(123456);

            buffer.clear();

            HPackEncoder.encodeInt(buffer, n, Integer.MAX_VALUE, 0x0);
            Assertions.assertThat(HPackDecoder.decodeInt(wrap(buffer), n)).isEqualTo(Integer.MAX_VALUE);
        }

    }

    @Test
    void testIntegerCodingLimit() throws Exception {

        final ByteBuffer src1 = createByteBuffer(0x7f, 0x80, 0xff, 0xff, 0xff, 0x07);
        Assertions.assertThat(HPackDecoder.decodeInt(src1, 7)).isEqualTo(Integer.MAX_VALUE);

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
    void testPlainStringDecoding() throws Exception {

        final ByteBuffer src = createByteBuffer(
                0x0a, 0x63, 0x75, 0x73, 0x74, 0x6f, 0x6d, 0x2d, 0x6b, 0x65, 0x79);

        final ByteArrayBuffer buffer = new ByteArrayBuffer(16);
        HPackDecoder.decodePlainString(buffer, src);
        Assertions.assertThat(new String(buffer.array(), 0, buffer.length(), StandardCharsets.US_ASCII)).isEqualTo("custom-key");
        Assertions.assertThat(src.hasRemaining()).withFailMessage("Decoding completed").isFalse();
    }

    @Test
    void testPlainStringDecodingRemainingContent() throws Exception {

        final ByteBuffer src = createByteBuffer(
                0x0a, 0x63, 0x75, 0x73, 0x74, 0x6f, 0x6d, 0x2d, 0x6b, 0x65, 0x79, 0x01, 0x01, 0x01, 0x01);

        final ByteArrayBuffer buffer = new ByteArrayBuffer(16);
        HPackDecoder.decodePlainString(buffer, src);
        Assertions.assertThat(new String(buffer.array(), 0, buffer.length(), StandardCharsets.US_ASCII)).isEqualTo("custom-key");
        Assertions.assertThat(src.remaining()).isEqualTo(4);
    }

    @Test
    void testPlainStringDecodingReadOnly() throws Exception {

        final ByteBuffer src = createByteBuffer(
                0x0a, 0x63, 0x75, 0x73, 0x74, 0x6f, 0x6d, 0x2d, 0x6b, 0x65, 0x79, 0x50, 0x50, 0x50, 0x50);

        final ByteBuffer srcRO = src.asReadOnlyBuffer();
        final ByteArrayBuffer buffer = new ByteArrayBuffer(16);
        HPackDecoder.decodePlainString(buffer, srcRO);
        Assertions.assertThat(new String(buffer.array(), 0, buffer.length(), StandardCharsets.US_ASCII)).isEqualTo("custom-key");
        Assertions.assertThat(srcRO.remaining()).isEqualTo(4);
    }

    @Test
    void testPlainStringDecodingTruncated() {

        final ByteBuffer src = createByteBuffer(
                0x0a, 0x63, 0x75, 0x73, 0x74, 0x6f, 0x6d, 0x2d, 0x6b, 0x65);

        final ByteArrayBuffer buffer = new ByteArrayBuffer(16);
        Assertions.assertThatThrownBy(() ->
                        HPackDecoder.decodePlainString(buffer, src))
                .isInstanceOf(HPackException.class);
    }

    @Test
    void testHuffmanDecodingRFC7541Examples() throws Exception {
        final ByteBuffer src = createByteBuffer(
                0x8c, 0xf1, 0xe3, 0xc2, 0xe5, 0xf2, 0x3a, 0x6b, 0xa0, 0xab, 0x90, 0xf4, 0xff);

        final ByteArrayBuffer buffer = new ByteArrayBuffer(16);
        HPackDecoder.decodeHuffman(buffer, src);
        Assertions.assertThat(new String(buffer.array(), 0, buffer.length(), StandardCharsets.US_ASCII)).isEqualTo("www.example.com");
        Assertions.assertThat(src.hasRemaining()).withFailMessage("Decoding completed").isFalse();
    }

    private static ByteBuffer createByteBuffer(final String s, final Charset charset) {

        return ByteBuffer.wrap(s.getBytes(charset));
    }

    @Test
    void testHuffmanEncoding() {
        final ByteArrayBuffer buffer = new ByteArrayBuffer(16);
        HPackEncoder.encodeHuffman(buffer, createByteBuffer("www.example.com", StandardCharsets.US_ASCII));
        final ByteBuffer expected = createByteBuffer(
                0xf1, 0xe3, 0xc2, 0xe5, 0xf2, 0x3a, 0x6b, 0xa0, 0xab, 0x90, 0xf4, 0xff);
        Assertions.assertThat(buffer.toByteArray()).containsExactly(toArray(expected));
    }

    @Test
    void testBasicStringCoding() throws Exception {

        final HPackEncoder encoder = new HPackEncoder(Integer.MAX_VALUE, StandardCharsets.US_ASCII);
        final HPackDecoder decoder = new HPackDecoder(Integer.MAX_VALUE, StandardCharsets.US_ASCII);

        final ByteArrayBuffer buffer = new ByteArrayBuffer(16);
        encoder.encodeString(buffer, "this and that", false);

        final StringBuilder strBuf = new StringBuilder();
        decoder.decodeString(wrap(buffer), strBuf);
        Assertions.assertThat(strBuf.toString()).isEqualTo("this and that");

        buffer.clear();
        strBuf.setLength(0);
        encoder.encodeString(buffer, "this and that and Huffman", true);
        decoder.decodeString(wrap(buffer), strBuf);
        Assertions.assertThat(strBuf.toString()).isEqualTo("this and that and Huffman");
    }

    @Test
    void testEnsureCapacity() throws Exception {

        final HPackEncoder encoder = new HPackEncoder(Integer.MAX_VALUE, StandardCharsets.US_ASCII);
        final HPackDecoder decoder = new HPackDecoder(Integer.MAX_VALUE, StandardCharsets.UTF_8);

        final ByteArrayBuffer buffer = new ByteArrayBuffer(16);
        encoder.encodeString(buffer, "this and that", false);

        final StringBuilder strBuf = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            decoder.decodeString(wrap(buffer), strBuf);
            strBuf.delete(0,strBuf.length());
        }
        Assertions.assertThat(decoder.getTmpBufSize()).isEqualTo(256);
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
    void testComplexStringCoding1() throws Exception {

        for (final Charset charset : new Charset[]{StandardCharsets.ISO_8859_1, StandardCharsets.UTF_8, StandardCharsets.UTF_16}) {

            final ByteArrayBuffer buffer = new ByteArrayBuffer(16);
            final StringBuilder strBuf = new StringBuilder();

            final HPackEncoder encoder = new HPackEncoder(Integer.MAX_VALUE, charset);
            final HPackDecoder decoder = new HPackDecoder(Integer.MAX_VALUE, charset);

            for (int n = 0; n < 10; n++) {

                final String hello = constructHelloString(SWISS_GERMAN_HELLO, 1 + 10 * n);

                for (final boolean b : new boolean[]{false, true}) {

                    buffer.clear();
                    encoder.encodeString(buffer, hello, b);
                    strBuf.setLength(0);
                    decoder.decodeString(wrap(buffer), strBuf);
                    final String helloBack = strBuf.toString();
                    Assertions.assertThat(helloBack).withFailMessage("charset: " + charset + "; huffman: " + b).isEqualTo(hello);
                }
            }
        }
    }

    @Test
    void testComplexStringCoding2() throws Exception {

        for (final Charset charset : new Charset[]{Charset.forName("KOI8-R"), StandardCharsets.UTF_8, StandardCharsets.UTF_16}) {

            final ByteArrayBuffer buffer = new ByteArrayBuffer(16);
            final StringBuilder strBuf = new StringBuilder();

            final HPackEncoder encoder = new HPackEncoder(Integer.MAX_VALUE, charset);
            final HPackDecoder decoder = new HPackDecoder(Integer.MAX_VALUE, charset);

            for (int n = 0; n < 10; n++) {

                final String hello = constructHelloString(RUSSIAN_HELLO, 1 + 10 * n);

                for (final boolean b : new boolean[]{false, true}) {

                    buffer.clear();
                    strBuf.setLength(0);
                    encoder.encodeString(buffer, hello, b);
                    decoder.decodeString(wrap(buffer), strBuf);
                    final String helloBack = strBuf.toString();
                    Assertions.assertThat(helloBack).withFailMessage("charset: " + charset + "; huffman: " + b).isEqualTo(hello);
                }
            }
        }
    }

    private static void assertHeaderEquals(final Header expected, final Header actual) {

        Assertions.assertThat(actual).isNotNull();
        Assertions.assertThat(actual.getName()).withFailMessage("Header name").isEqualTo(expected.getName());
        Assertions.assertThat(actual.getValue()).withFailMessage("Header value").isEqualTo(expected.getValue());
        Assertions.assertThat(actual.isSensitive()).withFailMessage("Header sensitive flag").isEqualTo(expected.isSensitive());
    }

    @Test
    void testLiteralHeaderWithIndexingDecodingRFC7541Examples() throws Exception {

        final ByteBuffer src = createByteBuffer(
                0x40, 0x0a, 0x63, 0x75, 0x73, 0x74, 0x6f, 0x6d, 0x2d, 0x6b, 0x65, 0x79, 0x0d, 0x63, 0x75, 0x73,
                0x74, 0x6f, 0x6d, 0x2d, 0x68, 0x65, 0x61, 0x64, 0x65, 0x72);

        final InboundDynamicTable dynamicTable = new InboundDynamicTable();
        final HPackDecoder decoder = new HPackDecoder(dynamicTable, StandardCharsets.US_ASCII);
        final Header header = decoder.decodeLiteralHeader(src, HPackRepresentation.WITH_INDEXING);
        assertHeaderEquals(new BasicHeader("custom-key", "custom-header"), header);
        Assertions.assertThat(src.hasRemaining()).withFailMessage("Decoding completed").isFalse();

        Assertions.assertThat(dynamicTable.dynamicLength()).isEqualTo(1);
        assertHeaderEquals(header, dynamicTable.getDynamicEntry(0));
    }

    @Test
    void testLiteralHeaderWithoutIndexingDecodingRFC7541Examples() throws Exception {

        final ByteBuffer src = createByteBuffer(
                0x04, 0x0c, 0x2f, 0x73, 0x61, 0x6d, 0x70, 0x6c, 0x65, 0x2f, 0x70, 0x61, 0x74, 0x68);

        final InboundDynamicTable dynamicTable = new InboundDynamicTable();
        final HPackDecoder decoder = new HPackDecoder(dynamicTable, StandardCharsets.US_ASCII);
        final Header header = decoder.decodeLiteralHeader(src, HPackRepresentation.WITHOUT_INDEXING);
        assertHeaderEquals(new BasicHeader(":path", "/sample/path"), header);
        Assertions.assertThat(src.hasRemaining()).withFailMessage("Decoding completed").isFalse();

        Assertions.assertThat(dynamicTable.dynamicLength()).isEqualTo(0);
    }

    @Test
    void testLiteralHeaderNeverIndexedDecodingRFC7541Examples() throws Exception {

        final ByteBuffer src = createByteBuffer(
                0x10, 0x08, 0x70, 0x61, 0x73, 0x73, 0x77, 0x6f, 0x72, 0x64, 0x06, 0x73, 0x65, 0x63, 0x72, 0x65, 0x74);

        final InboundDynamicTable dynamicTable = new InboundDynamicTable();
        final HPackDecoder decoder = new HPackDecoder(dynamicTable, StandardCharsets.US_ASCII);
        final Header header = decoder.decodeLiteralHeader(src, HPackRepresentation.NEVER_INDEXED);
        assertHeaderEquals(new BasicHeader("password", "secret", true), header);
        Assertions.assertThat(src.hasRemaining()).withFailMessage("Decoding completed").isFalse();

        Assertions.assertThat(dynamicTable.dynamicLength()).isEqualTo(0);
    }

    @Test
    void testIndexedHeaderDecodingRFC7541Examples() throws Exception {

        final ByteBuffer src = createByteBuffer(0x82);

        final InboundDynamicTable dynamicTable = new InboundDynamicTable();
        final HPackDecoder decoder = new HPackDecoder(dynamicTable, StandardCharsets.US_ASCII);
        final Header header = decoder.decodeIndexedHeader(src);
        assertHeaderEquals(new BasicHeader(":method", "GET"), header);
        Assertions.assertThat(src.hasRemaining()).withFailMessage("Decoding completed").isFalse();

        Assertions.assertThat(dynamicTable.dynamicLength()).isEqualTo(0);
    }

    @Test
    void testRequestDecodingWithoutHuffmanRFC7541Examples() throws Exception {

        final ByteBuffer src1 = createByteBuffer(
                0x82, 0x86, 0x84, 0x41, 0x0f, 0x77, 0x77, 0x77, 0x2e, 0x65, 0x78, 0x61, 0x6d, 0x70, 0x6c, 0x65, 0x2e,
                0x63, 0x6f, 0x6d);

        final InboundDynamicTable dynamicTable = new InboundDynamicTable();
        final HPackDecoder decoder = new HPackDecoder(dynamicTable, StandardCharsets.US_ASCII);
        final List<Header> headers1 = decoder.decodeHeaders(src1);

        Assertions.assertThat(headers1.size()).isEqualTo(4);
        assertHeaderEquals(new BasicHeader(":method", "GET"), headers1.get(0));
        assertHeaderEquals(new BasicHeader(":scheme", "http"), headers1.get(1));
        assertHeaderEquals(new BasicHeader(":path", "/"), headers1.get(2));
        assertHeaderEquals(new BasicHeader(":authority", "www.example.com"), headers1.get(3));

        Assertions.assertThat(dynamicTable.dynamicLength()).isEqualTo(1);
        assertHeaderEquals(new BasicHeader(":authority", "www.example.com"), dynamicTable.getDynamicEntry(0));
        Assertions.assertThat(dynamicTable.getCurrentSize()).isEqualTo(57);

        final ByteBuffer src2 = createByteBuffer(
                0x82, 0x86, 0x84, 0xbe, 0x58, 0x08, 0x6e, 0x6f, 0x2d, 0x63, 0x61, 0x63, 0x68, 0x65);

        final List<Header> headers2 = decoder.decodeHeaders(src2);

        Assertions.assertThat(headers2.size()).isEqualTo(5);
        assertHeaderEquals(new BasicHeader(":method", "GET"), headers2.get(0));
        assertHeaderEquals(new BasicHeader(":scheme", "http"), headers2.get(1));
        assertHeaderEquals(new BasicHeader(":path", "/"), headers2.get(2));
        assertHeaderEquals(new BasicHeader(":authority", "www.example.com"), headers2.get(3));
        assertHeaderEquals(new BasicHeader("cache-control", "no-cache"), headers2.get(4));

        Assertions.assertThat(dynamicTable.dynamicLength()).isEqualTo(2);
        assertHeaderEquals(new BasicHeader("cache-control", "no-cache"), dynamicTable.getDynamicEntry(0));
        assertHeaderEquals(new BasicHeader(":authority", "www.example.com"), dynamicTable.getDynamicEntry(1));
        Assertions.assertThat(dynamicTable.getCurrentSize()).isEqualTo(110);

        final ByteBuffer src3 = createByteBuffer(
                0x82, 0x87, 0x85, 0xbf, 0x40, 0x0a, 0x63, 0x75, 0x73, 0x74, 0x6f, 0x6d, 0x2d, 0x6b, 0x65, 0x79,
                0x0c, 0x63, 0x75, 0x73, 0x74, 0x6f, 0x6d, 0x2d, 0x76, 0x61, 0x6c, 0x75, 0x65);

        final List<Header> headers3 = decoder.decodeHeaders(src3);

        Assertions.assertThat(headers3.size()).isEqualTo(5);
        assertHeaderEquals(new BasicHeader(":method", "GET"), headers3.get(0));
        assertHeaderEquals(new BasicHeader(":scheme", "https"), headers3.get(1));
        assertHeaderEquals(new BasicHeader(":path", "/index.html"), headers3.get(2));
        assertHeaderEquals(new BasicHeader(":authority", "www.example.com"), headers3.get(3));
        assertHeaderEquals(new BasicHeader("custom-key", "custom-value"), headers3.get(4));

        Assertions.assertThat(dynamicTable.dynamicLength()).isEqualTo(3);
        assertHeaderEquals(new BasicHeader("custom-key", "custom-value"), dynamicTable.getDynamicEntry(0));
        assertHeaderEquals(new BasicHeader("cache-control", "no-cache"), dynamicTable.getDynamicEntry(1));
        assertHeaderEquals(new BasicHeader(":authority", "www.example.com"), dynamicTable.getDynamicEntry(2));
        Assertions.assertThat(dynamicTable.getCurrentSize()).isEqualTo(164);
    }

    @Test
    void testRequestDecodingWithHuffmanRFC7541Examples() throws Exception {

        final ByteBuffer src1 = createByteBuffer(
                0x82, 0x86, 0x84, 0x41, 0x8c, 0xf1, 0xe3, 0xc2, 0xe5, 0xf2, 0x3a, 0x6b, 0xa0, 0xab, 0x90, 0xf4, 0xff);

        final InboundDynamicTable dynamicTable = new InboundDynamicTable();
        final HPackDecoder decoder = new HPackDecoder(dynamicTable, StandardCharsets.US_ASCII);
        final List<Header> headers1 = decoder.decodeHeaders(src1);

        Assertions.assertThat(headers1.size()).isEqualTo(4);
        assertHeaderEquals(new BasicHeader(":method", "GET"), headers1.get(0));
        assertHeaderEquals(new BasicHeader(":scheme", "http"), headers1.get(1));
        assertHeaderEquals(new BasicHeader(":path", "/"), headers1.get(2));
        assertHeaderEquals(new BasicHeader(":authority", "www.example.com"), headers1.get(3));

        Assertions.assertThat(dynamicTable.dynamicLength()).isEqualTo(1);
        assertHeaderEquals(new BasicHeader(":authority", "www.example.com"), dynamicTable.getDynamicEntry(0));
        Assertions.assertThat(dynamicTable.getCurrentSize()).isEqualTo(57);

        final ByteBuffer src2 = createByteBuffer(
                0x82, 0x86, 0x84, 0xbe, 0x58, 0x86, 0xa8, 0xeb, 0x10, 0x64, 0x9c, 0xbf);

        final List<Header> headers2 = decoder.decodeHeaders(src2);

        Assertions.assertThat(headers2.size()).isEqualTo(5);
        assertHeaderEquals(new BasicHeader(":method", "GET"), headers2.get(0));
        assertHeaderEquals(new BasicHeader(":scheme", "http"), headers2.get(1));
        assertHeaderEquals(new BasicHeader(":path", "/"), headers2.get(2));
        assertHeaderEquals(new BasicHeader(":authority", "www.example.com"), headers2.get(3));
        assertHeaderEquals(new BasicHeader("cache-control", "no-cache"), headers2.get(4));

        Assertions.assertThat(dynamicTable.dynamicLength()).isEqualTo(2);
        assertHeaderEquals(new BasicHeader("cache-control", "no-cache"), dynamicTable.getDynamicEntry(0));
        assertHeaderEquals(new BasicHeader(":authority", "www.example.com"), dynamicTable.getDynamicEntry(1));
        Assertions.assertThat(dynamicTable.getCurrentSize()).isEqualTo(110);

        final ByteBuffer src3 = createByteBuffer(
                0x82, 0x87, 0x85, 0xbf, 0x40, 0x88, 0x25, 0xa8, 0x49, 0xe9, 0x5b, 0xa9, 0x7d, 0x7f, 0x89, 0x25,
                0xa8, 0x49, 0xe9, 0x5b, 0xb8, 0xe8, 0xb4, 0xbf);

        final List<Header> headers3 = decoder.decodeHeaders(src3);

        Assertions.assertThat(headers3.size()).isEqualTo(5);
        assertHeaderEquals(new BasicHeader(":method", "GET"), headers3.get(0));
        assertHeaderEquals(new BasicHeader(":scheme", "https"), headers3.get(1));
        assertHeaderEquals(new BasicHeader(":path", "/index.html"), headers3.get(2));
        assertHeaderEquals(new BasicHeader(":authority", "www.example.com"), headers3.get(3));
        assertHeaderEquals(new BasicHeader("custom-key", "custom-value"), headers3.get(4));

        Assertions.assertThat(dynamicTable.dynamicLength()).isEqualTo(3);
        assertHeaderEquals(new BasicHeader("custom-key", "custom-value"), dynamicTable.getDynamicEntry(0));
        assertHeaderEquals(new BasicHeader("cache-control", "no-cache"), dynamicTable.getDynamicEntry(1));
        assertHeaderEquals(new BasicHeader(":authority", "www.example.com"), dynamicTable.getDynamicEntry(2));
        Assertions.assertThat(dynamicTable.getCurrentSize()).isEqualTo(164);
    }

    @Test
    void testResponseDecodingWithoutHuffmanRFC7541Examples() throws Exception {

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

        Assertions.assertThat(headers1.size()).isEqualTo(4);
        assertHeaderEquals(new BasicHeader(":status", "302"), headers1.get(0));
        assertHeaderEquals(new BasicHeader("cache-control", "private"), headers1.get(1));
        assertHeaderEquals(new BasicHeader("date", "Mon, 21 Oct 2013 20:13:21 GMT"), headers1.get(2));
        assertHeaderEquals(new BasicHeader("location", "https://www.example.com"), headers1.get(3));

        Assertions.assertThat(dynamicTable.dynamicLength()).isEqualTo(4);
        assertHeaderEquals(new BasicHeader("location", "https://www.example.com"), dynamicTable.getDynamicEntry(0));
        assertHeaderEquals(new BasicHeader("date", "Mon, 21 Oct 2013 20:13:21 GMT"), dynamicTable.getDynamicEntry(1));
        assertHeaderEquals(new BasicHeader("cache-control", "private"), dynamicTable.getDynamicEntry(2));
        assertHeaderEquals(new BasicHeader(":status", "302"), dynamicTable.getDynamicEntry(3));
        Assertions.assertThat(dynamicTable.getCurrentSize()).isEqualTo(222);

        final ByteBuffer src2 = createByteBuffer(
                0x48, 0x03, 0x33, 0x30, 0x37, 0xc1, 0xc0, 0xbf);

        final List<Header> headers2 = decoder.decodeHeaders(src2);

        Assertions.assertThat(headers2.size()).isEqualTo(4);
        assertHeaderEquals(new BasicHeader(":status", "307"), headers2.get(0));
        assertHeaderEquals(new BasicHeader("cache-control", "private"), headers2.get(1));
        assertHeaderEquals(new BasicHeader("date", "Mon, 21 Oct 2013 20:13:21 GMT"), headers2.get(2));
        assertHeaderEquals(new BasicHeader("location", "https://www.example.com"), headers2.get(3));

        Assertions.assertThat(dynamicTable.dynamicLength()).isEqualTo(4);
        assertHeaderEquals(new BasicHeader(":status", "307"), dynamicTable.getDynamicEntry(0));
        assertHeaderEquals(new BasicHeader("location", "https://www.example.com"), dynamicTable.getDynamicEntry(1));
        assertHeaderEquals(new BasicHeader("date", "Mon, 21 Oct 2013 20:13:21 GMT"), dynamicTable.getDynamicEntry(2));
        assertHeaderEquals(new BasicHeader("cache-control", "private"), dynamicTable.getDynamicEntry(3));

        Assertions.assertThat(dynamicTable.getCurrentSize()).isEqualTo(222);

        final ByteBuffer src3 = createByteBuffer(
                0x88, 0xc1, 0x61, 0x1d, 0x4d, 0x6f, 0x6e, 0x2c, 0x20, 0x32, 0x31, 0x20, 0x4f, 0x63, 0x74, 0x20, 0x32,
                0x30, 0x31, 0x33, 0x20, 0x32, 0x30, 0x3a, 0x31, 0x33, 0x3a, 0x32, 0x32, 0x20, 0x47, 0x4d, 0x54, 0xc0,
                0x5a, 0x04, 0x67, 0x7a, 0x69, 0x70, 0x77, 0x38, 0x66, 0x6f, 0x6f, 0x3d, 0x41, 0x53, 0x44, 0x4a, 0x4b,
                0x48, 0x51, 0x4b, 0x42, 0x5a, 0x58, 0x4f, 0x51, 0x57, 0x45, 0x4f, 0x50, 0x49, 0x55, 0x41, 0x58, 0x51,
                0x57, 0x45, 0x4f, 0x49, 0x55, 0x3b, 0x20, 0x6d, 0x61, 0x78, 0x2d, 0x61, 0x67, 0x65, 0x3d, 0x33, 0x36,
                0x30, 0x30, 0x3b, 0x20, 0x76, 0x65, 0x72, 0x73, 0x69, 0x6f, 0x6e, 0x3d, 0x31);

        final List<Header> headers3 = decoder.decodeHeaders(src3);

        Assertions.assertThat(headers3.size()).isEqualTo(6);
        assertHeaderEquals(new BasicHeader(":status", "200"), headers3.get(0));
        assertHeaderEquals(new BasicHeader("cache-control", "private"), headers3.get(1));
        assertHeaderEquals(new BasicHeader("date", "Mon, 21 Oct 2013 20:13:22 GMT"), headers3.get(2));
        assertHeaderEquals(new BasicHeader("location", "https://www.example.com"), headers3.get(3));
        assertHeaderEquals(new BasicHeader("content-encoding", "gzip"), headers3.get(4));
        assertHeaderEquals(new BasicHeader("set-cookie", "foo=ASDJKHQKBZXOQWEOPIUAXQWEOIU; max-age=3600; version=1"), headers3.get(5));

        Assertions.assertThat(dynamicTable.dynamicLength()).isEqualTo(3);
        assertHeaderEquals(new BasicHeader("set-cookie", "foo=ASDJKHQKBZXOQWEOPIUAXQWEOIU; max-age=3600; version=1"), dynamicTable.getDynamicEntry(0));
        assertHeaderEquals(new BasicHeader("content-encoding", "gzip"), dynamicTable.getDynamicEntry(1));
        assertHeaderEquals(new BasicHeader("date", "Mon, 21 Oct 2013 20:13:22 GMT"), dynamicTable.getDynamicEntry(2));

        Assertions.assertThat(dynamicTable.getCurrentSize()).isEqualTo(215);
    }

    @Test
    void testResponseDecodingWithHuffmanRFC7541Examples() throws Exception {

        final ByteBuffer src1 = createByteBuffer(
                0x48, 0x82, 0x64, 0x02, 0x58, 0x85, 0xae, 0xc3, 0x77, 0x1a, 0x4b, 0x61, 0x96, 0xd0, 0x7a, 0xbe, 0x94,
                0x10, 0x54, 0xd4, 0x44, 0xa8, 0x20, 0x05, 0x95, 0x04, 0x0b, 0x81, 0x66, 0xe0, 0x82, 0xa6, 0x2d, 0x1b,
                0xff, 0x6e, 0x91, 0x9d, 0x29, 0xad, 0x17, 0x18, 0x63, 0xc7, 0x8f, 0x0b, 0x97, 0xc8, 0xe9, 0xae, 0x82,
                0xae, 0x43, 0xd3);

        final InboundDynamicTable dynamicTable = new InboundDynamicTable();
        dynamicTable.setMaxSize(256);
        final HPackDecoder decoder = new HPackDecoder(dynamicTable, StandardCharsets.US_ASCII);
        final List<Header> headers1 = decoder.decodeHeaders(src1);

        Assertions.assertThat(headers1.size()).isEqualTo(4);
        assertHeaderEquals(new BasicHeader(":status", "302"), headers1.get(0));
        assertHeaderEquals(new BasicHeader("cache-control", "private"), headers1.get(1));
        assertHeaderEquals(new BasicHeader("date", "Mon, 21 Oct 2013 20:13:21 GMT"), headers1.get(2));
        assertHeaderEquals(new BasicHeader("location", "https://www.example.com"), headers1.get(3));

        Assertions.assertThat(dynamicTable.dynamicLength()).isEqualTo(4);
        assertHeaderEquals(new BasicHeader("location", "https://www.example.com"), dynamicTable.getDynamicEntry(0));
        assertHeaderEquals(new BasicHeader("date", "Mon, 21 Oct 2013 20:13:21 GMT"), dynamicTable.getDynamicEntry(1));
        assertHeaderEquals(new BasicHeader("cache-control", "private"), dynamicTable.getDynamicEntry(2));
        assertHeaderEquals(new BasicHeader(":status", "302"), dynamicTable.getDynamicEntry(3));
        Assertions.assertThat(dynamicTable.getCurrentSize()).isEqualTo(222);

        final ByteBuffer src2 = createByteBuffer(
                0x48, 0x83, 0x64, 0x0e, 0xff, 0xc1, 0xc0, 0xbf);

        final List<Header> headers2 = decoder.decodeHeaders(src2);

        Assertions.assertThat(headers2.size()).isEqualTo(4);
        assertHeaderEquals(new BasicHeader(":status", "307"), headers2.get(0));
        assertHeaderEquals(new BasicHeader("cache-control", "private"), headers2.get(1));
        assertHeaderEquals(new BasicHeader("date", "Mon, 21 Oct 2013 20:13:21 GMT"), headers2.get(2));
        assertHeaderEquals(new BasicHeader("location", "https://www.example.com"), headers2.get(3));

        Assertions.assertThat(dynamicTable.dynamicLength()).isEqualTo(4);
        assertHeaderEquals(new BasicHeader(":status", "307"), dynamicTable.getDynamicEntry(0));
        assertHeaderEquals(new BasicHeader("location", "https://www.example.com"), dynamicTable.getDynamicEntry(1));
        assertHeaderEquals(new BasicHeader("date", "Mon, 21 Oct 2013 20:13:21 GMT"), dynamicTable.getDynamicEntry(2));
        assertHeaderEquals(new BasicHeader("cache-control", "private"), dynamicTable.getDynamicEntry(3));

        Assertions.assertThat(dynamicTable.getCurrentSize()).isEqualTo(222);

        final ByteBuffer src3 = createByteBuffer(
                0x88, 0xc1, 0x61, 0x96, 0xd0, 0x7a, 0xbe, 0x94, 0x10, 0x54, 0xd4, 0x44, 0xa8, 0x20, 0x05, 0x95, 0x04,
                0x0b, 0x81, 0x66, 0xe0, 0x84, 0xa6, 0x2d, 0x1b, 0xff, 0xc0, 0x5a, 0x83, 0x9b, 0xd9, 0xab, 0x77, 0xad,
                0x94, 0xe7, 0x82, 0x1d, 0xd7, 0xf2, 0xe6, 0xc7, 0xb3, 0x35, 0xdf, 0xdf, 0xcd, 0x5b, 0x39, 0x60, 0xd5,
                0xaf, 0x27, 0x08, 0x7f, 0x36, 0x72, 0xc1, 0xab, 0x27, 0x0f, 0xb5, 0x29, 0x1f, 0x95, 0x87, 0x31, 0x60,
                0x65, 0xc0, 0x03, 0xed, 0x4e, 0xe5, 0xb1, 0x06, 0x3d, 0x50, 0x07);

        final List<Header> headers3 = decoder.decodeHeaders(src3);

        Assertions.assertThat(headers3.size()).isEqualTo(6);
        assertHeaderEquals(new BasicHeader(":status", "200"), headers3.get(0));
        assertHeaderEquals(new BasicHeader("cache-control", "private"), headers3.get(1));
        assertHeaderEquals(new BasicHeader("date", "Mon, 21 Oct 2013 20:13:22 GMT"), headers3.get(2));
        assertHeaderEquals(new BasicHeader("location", "https://www.example.com"), headers3.get(3));
        assertHeaderEquals(new BasicHeader("content-encoding", "gzip"), headers3.get(4));
        assertHeaderEquals(new BasicHeader("set-cookie", "foo=ASDJKHQKBZXOQWEOPIUAXQWEOIU; max-age=3600; version=1"), headers3.get(5));

        Assertions.assertThat(dynamicTable.dynamicLength()).isEqualTo(3);
        assertHeaderEquals(new BasicHeader("set-cookie", "foo=ASDJKHQKBZXOQWEOPIUAXQWEOIU; max-age=3600; version=1"), dynamicTable.getDynamicEntry(0));
        assertHeaderEquals(new BasicHeader("content-encoding", "gzip"), dynamicTable.getDynamicEntry(1));
        assertHeaderEquals(new BasicHeader("date", "Mon, 21 Oct 2013 20:13:22 GMT"), dynamicTable.getDynamicEntry(2));

        Assertions.assertThat(dynamicTable.getCurrentSize()).isEqualTo(215);
    }

    private static byte[] createByteArray(final int... bytes) {
        final byte[] buffer = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            buffer[i] = (byte) bytes[i];
        }
        return buffer;
    }

    @Test
    void testLiteralHeaderWithIndexingEncodingRFC7541Examples() throws Exception {

        final OutboundDynamicTable dynamicTable = new OutboundDynamicTable();
        final HPackEncoder encoder = new HPackEncoder(dynamicTable, StandardCharsets.US_ASCII);

        final ByteArrayBuffer buf = new ByteArrayBuffer(128);

        final Header header = new BasicHeader("custom-key", "custom-header");
        encoder.encodeLiteralHeader(buf, null, header, HPackRepresentation.WITH_INDEXING, false);

        final byte[] expected = createByteArray(
                0x40, 0x0a, 0x63, 0x75, 0x73, 0x74, 0x6f, 0x6d, 0x2d, 0x6b, 0x65, 0x79, 0x0d, 0x63, 0x75, 0x73,
                0x74, 0x6f, 0x6d, 0x2d, 0x68, 0x65, 0x61, 0x64, 0x65, 0x72);

        Assertions.assertThat(buf.toByteArray()).containsExactly(expected);
    }

    @Test
    void testLiteralHeaderWithoutIndexingEncodingRFC7541Examples() throws Exception {

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
        Assertions.assertThat(buf.toByteArray()).containsExactly(expected);
    }

    @Test
    void testLiteralHeaderNeverIndexedEncodingRFC7541Examples() throws Exception {

        final OutboundDynamicTable dynamicTable = new OutboundDynamicTable();
        final HPackEncoder encoder = new HPackEncoder(dynamicTable, StandardCharsets.US_ASCII);

        final ByteArrayBuffer buf = new ByteArrayBuffer(128);

        final Header header = new BasicHeader("password", "secret", true);
        encoder.encodeLiteralHeader(buf, null, header, HPackRepresentation.NEVER_INDEXED, false);

        final byte[] expected = createByteArray(
                0x10, 0x08, 0x70, 0x61, 0x73, 0x73, 0x77, 0x6f, 0x72, 0x64, 0x06, 0x73, 0x65, 0x63, 0x72, 0x65, 0x74);
        Assertions.assertThat(buf.toByteArray()).containsExactly(expected);
    }

    @Test
    void testIndexedHeaderEncodingRFC7541Examples() {

        final OutboundDynamicTable dynamicTable = new OutboundDynamicTable();
        final HPackEncoder encoder = new HPackEncoder(dynamicTable, StandardCharsets.US_ASCII);

        final ByteArrayBuffer buf = new ByteArrayBuffer(128);
        encoder.encodeIndex(buf, 2);

        final byte[] expected = createByteArray(0x82);
        Assertions.assertThat(buf.toByteArray()).containsExactly(expected);
    }

    @Test
    void testRequestEncodingWithoutHuffmanRFC7541Examples() throws Exception {

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
        Assertions.assertThat(buf.toByteArray()).containsExactly(expected1);

        Assertions.assertThat(dynamicTable.dynamicLength()).isEqualTo(1);
        assertHeaderEquals(new BasicHeader(":authority", "www.example.com"), dynamicTable.getDynamicEntry(0));
        Assertions.assertThat(dynamicTable.getCurrentSize()).isEqualTo(57);

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
        Assertions.assertThat(buf.toByteArray()).containsExactly(expected2);

        Assertions.assertThat(dynamicTable.dynamicLength()).isEqualTo(2);
        assertHeaderEquals(new BasicHeader("cache-control", "no-cache"), dynamicTable.getDynamicEntry(0));
        assertHeaderEquals(new BasicHeader(":authority", "www.example.com"), dynamicTable.getDynamicEntry(1));
        Assertions.assertThat(dynamicTable.getCurrentSize()).isEqualTo(110);

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
        Assertions.assertThat(buf.toByteArray()).containsExactly(expected3);

        Assertions.assertThat(dynamicTable.dynamicLength()).isEqualTo(3);
        assertHeaderEquals(new BasicHeader("custom-key", "custom-value"), dynamicTable.getDynamicEntry(0));
        assertHeaderEquals(new BasicHeader("cache-control", "no-cache"), dynamicTable.getDynamicEntry(1));
        assertHeaderEquals(new BasicHeader(":authority", "www.example.com"), dynamicTable.getDynamicEntry(2));
        Assertions.assertThat(dynamicTable.getCurrentSize()).isEqualTo(164);
    }

    @Test
    void testRequestEncodingWithHuffmanRFC7541Examples() throws Exception {

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
        Assertions.assertThat(buf.toByteArray()).containsExactly(expected1);

        Assertions.assertThat(dynamicTable.dynamicLength()).isEqualTo(1);
        assertHeaderEquals(new BasicHeader(":authority", "www.example.com"), dynamicTable.getDynamicEntry(0));
        Assertions.assertThat(dynamicTable.getCurrentSize()).isEqualTo(57);

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
        Assertions.assertThat(buf.toByteArray()).containsExactly(expected2);

        Assertions.assertThat(dynamicTable.dynamicLength()).isEqualTo(2);
        assertHeaderEquals(new BasicHeader("cache-control", "no-cache"), dynamicTable.getDynamicEntry(0));
        assertHeaderEquals(new BasicHeader(":authority", "www.example.com"), dynamicTable.getDynamicEntry(1));
        Assertions.assertThat(dynamicTable.getCurrentSize()).isEqualTo(110);

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
        Assertions.assertThat(buf.toByteArray()).containsExactly(expected3);

        Assertions.assertThat(dynamicTable.dynamicLength()).isEqualTo(3);
        assertHeaderEquals(new BasicHeader("custom-key", "custom-value"), dynamicTable.getDynamicEntry(0));
        assertHeaderEquals(new BasicHeader("cache-control", "no-cache"), dynamicTable.getDynamicEntry(1));
        assertHeaderEquals(new BasicHeader(":authority", "www.example.com"), dynamicTable.getDynamicEntry(2));
        Assertions.assertThat(dynamicTable.getCurrentSize()).isEqualTo(164);
    }

    @Test
    void testResponseEncodingWithoutHuffmanRFC7541Examples() throws Exception {

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
        Assertions.assertThat(buf.toByteArray()).containsExactly(expected1);

        Assertions.assertThat(dynamicTable.dynamicLength()).isEqualTo(4);
        assertHeaderEquals(new BasicHeader("location", "https://www.example.com"), dynamicTable.getDynamicEntry(0));
        assertHeaderEquals(new BasicHeader("date", "Mon, 21 Oct 2013 20:13:21 GMT"), dynamicTable.getDynamicEntry(1));
        assertHeaderEquals(new BasicHeader("cache-control", "private"), dynamicTable.getDynamicEntry(2));
        assertHeaderEquals(new BasicHeader(":status", "302"), dynamicTable.getDynamicEntry(3));
        Assertions.assertThat(dynamicTable.getCurrentSize()).isEqualTo(222);

        final List<Header> headers2 = Arrays.asList(
                new BasicHeader(":status", "307"),
                new BasicHeader("cache-control", "private"),
                new BasicHeader("date", "Mon, 21 Oct 2013 20:13:21 GMT"),
                new BasicHeader("location", "https://www.example.com"));

        buf.clear();
        encoder.encodeHeaders(buf, headers2, false, false);

        final byte[] expected2 = createByteArray(
                0x48, 0x03, 0x33, 0x30, 0x37, 0xc1, 0xc0, 0xbf);
        Assertions.assertThat(buf.toByteArray()).containsExactly(expected2);

        Assertions.assertThat(dynamicTable.dynamicLength()).isEqualTo(4);
        assertHeaderEquals(new BasicHeader(":status", "307"), dynamicTable.getDynamicEntry(0));
        assertHeaderEquals(new BasicHeader("location", "https://www.example.com"), dynamicTable.getDynamicEntry(1));
        assertHeaderEquals(new BasicHeader("date", "Mon, 21 Oct 2013 20:13:21 GMT"), dynamicTable.getDynamicEntry(2));
        assertHeaderEquals(new BasicHeader("cache-control", "private"), dynamicTable.getDynamicEntry(3));

        Assertions.assertThat(dynamicTable.getCurrentSize()).isEqualTo(222);

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
        Assertions.assertThat(buf.toByteArray()).containsExactly(expected3);

        Assertions.assertThat(dynamicTable.dynamicLength()).isEqualTo(3);
        assertHeaderEquals(new BasicHeader("set-cookie", "foo=ASDJKHQKBZXOQWEOPIUAXQWEOIU; max-age=3600; version=1"), dynamicTable.getDynamicEntry(0));
        assertHeaderEquals(new BasicHeader("content-encoding", "gzip"), dynamicTable.getDynamicEntry(1));
        assertHeaderEquals(new BasicHeader("date", "Mon, 21 Oct 2013 20:13:22 GMT"), dynamicTable.getDynamicEntry(2));

        Assertions.assertThat(dynamicTable.getCurrentSize()).isEqualTo(215);
    }

    @Test
    void testResponseEncodingWithHuffmanRFC7541Examples() throws Exception {

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
        Assertions.assertThat(buf.toByteArray()).containsExactly(expected1);

        Assertions.assertThat(dynamicTable.dynamicLength()).isEqualTo(4);
        assertHeaderEquals(new BasicHeader("location", "https://www.example.com"), dynamicTable.getDynamicEntry(0));
        assertHeaderEquals(new BasicHeader("date", "Mon, 21 Oct 2013 20:13:21 GMT"), dynamicTable.getDynamicEntry(1));
        assertHeaderEquals(new BasicHeader("cache-control", "private"), dynamicTable.getDynamicEntry(2));
        assertHeaderEquals(new BasicHeader(":status", "302"), dynamicTable.getDynamicEntry(3));
        Assertions.assertThat(dynamicTable.getCurrentSize()).isEqualTo(222);

        final List<Header> headers2 = Arrays.asList(
                new BasicHeader(":status", "307"),
                new BasicHeader("cache-control", "private"),
                new BasicHeader("date", "Mon, 21 Oct 2013 20:13:21 GMT"),
                new BasicHeader("location", "https://www.example.com"));

        buf.clear();
        encoder.encodeHeaders(buf, headers2, false, true);

        final byte[] expected2 = createByteArray(
                0x48, 0x83, 0x64, 0x0e, 0xff, 0xc1, 0xc0, 0xbf);
        Assertions.assertThat(buf.toByteArray()).containsExactly(expected2);

        Assertions.assertThat(dynamicTable.dynamicLength()).isEqualTo(4);
        assertHeaderEquals(new BasicHeader(":status", "307"), dynamicTable.getDynamicEntry(0));
        assertHeaderEquals(new BasicHeader("location", "https://www.example.com"), dynamicTable.getDynamicEntry(1));
        assertHeaderEquals(new BasicHeader("date", "Mon, 21 Oct 2013 20:13:21 GMT"), dynamicTable.getDynamicEntry(2));
        assertHeaderEquals(new BasicHeader("cache-control", "private"), dynamicTable.getDynamicEntry(3));

        Assertions.assertThat(dynamicTable.getCurrentSize()).isEqualTo(222);

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
        Assertions.assertThat(buf.toByteArray()).containsExactly(expected3);

        Assertions.assertThat(dynamicTable.dynamicLength()).isEqualTo(3);
        assertHeaderEquals(new BasicHeader("set-cookie", "foo=ASDJKHQKBZXOQWEOPIUAXQWEOIU; max-age=3600; version=1"), dynamicTable.getDynamicEntry(0));
        assertHeaderEquals(new BasicHeader("content-encoding", "gzip"), dynamicTable.getDynamicEntry(1));
        assertHeaderEquals(new BasicHeader("date", "Mon, 21 Oct 2013 20:13:22 GMT"), dynamicTable.getDynamicEntry(2));

        Assertions.assertThat(dynamicTable.getCurrentSize()).isEqualTo(215);
    }

    @Test
    void testHeaderEntrySizeNonAscii() throws Exception {

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

        Assertions.assertThat(outboundTable1.dynamicLength()).isEqualTo(1);
        Assertions.assertThat(inboundTable1.dynamicLength()).isEqualTo(1);

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

        Assertions.assertThat(outboundTable2.dynamicLength()).isEqualTo(0);
        Assertions.assertThat(inboundTable2.dynamicLength()).isEqualTo(0);
    }

    @Test
    void testHeaderSizeLimit() throws Exception {

        final HPackEncoder encoder = new HPackEncoder(Integer.MAX_VALUE, StandardCharsets.US_ASCII);
        final HPackDecoder decoder = new HPackDecoder(Integer.MAX_VALUE, StandardCharsets.US_ASCII);

        final ByteArrayBuffer buf = new ByteArrayBuffer(128);

        encoder.encodeHeaders(buf,
                Arrays.asList(
                        new BasicHeader("regular-header", "blah"),
                        new BasicHeader("big-f-header", "12345678901234567890123456789012345678901234567890" +
                                "123456789012345678901234567890123456789012345678901234567890")),
                false);

        Assertions.assertThat(decoder.decodeHeaders(wrap(buf)).size()).isEqualTo(2);

        decoder.setMaxListSize(1000000);
        Assertions.assertThat(decoder.decodeHeaders(wrap(buf)).size()).isEqualTo(2);

        decoder.setMaxListSize(200);
        Assertions.assertThatThrownBy(() ->
                decoder.decodeHeaders(wrap(buf))).isInstanceOf(HeaderListConstraintException.class);
    }

    @Test
    void testHeaderEmptyASCII() throws Exception {

        final HPackEncoder encoder = new HPackEncoder(Integer.MAX_VALUE, StandardCharsets.US_ASCII);
        final HPackDecoder decoder = new HPackDecoder(Integer.MAX_VALUE, StandardCharsets.US_ASCII);

        final ByteArrayBuffer buf = new ByteArrayBuffer(128);

        final Header header = new BasicHeader("empty-header", "");
        encoder.encodeHeader(buf, header);

        assertHeaderEquals(header, decoder.decodeHeader(wrap(buf)));
    }

    @Test
    void testHeaderEmptyUTF8() throws Exception {

        final HPackEncoder encoder = new HPackEncoder(Integer.MAX_VALUE, StandardCharsets.UTF_8);
        final HPackDecoder decoder = new HPackDecoder(Integer.MAX_VALUE, StandardCharsets.UTF_8);

        final ByteArrayBuffer buf = new ByteArrayBuffer(128);

        final Header header = new BasicHeader("empty-header", "");
        encoder.encodeHeader(buf, header);

        assertHeaderEquals(header, decoder.decodeHeader(wrap(buf)));
    }

    @Test
    void encoderDynamicHeaderTableMaxSizeNotIncreasedBySettingsFrame() throws Exception {
        final OutboundDynamicTable dynamicTable = new OutboundDynamicTable(4096);
        final HPackEncoder encoder = new HPackEncoder(dynamicTable, StandardCharsets.UTF_8);
        //emulate receiving a settings frame from the receiver
        encoder.setMaxTableSize(Integer.MAX_VALUE);
        //actual table size should not change until we are able to send an update to the receiver
        Assertions.assertThat(dynamicTable.getMaxSize()).isEqualTo(4096);
    }

    @Test
    void encoderDynamicHeaderTableMaxSizeChangeCausesUpdateHeader() throws Exception {
        final OutboundDynamicTable dynamicTable = new OutboundDynamicTable(4096);
        final HPackEncoder encoder = new HPackEncoder(dynamicTable, StandardCharsets.UTF_8);
        //emulate receiving a settings frame from the receiver
        encoder.setMaxTableSize(8192);

        final ByteArrayBuffer buf = new ByteArrayBuffer(128);

        final Header header = new BasicHeader("empty-header", "");
        encoder.encodeHeader(buf, header);

        final int firstByte = buf.byteAt(0);
        final int masked = firstByte & 0xe0;

        //first header field is table size update
        Assertions.assertThat(masked).isEqualTo(0x20);

        //update has new table size as value
        Assertions.assertThat(HPackDecoder.decodeInt(wrap(buf), 5)).isEqualTo(8192);
    }

    @Test
    void decoderDynamicHeaderTableMaxSizeNotIncreasedBySettingsFrame() throws Exception {
        final InboundDynamicTable dynamicTable = new InboundDynamicTable(4096);
        final HPackDecoder decoder = new HPackDecoder(dynamicTable, StandardCharsets.UTF_8);
        //emulate something on our side changing the max dynamic table size
        //this would cause us to send a new settings frame to the sender
        decoder.setMaxTableSize(Integer.MAX_VALUE);
        //actual table size should not change until sender sends us a table update
        Assertions.assertThat(dynamicTable.getMaxSize()).isEqualTo(4096);
    }

    @Test
    void decoderDynamicHeaderTableMaxSizeUpdatesAfter() throws Exception {
        final InboundDynamicTable dynamicTable = new InboundDynamicTable(4096);
        final HPackDecoder decoder = new HPackDecoder(dynamicTable, StandardCharsets.UTF_8);
        decoder.setMaxTableSize(Integer.MAX_VALUE);

        final ByteArrayBuffer buf = new ByteArrayBuffer(128);
        HPackEncoder.encodeInt(buf, 5, 8192, 0x20);

        decoder.decodeHeaders(wrap(buf));

        Assertions.assertThat(dynamicTable.getMaxSize()).isEqualTo(8192);
    }

    @Test
    void decoderDynamicHeaderTableMaxSizeLimitedByConfig() throws Exception {
        final InboundDynamicTable dynamicTable = new InboundDynamicTable(4096);
        final HPackDecoder decoder = new HPackDecoder(dynamicTable, StandardCharsets.UTF_8);
        //do not increase max size, this should limit requests from the receiver to increase max size

        //emulate receiving header that illegally increases the table size above our max
        final ByteArrayBuffer buf = new ByteArrayBuffer(128);
        HPackEncoder.encodeInt(buf, 5, 8192, 0x20);
        Assertions.assertThatThrownBy(() ->
                        decoder.decodeHeaders(wrap(buf)))
                .isInstanceOf(HPackException.class);
    }

    @Test
    void decoderRejectsDynamicTableSizeUpdateMidHeaderBlock() {
        final InboundDynamicTable dynamicTable = new InboundDynamicTable(4096);
        final HPackDecoder decoder = new HPackDecoder(dynamicTable, StandardCharsets.US_ASCII);

        final ByteBuffer src = createByteBuffer(
                0x82, // :method: GET
                0x20  // dynamic table size update (0) - illegal after first header field
        );

        final HPackException ex = Assertions.assertThatThrownBy(() ->
                        decoder.decodeHeaders(src))
                .isInstanceOf(HPackException.class).asInstanceOf(InstanceOfAssertFactories.type(HPackException.class)).actual();
        Assertions.assertThat(ex.getMessage().contains("beginning of a header block")).isTrue();
    }

    @Test
    void decoderAllowsMultipleLeadingDynamicTableSizeUpdates() throws Exception {
        final InboundDynamicTable dynamicTable = new InboundDynamicTable(4096);
        final HPackDecoder decoder = new HPackDecoder(dynamicTable, StandardCharsets.US_ASCII);

        final ByteBuffer src = createByteBuffer(
                0x20, // dynamic table size update (0)
                0x2a, // dynamic table size update (10)
                0x82  // :method: GET
        );

        final List<Header> headers = decoder.decodeHeaders(src);
        Assertions.assertThat(headers.size()).isEqualTo(1);
        assertHeaderEquals(new BasicHeader(":method", "GET"), headers.get(0));
        Assertions.assertThat(dynamicTable.getMaxSize()).isEqualTo(10);
    }

    @Test
    void decoderRejectsDynamicTableSizeUpdateAfterLeadingUpdateAndHeader() {
        final InboundDynamicTable dynamicTable = new InboundDynamicTable(4096);
        final HPackDecoder decoder = new HPackDecoder(dynamicTable, StandardCharsets.US_ASCII);

        final ByteBuffer src = createByteBuffer(
                0x20, // dynamic table size update (0)
                0x82, // :method: GET
                0x20  // dynamic table size update (0) - illegal after first header field
        );

        final HPackException ex = Assertions.assertThatThrownBy(() ->
                        decoder.decodeHeaders(src))
                .isInstanceOf(HPackException.class).asInstanceOf(InstanceOfAssertFactories.type(HPackException.class)).actual();
        Assertions.assertThat(ex.getMessage().contains("beginning of a header block")).isTrue();
    }

    @Test
    void testIntegerDecodingOverflow() {
        // 7-bit prefix integer that mathematically decodes to Integer.MAX_VALUE + 127.
        // Without an overflow guard this wraps to a negative int and leaks into higher-level decoding.
        Assertions.assertThatThrownBy(() ->
                HPackDecoder.decodeInt(createByteBuffer(0x7f, 0xff, 0xff, 0xff, 0xff, 0x07), 7)).isInstanceOf(HPackException.class);
    }


}

