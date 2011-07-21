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
import java.io.InputStream;

import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.message.BasicHeader;
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
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testEmptyContentToByteArray() throws Exception {
        NullHttpEntity httpentity = new NullHttpEntity();
        byte[] bytes = EntityUtils.toByteArray(httpentity);
        Assert.assertNull(bytes);
    }

    @Test
    public void testMaxIntContentToByteArray() throws Exception {
        byte[] content = "Message content".getBytes("ISO-8859-1");
        BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContent(new ByteArrayInputStream(content));
        httpentity.setContentLength(Integer.MAX_VALUE + 100L);
        try {
            EntityUtils.toByteArray(httpentity);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testUnknownLengthContentToByteArray() throws Exception {
        byte[] bytes = "Message content".getBytes("ISO-8859-1");
        BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContent(new ByteArrayInputStream(bytes));
        httpentity.setContentLength(-1L);
        byte[] bytes2 = EntityUtils.toByteArray(httpentity);
        Assert.assertNotNull(bytes2);
        Assert.assertEquals(bytes.length, bytes2.length);
        for (int i = 0; i < bytes.length; i++) {
            Assert.assertEquals(bytes[i], bytes2[i]);
        }
    }

    @Test
    public void testKnownLengthContentToByteArray() throws Exception {
        byte[] bytes = "Message content".getBytes("ISO-8859-1");
        BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContent(new ByteArrayInputStream(bytes));
        httpentity.setContentLength(bytes.length);
        byte[] bytes2 = EntityUtils.toByteArray(httpentity);
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
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testEmptyContentToString() throws Exception {
        NullHttpEntity httpentity = new NullHttpEntity();
        String s = EntityUtils.toString(httpentity);
        Assert.assertNull(s);
    }

    @Test
    public void testMaxIntContentToString() throws Exception {
        byte[] content = "Message content".getBytes("ISO-8859-1");
        BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContent(new ByteArrayInputStream(content));
        httpentity.setContentLength(Integer.MAX_VALUE + 100L);
        try {
            EntityUtils.toString(httpentity);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testUnknownLengthContentToString() throws Exception {
        byte[] bytes = "Message content".getBytes("ISO-8859-1");
        BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContent(new ByteArrayInputStream(bytes));
        httpentity.setContentLength(-1L);
        String s = EntityUtils.toString(httpentity, "ISO-8859-1");
        Assert.assertEquals("Message content", s);
    }

    @Test
    public void testKnownLengthContentToString() throws Exception {
        byte[] bytes = "Message content".getBytes("ISO-8859-1");
        BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContent(new ByteArrayInputStream(bytes));
        httpentity.setContentLength(bytes.length);
        String s = EntityUtils.toString(httpentity, "ISO-8859-1");
        Assert.assertEquals("Message content", s);
    }

    static final int SWISS_GERMAN_HELLO [] = {
        0x47, 0x72, 0xFC, 0x65, 0x7A, 0x69, 0x5F, 0x7A, 0xE4, 0x6D, 0xE4
    };

    static final int RUSSIAN_HELLO [] = {
        0x412, 0x441, 0x435, 0x43C, 0x5F, 0x43F, 0x440, 0x438,
        0x432, 0x435, 0x442
    };

    private static String constructString(int [] unicodeChars) {
        StringBuilder buffer = new StringBuilder();
        if (unicodeChars != null) {
            for (int i = 0; i < unicodeChars.length; i++) {
                buffer.append((char)unicodeChars[i]);
            }
        }
        return buffer.toString();
    }

    @Test
    public void testNoCharsetContentToString() throws Exception {
        String content = constructString(SWISS_GERMAN_HELLO);
        byte[] bytes = content.getBytes("ISO-8859-1");
        BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContent(new ByteArrayInputStream(bytes));
        httpentity.setContentType(new BasicHeader("Content-Type", "text/plain"));
        String s = EntityUtils.toString(httpentity);
        Assert.assertEquals(content, s);
    }

    @Test
    public void testDefaultCharsetContentToString() throws Exception {
        String content = constructString(RUSSIAN_HELLO);
        byte[] bytes = content.getBytes("KOI8-R");
        BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContent(new ByteArrayInputStream(bytes));
        httpentity.setContentType(new BasicHeader("Content-Type", "text/plain"));
        String s = EntityUtils.toString(httpentity, "KOI8-R");
        Assert.assertEquals(content, s);
    }

    @Test
    public void testContentWithContentTypeToString() throws Exception {
        String content = constructString(RUSSIAN_HELLO);
        byte[] bytes = content.getBytes("UTF-8");
        BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContent(new ByteArrayInputStream(bytes));
        httpentity.setContentType(new BasicHeader("Content-Type", "text/plain; charset=UTF-8"));
        String s = EntityUtils.toString(httpentity, "ISO-8859-1");
        Assert.assertEquals(content, s);
    }

    /**
     * Helper class that returns <code>null</code> as the content.
     */
    public static class NullHttpEntity extends BasicHttpEntity {

        // default constructor
        /**
         * Obtains no content.
         * This method disables the state checks in the base class.
         *
         * @return <code>null</code>
         */
        @Override
        public InputStream getContent() {
            return null;
        }
    } // class NullEntity

} // class TestEntityUtils
