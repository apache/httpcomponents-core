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

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.http.ContentType;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link StringEntity}.
 */
class TestStringEntity {

    @Test
    void testBasics() throws Exception {
        final String s = "Message content";
        try (final StringEntity httpentity = new StringEntity(s, ContentType.TEXT_PLAIN)) {

            final byte[] bytes = s.getBytes(StandardCharsets.US_ASCII);
            Assertions.assertThat(httpentity.getContentLength()).isEqualTo(bytes.length);
            Assertions.assertThat(httpentity.getContent()).isNotNull();
            Assertions.assertThat(httpentity.isRepeatable()).isTrue();
            Assertions.assertThat(httpentity.isStreaming()).isFalse();
        }
    }

    @Test
    void testNullConstructor() {
        Assertions.assertThatThrownBy(() ->
                        new StringEntity(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testDefaultContent() throws Exception {
        final String s = "Message content";
        StringEntity httpentity = new StringEntity(s, ContentType.create("text/csv", "ANSI_X3.4-1968"));
        Assertions.assertThat(httpentity.getContentType()).isEqualTo("text/csv; charset=US-ASCII");
        httpentity = new StringEntity(s, StandardCharsets.US_ASCII);
        Assertions.assertThat(httpentity.getContentType()).isEqualTo("text/plain; charset=US-ASCII");
        httpentity = new StringEntity(s);
        Assertions.assertThat(httpentity.getContentType()).isEqualTo("text/plain; charset=UTF-8");
    }

    private static String constructString(final int [] unicodeChars) {
        final StringBuilder buffer = new StringBuilder();
        if (unicodeChars != null) {
            for (final int unicodeChar : unicodeChars) {
                buffer.append((char)unicodeChar);
            }
        }
        return buffer.toString();
    }

    static final int SWISS_GERMAN_HELLO [] = {
            0x47, 0x72, 0xFC, 0x65, 0x7A, 0x69, 0x5F, 0x7A, 0xE4, 0x6D, 0xE4
        };

    @Test
    void testNullCharset() throws Exception {
        final String s = constructString(SWISS_GERMAN_HELLO);
        StringEntity httpentity = new StringEntity(s, ContentType.create("text/plain", (Charset) null));
        Assertions.assertThat(httpentity.getContentType()).isNotNull();
        Assertions.assertThat(httpentity.getContentType()).isEqualTo("text/plain");
        Assertions.assertThat(EntityUtils.toString(httpentity, 1024)).isEqualTo(s);
        httpentity = new StringEntity(s, (Charset) null);
        Assertions.assertThat(httpentity.getContentType()).isNotNull();
        Assertions.assertThat(httpentity.getContentType()).isEqualTo("text/plain");
        Assertions.assertThat(EntityUtils.toString(httpentity, 1024)).isEqualTo(s);
    }

    @Test
    void testWriteTo() throws Exception {
        final String s = "Message content";
        final byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        try (final StringEntity httpentity = new StringEntity(s)) {

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            httpentity.writeTo(out);
            byte[] bytes2 = out.toByteArray();
            Assertions.assertThat(bytes2).isNotNull();
            Assertions.assertThat(bytes2.length).isEqualTo(bytes.length);
            for (int i = 0; i < bytes.length; i++) {
                Assertions.assertThat(bytes2[i]).isEqualTo(bytes[i]);
            }

            out = new ByteArrayOutputStream();
            httpentity.writeTo(out);
            bytes2 = out.toByteArray();
            Assertions.assertThat(bytes2).isNotNull();
            Assertions.assertThat(bytes2.length).isEqualTo(bytes.length);
            for (int i = 0; i < bytes.length; i++) {
                Assertions.assertThat(bytes2[i]).isEqualTo(bytes[i]);
            }

            Assertions.assertThatThrownBy(() ->
                            httpentity.writeTo(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

}
