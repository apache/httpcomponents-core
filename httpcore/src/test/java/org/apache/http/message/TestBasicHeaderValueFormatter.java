/*
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

import junit.framework.TestCase;

import org.apache.http.HeaderElement;
import org.apache.http.NameValuePair;

/**
 * Tests for header value formatting.
 *
 *
 */
public class TestBasicHeaderValueFormatter extends TestCase {

    // ------------------------------------------------------------ Constructor
    public TestBasicHeaderValueFormatter(String testName) {
        super(testName);
    }

    // ------------------------------------------------------- TestCase Methods

    public void testNVPFormatting() throws Exception {
        NameValuePair param1 = new BasicNameValuePair("param", "regular_stuff");
        NameValuePair param2 = new BasicNameValuePair("param", "this\\that");
        NameValuePair param3 = new BasicNameValuePair("param", "this,that");
        NameValuePair param4 = new BasicNameValuePair("param", "quote marks (\") must be escaped");
        NameValuePair param5 = new BasicNameValuePair("param", "back slash (\\) must be escaped too");
        NameValuePair param6 = new BasicNameValuePair("param", "values with\tblanks must always be quoted");
        NameValuePair param7 = new BasicNameValuePair("param", null);


        assertEquals("param=regular_stuff",
                     BasicHeaderValueFormatter.formatNameValuePair
                     (param1, false, null));
        assertEquals("param=\"this\\\\that\"",
                     BasicHeaderValueFormatter.formatNameValuePair
                     (param2, false, null));
        assertEquals("param=\"this,that\"",
                     BasicHeaderValueFormatter.formatNameValuePair
                     (param3, false, null));
        assertEquals("param=\"quote marks (\\\") must be escaped\"",
                     BasicHeaderValueFormatter.formatNameValuePair
                     (param4, false, null));
        assertEquals("param=\"back slash (\\\\) must be escaped too\"",
                     BasicHeaderValueFormatter.formatNameValuePair
                     (param5, false, null));
        assertEquals("param=\"values with\tblanks must always be quoted\"",
                     BasicHeaderValueFormatter.formatNameValuePair
                     (param6, false, null));
        assertEquals("param", BasicHeaderValueFormatter.formatNameValuePair
                     (param7, false, null));

        assertEquals("param=\"regular_stuff\"",
                     BasicHeaderValueFormatter.formatNameValuePair
                     (param1, true, null));
        assertEquals("param=\"this\\\\that\"",
                     BasicHeaderValueFormatter.formatNameValuePair
                     (param2, true, null));
        assertEquals("param=\"this,that\"",
                     BasicHeaderValueFormatter.formatNameValuePair
                     (param3, true, null));
        assertEquals("param=\"quote marks (\\\") must be escaped\"",
                     BasicHeaderValueFormatter.formatNameValuePair
                     (param4, true, null));
        assertEquals("param=\"back slash (\\\\) must be escaped too\"",
                     BasicHeaderValueFormatter.formatNameValuePair
                     (param5, true, null));
        assertEquals("param=\"values with\tblanks must always be quoted\"",
                     BasicHeaderValueFormatter.formatNameValuePair
                     (param6, true, null));
        assertEquals("param",
                     BasicHeaderValueFormatter.formatNameValuePair
                     (param7, false, null));
    }



    public void testParamsFormatting() throws Exception {
        NameValuePair param1 = new BasicNameValuePair("param", "regular_stuff");
        NameValuePair param2 = new BasicNameValuePair("param", "this\\that");
        NameValuePair param3 = new BasicNameValuePair("param", "this,that");
        NameValuePair[] params = new NameValuePair[] {param1, param2, param3};
        assertEquals("param=regular_stuff; param=\"this\\\\that\"; param=\"this,that\"",
                     BasicHeaderValueFormatter.formatParameters(params, false, null));
        assertEquals("param=\"regular_stuff\"; param=\"this\\\\that\"; param=\"this,that\"",
                     BasicHeaderValueFormatter.formatParameters(params, true, null));
    }



    public void testHEFormatting() throws Exception {
        NameValuePair param1 = new BasicNameValuePair("param", "regular_stuff");
        NameValuePair param2 = new BasicNameValuePair("param", "this\\that");
        NameValuePair param3 = new BasicNameValuePair("param", "this,that");
        NameValuePair param4 = new BasicNameValuePair("param", null);
        NameValuePair[] params = new NameValuePair[] {param1, param2, param3, param4};
        HeaderElement element = new BasicHeaderElement("name", "value", params);

        assertEquals("name=value; param=regular_stuff; param=\"this\\\\that\"; param=\"this,that\"; param",
                     BasicHeaderValueFormatter.formatHeaderElement(element, false, null));
    }

    public void testElementsFormatting() throws Exception {
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

        assertEquals
            ("name1=value1; param=regular_stuff, name2=value2; " +
             "param=\"this\\\\that\", name3=value3; param=\"this,that\", " +
             "name4=value4; param, name5",
             BasicHeaderValueFormatter.formatElements(elements, false, null));
    }


    public void testInvalidHEArguments() throws Exception {
        try {
            BasicHeaderValueFormatter.formatHeaderElement
                ((HeaderElement) null, false,
                 BasicHeaderValueFormatter.DEFAULT);
            fail("IllegalArgumentException should habe been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }

        try {
            BasicHeaderValueFormatter.formatElements
                ((HeaderElement[]) null, false,
                 BasicHeaderValueFormatter.DEFAULT);
            fail("IllegalArgumentException should habe been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }


    public void testInvalidNVArguments() throws Exception {

        try {
            BasicHeaderValueFormatter.formatNameValuePair
                ((NameValuePair) null, true, null);
            fail("IllegalArgumentException should habe been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }

        try {
            BasicHeaderValueFormatter.formatParameters
                ((NameValuePair[]) null, true,
                 BasicHeaderValueFormatter.DEFAULT);
            fail("IllegalArgumentException should habe been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }


}
