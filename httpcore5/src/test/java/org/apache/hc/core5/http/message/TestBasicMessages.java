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

import java.net.URI;

import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.net.URIBuilder;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link org.apache.hc.core5.http.HttpMessage}.
 *
 */
class TestBasicMessages {

    @Test
    void testDefaultResponseConstructors() {
        final HttpResponse response1 = new BasicHttpResponse(HttpStatus.SC_BAD_REQUEST, "Bad Request");
        Assertions.assertThat(response1.getCode()).isEqualTo(HttpStatus.SC_BAD_REQUEST);

        final HttpResponse response2 = new BasicHttpResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR, "whatever");
        Assertions.assertThat(response2.getCode()).isEqualTo(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        Assertions.assertThat(response2.getReasonPhrase()).isEqualTo("whatever");
    }

    @Test
    void testSetResponseStatus() {
        final HttpResponse response1 = new BasicHttpResponse(200, "OK");
        Assertions.assertThat(response1.getCode()).isEqualTo(200);

        final HttpResponse response2 = new BasicHttpResponse(HttpStatus.SC_BAD_REQUEST, "Bad Request");
        Assertions.assertThat(response2.getCode()).isEqualTo(HttpStatus.SC_BAD_REQUEST);

        final HttpResponse response3 = new BasicHttpResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR, "whatever");
        Assertions.assertThat(response3.getCode()).isEqualTo(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        Assertions.assertThat(response3.getReasonPhrase()).isEqualTo("whatever");

        final HttpResponse response4 = new BasicHttpResponse(HttpStatus.SC_OK, "OK");
        Assertions.assertThatThrownBy(() ->
                        response4.setCode(-23))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testDefaultRequestConstructors() {
        final HttpRequest request1 = new BasicHttpRequest("WHATEVER", "/");
        Assertions.assertThat(request1.getMethod()).isEqualTo("WHATEVER");
        Assertions.assertThat(request1.getPath()).isEqualTo("/");

        final HttpRequest request2 = new BasicHttpRequest(Method.GET, "/");
        Assertions.assertThat(request2.getMethod()).isEqualTo(Method.GET.name());
        Assertions.assertThat(request2.getPath()).isEqualTo("/");

        Assertions.assertThatThrownBy(() ->
                new BasicHttpRequest(Method.GET, (URI) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testResponseBasics() {
        final BasicHttpResponse response = new BasicHttpResponse(200, "OK");
        Assertions.assertThat(response.getCode()).isEqualTo(200);
        Assertions.assertThat(response.getReasonPhrase()).isEqualTo("OK");
    }

    @Test
    void testResponseStatusLineMutation() {
        final BasicHttpResponse response = new BasicHttpResponse(200, "OK");
        Assertions.assertThat(response.getCode()).isEqualTo(200);
        Assertions.assertThat(response.getReasonPhrase()).isEqualTo("OK");
        response.setReasonPhrase("Kind of OK");
        Assertions.assertThat(response.getCode()).isEqualTo(200);
        Assertions.assertThat(response.getReasonPhrase()).isEqualTo("Kind of OK");
        response.setCode(299);
        Assertions.assertThat(response.getCode()).isEqualTo(299);
        Assertions.assertThat(response.getReasonPhrase()).isNull();
    }

    @Test
    void testResponseInvalidStatusCode() {
        Assertions.assertThatThrownBy(() ->
                        new BasicHttpResponse(-200, "OK"))
                .isInstanceOf(IllegalArgumentException.class);
        final BasicHttpResponse response = new BasicHttpResponse(200, "OK");
        Assertions.assertThatThrownBy(() ->
                        response.setCode(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testRequestBasics() throws Exception {
        final HttpRequest request = new BasicHttpRequest(Method.GET, "/stuff");
        Assertions.assertThat(request.getMethod()).isEqualTo(Method.GET.name());
        Assertions.assertThat(request.getPath()).isEqualTo("/stuff");
        Assertions.assertThat(request.getAuthority()).isNull();
        Assertions.assertThat(request.getUri()).isEqualTo(new URI("/stuff"));
    }

    @Test
    void testRequestWithRelativeURI() throws Exception {
        final HttpRequest request = new BasicHttpRequest(Method.GET, new URI("/stuff"));
        Assertions.assertThat(request.getMethod()).isEqualTo(Method.GET.name());
        Assertions.assertThat(request.getPath()).isEqualTo("/stuff");
        Assertions.assertThat(request.getAuthority()).isNull();
        Assertions.assertThat(request.getUri()).isEqualTo(new URI("/stuff"));
    }

    @Test
    void testRequestWithAbsoluteURI() throws Exception {
        final HttpRequest request = new BasicHttpRequest(Method.GET, new URI("https://host:9443/stuff?param=value"));
        Assertions.assertThat(request.getMethod()).isEqualTo(Method.GET.name());
        Assertions.assertThat(request.getPath()).isEqualTo("/stuff?param=value");
        Assertions.assertThat(request.getAuthority()).isEqualTo(new URIAuthority("host", 9443));
        Assertions.assertThat(request.getScheme()).isEqualTo("https");
        Assertions.assertThat(request.getUri()).isEqualTo(new URI("https://host:9443/stuff?param=value"));
    }

    @Test
    void testRequestWithAbsoluteURIAsPath() throws Exception {
        final HttpRequest request = new BasicHttpRequest(Method.GET, "https://host:9443/stuff?param=value");
        Assertions.assertThat(request.getMethod()).isEqualTo(Method.GET.name());
        Assertions.assertThat(request.getPath()).isEqualTo("/stuff?param=value");
        Assertions.assertThat(request.getAuthority()).isEqualTo(new URIAuthority("host", 9443));
        Assertions.assertThat(request.getScheme()).isEqualTo("https");
        Assertions.assertThat(request.getUri()).isEqualTo(new URI("https://host:9443/stuff?param=value"));
    }

    @Test
    void testRequestWithNoPath() throws Exception {
        final HttpRequest request = new BasicHttpRequest(Method.GET, new URI("http://host"));
        Assertions.assertThat(request.getMethod()).isEqualTo(Method.GET.name());
        Assertions.assertThat(request.getPath()).isEqualTo("/");
        Assertions.assertThat(request.getAuthority()).isEqualTo(new URIAuthority("host"));
        Assertions.assertThat(request.getScheme()).isEqualTo("http");
        Assertions.assertThat(request.getUri()).isEqualTo(new URI("http://host/"));
    }

    @Test
    void testRequestWithUserInfo() throws Exception {
        final HttpRequest request = new BasicHttpRequest(Method.GET, new URI("https://user:pwd@host:9443/stuff?param=value"));
        Assertions.assertThat(request.getMethod()).isEqualTo(Method.GET.name());
        Assertions.assertThat(request.getPath()).isEqualTo("/stuff?param=value");
        Assertions.assertThat(request.getAuthority()).isEqualTo(new URIAuthority("user:pwd", "host", 9443));
        Assertions.assertThat(request.getScheme()).isEqualTo("https");
        Assertions.assertThat(request.getUri()).isEqualTo(new URI("https://host:9443/stuff?param=value"));
    }

    @Test
    void testRequestWithAuthority() throws Exception {
        final HttpRequest request = new BasicHttpRequest(Method.GET, new HttpHost("http", "somehost", -1), "/stuff");
        Assertions.assertThat(request.getMethod()).isEqualTo(Method.GET.name());
        Assertions.assertThat(request.getPath()).isEqualTo("/stuff");
        Assertions.assertThat(request.getAuthority()).isEqualTo(new URIAuthority("somehost"));
        Assertions.assertThat(request.getUri()).isEqualTo(new URI("http://somehost/stuff"));
    }

    @Test
    void testRequestWithAuthorityRelativePath() throws Exception {
        final HttpRequest request = new BasicHttpRequest(Method.GET, new HttpHost("http", "somehost", -1), "stuff");
        Assertions.assertThat(request.getMethod()).isEqualTo(Method.GET.name());
        Assertions.assertThat(request.getPath()).isEqualTo("stuff");
        Assertions.assertThat(request.getAuthority()).isEqualTo(new URIAuthority("somehost"));
        Assertions.assertThat(request.getUri()).isEqualTo(new URI("http://somehost/stuff"));
    }

    @Test
    void testRequestHostWithReservedChars() throws Exception {
        final HttpRequest request = new BasicHttpRequest(Method.GET, URI.create("http://someuser%21@%21example%21.com/stuff"));
        Assertions.assertThat(request.getMethod()).isEqualTo(Method.GET.name());
        Assertions.assertThat(request.getPath()).isEqualTo("/stuff");
        Assertions.assertThat(request.getAuthority()).isEqualTo(new URIAuthority("someuser%21", "%21example%21.com", -1));
        Assertions.assertThat(request.getUri()).isEqualTo(new URI("http://%21example%21.com/stuff"));
    }

    @Test
    void testRequestAbsoluteRequestUri() {
        final BasicHttpRequest request = new BasicHttpRequest(Method.GET, new HttpHost("http", "somehost", -1), "stuff");
        Assertions.assertThat(request.getRequestUri()).isEqualTo("stuff");
        request.setAbsoluteRequestUri(true);
        Assertions.assertThat(request.getRequestUri()).isEqualTo("http://somehost/stuff");
    }

    @Test
    void testModifyingExistingRequest() throws Exception {
        final URI uri = URI.create("https://example.org");
        final HttpRequest request = new BasicHttpRequest(Method.GET, uri);

        final URI newUri = new URIBuilder(request.getUri())
                .addParameter("name", "value")
                .build();

        request.setUri(newUri);
        Assertions.assertThat(request.getUri()).isEqualTo(newUri);
    }

}

