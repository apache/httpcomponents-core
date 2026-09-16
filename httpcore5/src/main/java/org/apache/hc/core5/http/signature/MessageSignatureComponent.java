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

import java.util.Locale;
import java.util.Objects;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.structured.StructuredFieldBareItem;
import org.apache.hc.core5.http.structured.StructuredFieldItem;
import org.apache.hc.core5.http.structured.StructuredFieldParameters;
import org.apache.hc.core5.http.structured.StructuredFieldSerializer;
import org.apache.hc.core5.http.structured.StructuredFieldType;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.LangUtils;

/**
 * Immutable RFC covered component identifier.
 *
 * @since 5.5
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public final class MessageSignatureComponent {

    private final String name;
    private final StructuredFieldParameters parameters;

    private MessageSignatureComponent(final String name, final StructuredFieldParameters parameters) {
        this.name = validateName(name);
        this.parameters = Args.notNull(parameters, "Component parameters");
    }

    /**
     * Creates a component identifier from an already canonical component name.
     *
     * @since 5.5
     */
    public static MessageSignatureComponent create(
            final String name, final StructuredFieldParameters parameters) {
        return new MessageSignatureComponent(name, parameters);
    }

    /**
     * Creates a derived component identifier without parameters.
     *
     * @since 5.5
     */
    public static MessageSignatureComponent derived(final String name) {
        Args.check(name != null && name.startsWith("@"), "Derived component name must start with '@'");
        return new MessageSignatureComponent(name, StructuredFieldParameters.EMPTY);
    }

    /**
     * Creates a lower-case HTTP field component identifier without parameters.
     *
     * @since 5.5
     */
    public static MessageSignatureComponent field(final String name) {
        Args.notBlank(name, "Field name");
        return new MessageSignatureComponent(name.toLowerCase(Locale.ROOT), StructuredFieldParameters.EMPTY);
    }

    /**
     * Creates an RFC 9421 {@code @query-param} identifier.
     *
     * @since 5.5
     */
    public static MessageSignatureComponent queryParam(final String encodedName) {
        final StructuredFieldParameters parameters = StructuredFieldParameters.builder()
                .put("name", StructuredFieldBareItem.ofString(encodedName))
                .build();
        return new MessageSignatureComponent("@query-param", parameters);
    }

    static MessageSignatureComponent fromStructuredFieldItem(final StructuredFieldItem item) throws ParseException {
        final StructuredFieldBareItem bareItem = item.getBareItem();
        if (bareItem.getType() != StructuredFieldType.STRING) {
            throw new ParseException("RFC 9421 component identifier must be a Structured Field String");
        }
        final String name = bareItem.getTextValue();
        try {
            return new MessageSignatureComponent(name, item.getParameters());
        } catch (final IllegalArgumentException ex) {
            throw new ParseException(ex.getMessage());
        }
    }

    private static String validateName(final String name) {
        Args.notBlank(name, "Component name");
        Args.check(!"@signature-params".equals(name), "@signature-params must not be a covered component");
        if (!name.startsWith("@")) {
            Args.check(name.equals(name.toLowerCase(Locale.ROOT)),
                    "HTTP field component names must be lowercase: %s", name);
            for (int i = 0; i < name.length(); i++) {
                Args.check(isTokenChar(name.charAt(i)), "Invalid HTTP field component name: %s", name);
            }
        }
        return name;
    }

    private static boolean isTokenChar(final char ch) {
        return ch >= 'a' && ch <= 'z'
                || ch >= '0' && ch <= '9'
                || ch == '!' || ch == '#' || ch == '$' || ch == '%' || ch == '&' || ch == '\''
                || ch == '*' || ch == '+' || ch == '-' || ch == '.' || ch == '^' || ch == '_'
                || ch == '`' || ch == '|' || ch == '~';
    }

    /**
     * Returns the canonical component name.
     *
     * @since 5.5
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the component parameters.
     *
     * @since 5.5
     */
    public StructuredFieldParameters getParameters() {
        return parameters;
    }

    /**
     * Tests whether this is a derived component.
     *
     * @since 5.5
     */
    public boolean isDerived() {
        return name.startsWith("@");
    }

    StructuredFieldItem toStructuredFieldItem() {
        return StructuredFieldItem.of(StructuredFieldBareItem.ofString(name), parameters);
    }

    /**
     * Returns the strict Structured Fields serialization used in the signature base.
     *
     * @since 5.5
     */
    public String serialize() {
        return StructuredFieldSerializer.serializeItem(toStructuredFieldItem());
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof MessageSignatureComponent) {
            final MessageSignatureComponent that = (MessageSignatureComponent) obj;
            // RFC 9421 requires parameter order to be preserved for serialization, but
            // explicitly declares it insignificant when comparing component identifiers.
            return Objects.equals(name, that.name)
                    && Objects.equals(parameters.asMap(), that.parameters.asMap());
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = LangUtils.HASH_SEED;
        hash = LangUtils.hashCode(hash, name);
        hash = LangUtils.hashCode(hash, parameters.asMap());
        return hash;
    }

    @Override
    public String toString() {
        return serialize();
    }

}
