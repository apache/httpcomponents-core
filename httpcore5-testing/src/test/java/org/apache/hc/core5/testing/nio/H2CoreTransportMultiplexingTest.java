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


import org.junit.jupiter.api.Assertions;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.hc.core5.concurrent.Cancellable;
import org.apache.hc.core5.concurrent.CountDownLatchFutureCallback;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.impl.routing.RequestRouter;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.entity.AsyncEntityProducers;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityProducer;
import org.apache.hc.core5.http.nio.support.BasicClientExchangeHandler;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2MultiplexingRequester;
import org.apache.hc.core5.http2.ssl.H2ClientTlsStrategy;
import org.apache.hc.core5.http2.ssl.H2ServerTlsStrategy;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.apache.hc.core5.testing.SSLTestContexts;
import org.apache.hc.core5.testing.extension.nio.H2AsyncServerResource;
import org.apache.hc.core5.testing.extension.nio.H2MultiplexingRequesterResource;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

abstract class H2CoreTransportMultiplexingTest {

    private static final Timeout TIMEOUT = Timeout.ofMinutes(1);

    private final URIScheme scheme;
    @RegisterExtension
    private final H2AsyncServerResource serverResource;
    @RegisterExtension
    private final H2MultiplexingRequesterResource clientResource;

    public H2CoreTransportMultiplexingTest(final URIScheme scheme) {
        this.scheme = scheme;
        this.serverResource = new H2AsyncServerResource();
        this.serverResource.configure(bootstrap -> bootstrap
                .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_2)
                .setTlsStrategy(new H2ServerTlsStrategy(SSLTestContexts.createServerSSLContext()))
                .setIOReactorConfig(
                        IOReactorConfig.custom()
                                .setSoTimeout(TIMEOUT)
                                .build())
                .setRequestRouter(RequestRouter.<Supplier<AsyncServerExchangeHandler>>builder()
                        .addRoute(RequestRouter.LOCAL_AUTHORITY, "*", () -> new EchoHandler(2048))
                        .resolveAuthority(RequestRouter.LOCAL_AUTHORITY_RESOLVER)
                        .build())
        );
        this.clientResource = new H2MultiplexingRequesterResource();
        this.clientResource.configure(bootstrap -> bootstrap
                .setTlsStrategy(new H2ClientTlsStrategy(SSLTestContexts.createClientSSLContext()))
                .setIOReactorConfig(IOReactorConfig.custom()
                        .setSoTimeout(TIMEOUT)
                        .build())
        );
    }

    @Test
    void testSequentialRequests() throws Exception {
        final HttpAsyncServer server = serverResource.start();
        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), scheme);
        final ListenerEndpoint listener = future.get();
        final InetSocketAddress address = (InetSocketAddress) listener.getAddress();
        final H2MultiplexingRequester requester = clientResource.start();

        final HttpHost target = new HttpHost(scheme.id, "localhost", address.getPort());
        final Future<Message<HttpResponse, String>> resultFuture1 = requester.execute(
                new BasicRequestProducer(Method.POST, target, "/stuff",
                        new StringAsyncEntityProducer("some stuff", ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), TIMEOUT, null);
        final Message<HttpResponse, String> message1 = resultFuture1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(message1);
        final HttpResponse response1 = message1.head();
        Assertions.assertEquals(HttpStatus.SC_OK, response1.getCode());
        final String body1 = message1.body();
        Assertions.assertEquals("some stuff", body1);

        final Future<Message<HttpResponse, String>> resultFuture2 = requester.execute(
                new BasicRequestProducer(Method.POST, target, "/other-stuff",
                        new StringAsyncEntityProducer("some other stuff", ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), TIMEOUT, null);
        final Message<HttpResponse, String> message2 = resultFuture2.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(message2);
        final HttpResponse response2 = message2.head();
        Assertions.assertEquals(HttpStatus.SC_OK, response2.getCode());
        final String body2 = message2.body();
        Assertions.assertEquals("some other stuff", body2);

        final Future<Message<HttpResponse, String>> resultFuture3 = requester.execute(
                new BasicRequestProducer(Method.POST, target, "/more-stuff",
                        new StringAsyncEntityProducer("some more stuff", ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), TIMEOUT, null);
        final Message<HttpResponse, String> message3 = resultFuture3.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(message3);
        final HttpResponse response3 = message3.head();
        Assertions.assertEquals(HttpStatus.SC_OK, response3.getCode());
        final String body3 = message3.body();
        Assertions.assertEquals("some more stuff", body3);
    }

    @Test
    void testLargeRequest() throws Exception {
        final HttpAsyncServer server = serverResource.start();
        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), scheme);
        final ListenerEndpoint listener = future.get();
        final InetSocketAddress address = (InetSocketAddress) listener.getAddress();
        final H2MultiplexingRequester requester = clientResource.start();

        final HttpHost target = new HttpHost(scheme.id, "localhost", address.getPort());
        final String content = IntStream.range(0, 1000).mapToObj(i -> "a lot of stuff").collect(Collectors.joining(" "));
        final Future<Message<HttpResponse, String>> resultFuture = requester.execute(
                new BasicRequestProducer(Method.POST, target, "/a-lot-of-stuff", AsyncEntityProducers.create(content, ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), TIMEOUT, null);
        final Message<HttpResponse, String> message = resultFuture.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(message);
        final HttpResponse response = message.head();
        Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
        final String body = message.body();
        Assertions.assertEquals(content, body);
    }

    @Test
    void testMultiplexedRequests() throws Exception {
        final HttpAsyncServer server = serverResource.start();
        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), scheme);
        final ListenerEndpoint listener = future.get();
        final InetSocketAddress address = (InetSocketAddress) listener.getAddress();
        final H2MultiplexingRequester requester = clientResource.start();

        final HttpHost target = new HttpHost(scheme.id, "localhost", address.getPort());
        final Queue<Future<Message<HttpResponse, String>>> queue = new LinkedList<>();

        queue.add(requester.execute(
                new BasicRequestProducer(Method.POST, target, "/stuff",
                        new StringAsyncEntityProducer("some stuff", ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), TIMEOUT, null));
        queue.add(requester.execute(
                new BasicRequestProducer(Method.POST, target, "/other-stuff",
                        new StringAsyncEntityProducer("some other stuff", ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), TIMEOUT, null));
        queue.add(requester.execute(
                new BasicRequestProducer(Method.POST, target, "/more-stuff",
                        new StringAsyncEntityProducer("some more stuff", ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), TIMEOUT, null));

        while (!queue.isEmpty()) {
            final Future<Message<HttpResponse, String>> resultFuture = queue.remove();
            final Message<HttpResponse, String> message = resultFuture.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertNotNull(message);
            final HttpResponse response = message.head();
            Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
            final String body = message.body();
            Assertions.assertTrue(body.contains("stuff"));
        }
    }

    @Test
    void testValidityCheck() throws Exception {
        final HttpAsyncServer server = serverResource.start();
        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), scheme);
        final ListenerEndpoint listener = future.get();
        final InetSocketAddress address = (InetSocketAddress) listener.getAddress();
        final H2MultiplexingRequester requester = clientResource.start();
        requester.setValidateAfterInactivity(TimeValue.ofMilliseconds(10));

        final HttpHost target = new HttpHost(scheme.id, "localhost", address.getPort());
        final Future<Message<HttpResponse, String>> resultFuture1 = requester.execute(
                new BasicRequestProducer(Method.POST, target, "/stuff",
                        new StringAsyncEntityProducer("some stuff", ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), TIMEOUT, null);
        final Message<HttpResponse, String> message1 = resultFuture1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(message1);
        final HttpResponse response1 = message1.head();
        Assertions.assertEquals(HttpStatus.SC_OK, response1.getCode());
        final String body1 = message1.body();
        Assertions.assertEquals("some stuff", body1);

        Thread.sleep(100);

        final Future<Message<HttpResponse, String>> resultFuture2 = requester.execute(
                new BasicRequestProducer(Method.POST, target, "/other-stuff",
                        new StringAsyncEntityProducer("some other stuff", ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), TIMEOUT, null);
        final Message<HttpResponse, String> message2 = resultFuture2.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(message2);
        final HttpResponse response2 = message2.head();
        Assertions.assertEquals(HttpStatus.SC_OK, response2.getCode());
        final String body2 = message2.body();
        Assertions.assertEquals("some other stuff", body2);

        Thread.sleep(100);

        final Future<Message<HttpResponse, String>> resultFuture3 = requester.execute(
                new BasicRequestProducer(Method.POST, target, "/more-stuff",
                        new StringAsyncEntityProducer("some more stuff", ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), TIMEOUT, null);
        final Message<HttpResponse, String> message3 = resultFuture3.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(message3);
        final HttpResponse response3 = message3.head();
        Assertions.assertEquals(HttpStatus.SC_OK, response3.getCode());
        final String body3 = message3.body();
        Assertions.assertEquals("some more stuff", body3);
    }

    @Test
    void testMultiplexedRequestCancellation() throws Exception {
        final HttpAsyncServer server = serverResource.start();
        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), scheme);
        final ListenerEndpoint listener = future.get();
        final InetSocketAddress address = (InetSocketAddress) listener.getAddress();
        final H2MultiplexingRequester requester = clientResource.start();

        final int reqNo = 20;

        final CountDownLatchFutureCallback<Message<HttpResponse, String>> countDownLatch = new CountDownLatchFutureCallback<>(reqNo);
        final Random random = new Random();
        final HttpHost target = new HttpHost(scheme.id, "localhost", address.getPort());
        for (int i = 0; i < reqNo; i++) {
            final Cancellable cancellable = requester.execute(
                    new BasicClientExchangeHandler<>(new BasicRequestProducer(Method.POST, target, "/stuff",
                            new StringAsyncEntityProducer("some stuff", ContentType.TEXT_PLAIN)),
                            new BasicResponseConsumer<>(new StringAsyncEntityConsumer()),
                            countDownLatch),
                    TIMEOUT,
                    HttpCoreContext.create());
            Thread.sleep(random.nextInt(10));
            cancellable.cancel();
        }
        Assertions.assertTrue(countDownLatch.await(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()));
    }

}
