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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestDefaultH2ResponseConverter {

    @Test
    public void testConvertFromFieldsBasic() throws Exception {

        final List<Header> headers = Arrays.asList(
                new BasicHeader(":status", "200"),
                new BasicHeader("location", "http://www.example.com/"),
                new BasicHeader("custom123", "value"));

        final DefaultH2ResponseConverter converter = new DefaultH2ResponseConverter();
        final HttpResponse response = converter.convert(headers);
        Assertions.assertNotNull(response );
        Assertions.assertEquals(200, response .getCode());
        final Header[] allHeaders = response.getHeaders();
        Assertions.assertEquals(2, allHeaders.length);
        Assertions.assertEquals("location", allHeaders[0].getName());
        Assertions.assertEquals("http://www.example.com/", allHeaders[0].getValue());
        Assertions.assertEquals("custom123", allHeaders[1].getName());
        Assertions.assertEquals("value", allHeaders[1].getValue());
    }

    @Test
    public void testConvertFromFieldsUpperCaseHeaderName() throws Exception {
        final List<Header> headers = Arrays.asList(
                new BasicHeader(":Status", "200"),
                new BasicHeader("location", "http://www.example.com/"),
                new BasicHeader("custom123", "value"));

        final DefaultH2ResponseConverter converter = new DefaultH2ResponseConverter();
        Assertions.assertThrows(HttpException.class, () -> converter.convert(headers),
                "Header name ':Status' is invalid (header name contains uppercase characters)");
    }

    @Test
    public void testConvertFromFieldsInvalidStatusCode() throws Exception {
        final List<Header> headers = Arrays.asList(
                new BasicHeader(":status", "boom"),
                new BasicHeader("location", "http://www.example.com/"),
                new BasicHeader("custom123", "value"));

        final DefaultH2ResponseConverter converter = new DefaultH2ResponseConverter();
        Assertions.assertThrows(HttpException.class, () -> converter.convert(headers));
    }

    @Test
    public void testConvertFromFieldsConnectionHeader() throws Exception {
        final List<Header> headers = Arrays.asList(
                new BasicHeader(":status", "200"),
                new BasicHeader("location", "http://www.example.com/"),
                new BasicHeader("connection", "keep-alive"));

        final DefaultH2ResponseConverter converter = new DefaultH2ResponseConverter();
        Assertions.assertThrows(HttpException.class, () -> converter.convert(headers),
                "Header 'connection: keep-alive' is illegal for HTTP/2 messages");
    }

    @Test
    public void testConvertFromFieldsKeepAliveHeader() throws Exception {
        final List<Header> headers = Arrays.asList(
            new BasicHeader(":status", "200"),
            new BasicHeader("location", "http://www.example.com/"),
            new BasicHeader("keep-alive", "timeout=5, max=1000"));

        final DefaultH2ResponseConverter converter = new DefaultH2ResponseConverter();
        Assertions.assertThrows(HttpException.class, () -> converter.convert(headers),
                "Header 'keep-alive: timeout=5, max=1000' is illegal for HTTP/2 messages");
    }

    @Test
    public void testConvertFromFieldsTransferEncodingHeader() throws Exception {
        final List<Header> headers = Arrays.asList(
            new BasicHeader(":status", "200"),
            new BasicHeader("location", "http://www.example.com/"),
            new BasicHeader("transfer-encoding", "gzip"));

        final DefaultH2ResponseConverter converter = new DefaultH2ResponseConverter();
        Assertions.assertThrows(HttpException.class, () -> converter.convert(headers),
                "Header 'transfer-encoding: gzip' is illegal for HTTP/2 messages");
    }

    @Test
    public void testConvertFromFieldsUpgradeHeader() throws Exception {
        final List<Header> headers = Arrays.asList(
            new BasicHeader(":status", "200"),
            new BasicHeader("location", "http://www.example.com/"),
            new BasicHeader("upgrade", "example/1, foo/2"));

        final DefaultH2ResponseConverter converter = new DefaultH2ResponseConverter();
        Assertions.assertThrows(HttpException.class, () -> converter.convert(headers),
                "Header 'upgrade: example/1, foo/2' is illegal for HTTP/2 messages");
    }

    @Test
    public void testConvertFromFieldsMissingStatus() throws Exception {
        final List<Header> headers = Arrays.asList(
                new BasicHeader("location", "http://www.example.com/"),
                new BasicHeader("custom", "value"));

        final DefaultH2ResponseConverter converter = new DefaultH2ResponseConverter();
        Assertions.assertThrows(HttpException.class, () -> converter.convert(headers),
                "Mandatory response header ':status' not found");
    }

    @Test
    public void testConvertFromFieldsUnknownPseudoHeader() throws Exception {
        final List<Header> headers = Arrays.asList(
                new BasicHeader(":status", "200"),
                new BasicHeader(":custom", "200"),
                new BasicHeader("location", "http://www.example.com/"),
                new BasicHeader("custom1", "value"));

        final DefaultH2ResponseConverter converter = new DefaultH2ResponseConverter();
        Assertions.assertThrows(HttpException.class, () -> converter.convert(headers),
                "Unsupported response header ':custom'");
    }

    @Test
    public void testConvertFromFieldsMultipleStatus() throws Exception {
        final List<Header> headers = Arrays.asList(
                new BasicHeader(":status", "200"),
                new BasicHeader(":status", "200"),
                new BasicHeader("location", "http://www.example.com/"),
                new BasicHeader("custom1", "value"));

        final DefaultH2ResponseConverter converter = new DefaultH2ResponseConverter();
        Assertions.assertThrows(HttpException.class, () -> converter.convert(headers),
                "Multiple ':status' response headers are illegal");
    }

    @Test
    public void testConvertFromMessageBasic() throws Exception {

        final HttpResponse response = new BasicHttpResponse(200);
        response.addHeader("custom123", "Value");

        final DefaultH2ResponseConverter converter = new DefaultH2ResponseConverter();
        final List<Header> headers = converter.convert(response);

        Assertions.assertNotNull(headers);
        Assertions.assertEquals(2, headers.size());
        final Header header1 = headers.get(0);
        Assertions.assertEquals(":status", header1.getName());
        Assertions.assertEquals("200", header1.getValue());
        final Header header2 = headers.get(1);
        Assertions.assertEquals("custom123", header2.getName());
        Assertions.assertEquals("Value", header2.getValue());
    }

    @Test
    public void testConvertFromMessageInvalidStatus() throws Exception {
        final HttpResponse response = new BasicHttpResponse(99);
        response.addHeader("Custom123", "Value");

        final DefaultH2ResponseConverter converter = new DefaultH2ResponseConverter();
        Assertions.assertThrows(HttpException.class, () -> converter.convert(response),
                "Response status 99 is invalid");
    }

    @Test
    public void testConvertFromMessageConnectionHeader() throws Exception {
        final HttpResponse response = new BasicHttpResponse(200);
        response.addHeader("Connection", "Keep-Alive");

        final DefaultH2ResponseConverter converter = new DefaultH2ResponseConverter();
        Assertions.assertThrows(HttpException.class, () -> converter.convert(response),
                "Header 'Connection: Keep-Alive' is illegal for HTTP/2 messages");
    }

    @Test
    public void testConvertFromMessageInvalidHeader() throws Exception {
        final HttpResponse response = new BasicHttpResponse(200);
        response.addHeader(":custom", "stuff");

        final DefaultH2ResponseConverter converter = new DefaultH2ResponseConverter();
        Assertions.assertThrows(HttpException.class, () -> converter.convert(response),
                "Header name ':custom' is invalid");
    }

}

