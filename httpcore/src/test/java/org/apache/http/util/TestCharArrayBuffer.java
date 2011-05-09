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

package org.apache.http.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link CharArrayBuffer}.
 *
 */
public class TestCharArrayBuffer {

    @Test
    public void testConstructor() throws Exception {
        CharArrayBuffer buffer = new CharArrayBuffer(16);
        Assert.assertEquals(16, buffer.capacity());
        Assert.assertEquals(0, buffer.length());
        Assert.assertNotNull(buffer.buffer());
        Assert.assertEquals(16, buffer.buffer().length);
        try {
            new CharArrayBuffer(-1);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testSimpleAppend() throws Exception {
        CharArrayBuffer buffer = new CharArrayBuffer(16);
        Assert.assertEquals(16, buffer.capacity());
        Assert.assertEquals(0, buffer.length());
        char[] b1 = buffer.toCharArray();
        Assert.assertNotNull(b1);
        Assert.assertEquals(0, b1.length);
        Assert.assertTrue(buffer.isEmpty());
        Assert.assertFalse(buffer.isFull());

        char[] tmp = new char[] { '1', '2', '3', '4'};
        buffer.append(tmp, 0, tmp.length);
        Assert.assertEquals(16, buffer.capacity());
        Assert.assertEquals(4, buffer.length());
        Assert.assertFalse(buffer.isEmpty());
        Assert.assertFalse(buffer.isFull());

        char[] b2 = buffer.toCharArray();
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
        CharArrayBuffer buffer = new CharArrayBuffer(4);
        Assert.assertEquals(4, buffer.capacity());

        char[] tmp = new char[] { '1', '2', '3', '4'};
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
        CharArrayBuffer buffer = new CharArrayBuffer(8);
        buffer.append("stuff");
        buffer.append(" and more stuff");
        Assert.assertEquals("stuff and more stuff", buffer.toString());
    }

    @Test
    public void testAppendNullString() throws Exception {
        CharArrayBuffer buffer = new CharArrayBuffer(8);
        buffer.append((String)null);
        Assert.assertEquals("null", buffer.toString());
    }

    @Test
    public void testAppendCharArrayBuffer() throws Exception {
        CharArrayBuffer buffer1 = new CharArrayBuffer(8);
        buffer1.append(" and more stuff");
        CharArrayBuffer buffer2 = new CharArrayBuffer(8);
        buffer2.append("stuff");
        buffer2.append(buffer1);
        Assert.assertEquals("stuff and more stuff", buffer2.toString());
    }

    @Test
    public void testAppendNullCharArrayBuffer() throws Exception {
        CharArrayBuffer buffer = new CharArrayBuffer(8);
        buffer.append((CharArrayBuffer)null);
        buffer.append((CharArrayBuffer)null, 0, 0);
        Assert.assertEquals("", buffer.toString());
    }

    @Test
    public void testAppendSingleChar() throws Exception {
        CharArrayBuffer buffer = new CharArrayBuffer(4);
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
        CharArrayBuffer buffer = new CharArrayBuffer(4);
        buffer.append((char[])null, 0, 0);

        char[] tmp = new char[] { '1', '2', '3', '4'};
        try {
            buffer.append(tmp, -1, 0);
            Assert.fail("IndexOutOfBoundsException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            buffer.append(tmp, 0, -1);
            Assert.fail("IndexOutOfBoundsException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            buffer.append(tmp, 0, 8);
            Assert.fail("IndexOutOfBoundsException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            buffer.append(tmp, 10, Integer.MAX_VALUE);
            Assert.fail("IndexOutOfBoundsException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            buffer.append(tmp, 2, 4);
            Assert.fail("IndexOutOfBoundsException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
    }

    @Test
    public void testSetLength() throws Exception {
        CharArrayBuffer buffer = new CharArrayBuffer(4);
        buffer.setLength(2);
        Assert.assertEquals(2, buffer.length());
    }

    @Test
    public void testSetInvalidLength() throws Exception {
        CharArrayBuffer buffer = new CharArrayBuffer(4);
        try {
            buffer.setLength(-2);
            Assert.fail("IndexOutOfBoundsException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            buffer.setLength(200);
            Assert.fail("IndexOutOfBoundsException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
    }

    @Test
    public void testEnsureCapacity() throws Exception {
        CharArrayBuffer buffer = new CharArrayBuffer(4);
        buffer.ensureCapacity(2);
        Assert.assertEquals(4, buffer.capacity());
        buffer.ensureCapacity(8);
        Assert.assertEquals(8, buffer.capacity());
    }

    @Test
    public void testIndexOf() {
        CharArrayBuffer buffer = new CharArrayBuffer(16);
        buffer.append("name: value");
        Assert.assertEquals(4, buffer.indexOf(':'));
        Assert.assertEquals(-1, buffer.indexOf(','));
        Assert.assertEquals(4, buffer.indexOf(':', -1, 11));
        Assert.assertEquals(4, buffer.indexOf(':', 0, 1000));
        Assert.assertEquals(-1, buffer.indexOf(':', 2, 1));
    }

    @Test
    public void testSubstring() {
        CharArrayBuffer buffer = new CharArrayBuffer(16);
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
        CharArrayBuffer buffer = new CharArrayBuffer(16);
        buffer.append("stuff");
        try {
            buffer.substring(-2, 10);
            Assert.fail("IndexOutOfBoundsException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            buffer.substringTrimmed(-2, 10);
            Assert.fail("IndexOutOfBoundsException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            buffer.substring(12, 10);
            Assert.fail("IndexOutOfBoundsException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            buffer.substringTrimmed(12, 10);
            Assert.fail("IndexOutOfBoundsException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            buffer.substring(2, 1);
            Assert.fail("IndexOutOfBoundsException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            buffer.substringTrimmed(2, 1);
            Assert.fail("IndexOutOfBoundsException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
    }

    @Test
    public void testAppendAsciiByteArray() throws Exception {
        String s1 = "stuff";
        String s2 = " and more stuff";
        byte[] b1 = s1.getBytes("US-ASCII");
        byte[] b2 = s2.getBytes("US-ASCII");

        CharArrayBuffer buffer = new CharArrayBuffer(8);
        buffer.append(b1, 0, b1.length);
        buffer.append(b2, 0, b2.length);

        Assert.assertEquals("stuff and more stuff", buffer.toString());
    }

    @Test
    public void testAppendISOByteArray() throws Exception {
        byte[] b = new byte[] {0x00, 0x20, 0x7F, -0x80, -0x01};

        CharArrayBuffer buffer = new CharArrayBuffer(8);
        buffer.append(b, 0, b.length);
        char[] ch = buffer.toCharArray();
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
        CharArrayBuffer buffer = new CharArrayBuffer(8);
        buffer.append((byte[])null, 0, 0);
        Assert.assertEquals("", buffer.toString());
    }

    @Test
    public void testAppendNullByteArrayBuffer() throws Exception {
        CharArrayBuffer buffer = new CharArrayBuffer(8);
        buffer.append((ByteArrayBuffer)null, 0, 0);
        Assert.assertEquals("", buffer.toString());
    }

    @Test
    public void testInvalidAppendAsciiByteArray() throws Exception {
        CharArrayBuffer buffer = new CharArrayBuffer(4);
        buffer.append((byte[])null, 0, 0);

        byte[] tmp = new byte[] { '1', '2', '3', '4'};
        try {
            buffer.append(tmp, -1, 0);
            Assert.fail("IndexOutOfBoundsException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            buffer.append(tmp, 0, -1);
            Assert.fail("IndexOutOfBoundsException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            buffer.append(tmp, 0, 8);
            Assert.fail("IndexOutOfBoundsException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            buffer.append(tmp, 10, Integer.MAX_VALUE);
            Assert.fail("IndexOutOfBoundsException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            buffer.append(tmp, 2, 4);
            Assert.fail("IndexOutOfBoundsException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
    }

    @Test
    public void testSerialization() throws Exception {
        CharArrayBuffer orig = new CharArrayBuffer(32);
        orig.append('a');
        orig.append('b');
        orig.append('c');
        ByteArrayOutputStream outbuffer = new ByteArrayOutputStream();
        ObjectOutputStream outstream = new ObjectOutputStream(outbuffer);
        outstream.writeObject(orig);
        outstream.close();
        byte[] raw = outbuffer.toByteArray();
        ByteArrayInputStream inbuffer = new ByteArrayInputStream(raw);
        ObjectInputStream instream = new ObjectInputStream(inbuffer);
        CharArrayBuffer clone = (CharArrayBuffer) instream.readObject();
        Assert.assertEquals(orig.capacity(), clone.capacity());
        Assert.assertEquals(orig.length(), clone.length());
        char[] data = clone.toCharArray();
        Assert.assertNotNull(data);
        Assert.assertEquals(3, data.length);
        Assert.assertEquals('a', data[0]);
        Assert.assertEquals('b', data[1]);
        Assert.assertEquals('c', data[2]);
    }

}
