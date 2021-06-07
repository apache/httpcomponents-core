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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.http.MessageConstraintException;
import org.apache.hc.core5.http.impl.BasicHttpTransportMetrics;
import org.apache.hc.core5.http.io.HttpTransportMetrics;
import org.apache.hc.core5.http.io.SessionInputBuffer;
import org.apache.hc.core5.http.io.SessionOutputBuffer;
import org.apache.hc.core5.util.CharArrayBuffer;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

public class TestSessionInOutBuffers {

    @Test
    public void testBasicBufferProperties() throws Exception {
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(new byte[] { 1, 2 , 3});
        Assert.assertEquals(16, inBuffer.capacity());
        Assert.assertEquals(16, inBuffer.available());
        Assert.assertEquals(0, inBuffer.length());
        inBuffer.read(inputStream);
        Assert.assertEquals(14, inBuffer.available());
        Assert.assertEquals(2, inBuffer.length());

        final SessionOutputBuffer outbuffer = new SessionOutputBufferImpl(16);
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Assert.assertEquals(16, outbuffer.capacity());
        Assert.assertEquals(16, outbuffer.available());
        Assert.assertEquals(0, outbuffer.length());
        outbuffer.write(new byte[] {1, 2, 3}, outputStream);
        Assert.assertEquals(13, outbuffer.available());
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
        final SessionOutputBuffer outbuffer = new SessionOutputBufferImpl(16);
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        for (final String teststr : teststrs) {
            chbuffer.clear();
            chbuffer.append(teststr);
            outbuffer.writeLine(chbuffer, outputStream);
        }
        //these write operations should have no effect
        outbuffer.writeLine(null, outputStream);
        outbuffer.flush(outputStream);

        HttpTransportMetrics tmetrics = outbuffer.getMetrics();
        final long bytesWritten = tmetrics.getBytesTransferred();
        long expected = 0;
        for (final String teststr : teststrs) {
            expected += (teststr.length() + 2/*CRLF*/);
        }
        Assert.assertEquals(expected, bytesWritten);

        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());

        for (final String teststr : teststrs) {
            chbuffer.clear();
            inBuffer.readLine(chbuffer, inputStream);
            Assert.assertEquals(teststr, chbuffer.toString());
        }

        chbuffer.clear();
        Assert.assertEquals(-1, inBuffer.readLine(chbuffer, inputStream));
        chbuffer.clear();
        Assert.assertEquals(-1, inBuffer.readLine(chbuffer, inputStream));
        tmetrics = inBuffer.getMetrics();
        final long bytesRead = tmetrics.getBytesTransferred();
        Assert.assertEquals(expected, bytesRead);
    }

    @Test
    public void testComplexReadWriteLine() throws Exception {
        final SessionOutputBuffer outbuffer = new SessionOutputBufferImpl(16);
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        outbuffer.write(new byte[] {'a', '\n'}, outputStream);
        outbuffer.write(new byte[] {'\r', '\n'}, outputStream);
        outbuffer.write(new byte[] {'\r', '\r', '\n'}, outputStream);
        outbuffer.write(new byte[] {'\n'},outputStream);
        //these write operations should have no effect
        outbuffer.write(null, outputStream);
        outbuffer.write(null, 0, 12, outputStream);

        outbuffer.flush(outputStream);

        long bytesWritten = outbuffer.getMetrics().getBytesTransferred();
        Assert.assertEquals(8, bytesWritten);

        final StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < 14; i++) {
            buffer.append("a");
        }
        final String s1 = buffer.toString();
        buffer.append("\r\n");
        outbuffer.write(buffer.toString().getBytes(StandardCharsets.US_ASCII), outputStream);
        outbuffer.flush(outputStream);
        bytesWritten = outbuffer.getMetrics().getBytesTransferred();
        Assert.assertEquals(8 + 14 +2, bytesWritten);

        buffer.setLength(0);
        for (int i = 0; i < 15; i++) {
            buffer.append("a");
        }
        final String s2 = buffer.toString();
        buffer.append("\r\n");
        outbuffer.write(buffer.toString().getBytes(StandardCharsets.US_ASCII), outputStream);
        outbuffer.flush(outputStream);
        bytesWritten = outbuffer.getMetrics().getBytesTransferred();
        Assert.assertEquals(8 + 14 + 2 + 15 + 2 , bytesWritten);

        buffer.setLength(0);
        for (int i = 0; i < 16; i++) {
            buffer.append("a");
        }
        final String s3 = buffer.toString();
        buffer.append("\r\n");
        outbuffer.write(buffer.toString().getBytes(StandardCharsets.US_ASCII), outputStream);
        outbuffer.flush(outputStream);
        bytesWritten = outbuffer.getMetrics().getBytesTransferred();
        Assert.assertEquals(8 + 14 + 2 + 15 + 2 + 16 + 2, bytesWritten);

        outbuffer.write(new byte[] {'a'}, outputStream);
        outbuffer.flush(outputStream);
        bytesWritten = outbuffer.getMetrics().getBytesTransferred();
        Assert.assertEquals(8 + 14 + 2 + 15 + 2 + 16 + 2 + 1, bytesWritten);

        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());

        final CharArrayBuffer chbuffer = new CharArrayBuffer(16);
        chbuffer.clear();
        inBuffer.readLine(chbuffer, inputStream);
        Assert.assertEquals("a", chbuffer.toString());
        chbuffer.clear();
        inBuffer.readLine(chbuffer, inputStream);
        Assert.assertEquals("", chbuffer.toString());
        chbuffer.clear();
        inBuffer.readLine(chbuffer, inputStream);
        Assert.assertEquals("\r", chbuffer.toString());
        chbuffer.clear();
        inBuffer.readLine(chbuffer, inputStream);
        Assert.assertEquals("", chbuffer.toString());
        chbuffer.clear();
        inBuffer.readLine(chbuffer, inputStream);
        Assert.assertEquals(s1, chbuffer.toString());
        chbuffer.clear();
        inBuffer.readLine(chbuffer, inputStream);
        Assert.assertEquals(s2, chbuffer.toString());
        chbuffer.clear();
        inBuffer.readLine(chbuffer, inputStream);
        Assert.assertEquals(s3, chbuffer.toString());
        chbuffer.clear();
        inBuffer.readLine(chbuffer, inputStream);
        Assert.assertEquals("a", chbuffer.toString());
        chbuffer.clear();
        inBuffer.readLine(chbuffer, inputStream);
        Assert.assertEquals(-1, inBuffer.readLine(chbuffer, inputStream));
        chbuffer.clear();
        inBuffer.readLine(chbuffer, inputStream);
        Assert.assertEquals(-1, inBuffer.readLine(chbuffer, inputStream));
        final long bytesRead = inBuffer.getMetrics().getBytesTransferred();
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
        final SessionOutputBuffer outbuffer = new SessionOutputBufferImpl(16);
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        for (final String teststr : teststrs) {
            chbuffer.clear();
            chbuffer.append(teststr);
            outbuffer.writeLine(chbuffer, outputStream);
        }
        //these write operations should have no effect
        outbuffer.writeLine(null, outputStream);
        outbuffer.flush(outputStream);

        final long bytesWritten = outbuffer.getMetrics().getBytesTransferred();
        long expected = 0;
        for (final String teststr : teststrs) {
            expected += (teststr.length() + 2/*CRLF*/);
        }
        Assert.assertEquals(expected, bytesWritten);

        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(1024);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());

        for (final String teststr : teststrs) {
            chbuffer.clear();
            inBuffer.readLine(chbuffer, inputStream);
            Assert.assertEquals(teststr, chbuffer.toString());
        }
        chbuffer.clear();
        Assert.assertEquals(-1, inBuffer.readLine(chbuffer, inputStream));
        chbuffer.clear();
        Assert.assertEquals(-1, inBuffer.readLine(chbuffer, inputStream));
        final long bytesRead = inBuffer.getMetrics().getBytesTransferred();
        Assert.assertEquals(expected, bytesRead);
    }

    @Test
    public void testReadWriteBytes() throws Exception {
        // make the buffer larger than that of outbuffer
        final byte[] out = new byte[40];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte)('0' + i);
        }
        final SessionOutputBuffer outbuffer = new SessionOutputBufferImpl(16);
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        int off = 0;
        int remaining = out.length;
        while (remaining > 0) {
            int chunk = 10;
            if (chunk > remaining) {
                chunk = remaining;
            }
            outbuffer.write(out, off, chunk, outputStream);
            off += chunk;
            remaining -= chunk;
        }
        outbuffer.flush(outputStream);
        final long bytesWritten = outbuffer.getMetrics().getBytesTransferred();
        Assert.assertEquals(out.length, bytesWritten);

        final byte[] tmp = outputStream.toByteArray();
        Assert.assertEquals(out.length, tmp.length);
        for (int i = 0; i < out.length; i++) {
            Assert.assertEquals(out[i], tmp[i]);
        }

        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(tmp);

        // these read operations will have no effect
        Assert.assertEquals(0, inBuffer.read(null, 0, 10, inputStream));
        Assert.assertEquals(0, inBuffer.read(null, inputStream));
        long bytesRead = inBuffer.getMetrics().getBytesTransferred();
        Assert.assertEquals(0, bytesRead);

        final byte[] in = new byte[40];
        off = 0;
        remaining = in.length;
        while (remaining > 0) {
            int chunk = 10;
            if (chunk > remaining) {
                chunk = remaining;
            }
            final int readLen = inBuffer.read(in, off, chunk, inputStream);
            if (readLen == -1) {
                break;
            }
            off += readLen;
            remaining -= readLen;
        }
        for (int i = 0; i < out.length; i++) {
            Assert.assertEquals(out[i], in[i]);
        }
        Assert.assertEquals(-1, inBuffer.read(tmp, inputStream));
        Assert.assertEquals(-1, inBuffer.read(tmp, inputStream));
        bytesRead = inBuffer.getMetrics().getBytesTransferred();
        Assert.assertEquals(out.length, bytesRead);
    }

    @Test
    public void testReadWriteByte() throws Exception {
        // make the buffer larger than that of outbuffer
        final byte[] out = new byte[40];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte)(120 + i);
        }
        final SessionOutputBuffer outbuffer = new SessionOutputBufferImpl(16);
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        for (final byte element : out) {
            outbuffer.write(element, outputStream);
        }
        outbuffer.flush(outputStream);
        final long bytesWritten = outbuffer.getMetrics().getBytesTransferred();
        Assert.assertEquals(out.length, bytesWritten);

        final byte[] tmp = outputStream.toByteArray();
        Assert.assertEquals(out.length, tmp.length);
        for (int i = 0; i < out.length; i++) {
            Assert.assertEquals(out[i], tmp[i]);
        }

        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(tmp);
        final byte[] in = new byte[40];
        for (int i = 0; i < in.length; i++) {
            in[i] = (byte)inBuffer.read(inputStream);
        }
        for (int i = 0; i < out.length; i++) {
            Assert.assertEquals(out[i], in[i]);
        }
        Assert.assertEquals(-1, inBuffer.read(inputStream));
        Assert.assertEquals(-1, inBuffer.read(inputStream));
        final long bytesRead = inBuffer.getMetrics().getBytesTransferred();
        Assert.assertEquals(out.length, bytesRead);
    }

    @Test
    public void testWriteSmallFragmentBuffering() throws Exception {
        final OutputStream outputStream = Mockito.mock(OutputStream.class);
        final SessionOutputBuffer outbuffer = new SessionOutputBufferImpl(new BasicHttpTransportMetrics(), 16, 16, null);
        outbuffer.write(1, outputStream);
        outbuffer.write(2, outputStream);
        outbuffer.write(new byte[] {1, 2}, outputStream);
        outbuffer.write(new byte[]{3, 4}, outputStream);
        outbuffer.flush(outputStream);
        Mockito.verify(outputStream, Mockito.times(1)).write(ArgumentMatchers.any(), ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt());
        Mockito.verify(outputStream, Mockito.never()).write(ArgumentMatchers.anyInt());
    }

    @Test
    public void testWriteSmallFragmentNoBuffering() throws Exception {
        final OutputStream outputStream = Mockito.mock(OutputStream.class);
        final SessionOutputBuffer outbuffer = new SessionOutputBufferImpl(new BasicHttpTransportMetrics(), 16, 0, null);
        outbuffer.write(1, outputStream);
        outbuffer.write(2, outputStream);
        outbuffer.write(new byte[] {1, 2}, outputStream);
        outbuffer.write(new byte[]{3, 4}, outputStream);
        Mockito.verify(outputStream, Mockito.times(2)).write(ArgumentMatchers.any(), ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt());
        Mockito.verify(outputStream, Mockito.times(2)).write(ArgumentMatchers.anyInt());
    }

    @Test
    public void testLineLimit() throws Exception {
        final String s = "a very looooooooooooooooooooooooooooooooooooooooooong line\r\n";
        final byte[] tmp = s.getBytes(StandardCharsets.US_ASCII);
        // no limit
        final SessionInputBuffer inBuffer1 = new SessionInputBufferImpl(5);
        final InputStream inputStream1 = new ByteArrayInputStream(tmp);
        final CharArrayBuffer chbuffer = new CharArrayBuffer(16);
        inBuffer1.readLine(chbuffer, inputStream1);
        final long bytesRead = inBuffer1.getMetrics().getBytesTransferred();
        Assert.assertEquals(60, bytesRead);

        // 15 char limit
        final SessionInputBuffer inBuffer2 = new SessionInputBufferImpl(5, 15);
        final InputStream inputStream2 = new ByteArrayInputStream(tmp);
        chbuffer.clear();
        Assert.assertThrows(MessageConstraintException.class, () ->
                inBuffer2.readLine(chbuffer, inputStream2));
    }

    @Test
    public void testLineLimit2() throws Exception {
        final String s = "just a line\r\n";
        final byte[] tmp = s.getBytes(StandardCharsets.US_ASCII);
        // no limit
        final SessionInputBuffer inBuffer1 = new SessionInputBufferImpl(25);
        final InputStream inputStream1 = new ByteArrayInputStream(tmp);
        final CharArrayBuffer chbuffer = new CharArrayBuffer(16);
        inBuffer1.readLine(chbuffer, inputStream1);
        final long bytesRead = inBuffer1.getMetrics().getBytesTransferred();
        Assert.assertEquals(13, bytesRead);

        // 10 char limit
        final SessionInputBuffer inBuffer2 = new SessionInputBufferImpl(25, 10);
        final InputStream inputStream2 = new ByteArrayInputStream(tmp);
        chbuffer.clear();
        Assert.assertThrows(MessageConstraintException.class, () ->
                inBuffer2.readLine(chbuffer, inputStream2));
    }

    @Test //HTTPCORE-472
    public void testLineLimit3() throws Exception {
        final String s = "012345678\r\nblaaaaaaaaaaaaaaaaaah";
        final byte[] tmp = s.getBytes(StandardCharsets.US_ASCII);
        final SessionInputBuffer inBuffer1 = new SessionInputBufferImpl(128);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(tmp);
        final CharArrayBuffer chbuffer = new CharArrayBuffer(16);
        inBuffer1.readLine(chbuffer, inputStream);
        Assert.assertEquals("012345678", chbuffer.toString());
    }

    @Test
    public void testReadLineFringeCase1() throws Exception {
        final String s = "abc\r\n";
        final byte[] tmp = s.getBytes(StandardCharsets.US_ASCII);
        final SessionInputBuffer inBuffer1 = new SessionInputBufferImpl(128);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(tmp);
        Assert.assertEquals('a', inBuffer1.read(inputStream));
        Assert.assertEquals('b', inBuffer1.read(inputStream));
        Assert.assertEquals('c', inBuffer1.read(inputStream));
        Assert.assertEquals('\r', inBuffer1.read(inputStream));
        final CharArrayBuffer chbuffer = new CharArrayBuffer(16);
        Assert.assertEquals(0, inBuffer1.readLine(chbuffer, inputStream));
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

        final SessionOutputBuffer outbuffer = new SessionOutputBufferImpl(16, StandardCharsets.UTF_8.newEncoder());
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        final CharArrayBuffer chbuffer = new CharArrayBuffer(16);
        for (int i = 0; i < 10; i++) {
            chbuffer.clear();
            chbuffer.append(s1);
            outbuffer.writeLine(chbuffer, outputStream);
            chbuffer.clear();
            chbuffer.append(s2);
            outbuffer.writeLine(chbuffer, outputStream);
            chbuffer.clear();
            chbuffer.append(s3);
            outbuffer.writeLine(chbuffer, outputStream);
        }
        outbuffer.flush(outputStream);
        final long bytesWritten = outbuffer.getMetrics().getBytesTransferred();
        final long expected = ((s1.getBytes(StandardCharsets.UTF_8).length + 2)+
                (s2.getBytes(StandardCharsets.UTF_8).length + 2) +
                (s3.getBytes(StandardCharsets.UTF_8).length + 2)) * 10;
        Assert.assertEquals(expected, bytesWritten);

        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16, StandardCharsets.UTF_8.newDecoder());
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());

        for (int i = 0; i < 10; i++) {
            chbuffer.clear();
            inBuffer.readLine(chbuffer, inputStream);
            Assert.assertEquals(s1, chbuffer.toString());
            chbuffer.clear();
            inBuffer.readLine(chbuffer, inputStream);
            Assert.assertEquals(s2, chbuffer.toString());
            chbuffer.clear();
            inBuffer.readLine(chbuffer, inputStream);
            Assert.assertEquals(s3, chbuffer.toString());
        }
        chbuffer.clear();
        Assert.assertEquals(-1, inBuffer.readLine(chbuffer, inputStream));
        chbuffer.clear();
        Assert.assertEquals(-1, inBuffer.readLine(chbuffer, inputStream));
        final long bytesRead = inBuffer.getMetrics().getBytesTransferred();
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

        final SessionOutputBuffer outbuffer = new SessionOutputBufferImpl(16, StandardCharsets.UTF_8.newEncoder());
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        final CharArrayBuffer chbuffer = new CharArrayBuffer(16);
        chbuffer.append(s);
        outbuffer.writeLine(chbuffer, outputStream);
        outbuffer.flush(outputStream);

        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16, StandardCharsets.UTF_8.newDecoder());
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());

        chbuffer.clear();
        inBuffer.readLine(chbuffer, inputStream);
        Assert.assertEquals(s, chbuffer.toString());
    }

    @Test
    public void testNonAsciiReadWriteLine() throws Exception {
        final String s1 = constructString(SWISS_GERMAN_HELLO);

        final SessionOutputBuffer outbuffer = new SessionOutputBufferImpl(16, StandardCharsets.ISO_8859_1.newEncoder());
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        final CharArrayBuffer chbuffer = new CharArrayBuffer(16);
        for (int i = 0; i < 10; i++) {
            chbuffer.clear();
            chbuffer.append(s1);
            outbuffer.writeLine(chbuffer, outputStream);
        }
        chbuffer.clear();
        outbuffer.writeLine(chbuffer, outputStream);
        outbuffer.flush(outputStream);
        final long bytesWritten = outbuffer.getMetrics().getBytesTransferred();
        final long expected = ((s1.getBytes(StandardCharsets.ISO_8859_1).length + 2)) * 10 + 2;
        Assert.assertEquals(expected, bytesWritten);

        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16, StandardCharsets.ISO_8859_1.newDecoder());
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());

        for (int i = 0; i < 10; i++) {
            chbuffer.clear();
            final int len = inBuffer.readLine(chbuffer, inputStream);
            Assert.assertEquals(len, SWISS_GERMAN_HELLO.length);
            Assert.assertEquals(s1, chbuffer.toString());
        }
        chbuffer.clear();
        Assert.assertEquals(0, inBuffer.readLine(chbuffer, inputStream));
        chbuffer.clear();
        Assert.assertEquals(-1, inBuffer.readLine(chbuffer, inputStream));
        chbuffer.clear();
        Assert.assertEquals(-1, inBuffer.readLine(chbuffer, inputStream));
        final long bytesRead = inBuffer.getMetrics().getBytesTransferred();
        Assert.assertEquals(expected, bytesRead);
    }

    @Test
    public void testUnmappableInputActionReport() throws Exception {
        final String s = "This text contains a circumflex \u0302 !!!";
        final CharsetEncoder encoder = StandardCharsets.ISO_8859_1.newEncoder();
        encoder.onMalformedInput(CodingErrorAction.IGNORE);
        encoder.onUnmappableCharacter(CodingErrorAction.REPORT);
        final SessionOutputBuffer outbuffer = new SessionOutputBufferImpl(16, encoder);
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final CharArrayBuffer chbuffer = new CharArrayBuffer(32);
        chbuffer.append(s);
        Assert.assertThrows(CharacterCodingException.class, () ->
                outbuffer.writeLine(chbuffer, outputStream));
    }

    @Test
    public void testUnmappableInputActionReplace() throws Exception {
        final String s = "This text contains a circumflex \u0302 !!!";
        final CharsetEncoder encoder = StandardCharsets.ISO_8859_1.newEncoder();
        encoder.onMalformedInput(CodingErrorAction.IGNORE);
        encoder.onUnmappableCharacter(CodingErrorAction.REPLACE);
        final SessionOutputBuffer outbuffer = new SessionOutputBufferImpl(16, encoder);
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final CharArrayBuffer chbuffer = new CharArrayBuffer(32);
        chbuffer.append(s);
        outbuffer.writeLine(chbuffer, outputStream);
        outbuffer.flush(outputStream);
        final String result = new String(outputStream.toByteArray(), StandardCharsets.ISO_8859_1);
        Assert.assertEquals("This text contains a circumflex ? !!!\r\n", result);
    }

    @Test
    public void testUnmappableInputActionIgnore() throws Exception {
        final String s = "This text contains a circumflex \u0302 !!!";
        final CharsetEncoder encoder = StandardCharsets.ISO_8859_1.newEncoder();
        encoder.onMalformedInput(CodingErrorAction.IGNORE);
        encoder.onUnmappableCharacter(CodingErrorAction.IGNORE);
        final SessionOutputBuffer outbuffer = new SessionOutputBufferImpl(16, encoder);
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final CharArrayBuffer chbuffer = new CharArrayBuffer(32);
        chbuffer.append(s);
        outbuffer.writeLine(chbuffer, outputStream);
        outbuffer.flush(outputStream);
        final String result = new String(outputStream.toByteArray(), StandardCharsets.ISO_8859_1);
        Assert.assertEquals("This text contains a circumflex  !!!\r\n", result);
    }

    @Test
    public void testMalformedInputActionReport() throws Exception {
        final byte[] tmp = constructString(SWISS_GERMAN_HELLO).getBytes(StandardCharsets.ISO_8859_1);
        final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
        decoder.onMalformedInput(CodingErrorAction.REPORT);
        decoder.onUnmappableCharacter(CodingErrorAction.IGNORE);
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16, decoder);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(tmp);
        final CharArrayBuffer chbuffer = new CharArrayBuffer(32);
        Assert.assertThrows(CharacterCodingException.class, () ->
                inBuffer.readLine(chbuffer, inputStream));
    }

    @Test
    public void testMalformedInputActionReplace() throws Exception {
        final byte[] tmp = constructString(SWISS_GERMAN_HELLO).getBytes(StandardCharsets.ISO_8859_1);
        final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
        decoder.onMalformedInput(CodingErrorAction.REPLACE);
        decoder.onUnmappableCharacter(CodingErrorAction.IGNORE);
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16, decoder);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(tmp);
        final CharArrayBuffer chbuffer = new CharArrayBuffer(32);
        inBuffer.readLine(chbuffer, inputStream);
        Assert.assertEquals("Gr\ufffdezi_z\ufffdm\ufffd", chbuffer.toString());
    }

    @Test
    public void testMalformedInputActionIgnore() throws Exception {
        final byte[] tmp = constructString(SWISS_GERMAN_HELLO).getBytes(StandardCharsets.ISO_8859_1);
        final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
        decoder.onMalformedInput(CodingErrorAction.IGNORE);
        decoder.onUnmappableCharacter(CodingErrorAction.IGNORE);
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16, decoder);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(tmp);
        final CharArrayBuffer chbuffer = new CharArrayBuffer(32);
        inBuffer.readLine(chbuffer, inputStream);
        Assert.assertEquals("Grezi_zm", chbuffer.toString());
    }

}

