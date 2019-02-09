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
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.net.URLEncodedUtils;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link EntityUtils}.
 *
 */
public class TestEntityUtils {

    @Test(expected = IllegalArgumentException.class)
    public void testNullEntityToByteArray() throws Exception {
        EntityUtils.toByteArray(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMaxIntContentToByteArray() throws Exception {
        final byte[] content = "Message content".getBytes(StandardCharsets.ISO_8859_1);
        final BasicHttpEntity entity = new BasicHttpEntity(new ByteArrayInputStream(content),
                Integer.MAX_VALUE + 100L, ContentType.TEXT_PLAIN.withCharset(StandardCharsets.ISO_8859_1));
        EntityUtils.toByteArray(entity);
    }

    @Test
    public void testUnknownLengthContentToByteArray() throws Exception {
        final byte[] bytes = "Message content".getBytes(StandardCharsets.ISO_8859_1);
        final BasicHttpEntity entity = new BasicHttpEntity(new ByteArrayInputStream(bytes), -1, null);
        final byte[] bytes2 = EntityUtils.toByteArray(entity);
        Assert.assertNotNull(bytes2);
        Assert.assertEquals(bytes.length, bytes2.length);
        for (int i = 0; i < bytes.length; i++) {
            Assert.assertEquals(bytes[i], bytes2[i]);
        }
    }

    @Test
    public void testKnownLengthContentToByteArray() throws Exception {
        final byte[] bytes = "Message content".getBytes(StandardCharsets.ISO_8859_1);
        final BasicHttpEntity entity = new BasicHttpEntity(new ByteArrayInputStream(bytes), bytes.length, null);
        final byte[] bytes2 = EntityUtils.toByteArray(entity);
        Assert.assertNotNull(bytes2);
        Assert.assertEquals(bytes.length, bytes2.length);
        for (int i = 0; i < bytes.length; i++) {
            Assert.assertEquals(bytes[i], bytes2[i]);
        }
    }

    @Test
    public void testNullEntityToString() throws Exception {
        try {
            EntityUtils.toString(null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMaxIntContentToString() throws Exception {
        final byte[] content = "Message content".getBytes(StandardCharsets.ISO_8859_1);
        final BasicHttpEntity entity = new BasicHttpEntity(new ByteArrayInputStream(content),
                Integer.MAX_VALUE + 100L, ContentType.TEXT_PLAIN.withCharset(StandardCharsets.ISO_8859_1));
        EntityUtils.toString(entity);
    }

    @Test
    public void testUnknownLengthContentToString() throws Exception {
        final byte[] bytes = "Message content".getBytes(StandardCharsets.ISO_8859_1);
        final BasicHttpEntity entity = new BasicHttpEntity(new ByteArrayInputStream(bytes), -1, null);
        final String s = EntityUtils.toString(entity, "ISO-8859-1");
        Assert.assertEquals("Message content", s);
    }

    @Test
    public void testKnownLengthContentToString() throws Exception {
        final byte[] bytes = "Message content".getBytes(StandardCharsets.ISO_8859_1);
        final BasicHttpEntity entity = new BasicHttpEntity(new ByteArrayInputStream(bytes), bytes.length,
                ContentType.TEXT_PLAIN.withCharset(StandardCharsets.ISO_8859_1));
        final String s = EntityUtils.toString(entity, StandardCharsets.ISO_8859_1);
        Assert.assertEquals("Message content", s);
    }

    static final int SWISS_GERMAN_HELLO [] = {
        0x47, 0x72, 0xFC, 0x65, 0x7A, 0x69, 0x5F, 0x7A, 0xE4, 0x6D, 0xE4
    };

    static final int RUSSIAN_HELLO [] = {
        0x412, 0x441, 0x435, 0x43C, 0x5F, 0x43F, 0x440, 0x438,
        0x432, 0x435, 0x442
    };

    private static String constructString(final int [] unicodeChars) {
        final StringBuilder buffer = new StringBuilder();
        if (unicodeChars != null) {
            for (final int unicodeChar : unicodeChars) {
                buffer.append((char)unicodeChar);
            }
        }
        return buffer.toString();
    }

    @Test
    public void testNoCharsetContentToString() throws Exception {
        final String content = constructString(SWISS_GERMAN_HELLO);
        final byte[] bytes = content.getBytes(StandardCharsets.ISO_8859_1);
        final BasicHttpEntity entity = new BasicHttpEntity(new ByteArrayInputStream(bytes), ContentType.TEXT_PLAIN);
        final String s = EntityUtils.toString(entity);
    }

    @Test
    public void testDefaultCharsetContentToString() throws Exception {
        final String content = constructString(RUSSIAN_HELLO);
        final byte[] bytes = content.getBytes(Charset.forName("KOI8-R"));
        final BasicHttpEntity entity = new BasicHttpEntity(new ByteArrayInputStream(bytes),
                ContentType.parse("text/plain"));
        final String s = EntityUtils.toString(entity, Charset.forName("KOI8-R"));
        Assert.assertEquals(content, s);
    }

    @Test
    public void testContentWithContentTypeToString() throws Exception {
        final String content = constructString(RUSSIAN_HELLO);
        final byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        final BasicHttpEntity entity = new BasicHttpEntity(new ByteArrayInputStream(bytes),
                ContentType.TEXT_PLAIN.withCharset(StandardCharsets.UTF_8));
        final String s = EntityUtils.toString(entity, "ISO-8859-1");
        Assert.assertEquals(content, s);
    }

    @Test
    public void testContentWithInvalidContentTypeToString() throws Exception {
        final String content = constructString(RUSSIAN_HELLO);
        final byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        final HttpEntity entity = new AbstractHttpEntity("text/plain; charset=nosuchcharset", null) {

            @Override
            public InputStream getContent() throws IOException, UnsupportedOperationException {
                return new ByteArrayInputStream(bytes);
            }

            @Override
            public boolean isStreaming() {
                return false;
            }

            @Override
            public long getContentLength() {
                return bytes.length;
            }

            @Override
            public void close() throws IOException {
            }

        };
        final String s = EntityUtils.toString(entity, "UTF-8");
        Assert.assertEquals(content, s);
    }

    private static void assertNameValuePair (
            final NameValuePair parameter,
            final String expectedName,
            final String expectedValue) {
        Assert.assertEquals(parameter.getName(), expectedName);
        Assert.assertEquals(parameter.getValue(), expectedValue);
    }

    @Test
    public void testParseEntity() throws Exception {
        final StringEntity entity1 = new StringEntity("Name1=Value1", ContentType.APPLICATION_FORM_URLENCODED);
        final List<NameValuePair> result = EntityUtils.parse(entity1);
        Assert.assertEquals(1, result.size());
        assertNameValuePair(result.get(0), "Name1", "Value1");

        final StringEntity entity2 = new StringEntity("Name1=Value1", ContentType.parse("text/test"));
        Assert.assertTrue(EntityUtils.parse(entity2).isEmpty());
    }

    @Test
    public void testParseUTF8Entity() throws Exception {
        final String ru_hello = constructString(RUSSIAN_HELLO);
        final String ch_hello = constructString(SWISS_GERMAN_HELLO);
        final List <NameValuePair> parameters = new ArrayList<>();
        parameters.add(new BasicNameValuePair("russian", ru_hello));
        parameters.add(new BasicNameValuePair("swiss", ch_hello));

        final String s = URLEncodedUtils.format(parameters, StandardCharsets.UTF_8);

        Assert.assertEquals("russian=%D0%92%D1%81%D0%B5%D0%BC_%D0%BF%D1%80%D0%B8%D0%B2%D0%B5%D1%82" +
                "&swiss=Gr%C3%BCezi_z%C3%A4m%C3%A4", s);
        final StringEntity entity = new StringEntity(s,
                ContentType.create(URLEncodedUtils.CONTENT_TYPE, StandardCharsets.UTF_8));
        final List<NameValuePair> result = EntityUtils.parse(entity);
        Assert.assertEquals(2, result.size());
        assertNameValuePair(result.get(0), "russian", ru_hello);
        assertNameValuePair(result.get(1), "swiss", ch_hello);
    }

}
