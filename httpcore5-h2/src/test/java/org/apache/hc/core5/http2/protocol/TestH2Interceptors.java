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

package org.apache.hc.core5.http2.protocol;

import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.io.entity.HttpEntities;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TestH2Interceptors {

    /**
     * HTTP context.
     */
    private HttpContext context;


    @BeforeEach
    public void setUp() {
        context = new BasicHttpContext(null);
        context.setProtocolVersion(HttpVersion.HTTP_2);
    }

    @Test
    public void testH2RequestContentProtocolException() {
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.TRACE, "/");
        request.addHeader(new BasicHeader(HttpHeaders.TRANSFER_ENCODING, "chunked"));
        request.setEntity(new StringEntity("whatever", StandardCharsets.US_ASCII));
        final HttpRequestInterceptor interceptor = H2RequestContent.INSTANCE;
        Assertions.assertThrows(ProtocolException.class, () ->
                interceptor.process(request, request.getEntity(), context));

    }

    @Test
    public void testH2RequestContentNullEntity() throws Exception {

        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/");
        final HttpRequestInterceptor interceptor = H2RequestContent.INSTANCE;
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.CONTENT_LENGTH);
        Assertions.assertNull(header);
        Assertions.assertNull(request.getFirstHeader(HttpHeaders.TRANSFER_ENCODING));
    }


    @Test
    public void testH2RequestContentValid() throws Exception {
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");

        // Create a mock entity with ContentType, ContentEncoding, and TrailerNames
        request.setEntity(HttpEntities.create("whatever", StandardCharsets.US_ASCII,
                new BasicHeader("h1", "this"), new BasicHeader("h1", "that"), new BasicHeader("h2", "this and that")));

        final HttpRequestInterceptor interceptor = H2RequestContent.INSTANCE;
        interceptor.process(request, request.getEntity(), context);

        // Assertions to validate headers
        final Header header1 = request.getFirstHeader(HttpHeaders.CONTENT_TYPE);
        Assertions.assertNotNull(header1);
        final Header header2 = request.getFirstHeader(HttpHeaders.TRAILER);
        Assertions.assertNotNull(header2);
        Assertions.assertEquals("h1, h2", header2.getValue());
    }

    @Test
    public void testH2RequestContentOptionMethodNullContentTypeProtocolException() {
        final H2RequestContent interceptor = new H2RequestContent();
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.OPTIONS, "/");
        request.addHeader(new BasicHeader(HttpHeaders.CONTENT_LENGTH, "10"));
        request.addHeader(new BasicHeader(HttpHeaders.TRANSFER_ENCODING, "whatever"));
        request.setEntity(new StringEntity(""));
        Assertions.assertThrows(ProtocolException.class, () ->
                interceptor.process(request, request.getEntity(), context));
    }

    @Test
    public void testH2RequestContentOptionMethodInvalidContentTypeProtocolException() {
        final H2RequestContent interceptor = new H2RequestContent();
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.OPTIONS, "/");
        request.addHeader(new BasicHeader(HttpHeaders.CONTENT_LENGTH, "10"));
        request.addHeader(new BasicHeader(HttpHeaders.TRANSFER_ENCODING, "whatever"));
        request.setEntity(new StringEntity(""));
        request.addHeader(new BasicHeader(HttpHeaders.CONTENT_TYPE, "text/plain; charset=invalid_charset_name!"));
        Assertions.assertThrows(ProtocolException.class, () ->
                interceptor.process(request, request.getEntity(), context));
    }

    @Test
    public void testH2RequestContentValidOptionsMethod() throws Exception {
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.OPTIONS, "/");

        // Create a mock entity with ContentType, ContentEncoding, and TrailerNames
        request.setEntity(HttpEntities.create("whatever", StandardCharsets.US_ASCII,
                new BasicHeader("h1", "this"), new BasicHeader("h1", "that"), new BasicHeader("h2", "this and that")));

        final HttpRequestInterceptor interceptor = H2RequestContent.INSTANCE;
        interceptor.process(request, request.getEntity(), context);

        // Assertions to validate headers
        final Header header1 = request.getFirstHeader(HttpHeaders.CONTENT_TYPE);
        Assertions.assertNotNull(header1);
    }

}
