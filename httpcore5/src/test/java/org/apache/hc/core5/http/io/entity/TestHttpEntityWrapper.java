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

import org.apache.hc.core5.http.ContentType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link HttpEntityWrapper}.
 *
 */
public class TestHttpEntityWrapper {

    @Test
    public void testBasics() throws Exception {
        final StringEntity entity = new StringEntity("Message content", ContentType.TEXT_PLAIN, "blah", false);
        try (final HttpEntityWrapper wrapped = new HttpEntityWrapper(entity)) {

            Assertions.assertEquals(entity.getContentLength(), wrapped.getContentLength());
            Assertions.assertEquals(entity.getContentType(), wrapped.getContentType());
            Assertions.assertEquals(entity.getContentEncoding(), wrapped.getContentEncoding());
            Assertions.assertEquals(entity.isChunked(), wrapped.isChunked());
            Assertions.assertEquals(entity.isRepeatable(), wrapped.isRepeatable());
            Assertions.assertEquals(entity.isStreaming(), wrapped.isStreaming());
            Assertions.assertNotNull(wrapped.getContent());
        }
    }

    @Test
    public void testIllegalConstructor() throws Exception {
        Assertions.assertThrows(NullPointerException.class, () -> new HttpEntityWrapper(null));
    }

    @Test
    public void testWriteTo() throws Exception {
        final String s = "Message content";
        final byte[] bytes = s.getBytes(StandardCharsets.US_ASCII);
        final StringEntity entity = new StringEntity(s);
        try (final HttpEntityWrapper wrapped = new HttpEntityWrapper(entity)) {

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wrapped.writeTo(out);
            byte[] bytes2 = out.toByteArray();
            Assertions.assertNotNull(bytes2);
            Assertions.assertEquals(bytes.length, bytes2.length);
            for (int i = 0; i < bytes.length; i++) {
                Assertions.assertEquals(bytes[i], bytes2[i]);
            }

            out = new ByteArrayOutputStream();
            wrapped.writeTo(out);
            bytes2 = out.toByteArray();
            Assertions.assertNotNull(bytes2);
            Assertions.assertEquals(bytes.length, bytes2.length);
            for (int i = 0; i < bytes.length; i++) {
                Assertions.assertEquals(bytes[i], bytes2[i]);
            }

            Assertions.assertThrows(NullPointerException.class, () -> wrapped.writeTo(null));
        }
    }

    @Test
    public void testConsumeContent() throws Exception {
        final String s = "Message content";
        final StringEntity entity = new StringEntity(s);
        final HttpEntityWrapper wrapped = new HttpEntityWrapper(entity);
        EntityUtils.consume(wrapped);
        EntityUtils.consume(wrapped);
    }

}
