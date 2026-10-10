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
package org.apache.hc.core5.http.signature;

import java.net.URI;
import java.util.Arrays;

import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.message.HeaderGroup;
import org.apache.hc.core5.http.structured.StructuredFieldBareItem;
import org.apache.hc.core5.http.structured.StructuredFieldParameters;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestMessageSignatureBaseBuilder {

    private static final String CONTENT_DIGEST =
            "sha-512=:WZDPaVn/7XgHaAy8pmojAkGWoRx2UFChF41A2svX+TaPm+AbwAgBWnrIiYllu7BNNyealdVLvRwEmTHWXvJwew==:";

    private final MessageSignatureBaseBuilder builder = new MessageSignatureBaseBuilder();

    @Test
    void testRfc9421B22SelectiveCoverage() throws Exception {
        final BasicHttpRequest request = testRequest();
        final StructuredFieldParameters queryName = StructuredFieldParameters.builder()
                .put("name", StructuredFieldBareItem.ofString("Pet"))
                .build();
        final StructuredFieldParameters signatureParameters = StructuredFieldParameters.builder()
                .put("created", StructuredFieldBareItem.ofInteger(1618884473L))
                .put("keyid", StructuredFieldBareItem.ofString("test-key-rsa-pss"))
                .put("tag", StructuredFieldBareItem.ofString("header-example"))
                .build();
        final MessageSignatureInput input = new MessageSignatureInput("sig-b22", Arrays.asList(
                MessageSignatureComponent.derived("@authority"),
                MessageSignatureComponent.field("content-digest"),
                MessageSignatureComponent.create("@query-param", queryName)), signatureParameters);

        final String actual = builder.build(input, MessageSignatureContext.builder(request).build());

        final String expected =
                "\"@authority\": example.com\n"
                + "\"content-digest\": " + CONTENT_DIGEST + "\n"
                + "\"@query-param\";name=\"Pet\": dog\n"
                + "\"@signature-params\": (\"@authority\" \"content-digest\" \"@query-param\";name=\"Pet\")"
                + ";created=1618884473;keyid=\"test-key-rsa-pss\";tag=\"header-example\"";
        Assertions.assertEquals(expected, actual);
    }

    @Test
    void testRfc9421B23FullCoverage() throws Exception {
        final BasicHttpRequest request = testRequest();
        final StructuredFieldParameters signatureParameters = StructuredFieldParameters.builder()
                .put("created", StructuredFieldBareItem.ofInteger(1618884473L))
                .put("keyid", StructuredFieldBareItem.ofString("test-key-rsa-pss"))
                .build();
        final MessageSignatureInput input = new MessageSignatureInput("sig-b23", Arrays.asList(
                MessageSignatureComponent.field("date"),
                MessageSignatureComponent.derived("@method"),
                MessageSignatureComponent.derived("@path"),
                MessageSignatureComponent.derived("@query"),
                MessageSignatureComponent.derived("@authority"),
                MessageSignatureComponent.field("content-type"),
                MessageSignatureComponent.field("content-digest"),
                MessageSignatureComponent.field("content-length")), signatureParameters);

        final String actual = builder.build(input, MessageSignatureContext.builder(request).build());

        final String expected =
                "\"date\": Tue, 20 Apr 2021 02:07:55 GMT\n"
                + "\"@method\": POST\n"
                + "\"@path\": /foo\n"
                + "\"@query\": ?param=Value&Pet=dog\n"
                + "\"@authority\": example.com\n"
                + "\"content-type\": application/json\n"
                + "\"content-digest\": " + CONTENT_DIGEST + "\n"
                + "\"content-length\": 18\n"
                + "\"@signature-params\": (\"date\" \"@method\" \"@path\" \"@query\" \"@authority\" "
                + "\"content-type\" \"content-digest\" \"content-length\")"
                + ";created=1618884473;keyid=\"test-key-rsa-pss\"";
        Assertions.assertEquals(expected, actual);
    }

    @Test
    void testAllStandardRequestDerivedComponents() throws Exception {
        final BasicHttpRequest request = new BasicHttpRequest(
                "POST", URI.create("HTTPS://EXAMPLE.COM:443/path%2Fsegment?x=1"));
        final MessageSignatureInput input = new MessageSignatureInput("derived", Arrays.asList(
                MessageSignatureComponent.derived("@method"),
                MessageSignatureComponent.derived("@target-uri"),
                MessageSignatureComponent.derived("@authority"),
                MessageSignatureComponent.derived("@scheme"),
                MessageSignatureComponent.derived("@request-target"),
                MessageSignatureComponent.derived("@path"),
                MessageSignatureComponent.derived("@query")), StructuredFieldParameters.EMPTY);

        final String actual = builder.build(input, MessageSignatureContext.builder(request).build());
        final String expected =
                "\"@method\": POST\n"
                + "\"@target-uri\": https://example.com/path%2Fsegment?x=1\n"
                + "\"@authority\": example.com\n"
                + "\"@scheme\": https\n"
                + "\"@request-target\": /path%2Fsegment?x=1\n"
                + "\"@path\": /path%2Fsegment\n"
                + "\"@query\": ?x=1\n"
                + "\"@signature-params\": (\"@method\" \"@target-uri\" \"@authority\" \"@scheme\" "
                + "\"@request-target\" \"@path\" \"@query\")";
        Assertions.assertEquals(expected, actual);
    }

    @Test
    void testRfc9421QueryParameterEncodingExample() throws Exception {
        final BasicHttpRequest request = new BasicHttpRequest("GET", URI.create(
                "https://www.example.com/parameters?var=this%20is%20a%20big%0Amultiline%20value"
                + "&bar=with+plus+whitespace&fa%C3%A7ade%22%3A%20=something"));
        final MessageSignatureInput input = new MessageSignatureInput("query", Arrays.asList(
                MessageSignatureComponent.queryParam("var"),
                MessageSignatureComponent.queryParam("bar"),
                MessageSignatureComponent.queryParam("fa%C3%A7ade%22%3A%20")), StructuredFieldParameters.EMPTY);

        final String actual = builder.build(input, MessageSignatureContext.builder(request).build());
        final String expected =
                "\"@query-param\";name=\"var\": this%20is%20a%20big%0Amultiline%20value\n"
                + "\"@query-param\";name=\"bar\": with%20plus%20whitespace\n"
                + "\"@query-param\";name=\"fa%C3%A7ade%22%3A%20\": something\n"
                + "\"@signature-params\": (\"@query-param\";name=\"var\" "
                + "\"@query-param\";name=\"bar\" \"@query-param\";name=\"fa%C3%A7ade%22%3A%20\")";
        Assertions.assertEquals(expected, actual);
    }


    @Test
    void testRfc9421B24ResponseCoverage() throws Exception {
        final BasicHttpResponse response = new BasicHttpResponse(200);
        response.addHeader("Content-Type", "application/json");
        response.addHeader("Content-Digest",
                "sha-512=:mEWXIS7MaLRuGgxOBdODa3xqM1XdEvxoYhvlCFJ41QJgJc4GTsPp29l5oGX69wWdXymyU0rjJuahq4l5aGgfLQ==:");
        response.addHeader("Content-Length", "23");
        final StructuredFieldParameters signatureParameters = StructuredFieldParameters.builder()
                .put("created", StructuredFieldBareItem.ofInteger(1618884473L))
                .put("keyid", StructuredFieldBareItem.ofString("test-key-ecc-p256"))
                .build();
        final MessageSignatureInput input = new MessageSignatureInput("sig-b24", Arrays.asList(
                MessageSignatureComponent.derived("@status"),
                MessageSignatureComponent.field("content-type"),
                MessageSignatureComponent.field("content-digest"),
                MessageSignatureComponent.field("content-length")), signatureParameters);

        final String actual = builder.build(input, MessageSignatureContext.builder(response).build());
        final String expected =
                "\"@status\": 200\n"
                + "\"content-type\": application/json\n"
                + "\"content-digest\": sha-512=:mEWXIS7MaLRuGgxOBdODa3xqM1XdEvxoYhvlCFJ41Q"
                + "JgJc4GTsPp29l5oGX69wWdXymyU0rjJuahq4l5aGgfLQ==:\n"
                + "\"content-length\": 23\n"
                + "\"@signature-params\": (\"@status\" \"content-type\" \"content-digest\" "
                + "\"content-length\");created=1618884473;keyid=\"test-key-ecc-p256\"";
        Assertions.assertEquals(expected, actual);
    }

    @Test
    void testInvalidHttpStatusCodeFails() throws Exception {
        final BasicHttpResponse response = new BasicHttpResponse(600);
        final MessageSignatureInput input = new MessageSignatureInput("bad-status", Arrays.asList(
                MessageSignatureComponent.derived("@status")), StructuredFieldParameters.EMPTY);
        Assertions.assertThrows(MessageSignatureException.class,
                () -> builder.build(input, MessageSignatureContext.builder(response).build()));
    }

    @Test
    void testGenericFieldCanonicalizationCombinesValues() throws Exception {
        final BasicHttpRequest request = new BasicHttpRequest("GET", URI.create("https://example.com/"));
        request.addHeader("X-Test", "  alpha  ");
        request.addHeader("X-Test", "  gamma\tvalue  ");
        final MessageSignatureInput input = new MessageSignatureInput("field", Arrays.asList(
                MessageSignatureComponent.field("x-test")), StructuredFieldParameters.EMPTY);

        final String actual = builder.build(input, MessageSignatureContext.builder(request).build());
        Assertions.assertEquals(
                "\"x-test\": alpha, gamma\tvalue\n"
                + "\"@signature-params\": (\"x-test\")", actual);
    }

    @Test
    void testQueryWithoutQueryStringIsQuestionMark() throws Exception {
        final BasicHttpRequest request = new BasicHttpRequest("GET", URI.create("https://example.com/path"));
        final MessageSignatureInput input = new MessageSignatureInput("q", Arrays.asList(
                MessageSignatureComponent.derived("@query")), StructuredFieldParameters.EMPTY);
        Assertions.assertEquals(
                "\"@query\": ?\n\"@signature-params\": (\"@query\")",
                builder.build(input, MessageSignatureContext.builder(request).build()));
    }

    @Test
    void testQueryParameterSkipsEmptyFormSegments() throws Exception {
        final BasicHttpRequest request = new BasicHttpRequest(
                "GET", URI.create("https://example.com/?&&a=1&&"));
        final MessageSignatureInput input = new MessageSignatureInput("q", Arrays.asList(
                MessageSignatureComponent.queryParam("a")), StructuredFieldParameters.EMPTY);
        Assertions.assertEquals(
                "\"@query-param\";name=\"a\": 1\n"
                + "\"@signature-params\": (\"@query-param\";name=\"a\")",
                builder.build(input, MessageSignatureContext.builder(request).build()));
    }

    @Test
    void testQueryParameterNameMustUseCanonicalEncoding() throws Exception {
        final BasicHttpRequest request = new BasicHttpRequest("GET", URI.create("https://example.com/?a=1"));
        final MessageSignatureInput input = new MessageSignatureInput("q", Arrays.asList(
                MessageSignatureComponent.queryParam("%61")), StructuredFieldParameters.EMPTY);
        Assertions.assertThrows(MessageSignatureException.class,
                () -> builder.build(input, MessageSignatureContext.builder(request).build()));
    }

    @Test
    void testEmptyQueryHasNoEmptyNamedParameter() throws Exception {
        final BasicHttpRequest request = new BasicHttpRequest("GET", URI.create("https://example.com/?"));
        final MessageSignatureInput input = new MessageSignatureInput("q", Arrays.asList(
                MessageSignatureComponent.queryParam("")), StructuredFieldParameters.EMPTY);
        Assertions.assertThrows(MessageSignatureException.class,
                () -> builder.build(input, MessageSignatureContext.builder(request).build()));
    }

    @Test
    void testExplicitEmptyNamedParameterIsAddressable() throws Exception {
        final BasicHttpRequest request = new BasicHttpRequest("GET", URI.create("https://example.com/?=value"));
        final MessageSignatureInput input = new MessageSignatureInput("q", Arrays.asList(
                MessageSignatureComponent.queryParam("")), StructuredFieldParameters.EMPTY);
        Assertions.assertEquals(
                "\"@query-param\";name=\"\": value\n"
                + "\"@signature-params\": (\"@query-param\";name=\"\")",
                builder.build(input, MessageSignatureContext.builder(request).build()));
    }

    @Test
    void testStatusOnRequestFails() throws Exception {
        final BasicHttpRequest request = testRequest();
        final MessageSignatureInput input = new MessageSignatureInput("bad", Arrays.asList(
                MessageSignatureComponent.derived("@status")), StructuredFieldParameters.EMPTY);
        Assertions.assertThrows(MessageSignatureException.class,
                () -> builder.build(input, MessageSignatureContext.builder(request).build()));
    }

    @Test
    void testTrailerFieldIsTakenFromTrailersNotHeaders() throws Exception {
        final BasicHttpRequest request = new BasicHttpRequest("POST", URI.create("https://example.com/"));
        request.addHeader("Trailer-Field", "from-header");
        final HeaderGroup trailers = new HeaderGroup();
        trailers.addHeader(new BasicHeader("Trailer-Field", "from-trailer"));
        final StructuredFieldParameters tr = StructuredFieldParameters.builder().putBoolean("tr", true).build();
        final MessageSignatureInput input = new MessageSignatureInput("tr", Arrays.asList(
                MessageSignatureComponent.create("trailer-field", tr)), StructuredFieldParameters.EMPTY);
        final String actual = builder.build(input,
                MessageSignatureContext.builder(request).trailers(trailers).build());
        Assertions.assertEquals(
                "\"trailer-field\";tr: from-trailer\n"
                + "\"@signature-params\": (\"trailer-field\";tr)", actual);
    }

    @Test
    void testDerivedComponentValueMustBePrintableAscii() throws Exception {
        final MessageSignatureBaseBuilder badBuilder = new MessageSignatureBaseBuilder((component, context) -> "a\tb");
        final BasicHttpRequest request = testRequest();
        final MessageSignatureInput input = new MessageSignatureInput("bad", Arrays.asList(
                MessageSignatureComponent.derived("@method")), StructuredFieldParameters.EMPTY);
        Assertions.assertThrows(MessageSignatureException.class,
                () -> badBuilder.build(input, MessageSignatureContext.builder(request).build()));
    }

    @Test
    void testDerivedComponentValueMustNotHaveEdgeWhitespace() throws Exception {
        final MessageSignatureBaseBuilder badBuilder = new MessageSignatureBaseBuilder(
                (component, context) -> " value ");
        final MessageSignatureInput input = new MessageSignatureInput("bad", Arrays.asList(
                MessageSignatureComponent.derived("@method")), StructuredFieldParameters.EMPTY);
        Assertions.assertThrows(MessageSignatureException.class,
                () -> badBuilder.build(input, MessageSignatureContext.builder(testRequest()).build()));
    }

    @Test
    void testInvalidFieldNameRejectedProgrammatically() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> MessageSignatureComponent.create("bad field", StructuredFieldParameters.EMPTY));
    }

    @Test
    void testComponentParameterOrderIsIgnoredForDuplicateDetection() {
        final StructuredFieldParameters first = StructuredFieldParameters.builder()
                .putBoolean("sf", true)
                .putBoolean("req", true)
                .build();
        final StructuredFieldParameters second = StructuredFieldParameters.builder()
                .putBoolean("req", true)
                .putBoolean("sf", true)
                .build();
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new MessageSignatureInput("dup", Arrays.asList(
                        MessageSignatureComponent.create("example", first),
                        MessageSignatureComponent.create("example", second)),
                        StructuredFieldParameters.EMPTY));
    }

    @Test
    void testRepeatedNamedQueryParameterFails() throws Exception {
        final BasicHttpRequest request = new BasicHttpRequest("GET", URI.create("https://example.com/?a=1&a=2"));
        final MessageSignatureInput input = new MessageSignatureInput(
                "q", Arrays.asList(MessageSignatureComponent.queryParam("a")), StructuredFieldParameters.EMPTY);
        Assertions.assertThrows(MessageSignatureException.class,
                () -> builder.build(input, MessageSignatureContext.builder(request).build()));
    }

    @Test
    void testResponseStatusAndRelatedRequestComponents() throws Exception {
        final BasicHttpRequest request = new BasicHttpRequest("POST", URI.create("https://example.com/foo?x=1"));
        final BasicHttpResponse response = new BasicHttpResponse(503);
        final StructuredFieldParameters req = StructuredFieldParameters.builder().putBoolean("req", true).build();
        final MessageSignatureInput input = new MessageSignatureInput("res", Arrays.asList(
                MessageSignatureComponent.derived("@status"),
                MessageSignatureComponent.create("@authority", req),
                MessageSignatureComponent.create("@method", req),
                MessageSignatureComponent.create("@path", req)), StructuredFieldParameters.EMPTY);

        final String actual = builder.build(input, MessageSignatureContext.builder(response)
                .relatedRequest(request)
                .build());
        final String expected =
                "\"@status\": 503\n"
                + "\"@authority\";req: example.com\n"
                + "\"@method\";req: POST\n"
                + "\"@path\";req: /foo\n"
                + "\"@signature-params\": (\"@status\" \"@authority\";req \"@method\";req \"@path\";req)";
        Assertions.assertEquals(expected, actual);
    }

    @Test
    void testReqOnRequestFails() throws Exception {
        final BasicHttpRequest request = testRequest();
        final StructuredFieldParameters req = StructuredFieldParameters.builder().putBoolean("req", true).build();
        final MessageSignatureInput input = new MessageSignatureInput("bad", Arrays.asList(
                MessageSignatureComponent.create("@method", req)), StructuredFieldParameters.EMPTY);
        Assertions.assertThrows(MessageSignatureException.class,
                () -> builder.build(input, MessageSignatureContext.builder(request).build()));
    }

    @Test
    void testStructuredFieldStrictSerializationAndDictionaryKey() throws Exception {
        final BasicHttpRequest request = new BasicHttpRequest("GET", URI.create("https://example.com/"));
        request.addHeader("Example-Dict", "a=1,    b=2;x=1;y=2,   c=(a   b   c)");
        final StructuredFieldParameters sf = StructuredFieldParameters.builder().putBoolean("sf", true).build();
        final StructuredFieldParameters key = StructuredFieldParameters.builder()
                .put("key", StructuredFieldBareItem.ofString("c"))
                .build();
        final MessageSignatureInput input = new MessageSignatureInput("sf", Arrays.asList(
                MessageSignatureComponent.create("example-dict", sf),
                MessageSignatureComponent.create("example-dict", key)), StructuredFieldParameters.EMPTY);

        final String actual = builder.build(input, MessageSignatureContext.builder(request)
                .structuredField("example-dict", StructuredFieldValueType.DICTIONARY)
                .build());
        final String expected =
                "\"example-dict\";sf: a=1, b=2;x=1;y=2, c=(a b c)\n"
                + "\"example-dict\";key=\"c\": (a b c)\n"
                + "\"@signature-params\": (\"example-dict\";sf \"example-dict\";key=\"c\")";
        Assertions.assertEquals(expected, actual);
    }

    @Test
    void testBinaryWrappedFieldValues() throws Exception {
        final BasicHttpRequest request = new BasicHttpRequest("GET", URI.create("https://example.com/"));
        request.addHeader("Set-Cookie", "a=1");
        request.addHeader("Set-Cookie", "b=2");
        final StructuredFieldParameters bs = StructuredFieldParameters.builder().putBoolean("bs", true).build();
        final MessageSignatureInput input = new MessageSignatureInput("bs", Arrays.asList(
                MessageSignatureComponent.create("set-cookie", bs)), StructuredFieldParameters.EMPTY);

        final String actual = builder.build(input, MessageSignatureContext.builder(request).build());
        Assertions.assertEquals(
                "\"set-cookie\";bs: :YT0x:, :Yj0y:\n"
                + "\"@signature-params\": (\"set-cookie\";bs)", actual);
    }

    @Test
    void testBsAndSfAreIncompatible() throws Exception {
        final BasicHttpRequest request = new BasicHttpRequest("GET", URI.create("https://example.com/"));
        request.addHeader("Example", "a=1");
        final StructuredFieldParameters parameters = StructuredFieldParameters.builder()
                .putBoolean("bs", true)
                .putBoolean("sf", true)
                .build();
        final MessageSignatureInput input = new MessageSignatureInput("bad", Arrays.asList(
                MessageSignatureComponent.create("example", parameters)), StructuredFieldParameters.EMPTY);
        Assertions.assertThrows(MessageSignatureException.class,
                () -> builder.build(input, MessageSignatureContext.builder(request).build()));
    }

    @Test
    void testUnknownComponentParameterFails() throws Exception {
        final BasicHttpRequest request = testRequest();
        final StructuredFieldParameters parameters = StructuredFieldParameters.builder()
                .putBoolean("future", true)
                .build();
        final MessageSignatureInput input = new MessageSignatureInput("bad", Arrays.asList(
                MessageSignatureComponent.create("content-type", parameters)), StructuredFieldParameters.EMPTY);
        Assertions.assertThrows(MessageSignatureException.class,
                () -> builder.build(input, MessageSignatureContext.builder(request).build()));
    }

    private static BasicHttpRequest testRequest() {
        final BasicHttpRequest request = new BasicHttpRequest(
                "POST", URI.create("https://example.com/foo?param=Value&Pet=dog"));
        request.addHeader("Date", "Tue, 20 Apr 2021 02:07:55 GMT");
        request.addHeader("Content-Type", "application/json");
        request.addHeader("Content-Digest", CONTENT_DIGEST);
        request.addHeader("Content-Length", "18");
        return request;
    }

}
