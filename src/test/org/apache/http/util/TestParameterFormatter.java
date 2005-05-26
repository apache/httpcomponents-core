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
        ParameterFormatter formatter = new ParameterFormatter();
        
        NameValuePair param1 = new NameValuePair("param", "regular_stuff"); 
        NameValuePair param2 = new NameValuePair("param", "this\\that"); 
        NameValuePair param3 = new NameValuePair("param", "this,that"); 
        NameValuePair param4 = new NameValuePair("param", "quote marks (\") must be escaped"); 
        NameValuePair param5 = new NameValuePair("param", "back slash (\\) must be escaped too"); 
        NameValuePair param6 = new NameValuePair("param", "values with\tblanks must always be quoted"); 
        
        formatter.setAlwaysUseQuotes(false);
        assertEquals("param=regular_stuff", formatter.format(param1));
        assertEquals("param=\"this\\\\that\"", formatter.format(param2));
        assertEquals("param=\"this,that\"", formatter.format(param3));
        assertEquals("param=\"quote marks (\\\") must be escaped\"", formatter.format(param4));
        assertEquals("param=\"back slash (\\\\) must be escaped too\"", formatter.format(param5));
        assertEquals("param=\"values with\tblanks must always be quoted\"", formatter.format(param6));

        formatter.setAlwaysUseQuotes(true);
        assertEquals("param=\"regular_stuff\"", formatter.format(param1));
        assertEquals("param=\"this\\\\that\"", formatter.format(param2));
        assertEquals("param=\"this,that\"", formatter.format(param3));
        assertEquals("param=\"quote marks (\\\") must be escaped\"", formatter.format(param4));
        assertEquals("param=\"back slash (\\\\) must be escaped too\"", formatter.format(param5));
        assertEquals("param=\"values with\tblanks must always be quoted\"", formatter.format(param6));
    }
    
}
