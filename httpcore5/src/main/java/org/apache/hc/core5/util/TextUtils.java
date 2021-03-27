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

/**
 * @since 4.3
 */
public final class TextUtils {

    private TextUtils() {
        // Do not allow utility class to be instantiated.
    }

    /**
     * Returns true if the parameter is null or of zero length
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
     * @since 4.4
     */
    public static boolean containsBlanks(final CharSequence s) {
        final int strLen = length(s);
        if (strLen == 0) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
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
    public static String toHexString(final byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        final StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            final int unsignedB = bytes[i] & 0xff;
            if (unsignedB < 16) {
                buffer.append('0');
            }
            buffer.append(Integer.toHexString(unsignedB));
        }
        return buffer.toString();
    }

}
