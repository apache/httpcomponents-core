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

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;

/**
 * Tiny tokenizer for URI scanning.
 *
 */
@Internal
@Contract(threading = ThreadingBehavior.UNSAFE)
final class UriTokenizer {

    final char[] buf;
    int pos;

    UriTokenizer(final char[] buf) {
        this.buf = buf;
        this.pos = 0;
    }

    boolean hasRemaining() {
        return pos < buf.length;
    }

    char current() {
        return buf[pos];
    }

    char peekAhead(final int n) {
        final int i = pos + n;
        return i < buf.length ? buf[i] : 0;
    }

    /**
     * Returns index of first char after the scheme, or -1 if no scheme.
     */
    int scanScheme() {
        int i = pos;
        if (i >= buf.length) {
            return -1;
        }
        if (!Ascii.isAlpha(buf[i])) {
            return -1; // first must be ALPHA
        }
        i++;
        while (i < buf.length) {
            final char c = buf[i];
            if (!(Ascii.isAlpha(c) || Ascii.isDigit(c) || c == '+' || c == '-' || c == '.')) {
                return i;
            }
            i++;
        }
        return -1;
    }

    /**
     * Scan until one of the {@code stopChars} or end; returns position.
     */
    int scanUntil(final String stopChars) {
        final boolean[] stop = new boolean[128];
        for (int i = 0; i < stopChars.length(); i++) {
            final char c = stopChars.charAt(i);
            if (c < 128) {
                stop[c] = true;
            }
        }
        final int len = buf.length;
        while (pos < len) {
            final char c = buf[pos];
            if (c < 128 && stop[c]) {
                return pos;
            }
            pos++;
        }
        return pos;
    }
}
