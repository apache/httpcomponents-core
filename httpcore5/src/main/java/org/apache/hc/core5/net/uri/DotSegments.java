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

import java.util.ArrayDeque;
import java.util.Deque;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;

/**
 * RFC 3986 §5.2.4 dot-segment removal.
 * <p>
 * - Preserves empty segments inside the path (e.g. {@code "/a//b"}).
 * - Does <strong>not</strong> preserve the artificial leading empty segment of absolute paths.
 * - Ensures a trailing slash when the terminal segment is {@code "."} or {@code ".."},
 * except for the pure relative {@code ".."} (no trailing slash).
 *
 */
@Internal
@Contract(threading = ThreadingBehavior.STATELESS)
final class DotSegments {

    static String remove(final String path) {
        if (path == null || path.isEmpty()) {
            return path == null ? null : "";
        }

        final boolean absolute = path.startsWith("/");
        final boolean hadTrailingSlash = path.endsWith("/");

        final Deque<String> out = new ArrayDeque<>();

        int i = 0;
        final int n = path.length();
        boolean firstSegment = true;     // suppress the artificial leading "" for absolute paths
        boolean forceTrailingSlash = false; // terminal "." or ".." wants slash in most cases

        while (i <= n) {
            final int j = i < n ? path.indexOf('/', i) : -1;

            final String seg;
            if (j == -1) {
                seg = path.substring(i, n);
                i = n + 1;
            } else {
                seg = path.substring(i, j);
                i = j + 1;
            }

            // Skip the artificial leading empty segment for absolute paths.
            if (firstSegment && absolute && seg.isEmpty()) {
                firstSegment = false;
                if (j == -1) {
                    break; // path was "/" only
                }
                continue;
            }
            firstSegment = false;

            final boolean isLast = j == -1;

            if (seg.equals(".")) {
                // Drop "."; if last, remember to add trailing slash (except for empty relative).
                if (isLast && (absolute || !out.isEmpty())) {
                    forceTrailingSlash = true;
                }
            } else if (seg.equals("..")) {
                if (!out.isEmpty()) {
                    final String last = out.peekLast();
                    if (!last.equals("..")) {
                        out.removeLast();
                    } else if (!absolute) {
                        out.addLast("..");
                    }
                } else if (!absolute) {
                    out.addLast("..");
                }
                // Terminal ".." prefers trailing slash, but not for pure relative "..".
                if (isLast && (absolute || !out.isEmpty())) {
                    forceTrailingSlash = true;
                }
            } else {
                // Normal (and internal empty) segments preserved.
                out.addLast(seg);
            }

            if (j == -1) {
                break;
            }
        }

        // Rebuild
        final StringBuilder b = new StringBuilder(path.length());
        if (absolute) {
            b.append('/');
        }
        boolean first = true;
        for (final String seg : out) {
            if (!first) {
                b.append('/');
            }
            b.append(seg);
            first = false;
        }

        // Keep original trailing slash OR add one for terminal "."/".."
        // BUT: do not add for pure relative ".." (i.e., out = [".."], not absolute, and original had no trailing slash).
        final boolean wantsTrailing =
                hadTrailingSlash
                        || forceTrailingSlash && (absolute || !out.isEmpty() && !"..".equals(out.peekLast()));

        if (wantsTrailing && (b.length() == 0 || b.charAt(b.length() - 1) != '/')) {
            b.append('/');
        }

        // Absolute path that reduced to empty -> "/"
        if (absolute && b.length() == 0) {
            b.append('/');
        }

        return b.toString();
    }

    private DotSegments() {
    }
}
