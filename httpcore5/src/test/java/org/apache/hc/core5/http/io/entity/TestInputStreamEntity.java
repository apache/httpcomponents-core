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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link InputStreamEntity}.
 *
 */
public class TestInputStreamEntity {

    @Test
    public void testBasics() throws Exception {
        final byte[] bytes = "Message content".getBytes(StandardCharsets.ISO_8859_1);
        final InputStream instream = new ByteArrayInputStream(bytes);
        final InputStreamEntity httpentity = new InputStreamEntity(instream, bytes.length);

        Assert.assertEquals(bytes.length, httpentity.getContentLength());
        Assert.assertEquals(instream, httpentity.getContent());
        Assert.assertNotNull(httpentity.getContent());
        Assert.assertFalse(httpentity.isRepeatable());
        Assert.assertTrue(httpentity.isStreaming());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIllegalConstructor() throws Exception {
        new InputStreamEntity(null, 0);
    }

    @Test
    public void testUnknownLengthConstructor() throws Exception {
        final InputStream instream = new ByteArrayInputStream(new byte[0]);
        final InputStreamEntity httpentity = new InputStreamEntity(instream);
        Assert.assertEquals(-1, httpentity.getContentLength());
    }

    @Test
    public void testWriteTo() throws Exception {
        final String message = "Message content";
        final byte[] bytes = message.getBytes(StandardCharsets.ISO_8859_1);
        final InputStream instream = new ByteArrayInputStream(bytes);
        final InputStreamEntity httpentity = new InputStreamEntity(instream, bytes.length);

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        httpentity.writeTo(out);
        final byte[] writtenBytes = out.toByteArray();
        Assert.assertNotNull(writtenBytes);
        Assert.assertEquals(bytes.length, writtenBytes.length);

        final String s = new String(writtenBytes, StandardCharsets.ISO_8859_1.name());
        Assert.assertEquals(message, s);
    }

    @Test
    public void testWriteToPartialContent() throws Exception {
        final String message = "Message content";
        final byte[] bytes = message.getBytes(StandardCharsets.ISO_8859_1);
        final InputStream instream = new ByteArrayInputStream(bytes);
        final int contentLength = 7;
        final InputStreamEntity httpentity = new InputStreamEntity(instream, contentLength);

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        httpentity.writeTo(out);
        final byte[] writtenBytes = out.toByteArray();
        Assert.assertNotNull(writtenBytes);
        Assert.assertEquals(contentLength, writtenBytes.length);

        final String s = new String(writtenBytes, StandardCharsets.ISO_8859_1.name());
        Assert.assertEquals(message.substring(0, contentLength), s);
    }

    @Test
    public void testWriteToUnknownLength() throws Exception {
        final String message = "Message content";
        final byte[] bytes = message.getBytes(StandardCharsets.ISO_8859_1);
        final InputStream instream = new ByteArrayInputStream(bytes);
        final InputStreamEntity httpentity = new InputStreamEntity(instream);

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        httpentity.writeTo(out);
        final byte[] writtenBytes = out.toByteArray();
        Assert.assertNotNull(writtenBytes);
        Assert.assertEquals(bytes.length, writtenBytes.length);

        final String s = new String(writtenBytes, StandardCharsets.ISO_8859_1.name());
        Assert.assertEquals(message, s);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWriteToNull() throws Exception {
        final InputStream instream = new ByteArrayInputStream(new byte[0]);
        final InputStreamEntity httpentity = new InputStreamEntity(instream, 0);
        httpentity.writeTo(null);
    }
}
