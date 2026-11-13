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

package org.apache.hc.core5.util;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TestTokenizer {

    private Tokenizer parser;

    @BeforeEach
    void setUp() {
        parser = new Tokenizer();
    }

    private static CharArrayBuffer createBuffer(final String value) {
        if (value == null) {
            return null;
        }
        final CharArrayBuffer buffer = new CharArrayBuffer(value.length());
        buffer.append(value);
        return buffer;
    }

    @Test
    void testBasicTokenParsing() {
        final String s = "   raw: \" some stuff \"";
        final CharArrayBuffer raw = createBuffer(s);
        final Tokenizer.Cursor cursor = new Tokenizer.Cursor(0, s.length());

        parser.skipWhiteSpace(raw, cursor);

        Assertions.assertThat(cursor.atEnd()).isFalse();
        Assertions.assertThat(cursor.getPos()).isEqualTo(3);

        final StringBuilder strbuf1 = new StringBuilder();
        parser.copyContent(raw, cursor, Tokenizer.delimiters(':'), strbuf1);

        Assertions.assertThat(cursor.atEnd()).isFalse();
        Assertions.assertThat(cursor.getPos()).isEqualTo(6);
        Assertions.assertThat(strbuf1.toString()).isEqualTo("raw");
        Assertions.assertThat(raw.charAt(cursor.getPos())).isEqualTo(':');
        cursor.updatePos(cursor.getPos() + 1);

        parser.skipWhiteSpace(raw, cursor);

        Assertions.assertThat(cursor.atEnd()).isFalse();
        Assertions.assertThat(cursor.getPos()).isEqualTo(8);

        final StringBuilder strbuf2 = new StringBuilder();
        parser.copyQuotedContent(raw, cursor, strbuf2);

        Assertions.assertThat(cursor.atEnd()).isTrue();
        Assertions.assertThat(strbuf2.toString()).isEqualTo(" some stuff ");

        parser.copyQuotedContent(raw, cursor, strbuf2);
        Assertions.assertThat(cursor.atEnd()).isTrue();

        parser.skipWhiteSpace(raw, cursor);
        Assertions.assertThat(cursor.atEnd()).isTrue();
    }

    @Test
    void testTokenParsingWithQuotedPairs() {
        final String s = "raw: \"\\\"some\\stuff\\\\\"";
        final CharArrayBuffer raw = createBuffer(s);
        final Tokenizer.Cursor cursor = new Tokenizer.Cursor(0, s.length());

        parser.skipWhiteSpace(raw, cursor);

        Assertions.assertThat(cursor.atEnd()).isFalse();
        Assertions.assertThat(cursor.getPos()).isEqualTo(0);

        final StringBuilder strbuf1 = new StringBuilder();
        parser.copyContent(raw, cursor, Tokenizer.delimiters(':'), strbuf1);

        Assertions.assertThat(cursor.atEnd()).isFalse();
        Assertions.assertThat(strbuf1.toString()).isEqualTo("raw");
        Assertions.assertThat(raw.charAt(cursor.getPos())).isEqualTo(':');
        cursor.updatePos(cursor.getPos() + 1);

        parser.skipWhiteSpace(raw, cursor);

        Assertions.assertThat(cursor.atEnd()).isFalse();

        final StringBuilder strbuf2 = new StringBuilder();
        parser.copyQuotedContent(raw, cursor, strbuf2);

        Assertions.assertThat(cursor.atEnd()).isTrue();
        Assertions.assertThat(strbuf2.toString()).isEqualTo("\"some\\stuff\\");
    }

    @Test
    void testTokenParsingIncompleteQuote() {
        final String s = "\"stuff and more stuff  ";
        final CharArrayBuffer raw = createBuffer(s);
        final Tokenizer.Cursor cursor = new Tokenizer.Cursor(0, s.length());
        final StringBuilder strbuf1 = new StringBuilder();
        parser.copyQuotedContent(raw, cursor, strbuf1);
        Assertions.assertThat(strbuf1.toString()).isEqualTo("stuff and more stuff  ");
    }

    @Test
    void testTokenParsingTokensWithUnquotedBlanks() {
        final String s = "  stuff and   \tsome\tmore  stuff  ;";
        final CharArrayBuffer raw = createBuffer(s);
        final Tokenizer.Cursor cursor = new Tokenizer.Cursor(0, s.length());
        final String result = parser.parseToken(raw, cursor, Tokenizer.delimiters(';'));
        Assertions.assertThat(result).isEqualTo("stuff and some more stuff");
    }

    @Test
    void testTokenParsingMixedValuesAndQuotedValues() {
        final String s = "  stuff and    \" some more \"   \"stuff  ;";
        final CharArrayBuffer raw = createBuffer(s);
        final Tokenizer.Cursor cursor = new Tokenizer.Cursor(0, s.length());
        final String result = parser.parseValue(raw, cursor, Tokenizer.delimiters(';'));
        Assertions.assertThat(result).isEqualTo("stuff and  some more  stuff  ;");
    }

    @Test
    void testTokenParsingMixedValuesAndQuotedValues2() {
        final String s = "stuff\"more\"stuff;";
        final CharArrayBuffer raw = createBuffer(s);
        final Tokenizer.Cursor cursor = new Tokenizer.Cursor(0, s.length());
        final String result = parser.parseValue(raw, cursor, Tokenizer.delimiters(';'));
        Assertions.assertThat(result).isEqualTo("stuffmorestuff");
    }

    @Test
    void testTokenParsingEscapedQuotes() {
        final String s = "stuff\"\\\"more\\\"\"stuff;";
        final CharArrayBuffer raw = createBuffer(s);
        final Tokenizer.Cursor cursor = new Tokenizer.Cursor(0, s.length());
        final String result = parser.parseValue(raw, cursor, Tokenizer.delimiters(';'));
        Assertions.assertThat(result).isEqualTo("stuff\"more\"stuff");
    }

    @Test
    void testTokenParsingEscapedDelimiter() {
        final String s = "stuff\"\\\"more\\\";\"stuff;";
        final CharArrayBuffer raw = createBuffer(s);
        final Tokenizer.Cursor cursor = new Tokenizer.Cursor(0, s.length());
        final String result = parser.parseValue(raw, cursor, Tokenizer.delimiters(';'));
        Assertions.assertThat(result).isEqualTo("stuff\"more\";stuff");
    }

    @Test
    void testTokenParsingEscapedSlash() {
        final String s = "stuff\"\\\"more\\\";\\\\\"stuff;";
        final CharArrayBuffer raw = createBuffer(s);
        final Tokenizer.Cursor cursor = new Tokenizer.Cursor(0, s.length());
        final String result = parser.parseValue(raw, cursor, Tokenizer.delimiters(';'));
        Assertions.assertThat(result).isEqualTo("stuff\"more\";\\stuff");
    }

    @Test
    void testTokenParsingSlashOutsideQuotes() {
        final String s = "stuff\\; more stuff;";
        final CharArrayBuffer raw = createBuffer(s);
        final Tokenizer.Cursor cursor = new Tokenizer.Cursor(0, s.length());
        final String result = parser.parseValue(raw, cursor, Tokenizer.delimiters(';'));
        Assertions.assertThat(result).isEqualTo("stuff\\");
    }
}
