/*
 * $HeadURL$
 * $Revision$
 * $Date$
 * ====================================================================
 *
 *  Copyright 1999-2006 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.nio.impl.codecs;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.http.nio.impl.SessionOutputBuffer;
import org.apache.http.util.EncodingUtils;

/**
 * Simple tests for {@link ChunkEncoder}.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 * 
 * @version $Id$
 */
public class TestChunkEncoder extends TestCase {

    // ------------------------------------------------------------ Constructor
    public TestChunkEncoder(String testName) {
        super(testName);
    }

    // ------------------------------------------------------------------- Main
    public static void main(String args[]) {
        String[] testCaseName = { TestChunkEncoder.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    // ------------------------------------------------------- TestCase Methods

    public static Test suite() {
        return new TestSuite(TestChunkEncoder.class);
    }

    private static ByteBuffer wrap(final String s) {
        return ByteBuffer.wrap(EncodingUtils.getAsciiBytes(s));
    }
    
    private static WritableByteChannel newChannel(final ByteArrayOutputStream baos) {
        return Channels.newChannel(baos);
    }
    
    public void testBasicCoding() throws Exception {
        SessionOutputBuffer outbuf = new SessionOutputBuffer(1024, 128);
        ChunkEncoder encoder = new ChunkEncoder(outbuf);
        
        encoder.write(wrap("12345"));
        encoder.write(wrap("678"));
        encoder.write(wrap("90"));
        encoder.complete();
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream(); 
        outbuf.flush(newChannel(baos));
        
        String s = baos.toString("US-ASCII");
        
        assertTrue(encoder.isCompleted());
        assertEquals("5\r\n12345\r\n3\r\n678\r\n2\r\n90\r\n0\r\n", s);
    }

    public void testCodingEmptyBuffer() throws Exception {
        SessionOutputBuffer outbuf = new SessionOutputBuffer(1024, 128);
        ChunkEncoder encoder = new ChunkEncoder(outbuf);
        
        encoder.write(wrap("12345"));
        encoder.write(wrap("678"));
        encoder.write(wrap("90"));
        
        ByteBuffer empty = ByteBuffer.allocate(100);
        empty.flip();
        encoder.write(empty);
        encoder.write(null);
        
        encoder.complete();
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream(); 
        outbuf.flush(newChannel(baos));
        
        String s = baos.toString("US-ASCII");
        
        assertTrue(encoder.isCompleted());
        assertEquals("5\r\n12345\r\n3\r\n678\r\n2\r\n90\r\n0\r\n", s);
    }

    public void testCodingCompleted() throws Exception {
        SessionOutputBuffer outbuf = new SessionOutputBuffer(1024, 128);
        ChunkEncoder encoder = new ChunkEncoder(outbuf);
        
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
        try {
            new ChunkEncoder(null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // ignore
        }
    }
    
}