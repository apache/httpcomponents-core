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
        RequestLine requestline = new RequestLine("GET", "/stuff", HttpVersion.HTTP_1_1);
        assertEquals("GET", requestline.getMethod()); 
        assertEquals("/stuff", requestline.getUri()); 
        assertEquals(HttpVersion.HTTP_1_1, requestline.getHttpVersion()); 
    }
        
    public void testConstructorInvalidInput() {
        try {
            RequestLine requestline = new RequestLine(null, "/stuff", HttpVersion.HTTP_1_1);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException e) { /* expected */ }
        try {
            RequestLine requestline = new RequestLine("GEt", null, HttpVersion.HTTP_1_1);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException e) { /* expected */ }
        try {
            RequestLine requestline = new RequestLine("GET", "/stuff", (HttpVersion)null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException e) { /* expected */ }
    }
        
    public void testSuccess() throws Exception {
        //typical request line
        RequestLine requestline = RequestLine.parse("GET /stuff HTTP/1.1");
        assertEquals("GET /stuff HTTP/1.1", requestline.toString());
        assertEquals("GET", requestline.getMethod());
        assertEquals("/stuff", requestline.getUri());
        assertEquals(HttpVersion.HTTP_1_1, requestline.getHttpVersion());

        //Lots of blanks
        requestline = RequestLine.parse("  GET    /stuff   HTTP/1.1   ");
        assertEquals("GET /stuff HTTP/1.1", requestline.toString());
        assertEquals("GET", requestline.getMethod());
        assertEquals("/stuff", requestline.getUri());
        assertEquals(HttpVersion.HTTP_1_1, requestline.getHttpVersion());

        //this is not strictly valid, but is lienent
        requestline = RequestLine.parse("\rGET /stuff HTTP/1.1");
        assertEquals("GET", requestline.getMethod());
        assertEquals("/stuff", requestline.getUri());
        assertEquals(HttpVersion.HTTP_1_1, requestline.getHttpVersion());
    }

    public void testFailure() throws Exception {
        RequestLine requestline = null;
        try {
            requestline = RequestLine.parse("GET /stuff");
            fail();
        } catch (HttpException e) { /* expected */ }

        try {
            requestline = RequestLine.parse("GET/stuff HTTP/1.1");
            fail();
        } catch (HttpException e) { /* expected */ }
    }

    public void testNullInput() throws Exception {
        try {
            RequestLine requestline = RequestLine.parse(null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException e) { /* expected */ }
    }

}
