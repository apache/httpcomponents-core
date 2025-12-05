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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hc.core5.net.uri.internal.paths.DotSegments;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RFC 3986 URI – parsing, normalization, and resolution")
final class Rfc3986UriTest {

    @Test
    void parseAbsoluteHttp() {
        final Rfc3986Uri u = Rfc3986Uri.parse("http://user:pass@example.com:8080/a/b/c%20d?x=1&y=2#frag");
        assertEquals("http", u.getScheme());
        assertEquals("user:pass", u.getUserInfo());
        assertEquals("example.com", u.getHost());
        assertEquals(8080, u.getPort());
        assertEquals("/a/b/c%20d", u.getPath());
        assertEquals("x=1&y=2", u.getQuery());
        assertEquals("frag", u.getFragment());
        assertEquals("http://user:pass@example.com:8080/a/b/c%20d?x=1&y=2#frag", u.toString());
    }

    @Test
    void preservePercentEncodedAmpersandInPath() {
        final Rfc3986Uri u = Rfc3986Uri.parse("http://example.com/a%26b/c");
        // Guard for HTTPCLIENT-1995 type regressions
        assertEquals("/a%26b/c", u.getPath());
    }

    @Test
    void equivalenceWithUnreservedDecoding() {
        final Rfc3986Uri a = Rfc3986Uri.parse("HTTP://EXAMPLE.COM/%7euser");
        final Rfc3986Uri b = Rfc3986Uri.parse("http://example.com/~user");
        assertTrue(a.equivalentTo(b));
    }

    @Test
    void parseIpv6Literal() {
        final Rfc3986Uri u = Rfc3986Uri.parse("http://[2001:db8::1]/a");
        assertEquals("[2001:db8::1]", u.getHost());
        assertEquals("/a", u.getPath());
    }

    @Test
    void parseAuthorityLessRelative() {
        final Rfc3986Uri u = Rfc3986Uri.parse("a/b?c#d");
        assertNull(u.getHost());
        assertEquals("a/b", u.getPath());
        assertEquals("c", u.getQuery());
        assertEquals("d", u.getFragment());
    }

    @Test
    void dotSegmentRemoval() {
        final String in = "/a/b/c/./../../g";
        final String out = DotSegments.remove(in);
        assertEquals("/a/g", out);
    }

    @Test
    void resolveExamplesFromRfc() {
        final Rfc3986Uri base = Rfc3986Uri.parse("http://a/b/c/d;p?q");

        assertEquals("g:h", Rfc3986Uri.resolve(base, Rfc3986Uri.parse("g:h")).toString());
        assertEquals("http://a/b/c/g", Rfc3986Uri.resolve(base, Rfc3986Uri.parse("g")).toString());
        assertEquals("http://a/b/c/g", Rfc3986Uri.resolve(base, Rfc3986Uri.parse("./g")).toString());
        assertEquals("http://a/b/c/g/", Rfc3986Uri.resolve(base, Rfc3986Uri.parse("g/")).toString());
        assertEquals("http://a/g", Rfc3986Uri.resolve(base, Rfc3986Uri.parse("/g")).toString());
        assertEquals("http://g", Rfc3986Uri.resolve(base, Rfc3986Uri.parse("//g")).toString());
        assertEquals("http://a/b/c/d;p?y", Rfc3986Uri.resolve(base, Rfc3986Uri.parse("?y")).toString());
        assertEquals("http://a/b/c/g?y", Rfc3986Uri.resolve(base, Rfc3986Uri.parse("g?y")).toString());
        assertEquals("http://a/b/c/d;p?q#s", Rfc3986Uri.resolve(base, Rfc3986Uri.parse("#s")).toString());
        assertEquals("http://a/b/c/g#s", Rfc3986Uri.resolve(base, Rfc3986Uri.parse("g#s")).toString());
        assertEquals("http://a/b/c/g;x", Rfc3986Uri.resolve(base, Rfc3986Uri.parse("g;x")).toString());
        assertEquals("http://a/b/c/g;x?y#s", Rfc3986Uri.resolve(base, Rfc3986Uri.parse("g;x?y#s")).toString());
        assertEquals("http://a/b/c/d;p?q", Rfc3986Uri.resolve(base, Rfc3986Uri.parse("")).toString());
        assertEquals("http://a/b/c/", Rfc3986Uri.resolve(base, Rfc3986Uri.parse(".")).toString());
        assertEquals("http://a/b/c/", Rfc3986Uri.resolve(base, Rfc3986Uri.parse("./")).toString());
        assertEquals("http://a/b/", Rfc3986Uri.resolve(base, Rfc3986Uri.parse("..")).toString());
        assertEquals("http://a/b/", Rfc3986Uri.resolve(base, Rfc3986Uri.parse("../")).toString());
        assertEquals("http://a/b/g", Rfc3986Uri.resolve(base, Rfc3986Uri.parse("../g")).toString());
        assertEquals("http://a/", Rfc3986Uri.resolve(base, Rfc3986Uri.parse("../..")).toString());
        assertEquals("http://a/", Rfc3986Uri.resolve(base, Rfc3986Uri.parse("../../")).toString());
        assertEquals("http://a/g", Rfc3986Uri.resolve(base, Rfc3986Uri.parse("../../g")).toString());
    }
}
