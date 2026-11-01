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

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.MessageHeaders;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.message.BufferedHeader;
import org.apache.hc.core5.http.message.MessageSupport;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.CharArrayBuffer;

/**
 * Integration between Structured Field values and HttpComponents message headers.
 *
 * @since 5.5
 */
public final class StructuredFieldHeaders {

    private StructuredFieldHeaders() {
    }

    /**
     * Parses one header as an Structured Field Item.
     *
     * @param header the header.
     * @return the parsed Item.
     * @throws ParseException if the complete field value is invalid.
     */
    public static StructuredFieldItem parseItem(final Header header) throws ParseException {
        return MessageSupport.parseHeaderValueStrict(header, StructuredFieldParser::parseItem);
    }

    /**
     * Parses the field named {@code name} as a single Structured Field Item. An Item is a single
     * value and cannot span field lines, so more than one matching field line is rejected.
     *
     * @param headers the message headers.
     * @param name the case-insensitive field name.
     * @return the parsed Item.
     * @throws ParseException if the field is absent, spans multiple field lines, or is invalid.
     */
    public static StructuredFieldItem parseItem(final MessageHeaders headers, final String name)
            throws ParseException {
        Args.notNull(headers, "Message headers");
        Args.notBlank(name, "Header name");
        final Iterator<Header> matching = headers.headerIterator(name);
        if (!matching.hasNext()) {
            throw new ParseException("Missing " + name + " field");
        }
        final Header header = matching.next();
        if (matching.hasNext()) {
            throw new ParseException(name + " Item must not span multiple field lines");
        }
        return MessageSupport.parseHeaderValueStrict(header, StructuredFieldParser::parseItem);
    }

    /**
     * Parses one header as an Structured Field List.
     *
     * @param header the header.
     * @return the parsed List.
     * @throws ParseException if the complete field value is invalid.
     */
    public static StructuredFieldList parseList(final Header header) throws ParseException {
        return MessageSupport.parseHeaderValueStrict(header, StructuredFieldParser::parseList);
    }

    /**
     * Parses the matching field lines as a Structured Field List, reading each field line in place
     * and appending its members, without combining the values into a new buffer.
     *
     * @param headers the message headers.
     * @param name the case-insensitive field name.
     * @return the parsed List, empty when the field is absent.
     * @throws ParseException if any field line is invalid.
     */
    public static StructuredFieldList parseList(final MessageHeaders headers, final String name)
            throws ParseException {
        Args.notNull(headers, "Message headers");
        Args.notBlank(name, "Header name");
        final List<StructuredFieldMember> members = new ArrayList<>();
        MessageSupport.parseElementListStrict(headers, name, (buffer, cursor) ->
                members.add(StructuredFieldParser.parseListElement(buffer, cursor)));
        return StructuredFieldList.of(members);
    }

    /**
     * Parses one header as an Structured Field Dictionary.
     *
     * @param header the header.
     * @return the parsed Dictionary.
     * @throws ParseException if the complete field value is invalid.
     */
    public static StructuredFieldDictionary parseDictionary(final Header header) throws ParseException {
        return MessageSupport.parseHeaderValueStrict(header, StructuredFieldParser::parseDictionary);
    }

    /**
     * Parses the matching field lines as individual Structured Field Dictionary entries, preserving
     * their wire order and repeated keys.
     *
     * @param headers the message headers.
     * @param name the case-insensitive field name.
     * @return the parsed entries in wire order, including repeated keys.
     * @throws ParseException if any field line is invalid.
     */
    public static List<Map.Entry<String, StructuredFieldMember>> parseDictionaryEntries(
            final MessageHeaders headers, final String name) throws ParseException {
        Args.notNull(headers, "Message headers");
        Args.notBlank(name, "Header name");
        final List<Map.Entry<String, StructuredFieldMember>> entries = new ArrayList<>();
        MessageSupport.parseElementListStrict(headers, name, (buffer, cursor) -> {
            final Map<String, StructuredFieldMember> entry = new LinkedHashMap<>(1);
            StructuredFieldParser.parseDictionaryElement(buffer, cursor, entry);
            final Map.Entry<String, StructuredFieldMember> parsed = entry.entrySet().iterator().next();
            entries.add(new AbstractMap.SimpleImmutableEntry<>(parsed.getKey(), parsed.getValue()));
        });
        return Collections.unmodifiableList(entries);
    }

    /**
     * Parses the matching field lines as a Structured Field Dictionary, reading each field line in
     * place and merging its members, without combining the values into a new buffer. A repeated key
     * keeps its last value.
     *
     * @param headers the message headers.
     * @param name the case-insensitive field name.
     * @return the parsed Dictionary, empty when the field is absent.
     * @throws ParseException if any field line is invalid.
     */
    public static StructuredFieldDictionary parseDictionary(final MessageHeaders headers, final String name)
            throws ParseException {
        final Map<String, StructuredFieldMember> members = new LinkedHashMap<>();
        for (final Map.Entry<String, StructuredFieldMember> entry : parseDictionaryEntries(headers, name)) {
            members.put(entry.getKey(), entry.getValue());
        }
        return StructuredFieldDictionary.copyOf(members);
    }

    /**
     * Creates a header for a Structured Field value.
     *
     * @param name the field name.
     * @param value the Structured Field value.
     * @return a header, or {@code null} for an empty List or Dictionary.
     */
    public static Header format(final String name, final StructuredFieldValue value) {
        Args.notBlank(name, "Header name");
        Args.notNull(value, "Structured Field value");
        if (value instanceof StructuredFieldList && ((StructuredFieldList) value).isEmpty()
                || value instanceof StructuredFieldDictionary && ((StructuredFieldDictionary) value).isEmpty()) {
            return null;
        }
        final CharArrayBuffer buffer = new CharArrayBuffer(name.length() + 66);
        buffer.append(name);
        buffer.append(": ");
        StructuredFieldSerializer.serialize(buffer, value);
        return BufferedHeader.create(buffer);
    }

}
