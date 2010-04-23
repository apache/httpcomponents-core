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

package org.apache.http;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import junit.framework.TestCase;

/**
 * Test cases for HTTP version class
 *
 */
public class TestHttpVersion extends TestCase {

    // ------------------------------------------------------------ Constructor

    public TestHttpVersion(String name) {
        super(name);
    }

    // ------------------------------------------------------------------ Tests

    public void testHttpVersionInvalidConstructorInput() throws Exception {
        try {
            new HttpVersion(-1, -1);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
        try {
            new HttpVersion(0, -1);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    public void testHttpVersionEquality() throws Exception {
        HttpVersion ver1 = new HttpVersion(1, 1);
        HttpVersion ver2 = new HttpVersion(1, 1);

        assertEquals(ver1.hashCode(), ver2.hashCode());
        assertTrue(ver1.equals(ver1));
        assertTrue(ver1.equals(ver2));
        assertTrue(ver1.equals(ver1));
        assertTrue(ver1.equals(ver2));

        assertFalse(ver1.equals(new Float(1.1)));

        assertTrue((new HttpVersion(0, 9)).equals(HttpVersion.HTTP_0_9));
        assertTrue((new HttpVersion(1, 0)).equals(HttpVersion.HTTP_1_0));
        assertTrue((new HttpVersion(1, 1)).equals(HttpVersion.HTTP_1_1));
        assertFalse((new HttpVersion(1, 1)).equals(HttpVersion.HTTP_1_0));

        assertTrue
            ((new ProtocolVersion("HTTP", 0, 9)).equals(HttpVersion.HTTP_0_9));
        assertTrue
            ((new ProtocolVersion("HTTP", 1, 0)).equals(HttpVersion.HTTP_1_0));
        assertTrue
            ((new ProtocolVersion("HTTP", 1, 1)).equals(HttpVersion.HTTP_1_1));
        assertFalse
            ((new ProtocolVersion("http", 1, 1)).equals(HttpVersion.HTTP_1_1));

        assertTrue
            (HttpVersion.HTTP_0_9.equals(new ProtocolVersion("HTTP", 0, 9)));
        assertTrue
            (HttpVersion.HTTP_1_0.equals(new ProtocolVersion("HTTP", 1, 0)));
        assertTrue
            (HttpVersion.HTTP_1_1.equals(new ProtocolVersion("HTTP", 1, 1)));
        assertFalse
            (HttpVersion.HTTP_1_1.equals(new ProtocolVersion("http", 1, 1)));
    }

    public void testHttpVersionComparison() {
        assertTrue(HttpVersion.HTTP_0_9.lessEquals(HttpVersion.HTTP_1_1));
        assertTrue(HttpVersion.HTTP_0_9.greaterEquals(HttpVersion.HTTP_0_9));
        assertFalse(HttpVersion.HTTP_0_9.greaterEquals(HttpVersion.HTTP_1_0));

        assertTrue(HttpVersion.HTTP_1_0.compareToVersion(HttpVersion.HTTP_1_1) < 0);
        assertTrue(HttpVersion.HTTP_1_0.compareToVersion(HttpVersion.HTTP_0_9) > 0);
        assertTrue(HttpVersion.HTTP_1_0.compareToVersion(HttpVersion.HTTP_1_0) == 0);
   }

    public void testCloning() throws Exception {
        HttpVersion orig = HttpVersion.HTTP_1_1;
        HttpVersion clone = (HttpVersion) orig.clone();
        assertEquals(orig, clone);
    }

    public void testSerialization() throws Exception {
        HttpVersion orig = HttpVersion.HTTP_1_1;
        ByteArrayOutputStream outbuffer = new ByteArrayOutputStream();
        ObjectOutputStream outstream = new ObjectOutputStream(outbuffer);
        outstream.writeObject(orig);
        outstream.close();
        byte[] raw = outbuffer.toByteArray();
        ByteArrayInputStream inbuffer = new ByteArrayInputStream(raw);
        ObjectInputStream instream = new ObjectInputStream(inbuffer);
        HttpVersion clone = (HttpVersion) instream.readObject();
        assertEquals(orig, clone);
    }

}

