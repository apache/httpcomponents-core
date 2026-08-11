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

import java.net.InetSocketAddress;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.bootstrap.AsyncRequesterBootstrap;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http.nio.AsyncRequestConsumer;
import org.apache.hc.core5.http.nio.AsyncServerRequestHandler;
import org.apache.hc.core5.http.nio.entity.AsyncEntityProducers;
import org.apache.hc.core5.http.nio.entity.DiscardingEntityConsumer;
import org.apache.hc.core5.http.nio.support.BasicRequestConsumer;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.http.nio.support.BasicResponseProducer;
import org.apache.hc.core5.http.nio.support.BasicServerExchangeHandler;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.testing.nio.Http1TestServer;
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
 * End-to-end HTTP/1.1 throughput of a large-body GET over loopback, as a function of the
 * {@link Http1Config} session buffer size. Both the requester and the server are configured with
 * the same buffer size, so this isolates the effect of the buffer default on large messages.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 2, timeUnit = TimeUnit.SECONDS)
// A fixed, generous heap keeps GC out of the measurement: at ~6 K x 1 MB/s the transient garbage
// otherwise triggers frequent collections whose pauses dominate the run-to-run variance.
@Fork(value = 1, jvmArgs = {"-Xms4g", "-Xmx4g"})
// Client and server run in the same JVM, so keep concurrency at or below the available cores to
// avoid oversubscription noise; override with JMH -t on larger machines.
@Threads(8)
@State(Scope.Benchmark)
public class Http1BufferSizeBenchmark {

    @Param({"8192", "16384", "32768", "65536"})
    public int bufferSize;

    @Param({"1048576"})
    public int bodySize;

    private static final Timeout TIMEOUT = Timeout.ofSeconds(60);

    private Http1TestServer server;
    private HttpAsyncRequester requester;
    private HttpHost target;

    @Setup(Level.Trial)
    public void setUp() throws Exception {
        final Http1Config h1Config = Http1Config.custom().setBufferSize(bufferSize).build();
        final byte[] body = new byte[bodySize];

        server = new Http1TestServer();
        server.configure(h1Config);
        server.register("*", () -> new BasicServerExchangeHandler<>(new AsyncServerRequestHandler<Message<HttpRequest, Void>>() {

            @Override
            public AsyncRequestConsumer<Message<HttpRequest, Void>> prepare(
                    final HttpRequest request, final EntityDetails entityDetails, final HttpContext context) {
                return new BasicRequestConsumer<>(entityDetails != null ? new DiscardingEntityConsumer<>() : null);
            }

            @Override
            public void handle(
                    final Message<HttpRequest, Void> message, final ResponseTrigger responseTrigger,
                    final HttpContext context) throws HttpException, java.io.IOException {
                responseTrigger.submitResponse(
                        new BasicResponseProducer(HttpStatus.SC_OK,
                                AsyncEntityProducers.create(body, ContentType.APPLICATION_OCTET_STREAM)),
                        context);
            }
        }));
        final InetSocketAddress address = server.start();

        requester = AsyncRequesterBootstrap.bootstrap()
                .setIOReactorConfig(IOReactorConfig.custom().setSoTimeout(TIMEOUT).build())
                .setHttp1Config(h1Config)
                .setMaxTotal(300)
                .setDefaultMaxPerRoute(300)
                .create();
        requester.start();
        target = new HttpHost("http", "localhost", address.getPort());
    }

    @TearDown(Level.Trial)
    public void tearDown() throws Exception {
        if (requester != null) {
            requester.close(CloseMode.GRACEFUL);
        }
        if (server != null) {
            server.close();
        }
    }

    @Benchmark
    public long getLargeBody() throws Exception {
        final Future<Message<HttpResponse, Void>> future = requester.execute(
                new BasicRequestProducer(Method.GET, target, "/"),
                new BasicResponseConsumer<>(new DiscardingEntityConsumer<Void>()),
                TIMEOUT, null);
        final Message<HttpResponse, Void> message = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        return message.head().getCode();
    }

}
