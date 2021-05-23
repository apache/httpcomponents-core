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
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link TlsVersionParser}.
 */
public class TestTlsVersionParser {

    private TlsVersionParser impl;

    @Before
    public void setup() {
        impl = new TlsVersionParser();
    }

    @Test
    public void testParseBasic() throws Exception {
        MatcherAssert.assertThat(impl.parse("TLSv1"), CoreMatchers.equalTo(TLS.V_1_0.version));
        MatcherAssert.assertThat(impl.parse("TLSv1.1"), CoreMatchers.equalTo(TLS.V_1_1.version));
        MatcherAssert.assertThat(impl.parse("TLSv1.2"), CoreMatchers.equalTo(TLS.V_1_2.version));
        MatcherAssert.assertThat(impl.parse("TLSv1.3"), CoreMatchers.equalTo(TLS.V_1_3.version));
        MatcherAssert.assertThat(impl.parse("TLSv22.356"), CoreMatchers.equalTo(new ProtocolVersion("TLS", 22, 356)));
    }

    @Test
    public void testParseBuffer() throws Exception {
        final Tokenizer.Cursor cursor = new Tokenizer.Cursor(1, 13);
        MatcherAssert.assertThat(impl.parse(" TLSv1.2,0000", cursor, Tokenizer.INIT_BITSET(',')),
                CoreMatchers.equalTo(TLS.V_1_2.version));
        MatcherAssert.assertThat(cursor.getPos(), CoreMatchers.equalTo(8));
    }

    @Test
    public void testParseFailure1() throws Exception {
        Assert.assertThrows(ParseException.class, () ->
                impl.parse("Tlsv1"));
    }

    @Test
    public void testParseFailure2() throws Exception {
        Assert.assertThrows(ParseException.class, () ->
                impl.parse("TLSV1"));
    }

    @Test
    public void testParseFailure3() throws Exception {
        Assert.assertThrows(ParseException.class, () ->
                impl.parse("TLSv"));
    }

    @Test
    public void testParseFailure4() throws Exception {
        Assert.assertThrows(ParseException.class, () ->
                impl.parse("TLSv1A"));
    }

    @Test
    public void testParseFailure5() throws Exception {
        Assert.assertThrows(ParseException.class, () ->
                impl.parse("TLSv1.A"));
    }

}
