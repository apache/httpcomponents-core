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

package org.apache.hc.core5.http2.impl;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TestDefaultH2ResponseConverter {

    @Test
    void testConvertFromFieldsBasic() throws Exception {

        final List<Header> headers = Arrays.asList(
                new BasicHeader(":status", "200"),
                new BasicHeader("location", "http://www.example.com/"),
                new BasicHeader("custom123", "value"));

        final DefaultH2ResponseConverter converter = new DefaultH2ResponseConverter();
        final HttpResponse response = converter.convert(headers);
        Assertions.assertThat(response).isNotNull();
        Assertions.assertThat(response.getCode()).isEqualTo(200);
        final Header[] allHeaders = response.getHeaders();
        Assertions.assertThat(allHeaders.length).isEqualTo(2);
        Assertions.assertThat(allHeaders[0].getName()).isEqualTo("location");
        Assertions.assertThat(allHeaders[0].getValue()).isEqualTo("http://www.example.com/");
        Assertions.assertThat(allHeaders[1].getName()).isEqualTo("custom123");
        Assertions.assertThat(allHeaders[1].getValue()).isEqualTo("value");
    }

    @Test
    void testConvertFromFieldsUpperCaseHeaderName() {
        final List<Header> headers = Arrays.asList(
                new BasicHeader(":Status", "200"),
                new BasicHeader("location", "http://www.example.com/"),
                new BasicHeader("custom123", "value"));

        final DefaultH2ResponseConverter converter = new DefaultH2ResponseConverter();
        Assertions.assertThatThrownBy(() ->
                        converter.convert(headers))
                .withFailMessage("Header name ':Status' is invalid (header name contains uppercase characters)").isInstanceOf(HttpException.class);
    }

    @Test
    void testConvertFromFieldsInvalidStatusCode() {
        final List<Header> headers = Arrays.asList(
                new BasicHeader(":status", "boom"),
                new BasicHeader("location", "http://www.example.com/"),
                new BasicHeader("custom123", "value"));

        final DefaultH2ResponseConverter converter = new DefaultH2ResponseConverter();
        Assertions.assertThatThrownBy(() ->
                        converter.convert(headers))
                .isInstanceOf(HttpException.class);
    }

    @Test
    void testConvertFromFieldsMissingStatus() {
        final List<Header> headers = Arrays.asList(
                new BasicHeader("location", "http://www.example.com/"),
                new BasicHeader("custom", "value"));

        final DefaultH2ResponseConverter converter = new DefaultH2ResponseConverter();
        Assertions.assertThatThrownBy(() ->
                        converter.convert(headers))
                .withFailMessage("Mandatory response header ':status' not found").isInstanceOf(HttpException.class);
    }

    @Test
    void testConvertFromFieldsUnknownPseudoHeader() {
        final List<Header> headers = Arrays.asList(
                new BasicHeader(":status", "200"),
                new BasicHeader(":custom", "200"),
                new BasicHeader("location", "http://www.example.com/"),
                new BasicHeader("custom1", "value"));

        final DefaultH2ResponseConverter converter = new DefaultH2ResponseConverter();
        Assertions.assertThatThrownBy(() ->
                        converter.convert(headers))
                .withFailMessage("Unsupported response header ':custom'").isInstanceOf(HttpException.class);
    }

    @Test
    void testConvertFromFieldsMultipleStatus() {
        final List<Header> headers = Arrays.asList(
                new BasicHeader(":status", "200"),
                new BasicHeader(":status", "200"),
                new BasicHeader("location", "http://www.example.com/"),
                new BasicHeader("custom1", "value"));

        final DefaultH2ResponseConverter converter = new DefaultH2ResponseConverter();
        Assertions.assertThatThrownBy(() ->
                        converter.convert(headers))
                .withFailMessage("Multiple ':status' response headers are illegal").isInstanceOf(HttpException.class);
    }

    @Test
    void testConvertFromMessageBasic() throws Exception {

        final HttpResponse response = new BasicHttpResponse(200);
        response.addHeader("custom123", "Value");

        final DefaultH2ResponseConverter converter = new DefaultH2ResponseConverter();
        final List<Header> headers = converter.convert(response);

        Assertions.assertThat(headers).isNotNull();
        Assertions.assertThat(headers.size()).isEqualTo(2);
        final Header header1 = headers.get(0);
        Assertions.assertThat(header1.getName()).isEqualTo(":status");
        Assertions.assertThat(header1.getValue()).isEqualTo("200");
        final Header header2 = headers.get(1);
        Assertions.assertThat(header2.getName()).isEqualTo("custom123");
        Assertions.assertThat(header2.getValue()).isEqualTo("Value");
    }

    @Test
    void testConvertFromMessageInvalidStatus() {
        final HttpResponse response = new BasicHttpResponse(99);
        response.addHeader("Custom123", "Value");

        final DefaultH2ResponseConverter converter = new DefaultH2ResponseConverter();
        Assertions.assertThatThrownBy(() ->
                        converter.convert(response))
                .withFailMessage("Response status 99 is invalid").isInstanceOf(HttpException.class);
    }

    @Test
    void testConvertFromMessageInvalidHeader() {
        final HttpResponse response = new BasicHttpResponse(200);
        response.addHeader(":custom", "stuff");

        final DefaultH2ResponseConverter converter = new DefaultH2ResponseConverter();
        Assertions.assertThatThrownBy(() ->
                        converter.convert(response))
                .withFailMessage("Header name ':custom' is invalid").isInstanceOf(HttpException.class);
    }

    @Test
    void testConvertFromFieldsMultipleCookies() throws Exception {
        final List<Header> headers = Arrays.asList(
                new BasicHeader(":status", "200"),
                new BasicHeader("cookie", "a=b"),
                new BasicHeader("location", "http://www.example.com/"),
                new BasicHeader("cookie", "c=d"),
                new BasicHeader("cookie", "e=f"));

        final DefaultH2ResponseConverter converter = new DefaultH2ResponseConverter();
        final HttpResponse response = converter.convert(headers);
        Assertions.assertThat(response).isNotNull();
        Assertions.assertThat(response.getCode()).isEqualTo(200);
        final Header[] allHeaders = response.getHeaders();
        Assertions.assertThat(allHeaders.length).isEqualTo(2);
        Assertions.assertThat(allHeaders[0].getName()).isEqualTo("location");
        Assertions.assertThat(allHeaders[0].getValue()).isEqualTo("http://www.example.com/");
        Assertions.assertThat(allHeaders[1].getName()).isEqualTo("cookie");
        Assertions.assertThat(allHeaders[1].getValue()).isEqualTo("a=b; c=d; e=f");
    }

    @Test
    void testConvertFromFieldsStatusCodeMustBeStrictThreeDigit() {
        final DefaultH2ResponseConverter converter = new DefaultH2ResponseConverter();

        // Demonstrate why Integer.parseInt(...) is insufficient (it accepts non-3-digit formats).
        final int parsedPlus = Integer.parseInt("+200");
        Assertions.assertThat(parsedPlus).isEqualTo(200);

        final int parsedLeadingZero = Integer.parseInt("0200");
        Assertions.assertThat(parsedLeadingZero).isEqualTo(200);

        // Converter must be strict: :status is exactly 3 digits, in range 100..599.
        Assertions.assertThatThrownBy(() ->
                        converter.convert(Collections.singletonList(new BasicHeader(":status", "+200"))))
                .isInstanceOf(HttpException.class);
        Assertions.assertThatThrownBy(() ->
                        converter.convert(Collections.singletonList(new BasicHeader(":status", "0200"))))
                .isInstanceOf(HttpException.class);

        Assertions.assertThatThrownBy(() ->
                        converter.convert(Collections.singletonList(new BasicHeader(":status", "099"))))
                .isInstanceOf(HttpException.class);
        Assertions.assertThatThrownBy(() ->
                        converter.convert(Collections.singletonList(new BasicHeader(":status", "600"))))
                .isInstanceOf(HttpException.class);
    }

}
