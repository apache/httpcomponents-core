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

import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
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
        HttpResponse response = new BasicHttpResponse(HttpStatus.SC_BAD_REQUEST, "Bad Request");
        Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getCode());

        response = new BasicHttpResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR, "whatever");
        Assert.assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getCode());
        Assert.assertEquals("whatever", response.getReasonPhrase());
    }

    @Test
    public void testSetResponseStatus() {
        HttpResponse response = new BasicHttpResponse(200, "OK");
        Assert.assertNotNull(response.getCode());
        Assert.assertEquals(200, response.getCode());

        response = new BasicHttpResponse(HttpStatus.SC_BAD_REQUEST, "Bad Request");
        Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getCode());

        response = new BasicHttpResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR, "whatever");
        Assert.assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getCode());
        Assert.assertEquals("whatever", response.getReasonPhrase());

        response = new BasicHttpResponse(HttpStatus.SC_OK, "OK");
        try {
            response.setCode(-23);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testDefaultRequestConstructors() {
        HttpRequest request = new BasicHttpRequest("WHATEVER", "/");
        Assert.assertEquals("WHATEVER", request.getMethod());
        Assert.assertEquals("/", request.getPath());

        request = new BasicHttpRequest("GET", "/");
        Assert.assertEquals("GET", request.getMethod());
        Assert.assertEquals("/", request.getPath());

        try {
            new BasicHttpRequest("GET", (URI) null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
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
        Assert.assertEquals(null, response.getReasonPhrase());
    }

    @Test
    public void testResponseInvalidStatusCode() {
        try {
            new BasicHttpResponse(-200, "OK");
            Assert.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException expected) {
        }
        final BasicHttpResponse response = new BasicHttpResponse(200, "OK");
        try {
            response.setCode(-1);
            Assert.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException expected) {
        }
    }

    @Test
    public void testRequestBasics() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/stuff");
        Assert.assertEquals("GET", request.getMethod());
        Assert.assertEquals("/stuff", request.getPath());
        Assert.assertEquals(null, request.getAuthority());
        Assert.assertEquals(new URI("/stuff"), request.getUri());
    }

    @Test
    public void testRequestWithRelativeURI() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", new URI("/stuff"));
        Assert.assertEquals("GET", request.getMethod());
        Assert.assertEquals("/stuff", request.getPath());
        Assert.assertEquals(null, request.getAuthority());
        Assert.assertEquals(new URI("/stuff"), request.getUri());
    }

    @Test
    public void testRequestWithAbsoluteURI() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", new URI("https://host:9443/stuff?param=value"));
        Assert.assertEquals("GET", request.getMethod());
        Assert.assertEquals("/stuff?param=value", request.getPath());
        Assert.assertEquals(new URIAuthority("host", 9443), request.getAuthority());
        Assert.assertEquals("https", request.getScheme());
        Assert.assertEquals(new URI("https://host:9443/stuff?param=value"), request.getUri());
    }

    @Test
    public void testRequestWithNoPath() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", new URI("http://host"));
        Assert.assertEquals("GET", request.getMethod());
        Assert.assertEquals("/", request.getPath());
        Assert.assertEquals(new URIAuthority("host"), request.getAuthority());
        Assert.assertEquals("http", request.getScheme());
        Assert.assertEquals(new URI("http://host/"), request.getUri());
    }

    @Test
    public void testRequestWithUserInfo() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", new URI("https://user:pwd@host:9443/stuff?param=value"));
        Assert.assertEquals("GET", request.getMethod());
        Assert.assertEquals("/stuff?param=value", request.getPath());
        Assert.assertEquals(new URIAuthority("user:pwd", "host", 9443), request.getAuthority());
        Assert.assertEquals("https", request.getScheme());
        Assert.assertEquals(new URI("https://host:9443/stuff?param=value"), request.getUri());
    }

}

