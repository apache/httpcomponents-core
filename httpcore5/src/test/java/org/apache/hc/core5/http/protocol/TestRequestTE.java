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

package org.apache.hc.core5.http.protocol;

import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TestRequestTE {

    @Test
    void testValidTEHeader() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        context.setProtocolVersion(HttpVersion.HTTP_1_1);

        // Set the TE header and Connection header
        request.setHeader(HttpHeaders.TE, "trailers");
        request.setHeader(HttpHeaders.CONNECTION, "TE");

        final HttpRequestInterceptor interceptor = new RequestTE();
        interceptor.process(request, request.getEntity(), context);

        Assertions.assertThat(request.getHeader(HttpHeaders.TE)).isNotNull();
        Assertions.assertThat(request.getHeader(HttpHeaders.TE).getValue()).isEqualTo("trailers");
    }


    @Test
    void testMultipleValidTEHeaders() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        context.setProtocolVersion(HttpVersion.HTTP_1_1);

        // Set both the TE header and the Connection header
        request.setHeader(HttpHeaders.TE, "trailers, deflate;q=0.5");
        request.setHeader(HttpHeaders.CONNECTION, "TE");

        final HttpRequestInterceptor interceptor = new RequestTE();
        interceptor.process(request, request.getEntity(), context);

        Assertions.assertThat(request.getHeader(HttpHeaders.TE)).isNotNull();
        Assertions.assertThat(request.getHeader(HttpHeaders.TE).getValue()).isEqualTo("trailers, deflate;q=0.5");
    }


    @Test
    void testTEHeaderNotPresent() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        context.setProtocolVersion(HttpVersion.HTTP_1_1);

        final HttpRequestInterceptor interceptor = new RequestTE();
        interceptor.process(request, request.getEntity(), context);

        // No TE header, no validation should occur
        Assertions.assertThat(request.getHeader(HttpHeaders.TE)).isNull();
    }

    @Test
    void testTEHeaderContainsChunked() {
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        context.setProtocolVersion(HttpVersion.HTTP_1_1);
        request.setHeader(HttpHeaders.TE, "chunked");

        final HttpRequestInterceptor interceptor = new RequestTE();
        Assertions.assertThatThrownBy(() ->
                interceptor.process(request, request.getEntity(), context)).isInstanceOf(ProtocolException.class);
    }

    @Test
    void testTEHeaderInvalidTransferCoding() {
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        context.setProtocolVersion(HttpVersion.HTTP_1_1);
        request.setHeader(HttpHeaders.TE, "invalid;q=abc");

        final HttpRequestInterceptor interceptor = new RequestTE();
        Assertions.assertThatThrownBy(() ->
                interceptor.process(request, request.getEntity(), context)).isInstanceOf(ProtocolException.class);
    }

    @Test
    void testTEHeaderAlreadySet() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        context.setProtocolVersion(HttpVersion.HTTP_1_1);

        final String teValue = "trailers";
        request.setHeader(HttpHeaders.TE, teValue);
        request.setHeader(HttpHeaders.CONNECTION, "TE"); // Add the Connection header as required

        final HttpRequestInterceptor interceptor = new RequestTE();
        interceptor.process(request, request.getEntity(), context);

        Assertions.assertThat(request.getHeader(HttpHeaders.TE).getName()).isEqualTo(HttpHeaders.TE);
        Assertions.assertThat(request.getHeader(HttpHeaders.TE)).isNotNull();
        Assertions.assertThat(request.getHeader(HttpHeaders.TE).getValue()).isEqualTo(teValue);
    }


    @Test
    void testTEHeaderWithConnectionHeaderValidation() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        context.setProtocolVersion(HttpVersion.HTTP_1_1);
        request.setHeader(HttpHeaders.TE, "trailers");
        request.setHeader(HttpHeaders.CONNECTION, "TE");

        final HttpRequestInterceptor interceptor = new RequestTE();
        interceptor.process(request, request.getEntity(), context);

        Assertions.assertThat(request.getHeader(HttpHeaders.TE).getName()).isEqualTo(HttpHeaders.TE);
        Assertions.assertThat(request.getHeader(HttpHeaders.TE)).isNotNull();
        Assertions.assertThat(request.getHeader(HttpHeaders.TE).getValue()).isEqualTo("trailers");
    }

    @Test
    void testTEHeaderWithoutConnectionHeaderThrowsException() {
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        context.setProtocolVersion(HttpVersion.HTTP_1_1);
        request.setHeader(HttpHeaders.TE, "trailers");

        final HttpRequestInterceptor interceptor = new RequestTE();
        Assertions.assertThatThrownBy(() ->
                interceptor.process(request, request.getEntity(), context)).isInstanceOf(ProtocolException.class);
    }

    @Test
    void testTEHeaderWithoutTEInConnectionHeaderThrowsException() {
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        context.setProtocolVersion(HttpVersion.HTTP_1_1);
        // Set TE header but Connection header does not include "TE"
        request.setHeader(HttpHeaders.TE, "trailers");
        request.setHeader(HttpHeaders.CONNECTION, "keep-alive"); // Missing "TE"

        final HttpRequestInterceptor interceptor = new RequestTE();
        Assertions.assertThatThrownBy(() ->
                interceptor.process(request, request.getEntity(), context)).isInstanceOf(ProtocolException.class);
    }

    @Test
    void testTEHeaderWithMultipleDirectivesInConnectionHeader() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        context.setProtocolVersion(HttpVersion.HTTP_1_1);

        // Set TE header and a Connection header with multiple directives
        request.setHeader(HttpHeaders.TE, "trailers");
        request.setHeader(HttpHeaders.CONNECTION, "keep-alive, close, TE");

        final HttpRequestInterceptor interceptor = new RequestTE();
        interceptor.process(request, request.getEntity(), context);

        Assertions.assertThat(request.getHeader(HttpHeaders.CONNECTION)).isNotNull();
        Assertions.assertThat(request.getHeader(HttpHeaders.CONNECTION).getValue().contains("TE")).isTrue();
    }


}

