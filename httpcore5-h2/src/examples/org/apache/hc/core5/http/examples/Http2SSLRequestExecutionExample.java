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
package org.apache.hc.core5.http.examples;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http.nio.AsyncClientEndpoint;
import org.apache.hc.core5.http.nio.BasicRequestProducer;
import org.apache.hc.core5.http.nio.BasicResponseConsumer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.frame.RawFrame;
import org.apache.hc.core5.http2.impl.nio.Http2StreamListener;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2RequesterBootstrap;
import org.apache.hc.core5.http2.ssl.H2ClientTlsStrategy;
import org.apache.hc.core5.io.ShutdownType;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.reactor.ssl.SSLSessionVerifier;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.ssl.TrustStrategy;
import org.apache.hc.core5.util.TimeValue;

/**
 * This example demonstrates how to execute HTTP/2 requests over TLS connections.
 */
public class Http2SSLRequestExecutionExample {

    public final static void main(final String[] args) throws Exception {
        // Trust standard CA and those trusted by our custom strategy
        final SSLContext sslcontext = SSLContexts.custom()
                .loadTrustMaterial(new TrustStrategy() {

                    @Override
                    public boolean isTrusted(
                            final X509Certificate[] chain,
                            final String authType) throws CertificateException {
                        final X509Certificate cert = chain[0];
                        return "CN=http2bin.org".equalsIgnoreCase(cert.getSubjectDN().getName());
                    }

                })
                .build();

        // Create and start requester
        H2Config h2Config = H2Config.custom()
                .setPushEnabled(false)
                .build();

        final HttpAsyncRequester requester = H2RequesterBootstrap.bootstrap()
                .setH2Config(h2Config)
                .setTlsStrategy(new H2ClientTlsStrategy(sslcontext, new SSLSessionVerifier() {

                    @Override
                    public TlsDetails verify(final NamedEndpoint endpoint, final SSLEngine sslEngine) throws SSLException {
                        // IMPORTANT
                        // In order for HTTP/2 protocol negotiation to succeed one must allow access
                        // to Java 9 specific properties of SSLEngine via reflection
                        // by adding the following line to the JVM arguments
                        //
                        // --add-opens java.base/sun.security.ssl=ALL-UNNAMED
                        //
                        // or uncomment the method below

                        // return new TlsDetails(sslEngine.getSession(), sslEngine.getApplicationProtocol());
                        return null;
                    }

                }))
                .setStreamListener(new Http2StreamListener() {

                    @Override
                    public void onHeaderInput(final HttpConnection connection, final int streamId, final List<? extends Header> headers) {
                        for (int i = 0; i < headers.size(); i++) {
                            System.out.println(connection + " (" + streamId + ") << " + headers.get(i));
                        }
                    }

                    @Override
                    public void onHeaderOutput(final HttpConnection connection, final int streamId, final List<? extends Header> headers) {
                        for (int i = 0; i < headers.size(); i++) {
                            System.out.println(connection + " (" + streamId + ") >> " + headers.get(i));
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
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                System.out.println("HTTP requester shutting down");
                requester.shutdown(ShutdownType.GRACEFUL);
            }
        });
        requester.start();

        HttpHost target = new HttpHost("http2bin.org", 443, "https");
        String[] requestUris = new String[] {"/", "/ip", "/user-agent", "/headers"};

        final CountDownLatch latch = new CountDownLatch(requestUris.length);
        for (final String requestUri: requestUris) {
            final Future<AsyncClientEndpoint> future = requester.connect(target, TimeValue.ofSeconds(5));
            final AsyncClientEndpoint clientEndpoint = future.get();
            clientEndpoint.execute(
                    new BasicRequestProducer("GET", target, requestUri),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()),
                    new FutureCallback<Message<HttpResponse, String>>() {

                        @Override
                        public void completed(final Message<HttpResponse, String> message) {
                            clientEndpoint.releaseAndReuse();
                            HttpResponse response = message.getHead();
                            String body = message.getBody();
                            System.out.println(requestUri + "->" + response.getCode() + " " + response.getVersion());
                            System.out.println(body);
                            latch.countDown();
                        }

                        @Override
                        public void failed(final Exception ex) {
                            clientEndpoint.releaseAndDiscard();
                            System.out.println(requestUri + "->" + ex);
                            latch.countDown();
                        }

                        @Override
                        public void cancelled() {
                            clientEndpoint.releaseAndDiscard();
                            System.out.println(requestUri + " cancelled");
                            latch.countDown();
                        }

                    });
        }

        latch.await();
        System.out.println("Shutting down I/O reactor");
        requester.initiateShutdown();
    }

}
