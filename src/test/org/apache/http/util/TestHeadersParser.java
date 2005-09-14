/*
 * $HeadURL$
 * $Revision$
 * $Date$
 * 
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

import org.apache.http.Header;
import org.apache.http.io.HttpDataReceiver;
import org.apache.http.mockup.HttpDataReceiverMockup;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Unit tests for {@link HeadersParser}.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 */
public class TestHeadersParser extends TestCase {

    public TestHeadersParser(String testName) {
        super(testName);
    }

    public static void main(String args[]) {
        String[] testCaseName = { TestHeadersParser.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    public static Test suite() {
        return new TestSuite(TestHeadersParser.class);
    }
    
    public void testSimpleHeaders() throws Exception {
        String s = 
            "header1: stuff\r\n" + 
            "header2  : stuff \r\n" + 
            "header3: stuff\r\n" + 
            "     and more stuff\r\n" + 
            "\t and even more stuff\r\n" +  
            "\r\n"; 
        HttpDataReceiver receiver = new HttpDataReceiverMockup(s, "US-ASCII"); 
        Header[] headers = HeadersParser.processHeaders(receiver);
        assertNotNull(headers);
        assertEquals(3, headers.length);
        assertEquals("header1", headers[0].getName());
        assertEquals("stuff", headers[0].getValue());
        assertEquals("header2", headers[1].getName());
        assertEquals("stuff", headers[1].getValue());
        assertEquals("header3", headers[2].getName());
        assertEquals("stuff and more stuff and even more stuff", headers[2].getValue());
    }

    public void testMalformedFirstHeader() throws Exception {
        String s = 
            "    header1: stuff\r\n" + 
            "header2  : stuff \r\n"; 
        HttpDataReceiver receiver = new HttpDataReceiverMockup(s, "US-ASCII"); 
        Header[] headers = HeadersParser.processHeaders(receiver);
        assertNotNull(headers);
        assertEquals(2, headers.length);
        assertEquals("header1", headers[0].getName());
        assertEquals("stuff", headers[0].getValue());
        assertEquals("header2", headers[1].getName());
        assertEquals("stuff", headers[1].getValue());
    }
    
    public void testEmptyDataStream() throws Exception {
        String s = ""; 
        HttpDataReceiver receiver = new HttpDataReceiverMockup(s, "US-ASCII"); 
        Header[] headers = HeadersParser.processHeaders(receiver);
        assertNotNull(headers);
        assertEquals(0, headers.length);
    }
    
}