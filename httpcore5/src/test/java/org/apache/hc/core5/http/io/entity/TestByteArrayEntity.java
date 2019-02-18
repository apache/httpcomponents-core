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

package org.apache.hc.core5.http.io.entity;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link ByteArrayEntity}.
 *
 */
public class TestByteArrayEntity {

    @Test
    public void testBasics() throws Exception {
        final byte[] bytes = "Message content".getBytes(StandardCharsets.US_ASCII);
        final ByteArrayEntity entity = new ByteArrayEntity(bytes, null);

        Assert.assertEquals(bytes.length, entity.getContentLength());
        Assert.assertNotNull(entity.getContent());
        Assert.assertTrue(entity.isRepeatable());
        Assert.assertFalse(entity.isStreaming());
    }

    @Test
    public void testBasicOffLen() throws Exception {
        final byte[] bytes = "Message content".getBytes(StandardCharsets.US_ASCII);
        final ByteArrayEntity entity = new ByteArrayEntity(bytes, 8, 7, null);

        Assert.assertEquals(7, entity.getContentLength());
        Assert.assertNotNull(entity.getContent());
        Assert.assertTrue(entity.isRepeatable());
        Assert.assertFalse(entity.isStreaming());
    }

    @Test(expected=IllegalArgumentException.class)
    public void testIllegalConstructorNullByteArray() throws Exception {
        new ByteArrayEntity(null, null);
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void testIllegalConstructorBadLen() throws Exception {
        final byte[] bytes = "Message content".getBytes(StandardCharsets.US_ASCII);
        new ByteArrayEntity(bytes, 0, bytes.length + 1, null);
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void testIllegalConstructorBadOff1() throws Exception {
        final byte[] bytes = "Message content".getBytes(StandardCharsets.US_ASCII);
        new ByteArrayEntity(bytes, -1, bytes.length, null);
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void testIllegalConstructorBadOff2() throws Exception {
        final byte[] bytes = "Message content".getBytes(StandardCharsets.US_ASCII);
        new ByteArrayEntity(bytes, bytes.length + 1, bytes.length, null);
    }

    @Test
    public void testWriteTo() throws Exception {
        final byte[] bytes = "Message content".getBytes(StandardCharsets.US_ASCII);
        final ByteArrayEntity entity = new ByteArrayEntity(bytes, null);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        entity.writeTo(out);
        byte[] bytes2 = out.toByteArray();
        Assert.assertNotNull(bytes2);
        Assert.assertEquals(bytes.length, bytes2.length);
        for (int i = 0; i < bytes.length; i++) {
            Assert.assertEquals(bytes[i], bytes2[i]);
        }

        out = new ByteArrayOutputStream();
        entity.writeTo(out);
        bytes2 = out.toByteArray();
        Assert.assertNotNull(bytes2);
        Assert.assertEquals(bytes.length, bytes2.length);
        for (int i = 0; i < bytes.length; i++) {
            Assert.assertEquals(bytes[i], bytes2[i]);
        }

        try {
            entity.writeTo(null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testWriteToOffLen() throws Exception {
        final byte[] bytes = "Message content".getBytes(StandardCharsets.US_ASCII);
        final int off = 8;
        final int len = 7;
        final ByteArrayEntity entity = new ByteArrayEntity(bytes, off, len, null);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        entity.writeTo(out);
        byte[] bytes2 = out.toByteArray();
        Assert.assertNotNull(bytes2);
        Assert.assertEquals(len, bytes2.length);
        for (int i = 0; i < len; i++) {
            Assert.assertEquals(bytes[i+off], bytes2[i]);
        }

        out = new ByteArrayOutputStream();
        entity.writeTo(out);
        bytes2 = out.toByteArray();
        Assert.assertNotNull(bytes2);
        Assert.assertEquals(len, bytes2.length);
        for (int i = 0; i < len; i++) {
            Assert.assertEquals(bytes[i+off], bytes2[i]);
        }

        try {
            entity.writeTo(null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
    }

}
