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

package org.apache.http.message;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.http.HttpVersion;
import org.apache.http.RequestLine;
import org.junit.Assert;
import org.junit.Test;

/**
 * Simple tests for {@link RequestLine}.
 */
public class TestRequestLine {

    @Test
    public void testConstructor() {
        RequestLine requestline = new BasicRequestLine("GET", "/stuff", HttpVersion.HTTP_1_1);
        Assert.assertEquals("GET", requestline.getMethod());
        Assert.assertEquals("/stuff", requestline.getUri());
        Assert.assertEquals(HttpVersion.HTTP_1_1, requestline.getProtocolVersion());
    }

    @Test
    public void testConstructorInvalidInput() {
        try {
            new BasicRequestLine(null, "/stuff", HttpVersion.HTTP_1_1);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException e) { /* expected */ }
        try {
            new BasicRequestLine("GET", null, HttpVersion.HTTP_1_1);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException e) { /* expected */ }
        try {
            new BasicRequestLine("GET", "/stuff", (HttpVersion)null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException e) { /* expected */ }
    }

    @Test
    public void testCloning() throws Exception {
        BasicRequestLine orig = new BasicRequestLine("GET", "/stuff", HttpVersion.HTTP_1_1);
        BasicRequestLine clone = (BasicRequestLine) orig.clone();
        Assert.assertEquals(orig.getMethod(), clone.getMethod());
        Assert.assertEquals(orig.getUri(), clone.getUri());
        Assert.assertEquals(orig.getProtocolVersion(), clone.getProtocolVersion());
    }

    @Test
    public void testSerialization() throws Exception {
        BasicRequestLine orig = new BasicRequestLine("GET", "/stuff", HttpVersion.HTTP_1_1);
        ByteArrayOutputStream outbuffer = new ByteArrayOutputStream();
        ObjectOutputStream outstream = new ObjectOutputStream(outbuffer);
        outstream.writeObject(orig);
        outstream.close();
        byte[] raw = outbuffer.toByteArray();
        ByteArrayInputStream inbuffer = new ByteArrayInputStream(raw);
        ObjectInputStream instream = new ObjectInputStream(inbuffer);
        BasicRequestLine clone = (BasicRequestLine) instream.readObject();
        Assert.assertEquals(orig.getMethod(), clone.getMethod());
        Assert.assertEquals(orig.getUri(), clone.getUri());
        Assert.assertEquals(orig.getProtocolVersion(), clone.getProtocolVersion());
    }

}
