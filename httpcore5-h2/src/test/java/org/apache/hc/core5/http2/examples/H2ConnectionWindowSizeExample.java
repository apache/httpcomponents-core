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

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.impl.routing.RequestRouter;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.AsyncClientEndpoint;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.entity.DiscardingEntityConsumer;
import org.apache.hc.core5.http.nio.support.AsyncClientPipeline;
import org.apache.hc.core5.http.nio.support.AsyncServerPipeline;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.frame.RawFrame;
import org.apache.hc.core5.http2.impl.nio.H2StreamListener;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2RequesterBootstrap;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2ServerBootstrap;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

/**
 * HTTP/2 client demo illustrating the effect of the connection-level receive
 * window on a multiplexed multi-stream exchange while keeping the per-stream
 * initial window constant.
 *
 * @since 5.7
 */
public final class H2ConnectionWindowSizeExample {

    private static final int STREAM_COUNT = 128;
    private static final int RESPONSE_SIZE = 2 * 1024 * 1024;

    private static final int STREAM_WINDOW_SIZE = 1024 * 1024;
    private static final int SMALL_CONNECTION_WINDOW = 65_535;
    private static final int LARGE_CONNECTION_WINDOW = 16 * 1024 * 1024;

    private static final Timeout CONNECT_TIMEOUT = Timeout.ofSeconds(30);
    private static final long COMPLETION_TIMEOUT_MINUTES = 5;

    private H2ConnectionWindowSizeExample() {
    }

    public static void main(final String[] args) throws Exception {
        final String payload = createPayload(RESPONSE_SIZE);
        final ServerHandle serverHandle = startServer(payload);
        try {
            System.out.println("Listening on " + serverHandle.port);
            System.out.println("Streams: " + STREAM_COUNT);
            System.out.println("Response size per stream: " + RESPONSE_SIZE + " bytes");
            System.out.println("Per-stream initial window: " + STREAM_WINDOW_SIZE + " bytes");
            System.out.println();

            final RunResult small = runScenario(serverHandle.port, SMALL_CONNECTION_WINDOW);
            final RunResult large = runScenario(serverHandle.port, LARGE_CONNECTION_WINDOW);

            printResult("small connection window", small);
            printResult("large connection window", large);
        } finally {
            serverHandle.server.close(CloseMode.GRACEFUL);
            serverHandle.server.awaitShutdown(TimeValue.ofSeconds(5));
        }
    }

    private static ServerHandle startServer(final String payload) throws Exception {
        final H2Config serverH2Config = H2Config.custom()
                .setPushEnabled(false)
                .setMaxConcurrentStreams(STREAM_COUNT)
                .build();

        final Supplier<AsyncServerExchangeHandler> exchangeHandlerSupplier = AsyncServerPipeline.assemble()
                .request(Method.GET)
                .<Void>consumeContent(contentType -> DiscardingEntityConsumer::new)
                .response()
                .asString(ContentType.TEXT_PLAIN)
                .handle((request, context) -> Message.of(
                        new BasicHttpResponse(HttpStatus.SC_OK),
                        payload))
                .supplier();

        final HttpAsyncServer server = H2ServerBootstrap.bootstrap()
                .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_2)
                .setH2Config(serverH2Config)
                .setRequestRouter(RequestRouter.<Supplier<AsyncServerExchangeHandler>>builder()
                        .addRoute(RequestRouter.LOCAL_AUTHORITY, "*", exchangeHandlerSupplier)
                        .resolveAuthority(RequestRouter.LOCAL_AUTHORITY_RESOLVER)
                        .build())
                .create();

        server.start();
        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(0), URIScheme.HTTP);
        final ListenerEndpoint listenerEndpoint = future.get();
        final InetSocketAddress address = (InetSocketAddress) listenerEndpoint.getAddress();
        return new ServerHandle(server, address.getPort());
    }

    private static RunResult runScenario(final int port, final int connectionWindowSize) throws Exception {
        final IOReactorConfig ioReactorConfig = IOReactorConfig.custom()
                .setSoTimeout(30, TimeUnit.SECONDS)
                .setTcpNoDelay(true)
                .build();

        final H2Config h2Config = H2Config.custom()
                .setPushEnabled(false)
                .setInitialWindowSize(STREAM_WINDOW_SIZE)
                .setConnectionWindowSize(connectionWindowSize)
                .setMaxConcurrentStreams(STREAM_COUNT)
                .build();

        final AtomicInteger inputConnFlowControlEvents = new AtomicInteger();
        final AtomicInteger failures = new AtomicInteger();

        final HttpAsyncRequester requester = H2RequesterBootstrap.bootstrap()
                .setIOReactorConfig(ioReactorConfig)
                .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_2)
                .setH2Config(h2Config)
                .setStreamListener(new H2StreamListener() {

                    @Override
                    public void onHeaderInput(
                            final HttpConnection connection,
                            final int streamId,
                            final List<? extends Header> headers) {
                    }

                    @Override
                    public void onHeaderOutput(
                            final HttpConnection connection,
                            final int streamId,
                            final List<? extends Header> headers) {
                    }

                    @Override
                    public void onFrameInput(
                            final HttpConnection connection,
                            final int streamId,
                            final RawFrame frame) {
                    }

                    @Override
                    public void onFrameOutput(
                            final HttpConnection connection,
                            final int streamId,
                            final RawFrame frame) {
                    }

                    @Override
                    public void onInputFlowControl(
                            final HttpConnection connection,
                            final int streamId,
                            final int delta,
                            final int actualSize) {
                        if (streamId == 0) {
                            inputConnFlowControlEvents.incrementAndGet();
                        }
                    }

                    @Override
                    public void onOutputFlowControl(
                            final HttpConnection connection,
                            final int streamId,
                            final int delta,
                            final int actualSize) {
                    }
                })
                .create();

        requester.start();
        try {
            final HttpHost target = new HttpHost("http", "localhost", port);
            final Future<AsyncClientEndpoint> future = requester.connect(target, CONNECT_TIMEOUT);
            final AsyncClientEndpoint clientEndpoint = future.get();

            try {
                final CountDownLatch latch = new CountDownLatch(STREAM_COUNT);
                final long started = System.nanoTime();

                for (int i = 0; i < STREAM_COUNT; i++) {
                    final String requestUri = "/bytes/" + i;
                    clientEndpoint.execute(
                            AsyncClientPipeline.assemble()
                                    .request()
                                    .get(target, requestUri)
                                    .response()
                                    .<Void>consumeContent(contentType -> DiscardingEntityConsumer::new)
                                    .result(new FutureCallback<Message<HttpResponse, Void>>() {

                                        @Override
                                        public void completed(final Message<HttpResponse, Void> message) {
                                            try {
                                                final HttpResponse response = message.head();
                                                if (response.getCode() != HttpStatus.SC_OK) {
                                                    failures.incrementAndGet();
                                                    System.out.println(requestUri + " -> unexpected status: " + response.getCode());
                                                }
                                            } finally {
                                                latch.countDown();
                                            }
                                        }

                                        @Override
                                        public void failed(final Exception ex) {
                                            failures.incrementAndGet();
                                            ex.printStackTrace(System.out);
                                            latch.countDown();
                                        }

                                        @Override
                                        public void cancelled() {
                                            failures.incrementAndGet();
                                            System.out.println(requestUri + " cancelled");
                                            latch.countDown();
                                        }
                                    })
                                    .create(),
                            null,
                            HttpCoreContext.create());
                }

                final boolean completed = latch.await(COMPLETION_TIMEOUT_MINUTES, TimeUnit.MINUTES);
                final long elapsedNanos = System.nanoTime() - started;

                if (!completed) {
                    throw new IllegalStateException("Timed out waiting for responses");
                }
                if (failures.get() > 0) {
                    throw new IllegalStateException("Scenario failed with " + failures.get() + " error(s)");
                }

                return new RunResult(
                        connectionWindowSize,
                        elapsedNanos,
                        inputConnFlowControlEvents.get());
            } finally {
                clientEndpoint.releaseAndDiscard();
            }
        } finally {
            requester.close(CloseMode.GRACEFUL);
        }
    }

    private static void printResult(final String label, final RunResult result) {
        System.out.println(label + ":");
        System.out.println("  connection window size: " + result.connectionWindowSize + " bytes");
        System.out.printf("  elapsed: %.3f s%n", nanosToSeconds(result.elapsedNanos));
        System.out.println("  stream-0 input flow-control events: " + result.inputConnFlowControlEvents);
        System.out.println();
    }

    private static double nanosToSeconds(final long nanos) {
        return nanos / 1_000_000_000.0;
    }

    private static String createPayload(final int size) {
        final StringBuilder buffer = new StringBuilder(size);
        while (buffer.length() < size) {
            buffer.append("0123456789abcdef");
        }
        if (buffer.length() > size) {
            buffer.setLength(size);
        }
        return buffer.toString();
    }

    private static final class ServerHandle {

        private final HttpAsyncServer server;
        private final int port;

        private ServerHandle(final HttpAsyncServer server, final int port) {
            this.server = server;
            this.port = port;
        }
    }

    private static final class RunResult {

        private final int connectionWindowSize;
        private final long elapsedNanos;
        private final int inputConnFlowControlEvents;

        private RunResult(
                final int connectionWindowSize,
                final long elapsedNanos,
                final int inputConnFlowControlEvents) {
            this.connectionWindowSize = connectionWindowSize;
            this.elapsedNanos = elapsedNanos;
            this.inputConnFlowControlEvents = inputConnFlowControlEvents;
        }
    }
}