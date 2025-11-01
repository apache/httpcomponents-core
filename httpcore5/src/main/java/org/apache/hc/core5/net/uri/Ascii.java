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
 * ASCII classification & utilities backed by a compact lookup table.
 *
 */
@Internal
@Contract(threading = ThreadingBehavior.STATELESS)
final class Ascii {

    private static final byte ALPHA = 1 << 0;
    private static final byte DIGIT = 1 << 1;
    private static final byte UNRESERVED = 1 << 2;
    private static final byte SUBDELIM = 1 << 3;
    private static final byte GENDELIM = 1 << 4;
    private static final byte HEXDIGIT = 1 << 5;
    private static final byte PCHAR_EX = 1 << 6; // ':' or '@'

    private static final byte[] CLASS = new byte[128];

    static {
        // alpha
        for (int c = 'A'; c <= 'Z'; c++) {
            CLASS[c] |= ALPHA;
        }
        for (int c = 'a'; c <= 'z'; c++) {
            CLASS[c] |= ALPHA;
        }
        // digit
        for (int c = '0'; c <= '9'; c++) {
            CLASS[c] |= DIGIT | HEXDIGIT;
        }
        for (int c = 'A'; c <= 'F'; c++) {
            CLASS[c] |= HEXDIGIT;
        }
        for (int c = 'a'; c <= 'f'; c++) {
            CLASS[c] |= HEXDIGIT;
        }

        // unreserved = alpha / digit / "-" / "." / "_" / "~"
        set('-', UNRESERVED);
        set('.', UNRESERVED);
        set('_', UNRESERVED);
        set('~', UNRESERVED);
        for (int c = 0; c < 128; c++) {
            if ((CLASS[c] & (ALPHA | DIGIT)) != 0) {
                CLASS[c] |= UNRESERVED;
            }
        }

        // sub-delims = "!" / "$" / "&" / "'" / "(" / ")" / "*" / "+" / "," / ";" / "="
        for (final char ch : new char[]{'!', '$', '&', '\'', '(', ')', '*', '+', ',', ';', '='}) {
            set(ch, SUBDELIM);
        }

        // gen-delims = ":" / "/" / "?" / "#" / "[" / "]" / "@"
        for (final char ch : new char[]{':', '/', '?', '#', '[', ']', '@'}) {
            set(ch, GENDELIM);
        }

        // pchar extras (':' '@') – used to define pchar without allowing raw '%'
        set(':', PCHAR_EX);
        set('@', PCHAR_EX);
    }

    private static void set(final char c, final byte mask) {
        CLASS[c] |= mask;
    }

    static boolean isAscii(final int c) {
        return (c & ~0x7F) == 0;
    }

    static boolean isAlpha(final int c) {
        return isAscii(c) && (CLASS[c] & ALPHA) != 0;
    }

    static boolean isDigit(final int c) {
        return isAscii(c) && (CLASS[c] & DIGIT) != 0;
    }

    static boolean isHexDigit(final int c) {
        return isAscii(c) && (CLASS[c] & HEXDIGIT) != 0;
    }

    static boolean isUnreserved(final int c) {
        return isAscii(c) && (CLASS[c] & UNRESERVED) != 0;
    }

    static boolean isSubDelim(final int c) {
        return isAscii(c) && (CLASS[c] & SUBDELIM) != 0;
    }

    static boolean isGenDelim(final int c) {
        return isAscii(c) && (CLASS[c] & GENDELIM) != 0;
    }

    /**
     * pchar = unreserved / sub-delims / ":" / "@"; '%' not included (reserved for pct-encoding).
     */
    static boolean isPchar(final int c) {
        return isAscii(c) && ((CLASS[c] & UNRESERVED) != 0 || (CLASS[c] & SUBDELIM) != 0 || (CLASS[c] & PCHAR_EX) != 0);
    }

    /**
     * Lower-case ASCII letters; leaves non-ASCII untouched.
     */
    static String lowerAscii(final String s) {
        if (s == null) {
            return null;
        }
        boolean any = false;
        for (int i = 0; i < s.length(); i++) {
            final char ch = s.charAt(i);
            if (ch >= 'A' && ch <= 'Z') {
                any = true;
                break;
            }
        }
        if (!any) {
            return s;
        }
        final char[] a = s.toCharArray();
        for (int i = 0; i < a.length; i++) {
            final char ch = a[i];
            if (ch >= 'A' && ch <= 'Z') {
                a[i] = (char) (ch + 32);
            }
        }
        return new String(a);
    }

    private Ascii() {
    }
}
