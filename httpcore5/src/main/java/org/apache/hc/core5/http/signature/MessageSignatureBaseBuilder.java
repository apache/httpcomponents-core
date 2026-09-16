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

import java.util.HashSet;
import java.util.Set;

import org.apache.hc.core5.http.structured.StructuredFieldSerializer;
import org.apache.hc.core5.util.Args;

/**
 * Builds the deterministic RFC 9421 signature base. No cryptography is performed.
 *
 * @since 5.5
 */
public final class MessageSignatureBaseBuilder {

    private final MessageSignatureComponentResolver resolver;

    /**
     * Creates a builder backed by the default component resolver.
     *
     * @since 5.5
     */
    public MessageSignatureBaseBuilder() {
        this(DefaultMessageSignatureComponentResolver.INSTANCE);
    }

    /**
     * Creates a builder backed by the given component resolver.
     *
     * @param resolver the resolver used to derive covered component values.
     * @since 5.5
     */
    public MessageSignatureBaseBuilder(final MessageSignatureComponentResolver resolver) {
        this.resolver = Args.notNull(resolver, "Component resolver");
    }

    /**
     * Builds the signature base for the given input and context.
     *
     * @param input   the covered components and signature parameters.
     * @param context the message context from which component values are resolved.
     * @return the signature base string.
     * @throws MessageSignatureException if a component is duplicated, cannot be resolved, or yields
     *                                   an invalid or non-ASCII value.
     * @since 5.5
     */
    public String build(final MessageSignatureInput input, final MessageSignatureContext context)
            throws MessageSignatureException {
        Args.notNull(input, "Signature input");
        Args.notNull(context, "Signature context");
        final StringBuilder buffer = new StringBuilder(256);
        final Set<MessageSignatureComponent> seen = new HashSet<>();
        for (final MessageSignatureComponent component : input.getComponents()) {
            if (!seen.add(component)) {
                throw new MessageSignatureException("Duplicate covered component: " + component.serialize());
            }
            final String value = resolver.resolve(component, context);
            validateComponentValue(component, value);
            buffer.append(component.serialize()).append(": ").append(value).append('\n');
        }
        buffer.append("\"@signature-params\": ")
                .append(StructuredFieldSerializer.serializeInnerList(input.toInnerList()));
        ensureAscii(buffer);
        return buffer.toString();
    }

    private static void validateComponentValue(
            final MessageSignatureComponent component, final String value) throws MessageSignatureException {
        if (value == null) {
            throw new MessageSignatureException("Resolver returned null for " + component.serialize());
        }
        if (component.isDerived() && !value.isEmpty()
                && (value.charAt(0) == ' ' || value.charAt(value.length() - 1) == ' ')) {
            throw new MessageSignatureException(
                    "Derived component value must not start or end with whitespace: " + component.serialize());
        }
        for (int i = 0; i < value.length(); i++) {
            final char ch = value.charAt(i);
            if (component.isDerived()) {
                if (ch < 0x20 || ch > 0x7e) {
                    throw new MessageSignatureException(
                            "Invalid derived component value for " + component.serialize() + " at index " + i);
                }
            } else if (ch != '\t' && (ch < 0x20 || ch > 0x7e)) {
                throw new MessageSignatureException(
                        "Invalid HTTP field component value for " + component.serialize() + " at index " + i);
            }
        }
    }

    private static void ensureAscii(final CharSequence value) throws MessageSignatureException {
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) > 0x7f) {
                throw new MessageSignatureException("Signature base contains non-ASCII data at index " + i);
            }
        }
    }

}
