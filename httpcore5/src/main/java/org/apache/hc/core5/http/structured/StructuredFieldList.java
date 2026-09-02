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
 * Immutable Structured Field List.
 *
 * @since 5.5
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public final class StructuredFieldList implements StructuredFieldValue, Iterable<StructuredFieldMember> {

    private final List<StructuredFieldMember> members;

    private StructuredFieldList(final List<? extends StructuredFieldMember> members) {
        final ArrayList<StructuredFieldMember> copy = new ArrayList<>(members.size());
        for (final StructuredFieldMember member : members) {
            copy.add(Args.notNull(member, "List member"));
        }
        this.members = Collections.unmodifiableList(copy);
    }

    /**
     * Creates a List from ordered members.
     *
     * @param members the ordered members.
     * @return the immutable List.
     */
    public static StructuredFieldList of(final List<? extends StructuredFieldMember> members) {
        return new StructuredFieldList(members);
    }

    /**
     * Creates a List from ordered members.
     *
     * @param members the ordered members.
     * @return the immutable List.
     */
    public static StructuredFieldList of(final StructuredFieldMember... members) {
        return of(Arrays.asList(members));
    }

    /**
     * @return the number of members.
     */
    public int size() {
        return members.size();
    }

    /**
     * @return whether the List has no members.
     */
    public boolean isEmpty() {
        return members.isEmpty();
    }

    /**
     * @param index the zero-based index.
     * @return the member at the index.
     */
    public StructuredFieldMember get(final int index) {
        return members.get(index);
    }

    /**
     * @return an unmodifiable ordered member list.
     */
    public List<StructuredFieldMember> getMembers() {
        return members;
    }

    @Override
    public Iterator<StructuredFieldMember> iterator() {
        return members.iterator();
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof StructuredFieldList) {
            final StructuredFieldList that = (StructuredFieldList) obj;
            return Objects.equals(this.members, that.members);
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = LangUtils.HASH_SEED;
        hash = LangUtils.hashCode(hash, this.members);
        return hash;
    }

    @Override
    public String toString() {
        final String value = StructuredFieldSerializer.serializeList(this);
        return value != null ? value : "";
    }
}
