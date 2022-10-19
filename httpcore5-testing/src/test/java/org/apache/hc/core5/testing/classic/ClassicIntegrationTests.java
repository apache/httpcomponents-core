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

package org.apache.hc.core5.testing.classic;

import org.apache.hc.core5.http.URIScheme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

public class ClassicIntegrationTests {

    @Nested
    @DisplayName("Core transport")
    public class CoreTransport extends ClassicHttp1CoreTransportTest {

        public CoreTransport() {
            super(URIScheme.HTTP);
        }

    }

    @Nested
    @DisplayName("Core transport (TLS)")
    public class CoreTransportTls extends ClassicHttp1CoreTransportTest {

        public CoreTransportTls() {
            super(URIScheme.HTTPS);
        }

    }

    @Nested
    @DisplayName("Authentication")
    public class Authentication extends ClassicAuthenticationTest {

        public Authentication() {
            super(false);
        }

    }

    @Nested
    @DisplayName("Authentication (immediate response)")
    public class AuthenticationImmediateResponse extends ClassicAuthenticationTest {

        public AuthenticationImmediateResponse() {
            super(true);
        }

    }

    @Nested
    @DisplayName("Out-of-order response monitoring")
    public class MonitoringResponseOutOfOrderStrategy extends MonitoringResponseOutOfOrderStrategyIntegrationTest {

        public MonitoringResponseOutOfOrderStrategy() {
            super(URIScheme.HTTP);
        }

    }

    @Nested
    @DisplayName("Out-of-order response monitoring (TLS)")
    public class MonitoringResponseOutOfOrderStrategyTls extends MonitoringResponseOutOfOrderStrategyIntegrationTest {

        public MonitoringResponseOutOfOrderStrategyTls() {
            super(URIScheme.HTTPS);
        }

    }

    @Nested
    @DisplayName("Server filters")
    public class HttpFilters extends ClassicServerBootstrapFilterTest {

        public HttpFilters() {
            super(URIScheme.HTTP);
        }

    }

    @Nested
    @DisplayName("Core transport (SOCKS)")
    public class CoreTransportSocksProxy extends ClassicHttp1SocksProxyCoreTransportTest {

        public CoreTransportSocksProxy() {
            super(URIScheme.HTTP);
        }

    }

    @Nested
    @DisplayName("Core transport (TLS, SOCKS)")
    public class CoreTransportSocksProxyTls extends ClassicHttp1SocksProxyCoreTransportTest {

        public CoreTransportSocksProxyTls() {
            super(URIScheme.HTTPS);
        }

    }

}
