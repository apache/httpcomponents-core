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

package org.apache.hc.core5.http.support;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.net.URIAuthority;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Simple tests for {@link BasicResponseBuilder} and {@link BasicRequestBuilder}.
 */
class TestBasicMessageBuilders {

    @Test
    void testResponseBasics() {
        final BasicResponseBuilder builder = BasicResponseBuilder.create(200);
        Assertions.assertThat(builder.getStatus()).isEqualTo(200);
        Assertions.assertThat(builder.getHeaders()).isNull();
        Assertions.assertThat(builder.getVersion()).isNull();

        final BasicHttpResponse r1 = builder.build();
        Assertions.assertThat(r1).isNotNull();
        Assertions.assertThat(r1.getCode()).isEqualTo(200);
        Assertions.assertThat(r1.getVersion()).isNull();

        builder.setStatus(500);
        builder.setVersion(HttpVersion.HTTP_1_0);
        Assertions.assertThat(builder.getStatus()).isEqualTo(500);
        Assertions.assertThat(builder.getVersion()).isEqualTo(HttpVersion.HTTP_1_0);

        final BasicHttpResponse r2 = builder.build();
        Assertions.assertThat(r2.getCode()).isEqualTo(500);
        Assertions.assertThat(r2.getVersion()).isEqualTo(HttpVersion.HTTP_1_0);

        builder.addHeader("h1", "v1");
        builder.addHeader("h1", "v2");
        builder.addHeader("h2", "v2");
        Assertions.assertThat(builder.getHeaders())
                .extracting(header -> Assertions.tuple(header.getName(), header.getValue()))
                .isEqualTo(Arrays.asList(Assertions.tuple("h1", "v1"), Assertions.tuple("h1", "v2"), Assertions.tuple("h2", "v2")));
        Assertions.assertThat(builder.getHeaders("h1"))
                .extracting(header -> Assertions.tuple(header.getName(), header.getValue()))
                .isEqualTo(Arrays.asList(Assertions.tuple("h1", "v1"), Assertions.tuple("h1", "v2")));
        Assertions.assertThat(builder.getFirstHeader("h1"))
                .extracting(header -> Assertions.tuple(header.getName(), header.getValue()))
                .isEqualTo(Assertions.tuple("h1", "v1"));
        Assertions.assertThat(builder.getLastHeader("h1"))
                .extracting(header -> Assertions.tuple(header.getName(), header.getValue()))
                .isEqualTo(Assertions.tuple("h1", "v2"));

        final BasicHttpResponse r3 = builder.build();
        Assertions.assertThat(r3.getHeaders())
                .extracting(header -> Assertions.tuple(header.getName(), header.getValue()))
                .isEqualTo(Arrays.asList(Assertions.tuple("h1", "v1"), Assertions.tuple("h1", "v2"), Assertions.tuple("h2", "v2")));
        Assertions.assertThat(r3.getHeaders("h1"))
                .extracting(header -> Assertions.tuple(header.getName(), header.getValue()))
                .isEqualTo(Arrays.asList(Assertions.tuple("h1", "v1"), Assertions.tuple("h1", "v2")));
        Assertions.assertThat(r3.getFirstHeader("h1"))
                .extracting(header -> Assertions.tuple(header.getName(), header.getValue()))
                .isEqualTo(Assertions.tuple("h1", "v1"));
        Assertions.assertThat(r3.getLastHeader("h1"))
                .extracting(header -> Assertions.tuple(header.getName(), header.getValue()))
                .isEqualTo(Assertions.tuple("h1", "v2"));

        builder.removeHeader(new BasicHeader("h1", "v2"));
        Assertions.assertThat(builder.getHeaders("h1"))
                .extracting(header -> Assertions.tuple(header.getName(), header.getValue()))
                .isEqualTo(Arrays.asList(Assertions.tuple("h1", "v1")));
        Assertions.assertThat(builder.getHeaders())
                .extracting(header -> Assertions.tuple(header.getName(), header.getValue()))
                .isEqualTo(Arrays.asList(Assertions.tuple("h1", "v1"), Assertions.tuple("h2", "v2")));

        final BasicHttpResponse r4 = builder.build();
        Assertions.assertThat(r4.getHeaders("h1"))
                .extracting(header -> Assertions.tuple(header.getName(), header.getValue()))
                .isEqualTo(Arrays.asList(Assertions.tuple("h1", "v1")));
        Assertions.assertThat(r4.getHeaders())
                .extracting(header -> Assertions.tuple(header.getName(), header.getValue()))
                .isEqualTo(Arrays.asList(Assertions.tuple("h1", "v1"), Assertions.tuple("h2", "v2")));

        builder.removeHeaders("h1");
        Assertions.assertThat(builder.getHeaders("h1"))
                .extracting(header -> Assertions.tuple(header.getName(), header.getValue()))
                .isEqualTo(Arrays.asList());
        Assertions.assertThat(builder.getHeaders())
                .extracting(header -> Assertions.tuple(header.getName(), header.getValue()))
                .isEqualTo(Arrays.asList(Assertions.tuple("h2", "v2")));

        final BasicHttpResponse r5 = builder.build();
        Assertions.assertThat(r5.getHeaders("h1"))
                .extracting(header -> Assertions.tuple(header.getName(), header.getValue()))
                .isEqualTo(Arrays.asList());
        Assertions.assertThat(r5.getHeaders())
                .extracting(header -> Assertions.tuple(header.getName(), header.getValue()))
                .isEqualTo(Arrays.asList(Assertions.tuple("h2", "v2")));
    }

    @Test
    void testRequestBasics() throws Exception {
        final BasicRequestBuilder builder = BasicRequestBuilder.get();
        Assertions.assertThat(builder.getUri()).isEqualTo(URI.create("/"));
        Assertions.assertThat(builder.getMethod()).isEqualTo("GET");
        Assertions.assertThat(builder.getScheme()).isNull();
        Assertions.assertThat(builder.getAuthority()).isNull();
        Assertions.assertThat(builder.getPath()).isNull();
        Assertions.assertThat(builder.getHeaders()).isNull();
        Assertions.assertThat(builder.getVersion()).isNull();
        Assertions.assertThat(builder.getCharset()).isNull();
        Assertions.assertThat(builder.getParameters()).isNull();

        final BasicHttpRequest r1 = builder.build();
        Assertions.assertThat(r1).isNotNull();
        Assertions.assertThat(r1.getMethod()).isEqualTo("GET");
        Assertions.assertThat(r1.getScheme()).isNull();
        Assertions.assertThat(r1.getAuthority()).isNull();
        Assertions.assertThat(r1.getPath()).isNull();
        Assertions.assertThat(r1.getUri()).isEqualTo(URI.create("/"));
        Assertions.assertThat(r1.getVersion()).isNull();

        builder.setUri(URI.create("http://host:1234/blah?param=value"));
        builder.setVersion(HttpVersion.HTTP_1_1);
        Assertions.assertThat(builder.getScheme()).isEqualTo("http");
        Assertions.assertThat(builder.getAuthority()).isEqualTo(new URIAuthority("host", 1234));
        Assertions.assertThat(builder.getPath()).isEqualTo("/blah?param=value");
        Assertions.assertThat(builder.getUri()).isEqualTo(URI.create("http://host:1234/blah?param=value"));
        Assertions.assertThat(builder.getVersion()).isEqualTo(HttpVersion.HTTP_1_1);

        final BasicHttpRequest r2 = builder.build();
        Assertions.assertThat(r2.getMethod()).isEqualTo("GET");
        Assertions.assertThat(r2.getScheme()).isEqualTo("http");
        Assertions.assertThat(r2.getAuthority()).isEqualTo(new URIAuthority("host", 1234));
        Assertions.assertThat(r2.getPath()).isEqualTo("/blah?param=value");
        Assertions.assertThat(r2.getUri()).isEqualTo(URI.create("http://host:1234/blah?param=value"));
        Assertions.assertThat(builder.getVersion()).isEqualTo(HttpVersion.HTTP_1_1);

        builder.setCharset(StandardCharsets.US_ASCII);
        builder.addParameter("param1", "value1");
        builder.addParameter("param2", null);
        builder.addParameters(new BasicNameValuePair("param3", "value3"), new BasicNameValuePair("param4", null));

        Assertions.assertThat(Arrays.asList(
                new BasicNameValuePair("param1", "value1"), new BasicNameValuePair("param2", null),
                new BasicNameValuePair("param3", "value3"), new BasicNameValuePair("param4", null)
        )).isEqualTo(builder.getParameters());
        Assertions.assertThat(builder.getUri()).isEqualTo(URI.create("http://host:1234/blah?param=value"));

        final BasicHttpRequest r3 = builder.build();
        Assertions.assertThat(r3.getMethod()).isEqualTo("GET");
        Assertions.assertThat(r3.getScheme()).isEqualTo("http");
        Assertions.assertThat(r3.getAuthority()).isEqualTo(new URIAuthority("host", 1234));
        Assertions.assertThat(r3.getPath()).isEqualTo("/blah?param=value&param1=value1&param2&param3=value3&param4");
        Assertions.assertThat(r3.getUri()).isEqualTo(URI.create("http://host:1234/blah?param=value&param1=value1&param2&param3=value3&param4"));

        builder.addHeader("h1", "v1");
        builder.addHeader("h1", "v2");
        builder.addHeader("h2", "v2");
        Assertions.assertThat(builder.getHeaders())
                .extracting(header -> Assertions.tuple(header.getName(), header.getValue()))
                .isEqualTo(Arrays.asList(Assertions.tuple("h1", "v1"), Assertions.tuple("h1", "v2"), Assertions.tuple("h2", "v2")));
        Assertions.assertThat(builder.getHeaders("h1"))
                .extracting(header -> Assertions.tuple(header.getName(), header.getValue()))
                .isEqualTo(Arrays.asList(Assertions.tuple("h1", "v1"), Assertions.tuple("h1", "v2")));
        Assertions.assertThat(builder.getFirstHeader("h1"))
                .extracting(header -> Assertions.tuple(header.getName(), header.getValue()))
                .isEqualTo(Assertions.tuple("h1", "v1"));
        Assertions.assertThat(builder.getLastHeader("h1"))
                .extracting(header -> Assertions.tuple(header.getName(), header.getValue()))
                .isEqualTo(Assertions.tuple("h1", "v2"));

        final BasicHttpRequest r4 = builder.build();
        Assertions.assertThat(r4.getHeaders())
                .extracting(header -> Assertions.tuple(header.getName(), header.getValue()))
                .isEqualTo(Arrays.asList(Assertions.tuple("h1", "v1"), Assertions.tuple("h1", "v2"), Assertions.tuple("h2", "v2")));
        Assertions.assertThat(r4.getHeaders("h1"))
                .extracting(header -> Assertions.tuple(header.getName(), header.getValue()))
                .isEqualTo(Arrays.asList(Assertions.tuple("h1", "v1"), Assertions.tuple("h1", "v2")));
        Assertions.assertThat(r4.getFirstHeader("h1"))
                .extracting(header -> Assertions.tuple(header.getName(), header.getValue()))
                .isEqualTo(Assertions.tuple("h1", "v1"));
        Assertions.assertThat(r4.getLastHeader("h1"))
                .extracting(header -> Assertions.tuple(header.getName(), header.getValue()))
                .isEqualTo(Assertions.tuple("h1", "v2"));

        builder.removeHeader(new BasicHeader("h1", "v2"));
        Assertions.assertThat(builder.getHeaders("h1"))
                .extracting(header -> Assertions.tuple(header.getName(), header.getValue()))
                .isEqualTo(Arrays.asList(Assertions.tuple("h1", "v1")));
        Assertions.assertThat(builder.getHeaders())
                .extracting(header -> Assertions.tuple(header.getName(), header.getValue()))
                .isEqualTo(Arrays.asList(Assertions.tuple("h1", "v1"), Assertions.tuple("h2", "v2")));

        final BasicHttpRequest r5 = builder.build();
        Assertions.assertThat(r5.getHeaders("h1"))
                .extracting(header -> Assertions.tuple(header.getName(), header.getValue()))
                .isEqualTo(Arrays.asList(Assertions.tuple("h1", "v1")));
        Assertions.assertThat(r5.getHeaders())
                .extracting(header -> Assertions.tuple(header.getName(), header.getValue()))
                .isEqualTo(Arrays.asList(Assertions.tuple("h1", "v1"), Assertions.tuple("h2", "v2")));

        builder.removeHeaders("h1");
        Assertions.assertThat(builder.getHeaders("h1"))
                .extracting(header -> Assertions.tuple(header.getName(), header.getValue()))
                .isEqualTo(Arrays.asList());
        Assertions.assertThat(builder.getHeaders())
                .extracting(header -> Assertions.tuple(header.getName(), header.getValue()))
                .isEqualTo(Arrays.asList(Assertions.tuple("h2", "v2")));

        final BasicHttpRequest r6 = builder.build();
        Assertions.assertThat(r6.getHeaders("h1"))
                .extracting(header -> Assertions.tuple(header.getName(), header.getValue()))
                .isEqualTo(Arrays.asList());
        Assertions.assertThat(r6.getHeaders())
                .extracting(header -> Assertions.tuple(header.getName(), header.getValue()))
                .isEqualTo(Arrays.asList(Assertions.tuple("h2", "v2")));
    }

    @Test
    void testResponseCopy() {
        final HttpResponse response = new BasicHttpResponse(400);
        response.addHeader("h1", "v1");
        response.addHeader("h1", "v2");
        response.addHeader("h2", "v2");
        response.setVersion(HttpVersion.HTTP_2);

        final BasicResponseBuilder builder = BasicResponseBuilder.copy(response);
        Assertions.assertThat(builder.getStatus()).isEqualTo(400);
        Assertions.assertThat(builder.getVersion()).isEqualTo(HttpVersion.HTTP_2);
        Assertions.assertThat(builder.getHeaders())
                .extracting(header -> Assertions.tuple(header.getName(), header.getValue()))
                .isEqualTo(Arrays.asList(Assertions.tuple("h1", "v1"), Assertions.tuple("h1", "v2"), Assertions.tuple("h2", "v2")));
    }

    @Test
    void testRequestCopy() {
        final HttpRequest request = new BasicHttpRequest(Method.GET, URI.create("https://host:3456/stuff?blah"));
        request.addHeader("h1", "v1");
        request.addHeader("h1", "v2");
        request.addHeader("h2", "v2");
        request.setVersion(HttpVersion.HTTP_2);

        final BasicRequestBuilder builder = BasicRequestBuilder.copy(request);
        Assertions.assertThat(builder.getMethod()).isEqualTo("GET");
        Assertions.assertThat(builder.getScheme()).isEqualTo("https");
        Assertions.assertThat(builder.getAuthority()).isEqualTo(new URIAuthority("host", 3456));
        Assertions.assertThat(builder.getPath()).isEqualTo("/stuff?blah");
        Assertions.assertThat(builder.getVersion()).isEqualTo(HttpVersion.HTTP_2);
        Assertions.assertThat(builder.getHeaders())
                .extracting(header -> Assertions.tuple(header.getName(), header.getValue()))
                .isEqualTo(Arrays.asList(Assertions.tuple("h1", "v1"), Assertions.tuple("h1", "v2"), Assertions.tuple("h2", "v2")));
    }

    @Test
    void testIDNIntegration() {
        final String url = "http://müller.example.com:8080/path";
        final HttpRequest request = new BasicHttpRequest(Method.GET, URI.create(url));
        Assertions.assertThat(request.getAuthority()).isEqualTo(new URIAuthority("xn--mller-kva.example.com", 8080));
    }

}
