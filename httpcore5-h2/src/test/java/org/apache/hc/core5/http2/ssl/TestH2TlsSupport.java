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
package org.apache.hc.core5.http2.ssl;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.reactor.ssl.SSLSessionInitializer;
import org.apache.hc.core5.ssl.SSLContexts;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TestH2TlsSupport {

    @Test
    void selectApplicationProtocolsDefault() {
        Assertions.assertThat(H2TlsSupport.selectApplicationProtocols(null))
                .containsExactly(new String[]{ApplicationProtocol.HTTP_2.id, ApplicationProtocol.HTTP_1_1.id});
    }

    @Test
    void selectApplicationProtocolsForced() {
        Assertions.assertThat(H2TlsSupport.selectApplicationProtocols(HttpVersionPolicy.FORCE_HTTP_2))
                .containsExactly(new String[]{ApplicationProtocol.HTTP_2.id});
        Assertions.assertThat(H2TlsSupport.selectApplicationProtocols(HttpVersionPolicy.FORCE_HTTP_1))
                .containsExactly(new String[]{ApplicationProtocol.HTTP_1_1.id});
    }

    @Test
    void enforceRequirementsSetsApplicationProtocols() {
        final SSLParameters sslParameters = new SSLParameters(
                new String[] {"TLSv1.3", "TLSv1.2"},
                new String[] {"TLS_AES_128_GCM_SHA256"});

        H2TlsSupport.enforceRequirements(HttpVersionPolicy.FORCE_HTTP_2, sslParameters);

        Assertions.assertThat(sslParameters.getApplicationProtocols())
                .containsExactly(new String[]{ApplicationProtocol.HTTP_2.id});
    }

    @Test
    void enforceRequirementsInitializerInvoked() throws Exception {
        final SSLContext sslContext = SSLContexts.createDefault();
        final SSLSessionInitializer[] called = new SSLSessionInitializer[1];
        final SSLSessionInitializer[] holder = new SSLSessionInitializer[1];
        holder[0] = (endpoint, sslEngine) -> called[0] = holder[0];
        final SSLSessionInitializer initializer = holder[0];

        final SSLSessionInitializer enforcing = H2TlsSupport.enforceRequirements(
                HttpVersionPolicy.FORCE_HTTP_1, initializer);
        enforcing.initialize(null, sslContext.createSSLEngine());

        Assertions.assertThat(called[0]).isSameAs(initializer);
    }

}
