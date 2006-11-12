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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.http.Header;
import org.apache.http.MalformedChunkCodingException;
import org.apache.http.nio.impl.SessionInputBuffer;
import org.apache.http.nio.mockup.ReadableByteChannelMockup;

/**
 * Simple tests for {@link ChunkDecoder}.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 * 
 * @version $Id$
 */
public class TestChunkDecoder extends TestCase {

    // ------------------------------------------------------------ Constructor
    public TestChunkDecoder(String testName) {
        super(testName);
    }

    // ------------------------------------------------------------------- Main
    public static void main(String args[]) {
        String[] testCaseName = { TestChunkDecoder.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    // ------------------------------------------------------- TestCase Methods

    public static Test suite() {
        return new TestSuite(TestChunkDecoder.class);
    }

    private static String convert(final ByteBuffer src) {
        src.flip();
        StringBuffer buffer = new StringBuffer(src.remaining()); 
        while (src.hasRemaining()) {
            buffer.append((char)(src.get() & 0xff));
        }
        return buffer.toString();
    }

    public void testBasicDecoding() throws Exception {
        String s = "5\r\n01234\r\n5\r\n56789\r\n6\r\nabcdef\r\n0\r\n\r\n";
        ReadableByteChannel channel = new ReadableByteChannelMockup(
                new String[] {s}, "US-ASCII"); 
        
        SessionInputBuffer inbuf = new SessionInputBuffer(1024, 256); 
        ChunkDecoder decoder = new ChunkDecoder(channel, inbuf);
        
        ByteBuffer dst = ByteBuffer.allocate(1024); 
        
        int bytesRead = decoder.read(dst);
        assertEquals(16, bytesRead);
        assertEquals("0123456789abcdef", convert(dst));
        Header[] footers = decoder.getFooters();
        assertEquals(0, footers.length);

        dst.clear();
        bytesRead = decoder.read(dst);
        assertEquals(-1, bytesRead);
        assertTrue(decoder.isCompleted());
    }

    public void testComplexDecoding() throws Exception {
        String s = "10;key=\"value\"\r\n1234567890123456\r\n" +
                "5\r\n12345\r\n5\r\n12345\r\n0\r\nFooter1: abcde\r\nFooter2: fghij\r\n\r\n";
        ReadableByteChannel channel = new ReadableByteChannelMockup(
                new String[] {s}, "US-ASCII"); 
    
        SessionInputBuffer inbuf = new SessionInputBuffer(1024, 256); 
        ChunkDecoder decoder = new ChunkDecoder(channel, inbuf);
        
        ByteBuffer dst = ByteBuffer.allocate(1024); 
        
        int bytesRead = decoder.read(dst);
        assertEquals(26, bytesRead);
        assertEquals("12345678901234561234512345", convert(dst));
        
        Header[] footers = decoder.getFooters();
        assertEquals(2, footers.length);
        assertEquals("Footer1", footers[0].getName());
        assertEquals("abcde", footers[0].getValue());
        assertEquals("Footer2", footers[1].getName());
        assertEquals("fghij", footers[1].getValue());
        
        dst.clear();
        bytesRead = decoder.read(dst);
        assertEquals(-1, bytesRead);
        assertTrue(decoder.isCompleted());
    }

    public void testDecodingWithSmallBuffer() throws Exception {
        String s1 = "5\r\n01234\r\n5\r\n5678";
        String s2 = "9\r\n6\r\nabcdef\r\n0\r\n\r\n";
        ReadableByteChannel channel = new ReadableByteChannelMockup(
                new String[] {s1, s2}, "US-ASCII"); 
        
        SessionInputBuffer inbuf = new SessionInputBuffer(1024, 256); 
        ChunkDecoder decoder = new ChunkDecoder(channel, inbuf);
        
        ByteBuffer dst = ByteBuffer.allocate(4); 
        
        int bytesRead = decoder.read(dst);
        assertEquals(4, bytesRead);
        assertEquals("0123", convert(dst));
        assertFalse(decoder.isCompleted());

        dst.clear();
        bytesRead = decoder.read(dst);
        assertEquals(4, bytesRead);
        assertEquals("4567", convert(dst));
        assertFalse(decoder.isCompleted());

        dst.clear();
        bytesRead = decoder.read(dst);
        assertEquals(4, bytesRead);
        assertEquals("89ab", convert(dst));
        assertFalse(decoder.isCompleted());

        dst.clear();
        bytesRead = decoder.read(dst);
        assertEquals(4, bytesRead);
        assertEquals("cdef", convert(dst));
        assertTrue(decoder.isCompleted());
        
        dst.clear();
        bytesRead = decoder.read(dst);
        assertEquals(-1, bytesRead);
        assertTrue(decoder.isCompleted());
    }

    public void testIncompleteChunkDecoding() throws Exception {
        String[] chunks = {
                "10;",
                "key=\"value\"\r",
                "\n123456789012345",
                "6\r\n5\r\n12",
                "345\r\n6\r",
                "\nabcdef\r",
                "\n0\r\nFoot",
                "er1: abcde\r\nFooter2: f",
                "ghij\r\n\r\n"
        };
        ReadableByteChannel channel = new ReadableByteChannelMockup(
                chunks, "US-ASCII"); 
    
        SessionInputBuffer inbuf = new SessionInputBuffer(1024, 256); 
        ByteBuffer dst = ByteBuffer.allocate(1024); 
        
        ChunkDecoder decoder = new ChunkDecoder(channel, inbuf);
        int bytesRead = decoder.read(dst);
        assertEquals(0, bytesRead);
        assertFalse(decoder.isCompleted());
        
        bytesRead = decoder.read(dst);
        assertEquals(0, bytesRead);
        assertFalse(decoder.isCompleted());

        bytesRead = decoder.read(dst);
        assertEquals(15, bytesRead);
        assertEquals("123456789012345", convert(dst));
        assertFalse(decoder.isCompleted());

        dst.clear();
        bytesRead = decoder.read(dst);
        assertEquals(3, bytesRead);
        assertEquals("612", convert(dst));
        assertFalse(decoder.isCompleted());

        dst.clear();
        bytesRead = decoder.read(dst);
        assertEquals(3, bytesRead);
        assertEquals("345", convert(dst));
        assertFalse(decoder.isCompleted());

        dst.clear();
        bytesRead = decoder.read(dst);
        assertEquals(6, bytesRead);
        assertEquals("abcdef", convert(dst));
        assertFalse(decoder.isCompleted());

        dst.clear();
        bytesRead = decoder.read(dst);
        assertEquals(0, bytesRead);
        assertFalse(decoder.isCompleted());

        dst.clear();
        bytesRead = decoder.read(dst);
        assertEquals(0, bytesRead);
        assertFalse(decoder.isCompleted());

        dst.clear();
        bytesRead = decoder.read(dst);
        assertEquals(0, bytesRead);
        assertTrue(decoder.isCompleted());

        Header[] footers = decoder.getFooters();
        assertEquals(2, footers.length);
        assertEquals("Footer1", footers[0].getName());
        assertEquals("abcde", footers[0].getValue());
        assertEquals("Footer2", footers[1].getName());
        assertEquals("fghij", footers[1].getValue());

        dst.clear();
        bytesRead = decoder.read(dst);
        assertEquals(-1, bytesRead);
        assertTrue(decoder.isCompleted());
    }
    
    public void testMalformedChunkSizeDecoding() throws Exception {
        String s = "5\r\n01234\r\n5zz\r\n56789\r\n6\r\nabcdef\r\n0\r\n\r\n";
        ReadableByteChannel channel = new ReadableByteChannelMockup(
                new String[] {s}, "US-ASCII"); 
        
        SessionInputBuffer inbuf = new SessionInputBuffer(1024, 256); 
        ChunkDecoder decoder = new ChunkDecoder(channel, inbuf);
        
        ByteBuffer dst = ByteBuffer.allocate(1024); 
        
        try {
            decoder.read(dst);
            fail("MalformedChunkCodingException should have been thrown");
        } catch (MalformedChunkCodingException ex) {
            // expected
        }
    }

    public void testMalformedChunkEndingDecoding() throws Exception {
        String s = "5\r\n01234\r\n5\r\n56789\n\r6\r\nabcdef\r\n0\r\n\r\n";
        ReadableByteChannel channel = new ReadableByteChannelMockup(
                new String[] {s}, "US-ASCII"); 
        
        SessionInputBuffer inbuf = new SessionInputBuffer(1024, 256); 
        ChunkDecoder decoder = new ChunkDecoder(channel, inbuf);
        
        ByteBuffer dst = ByteBuffer.allocate(1024); 
        
        try {
            decoder.read(dst);
            fail("MalformedChunkCodingException should have been thrown");
        } catch (MalformedChunkCodingException ex) {
            // expected
        }
    }

    public void testFoldedFooters() throws Exception {
        String s = "10;key=\"value\"\r\n1234567890123456\r\n" +
                "5\r\n12345\r\n5\r\n12345\r\n0\r\nFooter1: abcde\r\n   \r\n  fghij\r\n\r\n";
        ReadableByteChannel channel = new ReadableByteChannelMockup(
                new String[] {s}, "US-ASCII"); 
    
        SessionInputBuffer inbuf = new SessionInputBuffer(1024, 256); 
        ChunkDecoder decoder = new ChunkDecoder(channel, inbuf);
        
        ByteBuffer dst = ByteBuffer.allocate(1024); 
        
        int bytesRead = decoder.read(dst);
        assertEquals(26, bytesRead);
        assertEquals("12345678901234561234512345", convert(dst));
        
        Header[] footers = decoder.getFooters();
        assertEquals(1, footers.length);
        assertEquals("Footer1", footers[0].getName());
        assertEquals("abcde  fghij", footers[0].getValue());
    }

    public void testMalformedFooters() throws Exception {
        String s = "10;key=\"value\"\r\n1234567890123456\r\n" +
                "5\r\n12345\r\n5\r\n12345\r\n0\r\nFooter1 abcde\r\n\r\n";
        ReadableByteChannel channel = new ReadableByteChannelMockup(
                new String[] {s}, "US-ASCII"); 
    
        SessionInputBuffer inbuf = new SessionInputBuffer(1024, 256); 
        ChunkDecoder decoder = new ChunkDecoder(channel, inbuf);
        
        ByteBuffer dst = ByteBuffer.allocate(1024); 
        
        try {
            decoder.read(dst);
            fail("MalformedChunkCodingException should have been thrown");
        } catch (IOException ex) {
            // expected
        }
    }

    public void testEndOfStreamConditionReadingLastChunk() throws Exception {
        String s = "10\r\n1234567890123456\r\n" +
                "5\r\n12345\r\n5\r\n12345";
        ReadableByteChannel channel = new ReadableByteChannelMockup(
                new String[] {s}, "US-ASCII"); 
    
        SessionInputBuffer inbuf = new SessionInputBuffer(1024, 256); 
        ChunkDecoder decoder = new ChunkDecoder(channel, inbuf);
        
        ByteBuffer dst = ByteBuffer.allocate(1024); 
        
        int bytesRead = decoder.read(dst);
        assertEquals(26, bytesRead);
        assertEquals("12345678901234561234512345", convert(dst));
        assertFalse(decoder.isCompleted());
        
        bytesRead = decoder.read(dst);
        assertEquals(0, bytesRead);
        assertTrue(decoder.isCompleted());
    }

    public void testEndOfStreamConditionReadingFooters() throws Exception {
        String s = "10\r\n1234567890123456\r\n" +
                "5\r\n12345\r\n5\r\n12345\r\n0\r\n";
        ReadableByteChannel channel = new ReadableByteChannelMockup(
                new String[] {s}, "US-ASCII"); 
    
        SessionInputBuffer inbuf = new SessionInputBuffer(1024, 256); 
        ChunkDecoder decoder = new ChunkDecoder(channel, inbuf);
        
        ByteBuffer dst = ByteBuffer.allocate(1024); 
        
        int bytesRead = decoder.read(dst);
        assertEquals(26, bytesRead);
        assertEquals("12345678901234561234512345", convert(dst));
        assertFalse(decoder.isCompleted());
        
        bytesRead = decoder.read(dst);
        assertEquals(0, bytesRead);
        assertTrue(decoder.isCompleted());
    }

    public void testInvalidInput() throws Exception {
        String s = "10;key=\"value\"\r\n1234567890123456\r\n" +
                "5\r\n12345\r\n5\r\n12345\r\n0\r\nFooter1 abcde\r\n\r\n";
        ReadableByteChannel channel = new ReadableByteChannelMockup(
                new String[] {s}, "US-ASCII"); 
    
        SessionInputBuffer inbuf = new SessionInputBuffer(1024, 256); 
        ChunkDecoder decoder = new ChunkDecoder(channel, inbuf);
        
        try {
            decoder.read(null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }
    
}