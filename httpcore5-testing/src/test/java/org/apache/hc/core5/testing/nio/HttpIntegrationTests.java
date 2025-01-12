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

package org.apache.hc.core5.testing.nio;

import org.apache.hc.core5.http.URIScheme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

class HttpIntegrationTests {

    @Nested
    @DisplayName("Core transport (HTTP/1.1)")
    class CoreTransport extends Http1CoreTransportTest {

        public CoreTransport() {
            super(URIScheme.HTTP);
        }

    }

    @Nested
    @DisplayName("Core transport (HTTP/1.1, TLS)")
    class CoreTransportTls extends Http1CoreTransportTest {

        public CoreTransportTls() {
            super(URIScheme.HTTPS);
        }

    }

    @Nested
    @DisplayName("Core transport (HTTP/1.1, TLSv1.3)")
    class CoreTransportTls13 extends Http1CoreTransportTest {

        public CoreTransportTls13() {
            super(URIScheme.HTTPS, "TLSv1.3");
        }

    }

    @Nested
    @DisplayName("Core transport (H2)")
    class CoreTransportH2 extends H2CoreTransportTest {

        public CoreTransportH2() {
            super(URIScheme.HTTP);
        }

    }

    @Nested
    @DisplayName("Core transport (H2, TLS)")
    class CoreTransportH2Tls extends H2CoreTransportTest {

        public CoreTransportH2Tls() {
            super(URIScheme.HTTPS);
        }

    }

    @Nested
    @DisplayName("Core transport (H2, TLSv1.3)")
    class CoreTransportH2Tls13 extends H2CoreTransportTest {

        public CoreTransportH2Tls13() {
            super(URIScheme.HTTPS, "TLSv1.3");
        }

    }

    @Nested
    @DisplayName("Core transport (H2, multiplexing)")
    class CoreTransportH2Multiplexing extends H2CoreTransportMultiplexingTest {

        public CoreTransportH2Multiplexing() {
            super(URIScheme.HTTP);
        }

    }

    @Nested
    @DisplayName("Core transport (H2, multiplexing, TLS)")
    class CoreTransportH2MultiplexingTls extends H2CoreTransportMultiplexingTest {

        public CoreTransportH2MultiplexingTls() {
            super(URIScheme.HTTPS);
        }

    }

    @Nested
    @DisplayName("Server filters")
    class HttpFilters extends AsyncServerBootstrapFilterTest {

        public HttpFilters() {
            super();
        }

    }

    @Nested
    @DisplayName("H2 Server filters")
    class H2Filters extends H2ServerBootstrapFiltersTest {

        public H2Filters() {
            super();
        }

    }

    @Nested
    @DisplayName("Authentication")
    class Authentication extends Http1AuthenticationTest {

        public Authentication() {
            super(false);
        }

    }

    @Nested
    @DisplayName("Authentication (immediate response)")
    class AuthenticationImmediateResponse extends Http1AuthenticationTest {

        public AuthenticationImmediateResponse() {
            super(true);
        }

    }

}
