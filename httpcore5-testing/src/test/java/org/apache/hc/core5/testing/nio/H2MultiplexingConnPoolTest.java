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

package org.apache.hc.core5.testing.nio;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.impl.routing.RequestRouter;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncServerRequestHandler;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityProducer;
import org.apache.hc.core5.http.nio.support.AsyncResponseBuilder;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2MultiplexingRequester;
import org.apache.hc.core5.http2.nio.pool.H2PoolPolicy;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.apache.hc.core5.testing.extension.nio.H2AsyncServerResource;
import org.apache.hc.core5.testing.extension.nio.H2MultiplexingRequesterResource;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class H2MultiplexingConnPoolTest {

    private static final Timeout TIMEOUT = Timeout.ofSeconds(30);
    private static final int SINGLE_ROUTE_CONCURRENCY = 20;

    private final AtomicLong clientConnCount;
    private final AtomicInteger activeRequests;
    private final AtomicInteger maxActiveRequests;
    private final AtomicReference<Throwable> handlerFailure;

    private volatile CountDownLatch requestsArrived;
    private volatile CountDownLatch releaseResponses;

    @RegisterExtension
    private final H2AsyncServerResource serverResource;
    @RegisterExtension
    private final H2MultiplexingRequesterResource clientResource;

    public H2MultiplexingConnPoolTest() {
        this.clientConnCount = new AtomicLong();
        this.activeRequests = new AtomicInteger();
        this.maxActiveRequests = new AtomicInteger();
        this.handlerFailure = new AtomicReference<>();

        this.serverResource = new H2AsyncServerResource();
        this.serverResource.configure(bootstrap -> bootstrap
                .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_2)
                .setIOReactorConfig(
                        IOReactorConfig.custom()
                                .setSoTimeout(TIMEOUT)
                                .build())
                .setH2Config(H2Config.custom()
                        .setPushEnabled(false)
                        .setMaxConcurrentStreams(100)
                        .build())
                .setRequestRouter(RequestRouter.<Supplier<AsyncServerExchangeHandler>>builder()
                        .addRoute(RequestRouter.LOCAL_AUTHORITY, "/barrier",
                                () -> new BarrierHandler(
                                        activeRequests,
                                        maxActiveRequests,
                                        requestsArrived,
                                        releaseResponses,
                                        handlerFailure))
                        .addRoute(RequestRouter.LOCAL_AUTHORITY, "*",
                                () -> new EchoHandler(2048))
                        .resolveAuthority(RequestRouter.LOCAL_AUTHORITY_RESOLVER)
                        .build())
        );

        this.clientResource = new H2MultiplexingRequesterResource();
        this.clientResource.configure(bootstrap -> bootstrap
                .setIOReactorConfig(IOReactorConfig.custom()
                        .setSoTimeout(TIMEOUT)
                        .build())
                .setH2Config(H2Config.custom()
                        .setPushEnabled(false)
                        .setMaxConcurrentStreams(100)
                        .build())
                .setH2PoolPolicy(H2PoolPolicy.MULTIPLEXING)
                .setIOSessionListener(new LoggingIOSessionListener() {

                    @Override
                    public void connected(final IOSession session) {
                        clientConnCount.incrementAndGet();
                        super.connected(session);
                    }

                })
        );
    }

    @BeforeEach
    void resetCounts() {
        clientConnCount.set(0);
        activeRequests.set(0);
        maxActiveRequests.set(0);
        handlerFailure.set(null);
        requestsArrived = new CountDownLatch(SINGLE_ROUTE_CONCURRENCY);
        releaseResponses = new CountDownLatch(1);
    }

    @Test
    void testConcurrentRequestsSingleRoute() throws Exception {
        final HttpAsyncServer server = serverResource.start();
        final Future<ListenerEndpoint> future = server.listen(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                URIScheme.HTTP);
        final ListenerEndpoint listener = future.get();
        final InetSocketAddress address = (InetSocketAddress) listener.getAddress();
        final HttpHost target = new HttpHost(URIScheme.HTTP.id, "localhost",
                address.getPort());

        final H2MultiplexingRequester requester = clientResource.start();

        final Message<HttpResponse, String> warmup = requester.execute(
                new BasicRequestProducer(Method.POST, target, "/warmup",
                        new StringAsyncEntityProducer("warmup", ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()),
                TIMEOUT, null).get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());

        Assertions.assertNotNull(warmup);
        Assertions.assertEquals(HttpStatus.SC_OK, warmup.head().getCode());
        Assertions.assertEquals("warmup", warmup.body());

        final List<Future<Message<HttpResponse, String>>> futures =
                new ArrayList<>(SINGLE_ROUTE_CONCURRENCY);
        for (int i = 0; i < SINGLE_ROUTE_CONCURRENCY; i++) {
            futures.add(requester.execute(
                    new BasicRequestProducer(Method.POST, target, "/barrier",
                            new StringAsyncEntityProducer("msg-" + i,
                                    ContentType.TEXT_PLAIN)),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()),
                    TIMEOUT, null));
        }

        Assertions.assertTrue(requestsArrived.await(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()),
                "All requests should arrive before responses are released");

        Assertions.assertEquals(SINGLE_ROUTE_CONCURRENCY, maxActiveRequests.get(),
                "Requests should be concurrently in flight on the server");

        Assertions.assertEquals(1L, clientConnCount.get(),
                "Single route should multiplex over one HTTP/2 connection");

        releaseResponses.countDown();

        for (int i = 0; i < SINGLE_ROUTE_CONCURRENCY; i++) {
            final Message<HttpResponse, String> message = futures.get(i).get(
                    TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertNotNull(message);
            Assertions.assertEquals(HttpStatus.SC_OK, message.head().getCode());
            Assertions.assertEquals("msg-" + i, message.body());
        }

        if (handlerFailure.get() != null) {
            Assertions.fail(handlerFailure.get());
        }
    }

    @Test
    void testConcurrentRequestsMultipleRoutes() throws Exception {
        final int routeCount = 3;
        final int requestsPerRoute = 20;

        final HttpAsyncServer server = serverResource.start();
        final HttpHost[] targets = new HttpHost[routeCount];
        for (int r = 0; r < routeCount; r++) {
            final Future<ListenerEndpoint> future = server.listen(
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), URIScheme.HTTP);
            final ListenerEndpoint listener = future.get();
            final InetSocketAddress address = (InetSocketAddress) listener.getAddress();
            targets[r] = new HttpHost(URIScheme.HTTP.id, "localhost",
                    address.getPort());
        }

        final H2MultiplexingRequester requester = clientResource.start();

        final List<Future<Message<HttpResponse, String>>> futures = new ArrayList<>();
        for (int r = 0; r < routeCount; r++) {
            for (int i = 0; i < requestsPerRoute; i++) {
                final String body = "route-" + r + "-msg-" + i;
                futures.add(requester.execute(
                        new BasicRequestProducer(Method.POST, targets[r], "/echo", new StringAsyncEntityProducer(body, ContentType.TEXT_PLAIN)),
                        new BasicResponseConsumer<>(new StringAsyncEntityConsumer()),
                        TIMEOUT, null));
            }
        }

        int idx = 0;
        for (int r = 0; r < routeCount; r++) {
            for (int i = 0; i < requestsPerRoute; i++) {
                final Message<HttpResponse, String> message = futures.get(idx).get(
                        TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
                Assertions.assertNotNull(message);
                Assertions.assertEquals(HttpStatus.SC_OK, message.head().getCode());
                Assertions.assertEquals("route-" + r + "-msg-" + i, message.body());
                idx++;
            }
        }

        Assertions.assertTrue(clientConnCount.get() >= routeCount,
                "Each route should have at least one connection");
        Assertions.assertTrue(clientConnCount.get() <= routeCount * 3,
                "Each route should use at most defaultMaxPerRoute connections");
    }

    static final class BarrierHandler extends MessageExchangeHandler<String> {

        private final AtomicInteger activeRequests;
        private final AtomicInteger maxActiveRequests;
        private final CountDownLatch requestsArrived;
        private final CountDownLatch releaseResponses;
        private final AtomicReference<Throwable> failureRef;

        BarrierHandler(
                final AtomicInteger activeRequests,
                final AtomicInteger maxActiveRequests,
                final CountDownLatch requestsArrived,
                final CountDownLatch releaseResponses,
                final AtomicReference<Throwable> failureRef) {
            super(new StringAsyncEntityConsumer());
            this.activeRequests = activeRequests;
            this.maxActiveRequests = maxActiveRequests;
            this.requestsArrived = requestsArrived;
            this.releaseResponses = releaseResponses;
            this.failureRef = failureRef;
        }

        @Override
        protected void handle(
                final Message<HttpRequest, String> requestMessage,
                final AsyncServerRequestHandler.ResponseTrigger responseTrigger,
                final HttpContext context) {
            final int current = activeRequests.incrementAndGet();
            maxActiveRequests.accumulateAndGet(current, Math::max);
            requestsArrived.countDown();

            final Thread responder = new Thread(() -> {
                try {
                    if (!releaseResponses.await(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit())) {
                        failureRef.compareAndSet(null,
                                new AssertionError("Timed out waiting to release responses"));
                        return;
                    }
                    responseTrigger.submitResponse(
                            AsyncResponseBuilder.create(HttpStatus.SC_OK)
                                    .setEntity(requestMessage.getBody(), ContentType.TEXT_PLAIN)
                                    .build(),
                            context);
                } catch (final Exception ex) {
                    failureRef.compareAndSet(null, ex);
                } finally {
                    activeRequests.decrementAndGet();
                }
            });
            responder.setDaemon(true);
            responder.start();
        }
    }

}