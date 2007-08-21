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

import org.apache.http.HttpException;
import org.apache.http.HttpVersion;
import org.apache.http.RequestLine;
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

        //this is not strictly valid, but is lienent
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
        } catch (HttpException e) { /* expected */ }

        try {
            BasicLineParser.parseRequestLine("  GET", null);
            fail();
        } catch (HttpException e) { /* expected */ }

        try {
            BasicLineParser.parseRequestLine("GET /stuff", null);
            fail();
        } catch (HttpException e) { /* expected */ }

        try {
            BasicLineParser.parseRequestLine("GET/stuff HTTP/1.1", null);
            fail();
        } catch (HttpException e) { /* expected */ }
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
    
}
