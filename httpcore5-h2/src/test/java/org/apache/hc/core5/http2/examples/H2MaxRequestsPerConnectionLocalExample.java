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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.nio.AsyncRequestConsumer;
import org.apache.hc.core5.http.nio.AsyncServerRequestHandler;
import org.apache.hc.core5.http.nio.entity.DiscardingEntityConsumer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.support.AsyncRequestBuilder;
import org.apache.hc.core5.http.nio.support.AsyncResponseBuilder;
import org.apache.hc.core5.http.nio.support.BasicRequestConsumer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2MultiplexingRequester;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2MultiplexingRequesterBootstrap;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2ServerBootstrap;
import org.apache.hc.core5.http2.impl.nio.bootstrap.RequestSubmissionPolicy;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.apache.hc.core5.util.Timeout;

public final class H2MaxRequestsPerConnectionLocalExample {

    public static void main(final String[] args) throws Exception {
        final int maxPerConn = 2;
        final int totalRequests = 50;
        final Timeout timeout = Timeout.ofSeconds(30);

        final IOReactorConfig ioReactorConfig = IOReactorConfig.custom()
                .setIoThreadCount(1)
                .build();

        final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        final H2Config serverH2Config = H2Config.custom()
                .setPushEnabled(false)
                .setMaxConcurrentStreams(maxPerConn)
                .build();

        final HttpAsyncServer server = H2ServerBootstrap.bootstrap()
                .setIOReactorConfig(ioReactorConfig)
                .setH2Config(serverH2Config)
                .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_2)
                .setCanonicalHostName("127.0.0.1")
                .register("*", new AsyncServerRequestHandler<Message<HttpRequest, Void>>() {

                    @Override
                    public AsyncRequestConsumer<Message<HttpRequest, Void>> prepare(
                            final HttpRequest request,
                            final EntityDetails entityDetails,
                            final HttpContext context) {
                        return new BasicRequestConsumer<Void>(entityDetails != null ? new DiscardingEntityConsumer<>() : null);
                    }

                    @Override
                    public void handle(
                            final Message<HttpRequest, Void> message,
                            final ResponseTrigger responseTrigger,
                            final HttpContext localContext) {
                        final HttpCoreContext context = HttpCoreContext.cast(localContext);
                        scheduler.schedule(() -> {
                            try {
                                responseTrigger.submitResponse(
                                        AsyncResponseBuilder.create(200)
                                                .setEntity("ok\n", ContentType.TEXT_PLAIN)
                                                .build(),
                                        context);
                            } catch (final Exception ex) {
                                try {
                                    responseTrigger.submitResponse(
                                            AsyncResponseBuilder.create(500)
                                                    .setEntity(ex.toString(), ContentType.TEXT_PLAIN)
                                                    .build(),
                                            context);
                                } catch (final Exception ignore) {
                                }
                            }
                        }, 200, TimeUnit.MILLISECONDS);
                    }

                })
                .create();

        server.start();
        final ListenerEndpoint ep = server.listen(new InetSocketAddress("127.0.0.1", 0), URIScheme.HTTP).get();
        final int port = ((InetSocketAddress) ep.getAddress()).getPort();
        System.out.println("server on 127.0.0.1:" + port);

        final H2Config clientH2Config = H2Config.custom()
                .setPushEnabled(false)
                .build();

        final H2MultiplexingRequester requester = H2MultiplexingRequesterBootstrap.bootstrap()
                .setIOReactorConfig(ioReactorConfig)
                .setH2Config(clientH2Config)
                .setMaxRequestsPerConnection(maxPerConn)
                .setRequestSubmissionPolicy(RequestSubmissionPolicy.QUEUE)
                .create();

        requester.start();

        final HttpHost target = new HttpHost("http", "127.0.0.1", port);

        requester.execute(
                target,
                AsyncRequestBuilder.get()
                        .setPath("/warmup")
                        .build(),
                new BasicResponseConsumer<String>(new StringAsyncEntityConsumer()),
                timeout,
                HttpCoreContext.create(),
                new FutureCallback<Message<HttpResponse, String>>() {
                    @Override
                    public void completed(final Message<HttpResponse, String> result) {
                        System.out.println("warmup -> " + result.getHead().getCode());
                    }

                    @Override
                    public void failed(final Exception ex) {
                        System.out.println("warmup failed -> " + ex.getClass().getName() + ": " + ex.getMessage());
                    }

                    @Override
                    public void cancelled() {
                        System.out.println("warmup cancelled");
                    }
                }).get();

        final AtomicInteger ok = new AtomicInteger(0);
        final AtomicInteger failed = new AtomicInteger(0);
        final CountDownLatch latch = new CountDownLatch(totalRequests);

        final ExecutorService exec = Executors.newFixedThreadPool(Math.min(16, totalRequests));

        for (int i = 0; i < totalRequests; i++) {
            final int id = i;
            exec.execute(() -> {
                final String path = "/slow?i=" + id;

                requester.execute(
                        target,
                        AsyncRequestBuilder.get()
                                .setPath(path)
                                .build(),
                        new BasicResponseConsumer<String>(new StringAsyncEntityConsumer()),
                        timeout,
                        HttpCoreContext.create(),
                        new FutureCallback<Message<HttpResponse, String>>() {

                            @Override
                            public void completed(final Message<HttpResponse, String> message) {
                                if (message.getHead().getCode() == 200) {
                                    ok.incrementAndGet();
                                } else {
                                    failed.incrementAndGet();
                                    System.out.println("FAILED " + path + " -> HTTP " + message.getHead().getCode());
                                }
                                latch.countDown();
                            }

                            @Override
                            public void failed(final Exception ex) {
                                failed.incrementAndGet();
                                System.out.println("FAILED " + path + " -> " + ex.getClass().getName() + ": " + ex.getMessage());
                                latch.countDown();
                            }

                            @Override
                            public void cancelled() {
                                failed.incrementAndGet();
                                latch.countDown();
                            }
                        });
            });
        }

        final boolean done = latch.await(60, TimeUnit.SECONDS);
        exec.shutdownNow();

        System.out.println("done=" + done + " ok=" + ok.get() + ", failed=" + failed.get());

        requester.close(CloseMode.GRACEFUL);
        server.close(CloseMode.GRACEFUL);
        scheduler.shutdownNow();
    }

    private H2MaxRequestsPerConnectionLocalExample() {
    }

}
