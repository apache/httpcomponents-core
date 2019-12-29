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

package org.apache.hc.core5.http;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link HttpHost}.
 *
 */
public class TestHttpHost {

    @Test
    public void testConstructor() {
        final HttpHost host1 = new HttpHost("somehost");
        Assert.assertEquals("somehost", host1.getHostName());
        Assert.assertEquals(-1, host1.getPort());
        Assert.assertEquals("http", host1.getSchemeName());
        final HttpHost host2 = new HttpHost("somehost", 8080);
        Assert.assertEquals("somehost", host2.getHostName());
        Assert.assertEquals(8080, host2.getPort());
        Assert.assertEquals("http", host2.getSchemeName());
        final HttpHost host3 = new HttpHost("somehost", -1);
        Assert.assertEquals("somehost", host3.getHostName());
        Assert.assertEquals(-1, host3.getPort());
        Assert.assertEquals("http", host3.getSchemeName());
        final HttpHost host4 = new HttpHost("https", "somehost", 443);
        Assert.assertEquals("somehost", host4.getHostName());
        Assert.assertEquals(443, host4.getPort());
        Assert.assertEquals("https", host4.getSchemeName());
        final HttpHost host5 = new HttpHost("https", "somehost");
        Assert.assertEquals("somehost", host5.getHostName());
        Assert.assertEquals(-1, host5.getPort());
        Assert.assertEquals("https", host5.getSchemeName());
        try {
            new HttpHost(null, (String) null, -1);
            Assert.fail("NullPointerException should have been thrown");
        } catch (final NullPointerException expected) {
        }
        try {
            new HttpHost(null, "   ", -1);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException expected) {
        }
        try {
            new HttpHost(null, (InetAddress) null, -1);
            Assert.fail("NullPointerException should have been thrown");
        } catch (final NullPointerException expected) {
        }
    }

    @Test
    public void testHashCode() throws Exception {
        final HttpHost host1 = new HttpHost("http", "somehost", 8080);
        final HttpHost host2 = new HttpHost("http", "somehost", 80);
        final HttpHost host3 = new HttpHost("http", "someotherhost", 8080);
        final HttpHost host4 = new HttpHost("http", "somehost", 80);
        final HttpHost host5 = new HttpHost("http", "SomeHost", 80);
        final HttpHost host6 = new HttpHost("myhttp", "SomeHost", 80);
        final HttpHost host7 = new HttpHost(
                "http", InetAddress.getByAddress("127.0.0.1", new byte[] {127,0,0,1}), 80);
        final HttpHost host8 = new HttpHost("http", "127.0.0.1", 80);
        final HttpHost host9 = new HttpHost(
                        "http", InetAddress.getByAddress("somehost",new byte[] {127,0,0,1}), 80);
        final HttpHost host10 = new HttpHost(
                        "http", InetAddress.getByAddress(new byte[] {127,0,0,1}), "somehost", 80);
        final HttpHost host11 = new HttpHost(
                        "http", InetAddress.getByAddress("someotherhost",new byte[] {127,0,0,1}), 80);

        Assert.assertTrue(host1.hashCode() == host1.hashCode());
        Assert.assertTrue(host1.hashCode() != host2.hashCode());
        Assert.assertTrue(host1.hashCode() != host3.hashCode());
        Assert.assertTrue(host2.hashCode() == host4.hashCode());
        Assert.assertTrue(host2.hashCode() == host5.hashCode());
        Assert.assertTrue(host5.hashCode() != host6.hashCode());
        Assert.assertTrue(host7.hashCode() != host8.hashCode());
        Assert.assertTrue(host8.hashCode() != host9.hashCode());
        Assert.assertTrue(host9.hashCode() == host10.hashCode());
        Assert.assertTrue(host10.hashCode() != host11.hashCode());
        Assert.assertTrue(host9.hashCode() != host11.hashCode());
    }

    @Test
    public void testEquals() throws Exception {
        final HttpHost host1 = new HttpHost("http", "somehost", 8080);
        final HttpHost host2 = new HttpHost("http", "somehost", 80);
        final HttpHost host3 = new HttpHost("http", "someotherhost", 8080);
        final HttpHost host4 = new HttpHost("http", "somehost", 80);
        final HttpHost host5 = new HttpHost("http", "SomeHost", 80);
        final HttpHost host6 = new HttpHost("myhttp", "SomeHost", 80);
        final HttpHost host7 = new HttpHost(
                "http", InetAddress.getByAddress("127.0.0.1", new byte[] {127,0,0,1}), 80);
        final HttpHost host8 = new HttpHost("http", "127.0.0.1", 80);
        final HttpHost host9 = new HttpHost(
                        "http", InetAddress.getByAddress("somehost", new byte[] {127,0,0,1}), 80);
        final HttpHost host10 = new HttpHost(
                        "http", InetAddress.getByAddress(new byte[] {127,0,0,1}), "somehost", 80);
        final HttpHost host11 = new HttpHost(
                        "http", InetAddress.getByAddress("someotherhost",new byte[] {127,0,0,1}), 80);

        Assert.assertTrue(host1.equals(host1));
        Assert.assertFalse(host1.equals(host2));
        Assert.assertFalse(host1.equals(host3));
        Assert.assertTrue(host2.equals(host4));
        Assert.assertTrue(host2.equals(host5));
        Assert.assertFalse(host5.equals(host6));
        Assert.assertFalse(host7.equals(host8));
        Assert.assertTrue(!host7.equals(host9));
        Assert.assertFalse(host1.equals(null));
        Assert.assertFalse(host1.equals("http://somehost"));
        Assert.assertFalse(host9.equals("http://somehost"));
        Assert.assertFalse(host8.equals(host9));
        Assert.assertTrue(host9.equals(host10));
        Assert.assertFalse(host9.equals(host11));
    }

    @Test
    public void testToString() throws Exception {
        final HttpHost host1 = new HttpHost("somehost");
        Assert.assertEquals("http://somehost", host1.toString());
        final HttpHost host2 = new HttpHost("somehost", -1);
        Assert.assertEquals("http://somehost", host2.toString());
        final HttpHost host3 = new HttpHost("somehost", -1);
        Assert.assertEquals("http://somehost", host3.toString());
        final HttpHost host4 = new HttpHost("somehost", 8888);
        Assert.assertEquals("http://somehost:8888", host4.toString());
        final HttpHost host5 = new HttpHost("myhttp", "somehost", -1);
        Assert.assertEquals("myhttp://somehost", host5.toString());
        final HttpHost host6 = new HttpHost("myhttp", "somehost", 80);
        Assert.assertEquals("myhttp://somehost:80", host6.toString());
        final HttpHost host7 = new HttpHost(
                "http", InetAddress.getByAddress("127.0.0.1", new byte[] {127,0,0,1}), 80);
        Assert.assertEquals("http://127.0.0.1:80", host7.toString());
        final HttpHost host9 = new HttpHost(
                        "http", InetAddress.getByAddress("somehost", new byte[] {127,0,0,1}), 80);
        Assert.assertEquals("http://somehost:80", host9.toString());
    }

    @Test
    public void testToHostString() {
        final HttpHost host1 = new HttpHost("somehost");
        Assert.assertEquals("somehost", host1.toHostString());
        final HttpHost host2 = new HttpHost("somehost");
        Assert.assertEquals("somehost", host2.toHostString());
        final HttpHost host3 = new HttpHost("somehost", -1);
        Assert.assertEquals("somehost", host3.toHostString());
        final HttpHost host4 = new HttpHost("somehost", 8888);
        Assert.assertEquals("somehost:8888", host4.toHostString());
    }

    @Test
    public void testSerialization() throws Exception {
        final HttpHost orig = new HttpHost("https", "somehost", 8080);
        final ByteArrayOutputStream outbuffer = new ByteArrayOutputStream();
        final ObjectOutputStream outStream = new ObjectOutputStream(outbuffer);
        outStream.writeObject(orig);
        outStream.close();
        final byte[] raw = outbuffer.toByteArray();
        final ByteArrayInputStream inBuffer = new ByteArrayInputStream(raw);
        final ObjectInputStream inStream = new ObjectInputStream(inBuffer);
        final HttpHost clone = (HttpHost) inStream.readObject();
        Assert.assertEquals(orig, clone);
    }

    @Test
    public void testCreateFromString() throws Exception {
        Assert.assertEquals(new HttpHost("https", "somehost", 8080), HttpHost.create("https://somehost:8080"));
        Assert.assertEquals(new HttpHost("https", "somehost", 8080), HttpHost.create("HttpS://SomeHost:8080"));
        Assert.assertEquals(new HttpHost(null, "somehost", 1234), HttpHost.create("somehost:1234"));
        Assert.assertEquals(new HttpHost(null, "somehost", -1), HttpHost.create("somehost"));
    }

    @Test
    public void testCreateFromURI() throws Exception {
        Assert.assertEquals(new HttpHost("https", "somehost", 8080), HttpHost.create(URI.create("https://somehost:8080")));
        Assert.assertEquals(new HttpHost("https", "somehost", 8080), HttpHost.create(URI.create("HttpS://SomeHost:8080")));
        Assert.assertEquals(new HttpHost("https", "somehost", 8080), HttpHost.create(URI.create("HttpS://SomeHost:8080/foo")));
    }

    @Test
    public void testCreateFromStringInvalid() throws Exception {
        try {
            HttpHost.create(" host ");
            Assert.fail("URISyntaxException expected");
        } catch (final URISyntaxException expected) {
        }
        try {
            HttpHost.create("host :8080");
            Assert.fail("URISyntaxException expected");
        } catch (final URISyntaxException expected) {
        }
        try {
            HttpHost.create("");
            Assert.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException expected) {
        }
    }

}
