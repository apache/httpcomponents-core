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
 */
package org.apache.hc.core5.http.message;

import org.apache.hc.core5.http.HeaderElement;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.util.CharArrayBuffer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for header value formatting.
 *
 *
 */
public class TestBasicHeaderValueFormatter {

    private BasicHeaderValueFormatter formatter;

    @Before
    public void setup() {
        this.formatter = BasicHeaderValueFormatter.INSTANCE;
    }

    @Test
    public void testNVPFormatting() throws Exception {
        final NameValuePair param1 = new BasicNameValuePair("param", "regular_stuff");
        final NameValuePair param2 = new BasicNameValuePair("param", "this\\that");
        final NameValuePair param3 = new BasicNameValuePair("param", "this,that");
        final NameValuePair param4 = new BasicNameValuePair("param", "quote marks (\") must be escaped");
        final NameValuePair param5 = new BasicNameValuePair("param", "back slash (\\) must be escaped too");
        final NameValuePair param6 = new BasicNameValuePair("param", "values with\tblanks must always be quoted");
        final NameValuePair param7 = new BasicNameValuePair("param", null);

        final CharArrayBuffer buf = new CharArrayBuffer(64);

        buf.clear();
        this.formatter.formatNameValuePair(buf, param1, false);
        Assert.assertEquals("param=regular_stuff", buf.toString());
        buf.clear();
        this.formatter.formatNameValuePair(buf, param2, false);
        Assert.assertEquals("param=\"this\\\\that\"", buf.toString());
        buf.clear();
        this.formatter.formatNameValuePair(buf, param3, false);
        Assert.assertEquals("param=\"this,that\"", buf.toString());
        buf.clear();
        this.formatter.formatNameValuePair(buf, param4, false);
        Assert.assertEquals("param=\"quote marks (\\\") must be escaped\"", buf.toString());
        buf.clear();
        this.formatter.formatNameValuePair(buf, param5, false);
        Assert.assertEquals("param=\"back slash (\\\\) must be escaped too\"", buf.toString());
        buf.clear();
        this.formatter.formatNameValuePair(buf, param6, false);
        Assert.assertEquals("param=\"values with\tblanks must always be quoted\"", buf.toString());
        buf.clear();
        this.formatter.formatNameValuePair(buf, param7, false);
        Assert.assertEquals("param", buf.toString());

        buf.clear();
        this.formatter.formatNameValuePair(buf, param1, true);
        Assert.assertEquals("param=\"regular_stuff\"", buf.toString());
        buf.clear();
        this.formatter.formatNameValuePair(buf, param2, true);
        Assert.assertEquals("param=\"this\\\\that\"", buf.toString());
        buf.clear();
        this.formatter.formatNameValuePair(buf, param3, true);
        Assert.assertEquals("param=\"this,that\"", buf.toString());
        buf.clear();
        this.formatter.formatNameValuePair(buf, param4, true);
        Assert.assertEquals("param=\"quote marks (\\\") must be escaped\"", buf.toString());
        buf.clear();
        this.formatter.formatNameValuePair(buf, param5, true);
        Assert.assertEquals("param=\"back slash (\\\\) must be escaped too\"", buf.toString());
        buf.clear();
        this.formatter.formatNameValuePair(buf, param6, true);
        Assert.assertEquals("param=\"values with\tblanks must always be quoted\"", buf.toString());
        buf.clear();
        this.formatter.formatNameValuePair(buf, param7, true);
        Assert.assertEquals("param", buf.toString());
    }

    @Test
    public void testParamsFormatting() throws Exception {
        final NameValuePair param1 = new BasicNameValuePair("param", "regular_stuff");
        final NameValuePair param2 = new BasicNameValuePair("param", "this\\that");
        final NameValuePair param3 = new BasicNameValuePair("param", "this,that");
        final NameValuePair[] params = new NameValuePair[] {param1, param2, param3};

        final CharArrayBuffer buf = new CharArrayBuffer(64);

        buf.clear();
        this.formatter.formatParameters(buf, params, false);
        Assert.assertEquals("param=regular_stuff; param=\"this\\\\that\"; param=\"this,that\"",
                buf.toString());
        buf.clear();
        this.formatter.formatParameters(buf, params, true);
        Assert.assertEquals("param=\"regular_stuff\"; param=\"this\\\\that\"; param=\"this,that\"",
                buf.toString());
    }

    @Test
    public void testHEFormatting() throws Exception {
        final NameValuePair param1 = new BasicNameValuePair("param", "regular_stuff");
        final NameValuePair param2 = new BasicNameValuePair("param", "this\\that");
        final NameValuePair param3 = new BasicNameValuePair("param", "this,that");
        final NameValuePair param4 = new BasicNameValuePair("param", null);
        final NameValuePair[] params = new NameValuePair[] {param1, param2, param3, param4};
        final HeaderElement element = new BasicHeaderElement("name", "value", params);

        final CharArrayBuffer buf = new CharArrayBuffer(64);

        this.formatter.formatHeaderElement(buf, element, false);
        Assert.assertEquals("name=value; param=regular_stuff; param=\"this\\\\that\"; param=\"this,that\"; param",
                buf.toString());
    }

    @Test
    public void testElementsFormatting() throws Exception {
        final NameValuePair param1 = new BasicNameValuePair("param", "regular_stuff");
        final NameValuePair param2 = new BasicNameValuePair("param", "this\\that");
        final NameValuePair param3 = new BasicNameValuePair("param", "this,that");
        final NameValuePair param4 = new BasicNameValuePair("param", null);
        final HeaderElement element1 = new BasicHeaderElement("name1", "value1", new NameValuePair[] {param1});
        final HeaderElement element2 = new BasicHeaderElement("name2", "value2", new NameValuePair[] {param2});
        final HeaderElement element3 = new BasicHeaderElement("name3", "value3", new NameValuePair[] {param3});
        final HeaderElement element4 = new BasicHeaderElement("name4", "value4", new NameValuePair[] {param4});
        final HeaderElement element5 = new BasicHeaderElement("name5", null);
        final HeaderElement[] elements = new HeaderElement[] {element1, element2, element3, element4, element5};

        final CharArrayBuffer buf = new CharArrayBuffer(64);

        this.formatter.formatElements(buf, elements, false);
        Assert.assertEquals("name1=value1; param=regular_stuff, name2=value2; " +
             "param=\"this\\\\that\", name3=value3; param=\"this,that\", " +
             "name4=value4; param, name5", buf.toString());
    }


    @Test
    public void testInvalidArguments() throws Exception {
        final CharArrayBuffer buf = new CharArrayBuffer(64);
        final NameValuePair param = new BasicNameValuePair("param", "regular_stuff");
        final NameValuePair[] params = new NameValuePair[] {param};
        final HeaderElement element = new BasicHeaderElement("name1", "value1", null);
        final HeaderElement[] elements = new HeaderElement[] {element};

        Assert.assertThrows(NullPointerException.class, () ->
                formatter.formatNameValuePair(null, param, false));
        Assert.assertThrows(NullPointerException.class, () ->
                formatter.formatNameValuePair(buf, null, false));
        Assert.assertThrows(NullPointerException.class, () ->
                formatter.formatParameters(null, params, false));
        Assert.assertThrows(NullPointerException.class, () ->
                formatter.formatParameters(buf, null, false));
        Assert.assertThrows(NullPointerException.class, () ->
                formatter.formatHeaderElement(null, element, false));
        Assert.assertThrows(NullPointerException.class, () ->
                formatter.formatHeaderElement(buf, null, false));
        Assert.assertThrows(NullPointerException.class, () ->
                formatter.formatElements(null, elements, false));
        Assert.assertThrows(NullPointerException.class, () ->
                formatter.formatElements(buf, null, false));
    }

}
