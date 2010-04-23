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

import junit.framework.TestCase;

/**
 * Unit tests for {@link TestEncodingUtils}.
 *
 */
public class TestEncodingUtils extends TestCase {

    public TestEncodingUtils(String testName) {
        super(testName);
    }

    private static String constructString(int [] unicodeChars) {
        StringBuffer buffer = new StringBuffer();
        if (unicodeChars != null) {
            for (int i = 0; i < unicodeChars.length; i++) {
                buffer.append((char)unicodeChars[i]);
            }
        }
        return buffer.toString();
    }

    static final int SWISS_GERMAN_HELLO [] = {
            0x47, 0x72, 0xFC, 0x65, 0x7A, 0x69, 0x5F, 0x7A, 0xE4, 0x6D, 0xE4
        };

    public void testBytesToString() throws Exception {
        String s = constructString(SWISS_GERMAN_HELLO);
        byte[] utf = s.getBytes("UTF-8");
        byte[] latin1 = s.getBytes("ISO-8859-1");

        String s1 = EncodingUtils.getString(utf, "UTF-8");
        String s2 = EncodingUtils.getString(latin1, "ISO-8859-1");

        assertEquals(s, s1);
        assertEquals(s, s2);

        try {
            EncodingUtils.getString(null, 0, 0, "UTF-8");
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            EncodingUtils.getString(null, "UTF-8");
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            EncodingUtils.getString(new byte[] {}, null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            EncodingUtils.getString(new byte[] {}, "");
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    public void testStringToBytesToString() throws Exception {
        String s = constructString(SWISS_GERMAN_HELLO);
        byte[] utf = s.getBytes("UTF-8");
        byte[] latin1 = s.getBytes("ISO-8859-1");

        byte[] data1 = EncodingUtils.getBytes(s, "UTF-8");
        byte[] data2 = EncodingUtils.getBytes(s, "ISO-8859-1");

        assertNotNull(data1);
        assertEquals(utf.length, data1.length);
        for (int i = 0; i < utf.length; i++) {
            assertEquals(utf[i], data1[i]);
        }
        assertNotNull(data2);
        assertEquals(latin1.length, data2.length);
        for (int i = 0; i < latin1.length; i++) {
            assertEquals(latin1[i], data2[i]);
        }

        try {
            EncodingUtils.getBytes(null, "UTF-8");
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            EncodingUtils.getBytes("what not", null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            EncodingUtils.getBytes("what not", "");
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    public void testAsciiBytesToString() throws Exception {
        String s = "ascii only, I mean it!";
        assertEquals(s, EncodingUtils.getAsciiString(s.getBytes("US-ASCII")));
        try {
            EncodingUtils.getAsciiString(null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            EncodingUtils.getAsciiString(null, 0, 0);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    public void testAsciiStringToBytes() throws Exception {
        String s = "ascii only, I mean it!";
        byte[] ascii = s.getBytes("US-ASCII");
        byte[] data = EncodingUtils.getAsciiBytes(s);

        assertNotNull(data);
        assertEquals(ascii.length, data.length);
        for (int i = 0; i < ascii.length; i++) {
            assertEquals(ascii[i], data[i]);
        }
        try {
            EncodingUtils.getAsciiBytes(null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    public void testUnsupportedEncoding() {
        String s = constructString(SWISS_GERMAN_HELLO);
        byte[] b1 = s.getBytes();
        byte[] b2 = EncodingUtils.getBytes(s, "ThisJustAintRight");
        assertEquals(b1.length, b2.length);
        for (int i = 0; i < b1.length; i++) {
            assertEquals(b1[i], b2[i]);
        }
        String s1 = new String(b1);
        String s2 = EncodingUtils.getString(b1, "ThisJustAintRight");
        assertEquals(s1, s2);
    }

}
