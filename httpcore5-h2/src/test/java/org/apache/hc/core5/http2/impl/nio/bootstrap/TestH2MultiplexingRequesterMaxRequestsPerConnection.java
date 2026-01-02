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

package org.apache.hc.core5.http2.impl.nio.bootstrap;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
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
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Test;

class TestH2MultiplexingRequesterMaxRequestsPerConnection {

    @Test
    @org.junit.jupiter.api.Timeout(30)
    void rejectsWhenPerConnectionLimitReached() throws Exception {
        final int maxPerConn = 2;
        final int totalRequests = 20;

        final IOReactorConfig ioReactorConfig = IOReactorConfig.custom()
                .setIoThreadCount(1)
                .build();

        final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        final H2Config serverH2Config = H2Config.custom()
                .setPushEnabled(false)
                .setMaxConcurrentStreams(1)
                .build();

        final HttpAsyncServer server = H2ServerBootstrap.bootstrap()
                .setIOReactorConfig(ioReactorConfig)
                .setH2Config(serverH2Config)
                .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_2)
                .register("*", new AsyncServerRequestHandler<Message<HttpRequest, Void>>() {

                    @Override
                    public AsyncRequestConsumer<Message<HttpRequest, Void>> prepare(
                            final HttpRequest request,
                            final EntityDetails entityDetails,
                            final HttpContext context) {
                        return new BasicRequestConsumer<>(entityDetails != null ? new DiscardingEntityConsumer<Void>() : null);
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
                            } catch (final Exception ignore) {
                            }
                        }, 2, TimeUnit.SECONDS);
                    }

                })
                .create();

        server.start();
        final ListenerEndpoint ep = server.listen(new InetSocketAddress("127.0.0.1", 0), URIScheme.HTTP).get();
        final int port = ((InetSocketAddress) ep.getAddress()).getPort();

        final H2Config clientH2Config = H2Config.custom()
                .setPushEnabled(false)
                .build();

        final H2MultiplexingRequester requester = H2MultiplexingRequesterBootstrap.bootstrap()
                .setIOReactorConfig(ioReactorConfig)
                .setH2Config(clientH2Config)
                .setMaxRequestsPerConnection(maxPerConn)
                .create();

        requester.start();

        final HttpHost target = new HttpHost("http", "127.0.0.1", port);
        final Timeout timeout = Timeout.ofSeconds(30);

        requester.execute(
                target,
                AsyncRequestBuilder.get().setHttpHost(target).setPath("/warmup").build(),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()),
                timeout,
                HttpCoreContext.create(),
                new FutureCallback<Message<HttpResponse, String>>() {
                    @Override public void completed(final Message<HttpResponse, String> result) { }
                    @Override public void failed(final Exception ex) { }
                    @Override public void cancelled() { }
                }).get();

        final AtomicInteger rejected = new AtomicInteger(0);
        final CountDownLatch latch = new CountDownLatch(totalRequests);

        for (int i = 0; i < totalRequests; i++) {
            requester.execute(
                    target,
                    AsyncRequestBuilder.get().setHttpHost(target).setPath("/slow?i=" + i).build(),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()),
                    timeout,
                    HttpCoreContext.create(),
                    new FutureCallback<Message<HttpResponse, String>>() {
                        @Override
                        public void completed(final Message<HttpResponse, String> message) {
                            latch.countDown();
                        }
                        @Override
                        public void failed(final Exception ex) {
                            if (ex instanceof RejectedExecutionException) {
                                rejected.incrementAndGet();
                            }
                            latch.countDown();
                        }
                        @Override
                        public void cancelled() {
                            latch.countDown();
                        }
                    });
        }

        latch.await(20, TimeUnit.SECONDS);

        try {
            assertTrue(rejected.get() > 0);
        } finally {
            requester.close(CloseMode.GRACEFUL);
            server.close(CloseMode.GRACEFUL);
            scheduler.shutdownNow();
        }
    }

}
