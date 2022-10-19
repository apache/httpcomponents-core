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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSession;

import org.apache.hc.core5.concurrent.BasicFuture;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.concurrent.FutureContribution;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.bootstrap.AsyncRequesterBootstrap;
import org.apache.hc.core5.http.impl.bootstrap.AsyncServerBootstrap;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.nio.AsyncClientEndpoint;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityProducer;
import org.apache.hc.core5.http.nio.ssl.BasicClientTlsStrategy;
import org.apache.hc.core5.http.nio.ssl.BasicServerTlsStrategy;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http.nio.ssl.TlsSupport;
import org.apache.hc.core5.http.nio.ssl.TlsUpgradeCapable;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.http.protocol.UriPatternMatcher;
import org.apache.hc.core5.http.ssl.TLS;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.reactor.ssl.SSLBufferMode;
import org.apache.hc.core5.reactor.ssl.SSLSessionInitializer;
import org.apache.hc.core5.reactor.ssl.SSLSessionVerifier;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.hc.core5.reactor.ssl.TransportSecurityLayer;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.testing.SSLTestContexts;
import org.apache.hc.core5.testing.classic.LoggingConnPoolListener;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.ReflectionUtils;
import org.apache.hc.core5.util.Timeout;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.ValueSource;

public class TLSIntegrationTest {

    private static final Timeout TIMEOUT = Timeout.ofSeconds(30);

    private HttpAsyncServer server;

    @RegisterExtension
    public final AfterEachCallback serverCleanup = new AfterEachCallback() {

        @Override
        public void afterEach(final ExtensionContext context) throws Exception {
            if (server != null) {
                try {
                    server.close(CloseMode.IMMEDIATE);
                } catch (final Exception ignore) {
                }
            }
        }

    };

    private HttpAsyncRequester client;

    @RegisterExtension
    public final AfterEachCallback clientCleanup = new AfterEachCallback() {

        @Override
        public void afterEach(final ExtensionContext context) throws Exception {
            if (client != null) {
                try {
                    client.close(CloseMode.GRACEFUL);
                } catch (final Exception ignore) {
                }
            }
        }

    };

    HttpAsyncServer createServer(final TlsStrategy tlsStrategy) {
        return AsyncServerBootstrap.bootstrap()
                .setLookupRegistry(new UriPatternMatcher<>())
                .setIOReactorConfig(
                        IOReactorConfig.custom()
                                .setSoTimeout(TIMEOUT)
                                .setIoThreadCount(1)
                                .build())
                .setTlsStrategy(tlsStrategy)
                .setStreamListener(LoggingHttp1StreamListener.INSTANCE_SERVER)
                .setIOSessionDecorator(LoggingIOSessionDecorator.INSTANCE)
                .setExceptionCallback(LoggingExceptionCallback.INSTANCE)
                .setIOSessionListener(LoggingIOSessionListener.INSTANCE)
                .register("*", () -> new EchoHandler(2048))
                .create();
    }

    HttpAsyncRequester createClient(final TlsStrategy tlsStrategy) {
        return AsyncRequesterBootstrap.bootstrap()
                .setIOReactorConfig(IOReactorConfig.custom()
                        .setSoTimeout(TIMEOUT)
                        .build())
                .setTlsStrategy(tlsStrategy)
                .setStreamListener(LoggingHttp1StreamListener.INSTANCE_CLIENT)
                .setConnPoolListener(LoggingConnPoolListener.INSTANCE)
                .setIOSessionDecorator(LoggingIOSessionDecorator.INSTANCE)
                .setExceptionCallback(LoggingExceptionCallback.INSTANCE)
                .setIOSessionListener(LoggingIOSessionListener.INSTANCE)
                .create();
    }

    Future<TlsDetails> executeTlsHandshake() throws Exception {
        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(0), URIScheme.HTTPS);
        final ListenerEndpoint listener = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        final InetSocketAddress address = (InetSocketAddress) listener.getAddress();

        final HttpHost target = new HttpHost(URIScheme.HTTPS.id, "localhost", address.getPort());

        final BasicFuture<TlsDetails> tlsFuture = new BasicFuture<>(null);
        client.connect(
                new HttpHost(URIScheme.HTTP.id, "localhost", address.getPort()),
                TIMEOUT, null,
                new FutureContribution<AsyncClientEndpoint>(tlsFuture) {

                    @Override
                    public void completed(final AsyncClientEndpoint clientEndpoint) {
                        try {
                            ((TlsUpgradeCapable) clientEndpoint).tlsUpgrade(
                                    target,
                                    new FutureContribution<ProtocolIOSession>(tlsFuture) {

                                        @Override
                                        public void completed(final ProtocolIOSession protocolIOSession) {
                                            tlsFuture.completed(protocolIOSession.getTlsDetails());
                                        }

                                    });
                        } catch (final Exception ex) {
                            tlsFuture.failed(ex);
                        }
                    }

                });
        return tlsFuture;
    }

    @ParameterizedTest(name = "TLS protocol {0}")
    @ArgumentsSource(SupportedTLSProtocolProvider.class)
    public void testTLSSuccess(final TLS tlsProtocol) throws Exception {
        final TlsStrategy serverTlsStrategy = new TestTlsStrategy(
                SSLTestContexts.createServerSSLContext(),
                (endpoint, sslEngine) -> sslEngine.setEnabledProtocols(new String[]{tlsProtocol.getId()}),
                null);
        server = createServer(serverTlsStrategy);
        server.start();

        final TlsStrategy clientTlsStrategy = new TestTlsStrategy(SSLTestContexts.createClientSSLContext(),
                (endpoint, sslEngine) -> sslEngine.setEnabledProtocols(new String[]{tlsProtocol.getId()}),
                null);
        client = createClient(clientTlsStrategy);
        client.start();

        final Future<TlsDetails> tlsSessionFuture = executeTlsHandshake();

        final TlsDetails tlsDetails = tlsSessionFuture.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(tlsDetails);
        final SSLSession tlsSession = tlsDetails.getSSLSession();
        final ProtocolVersion tlsVersion = TLS.parse(tlsSession.getProtocol());
        MatcherAssert.assertThat(tlsVersion.greaterEquals(tlsProtocol.version), CoreMatchers.equalTo(true));
        MatcherAssert.assertThat(tlsSession.getPeerPrincipal().getName(),
                CoreMatchers.equalTo("CN=localhost,OU=Apache HttpComponents,O=Apache Software Foundation"));
    }

    @Test
    public void testTLSTrustFailure() throws Exception {
        final TlsStrategy serverTlsStrategy = new BasicServerTlsStrategy(SSLTestContexts.createServerSSLContext());
        server = createServer(serverTlsStrategy);
        server.start();

        final TlsStrategy clientTlsStrategy = new BasicClientTlsStrategy(SSLContexts.createDefault());
        client = createClient(clientTlsStrategy);
        client.start();

        final Future<TlsDetails> tlsSessionFuture = executeTlsHandshake();

        final ExecutionException exception = Assertions.assertThrows(ExecutionException.class, () ->
                tlsSessionFuture.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()));
        final Throwable cause = exception.getCause();
        Assertions.assertInstanceOf(SSLHandshakeException.class, cause);
    }

    @Test
    public void testTLSClientAuthFailure() throws Exception {
        final TlsStrategy serverTlsStrategy = new BasicServerTlsStrategy(
                SSLTestContexts.createServerSSLContext(),
                (endpoint, sslEngine) -> sslEngine.setNeedClientAuth(true),
                null);
        server = createServer(serverTlsStrategy);
        server.start();

        final TlsStrategy clientTlsStrategy = new BasicClientTlsStrategy(SSLTestContexts.createClientSSLContext());
        client = createClient(clientTlsStrategy);
        client.start();

        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(0), URIScheme.HTTPS);
        final ListenerEndpoint listener = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        final InetSocketAddress address = (InetSocketAddress) listener.getAddress();

        final HttpHost target = new HttpHost(URIScheme.HTTPS.id, "localhost", address.getPort());

        final Future<Message<HttpResponse, String>> resultFuture = client.execute(
                new BasicRequestProducer(Method.POST, target, "/stuff",
                        new StringAsyncEntityProducer("some stuff", ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), TIMEOUT, null);

        final ExecutionException exception = Assertions.assertThrows(ExecutionException.class, () ->
                resultFuture.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()));
        final Throwable cause = exception.getCause();
        Assertions.assertInstanceOf(IOException.class, cause);
    }

    @Test
    public void testSSLDisabledByDefault() throws Exception {
        final TlsStrategy serverTlsStrategy = new TestTlsStrategy(
                SSLTestContexts.createServerSSLContext(),
                (endpoint, sslEngine) -> sslEngine.setEnabledProtocols(new String[]{"SSLv3"}),
                null);
        server = createServer(serverTlsStrategy);
        server.start();

        final TlsStrategy clientTlsStrategy = new BasicClientTlsStrategy(SSLTestContexts.createClientSSLContext());
        client = createClient(clientTlsStrategy);
        client.start();

        final Future<TlsDetails> tlsSessionFuture = executeTlsHandshake();

        Assertions.assertThrows(ExecutionException.class, () ->
                tlsSessionFuture.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()));
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
    public void testWeakCipherDisabledByDefault(final String cipher) throws Exception {
        final TlsStrategy serverTlsStrategy = new TestTlsStrategy(
                SSLTestContexts.createServerSSLContext(),
                (endpoint, sslEngine) -> sslEngine.setEnabledCipherSuites(new String[]{cipher}),
                null);
        server = createServer(serverTlsStrategy);
        server.start();

        final TlsStrategy clientTlsStrategy = new BasicClientTlsStrategy(SSLTestContexts.createClientSSLContext());
        client = createClient(clientTlsStrategy);
        client.start();

        final Future<TlsDetails> tlsSessionFuture = executeTlsHandshake();

        Assertions.assertThrows(ExecutionException.class, () ->
                tlsSessionFuture.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()));
    }

    @Test
    public void testTLSVersionMismatch() throws Exception {
        final TlsStrategy serverTlsStrategy = new TestTlsStrategy(
                SSLTestContexts.createServerSSLContext(),
                (endpoint, sslEngine) -> {
                    sslEngine.setEnabledProtocols(new String[]{TLS.V_1_0.getId()});
                    sslEngine.setEnabledCipherSuites(new String[]{
                            "TLS_RSA_WITH_AES_256_CBC_SHA",
                            "TLS_RSA_WITH_AES_128_CBC_SHA",
                            "TLS_RSA_WITH_3DES_EDE_CBC_SHA"});
                },
                null);
        server = createServer(serverTlsStrategy);
        server.start();

        final TlsStrategy clientTlsStrategy = new BasicClientTlsStrategy(
                SSLTestContexts.createClientSSLContext(),
                (endpoint, sslEngine) -> sslEngine.setEnabledProtocols(new String[]{TLS.V_1_2.getId()}),
                null);
        client = createClient(clientTlsStrategy);
        client.start();

        final Future<TlsDetails> tlsSessionFuture = executeTlsHandshake();

        final ExecutionException exception = Assertions.assertThrows(ExecutionException.class, () ->
                tlsSessionFuture.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()));
        final Throwable cause = exception.getCause();
        Assertions.assertInstanceOf(IOException.class, cause);
    }

    static class SupportedTLSProtocolProvider implements ArgumentsProvider {

        int javaVere = ReflectionUtils.determineJRELevel();

        @Override
        public Stream<? extends Arguments> provideArguments(final ExtensionContext context) {
            if (javaVere >= 11) {
                return Stream.of(Arguments.of(TLS.V_1_2), Arguments.of(TLS.V_1_3));
            } else {
                return Stream.of(Arguments.of(TLS.V_1_2));
            }
        }
    }

    static class TestTlsStrategy implements TlsStrategy {

        private final SSLContext sslContext;
        private final SSLSessionInitializer initializer;
        private final SSLSessionVerifier verifier;

        public TestTlsStrategy(
                final SSLContext sslContext,
                final SSLSessionInitializer initializer,
                final SSLSessionVerifier verifier) {
            this.sslContext = Args.notNull(sslContext, "SSL context");
            this.initializer = initializer;
            this.verifier = verifier;
        }

        @Override
        public void upgrade(
                final TransportSecurityLayer tlsSession,
                final NamedEndpoint endpoint,
                final Object attachment,
                final Timeout handshakeTimeout,
                final FutureCallback<TransportSecurityLayer> callback) {
            tlsSession.startTls(sslContext, endpoint, SSLBufferMode.STATIC,
                    TlsSupport.enforceStrongSecurity(initializer), verifier, handshakeTimeout, callback);
        }

        /**
         * @deprecated do not use.
         */
        @Deprecated
        @Override
        public boolean upgrade(
                final TransportSecurityLayer tlsSession,
                final HttpHost host,
                final SocketAddress localAddress,
                final SocketAddress remoteAddress,
                final Object attachment,
                final Timeout handshakeTimeout) {
            tlsSession.startTls(sslContext, host, SSLBufferMode.STATIC,
                    TlsSupport.enforceStrongSecurity(initializer), verifier, handshakeTimeout, null);
            return true;
        }

    }

}
