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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.HeaderGroup;
import org.apache.hc.core5.http.structured.StructuredFieldDictionary;
import org.apache.hc.core5.http.structured.StructuredFieldHeaders;
import org.apache.hc.core5.http.structured.StructuredFieldInnerList;
import org.apache.hc.core5.http.structured.StructuredFieldItem;
import org.apache.hc.core5.http.structured.StructuredFieldList;

/**
 * Creates and consumes real HTTP fields that use the RFC 9651 data model.
 */
public final class StructuredFieldsExample {

    public static void main(final String... args) throws Exception {
        contentDigestRoundTrip();
        parsePriority();
        parseCacheStatus();
        parseSignatureInput();
    }

    /** RFC 9530 Content-Digest sender and receiver round trip. */
    private static void contentDigestRoundTrip() throws Exception {
        final byte[] body = "{\"message\":\"hello\"}".getBytes(StandardCharsets.UTF_8);
        final byte[] digest = MessageDigest.getInstance("SHA-256").digest(body);

        final StructuredFieldDictionary contentDigest = StructuredFieldDictionary.builder()
                .put("sha-256", StructuredFieldItem.ofByteSequence(digest))
                .build();
        final Header header = StructuredFieldHeaders.format("Content-Digest", contentDigest);
        System.out.println(header);

        final HeaderGroup receivedHeaders = new HeaderGroup();
        receivedHeaders.addHeader(header);
        final StructuredFieldItem receivedDigest = (StructuredFieldItem)
                StructuredFieldHeaders.parseDictionary(receivedHeaders, "Content-Digest").get("sha-256");
        final boolean valid = MessageDigest.isEqual(digest, receivedDigest.getBareItem().getByteSequenceValue());
        System.out.println("Digest valid: " + valid);
    }

    /** RFC 9218 Priority is a Dictionary Structured Field. */
    private static void parsePriority() throws Exception {
        final StructuredFieldDictionary priority = StructuredFieldHeaders.parseDictionary(
                new BasicHeader("Priority", "u=0, i"));
        final long urgency = ((StructuredFieldItem) priority.get("u")).getBareItem().getLongValue();
        final boolean incremental = ((StructuredFieldItem) priority.get("i"))
                .getBareItem().getBooleanValue();
        System.out.println("Priority urgency=" + urgency + ", incremental=" + incremental);
    }

    /** RFC 9211 Cache-Status is a List Structured Field. */
    private static void parseCacheStatus() throws Exception {
        final StructuredFieldList cacheStatus = StructuredFieldHeaders.parseList(
                new BasicHeader("Cache-Status", "ExampleCache;hit;ttl=376"));
        final StructuredFieldItem cache = (StructuredFieldItem) cacheStatus.get(0);
        System.out.println("Cache " + cache.getBareItem().getTextValue()
                + " hit=" + cache.getParameters().get("hit").getBooleanValue()
                + " ttl=" + cache.getParameters().get("ttl").getLongValue());
    }

    /** RFC 9421 Signature-Input is a Dictionary Structured Field. */
    private static void parseSignatureInput() throws Exception {
        final Header header = new BasicHeader("Signature-Input",
                "sig1=(\"@method\" \"@target-uri\" \"content-digest\")"
                        + ";created=1618884475;keyid=\"test-key-rsa-pss\"");
        final StructuredFieldInnerList signature = (StructuredFieldInnerList)
                StructuredFieldHeaders.parseDictionary(header).get("sig1");
        System.out.println("Signature covers " + signature.size()
                + " components using key " + signature.getParameters().get("keyid").getTextValue());
    }

    private StructuredFieldsExample() {
    }
}
