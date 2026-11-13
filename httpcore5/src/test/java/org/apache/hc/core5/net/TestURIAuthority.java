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

package org.apache.hc.core5.net;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URISyntaxException;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link URIAuthority}.
 *
 */
class TestURIAuthority {

    @Test
    void testConstructor() {
        final URIAuthority host1 = new URIAuthority("somehost");
        Assertions.assertThat(host1.getHostName()).isEqualTo("somehost");
        Assertions.assertThat(host1.getPort()).isEqualTo(-1);
        final URIAuthority host2 = new URIAuthority("somehost", 8080);
        Assertions.assertThat(host2.getHostName()).isEqualTo("somehost");
        Assertions.assertThat(host2.getPort()).isEqualTo(8080);
        final URIAuthority host3 = new URIAuthority("somehost", -1);
        Assertions.assertThat(host3.getHostName()).isEqualTo("somehost");
        Assertions.assertThat(host3.getPort()).isEqualTo(-1);
    }

    @Test
    void testHashCode() {
        final URIAuthority host1 = new URIAuthority("somehost", 8080);
        final URIAuthority host2 = new URIAuthority("somehost", 80);
        final URIAuthority host3 = new URIAuthority("someotherhost", 8080);
        final URIAuthority host4 = new URIAuthority("somehost", 80);
        final URIAuthority host5 = new URIAuthority("SomeHost", 80);
        final URIAuthority host6 = new URIAuthority("user", "SomeHost", 80);
        final URIAuthority host7 = new URIAuthority("user", "somehost", 80);

        Assertions.assertThat(host1.hashCode()).isEqualTo(host1.hashCode());
        Assertions.assertThat(host2.hashCode()).isNotEqualTo(host1.hashCode());
        Assertions.assertThat(host3.hashCode()).isNotEqualTo(host1.hashCode());
        Assertions.assertThat(host4.hashCode()).isEqualTo(host2.hashCode());
        Assertions.assertThat(host5.hashCode()).isEqualTo(host2.hashCode());
        Assertions.assertThat(host6.hashCode()).isNotEqualTo(host5.hashCode());
        Assertions.assertThat(host7.hashCode()).isEqualTo(host6.hashCode());
    }

    @Test
    void testEquals() {
        final URIAuthority host1 = new URIAuthority("somehost", 8080);
        final URIAuthority host2 = new URIAuthority("somehost", 80);
        final URIAuthority host3 = new URIAuthority("someotherhost", 8080);
        final URIAuthority host4 = new URIAuthority("somehost", 80);
        final URIAuthority host5 = new URIAuthority("SomeHost", 80);
        final URIAuthority host6 = new URIAuthority("user", "SomeHost", 80);
        final URIAuthority host7 = new URIAuthority("user", "somehost", 80);

        Assertions.assertThat(host1).isEqualTo(host1);
        Assertions.assertThat(host2).isNotEqualTo(host1);
        Assertions.assertThat(host3).isNotEqualTo(host1);
        Assertions.assertThat(host4).isEqualTo(host2);
        Assertions.assertThat(host5).isEqualTo(host2);
        Assertions.assertThat(host6).isNotEqualTo(host5);
        Assertions.assertThat(host7).isEqualTo(host6);
    }

    @Test
    void testToString() {
        final URIAuthority host1 = new URIAuthority("somehost");
        Assertions.assertThat(host1.toString()).isEqualTo("somehost");
        final URIAuthority host2 = new URIAuthority("somehost", -1);
        Assertions.assertThat(host2.toString()).isEqualTo("somehost");
        final URIAuthority host3 = new URIAuthority("somehost", 8888);
        Assertions.assertThat(host3.toString()).isEqualTo("somehost:8888");
    }

    @Test
    void testSerialization() throws Exception {
        final URIAuthority orig = new URIAuthority("somehost", 8080);
        final ByteArrayOutputStream outbuffer = new ByteArrayOutputStream();
        try (ObjectOutputStream outStream = new ObjectOutputStream(outbuffer)) {
            outStream.writeObject(orig);
        }
        final byte[] raw = outbuffer.toByteArray();
        final ByteArrayInputStream inBuffer = new ByteArrayInputStream(raw);
        final ObjectInputStream inStream = new ObjectInputStream(inBuffer);
        final URIAuthority clone = (URIAuthority) inStream.readObject();
        Assertions.assertThat(clone).isEqualTo(orig);
    }

    @Test
    void testParse() throws Exception {
        Assertions.assertThat(URIAuthority.parse("somehost")).isEqualTo(new URIAuthority("somehost", -1));
        Assertions.assertThat(URIAuthority.parse("somehost/blah")).isEqualTo(new URIAuthority("somehost", -1));
        Assertions.assertThat(URIAuthority.parse("somehost?blah")).isEqualTo(new URIAuthority("somehost", -1));
        Assertions.assertThat(URIAuthority.parse("somehost#blah")).isEqualTo(new URIAuthority("somehost", -1));
        Assertions.assertThat(URIAuthority.parse("aaaa@:8080")).isEqualTo(new URIAuthority("aaaa", "", 8080));
        Assertions.assertThat(URIAuthority.parse("@:")).isEqualTo(new URIAuthority(null, "", -1));
        Assertions.assertThat(URIAuthority.parse("somehost:8080")).isEqualTo(new URIAuthority("somehost", 8080));
        Assertions.assertThat(URIAuthority.parse("somehost:8080/blah")).isEqualTo(new URIAuthority("somehost", 8080));
        Assertions.assertThat(URIAuthority.parse("somehost:8080?blah")).isEqualTo(new URIAuthority("somehost", 8080));
        Assertions.assertThat(URIAuthority.parse("somehost:8080#blah")).isEqualTo(new URIAuthority("somehost", 8080));
        Assertions.assertThat(URIAuthority.parse("somehost:008080")).isEqualTo(new URIAuthority("somehost", 8080));
        Assertions.assertThatThrownBy(() ->
                        URIAuthority.create("somehost:aaaaa"))
                .isInstanceOf(URISyntaxException.class);
        Assertions.assertThatThrownBy(() ->
                        URIAuthority.create("somehost:90ab"))
                .isInstanceOf(URISyntaxException.class);

        Assertions.assertThat(URIAuthority.parse("someuser@somehost")).isEqualTo(new URIAuthority("someuser", "somehost", -1));
        Assertions.assertThat(URIAuthority.parse("someuser@somehost/blah")).isEqualTo(new URIAuthority("someuser", "somehost", -1));
        Assertions.assertThat(URIAuthority.parse("someuser@somehost?blah")).isEqualTo(new URIAuthority("someuser", "somehost", -1));
        Assertions.assertThat(URIAuthority.parse("someuser@somehost#blah")).isEqualTo(new URIAuthority("someuser", "somehost", -1));

        Assertions.assertThat(URIAuthority.parse("someuser@somehost:")).isEqualTo(new URIAuthority("someuser", "somehost", -1));
        Assertions.assertThat(URIAuthority.parse("someuser@somehost:/blah")).isEqualTo(new URIAuthority("someuser", "somehost", -1));
        Assertions.assertThat(URIAuthority.parse("someuser@somehost:?blah")).isEqualTo(new URIAuthority("someuser", "somehost", -1));
        Assertions.assertThat(URIAuthority.parse("someuser@somehost:#blah")).isEqualTo(new URIAuthority("someuser", "somehost", -1));

        Assertions.assertThat(URIAuthority.parse("someuser@somehost:8080")).isEqualTo(new URIAuthority("someuser", "somehost", 8080));
        Assertions.assertThat(URIAuthority.parse("someuser@somehost:8080/blah")).isEqualTo(new URIAuthority("someuser", "somehost", 8080));
        Assertions.assertThat(URIAuthority.parse("someuser@somehost:8080?blah")).isEqualTo(new URIAuthority("someuser", "somehost", 8080));
        Assertions.assertThat(URIAuthority.parse("someuser@somehost:8080#blah")).isEqualTo(new URIAuthority("someuser", "somehost", 8080));
        Assertions.assertThat(URIAuthority.parse("@somehost:8080")).isEqualTo(new URIAuthority("somehost", 8080));
        Assertions.assertThat(URIAuthority.parse("test:test@localhost:38339")).isEqualTo(new URIAuthority("test:test", "localhost", 38339));
        Assertions.assertThatThrownBy(() ->
                URIAuthority.create("blah@goggle.com:80@google.com/")).isInstanceOf(URISyntaxException.class);
    }

    @Test
    void testCreateFromString() throws Exception {
        Assertions.assertThat(URIAuthority.create("somehost:8080")).isEqualTo(new URIAuthority("somehost", 8080));
        Assertions.assertThat(URIAuthority.create("SomeHost:8080")).isEqualTo(new URIAuthority("SomeHost", 8080));
        Assertions.assertThat(URIAuthority.create("somehost:1234")).isEqualTo(new URIAuthority("somehost", 1234));
        Assertions.assertThat(URIAuthority.create("somehost")).isEqualTo(new URIAuthority("somehost", -1));
        Assertions.assertThat(URIAuthority.create("user@somehost")).isEqualTo(new URIAuthority("user", "somehost", -1));
        Assertions.assertThatThrownBy(() ->
                        URIAuthority.create(" host"))
                .isInstanceOf(URISyntaxException.class);
        Assertions.assertThatThrownBy(() ->
                        URIAuthority.create("host  "))
                .isInstanceOf(URISyntaxException.class);
        Assertions.assertThatThrownBy(() ->
                        URIAuthority.create("host :8080"))
                .isInstanceOf(URISyntaxException.class);
        Assertions.assertThatThrownBy(() ->
                        URIAuthority.create("user @ host:8080"))
                .isInstanceOf(URISyntaxException.class);
    }

    @Test
    void testCreateFromIPv6String() throws Exception {
        Assertions.assertThat(URIAuthority.create("[::1]:8080")).isEqualTo(new URIAuthority("::1", 8080));
        Assertions.assertThat(URIAuthority.create("[::1]")).isEqualTo(new URIAuthority("::1", -1));
        Assertions.assertThatThrownBy(() ->
                        URIAuthority.create("::1"))
                .isInstanceOf(URISyntaxException.class);
        Assertions.assertThatThrownBy(() ->
                        URIAuthority.create("[::1"))
                .isInstanceOf(URISyntaxException.class);
        Assertions.assertThatThrownBy(() ->
                        URIAuthority.create("[a]:8080"))
                .isInstanceOf(URISyntaxException.class);
    }

    @Test
    void testIpv6HostToString() {
        Assertions.assertThat(new URIAuthority("::1", 80).toString()).isEqualTo("[::1]:80");
        Assertions.assertThat(new URIAuthority("user", "::1", 80).toString()).isEqualTo("user@[::1]:80");
        Assertions.assertThat(new URIAuthority("::1", -1).toString()).isEqualTo("[::1]");
        Assertions.assertThat(new URIAuthority("user", "::1", -1).toString()).isEqualTo("user@[::1]");
    }
}
