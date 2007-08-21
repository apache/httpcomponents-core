/*
 * $HeadURL$
 * $Revision$
 * $Date$
 * 
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

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.CharArrayBuffer;

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
        NameValuePair param = new BasicNameValuePair("name", "value");
        assertEquals("name", param.getName()); 
        assertEquals("value", param.getValue()); 
    }
    
    public void testInvalidName() {
        try {
            new BasicNameValuePair(null, null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            //expected
        }
    }
    
    public void testHashCode() {
        NameValuePair param1 = new BasicNameValuePair("name1", "value1");
        NameValuePair param2 = new BasicNameValuePair("name2", "value2");
        NameValuePair param3 = new BasicNameValuePair("name1", "value1");
        assertTrue(param1.hashCode() != param2.hashCode());
        assertTrue(param1.hashCode() == param3.hashCode());
    }
    
    public void testEquals() {
        NameValuePair param1 = new BasicNameValuePair("name1", "value1");
        NameValuePair param2 = new BasicNameValuePair("name2", "value2");
        NameValuePair param3 = new BasicNameValuePair("name1", "value1");
        assertFalse(param1.equals(param2));
        assertFalse(param1.equals(null));
        assertFalse(param1.equals("name1 = value1"));
        assertTrue(param1.equals(param1));
        assertTrue(param2.equals(param2));
        assertTrue(param1.equals(param3));
    }
    
    public void testToString() {
        NameValuePair param1 = new BasicNameValuePair("name1", "value1");
        assertEquals("name1=value1", param1.toString());
        NameValuePair param2 = new BasicNameValuePair("name1", null);
        assertEquals("name1", param2.toString());
    }

    public void testBasicFormatting() throws Exception {
        NameValuePair param1 = new BasicNameValuePair("param", "regular_stuff"); 
        NameValuePair param2 = new BasicNameValuePair("param", "this\\that"); 
        NameValuePair param3 = new BasicNameValuePair("param", "this,that"); 
        NameValuePair param4 = new BasicNameValuePair("param", "quote marks (\") must be escaped"); 
        NameValuePair param5 = new BasicNameValuePair("param", "back slash (\\) must be escaped too"); 
        NameValuePair param6 = new BasicNameValuePair("param", "values with\tblanks must always be quoted"); 
        NameValuePair param7 = new BasicNameValuePair("param", null); 
        
        assertEquals("param=regular_stuff", BasicNameValuePair.format(param1, false));
        assertEquals("param=\"this\\\\that\"", BasicNameValuePair.format(param2, false));
        assertEquals("param=\"this,that\"", BasicNameValuePair.format(param3, false));
        assertEquals("param=\"quote marks (\\\") must be escaped\"", BasicNameValuePair.format(param4, false));
        assertEquals("param=\"back slash (\\\\) must be escaped too\"", BasicNameValuePair.format(param5, false));
        assertEquals("param=\"values with\tblanks must always be quoted\"", BasicNameValuePair.format(param6, false));
        assertEquals("param", BasicNameValuePair.format(param7, false));

        assertEquals("param=\"regular_stuff\"", BasicNameValuePair.format(param1, true));
        assertEquals("param=\"this\\\\that\"", BasicNameValuePair.format(param2, true));
        assertEquals("param=\"this,that\"", BasicNameValuePair.format(param3, true));
        assertEquals("param=\"quote marks (\\\") must be escaped\"", BasicNameValuePair.format(param4, true));
        assertEquals("param=\"back slash (\\\\) must be escaped too\"", BasicNameValuePair.format(param5, true));
        assertEquals("param=\"values with\tblanks must always be quoted\"", BasicNameValuePair.format(param6, true));
        assertEquals("param", BasicNameValuePair.format(param7, false));
    }

    public void testArrayFormatting() throws Exception {
        NameValuePair param1 = new BasicNameValuePair("param", "regular_stuff"); 
        NameValuePair param2 = new BasicNameValuePair("param", "this\\that"); 
        NameValuePair param3 = new BasicNameValuePair("param", "this,that");
        NameValuePair[] params = new NameValuePair[] {param1, param2, param3}; 
        assertEquals("param=regular_stuff; param=\"this\\\\that\"; param=\"this,that\"", 
                BasicNameValuePair.formatAll(params, false));
        assertEquals("param=\"regular_stuff\"; param=\"this\\\\that\"; param=\"this,that\"", 
                BasicNameValuePair.formatAll(params, true));
    }
    
    public void testFormatInvalidInput() throws Exception {
        try {
            BasicNameValuePair.format(null, new BasicNameValuePair("param", "value"), true);
            fail("IllegalArgumentException should habe been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            BasicNameValuePair.format(new CharArrayBuffer(10), (NameValuePair) null, true);
            fail("IllegalArgumentException should habe been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            BasicNameValuePair.formatAll(null, new NameValuePair[] {new BasicNameValuePair("param", "value")}, true);
            fail("IllegalArgumentException should habe been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            BasicNameValuePair.formatAll(new CharArrayBuffer(10), (NameValuePair[]) null, true);
            fail("IllegalArgumentException should habe been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }
    
}
