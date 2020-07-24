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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestTokenizer {

    private Tokenizer parser;

    @Before
    public void setUp() throws Exception {
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
    public void testBasicTokenParsing() throws Exception {
        final String s = "   raw: \" some stuff \"";
        final CharArrayBuffer raw = createBuffer(s);
        final Tokenizer.Cursor cursor = new Tokenizer.Cursor(0, s.length());

        parser.skipWhiteSpace(raw, cursor);

        Assert.assertFalse(cursor.atEnd());
        Assert.assertEquals(3, cursor.getPos());

        final StringBuilder strbuf1 = new StringBuilder();
        parser.copyContent(raw, cursor, Tokenizer.INIT_BITSET(':'), strbuf1);

        Assert.assertFalse(cursor.atEnd());
        Assert.assertEquals(6, cursor.getPos());
        Assert.assertEquals("raw", strbuf1.toString());
        Assert.assertEquals(':', raw.charAt(cursor.getPos()));
        cursor.updatePos(cursor.getPos() + 1);

        parser.skipWhiteSpace(raw, cursor);

        Assert.assertFalse(cursor.atEnd());
        Assert.assertEquals(8, cursor.getPos());

        final StringBuilder strbuf2 = new StringBuilder();
        parser.copyQuotedContent(raw, cursor, strbuf2);

        Assert.assertTrue(cursor.atEnd());
        Assert.assertEquals(" some stuff ", strbuf2.toString());

        parser.copyQuotedContent(raw, cursor, strbuf2);
        Assert.assertTrue(cursor.atEnd());

        parser.skipWhiteSpace(raw, cursor);
        Assert.assertTrue(cursor.atEnd());
    }

    @Test
    public void testTokenParsingWithQuotedPairs() throws Exception {
        final String s = "raw: \"\\\"some\\stuff\\\\\"";
        final CharArrayBuffer raw = createBuffer(s);
        final Tokenizer.Cursor cursor = new Tokenizer.Cursor(0, s.length());

        parser.skipWhiteSpace(raw, cursor);

        Assert.assertFalse(cursor.atEnd());
        Assert.assertEquals(0, cursor.getPos());

        final StringBuilder strbuf1 = new StringBuilder();
        parser.copyContent(raw, cursor, Tokenizer.INIT_BITSET(':'), strbuf1);

        Assert.assertFalse(cursor.atEnd());
        Assert.assertEquals("raw", strbuf1.toString());
        Assert.assertEquals(':', raw.charAt(cursor.getPos()));
        cursor.updatePos(cursor.getPos() + 1);

        parser.skipWhiteSpace(raw, cursor);

        Assert.assertFalse(cursor.atEnd());

        final StringBuilder strbuf2 = new StringBuilder();
        parser.copyQuotedContent(raw, cursor, strbuf2);

        Assert.assertTrue(cursor.atEnd());
        Assert.assertEquals("\"some\\stuff\\", strbuf2.toString());
    }

    @Test
    public void testTokenParsingIncompleteQuote() throws Exception {
        final String s = "\"stuff and more stuff  ";
        final CharArrayBuffer raw = createBuffer(s);
        final Tokenizer.Cursor cursor = new Tokenizer.Cursor(0, s.length());
        final StringBuilder strbuf1 = new StringBuilder();
        parser.copyQuotedContent(raw, cursor, strbuf1);
        Assert.assertEquals("stuff and more stuff  ", strbuf1.toString());
    }

    @Test
    public void testTokenParsingTokensWithUnquotedBlanks() throws Exception {
        final String s = "  stuff and   \tsome\tmore  stuff  ;";
        final CharArrayBuffer raw = createBuffer(s);
        final Tokenizer.Cursor cursor = new Tokenizer.Cursor(0, s.length());
        final String result = parser.parseToken(raw, cursor, Tokenizer.INIT_BITSET(';'));
        Assert.assertEquals("stuff and some more stuff", result);
    }

    @Test
    public void testTokenParsingMixedValuesAndQuotedValues() throws Exception {
        final String s = "  stuff and    \" some more \"   \"stuff  ;";
        final CharArrayBuffer raw = createBuffer(s);
        final Tokenizer.Cursor cursor = new Tokenizer.Cursor(0, s.length());
        final String result = parser.parseValue(raw, cursor, Tokenizer.INIT_BITSET(';'));
        Assert.assertEquals("stuff and  some more  stuff  ;", result);
    }

    @Test
    public void testTokenParsingMixedValuesAndQuotedValues2() throws Exception {
        final String s = "stuff\"more\"stuff;";
        final CharArrayBuffer raw = createBuffer(s);
        final Tokenizer.Cursor cursor = new Tokenizer.Cursor(0, s.length());
        final String result = parser.parseValue(raw, cursor, Tokenizer.INIT_BITSET(';'));
        Assert.assertEquals("stuffmorestuff", result);
    }

    @Test
    public void testTokenParsingEscapedQuotes() throws Exception {
        final String s = "stuff\"\\\"more\\\"\"stuff;";
        final CharArrayBuffer raw = createBuffer(s);
        final Tokenizer.Cursor cursor = new Tokenizer.Cursor(0, s.length());
        final String result = parser.parseValue(raw, cursor, Tokenizer.INIT_BITSET(';'));
        Assert.assertEquals("stuff\"more\"stuff", result);
    }

    @Test
    public void testTokenParsingEscapedDelimiter() throws Exception {
        final String s = "stuff\"\\\"more\\\";\"stuff;";
        final CharArrayBuffer raw = createBuffer(s);
        final Tokenizer.Cursor cursor = new Tokenizer.Cursor(0, s.length());
        final String result = parser.parseValue(raw, cursor, Tokenizer.INIT_BITSET(';'));
        Assert.assertEquals("stuff\"more\";stuff", result);
    }

    @Test
    public void testTokenParsingEscapedSlash() throws Exception {
        final String s = "stuff\"\\\"more\\\";\\\\\"stuff;";
        final CharArrayBuffer raw = createBuffer(s);
        final Tokenizer.Cursor cursor = new Tokenizer.Cursor(0, s.length());
        final String result = parser.parseValue(raw, cursor, Tokenizer.INIT_BITSET(';'));
        Assert.assertEquals("stuff\"more\";\\stuff", result);
    }

    @Test
    public void testTokenParsingSlashOutsideQuotes() throws Exception {
        final String s = "stuff\\; more stuff;";
        final CharArrayBuffer raw = createBuffer(s);
        final Tokenizer.Cursor cursor = new Tokenizer.Cursor(0, s.length());
        final String result = parser.parseValue(raw, cursor, Tokenizer.INIT_BITSET(';'));
        Assert.assertEquals("stuff\\", result);
    }
}
