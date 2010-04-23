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
 * Unit tests for {@link HttpHost}.
 *
 */
public class TestHttpHost extends TestCase {

    public TestHttpHost(String testName) {
        super(testName);
    }

    public void testConstructor() {
        HttpHost host1 = new HttpHost("somehost");
        assertEquals("somehost", host1.getHostName());
        assertEquals(-1, host1.getPort());
        assertEquals("http", host1.getSchemeName());
        HttpHost host2 = new HttpHost("somehost", 8080);
        assertEquals("somehost", host2.getHostName());
        assertEquals(8080, host2.getPort());
        assertEquals("http", host2.getSchemeName());
        HttpHost host3 = new HttpHost("somehost", -1);
        assertEquals("somehost", host3.getHostName());
        assertEquals(-1, host3.getPort());
        assertEquals("http", host3.getSchemeName());
        HttpHost host4 = new HttpHost("somehost", 443, "https");
        assertEquals("somehost", host4.getHostName());
        assertEquals(443, host4.getPort());
        assertEquals("https", host4.getSchemeName());
        try {
            new HttpHost(null, -1, null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            //expected
        }
    }

    public void testHashCode() {
        HttpHost host1 = new HttpHost("somehost", 8080, "http");
        HttpHost host2 = new HttpHost("somehost", 80, "http");
        HttpHost host3 = new HttpHost("someotherhost", 8080, "http");
        HttpHost host4 = new HttpHost("somehost", 80, "http");
        HttpHost host5 = new HttpHost("SomeHost", 80, "http");
        HttpHost host6 = new HttpHost("SomeHost", 80, "myhttp");

        assertTrue(host1.hashCode() == host1.hashCode());
        assertTrue(host1.hashCode() != host2.hashCode());
        assertTrue(host1.hashCode() != host3.hashCode());
        assertTrue(host2.hashCode() == host4.hashCode());
        assertTrue(host2.hashCode() == host5.hashCode());
        assertTrue(host5.hashCode() != host6.hashCode());
    }

    public void testEquals() {
        HttpHost host1 = new HttpHost("somehost", 8080, "http");
        HttpHost host2 = new HttpHost("somehost", 80, "http");
        HttpHost host3 = new HttpHost("someotherhost", 8080, "http");
        HttpHost host4 = new HttpHost("somehost", 80, "http");
        HttpHost host5 = new HttpHost("SomeHost", 80, "http");
        HttpHost host6 = new HttpHost("SomeHost", 80, "myhttp");

        assertTrue(host1.equals(host1));
        assertFalse(host1.equals(host2));
        assertFalse(host1.equals(host3));
        assertTrue(host2.equals(host4));
        assertTrue(host2.equals(host5));
        assertFalse(host5.equals(host6));
        assertFalse(host1.equals(null));
        assertFalse(host1.equals("http://somehost"));
    }

    public void testToString() {
        HttpHost host1 = new HttpHost("somehost");
        assertEquals("http://somehost", host1.toString());
        HttpHost host2 = new HttpHost("somehost", -1);
        assertEquals("http://somehost", host2.toString());
        HttpHost host3 = new HttpHost("somehost", -1);
        assertEquals("http://somehost", host3.toString());
        HttpHost host4 = new HttpHost("somehost", 8888);
        assertEquals("http://somehost:8888", host4.toString());
        HttpHost host5 = new HttpHost("somehost", -1, "myhttp");
        assertEquals("myhttp://somehost", host5.toString());
        HttpHost host6 = new HttpHost("somehost", 80, "myhttp");
        assertEquals("myhttp://somehost:80", host6.toString());
    }

    public void testToHostString() {
        HttpHost host1 = new HttpHost("somehost");
        assertEquals("somehost", host1.toHostString());
        HttpHost host2 = new HttpHost("somehost");
        assertEquals("somehost", host2.toHostString());
        HttpHost host3 = new HttpHost("somehost", -1);
        assertEquals("somehost", host3.toHostString());
        HttpHost host4 = new HttpHost("somehost", 8888);
        assertEquals("somehost:8888", host4.toHostString());
    }

    public void testCloning() throws Exception {
        HttpHost orig = new HttpHost("somehost", 8080, "https");
        HttpHost clone = (HttpHost) orig.clone();
        assertEquals(orig, clone);
    }

    public void testSerialization() throws Exception {
        HttpHost orig = new HttpHost("somehost", 8080, "https");
        ByteArrayOutputStream outbuffer = new ByteArrayOutputStream();
        ObjectOutputStream outstream = new ObjectOutputStream(outbuffer);
        outstream.writeObject(orig);
        outstream.close();
        byte[] raw = outbuffer.toByteArray();
        ByteArrayInputStream inbuffer = new ByteArrayInputStream(raw);
        ObjectInputStream instream = new ObjectInputStream(inbuffer);
        HttpHost clone = (HttpHost) instream.readObject();
        assertEquals(orig, clone);
    }

}
