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
package org.apache.hc.core5.benchmark;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;

import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.impl.routing.RequestRouter;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityProducer;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2MultiplexingRequester;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2MultiplexingRequesterBootstrap;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2ServerBootstrap;
import org.apache.hc.core5.http2.nio.pool.H2PoolPolicy;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.apache.hc.core5.testing.nio.EchoHandler;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

/**
 * JMH harness comparing H2 pool policies (BASIC vs MULTIPLEXING) using
 * the real httpcore5-h2 I/O reactor and HTTP/2 protocol stack. Each
 * iteration fires a GET request through the requester, exercises the
 * full pool lease / stream enqueue / response read / release cycle.
 */
@BenchmarkMode({Mode.Throughput})
@Warmup(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@OutputTimeUnit(TimeUnit.SECONDS)
public class H2PoolPolicyJmh {

    private static final Timeout TIMEOUT = Timeout.ofMinutes(2);

    @State(Scope.Benchmark)
    public static class BenchState {

        @Param({"BASIC", "MULTIPLEXING"})
        public String policy;

        @Param({"1", "4", "10"})
        public int routes;

        @Param({"10","20","50","100"})
        public int maxConcurrentStreams;

        @Param({"128"})
        public int payloadBytes;

        HttpAsyncServer server;
        H2MultiplexingRequester requester;
        HttpHost[] targets;
        String payload;

        @Setup(Level.Trial)
        public void setUp() throws Exception {
            final IOReactorConfig ioConfig = IOReactorConfig.custom()
                    .setSoTimeout(TIMEOUT)
                    .build();

            final H2Config serverH2Config = H2Config.custom()
                    .setPushEnabled(false)
                    .setMaxConcurrentStreams(maxConcurrentStreams)
                    .build();

            server = H2ServerBootstrap.bootstrap()
                    .setIOReactorConfig(ioConfig)
                    .setH2Config(serverH2Config)
                    .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_2)
                    .setRequestRouter(RequestRouter.<Supplier<AsyncServerExchangeHandler>>builder()
                            .addRoute(RequestRouter.LOCAL_AUTHORITY, "*",
                                    () -> new EchoHandler(2048))
                            .resolveAuthority(RequestRouter.LOCAL_AUTHORITY_RESOLVER)
                            .build())
                    .create();
            server.start();

            final List<HttpHost> hostList = new ArrayList<>();
            for (int i = 0; i < routes; i++) {
                final Future<ListenerEndpoint> future = server.listen(
                        new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                        URIScheme.HTTP);
                final ListenerEndpoint endpoint = future.get();
                final InetSocketAddress addr = (InetSocketAddress) endpoint.getAddress();
                hostList.add(new HttpHost(URIScheme.HTTP.id, "localhost", addr.getPort()));
            }
            targets = hostList.toArray(new HttpHost[0]);

            final H2PoolPolicy poolPolicy;
            switch (policy.toUpperCase(Locale.ROOT)) {
                case "MULTIPLEXING":
                    poolPolicy = H2PoolPolicy.MULTIPLEXING;
                    break;
                case "BASIC":
                default:
                    poolPolicy = H2PoolPolicy.BASIC;
                    break;
            }

            final H2Config clientH2Config = H2Config.custom()
                    .setPushEnabled(false)
                    .setMaxConcurrentStreams(maxConcurrentStreams)
                    .build();

            requester = H2MultiplexingRequesterBootstrap.bootstrap()
                    .setIOReactorConfig(ioConfig)
                    .setH2Config(clientH2Config)
                    .setH2PoolPolicy(poolPolicy)
                    .create();
            requester.start();

            final StringBuilder sb = new StringBuilder(payloadBytes);
            for (int i = 0; i < payloadBytes; i++) {
                sb.append('x');
            }
            payload = sb.toString();

            // Prime each route so the H2 session and SETTINGS exchange
            // are completed before the benchmark clock starts.
            final Timeout primeTimeout = Timeout.ofSeconds(10);
            for (final HttpHost t : targets) {
                try {
                    requester.execute(
                            new BasicRequestProducer(Method.POST, t, "/",
                                    new StringAsyncEntityProducer(payload, ContentType.TEXT_PLAIN)),
                            new BasicResponseConsumer<>(new StringAsyncEntityConsumer()),
                            primeTimeout,
                            null).get(primeTimeout.getDuration(), primeTimeout.getTimeUnit());
                } catch (final Exception ex) {
                    System.err.println("Priming " + t + " failed: " + ex);
                }
            }
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            if (requester != null) {
                try {
                    requester.initiateShutdown();
                    requester.awaitShutdown(TimeValue.ofSeconds(5));
                } catch (final Exception ignore) {
                }
                requester.close(CloseMode.IMMEDIATE);
            }
            if (server != null) {
                try {
                    server.initiateShutdown();
                    server.awaitShutdown(TimeValue.ofSeconds(5));
                } catch (final Exception ignore) {
                }
                server.close(CloseMode.IMMEDIATE);
            }
        }

        HttpHost pickTarget() {
            return targets[ThreadLocalRandom.current().nextInt(targets.length)];
        }

    }

    @Benchmark
    @Threads(50)
    public Message<HttpResponse, String> request(final BenchState s) throws Exception {
        final HttpHost target = s.pickTarget();
        final Future<Message<HttpResponse, String>> future = s.requester.execute(
                new BasicRequestProducer(Method.POST, target, "/",
                        new StringAsyncEntityProducer(s.payload, ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()),
                TIMEOUT,
                null);
        try {
            return future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        } catch (final TimeoutException te) {
            future.cancel(true);
            return null;
        } catch (final Exception ex) {
            if (ex.getCause() instanceof TimeoutException) {
                future.cancel(true);
            }
            return null;
        }
    }

}
