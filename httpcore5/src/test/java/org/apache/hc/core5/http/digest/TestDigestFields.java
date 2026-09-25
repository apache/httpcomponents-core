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
package org.apache.hc.core5.http.digest;

import java.util.Base64;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.structured.StructuredFieldBareItem;
import org.apache.hc.core5.http.structured.StructuredFieldDictionary;
import org.apache.hc.core5.http.structured.StructuredFieldItem;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestDigestFields {

    @Test
    void testParseContentDigestRfcExample() throws Exception {
        final String encoded = "RK/0qy18MlBSVnWgjwz6lZEWjP/lF5HF9bvEF8FabDg=";
        final Header header = new BasicHeader(HttpHeaders.CONTENT_DIGEST, "sha-256=:" + encoded + ":");

        final StructuredFieldDictionary dictionary = DigestFields.parseDigest(header);

        final StructuredFieldItem item = (StructuredFieldItem) dictionary.get("sha-256");
        Assertions.assertNotNull(item);
        Assertions.assertArrayEquals(
                Base64.getDecoder().decode(encoded),
                item.getBareItem().getByteSequenceValue());
    }

    @Test
    void testParseMultipleDigestAlgorithms() throws Exception {
        final Header header = new BasicHeader(
                HttpHeaders.CONTENT_DIGEST,
                "sha-256=:AQID:, sha-512=:BAUG:");

        final StructuredFieldDictionary dictionary = DigestFields.parseDigest(header);

        Assertions.assertEquals(2, dictionary.size());
        Assertions.assertArrayEquals(
                new byte[]{1, 2, 3},
                ((StructuredFieldItem) dictionary.get("sha-256")).getBareItem().getByteSequenceValue());
        Assertions.assertArrayEquals(
                new byte[]{4, 5, 6},
                ((StructuredFieldItem) dictionary.get("sha-512")).getBareItem().getByteSequenceValue());
    }

    @Test
    void testParseDigestAcrossMultipleFieldLines() throws Exception {
        final BasicHttpResponse response = new BasicHttpResponse(HttpStatus.SC_OK);
        response.addHeader(HttpHeaders.REPR_DIGEST, "sha-256=:AQID:");
        response.addHeader(HttpHeaders.REPR_DIGEST, "sha-512=:BAUG:");

        final StructuredFieldDictionary dictionary = DigestFields.parseDigest(response, HttpHeaders.REPR_DIGEST);

        Assertions.assertEquals(2, dictionary.size());
        Assertions.assertNotNull(dictionary.get("sha-256"));
        Assertions.assertNotNull(dictionary.get("sha-512"));
    }

    @Test
    void testParseDigestAcceptsUnknownAlgorithm() throws Exception {
        final Header header = new BasicHeader(HttpHeaders.CONTENT_DIGEST, "example-algorithm=:AQID:");

        final StructuredFieldDictionary dictionary = DigestFields.parseDigest(header);

        Assertions.assertNotNull(dictionary.get("example-algorithm"));
    }

    @Test
    void testParseDigestRejectsNonByteSequence() {
        final Header header = new BasicHeader(HttpHeaders.CONTENT_DIGEST, "sha-256=123");

        Assertions.assertThrows(ParseException.class, () -> DigestFields.parseDigest(header));
    }

    @Test
    void testParseDigestRejectsInnerList() {
        final Header header = new BasicHeader(HttpHeaders.CONTENT_DIGEST, "sha-256=(:AQID:)");

        Assertions.assertThrows(ParseException.class, () -> DigestFields.parseDigest(header));
    }

    @Test
    void testParseWantDigestRfcExample() throws Exception {
        final Header header = new BasicHeader(
                HttpHeaders.WANT_REPR_DIGEST,
                "sha-512=3, sha-256=10, unixsum=0");

        final StructuredFieldDictionary dictionary = DigestFields.parsePreferences(header);

        Assertions.assertEquals(3L,
                ((StructuredFieldItem) dictionary.get("sha-512")).getBareItem().getLongValue());
        Assertions.assertEquals(10L,
                ((StructuredFieldItem) dictionary.get("sha-256")).getBareItem().getLongValue());
        Assertions.assertEquals(0L,
                ((StructuredFieldItem) dictionary.get("unixsum")).getBareItem().getLongValue());
    }

    @Test
    void testParseWantDigestRejectsBooleanShorthand() {
        final Header header = new BasicHeader(HttpHeaders.WANT_CONTENT_DIGEST, "sha-256");

        Assertions.assertThrows(ParseException.class, () -> DigestFields.parsePreferences(header));
    }

    @Test
    void testParseWantDigestRejectsNegativePreference() {
        final Header header = new BasicHeader(HttpHeaders.WANT_CONTENT_DIGEST, "sha-256=-1");

        Assertions.assertThrows(ParseException.class, () -> DigestFields.parsePreferences(header));
    }

    @Test
    void testParseWantDigestRejectsPreferenceGreaterThanTen() {
        final Header header = new BasicHeader(HttpHeaders.WANT_CONTENT_DIGEST, "sha-256=11");

        Assertions.assertThrows(ParseException.class, () -> DigestFields.parsePreferences(header));
    }

    @Test
    void testFormatDigest() {
        final StructuredFieldDictionary dictionary = StructuredFieldDictionary.builder()
                .put("sha-256", StructuredFieldBareItem.ofByteSequence(new byte[]{1, 2, 3}))
                .put("sha-512", StructuredFieldBareItem.ofByteSequence(new byte[]{4, 5, 6}))
                .build();

        final Header header = DigestFields.formatDigest(HttpHeaders.CONTENT_DIGEST, dictionary);

        Assertions.assertEquals(HttpHeaders.CONTENT_DIGEST, header.getName());
        Assertions.assertEquals("sha-256=:AQID:, sha-512=:BAUG:", header.getValue());
    }

    @Test
    void testFormatDigestRejectsInvalidDictionary() {
        final StructuredFieldDictionary dictionary = StructuredFieldDictionary.builder()
                .put("sha-256", StructuredFieldBareItem.ofInteger(1))
                .build();

        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> DigestFields.formatDigest(HttpHeaders.CONTENT_DIGEST, dictionary));
    }

    @Test
    void testFormatPreferences() {
        final StructuredFieldDictionary dictionary = StructuredFieldDictionary.builder()
                .put("sha-512", StructuredFieldBareItem.ofInteger(3))
                .put("sha-256", StructuredFieldBareItem.ofInteger(10))
                .put("unixsum", StructuredFieldBareItem.ofInteger(0))
                .build();

        final Header header = DigestFields.formatPreferences(HttpHeaders.WANT_REPR_DIGEST, dictionary);

        Assertions.assertEquals(HttpHeaders.WANT_REPR_DIGEST, header.getName());
        Assertions.assertEquals("sha-512=3, sha-256=10, unixsum=0", header.getValue());
    }

    @Test
    void testFormatPreferencesRejectsInvalidDictionary() {
        final StructuredFieldDictionary dictionary = StructuredFieldDictionary.builder()
                .put("sha-256", StructuredFieldBareItem.ofInteger(11))
                .build();

        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> DigestFields.formatPreferences(HttpHeaders.WANT_CONTENT_DIGEST, dictionary));
    }

    @Test
    void testParametersArePreserved() throws Exception {
        final Header header = new BasicHeader(HttpHeaders.CONTENT_DIGEST, "sha-256=:AQID:;foo=bar");

        final StructuredFieldDictionary dictionary = DigestFields.parseDigest(header);
        final Header formatted = DigestFields.formatDigest(HttpHeaders.CONTENT_DIGEST, dictionary);

        Assertions.assertEquals("sha-256=:AQID:;foo=bar", formatted.getValue());
    }

    @Test
    void testMissingFieldProducesEmptyDictionary() throws Exception {
        final BasicHttpResponse response = new BasicHttpResponse(HttpStatus.SC_OK);

        final StructuredFieldDictionary dictionary = DigestFields.parseDigest(response, HttpHeaders.CONTENT_DIGEST);

        Assertions.assertTrue(dictionary.isEmpty());
    }
}
