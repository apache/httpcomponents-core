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
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import org.apache.hc.core5.http.ContentType;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link FileEntity}.
 *
 */
class TestFileEntity {

    @Test
    void testBasics() throws Exception {
        final File tmpfile = File.createTempFile("testfile", ".txt");
        tmpfile.deleteOnExit();
        try (final FileEntity httpentity = new FileEntity(tmpfile, ContentType.TEXT_PLAIN)) {

            Assertions.assertThat(httpentity.getContentLength()).isEqualTo(tmpfile.length());
            try (InputStream content = httpentity.getContent()) {
                Assertions.assertThat(content).isNotNull();
            }
            Assertions.assertThat(httpentity.isRepeatable()).isTrue();
            Assertions.assertThat(httpentity.isStreaming()).isFalse();
            Assertions.assertThat(tmpfile.delete()).withFailMessage("Failed to delete " + tmpfile).isTrue();
        }
    }

    @Test
    void testNullConstructor() {
        Assertions.assertThatThrownBy(() ->
                        new FileEntity(null, ContentType.TEXT_PLAIN))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testWriteTo() throws Exception {
        final File tmpfile = File.createTempFile("testfile", ".txt");
        tmpfile.deleteOnExit();

        try (FileOutputStream outStream = new FileOutputStream(tmpfile)) {
            outStream.write(0);
            outStream.write(1);
            outStream.write(2);
            outStream.write(3);
        }

        try (final FileEntity httpentity = new FileEntity(tmpfile, ContentType.TEXT_PLAIN)) {

            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            httpentity.writeTo(out);
            final byte[] bytes = out.toByteArray();
            Assertions.assertThat(bytes).isNotNull();
            Assertions.assertThat(bytes.length).isEqualTo(tmpfile.length());
            for (int i = 0; i < 4; i++) {
                Assertions.assertThat(bytes[i]).isEqualTo((byte) i);
            }
            Assertions.assertThat(tmpfile.delete()).withFailMessage("Failed to delete: " + tmpfile).isTrue();

            Assertions.assertThatThrownBy(() ->
                            httpentity.writeTo(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

}
