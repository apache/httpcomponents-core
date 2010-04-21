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

package org.apache.http.impl;

import junit.framework.TestCase;

import org.apache.http.HttpRequest;
import org.apache.http.HttpVersion;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.params.CoreProtocolPNames;

public class TestBasicRequest extends TestCase {

    public TestBasicRequest(String testName) {
        super(testName);
    }

    public void testConstructor() throws Exception {
        new BasicHttpRequest("GET", "/stuff");
        new BasicHttpRequest("GET", "/stuff", HttpVersion.HTTP_1_1);
        try {
            new BasicHttpRequest(null, "/stuff");
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
    }

    public void testRequestLine() throws Exception {
        HttpRequest request = new BasicHttpRequest("GET", "/stuff");
        request.getParams().setParameter(
                CoreProtocolPNames.PROTOCOL_VERSION, HttpVersion.HTTP_1_0);
        assertEquals("GET", request.getRequestLine().getMethod());
        assertEquals("/stuff", request.getRequestLine().getUri());
        assertEquals(HttpVersion.HTTP_1_0, request.getRequestLine().getProtocolVersion());
    }

    public void testRequestLine2() throws Exception {
        HttpRequest request = new BasicHttpRequest("GET", "/stuff", HttpVersion.HTTP_1_0);
        assertEquals("GET", request.getRequestLine().getMethod());
        assertEquals("/stuff", request.getRequestLine().getUri());
        assertEquals(HttpVersion.HTTP_1_0, request.getRequestLine().getProtocolVersion());
    }

}

