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
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link org.apache.hc.core5.http.HttpMessage}.
 *
 */
public class TestBasicMessages {

    @Test
    public void testDefaultResponseConstructors() {
        final HttpResponse response1 = new BasicHttpResponse(HttpStatus.SC_BAD_REQUEST, "Bad Request");
        Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response1.getCode());

        final HttpResponse response2 = new BasicHttpResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR, "whatever");
        Assert.assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response2.getCode());
        Assert.assertEquals("whatever", response2.getReasonPhrase());
    }

    @Test
    public void testSetResponseStatus() {
        final HttpResponse response1 = new BasicHttpResponse(200, "OK");
        Assert.assertNotNull(response1.getCode());
        Assert.assertEquals(200, response1.getCode());

        final HttpResponse response2 = new BasicHttpResponse(HttpStatus.SC_BAD_REQUEST, "Bad Request");
        Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response2.getCode());

        final HttpResponse response3 = new BasicHttpResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR, "whatever");
        Assert.assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response3.getCode());
        Assert.assertEquals("whatever", response3.getReasonPhrase());

        final HttpResponse response4 = new BasicHttpResponse(HttpStatus.SC_OK, "OK");
        Assert.assertThrows(IllegalArgumentException.class, () -> response4.setCode(-23));
    }

    @Test
    public void testDefaultRequestConstructors() {
        final HttpRequest request1 = new BasicHttpRequest("WHATEVER", "/");
        Assert.assertEquals("WHATEVER", request1.getMethod());
        Assert.assertEquals("/", request1.getPath());

        final HttpRequest request2 = new BasicHttpRequest(Method.GET, "/");
        Assert.assertEquals(Method.GET.name(), request2.getMethod());
        Assert.assertEquals("/", request2.getPath());

        Assert.assertThrows(NullPointerException.class, () ->
                new BasicHttpRequest(Method.GET, (URI) null));
    }

    @Test
    public void testResponseBasics() {
        final BasicHttpResponse response = new BasicHttpResponse(200, "OK");
        Assert.assertEquals(200, response.getCode());
        Assert.assertEquals("OK", response.getReasonPhrase());
    }

    @Test
    public void testResponseStatusLineMutation() {
        final BasicHttpResponse response = new BasicHttpResponse(200, "OK");
        Assert.assertEquals(200, response.getCode());
        Assert.assertEquals("OK", response.getReasonPhrase());
        response.setReasonPhrase("Kind of OK");
        Assert.assertEquals(200, response.getCode());
        Assert.assertEquals("Kind of OK", response.getReasonPhrase());
        response.setCode(299);
        Assert.assertEquals(299, response.getCode());
        Assert.assertNull(response.getReasonPhrase());
    }

    @Test
    public void testResponseInvalidStatusCode() {
        Assert.assertThrows(IllegalArgumentException.class, () -> new BasicHttpResponse(-200, "OK"));
        final BasicHttpResponse response = new BasicHttpResponse(200, "OK");
        Assert.assertThrows(IllegalArgumentException.class, () -> response.setCode(-1));
    }

    @Test
    public void testRequestBasics() throws Exception {
        final HttpRequest request = new BasicHttpRequest(Method.GET, "/stuff");
        Assert.assertEquals(Method.GET.name(), request.getMethod());
        Assert.assertEquals("/stuff", request.getPath());
        Assert.assertNull(request.getAuthority());
        Assert.assertEquals(new URI("/stuff"), request.getUri());
    }

    @Test
    public void testRequestWithRelativeURI() throws Exception {
        final HttpRequest request = new BasicHttpRequest(Method.GET, new URI("/stuff"));
        Assert.assertEquals(Method.GET.name(), request.getMethod());
        Assert.assertEquals("/stuff", request.getPath());
        Assert.assertNull(request.getAuthority());
        Assert.assertEquals(new URI("/stuff"), request.getUri());
    }

    @Test
    public void testRequestWithAbsoluteURI() throws Exception {
        final HttpRequest request = new BasicHttpRequest(Method.GET, new URI("https://host:9443/stuff?param=value"));
        Assert.assertEquals(Method.GET.name(), request.getMethod());
        Assert.assertEquals("/stuff?param=value", request.getPath());
        Assert.assertEquals(new URIAuthority("host", 9443), request.getAuthority());
        Assert.assertEquals("https", request.getScheme());
        Assert.assertEquals(new URI("https://host:9443/stuff?param=value"), request.getUri());
    }

    @Test
    public void testRequestWithAbsoluteURIAsPath() throws Exception {
        final HttpRequest request = new BasicHttpRequest(Method.GET, "https://host:9443/stuff?param=value");
        Assert.assertEquals(Method.GET.name(), request.getMethod());
        Assert.assertEquals("/stuff?param=value", request.getPath());
        Assert.assertEquals(new URIAuthority("host", 9443), request.getAuthority());
        Assert.assertEquals("https", request.getScheme());
        Assert.assertEquals(new URI("https://host:9443/stuff?param=value"), request.getUri());
    }

    @Test
    public void testRequestWithNoPath() throws Exception {
        final HttpRequest request = new BasicHttpRequest(Method.GET, new URI("http://host"));
        Assert.assertEquals(Method.GET.name(), request.getMethod());
        Assert.assertEquals("/", request.getPath());
        Assert.assertEquals(new URIAuthority("host"), request.getAuthority());
        Assert.assertEquals("http", request.getScheme());
        Assert.assertEquals(new URI("http://host/"), request.getUri());
    }

    @Test
    public void testRequestWithUserInfo() throws Exception {
        final HttpRequest request = new BasicHttpRequest(Method.GET, new URI("https://user:pwd@host:9443/stuff?param=value"));
        Assert.assertEquals(Method.GET.name(), request.getMethod());
        Assert.assertEquals("/stuff?param=value", request.getPath());
        Assert.assertEquals(new URIAuthority("user:pwd", "host", 9443), request.getAuthority());
        Assert.assertEquals("https", request.getScheme());
        Assert.assertEquals(new URI("https://host:9443/stuff?param=value"), request.getUri());
    }

    @Test
    public void testRequestWithAuthority() throws Exception {
        final HttpRequest request = new BasicHttpRequest(Method.GET, new HttpHost("http", "somehost", -1), "/stuff");
        Assert.assertEquals(Method.GET.name(), request.getMethod());
        Assert.assertEquals("/stuff", request.getPath());
        Assert.assertEquals(new URIAuthority("somehost"), request.getAuthority());
        Assert.assertEquals(new URI("http://somehost/stuff"), request.getUri());
    }

    @Test
    public void testRequestWithAuthorityRelativePath() throws Exception {
        final HttpRequest request = new BasicHttpRequest(Method.GET, new HttpHost("http", "somehost", -1), "stuff");
        Assert.assertEquals(Method.GET.name(), request.getMethod());
        Assert.assertEquals("stuff", request.getPath());
        Assert.assertEquals(new URIAuthority("somehost"), request.getAuthority());
        Assert.assertEquals(new URI("http://somehost/stuff"), request.getUri());
    }

    @Test
    public void testRequestHostWithReservedChars() throws Exception {
        final HttpRequest request = new BasicHttpRequest(Method.GET, URI.create("http://someuser%21@%21example%21.com/stuff"));
        Assert.assertEquals(Method.GET.name(), request.getMethod());
        Assert.assertEquals("/stuff", request.getPath());
        Assert.assertEquals(new URIAuthority("someuser%21", "%21example%21.com", -1), request.getAuthority());
        Assert.assertEquals(new URI("http://%21example%21.com/stuff"), request.getUri());
    }

    @Test
    public void testRequestPathWithMultipleLeadingSlashes() throws Exception {
        Assert.assertThrows(IllegalArgumentException.class, () ->
                new BasicHttpRequest(Method.GET, URI.create("http://host//stuff")));
    }

    @Test
    public void testRequestAbsoluteRequestUri() throws Exception {
        final BasicHttpRequest request = new BasicHttpRequest(Method.GET, new HttpHost("http", "somehost", -1), "stuff");
        Assert.assertEquals("stuff", request.getRequestUri());
        request.setAbsoluteRequestUri(true);
        Assert.assertEquals("http://somehost/stuff", request.getRequestUri());
    }

}

