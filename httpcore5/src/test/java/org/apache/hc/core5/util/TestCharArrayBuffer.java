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
import java.nio.charset.StandardCharsets;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CharArrayBuffer}.
 *
 */
class TestCharArrayBuffer {

    @Test
    void testConstructor() {
        final CharArrayBuffer buffer = new CharArrayBuffer(16);
        Assertions.assertThat(buffer.capacity()).isEqualTo(16);
        Assertions.assertThat(buffer.length()).isEqualTo(0);
        Assertions.assertThat(buffer.array()).isNotNull();
        Assertions.assertThat(buffer.array().length).isEqualTo(16);
        Assertions.assertThatThrownBy(() ->
                        new CharArrayBuffer(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testSimpleAppend() {
        final CharArrayBuffer buffer = new CharArrayBuffer(16);
        Assertions.assertThat(buffer.capacity()).isEqualTo(16);
        Assertions.assertThat(buffer.length()).isEqualTo(0);
        final char[] b1 = buffer.toCharArray();
        Assertions.assertThat(b1).isNotNull();
        Assertions.assertThat(b1.length).isEqualTo(0);
        Assertions.assertThat(buffer.isEmpty()).isTrue();
        Assertions.assertThat(buffer.isFull()).isFalse();

        final char[] tmp = new char[] { '1', '2', '3', '4'};
        buffer.append(tmp, 0, tmp.length);
        Assertions.assertThat(buffer.capacity()).isEqualTo(16);
        Assertions.assertThat(buffer.length()).isEqualTo(4);
        Assertions.assertThat(buffer.isEmpty()).isFalse();
        Assertions.assertThat(buffer.isFull()).isFalse();

        final char[] b2 = buffer.toCharArray();
        Assertions.assertThat(b2).isNotNull();
        Assertions.assertThat(b2.length).isEqualTo(4);
        for (int i = 0; i < tmp.length; i++) {
            Assertions.assertThat(b2[i]).isEqualTo(tmp[i]);
            Assertions.assertThat(buffer.charAt(i)).isEqualTo(tmp[i]);
        }
        Assertions.assertThat(buffer.toString()).isEqualTo("1234");

        buffer.clear();
        Assertions.assertThat(buffer.capacity()).isEqualTo(16);
        Assertions.assertThat(buffer.length()).isEqualTo(0);
        Assertions.assertThat(buffer.isEmpty()).isTrue();
        Assertions.assertThat(buffer.isFull()).isFalse();
    }

    @Test
    void testExpandAppend() {
        final CharArrayBuffer buffer = new CharArrayBuffer(4);
        Assertions.assertThat(buffer.capacity()).isEqualTo(4);

        final char[] tmp = new char[] { '1', '2', '3', '4'};
        buffer.append(tmp, 0, 2);
        buffer.append(tmp, 0, 4);
        buffer.append(tmp, 0, 0);

        Assertions.assertThat(buffer.capacity()).isEqualTo(8);
        Assertions.assertThat(buffer.length()).isEqualTo(6);

        buffer.append(tmp, 0, 4);

        Assertions.assertThat(buffer.capacity()).isEqualTo(16);
        Assertions.assertThat(buffer.length()).isEqualTo(10);

        Assertions.assertThat(buffer.toString()).isEqualTo("1212341234");
    }

    @Test
    void testAppendString() {
        final CharArrayBuffer buffer = new CharArrayBuffer(8);
        buffer.append("stuff");
        buffer.append(" and more stuff");
        Assertions.assertThat(buffer.toString()).isEqualTo("stuff and more stuff");
    }

    @Test
    void testAppendNullString() {
        final CharArrayBuffer buffer = new CharArrayBuffer(8);
        buffer.append((String)null);
        Assertions.assertThat(buffer.toString()).isEqualTo("null");
    }

    @Test
    void testAppendCharArrayBuffer() {
        final CharArrayBuffer buffer1 = new CharArrayBuffer(8);
        buffer1.append(" and more stuff");
        final CharArrayBuffer buffer2 = new CharArrayBuffer(8);
        buffer2.append("stuff");
        buffer2.append(buffer1);
        Assertions.assertThat(buffer2.toString()).isEqualTo("stuff and more stuff");
    }

    @Test
    void testAppendNullCharArrayBuffer() {
        final CharArrayBuffer buffer = new CharArrayBuffer(8);
        buffer.append((CharArrayBuffer)null);
        buffer.append((CharArrayBuffer)null, 0, 0);
        Assertions.assertThat(buffer.toString()).isEqualTo("");
    }

    @Test
    void testAppendSingleChar() {
        final CharArrayBuffer buffer = new CharArrayBuffer(4);
        buffer.append('1');
        buffer.append('2');
        buffer.append('3');
        buffer.append('4');
        buffer.append('5');
        buffer.append('6');
        Assertions.assertThat(buffer.toString()).isEqualTo("123456");
    }

    @Test
    void testInvalidCharArrayAppend() {
        final CharArrayBuffer buffer = new CharArrayBuffer(4);
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
    void testSetLength() {
        final CharArrayBuffer buffer = new CharArrayBuffer(4);
        buffer.setLength(2);
        Assertions.assertThat(buffer.length()).isEqualTo(2);
    }

    @Test
    void testSetInvalidLength() {
        final CharArrayBuffer buffer = new CharArrayBuffer(4);
        Assertions.assertThatThrownBy(() ->
                        buffer.setLength(-2))
                .isInstanceOf(IndexOutOfBoundsException.class);
        Assertions.assertThatThrownBy(() ->
                        buffer.setLength(200))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void testEnsureCapacity() {
        final CharArrayBuffer buffer = new CharArrayBuffer(4);
        buffer.ensureCapacity(2);
        Assertions.assertThat(buffer.capacity()).isEqualTo(4);
        buffer.ensureCapacity(8);
        Assertions.assertThat(buffer.capacity()).isEqualTo(8);
    }

    @Test
    void testIndexOf() {
        final CharArrayBuffer buffer = new CharArrayBuffer(16);
        buffer.append("name: value");
        Assertions.assertThat(buffer.indexOf(':')).isEqualTo(4);
        Assertions.assertThat(buffer.indexOf(',')).isEqualTo(-1);
        Assertions.assertThat(buffer.indexOf(':', -1, 11)).isEqualTo(4);
        Assertions.assertThat(buffer.indexOf(':', 0, 1000)).isEqualTo(4);
        Assertions.assertThat(buffer.indexOf(':', 2, 1)).isEqualTo(-1);
    }

    @Test
    void testSubstring() {
        final CharArrayBuffer buffer = new CharArrayBuffer(16);
        buffer.append(" name:  value    ");
        Assertions.assertThat(buffer.indexOf(':')).isEqualTo(5);
        Assertions.assertThat(buffer.substring(0, 5)).isEqualTo(" name");
        Assertions.assertThat(buffer.substring(6, buffer.length())).isEqualTo("  value    ");
        Assertions.assertThat(buffer.substringTrimmed(0, 5)).isEqualTo("name");
        Assertions.assertThat(buffer.substringTrimmed(6, buffer.length())).isEqualTo("value");
        Assertions.assertThat(buffer.substringTrimmed(13, buffer.length())).isEqualTo("");
    }

    @Test
    void testSubstringIndexOfOutBound() {
        final CharArrayBuffer buffer = new CharArrayBuffer(16);
        buffer.append("stuff");
        Assertions.assertThatThrownBy(() ->
                        buffer.substring(-2, 10))
                .isInstanceOf(IndexOutOfBoundsException.class);
        Assertions.assertThatThrownBy(() ->
                        buffer.substringTrimmed(-2, 10))
                .isInstanceOf(IndexOutOfBoundsException.class);
        Assertions.assertThatThrownBy(() ->
                        buffer.substring(12, 10))
                .isInstanceOf(IndexOutOfBoundsException.class);
        Assertions.assertThatThrownBy(() ->
                        buffer.substringTrimmed(12, 10))
                .isInstanceOf(IndexOutOfBoundsException.class);
        Assertions.assertThatThrownBy(() ->
                        buffer.substring(2, 1))
                .isInstanceOf(IndexOutOfBoundsException.class);
        Assertions.assertThatThrownBy(() ->
                        buffer.substringTrimmed(2, 1))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void testAppendAsciiByteArray() {
        final String s1 = "stuff";
        final String s2 = " and more stuff";
        final byte[] b1 = s1.getBytes(StandardCharsets.US_ASCII);
        final byte[] b2 = s2.getBytes(StandardCharsets.US_ASCII);

        final CharArrayBuffer buffer = new CharArrayBuffer(8);
        buffer.append(b1, 0, b1.length);
        buffer.append(b2, 0, b2.length);

        Assertions.assertThat(buffer.toString()).isEqualTo("stuff and more stuff");
    }

    @Test
    void testAppendISOByteArray() {
        final byte[] b = new byte[] {0x00, 0x20, 0x7F, -0x80, -0x01};

        final CharArrayBuffer buffer = new CharArrayBuffer(8);
        buffer.append(b, 0, b.length);
        final char[] ch = buffer.toCharArray();
        Assertions.assertThat(ch).isNotNull();
        Assertions.assertThat(ch.length).isEqualTo(5);
        Assertions.assertThat(ch[0]).isEqualTo((char) 0x00);
        Assertions.assertThat(ch[1]).isEqualTo((char) 0x20);
        Assertions.assertThat(ch[2]).isEqualTo((char) 0x7F);
        Assertions.assertThat(ch[3]).isEqualTo((char) 0x80);
        Assertions.assertThat(ch[4]).isEqualTo((char) 0xFF);
    }

    @Test
    void testAppendNullByteArray() {
        final CharArrayBuffer buffer = new CharArrayBuffer(8);
        buffer.append((byte[])null, 0, 0);
        Assertions.assertThat(buffer.toString()).isEqualTo("");
    }

    @Test
    void testAppendNullByteArrayBuffer() {
        final CharArrayBuffer buffer = new CharArrayBuffer(8);
        buffer.append((ByteArrayBuffer)null, 0, 0);
        Assertions.assertThat(buffer.toString()).isEqualTo("");
    }

    @Test
    void testInvalidAppendAsciiByteArray() {
        final CharArrayBuffer buffer = new CharArrayBuffer(4);
        buffer.append((byte[])null, 0, 0);

        final byte[] tmp = new byte[] { '1', '2', '3', '4'};
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
        final CharArrayBuffer orig = new CharArrayBuffer(32);
        orig.append('a');
        orig.append('b');
        orig.append('c');
        final ByteArrayOutputStream outbuffer = new ByteArrayOutputStream();
        try (final ObjectOutputStream outStream = new ObjectOutputStream(outbuffer)) {
            outStream.writeObject(orig);
        }
        final byte[] raw = outbuffer.toByteArray();
        final ByteArrayInputStream inBuffer = new ByteArrayInputStream(raw);
        final ObjectInputStream inStream = new ObjectInputStream(inBuffer);
        final CharArrayBuffer clone = (CharArrayBuffer) inStream.readObject();
        Assertions.assertThat(clone.capacity()).isEqualTo(orig.capacity());
        Assertions.assertThat(clone.length()).isEqualTo(orig.length());
        final char[] data = clone.toCharArray();
        Assertions.assertThat(data).isNotNull();
        Assertions.assertThat(data.length).isEqualTo(3);
        Assertions.assertThat(data[0]).isEqualTo('a');
        Assertions.assertThat(data[1]).isEqualTo('b');
        Assertions.assertThat(data[2]).isEqualTo('c');
    }

    @Test
    void testSubSequence() {
        final CharArrayBuffer buffer = new CharArrayBuffer(16);
        buffer.append(" name:  value    ");
        Assertions.assertThat(buffer.indexOf(':')).isEqualTo(5);
        Assertions.assertThat(buffer.subSequence(0, 5).toString()).isEqualTo(" name");
        Assertions.assertThat(buffer.subSequence(6, buffer.length()).toString()).isEqualTo("  value    ");
    }

    @Test
    void testSubSequenceIndexOfOutBound() {
        final CharArrayBuffer buffer = new CharArrayBuffer(16);
        buffer.append("stuff");
        Assertions.assertThatThrownBy(() ->
                        buffer.subSequence(-2, 10))
                .isInstanceOf(IndexOutOfBoundsException.class);
        Assertions.assertThatThrownBy(() ->
                        buffer.subSequence(12, 10))
                .isInstanceOf(IndexOutOfBoundsException.class);
        Assertions.assertThatThrownBy(() ->
                        buffer.subSequence(2, 1))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }

}
