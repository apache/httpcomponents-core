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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link BufferedHttpEntity}.
 *
 */
public class TestBufferedHttpEntity {

    @Test
    public void testBufferingEntity() throws Exception {
        final byte[] bytes = "Message content".getBytes(StandardCharsets.US_ASCII);
        final BufferedHttpEntity entity = new BufferedHttpEntity(
                new InputStreamEntity(new ByteArrayInputStream(bytes), -1, null));
        Assertions.assertEquals(bytes.length, entity.getContentLength());
        Assertions.assertTrue(entity.isRepeatable());
        Assertions.assertFalse(entity.isChunked());
        Assertions.assertFalse(entity.isStreaming());

        // test if we can obtain contain multiple times
        Assertions.assertNotNull(entity.getContent ());
        Assertions.assertNotNull(entity.getContent ());
    }

    @Test
    public void testWrappingEntity() throws Exception {
        final byte[] bytes = "Message content".getBytes(StandardCharsets.US_ASCII);
        final ByteArrayEntity httpentity = new ByteArrayEntity(bytes, null, true);
        try (final BufferedHttpEntity bufentity = new BufferedHttpEntity(httpentity)) {
            Assertions.assertEquals(bytes.length, bufentity.getContentLength());
            Assertions.assertTrue(bufentity.isRepeatable());
            Assertions.assertTrue(bufentity.isChunked());
            Assertions.assertFalse(bufentity.isStreaming());

            // test if we can obtain contain multiple times
            Assertions.assertNotNull(bufentity.getContent());
            Assertions.assertNotNull(bufentity.getContent());
        }
    }

    @Test
    public void testIllegalConstructor() throws Exception {
        Assertions.assertThrows(NullPointerException.class, () -> new BufferedHttpEntity(null));
    }

    @Test
    public void testWriteToBuffered() throws Exception {
        final byte[] bytes = "Message content".getBytes(StandardCharsets.US_ASCII);
        final InputStreamEntity httpentity = new InputStreamEntity(new ByteArrayInputStream(bytes), -1, null);
        try (final BufferedHttpEntity bufentity = new BufferedHttpEntity(httpentity)) {

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bufentity.writeTo(out);
            byte[] bytes2 = out.toByteArray();
            Assertions.assertNotNull(bytes2);
            Assertions.assertEquals(bytes.length, bytes2.length);
            for (int i = 0; i < bytes.length; i++) {
                Assertions.assertEquals(bytes[i], bytes2[i]);
            }

            out = new ByteArrayOutputStream();
            bufentity.writeTo(out);
            bytes2 = out.toByteArray();
            Assertions.assertNotNull(bytes2);
            Assertions.assertEquals(bytes.length, bytes2.length);
            for (int i = 0; i < bytes.length; i++) {
                Assertions.assertEquals(bytes[i], bytes2[i]);
            }

            Assertions.assertThrows(NullPointerException.class, () -> bufentity.writeTo(null));
        }
    }

    @Test
    public void testWriteToWrapped() throws Exception {
        final byte[] bytes = "Message content".getBytes(StandardCharsets.US_ASCII);
        final ByteArrayEntity httpentity = new ByteArrayEntity(bytes, null);
        try (final BufferedHttpEntity bufentity = new BufferedHttpEntity(httpentity)) {

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bufentity.writeTo(out);
            byte[] bytes2 = out.toByteArray();
            Assertions.assertNotNull(bytes2);
            Assertions.assertEquals(bytes.length, bytes2.length);
            for (int i = 0; i < bytes.length; i++) {
                Assertions.assertEquals(bytes[i], bytes2[i]);
            }

            out = new ByteArrayOutputStream();
            bufentity.writeTo(out);
            bytes2 = out.toByteArray();
            Assertions.assertNotNull(bytes2);
            Assertions.assertEquals(bytes.length, bytes2.length);
            for (int i = 0; i < bytes.length; i++) {
                Assertions.assertEquals(bytes[i], bytes2[i]);
            }

            Assertions.assertThrows(NullPointerException.class, () -> bufentity.writeTo(null));
        }
    }

}
