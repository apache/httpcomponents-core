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
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.util.Tokenizer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestStructuredFieldParser {

    @Test
    void testAllBareItemTypes() throws Exception {
        assertItem("42", StructuredFieldType.INTEGER, Long.valueOf(42));
        assertItem("-999999999999999", StructuredFieldType.INTEGER, Long.valueOf(-999999999999999L));
        assertItem("4.5", StructuredFieldType.DECIMAL, new BigDecimal("4.5"));
        assertItem("\"hello \\\"world\\\"\"", StructuredFieldType.STRING, "hello \"world\"");
        assertItem("foo123/456", StructuredFieldType.TOKEN, "foo123/456");
        assertItem("?1", StructuredFieldType.BOOLEAN, Boolean.TRUE);
        assertItem("@1659578233", StructuredFieldType.DATE, Long.valueOf(1659578233));
        assertItem("%\"f%c3%bc%c3%bc\"", StructuredFieldType.DISPLAY_STRING, "füü");

        final StructuredFieldBareItem bytes = StructuredFieldParser.parseItem(":aGVsbG8=:").getBareItem();
        Assertions.assertEquals(StructuredFieldType.BYTE_SEQUENCE, bytes.getType());
        Assertions.assertArrayEquals("hello".getBytes(StandardCharsets.US_ASCII), bytes.getByteSequenceValue());
    }

    @Test
    void testListAndInnerList() throws Exception {
        final StructuredFieldList list = StructuredFieldParser.parseList(
                "sugar, (tea \"with milk\";q=0.7);fresh, rum;proof=40");
        Assertions.assertEquals(3, list.size());
        Assertions.assertEquals("sugar", ((StructuredFieldItem) list.get(0)).getBareItem().getTextValue());
        final StructuredFieldInnerList inner = (StructuredFieldInnerList) list.get(1);
        Assertions.assertEquals(2, inner.size());
        Assertions.assertEquals(new BigDecimal("0.7"), inner.get(1).getParameters().get("q").getDecimalValue());
        Assertions.assertTrue(inner.getParameters().get("fresh").getBooleanValue());
        Assertions.assertEquals(40L, ((StructuredFieldItem) list.get(2)).getParameters().get("proof").getLongValue());
    }

    @Test
    void testDictionaryAndDuplicateReplacement() throws Exception {
        final StructuredFieldDictionary dictionary = StructuredFieldParser.parseDictionary(
                "a=?0, b, c;foo=bar, a=(1 2);valid");
        Assertions.assertEquals(3, dictionary.size());
        Assertions.assertEquals("a", dictionary.getName(0));
        Assertions.assertTrue(dictionary.get("a") instanceof StructuredFieldInnerList);
        Assertions.assertTrue(((StructuredFieldItem) dictionary.get("b")).getBareItem().getBooleanValue());
        Assertions.assertEquals("bar", ((StructuredFieldItem) dictionary.get("c"))
                .getParameters().get("foo").getTextValue());
    }

    @Test
    void testDuplicateParameterReplacementPreservesPosition() throws Exception {
        final StructuredFieldParameters parameters = StructuredFieldParser.parseItem("token;a=1;b=2;a=3")
                .getParameters();
        Assertions.assertEquals(2, parameters.size());
        Assertions.assertEquals("a", parameters.getName(0));
        Assertions.assertEquals(3L, parameters.get("a").getLongValue());
    }

    @Test
    void testMultipleFieldLines() throws Exception {
        final StructuredFieldList list = StructuredFieldParser.parseListLines(Arrays.asList("one, two", "three"));
        Assertions.assertEquals(3, list.size());
        Assertions.assertEquals("one, two, three", StructuredFieldSerializer.serializeList(list));

        final StructuredFieldDictionary dictionary = StructuredFieldParser.parseDictionaryLines(
                Arrays.asList("a=1", "b=2"));
        Assertions.assertEquals("a=1, b=2", StructuredFieldSerializer.serializeDictionary(dictionary));
    }

    @Test
    void testTokenizerCursorBoundsAndPosition() throws Exception {
        final String input = "xx 42 yy";
        final Tokenizer.Cursor cursor = new Tokenizer.Cursor(2, 6);
        final StructuredFieldItem item = StructuredFieldParser.parseItem(input, cursor);
        Assertions.assertEquals(42L, item.getBareItem().getLongValue());
        Assertions.assertEquals(6, cursor.getPos());
    }

    @Test
    void testRfcMinimumContainerSizes() throws Exception {
        final StringBuilder listValue = new StringBuilder();
        for (int i = 0; i < 1024; i++) {
            if (i > 0) {
                listValue.append(',');
            }
            listValue.append(i);
        }
        Assertions.assertEquals(1024, StructuredFieldParser.parseList(listValue).size());

        final StringBuilder parameters = new StringBuilder("token");
        for (int i = 0; i < 256; i++) {
            parameters.append(";p").append(i);
        }
        Assertions.assertEquals(256, StructuredFieldParser.parseItem(parameters).getParameters().size());
    }

    private static void assertItem(
            final String encoded, final StructuredFieldType type, final Object expected) throws ParseException {
        final StructuredFieldBareItem item = StructuredFieldParser.parseItem(encoded).getBareItem();
        Assertions.assertEquals(type, item.getType());
        Assertions.assertEquals(expected, item.getValue());
    }
}
