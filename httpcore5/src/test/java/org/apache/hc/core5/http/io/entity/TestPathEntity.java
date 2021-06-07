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
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.hc.core5.http.ContentType;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link PathEntity}.
 */
public class TestPathEntity {

    @Test
    public void testBasics() throws Exception {
        final Path tmpPath = Files.createTempFile("testfile", ".txt");
        // Mark the file for deletion on VM exit if an assertion fails.
        tmpPath.toFile().deleteOnExit();
        try (final PathEntity httpEntity = new PathEntity(tmpPath, ContentType.TEXT_PLAIN)) {
            Assert.assertEquals(Files.size(tmpPath), httpEntity.getContentLength());
            try (final InputStream content = httpEntity.getContent()) {
                Assert.assertNotNull(content);
            }
            Assert.assertTrue(httpEntity.isRepeatable());
            Assert.assertFalse(httpEntity.isStreaming());
            // If we can't delete the file now, then the PathEntity or test is hanging on to a file handle.
            Assert.assertTrue("Failed to delete " + tmpPath, Files.deleteIfExists(tmpPath));
        }
    }

    @Test
    public void testNullConstructor() throws Exception {
        Assert.assertThrows(NullPointerException.class, () -> new PathEntity(null, ContentType.TEXT_PLAIN));
    }

    @Test
    public void testWriteTo() throws Exception {
        final Path tmpPath = Files.createTempFile("testfile", ".txt");
        // Mark the file for deletion on VM exit if an assertion fails.
        tmpPath.toFile().deleteOnExit();
        try (final OutputStream outStream = Files.newOutputStream(tmpPath)) {
            outStream.write(0);
            outStream.write(1);
            outStream.write(2);
            outStream.write(3);
        }

        try (final PathEntity httpEntity = new PathEntity(tmpPath, ContentType.TEXT_PLAIN)) {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            httpEntity.writeTo(out);
            final byte[] bytes = out.toByteArray();
            Assert.assertNotNull(bytes);
            Assert.assertEquals(Files.size(tmpPath), bytes.length);
            for (int i = 0; i < 4; i++) {
                Assert.assertEquals(i, bytes[i]);
            }
            // If we can't delete the file now, then the PathEntity or test is hanging on to a file handle
            Assert.assertTrue("Failed to delete " + tmpPath, Files.deleteIfExists(tmpPath));
            Assert.assertThrows(NullPointerException.class, () -> httpEntity.writeTo(null));
        }
    }
}
