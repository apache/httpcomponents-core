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
import java.io.FileOutputStream;
import java.io.InputStreamReader;
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
public class TestIdentityDecoder extends TestCase {

    // ------------------------------------------------------------ Constructor
    public TestIdentityDecoder(String testName) {
        super(testName);
    }

    // ------------------------------------------------------------------- Main
    public static void main(String args[]) {
        String[] testCaseName = { TestIdentityDecoder.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    // ------------------------------------------------------- TestCase Methods

    public static Test suite() {
        return new TestSuite(TestIdentityDecoder.class);
    }

    private static String convert(final ByteBuffer src) {
        src.flip();
        StringBuffer buffer = new StringBuffer(src.remaining()); 
        while (src.hasRemaining()) {
            buffer.append((char)(src.get() & 0xff));
        }
        return buffer.toString();
    }
    
    private static String readFromFile(final File file, int numChars) throws Exception {
        FileInputStream filestream = new FileInputStream(file);
        InputStreamReader reader = new InputStreamReader(filestream);
        try {
            StringBuffer buffer = new StringBuffer(numChars);
            char[] tmp = new char[Math.min(2048, numChars)];
            int remaining = numChars;
            while (remaining > 0) {
                int l = reader.read(tmp);
                if (l == -1) {
                    break;
                }
                buffer.append(tmp, 0, l);
                remaining =- l;
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
        IdentityDecoder decoder = new IdentityDecoder(channel, inbuf, metrics); 
        
        ByteBuffer dst = ByteBuffer.allocate(1024); 
        
        int bytesRead = decoder.read(dst);
        assertEquals(6, bytesRead);
        assertEquals("stuff;", convert(dst));
        assertFalse(decoder.isCompleted());
        
        dst.clear();
        bytesRead = decoder.read(dst);
        assertEquals(10, bytesRead);
        assertEquals("more stuff", convert(dst));
        assertFalse(decoder.isCompleted());
        
        dst.clear();
        bytesRead = decoder.read(dst);
        assertEquals(-1, bytesRead);
        assertTrue(decoder.isCompleted());

        dst.clear();
        bytesRead = decoder.read(dst);
        assertEquals(-1, bytesRead);
        assertTrue(decoder.isCompleted());
    }
    
    public void testDecodingFromSessionBuffer() throws Exception {
        ReadableByteChannel channel = new ReadableByteChannelMockup(
                new String[] {"stuff;", "more stuff"}, "US-ASCII"); 
        HttpParams params = new BasicHttpParams();
        
        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, params);
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        
        inbuf.fill(channel);
        
        assertEquals(6, inbuf.length());
        
        IdentityDecoder decoder = new IdentityDecoder(channel, inbuf, metrics); 
        
        ByteBuffer dst = ByteBuffer.allocate(1024); 
        
        int bytesRead = decoder.read(dst);
        assertEquals(6, bytesRead);
        assertEquals("stuff;", convert(dst));
        assertFalse(decoder.isCompleted());
        
        dst.clear();
        bytesRead = decoder.read(dst);
        assertEquals(10, bytesRead);
        assertEquals("more stuff", convert(dst));
        assertFalse(decoder.isCompleted());
        
        dst.clear();
        bytesRead = decoder.read(dst);
        assertEquals(-1, bytesRead);
        assertTrue(decoder.isCompleted());

        dst.clear();
        bytesRead = decoder.read(dst);
        assertEquals(-1, bytesRead);
        assertTrue(decoder.isCompleted());
    }

    public void testBasicDecodingFile() throws Exception {
        ReadableByteChannel channel = new ReadableByteChannelMockup(
                new String[] {"stuff;", "more stuff"}, "US-ASCII"); 
        HttpParams params = new BasicHttpParams();
        
        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, params); 
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        IdentityDecoder decoder = new IdentityDecoder(channel, inbuf, metrics); 
        
        File tmpFile = File.createTempFile("testFile", ".txt");
        FileChannel fchannel = new FileOutputStream(tmpFile).getChannel();
            
        long bytesRead = decoder.transfer(fchannel, 0, 6);
        assertEquals(6, bytesRead);
        assertEquals("stuff;", readFromFile(tmpFile, 6));
        assertFalse(decoder.isCompleted());
        
        bytesRead = decoder.transfer(fchannel,0 , 10);
        assertEquals(10, bytesRead);
        assertEquals("more stuff", readFromFile(tmpFile, 10));
        assertFalse(decoder.isCompleted());
        
        bytesRead = decoder.transfer(fchannel, 0, 1);
        assertEquals(0, bytesRead);
        assertTrue(decoder.isCompleted());
        
        bytesRead = decoder.transfer(fchannel, 0, 1);
        assertEquals(0, bytesRead);
        assertTrue(decoder.isCompleted());
        
        tmpFile.delete();
    }
    
    public void testDecodingFromSessionBufferFile() throws Exception {
        ReadableByteChannel channel = new ReadableByteChannelMockup(
                new String[] {"stuff;", "more stuff"}, "US-ASCII"); 
        HttpParams params = new BasicHttpParams();
        
        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, params);
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();

        inbuf.fill(channel);
        
        assertEquals(6, inbuf.length());
        
        IdentityDecoder decoder = new IdentityDecoder(channel, inbuf, metrics); 
        
        File tmpFile = File.createTempFile("testFile", ".txt");
        FileChannel fchannel = new FileOutputStream(tmpFile).getChannel();
            
        long bytesRead = decoder.transfer(fchannel, 0, 6);
        assertEquals(6, bytesRead);
        assertEquals("stuff;", readFromFile(tmpFile, 6));
        assertFalse(decoder.isCompleted());
        
        bytesRead = decoder.transfer(fchannel,0 , 10);
        assertEquals(10, bytesRead);
        assertEquals("more stuff", readFromFile(tmpFile, 10));
        assertFalse(decoder.isCompleted());
        
        bytesRead = decoder.transfer(fchannel, 0, 1);
        assertEquals(0, bytesRead);
        assertTrue(decoder.isCompleted());
        
        bytesRead = decoder.transfer(fchannel, 0, 1);
        assertEquals(0, bytesRead);
        assertTrue(decoder.isCompleted());
        
        tmpFile.delete();
    }

    public void testInvalidConstructor() {
        ReadableByteChannel channel = new ReadableByteChannelMockup(
                new String[] {"stuff;", "more stuff"}, "US-ASCII"); 
        HttpParams params = new BasicHttpParams();
        
        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, params); 
        try {
            new IdentityDecoder(null, null, null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // ignore
        }
        try {
            new IdentityDecoder(channel, null, null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // ignore
        }
        try {
            new IdentityDecoder(channel, inbuf, null);
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
        IdentityDecoder decoder = new IdentityDecoder(channel, inbuf, metrics);
        
        try {
            decoder.read(null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }
    
}
