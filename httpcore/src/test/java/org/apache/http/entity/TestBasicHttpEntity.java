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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import org.apache.http.Consts;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link BasicHttpEntity}.
 *
 */
public class TestBasicHttpEntity {

    @Test
    public void testBasics() throws Exception {

        byte[] bytes = "Message content".getBytes(Consts.ASCII.name());
        InputStream content = new ByteArrayInputStream(bytes);
        BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContent(content);
        httpentity.setContentLength(bytes.length);

        Assert.assertEquals(bytes.length, httpentity.getContentLength());
        Assert.assertFalse(httpentity.isRepeatable());
        Assert.assertTrue(httpentity.isStreaming());
    }

    @Test
    public void testContent() throws Exception {
        byte[] bytes = "Message content".getBytes(Consts.ASCII.name());
        InputStream content = new ByteArrayInputStream(bytes);
        BasicHttpEntity httpentity = new BasicHttpEntity();
        try {
            httpentity.getContent();
            Assert.fail("IllegalStateException should have been thrown");
        } catch (IllegalStateException ex) {
            // expected
        }
        httpentity.setContent(content);
        Assert.assertEquals(content, httpentity.getContent());

        httpentity.setContent(null);
        try {
            httpentity.getContent();
            Assert.fail("IllegalStateException should have been thrown");
        } catch (IllegalStateException ex) {
            // expected
        }
    }

    @Test
    public void testWriteTo() throws Exception {
        byte[] bytes = "Message content".getBytes(Consts.ASCII.name());
        InputStream content = new ByteArrayInputStream(bytes);
        BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContent(content);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        httpentity.writeTo(out);
        byte[] bytes2 = out.toByteArray();
        Assert.assertNotNull(bytes2);
        Assert.assertEquals(bytes.length, bytes2.length);
        for (int i = 0; i < bytes.length; i++) {
            Assert.assertEquals(bytes[i], bytes2[i]);
        }
        httpentity.setContent(null);
        out = new ByteArrayOutputStream();
        try {
            httpentity.writeTo(out);
            Assert.fail("IllegalStateException should have been thrown");
        } catch (IllegalStateException ex) {
            // expected
        }

        try {
            httpentity.writeTo(null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

}
