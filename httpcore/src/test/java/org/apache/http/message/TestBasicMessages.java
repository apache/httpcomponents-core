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

import junit.framework.TestCase;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.params.CoreProtocolPNames;

/**
 * Unit tests for {@link Header}.
 *
 */
public class TestBasicMessages extends TestCase {

    public TestBasicMessages(String testName) {
        super(testName);
    }

    public void testDefaultResponseConstructors() {
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_0, HttpStatus.SC_BAD_REQUEST, "Bad Request");
        assertNotNull(response.getProtocolVersion());
        assertEquals(HttpVersion.HTTP_1_0, response.getProtocolVersion());
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());

        response = new BasicHttpResponse(new BasicStatusLine(
                HttpVersion.HTTP_1_1, HttpStatus.SC_INTERNAL_SERVER_ERROR, "whatever"));
        assertNotNull(response.getProtocolVersion());
        assertEquals(HttpVersion.HTTP_1_1, response.getProtocolVersion());
        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getStatusLine().getStatusCode());
        assertEquals("whatever", response.getStatusLine().getReasonPhrase());
    }

    public void testSetResponseStatus() {
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        assertNotNull(response.getProtocolVersion());
        assertNotNull(response.getStatusLine());
        assertEquals(200, response.getStatusLine().getStatusCode());

        response = new BasicHttpResponse(HttpVersion.HTTP_1_0, HttpStatus.SC_BAD_REQUEST, "Bad Request");
        assertNotNull(response.getProtocolVersion());
        assertEquals(HttpVersion.HTTP_1_0, response.getProtocolVersion());
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());

        response = new BasicHttpResponse(new BasicStatusLine(
                HttpVersion.HTTP_1_1, HttpStatus.SC_INTERNAL_SERVER_ERROR, "whatever"));
        assertNotNull(response.getProtocolVersion());
        assertEquals(HttpVersion.HTTP_1_1, response.getProtocolVersion());
        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getStatusLine().getStatusCode());
        assertEquals("whatever", response.getStatusLine().getReasonPhrase());

        response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        try {
            response.setStatusCode(-23);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        try {
            response.setStatusLine(null, 200);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        try {
            response.setStatusLine(null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    public void testSetResponseEntity() {
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        assertNull(response.getEntity());

        HttpEntity entity = new BasicHttpEntity();
        response.setEntity(entity);
        assertTrue(entity == response.getEntity());
    }

    public void testDefaultRequestConstructors() {
        HttpRequest request = new BasicHttpRequest("WHATEVER", "/");
        assertNotNull(request.getProtocolVersion());
        assertEquals("WHATEVER", request.getRequestLine().getMethod());
        assertEquals("/", request.getRequestLine().getUri());

        request = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_0);
        assertEquals(HttpVersion.HTTP_1_0, request.getProtocolVersion());
        assertEquals("GET", request.getRequestLine().getMethod());
        assertEquals("/", request.getRequestLine().getUri());

        try {
            new BasicHttpRequest(null, null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            new BasicHttpRequest("GET", null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            new BasicHttpRequest(null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    public void testDefaultEntityEnclosingRequestConstructors() {
        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("GET", "/");
        assertNotNull(request.getProtocolVersion());
        assertEquals("GET", request.getRequestLine().getMethod());
        assertEquals("/", request.getRequestLine().getUri());

        request = new BasicHttpEntityEnclosingRequest("GET", "/", HttpVersion.HTTP_1_0);
        assertEquals(HttpVersion.HTTP_1_0, request.getProtocolVersion());
        assertEquals("GET", request.getRequestLine().getMethod());
        assertEquals("/", request.getRequestLine().getUri());
    }

    public void testSetRequestEntity() {
        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("GET", "/");
        assertNull(request.getEntity());

        HttpEntity entity = new BasicHttpEntity();
        request.setEntity(entity);
        assertTrue(entity == request.getEntity());
    }

    public void testExpectContinue() {
        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("GET", "/");
        assertFalse(request.expectContinue());
        request.getParams().setBooleanParameter(CoreProtocolPNames.USE_EXPECT_CONTINUE, true);
        assertFalse(request.expectContinue());
        request.addHeader("Expect", "100-Continue");
        assertTrue(request.expectContinue());
    }

}

