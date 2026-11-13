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
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TestStructuredFieldSerializer {

    @Test
    void testCanonicalNumbersAndDisplayString() {
        Assertions.assertThat(bare(StructuredFieldBareItem.ofDecimal(new BigDecimal("-0.0001")))).isEqualTo("0.0");
        Assertions.assertThat(bare(StructuredFieldBareItem.ofDecimal(new BigDecimal("1.2344")))).isEqualTo("1.234");
        Assertions.assertThat(bare(StructuredFieldBareItem.ofDecimal(new BigDecimal("1.2345")))).isEqualTo("1.234");
        Assertions.assertThat(bare(StructuredFieldBareItem.ofDecimal(new BigDecimal("1.2355")))).isEqualTo("1.236");
        Assertions.assertThat(bare(StructuredFieldBareItem.ofDecimal(new BigDecimal("0.0015")))).isEqualTo("0.002");
        Assertions.assertThat(bare(StructuredFieldBareItem.ofDecimal(new BigDecimal("0.0025")))).isEqualTo("0.002");
        Assertions.assertThat(bare(StructuredFieldBareItem.ofDecimal(new BigDecimal("-0.0015")))).isEqualTo("-0.002");
        Assertions.assertThat(bare(StructuredFieldBareItem.ofDecimal(new BigDecimal("-0.0025")))).isEqualTo("-0.002");
        Assertions.assertThat(bare(StructuredFieldBareItem.ofDecimal(new BigDecimal("9.9995")))).isEqualTo("10.0");
        Assertions.assertThat(bare(StructuredFieldBareItem.ofDisplayString("Füße \"%\""))).isEqualTo("%\"F%c3%bc%c3%9fe %22%25%22\"");
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
        Assertions.assertThat(StructuredFieldSerializer.serializeDictionary(dictionary)).isEqualTo("available;valid;cached=?0;ttl=60, rating=1.5, feelings=(joy sadness)");
    }

    @Test
    void testEmptyContainersOmitField() throws Exception {
        final StructuredFieldList list = StructuredFieldList.of();
        final StructuredFieldDictionary dictionary = StructuredFieldDictionary.builder().build();
        Assertions.assertThat(StructuredFieldSerializer.serializeList(list)).isNull();
        Assertions.assertThat(StructuredFieldSerializer.serializeDictionary(dictionary)).isNull();
        Assertions.assertThat(StructuredFieldHeaders.format("Example", list)).isNull();
        Assertions.assertThat(StructuredFieldParser.parseList("").isEmpty()).isTrue();
        Assertions.assertThat(StructuredFieldParser.parseDictionary("").isEmpty()).isTrue();
    }

    @Test
    void testMessageHeadersCombineLinesCaseInsensitively() throws Exception {
        final HeaderGroup headers = new HeaderGroup();
        headers.addHeader(new BasicHeader("Example", "one, two"));
        headers.addHeader(new BasicHeader("EXAMPLE", "three"));
        final StructuredFieldList value = StructuredFieldHeaders.parseList(headers, "example");
        Assertions.assertThat(value.size()).isEqualTo(3);
        final Header formatted = StructuredFieldHeaders.format("Example", value);
        Assertions.assertThat(formatted).isNotNull();
        Assertions.assertThat(formatted instanceof BufferedHeader).isTrue();
        Assertions.assertThat(formatted.getValue()).isEqualTo("one, two, three");
        Assertions.assertThat(StructuredFieldHeaders.parseList(formatted)).isEqualTo(value);
    }

    @Test
    void testMessageHeadersCombineFormattedHeaderLines() throws Exception {
        final HeaderGroup headers = new HeaderGroup();
        headers.addHeader(bufferedHeader("Example: one, two"));
        headers.addHeader(bufferedHeader("Example: three"));
        final StructuredFieldList value = StructuredFieldHeaders.parseList(headers, "example");
        Assertions.assertThat(value.size()).isEqualTo(3);
        Assertions.assertThat(StructuredFieldHeaders.format("Example", value).getValue()).isEqualTo("one, two, three");
    }

    @Test
    void testMessageHeadersItemMustNotSpanFieldLines() throws Exception {
        final HeaderGroup single = new HeaderGroup();
        single.addHeader(new BasicHeader("Example", "10"));
        Assertions.assertThat(StructuredFieldHeaders.parseItem(single, "example")).isEqualTo(StructuredFieldHeaders.parseItem(new BasicHeader("Example", "10")));

        final HeaderGroup multiple = new HeaderGroup();
        multiple.addHeader(new BasicHeader("Example", "10"));
        multiple.addHeader(new BasicHeader("Example", "20"));
        Assertions.assertThatThrownBy(() ->
                        StructuredFieldHeaders.parseItem(multiple, "example"))
                .isInstanceOf(ParseException.class);
    }

    @Test
    void testMessageHeadersListMembersMustBeCommaSeparated() {
        final HeaderGroup headers = new HeaderGroup();
        headers.addHeader(new BasicHeader("Example", "a b"));
        Assertions.assertThatThrownBy(() ->
                        StructuredFieldHeaders.parseList(headers, "example"))
                .isInstanceOf(ParseException.class);
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
        Assertions.assertThat(buffer.toString()).isEqualTo("Priority: u=0, i");
    }

    @Test
    void testImmutabilityAndValueSemantics() {
        final byte[] bytes = {1, 2, 3};
        final StructuredFieldBareItem item = StructuredFieldBareItem.ofByteSequence(bytes);
        bytes[0] = 9;
        Assertions.assertThat(item.getByteSequenceValue())
                .containsExactly(new byte[]{1, 2, 3});
        final byte[] returned = item.getByteSequenceValue();
        returned[0] = 8;
        Assertions.assertThat(item.getByteSequenceValue())
                .containsExactly(new byte[]{1, 2, 3});

        final StructuredFieldList list = StructuredFieldList.of(
                StructuredFieldItem.of(StructuredFieldBareItem.ofInteger(1)));
        Assertions.assertThatThrownBy(() ->
                        list.getMembers().add(StructuredFieldItem.of(StructuredFieldBareItem.ofInteger(2))))
                .isInstanceOf(UnsupportedOperationException.class);
        Assertions.assertThat(StructuredFieldList.of(Arrays.asList(list.get(0)))).isEqualTo(list);
    }

    @Test
    void testValidationAtConstructionBoundary() {
        Assertions.assertThatThrownBy(() ->
                        StructuredFieldBareItem.ofInteger(1000000000000000L))
                .isInstanceOf(IllegalArgumentException.class);
        Assertions.assertThatThrownBy(() ->
                        StructuredFieldBareItem.ofDecimal(new BigDecimal("999999999999.9995")))
                .isInstanceOf(IllegalArgumentException.class);
        Assertions.assertThatThrownBy(() ->
                        StructuredFieldBareItem.ofString("é"))
                .isInstanceOf(IllegalArgumentException.class);
        Assertions.assertThatThrownBy(() ->
                        StructuredFieldBareItem.ofToken("1token"))
                .isInstanceOf(IllegalArgumentException.class);
        Assertions.assertThatThrownBy(() ->
                        StructuredFieldParameters.builder().putBoolean("Upper", true))
                .isInstanceOf(IllegalArgumentException.class);
        Assertions.assertThatThrownBy(() ->
                        StructuredFieldBareItem.ofDisplayString("\ud800"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static String bare(final StructuredFieldBareItem item) {
        return StructuredFieldSerializer.serializeBareItem(item);
    }
}
