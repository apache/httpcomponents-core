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

import org.apache.hc.core5.http.ParseException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Strictness cases aligned with the HTTPWG structured-field-tests corpus.
 */
class TestStructuredFieldStrictness {

    @Test
    void testInvalidItems() {
        assertItemFails("", "-", "9999999999999999", "1.", "1.2345", "1000000000000.0",
                "\"unterminated", "\"bad\\q\"", "?2", "@1.2", "'single'", "\té", "١");
    }

    @Test
    void testInvalidContainers() {
        assertListFails("one,", "one,,two", "(one\ttwo)", "(one,two)", "(one", "one ;x");
        assertDictionaryFails("A=1", "a =1", "a=1,", "a==1", "1a=1", "a=(1\t2)");
    }

    @Test
    void testStrictByteSequences() throws Exception {
        Assertions.assertThat(StructuredFieldParser.parseItem(":aGVsbG8:").getBareItem().getByteSequenceValue().length).isEqualTo(5);
        Assertions.assertThat(StructuredFieldSerializer.serializeItem(
                StructuredFieldParser.parseItem(":YQ=:"))).isEqualTo(":YQ==:");
        assertItemFails(":=aGVsbG8=:", ":a=GVsbG8=:", ":aGVsb G8=:", ":aGVsbG8=", ":_-Ah:", ":",
                ":YQ===:");

        final StructuredFieldItem nonZeroPadBits = StructuredFieldParser.parseItem(":iZ==:");
        Assertions.assertThat(StructuredFieldSerializer.serializeItem(nonZeroPadBits)).isEqualTo(":iQ==:");
    }

    @Test
    void testStrictDisplayStrings() throws Exception {
        final StructuredFieldItem item = StructuredFieldParser.parseItem("%\"foo %22bar%22 \\ baz\"");
        Assertions.assertThat(item.getBareItem().getTextValue()).isEqualTo("foo \"bar\" \\ baz");
        Assertions.assertThat(StructuredFieldSerializer.serializeItem(item)).isEqualTo("%\"foo %22bar%22 \\ baz\"");

        final StructuredFieldItem overEncoded = StructuredFieldParser.parseItem("%\"%61\"");
        Assertions.assertThat(StructuredFieldSerializer.serializeItem(overEncoded)).isEqualTo("%\"a\"");

        assertItemFails("%\"f%C3%BC\"", "%\"füü\"", "%\"%c3%28\"", "%\"%a0%a1\"",
                "%\"%e2%28%a1\"", "%\"%f0%28%8c%28\"", "%\"%g0\"", "%\"%\"", "%foo");
    }

    @Test
    void testOnlySpacesAreAllowedAtTopLevel() throws Exception {
        Assertions.assertThat(StructuredFieldParser.parseItem(" 1 ").getBareItem().getLongValue()).isEqualTo(1L);
        assertItemFails(" \t 1", "1 \t ");
    }

    @Test
    void testUnicodeDigitsAreNotRfcDigits() {
        Assertions.assertThatThrownBy(() ->
                        StructuredFieldParameters.builder().putBoolean("a١", true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static void assertItemFails(final String... values) {
        for (final String value : values) {
            Assertions.assertThatThrownBy(() ->
                            StructuredFieldParser.parseItem(value))
                    .withFailMessage(value).isInstanceOf(ParseException.class);
        }
    }

    private static void assertListFails(final String... values) {
        for (final String value : values) {
            Assertions.assertThatThrownBy(() ->
                            StructuredFieldParser.parseList(value))
                    .withFailMessage(value).isInstanceOf(ParseException.class);
        }
    }

    private static void assertDictionaryFails(final String... values) {
        for (final String value : values) {
            Assertions.assertThatThrownBy(() ->
                            StructuredFieldParser.parseDictionary(value))
                    .withFailMessage(value).isInstanceOf(ParseException.class);
        }
    }
}
