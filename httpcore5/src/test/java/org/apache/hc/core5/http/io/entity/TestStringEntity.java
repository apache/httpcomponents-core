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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link StringEntity}.
 */
public class TestStringEntity {

    @Test
    public void testBasics() throws Exception {
        final String s = "Message content";
        try (final StringEntity httpentity = new StringEntity(s, ContentType.TEXT_PLAIN)) {

            final byte[] bytes = s.getBytes(StandardCharsets.US_ASCII);
            Assertions.assertEquals(bytes.length, httpentity.getContentLength());
            Assertions.assertNotNull(httpentity.getContent());
            Assertions.assertTrue(httpentity.isRepeatable());
            Assertions.assertFalse(httpentity.isStreaming());
        }
    }

    @Test
    public void testNullConstructor() throws Exception {
        Assertions.assertThrows(NullPointerException.class, () -> new StringEntity(null));
    }

    @Test
    public void testDefaultContent() throws Exception {
        final String s = "Message content";
        StringEntity httpentity = new StringEntity(s, ContentType.create("text/csv", "ANSI_X3.4-1968"));
        Assertions.assertEquals("text/csv; charset=US-ASCII", httpentity.getContentType());
        httpentity = new StringEntity(s, StandardCharsets.US_ASCII);
        Assertions.assertEquals("text/plain; charset=US-ASCII", httpentity.getContentType());
        httpentity = new StringEntity(s);
        Assertions.assertEquals("text/plain; charset=UTF-8", httpentity.getContentType());
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
        Assertions.assertNotNull(httpentity.getContentType());
        Assertions.assertEquals("text/plain", httpentity.getContentType());
        Assertions.assertEquals(s, EntityUtils.toString(httpentity));
        httpentity = new StringEntity(s, (Charset) null);
        Assertions.assertNotNull(httpentity.getContentType());
        Assertions.assertEquals("text/plain", httpentity.getContentType());
        Assertions.assertEquals(s, EntityUtils.toString(httpentity));
    }

    @Test
    public void testWriteTo() throws Exception {
        final String s = "Message content";
        final byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        try (final StringEntity httpentity = new StringEntity(s)) {

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            httpentity.writeTo(out);
            byte[] bytes2 = out.toByteArray();
            Assertions.assertNotNull(bytes2);
            Assertions.assertEquals(bytes.length, bytes2.length);
            for (int i = 0; i < bytes.length; i++) {
                Assertions.assertEquals(bytes[i], bytes2[i]);
            }

            out = new ByteArrayOutputStream();
            httpentity.writeTo(out);
            bytes2 = out.toByteArray();
            Assertions.assertNotNull(bytes2);
            Assertions.assertEquals(bytes.length, bytes2.length);
            for (int i = 0; i < bytes.length; i++) {
                Assertions.assertEquals(bytes[i], bytes2[i]);
            }

            Assertions.assertThrows(NullPointerException.class, () -> httpentity.writeTo(null));
        }
    }

}
