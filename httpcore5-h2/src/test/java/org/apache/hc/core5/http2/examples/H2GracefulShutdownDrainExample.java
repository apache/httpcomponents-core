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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.core5.annotation.Experimental;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.AsyncClientEndpoint;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.ResponseChannel;
import org.apache.hc.core5.http.nio.support.classic.ClassicToAsyncRequestProducer;
import org.apache.hc.core5.http.nio.support.classic.ClassicToAsyncResponseConsumer;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.frame.FrameFlag;
import org.apache.hc.core5.http2.frame.FrameType;
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
 * Example that demonstrates graceful HTTP/2 connection drain.
 * <p>
 * This example starts an embedded HTTP/2 server and an HTTP/2 client, executes
 * a single request over a persistent connection, and then triggers graceful
 * server shutdown.
 * <p>
 * With two-phase GOAWAY drain support in the H2 stream multiplexer, the client
 * side frame log should show:
 * <pre>
 * << GOAWAY lastStreamId=2147483647 errorCode=0
 * << PING ack=false
 * >> PING ack=true
 * << GOAWAY lastStreamId=1 errorCode=0
 * </pre>
 */
@Experimental
public class H2GracefulShutdownDrainExample {

    private static final int PORT = 8080;

    public static void main(final String[] args) throws Exception {

        final IOReactorConfig ioReactorConfig = IOReactorConfig.custom()
                .setSoTimeout(30, TimeUnit.SECONDS)
                .setTcpNoDelay(true)
                .build();

        final H2Config h2Config = H2Config.custom()
                .setPushEnabled(false)
                .setMaxConcurrentStreams(100)
                .build();

        final CountDownLatch finalGoAwayLatch = new CountDownLatch(1);
        final AtomicInteger clientGoAwayCount = new AtomicInteger();

        final HttpAsyncServer server = H2ServerBootstrap.bootstrap()
                .setIOReactorConfig(ioReactorConfig)
                .setH2Config(h2Config)
                .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_2)
                .setStreamListener(new LoggingH2StreamListener("SERVER", null, null))
                .register("/hello", () -> new AsyncServerExchangeHandler() {

                    private final ByteBuffer content = StandardCharsets.UTF_8.encode("hello over h2\n");
                    private volatile boolean responseSubmitted;

                    @Override
                    public void handleRequest(
                            final HttpRequest request,
                            final EntityDetails entityDetails,
                            final ResponseChannel responseChannel,
                            final HttpContext context) throws HttpException, IOException {
                        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_OK);
                        response.setHeader(HttpHeaders.CONTENT_TYPE, ContentType.TEXT_PLAIN.toString());
                        responseChannel.sendResponse(response, null, context);
                        responseSubmitted = true;
                    }

                    @Override
                    public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
                        capacityChannel.update(Integer.MAX_VALUE);
                    }

                    @Override
                    public void consume(final ByteBuffer src) throws IOException {
                        while (src.hasRemaining()) {
                            src.get();
                        }
                    }

                    @Override
                    public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
                    }

                    @Override
                    public int available() {
                        return responseSubmitted ? content.remaining() : 0;
                    }

                    @Override
                    public void produce(final DataStreamChannel channel) throws IOException {
                        if (content.hasRemaining()) {
                            channel.write(content);
                        }
                        if (!content.hasRemaining()) {
                            channel.endStream();
                        }
                    }

                    @Override
                    public void failed(final Exception cause) {
                        cause.printStackTrace(System.out);
                    }

                    @Override
                    public void releaseResources() {
                    }

                })
                .create();

        final HttpAsyncRequester requester = H2RequesterBootstrap.bootstrap()
                .setIOReactorConfig(ioReactorConfig)
                .setH2Config(h2Config)
                .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_2)
                .setStreamListener(new LoggingH2StreamListener("CLIENT", finalGoAwayLatch, clientGoAwayCount))
                .create();

        server.start();
        final Future<ListenerEndpoint> listenerFuture = server.listen(new InetSocketAddress(PORT), URIScheme.HTTP);
        final ListenerEndpoint listenerEndpoint = listenerFuture.get();
        System.out.println("Server listening on " + listenerEndpoint.getAddress());

        requester.start();

        final HttpHost target = new HttpHost("http", "127.0.0.1", PORT);
        final Future<AsyncClientEndpoint> endpointFuture = requester.connect(target, Timeout.ofSeconds(30));
        final AsyncClientEndpoint clientEndpoint = endpointFuture.get();

        final ClassicHttpRequest request = ClassicRequestBuilder.get()
                .setHttpHost(target)
                .setPath("/hello")
                .build();

        final ClassicToAsyncRequestProducer requestProducer =
                new ClassicToAsyncRequestProducer(request, Timeout.ofSeconds(30));
        final ClassicToAsyncResponseConsumer responseConsumer =
                new ClassicToAsyncResponseConsumer(Timeout.ofSeconds(30));

        clientEndpoint.execute(requestProducer, responseConsumer, null);

        requestProducer.blockWaiting().execute();
        try (ClassicHttpResponse response = responseConsumer.blockWaiting()) {
            System.out.println("/hello -> " + response.getCode());
            final HttpEntity entity = response.getEntity();
            if (entity != null) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(entity.getContent(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println(line);
                    }
                }
            }
        }

        System.out.println();
        System.out.println("Triggering graceful server shutdown");
        server.initiateShutdown();

        final boolean completed = finalGoAwayLatch.await(10, TimeUnit.SECONDS);
        System.out.println("Final GOAWAY observed: " + completed);
        if (!completed) {
            throw new IllegalStateException("Did not observe the final GOAWAY frame");
        }

        Thread.sleep(1000);

        System.out.println();
        System.out.println("Triggering requester shutdown");
        requester.initiateShutdown();

        requester.awaitShutdown(TimeValue.ofSeconds(5));
        server.awaitShutdown(TimeValue.ofSeconds(5));

        requester.close(CloseMode.GRACEFUL);
        server.close(CloseMode.GRACEFUL);
    }

    static final class LoggingH2StreamListener implements H2StreamListener {

        private final String name;
        private final CountDownLatch finalGoAwayLatch;
        private final AtomicInteger goAwayCount;

        LoggingH2StreamListener(
                final String name,
                final CountDownLatch finalGoAwayLatch,
                final AtomicInteger goAwayCount) {
            this.name = name;
            this.finalGoAwayLatch = finalGoAwayLatch;
            this.goAwayCount = goAwayCount;
        }

        @Override
        public void onHeaderInput(final HttpConnection connection, final int streamId, final List<? extends Header> headers) {
            for (int i = 0; i < headers.size(); i++) {
                System.out.println(name + " " + connection.getRemoteAddress() + " (" + streamId + ") << " + headers.get(i));
            }
        }

        @Override
        public void onHeaderOutput(final HttpConnection connection, final int streamId, final List<? extends Header> headers) {
            for (int i = 0; i < headers.size(); i++) {
                System.out.println(name + " " + connection.getRemoteAddress() + " (" + streamId + ") >> " + headers.get(i));
            }
        }

        @Override
        public void onFrameInput(final HttpConnection connection, final int streamId, final RawFrame frame) {
            System.out.println(name + " " + connection.getRemoteAddress() + " (" + streamId + ") << " + formatFrame(frame));
            if (finalGoAwayLatch != null && goAwayCount != null && FrameType.valueOf(frame.getType()) == FrameType.GOAWAY) {
                if (goAwayCount.incrementAndGet() == 2) {
                    finalGoAwayLatch.countDown();
                }
            }
        }

        @Override
        public void onFrameOutput(final HttpConnection connection, final int streamId, final RawFrame frame) {
            System.out.println(name + " " + connection.getRemoteAddress() + " (" + streamId + ") >> " + formatFrame(frame));
        }

        @Override
        public void onInputFlowControl(final HttpConnection connection, final int streamId, final int delta, final int actualSize) {
        }

        @Override
        public void onOutputFlowControl(final HttpConnection connection, final int streamId, final int delta, final int actualSize) {
        }

        private static String formatFrame(final RawFrame frame) {
            final FrameType frameType = FrameType.valueOf(frame.getType());
            if (frameType == null) {
                return "UNKNOWN(" + frame.getType() + ")";
            }
            switch (frameType) {
                case GOAWAY: {
                    final ByteBuffer payload = frame.getPayload();
                    if (payload == null || payload.remaining() < 8) {
                        return "GOAWAY invalid";
                    }
                    final ByteBuffer dup = payload.asReadOnlyBuffer();
                    final int lastStreamId = dup.getInt() & 0x7fffffff;
                    final int errorCode = dup.getInt();
                    return "GOAWAY lastStreamId=" + lastStreamId + " errorCode=" + errorCode;
                }
                case PING:
                    return "PING ack=" + frame.isFlagSet(FrameFlag.ACK);
                case SETTINGS:
                    return frame.isFlagSet(FrameFlag.ACK) ? "SETTINGS ack=true" : "SETTINGS ack=false";
                case HEADERS:
                    return "HEADERS endStream=" + frame.isFlagSet(FrameFlag.END_STREAM) +
                            " endHeaders=" + frame.isFlagSet(FrameFlag.END_HEADERS);
                case DATA:
                    return "DATA endStream=" + frame.isFlagSet(FrameFlag.END_STREAM) +
                            " length=" + frame.getLength();
                default:
                    return frameType.name() + " length=" + frame.getLength();
            }
        }

    }

}
