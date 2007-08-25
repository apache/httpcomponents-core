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

package org.apache.http.message;

import org.apache.http.HttpVersion;
import org.apache.http.ParseException;
import org.apache.http.RequestLine;
import org.apache.http.StatusLine;
import org.apache.http.message.BasicRequestLine;
import org.apache.http.util.CharArrayBuffer;

import junit.framework.*;

/**
 * Tests for {@link BasicLineParser}.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 */
public class TestBasicLineParser extends TestCase {

    // ------------------------------------------------------------ Constructor
    public TestBasicLineParser(String testName) {
        super(testName);
    }

    // ------------------------------------------------------------------- Main
    public static void main(String args[]) {
        String[] testCaseName = { TestBasicLineParser.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    // ------------------------------------------------------- TestCase Methods

    public static Test suite() {
        return new TestSuite(TestBasicLineParser.class);
    }

        
    public void testRLParseSuccess() throws Exception {
        //typical request line
        RequestLine requestline = BasicLineParser.parseRequestLine
            ("GET /stuff HTTP/1.1", null);
        assertEquals("GET /stuff HTTP/1.1", requestline.toString());
        assertEquals("GET", requestline.getMethod());
        assertEquals("/stuff", requestline.getUri());
        assertEquals(HttpVersion.HTTP_1_1, requestline.getHttpVersion());

        //Lots of blanks
        requestline = BasicLineParser.parseRequestLine
            ("  GET    /stuff   HTTP/1.1   ", null);
        assertEquals("GET /stuff HTTP/1.1", requestline.toString());
        assertEquals("GET", requestline.getMethod());
        assertEquals("/stuff", requestline.getUri());
        assertEquals(HttpVersion.HTTP_1_1, requestline.getHttpVersion());

        //this is not strictly valid, but is lenient
        requestline = BasicLineParser.parseRequestLine
            ("\rGET /stuff HTTP/1.1", null);
        assertEquals("GET", requestline.getMethod());
        assertEquals("/stuff", requestline.getUri());
        assertEquals(HttpVersion.HTTP_1_1, requestline.getHttpVersion());
    }

    public void testRLParseFailure() throws Exception {
        try {
            BasicLineParser.parseRequestLine("    ", null);
            fail();
        } catch (ParseException e) {
            // expected
        }

        try {
            BasicLineParser.parseRequestLine("  GET", null);
            fail();
        } catch (ParseException e) {
            // expected
        }

        try {
            BasicLineParser.parseRequestLine("GET /stuff", null);
            fail();
        } catch (ParseException e) {
            // expected
        }

        try {
            BasicLineParser.parseRequestLine("GET/stuff HTTP/1.1", null);
            fail();
        } catch (ParseException e) {
            // expected
        }
    }

    public void testRLParseInvalidInput() throws Exception {
        CharArrayBuffer buffer = new CharArrayBuffer(32);
        buffer.append("GET /stuff HTTP/1.1");
        try {
            BasicLineParser.parseRequestLine(null, null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            BasicLineParser.DEFAULT.parseRequestLine(null, 0, 0);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            BasicLineParser.DEFAULT.parseRequestLine(buffer, -1, 0);
            fail("IllegalArgumentException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            BasicLineParser.DEFAULT.parseRequestLine(buffer, 0, 1000);
            fail("IllegalArgumentException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            BasicLineParser.DEFAULT.parseRequestLine(buffer, 2, 1);
            fail("IllegalArgumentException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
    }


        
    public void testSLParseSuccess() throws Exception {
        //typical status line
        StatusLine statusLine = BasicLineParser.parseStatusLine
            ("HTTP/1.1 200 OK", null);
        assertEquals("HTTP/1.1 200 OK", statusLine.toString());
        assertEquals(HttpVersion.HTTP_1_1, statusLine.getHttpVersion());
        assertEquals(200, statusLine.getStatusCode());
        assertEquals("OK", statusLine.getReasonPhrase());

        //status line with multi word reason phrase
        statusLine = BasicLineParser.parseStatusLine
            ("HTTP/1.1 404 Not Found", null);
        assertEquals(404, statusLine.getStatusCode());
        assertEquals("Not Found", statusLine.getReasonPhrase());

        //reason phrase can be anyting
        statusLine = BasicLineParser.parseStatusLine
            ("HTTP/1.1 404 Non Trouve", null);
        assertEquals("Non Trouve", statusLine.getReasonPhrase());

        //its ok to end with a \n\r
        statusLine = BasicLineParser.parseStatusLine
            ("HTTP/1.1 404 Not Found\r\n", null);
        assertEquals("Not Found", statusLine.getReasonPhrase());

        //this is valid according to the Status-Line BNF
        statusLine = BasicLineParser.parseStatusLine
            ("HTTP/1.1 200 ", null);
        assertEquals(200, statusLine.getStatusCode());
        assertEquals("", statusLine.getReasonPhrase());

        //this is not strictly valid, but is lenient
        statusLine = BasicLineParser.parseStatusLine
            ("HTTP/1.1 200", null);
        assertEquals(200, statusLine.getStatusCode());
        assertEquals("", statusLine.getReasonPhrase());

        //this is not strictly valid, but is lenient
        statusLine = BasicLineParser.parseStatusLine
            ("HTTP/1.1     200 OK", null);
        assertEquals(200, statusLine.getStatusCode());
        assertEquals("OK", statusLine.getReasonPhrase());

        //this is not strictly valid, but is lenient
        statusLine = BasicLineParser.parseStatusLine
            ("\rHTTP/1.1 200 OK", null);
        assertEquals(200, statusLine.getStatusCode());
        assertEquals("OK", statusLine.getReasonPhrase());
        assertEquals(HttpVersion.HTTP_1_1, statusLine.getHttpVersion());

        //this is not strictly valid, but is lenient
        statusLine = BasicLineParser.parseStatusLine
            ("  HTTP/1.1 200 OK", null);
        assertEquals(200, statusLine.getStatusCode());
        assertEquals("OK", statusLine.getReasonPhrase());
        assertEquals(HttpVersion.HTTP_1_1, statusLine.getHttpVersion());
    }

    public void testSLParseFailure() throws Exception {
        try {
            BasicLineParser.parseStatusLine("xxx 200 OK", null);
            fail();
        } catch (ParseException e) {
            // expected
        }

        try {
            BasicLineParser.parseStatusLine("HTTP/1.1 xxx OK", null);
            fail();
        } catch (ParseException e) {
            // expected
        }

        try {
            BasicLineParser.parseStatusLine("HTTP/1.1    ", null);
            fail();
        } catch (ParseException e) {
            // expected
        }
        try {
            BasicLineParser.parseStatusLine("HTTP/1.1", null);
            fail();
        } catch (ParseException e) {
            // expected
        }
    }

    public void testSLParseInvalidInput() throws Exception {
        CharArrayBuffer buffer = new CharArrayBuffer(32);
        buffer.append("HTTP/1.1 200 OK");
        try {
            BasicLineParser.parseStatusLine(null, null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            BasicLineParser.DEFAULT.parseStatusLine(null, 0, 0);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            BasicLineParser.DEFAULT.parseStatusLine(buffer, -1, 0);
            fail("IllegalArgumentException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            BasicLineParser.DEFAULT.parseStatusLine(buffer, 0, 1000);
            fail("IllegalArgumentException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            BasicLineParser.DEFAULT.parseStatusLine(buffer, 2, 1);
            fail("IllegalArgumentException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
    }


    
    public void testHttpVersionParsing() throws Exception {
        new HttpVersion(1, 1);
        String s = "HTTP/1.1";
        HttpVersion version = BasicLineParser.parseProtocolVersion(s, null);
        assertEquals("HTTP major version number", 1, version.getMajor());
        assertEquals("HTTP minor version number", 1, version.getMinor());
        assertEquals("HTTP version number", s, version.toString());

        s = "HTTP/123.4567";
        version = BasicLineParser.parseProtocolVersion(s, null);
        assertEquals("HTTP major version number", 123, version.getMajor());
        assertEquals("HTTP minor version number", 4567, version.getMinor());
        assertEquals("HTTP version number", s, version.toString());
    }

    public void testInvalidHttpVersionParsing() throws Exception {
        try {
            BasicLineParser.parseProtocolVersion(null, null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException e) {
            //expected
        }
        try {
            BasicLineParser.parseProtocolVersion
                ("    ", null);
            fail("ParseException should have been thrown");
        } catch (ParseException e) {
            //expected
        }
        try {
            BasicLineParser.parseProtocolVersion
                ("HTT", null);
            fail("ParseException should have been thrown");
        } catch (ParseException e) {
            //expected
        }
        try {
            BasicLineParser.parseProtocolVersion
                ("crap", null);
            fail("ParseException should have been thrown");
        } catch (ParseException e) {
            //expected
        }
        try {
            BasicLineParser.parseProtocolVersion
                ("HTTP/crap", null);
            fail("ParseException should have been thrown");
        } catch (ParseException e) {
            //expected
        }
        try {
            BasicLineParser.parseProtocolVersion
                ("HTTP/1", null);
            fail("ParseException should have been thrown");
        } catch (ParseException e) {
            //expected
        }
        try {
            BasicLineParser.parseProtocolVersion
                ("HTTP/1234   ", null);
            fail("ParseException should have been thrown");
        } catch (ParseException e) {
            //expected
        }
        try {
            BasicLineParser.parseProtocolVersion
                ("HTTP/1.", null);
            fail("ParseException should have been thrown");
        } catch (ParseException e) {
            //expected
        }
        try {
            BasicLineParser.parseProtocolVersion
                ("HTTP/1.1 crap", null);
            fail("ParseException should have been thrown");
        } catch (ParseException e) {
            //expected
        }
        try {
            BasicLineParser.parseProtocolVersion
                ("HTTP/whatever.whatever whatever", null);
            fail("ParseException should have been thrown");
        } catch (ParseException e) {
            //expected
        }
        try {
            BasicLineParser.parseProtocolVersion
                ("HTTP/1.whatever whatever", null);
            fail("ParseException should have been thrown");
        } catch (ParseException e) {
            //expected
        }
    }

    public void testHttpVersionParsingInvalidInput() throws Exception {
        CharArrayBuffer buffer = new CharArrayBuffer(32);
        buffer.append("HTTP/1.1");
        try {
            BasicLineParser.parseProtocolVersion(null, null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            BasicLineParser.DEFAULT.parseProtocolVersion(null, 0, 0);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            BasicLineParser.DEFAULT.parseProtocolVersion(buffer, -1, 0);
            fail("IllegalArgumentException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            BasicLineParser.DEFAULT.parseProtocolVersion(buffer, 0, 1000);
            fail("IllegalArgumentException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            BasicLineParser.DEFAULT.parseProtocolVersion(buffer, 2, 1);
            fail("IllegalArgumentException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
    }
    
}
