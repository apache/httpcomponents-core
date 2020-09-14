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

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public class TestNullEntity {

    @Test
    public void testLength() {
        assertEquals(0, NullEntity.INSTANCE.getContentLength());
    }

    @Test
    public void testContentType() {
        assertNull(NullEntity.INSTANCE.getContentType());
    }

    @Test
    public void testContentEncoding() {
        assertNull(NullEntity.INSTANCE.getContentEncoding());
    }

    @Test
    public void testTrailerNames() {
        assertEquals(Collections.emptySet(), NullEntity.INSTANCE.getTrailerNames());
    }

    @Test
    public void testContentStream() throws IOException {
        try (InputStream content = NullEntity.INSTANCE.getContent()) {
            assertEquals(-1, content.read());
        }
        // Closing the resource should have no impact
        try (InputStream content = NullEntity.INSTANCE.getContent()) {
            assertEquals(-1, content.read());
        }
    }

    @Test
    public void testWriteTo() throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        NullEntity.INSTANCE.writeTo(baos);
        assertEquals(0, baos.size());
    }

    @Test
    public void testIsStreaming() {
        assertFalse(NullEntity.INSTANCE.isStreaming());
    }

    @Test
    public void testIsChunked() {
        assertFalse(NullEntity.INSTANCE.isChunked());
    }
}