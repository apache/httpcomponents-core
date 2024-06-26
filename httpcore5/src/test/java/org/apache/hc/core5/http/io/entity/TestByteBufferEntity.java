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

import org.junit.jupiter.api.Assertions;
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

            Assertions.assertEquals(bytes.capacity(), httpentity.getContentLength());
            Assertions.assertNotNull(httpentity.getContent());
            Assertions.assertFalse(httpentity.isRepeatable());
            Assertions.assertFalse(httpentity.isStreaming());
        }
    }


    @Test
    void testWriteTo() throws Exception {
        final ByteBuffer bytes = ByteBuffer.wrap("Message content".getBytes(StandardCharsets.US_ASCII));
        try (final ByteBufferEntity httpentity = new ByteBufferEntity(bytes, null)) {

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            httpentity.writeTo(out);
            byte[] bytes2 = out.toByteArray();
            Assertions.assertNotNull(bytes2);
            Assertions.assertEquals(bytes.capacity(), bytes2.length);
            bytes.position(0);
            for (int i = 0; i < bytes2.length; i++) {
                Assertions.assertEquals(bytes.get(i), bytes2[i]);
            }

            out = new ByteArrayOutputStream();
            httpentity.writeTo(out);
            bytes2 = out.toByteArray();
            Assertions.assertNotNull(bytes2);
            Assertions.assertEquals(bytes.capacity(), bytes2.length);
            bytes.position(0);
            for (int i = 0; i < bytes.capacity(); i++) {
                Assertions.assertEquals(bytes.get(i), bytes2[i]);
            }

            Assertions.assertThrows(NullPointerException.class, () -> httpentity.writeTo(null));
        }
    }
}
