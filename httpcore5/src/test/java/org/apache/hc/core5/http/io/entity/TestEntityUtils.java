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

package org.apache.hc.core5.http.io.entity;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.net.WWWFormCodec;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link EntityUtils}.
 *
 */
class TestEntityUtils {

    @Test
    void testNullEntityToByteArray() {
        Assertions.assertThatThrownBy(() ->
                EntityUtils.toByteArray(null, 1024)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testMaxIntContentToByteArray() {
        final byte[] content = "Message content".getBytes(StandardCharsets.US_ASCII);
        final BasicHttpEntity entity = new BasicHttpEntity(new ByteArrayInputStream(content),
                Integer.MAX_VALUE + 100L, ContentType.TEXT_PLAIN.withCharset(StandardCharsets.US_ASCII));
        Assertions.assertThatThrownBy(() ->
                EntityUtils.toByteArray(entity, 1024)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testUnknownLengthContentToByteArray() throws Exception {
        final byte[] bytes = "Message content".getBytes(StandardCharsets.US_ASCII);
        final BasicHttpEntity entity = new BasicHttpEntity(new ByteArrayInputStream(bytes), -1, null);
        final byte[] bytes2 = EntityUtils.toByteArray(entity, 1024);
        Assertions.assertThat(bytes2).isNotNull();
        Assertions.assertThat(bytes2.length).isEqualTo(bytes.length);
        for (int i = 0; i < bytes.length; i++) {
            Assertions.assertThat(bytes2[i]).isEqualTo(bytes[i]);
        }
    }

    @Test
    void testKnownLengthContentToByteArray() throws Exception {
        final byte[] bytes = "Message content".getBytes(StandardCharsets.US_ASCII);
        final BasicHttpEntity entity = new BasicHttpEntity(new ByteArrayInputStream(bytes), bytes.length, null);
        final byte[] bytes2 = EntityUtils.toByteArray(entity, 1024);
        Assertions.assertThat(bytes2).isNotNull();
        Assertions.assertThat(bytes2.length).isEqualTo(bytes.length);
        for (int i = 0; i < bytes.length; i++) {
            Assertions.assertThat(bytes2[i]).isEqualTo(bytes[i]);
        }
    }

    @Test
    void testNullEntityToString() {
        Assertions.assertThatThrownBy(() ->
                        EntityUtils.toString(null, 1024))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testMaxIntContentToString() {
        final byte[] content = "Message content".getBytes(StandardCharsets.US_ASCII);
        final BasicHttpEntity entity = new BasicHttpEntity(new ByteArrayInputStream(content),
                Integer.MAX_VALUE + 100L, ContentType.TEXT_PLAIN.withCharset(StandardCharsets.US_ASCII));
        Assertions.assertThatThrownBy(() ->
                EntityUtils.toString(entity, "US-ASCII")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testUnknownLengthContentToString() throws Exception {
        final byte[] bytes = "Message content".getBytes(StandardCharsets.US_ASCII);
        final BasicHttpEntity entity = new BasicHttpEntity(new ByteArrayInputStream(bytes), -1, null);
        final String s = EntityUtils.toString(entity, "US-ASCII");
        Assertions.assertThat(s).isEqualTo("Message content");
    }

    @Test
    void testKnownLengthContentToString() throws Exception {
        final byte[] bytes = "Message content".getBytes(StandardCharsets.US_ASCII);
        final BasicHttpEntity entity = new BasicHttpEntity(new ByteArrayInputStream(bytes), bytes.length,
                ContentType.TEXT_PLAIN.withCharset(StandardCharsets.US_ASCII));
        final String s = EntityUtils.toString(entity, StandardCharsets.US_ASCII, 1024);
        Assertions.assertThat(s).isEqualTo("Message content");
    }

    static final int SWISS_GERMAN_HELLO [] = {
        0x47, 0x72, 0xFC, 0x65, 0x7A, 0x69, 0x5F, 0x7A, 0xE4, 0x6D, 0xE4
    };

    static final int RUSSIAN_HELLO [] = {
        0x412, 0x441, 0x435, 0x43C, 0x5F, 0x43F, 0x440, 0x438,
        0x432, 0x435, 0x442
    };

    private static String constructString(final int [] unicodeChars) {
        final StringBuilder buffer = new StringBuilder();
        if (unicodeChars != null) {
            for (final int unicodeChar : unicodeChars) {
                buffer.append((char)unicodeChar);
            }
        }
        return buffer.toString();
    }

    @Test
    void testNoCharsetContentToString() {
        final String content = constructString(SWISS_GERMAN_HELLO);
        final byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        final BasicHttpEntity entity = new BasicHttpEntity(new ByteArrayInputStream(bytes), ContentType.TEXT_PLAIN);
        Assertions.assertThatCode(() -> EntityUtils.toString(entity, 1024)).doesNotThrowAnyException();
    }

    @Test
    void testDefaultCharsetContentToString() throws Exception {
        final String content = constructString(RUSSIAN_HELLO);
        final byte[] bytes = content.getBytes(Charset.forName("KOI8-R"));
        final BasicHttpEntity entity = new BasicHttpEntity(new ByteArrayInputStream(bytes),
                ContentType.parse("text/plain"));
        final String s = EntityUtils.toString(entity, Charset.forName("KOI8-R"), 1024);
        Assertions.assertThat(s).isEqualTo(content);
    }

    @Test
    void testContentWithContentTypeToString() throws Exception {
        final String content = constructString(RUSSIAN_HELLO);
        final byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        final BasicHttpEntity entity = new BasicHttpEntity(new ByteArrayInputStream(bytes),
                ContentType.TEXT_PLAIN.withCharset(StandardCharsets.UTF_8));
        final String s = EntityUtils.toString(entity, "ISO-8859-1");
        Assertions.assertThat(s).isEqualTo(content);
    }

    @Test
    void testContentWithInvalidContentTypeToString() throws Exception {
        final String content = constructString(RUSSIAN_HELLO);
        final byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        final HttpEntity entity = new AbstractHttpEntity("text/plain; charset=nosuchcharset", null) {

            @Override
            public InputStream getContent() throws IOException, UnsupportedOperationException {
                return new ByteArrayInputStream(bytes);
            }

            @Override
            public boolean isStreaming() {
                return false;
            }

            @Override
            public long getContentLength() {
                return bytes.length;
            }

            @Override
            public void close() throws IOException {
            }

        };
        final String s = EntityUtils.toString(entity, "UTF-8");
        Assertions.assertThat(s).isEqualTo(content);
    }

    private static void assertNameValuePair (
            final NameValuePair parameter,
            final String expectedName,
            final String expectedValue) {
        Assertions.assertThat(expectedName).isEqualTo(parameter.getName());
        Assertions.assertThat(expectedValue).isEqualTo(parameter.getValue());
    }

    @Test
    void testParseEntity() throws Exception {
        final StringEntity entity1 = new StringEntity("Name1=Value1", ContentType.APPLICATION_FORM_URLENCODED);
        final List<NameValuePair> result = EntityUtils.parse(entity1, 1024);
        Assertions.assertThat(result.size()).isEqualTo(1);
        assertNameValuePair(result.get(0), "Name1", "Value1");

        final StringEntity entity2 = new StringEntity("Name1=Value1", ContentType.parse("text/test"));
        Assertions.assertThat(EntityUtils.parse(entity2, 1024).isEmpty()).isTrue();
    }

    @Test
    void testParseUTF8Entity() throws Exception {
        final String ru_hello = constructString(RUSSIAN_HELLO);
        final String ch_hello = constructString(SWISS_GERMAN_HELLO);
        final List<NameValuePair> parameters = new ArrayList<>();
        parameters.add(new BasicNameValuePair("russian", ru_hello));
        parameters.add(new BasicNameValuePair("swiss", ch_hello));

        final String s = WWWFormCodec.format(parameters, StandardCharsets.UTF_8);

        Assertions.assertThat(s).isEqualTo("russian=%D0%92%D1%81%D0%B5%D0%BC_%D0%BF%D1%80%D0%B8%D0%B2%D0%B5%D1%82" +
                "&swiss=Gr%C3%BCezi_z%C3%A4m%C3%A4");
        final StringEntity entity = new StringEntity(s,
                ContentType.APPLICATION_FORM_URLENCODED.withCharset(StandardCharsets.UTF_8));
        final List<NameValuePair> result = EntityUtils.parse(entity, 1024);
        Assertions.assertThat(result.size()).isEqualTo(2);
        assertNameValuePair(result.get(0), "russian", ru_hello);
        assertNameValuePair(result.get(1), "swiss", ch_hello);
    }

    @Test
    void testByteArrayMaxResultLength() throws IOException {
        final byte[] allBytes = "Message content".getBytes(StandardCharsets.US_ASCII);
        final Map<Integer, byte[]> testCases = new HashMap<>();
        testCases.put(0, new byte[]{});
        testCases.put(2, Arrays.copyOfRange(allBytes, 0, 2));
        testCases.put(allBytes.length, allBytes);
        testCases.put(Integer.MAX_VALUE, allBytes);

        for (final Map.Entry<Integer, byte[]> tc : testCases.entrySet()) {
            final BasicHttpEntity entity = new BasicHttpEntity(new ByteArrayInputStream(allBytes), allBytes.length, null);

            final byte[] bytes = EntityUtils.toByteArray(entity, tc.getKey());
            final byte[] expectedBytes = tc.getValue();
            Assertions.assertThat(bytes).isNotNull();
            Assertions.assertThat(bytes.length).isEqualTo(expectedBytes.length);
            for (int i = 0; i < expectedBytes.length; i++) {
                Assertions.assertThat(bytes[i]).isEqualTo(expectedBytes[i]);
            }
        }
    }

    @Test
    void testByteArrayMaxResultLengthWithNoContentLength() throws IOException {
        final byte b = 'b';
        final byte[] allBytes = new byte[5000];
        Arrays.fill(allBytes, b);
        final Map<Integer, byte[]> testCases = new HashMap<>();
        testCases.put(0, new byte[]{});
        testCases.put(2, Arrays.copyOfRange(allBytes, 0, 2));
        testCases.put(allBytes.length, allBytes);
        testCases.put(Integer.MAX_VALUE, allBytes);

        for (final Map.Entry<Integer, byte[]> tc : testCases.entrySet()) {
            final BasicHttpEntity entity = new BasicHttpEntity(new ByteArrayInputStream(allBytes), null);

            final byte[] bytes = EntityUtils.toByteArray(entity, tc.getKey());
            final byte[] expectedBytes = tc.getValue();
            Assertions.assertThat(bytes).isNotNull();
            Assertions.assertThat(bytes.length).isEqualTo(expectedBytes.length);
            for (int i = 0; i < expectedBytes.length; i++) {
                Assertions.assertThat(bytes[i]).isEqualTo(expectedBytes[i]);
            }
        }
    }

    @Test
    void testStringMaxResultLength() throws IOException, ParseException {
        final String allMessage = "Message content";
        final byte[] allBytes = allMessage.getBytes(StandardCharsets.US_ASCII);
        final Map<Integer, String> testCases = new HashMap<>();
        testCases.put(7, allMessage.substring(0, 7));
        testCases.put(allMessage.length(), allMessage);
        testCases.put(Integer.MAX_VALUE, allMessage);

        for (final Map.Entry<Integer, String> tc : testCases.entrySet()) {
            final BasicHttpEntity entity = new BasicHttpEntity(new ByteArrayInputStream(allBytes), allBytes.length, null);
            final String string = EntityUtils.toString(entity, StandardCharsets.US_ASCII, tc.getKey());
            final String expectedString = tc.getValue();
            Assertions.assertThat(string).isNotNull();
            Assertions.assertThat(string).isEqualTo(expectedString);
        }
    }

}
