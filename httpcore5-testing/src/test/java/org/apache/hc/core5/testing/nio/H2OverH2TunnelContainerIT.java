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

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.command.RequestExecutionCommand;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessorBuilder;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.impl.nio.ClientH2PrefaceHandler;
import org.apache.hc.core5.http2.impl.nio.ClientH2StreamMultiplexerFactory;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2MultiplexingRequester;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2MultiplexingRequesterBootstrap;
import org.apache.hc.core5.http2.nio.support.H2OverH2TunnelSupport;
import org.apache.hc.core5.http2.ssl.H2ClientTlsStrategy;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Full integration test for HTTP/2 CONNECT tunneling over a real proxy container.
 */
@Testcontainers(disabledWithoutDocker = true)
class H2OverH2TunnelContainerIT {

    private static final String ORIGIN_BODY = "h2-tunnel-it-ok";
    private static final int ORIGIN_PORT = 9443;
    private static final int PROXY_PORT = 8080;
    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(90);
    private static final String ORIGIN_ALIAS = "h2-origin";

    private static Network network;
    private static GenericContainer<?> originContainer;
    private static GenericContainer<?> proxyContainer;

    @BeforeAll
    @SuppressWarnings("resource")
    static void setUp() throws Exception {
        network = Network.newNetwork();

        originContainer = new GenericContainer<>(DockerImageName.parse("nginx:1.27.4-alpine"))
                .withNetwork(network)
                .withNetworkAliases(ORIGIN_ALIAS)
                .withCopyFileToContainer(
                        MountableFile.forClasspathResource("h2-tunnel-it/nginx-tls.conf"),
                        "/etc/nginx/nginx.conf")
                .withCopyFileToContainer(
                        MountableFile.forClasspathResource("h2-tunnel-it/certs/origin.crt"),
                        "/etc/nginx/certs/origin.crt")
                .withCopyFileToContainer(
                        MountableFile.forClasspathResource("h2-tunnel-it/certs/origin.key"),
                        "/etc/nginx/certs/origin.key")
                .withExposedPorts(ORIGIN_PORT)
                .waitingFor(Wait.forListeningPort())
                .withStartupTimeout(STARTUP_TIMEOUT);
        originContainer.start();

        proxyContainer = new GenericContainer<>(DockerImageName.parse("envoyproxy/envoy:v1.31.2"))
                .withNetwork(network)
                .withCommand("envoy", "-c", "/etc/envoy/envoy.yaml", "-l", "info")
                .withCopyFileToContainer(
                        MountableFile.forClasspathResource("h2-tunnel-it/envoy.yaml"),
                        "/etc/envoy/envoy.yaml")
                .withExposedPorts(PROXY_PORT)
                .waitingFor(Wait.forListeningPort())
                .withStartupTimeout(STARTUP_TIMEOUT);
        proxyContainer.start();
    }

    @AfterAll
    static void tearDown() {
        if (proxyContainer != null) {
            proxyContainer.close();
        }
        if (originContainer != null) {
            originContainer.close();
        }
        if (network != null) {
            network.close();
        }
    }

    @Test
    void testHttp2TunnelOverHttp2ProxyAndConnectionReuse() throws Exception {
        final Timeout timeout = Timeout.ofSeconds(30);
        final HttpHost proxy = new HttpHost(
                "http",
                proxyContainer.getHost(),
                proxyContainer.getMappedPort(PROXY_PORT));
        final HttpHost target = new HttpHost("https", ORIGIN_ALIAS, ORIGIN_PORT);

        final H2MultiplexingRequester requester = H2MultiplexingRequesterBootstrap.bootstrap()
                .setH2Config(H2Config.custom().setPushEnabled(false).build())
                .create();
        requester.start();
        try {
            final IOSession proxySession = awaitProxySession(requester, proxy, timeout);

            final IOSession tunnelA = awaitTunnelSession(
                    proxySession,
                    target,
                    timeout,
                    true,
                    createTunnelTlsStrategy());
            assertOriginResponse(executeGet(tunnelA, target, "/a"));

            final IOSession tunnelB = awaitTunnelSession(
                    proxySession,
                    target,
                    timeout,
                    true,
                    createTunnelTlsStrategy());
            assertOriginResponse(executeGet(tunnelB, target, "/b"));

            tunnelA.close(CloseMode.IMMEDIATE);
            Assertions.assertTrue(proxySession.isOpen(),
                    "Closing one tunnel must not close the shared proxy HTTP/2 connection");

            assertOriginResponse(executeGet(tunnelB, target, "/still-open"));
            tunnelB.close(CloseMode.GRACEFUL);
        } finally {
            requester.close(CloseMode.GRACEFUL);
        }
    }

    private static IOEventHandlerFactory tunnelProtocolStarter() {
        return (ioSession, attachment) -> new ClientH2PrefaceHandler(
                ioSession,
                new ClientH2StreamMultiplexerFactory(
                        HttpProcessorBuilder.create().build(),
                        null,
                        H2Config.DEFAULT,
                        CharCodingConfig.DEFAULT,
                        null),
                false,
                null);
    }

    private static IOSession awaitProxySession(
            final H2MultiplexingRequester requester,
            final HttpHost proxy,
            final Timeout timeout) throws Exception {

        final CompletableFuture<IOSession> resultFuture = new CompletableFuture<>();
        requester.getConnPool().getSession(proxy, timeout, new FutureCallback<IOSession>() {

            @Override
            public void completed(final IOSession result) {
                resultFuture.complete(result);
            }

            @Override
            public void failed(final Exception ex) {
                resultFuture.completeExceptionally(ex);
            }

            @Override
            public void cancelled() {
                resultFuture.cancel(false);
            }

        });
        return resultFuture.get(1, TimeUnit.MINUTES);
    }

    private static IOSession awaitTunnelSession(
            final IOSession proxySession,
            final HttpHost target,
            final Timeout timeout,
            final boolean secure,
            final TlsStrategy tlsStrategy) throws Exception {

        final CompletableFuture<IOSession> resultFuture = new CompletableFuture<>();
        H2OverH2TunnelSupport.establish(
                proxySession,
                target,
                timeout,
                secure,
                tlsStrategy,
                tunnelProtocolStarter(),
                new FutureCallback<IOSession>() {

                    @Override
                    public void completed(final IOSession result) {
                        resultFuture.complete(result);
                    }

                    @Override
                    public void failed(final Exception ex) {
                        resultFuture.completeExceptionally(ex);
                    }

                    @Override
                    public void cancelled() {
                        resultFuture.cancel(false);
                    }

                });
        try {
            return resultFuture.get(1, TimeUnit.MINUTES);
        } catch (final Exception ex) {
            final String proxyLogs = proxyContainer != null ? proxyContainer.getLogs() : "<proxy not started>";
            final String originLogs = originContainer != null ? originContainer.getLogs() : "<origin not started>";
            throw new IllegalStateException(
                    "Tunnel establishment failed. Proxy logs:\n" + proxyLogs + "\nOrigin logs:\n" + originLogs,
                    ex);
        }
    }

    private static Message<HttpResponse, String> executeGet(
            final IOSession tunnelSession,
            final HttpHost target,
            final String requestUri) throws Exception {

        final CompletableFuture<Message<HttpResponse, String>> responseFuture = new CompletableFuture<>();

        final AsyncClientExchangeHandler exchangeHandler = new org.apache.hc.core5.http.nio.support.BasicClientExchangeHandler<>(
                new BasicRequestProducer(Method.GET, target, requestUri),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()),
                new FutureCallback<Message<HttpResponse, String>>() {

                    @Override
                    public void completed(final Message<HttpResponse, String> message) {
                        responseFuture.complete(message);
                    }

                    @Override
                    public void failed(final Exception ex) {
                        responseFuture.completeExceptionally(ex);
                    }

                    @Override
                    public void cancelled() {
                        responseFuture.cancel(false);
                    }

                });

        tunnelSession.enqueue(
                new RequestExecutionCommand(exchangeHandler, HttpCoreContext.create()),
                Command.Priority.NORMAL);

        try {
            return responseFuture.get(1, TimeUnit.MINUTES);
        } catch (final Exception ex) {
            final String proxyLogs = proxyContainer != null ? proxyContainer.getLogs() : "<proxy not started>";
            final String originLogs = originContainer != null ? originContainer.getLogs() : "<origin not started>";
            throw new IllegalStateException(
                    "Tunnel request failed. Proxy logs:\n" + proxyLogs + "\nOrigin logs:\n" + originLogs,
                    ex);
        }
    }

    private static TlsStrategy createTunnelTlsStrategy() throws Exception {
        final SSLContext sslContext = SSLContexts.custom()
                .loadTrustMaterial(null, (chain, authType) -> true)
                .build();
        return new H2ClientTlsStrategy(sslContext);
    }

    private static void assertOriginResponse(final Message<HttpResponse, String> message) {
        Assertions.assertNotNull(message);
        Assertions.assertNotNull(message.getHead());
        Assertions.assertEquals(HttpStatus.SC_OK, message.getHead().getCode());
        Assertions.assertNotNull(message.getBody());
        Assertions.assertEquals(ORIGIN_BODY, message.getBody().trim());
    }

}