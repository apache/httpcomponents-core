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

import java.util.Locale;

import org.apache.hc.core5.util.Args;

/**
 * Common HTTP methods defined by the HTTP specification.
 * <p>
 * Each method is:
 * </p>
 * <ul>
 * <li>Either <em>safe</em> or <em>unsafe</em>: An HTTP method is safe if it doesn't change the state of the server. In other words, a method is safe if it's
 * read-only.</li>
 * <li>Either <em>idempotent</em> or <em>non-idempotent</em>: An HTTP method is idempotent if making a single request has the same effect as making several
 * identical requests. All safe methods are also idempotent, but not all idempotent methods are safe. For example, {@code PUT} and {@code DELETE} are both
 * idempotent but unsafe.</li>
 * </ul>
 *
 * @since 5.0
 */
public enum Method {

    /**
     * The HTTP {@code GET} method is safe and idempotent.
     */
    GET(true, true),

    /**
     * The HTTP {@code HEAD} method is safe and idempotent.
     */
    HEAD(true, true),

    /**
     * The HTTP {@code POST} method is unsafe and non-idempotent.
     */
    POST(false, false),

    /**
     * The HTTP {@code PUT} method is unsafe and idempotent.
     */
    PUT(false, true),

    /**
     * The HTTP {@code DELETE} method is unsafe and idempotent.
     */
    DELETE(false, true),

    /**
     * The HTTP {@code CONNECT} method is unsafe and non-idempotent.
     */
    CONNECT(false, false),

    /**
     * The HTTP {@code TRACE} method is safe and idempotent.
     */
    TRACE(true, true),

    /**
     * The HTTP {@code OPTIONS} method is safe and idempotent.
     */
    OPTIONS(true, true),

    /**
     * The HTTP {@code PATCH} method is unsafe and non-idempotent.
     */
    PATCH(false, false);

    private final boolean safe;
    private final boolean idempotent;

    Method(final boolean safe, final boolean idempotent) {
        this.safe = safe;
        this.idempotent = idempotent;
    }

    /**
     * Tests whether this method is safe.
     *
     * @return whether this method is safe.
     */
    public boolean isSafe() {
        return safe;
    }

    /**
     * Tests whether this method is idempotent.
     *
     * @return whether this method is idempotent.
     */
    public boolean isIdempotent() {
        return idempotent;
    }

    public static boolean isSafe(final String value) {
        if (value == null) {
            return false;
        }
        try {
            return normalizedValueOf(value).safe;
        } catch (final IllegalArgumentException ex) {
            return false;
        }
    }

    public static boolean isIdempotent(final String value) {
        if (value == null) {
            return false;
        }
        try {
            return normalizedValueOf(value).idempotent;
        } catch (final IllegalArgumentException ex) {
            return false;
        }
    }

    /**
     * Returns the Method for a normalized {@code value} of a method name.
     *
     * @param method A method name like {@code "delete"}, {@code "DELETE"}, or any mixed-case variant.
     * @return the Method for the given method name.
     */
    public static Method normalizedValueOf(final String method) {
        return valueOf(Args.notNull(method, "method").toUpperCase(Locale.ROOT));
    }

    public boolean isSame(final String value) {
        if (value == null) {
            return false;
        }
        return name().equalsIgnoreCase(value);
    }

}
