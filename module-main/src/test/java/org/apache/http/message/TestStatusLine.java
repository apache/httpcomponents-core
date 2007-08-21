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
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.StatusLine;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.util.CharArrayBuffer;

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
        StatusLine statusline = new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        assertEquals(HttpVersion.HTTP_1_1, statusline.getHttpVersion()); 
        assertEquals(HttpStatus.SC_OK, statusline.getStatusCode()); 
        assertEquals("OK", statusline.getReasonPhrase()); 
    }
        
    public void testConstructorInvalidInput() {
        try {
            new BasicStatusLine(null, HttpStatus.SC_OK, "OK");
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException e) { /* expected */ }
        try {
            new BasicStatusLine(HttpVersion.HTTP_1_1, -1, "OK");
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException e) { /* expected */ }
    }

    public void testToString() throws Exception {
        StatusLine statusline = new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        assertEquals("HTTP/1.1 200 OK", statusline.toString());
        statusline = new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, null);
        assertEquals("HTTP/1.1 200", statusline.toString());
    }
    
    public void testFormatting() throws Exception {
        StatusLine statusline = new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        String s = BasicStatusLine.format(statusline);
        assertEquals("HTTP/1.1 200 OK", s);
        statusline = new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, null);
        s = BasicStatusLine.format(statusline);
        assertEquals("HTTP/1.1 200 ", s);
        // compare with "testParseSuccess" above: trailing space is correct
    }
    
    public void testFormattingInvalidInput() throws Exception {
        try {
            BasicStatusLine.format(null, new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK"));
            fail("IllegalArgumentException should habe been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            BasicStatusLine.format(new CharArrayBuffer(10), (StatusLine) null);
            fail("IllegalArgumentException should habe been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }
    
}
