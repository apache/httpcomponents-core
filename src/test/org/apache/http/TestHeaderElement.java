/*
 * $Header: /home/jerenkrantz/tmp/commons/commons-convert/cvs/home/cvs/jakarta-commons//httpclient/src/test/org/apache/commons/httpclient/TestHeaderElement.java,v 1.7 2004/02/22 18:08:49 olegk Exp $
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
 * [Additional notices, if required by prior licensing conditions]
 *
 */

package org.apache.http;

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
        HeaderElement element = new HeaderElement("name", "value", 
                new NameValuePair[] {
                    new NameValuePair("param1", "value1"),
                    new NameValuePair("param2", "value2")
                } );
        assertEquals("name", element.getName());
        assertEquals("value", element.getValue());
        assertEquals(2, element.getParameters().length);
        assertEquals("value1", element.getParameterByName("param1").getValue());
        assertEquals("value2", element.getParameterByName("param2").getValue());
    }

    public void testConstructor2() throws Exception {
        HeaderElement element = new HeaderElement("name", "value");
        assertEquals("name", element.getName());
        assertEquals("value", element.getValue());
        assertEquals(0, element.getParameters().length);
    }

    public void testCharArrayConstructor() throws Exception {
        String s = "name = value; param1 = value1";
        HeaderElement element = new HeaderElement(s.toCharArray()); 
        assertEquals("name", element.getName());
        assertEquals("value", element.getValue());
        assertEquals(1, element.getParameters().length);
        assertEquals("value1", element.getParameterByName("param1").getValue());
    }
    
    public void testInvalidName() {
        try {
            HeaderElement element = new HeaderElement(null, null, null); 
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            //expected
        }
    }
    
    public void testParseHeaderElements() throws Exception {
        // this is derived from the old main method in HeaderElement
        String headerValue = "name1 = value1; name2; name3=\"value3\" , name4=value4; " +
            "name5=value5, name6= ; name7 = value7; name8 = \" value8\"";
        HeaderElement[] elements = HeaderElement.parseElements(headerValue);
        // there are 3 elements
        assertEquals(3,elements.length);
        // 1st element
        assertEquals("name1",elements[0].getName());
        assertEquals("value1",elements[0].getValue());
        // 1st element has 2 getParameters()
        assertEquals(2,elements[0].getParameters().length);
        assertEquals("name2",elements[0].getParameters()[0].getName());
        assertTrue(null == elements[0].getParameters()[0].getValue());
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
        assertEquals(null,elements[2].getValue());
        // 3rd element has 2 getParameters()
        assertEquals(2,elements[2].getParameters().length);
        assertEquals("name7",elements[2].getParameters()[0].getName());
        assertEquals("value7",elements[2].getParameters()[0].getValue());
        assertEquals("name8",elements[2].getParameters()[1].getName());
        assertEquals(" value8",elements[2].getParameters()[1].getValue());
    }

    public void testFringeCase1() throws Exception {
        String headerValue = "name1 = value1,";
        HeaderElement[] elements = HeaderElement.parseElements(headerValue);
        assertEquals("Number of elements", 1, elements.length);
    }

    public void testFringeCase2() throws Exception {
        String headerValue = "name1 = value1, ";
        HeaderElement[] elements = HeaderElement.parseElements(headerValue);
        assertEquals("Number of elements", 1, elements.length);
    }

    public void testFringeCase3() throws Exception {
        String headerValue = ",, ,, ,";
        HeaderElement[] elements = HeaderElement.parseElements(headerValue);
        assertEquals("Number of elements", 0, elements.length);
    }
    
    public void testNullInput() throws Exception {
        HeaderElement[] elements = HeaderElement.parseElements((char [])null);
        assertNotNull(elements);
        assertEquals("Number of elements", 0, elements.length);
        elements = HeaderElement.parseElements((String)null);
        assertNotNull(elements);
        assertEquals("Number of elements", 0, elements.length);
    }

    public void testParamByName() throws Exception {
        String s = "name = value; param1 = value1; param2 = value2";
        HeaderElement element = new HeaderElement(s.toCharArray()); 
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
        HeaderElement element1 = new HeaderElement("name", "value", 
                new NameValuePair[] {
                    new NameValuePair("param1", "value1"),
                    new NameValuePair("param2", "value2")
                } );
        HeaderElement element2 = new HeaderElement("name", "value", 
                new NameValuePair[] {
                    new NameValuePair("param2", "value2"),
                    new NameValuePair("param1", "value1")
                } );
        HeaderElement element3 = new HeaderElement("name", "value"); 
        HeaderElement element4 = new HeaderElement("name", "value"); 
        HeaderElement element5 = new HeaderElement("name", "value", 
                new NameValuePair[] {
                    new NameValuePair("param1", "value1"),
                    new NameValuePair("param2", "value2")
                } );
        assertTrue(element1.hashCode() != element2.hashCode());
        assertTrue(element1.hashCode() != element3.hashCode());
        assertTrue(element2.hashCode() != element3.hashCode());
        assertTrue(element3.hashCode() == element4.hashCode());
        assertTrue(element1.hashCode() == element5.hashCode());
    }
    
    public void testEquals() {
        HeaderElement element1 = new HeaderElement("name", "value", 
                new NameValuePair[] {
                    new NameValuePair("param1", "value1"),
                    new NameValuePair("param2", "value2")
                } );
        HeaderElement element2 = new HeaderElement("name", "value", 
                new NameValuePair[] {
                    new NameValuePair("param2", "value2"),
                    new NameValuePair("param1", "value1")
                } );
        HeaderElement element3 = new HeaderElement("name", "value"); 
        HeaderElement element4 = new HeaderElement("name", "value"); 
        HeaderElement element5 = new HeaderElement("name", "value", 
                new NameValuePair[] {
                    new NameValuePair("param1", "value1"),
                    new NameValuePair("param2", "value2")
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
    
}
