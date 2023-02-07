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
package org.apache.hc.core5.http.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.net.URIAuthority;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


public class ViaRequestTest {

    @Test
    public void testViaRequestGenerated() throws Exception {

        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        request.setAuthority(new URIAuthority("somehost", 8888));
        context.setProtocolVersion(HttpVersion.HTTP_1_1);
        final ViaRequest interceptor = new ViaRequest();
        interceptor.process(request, request.getEntity(), context);

        assertEquals(request.getHeader(HttpHeaders.VIA).getName(), HttpHeaders.VIA);
        assertNotNull(request.getHeader(HttpHeaders.VIA));
        assertEquals(request.getHeader(HttpHeaders.VIA).getValue(), "HTTP 1.1 somehost:8888");

    }

    @Test
    public void testViaRequestGeneratedWithOutPort() throws Exception {

        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        request.setAuthority(new URIAuthority("somehost"));
        context.setProtocolVersion(HttpVersion.HTTP_1_1);
        final ViaRequest interceptor = new ViaRequest();
        interceptor.process(request, request.getEntity(), context);

        assertEquals(request.getHeader(HttpHeaders.VIA).getName(), HttpHeaders.VIA);
        assertNotNull(request.getHeader(HttpHeaders.VIA));
        assertEquals(request.getHeader(HttpHeaders.VIA).getValue(), "HTTP 1.1 somehost");
    }


        @Test
    public void testViaRequestInvalidHttpVersion() {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        context.setProtocolVersion(HttpVersion.HTTP_0_9);
        request.setAuthority(new URIAuthority("somehost", 8888));

        final HttpRequestInterceptor interceptor = ViaRequest.INSTANCE;
        Assertions.assertThrows(ProtocolException.class, () ->
                interceptor.process(request, request.getEntity(), context));
    }

    @Test
    public void testViaRequestInvalidAuthority() {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        context.setProtocolVersion(HttpVersion.HTTP_1_1);

        final HttpRequestInterceptor interceptor = ViaRequest.INSTANCE;
        Assertions.assertThrows(ProtocolException.class, () ->
                interceptor.process(request, request.getEntity(), context));
    }

    @Test
    public void testViaRequestNotCreatedAlreadyAdded() throws Exception {

        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        context.setProtocolVersion(HttpVersion.HTTP_1_1);
        request.setAuthority(new URIAuthority("somehost", 8888));
        final String viaValue = "HTTP 1.1 host:8888";
        request.setHeader(HttpHeaders.VIA, viaValue);
        final ViaRequest interceptor = new ViaRequest();
        interceptor.process(request, request.getEntity(), context);

        assertEquals(request.getHeader(HttpHeaders.VIA).getName(), HttpHeaders.VIA);
        assertNotNull(request.getHeader(HttpHeaders.VIA));
        assertEquals(request.getHeader(HttpHeaders.VIA).getValue(), viaValue);

    }
}