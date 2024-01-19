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

import org.apache.hc.core5.util.Tokenizer;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestProtocolVersion {

    private static final ProtocolVersion PROTOCOL_VERSION_0_0 = new ProtocolVersion("a", 0, 0);
    private static final ProtocolVersion PROTOCOL_VERSION_1_0 = new ProtocolVersion("b", 1, 0);
    private static final ProtocolVersion PROTOCOL_VERSION_1_2 = new ProtocolVersion("c", 1, 2);

    @Test
    public void testEqualsMajorMinor() {
        Assertions.assertTrue(PROTOCOL_VERSION_0_0.equals(0, 0));
        Assertions.assertTrue(PROTOCOL_VERSION_1_0.equals(1, 0));
        Assertions.assertTrue(PROTOCOL_VERSION_1_2.equals(1, 2));
        //
        Assertions.assertFalse(PROTOCOL_VERSION_1_2.equals(2, 0));
    }

    @Test
    public void testParseBasic() throws Exception {
        assertThat(ProtocolVersion.parse("PROTO/1"), CoreMatchers.equalTo(new ProtocolVersion("PROTO", 1, 0)));
        assertThat(ProtocolVersion.parse("PROTO/1.1"), CoreMatchers.equalTo(new ProtocolVersion("PROTO", 1, 1)));
        assertThat(ProtocolVersion.parse("PROTO/1.2"), CoreMatchers.equalTo(new ProtocolVersion("PROTO", 1, 2)));
        assertThat(ProtocolVersion.parse("PROTO/1.3  "), CoreMatchers.equalTo(new ProtocolVersion("PROTO", 1, 3)));
        assertThat(ProtocolVersion.parse("PROTO/000.0000  "), CoreMatchers.equalTo(new ProtocolVersion("PROTO", 0, 0)));
        assertThat(ProtocolVersion.parse("PROTO/22.356"), CoreMatchers.equalTo(new ProtocolVersion("PROTO", 22, 356)));
    }

    @Test
    public void testParseBuffer() throws Exception {
        final Tokenizer.Cursor cursor = new Tokenizer.Cursor(1, 13);
        assertThat(ProtocolVersion.parse(" PROTO/1.2,0000", cursor, Tokenizer.delimiters(',')), CoreMatchers.equalTo(new ProtocolVersion("PROTO", 1, 2)));
        assertThat(cursor.getPos(), CoreMatchers.equalTo(10));
    }

    @Test
    public void testParseFailure() throws Exception {
        Assertions.assertThrows(ParseException.class, () -> ProtocolVersion.parse("/1"));
        Assertions.assertThrows(ParseException.class, () -> ProtocolVersion.parse(" /1"));
        Assertions.assertThrows(ParseException.class, () -> ProtocolVersion.parse("PROTO/"));
        Assertions.assertThrows(ParseException.class, () -> ProtocolVersion.parse("PROTO/1A"));
        Assertions.assertThrows(ParseException.class, () -> ProtocolVersion.parse("PROTO/1.A"));
        Assertions.assertThrows(ParseException.class, () -> ProtocolVersion.parse("PROTO/1.1 huh?"));
    }

}
