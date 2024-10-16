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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TestH2RequestTE {

    private HttpCoreContext context;

    @BeforeEach
    void setUp() {
        context = HttpCoreContext.create();
        context.setProtocolVersion(HttpVersion.HTTP_2);  // Set the protocol to HTTP/2 for tests
    }

    @Test
    void testValidTEHeaderForHttp2() throws Exception {
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        request.setHeader(HttpHeaders.TE, "trailers");

        final HttpRequestInterceptor interceptor = new H2RequestTE();
        interceptor.process(request, request.getEntity(), context);

        // Assertions
        assertNotNull(request.getHeader(HttpHeaders.TE));
        assertEquals("trailers", request.getHeader(HttpHeaders.TE).getValue());
    }

    @Test
    void testInvalidTEHeaderForHttp2() {
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        request.setHeader(HttpHeaders.TE, "trailers, deflate;q=0.5");

        final H2RequestTE interceptor = new H2RequestTE();
        // Expect a ProtocolException due to invalid value in the TE header for HTTP/2
        assertThrows(ProtocolException.class, () ->
                interceptor.process(request, request.getEntity(), context));
    }

    @Test
    void testTEHeaderWithoutTrailersForHttp2() {
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        request.setHeader(HttpHeaders.TE, "gzip;q=0.5");

        final H2RequestTE interceptor = new H2RequestTE();
        // Expect a ProtocolException because 'trailers' is not present
        assertThrows(ProtocolException.class, () ->
                interceptor.process(request, request.getEntity(), context));
    }

    @Test
    void testNoTEHeaderForHttp2() throws Exception {
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");

        final H2RequestTE interceptor = new H2RequestTE();
        interceptor.process(request, request.getEntity(), context);

        // Ensure that no TE header is present, which is valid
        assertNull(request.getHeader(HttpHeaders.TE));
    }

}