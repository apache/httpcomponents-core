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

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.ByteArrayBuffer;
import org.apache.hc.core5.util.CharArrayBuffer;
import org.apache.hc.core5.util.Tokenizer;

/**
 * Strict parser for Structured Field Structured Field Values.
 *
 * @since 5.5
 */
final class StructuredFieldParser {

    private static final Tokenizer TOKENIZER = Tokenizer.INSTANCE;
    private static final Tokenizer.Delimiter KEY_TERMINATOR =
            ch -> !StructuredFieldRules.isKeyChar(ch);
    private static final Tokenizer.Delimiter TOKEN_TERMINATOR =
            ch -> !StructuredFieldRules.isTokenChar(ch);

    private final CharSequence input;
    private final Tokenizer.Cursor cursor;

    private StructuredFieldParser(final CharSequence input, final Tokenizer.Cursor cursor) throws ParseException {
        this.input = Args.notNull(input, "Structured Field input");
        this.cursor = Args.notNull(cursor, "Parser cursor");
        if (cursor.getUpperBound() > input.length()) {
            throw new IllegalArgumentException("Parser cursor exceeds Structured Field input length");
        }
    }

    /**
     * Parses a complete Structured Field Item field value.
     *
     * @param input the field value.
     * @return the parsed Item.
     * @throws ParseException if the complete value is invalid.
     */
    static StructuredFieldItem parseItem(final CharSequence input) throws ParseException {
        Args.notNull(input, "Structured Field input");
        return parseItem(input, new Tokenizer.Cursor(0, input.length()));
    }

    /**
     * Parses an Item from the current cursor position through its upper bound.
     *
     * @param input the character sequence containing the field value.
     * @param cursor the bounds and current parse position.
     * @return the parsed Item.
     * @throws ParseException if the complete bounded value is invalid.
     */
    static StructuredFieldItem parseItem(
            final CharSequence input, final Tokenizer.Cursor cursor) throws ParseException {
        final StructuredFieldParser parser = new StructuredFieldParser(input, cursor);
        parser.skipSpaces();
        final StructuredFieldItem result = parser.parseItemValue();
        parser.skipSpaces();
        parser.requireEnd();
        return result;
    }

    /**
     * Parses a complete Structured Field List field value.
     *
     * @param input the field value.
     * @return the parsed List.
     * @throws ParseException if the complete value is invalid.
     */
    static StructuredFieldList parseList(final CharSequence input) throws ParseException {
        Args.notNull(input, "Structured Field input");
        return parseList(input, new Tokenizer.Cursor(0, input.length()));
    }

    /**
     * Parses a List from the current cursor position through its upper bound.
     *
     * @param input the character sequence containing the field value.
     * @param cursor the bounds and current parse position.
     * @return the parsed List.
     * @throws ParseException if the complete bounded value is invalid.
     */
    static StructuredFieldList parseList(
            final CharSequence input, final Tokenizer.Cursor cursor) throws ParseException {
        final StructuredFieldParser parser = new StructuredFieldParser(input, cursor);
        parser.skipSpaces();
        final StructuredFieldList result = parser.parseListValue();
        parser.skipSpaces();
        parser.requireEnd();
        return result;
    }

    /**
     * Parses a complete Structured Field Dictionary field value.
     *
     * @param input the field value.
     * @return the parsed Dictionary.
     * @throws ParseException if the complete value is invalid.
     */
    static StructuredFieldDictionary parseDictionary(final CharSequence input) throws ParseException {
        Args.notNull(input, "Structured Field input");
        return parseDictionary(input, new Tokenizer.Cursor(0, input.length()));
    }

    /**
     * Parses a Dictionary from the current cursor position through its upper bound.
     *
     * @param input the character sequence containing the field value.
     * @param cursor the bounds and current parse position.
     * @return the parsed Dictionary.
     * @throws ParseException if the complete bounded value is invalid.
     */
    static StructuredFieldDictionary parseDictionary(
            final CharSequence input, final Tokenizer.Cursor cursor) throws ParseException {
        final StructuredFieldParser parser = new StructuredFieldParser(input, cursor);
        parser.skipSpaces();
        final StructuredFieldDictionary result = parser.parseDictionaryValue();
        parser.skipSpaces();
        parser.requireEnd();
        return result;
    }

    /**
     * Parses a single List member at the current cursor position, consuming the surrounding optional
     * whitespace so that the cursor stops at the following comma or the upper bound. One field line
     * element is parsed in place, without copying the underlying data.
     *
     * @param input the character sequence containing the field line value.
     * @param cursor the bounds and current parse position.
     * @return the parsed member.
     * @throws ParseException if the member is invalid or is not comma-separated.
     */
    static StructuredFieldMember parseListElement(final CharSequence input, final Tokenizer.Cursor cursor)
            throws ParseException {
        final StructuredFieldParser parser = new StructuredFieldParser(input, cursor);
        parser.skipOptionalWhitespace();
        final StructuredFieldMember member = parser.parseMember();
        parser.requireElementSeparator();
        return member;
    }

    /**
     * Parses a single Dictionary entry at the current cursor position into {@code accumulator},
     * consuming the surrounding optional whitespace so that the cursor stops at the following comma or
     * the upper bound; a repeated key keeps its last value. One field line element is parsed in place,
     * without copying the underlying data.
     *
     * @param input the character sequence containing the field line value.
     * @param cursor the bounds and current parse position.
     * @param accumulator the map into which the parsed entry is merged.
     * @throws ParseException if the entry is invalid or is not comma-separated.
     */
    static void parseDictionaryElement(final CharSequence input, final Tokenizer.Cursor cursor,
                                       final Map<String, StructuredFieldMember> accumulator) throws ParseException {
        final StructuredFieldParser parser = new StructuredFieldParser(input, cursor);
        parser.skipOptionalWhitespace();
        final String key = parser.parseKey();
        final StructuredFieldMember member;
        if (parser.peek('=')) {
            parser.advance();
            member = parser.parseMember();
        } else {
            member = StructuredFieldItem.of(StructuredFieldBareItem.ofBoolean(true), parser.parseParameters());
        }
        accumulator.put(key, member);
        parser.requireElementSeparator();
    }

    /**
     * Combines field lines with a comma and parses an Structured Field Item.
     *
     * @param fieldValues the field line values.
     * @return the parsed Item.
     * @throws ParseException if the combined field value is invalid.
     */
    static StructuredFieldItem parseItemLines(final Iterable<? extends CharSequence> fieldValues)
            throws ParseException {
        return parseItem(combine(fieldValues));
    }

    /**
     * Combines field lines with a comma and parses an Structured Field List.
     *
     * @param fieldValues the field line values.
     * @return the parsed List.
     * @throws ParseException if the combined field value is invalid.
     */
    static StructuredFieldList parseListLines(final Iterable<? extends CharSequence> fieldValues)
            throws ParseException {
        return parseList(combine(fieldValues));
    }

    /**
     * Combines field lines with a comma and parses an Structured Field Dictionary.
     *
     * @param fieldValues the field line values.
     * @return the parsed Dictionary.
     * @throws ParseException if the combined field value is invalid.
     */
    static StructuredFieldDictionary parseDictionaryLines(
            final Iterable<? extends CharSequence> fieldValues) throws ParseException {
        return parseDictionary(combine(fieldValues));
    }

    private static String combine(final Iterable<? extends CharSequence> fieldValues) {
        Args.notNull(fieldValues, "Field values");
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        boolean first = true;
        for (final CharSequence fieldValue : fieldValues) {
            if (!first) {
                buffer.append(", ");
            }
            buffer.append(Args.notNull(fieldValue, "Field value").toString());
            first = false;
        }
        return buffer.toString();
    }

    private StructuredFieldList parseListValue() throws ParseException {
        final List<StructuredFieldMember> members = new ArrayList<>();
        while (!atEnd()) {
            members.add(parseMember());
            skipOptionalWhitespace();
            if (atEnd()) {
                break;
            }
            require(',');
            skipOptionalWhitespace();
            if (atEnd()) {
                throw error("Trailing comma in Structured Field List");
            }
        }
        return StructuredFieldList.of(members);
    }

    private StructuredFieldDictionary parseDictionaryValue() throws ParseException {
        final LinkedHashMap<String, StructuredFieldMember> members = new LinkedHashMap<>();
        while (!atEnd()) {
            final String key = parseKey();
            final StructuredFieldMember member;
            if (peek('=')) {
                advance();
                member = parseMember();
            } else {
                member = StructuredFieldItem.of(StructuredFieldBareItem.ofBoolean(true), parseParameters());
            }
            members.put(key, member);
            skipOptionalWhitespace();
            if (atEnd()) {
                break;
            }
            require(',');
            skipOptionalWhitespace();
            if (atEnd()) {
                throw error("Trailing comma in Structured Field Dictionary");
            }
        }
        return StructuredFieldDictionary.copyOf(members);
    }

    private void requireElementSeparator() throws ParseException {
        skipOptionalWhitespace();
        if (!atEnd() && !peek(',')) {
            throw error("Structured Field members must be separated by a comma");
        }
    }

    private StructuredFieldMember parseMember() throws ParseException {
        return peek('(') ? parseInnerList() : parseItemValue();
    }

    private StructuredFieldInnerList parseInnerList() throws ParseException {
        require('(');
        final List<StructuredFieldItem> items = new ArrayList<>();
        while (!atEnd()) {
            skipSpaces();
            if (peek(')')) {
                advance();
                return StructuredFieldInnerList.of(items, parseParameters());
            }
            items.add(parseItemValue());
            if (atEnd() || current() != Tokenizer.SP && current() != ')') {
                throw error("Inner List Items must be separated by SP");
            }
        }
        throw error("Unterminated Structured Field Inner List");
    }

    private StructuredFieldItem parseItemValue() throws ParseException {
        return StructuredFieldItem.of(parseBareItem(), parseParameters());
    }

    private StructuredFieldParameters parseParameters() throws ParseException {
        final Map<String, StructuredFieldBareItem> parameters = new LinkedHashMap<>();
        while (peek(';')) {
            advance();
            skipSpaces();
            final String key = parseKey();
            final StructuredFieldBareItem value;
            if (peek('=')) {
                advance();
                value = parseBareItem();
            } else {
                value = StructuredFieldBareItem.ofBoolean(true);
            }
            parameters.put(key, value);
        }
        return StructuredFieldParameters.copyOf(parameters);
    }

    private String parseKey() throws ParseException {
        if (atEnd() || !StructuredFieldRules.isKeyStart(current())) {
            throw error("Invalid Structured Field key");
        }
        return TOKENIZER.parseContent(input, cursor, KEY_TERMINATOR);
    }

    private StructuredFieldBareItem parseBareItem() throws ParseException {
        if (atEnd()) {
            throw error("Missing Structured Field bare Item");
        }
        final char ch = current();
        if (ch == '-' || StructuredFieldRules.isDigit(ch)) {
            return parseNumber();
        }
        if (ch == Tokenizer.DQUOTE) {
            return StructuredFieldBareItem.ofString(parseString());
        }
        if (StructuredFieldRules.isAlpha(ch) || ch == '*') {
            return StructuredFieldBareItem.ofToken(parseToken());
        }
        if (ch == ':') {
            return StructuredFieldBareItem.ofByteSequence(parseByteSequence());
        }
        if (ch == '?') {
            return StructuredFieldBareItem.ofBoolean(parseBoolean());
        }
        if (ch == '@') {
            advance();
            final StructuredFieldBareItem value = parseNumber();
            if (value.getType() != StructuredFieldType.INTEGER) {
                throw error("Structured Field Date must be an Integer");
            }
            return StructuredFieldBareItem.ofDate(value.getLongValue());
        }
        if (ch == '%') {
            return StructuredFieldBareItem.ofDisplayString(parseDisplayString());
        }
        throw error("Unrecognized Structured Field bare Item");
    }

    private StructuredFieldBareItem parseNumber() throws ParseException {
        boolean negative = false;
        if (peek('-')) {
            negative = true;
            advance();
        }
        if (atEnd() || !StructuredFieldRules.isDigit(current())) {
            throw error("Invalid Structured Field number");
        }
        final int digitsStart = position();
        int integerDigits = 0;
        while (!atEnd() && StructuredFieldRules.isDigit(current())) {
            advance();
            if (++integerDigits > 15) {
                throw error("Structured Field Integer has more than 15 digits");
            }
        }
        if (!peek('.')) {
            final String number = subsequence(digitsStart, position());
            final long value;
            try {
                value = Long.parseLong(number);
            } catch (final NumberFormatException ex) {
                throw error("Invalid Structured Field Integer");
            }
            return StructuredFieldBareItem.ofInteger(negative ? -value : value);
        }
        if (integerDigits > 12) {
            throw error("Structured Field Decimal has more than 12 integer digits");
        }
        advance();
        final int fractionStart = position();
        while (!atEnd() && StructuredFieldRules.isDigit(current())) {
            advance();
            if (position() - fractionStart > 3) {
                throw error("Structured Field Decimal has more than 3 fractional digits");
            }
        }
        if (position() == fractionStart) {
            throw error("Structured Field Decimal has no fractional digits");
        }
        final int numberStart = negative ? digitsStart - 1 : digitsStart;
        return StructuredFieldBareItem.ofDecimal(new BigDecimal(subsequence(numberStart, position())));
    }

    private String parseString() throws ParseException {
        require(Tokenizer.DQUOTE);
        final CharArrayBuffer buffer = new CharArrayBuffer(32);
        while (!atEnd()) {
            final char ch = current();
            advance();
            if (ch == Tokenizer.ESCAPE) {
                if (atEnd()) {
                    throw error("Incomplete escape in Structured Field String");
                }
                final char escaped = current();
                advance();
                if (escaped != Tokenizer.DQUOTE && escaped != Tokenizer.ESCAPE) {
                    throw error("Invalid escape in Structured Field String");
                }
                buffer.append(escaped);
            } else if (ch == Tokenizer.DQUOTE) {
                return buffer.toString();
            } else if (ch < 0x20 || ch > 0x7e) {
                throw error("Invalid character in Structured Field String");
            } else {
                buffer.append(ch);
            }
        }
        throw error("Unterminated Structured Field String");
    }

    private String parseToken() {
        return TOKENIZER.parseContent(input, cursor, TOKEN_TERMINATOR);
    }

    private byte[] parseByteSequence() throws ParseException {
        require(':');
        final int start = position();
        while (!atEnd() && current() != ':') {
            final char ch = current();
            if (!StructuredFieldRules.isAlpha(ch) && !StructuredFieldRules.isDigit(ch)
                    && ch != '+' && ch != '/' && ch != '=') {
                throw error("Invalid character in Structured Field Byte Sequence");
            }
            advance();
        }
        if (atEnd()) {
            throw error("Unterminated Structured Field Byte Sequence");
        }
        final String encoded = subsequence(start, position());
        advance();
        final String padded = padBase64(encoded);
        try {
            return Base64.getDecoder().decode(padded);
        } catch (final IllegalArgumentException ex) {
            throw error("Invalid base64 in Structured Field Byte Sequence");
        }
    }

    private String padBase64(final String encoded) throws ParseException {
        final int paddingStart = encoded.indexOf('=');
        final int contentLength = paddingStart >= 0 ? paddingStart : encoded.length();
        for (int i = contentLength; i < encoded.length(); i++) {
            if (encoded.charAt(i) != '=') {
                throw error("Invalid padding in Structured Field Byte Sequence");
            }
        }
        final int remainder = contentLength % 4;
        if (remainder == 1) {
            throw error("Invalid Structured Field Byte Sequence length");
        }
        final int requiredPadding = remainder == 0 ? 0 : 4 - remainder;
        final int suppliedPadding = encoded.length() - contentLength;
        if (suppliedPadding > requiredPadding) {
            throw error("Invalid padding in Structured Field Byte Sequence");
        }
        if (suppliedPadding == requiredPadding) {
            return encoded;
        }
        final StringBuilder buffer = new StringBuilder(encoded);
        for (int i = suppliedPadding; i < requiredPadding; i++) {
            buffer.append('=');
        }
        return buffer.toString();
    }

    private boolean parseBoolean() throws ParseException {
        require('?');
        if (atEnd()) {
            throw error("Missing Structured Field Boolean value");
        }
        final char ch = current();
        advance();
        if (ch == '1') {
            return true;
        }
        if (ch == '0') {
            return false;
        }
        throw error("Invalid Structured Field Boolean");
    }

    private String parseDisplayString() throws ParseException {
        require('%');
        require(Tokenizer.DQUOTE);
        final ByteArrayBuffer bytes = new ByteArrayBuffer(64);
        while (!atEnd()) {
            final char ch = current();
            advance();
            if (ch < 0x20 || ch > 0x7e) {
                throw error("Invalid character in Structured Field Display String");
            }
            if (ch == Tokenizer.DQUOTE) {
                try {
                    return StandardCharsets.UTF_8.newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(bytes.array(), 0, bytes.length())).toString();
                } catch (final CharacterCodingException ex) {
                    throw error("Invalid UTF-8 in Structured Field Display String");
                }
            }
            if (ch == '%') {
                if (position() + 2 > cursor.getUpperBound()) {
                    throw error("Incomplete percent escape in Structured Field Display String");
                }
                final int high = lowerHex(current());
                advance();
                final int low = lowerHex(current());
                advance();
                if (high < 0 || low < 0) {
                    throw error("Display String percent escapes must use lowercase hexadecimal");
                }
                bytes.append(high << 4 | low);
            } else {
                bytes.append(ch);
            }
        }
        throw error("Unterminated Structured Field Display String");
    }

    private static int lowerHex(final char ch) {
        if (ch <= 0x7f && Character.isDigit(ch)) {
            return ch - '0';
        }
        if (ch >= 'a' && ch <= 'f') {
            return ch - 'a' + 10;
        }
        return -1;
    }

    private void skipSpaces() {
        while (peek((char) Tokenizer.SP)) {
            advance();
        }
    }

    private void skipOptionalWhitespace() {
        while (!atEnd() && (current() == Tokenizer.SP || current() == Tokenizer.HT)) {
            advance();
        }
    }

    private boolean peek(final char ch) {
        return !atEnd() && current() == ch;
    }

    private void require(final char ch) throws ParseException {
        if (!peek(ch)) {
            throw error("Expected '" + ch + "'");
        }
        advance();
    }

    private void requireEnd() throws ParseException {
        if (!atEnd()) {
            throw error("Trailing data after Structured Field value");
        }
    }

    private char current() {
        return input.charAt(position());
    }

    private int position() {
        return cursor.getPos();
    }

    private void advance() {
        cursor.updatePos(position() + 1);
    }

    private String subsequence(final int start, final int end) {
        return input.subSequence(start, end).toString();
    }

    private boolean atEnd() {
        return cursor.atEnd();
    }

    private ParseException error(final String description) {
        return new ParseException(
                description,
                input,
                cursor.getLowerBound(),
                cursor.getUpperBound() - cursor.getLowerBound(),
                position());
    }
}
