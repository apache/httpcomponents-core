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

package org.apache.http.entity;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import junit.framework.TestCase;

import org.apache.http.protocol.HTTP;

/**
 * Unit tests for {@link InputStreamEntity}.
 *
 */
public class TestInputStreamEntity extends TestCase {

    public TestInputStreamEntity(String testName) {
        super(testName);
    }

    public void testBasics() throws Exception {
        byte[] bytes = "Message content".getBytes(HTTP.ISO_8859_1);
        InputStream instream = new ByteArrayInputStream(bytes);
        InputStreamEntity httpentity = new InputStreamEntity(instream, bytes.length);

        assertEquals(bytes.length, httpentity.getContentLength());
        assertEquals(instream, httpentity.getContent());
        assertNotNull(httpentity.getContent());
        assertFalse(httpentity.isRepeatable());
        assertTrue(httpentity.isStreaming());
    }

    public void testIllegalConstructor() throws Exception {
        try {
            new InputStreamEntity(null, 0);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    public void testWriteTo() throws Exception {
        byte[] bytes = "Message content".getBytes(HTTP.ISO_8859_1);
        InputStream instream = new ByteArrayInputStream(bytes);
        InputStreamEntity httpentity = new InputStreamEntity(instream, 7);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        httpentity.writeTo(out);
        byte[] bytes2 = out.toByteArray();
        assertNotNull(bytes2);
        assertEquals(7, bytes2.length);
        String s = new String(bytes2, HTTP.ISO_8859_1);
        assertEquals("Message", s);

        instream = new ByteArrayInputStream(bytes);
        httpentity = new InputStreamEntity(instream, 20);
        out = new ByteArrayOutputStream();
        httpentity.writeTo(out);
        bytes2 = out.toByteArray();
        assertNotNull(bytes2);
        assertEquals(bytes.length, bytes2.length);

        instream = new ByteArrayInputStream(bytes);
        httpentity = new InputStreamEntity(instream, -1);
        out = new ByteArrayOutputStream();
        httpentity.writeTo(out);
        bytes2 = out.toByteArray();
        assertNotNull(bytes2);
        assertEquals(bytes.length, bytes2.length);

        try {
            httpentity.writeTo(null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

}
