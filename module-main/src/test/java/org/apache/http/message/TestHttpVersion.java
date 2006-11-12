/*
 * $HeadURL$
 * $Revision$
 * $Date$
 * 
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

import org.apache.http.HttpVersion;
import org.apache.http.ProtocolException;
import org.apache.http.message.BasicHttpVersion;
import org.apache.http.util.CharArrayBuffer;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Test cases for HTTP version class
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 */
public class TestHttpVersion extends TestCase {

    // ------------------------------------------------------------ Constructor

    public TestHttpVersion(String name) {
        super(name);
    }

    // ------------------------------------------------------- TestCase Methods

    public static Test suite() {
        return new TestSuite(TestHttpVersion.class);
    }

    // ------------------------------------------------------------------ Tests
    
    public void testHttpVersionParsing() throws Exception {
        new BasicHttpVersion(1, 1);
        String s = "HTTP/1.1";
        HttpVersion version = BasicHttpVersion.parse(s);
        assertEquals("HTTP major version number", 1, version.getMajor());
        assertEquals("HTTP minor version number", 1, version.getMinor());
        assertEquals("HTTP version number", s, version.toString());

        s = "HTTP/123.4567";
        version = BasicHttpVersion.parse(s);
        assertEquals("HTTP major version number", 123, version.getMajor());
        assertEquals("HTTP minor version number", 4567, version.getMinor());
        assertEquals("HTTP version number", s, version.toString());
    }

    public void testInvalidHttpVersionParsing() throws Exception {
        try {
            BasicHttpVersion.parse(null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException e) {
            //expected
        }
        try {
            BasicHttpVersion.parse("    ");
            fail("ProtocolException should have been thrown");
        } catch (ProtocolException e) {
            //expected
        }
        try {
            BasicHttpVersion.parse("HTT");
            fail("ProtocolException should have been thrown");
        } catch (ProtocolException e) {
            //expected
        }
        try {
            BasicHttpVersion.parse("crap");
            fail("ProtocolException should have been thrown");
        } catch (ProtocolException e) {
            //expected
        }
        try {
            BasicHttpVersion.parse("HTTP/crap");
            fail("ProtocolException should have been thrown");
        } catch (ProtocolException e) {
            //expected
        }
        try {
            BasicHttpVersion.parse("HTTP/1");
            fail("ProtocolException should have been thrown");
        } catch (ProtocolException e) {
            //expected
        }
        try {
            BasicHttpVersion.parse("HTTP/1234   ");
            fail("ProtocolException should have been thrown");
        } catch (ProtocolException e) {
            //expected
        }
        try {
            BasicHttpVersion.parse("HTTP/1.");
            fail("ProtocolException should have been thrown");
        } catch (ProtocolException e) {
            //expected
        }
        try {
            BasicHttpVersion.parse("HTTP/1.1 crap");
            fail("ProtocolException should have been thrown");
        } catch (ProtocolException e) {
            //expected
        }
        try {
            BasicHttpVersion.parse("HTTP/whatever.whatever whatever");
            fail("ProtocolException should have been thrown");
        } catch (ProtocolException e) {
            //expected
        }
        try {
            BasicHttpVersion.parse("HTTP/1.whatever whatever");
            fail("ProtocolException should have been thrown");
        } catch (ProtocolException e) {
            //expected
        }
    }

    public void testHttpVersionParsingInvalidInput() throws Exception {
        CharArrayBuffer buffer = new CharArrayBuffer(32);
        buffer.append("HTTP/1.1");
        try {
            BasicHttpVersion.parse(null, 0, 0);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            BasicHttpVersion.parse(null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            BasicHttpVersion.parse(buffer, -1, 0);
            fail("IllegalArgumentException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            BasicHttpVersion.parse(buffer, 0, 1000);
            fail("IllegalArgumentException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            BasicHttpVersion.parse(buffer, 2, 1);
            fail("IllegalArgumentException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
    }

    public void testHttpVersionFormatting() throws Exception {
        String s = BasicHttpVersion.format(HttpVersion.HTTP_1_1);
        assertEquals("HTTP/1.1", s);
    }
    
    public void testHttpVersionFormattingInvalidInput() throws Exception {
        try {
            BasicHttpVersion.format(null, HttpVersion.HTTP_1_1);
            fail("IllegalArgumentException should habe been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            BasicHttpVersion.format(new CharArrayBuffer(10), (HttpVersion) null);
            fail("IllegalArgumentException should habe been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

}

