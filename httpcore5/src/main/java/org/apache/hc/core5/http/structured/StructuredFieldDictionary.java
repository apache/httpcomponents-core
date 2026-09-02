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
 * Immutable, ordered Structured Field Dictionary.
 *
 * @since 5.5
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public final class StructuredFieldDictionary
        implements StructuredFieldValue, Iterable<Map.Entry<String, StructuredFieldMember>> {

    private final List<Map.Entry<String, StructuredFieldMember>> entries;
    private final Map<String, StructuredFieldMember> values;

    private StructuredFieldDictionary(final Map<String, StructuredFieldMember> source) {
        final List<Map.Entry<String, StructuredFieldMember>> copy = new ArrayList<>(source.size());
        final Map<String, StructuredFieldMember> lookup = new LinkedHashMap<>();
        for (final Map.Entry<String, StructuredFieldMember> entry : source.entrySet()) {
            StructuredFieldRules.validateKey(entry.getKey());
            final StructuredFieldMember value = Args.notNull(entry.getValue(), "Dictionary member");
            copy.add(new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), value));
            lookup.put(entry.getKey(), value);
        }
        this.entries = Collections.unmodifiableList(copy);
        this.values = Collections.unmodifiableMap(lookup);
    }

    /**
     * @return a new ordered Dictionary builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    static StructuredFieldDictionary copyOf(final Map<String, StructuredFieldMember> source) {
        return new StructuredFieldDictionary(source);
    }

    /**
     * @return the number of members.
     */
    public int size() {
        return entries.size();
    }

    /**
     * @return whether the Dictionary has no members.
     */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * Looks up a member by key.
     *
     * @param key the member key.
     * @return the member, or {@code null} when absent.
     */
    public StructuredFieldMember get(final String key) {
        return values.get(key);
    }

    /**
     * @param index the zero-based ordered index.
     * @return the member key at the index.
     */
    public String getName(final int index) {
        return entries.get(index).getKey();
    }

    /**
     * @param index the zero-based ordered index.
     * @return the member value at the index.
     */
    public StructuredFieldMember getValue(final int index) {
        return entries.get(index).getValue();
    }

    /**
     * @return an unmodifiable, insertion-ordered map view.
     */
    public Map<String, StructuredFieldMember> asMap() {
        return values;
    }

    @Override
    public Iterator<Map.Entry<String, StructuredFieldMember>> iterator() {
        return entries.iterator();
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof StructuredFieldDictionary) {
            final StructuredFieldDictionary that = (StructuredFieldDictionary) obj;
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
        final String value = StructuredFieldSerializer.serializeDictionary(this);
        return value != null ? value : "";
    }

    /** Builds immutable, insertion-ordered Dictionaries. */
    public static final class Builder {

        private final LinkedHashMap<String, StructuredFieldMember> values = new LinkedHashMap<>();

        /**
         * Adds or replaces a member. Replacing a key preserves its original position.
         *
         * @param key the member key.
         * @param value the member value.
         * @return this builder.
         */
        public Builder put(final String key, final StructuredFieldMember value) {
            StructuredFieldRules.validateKey(key);
            values.put(key, Args.notNull(value, "Dictionary member"));
            return this;
        }

        /**
         * Adds or replaces a bare Item member.
         *
         * @param key the member key.
         * @param value the bare Item value.
         * @return this builder.
         */
        public Builder put(final String key, final StructuredFieldBareItem value) {
            return put(key, StructuredFieldItem.of(value));
        }

        /**
         * @return an immutable Dictionary.
         */
        public StructuredFieldDictionary build() {
            return StructuredFieldDictionary.copyOf(values);
        }
    }
}
