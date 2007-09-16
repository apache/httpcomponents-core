/*
 * $HeadURL$
 * $Revision$
 * $Date$
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

package org.apache.http.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.apache.http.io.HttpTransportMetrics;

import org.apache.http.params.BasicHttpParams;
import org.apache.http.mockup.SessionInputBufferMockup;
import org.apache.http.mockup.SessionOutputBufferMockup;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.CharArrayBuffer;

public class TestSessionBuffers extends TestCase {

    public TestSessionBuffers(String testName) {
        super(testName);
    }

    // ------------------------------------------------------- TestCase Methods

    public static Test suite() {
        return new TestSuite(TestSessionBuffers.class);
    }

    // ------------------------------------------------------------------- Main
    public static void main(String args[]) {
        String[] testCaseName = { TestSessionBuffers.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    public void testInit() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new SessionOutputBufferMockup(out); 
        try {
            new SessionOutputBufferMockup(null, new BasicHttpParams()); 
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            //expected
        }
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        new SessionInputBufferMockup(in, 10); 
        try {
            new SessionInputBufferMockup(in, -10); 
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            //expected
        }
        try {
            new SessionOutputBufferMockup(out, -10); 
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            //expected
        }
        try {
            new SessionInputBufferMockup((InputStream)null, 1024); 
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            //expected
        }
    }
    
    public void testBasicReadWriteLine() throws Exception {
        
        String[] teststrs = new String[5];
        teststrs[0] = "Hello";
        teststrs[1] = "This string should be much longer than the size of the output buffer " +
                "which is only 16 bytes for this test";
        StringBuffer buffer = new StringBuffer();
        for (int i = 0; i < 15; i++) {
            buffer.append("123456789 ");
        }
        buffer.append("and stuff like that");
        teststrs[2] = buffer.toString();
        teststrs[3] = "";
        teststrs[4] = "And goodbye";
        
        CharArrayBuffer chbuffer = new CharArrayBuffer(16); 
        SessionOutputBufferMockup outbuffer = new SessionOutputBufferMockup(); 
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
        long writedBytes = tmetrics.getBytesTransferred();
        long expWrited = 0;
        for (int i = 0; i < teststrs.length; i++) {
            expWrited += (teststrs[i].length() + 2/*CRLF*/);
        }
        assertEquals(expWrited, writedBytes);
        
        SessionInputBufferMockup inbuffer = new SessionInputBufferMockup(
        		outbuffer.getData());

        for (int i = 0; i < teststrs.length; i++) {
            assertEquals(teststrs[i], inbuffer.readLine());
        }
        
        assertNull(inbuffer.readLine());
        assertNull(inbuffer.readLine());
        tmetrics = inbuffer.getMetrics();
        long readedBytes = tmetrics.getBytesTransferred();
        assertEquals(expWrited, readedBytes);
    }

    public void testComplexReadWriteLine() throws Exception {
        SessionOutputBufferMockup outbuffer = new SessionOutputBufferMockup(); 
        outbuffer.write(new byte[] {'a', '\n'});
        outbuffer.write(new byte[] {'\r', '\n'});
        outbuffer.write(new byte[] {'\r', '\r', '\n'});
        outbuffer.write(new byte[] {'\n'});
        //these write operations should have no effect
        outbuffer.write(null);
        outbuffer.write(null, 0, 12);
        
        outbuffer.flush();

        long writedBytes = outbuffer.getMetrics().getBytesTransferred();
        assertEquals(8, writedBytes);
        
        StringBuffer buffer = new StringBuffer();
        for (int i = 0; i < 14; i++) {
            buffer.append("a");
        }
        String s1 = buffer.toString();
        buffer.append("\r\n");
        outbuffer.write(buffer.toString().getBytes("US-ASCII"));
        outbuffer.flush();
        writedBytes = outbuffer.getMetrics().getBytesTransferred();
        assertEquals(8 + 14 +2, writedBytes);

        buffer.setLength(0);
        for (int i = 0; i < 15; i++) {
            buffer.append("a");
        }
        String s2 = buffer.toString();
        buffer.append("\r\n");
        outbuffer.write(buffer.toString().getBytes("US-ASCII"));
        outbuffer.flush();
        writedBytes = outbuffer.getMetrics().getBytesTransferred();
        assertEquals(8 + 14 + 2 + 15 + 2 , writedBytes);

        buffer.setLength(0);
        for (int i = 0; i < 16; i++) {
            buffer.append("a");
        }
        String s3 = buffer.toString();
        buffer.append("\r\n");
        outbuffer.write(buffer.toString().getBytes("US-ASCII"));
        outbuffer.flush();
        writedBytes = outbuffer.getMetrics().getBytesTransferred();
        assertEquals(8 + 14 + 2 + 15 + 2 + 16 + 2, writedBytes);

        outbuffer.write(new byte[] {'a'});
        outbuffer.flush();
        writedBytes = outbuffer.getMetrics().getBytesTransferred();
        assertEquals(8 + 14 + 2 + 15 + 2 + 16 + 2 + 1, writedBytes);
        
        SessionInputBufferMockup inbuffer = new SessionInputBufferMockup(
        		outbuffer.getData());

        assertEquals("a", inbuffer.readLine());
        assertEquals("", inbuffer.readLine());
        assertEquals("\r", inbuffer.readLine());
        assertEquals("", inbuffer.readLine());
        assertEquals(s1, inbuffer.readLine());
        assertEquals(s2, inbuffer.readLine());
        assertEquals(s3, inbuffer.readLine());
        assertEquals("a", inbuffer.readLine());
        assertNull(inbuffer.readLine());
        assertNull(inbuffer.readLine());
        long received = inbuffer.getMetrics().getBytesTransferred();
        assertEquals(writedBytes, received);
    }
    
    public void testBasicReadWriteLineLargeBuffer() throws Exception {
        
        String[] teststrs = new String[5];
        teststrs[0] = "Hello";
        teststrs[1] = "This string should be much longer than the size of the output buffer " +
                "which is only 16 bytes for this test";
        StringBuffer buffer = new StringBuffer();
        for (int i = 0; i < 15; i++) {
            buffer.append("123456789 ");
        }
        buffer.append("and stuff like that");
        teststrs[2] = buffer.toString();
        teststrs[3] = "";
        teststrs[4] = "And goodbye";
        
        CharArrayBuffer chbuffer = new CharArrayBuffer(16); 
        SessionOutputBufferMockup outbuffer = new SessionOutputBufferMockup(); 
        for (int i = 0; i < teststrs.length; i++) {
            chbuffer.clear();
            chbuffer.append(teststrs[i]);
            outbuffer.writeLine(chbuffer);
        }
        //these write operations should have no effect
        outbuffer.writeLine((String)null);
        outbuffer.writeLine((CharArrayBuffer)null);
        outbuffer.flush();
        
        long writedBytes = outbuffer.getMetrics().getBytesTransferred();
        long expWrited = 0;
        for (int i = 0; i < teststrs.length; i++) {
            expWrited += (teststrs[i].length() + 2/*CRLF*/);
        }
        assertEquals(expWrited, writedBytes);
        
        SessionInputBufferMockup inbuffer = new SessionInputBufferMockup(
        		outbuffer.getData(), 1024);

        for (int i = 0; i < teststrs.length; i++) {
            assertEquals(teststrs[i], inbuffer.readLine());
        }
        assertNull(inbuffer.readLine());
        assertNull(inbuffer.readLine());
        long readedBytes = inbuffer.getMetrics().getBytesTransferred();
        assertEquals(expWrited, readedBytes);
    }

    public void testReadWriteBytes() throws Exception {
        // make the buffer larger than that of outbuffer
        byte[] out = new byte[40];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte)('0' + i);
        }
        SessionOutputBufferMockup outbuffer = new SessionOutputBufferMockup();
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
        long writedBytes = outbuffer.getMetrics().getBytesTransferred();
        assertEquals(out.length, writedBytes);

        byte[] tmp = outbuffer.getData();
        assertEquals(out.length, tmp.length);
        for (int i = 0; i < out.length; i++) {
            assertEquals(out[i], tmp[i]);
        }
        
        SessionInputBufferMockup inbuffer = new SessionInputBufferMockup(tmp);

        // these read operations will have no effect
        assertEquals(0, inbuffer.read(null, 0, 10));
        assertEquals(0, inbuffer.read(null));        
        long receivedBytes = inbuffer.getMetrics().getBytesTransferred();
        assertEquals(0, receivedBytes);
        
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
            assertEquals(out[i], in[i]);
        }
        assertEquals(-1, inbuffer.read(tmp));
        assertEquals(-1, inbuffer.read(tmp));
        receivedBytes = inbuffer.getMetrics().getBytesTransferred();
        assertEquals(out.length, receivedBytes);
    }
    
    public void testReadWriteByte() throws Exception {
        // make the buffer larger than that of outbuffer
        byte[] out = new byte[40];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte)(120 + i);
        }
        SessionOutputBufferMockup outbuffer = new SessionOutputBufferMockup();
        for (int i = 0; i < out.length; i++) {
            outbuffer.write(out[i]);
        }
        outbuffer.flush();
        long writedBytes = outbuffer.getMetrics().getBytesTransferred();
        assertEquals(out.length, writedBytes);

        byte[] tmp = outbuffer.getData();
        assertEquals(out.length, tmp.length);
        for (int i = 0; i < out.length; i++) {
            assertEquals(out[i], tmp[i]);
        }
        
        SessionInputBufferMockup inbuffer = new SessionInputBufferMockup(tmp);
        byte[] in = new byte[40];
        for (int i = 0; i < in.length; i++) {
            in[i] = (byte)inbuffer.read();
        }
        for (int i = 0; i < out.length; i++) {
            assertEquals(out[i], in[i]);
        }
        assertEquals(-1, inbuffer.read());
        assertEquals(-1, inbuffer.read());
        long readedBytes = inbuffer.getMetrics().getBytesTransferred();
        assertEquals(out.length, readedBytes);
    }

    public void testLineLimit() throws Exception {
        HttpParams params = new BasicHttpParams();
        String s = "a very looooooooooooooooooooooooooooooooooooooong line\r\n     ";
        byte[] tmp = s.getBytes("US-ASCII"); 
        // no limit
        params.setIntParameter(CoreConnectionPNames.MAX_LINE_LENGTH, 0);
        SessionInputBufferMockup inbuffer1 = new SessionInputBufferMockup(tmp, 5, params);
        assertNotNull(inbuffer1.readLine());
        long readedBytes = inbuffer1.getMetrics().getBytesTransferred();
        assertEquals(60, readedBytes);
        
        // 15 char limit
        params.setIntParameter(CoreConnectionPNames.MAX_LINE_LENGTH, 15);
        SessionInputBufferMockup inbuffer2 = new SessionInputBufferMockup(tmp, 5, params);
        try {
            inbuffer2.readLine();
            fail("IOException should have been thrown");
        } catch (IOException ex) {
            // expected
            readedBytes = inbuffer2.getMetrics().getBytesTransferred();
            assertEquals(20, readedBytes);
        }
    }

    static final int SWISS_GERMAN_HELLO [] = {
        0x47, 0x72, 0xFC, 0x65, 0x7A, 0x69, 0x5F, 0x7A, 0xE4, 0x6D, 0xE4
    };
        
    static final int RUSSIAN_HELLO [] = {
        0x412, 0x441, 0x435, 0x43C, 0x5F, 0x43F, 0x440, 0x438, 
        0x432, 0x435, 0x442 
    }; 
    
    private static String constructString(int [] unicodeChars) {
        StringBuffer buffer = new StringBuffer();
        if (unicodeChars != null) {
            for (int i = 0; i < unicodeChars.length; i++) {
                buffer.append((char)unicodeChars[i]); 
            }
        }
        return buffer.toString();
    }

    public void testMultibyteCodedReadWriteLine() throws Exception {
        String s1 = constructString(SWISS_GERMAN_HELLO);
        String s2 = constructString(RUSSIAN_HELLO);
        String s3 = "Like hello and stuff";
        
        HttpParams params = new BasicHttpParams(null);
        HttpProtocolParams.setHttpElementCharset(params, "UTF-8");
        
        SessionOutputBufferMockup outbuffer = new SessionOutputBufferMockup(params);

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
        long writedBytes = outbuffer.getMetrics().getBytesTransferred();
        long expBytes = ((s1.toString().getBytes("UTF-8").length + 2)+
                (s2.toString().getBytes("UTF-8").length + 2) +
                (s3.toString().getBytes("UTF-8").length + 2)) * 10;
        assertEquals(expBytes, writedBytes);
        
        SessionInputBufferMockup inbuffer = new SessionInputBufferMockup(
        		outbuffer.getData(), params);

        for (int i = 0; i < 10; i++) {
            assertEquals(s1, inbuffer.readLine());
            assertEquals(s2, inbuffer.readLine());
            assertEquals(s3, inbuffer.readLine());
        }
        assertNull(inbuffer.readLine());
        assertNull(inbuffer.readLine());
        long readedBytes = inbuffer.getMetrics().getBytesTransferred();
        assertEquals(expBytes, readedBytes);
    }

    public void testNonAsciiReadWriteLine() throws Exception {
        String s1 = constructString(SWISS_GERMAN_HELLO);
        
        HttpParams params = new BasicHttpParams(null);
        HttpProtocolParams.setHttpElementCharset(params, HTTP.ISO_8859_1);
        
        SessionOutputBufferMockup outbuffer = new SessionOutputBufferMockup(params);

        CharArrayBuffer chbuffer = new CharArrayBuffer(16); 
        for (int i = 0; i < 10; i++) {
            chbuffer.clear();
            chbuffer.append(s1);
            outbuffer.writeLine(chbuffer);
        }
        outbuffer.flush();
        long writedBytes = outbuffer.getMetrics().getBytesTransferred();
        long expBytes = ((s1.toString().getBytes(HTTP.ISO_8859_1).length + 2)) * 10;
        assertEquals(expBytes, writedBytes);
        
        SessionInputBufferMockup inbuffer = new SessionInputBufferMockup(
                outbuffer.getData(),
                params);
        HttpProtocolParams.setHttpElementCharset(params, HTTP.ISO_8859_1);

        for (int i = 0; i < 10; i++) {
            assertEquals(s1, inbuffer.readLine());
        }
        assertNull(inbuffer.readLine());
        assertNull(inbuffer.readLine());
        long readedBytes = inbuffer.getMetrics().getBytesTransferred();
        assertEquals(expBytes, readedBytes);
    }

    public void testInvalidCharArrayBuffer() throws Exception {
        SessionInputBufferMockup inbuffer = new SessionInputBufferMockup(new byte[] {});
        try {
            inbuffer.readLine(null); 
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            //expected
            long readedBytes = inbuffer.getMetrics().getBytesTransferred();
            assertEquals(0, readedBytes);
        }
    }
    
}

