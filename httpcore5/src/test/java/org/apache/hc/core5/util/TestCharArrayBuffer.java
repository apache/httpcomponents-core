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

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link CharArrayBuffer}.
 *
 */
public class TestCharArrayBuffer {

    @Test
    public void testConstructor() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(16);
        Assert.assertEquals(16, buffer.capacity());
        Assert.assertEquals(0, buffer.length());
        Assert.assertNotNull(buffer.array());
        Assert.assertEquals(16, buffer.array().length);
        Assert.assertThrows(IllegalArgumentException.class, () -> new CharArrayBuffer(-1));
    }

    @Test
    public void testSimpleAppend() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(16);
        Assert.assertEquals(16, buffer.capacity());
        Assert.assertEquals(0, buffer.length());
        final char[] b1 = buffer.toCharArray();
        Assert.assertNotNull(b1);
        Assert.assertEquals(0, b1.length);
        Assert.assertTrue(buffer.isEmpty());
        Assert.assertFalse(buffer.isFull());

        final char[] tmp = new char[] { '1', '2', '3', '4'};
        buffer.append(tmp, 0, tmp.length);
        Assert.assertEquals(16, buffer.capacity());
        Assert.assertEquals(4, buffer.length());
        Assert.assertFalse(buffer.isEmpty());
        Assert.assertFalse(buffer.isFull());

        final char[] b2 = buffer.toCharArray();
        Assert.assertNotNull(b2);
        Assert.assertEquals(4, b2.length);
        for (int i = 0; i < tmp.length; i++) {
            Assert.assertEquals(tmp[i], b2[i]);
            Assert.assertEquals(tmp[i], buffer.charAt(i));
        }
        Assert.assertEquals("1234", buffer.toString());

        buffer.clear();
        Assert.assertEquals(16, buffer.capacity());
        Assert.assertEquals(0, buffer.length());
        Assert.assertTrue(buffer.isEmpty());
        Assert.assertFalse(buffer.isFull());
    }

    @Test
    public void testExpandAppend() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(4);
        Assert.assertEquals(4, buffer.capacity());

        final char[] tmp = new char[] { '1', '2', '3', '4'};
        buffer.append(tmp, 0, 2);
        buffer.append(tmp, 0, 4);
        buffer.append(tmp, 0, 0);

        Assert.assertEquals(8, buffer.capacity());
        Assert.assertEquals(6, buffer.length());

        buffer.append(tmp, 0, 4);

        Assert.assertEquals(16, buffer.capacity());
        Assert.assertEquals(10, buffer.length());

        Assert.assertEquals("1212341234", buffer.toString());
    }

    @Test
    public void testAppendString() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(8);
        buffer.append("stuff");
        buffer.append(" and more stuff");
        Assert.assertEquals("stuff and more stuff", buffer.toString());
    }

    @Test
    public void testAppendNullString() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(8);
        buffer.append((String)null);
        Assert.assertEquals("null", buffer.toString());
    }

    @Test
    public void testAppendCharArrayBuffer() throws Exception {
        final CharArrayBuffer buffer1 = new CharArrayBuffer(8);
        buffer1.append(" and more stuff");
        final CharArrayBuffer buffer2 = new CharArrayBuffer(8);
        buffer2.append("stuff");
        buffer2.append(buffer1);
        Assert.assertEquals("stuff and more stuff", buffer2.toString());
    }

    @Test
    public void testAppendNullCharArrayBuffer() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(8);
        buffer.append((CharArrayBuffer)null);
        buffer.append((CharArrayBuffer)null, 0, 0);
        Assert.assertEquals("", buffer.toString());
    }

    @Test
    public void testAppendSingleChar() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(4);
        buffer.append('1');
        buffer.append('2');
        buffer.append('3');
        buffer.append('4');
        buffer.append('5');
        buffer.append('6');
        Assert.assertEquals("123456", buffer.toString());
    }

    @Test
    public void testInvalidCharArrayAppend() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(4);
        buffer.append((char[])null, 0, 0);

        final char[] tmp = new char[] { '1', '2', '3', '4'};
        Assert.assertThrows(IndexOutOfBoundsException.class, () -> buffer.append(tmp, -1, 0));
        Assert.assertThrows(IndexOutOfBoundsException.class, () -> buffer.append(tmp, 0, -1));
        Assert.assertThrows(IndexOutOfBoundsException.class, () -> buffer.append(tmp, 0, 8));
        Assert.assertThrows(IndexOutOfBoundsException.class, () -> buffer.append(tmp, 10, Integer.MAX_VALUE));
        Assert.assertThrows(IndexOutOfBoundsException.class, () -> buffer.append(tmp, 2, 4));
    }

    @Test
    public void testSetLength() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(4);
        buffer.setLength(2);
        Assert.assertEquals(2, buffer.length());
    }

    @Test
    public void testSetInvalidLength() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(4);
        Assert.assertThrows(IndexOutOfBoundsException.class, () -> buffer.setLength(-2));
        Assert.assertThrows(IndexOutOfBoundsException.class, () -> buffer.setLength(200));
    }

    @Test
    public void testEnsureCapacity() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(4);
        buffer.ensureCapacity(2);
        Assert.assertEquals(4, buffer.capacity());
        buffer.ensureCapacity(8);
        Assert.assertEquals(8, buffer.capacity());
    }

    @Test
    public void testIndexOf() {
        final CharArrayBuffer buffer = new CharArrayBuffer(16);
        buffer.append("name: value");
        Assert.assertEquals(4, buffer.indexOf(':'));
        Assert.assertEquals(-1, buffer.indexOf(','));
        Assert.assertEquals(4, buffer.indexOf(':', -1, 11));
        Assert.assertEquals(4, buffer.indexOf(':', 0, 1000));
        Assert.assertEquals(-1, buffer.indexOf(':', 2, 1));
    }

    @Test
    public void testSubstring() {
        final CharArrayBuffer buffer = new CharArrayBuffer(16);
        buffer.append(" name:  value    ");
        Assert.assertEquals(5, buffer.indexOf(':'));
        Assert.assertEquals(" name", buffer.substring(0, 5));
        Assert.assertEquals("  value    ", buffer.substring(6, buffer.length()));
        Assert.assertEquals("name", buffer.substringTrimmed(0, 5));
        Assert.assertEquals("value", buffer.substringTrimmed(6, buffer.length()));
        Assert.assertEquals("", buffer.substringTrimmed(13, buffer.length()));
    }

    @Test
    public void testSubstringIndexOfOutBound() {
        final CharArrayBuffer buffer = new CharArrayBuffer(16);
        buffer.append("stuff");
        Assert.assertThrows(IndexOutOfBoundsException.class, () -> buffer.substring(-2, 10));
        Assert.assertThrows(IndexOutOfBoundsException.class, () -> buffer.substringTrimmed(-2, 10));
        Assert.assertThrows(IndexOutOfBoundsException.class, () -> buffer.substring(12, 10));
        Assert.assertThrows(IndexOutOfBoundsException.class, () -> buffer.substringTrimmed(12, 10));
        Assert.assertThrows(IndexOutOfBoundsException.class, () -> buffer.substring(2, 1));
        Assert.assertThrows(IndexOutOfBoundsException.class, () -> buffer.substringTrimmed(2, 1));
    }

    @Test
    public void testAppendAsciiByteArray() throws Exception {
        final String s1 = "stuff";
        final String s2 = " and more stuff";
        final byte[] b1 = s1.getBytes(StandardCharsets.US_ASCII);
        final byte[] b2 = s2.getBytes(StandardCharsets.US_ASCII);

        final CharArrayBuffer buffer = new CharArrayBuffer(8);
        buffer.append(b1, 0, b1.length);
        buffer.append(b2, 0, b2.length);

        Assert.assertEquals("stuff and more stuff", buffer.toString());
    }

    @Test
    public void testAppendISOByteArray() throws Exception {
        final byte[] b = new byte[] {0x00, 0x20, 0x7F, -0x80, -0x01};

        final CharArrayBuffer buffer = new CharArrayBuffer(8);
        buffer.append(b, 0, b.length);
        final char[] ch = buffer.toCharArray();
        Assert.assertNotNull(ch);
        Assert.assertEquals(5, ch.length);
        Assert.assertEquals(0x00, ch[0]);
        Assert.assertEquals(0x20, ch[1]);
        Assert.assertEquals(0x7F, ch[2]);
        Assert.assertEquals(0x80, ch[3]);
        Assert.assertEquals(0xFF, ch[4]);
    }

    @Test
    public void testAppendNullByteArray() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(8);
        buffer.append((byte[])null, 0, 0);
        Assert.assertEquals("", buffer.toString());
    }

    @Test
    public void testAppendNullByteArrayBuffer() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(8);
        buffer.append((ByteArrayBuffer)null, 0, 0);
        Assert.assertEquals("", buffer.toString());
    }

    @Test
    public void testInvalidAppendAsciiByteArray() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(4);
        buffer.append((byte[])null, 0, 0);

        final byte[] tmp = new byte[] { '1', '2', '3', '4'};
        Assert.assertThrows(IndexOutOfBoundsException.class, () -> buffer.append(tmp, -1, 0));
        Assert.assertThrows(IndexOutOfBoundsException.class, () -> buffer.append(tmp, 0, -1));
        Assert.assertThrows(IndexOutOfBoundsException.class, () -> buffer.append(tmp, 0, 8));
        Assert.assertThrows(IndexOutOfBoundsException.class, () -> buffer.append(tmp, 10, Integer.MAX_VALUE));
        Assert.assertThrows(IndexOutOfBoundsException.class, () -> buffer.append(tmp, 2, 4));
    }

    @Test
    public void testSerialization() throws Exception {
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
        Assert.assertEquals(orig.capacity(), clone.capacity());
        Assert.assertEquals(orig.length(), clone.length());
        final char[] data = clone.toCharArray();
        Assert.assertNotNull(data);
        Assert.assertEquals(3, data.length);
        Assert.assertEquals('a', data[0]);
        Assert.assertEquals('b', data[1]);
        Assert.assertEquals('c', data[2]);
    }

    @Test
    public void testSubSequence() {
        final CharArrayBuffer buffer = new CharArrayBuffer(16);
        buffer.append(" name:  value    ");
        Assert.assertEquals(5, buffer.indexOf(':'));
        Assert.assertEquals(" name", buffer.subSequence(0, 5).toString());
        Assert.assertEquals("  value    ",
            buffer.subSequence(6, buffer.length()).toString());
    }

    @Test
    public void testSubSequenceIndexOfOutBound() {
        final CharArrayBuffer buffer = new CharArrayBuffer(16);
        buffer.append("stuff");
        Assert.assertThrows(IndexOutOfBoundsException.class, () -> buffer.subSequence(-2, 10));
        Assert.assertThrows(IndexOutOfBoundsException.class, () -> buffer.subSequence(12, 10));
        Assert.assertThrows(IndexOutOfBoundsException.class, () -> buffer.subSequence(2, 1));
    }

}
