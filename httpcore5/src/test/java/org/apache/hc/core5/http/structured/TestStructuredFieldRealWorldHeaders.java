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

package org.apache.hc.core5.http.structured;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.message.BasicHeader;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TestStructuredFieldRealWorldHeaders {

    @Test
    void testRfc9218Priority() throws Exception {
        final StructuredFieldDictionary priority = StructuredFieldHeaders.parseDictionary(
                new BasicHeader("Priority", "u=0, i"));
        Assertions.assertThat(item(priority, "u").getBareItem().getLongValue()).isEqualTo(0L);
        Assertions.assertThat(item(priority, "i").getBareItem().getBooleanValue()).isTrue();
    }

    @Test
    void testRfc9530ContentDigest() throws Exception {
        final byte[] content = "{\"hello\":\"world\"}\n".getBytes(StandardCharsets.UTF_8);
        final byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
        final StructuredFieldDictionary value = StructuredFieldDictionary.builder()
                .put("sha-256", StructuredFieldItem.ofByteSequence(digest))
                .build();
        final Header header = StructuredFieldHeaders.format("Content-Digest", value);
        final byte[] parsed = item(StructuredFieldHeaders.parseDictionary(header), "sha-256")
                .getBareItem().getByteSequenceValue();
        Assertions.assertThat(MessageDigest.isEqual(digest, parsed)).isTrue();
    }

    @Test
    void testRfc9211CacheStatus() throws Exception {
        final StructuredFieldList status = StructuredFieldHeaders.parseList(
                new BasicHeader("Cache-Status", "ExampleCache;hit;ttl=376"));
        final StructuredFieldItem cache = (StructuredFieldItem) status.get(0);
        Assertions.assertThat(cache.getBareItem().getTextValue()).isEqualTo("ExampleCache");
        Assertions.assertThat(cache.getParameters().get("hit").getBooleanValue()).isTrue();
        Assertions.assertThat(cache.getParameters().get("ttl").getLongValue()).isEqualTo(376L);
    }

    @Test
    void testRfc9209ProxyStatus() throws Exception {
        final StructuredFieldList status = StructuredFieldHeaders.parseList(new BasicHeader(
                "Proxy-Status", "ExampleCDN;error=connection_timeout;received-status=504"));
        final StructuredFieldItem proxy = (StructuredFieldItem) status.get(0);
        Assertions.assertThat(proxy.getParameters().get("error").getTextValue()).isEqualTo("connection_timeout");
        Assertions.assertThat(proxy.getParameters().get("received-status").getLongValue()).isEqualTo(504L);
    }

    @Test
    void testRfc9421SignatureInput() throws Exception {
        final StructuredFieldDictionary signatureInput = StructuredFieldHeaders.parseDictionary(new BasicHeader(
                "Signature-Input", "sig1=(\"@method\" \"@target-uri\" \"content-digest\")"
                        + ";created=1618884475;keyid=\"test-key-rsa-pss\""));
        final StructuredFieldInnerList signature = (StructuredFieldInnerList) signatureInput.get("sig1");
        Assertions.assertThat(signature.size()).isEqualTo(3);
        Assertions.assertThat(signature.get(0).getBareItem().getTextValue()).isEqualTo("@method");
        Assertions.assertThat(signature.getParameters().get("created").getLongValue()).isEqualTo(1618884475L);
        Assertions.assertThat(signature.getParameters().get("keyid").getTextValue()).isEqualTo("test-key-rsa-pss");
    }

    private static StructuredFieldItem item(final StructuredFieldDictionary dictionary, final String key) {
        return (StructuredFieldItem) dictionary.get(key);
    }
}
