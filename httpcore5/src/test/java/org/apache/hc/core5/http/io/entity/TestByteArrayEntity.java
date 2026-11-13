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
import java.nio.charset.StandardCharsets;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ByteArrayEntity}.
 *
 */
class TestByteArrayEntity {

    @Test
    void testBasics() throws Exception {
        final byte[] bytes = "Message content".getBytes(StandardCharsets.US_ASCII);
        try (final ByteArrayEntity entity = new ByteArrayEntity(bytes, null)) {

            Assertions.assertThat(entity.getContentLength()).isEqualTo(bytes.length);
            Assertions.assertThat(entity.getContent()).isNotNull();
            Assertions.assertThat(entity.isRepeatable()).isTrue();
            Assertions.assertThat(entity.isStreaming()).isFalse();
        }
    }

    @Test
    void testBasicOffLen() throws Exception {
        final byte[] bytes = "Message content".getBytes(StandardCharsets.US_ASCII);
        try (final ByteArrayEntity entity = new ByteArrayEntity(bytes, 8, 7, null)) {

            Assertions.assertThat(entity.getContentLength()).isEqualTo(7);
            Assertions.assertThat(entity.getContent()).isNotNull();
            Assertions.assertThat(entity.isRepeatable()).isTrue();
            Assertions.assertThat(entity.isStreaming()).isFalse();
        }
    }

    @Test
    void testIllegalConstructorNullByteArray() {
        Assertions.assertThatThrownBy(() ->
                new ByteArrayEntity(null, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testIllegalConstructorBadLen() {
        final byte[] bytes = "Message content".getBytes(StandardCharsets.US_ASCII);
        Assertions.assertThatThrownBy(() ->
                new ByteArrayEntity(bytes, 0, bytes.length + 1, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testIllegalConstructorBadOff1() {
        final byte[] bytes = "Message content".getBytes(StandardCharsets.US_ASCII);
        Assertions.assertThatThrownBy(() ->
                new ByteArrayEntity(bytes, -1, bytes.length, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testIllegalConstructorBadOff2() {
        final byte[] bytes = "Message content".getBytes(StandardCharsets.US_ASCII);
        Assertions.assertThatThrownBy(() ->
                new ByteArrayEntity(bytes, bytes.length + 1, bytes.length, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testWriteTo() throws Exception {
        final byte[] bytes = "Message content".getBytes(StandardCharsets.US_ASCII);
        try (final ByteArrayEntity entity = new ByteArrayEntity(bytes, null)) {

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            entity.writeTo(out);
            byte[] bytes2 = out.toByteArray();
            Assertions.assertThat(bytes2).isNotNull();
            Assertions.assertThat(bytes2.length).isEqualTo(bytes.length);
            for (int i = 0; i < bytes.length; i++) {
                Assertions.assertThat(bytes2[i]).isEqualTo(bytes[i]);
            }

            out = new ByteArrayOutputStream();
            entity.writeTo(out);
            bytes2 = out.toByteArray();
            Assertions.assertThat(bytes2).isNotNull();
            Assertions.assertThat(bytes2.length).isEqualTo(bytes.length);
            for (int i = 0; i < bytes.length; i++) {
                Assertions.assertThat(bytes2[i]).isEqualTo(bytes[i]);
            }

            Assertions.assertThatThrownBy(() ->
                            entity.writeTo(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Test
    void testWriteToOffLen() throws Exception {
        final byte[] bytes = "Message content".getBytes(StandardCharsets.US_ASCII);
        final int off = 8;
        final int len = 7;
        try (final ByteArrayEntity entity = new ByteArrayEntity(bytes, off, len, null)) {

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            entity.writeTo(out);
            byte[] bytes2 = out.toByteArray();
            Assertions.assertThat(bytes2).isNotNull();
            Assertions.assertThat(bytes2.length).isEqualTo(len);
            for (int i = 0; i < len; i++) {
                Assertions.assertThat(bytes2[i]).isEqualTo(bytes[i + off]);
            }

            out = new ByteArrayOutputStream();
            entity.writeTo(out);
            bytes2 = out.toByteArray();
            Assertions.assertThat(bytes2).isNotNull();
            Assertions.assertThat(bytes2.length).isEqualTo(len);
            for (int i = 0; i < len; i++) {
                Assertions.assertThat(bytes2[i]).isEqualTo(bytes[i + off]);
            }

            Assertions.assertThatThrownBy(() ->
                            entity.writeTo(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

}
