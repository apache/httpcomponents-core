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

package org.apache.hc.core5.util;

import java.util.Locale;

import org.apache.hc.core5.annotation.Internal;

/**
 * Tests and converts Strings and CharSequence.
 *
 * @since 4.3
 */
public final class TextUtils {

    private TextUtils() {
        // Do not allow utility class to be instantiated.
    }

    /**
     * Tests whether the parameter is null or of zero length.
     *
     * @param s The CharSequence to test.
     * @return whether the parameter is null or of zero length.
     */
    public static boolean isEmpty(final CharSequence s) {
        return length(s) == 0;
    }

    /**
     * <p>Checks if a CharSequence is empty (""), null or whitespace only.</p>
     *
     * <p>Whitespace is defined by {@link Character#isWhitespace(char)}.</p>
     *
     * <pre>
     * TextUtils.isBlank(null)      = true
     * TextUtils.isBlank("")        = true
     * TextUtils.isBlank(" ")       = true
     * TextUtils.isBlank("abg")     = false
     * TextUtils.isBlank("  abg  ") = false
     * </pre>
     *
     * @param s  the CharSequence to check, may be null
     * @return {@code true} if the CharSequence is null, empty or whitespace only
     */
    public static boolean isBlank(final CharSequence s) {
        final int strLen = length(s);
        if (strLen == 0) {
            return true;
        }
        for (int i = 0; i < strLen; i++) {
            if (!Character.isWhitespace(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Gets a CharSequence length or {@code 0} if the CharSequence is
     * {@code null}.
     *
     * @param cs
     *            a CharSequence or {@code null}
     * @return CharSequence length or {@code 0} if the CharSequence is
     *         {@code null}.
     * @since 5.1
     */
    public static int length(final CharSequence cs) {
        return cs == null ? 0 : cs.length();
    }

    /**
     * Tests whether a CharSequence contains any whitespace.
     *
     * @param s The CharSequence to test.
     * @return whether a CharSequence contains any whitespace.
     * @since 4.4
     */
    public static boolean containsBlanks(final CharSequence s) {
        final int strLen = length(s);
        if (strLen == 0) {
            return false;
        }
        for (int i = 0; i < strLen; i++) {
            if (Character.isWhitespace(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns a hexadecimal string with lowercase letters, representing the
     * values of the {@code bytes}.
     *
     * @param bytes whose hex string should be created
     * @return hex string for the bytes
     *
     * @since 5.0
     */
    private static final char[] HEX_CHARS = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd',
            'e', 'f' };

    public static String toHexString(final byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        final StringBuilder buffer = new StringBuilder(bytes.length * 2);
        for (final byte b : bytes) {
            buffer.append(HEX_CHARS[(0xf0 & b) >>> 4]).append(HEX_CHARS[0x0f & b]);
        }
        return buffer.toString();
    }

    /**
     * Converts a String to its lower case representation using {@link Locale#ROOT}.
     *
     * @param s The String to convert
     * @return The converted String.
     * @since 5.2
     */
    public static String toLowerCase(final String s) {
        if (s == null) {
            return null;
        }
        return s.toLowerCase(Locale.ROOT);
    }


    /**
     * Determines whether the given {@link CharSequence} contains only ASCII characters.
     *
     * @param s the {@link CharSequence} to check
     * @return true if the {@link CharSequence} contains only ASCII characters, false otherwise
     * @throws IllegalArgumentException if the input {@link CharSequence} is null
     * @since 5.3
     */
    public static boolean isAllASCII(final CharSequence s) {
        final int strLen = length(s);
        for (int i = 0; i < strLen; i++) {
            if (s.charAt(i) > 0x7F) {
                return false;
            }
        }
        return true;
    }

    /**
     * Casts character to byte filtering non-visible and non-ASCII characters before conversion.
     *
     * @param c The character to cast.
     * @return The given character or {@code '?'}.
     * @since 5.2
     */
    @Internal
    public static byte castAsByte(final int c) {
        if (c >= 0x20 && c <= 0x7E || // Visible ASCII
                c >= 0xA0 && c <= 0xFF || // Visible ISO-8859-1
                c == 0x09) {               // TAB
            return (byte) c;
        }
        return '?';
    }

    /**
     * Tests whether the given character is an ASCII hexadecimal digit.
     * <p>
     * Accepts {@code '0'..'9'}, {@code 'A'..'F'}, and {@code 'a'..'f'} only.
     * This method does not consider non-ASCII numerals or fullwidth forms.
     *
     * @param c the character to test
     * @return {@code true} if {@code c} is an ASCII hex digit, {@code false} otherwise
     * @since 5.4
     */
    public static boolean isHex(final char c) {
        return c >= '0' && c <= '9'
                || c >= 'A' && c <= 'F'
                || c >= 'a' && c <= 'f';
    }


}
