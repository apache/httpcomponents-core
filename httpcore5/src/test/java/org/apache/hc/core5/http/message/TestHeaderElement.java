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
import org.junit.jupiter.api.Test;

/**
 * Simple tests for {@link HeaderElement}.
 */
class TestHeaderElement {

    @Test
    void testConstructor3() {
        final HeaderElement element = new BasicHeaderElement("name", "value",
                new BasicNameValuePair("param1", "value1"),
                new BasicNameValuePair("param2", "value2"));
        Assertions.assertThat(element.getName()).isEqualTo("name");
        Assertions.assertThat(element.getValue()).isEqualTo("value");
        Assertions.assertThat(element.getParameters().length).isEqualTo(2);
        Assertions.assertThat(element.getParameterByName("param1").getValue()).isEqualTo("value1");
        Assertions.assertThat(element.getParameterByName("param2").getValue()).isEqualTo("value2");
    }

    @Test
    void testConstructor2() {
        final HeaderElement element = new BasicHeaderElement("name", "value");
        Assertions.assertThat(element.getName()).isEqualTo("name");
        Assertions.assertThat(element.getValue()).isEqualTo("value");
        Assertions.assertThat(element.getParameters().length).isEqualTo(0);
    }


    @Test
    void testInvalidName() {
        Assertions.assertThatThrownBy(() ->
                        new BasicHeaderElement(null, null, (NameValuePair[]) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testParamByName() {
        final String s = "name = value; param1 = value1; param2 = value2";
        final CharArrayBuffer buf = new CharArrayBuffer(64);
        buf.append(s);
        final ParserCursor cursor = new ParserCursor(0, buf.length());
        final HeaderElement element = BasicHeaderValueParser.INSTANCE.parseHeaderElement(buf, cursor);
        Assertions.assertThat(element.getParameterByName("param1").getValue()).isEqualTo("value1");
        Assertions.assertThat(element.getParameterByName("param2").getValue()).isEqualTo("value2");
        Assertions.assertThat(element.getParameterByName("param3")).isNull();
        Assertions.assertThatThrownBy(() ->
                        element.getParameterByName(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testToString() {
        final BasicHeaderElement element = new BasicHeaderElement("name", "value",
                new BasicNameValuePair("param1", "value1"),
                new BasicNameValuePair("param2", "value2"));
        Assertions.assertThat(element.toString()).isEqualTo("name=value; param1=value1; param2=value2");
    }

}
