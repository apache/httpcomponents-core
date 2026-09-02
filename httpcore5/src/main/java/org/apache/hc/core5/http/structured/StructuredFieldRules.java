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

package org.apache.hc.core5.http.structured;

final class StructuredFieldRules {

    static final long MAX_INTEGER = 999999999999999L;

    private StructuredFieldRules() {
    }

    static boolean isAlpha(final char ch) {
        return ch >= 'A' && ch <= 'Z' || ch >= 'a' && ch <= 'z';
    }

    static boolean isLowerAlpha(final char ch) {
        return ch >= 'a' && ch <= 'z';
    }

    static boolean isDigit(final char ch) {
        return ch <= 0x7f && Character.isDigit(ch);
    }

    static boolean isKeyStart(final char ch) {
        return isLowerAlpha(ch) || ch == '*';
    }

    static boolean isKeyChar(final char ch) {
        return isLowerAlpha(ch) || isDigit(ch) || ch == '_' || ch == '-' || ch == '.' || ch == '*';
    }

    static boolean isTokenChar(final char ch) {
        return isAlpha(ch) || isDigit(ch) || "!#$%&'*+-.^_`|~:/".indexOf(ch) >= 0;
    }

    static void validateKey(final String key) {
        if (key == null || key.isEmpty() || !isKeyStart(key.charAt(0))) {
            throw new IllegalArgumentException("Invalid Structured Field key: " + key);
        }
        for (int i = 1; i < key.length(); i++) {
            if (!isKeyChar(key.charAt(i))) {
                throw new IllegalArgumentException("Invalid Structured Field key: " + key);
            }
        }
    }

    static void validateToken(final String token) {
        if (token == null || token.isEmpty() || !(isAlpha(token.charAt(0)) || token.charAt(0) == '*')) {
            throw new IllegalArgumentException("Invalid Structured Field token: " + token);
        }
        for (int i = 1; i < token.length(); i++) {
            if (!isTokenChar(token.charAt(i))) {
                throw new IllegalArgumentException("Invalid Structured Field token: " + token);
            }
        }
    }

    static void validateString(final String value) {
        if (value == null) {
            throw new NullPointerException("String value");
        }
        for (int i = 0; i < value.length(); i++) {
            final char ch = value.charAt(i);
            if (ch < 0x20 || ch > 0x7e) {
                throw new IllegalArgumentException("Structured Field String must contain printable ASCII only");
            }
        }
    }

    static void validateDisplayString(final String value) {
        if (value == null) {
            throw new NullPointerException("Display String value");
        }
        for (int i = 0; i < value.length(); i++) {
            final char ch = value.charAt(i);
            if (Character.isHighSurrogate(ch)) {
                if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i))) {
                    throw new IllegalArgumentException("Display String contains an unpaired surrogate");
                }
            } else if (Character.isLowSurrogate(ch)) {
                throw new IllegalArgumentException("Display String contains an unpaired surrogate");
            }
        }
    }
}
