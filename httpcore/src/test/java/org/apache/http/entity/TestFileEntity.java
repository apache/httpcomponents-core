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

package org.apache.http.entity;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import junit.framework.TestCase;

import org.apache.http.protocol.HTTP;

/**
 * Unit tests for {@link FileEntity}.
 *
 */
public class TestFileEntity extends TestCase {

    public TestFileEntity(String testName) {
        super(testName);
    }

    public void testBasics() throws Exception {
        File tmpfile = File.createTempFile("testfile", ".txt");
        tmpfile.deleteOnExit();
        FileEntity httpentity = new FileEntity(tmpfile, HTTP.ISO_8859_1);

        assertEquals(tmpfile.length(), httpentity.getContentLength());
        final InputStream content = httpentity.getContent();
        assertNotNull(content);
        content.close();
        assertTrue(httpentity.isRepeatable());
        assertFalse(httpentity.isStreaming());
        if (!tmpfile.delete()){
            fail("Failed to delete: "+tmpfile);
        }
    }

    public void testIllegalConstructor() throws Exception {
        try {
            new FileEntity(null, null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    public void testWriteTo() throws Exception {
        File tmpfile = File.createTempFile("testfile", ".txt");
        tmpfile.deleteOnExit();

        FileOutputStream outstream = new FileOutputStream(tmpfile);
        outstream.write(0);
        outstream.write(1);
        outstream.write(2);
        outstream.write(3);
        outstream.close();

        FileEntity httpentity = new FileEntity(tmpfile, HTTP.ISO_8859_1);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        httpentity.writeTo(out);
        byte[] bytes = out.toByteArray();
        assertNotNull(bytes);
        assertEquals(tmpfile.length(), bytes.length);
        for (int i = 0; i < 4; i++) {
            assertEquals(i, bytes[i]);
        }
        if (!tmpfile.delete()){
            fail("Failed to delete: "+tmpfile);
        }

        try {
            httpentity.writeTo(null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

}
