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

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.util.List;

import org.apache.http.NameValuePair;

/**
 * Unit tests for {@link ParameterParser}.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 */
public class TestParameterParser extends TestCase {

    public TestParameterParser(String testName) {
        super(testName);
    }

    public static void main(String args[]) {
        String[] testCaseName = { TestParameterParser.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    public static Test suite() {
        return new TestSuite(TestParameterParser.class);
    }

    public void testParsing() {
        String s = 
          "test; test1 =  stuff   ; test2 =  \"stuff; stuff\"; test3=\"stuff";
        ParameterParser  parser = new ParameterParser();
        List params = parser.parse(s, ';');
        assertEquals("test", ((NameValuePair)params.get(0)).getName());
        assertEquals(null, ((NameValuePair)params.get(0)).getValue());
        assertEquals("test1", ((NameValuePair)params.get(1)).getName());
        assertEquals("stuff", ((NameValuePair)params.get(1)).getValue());
        assertEquals("test2", ((NameValuePair)params.get(2)).getName());
        assertEquals("stuff; stuff", ((NameValuePair)params.get(2)).getValue());
        assertEquals("test3", ((NameValuePair)params.get(3)).getName());
        assertEquals("\"stuff", ((NameValuePair)params.get(3)).getValue());

        s = "  test  , test1=stuff   ,  , test2=, test3, ";
        params = parser.parse(s, ',');
        assertEquals("test", ((NameValuePair)params.get(0)).getName());
        assertEquals(null, ((NameValuePair)params.get(0)).getValue());
        assertEquals("test1", ((NameValuePair)params.get(1)).getName());
        assertEquals("stuff", ((NameValuePair)params.get(1)).getValue());
        assertEquals("test2", ((NameValuePair)params.get(2)).getName());
        assertEquals(null, ((NameValuePair)params.get(2)).getValue());
        assertEquals("test3", ((NameValuePair)params.get(3)).getName());
        assertEquals(null, ((NameValuePair)params.get(3)).getValue());

        s = "  test";
        params = parser.parse(s, ';');
        assertEquals("test", ((NameValuePair)params.get(0)).getName());
        assertEquals(null, ((NameValuePair)params.get(0)).getValue());

        s = "  ";
        params = parser.parse(s, ';');
        assertEquals(0, params.size());

        s = " = stuff ";
        params = parser.parse(s, ';');
        assertEquals(0, params.size());
    }
}
