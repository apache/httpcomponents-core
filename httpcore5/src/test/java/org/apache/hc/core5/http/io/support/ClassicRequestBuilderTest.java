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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.net.URIBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ClassicRequestBuilderTest {

    @Test
    void constructor() throws UnknownHostException, URISyntaxException {
        final ClassicRequestBuilder classicRequestBuilder = new ClassicRequestBuilder(Method.HEAD);
        assertEquals(Method.HEAD.name(), classicRequestBuilder.getMethod());

        final ClassicRequestBuilder classicRequestBuilder2 = new ClassicRequestBuilder(Method.HEAD.name());
        assertEquals(Method.HEAD.name(), classicRequestBuilder2.getMethod());

        final ClassicRequestBuilder classicRequestBuilder3 = new ClassicRequestBuilder(Method.HEAD.name(), URIBuilder.localhost().build());
        assertEquals(Method.HEAD.name(), classicRequestBuilder3.getMethod());
        assertEquals(URIBuilder.localhost().getHost(), classicRequestBuilder3.getAuthority().getHostName());

        final ClassicRequestBuilder classicRequestBuilder4 = new ClassicRequestBuilder(Method.HEAD, URIBuilder.localhost().build());
        assertEquals(Method.HEAD.name(), classicRequestBuilder4.getMethod());
        assertEquals(URIBuilder.localhost().getHost(), classicRequestBuilder4.getAuthority().getHostName());

        final ClassicRequestBuilder classicRequestBuilder5 = new ClassicRequestBuilder(Method.HEAD, "/localhost");
        assertEquals(Method.HEAD.name(), classicRequestBuilder5.getMethod());
        assertEquals("/localhost", classicRequestBuilder5.getPath());

        final ClassicRequestBuilder classicRequestBuilder6 = new ClassicRequestBuilder(Method.HEAD.name(), "/localhost");
        assertEquals(Method.HEAD.name(), classicRequestBuilder6.getMethod());
        assertEquals("/localhost", classicRequestBuilder6.getPath());
    }

    @Test
    public void create() {
        final ClassicHttpRequest classicHttpRequest = ClassicRequestBuilder.create(Method.HEAD.name()).build();
        assertEquals(Method.HEAD.name(), classicHttpRequest.getMethod());
    }

    @Test
    public void get() throws UnknownHostException, URISyntaxException {
        final ClassicRequestBuilder classicRequestBuilder = ClassicRequestBuilder.get();
        assertEquals(Method.GET.name(), classicRequestBuilder.getMethod());

        final ClassicRequestBuilder classicRequestBuilder1 = ClassicRequestBuilder.get(URIBuilder.localhost().build());
        assertEquals(Method.GET.name(), classicRequestBuilder1.getMethod());

        final ClassicRequestBuilder classicRequestBuilder3 = ClassicRequestBuilder.get("/localhost");
        assertEquals(Method.GET.name(), classicRequestBuilder3.getMethod());
        assertEquals("/localhost", classicRequestBuilder3.getPath());
    }

    @Test
    public void head() throws UnknownHostException, URISyntaxException {
        final ClassicRequestBuilder classicRequestBuilder = ClassicRequestBuilder.head();
        assertEquals(Method.HEAD.name(), classicRequestBuilder.getMethod());

        final ClassicRequestBuilder classicRequestBuilder1 = ClassicRequestBuilder.head(URIBuilder.localhost().build());
        assertEquals(Method.HEAD.name(), classicRequestBuilder1.getMethod());

        final ClassicRequestBuilder classicRequestBuilder3 = ClassicRequestBuilder.head("/localhost");
        assertEquals(Method.HEAD.name(), classicRequestBuilder3.getMethod());
        assertEquals("/localhost", classicRequestBuilder3.getPath());
    }

    @Test
    public void patch() throws UnknownHostException, URISyntaxException {
        final ClassicRequestBuilder classicRequestBuilder = ClassicRequestBuilder.patch();
        assertEquals(Method.PATCH.name(), classicRequestBuilder.getMethod());

        final ClassicRequestBuilder classicRequestBuilder1 = ClassicRequestBuilder.patch(URIBuilder.localhost().build());
        assertEquals(Method.PATCH.name(), classicRequestBuilder1.getMethod());

        final ClassicRequestBuilder classicRequestBuilder3 = ClassicRequestBuilder.patch("/localhost");
        assertEquals(Method.PATCH.name(), classicRequestBuilder3.getMethod());
        assertEquals("/localhost", classicRequestBuilder3.getPath());
    }

    @Test
    public void post() throws UnknownHostException, URISyntaxException {
        final ClassicRequestBuilder classicRequestBuilder = ClassicRequestBuilder.post();
        assertEquals(Method.POST.name(), classicRequestBuilder.getMethod());

        final ClassicRequestBuilder classicRequestBuilder1 = ClassicRequestBuilder.post(URIBuilder.localhost().build());
        assertEquals(Method.POST.name(), classicRequestBuilder1.getMethod());

        final ClassicRequestBuilder classicRequestBuilder3 = ClassicRequestBuilder.post("/localhost");
        assertEquals(Method.POST.name(), classicRequestBuilder3.getMethod());
        assertEquals("/localhost", classicRequestBuilder3.getPath());
    }

    @Test
    public void put() throws UnknownHostException, URISyntaxException {
        final ClassicRequestBuilder classicRequestBuilder = ClassicRequestBuilder.put();
        assertEquals(Method.PUT.name(), classicRequestBuilder.getMethod());

        final ClassicRequestBuilder classicRequestBuilder1 = ClassicRequestBuilder.put(URIBuilder.localhost().build());
        assertEquals(Method.PUT.name(), classicRequestBuilder1.getMethod());

        final ClassicRequestBuilder classicRequestBuilder3 = ClassicRequestBuilder.put("/localhost");
        assertEquals(Method.PUT.name(), classicRequestBuilder3.getMethod());
        assertEquals("/localhost", classicRequestBuilder3.getPath());
    }

    @Test
    public void delete() throws UnknownHostException, URISyntaxException {
        final ClassicRequestBuilder classicRequestBuilder = ClassicRequestBuilder.delete();
        assertEquals(Method.DELETE.name(), classicRequestBuilder.getMethod());

        final ClassicRequestBuilder classicRequestBuilder1 = ClassicRequestBuilder.delete(URIBuilder.localhost().build());
        assertEquals(Method.DELETE.name(), classicRequestBuilder1.getMethod());

        final ClassicRequestBuilder classicRequestBuilder3 = ClassicRequestBuilder.delete("/localhost");
        assertEquals(Method.DELETE.name(), classicRequestBuilder3.getMethod());
        assertEquals("/localhost", classicRequestBuilder3.getPath());
    }

    @Test
    public void trace() throws UnknownHostException, URISyntaxException {
        final ClassicRequestBuilder classicRequestBuilder = ClassicRequestBuilder.trace();
        assertEquals(Method.TRACE.name(), classicRequestBuilder.getMethod());

        final ClassicRequestBuilder classicRequestBuilder1 = ClassicRequestBuilder.trace(URIBuilder.localhost().build());
        assertEquals(Method.TRACE.name(), classicRequestBuilder1.getMethod());

        final ClassicRequestBuilder classicRequestBuilder3 = ClassicRequestBuilder.trace("/localhost");
        assertEquals(Method.TRACE.name(), classicRequestBuilder3.getMethod());
        assertEquals("/localhost", classicRequestBuilder3.getPath());
    }

    @Test
    public void option() throws UnknownHostException, URISyntaxException {
        final ClassicRequestBuilder classicRequestBuilder = ClassicRequestBuilder.options();
        assertEquals(Method.OPTIONS.name(), classicRequestBuilder.getMethod());

        final ClassicRequestBuilder classicRequestBuilder1 = ClassicRequestBuilder.options(URIBuilder.localhost().build());
        assertEquals(Method.OPTIONS.name(), classicRequestBuilder1.getMethod());

        final ClassicRequestBuilder classicRequestBuilder3 = ClassicRequestBuilder.options("/localhost");
        assertEquals(Method.OPTIONS.name(), classicRequestBuilder3.getMethod());
        assertEquals("/localhost", classicRequestBuilder3.getPath());
    }

    @Test
    public void builder() {
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

        assertAll("Should return address of Oracle's headquarter",
                () -> assertNotNull(classicHttpRequest.getEntity()),
                () -> assertEquals(Method.GET.name(), classicHttpRequest.getMethod()),
                () -> assertEquals("http", classicHttpRequest.getScheme()),
                () -> assertEquals("httpbin.org", classicHttpRequest.getAuthority().getHostName()),
                () -> assertEquals(HttpVersion.HTTP_1_1, classicHttpRequest.getVersion()),
                () -> assertEquals(4, classicHttpRequest.getHeaders().length),
                () -> assertNotNull(ClassicRequestBuilder.get().toString()),
                () -> assertEquals("http://httpbin.org/theUri?header2=blah&param1=value1&param3=value3&param4", new String(classicHttpRequest.getRequestUri().getBytes()))
        );
    }


    @Test
    public void builderTraceThrowsIllegalStateException() {
        Assertions.assertThrows(IllegalStateException.class, () ->
                ClassicRequestBuilder.trace()
                        .setVersion(HttpVersion.HTTP_1_1)
                        .setEntity(new ByteArrayEntity(new byte[10240], ContentType.TEXT_PLAIN))
                        .build());
    }

}