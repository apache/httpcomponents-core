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

import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetSocketAddress;
import java.util.LinkedList;
import java.util.Queue;
import java.util.StringTokenizer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncServerRequestHandler;
import org.apache.hc.core5.http.nio.entity.AsyncEntityProducers;
import org.apache.hc.core5.http.nio.entity.DiscardingEntityConsumer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.support.AbstractAsyncPushHandler;
import org.apache.hc.core5.http.nio.support.AsyncResponseBuilder;
import org.apache.hc.core5.http.nio.support.BasicPushProducer;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.http.nio.support.BasicResponseProducer;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.support.BasicRequestBuilder;
import org.apache.hc.core5.http2.H2Error;
import org.apache.hc.core5.http2.H2StreamResetException;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.nio.command.PingCommand;
import org.apache.hc.core5.http2.nio.support.BasicPingHandler;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.testing.extension.nio.H2TestResources;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

abstract class H2IntegrationTest extends HttpIntegrationTest {

    @RegisterExtension
    private final H2TestResources resources;

    public H2IntegrationTest(final URIScheme scheme) {
        super(scheme);
        this.resources = new H2TestResources(scheme, TIMEOUT);
    }

    @Override
    protected HttpTestServer server() {
        return resources.server();
    }

    @Override
    protected HttpTestClient client() {
        return resources.client();
    }

    @BeforeEach
    void setup() {
        resources.server().configure(H2Config.DEFAULT);
        resources.client().configure(H2Config.DEFAULT);
    }

    @Test
    @Override
    void testSlowResponseConsumer() throws Exception {
        final H2TestClient client = resources.client();

        client.configure(H2Config.custom()
                .setInitialWindowSize(16)
                .build());
        client.start();

        super.testSlowResponseConsumer();
    }

    @Test
    void testSlowResponseProducer() throws Exception {
        final H2TestClient client = resources.client();

        client.configure(H2Config.custom()
                .setInitialWindowSize(512)
                .build());
        client.start();

        super.testSlowResponseProducer();
    }

    @Test
    void testPush() throws Exception {
        final H2TestServer server = resources.server();
        final H2TestClient client = resources.client();

        server.register("/hello", () -> new MessageExchangeHandler<Void>(new DiscardingEntityConsumer<>()) {

            @Override
            protected void handle(
                    final Message<HttpRequest, Void> request,
                    final AsyncServerRequestHandler.ResponseTrigger responseTrigger,
                    final HttpContext context) throws IOException, HttpException {
                responseTrigger.pushPromise(
                        new BasicHttpRequest(Method.GET, new HttpHost(scheme.id, "localhost"), "/stuff"),
                        context,
                        new BasicPushProducer(new MultiLineEntityProducer("Pushing lots of stuff", 500)));
                responseTrigger.submitResponse(
                        AsyncResponseBuilder.create(HttpStatus.SC_OK).setEntity("Hi there", ContentType.TEXT_PLAIN).build(),
                        context);
            }
        });
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.configure(H2Config.custom()
                .setPushEnabled(true)
                .build());
        client.start();

        final BlockingQueue<Message<HttpResponse, String>> pushMessageQueue = new LinkedBlockingDeque<>();

        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final BasicHttpRequest request = BasicRequestBuilder.get()
                .setHttpHost(target)
                .setPath("/hello")
                .build();

        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                new BasicRequestProducer(request, null),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()),
                (r, c) -> new AbstractAsyncPushHandler<Message<HttpResponse, String>>(new BasicResponseConsumer<>(new StringAsyncEntityConsumer())) {

                    @Override
                    protected void handleResponse(
                            final HttpRequest promise,
                            final Message<HttpResponse, String> responseMessage) throws IOException, HttpException {
                        try {
                            pushMessageQueue.put(responseMessage);
                        } catch (final InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            throw new InterruptedIOException(ex.getMessage());
                        }
                    }

                },
                null,
                null);
        final Message<HttpResponse, String> result1 = future1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(result1);
        final HttpResponse response1 = result1.getHead();
        final String entity1 = result1.getBody();
        Assertions.assertNotNull(response1);
        Assertions.assertEquals(200, response1.getCode());
        Assertions.assertEquals("Hi there", entity1);

        final Message<HttpResponse, String> result2 = pushMessageQueue.poll(5, TimeUnit.SECONDS);
        Assertions.assertNotNull(result2);
        final HttpResponse response2 = result2.getHead();
        final String entity2 = result2.getBody();
        Assertions.assertEquals(200, response2.getCode());
        Assertions.assertNotNull(entity2);
        final StringTokenizer t1 = new StringTokenizer(entity2, "\r\n");
        while (t1.hasMoreTokens()) {
            Assertions.assertEquals("Pushing lots of stuff", t1.nextToken());
        }
    }

    @Test
    void testPushRefused() throws Exception {
        final H2TestServer server = resources.server();
        final H2TestClient client = resources.client();

        final BlockingQueue<Exception> pushResultQueue = new LinkedBlockingDeque<>();
        server.register("/hello", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new MessageExchangeHandler<Void>(new DiscardingEntityConsumer<>()) {

                    @Override
                    protected void handle(
                            final Message<HttpRequest, Void> request,
                            final AsyncServerRequestHandler.ResponseTrigger responseTrigger,
                            final HttpContext context) throws IOException, HttpException {

                        responseTrigger.pushPromise(
                                new BasicHttpRequest(Method.GET, new HttpHost(scheme.id, "localhost"), "/stuff"),
                                context, new BasicPushProducer(AsyncEntityProducers.create("Pushing all sorts of stuff")) {

                            @Override
                            public void failed(final Exception cause) {
                                pushResultQueue.add(cause);
                                super.failed(cause);
                            }

                        });
                        responseTrigger.pushPromise(
                                new BasicHttpRequest(Method.GET, new HttpHost(scheme.id, "localhost"), "/more-stuff"),
                                context, new BasicPushProducer(new MultiLineEntityProducer("Pushing lots of stuff", 500)) {

                            @Override
                            public void failed(final Exception cause) {
                                pushResultQueue.add(cause);
                                super.failed(cause);
                            }

                        });
                        responseTrigger.submitResponse(
                                new BasicResponseProducer(HttpStatus.SC_OK, AsyncEntityProducers.create("Hi there")),
                                context);
                    }
                };
            }

        });
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.configure(H2Config.custom()
                .setPushEnabled(true)
                .build());
        client.start();

        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final BasicHttpRequest request1 = BasicRequestBuilder.get()
                .setHttpHost(target)
                .setPath("/hello")
                .build();

        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                new BasicRequestProducer(request1, null),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> result1 = future1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(result1);
        final HttpResponse response1 = result1.getHead();
        final String entity1 = result1.getBody();
        Assertions.assertNotNull(response1);
        Assertions.assertEquals(200, response1.getCode());
        Assertions.assertEquals("Hi there", entity1);

        final Object result2 = pushResultQueue.poll(5, TimeUnit.SECONDS);
        Assertions.assertNotNull(result2);
        Assertions.assertTrue(result2 instanceof H2StreamResetException);
        Assertions.assertEquals(H2Error.REFUSED_STREAM.getCode(), ((H2StreamResetException) result2).getCode());

        final Object result3 = pushResultQueue.poll(5, TimeUnit.SECONDS);
        Assertions.assertNotNull(result3);
        Assertions.assertTrue(result3 instanceof H2StreamResetException);
        Assertions.assertEquals(H2Error.REFUSED_STREAM.getCode(), ((H2StreamResetException) result3).getCode());
    }

    @Test
    void testExcessOfConcurrentStreams() throws Exception {
        final H2TestServer server = resources.server();
        final H2TestClient client = resources.client();

        server.register("/", () -> new MultiLineResponseHandler("0123456789abcdef", 2000));
        server.configure(H2Config.custom()
                .setMaxConcurrentStreams(20)
                .build());
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.configure(H2Config.custom()
                .setMaxConcurrentStreams(20)
                .build());
        client.start();

        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final Queue<Future<Message<HttpResponse, Void>>> queue = new LinkedList<>();
        for (int i = 0; i < 2000; i++) {
            final BasicHttpRequest request1 = BasicRequestBuilder.get()
                    .setHttpHost(target)
                    .setPath("/")
                    .build();
            final Future<Message<HttpResponse, Void>> future = streamEndpoint.execute(
                    new BasicRequestProducer(request1, null),
                    new BasicResponseConsumer<>(new DiscardingEntityConsumer<>()), null);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            final Future<Message<HttpResponse, Void>> future = queue.remove();
            final Message<HttpResponse, Void> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertNotNull(result);
            final HttpResponse response = result.getHead();
            Assertions.assertNotNull(response);
            Assertions.assertEquals(200, response.getCode());
        }
    }

    @Test
    void testConnectionPing() throws Exception {
        final H2TestServer server = resources.server();
        final H2TestClient client = resources.client();

        server.register("/hello", () -> new SingleLineResponseHandler("Hi there"));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final int n = 10;
        final CountDownLatch latch = new CountDownLatch(n);
        final AtomicInteger count = new AtomicInteger(0);
        for (int i = 0; i < n; i++) {
            final BasicHttpRequest request = BasicRequestBuilder.get()
                    .setHttpHost(target)
                    .setPath("/hello")
                    .build();
            streamEndpoint.execute(
                    new BasicRequestProducer(request, null),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
            streamEndpoint.execute(new PingCommand(new BasicPingHandler(result -> {
                if (result) {
                    count.incrementAndGet();
                }
                latch.countDown();
            })), Command.Priority.NORMAL);

        }
        Assertions.assertTrue(latch.await(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()));
        Assertions.assertEquals(n, count.get());
    }

    @Test
    void testRequestWithInvalidConnectionHeader() throws Exception {
        final H2TestServer server = resources.server();
        final H2TestClient client = resources.client();

        server.register("/hello", () -> new SingleLineResponseHandler("Hi there"));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();

        final Future<IOSession> sessionFuture = client.requestSession(new HttpHost("localhost", serverEndpoint.getPort()), TIMEOUT, null);
        final IOSession session = sessionFuture.get();
        try (final ClientSessionEndpoint streamEndpoint = new ClientSessionEndpoint(session)) {
            final BasicHttpRequest request = BasicRequestBuilder.get()
                    .setHttpHost(target)
                    .setPath("/hello")
                    .build();
            request.addHeader(HttpHeaders.CONNECTION, HeaderElements.CLOSE);
            final HttpCoreContext context = HttpCoreContext.create();
            final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                        new BasicRequestProducer(request, null),
                        new BasicResponseConsumer<>(new StringAsyncEntityConsumer()),
                        context, null);
            final ExecutionException exception = Assertions.assertThrows(ExecutionException.class, () ->
                    future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()));
            assertThat(exception.getCause(), CoreMatchers.instanceOf(ProtocolException.class));

            final EndpointDetails endpointDetails = context.getEndpointDetails();
            assertThat(endpointDetails.getRequestCount(), CoreMatchers.equalTo(0L));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "POST"})
    void testHeaderTooLarge(final String method) throws Exception {
        final H2TestServer server = resources.server();
        server.configure(H2Config.custom()
                .setMaxHeaderListSize(100)
                .build());
        super.testHeaderTooLarge(method);
    }

}
