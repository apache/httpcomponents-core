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

package org.apache.hc.core5.net.uri.internal.uris;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.net.uri.Rfc3986Uri;
import org.apache.hc.core5.net.uri.internal.authorities.HostOps;
import org.apache.hc.core5.net.uri.internal.encoding.PercentCodec;
import org.apache.hc.core5.net.uri.internal.paths.DotSegments;
import org.apache.hc.core5.net.uri.internal.utils.Ascii;

/**
 * Normalization and canonicalization helpers.
 *
 * @since 5.5
 */
@Internal
@Contract(threading = ThreadingBehavior.STATELESS)
public final class Rfc3986Normalizer {

    private Rfc3986Normalizer() {
    }

    public static Rfc3986Uri normalizePath(final Rfc3986Uri u) {
        final boolean canNormalizePath = u.getHost() != null || u.getPath() != null && u.getPath().startsWith("/");
        final String p = canNormalizePath ? DotSegments.remove(u.getPath()) : u.getPath();
        if (p != null && p.equals(u.getPath())) {
            return u;
        }
        return rebuild(u.getScheme(), u.getUserInfo(), u.getHost(), u.getPort(), p, u.getQuery(), u.getFragment());
    }

    /**
     * Canonicalization used by URIBuilder#optimize():
     * - lower-case scheme and reg-name host
     * - remove dot-segments for absolute/authority paths
     * - decode %XX for unreserved only; uppercase remaining escapes
     * - strict-encode path; normalize query/fragment escapes
     */
    public static Rfc3986Uri optimize(final Rfc3986Uri u) {
        final String s2 = u.getScheme() == null ? null : Ascii.lowerAscii(u.getScheme());
        final String h2 = HostOps.lowerRegNamePreserveIPv6(u.getHost());

        final boolean canNormalizePath = u.getHost() != null || u.getPath() != null && u.getPath().startsWith("/");
        final String p0 = canNormalizePath ? DotSegments.remove(u.getPath()) : u.getPath();

        final String p1 = PercentCodec.decode(p0, true);
        final String p2 = PercentCodec.uppercaseHexInPercents(p1);
        final String p3 = PercentCodec.encodeStrictPath(p2);

        final String q1 = u.getQuery() == null ? null
                : PercentCodec.uppercaseHexInPercents(PercentCodec.decode(u.getQuery(), true));
        final String f1 = u.getFragment() == null ? null
                : PercentCodec.uppercaseHexInPercents(PercentCodec.decode(u.getFragment(), true));

        return rebuild(s2, u.getUserInfo(), h2, u.getPort(), p3, q1, f1);
    }

    private static Rfc3986Uri rebuild(final String s, final String ui, final String h, final int port,
                                      final String p, final String q, final String f) {
        final String raw = Rfc3986Renderer.build(s, ui, h, port, p, q, f);
        return new Rfc3986Uri(raw, s, ui, h, port, p, q, f);
    }
}
