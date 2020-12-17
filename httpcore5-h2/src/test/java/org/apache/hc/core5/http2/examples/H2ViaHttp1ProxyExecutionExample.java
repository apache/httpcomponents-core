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

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.concurrent.ComplexFuture;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.concurrent.FutureContribution;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.impl.Http1StreamListener;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.message.RequestLine;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.http.nio.AsyncClientEndpoint;
import org.apache.hc.core5.http.nio.entity.DiscardingEntityConsumer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.ssl.TlsUpgradeCapable;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.frame.RawFrame;
import org.apache.hc.core5.http2.impl.nio.H2StreamListener;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2RequesterBootstrap;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.util.Timeout;

/**
 * Example of asynchronous HTTP/2 request execution via a HTTP/1.1 proxy.
 */
public class H2ViaHttp1ProxyExecutionExample {

    public static void main(final String[] args) throws Exception {

        // Create and start requester
        final H2Config h2Config = H2Config.custom()
                .setPushEnabled(false)
                .build();

        final HttpAsyncRequester requester = H2RequesterBootstrap.bootstrap()
                .setH2Config(h2Config)
                .setVersionPolicy(HttpVersionPolicy.NEGOTIATE)
                .setStreamListener(new Http1StreamListener() {

                    @Override
                    public void onRequestHead(final HttpConnection connection, final HttpRequest request) {
                        System.out.println(connection.getRemoteAddress() + " " + new RequestLine(request));
                    }

                    @Override
                    public void onResponseHead(final HttpConnection connection, final HttpResponse response) {
                        System.out.println(connection.getRemoteAddress() + " " + new StatusLine(response));
                    }

                    @Override
                    public void onExchangeComplete(final HttpConnection connection, final boolean keepAlive) {
                        if (keepAlive) {
                            System.out.println(connection.getRemoteAddress() + " exchange completed (connection kept alive)");
                        } else {
                            System.out.println(connection.getRemoteAddress() + " exchange completed (connection closed)");
                        }
                    }

                })
                .setStreamListener(new H2StreamListener() {

                    @Override
                    public void onHeaderInput(final HttpConnection connection, final int streamId, final List<? extends Header> headers) {
                        for (int i = 0; i < headers.size(); i++) {
                            System.out.println(connection.getRemoteAddress() + " (" + streamId + ") << " + headers.get(i));
                        }
                    }

                    @Override
                    public void onHeaderOutput(final HttpConnection connection, final int streamId, final List<? extends Header> headers) {
                        for (int i = 0; i < headers.size(); i++) {
                            System.out.println(connection.getRemoteAddress() + " (" + streamId + ") >> " + headers.get(i));
                        }
                    }

                    @Override
                    public void onFrameInput(final HttpConnection connection, final int streamId, final RawFrame frame) {
                    }

                    @Override
                    public void onFrameOutput(final HttpConnection connection, final int streamId, final RawFrame frame) {
                    }

                    @Override
                    public void onInputFlowControl(final HttpConnection connection, final int streamId, final int delta, final int actualSize) {
                    }

                    @Override
                    public void onOutputFlowControl(final HttpConnection connection, final int streamId, final int delta, final int actualSize) {
                    }

                })
                .create();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("HTTP requester shutting down");
            requester.close(CloseMode.GRACEFUL);
        }));
        requester.start();

        final HttpHost proxy = new HttpHost("localhost", 8888);
        final HttpHost target = new HttpHost("https", "nghttp2.org");

        final ComplexFuture<AsyncClientEndpoint> tunnelFuture = new ComplexFuture<>(null);
        tunnelFuture.setDependency(requester.connect(
                proxy,
                Timeout.ofSeconds(30),
                null,
                new FutureContribution<AsyncClientEndpoint>(tunnelFuture) {

                    @Override
                    public void completed(final AsyncClientEndpoint endpoint) {
                        if (endpoint instanceof TlsUpgradeCapable) {
                            final HttpRequest connect = new BasicHttpRequest(Method.CONNECT, proxy, target.toHostString());
                            endpoint.execute(
                                    new BasicRequestProducer(connect, null),
                                    new BasicResponseConsumer<>(new DiscardingEntityConsumer<>()),
                                    new FutureContribution<Message<HttpResponse, Void>>(tunnelFuture) {

                                        @Override
                                        public void completed(final Message<HttpResponse, Void> message) {
                                            final HttpResponse response = message.getHead();
                                            if (response.getCode() == HttpStatus.SC_OK) {
                                                ((TlsUpgradeCapable) endpoint).tlsUpgrade(
                                                        target,
                                                        new FutureContribution<ProtocolIOSession>(tunnelFuture) {

                                                            @Override
                                                            public void completed(final ProtocolIOSession protocolSession) {
                                                                System.out.println("Tunnel to " + target + " via " + proxy + " established");
                                                                tunnelFuture.completed(endpoint);
                                                            }

                                                        });
                                            } else {
                                                tunnelFuture.failed(new HttpException("Tunnel refused: " + new StatusLine(response)));
                                            }
                                        }

                                    });
                        } else {
                            tunnelFuture.failed(new IllegalStateException("TLS upgrade not supported"));
                        }
                    }

                }));

        final String[] requestUris = new String[] {"/httpbin/ip", "/httpbin/user-agent", "/httpbin/headers"};
        final AsyncClientEndpoint endpoint = tunnelFuture.get(1, TimeUnit.MINUTES);
        try {
            final CountDownLatch latch = new CountDownLatch(requestUris.length);
            for (final String requestUri : requestUris) {
                endpoint.execute(
                        new BasicRequestProducer(Method.GET, target, requestUri),
                        new BasicResponseConsumer<>(new StringAsyncEntityConsumer()),
                        new FutureCallback<Message<HttpResponse, String>>() {

                            @Override
                            public void completed(final Message<HttpResponse, String> message) {
                                final HttpResponse response = message.getHead();
                                final String body = message.getBody();
                                System.out.println(requestUri + "->" + response.getCode());
                                System.out.println(body);
                                latch.countDown();
                            }

                            @Override
                            public void failed(final Exception ex) {
                                System.out.println(requestUri + "->" + ex);
                                latch.countDown();
                            }

                            @Override
                            public void cancelled() {
                                System.out.println(requestUri + " cancelled");
                                latch.countDown();
                            }

                        });
            }

            latch.await();
        } finally {
            endpoint.releaseAndDiscard();
        }

        System.out.println("Shutting down I/O reactor");
        requester.initiateShutdown();
    }

}
