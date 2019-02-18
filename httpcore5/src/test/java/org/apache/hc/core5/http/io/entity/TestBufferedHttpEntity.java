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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link BufferedHttpEntity}.
 *
 */
public class TestBufferedHttpEntity {

    @Test
    public void testBufferingEntity() throws Exception {
        final byte[] bytes = "Message content".getBytes(StandardCharsets.US_ASCII);
        final BufferedHttpEntity entity = new BufferedHttpEntity(
                new InputStreamEntity(new ByteArrayInputStream(bytes), -1, null));
        Assert.assertEquals(bytes.length, entity.getContentLength());
        Assert.assertTrue(entity.isRepeatable());
        Assert.assertFalse(entity.isChunked());
        Assert.assertFalse(entity.isStreaming());

        // test if we can obtain contain multiple times
        Assert.assertNotNull(entity.getContent ());
        Assert.assertNotNull(entity.getContent ());
    }

    @Test
    public void testWrappingEntity() throws Exception {
        final byte[] bytes = "Message content".getBytes(StandardCharsets.US_ASCII);
        final ByteArrayEntity httpentity = new ByteArrayEntity(bytes, null, true);
        final BufferedHttpEntity bufentity = new BufferedHttpEntity(httpentity);
        Assert.assertEquals(bytes.length, bufentity.getContentLength());
        Assert.assertTrue(bufentity.isRepeatable());
        Assert.assertTrue(bufentity.isChunked());
        Assert.assertFalse(bufentity.isStreaming());

        // test if we can obtain contain multiple times
        Assert.assertNotNull(bufentity.getContent ());
        Assert.assertNotNull(bufentity.getContent ());
    }

    @Test
    public void testIllegalConstructor() throws Exception {
        try {
            new BufferedHttpEntity(null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testWriteToBuffered() throws Exception {
        final byte[] bytes = "Message content".getBytes(StandardCharsets.US_ASCII);
        final InputStreamEntity httpentity = new InputStreamEntity(new ByteArrayInputStream(bytes), -1, null);
        final BufferedHttpEntity bufentity = new BufferedHttpEntity(httpentity);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bufentity.writeTo(out);
        byte[] bytes2 = out.toByteArray();
        Assert.assertNotNull(bytes2);
        Assert.assertEquals(bytes.length, bytes2.length);
        for (int i = 0; i < bytes.length; i++) {
            Assert.assertEquals(bytes[i], bytes2[i]);
        }

        out = new ByteArrayOutputStream();
        bufentity.writeTo(out);
        bytes2 = out.toByteArray();
        Assert.assertNotNull(bytes2);
        Assert.assertEquals(bytes.length, bytes2.length);
        for (int i = 0; i < bytes.length; i++) {
            Assert.assertEquals(bytes[i], bytes2[i]);
        }

        try {
            bufentity.writeTo(null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testWriteToWrapped() throws Exception {
        final byte[] bytes = "Message content".getBytes(StandardCharsets.US_ASCII);
        final ByteArrayEntity httpentity = new ByteArrayEntity(bytes, null);
        final BufferedHttpEntity bufentity = new BufferedHttpEntity(httpentity);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bufentity.writeTo(out);
        byte[] bytes2 = out.toByteArray();
        Assert.assertNotNull(bytes2);
        Assert.assertEquals(bytes.length, bytes2.length);
        for (int i = 0; i < bytes.length; i++) {
            Assert.assertEquals(bytes[i], bytes2[i]);
        }

        out = new ByteArrayOutputStream();
        bufentity.writeTo(out);
        bytes2 = out.toByteArray();
        Assert.assertNotNull(bytes2);
        Assert.assertEquals(bytes.length, bytes2.length);
        for (int i = 0; i < bytes.length; i++) {
            Assert.assertEquals(bytes[i], bytes2[i]);
        }

        try {
            bufentity.writeTo(null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
    }

}
