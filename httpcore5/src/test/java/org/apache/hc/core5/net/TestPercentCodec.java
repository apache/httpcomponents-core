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

import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.stream.Stream;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Unit tests for {@link PercentCodec}.
 */
class TestPercentCodec {

    @Test
    void testCoding() {
        final StringBuilder buf = new StringBuilder();
        PercentCodec.encode(buf, "blah!", StandardCharsets.UTF_8);
        PercentCodec.encode(buf, " ~ ", StandardCharsets.UTF_8);
        PercentCodec.encode(buf, "huh?", StandardCharsets.UTF_8);
        Assertions.assertThat(buf.toString()).isEqualTo("blah%21%20~%20huh%3F");
    }

    @Test
    void testLowerCaseHexEncoding() {
        final BitSet safeChars = new BitSet(256);
        safeChars.set(' ');
        final PercentCodec codec = new PercentCodec(safeChars, true);
        Assertions.assertThat(codec.encode(" ü\"%")).isEqualTo(" %c3%bc%22%25");
        Assertions.assertThat(PercentCodec.RFC3986.encode("ü")).isEqualTo("%C3%BC");
    }

    @Test
    void testDecoding() {
        Assertions.assertThat(PercentCodec.decode("blah%21%20~%20huh%3F", StandardCharsets.UTF_8)).isEqualTo("blah! ~ huh?");
        Assertions.assertThat(PercentCodec.decode("blah%21+~%20huh%3F", StandardCharsets.UTF_8)).isEqualTo("blah!+~ huh?");
        Assertions.assertThat(PercentCodec.decode("blah%21+~%20huh%3F", StandardCharsets.UTF_8, true)).isEqualTo("blah! ~ huh?");
    }

    @Test
    void testDecodingPartialContent() {
        Assertions.assertThat(PercentCodec.decode("blah%21%20%", StandardCharsets.UTF_8)).isEqualTo("blah! %");
        Assertions.assertThat(PercentCodec.decode("blah%21%20%a", StandardCharsets.UTF_8)).isEqualTo("blah! %a");
        Assertions.assertThat(PercentCodec.decode("blah%21%20%wa", StandardCharsets.UTF_8)).isEqualTo("blah! %wa");
    }

    @ParameterizedTest
    @MethodSource("params")
    void testRfc5987EncodingDecoding(final String input, final String expected) {
        Assertions.assertThat(PercentCodec.RFC5987.encode(input)).isEqualTo(expected);
        Assertions.assertThat(PercentCodec.RFC5987.decode(expected)).isEqualTo(input);
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
    void verifyRfc5987EncodingandDecoding() {
        final String s = "!\"$£%^&*()_-+={[}]:@~;'#,./<>?\\|✓éèæðŃœ";
        Assertions.assertThat(PercentCodec.RFC5987.decode(PercentCodec.RFC5987.encode(s))).isEqualTo(s);
    }

    @Test
    void testRfc7639CanonicalAlpnTokenEncoding() {
        // RFC 7639 requires protocol-id to be a token and applies additional canonical constraints:
        // - Octets not allowed in tokens MUST be percent-encoded (RFC 3986).
        // - '%' MUST be percent-encoded.
        // - Octets that are valid token characters MUST NOT be percent-encoded (except '%').
        // - Uppercase hex digits MUST be used.
        Assertions.assertThat(PercentCodec.HTTP_TOKEN.encode("h2")).isEqualTo("h2");
        Assertions.assertThat(PercentCodec.HTTP_TOKEN.encode("http/1.1")).isEqualTo("http%2F1.1");
        Assertions.assertThat(PercentCodec.HTTP_TOKEN.encode("%")).isEqualTo("%25");
        Assertions.assertThat(PercentCodec.HTTP_TOKEN.encode("foo+bar")).isEqualTo("foo+bar");
        Assertions.assertThat(PercentCodec.HTTP_TOKEN.encode("!#$&'*+-.^_`|~")).isEqualTo("!#$&'*+-.^_`|~");
        Assertions.assertThat(PercentCodec.HTTP_TOKEN.decode("foo%20bar")).isEqualTo("foo bar");
        Assertions.assertThat(PercentCodec.HTTP_TOKEN.decode("ws%2F%C3%A9")).isEqualTo("ws/é");
    }

    @Test
    void testPercentCodecEncodeIsNotRfc7639Canonical() {
        // PercentCodec.encode(..) uses RFC 3986 UNRESERVED as the safe set.
        // This percent-encodes valid RFC 7230 tchar like '+', '*', '!', '|', which RFC 7639 forbids.
        Assertions.assertThat(PercentCodec.encode("foo+bar", StandardCharsets.UTF_8)).isEqualTo("foo%2Bbar");
        Assertions.assertThat(PercentCodec.encode("*", StandardCharsets.UTF_8)).isEqualTo("%2A");
        Assertions.assertThat(PercentCodec.encode("!", StandardCharsets.UTF_8)).isEqualTo("%21");
        Assertions.assertThat(PercentCodec.encode("|", StandardCharsets.UTF_8)).isEqualTo("%7C");
    }

    @Test
    void decodeIllegalChars() {
        Assertions.assertThat(PercentCodec.decode("1\u012E2\u012E3\u012E4", StandardCharsets.UTF_8)).isEqualTo("1?2?3?4");
    }

    @Test
    void testDecodeDoesNotCombineEscapedBytesAcrossLiteralCharacters() {
        Assertions.assertThat(PercentCodec.decode("%C3x%A4", StandardCharsets.UTF_8)).isEqualTo("\uFFFDx\uFFFD");
    }

    @Test
    void testEncodeHonorsUtf16Charset() {
        Assertions.assertThat(PercentCodec.encode("abc", StandardCharsets.UTF_16)).isEqualTo("%FE%FF%00a%00b%00c");

    }

}
