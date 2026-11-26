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


import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSession;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.impl.bootstrap.HttpRequester;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.routing.RequestRouter;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.ssl.TLS;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.testing.SSLTestContexts;
import org.apache.hc.core5.testing.SupportedTLSProtocolProvider;
import org.apache.hc.core5.testing.extension.SecurityProviderResource;
import org.apache.hc.core5.testing.extension.classic.HttpRequesterResource;
import org.apache.hc.core5.testing.extension.classic.HttpServerResource;
import org.apache.hc.core5.util.Timeout;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.ValueSource;

abstract class ClassicTLSIntegrationTest {

    private static final Timeout TIMEOUT = Timeout.ofSeconds(30);

    @RegisterExtension
    @Order(1)
    private final SecurityProviderResource securityProviderResource;
    @Order(2)
    @RegisterExtension
    private final HttpServerResource serverResource;
    @Order(3)
    @RegisterExtension
    private final HttpRequesterResource clientResource;

    public ClassicTLSIntegrationTest(final String securityProviderName) {
        this.securityProviderResource = new SecurityProviderResource(securityProviderName);
        this.serverResource = new HttpServerResource();
        this.serverResource.configure(bootstrap -> bootstrap
                .setSocketConfig(SocketConfig.custom()
                        .setSoTimeout(TIMEOUT)
                        .build())
                .setRequestRouter(RequestRouter.<HttpRequestHandler>builder()
                        .addRoute(RequestRouter.LOCAL_AUTHORITY, "*", new EchoHandler())
                        .resolveAuthority(RequestRouter.LOCAL_AUTHORITY_RESOLVER)
                        .build()));
        this.clientResource = new HttpRequesterResource();
        this.clientResource.configure(bootstrap -> bootstrap
                .setSslContext(SSLTestContexts.createClientSSLContext())
                .setSocketConfig(SocketConfig.custom()
                        .setSoTimeout(TIMEOUT)
                        .build()));
    }

    @ParameterizedTest(name = "TLS protocol {0}")
    @ArgumentsSource(SupportedTLSProtocolProvider.class)
    void testTLSSuccess(final TLS tlsProtocol) throws Exception {
        final SSLContext serverSSLContext = SSLTestContexts.createServerSSLContext(securityProviderResource.securityProvider(), "TLS");
        serverResource.configure(bootstrap -> bootstrap
                .setSslContext(serverSSLContext)
                .setSslSetupHandler(sslParameters -> sslParameters.setProtocols(new String[]{tlsProtocol.getId()}))
        );

        final AtomicReference<SSLSession> sslSessionRef = new AtomicReference<>();
        final SSLContext clientSSLContext = SSLTestContexts.createClientSSLContext(securityProviderResource.securityProvider(), "TLS");
        clientResource.configure(bootstrap -> bootstrap
                .setSslContext(clientSSLContext)
                .setSslSetupHandler(sslParameters -> sslParameters.setProtocols(new String[]{tlsProtocol.getId()}))
                .setSslSessionVerifier((endpoint, sslSession) -> sslSessionRef.set(sslSession))
        );

        final HttpServer server = serverResource.start();
        final HttpRequester requester = clientResource.start();

        final HttpCoreContext context = HttpCoreContext.create() ;
        final HttpHost target = new HttpHost("https", "localhost", server.getLocalPort());
        final ClassicHttpRequest request1 = new BasicClassicHttpRequest(Method.POST, "/stuff");
        request1.setEntity(new StringEntity("some stuff", ContentType.TEXT_PLAIN));
        try (final ClassicHttpResponse response1 = requester.execute(target, request1, TIMEOUT, context)) {
            Assertions.assertThat(response1.getCode()).isEqualTo(HttpStatus.SC_OK);
            final String body1 = EntityUtils.toString(response1.getEntity(), Integer.MAX_VALUE);
            Assertions.assertThat(body1).isEqualTo("some stuff");
        }

        final SSLSession sslSession = sslSessionRef.getAndSet(null);
        final ProtocolVersion tlsVersion = TLS.parse(sslSession.getProtocol());
        Assertions.assertThat(tlsVersion.greaterEquals(tlsProtocol.version)).isTrue();
        Assertions.assertThat(sslSession.getPeerPrincipal().getName()).isEqualTo("CN=localhost,OU=Apache HttpComponents,O=Apache Software Foundation");
    }

    @Test
    void testTLSTrustFailure() throws Exception {
        final SSLContext serverSSLContext = SSLTestContexts.createServerSSLContext(securityProviderResource.securityProvider(), "TLS");
        serverResource.configure(bootstrap -> bootstrap
                .setSslContext(serverSSLContext)
        );

        clientResource.configure(bootstrap -> bootstrap
                .setSslContext(SSLContexts.createDefault())
        );

        final HttpServer server = serverResource.start();
        final HttpRequester requester = clientResource.start();

        final HttpCoreContext context = HttpCoreContext.create() ;
        final HttpHost target = new HttpHost("https", "localhost", server.getLocalPort());
        final ClassicHttpRequest request1 = new BasicClassicHttpRequest(Method.POST, "/stuff");
        request1.setEntity(new StringEntity("some stuff", ContentType.TEXT_PLAIN));
        Assertions.assertThatThrownBy(() -> {
            try (final ClassicHttpResponse response1 = requester.execute(target, request1, TIMEOUT, context)) {
                EntityUtils.consume(response1.getEntity());
            }
        }).isInstanceOf(IOException.class);
    }

    @Test
    void testTLSClientAuthFailure() throws Exception {
        final SSLContext serverSSLContext = SSLTestContexts.createServerSSLContext(securityProviderResource.securityProvider(), "TLS");
        serverResource.configure(bootstrap -> bootstrap
                .setSslContext(serverSSLContext)
                .setSslSetupHandler(sslParameters -> sslParameters.setNeedClientAuth(true))
        );

        final SSLContext clientSSLContext = SSLTestContexts.createClientSSLContext(securityProviderResource.securityProvider(), "TLS");
        clientResource.configure(bootstrap -> bootstrap
                .setSslContext(clientSSLContext)
        );

        final HttpServer server = serverResource.start();
        final HttpRequester requester = clientResource.start();

        final HttpCoreContext context = HttpCoreContext.create() ;
        final HttpHost target = new HttpHost("https", "localhost", server.getLocalPort());
        final ClassicHttpRequest request1 = new BasicClassicHttpRequest(Method.POST, "/stuff");
        request1.setEntity(new StringEntity("some stuff", ContentType.TEXT_PLAIN));
        Assertions.assertThatThrownBy(() -> {
            try (final ClassicHttpResponse response1 = requester.execute(target, request1, TIMEOUT, context)) {
                EntityUtils.consume(response1.getEntity());
            }
        }).isInstanceOf(IOException.class);
    }

    @Test
    void testTLSClientAuthSuccess() throws Exception {
        final SSLContext serverSSLContext = SSLTestContexts.createServerSSLContext(securityProviderResource.securityProvider(), "TLS");
        serverResource.configure(bootstrap -> bootstrap
                .setSslContext(serverSSLContext)
                .setSslSetupHandler(sslParameters -> sslParameters.setNeedClientAuth(true))
        );

        final SSLContext clientSSLContext = SSLTestContexts.createClientSSLContextWithKeyMaterial(securityProviderResource.securityProvider(), "TLS");
        clientResource.configure(bootstrap -> bootstrap
                .setSslContext(clientSSLContext)
        );

        final HttpServer server = serverResource.start();
        final HttpRequester requester = clientResource.start();

        final HttpCoreContext context = HttpCoreContext.create() ;
        final HttpHost target = new HttpHost("https", "localhost", server.getLocalPort());
        final ClassicHttpRequest request1 = new BasicClassicHttpRequest(Method.POST, "/stuff");
        request1.setEntity(new StringEntity("some stuff", ContentType.TEXT_PLAIN));
        try (final ClassicHttpResponse response1 = requester.execute(target, request1, TIMEOUT, context)) {
            Assertions.assertThat(response1.getCode()).isEqualTo(HttpStatus.SC_OK);
            EntityUtils.consume(response1.getEntity());
        }
    }

    @Test
    void testSSLDisabledByDefault() throws Exception {
        final SSLContext serverSSLContext = SSLTestContexts.createServerSSLContext(securityProviderResource.securityProvider(), "TLS");
        serverResource.configure(bootstrap -> bootstrap
                .setSslContext(serverSSLContext)
                .setSslSetupHandler(sslParameters -> sslParameters.setProtocols(new String[]{"SSLv3"}))
        );

        final SSLContext clientSSLContext = SSLTestContexts.createClientSSLContext(securityProviderResource.securityProvider(), "TLS");
        clientResource.configure(bootstrap -> bootstrap
                .setSslContext(clientSSLContext)
        );

        final HttpServer server = serverResource.start();
        final HttpRequester requester = clientResource.start();

        final HttpCoreContext context = HttpCoreContext.create() ;
        final HttpHost target = new HttpHost("https", "localhost", server.getLocalPort());
        final ClassicHttpRequest request1 = new BasicClassicHttpRequest(Method.POST, "/stuff");
        request1.setEntity(new StringEntity("some stuff", ContentType.TEXT_PLAIN));
        Assertions.assertThatThrownBy(() -> {
            try (final ClassicHttpResponse response1 = requester.execute(target, request1, TIMEOUT, context)) {
                EntityUtils.consume(response1.getEntity());
            }
        }).isInstanceOf(IOException.class);
    }

    @ParameterizedTest(name = "cipher {0}")
    @ValueSource(strings = {
            "SSL_RSA_WITH_RC4_128_SHA",
            "SSL_RSA_WITH_3DES_EDE_CBC_SHA",
            "TLS_DH_anon_WITH_AES_128_CBC_SHA",
            "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA",
            "SSL_RSA_WITH_NULL_SHA",
            "SSL_RSA_WITH_3DES_EDE_CBC_SHA",
            "TLS_ECDHE_ECDSA_WITH_RC4_128_SHA",
            "TLS_ECDH_ECDSA_WITH_3DES_EDE_CBC_SHA",
            "TLS_DH_anon_WITH_AES_256_GCM_SHA384",
            "TLS_ECDH_anon_WITH_AES_256_CBC_SHA",
            "TLS_RSA_WITH_NULL_SHA256",
            "SSL_RSA_EXPORT_WITH_RC4_40_MD5",
            "SSL_DH_anon_EXPORT_WITH_RC4_40_MD5",
            "TLS_KRB5_EXPORT_WITH_RC4_40_SHA",
            "SSL_RSA_EXPORT_WITH_RC2_CBC_40_MD5"
    })
    void testWeakCipherDisabledByDefault(final String cipher) throws Exception {
        final SSLContext serverSSLContext = SSLTestContexts.createServerSSLContext(securityProviderResource.securityProvider(), "TLS");
        serverResource.configure(bootstrap -> bootstrap
                .setSslContext(serverSSLContext)
                .setSslSetupHandler(sslParameters -> sslParameters.setCipherSuites(new String[]{cipher}))
        );

        final SSLContext clientSSLContext = SSLTestContexts.createClientSSLContext(securityProviderResource.securityProvider(), "TLS");
        clientResource.configure(bootstrap -> bootstrap
                .setSslContext(clientSSLContext)
        );

        final HttpServer server = serverResource.start();
        final HttpRequester requester = clientResource.start();

        final HttpCoreContext context = HttpCoreContext.create();
        final HttpHost target = new HttpHost("https", "localhost", server.getLocalPort());
        final ClassicHttpRequest request1 = new BasicClassicHttpRequest(Method.POST, "/stuff");
        request1.setEntity(new StringEntity("some stuff", ContentType.TEXT_PLAIN));
        Assertions.assertThatThrownBy(() -> {
            try (final ClassicHttpResponse response1 = requester.execute(target, request1, TIMEOUT, context)) {
                EntityUtils.consume(response1.getEntity());
            }
        }).isInstanceOf(IOException.class);
    }

    @Test
    void testHostNameVerification() throws Exception {
        final SSLContext serverSSLContext = SSLTestContexts.createServerSSLContext(securityProviderResource.securityProvider(), "TLS");
        serverResource.configure(bootstrap -> bootstrap
                .setSslContext(serverSSLContext)
        );

        final SSLContext clientSSLContext = SSLTestContexts.createClientSSLContext(securityProviderResource.securityProvider(), "TLS");
        clientResource.configure(bootstrap -> bootstrap
                .setSslContext(clientSSLContext)
        );

        final HttpServer server = serverResource.start();
        final HttpRequester requester = clientResource.start();

        final HttpCoreContext context = HttpCoreContext.create();
        final HttpHost target1 = new HttpHost("https", InetAddress.getLoopbackAddress(), "localhost", server.getLocalPort());
        final ClassicHttpRequest request1 = new BasicClassicHttpRequest(Method.POST, "/stuff");
        request1.setEntity(new StringEntity("some stuff", ContentType.TEXT_PLAIN));
        try (final ClassicHttpResponse response1 = requester.execute(target1, request1, TIMEOUT, context)) {
            EntityUtils.consume(response1.getEntity());
        }

        Assertions.assertThatThrownBy(() -> {
            final HttpHost target2 = new HttpHost("https", InetAddress.getLoopbackAddress(), "some-other-host", server.getLocalPort());
            final ClassicHttpRequest request2 = new BasicClassicHttpRequest(Method.POST, "/stuff");
            request2.setEntity(new StringEntity("some stuff", ContentType.TEXT_PLAIN));
            try (final ClassicHttpResponse response2 = requester.execute(target2, request2, TIMEOUT, context)) {
                EntityUtils.consume(response2.getEntity());
            }
        }).isInstanceOf(SSLHandshakeException.class);
    }

}
