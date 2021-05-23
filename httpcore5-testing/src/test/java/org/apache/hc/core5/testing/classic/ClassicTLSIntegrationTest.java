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
import java.util.concurrent.atomic.AtomicReference;

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
import org.apache.hc.core5.http.impl.bootstrap.RequesterBootstrap;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.ssl.TLS;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.testing.SSLTestContexts;
import org.apache.hc.core5.util.Timeout;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;

public class ClassicTLSIntegrationTest {

    private static final Timeout TIMEOUT = Timeout.ofSeconds(30);

    private HttpServer server;

    @Rule
    public ExternalResource serverResource = new ExternalResource() {

        @Override
        protected void after() {
            if (server != null) {
                try {
                    server.close(CloseMode.IMMEDIATE);
                } catch (final Exception ignore) {
                }
            }
        }

    };

    private HttpRequester requester;

    @Rule
    public ExternalResource clientResource = new ExternalResource() {

        @Override
        protected void after() {
            if (requester != null) {
                try {
                    requester.close(CloseMode.GRACEFUL);
                } catch (final Exception ignore) {
                }
            }
        }

    };

    @Test
    public void testTLSSuccess() throws Exception {
        server = ServerBootstrap.bootstrap()
                .setSocketConfig(SocketConfig.custom()
                        .setSoTimeout(TIMEOUT)
                        .build())
                .setSslContext(SSLTestContexts.createServerSSLContext())
                .setExceptionListener(LoggingExceptionListener.INSTANCE)
                .setStreamListener(LoggingHttp1StreamListener.INSTANCE)
                .register("*", new EchoHandler())
                .create();
        server.start();

        final AtomicReference<SSLSession> sslSessionRef = new AtomicReference<>(null);

        requester = RequesterBootstrap.bootstrap()
                .setSslContext(SSLTestContexts.createClientSSLContext())
                .setSslSessionVerifier((endpoint, sslSession) -> sslSessionRef.set(sslSession))
                .setSocketConfig(SocketConfig.custom()
                        .setSoTimeout(TIMEOUT)
                        .build())
                .setStreamListener(LoggingHttp1StreamListener.INSTANCE)
                .setConnPoolListener(LoggingConnPoolListener.INSTANCE)
                .create();

        final HttpContext context = new BasicHttpContext();
        final HttpHost target = new HttpHost("https", "localhost", server.getLocalPort());
        final ClassicHttpRequest request1 = new BasicClassicHttpRequest(Method.POST, "/stuff");
        request1.setEntity(new StringEntity("some stuff", ContentType.TEXT_PLAIN));
        try (final ClassicHttpResponse response1 = requester.execute(target, request1, TIMEOUT, context)) {
            MatcherAssert.assertThat(response1.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
            final String body1 = EntityUtils.toString(response1.getEntity());
            MatcherAssert.assertThat(body1, CoreMatchers.equalTo("some stuff"));
        }

        final SSLSession sslSession = sslSessionRef.getAndSet(null);
        final ProtocolVersion tlsVersion = TLS.parse(sslSession.getProtocol());
        MatcherAssert.assertThat(tlsVersion.greaterEquals(TLS.V_1_2.version), CoreMatchers.equalTo(true));
        MatcherAssert.assertThat(sslSession.getPeerPrincipal().getName(),
                CoreMatchers.equalTo("CN=localhost,OU=Apache HttpComponents,O=Apache Software Foundation"));
    }

    @Test
    public void testTLSTrustFailure() throws Exception {
        server = ServerBootstrap.bootstrap()
                .setSocketConfig(SocketConfig.custom()
                        .setSoTimeout(TIMEOUT)
                        .build())
                .setSslContext(SSLTestContexts.createServerSSLContext())
                .setExceptionListener(LoggingExceptionListener.INSTANCE)
                .setStreamListener(LoggingHttp1StreamListener.INSTANCE)
                .register("*", new EchoHandler())
                .create();
        server.start();

        requester = RequesterBootstrap.bootstrap()
                .setSslContext(SSLContexts.createDefault())
                .setSocketConfig(SocketConfig.custom()
                        .setSoTimeout(TIMEOUT)
                        .build())
                .setStreamListener(LoggingHttp1StreamListener.INSTANCE)
                .setConnPoolListener(LoggingConnPoolListener.INSTANCE)
                .create();

        final HttpContext context = new BasicHttpContext();
        final HttpHost target = new HttpHost("https", "localhost", server.getLocalPort());
        final ClassicHttpRequest request1 = new BasicClassicHttpRequest(Method.POST, "/stuff");
        request1.setEntity(new StringEntity("some stuff", ContentType.TEXT_PLAIN));
        Assert.assertThrows(IOException.class, () -> {
            try (final ClassicHttpResponse response1 = requester.execute(target, request1, TIMEOUT, context)) {
                EntityUtils.consume(response1.getEntity());
            }
        });
    }

    @Test
    public void testTLSClientAuthFailure() throws Exception {
        server = ServerBootstrap.bootstrap()
                .setSslContext(SSLTestContexts.createClientSSLContext())
                .setSocketConfig(SocketConfig.custom()
                        .setSoTimeout(TIMEOUT)
                        .build())
                .setSslContext(SSLTestContexts.createServerSSLContext())
                .setSslSetupHandler(sslParameters -> sslParameters.setNeedClientAuth(true))
                .setExceptionListener(LoggingExceptionListener.INSTANCE)
                .setStreamListener(LoggingHttp1StreamListener.INSTANCE)
                .register("*", new EchoHandler())
                .create();
        server.start();

        requester = RequesterBootstrap.bootstrap()
                .setSslContext(SSLTestContexts.createClientSSLContext())
                .setSocketConfig(SocketConfig.custom()
                        .setSoTimeout(TIMEOUT)
                        .build())
                .setStreamListener(LoggingHttp1StreamListener.INSTANCE)
                .setConnPoolListener(LoggingConnPoolListener.INSTANCE)
                .create();

        final HttpContext context = new BasicHttpContext();
        final HttpHost target = new HttpHost("https", "localhost", server.getLocalPort());
        final ClassicHttpRequest request1 = new BasicClassicHttpRequest(Method.POST, "/stuff");
        request1.setEntity(new StringEntity("some stuff", ContentType.TEXT_PLAIN));
        Assert.assertThrows(IOException.class, () -> {
            try (final ClassicHttpResponse response1 = requester.execute(target, request1, TIMEOUT, context)) {
                EntityUtils.consume(response1.getEntity());
            }
        });
    }

    @Test
    public void testSSLDisabledByDefault() throws Exception {
        server = ServerBootstrap.bootstrap()
                .setSslContext(SSLTestContexts.createServerSSLContext())
                .setSslSetupHandler(sslParameters -> sslParameters.setProtocols(new String[]{"SSLv3"}))
                .create();
        server.start();

        requester = RequesterBootstrap.bootstrap()
                .setSslContext(SSLTestContexts.createClientSSLContext())
                .setSocketConfig(SocketConfig.custom()
                        .setSoTimeout(TIMEOUT)
                        .build())
                .setStreamListener(LoggingHttp1StreamListener.INSTANCE)
                .setConnPoolListener(LoggingConnPoolListener.INSTANCE)
                .create();

        final HttpContext context = new BasicHttpContext();
        final HttpHost target = new HttpHost("https", "localhost", server.getLocalPort());
        final ClassicHttpRequest request1 = new BasicClassicHttpRequest(Method.POST, "/stuff");
        request1.setEntity(new StringEntity("some stuff", ContentType.TEXT_PLAIN));
        Assert.assertThrows(IOException.class, () -> {
            try (final ClassicHttpResponse response1 = requester.execute(target, request1, TIMEOUT, context)) {
                EntityUtils.consume(response1.getEntity());
            }
        });
    }

    @Test
    public void testWeakCiphersDisabledByDefault() throws Exception {

        requester = RequesterBootstrap.bootstrap()
                .setSslContext(SSLTestContexts.createClientSSLContext())
                .setSocketConfig(SocketConfig.custom()
                        .setSoTimeout(TIMEOUT)
                        .build())
                .setStreamListener(LoggingHttp1StreamListener.INSTANCE)
                .setConnPoolListener(LoggingConnPoolListener.INSTANCE)
                .create();

        final String[] weakCiphersSuites = {
                "SSL_RSA_WITH_RC4_128_SHA",
                "SSL_RSA_WITH_3DES_EDE_CBC_SHA",
                "TLS_DH_anon_WITH_AES_128_CBC_SHA",
                "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA",
                "SSL_RSA_WITH_NULL_SHA",
                "TLS_ECDHE_ECDSA_WITH_RC4_128_SHA",
                "TLS_ECDH_ECDSA_WITH_3DES_EDE_CBC_SHA",
                "TLS_DH_anon_WITH_AES_256_GCM_SHA384",
                "TLS_ECDH_anon_WITH_AES_256_CBC_SHA",
                "TLS_RSA_WITH_NULL_SHA256",
                "SSL_RSA_EXPORT_WITH_RC4_40_MD5",
                "SSL_DH_anon_EXPORT_WITH_RC4_40_MD5",
                "TLS_KRB5_EXPORT_WITH_RC4_40_SHA",
                "SSL_RSA_EXPORT_WITH_RC2_CBC_40_MD5"
        };

        for (final String cipherSuite : weakCiphersSuites) {
            server = ServerBootstrap.bootstrap()
                    .setSslContext(SSLTestContexts.createServerSSLContext())
                    .setSslSetupHandler(sslParameters -> sslParameters.setProtocols(new String[]{cipherSuite}))
                    .create();
            Assert.assertThrows(Exception.class, () -> {
                try {
                    server.start();

                    final HttpContext context = new BasicHttpContext();
                    final HttpHost target = new HttpHost("https", "localhost", server.getLocalPort());
                    final ClassicHttpRequest request1 = new BasicClassicHttpRequest(Method.POST, "/stuff");
                    request1.setEntity(new StringEntity("some stuff", ContentType.TEXT_PLAIN));
                    try (final ClassicHttpResponse response1 = requester.execute(target, request1, TIMEOUT, context)) {
                        EntityUtils.consume(response1.getEntity());
                    }
                } finally {
                    server.close(CloseMode.IMMEDIATE);
                }
            });
        }
    }

}
