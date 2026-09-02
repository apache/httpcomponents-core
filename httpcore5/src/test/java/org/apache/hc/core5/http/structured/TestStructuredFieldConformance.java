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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.hc.core5.http.ParseException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Runs the complete HTTP Working Group Structured Field test corpus.
 *
 * <p>The corpus is revision 1e280c3ed9ffe0ca5fdb1d97219dddc389007677
 * from <a href="https://github.com/httpwg/structured-field-tests">...</a>.</p>
 */
class TestStructuredFieldConformance {

    private static final String ROOT = "/org/apache/hc/core5/http/structured/rfc9651/";

    private static final String[] PARSING_FILES = {
        "binary.json",
        "boolean.json",
        "date.json",
        "dictionary.json",
        "display-string.json",
        "examples.json",
        "item.json",
        "key-generated.json",
        "large-generated.json",
        "list.json",
        "listlist.json",
        "number-generated.json",
        "number.json",
        "param-dict.json",
        "param-list.json",
        "param-listlist.json",
        "string-generated.json",
        "string.json",
        "token-generated.json",
        "token.json"
    };

    private static final String[] SERIALIZATION_FILES = {
        "serialisation-tests/key-generated.json",
        "serialisation-tests/number.json",
        "serialisation-tests/string-generated.json",
        "serialisation-tests/token-generated.json"
    };

    @Test
    void testOfficialCorpus() throws Exception {
        final List<String> failures = new ArrayList<>();
        int count = 0;
        for (final String file : PARSING_FILES) {
            count += runFile(file, true, failures);
        }
        for (final String file : SERIALIZATION_FILES) {
            count += runFile(file, false, failures);
        }
        Assertions.assertEquals(2135, count, "Unexpected number of HTTPWG test cases");
        if (!failures.isEmpty()) {
            Assertions.fail(failures.size() + " HTTPWG conformance failure(s):\n" + String.join("\n", failures));
        }
    }

    private static int runFile(
            final String file, final boolean parsing, final List<String> failures) throws IOException {
        final Object document;
        try (InputStream inputStream = TestStructuredFieldConformance.class.getResourceAsStream(ROOT + file)) {
            if (inputStream == null) {
                throw new IOException("Missing test corpus resource " + file);
            }
            document = new JsonReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)).parse();
        }
        final List<?> tests = list(document);
        for (final Object value : tests) {
            final Map<?, ?> test = map(value);
            final String name = string(test.get("name"));
            try {
                if (parsing) {
                    runParsingCase(test);
                } else {
                    runSerializationCase(test);
                }
            } catch (final Exception ex) {
                if (!booleanValue(test.get("must_fail")) && !booleanValue(test.get("can_fail"))) {
                    failures.add(file + " :: " + name + " :: unexpected "
                            + ex.getClass().getSimpleName() + ": " + ex.getMessage());
                }
            } catch (final AssertionError ex) {
                failures.add(file + " :: " + name + " :: " + ex.getMessage());
            }
        }
        return tests.size();
    }

    private static void runParsingCase(final Map<?, ?> test) throws Exception {
        final boolean mustFail = booleanValue(test.get("must_fail"));
        try {
            final StructuredFieldValue actual = parse(
                    string(test.get("header_type")), strings(test.get("raw")));
            if (mustFail) {
                throw new AssertionError("parsing succeeded but must_fail is true");
            }
            final StructuredFieldValue expected = value(
                    string(test.get("header_type")), test.get("expected"));
            if (!expected.equals(actual)) {
                throw new AssertionError("expected " + expected + " but parsed " + actual);
            }
            verifyCanonical(test, actual);
        } catch (final ParseException | IllegalArgumentException ex) {
            if (!mustFail) {
                throw ex;
            }
        }
    }

    private static void runSerializationCase(final Map<?, ?> test) {
        final boolean mustFail = booleanValue(test.get("must_fail"));
        try {
            final StructuredFieldValue value = value(
                    string(test.get("header_type")), test.get("expected"));
            final String actual = StructuredFieldSerializer.serialize(value);
            if (mustFail) {
                throw new AssertionError("serialization succeeded but must_fail is true: " + actual);
            }
            verifyCanonical(test, value);
        } catch (final IllegalArgumentException ex) {
            if (!mustFail) {
                throw ex;
            }
        }
    }

    private static void verifyCanonical(final Map<?, ?> test, final StructuredFieldValue value) {
        final Object canonical = test.containsKey("canonical") ? test.get("canonical") : test.get("raw");
        final List<String> fieldLines = strings(canonical);
        final String expected = fieldLines.isEmpty() ? null : String.join(", ", fieldLines);
        final String actual = StructuredFieldSerializer.serialize(value);
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("expected canonical " + expected + " but serialized " + actual);
        }
    }

    private static StructuredFieldValue parse(final String type, final List<String> lines) throws ParseException {
        switch (type) {
        case "item":
            return StructuredFieldParser.parseItemLines(lines);
        case "list":
            return StructuredFieldParser.parseListLines(lines);
        case "dictionary":
            return StructuredFieldParser.parseDictionaryLines(lines);
        default:
            throw new IllegalArgumentException("Unknown header type " + type);
        }
    }

    private static StructuredFieldValue value(final String type, final Object expected) {
        switch (type) {
        case "item":
            return item(expected);
        case "list":
            return structuredList(expected);
        case "dictionary":
            return dictionary(expected);
        default:
            throw new IllegalArgumentException("Unknown header type " + type);
        }
    }

    private static StructuredFieldList structuredList(final Object value) {
        final List<StructuredFieldMember> members = new ArrayList<>();
        for (final Object member : list(value)) {
            members.add(member(member));
        }
        return StructuredFieldList.of(members);
    }

    private static StructuredFieldDictionary dictionary(final Object value) {
        final StructuredFieldDictionary.Builder builder = StructuredFieldDictionary.builder();
        for (final Object entry : list(value)) {
            final List<?> pair = list(entry);
            builder.put(string(pair.get(0)), member(pair.get(1)));
        }
        return builder.build();
    }

    private static StructuredFieldMember member(final Object value) {
        final List<?> pair = list(value);
        if (pair.get(0) instanceof List<?>) {
            final List<StructuredFieldItem> items = new ArrayList<>();
            for (final Object item : list(pair.get(0))) {
                items.add(item(item));
            }
            return StructuredFieldInnerList.of(items, parameters(pair.get(1)));
        }
        return item(value);
    }

    private static StructuredFieldItem item(final Object value) {
        final List<?> pair = list(value);
        return StructuredFieldItem.of(bareItem(pair.get(0)), parameters(pair.get(1)));
    }

    private static StructuredFieldParameters parameters(final Object value) {
        final StructuredFieldParameters.Builder builder = StructuredFieldParameters.builder();
        for (final Object parameter : list(value)) {
            final List<?> pair = list(parameter);
            builder.put(string(pair.get(0)), bareItem(pair.get(1)));
        }
        return builder.build();
    }

    private static StructuredFieldBareItem bareItem(final Object value) {
        if (value instanceof Boolean) {
            return StructuredFieldBareItem.ofBoolean(((Boolean) value).booleanValue());
        }
        if (value instanceof Long) {
            return StructuredFieldBareItem.ofInteger(((Long) value).longValue());
        }
        if (value instanceof BigDecimal) {
            return StructuredFieldBareItem.ofDecimal((BigDecimal) value);
        }
        if (value instanceof String) {
            return StructuredFieldBareItem.ofString((String) value);
        }
        final Map<?, ?> typed = map(value);
        final String type = string(typed.get("__type"));
        switch (type) {
        case "token":
            return StructuredFieldBareItem.ofToken(string(typed.get("value")));
        case "binary":
            return StructuredFieldBareItem.ofByteSequence(decodeBase32(string(typed.get("value"))));
        case "date":
            return StructuredFieldBareItem.ofDate(((Long) typed.get("value")).longValue());
        case "displaystring":
            return StructuredFieldBareItem.ofDisplayString(string(typed.get("value")));
        default:
            throw new IllegalArgumentException("Unknown bare Item type " + type);
        }
    }

    private static byte[] decodeBase32(final String encoded) {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        int bits = 0;
        int buffer = 0;
        for (int i = 0; i < encoded.length(); i++) {
            final char ch = encoded.charAt(i);
            if (ch == '=') {
                break;
            }
            final int value = ch >= 'A' && ch <= 'Z' ? ch - 'A'
                    : ch >= '2' && ch <= '7' ? ch - '2' + 26 : -1;
            if (value < 0) {
                throw new IllegalArgumentException("Invalid base32 character " + ch);
            }
            buffer = buffer << 5 | value;
            bits += 5;
            if (bits >= 8) {
                output.write(buffer >> (bits - 8) & 0xff);
                bits -= 8;
            }
        }
        return output.toByteArray();
    }

    private static boolean booleanValue(final Object value) {
        return value instanceof Boolean && ((Boolean) value).booleanValue();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(final Object value) {
        if (!(value instanceof List<?>)) {
            throw new IllegalArgumentException("Expected JSON array but got " + value);
        }
        return (List<Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> map(final Object value) {
        if (!(value instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("Expected JSON object but got " + value);
        }
        return (Map<Object, Object>) value;
    }

    private static String string(final Object value) {
        if (!(value instanceof String)) {
            throw new IllegalArgumentException("Expected JSON string but got " + value);
        }
        return (String) value;
    }

    private static List<String> strings(final Object value) {
        final List<String> strings = new ArrayList<>();
        for (final Object element : list(value)) {
            strings.add(string(element));
        }
        return strings;
    }

    private static final class JsonReader {

        private final Reader reader;
        private int current = -2;

        JsonReader(final Reader reader) {
            this.reader = reader;
        }

        Object parse() throws IOException {
            final Object value = readValue();
            skipWhitespace();
            if (peek() != -1) {
                throw error("Trailing JSON data");
            }
            return value;
        }

        private Object readValue() throws IOException {
            skipWhitespace();
            switch (peek()) {
            case '[':
                return readArray();
            case '{':
                return readObject();
            case '"':
                return readString();
            case 't':
                readLiteral("true");
                return Boolean.TRUE;
            case 'f':
                readLiteral("false");
                return Boolean.FALSE;
            case 'n':
                readLiteral("null");
                return null;
            default:
                return readNumber();
            }
        }

        private List<Object> readArray() throws IOException {
            expect('[');
            final List<Object> values = new ArrayList<>();
            skipWhitespace();
            if (peek() == ']') {
                read();
                return values;
            }
            while (true) {
                values.add(readValue());
                skipWhitespace();
                final int ch = read();
                if (ch == ']') {
                    return values;
                }
                if (ch != ',') {
                    throw error("Expected ',' or ']'");
                }
            }
        }

        private Map<Object, Object> readObject() throws IOException {
            expect('{');
            final Map<Object, Object> values = new LinkedHashMap<>();
            skipWhitespace();
            if (peek() == '}') {
                read();
                return values;
            }
            while (true) {
                skipWhitespace();
                final String key = readString();
                skipWhitespace();
                expect(':');
                values.put(key, readValue());
                skipWhitespace();
                final int ch = read();
                if (ch == '}') {
                    return values;
                }
                if (ch != ',') {
                    throw error("Expected ',' or '}'");
                }
            }
        }

        private String readString() throws IOException {
            expect('"');
            final StringBuilder buffer = new StringBuilder();
            while (true) {
                final int ch = read();
                if (ch == -1) {
                    throw error("Unterminated JSON string");
                }
                if (ch == '"') {
                    return buffer.toString();
                }
                if (ch == '\\') {
                    final int escaped = read();
                    switch (escaped) {
                    case '"':
                    case '\\':
                    case '/':
                        buffer.append((char) escaped);
                        break;
                    case 'b':
                        buffer.append('\b');
                        break;
                    case 'f':
                        buffer.append('\f');
                        break;
                    case 'n':
                        buffer.append('\n');
                        break;
                    case 'r':
                        buffer.append('\r');
                        break;
                    case 't':
                        buffer.append('\t');
                        break;
                    case 'u':
                        buffer.append((char) readHex4());
                        break;
                    default:
                        throw error("Invalid JSON escape");
                    }
                } else {
                    buffer.append((char) ch);
                }
            }
        }

        private Number readNumber() throws IOException {
            final StringBuilder buffer = new StringBuilder();
            int ch = peek();
            if (ch == '-') {
                buffer.append((char) read());
                ch = peek();
            }
            if (ch < '0' || ch > '9') {
                throw error("Invalid JSON number");
            }
            while ((ch = peek()) >= '0' && ch <= '9') {
                buffer.append((char) read());
            }
            boolean decimal = false;
            if (peek() == '.') {
                decimal = true;
                buffer.append((char) read());
                while ((ch = peek()) >= '0' && ch <= '9') {
                    buffer.append((char) read());
                }
            }
            ch = peek();
            if (ch == 'e' || ch == 'E') {
                decimal = true;
                buffer.append((char) read());
                ch = peek();
                if (ch == '+' || ch == '-') {
                    buffer.append((char) read());
                }
                while ((ch = peek()) >= '0' && ch <= '9') {
                    buffer.append((char) read());
                }
            }
            return decimal ? new BigDecimal(buffer.toString()) : Long.valueOf(buffer.toString());
        }

        private void readLiteral(final String literal) throws IOException {
            for (int i = 0; i < literal.length(); i++) {
                expect(literal.charAt(i));
            }
        }

        private int readHex4() throws IOException {
            int value = 0;
            for (int i = 0; i < 4; i++) {
                final int ch = read();
                final int digit = ch >= '0' && ch <= '9' ? ch - '0'
                        : ch >= 'a' && ch <= 'f' ? ch - 'a' + 10
                        : ch >= 'A' && ch <= 'F' ? ch - 'A' + 10 : -1;
                if (digit < 0) {
                    throw error("Invalid JSON Unicode escape");
                }
                value = value << 4 | digit;
            }
            return value;
        }

        private void skipWhitespace() throws IOException {
            int ch;
            while ((ch = peek()) == ' ' || ch == '\t' || ch == '\r' || ch == '\n') {
                read();
            }
        }

        private void expect(final int expected) throws IOException {
            if (read() != expected) {
                throw error("Expected '" + (char) expected + "'");
            }
        }

        private int peek() throws IOException {
            if (current == -2) {
                current = reader.read();
            }
            return current;
        }

        private int read() throws IOException {
            final int value = peek();
            current = -2;
            return value;
        }

        private IOException error(final String message) {
            return new IOException(message);
        }
    }
}
