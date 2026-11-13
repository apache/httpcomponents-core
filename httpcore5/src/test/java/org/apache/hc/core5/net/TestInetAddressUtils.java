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

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.hc.core5.http.HttpHost;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for InetAddressUtils.
 */
class TestInetAddressUtils {

    @Test
    void testValidIPv4Address() {
        Assertions.assertThat(InetAddressUtils.isIPv4("127.0.0.1")).isTrue();
        Assertions.assertThat(InetAddressUtils.isIPv4("192.168.0.0")).isTrue();
        Assertions.assertThat(InetAddressUtils.isIPv4("255.255.255.255")).isTrue();
        Assertions.assertThat(InetAddressUtils.isIPv4("0.168.0.0")).isTrue();
    }

    @Test
    void testInvalidIPv4Address() {
        Assertions.assertThat(InetAddressUtils.isIPv4(" 127.0.0.1 ")).isFalse();  // Blanks not allowed
        Assertions.assertThat(InetAddressUtils.isIPv4("g.ar.ba.ge")).isFalse();
        Assertions.assertThat(InetAddressUtils.isIPv4("192.168.0")).isFalse();
        Assertions.assertThat(InetAddressUtils.isIPv4("256.255.255.255")).isFalse();
    }

    @Test
    void testValidIPv6Address() {
        Assertions.assertThat(InetAddressUtils.isIPv6Std("2001:0db8:0000:0000:0000:0000:1428:57ab")).isTrue();
        Assertions.assertThat(InetAddressUtils.isIPv6Std("2001:db8:0:0:0:0:1428:57ab")).isTrue();
        Assertions.assertThat(InetAddressUtils.isIPv6Std("0:0:0:0:0:0:0:0")).isTrue();
        Assertions.assertThat(InetAddressUtils.isIPv6Std("0:0:0:0:0:0:0:1")).isTrue();

        Assertions.assertThat(InetAddressUtils.isIPv6HexCompressed("2001:0db8:0:0::1428:57ab")).isTrue();
        Assertions.assertThat(InetAddressUtils.isIPv6HexCompressed("2001:0db8::1428:57ab")).isTrue();
        Assertions.assertThat(InetAddressUtils.isIPv6HexCompressed("2001:db8::1428:57ab")).isTrue();
        Assertions.assertThat(InetAddressUtils.isIPv6HexCompressed("::1")).isTrue();
        Assertions.assertThat(InetAddressUtils.isIPv6HexCompressed("::")).isTrue(); // http://tools.ietf.org/html/rfc4291#section-2.2

        Assertions.assertThat(InetAddressUtils.isIPv6("2001:0db8:0000:0000:0000:0000:1428:57ab")).isTrue();
        Assertions.assertThat(InetAddressUtils.isIPv6("2001:db8:0:0:0:0:1428:57ab")).isTrue();
        Assertions.assertThat(InetAddressUtils.isIPv6("0:0:0:0:0:0:0:0")).isTrue();
        Assertions.assertThat(InetAddressUtils.isIPv6("0:0:0:0:0:0:0:1")).isTrue();
        Assertions.assertThat(InetAddressUtils.isIPv6("2001:0db8:0:0::1428:57ab")).isTrue();
        Assertions.assertThat(InetAddressUtils.isIPv6("2001:0db8::1428:57ab")).isTrue();
        Assertions.assertThat(InetAddressUtils.isIPv6("2001:db8::1428:57ab")).isTrue();
        Assertions.assertThat(InetAddressUtils.isIPv6("::1")).isTrue();
        Assertions.assertThat(InetAddressUtils.isIPv6("::")).isTrue(); // http://tools.ietf.org/html/rfc4291#section-2.2

        //HTTPCORE-674 InetAddressUtils scoped ID support
        Assertions.assertThat(InetAddressUtils.isIPv6("fe80::1ff:fe23:4567:890a")).isTrue();
        Assertions.assertThat(InetAddressUtils.isIPv6("fe80::1ff:fe23:4567:890a%eth2")).isTrue();
        Assertions.assertThat(InetAddressUtils.isIPv6("fe80::1ff:fe23:4567:890a%3")).isTrue();
    }

    @Test
    void testInvalidIPv6Address() {
        Assertions.assertThat(InetAddressUtils.isIPv6("2001:0db8:0000:garb:age0:0000:1428:57ab")).isFalse();
        Assertions.assertThat(InetAddressUtils.isIPv6("2001:0gb8:0000:0000:0000:0000:1428:57ab")).isFalse();
        Assertions.assertThat(InetAddressUtils.isIPv6Std("0:0:0:0:0:0:0:0:0")).isFalse(); // Too many
        Assertions.assertThat(InetAddressUtils.isIPv6Std("0:0:0:0:0:0:0")).isFalse(); // Too few
        Assertions.assertThat(InetAddressUtils.isIPv6HexCompressed(":1")).isFalse();
        Assertions.assertThat(InetAddressUtils.isIPv6(":1")).isFalse();
        Assertions.assertThat(InetAddressUtils.isIPv6("2001:0db8::0000::57ab")).isFalse(); // Cannot have two contractions
        Assertions.assertThat(InetAddressUtils.isIPv6HexCompressed("1:2:3:4:5:6:7::9")).isFalse(); // too many fields before ::
        Assertions.assertThat(InetAddressUtils.isIPv6HexCompressed("1::3:4:5:6:7:8:9")).isFalse(); // too many fields after ::
        Assertions.assertThat(InetAddressUtils.isIPv6HexCompressed("::3:4:5:6:7:8:9")).isFalse(); // too many fields after ::
        Assertions.assertThat(InetAddressUtils.isIPv6("")).isFalse(); // empty

        //Invalid scoped IDs
        Assertions.assertThat(InetAddressUtils.isIPv6("fe80::1ff:fe23:4567:890a%eth2#")).isFalse();
        Assertions.assertThat(InetAddressUtils.isIPv6("fe80::1ff:fe23:4567:890a%3@")).isFalse();
        Assertions.assertThat(InetAddressUtils.isIPv6("fe80::1ff:fe23:4567:890a#eth2")).isFalse();
        Assertions.assertThat(InetAddressUtils.isIPv6("fe80::1ff:fe23:4567:890a%")).isFalse();
        Assertions.assertThat(InetAddressUtils.isIPv6("fe80::1ff:fe23:4567:890a%eth2!")).isFalse();
        Assertions.assertThat(InetAddressUtils.isIPv6("2001:0db8:0:0::1428:57ab%")).isFalse();
        Assertions.assertThat(InetAddressUtils.isIPv6("2001:0db8:0:0::1428:57ab%eth2#")).isFalse();
        Assertions.assertThat(InetAddressUtils.isIPv6("fe80::1ff:fe23:4567:890a%eth2#3")).isFalse();
        Assertions.assertThat(InetAddressUtils.isIPv6("2001:0db8:0:0::1428:57ab%eth2#3")).isFalse();
        Assertions.assertThat(InetAddressUtils.isIPv6("fe80::1ff:fe23:4567:890a%3#eth2")).isFalse();
        Assertions.assertThat(InetAddressUtils.isIPv6("2001:0db8:0:0::1428:57ab%3#eth2")).isFalse();
    }

    @Test
    void testValidIPv6BracketAddress() {
        Assertions.assertThat(InetAddressUtils.isIPv6URLBracketed("[2001:0db8:0000:0000:0000:0000:1428:57ab]")).isTrue();
        Assertions.assertThat(InetAddressUtils.isIPv6URLBracketed("[2001:db8:0:0:0:0:1428:57ab]")).isTrue();
        Assertions.assertThat(InetAddressUtils.isIPv6URLBracketed("[0:0:0:0:0:0:0:0]")).isTrue();
        Assertions.assertThat(InetAddressUtils.isIPv6URLBracketed("[0:0:0:0:0:0:0:1]")).isTrue();
        Assertions.assertThat(InetAddressUtils.isIPv6URLBracketed("[2001:0db8:0:0::1428:57ab]")).isTrue();
        Assertions.assertThat(InetAddressUtils.isIPv6URLBracketed("[2001:0db8::1428:57ab]")).isTrue();
        Assertions.assertThat(InetAddressUtils.isIPv6URLBracketed("[2001:db8::1428:57ab]")).isTrue();
        Assertions.assertThat(InetAddressUtils.isIPv6URLBracketed("[::1]")).isTrue();
        // http://tools.ietf.org/html/rfc4291#section-2.2
        Assertions.assertThat(InetAddressUtils.isIPv6URLBracketed("[::]")).isTrue();
    }

    @Test
    void testInvalidIPv6BracketAddress() {
        Assertions.assertThat(InetAddressUtils.isIPv6URLBracketed("2001:0db8:0000:garb:age0:0000:1428:57ab")).isFalse();
        Assertions.assertThat(InetAddressUtils.isIPv6URLBracketed("[2001:0db8:0000:garb:age0:0000:1428:57ab]")).isFalse();
        Assertions.assertThat(InetAddressUtils.isIPv6URLBracketed("2001:0gb8:0000:0000:0000:0000:1428:57ab")).isFalse();
        Assertions.assertThat(InetAddressUtils.isIPv6URLBracketed("[2001:0gb8:0000:0000:0000:0000:1428:57ab]")).isFalse();
        // Too many
        Assertions.assertThat(InetAddressUtils.isIPv6URLBracketed("0:0:0:0:0:0:0:0:0")).isFalse();
        Assertions.assertThat(InetAddressUtils.isIPv6URLBracketed("[0:0:0:0:0:0:0:0:0]")).isFalse();
        // Too few
        Assertions.assertThat(InetAddressUtils.isIPv6URLBracketed("0:0:0:0:0:0:0")).isFalse();
        Assertions.assertThat(InetAddressUtils.isIPv6URLBracketed("[0:0:0:0:0:0:0]")).isFalse();
        Assertions.assertThat(InetAddressUtils.isIPv6URLBracketed(":1")).isFalse();
        Assertions.assertThat(InetAddressUtils.isIPv6URLBracketed("[:1]")).isFalse();
        // Cannot have two contractions
        Assertions.assertThat(InetAddressUtils.isIPv6URLBracketed("2001:0db8::0000::57ab")).isFalse();
        Assertions.assertThat(InetAddressUtils.isIPv6URLBracketed("[2001:0db8::0000::57ab]")).isFalse();
        // too many fields before ::
        Assertions.assertThat(InetAddressUtils.isIPv6URLBracketed("1:2:3:4:5:6:7::9")).isFalse();
        Assertions.assertThat(InetAddressUtils.isIPv6URLBracketed("[1:2:3:4:5:6:7::9]")).isFalse();
        // too many fields after ::
        Assertions.assertThat(InetAddressUtils.isIPv6URLBracketed("1::3:4:5:6:7:8:9")).isFalse();
        Assertions.assertThat(InetAddressUtils.isIPv6URLBracketed("[1::3:4:5:6:7:8:9]")).isFalse();
        // too many fields after ::
        Assertions.assertThat(InetAddressUtils.isIPv6URLBracketed("::3:4:5:6:7:8:9")).isFalse();
        Assertions.assertThat(InetAddressUtils.isIPv6URLBracketed("[::3:4:5:6:7:8:9]")).isFalse();
        // empty
        Assertions.assertThat(InetAddressUtils.isIPv6URLBracketed("")).isFalse();
        Assertions.assertThat(InetAddressUtils.isIPv6URLBracketed("[]")).isFalse();

        // missing brackets
        Assertions.assertThat(InetAddressUtils.isIPv6URLBracketed("::")).isFalse();
        Assertions.assertThat(InetAddressUtils.isIPv6URLBracketed("::1")).isFalse();
        Assertions.assertThat(InetAddressUtils.isIPv6URLBracketed("2001:db8::1428:57ab")).isFalse();
    }

    @Test
    // Test HTTPCLIENT-1319
    void testInvalidIPv6AddressIncorrectGroupCount() {
        Assertions.assertThat(InetAddressUtils.isIPv6HexCompressed("1:2::4:5:6:7:8:9")).isFalse(); // too many fields in total
        Assertions.assertThat(InetAddressUtils.isIPv6HexCompressed("1:2:3:4:5:6::8:9")).isFalse(); // too many fields in total
    }

    @Test
    void testHasValidIPv6ColonCount() {
        Assertions.assertThat(InetAddressUtils.hasValidIPv6ColonCount("")).isFalse();
        Assertions.assertThat(InetAddressUtils.hasValidIPv6ColonCount(":")).isFalse();
        Assertions.assertThat(InetAddressUtils.hasValidIPv6ColonCount("127.0.0.1")).isFalse();
        Assertions.assertThat(InetAddressUtils.hasValidIPv6ColonCount(":0")).isFalse();
        Assertions.assertThat(InetAddressUtils.hasValidIPv6ColonCount("0:")).isFalse();
        Assertions.assertThat(InetAddressUtils.hasValidIPv6ColonCount("1:2:3:4:5:6:7:8:")).isFalse();
        Assertions.assertThat(InetAddressUtils.hasValidIPv6ColonCount("1:2:3:4:5:6:7:8:9")).isFalse();

        Assertions.assertThat(InetAddressUtils.hasValidIPv6ColonCount("2001:0db8:0000:0000:0000:0000:1428:57ab")).isTrue();
        Assertions.assertThat(InetAddressUtils.hasValidIPv6ColonCount("2001:db8:0:0:0:0:1428:57ab")).isTrue();
        Assertions.assertThat(InetAddressUtils.hasValidIPv6ColonCount("0:0:0:0:0:0:0:0")).isTrue();
        Assertions.assertThat(InetAddressUtils.hasValidIPv6ColonCount("0:0:0:0:0:0:0:1")).isTrue();
        Assertions.assertThat(InetAddressUtils.hasValidIPv6ColonCount("2001:0db8:0:0::1428:57ab")).isTrue();
        Assertions.assertThat(InetAddressUtils.hasValidIPv6ColonCount("2001:0db8::1428:57ab")).isTrue();
        Assertions.assertThat(InetAddressUtils.hasValidIPv6ColonCount("2001:db8::1428:57ab")).isTrue();
        Assertions.assertThat(InetAddressUtils.hasValidIPv6ColonCount("::1")).isTrue();
        Assertions.assertThat(InetAddressUtils.hasValidIPv6ColonCount("::")).isTrue(); // http://tools.ietf.org/html/rfc4291#section-2.2
    }

    @Test
    void testValidIPv4MappedIPv6Address() {
        Assertions.assertThat(InetAddressUtils.isIPv4MappedIPv6("::FFFF:1.2.3.4")).isTrue();
        Assertions.assertThat(InetAddressUtils.isIPv4MappedIPv6("::ffff:255.255.255.255")).isTrue();
    }

    @Test
    void testInValidIPv4MappedIPv6Address() {
        Assertions.assertThat(InetAddressUtils.isIPv4MappedIPv6("2001:0db8:0000:0000:0000:0000:1428:57ab")).isFalse();
        Assertions.assertThat(InetAddressUtils.isIPv4MappedIPv6("::ffff:1:2:3:4")).isFalse();
    }

    @Test
    void testValidIPv4MappedIPv6AddressWithLeadingZeros() {
        Assertions.assertThat(InetAddressUtils.isIPv4MappedIPv6("::ffff:001.002.003.004")).isTrue();
        Assertions.assertThat(InetAddressUtils.isIPv4MappedIPv6("::FFFF:000.000.000.255")).isTrue();
        Assertions.assertThat(InetAddressUtils.isIPv4MappedIPv6("::ffff:010.020.030.040")).isTrue();
    }

    @Test
    void testInvalidIPv4MappedIPv6AddressWithBadOctets() {
        // >255 not allowed
        Assertions.assertThat(InetAddressUtils.isIPv4MappedIPv6("::ffff:256.000.000.000")).isFalse();
        // too few octets
        Assertions.assertThat(InetAddressUtils.isIPv4MappedIPv6("::ffff:01.02.03")).isFalse();
        // too many digits in an octet (4 digits)
        Assertions.assertThat(InetAddressUtils.isIPv4MappedIPv6("::ffff:0255.000.000.000")).isFalse();
    }

    @Test
    void parseAuthorityWithZoneId_decodesDelimiter() throws URISyntaxException {
        final URIAuthority a = URIAuthority.parse("[fe80::1%25eth0]:8080");
        Assertions.assertThat(a).isNotNull();
        Assertions.assertThat(a.getHostName()).isEqualTo("fe80::1%eth0");
        Assertions.assertThat(a.getPort()).isEqualTo(8080);
        Assertions.assertThat(a.getUserInfo()).isNull();
    }

    @Test
    void formatAuthorityWithZoneId_emitsPercent25() {
        final URIAuthority a = new URIAuthority(null, "fe80::1%eth0", 8080);
        Assertions.assertThat(a.toString()).isEqualTo("[fe80::1%25eth0]:8080");
    }

    @Test
    void httpHost_toURI_formatsZoneId() {
        final HttpHost h = new HttpHost("http", "fe80::1%eth0", 8080);
        Assertions.assertThat(h.toURI()).isEqualTo("http://[fe80::1%25eth0]:8080");
    }

    @Test
    void uriBuilder_roundTrip_zoneId() throws Exception {
        final URI u = new URI("http://[fe80::1%25eth0]:8080/path?q=1");
        final URIBuilder b = new URIBuilder(u);
        Assertions.assertThat(b.getHost()).isEqualTo("fe80::1%eth0");
        final URI rebuilt = b.build();
        Assertions.assertThat(rebuilt.toASCIIString()).isEqualTo("http://[fe80::1%25eth0]:8080/path?q=1");
    }

    @Test
    void zoneId_validation_rejects_bad_pct() {
        // empty zone — invalid
        Assertions.assertThatThrownBy(() ->
                        URIAuthority.parse("[fe80::1%25]:80"))
                .isInstanceOf(IllegalArgumentException.class);

        // dangling percent-triplet — invalid
        Assertions.assertThatThrownBy(() ->
                        URIAuthority.parse("[fe80::1%25%]:80"))
                .isInstanceOf(IllegalArgumentException.class);

        // non-hex in percent-triplet — invalid
        Assertions.assertThatThrownBy(() ->
                        URIAuthority.parse("[fe80::1%25%G1]:80"))
                .isInstanceOf(IllegalArgumentException.class);

        // character not in RFC 3986 "unreserved" — invalid
        Assertions.assertThatThrownBy(() ->
                        URIAuthority.parse("[fe80::1%25!]:80"))
                .isInstanceOf(IllegalArgumentException.class);

    }

    @Test
    void zoneId_allows_unreserved_and_pct() throws URISyntaxException {
        final URIAuthority a = URIAuthority.parse("[fe80::1%25en1-._~x%20]:443");
        Assertions.assertThat(a).isNotNull();
        Assertions.assertThat(a.getHostName()).isEqualTo("fe80::1%en1-._~x ");
        Assertions.assertThat(a.toString()).isEqualTo("[fe80::1%25en1-._~x%20]:443");
    }

    @Test
    void inetAddressUtils_helper_accepts_zone() {
        Assertions.assertThat(ZoneIdSupport.looksLikeIPv6AddressPart("fe80::1%eth0")).isTrue();
        Assertions.assertThat(ZoneIdSupport.looksLikeIPv6AddressPart("fe80::1234:0:0:0:0:0%en1")).isTrue();
        Assertions.assertThat(ZoneIdSupport.looksLikeIPv6AddressPart("not-an-ip")).isFalse();
    }

}
