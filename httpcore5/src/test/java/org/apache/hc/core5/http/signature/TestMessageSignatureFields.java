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
import java.util.List;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.structured.StructuredFieldBareItem;
import org.apache.hc.core5.http.structured.StructuredFieldParameters;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestMessageSignatureFields {

    @Test
    void testParseAndSerializeSignatureInput() throws Exception {
        final BasicHttpRequest request = new BasicHttpRequest("GET", URI.create("https://example.com/"));
        request.addHeader("Signature-Input",
                "sig1=(\"@method\" \"@target-uri\" \"@authority\" \"content-digest\" \"cache-control\")"
                + ";created=1618884475;keyid=\"test-key-rsa-pss\"");

        final List<MessageSignatureInput> parsed = MessageSignatureFields.parseSignatureInput(request);
        Assertions.assertEquals(1, parsed.size());
        Assertions.assertEquals("sig1", parsed.get(0).getLabel());
        Assertions.assertEquals(5, parsed.get(0).getComponents().size());
        Assertions.assertEquals("@method", parsed.get(0).getComponents().get(0).getName());
        Assertions.assertEquals("cache-control", parsed.get(0).getComponents().get(4).getName());
        Assertions.assertEquals(1618884475L,
                parsed.get(0).getParameters().get("created").getLongValue());

        final Header formatted = MessageSignatureFields.formatSignatureInput(parsed);
        Assertions.assertEquals(
                "sig1=(\"@method\" \"@target-uri\" \"@authority\" \"content-digest\" \"cache-control\")"
                + ";created=1618884475;keyid=\"test-key-rsa-pss\"",
                formatted.getValue());
    }

    @Test
    void testParseAndSerializeSignature() throws Exception {
        final BasicHttpRequest request = new BasicHttpRequest("GET", URI.create("https://example.com/"));
        request.addHeader("Signature", "sig1=:AQID:, sig2=:BAUG:");

        final List<MessageSignature> parsed = MessageSignatureFields.parseSignature(request);
        Assertions.assertEquals(2, parsed.size());
        Assertions.assertArrayEquals(new byte[] {1, 2, 3}, parsed.get(0).getValue());
        Assertions.assertArrayEquals(new byte[] {4, 5, 6}, parsed.get(1).getValue());

        final Header formatted = MessageSignatureFields.formatSignature(parsed);
        Assertions.assertEquals("sig1=:AQID:, sig2=:BAUG:", formatted.getValue());
    }

    @Test
    void testSignatureInputLabelDuplicateAcrossFieldLinesFails() {
        final BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        request.addHeader("Signature-Input", "sig1=(\"@method\")");
        request.addHeader("Signature-Input", "sig1=(\"@path\")");
        Assertions.assertThrows(ParseException.class,
                () -> MessageSignatureFields.parseSignatureInput(request));
    }

    @Test
    void testSignatureInputLabelDuplicateWithinOneFieldLineFails() {
        final BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        request.addHeader("Signature-Input", "sig1=(\"@method\"), sig1=(\"@path\")");
        Assertions.assertThrows(ParseException.class,
                () -> MessageSignatureFields.parseSignatureInput(request));
    }


    @Test
    void testSignatureLabelDuplicateAcrossFieldLinesFails() {
        final BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        request.addHeader("Signature", "sig1=:AQID:");
        request.addHeader("Signature", "sig1=:BAUG:");
        Assertions.assertThrows(ParseException.class,
                () -> MessageSignatureFields.parseSignature(request));
    }

    @Test
    void testSignatureLabelDuplicateWithinOneFieldLineFails() {
        final BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        request.addHeader("Signature", "sig1=:AQID:, sig1=:BAUG:");
        Assertions.assertThrows(ParseException.class,
                () -> MessageSignatureFields.parseSignature(request));
    }

    @Test
    void testProgrammaticSignatureLabelUsesStructuredFieldKeyGrammar() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new MessageSignature("Sig1", new byte[] {1}));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new MessageSignatureInput("sig/1",
                        Arrays.asList(MessageSignatureComponent.derived("@method")),
                        StructuredFieldParameters.EMPTY));
    }

    @Test
    void testMatchingLabelsRejectsDuplicateProgrammaticLabels() throws Exception {
        final MessageSignatureInput input = new MessageSignatureInput("sig1",
                Arrays.asList(MessageSignatureComponent.derived("@method")), StructuredFieldParameters.EMPTY);
        final MessageSignature signature = new MessageSignature("sig1", new byte[] {1});
        Assertions.assertThrows(MessageSignatureException.class,
                () -> MessageSignatureFields.validateMatchingLabels(
                        Arrays.asList(input, input), Arrays.asList(signature, signature)));
    }

    @Test
    void testSignatureMustBeByteSequence() {
        final BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        request.addHeader("Signature", "sig1=\"not-bytes\"");
        Assertions.assertThrows(ParseException.class,
                () -> MessageSignatureFields.parseSignature(request));
    }

    @Test
    void testKnownSignatureParameterTypesAreValidated() {
        final BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        request.addHeader("Signature-Input", "sig1=(\"@method\");created=\"wrong\"");
        Assertions.assertThrows(ParseException.class,
                () -> MessageSignatureFields.parseSignatureInput(request));
    }

    @Test
    void testMatchingLabels() throws Exception {
        final MessageSignatureInput input = new MessageSignatureInput("sig1",
                Arrays.asList(MessageSignatureComponent.derived("@method")),
                StructuredFieldParameters.builder()
                        .put("created", StructuredFieldBareItem.ofInteger(1))
                        .build());
        MessageSignatureFields.validateMatchingLabels(
                Arrays.asList(input), Arrays.asList(new MessageSignature("sig1", new byte[] {1})));
        Assertions.assertThrows(MessageSignatureException.class,
                () -> MessageSignatureFields.validateMatchingLabels(
                        Arrays.asList(input), Arrays.asList(new MessageSignature("sig2", new byte[] {1}))));
    }

    @Test
    void testFieldComponentNamesMustBeLowerCaseWhenParsed() {
        final BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        request.addHeader("Signature-Input", "sig1=(\"Content-Type\")");
        Assertions.assertThrows(ParseException.class,
                () -> MessageSignatureFields.parseSignatureInput(request));
    }

    @Test
    void testMessageSignatureValueSemantics() {
        final MessageSignature first = new MessageSignature("sig1", new byte[] {1, 2, 3});
        final MessageSignature second = new MessageSignature("sig1", new byte[] {1, 2, 3});
        Assertions.assertEquals(first, second);
        Assertions.assertEquals(first.hashCode(), second.hashCode());
        Assertions.assertEquals("sig1=:AQID:", first.toString());
    }

    @Test
    void testMessageSignatureInputValueSemantics() {
        final StructuredFieldParameters parameters = StructuredFieldParameters.builder()
                .put("created", StructuredFieldBareItem.ofInteger(1618884475L))
                .build();
        final MessageSignatureInput first = new MessageSignatureInput("sig1", Arrays.asList(
                MessageSignatureComponent.derived("@method"),
                MessageSignatureComponent.field("content-digest")), parameters);
        final MessageSignatureInput second = new MessageSignatureInput("sig1", Arrays.asList(
                MessageSignatureComponent.derived("@method"),
                MessageSignatureComponent.field("content-digest")), parameters);
        Assertions.assertEquals(first, second);
        Assertions.assertEquals(first.hashCode(), second.hashCode());
        Assertions.assertEquals(
                "sig1=(\"@method\" \"content-digest\");created=1618884475", first.toString());
    }

}
