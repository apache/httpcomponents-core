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
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.net.URIAuthority;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class HttpRequestWrapperTest {

    @Test
    void testRequestBasics() throws Exception {
        final HttpRequest request = new BasicHttpRequest(Method.GET, "/stuff");
        final HttpRequestWrapper httpRequestWrapper = new HttpRequestWrapper(request);

        Assertions.assertThat(httpRequestWrapper.getMethod()).isEqualTo(Method.GET.name());
        Assertions.assertThat(httpRequestWrapper.getPath()).isEqualTo("/stuff");
        Assertions.assertThat(httpRequestWrapper.getAuthority()).isNull();
        Assertions.assertThat(httpRequestWrapper.getUri()).isEqualTo(new URI("/stuff"));
        httpRequestWrapper.setPath("/another-stuff");
        Assertions.assertThat(httpRequestWrapper.getPath()).isEqualTo("/another-stuff");
    }

    @Test
    void testDefaultRequestConstructors() {
        final HttpRequest request1 = new BasicHttpRequest("WHATEVER", "/");
        final HttpRequestWrapper httpRequestWrapper = new HttpRequestWrapper(request1);
        Assertions.assertThat(httpRequestWrapper.getMethod()).isEqualTo("WHATEVER");
        Assertions.assertThat(httpRequestWrapper.getPath()).isEqualTo("/");

        final HttpRequest request2 = new BasicHttpRequest(Method.GET, "/");
        final HttpRequestWrapper httpRequestWrapper2 = new HttpRequestWrapper(request2);
        Assertions.assertThat(httpRequestWrapper2.getMethod()).isEqualTo(Method.GET.name());
        Assertions.assertThat(httpRequestWrapper2.getPath()).isEqualTo("/");

        Assertions.assertThatThrownBy(() ->
                new BasicHttpRequest(Method.GET, (URI) null)).isInstanceOf(NullPointerException.class);
    }


    @Test
    void testRequestWithRelativeURI() throws Exception {
        final HttpRequest request = new BasicHttpRequest(Method.GET, new URI("/stuff"));
        final HttpRequestWrapper httpRequestWrapper = new HttpRequestWrapper(request);
        Assertions.assertThat(httpRequestWrapper.getMethod()).isEqualTo(Method.GET.name());
        Assertions.assertThat(httpRequestWrapper.getPath()).isEqualTo("/stuff");
        Assertions.assertThat(httpRequestWrapper.getAuthority()).isNull();
        Assertions.assertThat(httpRequestWrapper.getUri()).isEqualTo(new URI("/stuff"));
    }

    @Test
    void testRequestWithAbsoluteURI() throws Exception {
        final HttpRequest request = new BasicHttpRequest(Method.GET, new URI("https://host:9443/stuff?param=value"));
        final HttpRequestWrapper httpRequestWrapper = new HttpRequestWrapper(request);
        Assertions.assertThat(httpRequestWrapper.getMethod()).isEqualTo(Method.GET.name());
        Assertions.assertThat(httpRequestWrapper.getPath()).isEqualTo("/stuff?param=value");
        Assertions.assertThat(httpRequestWrapper.getAuthority()).isEqualTo(new URIAuthority("host", 9443));
        Assertions.assertThat(httpRequestWrapper.getScheme()).isEqualTo("https");
        Assertions.assertThat(httpRequestWrapper.getUri()).isEqualTo(new URI("https://host:9443/stuff?param=value"));
        httpRequestWrapper.setScheme(URIScheme.HTTP.id);
        Assertions.assertThat(httpRequestWrapper.getScheme()).isEqualTo("http");
    }

    @Test
    void testRequestWithAbsoluteURIAsPath() throws Exception {
        final HttpRequest request = new BasicHttpRequest(Method.GET, "https://host:9443/stuff?param=value");
        final HttpRequestWrapper httpRequestWrapper = new HttpRequestWrapper(request);
        Assertions.assertThat(httpRequestWrapper.getMethod()).isEqualTo(Method.GET.name());
        Assertions.assertThat(httpRequestWrapper.getPath()).isEqualTo("/stuff?param=value");
        Assertions.assertThat(httpRequestWrapper.getAuthority()).isEqualTo(new URIAuthority("host", 9443));
        Assertions.assertThat(httpRequestWrapper.getScheme()).isEqualTo("https");
        Assertions.assertThat(httpRequestWrapper.getUri()).isEqualTo(new URI("https://host:9443/stuff?param=value"));
    }

    @Test
    void testRequestWithNoPath() throws Exception {
        final HttpRequest request = new BasicHttpRequest(Method.GET, new URI("http://host"));
        final HttpRequestWrapper httpRequestWrapper = new HttpRequestWrapper(request);
        Assertions.assertThat(httpRequestWrapper.getMethod()).isEqualTo(Method.GET.name());
        Assertions.assertThat(httpRequestWrapper.getPath()).isEqualTo("/");
        Assertions.assertThat(httpRequestWrapper.getAuthority()).isEqualTo(new URIAuthority("host"));
        Assertions.assertThat(httpRequestWrapper.getScheme()).isEqualTo("http");
        Assertions.assertThat(httpRequestWrapper.getUri()).isEqualTo(new URI("http://host/"));
    }

    @Test
    void testRequestWithUserInfo() throws Exception {
        final HttpRequest request = new BasicHttpRequest(Method.GET, new URI("https://user:pwd@host:9443/stuff?param=value"));
        final HttpRequestWrapper httpRequestWrapper = new HttpRequestWrapper(request);
        Assertions.assertThat(httpRequestWrapper.getMethod()).isEqualTo(Method.GET.name());
        Assertions.assertThat(httpRequestWrapper.getPath()).isEqualTo("/stuff?param=value");
        Assertions.assertThat(httpRequestWrapper.getAuthority()).isEqualTo(new URIAuthority("user:pwd", "host", 9443));
        Assertions.assertThat(httpRequestWrapper.getScheme()).isEqualTo("https");
        Assertions.assertThat(httpRequestWrapper.getUri()).isEqualTo(new URI("https://host:9443/stuff?param=value"));
    }

    @Test
    void testRequestWithAuthority() throws Exception {
        final HttpRequest request = new BasicHttpRequest(Method.GET, new HttpHost("http", "somehost", -1), "/stuff");
        final HttpRequestWrapper httpRequestWrapper = new HttpRequestWrapper(request);
        Assertions.assertThat(httpRequestWrapper.getMethod()).isEqualTo(Method.GET.name());
        Assertions.assertThat(httpRequestWrapper.getPath()).isEqualTo("/stuff");
        Assertions.assertThat(httpRequestWrapper.getAuthority()).isEqualTo(new URIAuthority("somehost"));
        Assertions.assertThat(httpRequestWrapper.getUri()).isEqualTo(new URI("http://somehost/stuff"));

        httpRequestWrapper.setAuthority(new URIAuthority("newHost"));
        Assertions.assertThat(httpRequestWrapper.getAuthority()).isEqualTo(new URIAuthority("newHost"));

    }

    @Test
    void testRequestWithAuthorityRelativePath() throws Exception {
        final HttpRequest request = new BasicHttpRequest(Method.GET, new HttpHost("http", "somehost", -1), "stuff");
        final HttpRequestWrapper httpRequestWrapper = new HttpRequestWrapper(request);
        Assertions.assertThat(httpRequestWrapper.getMethod()).isEqualTo(Method.GET.name());
        Assertions.assertThat(httpRequestWrapper.getPath()).isEqualTo("stuff");
        Assertions.assertThat(httpRequestWrapper.getRequestUri()).isEqualTo("stuff");
        Assertions.assertThat(httpRequestWrapper.getAuthority()).isEqualTo(new URIAuthority("somehost"));
        Assertions.assertThat(httpRequestWrapper.getUri()).isEqualTo(new URI("http://somehost/stuff"));
    }

    @Test
    void testRequestHostWithReservedChars() throws Exception {
        final HttpRequest request = new BasicHttpRequest(Method.GET, URI.create("http://someuser%21@%21example%21.com/stuff"));
        final HttpRequestWrapper httpRequestWrapper = new HttpRequestWrapper(request);
        Assertions.assertThat(httpRequestWrapper.getMethod()).isEqualTo(Method.GET.name());
        Assertions.assertThat(httpRequestWrapper.getPath()).isEqualTo("/stuff");
        Assertions.assertThat(httpRequestWrapper.getAuthority()).isEqualTo(new URIAuthority("someuser%21", "%21example%21.com", -1));
        Assertions.assertThat(httpRequestWrapper.getUri()).isEqualTo(new URI("http://%21example%21.com/stuff"));
    }
}