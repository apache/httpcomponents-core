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
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for header value formatting.
 *
 *
 */
class TestBasicHeaderValueFormatter {

    private BasicHeaderValueFormatter formatter;

    @BeforeEach
    void setup() {
        this.formatter = BasicHeaderValueFormatter.INSTANCE;
    }

    @Test
    void testNVPFormatting() {
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
        Assertions.assertThat(buf.toString()).isEqualTo("param=regular_stuff");
        buf.clear();
        this.formatter.formatNameValuePair(buf, param2, false);
        Assertions.assertThat(buf.toString()).isEqualTo("param=\"this\\\\that\"");
        buf.clear();
        this.formatter.formatNameValuePair(buf, param3, false);
        Assertions.assertThat(buf.toString()).isEqualTo("param=\"this,that\"");
        buf.clear();
        this.formatter.formatNameValuePair(buf, param4, false);
        Assertions.assertThat(buf.toString()).isEqualTo("param=\"quote marks (\\\") must be escaped\"");
        buf.clear();
        this.formatter.formatNameValuePair(buf, param5, false);
        Assertions.assertThat(buf.toString()).isEqualTo("param=\"back slash (\\\\) must be escaped too\"");
        buf.clear();
        this.formatter.formatNameValuePair(buf, param6, false);
        Assertions.assertThat(buf.toString()).isEqualTo("param=\"values with\tblanks must always be quoted\"");
        buf.clear();
        this.formatter.formatNameValuePair(buf, param7, false);
        Assertions.assertThat(buf.toString()).isEqualTo("param");

        buf.clear();
        this.formatter.formatNameValuePair(buf, param1, true);
        Assertions.assertThat(buf.toString()).isEqualTo("param=\"regular_stuff\"");
        buf.clear();
        this.formatter.formatNameValuePair(buf, param2, true);
        Assertions.assertThat(buf.toString()).isEqualTo("param=\"this\\\\that\"");
        buf.clear();
        this.formatter.formatNameValuePair(buf, param3, true);
        Assertions.assertThat(buf.toString()).isEqualTo("param=\"this,that\"");
        buf.clear();
        this.formatter.formatNameValuePair(buf, param4, true);
        Assertions.assertThat(buf.toString()).isEqualTo("param=\"quote marks (\\\") must be escaped\"");
        buf.clear();
        this.formatter.formatNameValuePair(buf, param5, true);
        Assertions.assertThat(buf.toString()).isEqualTo("param=\"back slash (\\\\) must be escaped too\"");
        buf.clear();
        this.formatter.formatNameValuePair(buf, param6, true);
        Assertions.assertThat(buf.toString()).isEqualTo("param=\"values with\tblanks must always be quoted\"");
        buf.clear();
        this.formatter.formatNameValuePair(buf, param7, true);
        Assertions.assertThat(buf.toString()).isEqualTo("param");
    }

    @Test
    void testParamsFormatting() {
        final NameValuePair param1 = new BasicNameValuePair("param", "regular_stuff");
        final NameValuePair param2 = new BasicNameValuePair("param", "this\\that");
        final NameValuePair param3 = new BasicNameValuePair("param", "this,that");
        final NameValuePair[] params = new NameValuePair[] {param1, param2, param3};

        final CharArrayBuffer buf = new CharArrayBuffer(64);

        buf.clear();
        this.formatter.formatParameters(buf, params, false);
        Assertions.assertThat(buf.toString()).isEqualTo("param=regular_stuff; param=\"this\\\\that\"; param=\"this,that\"");
        buf.clear();
        this.formatter.formatParameters(buf, params, true);
        Assertions.assertThat(buf.toString()).isEqualTo("param=\"regular_stuff\"; param=\"this\\\\that\"; param=\"this,that\"");
    }

    @Test
    void testHEFormatting() {
        final NameValuePair param1 = new BasicNameValuePair("param", "regular_stuff");
        final NameValuePair param2 = new BasicNameValuePair("param", "this\\that");
        final NameValuePair param3 = new BasicNameValuePair("param", "this,that");
        final NameValuePair param4 = new BasicNameValuePair("param", null);
        final NameValuePair[] params = new NameValuePair[] {param1, param2, param3, param4};
        final HeaderElement element = new BasicHeaderElement("name", "value", params);

        final CharArrayBuffer buf = new CharArrayBuffer(64);

        this.formatter.formatHeaderElement(buf, element, false);
        Assertions.assertThat(buf.toString()).isEqualTo("name=value; param=regular_stuff; param=\"this\\\\that\"; param=\"this,that\"; param");
    }

    @Test
    void testElementsFormatting() {
        final NameValuePair param1 = new BasicNameValuePair("param", "regular_stuff");
        final NameValuePair param2 = new BasicNameValuePair("param", "this\\that");
        final NameValuePair param3 = new BasicNameValuePair("param", "this,that");
        final NameValuePair param4 = new BasicNameValuePair("param", null);
        final HeaderElement element1 = new BasicHeaderElement("name1", "value1", param1);
        final HeaderElement element2 = new BasicHeaderElement("name2", "value2", param2);
        final HeaderElement element3 = new BasicHeaderElement("name3", "value3", param3);
        final HeaderElement element4 = new BasicHeaderElement("name4", "value4", param4);
        final HeaderElement element5 = new BasicHeaderElement("name5", null);
        final HeaderElement[] elements = new HeaderElement[] {element1, element2, element3, element4, element5};

        final CharArrayBuffer buf = new CharArrayBuffer(64);

        this.formatter.formatElements(buf, elements, false);
        Assertions.assertThat(buf.toString()).isEqualTo("name1=value1; param=regular_stuff, name2=value2; " +
             "param=\"this\\\\that\", name3=value3; param=\"this,that\", " +
                "name4=value4; param, name5");
    }


    @Test
    void testInvalidArguments() {
        final CharArrayBuffer buf = new CharArrayBuffer(64);
        final NameValuePair param = new BasicNameValuePair("param", "regular_stuff");
        final NameValuePair[] params = new NameValuePair[] {param};
        final HeaderElement element = new BasicHeaderElement("name1", "value1", (NameValuePair[]) null);
        final HeaderElement[] elements = new HeaderElement[] {element};

        Assertions.assertThatThrownBy(() ->
                formatter.formatNameValuePair(null, param, false)).isInstanceOf(NullPointerException.class);
        Assertions.assertThatThrownBy(() ->
                formatter.formatNameValuePair(buf, null, false)).isInstanceOf(NullPointerException.class);
        Assertions.assertThatThrownBy(() ->
                formatter.formatParameters(null, params, false)).isInstanceOf(NullPointerException.class);
        Assertions.assertThatThrownBy(() ->
                formatter.formatParameters(buf, null, false)).isInstanceOf(NullPointerException.class);
        Assertions.assertThatThrownBy(() ->
                formatter.formatHeaderElement(null, element, false)).isInstanceOf(NullPointerException.class);
        Assertions.assertThatThrownBy(() ->
                formatter.formatHeaderElement(buf, null, false)).isInstanceOf(NullPointerException.class);
        Assertions.assertThatThrownBy(() ->
                formatter.formatElements(null, elements, false)).isInstanceOf(NullPointerException.class);
        Assertions.assertThatThrownBy(() ->
                formatter.formatElements(buf, null, false)).isInstanceOf(NullPointerException.class);
    }

}
