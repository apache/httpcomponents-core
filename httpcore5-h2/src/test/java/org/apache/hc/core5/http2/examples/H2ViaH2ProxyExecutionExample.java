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
package org.apache.hc.core5.http2.examples;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import org.apache.hc.core5.concurrent.ComplexFuture;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.concurrent.FutureContribution;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http.nio.command.RequestExecutionCommand;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
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

/**
 * Full example of HTTP/2 request execution through an HTTP/2 proxy tunnel.
 */
public class H2ViaH2ProxyExecutionExample {

    private static TlsStrategy createTlsStrategy() throws Exception {
        final String trustStore = System.getProperty("h2.truststore");
        if (trustStore == null || trustStore.isEmpty()) {
            return new H2ClientTlsStrategy();
        }
        final String trustStorePassword = System.getProperty("h2.truststore.password", "changeit");
        final SSLContext sslContext = SSLContexts.custom()
                .loadTrustMaterial(new File(trustStore), trustStorePassword.toCharArray())
                .build();
        return new H2ClientTlsStrategy(sslContext);
    }

    public static void main(final String[] args) throws Exception {
        final String proxyScheme = System.getProperty("h2.proxy.scheme", "http");
        final String proxyHost = System.getProperty("h2.proxy.host", "localhost");
        final int proxyPort = Integer.parseInt(System.getProperty("h2.proxy.port", "8080"));
        final String targetScheme = System.getProperty("h2.target.scheme", "https");
        final String targetHost = System.getProperty("h2.target.host", "origin");
        final int targetPort = Integer.parseInt(System.getProperty("h2.target.port", "9443"));
        final String[] requestUris = System.getProperty("h2.paths", "/").split(",");

        final TlsStrategy tlsStrategy = createTlsStrategy();

        final H2MultiplexingRequester requester = H2MultiplexingRequesterBootstrap.bootstrap()
                .setH2Config(H2Config.custom().setPushEnabled(false).build())
                .setTlsStrategy(tlsStrategy)
                .create();
        requester.start();

        final HttpHost proxy = new HttpHost(proxyScheme, proxyHost, proxyPort);
        final HttpHost target = new HttpHost(targetScheme, targetHost, targetPort);
        final Timeout timeout = Timeout.ofSeconds(30);

        final IOEventHandlerFactory tunnelProtocolStarter = (ioSession, attachment) ->
                new ClientH2PrefaceHandler(ioSession, new ClientH2StreamMultiplexerFactory(
                        HttpProcessorBuilder.create().build(),
                        null,
                        H2Config.DEFAULT,
                        org.apache.hc.core5.http.config.CharCodingConfig.DEFAULT,
                        null), false, null);

        final ComplexFuture<IOSession> tunnelFuture = new ComplexFuture<>(null);
        tunnelFuture.setDependency(requester.getConnPool().getSession(proxy, timeout, new FutureContribution<IOSession>(tunnelFuture) {

            @Override
            public void completed(final IOSession proxySession) {
                H2OverH2TunnelSupport.establish(
                        proxySession,
                        target,
                        timeout,
                        true,
                        tlsStrategy,
                        tunnelProtocolStarter,
                        new FutureContribution<IOSession>(tunnelFuture) {

                            @Override
                            public void completed(final IOSession tunnelSession) {
                                tunnelFuture.completed(tunnelSession);
                            }
                        });
            }

        }));

        final IOSession tunnelSession = tunnelFuture.get(1, TimeUnit.MINUTES);
        try {
            final CountDownLatch latch = new CountDownLatch(requestUris.length);

            for (final String requestUri : requestUris) {
                final String normalizedRequestUri = requestUri.trim();
                final AsyncClientExchangeHandler exchangeHandler = new org.apache.hc.core5.http.nio.support.BasicClientExchangeHandler<>(
                        new BasicRequestProducer(Method.GET, target, normalizedRequestUri),
                        new BasicResponseConsumer<>(new StringAsyncEntityConsumer()),
                        new FutureCallback<Message<HttpResponse, String>>() {

                            @Override
                            public void completed(final Message<HttpResponse, String> message) {
                                final HttpResponse response = message.getHead();
                                final String body = message.getBody();
                                System.out.println(normalizedRequestUri + " -> " + response.getCode());
                                System.out.println(body);
                                latch.countDown();
                            }

                            @Override
                            public void failed(final Exception ex) {
                                System.out.println(normalizedRequestUri + " -> " + ex);
                                latch.countDown();
                            }

                            @Override
                            public void cancelled() {
                                System.out.println(normalizedRequestUri + " cancelled");
                                latch.countDown();
                            }

                        });

                tunnelSession.enqueue(
                        new RequestExecutionCommand(exchangeHandler, HttpCoreContext.create()),
                        Command.Priority.NORMAL);
            }
            latch.await();
        } finally {
            tunnelSession.close(CloseMode.GRACEFUL);
        }
        requester.close(CloseMode.GRACEFUL);
    }

}
