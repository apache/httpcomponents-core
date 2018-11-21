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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.util.Args;

/**
 * Maintains a map of objects keyed by a request URI regular expression.
 *
 * <p>
 * The insertion order is in maintained in that map such that the lookup tests each regex until there is a match. This
 * class can be used to resolve an object matching a particular request URI.
 * </p>
 *
 * @param <T> The type of registered objects.
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.SAFE)
public class UriRegexMatcher<T> implements LookupRegistry<T> {

    private final Map<String, T> objectMap;
    private final Map<String, Pattern> patternMap;

    public UriRegexMatcher() {
        super();
        this.objectMap = new LinkedHashMap<>();
        this.patternMap = new LinkedHashMap<>();
    }

    /**
     * Registers the given object for URIs matching the given regex.
     *
     * @param regex
     *            the regex to register the handler for.
     * @param obj
     *            the object.
     */
    @Override
    public synchronized void register(final String regex, final T obj) {
        Args.notNull(regex, "URI request regex");
        this.objectMap.put(regex, obj);
        this.patternMap.put(regex, Pattern.compile(regex));
    }

    /**
     * Removes registered object, if exists, for the given regex.
     *
     * @param regex
     *            the regex to unregister.
     */
    @Override
    public synchronized void unregister(final String regex) {
        if (regex == null) {
            return;
        }
        this.objectMap.remove(regex);
        this.patternMap.remove(regex);
    }

    /**
     * Looks up an object matching the given request path.
     *
     * @param path
     *            the request path
     * @return object or {@code null} if no match is found.
     */
    @Override
    public synchronized T lookup(final String path) {
        Args.notNull(path, "Request path");
        // direct match?
        final T obj = this.objectMap.get(path);
        if (obj == null) {
            // regex match?
            for (final Entry<String, Pattern> entry : this.patternMap.entrySet()) {
                if (entry.getValue().matcher(path).matches()) {
                    return objectMap.get(entry.getKey());
                }
            }
        }
        return obj;
    }

    @Override
    public String toString() {
        return this.objectMap.toString();
    }

}
