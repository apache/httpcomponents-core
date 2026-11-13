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
 * Tests for header value parsing.
 *
 * @version $Id$
 */
class TestBasicHeaderValueParser {

    private BasicHeaderValueParser parser;

    @BeforeEach
    void setup() {
        this.parser = BasicHeaderValueParser.INSTANCE;
    }

    @Test
    void testParseHeaderElements() {
        final String headerValue = "name1 = value1; name2; name3=\"value3\" , name4=value4; " +
            "name5=value5, name6= ; name7 = value7; name8 = \" value8\"";
        final CharArrayBuffer buf = new CharArrayBuffer(64);
        buf.append(headerValue);
        final ParserCursor cursor = new ParserCursor(0, buf.length());
        final HeaderElement[] elements = this.parser.parseElements(buf, cursor);
        // there are 3 elements
        Assertions.assertThat(elements.length).isEqualTo(3);
        // 1st element
        Assertions.assertThat(elements[0].getName()).isEqualTo("name1");
        Assertions.assertThat(elements[0].getValue()).isEqualTo("value1");
        // 1st element has 2 getParameters()
        Assertions.assertThat(elements[0].getParameters().length).isEqualTo(2);
        Assertions.assertThat(elements[0].getParameters()[0].getName()).isEqualTo("name2");
        Assertions.assertThat(elements[0].getParameters()[0].getValue()).isNull();
        Assertions.assertThat(elements[0].getParameters()[1].getName()).isEqualTo("name3");
        Assertions.assertThat(elements[0].getParameters()[1].getValue()).isEqualTo("value3");
        // 2nd element
        Assertions.assertThat(elements[1].getName()).isEqualTo("name4");
        Assertions.assertThat(elements[1].getValue()).isEqualTo("value4");
        // 2nd element has 1 parameter
        Assertions.assertThat(elements[1].getParameters().length).isEqualTo(1);
        Assertions.assertThat(elements[1].getParameters()[0].getName()).isEqualTo("name5");
        Assertions.assertThat(elements[1].getParameters()[0].getValue()).isEqualTo("value5");
        // 3rd element
        Assertions.assertThat(elements[2].getName()).isEqualTo("name6");
        Assertions.assertThat(elements[2].getValue()).isEqualTo("");
        // 3rd element has 2 getParameters()
        Assertions.assertThat(elements[2].getParameters().length).isEqualTo(2);
        Assertions.assertThat(elements[2].getParameters()[0].getName()).isEqualTo("name7");
        Assertions.assertThat(elements[2].getParameters()[0].getValue()).isEqualTo("value7");
        Assertions.assertThat(elements[2].getParameters()[1].getName()).isEqualTo("name8");
        Assertions.assertThat(elements[2].getParameters()[1].getValue()).isEqualTo(" value8");
    }

    @Test
    void testParseHEEscaped() {
        final String headerValue =
          "test1 =  \"\\\"stuff\\\"\", test2= \"\\\\\", test3 = \"stuff, stuff\"";
        final CharArrayBuffer buf = new CharArrayBuffer(64);
        buf.append(headerValue);
        final ParserCursor cursor = new ParserCursor(0, buf.length());
        final HeaderElement[] elements = this.parser.parseElements(buf, cursor);
        Assertions.assertThat(elements.length).isEqualTo(3);
        Assertions.assertThat(elements[0].getName()).isEqualTo("test1");
        Assertions.assertThat(elements[0].getValue()).isEqualTo("\"stuff\"");
        Assertions.assertThat(elements[1].getName()).isEqualTo("test2");
        Assertions.assertThat(elements[1].getValue()).isEqualTo("\\");
        Assertions.assertThat(elements[2].getName()).isEqualTo("test3");
        Assertions.assertThat(elements[2].getValue()).isEqualTo("stuff, stuff");
    }

    @Test
    void testHEFringeCase1() {
        final String headerValue = "name1 = value1,";
        final CharArrayBuffer buf = new CharArrayBuffer(64);
        buf.append(headerValue);
        final ParserCursor cursor = new ParserCursor(0, buf.length());
        final HeaderElement[] elements = this.parser.parseElements(buf, cursor);
        Assertions.assertThat(elements.length).withFailMessage("Number of elements").isEqualTo(1);
    }

    @Test
    void testHEFringeCase2() {
        final String headerValue = "name1 = value1, ";
        final CharArrayBuffer buf = new CharArrayBuffer(64);
        buf.append(headerValue);
        final ParserCursor cursor = new ParserCursor(0, buf.length());
        final HeaderElement[] elements = this.parser.parseElements(buf, cursor);
        Assertions.assertThat(elements.length).withFailMessage("Number of elements").isEqualTo(1);
    }

    @Test
    void testHEFringeCase3() {
        final String headerValue = ",, ,, ,";
        final CharArrayBuffer buf = new CharArrayBuffer(64);
        buf.append(headerValue);
        final ParserCursor cursor = new ParserCursor(0, buf.length());
        final HeaderElement[] elements = this.parser.parseElements(buf, cursor);
        Assertions.assertThat(elements.length).withFailMessage("Number of elements").isEqualTo(0);
    }

    @Test
    void testNVParse() {

        String s = "test";
        CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append(s);
        ParserCursor cursor = new ParserCursor(0, s.length());

        NameValuePair param = this.parser.parseNameValuePair(buffer, cursor);
        Assertions.assertThat(param.getName()).isEqualTo("test");
        Assertions.assertThat(param.getValue()).isNull();
        Assertions.assertThat(cursor.getPos()).isEqualTo(s.length());
        Assertions.assertThat(cursor.atEnd()).isTrue();

        s = "test;";
        buffer = new CharArrayBuffer(16);
        buffer.append(s);
        cursor = new ParserCursor(0, s.length());

        param = this.parser.parseNameValuePair(buffer, cursor);
        Assertions.assertThat(param.getName()).isEqualTo("test");
        Assertions.assertThat(param.getValue()).isNull();
        Assertions.assertThat(cursor.getPos()).isEqualTo(s.length() - 1);
        Assertions.assertThat(cursor.atEnd()).isFalse();

        s = "test  ,12";
        buffer = new CharArrayBuffer(16);
        buffer.append(s);
        cursor = new ParserCursor(0, s.length());

        param = this.parser.parseNameValuePair(buffer, cursor);
        Assertions.assertThat(param.getName()).isEqualTo("test");
        Assertions.assertThat(param.getValue()).isNull();
        Assertions.assertThat(cursor.getPos()).isEqualTo(s.length() - 3);
        Assertions.assertThat(cursor.atEnd()).isFalse();

        s = "test=stuff";
        buffer = new CharArrayBuffer(16);
        buffer.append(s);
        cursor = new ParserCursor(0, s.length());

        param = this.parser.parseNameValuePair(buffer, cursor);
        Assertions.assertThat(param.getName()).isEqualTo("test");
        Assertions.assertThat(param.getValue()).isEqualTo("stuff");
        Assertions.assertThat(cursor.getPos()).isEqualTo(s.length());
        Assertions.assertThat(cursor.atEnd()).isTrue();

        s = "   test  =   stuff ";
        buffer = new CharArrayBuffer(16);
        buffer.append(s);
        cursor = new ParserCursor(0, s.length());

        param = this.parser.parseNameValuePair(buffer, cursor);
        Assertions.assertThat(param.getName()).isEqualTo("test");
        Assertions.assertThat(param.getValue()).isEqualTo("stuff");
        Assertions.assertThat(cursor.getPos()).isEqualTo(s.length());
        Assertions.assertThat(cursor.atEnd()).isTrue();

        s = "   test  =   stuff ;1234";
        buffer = new CharArrayBuffer(16);
        buffer.append(s);
        cursor = new ParserCursor(0, s.length());

        param = this.parser.parseNameValuePair(buffer, cursor);
        Assertions.assertThat(param.getName()).isEqualTo("test");
        Assertions.assertThat(param.getValue()).isEqualTo("stuff");
        Assertions.assertThat(cursor.getPos()).isEqualTo(s.length() - 5);
        Assertions.assertThat(cursor.atEnd()).isFalse();

        s = "test  = \"stuff\"";
        buffer = new CharArrayBuffer(16);
        buffer.append(s);
        cursor = new ParserCursor(0, s.length());

        param = this.parser.parseNameValuePair(buffer, cursor);
        Assertions.assertThat(param.getName()).isEqualTo("test");
        Assertions.assertThat(param.getValue()).isEqualTo("stuff");

        s = "test  = \"  stuff\\\"\"";
        buffer = new CharArrayBuffer(16);
        buffer.append(s);
        cursor = new ParserCursor(0, s.length());

        param = this.parser.parseNameValuePair(buffer, cursor);
        Assertions.assertThat(param.getName()).isEqualTo("test");
        Assertions.assertThat(param.getValue()).isEqualTo("  stuff\"");

        s = "  test";
        buffer = new CharArrayBuffer(16);
        buffer.append(s);
        cursor = new ParserCursor(0, s.length());

        param = this.parser.parseNameValuePair(buffer, cursor);
        Assertions.assertThat(param.getName()).isEqualTo("test");
        Assertions.assertThat(param.getValue()).isNull();

        s = "  ";
        buffer = new CharArrayBuffer(16);
        buffer.append(s);
        cursor = new ParserCursor(0, s.length());

        param = this.parser.parseNameValuePair(buffer, cursor);
        Assertions.assertThat(param.getName()).isEqualTo("");
        Assertions.assertThat(param.getValue()).isNull();

        s = " = stuff ";
        buffer = new CharArrayBuffer(16);
        buffer.append(s);
        cursor = new ParserCursor(0, s.length());

        param = this.parser.parseNameValuePair(buffer, cursor);
        Assertions.assertThat(param.getName()).isEqualTo("");
        Assertions.assertThat(param.getValue()).isEqualTo("stuff");
    }

    @Test
    void testNVParseAll() {
        String s =
            "test; test1 =  stuff   ; test2 =  \"stuff; stuff\"; test3=\"stuff";
        CharArrayBuffer buffer = new CharArrayBuffer(16);
        buffer.append(s);
        ParserCursor cursor = new ParserCursor(0, s.length());

        NameValuePair[] params = this.parser.parseParameters(buffer, cursor);
        Assertions.assertThat(params[0].getName()).isEqualTo("test");
        Assertions.assertThat(params[0].getValue()).isNull();
        Assertions.assertThat(params[1].getName()).isEqualTo("test1");
        Assertions.assertThat(params[1].getValue()).isEqualTo("stuff");
        Assertions.assertThat(params[2].getName()).isEqualTo("test2");
        Assertions.assertThat(params[2].getValue()).isEqualTo("stuff; stuff");
        Assertions.assertThat(params[3].getName()).isEqualTo("test3");
        Assertions.assertThat(params[3].getValue()).isEqualTo("stuff");
        Assertions.assertThat(cursor.getPos()).isEqualTo(s.length());
        Assertions.assertThat(cursor.atEnd()).isTrue();

        s =
            "test; test1 =  stuff   ; test2 =  \"stuff; stuff\"; test3=\"stuff\",123";
        buffer = new CharArrayBuffer(16);
        buffer.append(s);
        cursor = new ParserCursor(0, s.length());

        params = this.parser.parseParameters(buffer, cursor);
        Assertions.assertThat(params[0].getName()).isEqualTo("test");
        Assertions.assertThat(params[0].getValue()).isNull();
        Assertions.assertThat(params[1].getName()).isEqualTo("test1");
        Assertions.assertThat(params[1].getValue()).isEqualTo("stuff");
        Assertions.assertThat(params[2].getName()).isEqualTo("test2");
        Assertions.assertThat(params[2].getValue()).isEqualTo("stuff; stuff");
        Assertions.assertThat(params[3].getName()).isEqualTo("test3");
        Assertions.assertThat(params[3].getValue()).isEqualTo("stuff");
        Assertions.assertThat(cursor.getPos()).isEqualTo(s.length() - 4);
        Assertions.assertThat(cursor.atEnd()).isFalse();

        s = "  ";
        buffer = new CharArrayBuffer(16);
        buffer.append(s);
        cursor = new ParserCursor(0, s.length());
        params = this.parser.parseParameters(buffer, cursor);
        Assertions.assertThat(params.length).isEqualTo(0);
    }

    @Test
    void testNVParseEscaped() {
        final String headerValue =
          "test1 =  \"\\\"stuff\\\"\"; test2= \"\\\\\"; test3 = \"stuff; stuff\"";
        final CharArrayBuffer buf = new CharArrayBuffer(64);
        buf.append(headerValue);
        final ParserCursor cursor = new ParserCursor(0, buf.length());
        final NameValuePair[] params = this.parser.parseParameters(buf, cursor);
        Assertions.assertThat(params.length).isEqualTo(3);
        Assertions.assertThat(params[0].getName()).isEqualTo("test1");
        Assertions.assertThat(params[0].getValue()).isEqualTo("\"stuff\"");
        Assertions.assertThat(params[1].getName()).isEqualTo("test2");
        Assertions.assertThat(params[1].getValue()).isEqualTo("\\");
        Assertions.assertThat(params[2].getName()).isEqualTo("test3");
        Assertions.assertThat(params[2].getValue()).isEqualTo("stuff; stuff");
    }

}
