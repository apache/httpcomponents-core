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
package org.apache.hc.core5.http.examples;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.signature.MessageSignatureBaseBuilder;
import org.apache.hc.core5.http.signature.MessageSignatureComponent;
import org.apache.hc.core5.http.signature.MessageSignatureContext;
import org.apache.hc.core5.http.signature.MessageSignatureFields;
import org.apache.hc.core5.http.signature.MessageSignatureInput;
import org.apache.hc.core5.http.structured.StructuredFieldBareItem;
import org.apache.hc.core5.http.structured.StructuredFieldParameters;

/**
 * Builds RFC 9421 Signature-Input and the exact signature base from the RFC Appendix B request.
 * Deliberately stops before cryptography or key lookup.
 */
public final class MessageSignatureCanonicalizationExample {

    private MessageSignatureCanonicalizationExample() {
    }

    public static void main(final String[] args) throws Exception {
        final BasicHttpRequest request = new BasicHttpRequest(
                "POST", URI.create("https://example.com/foo?param=Value&Pet=dog"));
        request.addHeader("Content-Digest",
                "sha-512=:WZDPaVn/7XgHaAy8pmojAkGWoRx2UFChF41A2svX+TaPm+AbwAgBWnrIiYllu7BNNyealdVLvRwEmTHWXvJwew==:");

        final StructuredFieldParameters queryParameter = StructuredFieldParameters.builder()
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
                MessageSignatureComponent.create("@query-param", queryParameter)), signatureParameters);

        final Header signatureInput = MessageSignatureFields.formatSignatureInput(Collections.singletonList(input));
        final String signatureBase = new MessageSignatureBaseBuilder().build(
                input, MessageSignatureContext.builder(request).build());

        System.out.println(signatureInput);
        System.out.println();
        System.out.println(signatureBase);
    }

}
