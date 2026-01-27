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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.config.H2PingPolicy;
import org.apache.hc.core5.http2.frame.FrameFlag;
import org.apache.hc.core5.http2.frame.FrameType;
import org.apache.hc.core5.http2.frame.RawFrame;
import org.apache.hc.core5.http2.impl.nio.H2StreamListener;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2RequesterBootstrap;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.Timeout;

/**
 * Minimal example demonstrating HTTP/2 connection keepalive using {@link H2PingPolicy}.
 * <p>
 * The client configures an idle timeout and an ACK timeout. When the underlying HTTP/2
 * connection becomes idle, the I/O reactor triggers a keepalive {@code PING}. If the
 * peer responds with {@code PING[ACK]} within the configured ACK timeout, the connection
 * remains usable; otherwise the connection is considered dead and is terminated by the
 * transport.
 * </p>
 * <p>
 * This example performs a single request to establish the connection and then waits
 * long enough for one keepalive round-trip. It prints:
 * </p>
 * <ul>
 *   <li>the remote endpoint once,</li>
 *   <li>{@code >> PING} when a keepalive PING is emitted,</li>
 *   <li>{@code << PING[ACK]} when the ACK is received,</li>
 *   <li>a final counter line {@code keepalive: pingsOut=..., pingAcksIn=...}.</li>
 * </ul>
 * <p>
 * Notes:
 * </p>
 * <ul>
 *   <li>This is intentionally not a unit test; it is a runnable sanity-check and usage example.</li>
 *   <li>Keepalive requires HTTP/2 settings negotiation to complete; PINGs may not be emitted
 *       immediately on startup.</li>
 *   <li>Timing is inherently environment-dependent; adjust {@code idleTime}/{@code ackTimeout}
 *       if running on a slow or heavily loaded machine.</li>
 * </ul>
 * @since 5.5
 */

public class H2KeepAlivePingClientExample {

    public static void main(final String[] args) throws Exception {

        final Timeout idleTime = Timeout.ofSeconds(1);
        final Timeout ackTimeout = Timeout.ofSeconds(2);

        final IOReactorConfig ioReactorConfig = IOReactorConfig.custom()
                .setSoTimeout(5, TimeUnit.SECONDS)
                .build();

        final H2PingPolicy pingPolicy = H2PingPolicy.custom()
                .setIdleTime(idleTime)
                .setAckTimeout(ackTimeout)
                .build();

        final H2Config h2Config = H2Config.custom()
                .setPushEnabled(false)
                .setMaxConcurrentStreams(100)
                .setPingPolicy(pingPolicy)
                .build();

        final AtomicBoolean remotePrinted = new AtomicBoolean(false);
        final AtomicInteger pingsOut = new AtomicInteger(0);
        final AtomicInteger pingAcksIn = new AtomicInteger(0);
        final CountDownLatch pingAckLatch = new CountDownLatch(1);

        final HttpAsyncRequester requester = H2RequesterBootstrap.bootstrap()
                .setIOReactorConfig(ioReactorConfig)
                .setH2Config(h2Config)
                .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_2)
                .setStreamListener(new H2StreamListener() {

                    private void printRemoteOnce(final HttpConnection connection) {
                        if (remotePrinted.compareAndSet(false, true)) {
                            System.out.println("remote=" + connection.getRemoteAddress());
                        }
                    }

                    @Override
                    public void onHeaderInput(final HttpConnection connection, final int streamId, final List<? extends Header> headers) {
                    }

                    @Override
                    public void onHeaderOutput(final HttpConnection connection, final int streamId, final List<? extends Header> headers) {
                    }

                    @Override
                    public void onFrameInput(final HttpConnection connection, final int streamId, final RawFrame frame) {
                        printRemoteOnce(connection);
                        if (FrameType.valueOf(frame.getType()) == FrameType.PING && frame.isFlagSet(FrameFlag.ACK)) {
                            System.out.println("<< PING[ACK]");
                            pingAcksIn.incrementAndGet();
                            pingAckLatch.countDown();
                        }
                    }

                    @Override
                    public void onFrameOutput(final HttpConnection connection, final int streamId, final RawFrame frame) {
                        printRemoteOnce(connection);
                        if (FrameType.valueOf(frame.getType()) == FrameType.PING && !frame.isFlagSet(FrameFlag.ACK)) {
                            System.out.println(">> PING");
                            pingsOut.incrementAndGet();
                        }
                    }

                    @Override
                    public void onInputFlowControl(final HttpConnection connection, final int streamId, final int delta, final int actualSize) {
                    }

                    @Override
                    public void onOutputFlowControl(final HttpConnection connection, final int streamId, final int delta, final int actualSize) {
                    }

                })
                .create();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> requester.close(CloseMode.GRACEFUL)));

        requester.start();

        final URI requestUri = new URI("http://nghttp2.org/httpbin/post");
        final AsyncRequestProducer requestProducer = AsyncRequestBuilder.post(requestUri)
                .setEntity("stuff")
                .build();
        final BasicResponseConsumer<String> responseConsumer = new BasicResponseConsumer<>(new StringAsyncEntityConsumer());

        final CountDownLatch exchangeLatch = new CountDownLatch(1);

        requester.execute(new AsyncClientExchangeHandler() {

            @Override
            public void releaseResources() {
                requestProducer.releaseResources();
                responseConsumer.releaseResources();
                exchangeLatch.countDown();
            }

            @Override
            public void cancel() {
                exchangeLatch.countDown();
            }

            @Override
            public void failed(final Exception cause) {
                exchangeLatch.countDown();
            }

            @Override
            public void produceRequest(final RequestChannel channel, final HttpContext httpContext) throws HttpException, IOException {
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
            public void consumeInformation(final HttpResponse response, final HttpContext httpContext) {
            }

            @Override
            public void consumeResponse(final HttpResponse response, final EntityDetails entityDetails, final HttpContext httpContext) throws HttpException, IOException {
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
            public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
                responseConsumer.streamEnd(trailers);
            }

        }, Timeout.ofSeconds(30), HttpCoreContext.create());

        exchangeLatch.await();

        final long waitMs = idleTime.toMilliseconds() + ackTimeout.toMilliseconds() + 500L;
        pingAckLatch.await(waitMs, TimeUnit.MILLISECONDS);

        System.out.println("keepalive: pingsOut=" + pingsOut.get() + ", pingAcksIn=" + pingAcksIn.get());

        requester.close(CloseMode.GRACEFUL);
    }

}
