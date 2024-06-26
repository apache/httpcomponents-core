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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for InetAddressUtils.
 */
class TestInetAddressUtils {

    @Test
    void testValidIPv4Address() {
        Assertions.assertTrue(InetAddressUtils.isIPv4("127.0.0.1"));
        Assertions.assertTrue(InetAddressUtils.isIPv4("192.168.0.0"));
        Assertions.assertTrue(InetAddressUtils.isIPv4("255.255.255.255"));
    }

    @Test
    void testInvalidIPv4Address() {
        Assertions.assertFalse(InetAddressUtils.isIPv4(" 127.0.0.1 "));  // Blanks not allowed
        Assertions.assertFalse(InetAddressUtils.isIPv4("g.ar.ba.ge"));
        Assertions.assertFalse(InetAddressUtils.isIPv4("192.168.0"));
        Assertions.assertFalse(InetAddressUtils.isIPv4("256.255.255.255"));
        Assertions.assertFalse(InetAddressUtils.isIPv4("0.168.0.0"));    //IP address that starts with zero not allowed
    }

    @Test
    void testValidIPv6Address() {
        Assertions.assertTrue(InetAddressUtils.isIPv6Std("2001:0db8:0000:0000:0000:0000:1428:57ab"));
        Assertions.assertTrue(InetAddressUtils.isIPv6Std("2001:db8:0:0:0:0:1428:57ab"));
        Assertions.assertTrue(InetAddressUtils.isIPv6Std("0:0:0:0:0:0:0:0"));
        Assertions.assertTrue(InetAddressUtils.isIPv6Std("0:0:0:0:0:0:0:1"));

        Assertions.assertTrue(InetAddressUtils.isIPv6HexCompressed("2001:0db8:0:0::1428:57ab"));
        Assertions.assertTrue(InetAddressUtils.isIPv6HexCompressed("2001:0db8::1428:57ab"));
        Assertions.assertTrue(InetAddressUtils.isIPv6HexCompressed("2001:db8::1428:57ab"));
        Assertions.assertTrue(InetAddressUtils.isIPv6HexCompressed("::1"));
        Assertions.assertTrue(InetAddressUtils.isIPv6HexCompressed("::")); // http://tools.ietf.org/html/rfc4291#section-2.2

        Assertions.assertTrue(InetAddressUtils.isIPv6("2001:0db8:0000:0000:0000:0000:1428:57ab"));
        Assertions.assertTrue(InetAddressUtils.isIPv6("2001:db8:0:0:0:0:1428:57ab"));
        Assertions.assertTrue(InetAddressUtils.isIPv6("0:0:0:0:0:0:0:0"));
        Assertions.assertTrue(InetAddressUtils.isIPv6("0:0:0:0:0:0:0:1"));
        Assertions.assertTrue(InetAddressUtils.isIPv6("2001:0db8:0:0::1428:57ab"));
        Assertions.assertTrue(InetAddressUtils.isIPv6("2001:0db8::1428:57ab"));
        Assertions.assertTrue(InetAddressUtils.isIPv6("2001:db8::1428:57ab"));
        Assertions.assertTrue(InetAddressUtils.isIPv6("::1"));
        Assertions.assertTrue(InetAddressUtils.isIPv6("::")); // http://tools.ietf.org/html/rfc4291#section-2.2

        //HTTPCORE-674 InetAddressUtils scoped ID support
        Assertions.assertTrue(InetAddressUtils.isIPv6("fe80::1ff:fe23:4567:890a"));
        Assertions.assertTrue(InetAddressUtils.isIPv6("fe80::1ff:fe23:4567:890a%eth2"));
        Assertions.assertTrue(InetAddressUtils.isIPv6("fe80::1ff:fe23:4567:890a%3"));
    }

    @Test
    void testInvalidIPv6Address() {
        Assertions.assertFalse(InetAddressUtils.isIPv6("2001:0db8:0000:garb:age0:0000:1428:57ab"));
        Assertions.assertFalse(InetAddressUtils.isIPv6("2001:0gb8:0000:0000:0000:0000:1428:57ab"));
        Assertions.assertFalse(InetAddressUtils.isIPv6Std("0:0:0:0:0:0:0:0:0")); // Too many
        Assertions.assertFalse(InetAddressUtils.isIPv6Std("0:0:0:0:0:0:0")); // Too few
        Assertions.assertFalse(InetAddressUtils.isIPv6HexCompressed(":1"));
        Assertions.assertFalse(InetAddressUtils.isIPv6(":1"));
        Assertions.assertFalse(InetAddressUtils.isIPv6("2001:0db8::0000::57ab")); // Cannot have two contractions
        Assertions.assertFalse(InetAddressUtils.isIPv6HexCompressed("1:2:3:4:5:6:7::9")); // too many fields before ::
        Assertions.assertFalse(InetAddressUtils.isIPv6HexCompressed("1::3:4:5:6:7:8:9")); // too many fields after ::
        Assertions.assertFalse(InetAddressUtils.isIPv6HexCompressed("::3:4:5:6:7:8:9")); // too many fields after ::
        Assertions.assertFalse(InetAddressUtils.isIPv6("")); // empty

        //Invalid scoped IDs
        Assertions.assertFalse(InetAddressUtils.isIPv6("fe80::1ff:fe23:4567:890a%eth2#"));
        Assertions.assertFalse(InetAddressUtils.isIPv6("fe80::1ff:fe23:4567:890a%3@"));
        Assertions.assertFalse(InetAddressUtils.isIPv6("fe80::1ff:fe23:4567:890a#eth2"));
        Assertions.assertFalse(InetAddressUtils.isIPv6("fe80::1ff:fe23:4567:890a%"));
        Assertions.assertFalse(InetAddressUtils.isIPv6("fe80::1ff:fe23:4567:890a%eth2!"));
        Assertions.assertFalse(InetAddressUtils.isIPv6("2001:0db8:0:0::1428:57ab%"));
        Assertions.assertFalse(InetAddressUtils.isIPv6("2001:0db8:0:0::1428:57ab%eth2#"));
        Assertions.assertFalse(InetAddressUtils.isIPv6("fe80::1ff:fe23:4567:890a%eth2#3"));
        Assertions.assertFalse(InetAddressUtils.isIPv6("2001:0db8:0:0::1428:57ab%eth2#3"));
        Assertions.assertFalse(InetAddressUtils.isIPv6("fe80::1ff:fe23:4567:890a%3#eth2"));
        Assertions.assertFalse(InetAddressUtils.isIPv6("2001:0db8:0:0::1428:57ab%3#eth2"));
    }

    @Test
    void testValidIPv6BracketAddress() {
        Assertions.assertTrue(InetAddressUtils.isIPv6URLBracketed("[2001:0db8:0000:0000:0000:0000:1428:57ab]"));
        Assertions.assertTrue(InetAddressUtils.isIPv6URLBracketed("[2001:db8:0:0:0:0:1428:57ab]"));
        Assertions.assertTrue(InetAddressUtils.isIPv6URLBracketed("[0:0:0:0:0:0:0:0]"));
        Assertions.assertTrue(InetAddressUtils.isIPv6URLBracketed("[0:0:0:0:0:0:0:1]"));
        Assertions.assertTrue(InetAddressUtils.isIPv6URLBracketed("[2001:0db8:0:0::1428:57ab]"));
        Assertions.assertTrue(InetAddressUtils.isIPv6URLBracketed("[2001:0db8::1428:57ab]"));
        Assertions.assertTrue(InetAddressUtils.isIPv6URLBracketed("[2001:db8::1428:57ab]"));
        Assertions.assertTrue(InetAddressUtils.isIPv6URLBracketed("[::1]"));
        // http://tools.ietf.org/html/rfc4291#section-2.2
        Assertions.assertTrue(InetAddressUtils.isIPv6URLBracketed("[::]"));
    }

    @Test
    void testInvalidIPv6BracketAddress() {
        Assertions.assertFalse(InetAddressUtils.isIPv6URLBracketed("2001:0db8:0000:garb:age0:0000:1428:57ab"));
        Assertions.assertFalse(InetAddressUtils.isIPv6URLBracketed("[2001:0db8:0000:garb:age0:0000:1428:57ab]"));
        Assertions.assertFalse(InetAddressUtils.isIPv6URLBracketed("2001:0gb8:0000:0000:0000:0000:1428:57ab"));
        Assertions.assertFalse(InetAddressUtils.isIPv6URLBracketed("[2001:0gb8:0000:0000:0000:0000:1428:57ab]"));
        // Too many
        Assertions.assertFalse(InetAddressUtils.isIPv6URLBracketed("0:0:0:0:0:0:0:0:0"));
        Assertions.assertFalse(InetAddressUtils.isIPv6URLBracketed("[0:0:0:0:0:0:0:0:0]"));
        // Too few
        Assertions.assertFalse(InetAddressUtils.isIPv6URLBracketed("0:0:0:0:0:0:0"));
        Assertions.assertFalse(InetAddressUtils.isIPv6URLBracketed("[0:0:0:0:0:0:0]"));
        Assertions.assertFalse(InetAddressUtils.isIPv6URLBracketed(":1"));
        Assertions.assertFalse(InetAddressUtils.isIPv6URLBracketed("[:1]"));
        // Cannot have two contractions
        Assertions.assertFalse(InetAddressUtils.isIPv6URLBracketed("2001:0db8::0000::57ab"));
        Assertions.assertFalse(InetAddressUtils.isIPv6URLBracketed("[2001:0db8::0000::57ab]"));
        // too many fields before ::
        Assertions.assertFalse(InetAddressUtils.isIPv6URLBracketed("1:2:3:4:5:6:7::9"));
        Assertions.assertFalse(InetAddressUtils.isIPv6URLBracketed("[1:2:3:4:5:6:7::9]"));
        // too many fields after ::
        Assertions.assertFalse(InetAddressUtils.isIPv6URLBracketed("1::3:4:5:6:7:8:9"));
        Assertions.assertFalse(InetAddressUtils.isIPv6URLBracketed("[1::3:4:5:6:7:8:9]"));
        // too many fields after ::
        Assertions.assertFalse(InetAddressUtils.isIPv6URLBracketed("::3:4:5:6:7:8:9"));
        Assertions.assertFalse(InetAddressUtils.isIPv6URLBracketed("[::3:4:5:6:7:8:9]"));
        // empty
        Assertions.assertFalse(InetAddressUtils.isIPv6URLBracketed(""));
        Assertions.assertFalse(InetAddressUtils.isIPv6URLBracketed("[]"));

        // missing brackets
        Assertions.assertFalse(InetAddressUtils.isIPv6URLBracketed("::"));
        Assertions.assertFalse(InetAddressUtils.isIPv6URLBracketed("::1"));
        Assertions.assertFalse(InetAddressUtils.isIPv6URLBracketed("2001:db8::1428:57ab"));
    }

    @Test
    // Test HTTPCLIENT-1319
    void testInvalidIPv6AddressIncorrectGroupCount() {
        Assertions.assertFalse(InetAddressUtils.isIPv6HexCompressed("1:2::4:5:6:7:8:9")); // too many fields in total
        Assertions.assertFalse(InetAddressUtils.isIPv6HexCompressed("1:2:3:4:5:6::8:9")); // too many fields in total
    }

    @Test
    void testHasValidIPv6ColonCount() {
        Assertions.assertFalse(InetAddressUtils.hasValidIPv6ColonCount(""));
        Assertions.assertFalse(InetAddressUtils.hasValidIPv6ColonCount(":"));
        Assertions.assertFalse(InetAddressUtils.hasValidIPv6ColonCount("127.0.0.1"));
        Assertions.assertFalse(InetAddressUtils.hasValidIPv6ColonCount(":0"));
        Assertions.assertFalse(InetAddressUtils.hasValidIPv6ColonCount("0:"));
        Assertions.assertFalse(InetAddressUtils.hasValidIPv6ColonCount("1:2:3:4:5:6:7:8:"));
        Assertions.assertFalse(InetAddressUtils.hasValidIPv6ColonCount("1:2:3:4:5:6:7:8:9"));

        Assertions.assertTrue(InetAddressUtils.hasValidIPv6ColonCount("2001:0db8:0000:0000:0000:0000:1428:57ab"));
        Assertions.assertTrue(InetAddressUtils.hasValidIPv6ColonCount("2001:db8:0:0:0:0:1428:57ab"));
        Assertions.assertTrue(InetAddressUtils.hasValidIPv6ColonCount("0:0:0:0:0:0:0:0"));
        Assertions.assertTrue(InetAddressUtils.hasValidIPv6ColonCount("0:0:0:0:0:0:0:1"));
        Assertions.assertTrue(InetAddressUtils.hasValidIPv6ColonCount("2001:0db8:0:0::1428:57ab"));
        Assertions.assertTrue(InetAddressUtils.hasValidIPv6ColonCount("2001:0db8::1428:57ab"));
        Assertions.assertTrue(InetAddressUtils.hasValidIPv6ColonCount("2001:db8::1428:57ab"));
        Assertions.assertTrue(InetAddressUtils.hasValidIPv6ColonCount("::1"));
        Assertions.assertTrue(InetAddressUtils.hasValidIPv6ColonCount("::")); // http://tools.ietf.org/html/rfc4291#section-2.2
    }

    @Test
    void testValidIPv4MappedIPv6Address() {
        Assertions.assertTrue(InetAddressUtils.isIPv4MappedIPv6("::FFFF:1.2.3.4"));
        Assertions.assertTrue(InetAddressUtils.isIPv4MappedIPv6("::ffff:255.255.255.255"));
    }

    @Test
    void testInValidIPv4MappedIPv6Address() {
        Assertions.assertFalse(InetAddressUtils.isIPv4MappedIPv6("2001:0db8:0000:0000:0000:0000:1428:57ab"));
        Assertions.assertFalse(InetAddressUtils.isIPv4MappedIPv6("::ffff:1:2:3:4"));
    }

}
