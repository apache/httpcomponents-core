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

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for InetAddressUtils.
 */
public class TestInetAddressUtils {

    @Test
    public void testValidIPv4Address() {
        Assert.assertTrue(InetAddressUtils.isIPv4Address("127.0.0.1"));
        Assert.assertTrue(InetAddressUtils.isIPv4Address("192.168.0.0"));
        Assert.assertTrue(InetAddressUtils.isIPv4Address("255.255.255.255"));
    }

    @Test
    public void testInvalidIPv4Address() {
        Assert.assertFalse(InetAddressUtils.isIPv4Address(" 127.0.0.1 "));  // Blanks not allowed
        Assert.assertFalse(InetAddressUtils.isIPv4Address("g.ar.ba.ge"));
        Assert.assertFalse(InetAddressUtils.isIPv4Address("192.168.0"));
        Assert.assertFalse(InetAddressUtils.isIPv4Address("256.255.255.255"));
        Assert.assertFalse(InetAddressUtils.isIPv4Address("0.168.0.0"));    //IP address that starts with zero not allowed
    }

    @Test
    public void testValidIPv6Address() {
        Assert.assertTrue(InetAddressUtils.isIPv6StdAddress("2001:0db8:0000:0000:0000:0000:1428:57ab"));
        Assert.assertTrue(InetAddressUtils.isIPv6StdAddress("2001:db8:0:0:0:0:1428:57ab"));
        Assert.assertTrue(InetAddressUtils.isIPv6StdAddress("0:0:0:0:0:0:0:0"));
        Assert.assertTrue(InetAddressUtils.isIPv6StdAddress("0:0:0:0:0:0:0:1"));
        Assert.assertTrue(InetAddressUtils.isIPv6HexCompressedAddress("2001:0db8:0:0::1428:57ab"));
        Assert.assertTrue(InetAddressUtils.isIPv6HexCompressedAddress("2001:0db8::1428:57ab"));
        Assert.assertTrue(InetAddressUtils.isIPv6HexCompressedAddress("2001:db8::1428:57ab"));
        Assert.assertTrue(InetAddressUtils.isIPv6HexCompressedAddress("::1"));
        Assert.assertTrue(InetAddressUtils.isIPv6HexCompressedAddress("::")); // http://tools.ietf.org/html/rfc4291#section-2.2
    }

    @Test
    public void testInvalidIPv6Address() {
        Assert.assertFalse(InetAddressUtils.isIPv6Address("2001:0db8:0000:garb:age0:0000:1428:57ab"));
        Assert.assertFalse(InetAddressUtils.isIPv6Address("2001:0gb8:0000:0000:0000:0000:1428:57ab"));
        Assert.assertFalse(InetAddressUtils.isIPv6StdAddress("0:0:0:0:0:0:0:0:0")); // Too many
        Assert.assertFalse(InetAddressUtils.isIPv6StdAddress("0:0:0:0:0:0:0")); // Too few
        Assert.assertFalse(InetAddressUtils.isIPv6HexCompressedAddress(":1"));
        Assert.assertFalse(InetAddressUtils.isIPv6Address("2001:0db8::0000::57ab")); // Cannot have two contractions
        Assert.assertFalse(InetAddressUtils.isIPv6HexCompressedAddress("1:2:3:4:5:6:7::9")); // too many fields before ::
        Assert.assertFalse(InetAddressUtils.isIPv6HexCompressedAddress("1::3:4:5:6:7:8:9")); // too many fields after ::
        Assert.assertFalse(InetAddressUtils.isIPv6HexCompressedAddress("::3:4:5:6:7:8:9")); // too many fields after ::
        Assert.assertFalse(InetAddressUtils.isIPv6Address("")); // empty
    }

    @Test
    public void testValidIPv6BracketAddress() {
        Assert.assertTrue(InetAddressUtils.isIPv6URLBracketedAddress("[2001:0db8:0000:0000:0000:0000:1428:57ab]"));
        Assert.assertTrue(InetAddressUtils.isIPv6URLBracketedAddress("[2001:db8:0:0:0:0:1428:57ab]"));
        Assert.assertTrue(InetAddressUtils.isIPv6URLBracketedAddress("[0:0:0:0:0:0:0:0]"));
        Assert.assertTrue(InetAddressUtils.isIPv6URLBracketedAddress("[0:0:0:0:0:0:0:1]"));
        Assert.assertTrue(InetAddressUtils.isIPv6URLBracketedAddress("[2001:0db8:0:0::1428:57ab]"));
        Assert.assertTrue(InetAddressUtils.isIPv6URLBracketedAddress("[2001:0db8::1428:57ab]"));
        Assert.assertTrue(InetAddressUtils.isIPv6URLBracketedAddress("[2001:db8::1428:57ab]"));
        Assert.assertTrue(InetAddressUtils.isIPv6URLBracketedAddress("[::1]"));
        // http://tools.ietf.org/html/rfc4291#section-2.2
        Assert.assertTrue(InetAddressUtils.isIPv6URLBracketedAddress("[::]"));
    }

    @Test
    public void testInvalidIPv6BracketAddress() {
        Assert.assertFalse(InetAddressUtils.isIPv6URLBracketedAddress("2001:0db8:0000:garb:age0:0000:1428:57ab"));
        Assert.assertFalse(InetAddressUtils.isIPv6URLBracketedAddress("[2001:0db8:0000:garb:age0:0000:1428:57ab]"));
        Assert.assertFalse(InetAddressUtils.isIPv6URLBracketedAddress("2001:0gb8:0000:0000:0000:0000:1428:57ab"));
        Assert.assertFalse(InetAddressUtils.isIPv6URLBracketedAddress("[2001:0gb8:0000:0000:0000:0000:1428:57ab]"));
        // Too many
        Assert.assertFalse(InetAddressUtils.isIPv6URLBracketedAddress("0:0:0:0:0:0:0:0:0"));
        Assert.assertFalse(InetAddressUtils.isIPv6URLBracketedAddress("[0:0:0:0:0:0:0:0:0]"));
        // Too few
        Assert.assertFalse(InetAddressUtils.isIPv6URLBracketedAddress("0:0:0:0:0:0:0"));
        Assert.assertFalse(InetAddressUtils.isIPv6URLBracketedAddress("[0:0:0:0:0:0:0]"));
        Assert.assertFalse(InetAddressUtils.isIPv6URLBracketedAddress(":1"));
        Assert.assertFalse(InetAddressUtils.isIPv6URLBracketedAddress("[:1]"));
        // Cannot have two contractions
        Assert.assertFalse(InetAddressUtils.isIPv6URLBracketedAddress("2001:0db8::0000::57ab"));
        Assert.assertFalse(InetAddressUtils.isIPv6URLBracketedAddress("[2001:0db8::0000::57ab]"));
        // too many fields before ::
        Assert.assertFalse(InetAddressUtils.isIPv6URLBracketedAddress("1:2:3:4:5:6:7::9"));
        Assert.assertFalse(InetAddressUtils.isIPv6URLBracketedAddress("[1:2:3:4:5:6:7::9]"));
        // too many fields after ::
        Assert.assertFalse(InetAddressUtils.isIPv6URLBracketedAddress("1::3:4:5:6:7:8:9"));
        Assert.assertFalse(InetAddressUtils.isIPv6URLBracketedAddress("[1::3:4:5:6:7:8:9]"));
        // too many fields after ::
        Assert.assertFalse(InetAddressUtils.isIPv6URLBracketedAddress("::3:4:5:6:7:8:9"));
        Assert.assertFalse(InetAddressUtils.isIPv6URLBracketedAddress("[::3:4:5:6:7:8:9]"));
        // empty
        Assert.assertFalse(InetAddressUtils.isIPv6URLBracketedAddress(""));
        Assert.assertFalse(InetAddressUtils.isIPv6URLBracketedAddress("[]"));

        // missing brackets
        Assert.assertFalse(InetAddressUtils.isIPv6URLBracketedAddress("::"));
        Assert.assertFalse(InetAddressUtils.isIPv6URLBracketedAddress("::1"));
        Assert.assertFalse(InetAddressUtils.isIPv6URLBracketedAddress("2001:db8::1428:57ab"));
    }

    @Test
    // Test HTTPCLIENT-1319
    public void testInvalidIPv6AddressIncorrectGroupCount() {
        Assert.assertFalse(InetAddressUtils.isIPv6HexCompressedAddress("1:2::4:5:6:7:8:9")); // too many fields in total
        Assert.assertFalse(InetAddressUtils.isIPv6HexCompressedAddress("1:2:3:4:5:6::8:9")); // too many fields in total
    }

    @Test
    public void testValidIPv4MappedIPv6Address() {
        Assert.assertTrue(InetAddressUtils.isIPv4MappedIPv64Address("::FFFF:1.2.3.4"));
        Assert.assertTrue(InetAddressUtils.isIPv4MappedIPv64Address("::ffff:255.255.255.255"));
    }

    @Test
    public void testInValidIPv4MappedIPv6Address() {
        Assert.assertFalse(InetAddressUtils.isIPv4MappedIPv64Address("2001:0db8:0000:0000:0000:0000:1428:57ab"));
        Assert.assertFalse(InetAddressUtils.isIPv4MappedIPv64Address("::ffff:1:2:3:4"));
    }

}
