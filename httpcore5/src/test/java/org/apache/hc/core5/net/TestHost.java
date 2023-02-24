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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Host}.
 *
 */
public class TestHost {

    @Test
    public void testConstructor() {
        final Host host1 = new Host("somehost", 8080);
        Assertions.assertEquals("somehost", host1.getHostName());
        Assertions.assertEquals(8080, host1.getPort());
        final Host host2 = new Host("somehost", 0);
        Assertions.assertEquals("somehost", host2.getHostName());
        Assertions.assertEquals(0, host2.getPort());
        final Host host3 = new Host("Яндекс.Ру", 0);
        Assertions.assertEquals("Яндекс.Ру", host3.getHostName());
        Assertions.assertEquals(0, host3.getPort());
        final Host host4 = new Host("xn--d1acpjx3f.xn--p1ag", 0);
        Assertions.assertEquals("яндекс.ру", host4.getHostName());
        Assertions.assertEquals(0, host4.getPort());
        final Host host5 = new Host("XN--D1Acpjx3f.xn--p1ag", 0);
        Assertions.assertEquals("яндекс.ру", host5.getHostName());
        Assertions.assertEquals(0, host5.getPort());
        Assertions.assertThrows(NullPointerException.class, () -> new Host(null, 0));
    }

    @Test
    public void testHashCode() throws Exception {
        final Host host1 = new Host("somehost", 8080);
        final Host host2 = new Host("somehost", 80);
        final Host host3 = new Host("someotherhost", 8080);
        final Host host4 = new Host("somehost", 80);

        Assertions.assertEquals(host1.hashCode(), host1.hashCode());
        Assertions.assertTrue(host1.hashCode() != host2.hashCode());
        Assertions.assertTrue(host1.hashCode() != host3.hashCode());
        Assertions.assertEquals(host2.hashCode(), host4.hashCode());
    }

    @Test
    public void testEquals() throws Exception {
        final Host host1 = new Host("somehost", 8080);
        final Host host2 = new Host("somehost", 80);
        final Host host3 = new Host("someotherhost", 8080);
        final Host host4 = new Host("somehost", 80);

        Assertions.assertEquals(host1, host1);
        Assertions.assertNotEquals(host1, host2);
        Assertions.assertNotEquals(host1, host3);
        Assertions.assertEquals(host2, host4);
    }

    @Test
    public void testToString() throws Exception {
        final Host host1 = new Host("somehost", 8888);
        Assertions.assertEquals("somehost:8888", host1.toString());
    }

    @Test
    public void testSerialization() throws Exception {
        final Host orig = new Host("somehost", 8080);
        final ByteArrayOutputStream outbuffer = new ByteArrayOutputStream();
        final ObjectOutputStream outStream = new ObjectOutputStream(outbuffer);
        outStream.writeObject(orig);
        outStream.close();
        final byte[] raw = outbuffer.toByteArray();
        final ByteArrayInputStream inBuffer = new ByteArrayInputStream(raw);
        final ObjectInputStream inStream = new ObjectInputStream(inBuffer);
        final Host clone = (Host) inStream.readObject();
        Assertions.assertEquals(orig, clone);
    }

    @Test
    public void testCreateFromString() throws Exception {
        Assertions.assertEquals(new Host("somehost", 8080), Host.create("somehost:8080"));
        Assertions.assertEquals(new Host("somehost", 1234), Host.create("somehost:1234"));
        Assertions.assertEquals(new Host("somehost", 0), Host.create("somehost:0"));
        Assertions.assertEquals(new Host("яндекс.ру", -1), Host.create("xn--d1acpjx3f.xn--p1ag"));
        Assertions.assertEquals(new Host("Яндекс.Ру", -1), Host.create("Яндекс.Ру"));
    }

    @Test
    public void testCreateFromStringInvalid() throws Exception {
        Assertions.assertThrows(URISyntaxException.class, () -> Host.create(" host "));
        Assertions.assertThrows(URISyntaxException.class, () -> Host.create("host :8080"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> Host.create(""));
    }

    @Test
    public void testIpv6HostAndPort() throws Exception {
        final Host host = Host.create("[::1]:80");
        Assertions.assertEquals("::1", host.getHostName());
        Assertions.assertEquals(80, host.getPort());
    }

    @Test
    public void testIpv6HostAndPortWithoutBrackets() {
        // ambiguous
        Assertions.assertThrows(URISyntaxException.class, () -> Host.create("::1:80"));
    }

    @Test
    public void testIpv6HostWithoutPort() {
        Assertions.assertThrows(URISyntaxException.class, () -> Host.create("::1"));
    }

    @Test
    public void testIpv6HostToString() {
        Assertions.assertEquals("[::1]:80", new Host("::1", 80).toString());
        Assertions.assertEquals("[::1]", new Host("::1", -1).toString());
    }
}
