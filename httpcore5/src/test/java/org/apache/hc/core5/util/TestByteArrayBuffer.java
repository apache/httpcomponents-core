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

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link ByteArrayBuffer}.
 *
 */
public class TestByteArrayBuffer {

    @Test
    public void testConstructor() throws Exception {
        final ByteArrayBuffer buffer = new ByteArrayBuffer(16);
        Assert.assertEquals(16, buffer.capacity());
        Assert.assertEquals(0, buffer.length());
        Assert.assertNotNull(buffer.array());
        Assert.assertEquals(16, buffer.array().length);
        Assert.assertThrows(IllegalArgumentException.class, () -> new ByteArrayBuffer(-1));
    }

    @Test
    public void testSimpleAppend() throws Exception {
        final ByteArrayBuffer buffer = new ByteArrayBuffer(16);
        Assert.assertEquals(16, buffer.capacity());
        Assert.assertEquals(0, buffer.length());
        final byte[] b1 = buffer.toByteArray();
        Assert.assertNotNull(b1);
        Assert.assertEquals(0, b1.length);
        Assert.assertTrue(buffer.isEmpty());
        Assert.assertFalse(buffer.isFull());

        final byte[] tmp = new byte[] { 1, 2, 3, 4};
        buffer.append(tmp, 0, tmp.length);
        Assert.assertEquals(16, buffer.capacity());
        Assert.assertEquals(4, buffer.length());
        Assert.assertFalse(buffer.isEmpty());
        Assert.assertFalse(buffer.isFull());

        final byte[] b2 = buffer.toByteArray();
        Assert.assertNotNull(b2);
        Assert.assertEquals(4, b2.length);
        for (int i = 0; i < tmp.length; i++) {
            Assert.assertEquals(tmp[i], b2[i]);
            Assert.assertEquals(tmp[i], buffer.byteAt(i));
        }
        buffer.clear();
        Assert.assertEquals(16, buffer.capacity());
        Assert.assertEquals(0, buffer.length());
        Assert.assertTrue(buffer.isEmpty());
        Assert.assertFalse(buffer.isFull());
    }

    @Test
    public void testExpandAppend() throws Exception {
        final ByteArrayBuffer buffer = new ByteArrayBuffer(4);
        Assert.assertEquals(4, buffer.capacity());

        final byte[] tmp = new byte[] { 1, 2, 3, 4};
        buffer.append(tmp, 0, 2);
        buffer.append(tmp, 0, 4);
        buffer.append(tmp, 0, 0);

        Assert.assertEquals(8, buffer.capacity());
        Assert.assertEquals(6, buffer.length());

        buffer.append(tmp, 0, 4);

        Assert.assertEquals(16, buffer.capacity());
        Assert.assertEquals(10, buffer.length());
    }

    @Test
    public void testAppendHeapByteBuffer() {
        final ByteArrayBuffer buffer = new ByteArrayBuffer(4);
        Assert.assertEquals(4, buffer.capacity());

        final ByteBuffer tmp = ByteBuffer.wrap(new byte[] { 1, 2, 3, 4, 5, 6});
        buffer.append(tmp);

        Assert.assertFalse("The input buffer should be drained", tmp.hasRemaining());
        Assert.assertEquals(8, buffer.capacity());
        Assert.assertEquals(6, buffer.length());

        tmp.clear();
        buffer.append(tmp);

        Assert.assertFalse("The input buffer should be drained", tmp.hasRemaining());
        Assert.assertEquals(16, buffer.capacity());
        Assert.assertEquals(12, buffer.length());
        Assert.assertArrayEquals(new byte[] {1, 2, 3, 4, 5, 6, 1, 2, 3, 4, 5, 6}, buffer.toByteArray());
    }

    @Test
    public void testAppendHeapByteBufferWithOffset() {
        final ByteArrayBuffer buffer = new ByteArrayBuffer(4);
        Assert.assertEquals(4, buffer.capacity());

        final ByteBuffer tmp = ByteBuffer.wrap(new byte[] { 7, 7, 1, 2, 3, 4, 5, 6, 7, 7}, 2, 6).slice();
        Assert.assertTrue("Validate this is testing a buffer with an array offset", tmp.arrayOffset() > 0);

        buffer.append(tmp);

        Assert.assertFalse("The input buffer should be drained", tmp.hasRemaining());
        Assert.assertEquals(8, buffer.capacity());
        Assert.assertEquals(6, buffer.length());

        tmp.clear();
        Assert.assertEquals(6, tmp.remaining());
        buffer.append(tmp);

        Assert.assertFalse("The input buffer should be drained", tmp.hasRemaining());
        Assert.assertEquals(16, buffer.capacity());
        Assert.assertEquals(12, buffer.length());
        Assert.assertArrayEquals(new byte[] {1, 2, 3, 4, 5, 6, 1, 2, 3, 4, 5, 6}, buffer.toByteArray());
    }

    @Test
    public void testAppendDirectByteBuffer() {
        final ByteArrayBuffer buffer = new ByteArrayBuffer(4);
        Assert.assertEquals(4, buffer.capacity());

        final ByteBuffer tmp = ByteBuffer.allocateDirect(6);
        tmp.put(new byte[] { 1, 2, 3, 4, 5, 6}).flip();
        buffer.append(tmp);

        Assert.assertFalse("The input buffer should be drained", tmp.hasRemaining());
        Assert.assertEquals(8, buffer.capacity());
        Assert.assertEquals(6, buffer.length());

        tmp.clear();
        buffer.append(tmp);

        Assert.assertFalse("The input buffer should be drained", tmp.hasRemaining());
        Assert.assertEquals(16, buffer.capacity());
        Assert.assertEquals(12, buffer.length());
        Assert.assertArrayEquals(new byte[] {1, 2, 3, 4, 5, 6, 1, 2, 3, 4, 5, 6}, buffer.toByteArray());
    }

    @Test
    public void testInvalidAppend() throws Exception {
        final ByteArrayBuffer buffer = new ByteArrayBuffer(4);
        buffer.append((byte[])null, 0, 0);

        final byte[] tmp = new byte[] { 1, 2, 3, 4};
        Assert.assertThrows(IndexOutOfBoundsException.class, () -> buffer.append(tmp, -1, 0));
        Assert.assertThrows(IndexOutOfBoundsException.class, () -> buffer.append(tmp, 0, -1));
        Assert.assertThrows(IndexOutOfBoundsException.class, () -> buffer.append(tmp, 0, 8));
        Assert.assertThrows(IndexOutOfBoundsException.class, () -> buffer.append(tmp, 10, Integer.MAX_VALUE));
        Assert.assertThrows(IndexOutOfBoundsException.class, () -> buffer.append(tmp, 2, 4));
    }

    @Test
    public void testAppendOneByte() throws Exception {
        final ByteArrayBuffer buffer = new ByteArrayBuffer(4);
        Assert.assertEquals(4, buffer.capacity());

        final byte[] tmp = new byte[] { 1, 127, -1, -128, 1, -2};
        for (final byte element : tmp) {
            buffer.append(element);
        }
        Assert.assertEquals(8, buffer.capacity());
        Assert.assertEquals(6, buffer.length());

        for (int i = 0; i < tmp.length; i++) {
            Assert.assertEquals(tmp[i], buffer.byteAt(i));
        }
    }

    @Test
    public void testSetLength() throws Exception {
        final ByteArrayBuffer buffer = new ByteArrayBuffer(4);
        buffer.setLength(2);
        Assert.assertEquals(2, buffer.length());
    }

    @Test
    public void testSetInvalidLength() throws Exception {
        final ByteArrayBuffer buffer = new ByteArrayBuffer(4);
        Assert.assertThrows(IndexOutOfBoundsException.class, () -> buffer.setLength(-2));
        Assert.assertThrows(IndexOutOfBoundsException.class, () -> buffer.setLength(200));
    }

    @Test
    public void testEnsureCapacity() throws Exception {
        final ByteArrayBuffer buffer = new ByteArrayBuffer(4);
        buffer.ensureCapacity(2);
        Assert.assertEquals(4, buffer.capacity());
        buffer.ensureCapacity(8);
        Assert.assertEquals(8, buffer.capacity());
    }

    @Test
    public void testIndexOf() throws Exception {
        final byte COLON = (byte) ':';
        final byte COMMA = (byte) ',';
        final byte[] bytes = "name1: value1; name2: value2".getBytes(StandardCharsets.US_ASCII);
        final int index1 = 5;
        final int index2 = 20;

        final ByteArrayBuffer buffer = new ByteArrayBuffer(16);
        buffer.append(bytes, 0, bytes.length);

        Assert.assertEquals(index1, buffer.indexOf(COLON));
        Assert.assertEquals(-1, buffer.indexOf(COMMA));
        Assert.assertEquals(index1, buffer.indexOf(COLON, -1, 11));
        Assert.assertEquals(index1, buffer.indexOf(COLON, 0, 1000));
        Assert.assertEquals(-1, buffer.indexOf(COLON, 2, 1));
        Assert.assertEquals(index2, buffer.indexOf(COLON, index1 + 1, buffer.length()));
    }

    @Test
    public void testAppendCharArrayAsAscii() throws Exception {
        final String s1 = "stuff";
        final String s2 = " and more stuff";
        final char[] b1 = s1.toCharArray();
        final char[] b2 = s2.toCharArray();

        final ByteArrayBuffer buffer = new ByteArrayBuffer(8);
        buffer.append(b1, 0, b1.length);
        buffer.append(b2, 0, b2.length);

        Assert.assertEquals(s1 + s2, new String(buffer.toByteArray(), StandardCharsets.US_ASCII));
    }

    @Test
    public void testAppendNullCharArray() throws Exception {
        final ByteArrayBuffer buffer = new ByteArrayBuffer(8);
        buffer.append((char[])null, 0, 0);
        Assert.assertEquals(0, buffer.length());
    }

    @Test
    public void testAppendEmptyCharArray() throws Exception {
        final ByteArrayBuffer buffer = new ByteArrayBuffer(8);
        buffer.append(new char[] {}, 0, 0);
        Assert.assertEquals(0, buffer.length());
    }

    @Test
    public void testAppendNullCharArrayBuffer() throws Exception {
        final ByteArrayBuffer buffer = new ByteArrayBuffer(8);
        buffer.append((CharArrayBuffer)null, 0, 0);
        Assert.assertEquals(0, buffer.length());
    }

    @Test
    public void testAppendNullByteBuffer() throws Exception {
        final ByteArrayBuffer buffer = new ByteArrayBuffer(8);
        final ByteBuffer nullBuffer = null;
        buffer.append(nullBuffer);
        Assert.assertEquals(0, buffer.length());
    }

    @Test
    public void testInvalidAppendCharArrayAsAscii() throws Exception {
        final ByteArrayBuffer buffer = new ByteArrayBuffer(4);
        buffer.append((char[])null, 0, 0);

        final char[] tmp = new char[] { '1', '2', '3', '4'};
        Assert.assertThrows(IndexOutOfBoundsException.class, () -> buffer.append(tmp, -1, 0));
        Assert.assertThrows(IndexOutOfBoundsException.class, () -> buffer.append(tmp, 0, -1));
        Assert.assertThrows(IndexOutOfBoundsException.class, () -> buffer.append(tmp, 0, 8));
        Assert.assertThrows(IndexOutOfBoundsException.class, () -> buffer.append(tmp, 10, Integer.MAX_VALUE));
        Assert.assertThrows(IndexOutOfBoundsException.class, () -> buffer.append(tmp, 2, 4));
    }

    @Test
    public void testSerialization() throws Exception {
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
        Assert.assertEquals(orig.capacity(), clone.capacity());
        Assert.assertEquals(orig.length(), clone.length());
        final byte[] data = clone.toByteArray();
        Assert.assertNotNull(data);
        Assert.assertEquals(3, data.length);
        Assert.assertEquals(1, data[0]);
        Assert.assertEquals(2, data[1]);
        Assert.assertEquals(3, data[2]);
    }

    @Test
    public void testControlCharFiltering() throws Exception {
        final char[] chars = new char[256];
        for (char i = 0; i < 256; i++) {
            chars[i] = i;
        }

        final byte[] bytes = asByteArray(chars);

        Assert.assertEquals(
            "?????????\t??????????????????????"
                + " !\"#$%&'()*+,-./0123456789:;<=>?"
                + "@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_"
                + "`abcdefghijklmnopqrstuvwxyz"
                + "{|}~???????????????????????"
                + "??????????\u00A0¡¢£¤¥¦§¨©ª«¬\u00AD®¯"
                + "°±²³´µ¶·¸¹º»¼½¾¿ÀÁÂÃÄÅÆÇÈÉÊËÌÍÎÏ"
                + "ÐÑÒÓÔÕÖ×ØÙÚÛÜÝÞßàáâãäåæçèéêëìíîï"
                + "ðñòóôõö÷øùúûüýþÿ",
            new String(bytes, StandardCharsets.ISO_8859_1));
    }

    @Test
    public void testUnicodeFiltering() throws Exception {
        // Various languages
        Assert.assertEquals("?????", new String(asByteArray("буквы".toCharArray()), StandardCharsets.ISO_8859_1));
        Assert.assertEquals("????", new String(asByteArray("四字熟語".toCharArray()), StandardCharsets.ISO_8859_1));

        // Unicode snowman
        Assert.assertEquals("?", new String(asByteArray("☃".toCharArray()), StandardCharsets.ISO_8859_1));

        // Emoji (surrogate pair)
        Assert.assertEquals("??", new String(asByteArray("\uD83D\uDE00".toCharArray()), StandardCharsets.ISO_8859_1));
    }

    private static byte[] asByteArray(final char[] chars) {
        final ByteArrayBuffer byteArrayBuffer = new ByteArrayBuffer(chars.length);
        byteArrayBuffer.append(chars, 0, chars.length);
        return byteArrayBuffer.toByteArray();
    }
}
