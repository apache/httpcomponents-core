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
 * Unit tests for {@link Host}.
 *
 */
class TestHost {

    @Test
    void testConstructor() {
        final Host host1 = new Host("somehost", 8080);
        Assertions.assertThat(host1.getHostName()).isEqualTo("somehost");
        Assertions.assertThat(host1.getPort()).isEqualTo(8080);
        final Host host2 = new Host("somehost", 0);
        Assertions.assertThat(host2.getHostName()).isEqualTo("somehost");
        Assertions.assertThat(host2.getPort()).isEqualTo(0);
        final Host host3 = new Host("Яндекс.Ру", 0);
        Assertions.assertThat(host3.getHostName()).isEqualTo("Яндекс.Ру");
        Assertions.assertThat(host3.getPort()).isEqualTo(0);
        final Host host4 = new Host("xn--d1acpjx3f.xn--p1ag", 0);
        Assertions.assertThat(host4.getHostName()).isEqualTo("яндекс.ру");
        Assertions.assertThat(host4.getPort()).isEqualTo(0);
        final Host host5 = new Host("XN--D1Acpjx3f.xn--p1ag", 0);
        Assertions.assertThat(host5.getHostName()).isEqualTo("яндекс.ру");
        Assertions.assertThat(host5.getPort()).isEqualTo(0);
        Assertions.assertThatThrownBy(() ->
                        new Host(null, 0))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testHashCode() {
        final Host host1 = new Host("somehost", 8080);
        final Host host2 = new Host("somehost", 80);
        final Host host3 = new Host("someotherhost", 8080);
        final Host host4 = new Host("somehost", 80);

        Assertions.assertThat(host1.hashCode()).isEqualTo(host1.hashCode());
        Assertions.assertThat(host2.hashCode()).isNotEqualTo(host1.hashCode());
        Assertions.assertThat(host3.hashCode()).isNotEqualTo(host1.hashCode());
        Assertions.assertThat(host4.hashCode()).isEqualTo(host2.hashCode());
    }

    @Test
    void testEquals() {
        final Host host1 = new Host("somehost", 8080);
        final Host host2 = new Host("somehost", 80);
        final Host host3 = new Host("someotherhost", 8080);
        final Host host4 = new Host("somehost", 80);

        Assertions.assertThat(host1).isEqualTo(host1);
        Assertions.assertThat(host2).isNotEqualTo(host1);
        Assertions.assertThat(host3).isNotEqualTo(host1);
        Assertions.assertThat(host4).isEqualTo(host2);
    }

    @Test
    void testToString() {
        final Host host1 = new Host("somehost", 8888);
        Assertions.assertThat(host1.toString()).isEqualTo("somehost:8888");
    }

    @Test
    void testSerialization() throws Exception {
        final Host orig = new Host("somehost", 8080);
        final ByteArrayOutputStream outbuffer = new ByteArrayOutputStream();
        try (ObjectOutputStream outStream = new ObjectOutputStream(outbuffer)) {
            outStream.writeObject(orig);
        }
        final byte[] raw = outbuffer.toByteArray();
        final ByteArrayInputStream inBuffer = new ByteArrayInputStream(raw);
        final ObjectInputStream inStream = new ObjectInputStream(inBuffer);
        final Host clone = (Host) inStream.readObject();
        Assertions.assertThat(clone).isEqualTo(orig);
    }

    @Test
    void testCreateFromString() throws Exception {
        Assertions.assertThat(Host.create("somehost:8080")).isEqualTo(new Host("somehost", 8080));
        Assertions.assertThat(Host.create("somehost:1234")).isEqualTo(new Host("somehost", 1234));
        Assertions.assertThat(Host.create("somehost:0")).isEqualTo(new Host("somehost", 0));
        Assertions.assertThat(Host.create("xn--d1acpjx3f.xn--p1ag")).isEqualTo(new Host("яндекс.ру", -1));
        Assertions.assertThat(Host.create("Яндекс.Ру")).isEqualTo(new Host("Яндекс.Ру", -1));
    }

    @Test
    void testCreateFromStringInvalid() {
        Assertions.assertThatThrownBy(() ->
                        Host.create(" host "))
                .isInstanceOf(URISyntaxException.class);
        Assertions.assertThatThrownBy(() ->
                        Host.create("host :8080"))
                .isInstanceOf(URISyntaxException.class);
        Assertions.assertThatThrownBy(() ->
                        Host.create(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testIpv6HostAndPort() throws Exception {
        final Host host = Host.create("[::1]:80");
        Assertions.assertThat(host.getHostName()).isEqualTo("::1");
        Assertions.assertThat(host.getPort()).isEqualTo(80);
    }

    @Test
    void testIpv6HostAndPortWithoutBrackets() {
        // ambiguous
        Assertions.assertThatThrownBy(() ->
                        Host.create("::1:80"))
                .isInstanceOf(URISyntaxException.class);
    }

    @Test
    void testIpv6HostWithoutPort() {
        Assertions.assertThatThrownBy(() ->
                        Host.create("::1"))
                .isInstanceOf(URISyntaxException.class);
    }

    @Test
    void testIpv6HostToString() {
        Assertions.assertThat(new Host("::1", 80).toString()).isEqualTo("[::1]:80");
        Assertions.assertThat(new Host("::1", -1).toString()).isEqualTo("[::1]");
    }
}
