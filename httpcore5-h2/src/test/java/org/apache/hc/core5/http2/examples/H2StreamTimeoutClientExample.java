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
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http.nio.AsyncClientEndpoint;
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
import org.apache.hc.core5.http2.H2StreamTimeoutException;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.frame.RawFrame;
import org.apache.hc.core5.http2.impl.nio.H2StreamListener;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2RequesterBootstrap;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.Timeout;

/**
 * Example of an HTTP/2 client where a "slow" request gets aborted by a
 * per-stream idle timeout enforced by the HTTP/2 multiplexer.
 * <p>
 * The connection socket timeout is set to 2 seconds and is used as the initial / default
 * per-stream idle timeout value. The example keeps the connection active by sending
 * small "keep-alive" requests on separate streams, so the connection itself does not time out.
 * The "slow" stream remains idle long enough to exceed the per-stream idle timeout and gets reset.
 *
 * @since 5.7
 */
public class H2StreamTimeoutClientExample {

    public static void main(final String[] args) throws Exception {

        final IOReactorConfig ioReactorConfig = IOReactorConfig.custom()
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

        final HttpHost target = new HttpHost("https", "nghttp2.org", 443);

        final AsyncClientEndpoint endpoint;
        try {
            endpoint = requester.connect(target, Timeout.ofSeconds(10)).get();
        } catch (final ExecutionException ex) {
            if (ex.getCause() instanceof UnknownHostException) {
                final UnknownHostException uhe = (UnknownHostException) ex.getCause();
                System.out.println("Target host cannot be resolved: " + target + " -> " + uhe.getMessage());
                System.out.println("Shutting down I/O reactor");
                requester.initiateShutdown();
                return;
            }
            throw ex;
        }

        try {
            final URI keepAliveUri = new URI("https://nghttp2.org/httpbin/ip");
            final URI slowUri = new URI("https://nghttp2.org/httpbin/delay/5");

            final CountDownLatch latch = new CountDownLatch(2);
            final AtomicBoolean stop = new AtomicBoolean(false);

            // Keep the connection active with short requests on new streams,
            // so the connection does NOT hit "Timeout due to inactivity".
            final Thread keepAliveThread = new Thread(() -> {
                try {
                    while (!stop.get()) {
                        executeKeepAliveOnce(endpoint, keepAliveUri);
                        Thread.sleep(500);
                    }
                } catch (final Exception ignore) {
                } finally {
                    latch.countDown();
                }
            });
            keepAliveThread.setDaemon(true);
            keepAliveThread.start();

            // Slow stream: should be reset by per-stream idle timeout while the connection stays active.
            executeWithLogging(
                    endpoint,
                    slowUri,
                    "[slow]",
                    latch,
                    stop);

            latch.await(30, TimeUnit.SECONDS);

        } finally {
            endpoint.releaseAndReuse();
            System.out.println("Shutting down I/O reactor");
            requester.initiateShutdown();
        }
    }

    private static void executeKeepAliveOnce(
            final AsyncClientEndpoint endpoint,
            final URI requestUri) throws InterruptedException {

        final AsyncRequestProducer requestProducer = AsyncRequestBuilder.get(requestUri).build();
        final BasicResponseConsumer<String> responseConsumer = new BasicResponseConsumer<>(
                new StringAsyncEntityConsumer());

        final CountDownLatch done = new CountDownLatch(1);

        endpoint.execute(new AsyncClientExchangeHandler() {

            @Override
            public void releaseResources() {
                requestProducer.releaseResources();
                responseConsumer.releaseResources();
                done.countDown();
            }

            @Override
            public void cancel() {
                done.countDown();
            }

            @Override
            public void failed(final Exception cause) {
                done.countDown();
            }

            @Override
            public void produceRequest(
                    final RequestChannel channel,
                    final HttpContext httpContext) throws HttpException, IOException {
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
                // No-op
            }

            @Override
            public void consumeResponse(
                    final HttpResponse response,
                    final EntityDetails entityDetails,
                    final HttpContext httpContext) throws HttpException, IOException {
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
            }

        }, HttpCoreContext.create());

        done.await(5, TimeUnit.SECONDS);
    }

    private static void executeWithLogging(
            final AsyncClientEndpoint endpoint,
            final URI requestUri,
            final String label,
            final CountDownLatch latch,
            final AtomicBoolean stop) {

        final AsyncRequestProducer requestProducer = AsyncRequestBuilder.get(requestUri)
                .build();
        final BasicResponseConsumer<String> responseConsumer = new BasicResponseConsumer<>(
                new StringAsyncEntityConsumer());

        endpoint.execute(new AsyncClientExchangeHandler() {

            @Override
            public void releaseResources() {
                requestProducer.releaseResources();
                responseConsumer.releaseResources();
                stop.set(true);
                latch.countDown();
            }

            @Override
            public void cancel() {
                System.out.println(label + " " + requestUri + " cancelled");
            }

            @Override
            public void failed(final Exception cause) {
                if (cause instanceof H2StreamTimeoutException) {
                    System.out.println(label + " expected per-stream timeout reset: "
                            + requestUri + " -> " + cause);
                } else if (cause instanceof H2StreamResetException) {
                    System.out.println(label + " stream reset: "
                            + requestUri + " -> " + cause);
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
                System.out.println(label + " response: "
                        + requestUri + " -> " + response.getCode());
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
            }

        }, HttpCoreContext.create());
    }

}
