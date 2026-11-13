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
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link BufferedHttpEntity}.
 *
 */
class TestBufferedHttpEntity {

    @Test
    void testBufferingEntity() throws Exception {
        final byte[] bytes = "Message content".getBytes(StandardCharsets.US_ASCII);
        final BufferedHttpEntity entity = new BufferedHttpEntity(
                new InputStreamEntity(new ByteArrayInputStream(bytes), -1, null));
        Assertions.assertThat(entity.getContentLength()).isEqualTo(bytes.length);
        Assertions.assertThat(entity.isRepeatable()).isTrue();
        Assertions.assertThat(entity.isChunked()).isFalse();
        Assertions.assertThat(entity.isStreaming()).isFalse();

        // test if we can obtain contain multiple times
        Assertions.assertThat(entity.getContent()).isNotNull();
        Assertions.assertThat(entity.getContent()).isNotNull();
    }

    @Test
    void testWrappingEntity() throws Exception {
        final byte[] bytes = "Message content".getBytes(StandardCharsets.US_ASCII);
        final ByteArrayEntity httpentity = new ByteArrayEntity(bytes, null, true);
        try (final BufferedHttpEntity bufentity = new BufferedHttpEntity(httpentity)) {
            Assertions.assertThat(bufentity.getContentLength()).isEqualTo(bytes.length);
            Assertions.assertThat(bufentity.isRepeatable()).isTrue();
            Assertions.assertThat(bufentity.isChunked()).isTrue();
            Assertions.assertThat(bufentity.isStreaming()).isFalse();

            // test if we can obtain contain multiple times
            Assertions.assertThat(bufentity.getContent()).isNotNull();
            Assertions.assertThat(bufentity.getContent()).isNotNull();
        }
    }

    @Test
    void testIllegalConstructor() {
        Assertions.assertThatThrownBy(() ->
                        new BufferedHttpEntity(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testWriteToBuffered() throws Exception {
        final byte[] bytes = "Message content".getBytes(StandardCharsets.US_ASCII);
        final InputStreamEntity httpentity = new InputStreamEntity(new ByteArrayInputStream(bytes), -1, null);
        try (final BufferedHttpEntity bufentity = new BufferedHttpEntity(httpentity)) {

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bufentity.writeTo(out);
            byte[] bytes2 = out.toByteArray();
            Assertions.assertThat(bytes2).isNotNull();
            Assertions.assertThat(bytes2.length).isEqualTo(bytes.length);
            for (int i = 0; i < bytes.length; i++) {
                Assertions.assertThat(bytes2[i]).isEqualTo(bytes[i]);
            }

            out = new ByteArrayOutputStream();
            bufentity.writeTo(out);
            bytes2 = out.toByteArray();
            Assertions.assertThat(bytes2).isNotNull();
            Assertions.assertThat(bytes2.length).isEqualTo(bytes.length);
            for (int i = 0; i < bytes.length; i++) {
                Assertions.assertThat(bytes2[i]).isEqualTo(bytes[i]);
            }

            Assertions.assertThatThrownBy(() ->
                            bufentity.writeTo(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Test
    void testWriteToWrapped() throws Exception {
        final byte[] bytes = "Message content".getBytes(StandardCharsets.US_ASCII);
        final ByteArrayEntity httpentity = new ByteArrayEntity(bytes, null);
        try (final BufferedHttpEntity bufentity = new BufferedHttpEntity(httpentity)) {

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bufentity.writeTo(out);
            byte[] bytes2 = out.toByteArray();
            Assertions.assertThat(bytes2).isNotNull();
            Assertions.assertThat(bytes2.length).isEqualTo(bytes.length);
            for (int i = 0; i < bytes.length; i++) {
                Assertions.assertThat(bytes2[i]).isEqualTo(bytes[i]);
            }

            out = new ByteArrayOutputStream();
            bufentity.writeTo(out);
            bytes2 = out.toByteArray();
            Assertions.assertThat(bytes2).isNotNull();
            Assertions.assertThat(bytes2.length).isEqualTo(bytes.length);
            for (int i = 0; i < bytes.length; i++) {
                Assertions.assertThat(bytes2[i]).isEqualTo(bytes[i]);
            }

            Assertions.assertThatThrownBy(() ->
                            bufentity.writeTo(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

}
