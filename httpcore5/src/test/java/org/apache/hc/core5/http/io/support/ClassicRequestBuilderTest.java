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

package org.apache.hc.core5.http.io.support;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.net.URIBuilder;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

class ClassicRequestBuilderTest {

    @Test
    void constructor() throws UnknownHostException, URISyntaxException {
        final ClassicRequestBuilder classicRequestBuilder = new ClassicRequestBuilder(Method.HEAD);
        Assertions.assertThat(classicRequestBuilder.getMethod()).isEqualTo(Method.HEAD.name());

        final ClassicRequestBuilder classicRequestBuilder2 = new ClassicRequestBuilder(Method.HEAD.name());
        Assertions.assertThat(classicRequestBuilder2.getMethod()).isEqualTo(Method.HEAD.name());

        final ClassicRequestBuilder classicRequestBuilder3 = new ClassicRequestBuilder(Method.HEAD.name(), URIBuilder.localhost().build());
        Assertions.assertThat(classicRequestBuilder3.getMethod()).isEqualTo(Method.HEAD.name());
        Assertions.assertThat(classicRequestBuilder3.getAuthority().getHostName()).isEqualTo(URIBuilder.localhost().getHost());

        final ClassicRequestBuilder classicRequestBuilder4 = new ClassicRequestBuilder(Method.HEAD, URIBuilder.localhost().build());
        Assertions.assertThat(classicRequestBuilder4.getMethod()).isEqualTo(Method.HEAD.name());
        Assertions.assertThat(classicRequestBuilder4.getAuthority().getHostName()).isEqualTo(URIBuilder.localhost().getHost());

        final ClassicRequestBuilder classicRequestBuilder5 = new ClassicRequestBuilder(Method.HEAD, "/localhost");
        Assertions.assertThat(classicRequestBuilder5.getMethod()).isEqualTo(Method.HEAD.name());
        Assertions.assertThat(classicRequestBuilder5.getPath()).isEqualTo("/localhost");

        final ClassicRequestBuilder classicRequestBuilder6 = new ClassicRequestBuilder(Method.HEAD.name(), "/localhost");
        Assertions.assertThat(classicRequestBuilder6.getMethod()).isEqualTo(Method.HEAD.name());
        Assertions.assertThat(classicRequestBuilder6.getPath()).isEqualTo("/localhost");
    }

    @Test
    void create() {
        final ClassicHttpRequest classicHttpRequest = ClassicRequestBuilder.create(Method.HEAD.name()).build();
        Assertions.assertThat(classicHttpRequest.getMethod()).isEqualTo(Method.HEAD.name());
    }

    @Test
    void get() throws UnknownHostException, URISyntaxException {
        final ClassicRequestBuilder classicRequestBuilder = ClassicRequestBuilder.get();
        Assertions.assertThat(classicRequestBuilder.getMethod()).isEqualTo(Method.GET.name());

        final ClassicRequestBuilder classicRequestBuilder1 = ClassicRequestBuilder.get(URIBuilder.localhost().build());
        Assertions.assertThat(classicRequestBuilder1.getMethod()).isEqualTo(Method.GET.name());

        final ClassicRequestBuilder classicRequestBuilder3 = ClassicRequestBuilder.get("/localhost");
        Assertions.assertThat(classicRequestBuilder3.getMethod()).isEqualTo(Method.GET.name());
        Assertions.assertThat(classicRequestBuilder3.getPath()).isEqualTo("/localhost");
    }

    @Test
    void head() throws UnknownHostException, URISyntaxException {
        final ClassicRequestBuilder classicRequestBuilder = ClassicRequestBuilder.head();
        Assertions.assertThat(classicRequestBuilder.getMethod()).isEqualTo(Method.HEAD.name());

        final ClassicRequestBuilder classicRequestBuilder1 = ClassicRequestBuilder.head(URIBuilder.localhost().build());
        Assertions.assertThat(classicRequestBuilder1.getMethod()).isEqualTo(Method.HEAD.name());

        final ClassicRequestBuilder classicRequestBuilder3 = ClassicRequestBuilder.head("/localhost");
        Assertions.assertThat(classicRequestBuilder3.getMethod()).isEqualTo(Method.HEAD.name());
        Assertions.assertThat(classicRequestBuilder3.getPath()).isEqualTo("/localhost");
    }

    @Test
    void query() throws UnknownHostException, URISyntaxException {
        final ClassicRequestBuilder classicRequestBuilder = ClassicRequestBuilder.query();
        Assertions.assertThat(classicRequestBuilder.getMethod()).isEqualTo(Method.QUERY.name());

        final ClassicRequestBuilder classicRequestBuilder1 = ClassicRequestBuilder.query(URIBuilder.localhost().build());
        Assertions.assertThat(classicRequestBuilder1.getMethod()).isEqualTo(Method.QUERY.name());

        final ClassicRequestBuilder classicRequestBuilder3 = ClassicRequestBuilder.query("/localhost");
        Assertions.assertThat(classicRequestBuilder3.getMethod()).isEqualTo(Method.QUERY.name());
        Assertions.assertThat(classicRequestBuilder3.getPath()).isEqualTo("/localhost");
    }

    @Test
    void patch() throws UnknownHostException, URISyntaxException {
        final ClassicRequestBuilder classicRequestBuilder = ClassicRequestBuilder.patch();
        Assertions.assertThat(classicRequestBuilder.getMethod()).isEqualTo(Method.PATCH.name());

        final ClassicRequestBuilder classicRequestBuilder1 = ClassicRequestBuilder.patch(URIBuilder.localhost().build());
        Assertions.assertThat(classicRequestBuilder1.getMethod()).isEqualTo(Method.PATCH.name());

        final ClassicRequestBuilder classicRequestBuilder3 = ClassicRequestBuilder.patch("/localhost");
        Assertions.assertThat(classicRequestBuilder3.getMethod()).isEqualTo(Method.PATCH.name());
        Assertions.assertThat(classicRequestBuilder3.getPath()).isEqualTo("/localhost");
    }

    @Test
    void post() throws UnknownHostException, URISyntaxException {
        final ClassicRequestBuilder classicRequestBuilder = ClassicRequestBuilder.post();
        Assertions.assertThat(classicRequestBuilder.getMethod()).isEqualTo(Method.POST.name());

        final ClassicRequestBuilder classicRequestBuilder1 = ClassicRequestBuilder.post(URIBuilder.localhost().build());
        Assertions.assertThat(classicRequestBuilder1.getMethod()).isEqualTo(Method.POST.name());

        final ClassicRequestBuilder classicRequestBuilder3 = ClassicRequestBuilder.post("/localhost");
        Assertions.assertThat(classicRequestBuilder3.getMethod()).isEqualTo(Method.POST.name());
        Assertions.assertThat(classicRequestBuilder3.getPath()).isEqualTo("/localhost");
    }

    @Test
    void put() throws UnknownHostException, URISyntaxException {
        final ClassicRequestBuilder classicRequestBuilder = ClassicRequestBuilder.put();
        Assertions.assertThat(classicRequestBuilder.getMethod()).isEqualTo(Method.PUT.name());

        final ClassicRequestBuilder classicRequestBuilder1 = ClassicRequestBuilder.put(URIBuilder.localhost().build());
        Assertions.assertThat(classicRequestBuilder1.getMethod()).isEqualTo(Method.PUT.name());

        final ClassicRequestBuilder classicRequestBuilder3 = ClassicRequestBuilder.put("/localhost");
        Assertions.assertThat(classicRequestBuilder3.getMethod()).isEqualTo(Method.PUT.name());
        Assertions.assertThat(classicRequestBuilder3.getPath()).isEqualTo("/localhost");
    }

    @Test
    void delete() throws UnknownHostException, URISyntaxException {
        final ClassicRequestBuilder classicRequestBuilder = ClassicRequestBuilder.delete();
        Assertions.assertThat(classicRequestBuilder.getMethod()).isEqualTo(Method.DELETE.name());

        final ClassicRequestBuilder classicRequestBuilder1 = ClassicRequestBuilder.delete(URIBuilder.localhost().build());
        Assertions.assertThat(classicRequestBuilder1.getMethod()).isEqualTo(Method.DELETE.name());

        final ClassicRequestBuilder classicRequestBuilder3 = ClassicRequestBuilder.delete("/localhost");
        Assertions.assertThat(classicRequestBuilder3.getMethod()).isEqualTo(Method.DELETE.name());
        Assertions.assertThat(classicRequestBuilder3.getPath()).isEqualTo("/localhost");
    }

    @Test
    void trace() throws UnknownHostException, URISyntaxException {
        final ClassicRequestBuilder classicRequestBuilder = ClassicRequestBuilder.trace();
        Assertions.assertThat(classicRequestBuilder.getMethod()).isEqualTo(Method.TRACE.name());

        final ClassicRequestBuilder classicRequestBuilder1 = ClassicRequestBuilder.trace(URIBuilder.localhost().build());
        Assertions.assertThat(classicRequestBuilder1.getMethod()).isEqualTo(Method.TRACE.name());

        final ClassicRequestBuilder classicRequestBuilder3 = ClassicRequestBuilder.trace("/localhost");
        Assertions.assertThat(classicRequestBuilder3.getMethod()).isEqualTo(Method.TRACE.name());
        Assertions.assertThat(classicRequestBuilder3.getPath()).isEqualTo("/localhost");
    }

    @Test
    void option() throws UnknownHostException, URISyntaxException {
        final ClassicRequestBuilder classicRequestBuilder = ClassicRequestBuilder.options();
        Assertions.assertThat(classicRequestBuilder.getMethod()).isEqualTo(Method.OPTIONS.name());

        final ClassicRequestBuilder classicRequestBuilder1 = ClassicRequestBuilder.options(URIBuilder.localhost().build());
        Assertions.assertThat(classicRequestBuilder1.getMethod()).isEqualTo(Method.OPTIONS.name());

        final ClassicRequestBuilder classicRequestBuilder3 = ClassicRequestBuilder.options("/localhost");
        Assertions.assertThat(classicRequestBuilder3.getMethod()).isEqualTo(Method.OPTIONS.name());
        Assertions.assertThat(classicRequestBuilder3.getPath()).isEqualTo("/localhost");
    }

    @Test
    void builder() {
        final Header header = new BasicHeader("header2", "blah");
        final ClassicHttpRequest classicHttpRequest = ClassicRequestBuilder.get()
                .setVersion(HttpVersion.HTTP_1_1)
                .setCharset(StandardCharsets.US_ASCII)
                .setAuthority(new URIAuthority("host"))
                .setEntity("<html><body><h1>Access denied</h1></body></html>", ContentType.TEXT_HTML)
                .setHeader(new BasicHeader("header2", "blah"))
                .setHeader("X-Test-Filter", "active")
                .setHeader(header)
                .setPath("path/")
                .setScheme("http")
                .addHeader(header)
                .addHeader("header", ".addHeader(header)")
                .addParameter(new BasicHeader("header2", "blah"))
                .addParameter("param1", "value1")
                .addParameters(new BasicNameValuePair("param3", "value3"), new BasicNameValuePair("param4", null))
                .setAbsoluteRequestUri(true)
                .setEntity(new StringEntity("requestBody"))
                .setEntity(new ByteArrayEntity(new byte[10240], ContentType.TEXT_PLAIN))
                .setEntity(new byte[10240], ContentType.TEXT_HTML)
                .setEntity("requestBody")
                .setUri("theUri")
                .setHttpHost(new HttpHost("httpbin.org"))
                .build();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(classicHttpRequest.getEntity()).isNotNull();
            softly.assertThat(classicHttpRequest.getMethod()).isEqualTo(Method.GET.name());
            softly.assertThat(classicHttpRequest.getScheme()).isEqualTo("http");
            softly.assertThat(classicHttpRequest.getAuthority().getHostName()).isEqualTo("httpbin.org");
            softly.assertThat(classicHttpRequest.getVersion()).isEqualTo(HttpVersion.HTTP_1_1);
            softly.assertThat(classicHttpRequest.getHeaders()).hasSize(4);
            softly.assertThat(ClassicRequestBuilder.get().toString()).isNotNull();
            softly.assertThat(new String(classicHttpRequest.getRequestUri().getBytes()))
                    .isEqualTo("http://httpbin.org/theUri?header2=blah&param1=value1&param3=value3&param4");
        });
    }


    @Test
    void builderTraceThrowsIllegalStateException() {
        Assertions.assertThatThrownBy(() ->
                ClassicRequestBuilder.trace()
                        .setVersion(HttpVersion.HTTP_1_1)
                        .setEntity(new ByteArrayEntity(new byte[10240], ContentType.TEXT_PLAIN))
                        .build()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void builderQuery() throws IOException, ParseException {
        final ClassicHttpRequest classicHttpRequest = ClassicRequestBuilder.query()
                .setHttpHost(new HttpHost("httpbin.org"))
                .setPath("/theUri")
                .addParameter("param1", "value1")
                .addParameter("param2", "value2")
                .build();

        final String entityContent = EntityUtils.toString(classicHttpRequest.getEntity(), 1024);
        final String contentType = ContentType.parse(classicHttpRequest.getEntity().getContentType()).getMimeType();
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(classicHttpRequest.getMethod()).isEqualTo(Method.QUERY.name());
            softly.assertThat(classicHttpRequest.getAuthority().getHostName()).isEqualTo("httpbin.org");
            softly.assertThat(classicHttpRequest.getPath()).isEqualTo("/theUri");
            softly.assertThat(classicHttpRequest.getEntity()).isNotNull();
            softly.assertThat(contentType).isEqualTo(ContentType.APPLICATION_FORM_URLENCODED.getMimeType());
            softly.assertThat(entityContent).isEqualTo("param1=value1&param2=value2");
        });
    }

}