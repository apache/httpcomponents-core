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

import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.StatusLine;
import org.junit.Assert;
import org.junit.Test;

/**
 * Simple tests for {@link StatusLine}.
 */
public class TestStatusLine {

    @Test
    public void testConstructor() {
        StatusLine statusline = new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        Assert.assertEquals(HttpVersion.HTTP_1_1, statusline.getProtocolVersion());
        Assert.assertEquals(HttpStatus.SC_OK, statusline.getStatusCode());
        Assert.assertEquals("OK", statusline.getReasonPhrase());
    }

    @Test
    public void testConstructorInvalidInput() {
        try {
            new BasicStatusLine(null, HttpStatus.SC_OK, "OK");
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException e) { /* expected */ }
        try {
            new BasicStatusLine(HttpVersion.HTTP_1_1, -1, "OK");
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException e) { /* expected */ }
    }

    @Test
    public void testToString() throws Exception {
        StatusLine statusline = new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        Assert.assertEquals("HTTP/1.1 200 OK", statusline.toString());
        statusline = new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, null);
        // toString uses default formatting, hence the trailing space
        Assert.assertEquals("HTTP/1.1 200 ", statusline.toString());
    }

    @Test
    public void testCloning() throws Exception {
        BasicStatusLine orig = new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        BasicStatusLine clone = (BasicStatusLine) orig.clone();
        Assert.assertEquals(orig.getReasonPhrase(), clone.getReasonPhrase());
        Assert.assertEquals(orig.getStatusCode(), clone.getStatusCode());
        Assert.assertEquals(orig.getProtocolVersion(), clone.getProtocolVersion());
    }

    @Test
    public void testSerialization() throws Exception {
        BasicStatusLine orig = new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        ByteArrayOutputStream outbuffer = new ByteArrayOutputStream();
        ObjectOutputStream outstream = new ObjectOutputStream(outbuffer);
        outstream.writeObject(orig);
        outstream.close();
        byte[] raw = outbuffer.toByteArray();
        ByteArrayInputStream inbuffer = new ByteArrayInputStream(raw);
        ObjectInputStream instream = new ObjectInputStream(inbuffer);
        BasicStatusLine clone = (BasicStatusLine) instream.readObject();
        Assert.assertEquals(orig.getReasonPhrase(), clone.getReasonPhrase());
        Assert.assertEquals(orig.getStatusCode(), clone.getStatusCode());
        Assert.assertEquals(orig.getProtocolVersion(), clone.getProtocolVersion());
    }

}
