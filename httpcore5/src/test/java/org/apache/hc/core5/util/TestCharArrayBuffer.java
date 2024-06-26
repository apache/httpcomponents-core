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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CharArrayBuffer}.
 *
 */
class TestCharArrayBuffer {

    @Test
    void testConstructor() {
        final CharArrayBuffer buffer = new CharArrayBuffer(16);
        Assertions.assertEquals(16, buffer.capacity());
        Assertions.assertEquals(0, buffer.length());
        Assertions.assertNotNull(buffer.array());
        Assertions.assertEquals(16, buffer.array().length);
        Assertions.assertThrows(IllegalArgumentException.class, () -> new CharArrayBuffer(-1));
    }

    @Test
    void testSimpleAppend() {
        final CharArrayBuffer buffer = new CharArrayBuffer(16);
        Assertions.assertEquals(16, buffer.capacity());
        Assertions.assertEquals(0, buffer.length());
        final char[] b1 = buffer.toCharArray();
        Assertions.assertNotNull(b1);
        Assertions.assertEquals(0, b1.length);
        Assertions.assertTrue(buffer.isEmpty());
        Assertions.assertFalse(buffer.isFull());

        final char[] tmp = new char[] { '1', '2', '3', '4'};
        buffer.append(tmp, 0, tmp.length);
        Assertions.assertEquals(16, buffer.capacity());
        Assertions.assertEquals(4, buffer.length());
        Assertions.assertFalse(buffer.isEmpty());
        Assertions.assertFalse(buffer.isFull());

        final char[] b2 = buffer.toCharArray();
        Assertions.assertNotNull(b2);
        Assertions.assertEquals(4, b2.length);
        for (int i = 0; i < tmp.length; i++) {
            Assertions.assertEquals(tmp[i], b2[i]);
            Assertions.assertEquals(tmp[i], buffer.charAt(i));
        }
        Assertions.assertEquals("1234", buffer.toString());

        buffer.clear();
        Assertions.assertEquals(16, buffer.capacity());
        Assertions.assertEquals(0, buffer.length());
        Assertions.assertTrue(buffer.isEmpty());
        Assertions.assertFalse(buffer.isFull());
    }

    @Test
    void testExpandAppend() {
        final CharArrayBuffer buffer = new CharArrayBuffer(4);
        Assertions.assertEquals(4, buffer.capacity());

        final char[] tmp = new char[] { '1', '2', '3', '4'};
        buffer.append(tmp, 0, 2);
        buffer.append(tmp, 0, 4);
        buffer.append(tmp, 0, 0);

        Assertions.assertEquals(8, buffer.capacity());
        Assertions.assertEquals(6, buffer.length());

        buffer.append(tmp, 0, 4);

        Assertions.assertEquals(16, buffer.capacity());
        Assertions.assertEquals(10, buffer.length());

        Assertions.assertEquals("1212341234", buffer.toString());
    }

    @Test
    void testAppendString() {
        final CharArrayBuffer buffer = new CharArrayBuffer(8);
        buffer.append("stuff");
        buffer.append(" and more stuff");
        Assertions.assertEquals("stuff and more stuff", buffer.toString());
    }

    @Test
    void testAppendNullString() {
        final CharArrayBuffer buffer = new CharArrayBuffer(8);
        buffer.append((String)null);
        Assertions.assertEquals("null", buffer.toString());
    }

    @Test
    void testAppendCharArrayBuffer() {
        final CharArrayBuffer buffer1 = new CharArrayBuffer(8);
        buffer1.append(" and more stuff");
        final CharArrayBuffer buffer2 = new CharArrayBuffer(8);
        buffer2.append("stuff");
        buffer2.append(buffer1);
        Assertions.assertEquals("stuff and more stuff", buffer2.toString());
    }

    @Test
    void testAppendNullCharArrayBuffer() {
        final CharArrayBuffer buffer = new CharArrayBuffer(8);
        buffer.append((CharArrayBuffer)null);
        buffer.append((CharArrayBuffer)null, 0, 0);
        Assertions.assertEquals("", buffer.toString());
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
        Assertions.assertEquals("123456", buffer.toString());
    }

    @Test
    void testInvalidCharArrayAppend() {
        final CharArrayBuffer buffer = new CharArrayBuffer(4);
        buffer.append((char[])null, 0, 0);

        final char[] tmp = new char[] { '1', '2', '3', '4'};
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> buffer.append(tmp, -1, 0));
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> buffer.append(tmp, 0, -1));
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> buffer.append(tmp, 0, 8));
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> buffer.append(tmp, 10, Integer.MAX_VALUE));
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> buffer.append(tmp, 2, 4));
    }

    @Test
    void testSetLength() {
        final CharArrayBuffer buffer = new CharArrayBuffer(4);
        buffer.setLength(2);
        Assertions.assertEquals(2, buffer.length());
    }

    @Test
    void testSetInvalidLength() {
        final CharArrayBuffer buffer = new CharArrayBuffer(4);
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> buffer.setLength(-2));
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> buffer.setLength(200));
    }

    @Test
    void testEnsureCapacity() {
        final CharArrayBuffer buffer = new CharArrayBuffer(4);
        buffer.ensureCapacity(2);
        Assertions.assertEquals(4, buffer.capacity());
        buffer.ensureCapacity(8);
        Assertions.assertEquals(8, buffer.capacity());
    }

    @Test
    void testIndexOf() {
        final CharArrayBuffer buffer = new CharArrayBuffer(16);
        buffer.append("name: value");
        Assertions.assertEquals(4, buffer.indexOf(':'));
        Assertions.assertEquals(-1, buffer.indexOf(','));
        Assertions.assertEquals(4, buffer.indexOf(':', -1, 11));
        Assertions.assertEquals(4, buffer.indexOf(':', 0, 1000));
        Assertions.assertEquals(-1, buffer.indexOf(':', 2, 1));
    }

    @Test
    void testSubstring() {
        final CharArrayBuffer buffer = new CharArrayBuffer(16);
        buffer.append(" name:  value    ");
        Assertions.assertEquals(5, buffer.indexOf(':'));
        Assertions.assertEquals(" name", buffer.substring(0, 5));
        Assertions.assertEquals("  value    ", buffer.substring(6, buffer.length()));
        Assertions.assertEquals("name", buffer.substringTrimmed(0, 5));
        Assertions.assertEquals("value", buffer.substringTrimmed(6, buffer.length()));
        Assertions.assertEquals("", buffer.substringTrimmed(13, buffer.length()));
    }

    @Test
    void testSubstringIndexOfOutBound() {
        final CharArrayBuffer buffer = new CharArrayBuffer(16);
        buffer.append("stuff");
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> buffer.substring(-2, 10));
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> buffer.substringTrimmed(-2, 10));
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> buffer.substring(12, 10));
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> buffer.substringTrimmed(12, 10));
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> buffer.substring(2, 1));
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> buffer.substringTrimmed(2, 1));
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

        Assertions.assertEquals("stuff and more stuff", buffer.toString());
    }

    @Test
    void testAppendISOByteArray() {
        final byte[] b = new byte[] {0x00, 0x20, 0x7F, -0x80, -0x01};

        final CharArrayBuffer buffer = new CharArrayBuffer(8);
        buffer.append(b, 0, b.length);
        final char[] ch = buffer.toCharArray();
        Assertions.assertNotNull(ch);
        Assertions.assertEquals(5, ch.length);
        Assertions.assertEquals(0x00, ch[0]);
        Assertions.assertEquals(0x20, ch[1]);
        Assertions.assertEquals(0x7F, ch[2]);
        Assertions.assertEquals(0x80, ch[3]);
        Assertions.assertEquals(0xFF, ch[4]);
    }

    @Test
    void testAppendNullByteArray() {
        final CharArrayBuffer buffer = new CharArrayBuffer(8);
        buffer.append((byte[])null, 0, 0);
        Assertions.assertEquals("", buffer.toString());
    }

    @Test
    void testAppendNullByteArrayBuffer() {
        final CharArrayBuffer buffer = new CharArrayBuffer(8);
        buffer.append((ByteArrayBuffer)null, 0, 0);
        Assertions.assertEquals("", buffer.toString());
    }

    @Test
    void testInvalidAppendAsciiByteArray() {
        final CharArrayBuffer buffer = new CharArrayBuffer(4);
        buffer.append((byte[])null, 0, 0);

        final byte[] tmp = new byte[] { '1', '2', '3', '4'};
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> buffer.append(tmp, -1, 0));
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> buffer.append(tmp, 0, -1));
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> buffer.append(tmp, 0, 8));
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> buffer.append(tmp, 10, Integer.MAX_VALUE));
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> buffer.append(tmp, 2, 4));
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
        Assertions.assertEquals(orig.capacity(), clone.capacity());
        Assertions.assertEquals(orig.length(), clone.length());
        final char[] data = clone.toCharArray();
        Assertions.assertNotNull(data);
        Assertions.assertEquals(3, data.length);
        Assertions.assertEquals('a', data[0]);
        Assertions.assertEquals('b', data[1]);
        Assertions.assertEquals('c', data[2]);
    }

    @Test
    void testSubSequence() {
        final CharArrayBuffer buffer = new CharArrayBuffer(16);
        buffer.append(" name:  value    ");
        Assertions.assertEquals(5, buffer.indexOf(':'));
        Assertions.assertEquals(" name", buffer.subSequence(0, 5).toString());
        Assertions.assertEquals("  value    ",
            buffer.subSequence(6, buffer.length()).toString());
    }

    @Test
    void testSubSequenceIndexOfOutBound() {
        final CharArrayBuffer buffer = new CharArrayBuffer(16);
        buffer.append("stuff");
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> buffer.subSequence(-2, 10));
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> buffer.subSequence(12, 10));
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> buffer.subSequence(2, 1));
    }

}
