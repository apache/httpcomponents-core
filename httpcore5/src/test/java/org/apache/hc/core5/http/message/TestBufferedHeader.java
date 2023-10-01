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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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
        Assertions.assertEquals("name", header.getName());
        Assertions.assertEquals("value", header.getValue());
        Assertions.assertSame(buf, header.getBuffer());
        Assertions.assertEquals(5, header.getValuePos());
    }

    @Test
    public void testSerialization() throws Exception {
        final CharArrayBuffer buf = new CharArrayBuffer(32);
        buf.append("name: value");
        final BufferedHeader orig = new BufferedHeader(buf, false);
        final ByteArrayOutputStream outbuffer = new ByteArrayOutputStream();
        final ObjectOutputStream outStream = new ObjectOutputStream(outbuffer);
        outStream.writeObject(orig);
        outStream.close();
        final byte[] raw = outbuffer.toByteArray();
        final ByteArrayInputStream inBuffer = new ByteArrayInputStream(raw);
        final ObjectInputStream inStream = new ObjectInputStream(inBuffer);
        final BufferedHeader clone = (BufferedHeader) inStream.readObject();
        Assertions.assertEquals(orig.getName(), clone.getName());
        Assertions.assertEquals(orig.getValue(), clone.getValue());
    }

    @Test
    public void testInvalidHeaderParsing() throws Exception {
        final CharArrayBuffer buf = new CharArrayBuffer(16);
        buf.clear();
        buf.append("");
        Assertions.assertThrows(ParseException.class, () -> new BufferedHeader(buf, false));
        buf.clear();
        buf.append("blah");
        Assertions.assertThrows(ParseException.class, () -> new BufferedHeader(buf, false));
        buf.clear();
        buf.append(":");
        Assertions.assertThrows(ParseException.class, () -> new BufferedHeader(buf, false));
        buf.clear();
        buf.append("   :");
        Assertions.assertThrows(ParseException.class, () -> new BufferedHeader(buf, false));
        buf.clear();
        buf.append(": blah");
        Assertions.assertThrows(ParseException.class, () -> new BufferedHeader(buf, false));
        buf.clear();
        buf.append(" : blah");
        Assertions.assertThrows(ParseException.class, () -> new BufferedHeader(buf, false));
        buf.clear();
        buf.append("header : blah");
        Assertions.assertThrows(ParseException.class, () -> new BufferedHeader(buf, true));
    }

    @Test
    public void testCRLFNullInHeaderValue() throws Exception {
        final CharArrayBuffer buf = new CharArrayBuffer(16);
        buf.clear();
        buf.append("name:  blah\0blah  ");
        final BufferedHeader header1 = new BufferedHeader(buf, false);
        Assertions.assertEquals("name", header1.getName());
        Assertions.assertEquals("blah blah", header1.getValue());

        buf.clear();
        buf.append("name:  blah\rblah  ");
        final BufferedHeader header2 = new BufferedHeader(buf, false);
        Assertions.assertEquals("name", header2.getName());
        Assertions.assertEquals("blah blah", header2.getValue());

        buf.clear();
        buf.append("name:  blah\nblah  ");
        final BufferedHeader header3 = new BufferedHeader(buf, false);
        Assertions.assertEquals("name", header3.getName());
        Assertions.assertEquals("blah blah", header3.getValue());
    }

}

