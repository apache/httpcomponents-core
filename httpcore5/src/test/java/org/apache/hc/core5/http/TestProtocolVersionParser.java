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

package org.apache.hc.core5.http;


import org.apache.hc.core5.http.message.ParserCursor;
import org.apache.hc.core5.util.Tokenizer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ProtocolVersionParser}.
 */
class TestProtocolVersionParser {

    private ProtocolVersionParser impl;

    @BeforeEach
    void setup() {
        impl = new ProtocolVersionParser();
    }

    public ProtocolVersion parseStr(final String protocol, final String s) throws ParseException {
        return impl.parse(protocol, null, s, new ParserCursor(0, s.length()), null);
    }

    public ProtocolVersion parseStr(final String protocol, final String s, final Tokenizer.Delimiter delimiterPredicate) throws ParseException {
        return impl.parse(protocol, null, s, new ParserCursor(0, s.length()), delimiterPredicate);
    }

    public ProtocolVersion parseStr(final String protocol, final String s, final Tokenizer.Cursor cursor, final Tokenizer.Delimiter delimiterPredicate) throws ParseException {
        return impl.parse(protocol, null, s, cursor, delimiterPredicate);
    }

    public ProtocolVersion parseStr(final String s) throws ParseException {
        return impl.parse(s, new ParserCursor(0, s.length()), null);
    }

    public ProtocolVersion parseStr(final String s, final Tokenizer.Delimiter delimiterPredicate) throws ParseException {
        return impl.parse(s, new ParserCursor(0, s.length()), delimiterPredicate);
    }

    @Test
    void testParseVersion() throws Exception {
        Assertions.assertThat(parseStr("PROTO", "1  ")).isEqualTo(new ProtocolVersion("PROTO", 1, 0));
        Assertions.assertThat(parseStr("PROTO", "1.1   ")).isEqualTo(new ProtocolVersion("PROTO", 1, 1));
        Assertions.assertThat(parseStr("PROTO", "1.020  ")).isEqualTo(new ProtocolVersion("PROTO", 1, 20));
        Assertions.assertThat(parseStr("PROTO", "22.356  ")).isEqualTo(new ProtocolVersion("PROTO", 22, 356));

        Assertions.assertThat(parseStr("PROTO", "1,  ", Tokenizer.delimiters(','))).isEqualTo(new ProtocolVersion("PROTO", 1, 0));
        Assertions.assertThat(parseStr("PROTO", "1.1;   ", Tokenizer.delimiters(',', ';'))).isEqualTo(new ProtocolVersion("PROTO", 1, 1));
        Assertions.assertThat(parseStr("PROTO", "1.200 blah; ", Tokenizer.delimiters(','))).isEqualTo(new ProtocolVersion("PROTO", 1, 200));
    }

    @Test
    void testParseVersionWithCursor() throws Exception {
        final Tokenizer.Cursor cursor = new Tokenizer.Cursor(2, 13);
        Assertions.assertThat(parseStr("PROTO", "  1.20,0000,00000", cursor, Tokenizer.delimiters(','))).isEqualTo(new ProtocolVersion("PROTO", 1, 20));
        Assertions.assertThat(cursor.getPos()).isEqualTo(6);
    }

    @Test
    void testParseProtocolVersion() throws Exception {
        Assertions.assertThat(parseStr("PROTO/1  ")).isEqualTo(new ProtocolVersion("PROTO", 1, 0));
        Assertions.assertThat(parseStr("PROTO/1.1   ")).isEqualTo(new ProtocolVersion("PROTO", 1, 1));
        Assertions.assertThat(parseStr("PROTO/1.020  ")).isEqualTo(new ProtocolVersion("PROTO", 1, 20));
        Assertions.assertThat(parseStr("PROTO/22.356  ")).isEqualTo(new ProtocolVersion("PROTO", 22, 356));

        Assertions.assertThat(parseStr("PROTO/1,  ", Tokenizer.delimiters(','))).isEqualTo(new ProtocolVersion("PROTO", 1, 0));
        Assertions.assertThat(parseStr("PROTO/1.1;   ", Tokenizer.delimiters(',', ';'))).isEqualTo(new ProtocolVersion("PROTO", 1, 1));
        Assertions.assertThat(parseStr("PROTO/1.200 blah; ", Tokenizer.delimiters(','))).isEqualTo(new ProtocolVersion("PROTO", 1, 200));
    }

    @Test
    void testParseFailures() {
        Assertions.assertThatThrownBy(() ->
                        parseStr("PROTO", "blah"))
                .isInstanceOf(ParseException.class);
        Assertions.assertThatThrownBy(() ->
                        parseStr("PROTO", "1.blah"))
                .isInstanceOf(ParseException.class);
        Assertions.assertThatThrownBy(() ->
                        parseStr("PROTO", "1A.0"))
                .isInstanceOf(ParseException.class);
        Assertions.assertThatThrownBy(() ->
                        parseStr("PROTO", ""))
                .isInstanceOf(ParseException.class);
        Assertions.assertThatThrownBy(() ->
                        parseStr(""))
                .isInstanceOf(ParseException.class);
        Assertions.assertThatThrownBy(() ->
                        parseStr("   "))
                .isInstanceOf(ParseException.class);
        Assertions.assertThatThrownBy(() ->
                        parseStr("   /1.0"))
                .isInstanceOf(ParseException.class);
        Assertions.assertThatThrownBy(() ->
                        parseStr("   / "))
                .isInstanceOf(ParseException.class);
        Assertions.assertThatThrownBy(() ->
                        parseStr("PROTO"))
                .isInstanceOf(ParseException.class);
        Assertions.assertThatThrownBy(() ->
                        parseStr("PROTO/"))
                .isInstanceOf(ParseException.class);
        Assertions.assertThatThrownBy(() ->
                        parseStr("PROTO/ 1.0"))
                .isInstanceOf(ParseException.class);
    }

}
