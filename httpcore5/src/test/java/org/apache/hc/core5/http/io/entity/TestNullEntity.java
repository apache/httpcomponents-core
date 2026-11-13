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
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TestNullEntity {

    @Test
    void testLength() {
        Assertions.assertThat(NullEntity.INSTANCE.getContentLength()).isEqualTo(0);
    }

    @Test
    void testContentType() {
        Assertions.assertThat(NullEntity.INSTANCE.getContentType()).isNull();
    }

    @Test
    void testContentEncoding() {
        Assertions.assertThat(NullEntity.INSTANCE.getContentEncoding()).isNull();
    }

    @Test
    void testTrailerNames() {
        Assertions.assertThat(NullEntity.INSTANCE.getTrailerNames()).isEqualTo(Collections.emptySet());
    }

    @Test
    void testContentStream() throws IOException {
        try (InputStream content = NullEntity.INSTANCE.getContent()) {
            Assertions.assertThat(content.read()).isEqualTo(-1);
        }
        // Closing the resource should have no impact
        try (InputStream content = NullEntity.INSTANCE.getContent()) {
            Assertions.assertThat(content.read()).isEqualTo(-1);
        }
    }

    @Test
    void testWriteTo() throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        NullEntity.INSTANCE.writeTo(baos);
        Assertions.assertThat(baos.size()).isEqualTo(0);
    }

    @Test
    void testIsStreaming() {
        Assertions.assertThat(NullEntity.INSTANCE.isStreaming()).isFalse();
    }

    @Test
    void testIsChunked() {
        Assertions.assertThat(NullEntity.INSTANCE.isChunked()).isFalse();
    }
}