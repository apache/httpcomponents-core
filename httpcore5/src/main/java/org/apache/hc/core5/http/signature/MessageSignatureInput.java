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
package org.apache.hc.core5.http.signature;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.structured.StructuredFieldBareItem;
import org.apache.hc.core5.http.structured.StructuredFieldInnerList;
import org.apache.hc.core5.http.structured.StructuredFieldItem;
import org.apache.hc.core5.http.structured.StructuredFieldParameters;
import org.apache.hc.core5.http.structured.StructuredFieldSerializer;
import org.apache.hc.core5.http.structured.StructuredFieldType;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.LangUtils;

/**
 * One labeled value from the RFC 9421 {@code Signature-Input} dictionary.
 *
 * @since 5.5
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public final class MessageSignatureInput {

    private final String label;
    private final List<MessageSignatureComponent> components;
    private final StructuredFieldParameters parameters;

    /**
     * Creates an entry from a label, its covered components and the signature parameters.
     *
     * @param label      the signature label.
     * @param components the covered message components in signing order.
     * @param parameters the signature parameters.
     * @since 5.5
     */
    public MessageSignatureInput(
            final String label,
            final List<MessageSignatureComponent> components,
            final StructuredFieldParameters parameters) {
        this.label = MessageSignatureSupport.validateLabel(label);
        final List<MessageSignatureComponent> source = Args.notNull(components, "Covered components");
        final List<MessageSignatureComponent> copy = new ArrayList<>(source.size());
        final Set<MessageSignatureComponent> unique = new HashSet<>();
        for (final MessageSignatureComponent component : source) {
            final MessageSignatureComponent checked = Args.notNull(component, "Covered component");
            Args.check(unique.add(checked), "Duplicate covered component: %s", checked.serialize());
            copy.add(checked);
        }
        this.components = Collections.unmodifiableList(copy);
        this.parameters = Args.notNull(parameters, "Signature parameters");
        validateSignatureParameters(parameters);
    }

    static MessageSignatureInput fromInnerList(
            final String label, final StructuredFieldInnerList innerList) throws ParseException {
        final List<MessageSignatureComponent> components = new ArrayList<>(innerList.size());
        final Set<MessageSignatureComponent> unique = new HashSet<>();
        for (final StructuredFieldItem item : innerList) {
            final MessageSignatureComponent component = MessageSignatureComponent.fromStructuredFieldItem(item);
            if (!unique.add(component)) {
                throw new ParseException("Duplicate covered component: " + component.serialize());
            }
            components.add(component);
        }
        try {
            validateSignatureParameters(innerList.getParameters());
            return new MessageSignatureInput(label, components, innerList.getParameters());
        } catch (final IllegalArgumentException ex) {
            throw new ParseException(ex.getMessage());
        }
    }

    private static void validateSignatureParameters(final StructuredFieldParameters parameters) {
        requireType(parameters, "created", StructuredFieldType.INTEGER);
        requireType(parameters, "expires", StructuredFieldType.INTEGER);
        requireType(parameters, "nonce", StructuredFieldType.STRING);
        requireType(parameters, "alg", StructuredFieldType.STRING);
        requireType(parameters, "keyid", StructuredFieldType.STRING);
        requireType(parameters, "tag", StructuredFieldType.STRING);
    }

    private static void requireType(
            final StructuredFieldParameters parameters,
            final String name,
            final StructuredFieldType expected) {
        final StructuredFieldBareItem item = parameters.get(name);
        Args.check(item == null || item.getType() == expected,
                "Signature parameter '%s' must be %s", name, expected);
    }

    /**
     * Returns the signature label.
     *
     * @since 5.5
     */
    public String getLabel() {
        return label;
    }

    /**
     * Returns the covered message components.
     *
     * @since 5.5
     */
    public List<MessageSignatureComponent> getComponents() {
        return components;
    }

    /**
     * Returns the signature parameters.
     *
     * @since 5.5
     */
    public StructuredFieldParameters getParameters() {
        return parameters;
    }

    StructuredFieldInnerList toInnerList() {
        final List<StructuredFieldItem> items = new ArrayList<>(components.size());
        for (final MessageSignatureComponent component : components) {
            items.add(component.toStructuredFieldItem());
        }
        return StructuredFieldInnerList.of(items, parameters);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof MessageSignatureInput) {
            final MessageSignatureInput that = (MessageSignatureInput) obj;
            return Objects.equals(this.label, that.label)
                    && Objects.equals(this.components, that.components)
                    && Objects.equals(this.parameters, that.parameters);
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = LangUtils.HASH_SEED;
        hash = LangUtils.hashCode(hash, this.label);
        hash = LangUtils.hashCode(hash, this.components);
        hash = LangUtils.hashCode(hash, this.parameters);
        return hash;
    }

    @Override
    public String toString() {
        return label + "=" + StructuredFieldSerializer.serializeInnerList(toInnerList());
    }

}
