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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSession;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.bootstrap.AsyncServerBootstrap;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityProducer;
import org.apache.hc.core5.http.nio.ssl.BasicClientTlsStrategy;
import org.apache.hc.core5.http.nio.ssl.BasicServerTlsStrategy;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.http.protocol.UriPatternMatcher;
import org.apache.hc.core5.http.ssl.TLS;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2RequesterBootstrap;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.testing.SSLTestContexts;
import org.apache.hc.core5.testing.classic.LoggingConnPoolListener;
import org.apache.hc.core5.util.Timeout;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;

public class H2TLSIntegrationTest {

    private static final Timeout TIMEOUT = Timeout.ofSeconds(30);

    private HttpAsyncServer server;

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

    private HttpAsyncRequester requester;

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
        server = AsyncServerBootstrap.bootstrap()
                .setLookupRegistry(new UriPatternMatcher<>())
                .setIOReactorConfig(
                        IOReactorConfig.custom()
                                .setSoTimeout(TIMEOUT)
                                .build())
                .setTlsStrategy(new BasicServerTlsStrategy(SSLTestContexts.createServerSSLContext()))
                .setStreamListener(LoggingHttp1StreamListener.INSTANCE_SERVER)
                .setIOSessionDecorator(LoggingIOSessionDecorator.INSTANCE)
                .setExceptionCallback(LoggingExceptionCallback.INSTANCE)
                .setIOSessionListener(LoggingIOSessionListener.INSTANCE)
                .register("*", () -> new EchoHandler(2048))
                .create();
        server.start();

        final AtomicReference<SSLSession> sslSessionRef = new AtomicReference<>(null);

        requester = H2RequesterBootstrap.bootstrap()
                .setIOReactorConfig(IOReactorConfig.custom()
                        .setSoTimeout(TIMEOUT)
                        .build())
                .setTlsStrategy(new BasicClientTlsStrategy(
                        SSLTestContexts.createClientSSLContext(),
                        (endpoint, sslEngine) -> {
                            sslSessionRef.set(sslEngine.getSession());
                            return null;
                        }))
                .setStreamListener(LoggingHttp1StreamListener.INSTANCE_CLIENT)
                .setConnPoolListener(LoggingConnPoolListener.INSTANCE)
                .setIOSessionDecorator(LoggingIOSessionDecorator.INSTANCE)
                .setExceptionCallback(LoggingExceptionCallback.INSTANCE)
                .setIOSessionListener(LoggingIOSessionListener.INSTANCE)
                .create();

        server.start();
        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(0), URIScheme.HTTPS);
        final ListenerEndpoint listener = future.get();
        final InetSocketAddress address = (InetSocketAddress) listener.getAddress();
        requester.start();

        final HttpHost target = new HttpHost(URIScheme.HTTPS.id, "localhost", address.getPort());
        final Future<Message<HttpResponse, String>> resultFuture1 = requester.execute(
                new BasicRequestProducer(Method.POST, target, "/stuff",
                        new StringAsyncEntityProducer("some stuff", ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), TIMEOUT, null);
        final Message<HttpResponse, String> message1 = resultFuture1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        MatcherAssert.assertThat(message1, CoreMatchers.notNullValue());
        final HttpResponse response1 = message1.getHead();
        MatcherAssert.assertThat(response1.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
        final String body1 = message1.getBody();
        MatcherAssert.assertThat(body1, CoreMatchers.equalTo("some stuff"));

        final SSLSession sslSession = sslSessionRef.getAndSet(null);
        final ProtocolVersion tlsVersion = TLS.parse(sslSession.getProtocol());
        MatcherAssert.assertThat(tlsVersion.greaterEquals(TLS.V_1_2.version), CoreMatchers.equalTo(true));
        MatcherAssert.assertThat(sslSession.getPeerPrincipal().getName(),
                CoreMatchers.equalTo("CN=localhost,OU=Apache HttpComponents,O=Apache Software Foundation"));
    }

    @Test
    public void testTLSTrustFailure() throws Exception {
        server = AsyncServerBootstrap.bootstrap()
                .setLookupRegistry(new UriPatternMatcher<>())
                .setIOReactorConfig(
                        IOReactorConfig.custom()
                                .setSoTimeout(TIMEOUT)
                                .build())
                .setTlsStrategy(new BasicServerTlsStrategy(SSLTestContexts.createServerSSLContext()))
                .setStreamListener(LoggingHttp1StreamListener.INSTANCE_SERVER)
                .setIOSessionDecorator(LoggingIOSessionDecorator.INSTANCE)
                .setExceptionCallback(LoggingExceptionCallback.INSTANCE)
                .setIOSessionListener(LoggingIOSessionListener.INSTANCE)
                .register("*", () -> new EchoHandler(2048))
                .create();
        server.start();

        requester = H2RequesterBootstrap.bootstrap()
                .setIOReactorConfig(IOReactorConfig.custom()
                        .setSoTimeout(TIMEOUT)
                        .build())
                .setTlsStrategy(new BasicClientTlsStrategy(SSLContexts.createDefault()))
                .setStreamListener(LoggingHttp1StreamListener.INSTANCE_CLIENT)
                .setConnPoolListener(LoggingConnPoolListener.INSTANCE)
                .setIOSessionDecorator(LoggingIOSessionDecorator.INSTANCE)
                .setExceptionCallback(LoggingExceptionCallback.INSTANCE)
                .setIOSessionListener(LoggingIOSessionListener.INSTANCE)
                .create();

        server.start();
        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(0), URIScheme.HTTPS);
        final ListenerEndpoint listener = future.get();
        final InetSocketAddress address = (InetSocketAddress) listener.getAddress();
        requester.start();

        final HttpHost target = new HttpHost(URIScheme.HTTPS.id, "localhost", address.getPort());
        final Future<Message<HttpResponse, String>> resultFuture1 = requester.execute(
                new BasicRequestProducer(Method.POST, target, "/stuff",
                        new StringAsyncEntityProducer("some stuff", ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), TIMEOUT, null);
        final ExecutionException exception = Assert.assertThrows(ExecutionException.class, () ->
                resultFuture1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()));
        final Throwable cause = exception.getCause();
        MatcherAssert.assertThat(cause, CoreMatchers.instanceOf(SSLHandshakeException.class));
    }

    @Test
    public void testTLSClientAuthFailure() throws Exception {
        server = AsyncServerBootstrap.bootstrap()
                .setLookupRegistry(new UriPatternMatcher<>())
                .setIOReactorConfig(
                        IOReactorConfig.custom()
                                .setSoTimeout(TIMEOUT)
                                .build())
                .setTlsStrategy(new BasicServerTlsStrategy(
                        SSLTestContexts.createServerSSLContext(),
                        (endpoint, sslEngine) -> sslEngine.setNeedClientAuth(true),
                        null))
                .setStreamListener(LoggingHttp1StreamListener.INSTANCE_SERVER)
                .setIOSessionDecorator(LoggingIOSessionDecorator.INSTANCE)
                .setExceptionCallback(LoggingExceptionCallback.INSTANCE)
                .setIOSessionListener(LoggingIOSessionListener.INSTANCE)
                .register("*", () -> new EchoHandler(2048))
                .create();
        server.start();

        requester = H2RequesterBootstrap.bootstrap()
                .setIOReactorConfig(IOReactorConfig.custom()
                        .setSoTimeout(TIMEOUT)
                        .build())
                .setTlsStrategy(new BasicClientTlsStrategy(SSLTestContexts.createClientSSLContext()))
                .setStreamListener(LoggingHttp1StreamListener.INSTANCE_CLIENT)
                .setConnPoolListener(LoggingConnPoolListener.INSTANCE)
                .setIOSessionDecorator(LoggingIOSessionDecorator.INSTANCE)
                .setExceptionCallback(LoggingExceptionCallback.INSTANCE)
                .setIOSessionListener(LoggingIOSessionListener.INSTANCE)
                .create();

        server.start();
        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(0), URIScheme.HTTPS);
        final ListenerEndpoint listener = future.get();
        final InetSocketAddress address = (InetSocketAddress) listener.getAddress();
        requester.start();

        final HttpHost target = new HttpHost(URIScheme.HTTPS.id, "localhost", address.getPort());
        final Future<Message<HttpResponse, String>> resultFuture1 = requester.execute(
                new BasicRequestProducer(Method.POST, target, "/stuff",
                        new StringAsyncEntityProducer("some stuff", ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), TIMEOUT, null);
        final ExecutionException exception = Assert.assertThrows(ExecutionException.class, () ->
                resultFuture1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()));
        final Throwable cause = exception.getCause();
        MatcherAssert.assertThat(cause, CoreMatchers.instanceOf(IOException.class));
    }

    @Test
    public void testSSLDisabledByDefault() throws Exception {
        server = AsyncServerBootstrap.bootstrap()
                .setLookupRegistry(new UriPatternMatcher<>())
                .setIOReactorConfig(
                        IOReactorConfig.custom()
                                .setSoTimeout(TIMEOUT)
                                .build())
                .setTlsStrategy(new BasicServerTlsStrategy(
                        SSLTestContexts.createServerSSLContext(),
                        (endpoint, sslEngine) -> sslEngine.setEnabledProtocols(new String[]{"SSLv3"}),
                        null))
                .setStreamListener(LoggingHttp1StreamListener.INSTANCE_SERVER)
                .setIOSessionDecorator(LoggingIOSessionDecorator.INSTANCE)
                .setExceptionCallback(LoggingExceptionCallback.INSTANCE)
                .setIOSessionListener(LoggingIOSessionListener.INSTANCE)
                .register("*", () -> new EchoHandler(2048))
                .create();
        server.start();

        requester = H2RequesterBootstrap.bootstrap()
                .setIOReactorConfig(IOReactorConfig.custom()
                        .setSoTimeout(TIMEOUT)
                        .build())
                .setTlsStrategy(new BasicClientTlsStrategy(SSLTestContexts.createClientSSLContext()))
                .setStreamListener(LoggingHttp1StreamListener.INSTANCE_CLIENT)
                .setConnPoolListener(LoggingConnPoolListener.INSTANCE)
                .setIOSessionDecorator(LoggingIOSessionDecorator.INSTANCE)
                .setExceptionCallback(LoggingExceptionCallback.INSTANCE)
                .setIOSessionListener(LoggingIOSessionListener.INSTANCE)
                .create();

        server.start();
        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(0), URIScheme.HTTPS);
        final ListenerEndpoint listener = future.get();
        final InetSocketAddress address = (InetSocketAddress) listener.getAddress();
        requester.start();

        final HttpHost target = new HttpHost(URIScheme.HTTPS.id, "localhost", address.getPort());
        final Future<Message<HttpResponse, String>> resultFuture1 = requester.execute(
                new BasicRequestProducer(Method.POST, target, "/stuff",
                        new StringAsyncEntityProducer("some stuff", ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), TIMEOUT, null);
        final ExecutionException exception = Assert.assertThrows(ExecutionException.class, () ->
                resultFuture1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()));
        final Throwable cause = exception.getCause();
        MatcherAssert.assertThat(cause, CoreMatchers.instanceOf(IOException.class));
    }

    @Test
    public void testWeakCiphersDisabledByDefault() throws Exception {
        requester = H2RequesterBootstrap.bootstrap()
                .setIOReactorConfig(IOReactorConfig.custom()
                        .setSoTimeout(TIMEOUT)
                        .build())
                .setTlsStrategy(new BasicClientTlsStrategy(SSLTestContexts.createClientSSLContext()))
                .setStreamListener(LoggingHttp1StreamListener.INSTANCE_CLIENT)
                .setConnPoolListener(LoggingConnPoolListener.INSTANCE)
                .setIOSessionDecorator(LoggingIOSessionDecorator.INSTANCE)
                .setExceptionCallback(LoggingExceptionCallback.INSTANCE)
                .setIOSessionListener(LoggingIOSessionListener.INSTANCE)
                .create();
        requester.start();

        final String[] weakCiphersSuites = {
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
        };

        for (final String cipherSuite : weakCiphersSuites) {
            server = AsyncServerBootstrap.bootstrap()
                    .setLookupRegistry(new UriPatternMatcher<>())
                    .setIOReactorConfig(
                            IOReactorConfig.custom()
                                    .setSoTimeout(TIMEOUT)
                                    .build())
                    .setTlsStrategy(new BasicServerTlsStrategy(
                            SSLTestContexts.createServerSSLContext(),
                            (endpoint, sslEngine) -> sslEngine.setEnabledCipherSuites(new String[]{cipherSuite}),
                            null))
                    .setStreamListener(LoggingHttp1StreamListener.INSTANCE_SERVER)
                    .setIOSessionDecorator(LoggingIOSessionDecorator.INSTANCE)
                    .setExceptionCallback(LoggingExceptionCallback.INSTANCE)
                    .setIOSessionListener(LoggingIOSessionListener.INSTANCE)
                    .register("*", () -> new EchoHandler(2048))
                    .create();
            try {
                server.start();
                final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(0), URIScheme.HTTPS);
                final ListenerEndpoint listener = future.get();
                final InetSocketAddress address = (InetSocketAddress) listener.getAddress();

                final HttpHost target = new HttpHost(URIScheme.HTTPS.id, "localhost", address.getPort());
                final Future<Message<HttpResponse, String>> resultFuture1 = requester.execute(
                        new BasicRequestProducer(Method.POST, target, "/stuff",
                                new StringAsyncEntityProducer("some stuff", ContentType.TEXT_PLAIN)),
                        new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), TIMEOUT, null);
                final ExecutionException exception = Assert.assertThrows(ExecutionException.class, () ->
                        resultFuture1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()));
                final Throwable cause = exception.getCause();
                MatcherAssert.assertThat(cause, CoreMatchers.instanceOf(IOException.class));
            } finally {
                server.close(CloseMode.IMMEDIATE);
            }
        }
    }

}
