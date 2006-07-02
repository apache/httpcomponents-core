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

package org.apache.http.message;

import org.apache.http.HttpException;
import org.apache.http.HttpVersion;
import org.apache.http.RequestLine;
import org.apache.http.io.CharArrayBuffer;
import org.apache.http.message.BasicRequestLine;

import junit.framework.*;

/**
 * Simple tests for {@link RequestLine}.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 */
public class TestRequestLine extends TestCase {

    // ------------------------------------------------------------ Constructor
    public TestRequestLine(String testName) {
        super(testName);
    }

    // ------------------------------------------------------------------- Main
    public static void main(String args[]) {
        String[] testCaseName = { TestRequestLine.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    // ------------------------------------------------------- TestCase Methods

    public static Test suite() {
        return new TestSuite(TestRequestLine.class);
    }

    public void testConstructor() {
        RequestLine requestline = new BasicRequestLine("GET", "/stuff", HttpVersion.HTTP_1_1);
        assertEquals("GET", requestline.getMethod()); 
        assertEquals("/stuff", requestline.getUri()); 
        assertEquals(HttpVersion.HTTP_1_1, requestline.getHttpVersion()); 
    }
        
    public void testConstructorInvalidInput() {
        try {
            new BasicRequestLine(null, "/stuff", HttpVersion.HTTP_1_1);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException e) { /* expected */ }
        try {
            new BasicRequestLine("GEt", null, HttpVersion.HTTP_1_1);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException e) { /* expected */ }
        try {
            new BasicRequestLine("GET", "/stuff", (HttpVersion)null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException e) { /* expected */ }
    }
        
    public void testParseSuccess() throws Exception {
        //typical request line
        RequestLine requestline = BasicRequestLine.parse("GET /stuff HTTP/1.1");
        assertEquals("GET /stuff HTTP/1.1", requestline.toString());
        assertEquals("GET", requestline.getMethod());
        assertEquals("/stuff", requestline.getUri());
        assertEquals(HttpVersion.HTTP_1_1, requestline.getHttpVersion());

        //Lots of blanks
        requestline = BasicRequestLine.parse("  GET    /stuff   HTTP/1.1   ");
        assertEquals("GET /stuff HTTP/1.1", requestline.toString());
        assertEquals("GET", requestline.getMethod());
        assertEquals("/stuff", requestline.getUri());
        assertEquals(HttpVersion.HTTP_1_1, requestline.getHttpVersion());

        //this is not strictly valid, but is lienent
        requestline = BasicRequestLine.parse("\rGET /stuff HTTP/1.1");
        assertEquals("GET", requestline.getMethod());
        assertEquals("/stuff", requestline.getUri());
        assertEquals(HttpVersion.HTTP_1_1, requestline.getHttpVersion());
    }

    public void testParseFailure() throws Exception {
        try {
            BasicRequestLine.parse("    ");
            fail();
        } catch (HttpException e) { /* expected */ }

        try {
            BasicRequestLine.parse("  GET");
            fail();
        } catch (HttpException e) { /* expected */ }

        try {
            BasicRequestLine.parse("GET /stuff");
            fail();
        } catch (HttpException e) { /* expected */ }

        try {
            BasicRequestLine.parse("GET/stuff HTTP/1.1");
            fail();
        } catch (HttpException e) { /* expected */ }
    }

    public void testParseInvalidInput() throws Exception {
        CharArrayBuffer buffer = new CharArrayBuffer(32);
        buffer.append("GET /stuff HTTP/1.1");
        try {
            BasicRequestLine.parse(null, 0, 0);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            BasicRequestLine.parse(null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            BasicRequestLine.parse(buffer, -1, 0);
            fail("IllegalArgumentException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            BasicRequestLine.parse(buffer, 0, 1000);
            fail("IllegalArgumentException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            BasicRequestLine.parse(buffer, 2, 1);
            fail("IllegalArgumentException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
    }

    public void testFormatting() throws Exception {
        RequestLine requestline = new BasicRequestLine("GET", "/stuff", HttpVersion.HTTP_1_1);
        String s = BasicRequestLine.format(requestline);
        assertEquals("GET /stuff HTTP/1.1", s);
    }
    
    public void testFormattingInvalidInput() throws Exception {
        try {
            BasicRequestLine.format(null, new BasicRequestLine("GET", "/stuff", HttpVersion.HTTP_1_1));
            fail("IllegalArgumentException should habe been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            BasicRequestLine.format(new CharArrayBuffer(10), (RequestLine) null);
            fail("IllegalArgumentException should habe been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }
    
}
