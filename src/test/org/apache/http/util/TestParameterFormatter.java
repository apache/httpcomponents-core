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

package org.apache.http.util;

import org.apache.http.NameValuePair;
import org.apache.http.util.ParameterFormatter;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Unit tests for {@link ParameterFormatter}.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 */
public class TestParameterFormatter extends TestCase {

    // ------------------------------------------------------------ Constructor
    public TestParameterFormatter(String testName) {
        super(testName);
    }

    // ------------------------------------------------------------------- Main
    public static void main(String args[]) {
        String[] testCaseName = { TestParameterFormatter.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    // ------------------------------------------------------- TestCase Methods

    public static Test suite() {
        return new TestSuite(TestParameterFormatter.class);
    }

    public void testBasicValueFormatting() throws Exception {
        NameValuePair param1 = new NameValuePair("param", "regular_stuff"); 
        NameValuePair param2 = new NameValuePair("param", "this\\that"); 
        NameValuePair param3 = new NameValuePair("param", "this,that"); 
        NameValuePair param4 = new NameValuePair("param", "quote marks (\") must be escaped"); 
        NameValuePair param5 = new NameValuePair("param", "back slash (\\) must be escaped too"); 
        NameValuePair param6 = new NameValuePair("param", "values with\tblanks must always be quoted"); 
        NameValuePair param7 = new NameValuePair("param", null); 
        
        assertEquals("param=regular_stuff", ParameterFormatter.format(param1, false));
        assertEquals("param=\"this\\\\that\"", ParameterFormatter.format(param2, false));
        assertEquals("param=\"this,that\"", ParameterFormatter.format(param3, false));
        assertEquals("param=\"quote marks (\\\") must be escaped\"", ParameterFormatter.format(param4, false));
        assertEquals("param=\"back slash (\\\\) must be escaped too\"", ParameterFormatter.format(param5, false));
        assertEquals("param=\"values with\tblanks must always be quoted\"", ParameterFormatter.format(param6, false));
        assertEquals("param", ParameterFormatter.format(param7, false));

        assertEquals("param=\"regular_stuff\"", ParameterFormatter.format(param1, true));
        assertEquals("param=\"this\\\\that\"", ParameterFormatter.format(param2, true));
        assertEquals("param=\"this,that\"", ParameterFormatter.format(param3, true));
        assertEquals("param=\"quote marks (\\\") must be escaped\"", ParameterFormatter.format(param4, true));
        assertEquals("param=\"back slash (\\\\) must be escaped too\"", ParameterFormatter.format(param5, true));
        assertEquals("param=\"values with\tblanks must always be quoted\"", ParameterFormatter.format(param6, true));
        assertEquals("param", ParameterFormatter.format(param7, false));
    }

    public void testInvalidInput() throws Exception {
        try {
        	ParameterFormatter.format(null, new NameValuePair("param", "value"), true);
            fail("IllegalArgumentException should habe been thrown");
        } catch (IllegalArgumentException ex) {
        	// expected
        }
        try {
        	ParameterFormatter.format(null, "ssss", true);
            fail("IllegalArgumentException should habe been thrown");
        } catch (IllegalArgumentException ex) {
        	// expected
        }
        try {
        	ParameterFormatter.format(new StringBuffer(), (NameValuePair) null, true);
            fail("IllegalArgumentException should habe been thrown");
        } catch (IllegalArgumentException ex) {
        	// expected
        }
        try {
        	ParameterFormatter.format(new StringBuffer(), (String) null, true);
            fail("IllegalArgumentException should habe been thrown");
        } catch (IllegalArgumentException ex) {
        	// expected
        }
    }
    
}
