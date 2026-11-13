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

package org.apache.hc.core5.http.ssl;

import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.util.Tokenizer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TLSTest {


    @Test
    void isSame() throws ParseException {
        Assertions.assertThat(TLS.V_1_0.isSame(TLS.parse("TLSv1"))).isTrue();
    }

    @Test
    void isComparable() throws ParseException {
        Assertions.assertThat(TLS.V_1_0.isComparable(TLS.parse("TLSv1"))).isTrue();
    }

    @Test
    void greaterEquals() throws ParseException {
        Assertions.assertThat(TLS.V_1_3.greaterEquals(TLS.parse("TLSv1"))).isTrue();
    }

    @Test
    void lessEquals() throws ParseException {
        Assertions.assertThat(TLS.V_1_0.lessEquals(TLS.parse("TLSv1.3"))).isTrue();
    }

    @Test
    void parse() throws ParseException {
        Assertions.assertThat(TLS.V_1_0.lessEquals(TLS.parse("TLSv1.3"))).isTrue();
    }

    @Test
    void parseNull() throws ParseException {
        Assertions.assertThat(TLS.parse(null)).isNull();
    }

    @Test
    void excludeWeakNull() {
        Assertions.assertThat(TLS.excludeWeak((String[]) null)).isNull();
    }

    @Test
    void excludeWeak() {
        final String[] mixProtocol = {
                "SSL 2.0",
                "TLS 1.3",
                "SSL 3.0",
                "TLS 1.2",
                "TLS 1.1"
        };
        final String[] strongProtocols = TLS.excludeWeak(mixProtocol);
        for (final String protocol : strongProtocols) {
            Assertions.assertThat(TLS.isSecure(protocol)).isTrue();
        }
    }

    @Test
    void testParseBasic() throws Exception {
        Assertions.assertThat(TLS.parse("TLSv1")).isEqualTo(TLS.V_1_0.getVersion());
        Assertions.assertThat(TLS.parse("TLSv1.1")).isEqualTo(TLS.V_1_1.getVersion());
        Assertions.assertThat(TLS.parse("TLSv1.2")).isEqualTo(TLS.V_1_2.getVersion());
        Assertions.assertThat(TLS.parse("TLSv1.3  ")).isEqualTo(TLS.V_1_3.getVersion());
        Assertions.assertThat(TLS.parse("TLSv22.356")).isEqualTo(new ProtocolVersion("TLS", 22, 356));
    }

    @Test
    void testParseBuffer() throws Exception {
        final Tokenizer.Cursor cursor = new Tokenizer.Cursor(1, 13);
        Assertions.assertThat(TLS.parse(" TLSv1.2,0000", cursor, Tokenizer.delimiters(','))).isEqualTo(TLS.V_1_2.getVersion());
        Assertions.assertThat(cursor.getPos()).isEqualTo(8);
    }

    @Test
    void testParseFailure() {
        Assertions.assertThatThrownBy(() ->
                        TLS.parse("Tlsv1"))
                .isInstanceOf(ParseException.class);
        Assertions.assertThatThrownBy(() ->
                        TLS.parse("TLSV1"))
                .isInstanceOf(ParseException.class);
        Assertions.assertThatThrownBy(() ->
                        TLS.parse("TLSv"))
                .isInstanceOf(ParseException.class);
        Assertions.assertThatThrownBy(() ->
                        TLS.parse("TLSv1A"))
                .isInstanceOf(ParseException.class);
        Assertions.assertThatThrownBy(() ->
                        TLS.parse("TLSv1.A"))
                .isInstanceOf(ParseException.class);
        Assertions.assertThatThrownBy(() ->
                        TLS.parse("TLSv1.1 huh?"))
                .isInstanceOf(ParseException.class);
    }

}
