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

import org.junit.jupiter.api.Assertions;
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

            Assertions.assertEquals(bytes.length, entity.getContentLength());
            Assertions.assertNotNull(entity.getContent());
            Assertions.assertTrue(entity.isRepeatable());
            Assertions.assertFalse(entity.isStreaming());
        }
    }

    @Test
    void testBasicOffLen() throws Exception {
        final byte[] bytes = "Message content".getBytes(StandardCharsets.US_ASCII);
        try (final ByteArrayEntity entity = new ByteArrayEntity(bytes, 8, 7, null)) {

            Assertions.assertEquals(7, entity.getContentLength());
            Assertions.assertNotNull(entity.getContent());
            Assertions.assertTrue(entity.isRepeatable());
            Assertions.assertFalse(entity.isStreaming());
        }
    }

    @Test
    void testIllegalConstructorNullByteArray() {
        Assertions.assertThrows(NullPointerException.class, () ->
                new ByteArrayEntity(null, null));
    }

    @Test
    void testIllegalConstructorBadLen() {
        final byte[] bytes = "Message content".getBytes(StandardCharsets.US_ASCII);
        Assertions.assertThrows(IllegalArgumentException.class, () ->
                new ByteArrayEntity(bytes, 0, bytes.length + 1, null));
    }

    @Test
    void testIllegalConstructorBadOff1() {
        final byte[] bytes = "Message content".getBytes(StandardCharsets.US_ASCII);
        Assertions.assertThrows(IllegalArgumentException.class, () ->
                new ByteArrayEntity(bytes, -1, bytes.length, null));
    }

    @Test
    void testIllegalConstructorBadOff2() {
        final byte[] bytes = "Message content".getBytes(StandardCharsets.US_ASCII);
        Assertions.assertThrows(IllegalArgumentException.class, () ->
                new ByteArrayEntity(bytes, bytes.length + 1, bytes.length, null));
    }

    @Test
    void testWriteTo() throws Exception {
        final byte[] bytes = "Message content".getBytes(StandardCharsets.US_ASCII);
        try (final ByteArrayEntity entity = new ByteArrayEntity(bytes, null)) {

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            entity.writeTo(out);
            byte[] bytes2 = out.toByteArray();
            Assertions.assertNotNull(bytes2);
            Assertions.assertEquals(bytes.length, bytes2.length);
            for (int i = 0; i < bytes.length; i++) {
                Assertions.assertEquals(bytes[i], bytes2[i]);
            }

            out = new ByteArrayOutputStream();
            entity.writeTo(out);
            bytes2 = out.toByteArray();
            Assertions.assertNotNull(bytes2);
            Assertions.assertEquals(bytes.length, bytes2.length);
            for (int i = 0; i < bytes.length; i++) {
                Assertions.assertEquals(bytes[i], bytes2[i]);
            }

            Assertions.assertThrows(NullPointerException.class, () -> entity.writeTo(null));
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
            Assertions.assertNotNull(bytes2);
            Assertions.assertEquals(len, bytes2.length);
            for (int i = 0; i < len; i++) {
                Assertions.assertEquals(bytes[i + off], bytes2[i]);
            }

            out = new ByteArrayOutputStream();
            entity.writeTo(out);
            bytes2 = out.toByteArray();
            Assertions.assertNotNull(bytes2);
            Assertions.assertEquals(len, bytes2.length);
            for (int i = 0; i < len; i++) {
                Assertions.assertEquals(bytes[i + off], bytes2[i]);
            }

            Assertions.assertThrows(NullPointerException.class, () -> entity.writeTo(null));
        }
    }

}
