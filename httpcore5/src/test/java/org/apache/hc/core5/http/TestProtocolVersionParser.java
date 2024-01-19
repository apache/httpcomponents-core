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

import static org.hamcrest.MatcherAssert.assertThat;

import org.apache.hc.core5.http.message.ParserCursor;
import org.apache.hc.core5.util.Tokenizer;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ProtocolVersionParser}.
 */
public class TestProtocolVersionParser {

    private ProtocolVersionParser impl;

    @BeforeEach
    public void setup() {
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
    public void testParseVersion() throws Exception {
        Assertions.assertEquals(new ProtocolVersion("PROTO", 1, 0), parseStr("PROTO", "1  "));
        Assertions.assertEquals(new ProtocolVersion("PROTO", 1, 1), parseStr("PROTO", "1.1   "));
        Assertions.assertEquals(new ProtocolVersion("PROTO", 1, 20), parseStr("PROTO", "1.020  "));
        Assertions.assertEquals(new ProtocolVersion("PROTO", 22, 356), parseStr("PROTO", "22.356  "));

        Assertions.assertEquals(new ProtocolVersion("PROTO", 1, 0), parseStr("PROTO", "1,  ", Tokenizer.delimiters(',')));
        Assertions.assertEquals(new ProtocolVersion("PROTO", 1, 1), parseStr("PROTO", "1.1;   ", Tokenizer.delimiters(',', ';')));
        Assertions.assertEquals(new ProtocolVersion("PROTO", 1, 200), parseStr("PROTO", "1.200 blah; ", Tokenizer.delimiters(',')));
    }

    @Test
    public void testParseVersionWithCursor() throws Exception {
        final Tokenizer.Cursor cursor = new Tokenizer.Cursor(2, 13);
        Assertions.assertEquals(new ProtocolVersion("PROTO", 1, 20), parseStr("PROTO", "  1.20,0000,00000", cursor, Tokenizer.delimiters(',')));
        assertThat(cursor.getPos(), CoreMatchers.equalTo(6));
    }

    @Test
    public void testParseProtocolVersion() throws Exception {
        Assertions.assertEquals(new ProtocolVersion("PROTO", 1, 0), parseStr("PROTO/1  "));
        Assertions.assertEquals(new ProtocolVersion("PROTO", 1, 1), parseStr("PROTO/1.1   "));
        Assertions.assertEquals(new ProtocolVersion("PROTO", 1, 20), parseStr("PROTO/1.020  "));
        Assertions.assertEquals(new ProtocolVersion("PROTO", 22, 356), parseStr("PROTO/22.356  "));

        Assertions.assertEquals(new ProtocolVersion("PROTO", 1, 0), parseStr("PROTO/1,  ", Tokenizer.delimiters(',')));
        Assertions.assertEquals(new ProtocolVersion("PROTO", 1, 1), parseStr("PROTO/1.1;   ", Tokenizer.delimiters(',', ';')));
        Assertions.assertEquals(new ProtocolVersion("PROTO", 1, 200), parseStr("PROTO/1.200 blah; ", Tokenizer.delimiters(',')));
    }

    @Test
    public void testParseFailures() throws Exception {
        Assertions.assertThrows(ParseException.class, () -> parseStr("PROTO", "blah"));
        Assertions.assertThrows(ParseException.class, () -> parseStr("PROTO", "1.blah"));
        Assertions.assertThrows(ParseException.class, () -> parseStr("PROTO", "1A.0"));
        Assertions.assertThrows(ParseException.class, () -> parseStr("PROTO", ""));
        Assertions.assertThrows(ParseException.class, () -> parseStr(""));
        Assertions.assertThrows(ParseException.class, () -> parseStr("   "));
        Assertions.assertThrows(ParseException.class, () -> parseStr("   /1.0"));
        Assertions.assertThrows(ParseException.class, () -> parseStr("   / "));
        Assertions.assertThrows(ParseException.class, () -> parseStr("PROTO"));
        Assertions.assertThrows(ParseException.class, () -> parseStr("PROTO/"));
        Assertions.assertThrows(ParseException.class, () -> parseStr("PROTO/ 1.0"));
    }

}
