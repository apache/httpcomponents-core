/*
 * $HeadURL$
 * $Revision$
 * $Date$
 * ====================================================================
 *
 *  Copyright 1999-2004 The Apache Software Foundation
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

package org.apache.http.util;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import junit.framework.*;

/**
 * Simple tests for {@link HttpParser}.
 *
 * @author Oleg Kalnichevski
 * @version $Id$
 */
public class TestHttpLineParser extends TestCase {

    private static final String ASCII = "US-ASCII";
    private static final String UTF8 = "UTF-8";

    // ------------------------------------------------------------ Constructor
    public TestHttpLineParser(String testName) {
        super(testName);
    }

    // ------------------------------------------------------------------- Main
    public static void main(String args[]) {
        String[] testCaseName = { TestHttpLineParser.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    // ------------------------------------------------------- TestCase Methods

    public static Test suite() {
        return new TestSuite(TestHttpLineParser.class);
    }

    public void testReadHttpLine() throws Exception {
        InputStream instream = new ByteArrayInputStream(
            "\r\r\nstuff\r\n".getBytes(ASCII)); 
        assertEquals("\r", HttpLineParser.readLine(instream, ASCII));
        assertEquals("stuff", HttpLineParser.readLine(instream, ASCII));
        assertEquals(null, HttpLineParser.readLine(instream, ASCII));

        instream = new ByteArrayInputStream(
            "\n\r\nstuff\r\nstuff\nstuff".getBytes(ASCII)); 
        assertEquals("", HttpLineParser.readLine(instream, ASCII));
        assertEquals("", HttpLineParser.readLine(instream, ASCII));
        assertEquals("stuff", HttpLineParser.readLine(instream, ASCII));
        assertEquals("stuff", HttpLineParser.readLine(instream, ASCII));
        assertEquals("stuff", HttpLineParser.readLine(instream, ASCII));
        assertEquals(null, HttpLineParser.readLine(instream, ASCII));
        assertEquals(null, HttpLineParser.readLine(instream, ASCII));
    }

    private static String constructString(int [] unicodeChars) {
        StringBuffer buffer = new StringBuffer();
        if (unicodeChars != null) {
            for (int i = 0; i < unicodeChars.length; i++) {
                buffer.append((char)unicodeChars[i]); 
            }
        }
        return buffer.toString();
    }

    static final int SWISS_GERMAN_HELLO [] = {
            0x47, 0x72, 0xFC, 0x65, 0x7A, 0x69, 0x5F, 0x7A, 0xE4, 0x6D, 0xE4
        };
                
    public void testReadMultibyeHttpLine() throws Exception {
        String s = constructString(SWISS_GERMAN_HELLO);
        InputStream instream = new ByteArrayInputStream(
            ("\r\r\n" + s + "\r\n" + s + "\n" + s).getBytes(UTF8)); 
        assertEquals("\r", HttpLineParser.readLine(instream, UTF8));
        assertEquals(s, HttpLineParser.readLine(instream, UTF8));
        assertEquals(s, HttpLineParser.readLine(instream, UTF8));
        assertEquals(s, HttpLineParser.readLine(instream, UTF8));
        assertEquals(null, HttpLineParser.readLine(instream, UTF8));

        instream = new ByteArrayInputStream(
            ("\n\r\n" + s + "\r\n").getBytes(UTF8)); 
        assertEquals("", HttpLineParser.readLine(instream, UTF8));
        assertEquals("", HttpLineParser.readLine(instream, UTF8));
        assertEquals(s, HttpLineParser.readLine(instream, UTF8));
        assertEquals(null, HttpLineParser.readLine(instream, UTF8));
    }
}
