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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link HttpHost}.
 *
 */
class TestHttpHost {

    @Test
    void testConstructor() {
        final HttpHost host1 = new HttpHost("somehost");
        Assertions.assertThat(host1.getHostName()).isEqualTo("somehost");
        Assertions.assertThat(host1.getPort()).isEqualTo(-1);
        Assertions.assertThat(host1.getSchemeName()).isEqualTo("http");
        final HttpHost host2 = new HttpHost("somehost", 8080);
        Assertions.assertThat(host2.getHostName()).isEqualTo("somehost");
        Assertions.assertThat(host2.getPort()).isEqualTo(8080);
        Assertions.assertThat(host2.getSchemeName()).isEqualTo("http");
        final HttpHost host3 = new HttpHost("somehost", -1);
        Assertions.assertThat(host3.getHostName()).isEqualTo("somehost");
        Assertions.assertThat(host3.getPort()).isEqualTo(-1);
        Assertions.assertThat(host3.getSchemeName()).isEqualTo("http");
        final HttpHost host4 = new HttpHost("https", "somehost", 443);
        Assertions.assertThat(host4.getHostName()).isEqualTo("somehost");
        Assertions.assertThat(host4.getPort()).isEqualTo(443);
        Assertions.assertThat(host4.getSchemeName()).isEqualTo("https");
        final HttpHost host5 = new HttpHost("https", "somehost");
        Assertions.assertThat(host5.getHostName()).isEqualTo("somehost");
        Assertions.assertThat(host5.getPort()).isEqualTo(-1);
        Assertions.assertThat(host5.getSchemeName()).isEqualTo("https");
        Assertions.assertThatThrownBy(() ->
                        new HttpHost(null, (String) null, -1))
                .isInstanceOf(NullPointerException.class);
        Assertions.assertThatThrownBy(() ->
                        new HttpHost(null, "   ", -1))
                .isInstanceOf(IllegalArgumentException.class);
        Assertions.assertThatThrownBy(() ->
                        new HttpHost(null, (InetAddress) null, -1))
                .isInstanceOf(NullPointerException.class);
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

        Assertions.assertThat(host1.hashCode()).isEqualTo(host1.hashCode());
        Assertions.assertThat(host2.hashCode()).isNotEqualTo(host1.hashCode());
        Assertions.assertThat(host3.hashCode()).isNotEqualTo(host1.hashCode());
        Assertions.assertThat(host4.hashCode()).isEqualTo(host2.hashCode());
        Assertions.assertThat(host5.hashCode()).isEqualTo(host2.hashCode());
        Assertions.assertThat(host6.hashCode()).isNotEqualTo(host5.hashCode());
        Assertions.assertThat(host8.hashCode()).isNotEqualTo(host7.hashCode());
        Assertions.assertThat(host9.hashCode()).isNotEqualTo(host8.hashCode());
        Assertions.assertThat(host10.hashCode()).isEqualTo(host9.hashCode());
        Assertions.assertThat(host11.hashCode()).isNotEqualTo(host10.hashCode());
        Assertions.assertThat(host11.hashCode()).isNotEqualTo(host9.hashCode());
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

        Assertions.assertThat(host1).isEqualTo(host1);
        Assertions.assertThat(host2).isNotEqualTo(host1);
        Assertions.assertThat(host3).isNotEqualTo(host1);
        Assertions.assertThat(host4).isEqualTo(host2);
        Assertions.assertThat(host5).isEqualTo(host2);
        Assertions.assertThat(host6).isNotEqualTo(host5);
        Assertions.assertThat(host8).isNotEqualTo(host7);
        Assertions.assertThat(host9).isNotEqualTo(host7);
        Assertions.assertThat(host1).isNotEqualTo(null);
        Assertions.assertThat(host1).isNotEqualTo("http://somehost");
        Assertions.assertThat(host9).isNotEqualTo("http://somehost");
        Assertions.assertThat(host9).isNotEqualTo(host8);
        Assertions.assertThat(host10).isEqualTo(host9);
        Assertions.assertThat(host11).isNotEqualTo(host9);
    }

    @Test
    void testToString() throws Exception {
        final HttpHost host1 = new HttpHost("somehost");
        Assertions.assertThat(host1.toString()).isEqualTo("http://somehost");
        final HttpHost host2 = new HttpHost("somehost", -1);
        Assertions.assertThat(host2.toString()).isEqualTo("http://somehost");
        final HttpHost host3 = new HttpHost("somehost", -1);
        Assertions.assertThat(host3.toString()).isEqualTo("http://somehost");
        final HttpHost host4 = new HttpHost("somehost", 8888);
        Assertions.assertThat(host4.toString()).isEqualTo("http://somehost:8888");
        final HttpHost host5 = new HttpHost("myhttp", "somehost", -1);
        Assertions.assertThat(host5.toString()).isEqualTo("myhttp://somehost");
        final HttpHost host6 = new HttpHost("myhttp", "somehost", 80);
        Assertions.assertThat(host6.toString()).isEqualTo("myhttp://somehost:80");
        final HttpHost host7 = new HttpHost(
                "http", InetAddress.getByAddress("127.0.0.1", new byte[] {127,0,0,1}), 80);
        Assertions.assertThat(host7.toString()).isEqualTo("http://127.0.0.1:80");
        final HttpHost host9 = new HttpHost(
                        "http", InetAddress.getByAddress("somehost", new byte[] {127,0,0,1}), 80);
        Assertions.assertThat(host9.toString()).isEqualTo("http://somehost:80");
    }

    @Test
    void testToHostString() {
        final HttpHost host1 = new HttpHost("somehost");
        Assertions.assertThat(host1.toHostString()).isEqualTo("somehost");
        final HttpHost host2 = new HttpHost("somehost");
        Assertions.assertThat(host2.toHostString()).isEqualTo("somehost");
        final HttpHost host3 = new HttpHost("somehost", -1);
        Assertions.assertThat(host3.toHostString()).isEqualTo("somehost");
        final HttpHost host4 = new HttpHost("somehost", 8888);
        Assertions.assertThat(host4.toHostString()).isEqualTo("somehost:8888");
    }

    @Test
    void testSerialization() throws Exception {
        final HttpHost orig = new HttpHost("https", "somehost", 8080);
        final ByteArrayOutputStream outbuffer = new ByteArrayOutputStream();
        try (ObjectOutputStream outStream = new ObjectOutputStream(outbuffer)) {
            outStream.writeObject(orig);
        }
        final byte[] raw = outbuffer.toByteArray();
        final ByteArrayInputStream inBuffer = new ByteArrayInputStream(raw);
        final ObjectInputStream inStream = new ObjectInputStream(inBuffer);
        final HttpHost clone = (HttpHost) inStream.readObject();
        Assertions.assertThat(clone).isEqualTo(orig);
    }

    @Test
    void testCreateFromString() throws Exception {
        Assertions.assertThat(HttpHost.create("https://somehost:8080")).isEqualTo(new HttpHost("https", "somehost", 8080));
        Assertions.assertThat(HttpHost.create("HttpS://SomeHost:8080")).isEqualTo(new HttpHost("https", "somehost", 8080));
        Assertions.assertThat(HttpHost.create("somehost:1234")).isEqualTo(new HttpHost(null, "somehost", 1234));
        Assertions.assertThat(HttpHost.create("somehost")).isEqualTo(new HttpHost(null, "somehost", -1));
    }

    @Test
    void testCreateFromURI() {
        Assertions.assertThat(HttpHost.create(URI.create("https://somehost:8080"))).isEqualTo(new HttpHost("https", "somehost", 8080));
        Assertions.assertThat(HttpHost.create(URI.create("HttpS://SomeHost:8080"))).isEqualTo(new HttpHost("https", "somehost", 8080));
        Assertions.assertThat(HttpHost.create(URI.create("HttpS://SomeHost:8080/foo"))).isEqualTo(new HttpHost("https", "somehost", 8080));
    }

    @Test
    void testCreateFromStringInvalid() {
        Assertions.assertThatThrownBy(() ->
                        HttpHost.create(" host "))
                .isInstanceOf(URISyntaxException.class);
        Assertions.assertThatThrownBy(() ->
                        HttpHost.create("host :8080"))
                .isInstanceOf(URISyntaxException.class);
        Assertions.assertThatThrownBy(() ->
                        HttpHost.create(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testIpv6HostAndPort() throws Exception {
        final HttpHost host = HttpHost.create("[::1]:80");
        Assertions.assertThat(host.getSchemeName()).isEqualTo("http");
        Assertions.assertThat(host.getHostName()).isEqualTo("::1");
        Assertions.assertThat(host.getPort()).isEqualTo(80);
    }

    @Test
    void testIpv6HostAndPortWithScheme() throws Exception {
        final HttpHost host = HttpHost.create("https://[::1]:80");
        Assertions.assertThat(host.getSchemeName()).isEqualTo("https");
        Assertions.assertThat(host.getHostName()).isEqualTo("::1");
        Assertions.assertThat(host.getPort()).isEqualTo(80);
    }

    @Test
    void testIpv6HostAndPortWithoutBrackets() {
        Assertions.assertThatThrownBy(() ->
                        HttpHost.create("::1:80"))
                .isInstanceOf(URISyntaxException.class);
    }

    @Test
    void testIpv6HostWithoutPort() {
        Assertions.assertThatThrownBy(() ->
                        HttpHost.create("::1"))
                .isInstanceOf(URISyntaxException.class);
    }

    @Test
    void testIpv6HostToString() {
        Assertions.assertThat(new HttpHost("::1", 80).toString()).isEqualTo("http://[::1]:80");
        Assertions.assertThat(new HttpHost("::1", -1).toString()).isEqualTo("http://[::1]");
    }
}
