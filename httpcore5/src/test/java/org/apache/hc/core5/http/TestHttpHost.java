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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link HttpHost}.
 *
 */
class TestHttpHost {

    @Test
    void testConstructor() {
        final HttpHost host1 = new HttpHost("somehost");
        Assertions.assertEquals("somehost", host1.getHostName());
        Assertions.assertEquals(-1, host1.getPort());
        Assertions.assertEquals("http", host1.getSchemeName());
        final HttpHost host2 = new HttpHost("somehost", 8080);
        Assertions.assertEquals("somehost", host2.getHostName());
        Assertions.assertEquals(8080, host2.getPort());
        Assertions.assertEquals("http", host2.getSchemeName());
        final HttpHost host3 = new HttpHost("somehost", -1);
        Assertions.assertEquals("somehost", host3.getHostName());
        Assertions.assertEquals(-1, host3.getPort());
        Assertions.assertEquals("http", host3.getSchemeName());
        final HttpHost host4 = new HttpHost("https", "somehost", 443);
        Assertions.assertEquals("somehost", host4.getHostName());
        Assertions.assertEquals(443, host4.getPort());
        Assertions.assertEquals("https", host4.getSchemeName());
        final HttpHost host5 = new HttpHost("https", "somehost");
        Assertions.assertEquals("somehost", host5.getHostName());
        Assertions.assertEquals(-1, host5.getPort());
        Assertions.assertEquals("https", host5.getSchemeName());
        Assertions.assertThrows(NullPointerException.class, () -> new HttpHost(null, (String) null, -1));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new HttpHost(null, "   ", -1));
        Assertions.assertThrows(NullPointerException.class, () -> new HttpHost(null, (InetAddress) null, -1));
    }

    @Test
    void testHashCode() throws Exception {
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

        Assertions.assertEquals(host1.hashCode(), host1.hashCode());
        Assertions.assertNotEquals(host1.hashCode(), host2.hashCode());
        Assertions.assertNotEquals(host1.hashCode(), host3.hashCode());
        Assertions.assertEquals(host2.hashCode(), host4.hashCode());
        Assertions.assertEquals(host2.hashCode(), host5.hashCode());
        Assertions.assertNotEquals(host5.hashCode(), host6.hashCode());
        Assertions.assertNotEquals(host7.hashCode(), host8.hashCode());
        Assertions.assertNotEquals(host8.hashCode(), host9.hashCode());
        Assertions.assertEquals(host9.hashCode(), host10.hashCode());
        Assertions.assertNotEquals(host10.hashCode(), host11.hashCode());
        Assertions.assertNotEquals(host9.hashCode(), host11.hashCode());
    }

    @Test
    void testEquals() throws Exception {
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

        Assertions.assertEquals(host1, host1);
        Assertions.assertNotEquals(host1, host2);
        Assertions.assertNotEquals(host1, host3);
        Assertions.assertEquals(host2, host4);
        Assertions.assertEquals(host2, host5);
        Assertions.assertNotEquals(host5, host6);
        Assertions.assertNotEquals(host7, host8);
        Assertions.assertNotEquals(host7, host9);
        Assertions.assertNotEquals(null, host1);
        Assertions.assertNotEquals("http://somehost", host1);
        Assertions.assertNotEquals("http://somehost", host9);
        Assertions.assertNotEquals(host8, host9);
        Assertions.assertEquals(host9, host10);
        Assertions.assertNotEquals(host9, host11);
    }

    @Test
    void testToString() throws Exception {
        final HttpHost host1 = new HttpHost("somehost");
        Assertions.assertEquals("http://somehost", host1.toString());
        final HttpHost host2 = new HttpHost("somehost", -1);
        Assertions.assertEquals("http://somehost", host2.toString());
        final HttpHost host3 = new HttpHost("somehost", -1);
        Assertions.assertEquals("http://somehost", host3.toString());
        final HttpHost host4 = new HttpHost("somehost", 8888);
        Assertions.assertEquals("http://somehost:8888", host4.toString());
        final HttpHost host5 = new HttpHost("myhttp", "somehost", -1);
        Assertions.assertEquals("myhttp://somehost", host5.toString());
        final HttpHost host6 = new HttpHost("myhttp", "somehost", 80);
        Assertions.assertEquals("myhttp://somehost:80", host6.toString());
        final HttpHost host7 = new HttpHost(
                "http", InetAddress.getByAddress("127.0.0.1", new byte[] {127,0,0,1}), 80);
        Assertions.assertEquals("http://127.0.0.1:80", host7.toString());
        final HttpHost host9 = new HttpHost(
                        "http", InetAddress.getByAddress("somehost", new byte[] {127,0,0,1}), 80);
        Assertions.assertEquals("http://somehost:80", host9.toString());
    }

    @Test
    void testToHostString() {
        final HttpHost host1 = new HttpHost("somehost");
        Assertions.assertEquals("somehost", host1.toHostString());
        final HttpHost host2 = new HttpHost("somehost");
        Assertions.assertEquals("somehost", host2.toHostString());
        final HttpHost host3 = new HttpHost("somehost", -1);
        Assertions.assertEquals("somehost", host3.toHostString());
        final HttpHost host4 = new HttpHost("somehost", 8888);
        Assertions.assertEquals("somehost:8888", host4.toHostString());
    }

    @Test
    void testSerialization() throws Exception {
        final HttpHost orig = new HttpHost("https", "somehost", 8080);
        final ByteArrayOutputStream outbuffer = new ByteArrayOutputStream();
        final ObjectOutputStream outStream = new ObjectOutputStream(outbuffer);
        outStream.writeObject(orig);
        outStream.close();
        final byte[] raw = outbuffer.toByteArray();
        final ByteArrayInputStream inBuffer = new ByteArrayInputStream(raw);
        final ObjectInputStream inStream = new ObjectInputStream(inBuffer);
        final HttpHost clone = (HttpHost) inStream.readObject();
        Assertions.assertEquals(orig, clone);
    }

    @Test
    void testCreateFromString() throws Exception {
        Assertions.assertEquals(new HttpHost("https", "somehost", 8080), HttpHost.create("https://somehost:8080"));
        Assertions.assertEquals(new HttpHost("https", "somehost", 8080), HttpHost.create("HttpS://SomeHost:8080"));
        Assertions.assertEquals(new HttpHost(null, "somehost", 1234), HttpHost.create("somehost:1234"));
        Assertions.assertEquals(new HttpHost(null, "somehost", -1), HttpHost.create("somehost"));
    }

    @Test
    void testCreateFromURI() {
        Assertions.assertEquals(new HttpHost("https", "somehost", 8080), HttpHost.create(URI.create("https://somehost:8080")));
        Assertions.assertEquals(new HttpHost("https", "somehost", 8080), HttpHost.create(URI.create("HttpS://SomeHost:8080")));
        Assertions.assertEquals(new HttpHost("https", "somehost", 8080), HttpHost.create(URI.create("HttpS://SomeHost:8080/foo")));
    }

    @Test
    void testCreateFromStringInvalid() {
        Assertions.assertThrows(URISyntaxException.class, () -> HttpHost.create(" host "));
        Assertions.assertThrows(URISyntaxException.class, () -> HttpHost.create("host :8080"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> HttpHost.create(""));
    }

    @Test
    void testIpv6HostAndPort() throws Exception {
        final HttpHost host = HttpHost.create("[::1]:80");
        Assertions.assertEquals("http", host.getSchemeName());
        Assertions.assertEquals("::1", host.getHostName());
        Assertions.assertEquals(80, host.getPort());
    }

    @Test
    void testIpv6HostAndPortWithScheme() throws Exception {
        final HttpHost host = HttpHost.create("https://[::1]:80");
        Assertions.assertEquals("https", host.getSchemeName());
        Assertions.assertEquals("::1", host.getHostName());
        Assertions.assertEquals(80, host.getPort());
    }

    @Test
    void testIpv6HostAndPortWithoutBrackets() {
        Assertions.assertThrows(URISyntaxException.class, () -> HttpHost.create("::1:80"));
    }

    @Test
    void testIpv6HostWithoutPort() {
        Assertions.assertThrows(URISyntaxException.class, () -> HttpHost.create("::1"));
    }

    @Test
    void testIpv6HostToString() {
        Assertions.assertEquals("http://[::1]:80", new HttpHost("::1", 80).toString());
        Assertions.assertEquals("http://[::1]", new HttpHost("::1", -1).toString());
    }
}
