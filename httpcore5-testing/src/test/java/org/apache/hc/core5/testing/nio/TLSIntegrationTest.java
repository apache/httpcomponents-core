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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSession;

import com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.http.nio.ssl.TlsSupport;

import org.apache.hc.core5.concurrent.BasicFuture;
import org.apache.hc.core5.concurrent.CompletingFutureContribution;
import org.apache.hc.core5.concurrent.FutureContribution;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.impl.routing.RequestRouter;
import org.apache.hc.core5.http.nio.AsyncClientEndpoint;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityProducer;
import org.apache.hc.core5.http.nio.ssl.TlsUpgradeCapable;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.http.ssl.TLS;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.testing.SSLTestContexts;
import org.apache.hc.core5.testing.SupportedTLSProtocolProvider;
import org.apache.hc.core5.testing.TlsStrategyFactory;
import org.apache.hc.core5.testing.extension.SecurityProviderResource;
import org.apache.hc.core5.testing.extension.nio.H2AsyncRequesterResource;
import org.apache.hc.core5.testing.extension.nio.H2AsyncServerResource;
import org.apache.hc.core5.util.Timeout;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.ValueSource;

abstract class TLSIntegrationTest {

    private static final Timeout TIMEOUT = Timeout.ofSeconds(30);

    @RegisterExtension
    @Order(1)
    private final SecurityProviderResource securityProviderResource;
    private final TlsStrategyFactory serverTlsStrategyFactory;
    private final TlsStrategyFactory clientTlsStrategyFactory;
    @RegisterExtension
    @Order(2)
    private final H2AsyncServerResource serverResource;
    @RegisterExtension
    @Order(3)
    private final H2AsyncRequesterResource clientResource;

    public TLSIntegrationTest(final String securityProviderName,
                              final TlsStrategyFactory serverTlsStrategyFactory,
                              final TlsStrategyFactory clientTlsStrategyFactory) {
        this.securityProviderResource = new SecurityProviderResource(securityProviderName);
        this.serverTlsStrategyFactory = serverTlsStrategyFactory;
        this.clientTlsStrategyFactory = clientTlsStrategyFactory;
        this.serverResource = new H2AsyncServerResource();
        this.serverResource.configure(bootstrap -> bootstrap
                .setIOReactorConfig(
                        IOReactorConfig.custom()
                                .setSoTimeout(TIMEOUT)
                                .build())
                .setRequestRouter(RequestRouter.<Supplier<AsyncServerExchangeHandler>>builder()
                        .addRoute(RequestRouter.LOCAL_AUTHORITY, "*", () -> new AsyncEchoStreamingHandler(2048))
                        .resolveAuthority(RequestRouter.LOCAL_AUTHORITY_RESOLVER)
                        .build())
        );
        this.clientResource = new H2AsyncRequesterResource();
        this.clientResource.configure(bootstrap -> bootstrap
                .setIOReactorConfig(IOReactorConfig.custom()
                        .setSoTimeout(TIMEOUT)
                        .build())
        );
    }

    Future<TlsDetails> executeTlsHandshake() throws Exception {
        final HttpAsyncServer server = serverResource.start();
        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), URIScheme.HTTPS);
        final ListenerEndpoint listener = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        final InetSocketAddress address = (InetSocketAddress) listener.getAddress();

        final HttpHost target = new HttpHost(URIScheme.HTTPS.id, "localhost", address.getPort());

        final HttpAsyncRequester client = clientResource.start();
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
                                    new CompletingFutureContribution<>(tlsFuture, ProtocolIOSession::getTlsDetails));
                        } catch (final Exception ex) {
                            tlsFuture.failed(ex);
                        }
                    }

                });
        return tlsFuture;
    }

    @ParameterizedTest(name = "TLS protocol {0}")
    @ArgumentsSource(SupportedTLSProtocolProvider.class)
    void testTLSSuccess(final TLS tlsProtocol) throws Exception {
        final SSLContext serverSSLContext = SSLTestContexts.createServerSSLContext(securityProviderResource.securityProvider(), "TLS");
        serverResource.configure(bootstrap -> bootstrap
                .setVersionPolicy(HttpVersionPolicy.NEGOTIATE)
                .setTlsStrategy(serverTlsStrategyFactory.create(
                        serverSSLContext,
                        (endpoint, sslEngine) -> sslEngine.setEnabledProtocols(new String[]{tlsProtocol.getId()}),
                        null))
        );
        final SSLContext clientSSLContext = SSLTestContexts.createClientSSLContext(securityProviderResource.securityProvider(), "TLS");
        clientResource.configure(bootstrap -> bootstrap
                .setVersionPolicy(HttpVersionPolicy.NEGOTIATE)
                .setTlsStrategy(clientTlsStrategyFactory.create(
                        clientSSLContext,
                        (endpoint, sslEngine) -> sslEngine.setEnabledProtocols(new String[]{tlsProtocol.getId()}),
                        null))
        );

        final Future<TlsDetails> tlsSessionFuture = executeTlsHandshake();

        final TlsDetails tlsDetails = tlsSessionFuture.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertThat(tlsDetails).isNotNull();
        final SSLSession tlsSession = tlsDetails.getSSLSession();
        final ProtocolVersion tlsVersion = TLS.parse(tlsSession.getProtocol());
        Assertions.assertThat(tlsVersion.greaterEquals(tlsProtocol.version)).isTrue();
        Assertions.assertThat(tlsSession.getPeerPrincipal().getName()).isEqualTo("CN=localhost,OU=Apache HttpComponents,O=Apache Software Foundation");
    }

    @Test
    void testTLSTrustFailure() throws Exception {
        final SSLContext serverSSLContext = SSLTestContexts.createServerSSLContext(securityProviderResource.securityProvider(), "TLS");
        serverResource.configure(bootstrap -> bootstrap
                .setVersionPolicy(HttpVersionPolicy.NEGOTIATE)
                .setTlsStrategy(serverTlsStrategyFactory.create(serverSSLContext, null, null))
        );
        final SSLContext clientSSLContext = SSLContexts.createDefault();
        clientResource.configure(bootstrap -> bootstrap
                .setVersionPolicy(HttpVersionPolicy.NEGOTIATE)
                .setTlsStrategy(clientTlsStrategyFactory.create(clientSSLContext, null, null))
        );

        final Future<TlsDetails> tlsSessionFuture = executeTlsHandshake();

        final ExecutionException exception = Assertions.assertThatThrownBy(() ->
                tlsSessionFuture.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()))
                .isInstanceOf(ExecutionException.class)
                .asInstanceOf(InstanceOfAssertFactories.type(ExecutionException.class))
                .actual();
        final Throwable cause = exception.getCause();
        Assertions.assertThat(cause).isInstanceOf(SSLException.class);
    }

    @Test
    void testTLSClientAuthFailure() throws Exception {
        final SSLContext serverSSLContext = SSLTestContexts.createServerSSLContext(securityProviderResource.securityProvider(), "TLS");
        serverResource.configure(bootstrap -> bootstrap
                .setVersionPolicy(HttpVersionPolicy.NEGOTIATE)
                .setTlsStrategy(serverTlsStrategyFactory.create(
                        serverSSLContext,
                        (endpoint, sslEngine) -> sslEngine.setNeedClientAuth(true),
                        null))
        );
        final SSLContext clientSSLContext = SSLTestContexts.createClientSSLContext(securityProviderResource.securityProvider(), "TLS");
        clientResource.configure(bootstrap -> bootstrap
                .setVersionPolicy(HttpVersionPolicy.NEGOTIATE)
                .setTlsStrategy(clientTlsStrategyFactory.create(
                        clientSSLContext,
                        null,
                        null))
        );
        final HttpAsyncServer server = serverResource.start();
        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), URIScheme.HTTPS);
        final ListenerEndpoint listener = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        final InetSocketAddress address = (InetSocketAddress) listener.getAddress();

        final HttpHost target = new HttpHost(URIScheme.HTTPS.id, "localhost", address.getPort());

        final HttpAsyncRequester client = clientResource.start();

        final Future<Message<HttpResponse, String>> resultFuture = client.execute(
                new BasicRequestProducer(Method.POST, target, "/stuff",
                        new StringAsyncEntityProducer("some stuff", ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), TIMEOUT, null);

        final ExecutionException exception = Assertions.assertThatThrownBy(() ->
                resultFuture.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()))
                .isInstanceOf(ExecutionException.class)
                .asInstanceOf(InstanceOfAssertFactories.type(ExecutionException.class))
                .actual();
        final Throwable cause = exception.getCause();
        Assertions.assertThat(cause).isInstanceOf(IOException.class);
    }

    @Test
    void testTLSClientAuthSuccess() throws Exception {
        final SSLContext serverSSLContext = SSLTestContexts.createServerSSLContext(securityProviderResource.securityProvider(), "TLS");
        serverResource.configure(bootstrap -> bootstrap
                .setVersionPolicy(HttpVersionPolicy.NEGOTIATE)
                .setTlsStrategy(serverTlsStrategyFactory.create(
                        serverSSLContext,
                        (endpoint, sslEngine) -> sslEngine.setNeedClientAuth(true),
                        null))
        );
        final SSLContext clientSSLContext = SSLTestContexts.createClientSSLContextWithKeyMaterial(securityProviderResource.securityProvider(), "TLS");
        clientResource.configure(bootstrap -> bootstrap
                .setVersionPolicy(HttpVersionPolicy.NEGOTIATE)
                .setTlsStrategy(clientTlsStrategyFactory.create(
                        clientSSLContext,
                        null,
                        null))
        );
        final HttpAsyncServer server = serverResource.start();
        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), URIScheme.HTTPS);
        final ListenerEndpoint listener = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        final InetSocketAddress address = (InetSocketAddress) listener.getAddress();

        final HttpHost target = new HttpHost(URIScheme.HTTPS.id, "localhost", address.getPort());

        final HttpAsyncRequester client = clientResource.start();

        final Future<Message<HttpResponse, String>> resultFuture = client.execute(
                new BasicRequestProducer(Method.POST, target, "/stuff",
                        new StringAsyncEntityProducer("some stuff", ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), TIMEOUT, null);

        final Message<HttpResponse, String> message = resultFuture.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertThat(message).isNotNull();
        Assertions.assertThat(message.head().getCode()).isEqualTo(200);
    }

    @Test
    void testSSLDisabledByDefault() throws Exception {
        final SSLContext serverSSLContext = SSLTestContexts.createServerSSLContext(securityProviderResource.securityProvider(), "TLS");
        serverResource.configure(bootstrap -> bootstrap
                .setVersionPolicy(HttpVersionPolicy.NEGOTIATE)
                .setTlsStrategy(serverTlsStrategyFactory.create(
                        serverSSLContext,
                        (endpoint, sslEngine) -> sslEngine.setEnabledProtocols(new String[]{"SSLv3"}),
                        null))
        );
        final SSLContext clientSSLContext = SSLTestContexts.createClientSSLContext(securityProviderResource.securityProvider(), "TLS");
        clientResource.configure(bootstrap -> bootstrap
                .setVersionPolicy(HttpVersionPolicy.NEGOTIATE)
                .setTlsStrategy(clientTlsStrategyFactory.create(
                        clientSSLContext,
                        null,
                        null))
        );
        final Future<TlsDetails> tlsSessionFuture = executeTlsHandshake();

        Assertions.assertThatThrownBy(() ->
                tlsSessionFuture.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()))
                .isInstanceOf(ExecutionException.class);
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
                .setVersionPolicy(HttpVersionPolicy.NEGOTIATE)
                .setTlsStrategy(serverTlsStrategyFactory.create(
                        serverSSLContext,
                        (endpoint, sslEngine) -> sslEngine.setEnabledCipherSuites(new String[]{cipher}),
                        null))
        );
        final SSLContext clientSSLContext = SSLTestContexts.createClientSSLContext(securityProviderResource.securityProvider(), "TLS");
        clientResource.configure(bootstrap -> bootstrap
                .setVersionPolicy(HttpVersionPolicy.NEGOTIATE)
                .setTlsStrategy(clientTlsStrategyFactory.create(
                        clientSSLContext,
                        (endpoint, sslEngine) -> sslEngine.setSSLParameters(TlsSupport.enforceStrongSecurity(sslEngine.getSSLParameters())),
                        null))
        );
        final Future<TlsDetails> tlsSessionFuture = executeTlsHandshake();

        Assertions.assertThatThrownBy(() ->
                tlsSessionFuture.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()))
                .isInstanceOf(ExecutionException.class);
    }

    @Test
    void testTLSVersionMismatch() throws Exception {
        final SSLContext serverSSLContext = SSLTestContexts.createServerSSLContext(securityProviderResource.securityProvider(), "TLS");
        serverResource.configure(bootstrap -> bootstrap
                .setVersionPolicy(HttpVersionPolicy.NEGOTIATE)
                .setTlsStrategy(serverTlsStrategyFactory.create(
                        serverSSLContext,
                        (endpoint, sslEngine) -> {
                            sslEngine.setEnabledProtocols(new String[]{TLS.V_1_0.getId()});
                            sslEngine.setEnabledCipherSuites(new String[]{
                                    "TLS_RSA_WITH_AES_256_CBC_SHA",
                                    "TLS_RSA_WITH_AES_128_CBC_SHA",
                                    "TLS_RSA_WITH_3DES_EDE_CBC_SHA"});
                        },
                        null))
        );
        final SSLContext clientSSLContext = SSLTestContexts.createClientSSLContext(securityProviderResource.securityProvider(), "TLS");
        clientResource.configure(bootstrap -> bootstrap
                .setVersionPolicy(HttpVersionPolicy.NEGOTIATE)
                .setTlsStrategy(clientTlsStrategyFactory.create(
                        clientSSLContext,
                        (endpoint, sslEngine) -> sslEngine.setEnabledProtocols(new String[]{TLS.V_1_2.getId()}),
                        null))
        );
        final Future<TlsDetails> tlsSessionFuture = executeTlsHandshake();

        final ExecutionException exception = Assertions.assertThatThrownBy(() ->
                tlsSessionFuture.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()))
                .isInstanceOf(ExecutionException.class)
                .asInstanceOf(InstanceOfAssertFactories.type(ExecutionException.class))
                .actual();
        final Throwable cause = exception.getCause();
        Assertions.assertThat(cause).isInstanceOf(IOException.class);
    }

    @Test
    void testHostNameVerification() throws Exception {
        final SSLContext serverSSLContext = SSLTestContexts.createServerSSLContext(securityProviderResource.securityProvider(), "TLS");
        serverResource.configure(bootstrap -> bootstrap
                .setVersionPolicy(HttpVersionPolicy.NEGOTIATE)
                .setTlsStrategy(serverTlsStrategyFactory.create(
                        serverSSLContext,
                        null,
                        null))
        );
        final SSLContext clientSSLContext = SSLTestContexts.createClientSSLContext(securityProviderResource.securityProvider(), "TLS");
        clientResource.configure(bootstrap -> bootstrap
                .setVersionPolicy(HttpVersionPolicy.NEGOTIATE)
                .setTlsStrategy(clientTlsStrategyFactory.create(
                        clientSSLContext,
                        null,
                        null))
        );

        final HttpAsyncServer server = serverResource.start();
        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), URIScheme.HTTPS);
        final ListenerEndpoint listener = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        final InetSocketAddress address = (InetSocketAddress) listener.getAddress();

        final HttpAsyncRequester client = clientResource.start();

        final HttpHost target1 = new HttpHost(URIScheme.HTTPS.id, InetAddress.getLoopbackAddress(), "localhost", address.getPort());
        final Future<Message<HttpResponse, String>> resultFuture1 = client.execute(
                target1,
                new BasicRequestProducer(Method.POST, target1, "/stuff",
                        new StringAsyncEntityProducer("some stuff", ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), TIMEOUT, null);
        final Message<HttpResponse, String> message1 = resultFuture1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertThat(message1).isNotNull();
        Assertions.assertThat(message1.head().getCode()).isEqualTo(200);

        final HttpHost target2 = new HttpHost(URIScheme.HTTPS.id, InetAddress.getLoopbackAddress(), "some-other-host", address.getPort());
        final Future<Message<HttpResponse, String>> resultFuture2 = client.execute(
                target2,
                new BasicRequestProducer(Method.POST, target2, "/stuff",
                        new StringAsyncEntityProducer("some stuff", ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), TIMEOUT, null);
        final ExecutionException exception = Assertions.assertThatThrownBy(() ->
                resultFuture2.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()))
                .isInstanceOf(ExecutionException.class)
                .asInstanceOf(InstanceOfAssertFactories.type(ExecutionException.class))
                .actual();
        final Throwable cause = exception.getCause();
        Assertions.assertThat(cause).isInstanceOf(SSLHandshakeException.class);
    }

}
