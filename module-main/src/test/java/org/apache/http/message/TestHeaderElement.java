/*
 * $Header: /home/jerenkrantz/tmp/commons/commons-convert/cvs/home/cvs/jakarta-commons//httpclient/src/test/org/apache/commons/httpclient/TestHeaderElement.java,v 1.7 2004/02/22 18:08:49 olegk Exp $
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
 * [Additional notices, if required by prior licensing conditions]
 *
 */

package org.apache.http.message;

import org.apache.http.HeaderElement;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicHeaderElement;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.CharArrayBuffer;

import junit.framework.*;

/**
 * Simple tests for {@link HeaderElement}.
 *
 * @author Rodney Waldhoff
 * @author <a href="mailto:bcholmes@interlog.com">B.C. Holmes</a>
 * @author <a href="mailto:jericho@thinkfree.com">Park, Sung-Gu</a>
 * @author <a href="mailto:oleg at ural.ru">oleg Kalnichevski</a>
 * @version $Id$
 */
public class TestHeaderElement extends TestCase {

    // ------------------------------------------------------------ Constructor
    public TestHeaderElement(String testName) {
        super(testName);
    }

    // ------------------------------------------------------------------- Main
    public static void main(String args[]) {
        String[] testCaseName = { TestHeaderElement.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    // ------------------------------------------------------- TestCase Methods

    public static Test suite() {
        return new TestSuite(TestHeaderElement.class);
    }

    public void testConstructor() throws Exception {
        HeaderElement element = new BasicHeaderElement("name", "value", 
                new NameValuePair[] {
                    new BasicNameValuePair("param1", "value1"),
                    new BasicNameValuePair("param2", "value2")
                } );
        assertEquals("name", element.getName());
        assertEquals("value", element.getValue());
        assertEquals(2, element.getParameters().length);
        assertEquals("value1", element.getParameterByName("param1").getValue());
        assertEquals("value2", element.getParameterByName("param2").getValue());
    }

    public void testConstructor2() throws Exception {
        HeaderElement element = new BasicHeaderElement("name", "value");
        assertEquals("name", element.getName());
        assertEquals("value", element.getValue());
        assertEquals(0, element.getParameters().length);
    }

    public void testCharArrayConstructor() throws Exception {
        String s = "name = value; param1 = value1";
        HeaderElement element = BasicHeaderElement.parse(s); 
        assertEquals("name", element.getName());
        assertEquals("value", element.getValue());
        assertEquals(1, element.getParameters().length);
        assertEquals("value1", element.getParameterByName("param1").getValue());
    }
    
    public void testInvalidName() {
        try {
            new BasicHeaderElement(null, null, null); 
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            //expected
        }
    }
    
    public void testParseHeaderElements() throws Exception {
        String headerValue = "name1 = value1; name2; name3=\"value3\" , name4=value4; " +
            "name5=value5, name6= ; name7 = value7; name8 = \" value8\"";
        HeaderElement[] elements = BasicHeaderElement.parseAll(headerValue);
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

    public void testParseEscaped() {
        String s = 
          "test1 =  \"\\\"stuff\\\"\", test2= \"\\\\\", test3 = \"stuff, stuff\"";
        HeaderElement[] elements = BasicHeaderElement.parseAll(s);
        assertEquals(3, elements.length);
        assertEquals("test1", elements[0].getName());
        assertEquals("\\\"stuff\\\"", elements[0].getValue());
        assertEquals("test2", elements[1].getName());
        assertEquals("\\\\", elements[1].getValue());
        assertEquals("test3", elements[2].getName());
        assertEquals("stuff, stuff", elements[2].getValue());
    }

    public void testFringeCase1() throws Exception {
        String headerValue = "name1 = value1,";
        HeaderElement[] elements = BasicHeaderElement.parseAll(headerValue);
        assertEquals("Number of elements", 1, elements.length);
    }

    public void testFringeCase2() throws Exception {
        String headerValue = "name1 = value1, ";
        HeaderElement[] elements = BasicHeaderElement.parseAll(headerValue);
        assertEquals("Number of elements", 1, elements.length);
    }

    public void testFringeCase3() throws Exception {
        String headerValue = ",, ,, ,";
        HeaderElement[] elements = BasicHeaderElement.parseAll(headerValue);
        assertEquals("Number of elements", 0, elements.length);
    }

    public void testInvalidInput() throws Exception {
        CharArrayBuffer buffer = new CharArrayBuffer(32);
        buffer.append("name = value");
        try {
            BasicHeaderElement.parseAll(null, 0, 0);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            BasicHeaderElement.parseAll(null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            BasicHeaderElement.parseAll(buffer, -1, 0);
            fail("IllegalArgumentException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            BasicHeaderElement.parseAll(buffer, 0, 1000);
            fail("IllegalArgumentException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            BasicHeaderElement.parseAll(buffer, 2, 1);
            fail("IllegalArgumentException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            BasicHeaderElement.parse(null, 0, 0);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            BasicHeaderElement.parse(null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            BasicHeaderElement.parse(buffer, -1, 0);
            fail("IllegalArgumentException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            BasicHeaderElement.parse(buffer, 0, 1000);
            fail("IllegalArgumentException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            BasicHeaderElement.parse(buffer, 2, 1);
            fail("IllegalArgumentException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
    }
    
    public void testParamByName() throws Exception {
        String s = "name = value; param1 = value1; param2 = value2";
        HeaderElement element = BasicHeaderElement.parse(s); 
        assertEquals("value1", element.getParameterByName("param1").getValue());
        assertEquals("value2", element.getParameterByName("param2").getValue());
        assertNull(element.getParameterByName("param3"));
        try {
            element.getParameterByName(null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            //expected
        }
    }

    public void testHashCode() {
        HeaderElement element1 = new BasicHeaderElement("name", "value", 
                new NameValuePair[] {
                    new BasicNameValuePair("param1", "value1"),
                    new BasicNameValuePair("param2", "value2")
                } );
        HeaderElement element2 = new BasicHeaderElement("name", "value", 
                new NameValuePair[] {
                    new BasicNameValuePair("param2", "value2"),
                    new BasicNameValuePair("param1", "value1")
                } );
        HeaderElement element3 = new BasicHeaderElement("name", "value"); 
        HeaderElement element4 = new BasicHeaderElement("name", "value"); 
        HeaderElement element5 = new BasicHeaderElement("name", "value", 
                new NameValuePair[] {
                    new BasicNameValuePair("param1", "value1"),
                    new BasicNameValuePair("param2", "value2")
                } );
        assertTrue(element1.hashCode() != element2.hashCode());
        assertTrue(element1.hashCode() != element3.hashCode());
        assertTrue(element2.hashCode() != element3.hashCode());
        assertTrue(element3.hashCode() == element4.hashCode());
        assertTrue(element1.hashCode() == element5.hashCode());
    }
    
    public void testEquals() {
        HeaderElement element1 = new BasicHeaderElement("name", "value", 
                new NameValuePair[] {
                    new BasicNameValuePair("param1", "value1"),
                    new BasicNameValuePair("param2", "value2")
                } );
        HeaderElement element2 = new BasicHeaderElement("name", "value", 
                new NameValuePair[] {
                    new BasicNameValuePair("param2", "value2"),
                    new BasicNameValuePair("param1", "value1")
                } );
        HeaderElement element3 = new BasicHeaderElement("name", "value"); 
        HeaderElement element4 = new BasicHeaderElement("name", "value"); 
        HeaderElement element5 = new BasicHeaderElement("name", "value", 
                new NameValuePair[] {
                    new BasicNameValuePair("param1", "value1"),
                    new BasicNameValuePair("param2", "value2")
                } );
        assertTrue(element1.equals(element1));
        assertTrue(!element1.equals(element2));
        assertTrue(!element1.equals(element3));
        assertTrue(!element2.equals(element3));
        assertTrue(element3.equals(element4));
        assertTrue(element1.equals(element5));
        assertFalse(element1.equals(null));
        assertFalse(element1.equals("name = value; param1 = value1; param2 = value2"));
    }
    
    public void testToString() {
        String s = "name=value; param1=value1; param2=value2";
        HeaderElement element = BasicHeaderElement.parse(s);
        assertEquals(s, element.toString());
        s = "name; param1=value1; param2=value2";
        element = BasicHeaderElement.parse(s);
        assertEquals(s, element.toString());
    }
    
    public void testElementFormatting() throws Exception {
        NameValuePair param1 = new BasicNameValuePair("param", "regular_stuff"); 
        NameValuePair param2 = new BasicNameValuePair("param", "this\\that"); 
        NameValuePair param3 = new BasicNameValuePair("param", "this,that");
        NameValuePair param4 = new BasicNameValuePair("param", null);
        NameValuePair[] params = new NameValuePair[] {param1, param2, param3, param4};
        HeaderElement element = new BasicHeaderElement("name", "value", params); 
        
        assertEquals("name=value; param=regular_stuff; param=\"this\\\\that\"; param=\"this,that\"; param", 
                BasicHeaderElement.format(element));
    }
    
    public void testElementArrayFormatting() throws Exception {
        NameValuePair param1 = new BasicNameValuePair("param", "regular_stuff"); 
        NameValuePair param2 = new BasicNameValuePair("param", "this\\that"); 
        NameValuePair param3 = new BasicNameValuePair("param", "this,that");
        NameValuePair param4 = new BasicNameValuePair("param", null);
        HeaderElement element1 = new BasicHeaderElement("name1", "value1", new NameValuePair[] {param1}); 
        HeaderElement element2 = new BasicHeaderElement("name2", "value2", new NameValuePair[] {param2}); 
        HeaderElement element3 = new BasicHeaderElement("name3", "value3", new NameValuePair[] {param3}); 
        HeaderElement element4 = new BasicHeaderElement("name4", "value4", new NameValuePair[] {param4}); 
        HeaderElement element5 = new BasicHeaderElement("name5", null); 
        HeaderElement[] elements = new HeaderElement[] {element1, element2, element3, element4, element5}; 
        
        assertEquals("name1=value1; param=regular_stuff, name2=value2; " +
                "param=\"this\\\\that\", name3=value3; param=\"this,that\", " +
                "name4=value4; param, name5", 
                BasicHeaderElement.formatAll(elements));
    }
    
    public void testFormatInvalidInput() throws Exception {
        try {
            BasicHeaderElement.format(null, new BasicHeaderElement("name1", "value1"));
            fail("IllegalArgumentException should habe been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            BasicHeaderElement.format(new CharArrayBuffer(10), (HeaderElement) null);
            fail("IllegalArgumentException should habe been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            BasicHeaderElement.formatAll(null, new HeaderElement[] {new BasicHeaderElement("name1", "value1")});
            fail("IllegalArgumentException should habe been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            BasicHeaderElement.formatAll(new CharArrayBuffer(10), (HeaderElement[]) null);
            fail("IllegalArgumentException should habe been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }
    
}
