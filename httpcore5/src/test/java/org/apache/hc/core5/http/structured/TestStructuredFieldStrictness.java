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
import org.junit.jupiter.api.Assertions;
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
        Assertions.assertEquals(5,
                StructuredFieldParser.parseItem(":aGVsbG8:").getBareItem().getByteSequenceValue().length);
        Assertions.assertEquals(":YQ==:", StructuredFieldSerializer.serializeItem(
                StructuredFieldParser.parseItem(":YQ=:")));
        assertItemFails(":=aGVsbG8=:", ":a=GVsbG8=:", ":aGVsb G8=:", ":aGVsbG8=", ":_-Ah:", ":",
                ":YQ===:");

        final StructuredFieldItem nonZeroPadBits = StructuredFieldParser.parseItem(":iZ==:");
        Assertions.assertEquals(":iQ==:", StructuredFieldSerializer.serializeItem(nonZeroPadBits));
    }

    @Test
    void testStrictDisplayStrings() throws Exception {
        final StructuredFieldItem item = StructuredFieldParser.parseItem("%\"foo %22bar%22 \\ baz\"");
        Assertions.assertEquals("foo \"bar\" \\ baz", item.getBareItem().getTextValue());
        Assertions.assertEquals("%\"foo %22bar%22 \\ baz\"", StructuredFieldSerializer.serializeItem(item));

        final StructuredFieldItem overEncoded = StructuredFieldParser.parseItem("%\"%61\"");
        Assertions.assertEquals("%\"a\"", StructuredFieldSerializer.serializeItem(overEncoded));

        assertItemFails("%\"f%C3%BC\"", "%\"füü\"", "%\"%c3%28\"", "%\"%a0%a1\"",
                "%\"%e2%28%a1\"", "%\"%f0%28%8c%28\"", "%\"%g0\"", "%\"%\"", "%foo");
    }

    @Test
    void testOnlySpacesAreAllowedAtTopLevel() throws Exception {
        Assertions.assertEquals(1L, StructuredFieldParser.parseItem(" 1 ").getBareItem().getLongValue());
        assertItemFails(" \t 1", "1 \t ");
    }

    @Test
    void testUnicodeDigitsAreNotRfcDigits() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> StructuredFieldParameters.builder().putBoolean("a١", true));
    }

    private static void assertItemFails(final String... values) {
        for (final String value : values) {
            Assertions.assertThrows(ParseException.class, () -> StructuredFieldParser.parseItem(value), value);
        }
    }

    private static void assertListFails(final String... values) {
        for (final String value : values) {
            Assertions.assertThrows(ParseException.class, () -> StructuredFieldParser.parseList(value), value);
        }
    }

    private static void assertDictionaryFails(final String... values) {
        for (final String value : values) {
            Assertions.assertThrows(ParseException.class, () -> StructuredFieldParser.parseDictionary(value), value);
        }
    }
}
