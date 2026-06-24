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

import java.util.Objects;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.net.uri.Rfc3986Uri;
import org.apache.hc.core5.net.uri.internal.authorities.HostOps;
import org.apache.hc.core5.net.uri.internal.encoding.PercentCodec;
import org.apache.hc.core5.net.uri.internal.utils.Ascii;

/**
 * RFC 3986 equivalence utilities (§6.2).
 *
 * @since 5.5
 */
@Internal
@Contract(threading = ThreadingBehavior.STATELESS)
public final class Rfc3986Equivalence {

    private Rfc3986Equivalence() {
    }

    public static boolean equivalent(final Rfc3986Uri a, final Rfc3986Uri b) {
        if (a == b) {
            return true;
        }
        if (b == null) {
            return false;
        }
        if (!Objects.equals(Ascii.lowerAscii(a.getScheme()), Ascii.lowerAscii(b.getScheme()))) {
            return false;
        }
        if (!Objects.equals(HostOps.lowerRegNamePreserveIPv6(a.getHost()),
                HostOps.lowerRegNamePreserveIPv6(b.getHost()))) {
            return false;
        }
        if (a.getPort() != b.getPort()) {
            return false;
        }
        if (!Objects.equals(norm(a.getPath()), norm(b.getPath()))) {
            return false;
        }
        if (!Objects.equals(norm(a.getQuery()), norm(b.getQuery()))) {
            return false;
        }
        if (!Objects.equals(norm(a.getFragment()), norm(b.getFragment()))) {
            return false;
        }
        return Objects.equals(a.getUserInfo(), b.getUserInfo());
    }

    private static String norm(final String s) {
        if (s == null) {
            return null;
        }
        return PercentCodec.uppercaseHexInPercents(PercentCodec.decode(s, true));
    }
}
