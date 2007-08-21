/*
 * $Header$
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
 * [Additional notices, if required by prior licensing conditions]
 *
 */

package org.apache.http.message;

import org.apache.http.HeaderElement;
import org.apache.http.NameValuePair;
import org.apache.http.util.CharArrayBuffer;

import junit.framework.*;

/**
 * Tests for header value parsing.
 *
 * @author Rodney Waldhoff
 * @author <a href="mailto:bcholmes@interlog.com">B.C. Holmes</a>
 * @author <a href="mailto:jericho@thinkfree.com">Park, Sung-Gu</a>
 * @author <a href="mailto:oleg at ural.ru">oleg Kalnichevski</a>
 * @author and others
 * @version $Id$
 */
public class TestBasicHeaderValueParser extends TestCase {

    // ------------------------------------------------------------ Constructor
    public TestBasicHeaderValueParser(String testName) {
        super(testName);
    }

    // ------------------------------------------------------------------- Main
    public static void main(String args[]) {
        String[] testCaseName = { TestBasicHeaderValueParser.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    // ------------------------------------------------------- TestCase Methods

    public static Test suite() {
        return new TestSuite(TestBasicHeaderValueParser.class);
    }
    

    public void testParseHeaderElements() throws Exception {
        String headerValue = "name1 = value1; name2; name3=\"value3\" , name4=value4; " +
            "name5=value5, name6= ; name7 = value7; name8 = \" value8\"";
        HeaderElement[] elements = BasicHeaderValueParser.parseElements(headerValue, null);
        // there are 3 elements
        assertEquals(3,elements.length);
        // 1st element
        assertEquals("name1",elements[0].getName());
        assertEquals("value1",elements[0].getValue());
        // 1st element has 2 getParameters()
        assertEquals(2,elements[0].getParameters().length);
        assertEquals("name2",elements[0].getParameters()[0].getName());
        assertEquals(null, elements[0].getParameters()[0].getValue());
        assertEquals("name3",elements[0].getParameters()[1].getName());
        assertEquals("value3",elements[0].getParameters()[1].getValue());
        // 2nd element
        assertEquals("name4",elements[1].getName());
        assertEquals("value4",elements[1].getValue());
        // 2nd element has 1 parameter
        assertEquals(1,elements[1].getParameters().length);
        assertEquals("name5",elements[1].getParameters()[0].getName());
        assertEquals("value5",elements[1].getParameters()[0].getValue());
        // 3rd element
        assertEquals("name6",elements[2].getName());
        assertEquals("",elements[2].getValue());
        // 3rd element has 2 getParameters()
        assertEquals(2,elements[2].getParameters().length);
        assertEquals("name7",elements[2].getParameters()[0].getName());
        assertEquals("value7",elements[2].getParameters()[0].getValue());
        assertEquals("name8",elements[2].getParameters()[1].getName());
        assertEquals(" value8",elements[2].getParameters()[1].getValue());
    }

    public void testParseHEEscaped() {
        String s = 
          "test1 =  \"\\\"stuff\\\"\", test2= \"\\\\\", test3 = \"stuff, stuff\"";
        HeaderElement[] elements = BasicHeaderValueParser.parseElements(s, null);
        assertEquals(3, elements.length);
        assertEquals("test1", elements[0].getName());
        assertEquals("\\\"stuff\\\"", elements[0].getValue());
        assertEquals("test2", elements[1].getName());
        assertEquals("\\\\", elements[1].getValue());
        assertEquals("test3", elements[2].getName());
        assertEquals("stuff, stuff", elements[2].getValue());
    }

    public void testHEFringeCase1() throws Exception {
        String headerValue = "name1 = value1,";
        HeaderElement[] elements = BasicHeaderValueParser.parseElements(headerValue, null);
        assertEquals("Number of elements", 1, elements.length);
    }

    public void testHEFringeCase2() throws Exception {
        String headerValue = "name1 = value1, ";
        HeaderElement[] elements = BasicHeaderValueParser.parseElements(headerValue, null);
        assertEquals("Number of elements", 1, elements.length);
    }

    public void testHEFringeCase3() throws Exception {
        String headerValue = ",, ,, ,";
        HeaderElement[] elements = BasicHeaderValueParser.parseElements(headerValue, null);
        assertEquals("Number of elements", 0, elements.length);
    }

    public void testHEInvalidInput() throws Exception {
        CharArrayBuffer buffer = new CharArrayBuffer(32);
        buffer.append("name = value");
        try {
            BasicHeaderValueParser.parseElements(null, null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }

        try {
            BasicHeaderValueParser.DEFAULT.parseElements(null, 0, 0);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            BasicHeaderValueParser.DEFAULT.parseElements(buffer, -1, 0);
            fail("IllegalArgumentException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            BasicHeaderValueParser.DEFAULT.parseElements(buffer, 0, 1000);
            fail("IllegalArgumentException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            BasicHeaderValueParser.DEFAULT.parseElements(buffer, 2, 1);
            fail("IllegalArgumentException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }


        try {
            BasicHeaderValueParser.DEFAULT.parseHeaderElement(null, 0, 0);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            BasicHeaderValueParser.DEFAULT.parseHeaderElement(buffer, -1, 0);
            fail("IllegalArgumentException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            BasicHeaderValueParser.DEFAULT.parseHeaderElement(buffer, 0, 1000);
            fail("IllegalArgumentException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            BasicHeaderValueParser.DEFAULT.parseHeaderElement(buffer, 2, 1);
            fail("IllegalArgumentException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
    }


    
    public void testNVParse() {
        String s = "test";
        NameValuePair param =
            BasicHeaderValueParser.parseNameValuePair(s, null);
        assertEquals("test", param.getName());
        assertEquals(null, param.getValue());

        s = "test=stuff";
        param = BasicHeaderValueParser.parseNameValuePair(s, null);
        assertEquals("test", param.getName());
        assertEquals("stuff", param.getValue());
        
        s = "   test  =   stuff ";
        param = BasicHeaderValueParser.parseNameValuePair(s, null);
        assertEquals("test", param.getName());
        assertEquals("stuff", param.getValue());
        
        s = "test  = \"stuff\"";
        param = BasicHeaderValueParser.parseNameValuePair(s, null);
        assertEquals("test", param.getName());
        assertEquals("stuff", param.getValue());
        
        s = "test  = \"  stuff\\\"\"";
        param = BasicHeaderValueParser.parseNameValuePair(s, null);
        assertEquals("test", param.getName());
        assertEquals("  stuff\\\"", param.getValue());
        
        s = "  test";
        param = BasicHeaderValueParser.parseNameValuePair(s, null);
        assertEquals("test", param.getName());
        assertEquals(null, param.getValue());

        s = "  ";
        param = BasicHeaderValueParser.parseNameValuePair(s, null);
        assertEquals("", param.getName());
        assertEquals(null, param.getValue());

        s = " = stuff ";
        param = BasicHeaderValueParser.parseNameValuePair(s, null);
        assertEquals("", param.getName());
        assertEquals("stuff", param.getValue());
    }

    public void testNVParseAll() {
        String s = 
          "test; test1 =  stuff   ; test2 =  \"stuff; stuff\"; test3=\"stuff";
        NameValuePair[] params =
            BasicHeaderValueParser.parseParameters(s, null);
        assertEquals("test", params[0].getName());
        assertEquals(null, params[0].getValue());
        assertEquals("test1", params[1].getName());
        assertEquals("stuff", params[1].getValue());
        assertEquals("test2", params[2].getName());
        assertEquals("stuff; stuff", params[2].getValue());
        assertEquals("test3", params[3].getName());
        assertEquals("\"stuff", params[3].getValue());

        s = "  ";
        params = BasicHeaderValueParser.parseParameters(s, null);
        assertEquals(0, params.length);
    }

    public void testNVParseEscaped() {
        String s = 
          "test1 =  \"\\\"stuff\\\"\"; test2= \"\\\\\"; test3 = \"stuff; stuff\"";
        NameValuePair[] params =
            BasicHeaderValueParser.parseParameters(s, null);
        assertEquals(3, params.length);
        assertEquals("test1", params[0].getName());
        assertEquals("\\\"stuff\\\"", params[0].getValue());
        assertEquals("test2", params[1].getName());
        assertEquals("\\\\", params[1].getValue());
        assertEquals("test3", params[2].getName());
        assertEquals("stuff; stuff", params[2].getValue());
    }

    public void testNVParseInvalidInput() throws Exception {
        CharArrayBuffer buffer = new CharArrayBuffer(32);
        buffer.append("name = value");

        try {
            BasicHeaderValueParser.parseParameters(null, null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            BasicHeaderValueParser.DEFAULT.parseParameters(null, 0, 0);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            BasicHeaderValueParser.DEFAULT.parseParameters(buffer, -1, 0);
            fail("IllegalArgumentException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            BasicHeaderValueParser.DEFAULT.parseParameters(buffer, 0, 1000);
            fail("IllegalArgumentException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            BasicHeaderValueParser.DEFAULT.parseParameters(buffer, 2, 1);
            fail("IllegalArgumentException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }

        try {
            BasicHeaderValueParser.parseNameValuePair(null, null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            BasicHeaderValueParser.DEFAULT.parseNameValuePair(null, 0, 0);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            BasicHeaderValueParser.DEFAULT.parseNameValuePair(buffer, -1, 0);
            fail("IllegalArgumentException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            BasicHeaderValueParser.DEFAULT.parseNameValuePair(buffer, 0, 1000);
            fail("IllegalArgumentException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            BasicHeaderValueParser.DEFAULT.parseNameValuePair(buffer, 2, 1);
            fail("IllegalArgumentException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
    }

}
