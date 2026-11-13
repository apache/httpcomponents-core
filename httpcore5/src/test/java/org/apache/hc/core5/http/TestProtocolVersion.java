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


import org.apache.hc.core5.util.Tokenizer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TestProtocolVersion {

    private static final ProtocolVersion PROTOCOL_VERSION_0_0 = new ProtocolVersion("a", 0, 0);
    private static final ProtocolVersion PROTOCOL_VERSION_1_0 = new ProtocolVersion("b", 1, 0);
    private static final ProtocolVersion PROTOCOL_VERSION_1_2 = new ProtocolVersion("c", 1, 2);

    @Test
    void testEqualsMajorMinor() {
        Assertions.assertThat(PROTOCOL_VERSION_0_0.equals(0, 0)).isTrue();
        Assertions.assertThat(PROTOCOL_VERSION_1_0.equals(1, 0)).isTrue();
        Assertions.assertThat(PROTOCOL_VERSION_1_2.equals(1, 2)).isTrue();
        //
        Assertions.assertThat(PROTOCOL_VERSION_1_2.equals(2, 0)).isFalse();
    }

    @Test
    void testParseBasic() throws Exception {
        Assertions.assertThat(ProtocolVersion.parse("PROTO/1")).isEqualTo(new ProtocolVersion("PROTO", 1, 0));
        Assertions.assertThat(ProtocolVersion.parse("PROTO/1.1")).isEqualTo(new ProtocolVersion("PROTO", 1, 1));
        Assertions.assertThat(ProtocolVersion.parse("PROTO/1.2")).isEqualTo(new ProtocolVersion("PROTO", 1, 2));
        Assertions.assertThat(ProtocolVersion.parse("PROTO/1.3  ")).isEqualTo(new ProtocolVersion("PROTO", 1, 3));
        Assertions.assertThat(ProtocolVersion.parse("PROTO/000.0000  ")).isEqualTo(new ProtocolVersion("PROTO", 0, 0));
        Assertions.assertThat(ProtocolVersion.parse("PROTO/22.356")).isEqualTo(new ProtocolVersion("PROTO", 22, 356));
    }

    @Test
    void testParseBuffer() throws Exception {
        final Tokenizer.Cursor cursor = new Tokenizer.Cursor(1, 13);
        Assertions.assertThat(ProtocolVersion.parse(" PROTO/1.2,0000", cursor, Tokenizer.delimiters(','))).isEqualTo(new ProtocolVersion("PROTO", 1, 2));
        Assertions.assertThat(cursor.getPos()).isEqualTo(10);
    }

    @Test
    void testParseFailure() {
        Assertions.assertThatThrownBy(() ->
                        ProtocolVersion.parse("/1"))
                .isInstanceOf(ParseException.class);
        Assertions.assertThatThrownBy(() ->
                        ProtocolVersion.parse(" /1"))
                .isInstanceOf(ParseException.class);
        Assertions.assertThatThrownBy(() ->
                        ProtocolVersion.parse("PROTO/"))
                .isInstanceOf(ParseException.class);
        Assertions.assertThatThrownBy(() ->
                        ProtocolVersion.parse("PROTO/1A"))
                .isInstanceOf(ParseException.class);
        Assertions.assertThatThrownBy(() ->
                        ProtocolVersion.parse("PROTO/1.A"))
                .isInstanceOf(ParseException.class);
        Assertions.assertThatThrownBy(() ->
                        ProtocolVersion.parse("PROTO/1.1 huh?"))
                .isInstanceOf(ParseException.class);
    }

}
