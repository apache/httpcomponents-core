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

package org.apache.http;

import org.apache.http.io.CharArrayBuffer;

import junit.framework.*;

/**
 * Simple tests for {@link StatusLine}.
 *
 * @author <a href="mailto:jsdever@apache.org">Jeff Dever</a>
 * 
 * @version $Id$
 */
public class TestStatusLine extends TestCase {

    // ------------------------------------------------------------ Constructor
    public TestStatusLine(String testName) {
        super(testName);
    }

    // ------------------------------------------------------------------- Main
    public static void main(String args[]) {
        String[] testCaseName = { TestStatusLine.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    // ------------------------------------------------------- TestCase Methods

    public static Test suite() {
        return new TestSuite(TestStatusLine.class);
    }

    // ------------------------------------------------------ Protected Methods


    // ----------------------------------------------------------- Test Methods

    public void testConstructor() {
        StatusLine statusline = new StatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        assertEquals(HttpVersion.HTTP_1_1, statusline.getHttpVersion()); 
        assertEquals(HttpStatus.SC_OK, statusline.getStatusCode()); 
        assertEquals("OK", statusline.getReasonPhrase()); 

        statusline = new StatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK);
        assertEquals(HttpVersion.HTTP_1_1, statusline.getHttpVersion()); 
        assertEquals(HttpStatus.SC_OK, statusline.getStatusCode()); 
        assertEquals("OK", statusline.getReasonPhrase()); 
    }
        
    public void testConstructorInvalidInput() {
        try {
            new StatusLine(null, HttpStatus.SC_OK, "OK");
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException e) { /* expected */ }
        try {
            new StatusLine(HttpVersion.HTTP_1_1, -1, "OK");
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException e) { /* expected */ }
    }
        
    public void testParseSuccess() throws Exception {
        //typical status line
        StatusLine statusLine = StatusLine.parse("HTTP/1.1 200 OK");
        assertEquals("HTTP/1.1 200 OK", statusLine.toString());
        assertEquals(HttpVersion.HTTP_1_1, statusLine.getHttpVersion());
        assertEquals(200, statusLine.getStatusCode());
        assertEquals("OK", statusLine.getReasonPhrase());

        //status line with multi word reason phrase
        statusLine = StatusLine.parse("HTTP/1.1 404 Not Found");
        assertEquals(404, statusLine.getStatusCode());
        assertEquals("Not Found", statusLine.getReasonPhrase());

        //reason phrase can be anyting
        statusLine = StatusLine.parse("HTTP/1.1 404 Non Trouve");
        assertEquals("Non Trouve", statusLine.getReasonPhrase());

        //its ok to end with a \n\r
        statusLine = StatusLine.parse("HTTP/1.1 404 Not Found\r\n");
        assertEquals("Not Found", statusLine.getReasonPhrase());

        //this is valid according to the Status-Line BNF
        statusLine = StatusLine.parse("HTTP/1.1 200 ");
        assertEquals(200, statusLine.getStatusCode());
        assertEquals("", statusLine.getReasonPhrase());

        //this is not strictly valid, but is lienent
        statusLine = StatusLine.parse("HTTP/1.1 200");
        assertEquals(200, statusLine.getStatusCode());
        assertEquals("", statusLine.getReasonPhrase());

        //this is not strictly valid, but is lienent
        statusLine = StatusLine.parse("HTTP/1.1     200 OK");
        assertEquals(200, statusLine.getStatusCode());
        assertEquals("OK", statusLine.getReasonPhrase());

        //this is not strictly valid, but is lienent
        statusLine = StatusLine.parse("\rHTTP/1.1 200 OK");
        assertEquals(200, statusLine.getStatusCode());
        assertEquals("OK", statusLine.getReasonPhrase());
        assertEquals(HttpVersion.HTTP_1_1, statusLine.getHttpVersion());

        //this is not strictly valid, but is lienent
        statusLine = StatusLine.parse("  HTTP/1.1 200 OK");
        assertEquals(200, statusLine.getStatusCode());
        assertEquals("OK", statusLine.getReasonPhrase());
        assertEquals(HttpVersion.HTTP_1_1, statusLine.getHttpVersion());
    }

    public void testParseFailure() throws Exception {
        try {
            StatusLine.parse("xxx 200 OK");
            fail();
        } catch (HttpException e) { /* expected */ }

        try {
            StatusLine.parse("HTTP/1.1 xxx OK");
            fail();
        } catch (HttpException e) { /* expected */ }

        try {
            StatusLine.parse("HTTP/1.1    ");
            fail();
        } catch (HttpException e) { /* expected */ }
        try {
            StatusLine.parse("HTTP/1.1");
            fail();
        } catch (HttpException e) { /* expected */ }
    }

    public void testParseInvalidInput() throws Exception {
        CharArrayBuffer buffer = new CharArrayBuffer(32);
        buffer.append("HTTP/1.1 200 OK");
        try {
            StatusLine.parse(null, 0, 0);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            StatusLine.parse(null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            StatusLine.parse(buffer, -1, 0);
            fail("IllegalArgumentException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            StatusLine.parse(buffer, 0, 1000);
            fail("IllegalArgumentException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            StatusLine.parse(buffer, 2, 1);
            fail("IllegalArgumentException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
    }

    public void testToString() throws Exception {
        StatusLine statusline = new StatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        assertEquals("HTTP/1.1 200 OK", statusline.toString());
        statusline = new StatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, null);
        assertEquals("HTTP/1.1 200", statusline.toString());
    }
    
    public void testFormatting() throws Exception {
        StatusLine statusline = new StatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        String s = StatusLine.format(statusline);
        assertEquals("HTTP/1.1 200 OK", s);
        statusline = new StatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, null);
        s = StatusLine.format(statusline);
        assertEquals("HTTP/1.1 200", s);
    }
    
    public void testFormattingInvalidInput() throws Exception {
        try {
            StatusLine.format(null, new StatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK"));
            fail("IllegalArgumentException should habe been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            StatusLine.format(new CharArrayBuffer(10), (StatusLine) null);
            fail("IllegalArgumentException should habe been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }
    
}
