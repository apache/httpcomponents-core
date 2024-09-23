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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.http.ContentType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link InputStreamEntity}.
 *
 */
class TestInputStreamEntity {

    @Test
    void testBasics() throws Exception {
        final byte[] bytes = "Message content".getBytes(StandardCharsets.US_ASCII);
        final InputStreamEntity entity = new InputStreamEntity(new ByteArrayInputStream(bytes), bytes.length, null);

        Assertions.assertEquals(bytes.length, entity.getContentLength());
        Assertions.assertNotNull(entity.getContent());
        Assertions.assertFalse(entity.isRepeatable());
        Assertions.assertTrue(entity.isStreaming());
    }

    @Test
    void testNullConstructor() {
        Assertions.assertThrows(NullPointerException.class, () ->
                new InputStreamEntity(null, 0, null));
    }

    @Test
    void testUnknownLengthConstructor() throws Exception {
        try (final InputStreamEntity entity = new InputStreamEntity(EmptyInputStream.INSTANCE, null)) {
            Assertions.assertEquals(-1, entity.getContentLength());
        }
    }

    @Test
    void testWriteTo() throws Exception {
        final String message = "Message content";
        final byte[] bytes = message.getBytes(StandardCharsets.US_ASCII);
        final InputStream inStream = new ByteArrayInputStream(bytes);
        try (final InputStreamEntity entity = new InputStreamEntity(inStream, bytes.length,
                ContentType.TEXT_PLAIN.withCharset(StandardCharsets.US_ASCII))) {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            entity.writeTo(out);
            final byte[] writtenBytes = out.toByteArray();
            Assertions.assertNotNull(writtenBytes);
            Assertions.assertEquals(bytes.length, writtenBytes.length);

            final String s = new String(writtenBytes, StandardCharsets.US_ASCII.name());
            Assertions.assertEquals(message, s);
        }
    }

    @Test
    void testWriteToPartialContent() throws Exception {
        final String message = "Message content";
        final byte[] bytes = message.getBytes(StandardCharsets.US_ASCII);
        final InputStream inStream = new ByteArrayInputStream(bytes);
        final int contentLength = 7;
        try (final InputStreamEntity entity = new InputStreamEntity(inStream, contentLength,
                ContentType.TEXT_PLAIN.withCharset(StandardCharsets.US_ASCII))) {

            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            entity.writeTo(out);
            final byte[] writtenBytes = out.toByteArray();
            Assertions.assertNotNull(writtenBytes);
            Assertions.assertEquals(contentLength, writtenBytes.length);

            final String s = new String(writtenBytes, StandardCharsets.US_ASCII.name());
            Assertions.assertEquals(message.substring(0, contentLength), s);
        }
    }

    @Test
    void testWriteToUnknownLength() throws Exception {
        final String message = "Message content";
        final byte[] bytes = message.getBytes(StandardCharsets.US_ASCII);
        final InputStreamEntity entity = new InputStreamEntity(new ByteArrayInputStream(bytes),
                ContentType.TEXT_PLAIN.withCharset(StandardCharsets.US_ASCII));

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        entity.writeTo(out);
        final byte[] writtenBytes = out.toByteArray();
        Assertions.assertNotNull(writtenBytes);
        Assertions.assertEquals(bytes.length, writtenBytes.length);

        final String s = new String(writtenBytes, StandardCharsets.US_ASCII.name());
        Assertions.assertEquals(message, s);
    }

    @Test
    void testWriteToNull() throws Exception {
        try (final InputStreamEntity entity = new InputStreamEntity(EmptyInputStream.INSTANCE, 0, null)) {
            Assertions.assertThrows(NullPointerException.class, () -> entity.writeTo(null));
        }
    }
}
