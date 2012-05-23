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

package org.apache.http.impl.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;

import org.apache.http.Consts;
import org.apache.http.impl.SessionInputBufferMock;
import org.apache.http.impl.SessionOutputBufferMock;
import org.apache.http.io.HttpTransportMetrics;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.util.CharArrayBuffer;
import org.junit.Assert;
import org.junit.Test;

public class TestSessionBuffers {

    @Test
    public void testInit() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new SessionOutputBufferMock(out);
        try {
            new SessionOutputBufferMock(null, new BasicHttpParams());
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            //expected
        }
        try {
            new SessionOutputBufferMock(out, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            //expected
        }
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        new SessionInputBufferMock(in, 10);
        try {
            new SessionInputBufferMock(in, -10);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            //expected
        }
        try {
            new SessionOutputBufferMock(out, -10);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            //expected
        }
        try {
            new SessionInputBufferMock((InputStream)null, 1024);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            //expected
        }
        try {
            new SessionInputBufferMock(in, 10, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            //expected
        }
    }

    @Test
    public void testBasicBufferProperties() throws Exception {
        SessionInputBufferMock inbuffer = new SessionInputBufferMock(new byte[] { 1, 2 , 3});
        Assert.assertEquals(SessionInputBufferMock.BUFFER_SIZE, inbuffer.capacity());
        Assert.assertEquals(SessionInputBufferMock.BUFFER_SIZE, inbuffer.available());
        Assert.assertEquals(0, inbuffer.length());
        inbuffer.read();
        Assert.assertEquals(SessionInputBufferMock.BUFFER_SIZE - 2, inbuffer.available());
        Assert.assertEquals(2, inbuffer.length());

        SessionOutputBufferMock outbuffer = new SessionOutputBufferMock();
        Assert.assertEquals(SessionOutputBufferMock.BUFFER_SIZE, outbuffer.capacity());
        Assert.assertEquals(SessionOutputBufferMock.BUFFER_SIZE, outbuffer.available());
        Assert.assertEquals(0, outbuffer.length());
        outbuffer.write(new byte[] {1, 2, 3});
        Assert.assertEquals(SessionOutputBufferMock.BUFFER_SIZE - 3, outbuffer.available());
        Assert.assertEquals(3, outbuffer.length());
    }

    @Test
    public void testBasicReadWriteLine() throws Exception {

        String[] teststrs = new String[5];
        teststrs[0] = "Hello";
        teststrs[1] = "This string should be much longer than the size of the output buffer " +
                "which is only 16 bytes for this test";
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < 15; i++) {
            buffer.append("123456789 ");
        }
        buffer.append("and stuff like that");
        teststrs[2] = buffer.toString();
        teststrs[3] = "";
        teststrs[4] = "And goodbye";

        CharArrayBuffer chbuffer = new CharArrayBuffer(16);
        SessionOutputBufferMock outbuffer = new SessionOutputBufferMock();
        for (int i = 0; i < teststrs.length; i++) {
            chbuffer.clear();
            chbuffer.append(teststrs[i]);
            outbuffer.writeLine(chbuffer);
        }
        //these write operations should have no effect
        outbuffer.writeLine((String)null);
        outbuffer.writeLine((CharArrayBuffer)null);
        outbuffer.flush();

        HttpTransportMetrics tmetrics = outbuffer.getMetrics();
        long bytesWritten = tmetrics.getBytesTransferred();
        long expected = 0;
        for (int i = 0; i < teststrs.length; i++) {
            expected += (teststrs[i].length() + 2/*CRLF*/);
        }
        Assert.assertEquals(expected, bytesWritten);

        SessionInputBufferMock inbuffer = new SessionInputBufferMock(
                outbuffer.getData());

        for (int i = 0; i < teststrs.length; i++) {
            Assert.assertEquals(teststrs[i], inbuffer.readLine());
        }

        Assert.assertNull(inbuffer.readLine());
        Assert.assertNull(inbuffer.readLine());
        tmetrics = inbuffer.getMetrics();
        long bytesRead = tmetrics.getBytesTransferred();
        Assert.assertEquals(expected, bytesRead);
    }

    @Test
    public void testComplexReadWriteLine() throws Exception {
        SessionOutputBufferMock outbuffer = new SessionOutputBufferMock();
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

        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < 14; i++) {
            buffer.append("a");
        }
        String s1 = buffer.toString();
        buffer.append("\r\n");
        outbuffer.write(buffer.toString().getBytes("US-ASCII"));
        outbuffer.flush();
        bytesWritten = outbuffer.getMetrics().getBytesTransferred();
        Assert.assertEquals(8 + 14 +2, bytesWritten);

        buffer.setLength(0);
        for (int i = 0; i < 15; i++) {
            buffer.append("a");
        }
        String s2 = buffer.toString();
        buffer.append("\r\n");
        outbuffer.write(buffer.toString().getBytes("US-ASCII"));
        outbuffer.flush();
        bytesWritten = outbuffer.getMetrics().getBytesTransferred();
        Assert.assertEquals(8 + 14 + 2 + 15 + 2 , bytesWritten);

        buffer.setLength(0);
        for (int i = 0; i < 16; i++) {
            buffer.append("a");
        }
        String s3 = buffer.toString();
        buffer.append("\r\n");
        outbuffer.write(buffer.toString().getBytes("US-ASCII"));
        outbuffer.flush();
        bytesWritten = outbuffer.getMetrics().getBytesTransferred();
        Assert.assertEquals(8 + 14 + 2 + 15 + 2 + 16 + 2, bytesWritten);

        outbuffer.write(new byte[] {'a'});
        outbuffer.flush();
        bytesWritten = outbuffer.getMetrics().getBytesTransferred();
        Assert.assertEquals(8 + 14 + 2 + 15 + 2 + 16 + 2 + 1, bytesWritten);

        SessionInputBufferMock inbuffer = new SessionInputBufferMock(
                outbuffer.getData());

        Assert.assertEquals("a", inbuffer.readLine());
        Assert.assertEquals("", inbuffer.readLine());
        Assert.assertEquals("\r", inbuffer.readLine());
        Assert.assertEquals("", inbuffer.readLine());
        Assert.assertEquals(s1, inbuffer.readLine());
        Assert.assertEquals(s2, inbuffer.readLine());
        Assert.assertEquals(s3, inbuffer.readLine());
        Assert.assertEquals("a", inbuffer.readLine());
        Assert.assertNull(inbuffer.readLine());
        Assert.assertNull(inbuffer.readLine());
        long bytesRead = inbuffer.getMetrics().getBytesTransferred();
        Assert.assertEquals(bytesWritten, bytesRead);
    }

    @Test
    public void testBasicReadWriteLineLargeBuffer() throws Exception {

        String[] teststrs = new String[5];
        teststrs[0] = "Hello";
        teststrs[1] = "This string should be much longer than the size of the output buffer " +
                "which is only 16 bytes for this test";
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < 15; i++) {
            buffer.append("123456789 ");
        }
        buffer.append("and stuff like that");
        teststrs[2] = buffer.toString();
        teststrs[3] = "";
        teststrs[4] = "And goodbye";

        CharArrayBuffer chbuffer = new CharArrayBuffer(16);
        SessionOutputBufferMock outbuffer = new SessionOutputBufferMock();
        for (int i = 0; i < teststrs.length; i++) {
            chbuffer.clear();
            chbuffer.append(teststrs[i]);
            outbuffer.writeLine(chbuffer);
        }
        //these write operations should have no effect
        outbuffer.writeLine((String)null);
        outbuffer.writeLine((CharArrayBuffer)null);
        outbuffer.flush();

        long bytesWritten = outbuffer.getMetrics().getBytesTransferred();
        long expected = 0;
        for (int i = 0; i < teststrs.length; i++) {
            expected += (teststrs[i].length() + 2/*CRLF*/);
        }
        Assert.assertEquals(expected, bytesWritten);

        SessionInputBufferMock inbuffer = new SessionInputBufferMock(
                outbuffer.getData(), 1024);

        for (int i = 0; i < teststrs.length; i++) {
            Assert.assertEquals(teststrs[i], inbuffer.readLine());
        }
        Assert.assertNull(inbuffer.readLine());
        Assert.assertNull(inbuffer.readLine());
        long bytesRead = inbuffer.getMetrics().getBytesTransferred();
        Assert.assertEquals(expected, bytesRead);
    }

    @Test
    public void testReadWriteBytes() throws Exception {
        // make the buffer larger than that of outbuffer
        byte[] out = new byte[40];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte)('0' + i);
        }
        SessionOutputBufferMock outbuffer = new SessionOutputBufferMock();
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
        long bytesWritten = outbuffer.getMetrics().getBytesTransferred();
        Assert.assertEquals(out.length, bytesWritten);

        byte[] tmp = outbuffer.getData();
        Assert.assertEquals(out.length, tmp.length);
        for (int i = 0; i < out.length; i++) {
            Assert.assertEquals(out[i], tmp[i]);
        }

        SessionInputBufferMock inbuffer = new SessionInputBufferMock(tmp);

        // these read operations will have no effect
        Assert.assertEquals(0, inbuffer.read(null, 0, 10));
        Assert.assertEquals(0, inbuffer.read(null));
        long bytesRead = inbuffer.getMetrics().getBytesTransferred();
        Assert.assertEquals(0, bytesRead);

        byte[] in = new byte[40];
        off = 0;
        remaining = in.length;
        while (remaining > 0) {
            int chunk = 10;
            if (chunk > remaining) {
                chunk = remaining;
            }
            int l = inbuffer.read(in, off, chunk);
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
        byte[] out = new byte[40];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte)(120 + i);
        }
        SessionOutputBufferMock outbuffer = new SessionOutputBufferMock();
        for (int i = 0; i < out.length; i++) {
            outbuffer.write(out[i]);
        }
        outbuffer.flush();
        long bytesWritten = outbuffer.getMetrics().getBytesTransferred();
        Assert.assertEquals(out.length, bytesWritten);

        byte[] tmp = outbuffer.getData();
        Assert.assertEquals(out.length, tmp.length);
        for (int i = 0; i < out.length; i++) {
            Assert.assertEquals(out[i], tmp[i]);
        }

        SessionInputBufferMock inbuffer = new SessionInputBufferMock(tmp);
        byte[] in = new byte[40];
        for (int i = 0; i < in.length; i++) {
            in[i] = (byte)inbuffer.read();
        }
        for (int i = 0; i < out.length; i++) {
            Assert.assertEquals(out[i], in[i]);
        }
        Assert.assertEquals(-1, inbuffer.read());
        Assert.assertEquals(-1, inbuffer.read());
        long bytesRead = inbuffer.getMetrics().getBytesTransferred();
        Assert.assertEquals(out.length, bytesRead);
    }

    @Test
    public void testLineLimit() throws Exception {
        HttpParams params = new BasicHttpParams();
        String s = "a very looooooooooooooooooooooooooooooooooooooong line\r\n     ";
        byte[] tmp = s.getBytes("US-ASCII");
        // no limit
        params.setIntParameter(CoreConnectionPNames.MAX_LINE_LENGTH, 0);
        SessionInputBufferMock inbuffer1 = new SessionInputBufferMock(tmp, 5, params);
        Assert.assertNotNull(inbuffer1.readLine());
        long bytesRead = inbuffer1.getMetrics().getBytesTransferred();
        Assert.assertEquals(60, bytesRead);

        // 15 char limit
        params.setIntParameter(CoreConnectionPNames.MAX_LINE_LENGTH, 15);
        SessionInputBufferMock inbuffer2 = new SessionInputBufferMock(tmp, 5, params);
        try {
            inbuffer2.readLine();
            Assert.fail("IOException should have been thrown");
        } catch (IOException ex) {
            // expected
            bytesRead = inbuffer2.getMetrics().getBytesTransferred();
            Assert.assertEquals(20, bytesRead);
        }
    }

    @Test
    public void testReadLineFringeCase1() throws Exception {
        HttpParams params = new BasicHttpParams();
        String s = "abc\r\n";
        byte[] tmp = s.getBytes("US-ASCII");
        SessionInputBufferMock inbuffer1 = new SessionInputBufferMock(tmp, 128, params);
        Assert.assertEquals('a', inbuffer1.read());
        Assert.assertEquals('b', inbuffer1.read());
        Assert.assertEquals('c', inbuffer1.read());
        Assert.assertEquals('\r', inbuffer1.read());
        Assert.assertEquals("", inbuffer1.readLine());
    }

    static final int SWISS_GERMAN_HELLO [] = {
        0x47, 0x72, 0xFC, 0x65, 0x7A, 0x69, 0x5F, 0x7A, 0xE4, 0x6D, 0xE4
    };

    static final int RUSSIAN_HELLO [] = {
        0x412, 0x441, 0x435, 0x43C, 0x5F, 0x43F, 0x440, 0x438,
        0x432, 0x435, 0x442
    };

    private static String constructString(int [] unicodeChars) {
        StringBuilder buffer = new StringBuilder();
        if (unicodeChars != null) {
            for (int i = 0; i < unicodeChars.length; i++) {
                buffer.append((char)unicodeChars[i]);
            }
        }
        return buffer.toString();
    }

    @Test
    public void testMultibyteCodedReadWriteLine() throws Exception {
        String s1 = constructString(SWISS_GERMAN_HELLO);
        String s2 = constructString(RUSSIAN_HELLO);
        String s3 = "Like hello and stuff";

        HttpParams params = new BasicHttpParams();
        HttpProtocolParams.setHttpElementCharset(params, "UTF-8");

        SessionOutputBufferMock outbuffer = new SessionOutputBufferMock(params);

        CharArrayBuffer chbuffer = new CharArrayBuffer(16);
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
        long bytesWritten = outbuffer.getMetrics().getBytesTransferred();
        long expected = ((s1.getBytes("UTF-8").length + 2)+
                (s2.getBytes("UTF-8").length + 2) +
                (s3.getBytes("UTF-8").length + 2)) * 10;
        Assert.assertEquals(expected, bytesWritten);

        SessionInputBufferMock inbuffer = new SessionInputBufferMock(
                outbuffer.getData(), params);

        for (int i = 0; i < 10; i++) {
            Assert.assertEquals(s1, inbuffer.readLine());
            Assert.assertEquals(s2, inbuffer.readLine());
            Assert.assertEquals(s3, inbuffer.readLine());
        }
        Assert.assertNull(inbuffer.readLine());
        Assert.assertNull(inbuffer.readLine());
        long bytesRead = inbuffer.getMetrics().getBytesTransferred();
        Assert.assertEquals(expected, bytesRead);
    }

    @Test
    public void testMultibyteCodedReadWriteLongLine() throws Exception {
        String s1 = constructString(SWISS_GERMAN_HELLO);
        String s2 = constructString(RUSSIAN_HELLO);
        String s3 = "Like hello and stuff";
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < 1024; i++) {
            buf.append(s1).append(s2).append(s3);
        }
        String s = buf.toString();

        HttpParams params = new BasicHttpParams();
        HttpProtocolParams.setHttpElementCharset(params, "UTF-8");

        SessionOutputBufferMock outbuffer = new SessionOutputBufferMock(params);

        CharArrayBuffer chbuffer = new CharArrayBuffer(16);
        chbuffer.append(s);
        outbuffer.writeLine(chbuffer);
        outbuffer.flush();

        SessionInputBufferMock inbuffer = new SessionInputBufferMock(
                outbuffer.getData(), params);

        Assert.assertEquals(s, inbuffer.readLine());
    }

    @Test
    public void testNonAsciiReadWriteLine() throws Exception {
        String s1 = constructString(SWISS_GERMAN_HELLO);

        HttpParams params = new BasicHttpParams();
        HttpProtocolParams.setHttpElementCharset(params, Consts.ISO_8859_1.name());

        SessionOutputBufferMock outbuffer = new SessionOutputBufferMock(params);

        CharArrayBuffer chbuffer = new CharArrayBuffer(16);
        for (int i = 0; i < 5; i++) {
            chbuffer.clear();
            chbuffer.append(s1);
            outbuffer.writeLine(chbuffer);
        }
        for (int i = 0; i < 5; i++) {
            outbuffer.writeLine(s1);
        }
        chbuffer.clear();
        outbuffer.writeLine(chbuffer);
        outbuffer.flush();
        long bytesWritten = outbuffer.getMetrics().getBytesTransferred();
        long expected = ((s1.toString().getBytes(Consts.ISO_8859_1.name()).length + 2)) * 10 + 2;
        Assert.assertEquals(expected, bytesWritten);

        SessionInputBufferMock inbuffer = new SessionInputBufferMock(
                outbuffer.getData(),
                params);
        HttpProtocolParams.setHttpElementCharset(params, Consts.ISO_8859_1.name());

        CharArrayBuffer buf = new CharArrayBuffer(64);
        for (int i = 0; i < 10; i++) {
            buf.clear();
            int len = inbuffer.readLine(buf);
            Assert.assertEquals(len, SWISS_GERMAN_HELLO.length);
            Assert.assertEquals(s1, buf.toString());
        }
        buf.clear();
        Assert.assertEquals("", inbuffer.readLine());
        Assert.assertNull(inbuffer.readLine());
        Assert.assertNull(inbuffer.readLine());
        long bytesRead = inbuffer.getMetrics().getBytesTransferred();
        Assert.assertEquals(expected, bytesRead);
    }

    @Test
    public void testUnmappableInputAction() throws Exception {
        BasicHttpParams params = new BasicHttpParams();
        String s = "In valid ISO-8859-1 character string because  of Ŵ and ŵ";
        HttpProtocolParams.setHttpElementCharset(params, Consts.ISO_8859_1.name());

        // Action with report
        HttpProtocolParams.setUnmappableInputAction(params, CodingErrorAction.REPORT);
        SessionOutputBufferMock outbuf = new SessionOutputBufferMock(params);
        try {
            outbuf.writeLine(s);
            Assert.fail("Expected CharacterCodingException");
        } catch (CharacterCodingException expected) {
        }

        // Action with ignore
        HttpProtocolParams.setUnmappableInputAction(params, CodingErrorAction.IGNORE);
        outbuf = new SessionOutputBufferMock(params);
        try {
            outbuf.writeLine(s);
        } catch (CharacterCodingException e) {
            Assert.fail("Unexpected CharacterCodingException");
        }

        // Action with replace
        HttpProtocolParams.setUnmappableInputAction(params, CodingErrorAction.REPLACE);
        outbuf = new SessionOutputBufferMock(params);
        try {
            outbuf.writeLine(s);
        } catch (IOException e) {
            Assert.fail("Unexpected CharacterCodingException");
        }
    }

    @Test
    public void testMalformedInputAction() throws Exception {
        byte[] tmp = constructString(SWISS_GERMAN_HELLO).getBytes("UTF-16");
        CharArrayBuffer buf = new CharArrayBuffer(1);

        BasicHttpParams params = new BasicHttpParams();
        HttpProtocolParams.setHttpElementCharset(params, "UTF-8");

        // Action with report
        HttpProtocolParams.setMalformedInputAction(params, CodingErrorAction.REPORT);
        SessionInputBufferMock inbuffer = new SessionInputBufferMock(tmp, params);
        try {
            inbuffer.readLine(buf);
            Assert.fail("Expected CharacterCodingException");
        } catch (CharacterCodingException e) {
        }

        // Action with replace
        HttpProtocolParams.setMalformedInputAction(params, CodingErrorAction.REPLACE);
        inbuffer = new SessionInputBufferMock(tmp, params);
        try {
            inbuffer.readLine(buf);
        } catch (CharacterCodingException e) {
            Assert.fail("Unexpected CharacterCodingException");
        }

        // Action with ignore
        HttpProtocolParams.setMalformedInputAction(params, CodingErrorAction.IGNORE);
        inbuffer = new SessionInputBufferMock(tmp, params);
        try {
            inbuffer.readLine();
        } catch (IOException e) {
            Assert.fail("Unexpected CharacterCodingException");
        }
    }

    @Test
    public void testInvalidCharArrayBuffer() throws Exception {
        SessionInputBufferMock inbuffer = new SessionInputBufferMock(new byte[] {});
        try {
            inbuffer.readLine(null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            //expected
            long bytesRead = inbuffer.getMetrics().getBytesTransferred();
            Assert.assertEquals(0, bytesRead);
        }
    }

}

