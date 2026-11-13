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

import org.apache.hc.core5.http.ContentType;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link BasicHttpEntity}.
 *
 */
class TestBasicHttpEntity {

    @Test
    void testBasics() {
        final byte[] bytes = "Message content".getBytes(StandardCharsets.US_ASCII);
        final BasicHttpEntity httpentity = new BasicHttpEntity(new ByteArrayInputStream(bytes), bytes.length, null);
        Assertions.assertThat(httpentity.getContentLength()).isEqualTo(bytes.length);
        // always false
        Assertions.assertThat(httpentity.isRepeatable()).isFalse();
        // always true
        Assertions.assertThat(httpentity.isStreaming()).isTrue();
    }

    @Test
    void testConstructorNullContent() {
        // BasicHttpEntity(InputStream, ContentType)
        Assertions.assertThatThrownBy(() ->
                        new BasicHttpEntity(null, ContentType.APPLICATION_ATOM_XML))
                .isInstanceOf(NullPointerException.class);
        Assertions.assertThatThrownBy(() ->
                        new BasicHttpEntity(null, null))
                .isInstanceOf(NullPointerException.class);
        // BasicHttpEntity(InputStream, ContentType, boolean)
        Assertions.assertThatThrownBy(() ->
                        new BasicHttpEntity(null, ContentType.APPLICATION_ATOM_XML, false))
                .isInstanceOf(NullPointerException.class);
        Assertions.assertThatThrownBy(() ->
                        new BasicHttpEntity(null, null, false))
                .isInstanceOf(NullPointerException.class);
        // BasicHttpEntity(InputStream, ContentType, String)
        Assertions.assertThatThrownBy(() ->
                        new BasicHttpEntity(null, ContentType.APPLICATION_ATOM_XML, ""))
                .isInstanceOf(NullPointerException.class);
        Assertions.assertThatThrownBy(() ->
                        new BasicHttpEntity(null, null, ""))
                .isInstanceOf(NullPointerException.class);
        // BasicHttpEntity(InputStream, long, ContentType)
        Assertions.assertThatThrownBy(() ->
                        new BasicHttpEntity(null, 0, ContentType.APPLICATION_ATOM_XML))
                .isInstanceOf(NullPointerException.class);
        Assertions.assertThatThrownBy(() ->
                        new BasicHttpEntity(null, 0, null))
                .isInstanceOf(NullPointerException.class);
        // BasicHttpEntity(InputStream, long, ContentType, String)
        Assertions.assertThatThrownBy(() ->
                        new BasicHttpEntity(null, 0, ContentType.APPLICATION_ATOM_XML, ""))
                .isInstanceOf(NullPointerException.class);
        Assertions.assertThatThrownBy(() ->
                        new BasicHttpEntity(null, 0, null, ""))
                .isInstanceOf(NullPointerException.class);
        // BasicHttpEntity(InputStream, long, ContentType, String, boolean)
        Assertions.assertThatThrownBy(() ->
                        new BasicHttpEntity(null, 0, ContentType.APPLICATION_ATOM_XML, "", false))
                .isInstanceOf(NullPointerException.class);
        Assertions.assertThatThrownBy(() ->
                        new BasicHttpEntity(null, 0, null, "", false))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testToString() throws Exception {
        try (final BasicHttpEntity httpentity = new BasicHttpEntity(EmptyInputStream.INSTANCE, 10,
                ContentType.parseLenient("blah"), "yada", true)) {
            Assertions.assertThat(httpentity.toString()).isEqualTo("[Entity-Class: BasicHttpEntity, Content-Type: blah, Content-Encoding: yada, chunked: true]");
        }
    }

    @Test
    void testWriteTo() throws Exception {
        final byte[] bytes = "Message content".getBytes(StandardCharsets.US_ASCII);
        final BasicHttpEntity httpentity = new BasicHttpEntity(new ByteArrayInputStream(bytes), bytes.length,
                ContentType.TEXT_PLAIN);
        // always false
        Assertions.assertThat(httpentity.isRepeatable()).isFalse();
        // always true
        Assertions.assertThat(httpentity.isStreaming()).isTrue();
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        httpentity.writeTo(out);
        final byte[] bytes2 = out.toByteArray();
        Assertions.assertThat(bytes2).isNotNull();
        Assertions.assertThat(bytes2.length).isEqualTo(bytes.length);
        for (int i = 0; i < bytes.length; i++) {
            Assertions.assertThat(bytes2[i]).isEqualTo(bytes[i]);
        }
    }

}
