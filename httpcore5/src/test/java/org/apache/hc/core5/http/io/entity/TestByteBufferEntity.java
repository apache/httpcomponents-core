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
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ByteBufferEntity}.
 *
 */
class TestByteBufferEntity {

    @Test
    void testBasics() throws Exception {
        final ByteBuffer bytes = ByteBuffer.wrap("Message content".getBytes(StandardCharsets.US_ASCII));
        try (final ByteBufferEntity httpentity = new ByteBufferEntity(bytes, null)) {

            Assertions.assertThat(httpentity.getContentLength()).isEqualTo(bytes.capacity());
            Assertions.assertThat(httpentity.getContent()).isNotNull();
            Assertions.assertThat(httpentity.isRepeatable()).isFalse();
            Assertions.assertThat(httpentity.isStreaming()).isFalse();
        }
    }


    @Test
    void testWriteTo() throws Exception {
        final ByteBuffer bytes = ByteBuffer.wrap("Message content".getBytes(StandardCharsets.US_ASCII));
        try (final ByteBufferEntity httpentity = new ByteBufferEntity(bytes, null)) {

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            httpentity.writeTo(out);
            byte[] bytes2 = out.toByteArray();
            Assertions.assertThat(bytes2).isNotNull();
            Assertions.assertThat(bytes2.length).isEqualTo(bytes.capacity());
            bytes.position(0);
            for (int i = 0; i < bytes2.length; i++) {
                Assertions.assertThat(bytes2[i]).isEqualTo(bytes.get(i));
            }

            out = new ByteArrayOutputStream();
            httpentity.writeTo(out);
            bytes2 = out.toByteArray();
            Assertions.assertThat(bytes2).isNotNull();
            Assertions.assertThat(bytes2.length).isEqualTo(bytes.capacity());
            bytes.position(0);
            for (int i = 0; i < bytes.capacity(); i++) {
                Assertions.assertThat(bytes2[i]).isEqualTo(bytes.get(i));
            }

            Assertions.assertThatThrownBy(() ->
                            httpentity.writeTo(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
