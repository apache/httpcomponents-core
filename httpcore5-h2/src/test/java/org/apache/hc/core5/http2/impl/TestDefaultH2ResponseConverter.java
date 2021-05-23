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
import java.util.List;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.junit.Assert;
import org.junit.Test;

public class TestDefaultH2ResponseConverter {

    @Test
    public void testConvertFromFieldsBasic() throws Exception {

        final List<Header> headers = Arrays.asList(
                new BasicHeader(":status", "200"),
                new BasicHeader("location", "http://www.example.com/"),
                new BasicHeader("custom123", "value"));

        final DefaultH2ResponseConverter converter = new DefaultH2ResponseConverter();
        final HttpResponse response = converter.convert(headers);
        Assert.assertNotNull(response );
        Assert.assertEquals(200, response .getCode());
        final Header[] allHeaders = response.getHeaders();
        Assert.assertEquals(2, allHeaders.length);
        Assert.assertEquals("location", allHeaders[0].getName());
        Assert.assertEquals("http://www.example.com/", allHeaders[0].getValue());
        Assert.assertEquals("custom123", allHeaders[1].getName());
        Assert.assertEquals("value", allHeaders[1].getValue());
    }

    @Test
    public void testConvertFromFieldsUpperCaseHeaderName() throws Exception {
        final List<Header> headers = Arrays.asList(
                new BasicHeader(":Status", "200"),
                new BasicHeader("location", "http://www.example.com/"),
                new BasicHeader("custom123", "value"));

        final DefaultH2ResponseConverter converter = new DefaultH2ResponseConverter();
        Assert.assertThrows("Header name ':Status' is invalid (header name contains uppercase characters)",
                HttpException.class, () -> converter.convert(headers));
    }

    @Test
    public void testConvertFromFieldsInvalidStatusCode() throws Exception {
        final List<Header> headers = Arrays.asList(
                new BasicHeader(":status", "boom"),
                new BasicHeader("location", "http://www.example.com/"),
                new BasicHeader("custom123", "value"));

        final DefaultH2ResponseConverter converter = new DefaultH2ResponseConverter();
        Assert.assertThrows(HttpException.class, () -> converter.convert(headers));
    }

    @Test
    public void testConvertFromFieldsConnectionHeader() throws Exception {
        final List<Header> headers = Arrays.asList(
                new BasicHeader(":status", "200"),
                new BasicHeader("location", "http://www.example.com/"),
                new BasicHeader("connection", "keep-alive"));

        final DefaultH2ResponseConverter converter = new DefaultH2ResponseConverter();
        Assert.assertThrows("Header 'connection: keep-alive' is illegal for HTTP/2 messages",
                HttpException.class, () -> converter.convert(headers));
    }

    @Test
    public void testConvertFromFieldsMissingStatus() throws Exception {
        final List<Header> headers = Arrays.asList(
                new BasicHeader("location", "http://www.example.com/"),
                new BasicHeader("custom", "value"));

        final DefaultH2ResponseConverter converter = new DefaultH2ResponseConverter();
        Assert.assertThrows("Mandatory response header ':status' not found",
                HttpException.class, () -> converter.convert(headers));
    }

    @Test
    public void testConvertFromFieldsUnknownPseudoHeader() throws Exception {
        final List<Header> headers = Arrays.asList(
                new BasicHeader(":status", "200"),
                new BasicHeader(":custom", "200"),
                new BasicHeader("location", "http://www.example.com/"),
                new BasicHeader("custom1", "value"));

        final DefaultH2ResponseConverter converter = new DefaultH2ResponseConverter();
        Assert.assertThrows("Unsupported response header ':custom'",
                HttpException.class, () -> converter.convert(headers));
    }

    @Test
    public void testConvertFromFieldsMultipleStatus() throws Exception {
        final List<Header> headers = Arrays.asList(
                new BasicHeader(":status", "200"),
                new BasicHeader(":status", "200"),
                new BasicHeader("location", "http://www.example.com/"),
                new BasicHeader("custom1", "value"));

        final DefaultH2ResponseConverter converter = new DefaultH2ResponseConverter();
        Assert.assertThrows("Multiple ':status' response headers are illegal",
                HttpException.class, () -> converter.convert(headers));
    }

    @Test
    public void testConvertFromMessageBasic() throws Exception {

        final HttpResponse response = new BasicHttpResponse(200);
        response.addHeader("custom123", "Value");

        final DefaultH2ResponseConverter converter = new DefaultH2ResponseConverter();
        final List<Header> headers = converter.convert(response);

        Assert.assertNotNull(headers);
        Assert.assertEquals(2, headers.size());
        final Header header1 = headers.get(0);
        Assert.assertEquals(":status", header1.getName());
        Assert.assertEquals("200", header1.getValue());
        final Header header2 = headers.get(1);
        Assert.assertEquals("custom123", header2.getName());
        Assert.assertEquals("Value", header2.getValue());
    }

    @Test
    public void testConvertFromMessageInvalidStatus() throws Exception {
        final HttpResponse response = new BasicHttpResponse(99);
        response.addHeader("Custom123", "Value");

        final DefaultH2ResponseConverter converter = new DefaultH2ResponseConverter();
        Assert.assertThrows("Response status 99 is invalid",
                HttpException.class, () -> converter.convert(response));
    }

    @Test
    public void testConvertFromMessageConnectionHeader() throws Exception {
        final HttpResponse response = new BasicHttpResponse(200);
        response.addHeader("Connection", "Keep-Alive");

        final DefaultH2ResponseConverter converter = new DefaultH2ResponseConverter();
        Assert.assertThrows("Header 'Connection: Keep-Alive' is illegal for HTTP/2 messages",
                HttpException.class, () -> converter.convert(response));
    }

    @Test
    public void testConvertFromMessageInvalidHeader() throws Exception {
        final HttpResponse response = new BasicHttpResponse(200);
        response.addHeader(":custom", "stuff");

        final DefaultH2ResponseConverter converter = new DefaultH2ResponseConverter();
        Assert.assertThrows("Header name ':custom' is invalid",
                HttpException.class, () -> converter.convert(response));
    }

}

