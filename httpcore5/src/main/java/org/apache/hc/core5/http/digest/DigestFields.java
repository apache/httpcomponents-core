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
package org.apache.hc.core5.http.digest;

import java.util.Map;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.MessageHeaders;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.structured.StructuredFieldBareItem;
import org.apache.hc.core5.http.structured.StructuredFieldDictionary;
import org.apache.hc.core5.http.structured.StructuredFieldHeaders;
import org.apache.hc.core5.http.structured.StructuredFieldItem;
import org.apache.hc.core5.http.structured.StructuredFieldMember;
import org.apache.hc.core5.http.structured.StructuredFieldType;
import org.apache.hc.core5.util.Args;

/**
 * Support for Digest Fields as defined by RFC 9530.
 * <p>
 * Digest fields are Structured Field Dictionaries. Syntax parsing and
 * serialization is therefore delegated to the RFC 9651 implementation in
 * {@link StructuredFieldHeaders}. This class only applies the additional
 * semantic constraints imposed by RFC 9530.
 * </p>
 *
 * @since 5.5
 */
public final class DigestFields {

    private DigestFields() {
    }

    /**
     * Parses and validates a {@code Content-Digest} or {@code Repr-Digest}
     * field value.
     *
     * @param header the header to parse.
     * @return the parsed digest dictionary.
     * @throws ParseException if the field is syntactically invalid or any
     *                        dictionary member is not a Byte Sequence item.
     */
    public static StructuredFieldDictionary parseDigest(final Header header) throws ParseException {
        Args.notNull(header, "Header");
        return validateParsedDigest(StructuredFieldHeaders.parseDictionary(header));
    }

    /**
     * Parses and validates all field lines with the given name as one
     * {@code Content-Digest} or {@code Repr-Digest} dictionary.
     *
     * @param headers the message headers.
     * @param name    the field name.
     * @return the parsed digest dictionary, or an empty dictionary if the
     * field is not present.
     * @throws ParseException if the field is syntactically invalid or any
     *                        dictionary member is not a Byte Sequence item.
     */
    public static StructuredFieldDictionary parseDigest(
            final MessageHeaders headers,
            final String name) throws ParseException {
        Args.notNull(headers, "Message headers");
        Args.notBlank(name, "Header name");
        return validateParsedDigest(StructuredFieldHeaders.parseDictionary(headers, name));
    }

    /**
     * Parses and validates a {@code Want-Content-Digest} or
     * {@code Want-Repr-Digest} field value.
     *
     * @param header the header to parse.
     * @return the parsed preference dictionary.
     * @throws ParseException if the field is syntactically invalid, a member
     *                        is not an Integer item, or its value is outside the range 0..10.
     */
    public static StructuredFieldDictionary parsePreferences(final Header header) throws ParseException {
        Args.notNull(header, "Header");
        return validateParsedPreferences(StructuredFieldHeaders.parseDictionary(header));
    }

    /**
     * Parses and validates all field lines with the given name as one
     * {@code Want-Content-Digest} or {@code Want-Repr-Digest} dictionary.
     *
     * @param headers the message headers.
     * @param name    the field name.
     * @return the parsed preference dictionary, or an empty dictionary if the
     * field is not present.
     * @throws ParseException if the field is syntactically invalid, a member
     *                        is not an Integer item, or its value is outside the range 0..10.
     */
    public static StructuredFieldDictionary parsePreferences(
            final MessageHeaders headers,
            final String name) throws ParseException {
        Args.notNull(headers, "Message headers");
        Args.notBlank(name, "Header name");
        return validateParsedPreferences(StructuredFieldHeaders.parseDictionary(headers, name));
    }

    /**
     * Formats a validated {@code Content-Digest} or {@code Repr-Digest}
     * dictionary.
     *
     * @param name  the field name.
     * @param value the digest dictionary.
     * @return the formatted header, or {@code null} for an empty dictionary.
     * @throws IllegalArgumentException if any dictionary member is not a Byte
     *                                  Sequence item.
     */
    public static Header formatDigest(final String name, final StructuredFieldDictionary value) {
        Args.notBlank(name, "Header name");
        Args.notNull(value, "Digest field");
        validateDigest(value);
        return StructuredFieldHeaders.format(name, value);
    }

    /**
     * Formats a validated {@code Want-Content-Digest} or
     * {@code Want-Repr-Digest} dictionary.
     *
     * @param name  the field name.
     * @param value the preference dictionary.
     * @return the formatted header, or {@code null} for an empty dictionary.
     * @throws IllegalArgumentException if a member is not an Integer item or
     *                                  its value is outside the range 0..10.
     */
    public static Header formatPreferences(final String name, final StructuredFieldDictionary value) {
        Args.notBlank(name, "Header name");
        Args.notNull(value, "Digest preferences");
        validatePreferences(value);
        return StructuredFieldHeaders.format(name, value);
    }

    private static StructuredFieldDictionary validateParsedDigest(
            final StructuredFieldDictionary dictionary) throws ParseException {
        try {
            validateDigest(dictionary);
            return dictionary;
        } catch (final IllegalArgumentException ex) {
            throw new ParseException(ex.getMessage());
        }
    }

    private static StructuredFieldDictionary validateParsedPreferences(
            final StructuredFieldDictionary dictionary) throws ParseException {
        try {
            validatePreferences(dictionary);
            return dictionary;
        } catch (final IllegalArgumentException ex) {
            throw new ParseException(ex.getMessage());
        }
    }

    private static void validateDigest(final StructuredFieldDictionary dictionary) {
        for (final Map.Entry<String, StructuredFieldMember> entry : dictionary) {
            final StructuredFieldItem item = requireItem(entry, "Digest");
            Args.check(item.getBareItem().getType() == StructuredFieldType.BYTE_SEQUENCE,
                    "Digest field member '%s' must be a Byte Sequence", entry.getKey());
        }
    }

    private static void validatePreferences(final StructuredFieldDictionary dictionary) {
        for (final Map.Entry<String, StructuredFieldMember> entry : dictionary) {
            final StructuredFieldItem item = requireItem(entry, "Digest preference");
            final StructuredFieldBareItem bareItem = item.getBareItem();
            Args.check(bareItem.getType() == StructuredFieldType.INTEGER,
                    "Digest preference member '%s' must be an Integer", entry.getKey());
            final long value = bareItem.getLongValue();
            Args.check(value >= 0 && value <= 10,
                    "Digest preference member '%s' must be in the range 0..10", entry.getKey());
        }
    }

    private static StructuredFieldItem requireItem(
            final Map.Entry<String, StructuredFieldMember> entry,
            final String description) {
        final StructuredFieldMember member = entry.getValue();
        Args.check(member instanceof StructuredFieldItem,
                "%s field member '%s' must be an Item", description, entry.getKey());
        return (StructuredFieldItem) member;
    }
}
