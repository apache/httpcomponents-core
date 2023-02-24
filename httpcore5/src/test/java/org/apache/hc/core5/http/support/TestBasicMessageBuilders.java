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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.hc.core5.http.HeaderMatcher;
import org.apache.hc.core5.http.HeadersMatcher;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.net.URIAuthority;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Simple tests for {@link BasicResponseBuilder} and {@link BasicRequestBuilder}.
 */
public class TestBasicMessageBuilders {

    @Test
    public void testResponseBasics() throws Exception {
        final BasicResponseBuilder builder = BasicResponseBuilder.create(200);
        Assertions.assertEquals(200, builder.getStatus());
        Assertions.assertNull(builder.getHeaders());
        Assertions.assertNull(builder.getVersion());

        final BasicHttpResponse r1 = builder.build();
        Assertions.assertNotNull(r1);
        Assertions.assertEquals(200, r1.getCode());
        Assertions.assertNull(r1.getVersion());

        builder.setStatus(500);
        builder.setVersion(HttpVersion.HTTP_1_0);
        Assertions.assertEquals(500, builder.getStatus());
        Assertions.assertEquals(HttpVersion.HTTP_1_0, builder.getVersion());

        final BasicHttpResponse r2 = builder.build();
        Assertions.assertEquals(500, r2.getCode());
        Assertions.assertEquals(HttpVersion.HTTP_1_0, r2.getVersion());

        builder.addHeader("h1", "v1");
        builder.addHeader("h1", "v2");
        builder.addHeader("h2", "v2");
        assertThat(builder.getHeaders(), HeadersMatcher.same(
                new BasicHeader("h1", "v1"), new BasicHeader("h1", "v2"), new BasicHeader("h2", "v2")));
        assertThat(builder.getHeaders("h1"), HeadersMatcher.same(
                new BasicHeader("h1", "v1"), new BasicHeader("h1", "v2")));
        assertThat(builder.getFirstHeader("h1"), HeaderMatcher.same("h1", "v1"));
        assertThat(builder.getLastHeader("h1"), HeaderMatcher.same("h1", "v2"));

        final BasicHttpResponse r3 = builder.build();
        assertThat(r3.getHeaders(), HeadersMatcher.same(
                new BasicHeader("h1", "v1"), new BasicHeader("h1", "v2"), new BasicHeader("h2", "v2")));
        assertThat(r3.getHeaders("h1"), HeadersMatcher.same(
                new BasicHeader("h1", "v1"), new BasicHeader("h1", "v2")));
        assertThat(r3.getFirstHeader("h1"), HeaderMatcher.same("h1", "v1"));
        assertThat(r3.getLastHeader("h1"), HeaderMatcher.same("h1", "v2"));

        builder.removeHeader(new BasicHeader("h1", "v2"));
        assertThat(builder.getHeaders("h1"), HeadersMatcher.same(new BasicHeader("h1", "v1")));
        assertThat(builder.getHeaders(), HeadersMatcher.same(
                new BasicHeader("h1", "v1"), new BasicHeader("h2", "v2")));

        final BasicHttpResponse r4 = builder.build();
        assertThat(r4.getHeaders("h1"), HeadersMatcher.same(new BasicHeader("h1", "v1")));
        assertThat(r4.getHeaders(), HeadersMatcher.same(
                new BasicHeader("h1", "v1"), new BasicHeader("h2", "v2")));

        builder.removeHeaders("h1");
        assertThat(builder.getHeaders("h1"), HeadersMatcher.same());
        assertThat(builder.getHeaders(), HeadersMatcher.same(new BasicHeader("h2", "v2")));

        final BasicHttpResponse r5 = builder.build();
        assertThat(r5.getHeaders("h1"), HeadersMatcher.same());
        assertThat(r5.getHeaders(), HeadersMatcher.same(new BasicHeader("h2", "v2")));
    }

    @Test
    public void testRequestBasics() throws Exception {
        final BasicRequestBuilder builder = BasicRequestBuilder.get();
        Assertions.assertEquals(URI.create("/"), builder.getUri());
        Assertions.assertEquals("GET", builder.getMethod());
        Assertions.assertNull(builder.getScheme());
        Assertions.assertNull(builder.getAuthority());
        Assertions.assertNull(builder.getPath());
        Assertions.assertNull(builder.getHeaders());
        Assertions.assertNull(builder.getVersion());
        Assertions.assertNull(builder.getCharset());
        Assertions.assertNull(builder.getParameters());

        final BasicHttpRequest r1 = builder.build();
        Assertions.assertNotNull(r1);
        Assertions.assertEquals("GET", r1.getMethod());
        Assertions.assertNull(r1.getScheme());
        Assertions.assertNull(r1.getAuthority());
        Assertions.assertNull(r1.getPath());
        Assertions.assertEquals(URI.create("/"), r1.getUri());
        Assertions.assertNull(r1.getVersion());

        builder.setUri(URI.create("http://host:1234/blah?param=value"));
        builder.setVersion(HttpVersion.HTTP_1_1);
        Assertions.assertEquals("http", builder.getScheme());
        Assertions.assertEquals(new URIAuthority("host", 1234), builder.getAuthority());
        Assertions.assertEquals("/blah?param=value", builder.getPath());
        Assertions.assertEquals(URI.create("http://host:1234/blah?param=value"), builder.getUri());
        Assertions.assertEquals(HttpVersion.HTTP_1_1, builder.getVersion());

        final BasicHttpRequest r2 = builder.build();
        Assertions.assertEquals("GET", r2.getMethod());
        Assertions.assertEquals("http", r2.getScheme());
        Assertions.assertEquals(new URIAuthority("host", 1234), r2.getAuthority());
        Assertions.assertEquals("/blah?param=value", r2.getPath());
        Assertions.assertEquals(URI.create("http://host:1234/blah?param=value"), r2.getUri());
        Assertions.assertEquals(HttpVersion.HTTP_1_1, builder.getVersion());

        builder.setCharset(StandardCharsets.US_ASCII);
        builder.addParameter("param1", "value1");
        builder.addParameter("param2", null);
        builder.addParameters(new BasicNameValuePair("param3", "value3"), new BasicNameValuePair("param4", null));

        Assertions.assertEquals(builder.getParameters(), Arrays.asList(
                new BasicNameValuePair("param1", "value1"), new BasicNameValuePair("param2", null),
                new BasicNameValuePair("param3", "value3"), new BasicNameValuePair("param4", null)
        ));
        Assertions.assertEquals(URI.create("http://host:1234/blah?param=value"), builder.getUri());

        final BasicHttpRequest r3 = builder.build();
        Assertions.assertEquals("GET", r3.getMethod());
        Assertions.assertEquals("http", r3.getScheme());
        Assertions.assertEquals(new URIAuthority("host", 1234), r3.getAuthority());
        Assertions.assertEquals("/blah?param=value&param1=value1&param2&param3=value3&param4", r3.getPath());
        Assertions.assertEquals(URI.create("http://host:1234/blah?param=value&param1=value1&param2&param3=value3&param4"),
                r3.getUri());

        builder.addHeader("h1", "v1");
        builder.addHeader("h1", "v2");
        builder.addHeader("h2", "v2");
        assertThat(builder.getHeaders(), HeadersMatcher.same(
                new BasicHeader("h1", "v1"), new BasicHeader("h1", "v2"), new BasicHeader("h2", "v2")));
        assertThat(builder.getHeaders("h1"), HeadersMatcher.same(
                new BasicHeader("h1", "v1"), new BasicHeader("h1", "v2")));
        assertThat(builder.getFirstHeader("h1"), HeaderMatcher.same("h1", "v1"));
        assertThat(builder.getLastHeader("h1"), HeaderMatcher.same("h1", "v2"));

        final BasicHttpRequest r4 = builder.build();
        assertThat(r4.getHeaders(), HeadersMatcher.same(
                new BasicHeader("h1", "v1"), new BasicHeader("h1", "v2"), new BasicHeader("h2", "v2")));
        assertThat(r4.getHeaders("h1"), HeadersMatcher.same(
                new BasicHeader("h1", "v1"), new BasicHeader("h1", "v2")));
        assertThat(r4.getFirstHeader("h1"), HeaderMatcher.same("h1", "v1"));
        assertThat(r4.getLastHeader("h1"), HeaderMatcher.same("h1", "v2"));

        builder.removeHeader(new BasicHeader("h1", "v2"));
        assertThat(builder.getHeaders("h1"), HeadersMatcher.same(new BasicHeader("h1", "v1")));
        assertThat(builder.getHeaders(), HeadersMatcher.same(
                new BasicHeader("h1", "v1"), new BasicHeader("h2", "v2")));

        final BasicHttpRequest r5 = builder.build();
        assertThat(r5.getHeaders("h1"), HeadersMatcher.same(new BasicHeader("h1", "v1")));
        assertThat(r5.getHeaders(), HeadersMatcher.same(
                new BasicHeader("h1", "v1"), new BasicHeader("h2", "v2")));

        builder.removeHeaders("h1");
        assertThat(builder.getHeaders("h1"), HeadersMatcher.same());
        assertThat(builder.getHeaders(), HeadersMatcher.same(new BasicHeader("h2", "v2")));

        final BasicHttpRequest r6 = builder.build();
        assertThat(r6.getHeaders("h1"), HeadersMatcher.same());
        assertThat(r6.getHeaders(), HeadersMatcher.same(new BasicHeader("h2", "v2")));
    }

    @Test
    public void testResponseCopy() throws Exception {
        final HttpResponse response = new BasicHttpResponse(400);
        response.addHeader("h1", "v1");
        response.addHeader("h1", "v2");
        response.addHeader("h2", "v2");
        response.setVersion(HttpVersion.HTTP_2);

        final BasicResponseBuilder builder = BasicResponseBuilder.copy(response);
        Assertions.assertEquals(400, builder.getStatus());
        Assertions.assertEquals(HttpVersion.HTTP_2, builder.getVersion());
        assertThat(builder.getHeaders(), HeadersMatcher.same(
                new BasicHeader("h1", "v1"), new BasicHeader("h1", "v2"), new BasicHeader("h2", "v2")));
    }

    @Test
    public void testRequestCopy() throws Exception {
        final HttpRequest request = new BasicHttpRequest(Method.GET, URI.create("https://host:3456/stuff?blah")) ;
        request.addHeader("h1", "v1");
        request.addHeader("h1", "v2");
        request.addHeader("h2", "v2");
        request.setVersion(HttpVersion.HTTP_2);

        final BasicRequestBuilder builder = BasicRequestBuilder.copy(request);
        Assertions.assertEquals("GET", builder.getMethod());
        Assertions.assertEquals("https", builder.getScheme());
        Assertions.assertEquals(new URIAuthority("host", 3456), builder.getAuthority());
        Assertions.assertEquals("/stuff?blah", builder.getPath());
        Assertions.assertEquals(HttpVersion.HTTP_2, builder.getVersion());
        assertThat(builder.getHeaders(), HeadersMatcher.same(
                new BasicHeader("h1", "v1"), new BasicHeader("h1", "v2"), new BasicHeader("h2", "v2")));
    }

    @Test
    void testIDNIntegration() {
        final String url = "http://müller.example.com:8080/path";
        final HttpRequest request = new BasicHttpRequest(Method.GET, URI.create(url));
        assertEquals(new URIAuthority("xn--mller-kva.example.com",8080), request.getAuthority());
    }

}
