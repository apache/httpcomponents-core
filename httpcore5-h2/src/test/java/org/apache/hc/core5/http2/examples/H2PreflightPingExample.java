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

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.support.AsyncRequestBuilder;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.frame.FrameFlag;
import org.apache.hc.core5.http2.frame.FrameType;
import org.apache.hc.core5.http2.frame.RawFrame;
import org.apache.hc.core5.http2.impl.nio.H2StreamListener;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2MultiplexingRequester;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2MultiplexingRequesterBootstrap;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.IOSessionListener;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

/**
 * HTTP/2 validate-after-inactivity sanity-check for HttpComponents Core (no HttpClient classes).
 * <p>
 * Demonstrates the pre-flight PING stale-check triggered by {@code validateAfterInactivity}:
 * when there are pending commands and the session has been idle longer than the configured
 * threshold, the multiplexer issues a single PING before executing commands.
 * <p>
 * This is not a periodic keep-alive mechanism.
 *
 * @since 5.5
 */
public final class H2PreflightPingExample {

    private static final URI TARGET = URI.create("https://nghttp2.org/httpbin/get");

    private static final class Counters {
        final AtomicInteger sessionsConnected = new AtomicInteger(0);
        final AtomicInteger reactorTimeoutEvents = new AtomicInteger(0);
        final AtomicInteger pingsOut = new AtomicInteger(0);
        final AtomicInteger pingAcksIn = new AtomicInteger(0);
        final AtomicInteger goAwayIn = new AtomicInteger(0);
        final AtomicInteger rstStreamIn = new AtomicInteger(0);
        final AtomicInteger exceptions = new AtomicInteger(0);
    }

    public static void main(final String[] args) throws Exception {

        // Base socket timeout BEFORE any validate-after-inactivity logic.
        final IOReactorConfig ioReactorConfig = IOReactorConfig.custom()
                .setSoTimeout(Timeout.ofSeconds(30))
                .build();

        // Idle threshold (used by the multiplexer to decide whether to pre-flight PING before executing commands).
        final TimeValue validateAfterInactivity = TimeValue.ofSeconds(3);

        final H2Config h2Config = H2Config.custom()
                .setPushEnabled(false)
                .setMaxConcurrentStreams(100)
                .build();

        final Counters counters = new Counters();

        // Suppress ping accounting / logs after we start shutting down, to keep the demo output clean.
        final AtomicBoolean shuttingDown = new AtomicBoolean(false);

        final H2MultiplexingRequester requester = H2MultiplexingRequesterBootstrap.bootstrap()
                .setIOReactorConfig(ioReactorConfig)
                .setH2Config(h2Config)
                .setIOSessionListener(new IOSessionListener() {

                    @Override
                    public void connected(final IOSession session) {
                        counters.sessionsConnected.incrementAndGet();
                        log("session.connected id=" + safeId(session)
                                + " remote=" + session.getRemoteAddress()
                                + " soTimeout=" + session.getSocketTimeout());
                    }

                    @Override
                    public void startTls(final IOSession session) {
                        log("session.startTls id=" + safeId(session)
                                + " soTimeout=" + session.getSocketTimeout());
                    }

                    @Override
                    public void inputReady(final IOSession session) {
                        // no-op
                    }

                    @Override
                    public void outputReady(final IOSession session) {
                        // no-op
                    }

                    @Override
                    public void timeout(final IOSession session) {
                        // Expected: may be used by the reactor timer.
                        counters.reactorTimeoutEvents.incrementAndGet();
                        log("session.timeout (reactor) id=" + safeId(session)
                                + " soTimeout=" + session.getSocketTimeout());
                    }

                    @Override
                    public void exception(final IOSession session, final Exception ex) {
                        counters.exceptions.incrementAndGet();
                        log("session.exception id=" + safeId(session) + " ex=" + ex);
                    }

                    @Override
                    public void disconnected(final IOSession session) {
                        log("session.disconnected id=" + safeId(session));
                    }

                    private String safeId(final IOSession session) {
                        try {
                            return session.getId();
                        } catch (final IllegalStateException ignore) {
                            return "n/a";
                        }
                    }

                })
                .setStreamListener(new H2StreamListener() {

                    @Override
                    public void onHeaderInput(
                            final org.apache.hc.core5.http.HttpConnection connection,
                            final int streamId,
                            final List<? extends Header> headers) {
                        // no-op
                    }

                    @Override
                    public void onHeaderOutput(
                            final org.apache.hc.core5.http.HttpConnection connection,
                            final int streamId,
                            final List<? extends Header> headers) {
                        // no-op
                    }

                    @Override
                    public void onFrameInput(
                            final org.apache.hc.core5.http.HttpConnection connection,
                            final int streamId,
                            final RawFrame frame) {

                        if (shuttingDown.get()) {
                            return;
                        }

                        final FrameType type = FrameType.valueOf(frame.getType());

                        if (type == FrameType.PING && frame.isFlagSet(FrameFlag.ACK)) {
                            counters.pingAcksIn.incrementAndGet();
                            log("<< PING[ACK]");
                        } else if (type == FrameType.GOAWAY) {
                            counters.goAwayIn.incrementAndGet();
                            log("<< GOAWAY");
                        } else if (type == FrameType.RST_STREAM) {
                            counters.rstStreamIn.incrementAndGet();
                            log("<< RST_STREAM streamId=" + streamId);
                        }
                    }

                    @Override
                    public void onFrameOutput(
                            final org.apache.hc.core5.http.HttpConnection connection,
                            final int streamId,
                            final RawFrame frame) {

                        if (shuttingDown.get()) {
                            return;
                        }

                        final FrameType type = FrameType.valueOf(frame.getType());

                        if (type == FrameType.PING && !frame.isFlagSet(FrameFlag.ACK)) {
                            counters.pingsOut.incrementAndGet();
                            log(">> PING");
                        }
                    }

                    @Override
                    public void onInputFlowControl(
                            final org.apache.hc.core5.http.HttpConnection connection,
                            final int streamId,
                            final int delta,
                            final int actualSize) {
                        // no-op
                    }

                    @Override
                    public void onOutputFlowControl(
                            final org.apache.hc.core5.http.HttpConnection connection,
                            final int streamId,
                            final int delta,
                            final int actualSize) {
                        // no-op
                    }

                })
                .create();

        // Arm validate-after-inactivity before any connection is created.
        requester.setValidateAfterInactivity(validateAfterInactivity);

        requester.start();
        try {
            log("requester.validateAfterInactivity=" + requester.getValidateAfterInactivity());

            final Message<HttpResponse, String> m1 = executeSimpleGet(requester, TARGET);
            log("response1=" + m1.getHead().getCode());

            Thread.sleep(250);

            // Wait long enough to exceed validateAfterInactivity, but ideally not long enough for the server to drop idle connections.
            final long waitMs = validateAfterInactivity.toMilliseconds() + 800L;
            log("waiting=" + waitMs + "ms to exceed validateAfterInactivity...");
            Thread.sleep(waitMs);

            final Message<HttpResponse, String> m2 = executeSimpleGet(requester, TARGET);
            log("response2=" + m2.getHead().getCode());

            // From this point on we want to keep output clean (shutdown may enqueue commands and generate frames).
            shuttingDown.set(true);

            log("stats: sessionsConnected=" + counters.sessionsConnected.get()
                    + ", pingsOut=" + counters.pingsOut.get()
                    + ", pingAcksIn=" + counters.pingAcksIn.get()
                    + ", goAwayIn=" + counters.goAwayIn.get()
                    + ", rstStreamIn=" + counters.rstStreamIn.get()
                    + ", reactorTimeoutEvents=" + counters.reactorTimeoutEvents.get()
                    + ", exceptions=" + counters.exceptions.get());

        } finally {
            requester.close(CloseMode.GRACEFUL);
        }
    }

    private static Message<HttpResponse, String> executeSimpleGet(
            final H2MultiplexingRequester requester,
            final URI uri) throws Exception {

        final Timeout timeout = Timeout.ofSeconds(30);

        final AsyncRequestProducer requestProducer = AsyncRequestBuilder.get(uri).build();
        final BasicResponseConsumer<String> responseConsumer =
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer());

        try {
            final Future<Message<HttpResponse, String>> f = requester.execute(
                    requestProducer,
                    responseConsumer,
                    timeout,
                    HttpCoreContext.create(),
                    null);
            return f.get(timeout.toMilliseconds(), TimeUnit.MILLISECONDS);
        } finally {
            requestProducer.releaseResources();
            responseConsumer.releaseResources();
        }
    }

    private static void log(final String s) {
        System.out.println(Instant.now() + " " + s);
    }

}
