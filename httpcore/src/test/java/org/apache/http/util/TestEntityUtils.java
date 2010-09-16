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

import junit.framework.TestCase;

import org.apache.http.Header;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.message.BasicHeader;

/**
 * Unit tests for {@link EntityUtils}.
 *
 */
public class TestEntityUtils extends TestCase {

    public TestEntityUtils(String testName) {
        super(testName);
    }

    public void testNullEntityToByteArray() throws Exception {
        try {
            EntityUtils.toByteArray(null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    public void testEmptyContentToByteArray() throws Exception {
        NullHttpEntity httpentity = new NullHttpEntity();
        byte[] bytes = EntityUtils.toByteArray(httpentity);
        assertNull(bytes);
    }

    public void testMaxIntContentToByteArray() throws Exception {
        byte[] content = "Message content".getBytes("ISO-8859-1");
        BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContent(new ByteArrayInputStream(content));
        httpentity.setContentLength(Integer.MAX_VALUE + 100L);
        try {
            EntityUtils.toByteArray(httpentity);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    public void testUnknownLengthContentToByteArray() throws Exception {
        byte[] bytes = "Message content".getBytes("ISO-8859-1");
        BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContent(new ByteArrayInputStream(bytes));
        httpentity.setContentLength(-1L);
        byte[] bytes2 = EntityUtils.toByteArray(httpentity);
        assertNotNull(bytes2);
        assertEquals(bytes.length, bytes2.length);
        for (int i = 0; i < bytes.length; i++) {
            assertEquals(bytes[i], bytes2[i]);
        }
    }

    public void testKnownLengthContentToByteArray() throws Exception {
        byte[] bytes = "Message content".getBytes("ISO-8859-1");
        BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContent(new ByteArrayInputStream(bytes));
        httpentity.setContentLength(bytes.length);
        byte[] bytes2 = EntityUtils.toByteArray(httpentity);
        assertNotNull(bytes2);
        assertEquals(bytes.length, bytes2.length);
        for (int i = 0; i < bytes.length; i++) {
            assertEquals(bytes[i], bytes2[i]);
        }
    }

    public void testNullEntityGetContentCharset() throws Exception {
        try {
            EntityUtils.getContentCharSet(null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    public void testNullContentTypeGetContentCharset() throws Exception {
        BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContentType((Header)null);
        assertNull(EntityUtils.getContentCharSet(httpentity));
    }

    public void testNoCharsetGetContentCharset() throws Exception {
        BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContentType(new BasicHeader("Content-Type", "text/plain; param=yadayada"));
        assertNull(EntityUtils.getContentCharSet(httpentity));
    }

    public void testGetContentCharset() throws Exception {
        BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContentType(new BasicHeader("Content-Type", "text/plain; charset = UTF-8"));
        assertEquals("UTF-8", EntityUtils.getContentCharSet(httpentity));
    }

    public void testGetContentMimeTypeWithCharset() throws Exception {
        BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContentType(new BasicHeader("Content-Type", "text/plain; " +
                "whatever; charset = UTF-8"));
        assertEquals("text/plain", EntityUtils.getContentMimeType(httpentity));
    }

    public void testGetContentMimeTypeWithoutCharset() throws Exception {
        BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContentType(new BasicHeader("Content-Type", "text/whatever"));
        assertEquals("text/whatever", EntityUtils.getContentMimeType(httpentity));
    }

    public void testNullEntityGetMimeType() throws Exception {
        try {
            EntityUtils.getContentMimeType(null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    public void testNullEntityToString() throws Exception {
        try {
            EntityUtils.toString(null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    public void testEmptyContentToString() throws Exception {
        NullHttpEntity httpentity = new NullHttpEntity();
        String s = EntityUtils.toString(httpentity);
        assertNull(s);
    }

    public void testMaxIntContentToString() throws Exception {
        byte[] content = "Message content".getBytes("ISO-8859-1");
        BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContent(new ByteArrayInputStream(content));
        httpentity.setContentLength(Integer.MAX_VALUE + 100L);
        try {
            EntityUtils.toString(httpentity);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    public void testUnknownLengthContentToString() throws Exception {
        byte[] bytes = "Message content".getBytes("ISO-8859-1");
        BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContent(new ByteArrayInputStream(bytes));
        httpentity.setContentLength(-1L);
        String s = EntityUtils.toString(httpentity, "ISO-8859-1");
        assertEquals("Message content", s);
    }

    public void testKnownLengthContentToString() throws Exception {
        byte[] bytes = "Message content".getBytes("ISO-8859-1");
        BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContent(new ByteArrayInputStream(bytes));
        httpentity.setContentLength(bytes.length);
        String s = EntityUtils.toString(httpentity, "ISO-8859-1");
        assertEquals("Message content", s);
    }

    static final int SWISS_GERMAN_HELLO [] = {
        0x47, 0x72, 0xFC, 0x65, 0x7A, 0x69, 0x5F, 0x7A, 0xE4, 0x6D, 0xE4
    };

    static final int RUSSIAN_HELLO [] = {
        0x412, 0x441, 0x435, 0x43C, 0x5F, 0x43F, 0x440, 0x438,
        0x432, 0x435, 0x442
    };

    private static String constructString(int [] unicodeChars) {
        StringBuffer buffer = new StringBuffer();
        if (unicodeChars != null) {
            for (int i = 0; i < unicodeChars.length; i++) {
                buffer.append((char)unicodeChars[i]);
            }
        }
        return buffer.toString();
    }

    public void testNoCharsetContentToString() throws Exception {
        String content = constructString(SWISS_GERMAN_HELLO);
        byte[] bytes = content.getBytes("ISO-8859-1");
        BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContent(new ByteArrayInputStream(bytes));
        httpentity.setContentType(new BasicHeader("Content-Type", "text/plain"));
        String s = EntityUtils.toString(httpentity);
        assertEquals(content, s);
    }

    public void testDefaultCharsetContentToString() throws Exception {
        String content = constructString(RUSSIAN_HELLO);
        byte[] bytes = content.getBytes("KOI8-R");
        BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContent(new ByteArrayInputStream(bytes));
        httpentity.setContentType(new BasicHeader("Content-Type", "text/plain"));
        String s = EntityUtils.toString(httpentity, "KOI8-R");
        assertEquals(content, s);
    }

    public void testContentWithContentTypeToString() throws Exception {
        String content = constructString(RUSSIAN_HELLO);
        byte[] bytes = content.getBytes("UTF-8");
        BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContent(new ByteArrayInputStream(bytes));
        httpentity.setContentType(new BasicHeader("Content-Type", "text/plain; charset=UTF-8"));
        String s = EntityUtils.toString(httpentity, "ISO-8859-1");
        assertEquals(content, s);
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
        public InputStream getContent() {
            return null;
        }
    } // class NullEntity

} // class TestEntityUtils
