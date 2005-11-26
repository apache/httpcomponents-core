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

package org.apache.http;

import org.apache.http.io.CharArrayBuffer;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Unit tests for {@link NameValuePair}.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 */
public class TestNameValuePair extends TestCase {

    public TestNameValuePair(String testName) {
        super(testName);
    }

    public static void main(String args[]) {
        String[] testCaseName = { TestNameValuePair.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    public static Test suite() {
        return new TestSuite(TestNameValuePair.class);
    }

    public void testConstructor() {
        NameValuePair param = new NameValuePair("name", "value");
        assertEquals("name", param.getName()); 
        assertEquals("value", param.getValue()); 
    }
    
    public void testInvalidName() {
        try {
            new NameValuePair(null, null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            //expected
        }
    }
    
    public void testHashCode() {
        NameValuePair param1 = new NameValuePair("name1", "value1");
        NameValuePair param2 = new NameValuePair("name2", "value2");
        NameValuePair param3 = new NameValuePair("name1", "value1");
        assertTrue(param1.hashCode() != param2.hashCode());
        assertTrue(param1.hashCode() == param3.hashCode());
    }
    
    public void testEquals() {
        NameValuePair param1 = new NameValuePair("name1", "value1");
        NameValuePair param2 = new NameValuePair("name2", "value2");
        NameValuePair param3 = new NameValuePair("name1", "value1");
        assertFalse(param1.equals(param2));
        assertFalse(param1.equals(null));
        assertFalse(param1.equals("name1 = value1"));
        assertTrue(param1.equals(param1));
        assertTrue(param2.equals(param2));
        assertTrue(param1.equals(param3));
    }
    
    public void testToString() {
        NameValuePair param1 = new NameValuePair("name1", "value1");
        assertEquals("name1 = value1", param1.toString());
    }
    
    public void testParse() {
        String s = "test";
        NameValuePair param = NameValuePair.parse(s);
        assertEquals("test", param.getName());
        assertEquals(null, param.getValue());

        s = "test=stuff";
        param = NameValuePair.parse(s);
        assertEquals("test", param.getName());
        assertEquals("stuff", param.getValue());
        
        s = "   test  =   stuff ";
        param = NameValuePair.parse(s);
        assertEquals("test", param.getName());
        assertEquals("stuff", param.getValue());
        
        s = "test  = \"stuff\"";
        param = NameValuePair.parse(s);
        assertEquals("test", param.getName());
        assertEquals("stuff", param.getValue());
        
        s = "test  = \"  stuff\\\"\"";
        param = NameValuePair.parse(s);
        assertEquals("test", param.getName());
        assertEquals("  stuff\\\"", param.getValue());
        
        s = "  test";
        param = NameValuePair.parse(s);
        assertEquals("test", param.getName());
        assertEquals(null, param.getValue());

        s = "  ";
        param = NameValuePair.parse(s);
        assertEquals("", param.getName());
        assertEquals(null, param.getValue());

        s = " = stuff ";
        param = NameValuePair.parse(s);
        assertEquals("", param.getName());
        assertEquals("stuff", param.getValue());
    }

    public void testParseAll() {
        String s = 
          "test; test1 =  stuff   ; test2 =  \"stuff; stuff\"; test3=\"stuff";
        NameValuePair[] params = NameValuePair.parseAll(s);
        assertEquals("test", params[0].getName());
        assertEquals(null, params[0].getValue());
        assertEquals("test1", params[1].getName());
        assertEquals("stuff", params[1].getValue());
        assertEquals("test2", params[2].getName());
        assertEquals("stuff; stuff", params[2].getValue());
        assertEquals("test3", params[3].getName());
        assertEquals("\"stuff", params[3].getValue());

        s = "  ";
        params = NameValuePair.parseAll(s);
        assertEquals(0, params.length);
    }

    public void testParseEscaped() {
        String s = 
          "test1 = stuff\\; stuff; test2 =  \"\\\"stuff\\\"\"";
        NameValuePair[] params = NameValuePair.parseAll(s);
        assertEquals("test1", params[0].getName());
        assertEquals("stuff\\; stuff", params[0].getValue());
        assertEquals("test2", params[1].getName());
        assertEquals("\\\"stuff\\\"", params[1].getValue());
    }

    public void testInvalidInput() throws Exception {
        CharArrayBuffer buffer = new CharArrayBuffer(32);
        buffer.append("name = value");
        try {
            NameValuePair.parseAll(null, 0, 0);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            NameValuePair.parseAll(null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            NameValuePair.parseAll(buffer, -1, 0);
            fail("IllegalArgumentException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            NameValuePair.parseAll(buffer, 0, 1000);
            fail("IllegalArgumentException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            NameValuePair.parseAll(buffer, 2, 1);
            fail("IllegalArgumentException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            NameValuePair.parse(null, 0, 0);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            NameValuePair.parse(null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            NameValuePair.parse(buffer, -1, 0);
            fail("IllegalArgumentException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            NameValuePair.parse(buffer, 0, 1000);
            fail("IllegalArgumentException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            NameValuePair.parse(buffer, 2, 1);
            fail("IllegalArgumentException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
    }
    
}
