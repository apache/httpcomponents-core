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
 * Unit tests for {@link ByteArrayBuffer}.
 *
 */
public class TestByteArrayBuffer {

    @Test
    public void testConstructor() throws Exception {
        ByteArrayBuffer buffer = new ByteArrayBuffer(16);
        Assert.assertEquals(16, buffer.capacity());
        Assert.assertEquals(0, buffer.length());
        Assert.assertNotNull(buffer.buffer());
        Assert.assertEquals(16, buffer.buffer().length);
        try {
            new ByteArrayBuffer(-1);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testSimpleAppend() throws Exception {
        ByteArrayBuffer buffer = new ByteArrayBuffer(16);
        Assert.assertEquals(16, buffer.capacity());
        Assert.assertEquals(0, buffer.length());
        byte[] b1 = buffer.toByteArray();
        Assert.assertNotNull(b1);
        Assert.assertEquals(0, b1.length);
        Assert.assertTrue(buffer.isEmpty());
        Assert.assertFalse(buffer.isFull());

        byte[] tmp = new byte[] { 1, 2, 3, 4};
        buffer.append(tmp, 0, tmp.length);
        Assert.assertEquals(16, buffer.capacity());
        Assert.assertEquals(4, buffer.length());
        Assert.assertFalse(buffer.isEmpty());
        Assert.assertFalse(buffer.isFull());

        byte[] b2 = buffer.toByteArray();
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
        ByteArrayBuffer buffer = new ByteArrayBuffer(4);
        Assert.assertEquals(4, buffer.capacity());

        byte[] tmp = new byte[] { 1, 2, 3, 4};
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
    public void testInvalidAppend() throws Exception {
        ByteArrayBuffer buffer = new ByteArrayBuffer(4);
        buffer.append((byte[])null, 0, 0);

        byte[] tmp = new byte[] { 1, 2, 3, 4};
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
    public void testAppendOneByte() throws Exception {
        ByteArrayBuffer buffer = new ByteArrayBuffer(4);
        Assert.assertEquals(4, buffer.capacity());

        byte[] tmp = new byte[] { 1, 127, -1, -128, 1, -2};
        for (int i = 0; i < tmp.length; i++) {
            buffer.append(tmp[i]);
        }
        Assert.assertEquals(8, buffer.capacity());
        Assert.assertEquals(6, buffer.length());

        for (int i = 0; i < tmp.length; i++) {
            Assert.assertEquals(tmp[i], buffer.byteAt(i));
        }
    }

    @Test
    public void testSetLength() throws Exception {
        ByteArrayBuffer buffer = new ByteArrayBuffer(4);
        buffer.setLength(2);
        Assert.assertEquals(2, buffer.length());
    }

    @Test
    public void testSetInvalidLength() throws Exception {
        ByteArrayBuffer buffer = new ByteArrayBuffer(4);
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
        ByteArrayBuffer buffer = new ByteArrayBuffer(4);
        buffer.ensureCapacity(2);
        Assert.assertEquals(4, buffer.capacity());
        buffer.ensureCapacity(8);
        Assert.assertEquals(8, buffer.capacity());
    }

    @Test
    public void testIndexOf() throws Exception {
        final byte COLON = (byte) ':';
        final byte COMMA = (byte) ',';
        byte[] bytes = "name1: value1; name2: value2".getBytes("US-ASCII");
        int index1 = 5;
        int index2 = 20;

        ByteArrayBuffer buffer = new ByteArrayBuffer(16);
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
        String s1 = "stuff";
        String s2 = " and more stuff";
        char[] b1 = s1.toCharArray();
        char[] b2 = s2.toCharArray();

        ByteArrayBuffer buffer = new ByteArrayBuffer(8);
        buffer.append(b1, 0, b1.length);
        buffer.append(b2, 0, b2.length);

        Assert.assertEquals(s1 + s2, new String(buffer.toByteArray(), "US-ASCII"));
    }

    @Test
    public void testAppendNullCharArray() throws Exception {
        ByteArrayBuffer buffer = new ByteArrayBuffer(8);
        buffer.append((char[])null, 0, 0);
        Assert.assertEquals(0, buffer.length());
    }

    @Test
    public void testAppendEmptyCharArray() throws Exception {
        ByteArrayBuffer buffer = new ByteArrayBuffer(8);
        buffer.append(new char[] {}, 0, 0);
        Assert.assertEquals(0, buffer.length());
    }

    @Test
    public void testAppendNullCharArrayBuffer() throws Exception {
        ByteArrayBuffer buffer = new ByteArrayBuffer(8);
        buffer.append((CharArrayBuffer)null, 0, 0);
        Assert.assertEquals(0, buffer.length());
    }

    @Test
    public void testInvalidAppendCharArrayAsAscii() throws Exception {
        ByteArrayBuffer buffer = new ByteArrayBuffer(4);
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
    public void testSerialization() throws Exception {
        ByteArrayBuffer orig = new ByteArrayBuffer(32);
        orig.append(1);
        orig.append(2);
        orig.append(3);
        ByteArrayOutputStream outbuffer = new ByteArrayOutputStream();
        ObjectOutputStream outstream = new ObjectOutputStream(outbuffer);
        outstream.writeObject(orig);
        outstream.close();
        byte[] raw = outbuffer.toByteArray();
        ByteArrayInputStream inbuffer = new ByteArrayInputStream(raw);
        ObjectInputStream instream = new ObjectInputStream(inbuffer);
        ByteArrayBuffer clone = (ByteArrayBuffer) instream.readObject();
        Assert.assertEquals(orig.capacity(), clone.capacity());
        Assert.assertEquals(orig.length(), clone.length());
        byte[] data = clone.toByteArray();
        Assert.assertNotNull(data);
        Assert.assertEquals(3, data.length);
        Assert.assertEquals(1, data[0]);
        Assert.assertEquals(2, data[1]);
        Assert.assertEquals(3, data[2]);
    }

}
