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

package org.apache.hc.core5.http.protocol;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;

/**
 * Maintains a map of objects keyed by a request URI pattern.
 * <p>
 * Patterns may have three formats:
 * </p>
 * <ul>
 * <li>{@code *}</li>
 * <li>{@code *<uri>}</li>
 * <li>{@code <uri>*}</li>
 * </ul>
 * <p>
 * This class can be used to resolve an object matching a particular request
 * URI.
 * </p>
 *
 * @param <T> The type of registered objects.
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.SAFE)
public class UriPatternOrderedMatcher<T> implements LookupRegistry<T> {

    private final Map<String, T> map;

    public UriPatternOrderedMatcher() {
        super();
        this.map = new LinkedHashMap<>();
    }

    /**
     * Returns a {@link Set} view of the mappings contained in this matcher.
     *
     * @return a set view of the mappings contained in this matcher.
     *
     * @see Map#entrySet()
     * @since 4.4.9
     */
    public synchronized Set<Entry<String, T>> entrySet() {
        return new HashSet<>(map.entrySet());
    }

    /**
     * Registers the given object for URIs matching the given pattern.
     *
     * @param pattern the pattern to register the handler for.
     * @param obj     the object.
     */
    @Override
    public synchronized void register(final String pattern, final T obj) {
        Objects.requireNonNull(pattern, "URI request pattern");
        this.map.put(pattern, obj);
    }

    /**
     * Removes registered object, if exists, for the given pattern.
     *
     * @param pattern the pattern to unregister.
     */
    @Override
    public synchronized void unregister(final String pattern) {
        if (pattern == null) {
            return;
        }
        this.map.remove(pattern);
    }

    /**
     * Looks up an object matching the given request path.
     *
     * @param path the request path
     * @return object or {@code null} if no match is found.
     */
    @Override
    public synchronized T lookup(final String path) {
        Objects.requireNonNull(path, "Request path");
        for (final Entry<String, T> entry : this.map.entrySet()) {
            final String pattern = entry.getKey();
            if (path.equals(pattern)) {
                return entry.getValue();
            }
            if (matchUriRequestPattern(pattern, path)) {
                return this.map.get(pattern);
            }
        }
        return null;
    }

    /**
     * Tests if the given request path matches the given pattern.
     *
     * @param pattern the pattern
     * @param path    the request path
     * @return {@code true} if the request URI matches the pattern, {@code false}
     *         otherwise.
     */
    protected boolean matchUriRequestPattern(final String pattern, final String path) {
        if (pattern.equals("*")) {
            return true;
        }
        return (pattern.endsWith("*") && path.startsWith(pattern.substring(0, pattern.length() - 1)))
                || (pattern.startsWith("*") && path.endsWith(pattern.substring(1, pattern.length())));
    }

    @Override
    public String toString() {
        return this.map.toString();
    }

}
