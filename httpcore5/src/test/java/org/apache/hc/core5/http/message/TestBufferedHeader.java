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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link BufferedHeader}.
 *
 */
class TestBufferedHeader {

    @Test
    void testBasicConstructor() throws Exception {
        final CharArrayBuffer buf = new CharArrayBuffer(32);
        buf.append("name: value");
        final BufferedHeader header = new BufferedHeader(buf, false);
        Assertions.assertEquals("name", header.getName());
        Assertions.assertEquals("value", header.getValue());
        Assertions.assertSame(buf, header.getBuffer());
        Assertions.assertEquals(5, header.getValuePos());
    }

    @Test
    void testSerialization() throws Exception {
        final CharArrayBuffer buf = new CharArrayBuffer(32);
        buf.append("name: value");
        final BufferedHeader orig = new BufferedHeader(buf, false);
        final ByteArrayOutputStream outbuffer = new ByteArrayOutputStream();
        try (ObjectOutputStream outStream = new ObjectOutputStream(outbuffer)) {
            outStream.writeObject(orig);
        }
        final byte[] raw = outbuffer.toByteArray();
        final ByteArrayInputStream inBuffer = new ByteArrayInputStream(raw);
        final ObjectInputStream inStream = new ObjectInputStream(inBuffer);
        final BufferedHeader clone = (BufferedHeader) inStream.readObject();
        Assertions.assertEquals(orig.getName(), clone.getName());
        Assertions.assertEquals(orig.getValue(), clone.getValue());
    }

    @Test
    void testInvalidHeaderParsing() {
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
    void testCRLFNullInHeaderValue() throws Exception {
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

    @ParameterizedTest
    @ValueSource(strings = {
            "Content-Length", "X_Custom", "X.Custom", "X-1", "a", "ABCxyz09",
            "!#$%&'*+-.^_`|~"})
    void testTokenName(final String name) throws Exception {
        final CharArrayBuffer buf = new CharArrayBuffer(32);
        buf.append(name);
        buf.append(": value");
        Assertions.assertEquals(name, new BufferedHeader(buf, true).getName());
        Assertions.assertEquals(name, new BufferedHeader(buf, false).getName());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Foo Bar", "Foo\tBar", "X\u0001Y", "X\u0000Y", "Foo\rBar", "Foo\nBar", "Foo\u007f",
            "F\u00e9", "F\u0100", "F\u0080",
            "Foo;", "Foo(", "Foo)", "Foo,", "Foo/", "Foo<", "Foo=", "Foo>", "Foo?", "Foo@",
            "Foo[", "Foo\\", "Foo]", "Foo{", "Foo}", "Foo\""})
    void testNonTokenNameRejected(final String name) {
        final CharArrayBuffer buf = new CharArrayBuffer(32);
        buf.append(name);
        buf.append(": value");
        Assertions.assertThrows(ParseException.class, () -> new BufferedHeader(buf, true));
        Assertions.assertThrows(ParseException.class, () -> new BufferedHeader(buf, false));
    }

    @Test
    void testWhitespaceAroundNamePreserved() throws Exception {
        // Whitespace before the colon is rejected in strict mode and trimmed in lax mode.
        final CharArrayBuffer buf = new CharArrayBuffer(32);
        buf.append("Foo : v");
        Assertions.assertThrows(ParseException.class, () -> new BufferedHeader(buf, true));
        Assertions.assertEquals("Foo", new BufferedHeader(buf, false).getName());
        // Leading whitespace is trimmed; a folded line never reaches the header parser on its own.
        final CharArrayBuffer buf2 = new CharArrayBuffer(32);
        buf2.append("  Foo: v");
        Assertions.assertEquals("Foo", new BufferedHeader(buf2, true).getName());
    }

}
