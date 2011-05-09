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

package org.apache.http.message;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.params.CoreProtocolPNames;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link Header}.
 *
 */
public class TestBasicMessages {

    @Test
    public void testDefaultResponseConstructors() {
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_0, HttpStatus.SC_BAD_REQUEST, "Bad Request");
        Assert.assertNotNull(response.getProtocolVersion());
        Assert.assertEquals(HttpVersion.HTTP_1_0, response.getProtocolVersion());
        Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());

        response = new BasicHttpResponse(new BasicStatusLine(
                HttpVersion.HTTP_1_1, HttpStatus.SC_INTERNAL_SERVER_ERROR, "whatever"));
        Assert.assertNotNull(response.getProtocolVersion());
        Assert.assertEquals(HttpVersion.HTTP_1_1, response.getProtocolVersion());
        Assert.assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getStatusLine().getStatusCode());
        Assert.assertEquals("whatever", response.getStatusLine().getReasonPhrase());
    }

    @Test
    public void testSetResponseStatus() {
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        Assert.assertNotNull(response.getProtocolVersion());
        Assert.assertNotNull(response.getStatusLine());
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        response = new BasicHttpResponse(HttpVersion.HTTP_1_0, HttpStatus.SC_BAD_REQUEST, "Bad Request");
        Assert.assertNotNull(response.getProtocolVersion());
        Assert.assertEquals(HttpVersion.HTTP_1_0, response.getProtocolVersion());
        Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());

        response = new BasicHttpResponse(new BasicStatusLine(
                HttpVersion.HTTP_1_1, HttpStatus.SC_INTERNAL_SERVER_ERROR, "whatever"));
        Assert.assertNotNull(response.getProtocolVersion());
        Assert.assertEquals(HttpVersion.HTTP_1_1, response.getProtocolVersion());
        Assert.assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getStatusLine().getStatusCode());
        Assert.assertEquals("whatever", response.getStatusLine().getReasonPhrase());

        response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        try {
            response.setStatusCode(-23);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        try {
            response.setStatusLine(null, 200);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        try {
            response.setStatusLine(null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testSetResponseEntity() {
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        Assert.assertNull(response.getEntity());

        HttpEntity entity = new BasicHttpEntity();
        response.setEntity(entity);
        Assert.assertTrue(entity == response.getEntity());
    }

    @Test
    public void testDefaultRequestConstructors() {
        HttpRequest request = new BasicHttpRequest("WHATEVER", "/");
        Assert.assertNotNull(request.getProtocolVersion());
        Assert.assertEquals("WHATEVER", request.getRequestLine().getMethod());
        Assert.assertEquals("/", request.getRequestLine().getUri());

        request = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_0);
        Assert.assertEquals(HttpVersion.HTTP_1_0, request.getProtocolVersion());
        Assert.assertEquals("GET", request.getRequestLine().getMethod());
        Assert.assertEquals("/", request.getRequestLine().getUri());

        try {
            new BasicHttpRequest(null, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            new BasicHttpRequest("GET", null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            new BasicHttpRequest(null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testDefaultEntityEnclosingRequestConstructors() {
        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("GET", "/");
        Assert.assertNotNull(request.getProtocolVersion());
        Assert.assertEquals("GET", request.getRequestLine().getMethod());
        Assert.assertEquals("/", request.getRequestLine().getUri());

        request = new BasicHttpEntityEnclosingRequest("GET", "/", HttpVersion.HTTP_1_0);
        Assert.assertEquals(HttpVersion.HTTP_1_0, request.getProtocolVersion());
        Assert.assertEquals("GET", request.getRequestLine().getMethod());
        Assert.assertEquals("/", request.getRequestLine().getUri());
    }

    @Test
    public void testSetRequestEntity() {
        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("GET", "/");
        Assert.assertNull(request.getEntity());

        HttpEntity entity = new BasicHttpEntity();
        request.setEntity(entity);
        Assert.assertTrue(entity == request.getEntity());
    }

    @Test
    public void testExpectContinue() {
        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("GET", "/");
        Assert.assertFalse(request.expectContinue());
        request.getParams().setBooleanParameter(CoreProtocolPNames.USE_EXPECT_CONTINUE, true);
        Assert.assertFalse(request.expectContinue());
        request.addHeader("Expect", "100-Continue");
        Assert.assertTrue(request.expectContinue());
    }

}

