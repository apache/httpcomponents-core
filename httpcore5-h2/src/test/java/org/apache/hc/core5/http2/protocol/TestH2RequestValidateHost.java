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
package org.apache.hc.core5.http2.protocol;

import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.net.URIAuthority;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TestH2RequestValidateHost {

    @Test
    void validatesHostForHttp1() throws Exception {
        final BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        request.addHeader(HttpHeaders.HOST, "example.com");
        final HttpCoreContext context = HttpCoreContext.create();
        context.setProtocolVersion(HttpVersion.HTTP_1_1);

        H2RequestValidateHost.INSTANCE.process(request, null, context);

        Assertions.assertNotNull(request.getAuthority());
    }

    @Test
    void skipsValidationForHttp2() throws Exception {
        final BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        request.addHeader(HttpHeaders.HOST, "example.com");
        final HttpCoreContext context = HttpCoreContext.create();
        context.setProtocolVersion(HttpVersion.HTTP_2);

        H2RequestValidateHost.INSTANCE.process(request, null, context);

        Assertions.assertNull(request.getAuthority());
    }

    @Test
    void testHttp2HostMatchesAuthority() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        request.setScheme("https");
        request.setAuthority(new URIAuthority("example.com"));
        request.addHeader(HttpHeaders.HOST, "example.com");

        final HttpContext context = Mockito.mock(HttpContext.class);
        Mockito.when(context.getProtocolVersion()).thenReturn(HttpVersion.HTTP_2);

        H2RequestValidateHost.INSTANCE.process(request, (EntityDetails) null, context);
    }

    @Test
    void testHttp2HostMismatchRejected() {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        request.setScheme("https");
        request.setAuthority(new URIAuthority("example.com"));
        request.addHeader(HttpHeaders.HOST, "evil.com");

        final HttpContext context = Mockito.mock(HttpContext.class);
        Mockito.when(context.getProtocolVersion()).thenReturn(HttpVersion.HTTP_2);

        Assertions.assertThrows(ProtocolException.class, () ->
                H2RequestValidateHost.INSTANCE.process(request, null, context));
    }

    @Test
    void testHttp2HostCaseInsensitiveMatch() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        request.setScheme("https");
        request.setAuthority(new URIAuthority("example.com"));
        request.addHeader(HttpHeaders.HOST, "EXAMPLE.COM");

        final HttpContext context = Mockito.mock(HttpContext.class);
        Mockito.when(context.getProtocolVersion()).thenReturn(HttpVersion.HTTP_2);

        H2RequestValidateHost.INSTANCE.process(request, null, context);
    }

    @Test
    void testHttp2HostPortDiffersRejected() {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        request.setScheme("https");
        request.setAuthority(new URIAuthority("example.com", 443));
        request.addHeader(HttpHeaders.HOST, "example.com");

        final HttpContext context = Mockito.mock(HttpContext.class);
        Mockito.when(context.getProtocolVersion()).thenReturn(HttpVersion.HTTP_2);

        Assertions.assertThrows(ProtocolException.class, () ->
                H2RequestValidateHost.INSTANCE.process(request, null, context));
    }

}
