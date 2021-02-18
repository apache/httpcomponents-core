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

    /**
     * Empty char array.
     *
     * @since 5.2
     */
    public static final char[] EMPTY_CHAR_ARRAY = new char[0];

    /**
     * Empty string array.
     *
     * @since 5.2
     */
    public static final String[] EMPTY_STRING_ARRAY = new String[0];

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
     * Returns true if the parameter is null or contains only whitespace
     */
    public static boolean isBlank(final CharSequence s) {
        for (int i = 0; i < length(s); i++) {
            if (!Character.isWhitespace(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Gets the length of the given string or 0 if null.
     *
     * @param s the input string.
     * @return the length of the given string or 0 if null.
     *
     * @since 5.2
     */
    public static int length(final CharSequence s) {
        return s == null ? 0 : s.length();
    }

    /**
     * @since 4.4
     */
    public static boolean containsBlanks(final CharSequence s) {
        for (int i = 0; i < length(s); i++) {
            if (Character.isWhitespace(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    public static String toHexString(final byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        final StringBuffer buffer = new StringBuffer();
        for (int i = 0; i < bytes.length; i++) {
            final byte b = bytes[i];
            if (b < 16) {
                buffer.append('0');
            }
            buffer.append(Integer.toHexString(b & 0xff));
        }
        return buffer.toString();
    }

}
