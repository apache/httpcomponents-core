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
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link FileEntity}.
 *
 */
public class TestFileEntity {

    @Test
    public void testBasics() throws Exception {
        final File tmpfile = File.createTempFile("testfile", ".txt");
        tmpfile.deleteOnExit();
        final FileEntity httpentity = new FileEntity(tmpfile, ContentType.TEXT_PLAIN);

        Assert.assertEquals(tmpfile.length(), httpentity.getContentLength());
        final InputStream content = httpentity.getContent();
        Assert.assertNotNull(content);
        content.close();
        Assert.assertTrue(httpentity.isRepeatable());
        Assert.assertFalse(httpentity.isStreaming());
        Assert.assertTrue("Failed to delete " + tmpfile, tmpfile.delete());
    }

    @Test
    public void testNullConstructor() throws Exception {
        Assert.assertThrows(NullPointerException.class, () -> new FileEntity(null, ContentType.TEXT_PLAIN));
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

        final FileEntity httpentity = new FileEntity(tmpfile, ContentType.TEXT_PLAIN);

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        httpentity.writeTo(out);
        final byte[] bytes = out.toByteArray();
        Assert.assertNotNull(bytes);
        Assert.assertEquals(tmpfile.length(), bytes.length);
        for (int i = 0; i < 4; i++) {
            Assert.assertEquals(i, bytes[i]);
        }
        Assert.assertTrue("Failed to delete: " + tmpfile, tmpfile.delete());

        Assert.assertThrows(NullPointerException.class, () -> httpentity.writeTo(null));
    }

}
