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

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

import org.apache.hc.core5.http.EntityDetails;

public class Args {

    public static void check(final boolean expression, final String message) {
        if (!expression) {
            throw new IllegalArgumentException(message);
        }
    }

    public static void check(final boolean expression, final String message, final Object... args) {
        if (!expression) {
            throw new IllegalArgumentException(String.format(message, args));
        }
    }

    public static void check(final boolean expression, final String message, final Object arg) {
        if (!expression) {
            throw new IllegalArgumentException(String.format(message, arg));
        }
    }

    public static long checkContentLength(final EntityDetails entityDetails) {
        // -1 is a special value,
        // 0 is allowed as well,
        // but never more than Integer.MAX_VALUE.
        return checkRange(entityDetails.getContentLength(), -1, Integer.MAX_VALUE,
                        "HTTP entity too large to be buffered in memory)");
    }

    public static int checkRange(final int value, final int lowInclusive, final int highInclusive,
                    final String message) {
        if (value < lowInclusive || value > highInclusive) {
            throw illegalArgumentException("%s: %d is out of range [%d, %d]", message, value,
                    lowInclusive, highInclusive);
        }
        return value;
    }

    public static long checkRange(final long value, final long lowInclusive, final long highInclusive,
                    final String message) {
        if (value < lowInclusive || value > highInclusive) {
            throw illegalArgumentException("%s: %d is out of range [%d, %d]", message, value,
                    lowInclusive, highInclusive);
        }
        return value;
    }

    public static <T extends CharSequence> T containsNoBlanks(final T argument, final String name) {
        notNull(argument, name);
        if (isEmpty(argument)) {
            throw illegalArgumentExceptionNotEmpty(name);
        }
        if (TextUtils.containsBlanks(argument)) {
            throw new IllegalArgumentException(name + " must not contain blanks");
        }
        return argument;
    }

    private static IllegalArgumentException illegalArgumentException(final String format, final Object... args) {
        return new IllegalArgumentException(String.format(format, args));
    }

    private static IllegalArgumentException illegalArgumentExceptionNotEmpty(final String name) {
        return new IllegalArgumentException(name + " must not be empty");
    }

    private static NullPointerException NullPointerException(final String name) {
        return new NullPointerException(name + " must not be null");
    }

    public static <T extends CharSequence> T notBlank(final T argument, final String name) {
        notNull(argument, name);
        if (TextUtils.isBlank(argument)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return argument;
    }

    public static <T extends CharSequence> T notEmpty(final T argument, final String name) {
        notNull(argument, name);
        if (isEmpty(argument)) {
            throw illegalArgumentExceptionNotEmpty(name);
        }
        return argument;
    }

    public static <E, T extends Collection<E>> T notEmpty(final T argument, final String name) {
        notNull(argument, name);
        if (isEmpty(argument)) {
            throw illegalArgumentExceptionNotEmpty(name);
        }
        return argument;
    }

    public static <T> T notEmpty(final T argument, final String name) {
        notNull(argument, name);
        if (isEmpty(argument)) {
            throw illegalArgumentExceptionNotEmpty(name);
        }
        return argument;
    }

    public static int notNegative(final int n, final String name) {
        if (n < 0) {
            throw illegalArgumentException("%s must not be negative: %d", name, n);
        }
        return n;
    }

    public static long notNegative(final long n, final String name) {
        if (n < 0) {
            throw illegalArgumentException("%s must not be negative: %d", name, n);
        }
        return n;
    }

    /**
     * <p>Validate that the specified argument is not {@code null};
     * otherwise throwing an exception with the specified message.
     *
     * <pre>Args.notNull(myObject, "The object must not be null");</pre>
     *
     * @param <T> the object type
     * @param argument  the object to check
     * @param name  the {@link String} exception message if invalid, not null
     * @return the validated object (never {@code null} for method chaining)
     * @throws NullPointerException if the object is {@code null}
     */
    public static <T> T notNull(final T argument, final String name) {
        return Objects.requireNonNull(argument, name);
    }

    /**
     * <p>Checks if an Object is empty or null.</p>
     *
     * The following types are supported:
     * <ul>
     * <li>{@link CharSequence}: Considered empty if its length is zero.</li>
     * <li>{@code Array}: Considered empty if its length is zero.</li>
     * <li>{@link Collection}: Considered empty if it has zero elements.</li>
     * <li>{@link Map}: Considered empty if it has zero key-value mappings.</li>
     * </ul>
     *
     * <pre>
     * Args.isEmpty(null)             = true
     * Args.isEmpty("")               = true
     * Args.isEmpty("ab")             = false
     * Args.isEmpty(new int[]{})      = true
     * Args.isEmpty(new int[]{1,2,3}) = false
     * Args.isEmpty(1234)             = false
     * </pre>
     *
     * @param object  the {@code Object} to test, may be {@code null}
     * @return {@code true} if the object has a supported type and is empty or null,
     * {@code false} otherwise
     * @since 5.1
     */
    public static boolean isEmpty(final Object object) {
        if (object == null) {
            return true;
        }
        if (object instanceof CharSequence) {
            return ((CharSequence) object).length() == 0;
        }
        if (object.getClass().isArray()) {
            return Array.getLength(object) == 0;
        }
        if (object instanceof Collection<?>) {
            return ((Collection<?>) object).isEmpty();
        }
        if (object instanceof Map<?, ?>) {
            return ((Map<?, ?>) object).isEmpty();
        }
        return false;
    }

    public static int positive(final int n, final String name) {
        if (n <= 0) {
            throw illegalArgumentException("%s must not be negative or zero: %d", name, n);
        }
        return n;
    }

    public static long positive(final long n, final String name) {
        if (n <= 0) {
            throw illegalArgumentException("%s must not be negative or zero: %d", name, n);
        }
        return n;
    }

    public static <T extends TimeValue> T positive(final T timeValue, final String name) {
        positive(timeValue.getDuration(), name);
        return timeValue;
    }

    /**
     * Private constructor so that no instances can be created. This class
     * contains only static utility methods.
     */
    private Args() {
    }

}
