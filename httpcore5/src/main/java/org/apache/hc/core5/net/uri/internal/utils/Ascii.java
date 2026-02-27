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

package org.apache.hc.core5.net.uri.internal.utils;

import java.util.Arrays;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;

/**
 * Branch-light ASCII helpers (no regex, no Character classes).
 *
 * @since 5.4
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public final class Ascii {

    // Bit flags for the CLASS table
    private static final byte ALPHA = 1 << 0;
    private static final byte DIGIT = 1 << 1;
    private static final byte HEXMASK = 1 << 2;
    private static final byte GENDELIM = 1 << 3;
    private static final byte SUBDELIM = 1 << 4;
    private static final byte UNRESERVED = 1 << 5;

    /**
     * Per-ASCII classification flags.
     */
    private static final byte[] CLASS = new byte[128];

    /**
     * ASCII → hex value (0..15) or -1 if not a hex digit.
     */
    private static final byte[] HEX_VALUE = new byte[128];

    static {
        // init HEX_VALUE = -1
        Arrays.fill(HEX_VALUE, (byte) -1);

        // ALPHA + unreserved
        for (int c = 'A'; c <= 'Z'; c++) {
            CLASS[c] |= ALPHA | UNRESERVED;
        }
        for (int c = 'a'; c <= 'z'; c++) {
            CLASS[c] |= ALPHA | UNRESERVED;
        }

        // DIGIT + unreserved + hex values 0..9
        for (int c = '0'; c <= '9'; c++) {
            CLASS[c] |= DIGIT | UNRESERVED | HEXMASK;
            HEX_VALUE[c] = (byte) (c - '0');
        }

        // Hex A..F / a..f → 10..15
        for (int c = 'A'; c <= 'F'; c++) {
            CLASS[c] |= HEXMASK;
            HEX_VALUE[c] = (byte) (10 + (c - 'A'));
        }
        for (int c = 'a'; c <= 'f'; c++) {
            CLASS[c] |= HEXMASK;
            HEX_VALUE[c] = (byte) (10 + (c - 'a'));
        }

        // unreserved punctuation - . _ ~
        CLASS['-'] |= UNRESERVED;
        CLASS['.'] |= UNRESERVED;
        CLASS['_'] |= UNRESERVED;
        CLASS['~'] |= UNRESERVED;

        // gen-delims : / ? # [ ] @
        CLASS[':'] |= GENDELIM;
        CLASS['/'] |= GENDELIM;
        CLASS['?'] |= GENDELIM;
        CLASS['#'] |= GENDELIM;
        CLASS['['] |= GENDELIM;
        CLASS[']'] |= GENDELIM;
        CLASS['@'] |= GENDELIM;

        // sub-delims ! $ & ' ( ) * + , ; =
        CLASS['!'] |= SUBDELIM;
        CLASS['$'] |= SUBDELIM;
        CLASS['&'] |= SUBDELIM;
        CLASS['\''] |= SUBDELIM;
        CLASS['('] |= SUBDELIM;
        CLASS[')'] |= SUBDELIM;
        CLASS['*'] |= SUBDELIM;
        CLASS['+'] |= SUBDELIM;
        CLASS[','] |= SUBDELIM;
        CLASS[';'] |= SUBDELIM;
        CLASS['='] |= SUBDELIM;
    }

    private Ascii() {
    }

    /**
     * @return {@code true} if {@code c} is 7-bit US-ASCII.
     */
    public static boolean isAscii(final int c) {
        return (c & ~0x7F) == 0;
    }

    public static boolean isAlpha(final int c) {
        return isAscii(c) && (CLASS[c] & ALPHA) != 0;
    }

    public static boolean isDigit(final int c) {
        return isAscii(c) && (CLASS[c] & DIGIT) != 0;
    }

    public static boolean isUnreserved(final int c) {
        return isAscii(c) && (CLASS[c] & UNRESERVED) != 0;
    }

    /**
     * Hex value for ASCII hex char; returns -1 if not hex.
     * Accepts '0'..'9','A'..'F','a'..'f'.
     */
    public static int hexValue(final int c) {
        return isAscii(c) ? HEX_VALUE[c] : -1;
    }

    /**
     * Alias requested by user code.
     */
    public static int hexVal(final int c) {
        return hexValue(c);
    }

    /**
     * Converts a 4-bit nibble (0..15) to uppercase hex ASCII ('0'..'9','A'..'F').
     *
     * @throws IllegalArgumentException if value is outside 0..15
     */
    public static char toHexUpper(final int nibble) {
        if ((nibble & ~0xF) != 0) {
            throw new IllegalArgumentException("nibble out of range: " + nibble);
        }
        return (char) (nibble < 10 ? ('0' + nibble) : ('A' + (nibble - 10)));
    }

    /**
     * ASCII-lowercase conversion that avoids locale effects and allocations
     * when the input is already lowercase.
     */
    public static String lowerAscii(final String s) {
        if (s == null) {
            return null;
        }
        final int n = s.length();
        for (int i = 0; i < n; i++) {
            final char ch = s.charAt(i);
            if (ch >= 'A' && ch <= 'Z') {
                final char[] a = s.toCharArray();
                for (int j = i; j < n; j++) {
                    final char cj = a[j];
                    if (cj >= 'A' && cj <= 'Z') {
                        a[j] = (char) (cj + 0x20);
                    }
                }
                return new String(a);
            }
        }
        return s;
    }

    /**
     * Fast scan helpers (used by parsers).
     */
    public static int indexOf(final char[] a, final char ch, final int from, final int toExcl) {
        for (int i = from; i < toExcl; i++) {
            if (a[i] == ch) {
                return i;
            }
        }
        return -1;
    }

}