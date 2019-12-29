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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.http.ContentType;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link StringEntity}.
 */
public class TestStringEntity {

    @Test
    public void testBasics() throws Exception {
        final String s = "Message content";
        final StringEntity httpentity = new StringEntity(s, ContentType.TEXT_PLAIN);

        final byte[] bytes = s.getBytes(StandardCharsets.ISO_8859_1);
        Assert.assertEquals(bytes.length, httpentity.getContentLength());
        Assert.assertNotNull(httpentity.getContent());
        Assert.assertTrue(httpentity.isRepeatable());
        Assert.assertFalse(httpentity.isStreaming());
    }

    @Test
    public void testNullConstructor() throws Exception {
        try {
            new StringEntity(null);
            Assert.fail("NullPointerException should have been thrown");
        } catch (final NullPointerException ex) {
            // expected
        }
    }

    @Test
    public void testDefaultContent() throws Exception {
        final String s = "Message content";
        StringEntity httpentity = new StringEntity(s, ContentType.create("text/csv", "ANSI_X3.4-1968"));
        Assert.assertEquals("text/csv; charset=US-ASCII", httpentity.getContentType());
        httpentity = new StringEntity(s, StandardCharsets.US_ASCII);
        Assert.assertEquals("text/plain; charset=US-ASCII", httpentity.getContentType());
        httpentity = new StringEntity(s);
        Assert.assertEquals("text/plain; charset=ISO-8859-1", httpentity.getContentType());
    }

    private static String constructString(final int [] unicodeChars) {
        final StringBuilder buffer = new StringBuilder();
        if (unicodeChars != null) {
            for (final int unicodeChar : unicodeChars) {
                buffer.append((char)unicodeChar);
            }
        }
        return buffer.toString();
    }

    static final int SWISS_GERMAN_HELLO [] = {
            0x47, 0x72, 0xFC, 0x65, 0x7A, 0x69, 0x5F, 0x7A, 0xE4, 0x6D, 0xE4
        };

    @Test
    public void testNullCharset() throws Exception {
        final String s = constructString(SWISS_GERMAN_HELLO);
        StringEntity httpentity = new StringEntity(s, ContentType.create("text/plain", (Charset) null));
        Assert.assertNotNull(httpentity.getContentType());
        Assert.assertEquals("text/plain", httpentity.getContentType());
        Assert.assertEquals(s, EntityUtils.toString(httpentity));
        httpentity = new StringEntity(s, (Charset) null);
        Assert.assertNotNull(httpentity.getContentType());
        Assert.assertEquals("text/plain", httpentity.getContentType());
        Assert.assertEquals(s, EntityUtils.toString(httpentity));
    }

    @Test
    public void testWriteTo() throws Exception {
        final String s = "Message content";
        final byte[] bytes = s.getBytes(StandardCharsets.ISO_8859_1);
        final StringEntity httpentity = new StringEntity(s);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        httpentity.writeTo(out);
        byte[] bytes2 = out.toByteArray();
        Assert.assertNotNull(bytes2);
        Assert.assertEquals(bytes.length, bytes2.length);
        for (int i = 0; i < bytes.length; i++) {
            Assert.assertEquals(bytes[i], bytes2[i]);
        }

        out = new ByteArrayOutputStream();
        httpentity.writeTo(out);
        bytes2 = out.toByteArray();
        Assert.assertNotNull(bytes2);
        Assert.assertEquals(bytes.length, bytes2.length);
        for (int i = 0; i < bytes.length; i++) {
            Assert.assertEquals(bytes[i], bytes2[i]);
        }

        try {
            httpentity.writeTo(null);
            Assert.fail("NullPointerException should have been thrown");
        } catch (final NullPointerException ex) {
            // expected
        }
    }

}
