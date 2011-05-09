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

import org.apache.http.HeaderElement;
import org.apache.http.util.CharArrayBuffer;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link BufferedHeader}.
 *
 */
public class TestBufferedHeader {

    @Test
    public void testBasicConstructor() {
        CharArrayBuffer buf = new CharArrayBuffer(32);
        buf.append("name: value");
        BufferedHeader header = new BufferedHeader(buf);
        Assert.assertEquals("name", header.getName());
        Assert.assertEquals("value", header.getValue());
        Assert.assertSame(buf, header.getBuffer());
        Assert.assertEquals(5, header.getValuePos());
    }

    @Test
    public void testInvalidName() {
        try {
            new BufferedHeader(null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            //expected
        }
    }

    @Test
    public void testHeaderElements() {
        CharArrayBuffer buf = new CharArrayBuffer(32);
        buf.append("name: element1 = value1, element2; param1 = value1, element3");
        BufferedHeader header = new BufferedHeader(buf);
        HeaderElement[] elements = header.getElements();
        Assert.assertNotNull(elements);
        Assert.assertEquals(3, elements.length);
        Assert.assertEquals("element1", elements[0].getName());
        Assert.assertEquals("value1", elements[0].getValue());
        Assert.assertEquals("element2", elements[1].getName());
        Assert.assertEquals(null, elements[1].getValue());
        Assert.assertEquals("element3", elements[2].getName());
        Assert.assertEquals(null, elements[2].getValue());
        Assert.assertEquals(1, elements[1].getParameters().length);
    }

    @Test
    public void testCloning() throws Exception {
        CharArrayBuffer buf = new CharArrayBuffer(32);
        buf.append("name: value");
        BufferedHeader orig = new BufferedHeader(buf);
        BufferedHeader clone = (BufferedHeader) orig.clone();
        Assert.assertEquals(orig.getName(), clone.getName());
        Assert.assertEquals(orig.getValue(), clone.getValue());
    }

    @Test
    public void testSerialization() throws Exception {
        CharArrayBuffer buf = new CharArrayBuffer(32);
        buf.append("name: value");
        BufferedHeader orig = new BufferedHeader(buf);
        ByteArrayOutputStream outbuffer = new ByteArrayOutputStream();
        ObjectOutputStream outstream = new ObjectOutputStream(outbuffer);
        outstream.writeObject(orig);
        outstream.close();
        byte[] raw = outbuffer.toByteArray();
        ByteArrayInputStream inbuffer = new ByteArrayInputStream(raw);
        ObjectInputStream instream = new ObjectInputStream(inbuffer);
        BufferedHeader clone = (BufferedHeader) instream.readObject();
        Assert.assertEquals(orig.getName(), clone.getName());
        Assert.assertEquals(orig.getValue(), clone.getValue());
    }

}

