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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.LangUtils;

/**
 * Immutable Structured Field Inner List.
 *
 * @since 5.5
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public final class StructuredFieldInnerList implements StructuredFieldMember, Iterable<StructuredFieldItem> {

    private final List<StructuredFieldItem> items;
    private final StructuredFieldParameters parameters;

    private StructuredFieldInnerList(
            final List<StructuredFieldItem> items, final StructuredFieldParameters parameters) {
        final ArrayList<StructuredFieldItem> copy = new ArrayList<>(items.size());
        for (final StructuredFieldItem item : items) {
            copy.add(Args.notNull(item, "Inner List Item"));
        }
        this.items = Collections.unmodifiableList(copy);
        this.parameters = Args.notNull(parameters, "Parameters");
    }

    /**
     * Creates an Inner List without parameters.
     *
     * @param items the ordered Items.
     * @return the immutable Inner List.
     */
    public static StructuredFieldInnerList of(final List<StructuredFieldItem> items) {
        return new StructuredFieldInnerList(items, StructuredFieldParameters.EMPTY);
    }

    /**
     * Creates an Inner List without parameters.
     *
     * @param items the ordered Items.
     * @return the immutable Inner List.
     */
    public static StructuredFieldInnerList of(final StructuredFieldItem... items) {
        return of(Arrays.asList(items));
    }

    /**
     * Creates an Inner List with parameters.
     *
     * @param items the ordered Items.
     * @param parameters the Inner List parameters.
     * @return the immutable Inner List.
     */
    public static StructuredFieldInnerList of(
            final List<StructuredFieldItem> items, final StructuredFieldParameters parameters) {
        return new StructuredFieldInnerList(items, parameters);
    }

    /**
     * @return the number of Items.
     */
    public int size() {
        return items.size();
    }

    /**
     * @return whether the Inner List has no Items.
     */
    public boolean isEmpty() {
        return items.isEmpty();
    }

    /**
     * @param index the zero-based index.
     * @return the Item at the index.
     */
    public StructuredFieldItem get(final int index) {
        return items.get(index);
    }

    /**
     * @return an unmodifiable ordered Item list.
     */
    public List<StructuredFieldItem> getItems() {
        return items;
    }

    @Override
    public StructuredFieldParameters getParameters() {
        return parameters;
    }

    @Override
    public Iterator<StructuredFieldItem> iterator() {
        return items.iterator();
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof StructuredFieldInnerList) {
            final StructuredFieldInnerList that = (StructuredFieldInnerList) obj;
            return Objects.equals(this.items, that.items)
                    && Objects.equals(this.parameters, that.parameters);
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = LangUtils.HASH_SEED;
        hash = LangUtils.hashCode(hash, this.items);
        hash = LangUtils.hashCode(hash, this.parameters);
        return hash;
    }

    @Override
    public String toString() {
        return StructuredFieldSerializer.serializeInnerList(this);
    }
}
