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
import java.util.Arrays;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BufferedHeader;
import org.apache.hc.core5.http.message.HeaderGroup;
import org.apache.hc.core5.util.CharArrayBuffer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestStructuredFieldSerializer {

    @Test
    void testCanonicalNumbersAndDisplayString() {
        Assertions.assertEquals("0.0", bare(StructuredFieldBareItem.ofDecimal(new BigDecimal("-0.0001"))));
        Assertions.assertEquals("1.234", bare(StructuredFieldBareItem.ofDecimal(new BigDecimal("1.2344"))));
        Assertions.assertEquals("1.234", bare(StructuredFieldBareItem.ofDecimal(new BigDecimal("1.2345"))));
        Assertions.assertEquals("1.236", bare(StructuredFieldBareItem.ofDecimal(new BigDecimal("1.2355"))));
        Assertions.assertEquals("0.002", bare(StructuredFieldBareItem.ofDecimal(new BigDecimal("0.0015"))));
        Assertions.assertEquals("0.002", bare(StructuredFieldBareItem.ofDecimal(new BigDecimal("0.0025"))));
        Assertions.assertEquals("-0.002", bare(StructuredFieldBareItem.ofDecimal(new BigDecimal("-0.0015"))));
        Assertions.assertEquals("-0.002", bare(StructuredFieldBareItem.ofDecimal(new BigDecimal("-0.0025"))));
        Assertions.assertEquals("10.0", bare(StructuredFieldBareItem.ofDecimal(new BigDecimal("9.9995"))));
        Assertions.assertEquals("%\"F%c3%bc%c3%9fe %22%25%22\"",
                bare(StructuredFieldBareItem.ofDisplayString("Füße \"%\"")));
    }

    @Test
    void testCanonicalDictionaryAndParameters() {
        final StructuredFieldParameters parameters = StructuredFieldParameters.builder()
                .putBoolean("valid", true)
                .putBoolean("cached", false)
                .put("ttl", StructuredFieldBareItem.ofInteger(60))
                .build();
        final StructuredFieldDictionary dictionary = StructuredFieldDictionary.builder()
                .put("available", StructuredFieldItem.of(StructuredFieldBareItem.ofBoolean(true), parameters))
                .put("rating", StructuredFieldItem.of(
                        StructuredFieldBareItem.ofDecimal(new BigDecimal("1.500"))))
                .put("feelings", StructuredFieldInnerList.of(
                        StructuredFieldItem.of(StructuredFieldBareItem.ofToken("joy")),
                        StructuredFieldItem.of(StructuredFieldBareItem.ofToken("sadness"))))
                .build();
        Assertions.assertEquals(
                "available;valid;cached=?0;ttl=60, rating=1.5, feelings=(joy sadness)",
                StructuredFieldSerializer.serializeDictionary(dictionary));
    }

    @Test
    void testEmptyContainersOmitField() throws Exception {
        final StructuredFieldList list = StructuredFieldList.of();
        final StructuredFieldDictionary dictionary = StructuredFieldDictionary.builder().build();
        Assertions.assertNull(StructuredFieldSerializer.serializeList(list));
        Assertions.assertNull(StructuredFieldSerializer.serializeDictionary(dictionary));
        Assertions.assertNull(StructuredFieldHeaders.format("Example", list));
        Assertions.assertTrue(StructuredFieldParser.parseList("").isEmpty());
        Assertions.assertTrue(StructuredFieldParser.parseDictionary("").isEmpty());
    }

    @Test
    void testMessageHeadersCombineLinesCaseInsensitively() throws Exception {
        final HeaderGroup headers = new HeaderGroup();
        headers.addHeader(new BasicHeader("Example", "one, two"));
        headers.addHeader(new BasicHeader("EXAMPLE", "three"));
        final StructuredFieldList value = StructuredFieldHeaders.parseList(headers, "example");
        Assertions.assertEquals(3, value.size());
        final Header formatted = StructuredFieldHeaders.format("Example", value);
        Assertions.assertNotNull(formatted);
        Assertions.assertTrue(formatted instanceof BufferedHeader);
        Assertions.assertEquals("one, two, three", formatted.getValue());
        Assertions.assertEquals(value, StructuredFieldHeaders.parseList(formatted));
    }

    @Test
    void testMessageHeadersCombineFormattedHeaderLines() throws Exception {
        final HeaderGroup headers = new HeaderGroup();
        headers.addHeader(bufferedHeader("Example: one, two"));
        headers.addHeader(bufferedHeader("Example: three"));
        final StructuredFieldList value = StructuredFieldHeaders.parseList(headers, "example");
        Assertions.assertEquals(3, value.size());
        Assertions.assertEquals("one, two, three",
                StructuredFieldHeaders.format("Example", value).getValue());
    }

    @Test
    void testMessageHeadersItemMustNotSpanFieldLines() throws Exception {
        final HeaderGroup single = new HeaderGroup();
        single.addHeader(new BasicHeader("Example", "10"));
        Assertions.assertEquals(
                StructuredFieldHeaders.parseItem(new BasicHeader("Example", "10")),
                StructuredFieldHeaders.parseItem(single, "example"));

        final HeaderGroup multiple = new HeaderGroup();
        multiple.addHeader(new BasicHeader("Example", "10"));
        multiple.addHeader(new BasicHeader("Example", "20"));
        Assertions.assertThrows(ParseException.class,
                () -> StructuredFieldHeaders.parseItem(multiple, "example"));
    }

    @Test
    void testMessageHeadersListMembersMustBeCommaSeparated() {
        final HeaderGroup headers = new HeaderGroup();
        headers.addHeader(new BasicHeader("Example", "a b"));
        Assertions.assertThrows(ParseException.class,
                () -> StructuredFieldHeaders.parseList(headers, "example"));
    }

    private static BufferedHeader bufferedHeader(final String line) {
        final CharArrayBuffer buffer = new CharArrayBuffer(line.length());
        buffer.append(line);
        return BufferedHeader.create(buffer);
    }

    @Test
    void testSerializeIntoExistingBuffer() {
        final CharArrayBuffer buffer = new CharArrayBuffer(32);
        buffer.append("Priority: ");
        StructuredFieldSerializer.serialize(buffer, StructuredFieldDictionary.builder()
                .put("u", StructuredFieldItem.ofInteger(0))
                .put("i", StructuredFieldItem.ofBoolean(true))
                .build());
        Assertions.assertEquals("Priority: u=0, i", buffer.toString());
    }

    @Test
    void testImmutabilityAndValueSemantics() {
        final byte[] bytes = {1, 2, 3};
        final StructuredFieldBareItem item = StructuredFieldBareItem.ofByteSequence(bytes);
        bytes[0] = 9;
        Assertions.assertArrayEquals(new byte[] {1, 2, 3}, item.getByteSequenceValue());
        final byte[] returned = item.getByteSequenceValue();
        returned[0] = 8;
        Assertions.assertArrayEquals(new byte[] {1, 2, 3}, item.getByteSequenceValue());

        final StructuredFieldList list = StructuredFieldList.of(
                StructuredFieldItem.of(StructuredFieldBareItem.ofInteger(1)));
        Assertions.assertThrows(UnsupportedOperationException.class,
                () -> list.getMembers().add(StructuredFieldItem.of(StructuredFieldBareItem.ofInteger(2))));
        Assertions.assertEquals(list, StructuredFieldList.of(Arrays.asList(list.get(0))));
    }

    @Test
    void testValidationAtConstructionBoundary() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> StructuredFieldBareItem.ofInteger(1000000000000000L));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> StructuredFieldBareItem.ofDecimal(new BigDecimal("999999999999.9995")));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> StructuredFieldBareItem.ofString("é"));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> StructuredFieldBareItem.ofToken("1token"));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> StructuredFieldParameters.builder().putBoolean("Upper", true));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> StructuredFieldBareItem.ofDisplayString("\ud800"));
    }

    private static String bare(final StructuredFieldBareItem item) {
        return StructuredFieldSerializer.serializeBareItem(item);
    }
}
