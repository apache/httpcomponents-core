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

import org.apache.http.HttpVersion;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link BasicHttpResponse}.
 */
public class TestBasicHttpResponse {

    @Test
    public void testBasics() {
        final BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        Assert.assertEquals(HttpVersion.HTTP_1_1, response.getProtocolVersion());
        Assert.assertEquals(HttpVersion.HTTP_1_1, response.getStatusLine().getProtocolVersion());
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        Assert.assertEquals("OK", response.getStatusLine().getReasonPhrase());
    }

    @Test
    public void testStatusLineMutation() {
        final BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        Assert.assertEquals(HttpVersion.HTTP_1_1, response.getStatusLine().getProtocolVersion());
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        Assert.assertEquals("OK", response.getStatusLine().getReasonPhrase());
        response.setReasonPhrase("Kind of OK");
        Assert.assertEquals(HttpVersion.HTTP_1_1, response.getStatusLine().getProtocolVersion());
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        Assert.assertEquals("Kind of OK", response.getStatusLine().getReasonPhrase());
        response.setStatusCode(299);
        Assert.assertEquals(HttpVersion.HTTP_1_1, response.getStatusLine().getProtocolVersion());
        Assert.assertEquals(299, response.getStatusLine().getStatusCode());
        Assert.assertEquals(null, response.getStatusLine().getReasonPhrase());
        response.setStatusLine(HttpVersion.HTTP_1_0, 298);
        Assert.assertEquals(HttpVersion.HTTP_1_0, response.getStatusLine().getProtocolVersion());
        Assert.assertEquals(298, response.getStatusLine().getStatusCode());
        Assert.assertEquals(null, response.getStatusLine().getReasonPhrase());
        response.setStatusLine(HttpVersion.HTTP_1_1, 200, "OK");
        Assert.assertEquals(HttpVersion.HTTP_1_1, response.getStatusLine().getProtocolVersion());
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        Assert.assertEquals("OK", response.getStatusLine().getReasonPhrase());
        response.setStatusLine(new BasicStatusLine(HttpVersion.HTTP_1_0, 500, "Boom"));
        Assert.assertEquals(HttpVersion.HTTP_1_0, response.getStatusLine().getProtocolVersion());
        Assert.assertEquals(500, response.getStatusLine().getStatusCode());
        Assert.assertEquals("Boom", response.getStatusLine().getReasonPhrase());
    }

    @Test
    public void testInvalidStatusCode() {
        try {
            new BasicHttpResponse(HttpVersion.HTTP_1_1, -200, "OK");
            Assert.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException expected) {
        }
        final BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        try {
            response.setStatusCode(-1);
            Assert.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException expected) {
        }
        try {
            response.setStatusLine(HttpVersion.HTTP_1_1, -1);
            Assert.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException expected) {
        }
        try {
            response.setStatusLine(HttpVersion.HTTP_1_1, -1, "not ok");
            Assert.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException expected) {
        }
    }

}
