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

package org.apache.hc.core5.net;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Unit tests for {@link PercentCodec}.
 */
public class TestPercentCodec {

    @Test
    public void testCoding() {
        final StringBuilder buf = new StringBuilder();
        PercentCodec.encode(buf, "blah!", StandardCharsets.UTF_8);
        PercentCodec.encode(buf, " ~ ", StandardCharsets.UTF_8);
        PercentCodec.encode(buf, "huh?", StandardCharsets.UTF_8);
        assertThat(buf.toString(), CoreMatchers.equalTo("blah%21%20~%20huh%3F"));
    }

    @Test
    public void testDecoding() {
        assertThat(PercentCodec.decode("blah%21%20~%20huh%3F", StandardCharsets.UTF_8),
                CoreMatchers.equalTo("blah! ~ huh?"));
        assertThat(PercentCodec.decode("blah%21+~%20huh%3F", StandardCharsets.UTF_8),
                CoreMatchers.equalTo("blah!+~ huh?"));
        assertThat(PercentCodec.decode("blah%21+~%20huh%3F", StandardCharsets.UTF_8, true),
                CoreMatchers.equalTo("blah! ~ huh?"));
    }

    @Test
    public void testDecodingPartialContent() {
        assertThat(PercentCodec.decode("blah%21%20%", StandardCharsets.UTF_8),
                CoreMatchers.equalTo("blah! %"));
        assertThat(PercentCodec.decode("blah%21%20%a", StandardCharsets.UTF_8),
                CoreMatchers.equalTo("blah! %a"));
        assertThat(PercentCodec.decode("blah%21%20%wa", StandardCharsets.UTF_8),
                CoreMatchers.equalTo("blah! %wa"));
    }

    @ParameterizedTest
    @MethodSource("params")
    public void testRfc5987EncodingDecoding(final String input, final String expected) {
        assertEquals(expected, PercentCodec.RFC5987.encode(input));
        assertEquals(input, PercentCodec.RFC5987.decode(expected));
    }

    static Stream<Object[]> params() {
        return Stream.of(
                new Object[]{"foo-ä-€.html", "foo-%C3%A4-%E2%82%AC.html"},
                new Object[]{"世界ーファイル 2.jpg", "%E4%B8%96%E7%95%8C%E3%83%BC%E3%83%95%E3%82%A1%E3%82%A4%E3%83%AB%202.jpg"},
                new Object[]{"foo.jpg", "foo.jpg"},
                new Object[]{"simple", "simple"},  // Unreserved characters
                new Object[]{"reserved/chars?", "reserved%2Fchars%3F"},  // Reserved characters
                new Object[]{"", ""},  // Empty string
                new Object[]{"space test", "space%20test"},  // String with space
                new Object[]{"ümlaut", "%C3%BCmlaut"}  // Non-ASCII characters
        );
    }

    @Test
    public void verifyRfc5987EncodingandDecoding() {
        final String s = "!\"$£%^&*()_-+={[}]:@~;'#,./<>?\\|✓éèæðŃœ";
        assertThat(PercentCodec.RFC5987.decode(PercentCodec.RFC5987.encode(s)), CoreMatchers.equalTo(s));
    }

}
