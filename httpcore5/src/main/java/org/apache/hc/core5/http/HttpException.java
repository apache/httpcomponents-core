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

package org.apache.hc.core5.http;

/**
 * Signals that an HTTP exception has occurred.
 *
 * @since 4.0
 */
public class HttpException extends Exception {

    private static final int FIRST_VALID_CHAR = Chars.SP;
    private static final long serialVersionUID = -5437299376222011036L;

    /**
     * Cleans the given String by converting characters with values less than 32 to equivalent hexadecimal codes.
     *
     * @param message the source string.
     * @return a converted string.
     */
    static String clean(final String message) {
        final char[] chars = message.toCharArray();
        int i;
        // First check to see if need to allocate a new StringBuilder
        for (i = 0; i < chars.length; i++) {
            if (chars[i] < FIRST_VALID_CHAR) {
                break;
            }
        }
        if (i == chars.length) {
            return message;
        }
        final StringBuilder builder = new StringBuilder(chars.length * 2);
        for (i = 0; i < chars.length; i++) {
            final char ch = chars[i];
            if (ch < FIRST_VALID_CHAR) {
                builder.append("[0x");
                final String hexString = Integer.toHexString(i);
                if (hexString.length() == 1) {
                    builder.append("0");
                }
                builder.append(hexString);
                builder.append("]");
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    /**
     * Creates a new HttpException with a {@code null} detail message.
     */
    public HttpException() {
        super();
    }

    /**
     * Creates a new HttpException with the specified detail message.
     *
     * @param message the exception detail message
     */
    public HttpException(final String message) {
        super(clean(message));
    }

    /**
     * Constructs a new HttpException with the specified detail message.
     *
     * @param format The exception detail message format; see {@link String#format(String, Object...)}.
     * @param args The exception detail message arguments; see {@link String#format(String, Object...)}.
     *
     * @since 5.0
     */
    public HttpException(final String format, final Object... args) {
        super(HttpException.clean(String.format(format, args)));
    }

    /**
     * Creates a new HttpException with the specified detail message and cause.
     *
     * @param message the exception detail message
     * @param cause the {@code Throwable} that caused this exception, or {@code null}
     * if the cause is unavailable, unknown, or not a {@code Throwable}
     */
    public HttpException(final String message, final Throwable cause) {
        super(clean(message));
        initCause(cause);
    }

}
