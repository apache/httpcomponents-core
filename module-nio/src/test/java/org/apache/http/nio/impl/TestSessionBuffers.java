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

package org.apache.http.nio.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.http.io.CharArrayBuffer;

/**
 * Simple tests for {@link SessionInputBuffer}.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 * 
 * @version $Id$
 */
public class TestSessionBuffers extends TestCase {

    // ------------------------------------------------------------ Constructor
    public TestSessionBuffers(String testName) {
        super(testName);
    }

    // ------------------------------------------------------------------- Main
    public static void main(String args[]) {
        String[] testCaseName = { TestSessionBuffers.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    // ------------------------------------------------------- TestCase Methods

    public static Test suite() {
        return new TestSuite(TestSessionBuffers.class);
    }

    private static WritableByteChannel newChannel(final ByteArrayOutputStream outstream) {
        return Channels.newChannel(outstream);
    }
    
    private static ReadableByteChannel newChannel(final String s, final String charset) 
            throws UnsupportedEncodingException {
        return Channels.newChannel(new ByteArrayInputStream(s.getBytes(charset)));
    }
    
    private static ReadableByteChannel newChannel(final String s) 
            throws UnsupportedEncodingException {
        return newChannel(s, "US-ASCII");
    }

    public void testReadLine() throws Exception {
        
        SessionInputBuffer inbuf = new SessionInputBuffer(16, 16);
        
        ReadableByteChannel channel = newChannel("One\r\nTwo\r\nThree");
        
        inbuf.fill(channel);
        
        CharArrayBuffer line = new CharArrayBuffer(64);
        
        line.clear();
        assertTrue(inbuf.readLine(line, false));
        assertEquals("One", line.toString());
        
        line.clear();
        assertTrue(inbuf.readLine(line, false));
        assertEquals("Two", line.toString());

        line.clear();
        assertFalse(inbuf.readLine(line, false));

        channel = newChannel("\r\nFour");
        inbuf.fill(channel);
        
        line.clear();
        assertTrue(inbuf.readLine(line, false));
        assertEquals("Three", line.toString());

        inbuf.fill(channel);
        
        line.clear();
        assertTrue(inbuf.readLine(line, true));
        assertEquals("Four", line.toString());

        line.clear();
        assertFalse(inbuf.readLine(line, true));
    }
    
    public void testWriteLine() throws Exception {
        
        SessionOutputBuffer outbuf = new SessionOutputBuffer(16, 16);
        SessionInputBuffer inbuf = new SessionInputBuffer(16, 16);
        
        ReadableByteChannel inChannel = newChannel("One\r\nTwo\r\nThree");
        
        inbuf.fill(inChannel);
        
        CharArrayBuffer line = new CharArrayBuffer(64);
        
        line.clear();
        assertTrue(inbuf.readLine(line, false));
        assertEquals("One", line.toString());
        
        outbuf.writeLine(line);
        
        line.clear();
        assertTrue(inbuf.readLine(line, false));
        assertEquals("Two", line.toString());

        outbuf.writeLine(line);
        
        line.clear();
        assertFalse(inbuf.readLine(line, false));

        inChannel = newChannel("\r\nFour");
        inbuf.fill(inChannel);

        line.clear();
        assertTrue(inbuf.readLine(line, false));
        assertEquals("Three", line.toString());

        outbuf.writeLine(line);
        
        inbuf.fill(inChannel);
        
        line.clear();
        assertTrue(inbuf.readLine(line, true));
        assertEquals("Four", line.toString());

        outbuf.writeLine(line);
        
        line.clear();
        assertFalse(inbuf.readLine(line, true));

        ByteArrayOutputStream outstream = new ByteArrayOutputStream(); 
        WritableByteChannel outChannel = newChannel(outstream);
        outbuf.flush(outChannel);
        
        String s = new String(outstream.toByteArray(), "US-ASCII");
        assertEquals("One\r\nTwo\r\nThree\r\nFour\r\n", s);
    }
    
}