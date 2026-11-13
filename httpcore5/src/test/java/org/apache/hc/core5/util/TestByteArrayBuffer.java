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

package org.apache.hc.core5.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ByteArrayBuffer}.
 *
 */
class TestByteArrayBuffer {

    @Test
    void testConstructor() {
        final ByteArrayBuffer buffer = new ByteArrayBuffer(16);
        Assertions.assertThat(buffer.capacity()).isEqualTo(16);
        Assertions.assertThat(buffer.length()).isEqualTo(0);
        Assertions.assertThat(buffer.array()).isNotNull();
        Assertions.assertThat(buffer.array().length).isEqualTo(16);
        Assertions.assertThatThrownBy(() ->
                        new ByteArrayBuffer(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testSimpleAppend() {
        final ByteArrayBuffer buffer = new ByteArrayBuffer(16);
        Assertions.assertThat(buffer.capacity()).isEqualTo(16);
        Assertions.assertThat(buffer.length()).isEqualTo(0);
        final byte[] b1 = buffer.toByteArray();
        Assertions.assertThat(b1).isNotNull();
        Assertions.assertThat(b1.length).isEqualTo(0);
        Assertions.assertThat(buffer.isEmpty()).isTrue();
        Assertions.assertThat(buffer.isFull()).isFalse();

        final byte[] tmp = new byte[] { 1, 2, 3, 4};
        buffer.append(tmp, 0, tmp.length);
        Assertions.assertThat(buffer.capacity()).isEqualTo(16);
        Assertions.assertThat(buffer.length()).isEqualTo(4);
        Assertions.assertThat(buffer.isEmpty()).isFalse();
        Assertions.assertThat(buffer.isFull()).isFalse();

        final byte[] b2 = buffer.toByteArray();
        Assertions.assertThat(b2).isNotNull();
        Assertions.assertThat(b2.length).isEqualTo(4);
        for (int i = 0; i < tmp.length; i++) {
            Assertions.assertThat(b2[i]).isEqualTo(tmp[i]);
            Assertions.assertThat(buffer.byteAt(i)).isEqualTo(tmp[i]);
        }
        buffer.clear();
        Assertions.assertThat(buffer.capacity()).isEqualTo(16);
        Assertions.assertThat(buffer.length()).isEqualTo(0);
        Assertions.assertThat(buffer.isEmpty()).isTrue();
        Assertions.assertThat(buffer.isFull()).isFalse();
    }

    @Test
    void testExpandAppend() {
        final ByteArrayBuffer buffer = new ByteArrayBuffer(4);
        Assertions.assertThat(buffer.capacity()).isEqualTo(4);

        final byte[] tmp = new byte[] { 1, 2, 3, 4};
        buffer.append(tmp, 0, 2);
        buffer.append(tmp, 0, 4);
        buffer.append(tmp, 0, 0);

        Assertions.assertThat(buffer.capacity()).isEqualTo(8);
        Assertions.assertThat(buffer.length()).isEqualTo(6);

        buffer.append(tmp, 0, 4);

        Assertions.assertThat(buffer.capacity()).isEqualTo(16);
        Assertions.assertThat(buffer.length()).isEqualTo(10);
    }

    @Test
    void testAppendHeapByteBuffer() {
        final ByteArrayBuffer buffer = new ByteArrayBuffer(4);
        Assertions.assertThat(buffer.capacity()).isEqualTo(4);

        final ByteBuffer tmp = ByteBuffer.wrap(new byte[] { 1, 2, 3, 4, 5, 6});
        buffer.append(tmp);

        Assertions.assertThat(tmp.hasRemaining()).withFailMessage("The input buffer should be drained").isFalse();
        Assertions.assertThat(buffer.capacity()).isEqualTo(8);
        Assertions.assertThat(buffer.length()).isEqualTo(6);

        tmp.clear();
        buffer.append(tmp);

        Assertions.assertThat(tmp.hasRemaining()).withFailMessage("The input buffer should be drained").isFalse();
        Assertions.assertThat(buffer.capacity()).isEqualTo(16);
        Assertions.assertThat(buffer.length()).isEqualTo(12);
        Assertions.assertThat(buffer.toByteArray())
                .containsExactly(new byte[]{1, 2, 3, 4, 5, 6, 1, 2, 3, 4, 5, 6});
    }

    @Test
    void testAppendHeapByteBufferWithOffset() {
        final ByteArrayBuffer buffer = new ByteArrayBuffer(4);
        Assertions.assertThat(buffer.capacity()).isEqualTo(4);

        final ByteBuffer tmp = ByteBuffer.wrap(new byte[] { 7, 7, 1, 2, 3, 4, 5, 6, 7, 7}, 2, 6).slice();
        Assertions.assertThat(tmp.arrayOffset() > 0).withFailMessage("Validate this is testing a buffer with an array offset").isTrue();

        buffer.append(tmp);

        Assertions.assertThat(tmp.hasRemaining()).withFailMessage("The input buffer should be drained").isFalse();
        Assertions.assertThat(buffer.capacity()).isEqualTo(8);
        Assertions.assertThat(buffer.length()).isEqualTo(6);

        tmp.clear();
        Assertions.assertThat(tmp.remaining()).isEqualTo(6);
        buffer.append(tmp);

        Assertions.assertThat(tmp.hasRemaining()).withFailMessage("The input buffer should be drained").isFalse();
        Assertions.assertThat(buffer.capacity()).isEqualTo(16);
        Assertions.assertThat(buffer.length()).isEqualTo(12);
        Assertions.assertThat(buffer.toByteArray())
                .containsExactly(new byte[]{1, 2, 3, 4, 5, 6, 1, 2, 3, 4, 5, 6});
    }

    @Test
    void testAppendDirectByteBuffer() {
        final ByteArrayBuffer buffer = new ByteArrayBuffer(4);
        Assertions.assertThat(buffer.capacity()).isEqualTo(4);

        final ByteBuffer tmp = ByteBuffer.allocateDirect(6);
        tmp.put(new byte[] { 1, 2, 3, 4, 5, 6}).flip();
        buffer.append(tmp);

        Assertions.assertThat(tmp.hasRemaining()).withFailMessage("The input buffer should be drained").isFalse();
        Assertions.assertThat(buffer.capacity()).isEqualTo(8);
        Assertions.assertThat(buffer.length()).isEqualTo(6);

        tmp.clear();
        buffer.append(tmp);

        Assertions.assertThat(tmp.hasRemaining()).withFailMessage("The input buffer should be drained").isFalse();
        Assertions.assertThat(buffer.capacity()).isEqualTo(16);
        Assertions.assertThat(buffer.length()).isEqualTo(12);
        Assertions.assertThat(buffer.toByteArray())
                .containsExactly(new byte[]{1, 2, 3, 4, 5, 6, 1, 2, 3, 4, 5, 6});
    }

    @Test
    void testInvalidAppend() {
        final ByteArrayBuffer buffer = new ByteArrayBuffer(4);
        buffer.append((byte[])null, 0, 0);

        final byte[] tmp = new byte[] { 1, 2, 3, 4};
        Assertions.assertThatThrownBy(() ->
                        buffer.append(tmp, -1, 0))
                .isInstanceOf(IndexOutOfBoundsException.class);
        Assertions.assertThatThrownBy(() ->
                        buffer.append(tmp, 0, -1))
                .isInstanceOf(IndexOutOfBoundsException.class);
        Assertions.assertThatThrownBy(() ->
                        buffer.append(tmp, 0, 8))
                .isInstanceOf(IndexOutOfBoundsException.class);
        Assertions.assertThatThrownBy(() ->
                        buffer.append(tmp, 10, Integer.MAX_VALUE))
                .isInstanceOf(IndexOutOfBoundsException.class);
        Assertions.assertThatThrownBy(() ->
                        buffer.append(tmp, 2, 4))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void testAppendOneByte() {
        final ByteArrayBuffer buffer = new ByteArrayBuffer(4);
        Assertions.assertThat(buffer.capacity()).isEqualTo(4);

        final byte[] tmp = new byte[] { 1, 127, -1, -128, 1, -2};
        for (final byte element : tmp) {
            buffer.append(element);
        }
        Assertions.assertThat(buffer.capacity()).isEqualTo(8);
        Assertions.assertThat(buffer.length()).isEqualTo(6);

        for (int i = 0; i < tmp.length; i++) {
            Assertions.assertThat(buffer.byteAt(i)).isEqualTo(tmp[i]);
        }
    }

    @Test
    void testSetLength() {
        final ByteArrayBuffer buffer = new ByteArrayBuffer(4);
        buffer.setLength(2);
        Assertions.assertThat(buffer.length()).isEqualTo(2);
    }

    @Test
    void testSetInvalidLength() {
        final ByteArrayBuffer buffer = new ByteArrayBuffer(4);
        Assertions.assertThatThrownBy(() ->
                        buffer.setLength(-2))
                .isInstanceOf(IndexOutOfBoundsException.class);
        Assertions.assertThatThrownBy(() ->
                        buffer.setLength(200))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void testEnsureCapacity() {
        final ByteArrayBuffer buffer = new ByteArrayBuffer(4);
        buffer.ensureCapacity(2);
        Assertions.assertThat(buffer.capacity()).isEqualTo(4);
        buffer.ensureCapacity(8);
        Assertions.assertThat(buffer.capacity()).isEqualTo(8);
    }

    @Test
    void testIndexOf() {
        final byte COLON = (byte) ':';
        final byte COMMA = (byte) ',';
        final byte[] bytes = "name1: value1; name2: value2".getBytes(StandardCharsets.US_ASCII);
        final int index1 = 5;
        final int index2 = 20;

        final ByteArrayBuffer buffer = new ByteArrayBuffer(16);
        buffer.append(bytes, 0, bytes.length);

        Assertions.assertThat(buffer.indexOf(COLON)).isEqualTo(index1);
        Assertions.assertThat(buffer.indexOf(COMMA)).isEqualTo(-1);
        Assertions.assertThat(buffer.indexOf(COLON, -1, 11)).isEqualTo(index1);
        Assertions.assertThat(buffer.indexOf(COLON, 0, 1000)).isEqualTo(index1);
        Assertions.assertThat(buffer.indexOf(COLON, 2, 1)).isEqualTo(-1);
        Assertions.assertThat(buffer.indexOf(COLON, index1 + 1, buffer.length())).isEqualTo(index2);
    }

    @Test
    void testAppendCharArrayAsAscii() {
        final String s1 = "stuff";
        final String s2 = " and more stuff";
        final char[] b1 = s1.toCharArray();
        final char[] b2 = s2.toCharArray();

        final ByteArrayBuffer buffer = new ByteArrayBuffer(8);
        buffer.append(b1, 0, b1.length);
        buffer.append(b2, 0, b2.length);

        Assertions.assertThat(new String(buffer.toByteArray(), StandardCharsets.US_ASCII)).isEqualTo(s1 + s2);
    }

    @Test
    void testAppendNullCharArray() {
        final ByteArrayBuffer buffer = new ByteArrayBuffer(8);
        buffer.append((char[])null, 0, 0);
        Assertions.assertThat(buffer.length()).isEqualTo(0);
    }

    @Test
    void testAppendEmptyCharArray() {
        final ByteArrayBuffer buffer = new ByteArrayBuffer(8);
        buffer.append(new char[] {}, 0, 0);
        Assertions.assertThat(buffer.length()).isEqualTo(0);
    }

    @Test
    void testAppendNullCharArrayBuffer() {
        final ByteArrayBuffer buffer = new ByteArrayBuffer(8);
        buffer.append((CharArrayBuffer)null, 0, 0);
        Assertions.assertThat(buffer.length()).isEqualTo(0);
    }

    @Test
    void testAppendNullByteBuffer() {
        final ByteArrayBuffer buffer = new ByteArrayBuffer(8);
        final ByteBuffer nullBuffer = null;
        buffer.append(nullBuffer);
        Assertions.assertThat(buffer.length()).isEqualTo(0);
    }

    @Test
    void testInvalidAppendCharArrayAsAscii() {
        final ByteArrayBuffer buffer = new ByteArrayBuffer(4);
        buffer.append((char[])null, 0, 0);

        final char[] tmp = new char[] { '1', '2', '3', '4'};
        Assertions.assertThatThrownBy(() ->
                        buffer.append(tmp, -1, 0))
                .isInstanceOf(IndexOutOfBoundsException.class);
        Assertions.assertThatThrownBy(() ->
                        buffer.append(tmp, 0, -1))
                .isInstanceOf(IndexOutOfBoundsException.class);
        Assertions.assertThatThrownBy(() ->
                        buffer.append(tmp, 0, 8))
                .isInstanceOf(IndexOutOfBoundsException.class);
        Assertions.assertThatThrownBy(() ->
                        buffer.append(tmp, 10, Integer.MAX_VALUE))
                .isInstanceOf(IndexOutOfBoundsException.class);
        Assertions.assertThatThrownBy(() ->
                        buffer.append(tmp, 2, 4))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void testSerialization() throws Exception {
        final ByteArrayBuffer orig = new ByteArrayBuffer(32);
        orig.append(1);
        orig.append(2);
        orig.append(3);
        final ByteArrayOutputStream outbuffer = new ByteArrayOutputStream();
        try (final ObjectOutputStream outStream = new ObjectOutputStream(outbuffer)) {
            outStream.writeObject(orig);
        }
        final byte[] raw = outbuffer.toByteArray();
        final ByteArrayInputStream inBuffer = new ByteArrayInputStream(raw);
        final ObjectInputStream inStream = new ObjectInputStream(inBuffer);
        final ByteArrayBuffer clone = (ByteArrayBuffer) inStream.readObject();
        Assertions.assertThat(clone.capacity()).isEqualTo(orig.capacity());
        Assertions.assertThat(clone.length()).isEqualTo(orig.length());
        final byte[] data = clone.toByteArray();
        Assertions.assertThat(data).isNotNull();
        Assertions.assertThat(data.length).isEqualTo(3);
        Assertions.assertThat(data[0]).isEqualTo((byte) 1);
        Assertions.assertThat(data[1]).isEqualTo((byte) 2);
        Assertions.assertThat(data[2]).isEqualTo((byte) 3);
    }

    @Test
    void testControlCharFiltering() {
        final char[] chars = new char[256];
        for (char i = 0; i < 256; i++) {
            chars[i] = i;
        }

        final byte[] bytes = asByteArray(chars);

        Assertions.assertThat(new String(bytes, StandardCharsets.ISO_8859_1)).isEqualTo("?????????\t??????????????????????"
                + " !\"#$%&'()*+,-./0123456789:;<=>?"
                + "@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_"
                + "`abcdefghijklmnopqrstuvwxyz"
                + "{|}~???????????????????????"
                + "??????????\u00A0¡¢£¤¥¦§¨©ª«¬\u00AD®¯"
                + "°±²³´µ¶·¸¹º»¼½¾¿ÀÁÂÃÄÅÆÇÈÉÊËÌÍÎÏ"
                + "ÐÑÒÓÔÕÖ×ØÙÚÛÜÝÞßàáâãäåæçèéêëìíîï"
                + "ðñòóôõö÷øùúûüýþÿ");
    }

    @Test
    void testUnicodeFiltering() {
        // Various languages
        Assertions.assertThat(new String(asByteArray("буквы".toCharArray()), StandardCharsets.ISO_8859_1)).isEqualTo("?????");
        Assertions.assertThat(new String(asByteArray("四字熟語".toCharArray()), StandardCharsets.ISO_8859_1)).isEqualTo("????");

        // Unicode snowman
        Assertions.assertThat(new String(asByteArray("☃".toCharArray()), StandardCharsets.ISO_8859_1)).isEqualTo("?");

        // Emoji (surrogate pair)
        Assertions.assertThat(new String(asByteArray("\uD83D\uDE00".toCharArray()), StandardCharsets.ISO_8859_1)).isEqualTo("??");
    }

    private static byte[] asByteArray(final char[] chars) {
        final ByteArrayBuffer byteArrayBuffer = new ByteArrayBuffer(chars.length);
        byteArrayBuffer.append(chars, 0, chars.length);
        return byteArrayBuffer.toByteArray();
    }
}
