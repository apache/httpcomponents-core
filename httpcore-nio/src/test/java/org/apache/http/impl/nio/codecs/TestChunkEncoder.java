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

package org.apache.http.impl.nio.codecs;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;

import junit.framework.TestCase;

import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.impl.nio.reactor.SessionOutputBufferImpl;
import org.apache.http.nio.reactor.SessionOutputBuffer;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EncodingUtils;

/**
 * Simple tests for {@link ChunkEncoder}.
 *
 * 
 * @version $Id$
 */
public class TestChunkEncoder extends TestCase {

    // ------------------------------------------------------------ Constructor
    public TestChunkEncoder(String testName) {
        super(testName);
    }

    // ------------------------------------------------------- TestCase Methods

    private static ByteBuffer wrap(final String s) {
        return ByteBuffer.wrap(EncodingUtils.getAsciiBytes(s));
    }
    
    private static WritableByteChannel newChannel(final ByteArrayOutputStream baos) {
        return Channels.newChannel(baos);
    }
    
    public void testBasicCoding() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(); 
        WritableByteChannel channel = newChannel(baos);
        HttpParams params = new BasicHttpParams();
        SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128, params);
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        ChunkEncoder encoder = new ChunkEncoder(channel, outbuf, metrics);
        
        encoder.write(wrap("12345"));
        encoder.write(wrap("678"));
        encoder.write(wrap("90"));
        encoder.complete();
        
        outbuf.flush(channel);
        
        String s = baos.toString("US-ASCII");
        
        assertTrue(encoder.isCompleted());
        assertEquals("5\r\n12345\r\n3\r\n678\r\n2\r\n90\r\n0\r\n\r\n", s);
    }
    
    public void testChunkNoExceed() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(); 
        WritableByteChannel channel = newChannel(baos);
        HttpParams params = new BasicHttpParams();
        SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 16, params);
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        ChunkEncoder encoder = new ChunkEncoder(channel, outbuf, metrics);
        encoder.write(wrap("1234"));
        encoder.complete();
        
        outbuf.flush(channel);
        
        String s = baos.toString("US-ASCII");
        
        assertTrue(encoder.isCompleted());
        assertEquals("4\r\n1234\r\n0\r\n\r\n", s);    
    }
    
    
    public void testChunkExceed() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(); 
        WritableByteChannel channel = newChannel(baos);
        HttpParams params = new BasicHttpParams();
        SessionOutputBuffer outbuf = new SessionOutputBufferImpl(16, 16, params);
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        ChunkEncoder encoder = new ChunkEncoder(channel, outbuf, metrics);
        
        ByteBuffer src = wrap("0123456789ABCDEF");
        
        assertEquals(6, encoder.write(src));
        assertTrue(src.hasRemaining());
        assertEquals(10, src.remaining());

        assertEquals(6, encoder.write(src));
        assertTrue(src.hasRemaining());
        assertEquals(4, src.remaining());

        assertEquals(4, encoder.write(src));
        assertFalse(src.hasRemaining());
        
        outbuf.flush(channel);
        String s = baos.toString("US-ASCII");
        assertEquals("6\r\n012345\r\n6\r\n6789AB\r\n4\r\nCDEF\r\n", s);    
        
    }

    public void testCodingEmptyBuffer() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(); 
        WritableByteChannel channel = newChannel(baos);
        HttpParams params = new BasicHttpParams();
        SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128, params);
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        ChunkEncoder encoder = new ChunkEncoder(channel, outbuf, metrics);
        
        encoder.write(wrap("12345"));
        encoder.write(wrap("678"));
        encoder.write(wrap("90"));
        
        ByteBuffer empty = ByteBuffer.allocate(100);
        empty.flip();
        encoder.write(empty);
        encoder.write(null);
        
        encoder.complete();
        
        outbuf.flush(channel);
        
        String s = baos.toString("US-ASCII");
        
        assertTrue(encoder.isCompleted());
        assertEquals("5\r\n12345\r\n3\r\n678\r\n2\r\n90\r\n0\r\n\r\n", s);
    }

    public void testCodingCompleted() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(); 
        WritableByteChannel channel = newChannel(baos);
        HttpParams params = new BasicHttpParams();
        SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128, params);
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        ChunkEncoder encoder = new ChunkEncoder(channel, outbuf, metrics);
        
        encoder.write(wrap("12345"));
        encoder.write(wrap("678"));
        encoder.write(wrap("90"));
        encoder.complete();

        try {
            encoder.write(wrap("more stuff"));
            fail("IllegalStateException should have been thrown");
        } catch (IllegalStateException ex) {
            // ignore
        }
        try {
            encoder.complete();
            fail("IllegalStateException should have been thrown");
        } catch (IllegalStateException ex) {
            // ignore
        }
    }

    public void testInvalidConstructor() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(); 
        WritableByteChannel channel = newChannel(baos);
        HttpParams params = new BasicHttpParams();
        SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128, params);

        try {
            new ChunkEncoder(null, null, null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // ignore
        }
        try {
            new ChunkEncoder(channel, null, null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // ignore
        }
        try {
            new ChunkEncoder(channel, outbuf, null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // ignore
        }
    }
    
}
