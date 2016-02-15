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

package org.apache.hc.core5.http.message;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.util.CharArrayBuffer;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link BufferedHeader}.
 *
 */
public class TestBufferedHeader {

    @Test
    public void testBasicConstructor() throws Exception {
        final CharArrayBuffer buf = new CharArrayBuffer(32);
        buf.append("name: value");
        final BufferedHeader header = new BufferedHeader(buf, false);
        Assert.assertEquals("name", header.getName());
        Assert.assertEquals("value", header.getValue());
        Assert.assertSame(buf, header.getBuffer());
        Assert.assertEquals(5, header.getValuePos());
    }

    @Test
    public void testSerialization() throws Exception {
        final CharArrayBuffer buf = new CharArrayBuffer(32);
        buf.append("name: value");
        final BufferedHeader orig = new BufferedHeader(buf, false);
        final ByteArrayOutputStream outbuffer = new ByteArrayOutputStream();
        final ObjectOutputStream outstream = new ObjectOutputStream(outbuffer);
        outstream.writeObject(orig);
        outstream.close();
        final byte[] raw = outbuffer.toByteArray();
        final ByteArrayInputStream inbuffer = new ByteArrayInputStream(raw);
        final ObjectInputStream instream = new ObjectInputStream(inbuffer);
        final BufferedHeader clone = (BufferedHeader) instream.readObject();
        Assert.assertEquals(orig.getName(), clone.getName());
        Assert.assertEquals(orig.getValue(), clone.getValue());
    }

    @Test
    public void testInvalidHeaderParsing() throws Exception {
        final CharArrayBuffer buf = new CharArrayBuffer(16);
        buf.clear();
        buf.append("");
        try {
            new BufferedHeader(buf, false);
            Assert.fail("ParseException should have been thrown");
        } catch (final ParseException e) {
            //expected
        }
        buf.clear();
        buf.append("blah");
        try {
            new BufferedHeader(buf, false);
            Assert.fail("ParseException should have been thrown");
        } catch (final ParseException e) {
            //expected
        }
        buf.clear();
        buf.append(":");
        try {
            new BufferedHeader(buf, false);
            Assert.fail("ParseException should have been thrown");
        } catch (final ParseException e) {
            //expected
        }
        buf.clear();
        buf.append("   :");
        try {
            new BufferedHeader(buf, false);
            Assert.fail("ParseException should have been thrown");
        } catch (final ParseException e) {
            //expected
        }
        buf.clear();
        buf.append(": blah");
        try {
            new BufferedHeader(buf, false);
            Assert.fail("ParseException should have been thrown");
        } catch (final ParseException e) {
            //expected
        }
        buf.clear();
        buf.append(" : blah");
        try {
            new BufferedHeader(buf, false);
            Assert.fail("ParseException should have been thrown");
        } catch (final ParseException e) {
            //expected
        }
        buf.clear();
        buf.append("header : blah");
        try {
            new BufferedHeader(buf, true);
            Assert.fail("ParseException should have been thrown");
        } catch (final ParseException e) {
            //expected
        }
    }

}

