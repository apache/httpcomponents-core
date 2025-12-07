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

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.RequestChannel;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.support.AsyncRequestBuilder;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http2.H2StreamResetException;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.frame.RawFrame;
import org.apache.hc.core5.http2.impl.nio.H2StreamListener;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2RequesterBootstrap;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.Timeout;

/**
 * Example of an HTTP/2 client where a "slow" request gets aborted when the
 * underlying HTTP/2 connection times out due to inactivity (socket timeout).
 * <p>
 * The client opens a single HTTP/2 connection to {@code nghttp2.org} and
 * executes two concurrent requests:
 * <ul>
 *     <li>a "fast" request ({@code /httpbin/ip}), which completes before
 *     the connection idle timeout, and</li>
 *     <li>a "slow" request ({@code /httpbin/delay/5}), which keeps the
 *     connection idle long enough for the I/O reactor to trigger a timeout
 *     and close the HTTP/2 connection.</li>
 * </ul>
 * <p>
 * When the reactor closes the connection due to inactivity, all active
 * streams fail with {@link H2StreamResetException} reporting
 * {@code "Timeout due to inactivity (...)"}. The already completed stream
 * is not affected.
 *
 * @since 5.4
 */
public class H2StreamTimeoutClientExample {

    public static void main(final String[] args) throws Exception {

        final IOReactorConfig ioReactorConfig = IOReactorConfig.custom()
                // Connection-level inactivity timeout: keep it short so that
                // /httpbin/delay/5 reliably triggers it.
                .setSoTimeout(2, TimeUnit.SECONDS)
                .build();

        final H2Config h2Config = H2Config.custom()
                .setPushEnabled(false)
                .setMaxConcurrentStreams(100)
                .build();

        final HttpAsyncRequester requester = H2RequesterBootstrap.bootstrap()
                .setIOReactorConfig(ioReactorConfig)
                .setH2Config(h2Config)
                .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_2)
                .setStreamListener(new H2StreamListener() {

                    @Override
                    public void onHeaderInput(
                            final HttpConnection connection,
                            final int streamId,
                            final List<? extends Header> headers) {
                        for (int i = 0; i < headers.size(); i++) {
                            System.out.println(connection.getRemoteAddress()
                                    + " (" + streamId + ") << " + headers.get(i));
                        }
                    }

                    @Override
                    public void onHeaderOutput(
                            final HttpConnection connection,
                            final int streamId,
                            final List<? extends Header> headers) {
                        for (int i = 0; i < headers.size(); i++) {
                            System.out.println(connection.getRemoteAddress()
                                    + " (" + streamId + ") >> " + headers.get(i));
                        }
                    }

                    @Override
                    public void onFrameInput(
                            final HttpConnection connection,
                            final int streamId,
                            final RawFrame frame) {
                        // No-op in this example.
                    }

                    @Override
                    public void onFrameOutput(
                            final HttpConnection connection,
                            final int streamId,
                            final RawFrame frame) {
                        // No-op in this example.
                    }

                    @Override
                    public void onInputFlowControl(
                            final HttpConnection connection,
                            final int streamId,
                            final int delta,
                            final int actualSize) {
                        // No-op in this example.
                    }

                    @Override
                    public void onOutputFlowControl(
                            final HttpConnection connection,
                            final int streamId,
                            final int delta,
                            final int actualSize) {
                        // No-op in this example.
                    }

                })
                .create();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("HTTP requester shutting down");
            requester.close(CloseMode.GRACEFUL);
        }));

        requester.start();

        final URI fastUri = new URI("https://nghttp2.org/httpbin/ip");
        final URI slowUri = new URI("https://nghttp2.org/httpbin/delay/5");

        final CountDownLatch latch = new CountDownLatch(2);

        // --- Fast stream: expected to succeed
        executeWithLogging(
                requester,
                fastUri,
                "[fast]",
                latch,
                false);

        // --- Slow stream: /delay/5 sleeps 5 seconds and should exceed
        // the 2-second connection idle timeout, resulting in a reset.
        executeWithLogging(
                requester,
                slowUri,
                "[slow]",
                latch,
                true);

        latch.await();

        System.out.println("Shutting down I/O reactor");
        requester.initiateShutdown();
    }

    private static void executeWithLogging(
            final HttpAsyncRequester requester,
            final URI requestUri,
            final String label,
            final CountDownLatch latch,
            final boolean expectTimeout) {

        final AsyncRequestProducer requestProducer = AsyncRequestBuilder.get(requestUri)
                .build();
        final BasicResponseConsumer<String> responseConsumer = new BasicResponseConsumer<>(
                new StringAsyncEntityConsumer());

        requester.execute(new AsyncClientExchangeHandler() {

            @Override
            public void releaseResources() {
                requestProducer.releaseResources();
                responseConsumer.releaseResources();
                latch.countDown();
            }

            @Override
            public void cancel() {
                System.out.println(label + " " + requestUri + " cancelled");
            }

            @Override
            public void failed(final Exception cause) {
                if (expectTimeout && cause instanceof H2StreamResetException) {
                    final H2StreamResetException ex = (H2StreamResetException) cause;
                    System.out.println(label + " expected timeout reset: "
                            + requestUri
                            + " -> " + ex);
                } else {
                    System.out.println(label + " failure: "
                            + requestUri + " -> " + cause);
                }
            }

            @Override
            public void produceRequest(
                    final RequestChannel channel,
                    final HttpContext httpContext) throws HttpException, IOException {
                System.out.println(label + " sending request: " + requestUri);
                requestProducer.sendRequest(channel, httpContext);
            }

            @Override
            public int available() {
                return requestProducer.available();
            }

            @Override
            public void produce(final DataStreamChannel channel) throws IOException {
                requestProducer.produce(channel);
            }

            @Override
            public void consumeInformation(
                    final HttpResponse response,
                    final HttpContext httpContext) throws HttpException, IOException {
                System.out.println(label + " " + requestUri + " -> informational "
                        + response.getCode());
            }

            @Override
            public void consumeResponse(
                    final HttpResponse response,
                    final EntityDetails entityDetails,
                    final HttpContext httpContext) throws HttpException, IOException {
                if (expectTimeout) {
                    System.out.println(label + " UNEXPECTED success: "
                            + requestUri + " -> " + response.getCode());
                } else {
                    System.out.println(label + " response: "
                            + requestUri + " -> " + response.getCode());
                }
                responseConsumer.consumeResponse(response, entityDetails, httpContext, null);
            }

            @Override
            public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
                responseConsumer.updateCapacity(capacityChannel);
            }

            @Override
            public void consume(final ByteBuffer src) throws IOException {
                responseConsumer.consume(src);
            }

            @Override
            public void streamEnd(final List<? extends Header> trailers)
                    throws HttpException, IOException {
                responseConsumer.streamEnd(trailers);
                if (!expectTimeout) {
                    System.out.println(label + " body completed for " + requestUri);
                }
            }

        }, Timeout.ofSeconds(10), HttpCoreContext.create());
    }

}
