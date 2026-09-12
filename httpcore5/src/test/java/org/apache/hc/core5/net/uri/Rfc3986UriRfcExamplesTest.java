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
package org.apache.hc.core5.net.uri;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hc.core5.net.uri.internal.paths.DotSegments;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RFC 3986 compliance tests for our Rfc3986Uri implementation.
 * Includes the resolution matrix and dot-segment removal examples straight from §5.2 / §5.4.
 *
 * @since 5.6
 */
@DisplayName("RFC 3986 – Resolution & Dot-Segment Removal Examples")
final class Rfc3986UriRfcExamplesTest {

    private static Rfc3986Uri U(final String s) {
        return Rfc3986Uri.parse(s);
    }

    @Test
    @DisplayName("§5.4.1 Resolution Examples (base: http://a/b/c/d;p?q)")
    void resolutionMatrix() {
        final Rfc3986Uri base = U("http://a/b/c/d;p?q");

        // Absolute and net-path
        assertEquals("g:h", Rfc3986Uri.resolve(base, U("g:h")).toString());
        assertEquals("http://a/b/c/g", Rfc3986Uri.resolve(base, U("g")).toString());
        assertEquals("http://a/b/c/g", Rfc3986Uri.resolve(base, U("./g")).toString());
        assertEquals("http://a/b/c/g/", Rfc3986Uri.resolve(base, U("g/")).toString());
        assertEquals("http://a/g", Rfc3986Uri.resolve(base, U("/g")).toString());
        assertEquals("http://g", Rfc3986Uri.resolve(base, U("//g")).toString());

        // Query & fragment interactions
        assertEquals("http://a/b/c/d;p?y", Rfc3986Uri.resolve(base, U("?y")).toString());
        assertEquals("http://a/b/c/g?y", Rfc3986Uri.resolve(base, U("g?y")).toString());
        assertEquals("http://a/b/c/d;p?q#s", Rfc3986Uri.resolve(base, U("#s")).toString()); // fragment-only keeps base query
        assertEquals("http://a/b/c/g#s", Rfc3986Uri.resolve(base, U("g#s")).toString());
        assertEquals("http://a/b/c/g;x", Rfc3986Uri.resolve(base, U("g;x")).toString());
        assertEquals("http://a/b/c/g;x?y#s", Rfc3986Uri.resolve(base, U("g;x?y#s")).toString());

        // No-op and dot-segments
        assertEquals("http://a/b/c/d;p?q", Rfc3986Uri.resolve(base, U("")).toString());
        assertEquals("http://a/b/c/", Rfc3986Uri.resolve(base, U(".")).toString());
        assertEquals("http://a/b/c/", Rfc3986Uri.resolve(base, U("./")).toString());
        assertEquals("http://a/b/", Rfc3986Uri.resolve(base, U("..")).toString());
        assertEquals("http://a/b/", Rfc3986Uri.resolve(base, U("../")).toString());
        assertEquals("http://a/b/g", Rfc3986Uri.resolve(base, U("../g")).toString());
        assertEquals("http://a/", Rfc3986Uri.resolve(base, U("../..")).toString());
        assertEquals("http://a/", Rfc3986Uri.resolve(base, U("../../")).toString());
        assertEquals("http://a/g", Rfc3986Uri.resolve(base, U("../../g")).toString());
    }

    @Test
    @DisplayName("§5.2.4 Dot-Segment Removal – canonical examples")
    void dotSegmentRemovalExamples() {
        // Examples adapted from RFC table (input -> expected)
        assertEquals("/a/b/c/./../../g", "/a/b/c/./../../g"); // sanity on source
        assertEquals("/a/g", DotSegments.remove("/a/b/c/./../../g"));

        // Trailing slash preservation for "." and ".." per §5.2.4
        assertEquals("/a/b/c/", DotSegments.remove("/a/b/c/."));   // keep trailing slash
        assertEquals("/a/b/", DotSegments.remove("/a/b/c/.."));  // keep trailing slash

        // Leading and internal edge cases
        assertEquals("/", DotSegments.remove("/."));         // root with trailing slash
        assertEquals("/", DotSegments.remove("/.."));        // cannot go above root
        assertEquals("", DotSegments.remove(""));           // empty stays empty
        assertEquals("..", DotSegments.remove(".."));         // relative upward kept in relative paths
        assertEquals("../x", DotSegments.remove("../x"));
        assertEquals("a/b", DotSegments.remove("a/b"));
        assertEquals("/a//b/", DotSegments.remove("/a//b/"));     // double slash preserved structurally
    }

    @Test
    @DisplayName("Fragment-only reference: keep base query, replace fragment")
    void fragmentOnlyKeepsQuery() {
        final Rfc3986Uri base = U("http://a/b/c/d;p?q");
        assertEquals("http://a/b/c/d;p?q#frag", Rfc3986Uri.resolve(base, U("#frag")).toString());
    }

    @Test
    @DisplayName("Relative-path merge when base has authority and empty path")
    void mergeWhenBasePathEmpty() {
        final Rfc3986Uri base = U("http://a?q");
        // base path is empty; merging a relative path must prefix with "/" (§5.2.3)
        assertEquals("http://a/g", Rfc3986Uri.resolve(base, U("g")).toString());
        assertEquals("http://a/g/h", Rfc3986Uri.resolve(base, U("g/h")).toString());
    }

    @Test
    @DisplayName("Equivalence: case-insensitive scheme/host, unreserved decoding, uppercased pct-hex")
    void equivalenceNormalization() {
        final Rfc3986Uri a = U("HTTP://EXAMPLE.COM/%7euser");
        final Rfc3986Uri b = U("http://example.com/~user");
        assertTrue(a.equivalentTo(b));

        final Rfc3986Uri c = U("http://www.example.com/%3c");
        final Rfc3986Uri d = U("http://www.example.com/%3C");
        assertTrue(c.equivalentTo(d));
    }

    @Test
    @DisplayName("IPv6 literal host parsing is preserved with brackets")
    void ipv6LiteralAuthority() {
        final Rfc3986Uri u = U("http://[2001:db8::1]:8080/a");
        assertEquals("[2001:db8::1]", u.getHost());
        assertEquals(8080, u.getPort());
        assertEquals("/a", u.getPath());
        assertEquals("http://[2001:db8::1]:8080/a", u.toString());
    }

    @Test
    @DisplayName("Percent-encoded ampersand in path is preserved (HTTPCLIENT-1995 class of bugs)")
    void encodedAmpersandInPathPreserved() {
        final Rfc3986Uri u = U("http://example.com/a%26b/c");
        assertEquals("/a%26b/c", u.getPath());
        assertEquals("http://example.com/a%26b/c", u.toString());
    }

    @Test
    @DisplayName("Round-trip: canonical form may lowercase host")
    void rawRoundTrip() {
        final String s = "scheme://user:pass@Host.EXAMPLE:1234/a/%7e/b;c;d?p=%26#Frag";
        final Rfc3986Uri u = Rfc3986Uri.parse(s);

        assertEquals("scheme", u.getScheme());
        assertEquals("user:pass", u.getUserInfo());
        assertEquals("host.example", u.getHost()); // normalized
        assertEquals(1234, u.getPort());
        assertEquals("/a/%7e/b;c;d", u.getPath());
        assertEquals("p=%26", u.getQuery());
        assertEquals("Frag", u.getFragment());

        // expect canonical serialization (host lower-cased)
        final String expected = "scheme://user:pass@host.example:1234/a/%7e/b;c;d?p=%26#Frag";
        assertEquals(expected, u.toString());
    }
}
