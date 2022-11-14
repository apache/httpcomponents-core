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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link FileEntity}.
 *
 */
public class TestFileEntity {

    @Test
    public void testBasics() throws Exception {
        final File tmpfile = File.createTempFile("testfile", ".txt");
        tmpfile.deleteOnExit();
        try (final FileEntity httpentity = new FileEntity(tmpfile, ContentType.TEXT_PLAIN)) {

            Assertions.assertEquals(tmpfile.length(), httpentity.getContentLength());
            final InputStream content = httpentity.getContent();
            Assertions.assertNotNull(content);
            content.close();
            Assertions.assertTrue(httpentity.isRepeatable());
            Assertions.assertFalse(httpentity.isStreaming());
            Assertions.assertTrue(tmpfile.delete(), "Failed to delete " + tmpfile);
        }
    }

    @Test
    public void testNullConstructor() throws Exception {
        Assertions.assertThrows(NullPointerException.class, () -> new FileEntity(null, ContentType.TEXT_PLAIN));
    }

    @Test
    public void testWriteTo() throws Exception {
        final File tmpfile = File.createTempFile("testfile", ".txt");
        tmpfile.deleteOnExit();

        final FileOutputStream outStream = new FileOutputStream(tmpfile);
        outStream.write(0);
        outStream.write(1);
        outStream.write(2);
        outStream.write(3);
        outStream.close();

        try (final FileEntity httpentity = new FileEntity(tmpfile, ContentType.TEXT_PLAIN)) {

            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            httpentity.writeTo(out);
            final byte[] bytes = out.toByteArray();
            Assertions.assertNotNull(bytes);
            Assertions.assertEquals(tmpfile.length(), bytes.length);
            for (int i = 0; i < 4; i++) {
                Assertions.assertEquals(i, bytes[i]);
            }
            Assertions.assertTrue(tmpfile.delete(), "Failed to delete: " + tmpfile);

            Assertions.assertThrows(NullPointerException.class, () -> httpentity.writeTo(null));
        }
    }

}
