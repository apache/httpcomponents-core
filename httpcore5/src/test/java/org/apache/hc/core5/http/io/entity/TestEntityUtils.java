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
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.List;

import org.apache.hc.core5.http.ContentType;
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

    @Test
    public void testNullEntityToByteArray() throws Exception {
        try {
            EntityUtils.toByteArray(null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testEmptyContentToByteArray() throws Exception {
        final NullHttpEntity httpentity = new NullHttpEntity();
        final byte[] bytes = EntityUtils.toByteArray(httpentity);
        Assert.assertNull(bytes);
    }

    @Test
    public void testMaxIntContentToByteArray() throws Exception {
        final byte[] content = "Message content".getBytes(StandardCharsets.ISO_8859_1);
        final BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContent(new ByteArrayInputStream(content));
        httpentity.setContentLength(Integer.MAX_VALUE + 100L);
        try {
            EntityUtils.toByteArray(httpentity);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testUnknownLengthContentToByteArray() throws Exception {
        final byte[] bytes = "Message content".getBytes(StandardCharsets.ISO_8859_1);
        final BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContent(new ByteArrayInputStream(bytes));
        httpentity.setContentLength(-1L);
        final byte[] bytes2 = EntityUtils.toByteArray(httpentity);
        Assert.assertNotNull(bytes2);
        Assert.assertEquals(bytes.length, bytes2.length);
        for (int i = 0; i < bytes.length; i++) {
            Assert.assertEquals(bytes[i], bytes2[i]);
        }
    }

    @Test
    public void testKnownLengthContentToByteArray() throws Exception {
        final byte[] bytes = "Message content".getBytes(StandardCharsets.ISO_8859_1);
        final BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContent(new ByteArrayInputStream(bytes));
        httpentity.setContentLength(bytes.length);
        final byte[] bytes2 = EntityUtils.toByteArray(httpentity);
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

    @Test
    public void testEmptyContentToString() throws Exception {
        final NullHttpEntity httpentity = new NullHttpEntity();
        final String s = EntityUtils.toString(httpentity);
        Assert.assertNull(s);
    }

    @Test
    public void testMaxIntContentToString() throws Exception {
        final byte[] content = "Message content".getBytes(StandardCharsets.ISO_8859_1);
        final BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContent(new ByteArrayInputStream(content));
        httpentity.setContentLength(Integer.MAX_VALUE + 100L);
        try {
            EntityUtils.toString(httpentity);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testUnknownLengthContentToString() throws Exception {
        final byte[] bytes = "Message content".getBytes(StandardCharsets.ISO_8859_1);
        final BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContent(new ByteArrayInputStream(bytes));
        httpentity.setContentLength(-1L);
        final String s = EntityUtils.toString(httpentity, "ISO-8859-1");
        Assert.assertEquals("Message content", s);
    }

    @Test
    public void testKnownLengthContentToString() throws Exception {
        final byte[] bytes = "Message content".getBytes(StandardCharsets.ISO_8859_1);
        final BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContent(new ByteArrayInputStream(bytes));
        httpentity.setContentLength(bytes.length);
        final String s = EntityUtils.toString(httpentity, "ISO-8859-1");
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
        final BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContent(new ByteArrayInputStream(bytes));
        httpentity.setContentType("text/plain");
        final String s = EntityUtils.toString(httpentity);
        Assert.assertEquals(content, s);
    }

    @Test
    public void testDefaultCharsetContentToString() throws Exception {
        final String content = constructString(RUSSIAN_HELLO);
        final byte[] bytes = content.getBytes(Charset.forName("KOI8-R"));
        final BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContent(new ByteArrayInputStream(bytes));
        httpentity.setContentType("text/plain");
        final String s = EntityUtils.toString(httpentity, "KOI8-R");
        Assert.assertEquals(content, s);
    }

    @Test
    public void testContentWithContentTypeToString() throws Exception {
        final String content = constructString(RUSSIAN_HELLO);
        final byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        final BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContent(new ByteArrayInputStream(bytes));
        httpentity.setContentType("text/plain; charset=UTF-8");
        final String s = EntityUtils.toString(httpentity, "ISO-8859-1");
        Assert.assertEquals(content, s);
    }
    @Test
    public void testContentWithInvalidContentTypeToString() throws Exception {
        final String content = constructString(RUSSIAN_HELLO);
        final byte[] bytes = content.getBytes("UTF-8");
        final BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContent(new ByteArrayInputStream(bytes));
        httpentity.setContentType("text/plain; charset=nosuchcharset");
        final String s = EntityUtils.toString(httpentity, "UTF-8");
        Assert.assertEquals(content, s);
    }

    @Test
    public void testExtractNullInput() throws Exception {
        Assert.assertNull(EntityUtils.getContentType(null));
    }

    @Test
    public void testExtractNullContentType() throws Exception {
        final BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContentType(null);
        Assert.assertNull(EntityUtils.getContentType(httpentity));
    }

    @Test
    public void testExtract() throws Exception {
        final BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContentType("text/plain; charset = UTF-8");
        final ContentType contentType = EntityUtils.getContentType(httpentity);
        Assert.assertNotNull(contentType);
        Assert.assertEquals("text/plain", contentType.getMimeType());
        Assert.assertEquals(StandardCharsets.UTF_8, contentType.getCharset());
    }

    @Test
    public void testExtractNoCharset() throws Exception {
        final BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContentType("text/plain; param=yadayada");
        final ContentType contentType = EntityUtils.getContentType(httpentity);
        Assert.assertNotNull(contentType);
        Assert.assertEquals("text/plain", contentType.getMimeType());
        Assert.assertNull(contentType.getCharset());
    }

    @Test(expected = UnsupportedCharsetException.class)
    public void testExtractInvalidCharset() throws Exception {
        final BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContentType("text/plain; charset = stuff");
        EntityUtils.getContentType(httpentity);
    }

    @Test
    public void testExtracLenienttNullInput() throws Exception {
        Assert.assertNull(EntityUtils.getContentTypeLenient(null));
    }

    @Test
    public void testExtractLenientNullContentType() throws Exception {
        final BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContentType(null);
        Assert.assertNull(EntityUtils.getContentTypeLenient(httpentity));
    }

    @Test
    public void testLenientExtractInvalidCharset() throws Exception {
        final BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContentType("text/plain; charset = stuff");
        final ContentType contentType = EntityUtils.getContentTypeLenient(httpentity);
        Assert.assertNotNull(contentType);
        Assert.assertEquals("text/plain", contentType.getMimeType());
        Assert.assertEquals(null, contentType.getCharset());
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
        final StringEntity entity = new StringEntity("Name1=Value1");

        entity.setContentType(URLEncodedUtils.CONTENT_TYPE);
        final List<NameValuePair> result = EntityUtils.parse(entity);
        Assert.assertEquals(1, result.size());
        assertNameValuePair(result.get(0), "Name1", "Value1");

        entity.setContentType("text/test");
        Assert.assertTrue(EntityUtils.parse(entity).isEmpty());
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

        final StringEntity entity = new StringEntity(s, ContentType.create(
                URLEncodedUtils.CONTENT_TYPE, StandardCharsets.UTF_8));
        final List <NameValuePair> result = EntityUtils.parse(entity);
        Assert.assertEquals(2, result.size());
        assertNameValuePair(result.get(0), "russian", ru_hello);
        assertNameValuePair(result.get(1), "swiss", ch_hello);
    }

    /**
     * Helper class that returns {@code null} as the content.
     */
    public static class NullHttpEntity extends BasicHttpEntity {

        // default constructor
        /**
         * Obtains no content.
         * This method disables the state checks in the base class.
         *
         * @return {@code null}
         */
        @Override
        public InputStream getContent() {
            return null;
        }
    }

}
