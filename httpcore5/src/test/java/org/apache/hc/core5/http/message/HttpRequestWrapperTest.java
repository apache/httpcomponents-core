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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class HttpRequestWrapperTest {

    @Test
    public void testRequestBasics() throws Exception {
        final HttpRequest request = new BasicHttpRequest(Method.GET, "/stuff");
        final HttpRequestWrapper httpRequestWrapper = new HttpRequestWrapper(request);

        Assertions.assertEquals(Method.GET.name(), httpRequestWrapper.getMethod());
        Assertions.assertEquals("/stuff", httpRequestWrapper.getPath());
        Assertions.assertNull(httpRequestWrapper.getAuthority());
        Assertions.assertEquals(new URI("/stuff"), httpRequestWrapper.getUri());
        httpRequestWrapper.setPath("/another-stuff");
        Assertions.assertEquals("/another-stuff", httpRequestWrapper.getPath());
    }

    @Test
    public void testDefaultRequestConstructors() {
        final HttpRequest request1 = new BasicHttpRequest("WHATEVER", "/");
        final HttpRequestWrapper httpRequestWrapper = new HttpRequestWrapper(request1);
        Assertions.assertEquals("WHATEVER", httpRequestWrapper.getMethod());
        Assertions.assertEquals("/", httpRequestWrapper.getPath());

        final HttpRequest request2 = new BasicHttpRequest(Method.GET, "/");
        final HttpRequestWrapper httpRequestWrapper2 = new HttpRequestWrapper(request2);
        Assertions.assertEquals(Method.GET.name(), httpRequestWrapper2.getMethod());
        Assertions.assertEquals("/", httpRequestWrapper2.getPath());

        Assertions.assertThrows(NullPointerException.class, () ->
                new BasicHttpRequest(Method.GET, (URI) null));
    }


    @Test
    public void testRequestWithRelativeURI() throws Exception {
        final HttpRequest request = new BasicHttpRequest(Method.GET, new URI("/stuff"));
        final HttpRequestWrapper httpRequestWrapper = new HttpRequestWrapper(request);
        Assertions.assertEquals(Method.GET.name(), httpRequestWrapper.getMethod());
        Assertions.assertEquals("/stuff", httpRequestWrapper.getPath());
        Assertions.assertNull(httpRequestWrapper.getAuthority());
        Assertions.assertEquals(new URI("/stuff"), httpRequestWrapper.getUri());
    }

    @Test
    public void testRequestWithAbsoluteURI() throws Exception {
        final HttpRequest request = new BasicHttpRequest(Method.GET, new URI("https://host:9443/stuff?param=value"));
        final HttpRequestWrapper httpRequestWrapper = new HttpRequestWrapper(request);
        Assertions.assertEquals(Method.GET.name(), httpRequestWrapper.getMethod());
        Assertions.assertEquals("/stuff?param=value", httpRequestWrapper.getPath());
        Assertions.assertEquals(new URIAuthority("host", 9443), httpRequestWrapper.getAuthority());
        Assertions.assertEquals("https", httpRequestWrapper.getScheme());
        Assertions.assertEquals(new URI("https://host:9443/stuff?param=value"), httpRequestWrapper.getUri());
        httpRequestWrapper.setScheme((URIScheme.HTTP.id));
        Assertions.assertEquals("http", httpRequestWrapper.getScheme());
    }

    @Test
    public void testRequestWithAbsoluteURIAsPath() throws Exception {
        final HttpRequest request = new BasicHttpRequest(Method.GET, "https://host:9443/stuff?param=value");
        final HttpRequestWrapper httpRequestWrapper = new HttpRequestWrapper(request);
        Assertions.assertEquals(Method.GET.name(), httpRequestWrapper.getMethod());
        Assertions.assertEquals("/stuff?param=value", httpRequestWrapper.getPath());
        Assertions.assertEquals(new URIAuthority("host", 9443), httpRequestWrapper.getAuthority());
        Assertions.assertEquals("https", httpRequestWrapper.getScheme());
        Assertions.assertEquals(new URI("https://host:9443/stuff?param=value"), httpRequestWrapper.getUri());
    }

    @Test
    public void testRequestWithNoPath() throws Exception {
        final HttpRequest request = new BasicHttpRequest(Method.GET, new URI("http://host"));
        final HttpRequestWrapper httpRequestWrapper = new HttpRequestWrapper(request);
        Assertions.assertEquals(Method.GET.name(), httpRequestWrapper.getMethod());
        Assertions.assertEquals("/", httpRequestWrapper.getPath());
        Assertions.assertEquals(new URIAuthority("host"), httpRequestWrapper.getAuthority());
        Assertions.assertEquals("http", httpRequestWrapper.getScheme());
        Assertions.assertEquals(new URI("http://host/"), httpRequestWrapper.getUri());
    }

    @Test
    public void testRequestWithUserInfo() throws Exception {
        final HttpRequest request = new BasicHttpRequest(Method.GET, new URI("https://user:pwd@host:9443/stuff?param=value"));
        final HttpRequestWrapper httpRequestWrapper = new HttpRequestWrapper(request);
        Assertions.assertEquals(Method.GET.name(), httpRequestWrapper.getMethod());
        Assertions.assertEquals("/stuff?param=value", httpRequestWrapper.getPath());
        Assertions.assertEquals(new URIAuthority("user:pwd", "host", 9443), httpRequestWrapper.getAuthority());
        Assertions.assertEquals("https", httpRequestWrapper.getScheme());
        Assertions.assertEquals(new URI("https://host:9443/stuff?param=value"), httpRequestWrapper.getUri());
    }

    @Test
    public void testRequestWithAuthority() throws Exception {
        final HttpRequest request = new BasicHttpRequest(Method.GET, new HttpHost("http", "somehost", -1), "/stuff");
        final HttpRequestWrapper httpRequestWrapper = new HttpRequestWrapper(request);
        Assertions.assertEquals(Method.GET.name(), httpRequestWrapper.getMethod());
        Assertions.assertEquals("/stuff", httpRequestWrapper.getPath());
        Assertions.assertEquals(new URIAuthority("somehost"), httpRequestWrapper.getAuthority());
        Assertions.assertEquals(new URI("http://somehost/stuff"), httpRequestWrapper.getUri());

        httpRequestWrapper.setAuthority(new URIAuthority("newHost"));
        Assertions.assertEquals(new URIAuthority("newHost"), httpRequestWrapper.getAuthority());

    }

    @Test
    public void testRequestWithAuthorityRelativePath() throws Exception {
        final HttpRequest request = new BasicHttpRequest(Method.GET, new HttpHost("http", "somehost", -1), "stuff");
        final HttpRequestWrapper httpRequestWrapper = new HttpRequestWrapper(request);
        Assertions.assertEquals(Method.GET.name(), httpRequestWrapper.getMethod());
        Assertions.assertEquals("stuff", httpRequestWrapper.getPath());
        Assertions.assertEquals("stuff", httpRequestWrapper.getRequestUri());
        Assertions.assertEquals(new URIAuthority("somehost"), httpRequestWrapper.getAuthority());
        Assertions.assertEquals(new URI("http://somehost/stuff"), httpRequestWrapper.getUri());
    }

    @Test
    public void testRequestHostWithReservedChars() throws Exception {
        final HttpRequest request = new BasicHttpRequest(Method.GET, URI.create("http://someuser%21@%21example%21.com/stuff"));
        final HttpRequestWrapper httpRequestWrapper = new HttpRequestWrapper(request);
        Assertions.assertEquals(Method.GET.name(), httpRequestWrapper.getMethod());
        Assertions.assertEquals("/stuff", httpRequestWrapper.getPath());
        Assertions.assertEquals(new URIAuthority("someuser%21", "%21example%21.com", -1), httpRequestWrapper.getAuthority());
        Assertions.assertEquals(new URI("http://%21example%21.com/stuff"), httpRequestWrapper.getUri());
    }
}