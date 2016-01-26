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

package org.apache.hc.core5.http.impl.io;

import java.io.ByteArrayOutputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.http.MessageConstraintException;
import org.apache.hc.core5.http.config.MessageConstraints;
import org.apache.hc.core5.http.io.HttpTransportMetrics;
import org.apache.hc.core5.util.CharArrayBuffer;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class TestSessionInOutBuffers {

    @Test
    public void testBasicBufferProperties() throws Exception {
        final SessionInputBufferMock inbuffer = new SessionInputBufferMock(new byte[] { 1, 2 , 3});
        Assert.assertEquals(SessionInputBufferMock.BUFFER_SIZE, inbuffer.capacity());
        Assert.assertEquals(SessionInputBufferMock.BUFFER_SIZE, inbuffer.available());
        Assert.assertEquals(0, inbuffer.length());
        inbuffer.read();
        Assert.assertEquals(SessionInputBufferMock.BUFFER_SIZE - 2, inbuffer.available());
        Assert.assertEquals(2, inbuffer.length());

        final SessionOutputBufferMock outbuffer = new SessionOutputBufferMock();
        Assert.assertEquals(SessionOutputBufferMock.BUFFER_SIZE, outbuffer.capacity());
        Assert.assertEquals(SessionOutputBufferMock.BUFFER_SIZE, outbuffer.available());
        Assert.assertEquals(0, outbuffer.length());
        outbuffer.write(new byte[] {1, 2, 3});
        Assert.assertEquals(SessionOutputBufferMock.BUFFER_SIZE - 3, outbuffer.available());
        Assert.assertEquals(3, outbuffer.length());
    }

    @Test
    public void testBasicReadWriteLine() throws Exception {

        final String[] teststrs = new String[5];
        teststrs[0] = "Hello";
        teststrs[1] = "This string should be much longer than the size of the output buffer " +
                "which is only 16 bytes for this test";
        final StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < 15; i++) {
            buffer.append("123456789 ");
        }
        buffer.append("and stuff like that");
        teststrs[2] = buffer.toString();
        teststrs[3] = "";
        teststrs[4] = "And goodbye";

        final CharArrayBuffer chbuffer = new CharArrayBuffer(16);
        final SessionOutputBufferMock outbuffer = new SessionOutputBufferMock();
        for (final String teststr : teststrs) {
            chbuffer.clear();
            chbuffer.append(teststr);
            outbuffer.writeLine(chbuffer);
        }
        //these write operations should have no effect
        outbuffer.writeLine(null);
        outbuffer.flush();

        HttpTransportMetrics tmetrics = outbuffer.getMetrics();
        final long bytesWritten = tmetrics.getBytesTransferred();
        long expected = 0;
        for (final String teststr : teststrs) {
            expected += (teststr.length() + 2/*CRLF*/);
        }
        Assert.assertEquals(expected, bytesWritten);

        final SessionInputBufferMock inbuffer = new SessionInputBufferMock(
                outbuffer.getData());

        for (final String teststr : teststrs) {
            chbuffer.clear();
            inbuffer.readLine(chbuffer);
            Assert.assertEquals(teststr, chbuffer.toString());
        }

        chbuffer.clear();
        Assert.assertEquals(-1, inbuffer.readLine(chbuffer));
        chbuffer.clear();
        Assert.assertEquals(-1, inbuffer.readLine(chbuffer));
        tmetrics = inbuffer.getMetrics();
        final long bytesRead = tmetrics.getBytesTransferred();
        Assert.assertEquals(expected, bytesRead);
    }

    @Test
    public void testComplexReadWriteLine() throws Exception {
        final SessionOutputBufferMock outbuffer = new SessionOutputBufferMock();
        outbuffer.write(new byte[] {'a', '\n'});
        outbuffer.write(new byte[] {'\r', '\n'});
        outbuffer.write(new byte[] {'\r', '\r', '\n'});
        outbuffer.write(new byte[] {'\n'});
        //these write operations should have no effect
        outbuffer.write(null);
        outbuffer.write(null, 0, 12);

        outbuffer.flush();

        long bytesWritten = outbuffer.getMetrics().getBytesTransferred();
        Assert.assertEquals(8, bytesWritten);

        final StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < 14; i++) {
            buffer.append("a");
        }
        final String s1 = buffer.toString();
        buffer.append("\r\n");
        outbuffer.write(buffer.toString().getBytes(StandardCharsets.US_ASCII));
        outbuffer.flush();
        bytesWritten = outbuffer.getMetrics().getBytesTransferred();
        Assert.assertEquals(8 + 14 +2, bytesWritten);

        buffer.setLength(0);
        for (int i = 0; i < 15; i++) {
            buffer.append("a");
        }
        final String s2 = buffer.toString();
        buffer.append("\r\n");
        outbuffer.write(buffer.toString().getBytes(StandardCharsets.US_ASCII));
        outbuffer.flush();
        bytesWritten = outbuffer.getMetrics().getBytesTransferred();
        Assert.assertEquals(8 + 14 + 2 + 15 + 2 , bytesWritten);

        buffer.setLength(0);
        for (int i = 0; i < 16; i++) {
            buffer.append("a");
        }
        final String s3 = buffer.toString();
        buffer.append("\r\n");
        outbuffer.write(buffer.toString().getBytes(StandardCharsets.US_ASCII));
        outbuffer.flush();
        bytesWritten = outbuffer.getMetrics().getBytesTransferred();
        Assert.assertEquals(8 + 14 + 2 + 15 + 2 + 16 + 2, bytesWritten);

        outbuffer.write(new byte[] {'a'});
        outbuffer.flush();
        bytesWritten = outbuffer.getMetrics().getBytesTransferred();
        Assert.assertEquals(8 + 14 + 2 + 15 + 2 + 16 + 2 + 1, bytesWritten);

        final SessionInputBufferMock inbuffer = new SessionInputBufferMock(outbuffer.getData());

        final CharArrayBuffer chbuffer = new CharArrayBuffer(16);
        chbuffer.clear();
        inbuffer.readLine(chbuffer);
        Assert.assertEquals("a", chbuffer.toString());
        chbuffer.clear();
        inbuffer.readLine(chbuffer);
        Assert.assertEquals("", chbuffer.toString());
        chbuffer.clear();
        inbuffer.readLine(chbuffer);
        Assert.assertEquals("\r", chbuffer.toString());
        chbuffer.clear();
        inbuffer.readLine(chbuffer);
        Assert.assertEquals("", chbuffer.toString());
        chbuffer.clear();
        inbuffer.readLine(chbuffer);
        Assert.assertEquals(s1, chbuffer.toString());
        chbuffer.clear();
        inbuffer.readLine(chbuffer);
        Assert.assertEquals(s2, chbuffer.toString());
        chbuffer.clear();
        inbuffer.readLine(chbuffer);
        Assert.assertEquals(s3, chbuffer.toString());
        chbuffer.clear();
        inbuffer.readLine(chbuffer);
        Assert.assertEquals("a", chbuffer.toString());
        chbuffer.clear();
        inbuffer.readLine(chbuffer);
        Assert.assertEquals(-1, inbuffer.readLine(chbuffer));
        chbuffer.clear();
        inbuffer.readLine(chbuffer);
        Assert.assertEquals(-1, inbuffer.readLine(chbuffer));
        final long bytesRead = inbuffer.getMetrics().getBytesTransferred();
        Assert.assertEquals(bytesWritten, bytesRead);
    }

    @Test
    public void testBasicReadWriteLineLargeBuffer() throws Exception {

        final String[] teststrs = new String[5];
        teststrs[0] = "Hello";
        teststrs[1] = "This string should be much longer than the size of the output buffer " +
                "which is only 16 bytes for this test";
        final StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < 15; i++) {
            buffer.append("123456789 ");
        }
        buffer.append("and stuff like that");
        teststrs[2] = buffer.toString();
        teststrs[3] = "";
        teststrs[4] = "And goodbye";

        final CharArrayBuffer chbuffer = new CharArrayBuffer(16);
        final SessionOutputBufferMock outbuffer = new SessionOutputBufferMock();
        for (final String teststr : teststrs) {
            chbuffer.clear();
            chbuffer.append(teststr);
            outbuffer.writeLine(chbuffer);
        }
        //these write operations should have no effect
        outbuffer.writeLine(null);
        outbuffer.flush();

        final long bytesWritten = outbuffer.getMetrics().getBytesTransferred();
        long expected = 0;
        for (final String teststr : teststrs) {
            expected += (teststr.length() + 2/*CRLF*/);
        }
        Assert.assertEquals(expected, bytesWritten);

        final SessionInputBufferMock inbuffer = new SessionInputBufferMock(
                outbuffer.getData(), 1024);

        for (final String teststr : teststrs) {
            chbuffer.clear();
            inbuffer.readLine(chbuffer);
            Assert.assertEquals(teststr, chbuffer.toString());
        }
        chbuffer.clear();
        Assert.assertEquals(-1, inbuffer.readLine(chbuffer));
        chbuffer.clear();
        Assert.assertEquals(-1, inbuffer.readLine(chbuffer));
        final long bytesRead = inbuffer.getMetrics().getBytesTransferred();
        Assert.assertEquals(expected, bytesRead);
    }

    @Test
    public void testReadWriteBytes() throws Exception {
        // make the buffer larger than that of outbuffer
        final byte[] out = new byte[40];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte)('0' + i);
        }
        final SessionOutputBufferMock outbuffer = new SessionOutputBufferMock();
        int off = 0;
        int remaining = out.length;
        while (remaining > 0) {
            int chunk = 10;
            if (chunk > remaining) {
                chunk = remaining;
            }
            outbuffer.write(out, off, chunk);
            off += chunk;
            remaining -= chunk;
        }
        outbuffer.flush();
        final long bytesWritten = outbuffer.getMetrics().getBytesTransferred();
        Assert.assertEquals(out.length, bytesWritten);

        final byte[] tmp = outbuffer.getData();
        Assert.assertEquals(out.length, tmp.length);
        for (int i = 0; i < out.length; i++) {
            Assert.assertEquals(out[i], tmp[i]);
        }

        final SessionInputBufferMock inbuffer = new SessionInputBufferMock(tmp);

        // these read operations will have no effect
        Assert.assertEquals(0, inbuffer.read(null, 0, 10));
        Assert.assertEquals(0, inbuffer.read(null));
        long bytesRead = inbuffer.getMetrics().getBytesTransferred();
        Assert.assertEquals(0, bytesRead);

        final byte[] in = new byte[40];
        off = 0;
        remaining = in.length;
        while (remaining > 0) {
            int chunk = 10;
            if (chunk > remaining) {
                chunk = remaining;
            }
            final int l = inbuffer.read(in, off, chunk);
            if (l == -1) {
                break;
            }
            off += l;
            remaining -= l;
        }
        for (int i = 0; i < out.length; i++) {
            Assert.assertEquals(out[i], in[i]);
        }
        Assert.assertEquals(-1, inbuffer.read(tmp));
        Assert.assertEquals(-1, inbuffer.read(tmp));
        bytesRead = inbuffer.getMetrics().getBytesTransferred();
        Assert.assertEquals(out.length, bytesRead);
    }

    @Test
    public void testReadWriteByte() throws Exception {
        // make the buffer larger than that of outbuffer
        final byte[] out = new byte[40];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte)(120 + i);
        }
        final SessionOutputBufferMock outbuffer = new SessionOutputBufferMock();
        for (final byte element : out) {
            outbuffer.write(element);
        }
        outbuffer.flush();
        final long bytesWritten = outbuffer.getMetrics().getBytesTransferred();
        Assert.assertEquals(out.length, bytesWritten);

        final byte[] tmp = outbuffer.getData();
        Assert.assertEquals(out.length, tmp.length);
        for (int i = 0; i < out.length; i++) {
            Assert.assertEquals(out[i], tmp[i]);
        }

        final SessionInputBufferMock inbuffer = new SessionInputBufferMock(tmp);
        final byte[] in = new byte[40];
        for (int i = 0; i < in.length; i++) {
            in[i] = (byte)inbuffer.read();
        }
        for (int i = 0; i < out.length; i++) {
            Assert.assertEquals(out[i], in[i]);
        }
        Assert.assertEquals(-1, inbuffer.read());
        Assert.assertEquals(-1, inbuffer.read());
        final long bytesRead = inbuffer.getMetrics().getBytesTransferred();
        Assert.assertEquals(out.length, bytesRead);
    }

    @Test
    public void testWriteSmallFragmentBuffering() throws Exception {
        final ByteArrayOutputStream outstream = Mockito.spy(new ByteArrayOutputStream());
        final SessionOutputBufferMock outbuffer = new SessionOutputBufferMock(outstream, 16, 16, null);
        outbuffer.write(1);
        outbuffer.write(2);
        outbuffer.write(new byte[] {1, 2});
        outbuffer.write(new byte[]{3, 4});
        outbuffer.flush();
        Mockito.verify(outstream, Mockito.times(1)).write(
                Mockito.<byte[]>any(), Mockito.anyInt(), Mockito.anyInt());
        Mockito.verify(outstream, Mockito.never()).write(Mockito.anyInt());
    }

    @Test
    public void testWriteSmallFragmentNoBuffering() throws Exception {
        final ByteArrayOutputStream outstream = Mockito.spy(new ByteArrayOutputStream());
        final SessionOutputBufferMock outbuffer = new SessionOutputBufferMock(outstream, 16, 0, null);
        outbuffer.write(1);
        outbuffer.write(2);
        outbuffer.write(new byte[] {1, 2});
        outbuffer.write(new byte[]{3, 4});
        Mockito.verify(outstream, Mockito.times(2)).write(
                Mockito.<byte []>any(), Mockito.anyInt(), Mockito.anyInt());
        Mockito.verify(outstream, Mockito.times(2)).write(Mockito.anyInt());
    }

    @Test
    public void testLineLimit() throws Exception {
        final String s = "a very looooooooooooooooooooooooooooooooooooooooooong line\r\n";
        final byte[] tmp = s.getBytes(StandardCharsets.US_ASCII);
        // no limit
        final SessionInputBufferMock inbuffer1 = new SessionInputBufferMock(tmp, 5,
                MessageConstraints.DEFAULT);
        final CharArrayBuffer chbuffer = new CharArrayBuffer(16);
        inbuffer1.readLine(chbuffer);
        final long bytesRead = inbuffer1.getMetrics().getBytesTransferred();
        Assert.assertEquals(60, bytesRead);

        // 15 char limit
        final SessionInputBufferMock inbuffer2 = new SessionInputBufferMock(tmp, 5,
                MessageConstraints.lineLen(15));
        try {
            chbuffer.clear();
            inbuffer2.readLine(chbuffer);
            Assert.fail("MessageConstraintException expected");
        } catch (final MessageConstraintException ex) {
        }
    }

    @Test
    public void testLineLimit2() throws Exception {
        final String s = "just a line\r\n";
        final byte[] tmp = s.getBytes(StandardCharsets.US_ASCII);
        // no limit
        final SessionInputBufferMock inbuffer1 = new SessionInputBufferMock(tmp, 25,
                MessageConstraints.DEFAULT);
        final CharArrayBuffer chbuffer = new CharArrayBuffer(16);
        inbuffer1.readLine(chbuffer);
        final long bytesRead = inbuffer1.getMetrics().getBytesTransferred();
        Assert.assertEquals(13, bytesRead);

        // 10 char limit
        final SessionInputBufferMock inbuffer2 = new SessionInputBufferMock(tmp, 25,
                MessageConstraints.lineLen(10));
        try {
            chbuffer.clear();
            inbuffer2.readLine(chbuffer);
            Assert.fail("MessageConstraintException expected");
        } catch (final MessageConstraintException ex) {
        }
    }

    @Test
    public void testReadLineFringeCase1() throws Exception {
        final String s = "abc\r\n";
        final byte[] tmp = s.getBytes(StandardCharsets.US_ASCII);
        final SessionInputBufferMock inbuffer1 = new SessionInputBufferMock(tmp, 128);
        Assert.assertEquals('a', inbuffer1.read());
        Assert.assertEquals('b', inbuffer1.read());
        Assert.assertEquals('c', inbuffer1.read());
        Assert.assertEquals('\r', inbuffer1.read());
        final CharArrayBuffer chbuffer = new CharArrayBuffer(16);
        Assert.assertEquals(0, inbuffer1.readLine(chbuffer));
    }

    static final int SWISS_GERMAN_HELLO [] = {
        0x47, 0x72, 0xFC, 0x65, 0x7A, 0x69, 0x5F, 0x7A, 0xE4, 0x6D, 0xE4
    };

    static final int RUSSIAN_HELLO [] = {
        0x412, 0x441, 0x435, 0x43C, 0x5F, 0x43F, 0x440, 0x438,
        0x432, 0x435, 0x442
    };

    private static String constructString(final int [] unicodeChars) {
        final StringBuilder buffer = new StringBuilder();
        if (unicodeChars != null) {
            for (final int unicodeChar : unicodeChars) {
                buffer.append((char)unicodeChar);
            }
        }
        return buffer.toString();
    }

    @Test
    public void testMultibyteCodedReadWriteLine() throws Exception {
        final String s1 = constructString(SWISS_GERMAN_HELLO);
        final String s2 = constructString(RUSSIAN_HELLO);
        final String s3 = "Like hello and stuff";

        final SessionOutputBufferMock outbuffer = new SessionOutputBufferMock(StandardCharsets.UTF_8);

        final CharArrayBuffer chbuffer = new CharArrayBuffer(16);
        for (int i = 0; i < 10; i++) {
            chbuffer.clear();
            chbuffer.append(s1);
            outbuffer.writeLine(chbuffer);
            chbuffer.clear();
            chbuffer.append(s2);
            outbuffer.writeLine(chbuffer);
            chbuffer.clear();
            chbuffer.append(s3);
            outbuffer.writeLine(chbuffer);
        }
        outbuffer.flush();
        final long bytesWritten = outbuffer.getMetrics().getBytesTransferred();
        final long expected = ((s1.getBytes(StandardCharsets.UTF_8).length + 2)+
                (s2.getBytes(StandardCharsets.UTF_8).length + 2) +
                (s3.getBytes(StandardCharsets.UTF_8).length + 2)) * 10;
        Assert.assertEquals(expected, bytesWritten);

        final SessionInputBufferMock inbuffer = new SessionInputBufferMock(
                outbuffer.getData(), StandardCharsets.UTF_8);

        for (int i = 0; i < 10; i++) {
            chbuffer.clear();
            inbuffer.readLine(chbuffer);
            Assert.assertEquals(s1, chbuffer.toString());
            chbuffer.clear();
            inbuffer.readLine(chbuffer);
            Assert.assertEquals(s2, chbuffer.toString());
            chbuffer.clear();
            inbuffer.readLine(chbuffer);
            Assert.assertEquals(s3, chbuffer.toString());
        }
        chbuffer.clear();
        Assert.assertEquals(-1, inbuffer.readLine(chbuffer));
        chbuffer.clear();
        Assert.assertEquals(-1, inbuffer.readLine(chbuffer));
        final long bytesRead = inbuffer.getMetrics().getBytesTransferred();
        Assert.assertEquals(expected, bytesRead);
    }

    @Test
    public void testMultibyteCodedReadWriteLongLine() throws Exception {
        final String s1 = constructString(SWISS_GERMAN_HELLO);
        final String s2 = constructString(RUSSIAN_HELLO);
        final String s3 = "Like hello and stuff";
        final StringBuilder buf = new StringBuilder();
        for (int i = 0; i < 1024; i++) {
            buf.append(s1).append(s2).append(s3);
        }
        final String s = buf.toString();

        final SessionOutputBufferMock outbuffer = new SessionOutputBufferMock(StandardCharsets.UTF_8);

        final CharArrayBuffer chbuffer = new CharArrayBuffer(16);
        chbuffer.append(s);
        outbuffer.writeLine(chbuffer);
        outbuffer.flush();

        final SessionInputBufferMock inbuffer = new SessionInputBufferMock(
                outbuffer.getData(), StandardCharsets.UTF_8);

        chbuffer.clear();
        inbuffer.readLine(chbuffer);
        Assert.assertEquals(s, chbuffer.toString());
    }

    @Test
    public void testNonAsciiReadWriteLine() throws Exception {
        final String s1 = constructString(SWISS_GERMAN_HELLO);

        final SessionOutputBufferMock outbuffer = new SessionOutputBufferMock(StandardCharsets.ISO_8859_1);

        final CharArrayBuffer chbuffer = new CharArrayBuffer(16);
        for (int i = 0; i < 10; i++) {
            chbuffer.clear();
            chbuffer.append(s1);
            outbuffer.writeLine(chbuffer);
        }
        chbuffer.clear();
        outbuffer.writeLine(chbuffer);
        outbuffer.flush();
        final long bytesWritten = outbuffer.getMetrics().getBytesTransferred();
        final long expected = ((s1.getBytes(StandardCharsets.ISO_8859_1).length + 2)) * 10 + 2;
        Assert.assertEquals(expected, bytesWritten);

        final SessionInputBufferMock inbuffer = new SessionInputBufferMock(
                outbuffer.getData(), StandardCharsets.ISO_8859_1);

        for (int i = 0; i < 10; i++) {
            chbuffer.clear();
            final int len = inbuffer.readLine(chbuffer);
            Assert.assertEquals(len, SWISS_GERMAN_HELLO.length);
            Assert.assertEquals(s1, chbuffer.toString());
        }
        chbuffer.clear();
        Assert.assertEquals(0, inbuffer.readLine(chbuffer));
        chbuffer.clear();
        Assert.assertEquals(-1, inbuffer.readLine(chbuffer));
        chbuffer.clear();
        Assert.assertEquals(-1, inbuffer.readLine(chbuffer));
        final long bytesRead = inbuffer.getMetrics().getBytesTransferred();
        Assert.assertEquals(expected, bytesRead);
    }

    @Test(expected=CharacterCodingException.class)
    public void testUnmappableInputActionReport() throws Exception {
        final String s = "This text contains a circumflex \u0302 !!!";
        final CharsetEncoder encoder = StandardCharsets.ISO_8859_1.newEncoder();
        encoder.onMalformedInput(CodingErrorAction.IGNORE);
        encoder.onUnmappableCharacter(CodingErrorAction.REPORT);
        final SessionOutputBufferMock outbuf = new SessionOutputBufferMock(encoder);
        final CharArrayBuffer chbuffer = new CharArrayBuffer(32);
        chbuffer.append(s);
        outbuf.writeLine(chbuffer);
    }

    @Test
    public void testUnmappableInputActionReplace() throws Exception {
        final String s = "This text contains a circumflex \u0302 !!!";
        final CharsetEncoder encoder = StandardCharsets.ISO_8859_1.newEncoder();
        encoder.onMalformedInput(CodingErrorAction.IGNORE);
        encoder.onUnmappableCharacter(CodingErrorAction.REPLACE);
        final SessionOutputBufferMock outbuf = new SessionOutputBufferMock(encoder);
        final CharArrayBuffer chbuffer = new CharArrayBuffer(32);
        chbuffer.append(s);
        outbuf.writeLine(chbuffer);
        outbuf.flush();
        final String result = new String(outbuf.getData(), "ISO-8859-1");
        Assert.assertEquals("This text contains a circumflex ? !!!\r\n", result);
    }

    @Test
    public void testUnmappableInputActionIgnore() throws Exception {
        final String s = "This text contains a circumflex \u0302 !!!";
        final CharsetEncoder encoder = StandardCharsets.ISO_8859_1.newEncoder();
        encoder.onMalformedInput(CodingErrorAction.IGNORE);
        encoder.onUnmappableCharacter(CodingErrorAction.IGNORE);
        final SessionOutputBufferMock outbuf = new SessionOutputBufferMock(encoder);
        final CharArrayBuffer chbuffer = new CharArrayBuffer(32);
        chbuffer.append(s);
        outbuf.writeLine(chbuffer);
        outbuf.flush();
        final String result = new String(outbuf.getData(), "ISO-8859-1");
        Assert.assertEquals("This text contains a circumflex  !!!\r\n", result);
    }

    @Test(expected=CharacterCodingException.class)
    public void testMalformedInputActionReport() throws Exception {
        final byte[] tmp = constructString(SWISS_GERMAN_HELLO).getBytes(StandardCharsets.ISO_8859_1);
        final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
        decoder.onMalformedInput(CodingErrorAction.REPORT);
        decoder.onUnmappableCharacter(CodingErrorAction.IGNORE);
        final SessionInputBufferMock inbuffer = new SessionInputBufferMock(tmp, decoder);
        final CharArrayBuffer chbuffer = new CharArrayBuffer(32);
        inbuffer.readLine(chbuffer);
    }

    @Test
    public void testMalformedInputActionReplace() throws Exception {
        final byte[] tmp = constructString(SWISS_GERMAN_HELLO).getBytes(StandardCharsets.ISO_8859_1);
        final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
        decoder.onMalformedInput(CodingErrorAction.REPLACE);
        decoder.onUnmappableCharacter(CodingErrorAction.IGNORE);
        final SessionInputBufferMock inbuffer = new SessionInputBufferMock(tmp, decoder);
        final CharArrayBuffer chbuffer = new CharArrayBuffer(32);
        inbuffer.readLine(chbuffer);
        Assert.assertEquals("Gr\ufffdezi_z\ufffdm\ufffd", chbuffer.toString());
    }

    @Test
    public void testMalformedInputActionIgnore() throws Exception {
        final byte[] tmp = constructString(SWISS_GERMAN_HELLO).getBytes(StandardCharsets.ISO_8859_1);
        final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
        decoder.onMalformedInput(CodingErrorAction.IGNORE);
        decoder.onUnmappableCharacter(CodingErrorAction.IGNORE);
        final SessionInputBufferMock inbuffer = new SessionInputBufferMock(tmp, decoder);
        final CharArrayBuffer chbuffer = new CharArrayBuffer(32);
        inbuffer.readLine(chbuffer);
        Assert.assertEquals("Grezi_zm", chbuffer.toString());
    }

    @Test
    public void testInvalidCharArrayBuffer() throws Exception {
        final SessionInputBufferMock inbuffer = new SessionInputBufferMock(new byte[] {});
        try {
            inbuffer.readLine(null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            //expected
            final long bytesRead = inbuffer.getMetrics().getBytesTransferred();
            Assert.assertEquals(0, bytesRead);
        }
    }

}

