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

package org.apache.http.impl.nio.codecs;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.impl.nio.reactor.SessionInputBufferImpl;
import org.apache.http.mockup.ReadableByteChannelMockup;
import org.apache.http.nio.reactor.SessionInputBuffer;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;

/**
 * Simple tests for {@link LengthDelimitedDecoder}.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 * 
 * @version $Id$
 */
public class TestLengthDelimitedDecoder extends TestCase {

    // ------------------------------------------------------------ Constructor
    public TestLengthDelimitedDecoder(String testName) {
        super(testName);
    }

    // ------------------------------------------------------------------- Main
    public static void main(String args[]) {
        String[] testCaseName = { TestLengthDelimitedDecoder.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    // ------------------------------------------------------- TestCase Methods

    public static Test suite() {
        return new TestSuite(TestLengthDelimitedDecoder.class);
    }

    private static String convert(final ByteBuffer src) {
        src.flip();
        StringBuffer buffer = new StringBuffer(src.remaining()); 
        while (src.hasRemaining()) {
            buffer.append((char)(src.get() & 0xff));
        }
        return buffer.toString();
    }
    
    private static String readFromFile(final File file) throws Exception {
        FileInputStream filestream = new FileInputStream(file);
        InputStreamReader reader = new InputStreamReader(filestream);
        try {
            StringBuffer buffer = new StringBuffer();
            char[] tmp = new char[2048];
            int l;
            while ((l = reader.read(tmp)) != -1) {
                buffer.append(tmp, 0, l);
            }
            return buffer.toString();
        } finally {
            reader.close();
        }
    }

    public void testBasicDecoding() throws Exception {
        ReadableByteChannel channel = new ReadableByteChannelMockup(
                new String[] {"stuff;", "more stuff"}, "US-ASCII"); 
        HttpParams params = new BasicHttpParams();
        
        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, params); 
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        LengthDelimitedDecoder decoder = new LengthDelimitedDecoder(
                channel, inbuf, metrics, 16); 
        
        ByteBuffer dst = ByteBuffer.allocate(1024); 
        
        int bytesRead = decoder.read(dst);
        assertEquals(6, bytesRead);
        assertEquals("stuff;", convert(dst));
        assertFalse(decoder.isCompleted());
        assertEquals(6, metrics.getBytesTransferred());
        
        dst.clear();
        bytesRead = decoder.read(dst);
        assertEquals(10, bytesRead);
        assertEquals("more stuff", convert(dst));
        assertTrue(decoder.isCompleted());
        assertEquals(16, metrics.getBytesTransferred());
        
        dst.clear();
        bytesRead = decoder.read(dst);
        assertEquals(-1, bytesRead);
        assertTrue(decoder.isCompleted());
        assertEquals(16, metrics.getBytesTransferred());
    }
    
    public void testCodingBeyondContentLimit() throws Exception {
        ReadableByteChannel channel = new ReadableByteChannelMockup(
                new String[] {
                        "stuff;", 
                        "more stuff; and a lot more stuff"}, "US-ASCII"); 
        HttpParams params = new BasicHttpParams();
        
        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, params); 
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        LengthDelimitedDecoder decoder = new LengthDelimitedDecoder(
                channel, inbuf, metrics, 16); 
        
        ByteBuffer dst = ByteBuffer.allocate(1024); 
        
        int bytesRead = decoder.read(dst);
        assertEquals(6, bytesRead);
        assertEquals("stuff;", convert(dst));
        assertFalse(decoder.isCompleted());
        assertEquals(6, metrics.getBytesTransferred());
        
        dst.clear();
        bytesRead = decoder.read(dst);
        assertEquals(10, bytesRead);
        assertEquals("more stuff", convert(dst));
        assertTrue(decoder.isCompleted());
        assertEquals(16, metrics.getBytesTransferred());
        
        dst.clear();
        bytesRead = decoder.read(dst);
        assertEquals(-1, bytesRead);
        assertTrue(decoder.isCompleted());
        assertEquals(16, metrics.getBytesTransferred());
    }

    public void testBasicDecodingSmallBuffer() throws Exception {
        ReadableByteChannel channel = new ReadableByteChannelMockup(
                new String[] {"stuff;", "more stuff"}, "US-ASCII"); 
        HttpParams params = new BasicHttpParams();
        
        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, params); 
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        LengthDelimitedDecoder decoder = new LengthDelimitedDecoder(
                channel, inbuf, metrics, 16); 
        
        ByteBuffer dst = ByteBuffer.allocate(4); 
        
        int bytesRead = decoder.read(dst);
        assertEquals(4, bytesRead);
        assertEquals("stuf", convert(dst));
        assertFalse(decoder.isCompleted());
        assertEquals(4, metrics.getBytesTransferred());
        
        dst.clear();
        bytesRead = decoder.read(dst);
        assertEquals(2, bytesRead);
        assertEquals("f;", convert(dst));
        assertFalse(decoder.isCompleted());
        assertEquals(6, metrics.getBytesTransferred());
        
        dst.clear();
        bytesRead = decoder.read(dst);
        assertEquals(4, bytesRead);
        assertEquals("more", convert(dst));
        assertFalse(decoder.isCompleted());
        assertEquals(10, metrics.getBytesTransferred());

        dst.clear();
        bytesRead = decoder.read(dst);
        assertEquals(4, bytesRead);
        assertEquals(" stu", convert(dst));
        assertFalse(decoder.isCompleted());
        assertEquals(14, metrics.getBytesTransferred());

        dst.clear();
        bytesRead = decoder.read(dst);
        assertEquals(2, bytesRead);
        assertEquals("ff", convert(dst));
        assertTrue(decoder.isCompleted());
        assertEquals(16, metrics.getBytesTransferred());

        dst.clear();
        bytesRead = decoder.read(dst);
        assertEquals(-1, bytesRead);
        assertTrue(decoder.isCompleted());
        assertEquals(16, metrics.getBytesTransferred());
    }
    
    public void testDecodingFromSessionBuffer1() throws Exception {
        ReadableByteChannel channel = new ReadableByteChannelMockup(
                new String[] {"stuff;", "more stuff"}, "US-ASCII"); 
        HttpParams params = new BasicHttpParams();
        
        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, params);
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();

        inbuf.fill(channel);
        
        assertEquals(6, inbuf.length());
        
        LengthDelimitedDecoder decoder = new LengthDelimitedDecoder(
                channel, inbuf, metrics, 16); 
        
        ByteBuffer dst = ByteBuffer.allocate(1024); 
        
        int bytesRead = decoder.read(dst);
        assertEquals(6, bytesRead);
        assertEquals("stuff;", convert(dst));
        assertFalse(decoder.isCompleted());
        assertEquals(0, metrics.getBytesTransferred());
        
        dst.clear();
        bytesRead = decoder.read(dst);
        assertEquals(10, bytesRead);
        assertEquals("more stuff", convert(dst));
        assertTrue(decoder.isCompleted());
        assertEquals(10, metrics.getBytesTransferred());
        
        dst.clear();
        bytesRead = decoder.read(dst);
        assertEquals(-1, bytesRead);
        assertTrue(decoder.isCompleted());
        assertEquals(10, metrics.getBytesTransferred());
    }

    public void testDecodingFromSessionBuffer2() throws Exception {
        ReadableByteChannel channel = new ReadableByteChannelMockup(
                new String[] {
                        "stuff;", 
                        "more stuff; and a lot more stuff"}, "US-ASCII"); 
        HttpParams params = new BasicHttpParams();
        
        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, params);
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();

        inbuf.fill(channel);
        inbuf.fill(channel);
        
        assertEquals(38, inbuf.length());
        
        LengthDelimitedDecoder decoder = new LengthDelimitedDecoder(
                channel, inbuf, metrics, 16); 
        
        ByteBuffer dst = ByteBuffer.allocate(1024); 
        
        int bytesRead = decoder.read(dst);
        assertEquals(16, bytesRead);
        assertEquals("stuff;more stuff", convert(dst));
        assertTrue(decoder.isCompleted());
        assertEquals(0, metrics.getBytesTransferred());
        
        dst.clear();
        bytesRead = decoder.read(dst);
        assertEquals(-1, bytesRead);
        assertTrue(decoder.isCompleted());
        assertEquals(0, metrics.getBytesTransferred());
    }

    public void testBasicDecodingFile() throws Exception {
        ReadableByteChannel channel = new ReadableByteChannelMockup(
                new String[] {"stuff; ", "more stuff; ", "a lot more stuff!!!"}, "US-ASCII"); 
        HttpParams params = new BasicHttpParams();
        
        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, params); 
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        LengthDelimitedDecoder decoder = new LengthDelimitedDecoder(
                channel, inbuf, metrics, 36); 
        
        File fileHandle = File.createTempFile("testFile", ".txt");

        RandomAccessFile testfile = new RandomAccessFile(fileHandle, "rw"); 
        FileChannel fchannel = testfile.getChannel();
            
        long pos = 0;
        while (!decoder.isCompleted()) {
            long bytesRead = decoder.transfer(fchannel, pos, 10);
            if (bytesRead > 0) {
                pos += bytesRead;
            }
        }
        assertEquals(testfile.length(), metrics.getBytesTransferred());
        fchannel.close();
        
        assertEquals("stuff; more stuff; a lot more stuff!", readFromFile(fileHandle));
        
        fileHandle.delete();
    }
    
    public void testDecodingFileWithBufferedSessionData() throws Exception {
        ReadableByteChannel channel = new ReadableByteChannelMockup(
                new String[] {"stuff; ", "more stuff; ", "a lot more stuff!!!"}, "US-ASCII"); 
        HttpParams params = new BasicHttpParams();
        
        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, params); 
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        LengthDelimitedDecoder decoder = new LengthDelimitedDecoder(
                channel, inbuf, metrics, 36); 
        
        int i = inbuf.fill(channel);
        assertEquals(7, i);
        
        File fileHandle = File.createTempFile("testFile", ".txt");

        RandomAccessFile testfile = new RandomAccessFile(fileHandle, "rw"); 
        FileChannel fchannel = testfile.getChannel();
            
        long pos = 0;
        while (!decoder.isCompleted()) {
            long bytesRead = decoder.transfer(fchannel, pos, 10);
            if (bytesRead > 0) {
                pos += bytesRead;
            }
        }
        
        assertEquals(testfile.length() - 7, metrics.getBytesTransferred());
        fchannel.close();
        
        assertEquals("stuff; more stuff; a lot more stuff!", readFromFile(fileHandle));
        
        fileHandle.delete();
    }
    
    public void testDecodingFileWithOffsetAndBufferedSessionData() throws Exception {
        ReadableByteChannel channel = new ReadableByteChannelMockup(
                new String[] {"stuff; ", "more stuff; ", "a lot more stuff!"}, "US-ASCII"); 
        HttpParams params = new BasicHttpParams();
        
        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, params); 
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        LengthDelimitedDecoder decoder = new LengthDelimitedDecoder(
                channel, inbuf, metrics, 36); 
        
        int i = inbuf.fill(channel);
        assertEquals(7, i);
        
        File fileHandle = File.createTempFile("testFile", ".txt");

        RandomAccessFile testfile = new RandomAccessFile(fileHandle, "rw");
        byte[] beginning = "beginning; ".getBytes("US-ASCII");
        testfile.write(beginning);
        testfile.close();
        
        testfile = new RandomAccessFile(fileHandle, "rw");
        FileChannel fchannel = testfile.getChannel();
            
        long pos = beginning.length;
        while (!decoder.isCompleted()) {
            if(testfile.length() < pos)
                testfile.setLength(pos);
            long bytesRead = decoder.transfer(fchannel, pos, 10);
            if (bytesRead > 0) {
                pos += bytesRead;
            }
        }
        
        // count everything except the initial 7 bytes that went to the session buffer
        assertEquals(testfile.length() - 7 - beginning.length, metrics.getBytesTransferred());
        fchannel.close();
        
        assertEquals("beginning; stuff; more stuff; a lot more stuff!", readFromFile(fileHandle));
        
        fileHandle.delete();
    }
    
    public void testWriteBeyondFileSize() throws Exception {
        ReadableByteChannel channel = new ReadableByteChannelMockup(
                new String[] {"a"}, "US-ASCII"); 
        HttpParams params = new BasicHttpParams();
        
        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, params); 
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        LengthDelimitedDecoder decoder = new LengthDelimitedDecoder(
                channel, inbuf, metrics, 1); 
        
        File fileHandle = File.createTempFile("testFile", ".txt");

        RandomAccessFile testfile = new RandomAccessFile(fileHandle, "rw");
        FileChannel fchannel = testfile.getChannel();
        assertEquals(0, testfile.length());
            
        try {
            decoder.transfer(fchannel, 5, 10);
            fail("expected IOException");
        } catch(IOException iox) {}
        
        fileHandle.delete();
    }
    
    public void testCodingBeyondContentLimitFile() throws Exception {
        ReadableByteChannel channel = new ReadableByteChannelMockup(
                new String[] {
                        "stuff;", 
                        "more stuff; and a lot more stuff"}, "US-ASCII"); 
        HttpParams params = new BasicHttpParams();
        
        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, params); 
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        LengthDelimitedDecoder decoder = new LengthDelimitedDecoder(
                channel, inbuf, metrics, 16); 
        
        File fileHandle = File.createTempFile("testFile", ".txt");
        RandomAccessFile testfile  = new RandomAccessFile(fileHandle, "rw");
        FileChannel fchannel = testfile.getChannel();
        
        long bytesRead = decoder.transfer(fchannel, 0, 6);
        assertEquals(6, bytesRead);
        assertFalse(decoder.isCompleted());
        assertEquals(6, metrics.getBytesTransferred());
        
        bytesRead = decoder.transfer(fchannel,0 , 10);
        assertEquals(10, bytesRead);
        assertTrue(decoder.isCompleted());
        assertEquals(16, metrics.getBytesTransferred());
        
        bytesRead = decoder.transfer(fchannel, 0, 1);
        assertEquals(-1, bytesRead);
        assertTrue(decoder.isCompleted());
        assertEquals(16, metrics.getBytesTransferred());
        
        fileHandle.delete();
    }
    
    public void testInvalidConstructor() {
        ReadableByteChannel channel = new ReadableByteChannelMockup(
                new String[] {"stuff;", "more stuff"}, "US-ASCII"); 
        HttpParams params = new BasicHttpParams();
        
        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, params); 
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        try {
            new LengthDelimitedDecoder(null, null, null, 10);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // ignore
        }
        try {
            new LengthDelimitedDecoder(channel, null, null, 10);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // ignore
        }
        try {
            new LengthDelimitedDecoder(channel, inbuf, null, 10);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // ignore
        }
        try {
            new LengthDelimitedDecoder(channel, inbuf, metrics, -10);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // ignore
        }
    }

    public void testInvalidInput() throws Exception {
        String s = "stuff";
        ReadableByteChannel channel = new ReadableByteChannelMockup(
                new String[] {s}, "US-ASCII"); 
        HttpParams params = new BasicHttpParams();
    
        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, params); 
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        LengthDelimitedDecoder decoder = new LengthDelimitedDecoder(
                channel, inbuf, metrics, 3);
        
        try {
            decoder.read(null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }
    
}
