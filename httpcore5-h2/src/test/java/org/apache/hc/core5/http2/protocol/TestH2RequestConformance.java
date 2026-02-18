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

import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestH2RequestConformance {

    @Test
    void testTEAbsent() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        H2RequestConformance.INSTANCE.process(request, null, HttpCoreContext.create());
    }

    @Test
    void testTESingleTrailers() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        request.addHeader(HttpHeaders.TE, "trailers");
        H2RequestConformance.INSTANCE.process(request, null, HttpCoreContext.create());
    }

    @Test
    void testTECombinedTokensRejected() {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        request.addHeader(HttpHeaders.TE, "trailers, gzip");

        Assertions.assertThrows(ProtocolException.class,
                () -> H2RequestConformance.INSTANCE.process(request, null, HttpCoreContext.create()));
    }

    @Test
    void testTEMultipleHeadersSecondIllegalRejected() {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        request.addHeader(HttpHeaders.TE, "trailers");
        request.addHeader(HttpHeaders.TE, "gzip");

        Assertions.assertThrows(ProtocolException.class,
                () -> H2RequestConformance.INSTANCE.process(request, null, HttpCoreContext.create()));
    }

    @Test
    void testTEMultipleHeadersAllTrailersAccepted() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        request.addHeader(HttpHeaders.TE, "trailers");
        request.addHeader(HttpHeaders.TE, "trailers");

        H2RequestConformance.INSTANCE.process(request, null, HttpCoreContext.create());
    }

    @Test
    void testTEWhitespaceAccepted() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        request.addHeader(HttpHeaders.TE, "  trailers \t");

        H2RequestConformance.INSTANCE.process(request, null, HttpCoreContext.create());
    }

    @Test
    void testTESingleTrailersAllowed() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        request.addHeader(HttpHeaders.TE, "trailers");
        H2RequestConformance.INSTANCE.process(request, null, HttpCoreContext.create());
    }

    @Test
    void testTECommaListRejected() {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        request.addHeader(HttpHeaders.TE, "trailers, deflate;q=0.5");

        Assertions.assertThrows(ProtocolException.class,
                () -> H2RequestConformance.INSTANCE.process(request, null, HttpCoreContext.create()));
    }


    @Test
    void testTEMultipleHeadersAllTrailersAllowed() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        request.addHeader(HttpHeaders.TE, "trailers");
        request.addHeader(HttpHeaders.TE, "trailers");

        H2RequestConformance.INSTANCE.process(request, null, HttpCoreContext.create());
    }


    @Test
    void testTEEmptyValueRejected() {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        request.addHeader(HttpHeaders.TE, "");

        Assertions.assertThrows(ProtocolException.class,
                () -> H2RequestConformance.INSTANCE.process(request, null, HttpCoreContext.create()));
    }
}
