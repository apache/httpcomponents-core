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
import java.io.IOException;
import java.io.OutputStream;

import junit.framework.TestCase;

import org.apache.http.HttpEntity;

/**
 * Unit tests for {@link EntityTemplate}.
 *
 */
public class TestEntityTemplate extends TestCase {

    public TestEntityTemplate(String testName) {
        super(testName);
    }

    public void testBasics() throws Exception {

        HttpEntity httpentity = new EntityTemplate(new ContentProducer() {

            public void writeTo(final OutputStream outstream) throws IOException {
                outstream.write('a');
            }

        });

        assertEquals(-1, httpentity.getContentLength());
        assertTrue(httpentity.isRepeatable());
        assertFalse(httpentity.isStreaming());
    }

    public void testIllegalConstructor() throws Exception {
        try {
            new EntityTemplate(null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    public void testWriteTo() throws Exception {
        HttpEntity httpentity = new EntityTemplate(new ContentProducer() {

            public void writeTo(final OutputStream outstream) throws IOException {
                outstream.write('a');
            }

        });

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        httpentity.writeTo(out);
        byte[] bytes2 = out.toByteArray();
        assertNotNull(bytes2);
        assertEquals(1, bytes2.length);

        try {
            httpentity.writeTo(null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    public void testgetContent() throws Exception {
        HttpEntity httpentity = new EntityTemplate(new ContentProducer() {

            public void writeTo(final OutputStream outstream) throws IOException {
                outstream.write('a');
            }

        });
        try {
            httpentity.getContent();
            fail("UnsupportedOperationException should have been thrown");
        } catch (UnsupportedOperationException ex) {
            // expected
        }
    }

}
