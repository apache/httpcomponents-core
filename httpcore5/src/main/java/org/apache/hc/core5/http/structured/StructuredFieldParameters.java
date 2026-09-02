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

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.LangUtils;

/**
 * Immutable, ordered Structured Field Parameters map.
 *
 * @since 5.5
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public final class StructuredFieldParameters implements Iterable<Map.Entry<String, StructuredFieldBareItem>> {

    /** Empty parameter map. */
    public static final StructuredFieldParameters EMPTY = new StructuredFieldParameters(Collections.emptyMap());

    private final List<Map.Entry<String, StructuredFieldBareItem>> entries;
    private final Map<String, StructuredFieldBareItem> values;

    private StructuredFieldParameters(final Map<String, StructuredFieldBareItem> source) {
        final List<Map.Entry<String, StructuredFieldBareItem>> copy = new ArrayList<>(source.size());
        final Map<String, StructuredFieldBareItem> lookup = new LinkedHashMap<>();
        for (final Map.Entry<String, StructuredFieldBareItem> entry : source.entrySet()) {
            StructuredFieldRules.validateKey(entry.getKey());
            final StructuredFieldBareItem value = Args.notNull(entry.getValue(), "Parameter value");
            copy.add(new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), value));
            lookup.put(entry.getKey(), value);
        }
        this.entries = Collections.unmodifiableList(copy);
        this.values = Collections.unmodifiableMap(lookup);
    }

    /**
     * @return a new ordered Parameters builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    static StructuredFieldParameters copyOf(final Map<String, StructuredFieldBareItem> source) {
        return source.isEmpty() ? EMPTY : new StructuredFieldParameters(source);
    }

    /**
     * @return the number of parameters.
     */
    public int size() {
        return entries.size();
    }

    /**
     * @return whether there are no parameters.
     */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * Looks up a parameter by key.
     *
     * @param key the parameter key.
     * @return the bare Item value, or {@code null} when absent.
     */
    public StructuredFieldBareItem get(final String key) {
        return values.get(key);
    }

    /**
     * @param index the zero-based ordered index.
     * @return the parameter key at the index.
     */
    public String getName(final int index) {
        return entries.get(index).getKey();
    }

    /**
     * @param index the zero-based ordered index.
     * @return the parameter value at the index.
     */
    public StructuredFieldBareItem getValue(final int index) {
        return entries.get(index).getValue();
    }

    /**
     * @return an unmodifiable, insertion-ordered map view.
     */
    public Map<String, StructuredFieldBareItem> asMap() {
        return values;
    }

    @Override
    public Iterator<Map.Entry<String, StructuredFieldBareItem>> iterator() {
        return entries.iterator();
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof StructuredFieldParameters) {
            final StructuredFieldParameters that = (StructuredFieldParameters) obj;
            return Objects.equals(this.entries, that.entries);
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = LangUtils.HASH_SEED;
        hash = LangUtils.hashCode(hash, this.entries);
        return hash;
    }

    @Override
    public String toString() {
        return StructuredFieldSerializer.serializeParameters(this);
    }

    /** Builds immutable, insertion-ordered Parameters maps. */
    public static final class Builder {

        private final LinkedHashMap<String, StructuredFieldBareItem> values = new LinkedHashMap<>();

        /**
         * Adds or replaces a parameter. Replacing a key preserves its original position.
         *
         * @param key the parameter key.
         * @param value the bare Item value.
         * @return this builder.
         */
        public Builder put(final String key, final StructuredFieldBareItem value) {
            StructuredFieldRules.validateKey(key);
            values.put(key, Args.notNull(value, "Parameter value"));
            return this;
        }

        /**
         * Adds or replaces a Boolean parameter.
         *
         * @param key the parameter key.
         * @param value the boolean value.
         * @return this builder.
         */
        public Builder putBoolean(final String key, final boolean value) {
            return put(key, StructuredFieldBareItem.ofBoolean(value));
        }

        /**
         * @return an immutable Parameters map.
         */
        public StructuredFieldParameters build() {
            return StructuredFieldParameters.copyOf(values);
        }
    }
}
