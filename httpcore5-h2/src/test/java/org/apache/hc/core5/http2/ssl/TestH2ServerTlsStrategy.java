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

import java.net.InetSocketAddress;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.reactor.ssl.SSLBufferMode;
import org.apache.hc.core5.reactor.ssl.SSLSessionInitializer;
import org.apache.hc.core5.reactor.ssl.SSLSessionVerifier;
import org.apache.hc.core5.reactor.ssl.TransportSecurityLayer;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

@SuppressWarnings("deprecation")
class TestH2ServerTlsStrategy {

    @Test
    void upgradeConfiguresSslParameters() throws Exception {
        final SSLContext sslContext = SSLContexts.createDefault();
        final SSLSessionVerifier verifier = Mockito.mock(SSLSessionVerifier.class);
        final H2ServerTlsStrategy strategy = new H2ServerTlsStrategy(sslContext, SSLBufferMode.STATIC, null, verifier);
        final TransportSecurityLayer tlsSession = Mockito.mock(TransportSecurityLayer.class);
        final HttpHost endpoint = new HttpHost(URIScheme.HTTPS.id, "example.com", 443);

        final ArgumentCaptor<SSLSessionInitializer> initializerCaptor = ArgumentCaptor.forClass(SSLSessionInitializer.class);

        strategy.upgrade(tlsSession, endpoint, HttpVersionPolicy.FORCE_HTTP_1, Timeout.ofSeconds(1), null);

        Mockito.verify(tlsSession).startTls(
                Mockito.eq(sslContext),
                Mockito.eq(endpoint),
                Mockito.eq(SSLBufferMode.STATIC),
                initializerCaptor.capture(),
                Mockito.eq(verifier),
                Mockito.eq(Timeout.ofSeconds(1)),
                Mockito.<FutureCallback<TransportSecurityLayer>>isNull());

        final SSLSessionInitializer initializer = initializerCaptor.getValue();
        final javax.net.ssl.SSLEngine sslEngine = sslContext.createSSLEngine();
        initializer.initialize(endpoint, sslEngine);

        final SSLParameters sslParameters = sslEngine.getSSLParameters();
        Assertions.assertArrayEquals(new String[] { ApplicationProtocol.HTTP_1_1.id },
                sslParameters.getApplicationProtocols());
    }

    @Test
    void deprecatedUpgradeRespectsSecurePorts() {
        final SSLContext sslContext = SSLContexts.createDefault();
        final H2ServerTlsStrategy strategy = new H2ServerTlsStrategy(
                sslContext,
                new org.apache.hc.core5.http.nio.ssl.FixedPortStrategy(8443));
        final TransportSecurityLayer tlsSession = Mockito.mock(TransportSecurityLayer.class);
        final HttpHost host = new HttpHost(URIScheme.HTTPS.id, "localhost", 8443);

        final boolean notUpgraded = strategy.upgrade(
                tlsSession,
                host,
                new InetSocketAddress("localhost", 8080),
                null,
                null,
                Timeout.ofSeconds(1));
        Assertions.assertFalse(notUpgraded);
        Mockito.verifyNoInteractions(tlsSession);

        final boolean upgraded = strategy.upgrade(
                tlsSession,
                host,
                new InetSocketAddress("localhost", 8443),
                null,
                null,
                Timeout.ofSeconds(1));
        Assertions.assertTrue(upgraded);
        Mockito.verify(tlsSession).startTls(
                Mockito.eq(sslContext),
                Mockito.eq(host),
                Mockito.isNull(),
                Mockito.any(),
                Mockito.isNull(),
                Mockito.eq(Timeout.ofSeconds(1)),
                Mockito.<FutureCallback<TransportSecurityLayer>>isNull());
    }

}
