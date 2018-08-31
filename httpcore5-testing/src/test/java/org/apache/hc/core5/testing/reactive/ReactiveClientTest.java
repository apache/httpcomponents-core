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
package org.apache.hc.core5.testing.reactive;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStreamResetException;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.BasicRequestProducer;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2RequesterBootstrap;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2ServerBootstrap;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactive.ReactiveEntityProducer;
import org.apache.hc.core5.reactive.ReactiveResponseConsumer;
import org.apache.hc.core5.reactor.ExceptionEvent;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.apache.hc.core5.testing.classic.LoggingConnPoolListener;
import org.apache.hc.core5.testing.nio.EchoHandler;
import org.apache.hc.core5.testing.nio.LoggingHttp1StreamListener;
import org.apache.hc.core5.testing.nio.LoggingHttp2StreamListener;
import org.apache.hc.core5.testing.nio.LoggingIOSessionDecorator;
import org.apache.hc.core5.testing.nio.LoggingIOSessionListener;
import org.apache.hc.core5.util.Timeout;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;

@RunWith(Parameterized.class)
public class ReactiveClientTest {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> protocols() {
        return Arrays.asList(new Object[][]{
            { HttpVersionPolicy.FORCE_HTTP_1 },
            { HttpVersionPolicy.FORCE_HTTP_2 }
        });
    }
    private static final Timeout TIMEOUT = Timeout.ofSeconds(3000);
    private static final Random RANDOM = new Random();

    private final HttpVersionPolicy versionPolicy;

    public ReactiveClientTest(final HttpVersionPolicy httpVersionPolicy) {
        this.versionPolicy = httpVersionPolicy;
    }

    private HttpAsyncServer server;

    @Rule
    public ExternalResource serverResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            log.debug("Starting up test server");
            server = H2ServerBootstrap.bootstrap()
                .setVersionPolicy(versionPolicy)
                .setIOReactorConfig(
                    IOReactorConfig.custom()
                        .setSoTimeout(TIMEOUT)
                        .build())
                .setIOSessionListener(LoggingIOSessionListener.INSTANCE)
                .setStreamListener(LoggingHttp1StreamListener.INSTANCE_SERVER)
                .setStreamListener(LoggingHttp2StreamListener.INSTANCE)
                .setIOSessionDecorator(LoggingIOSessionDecorator.INSTANCE)
                .register("*", new Supplier<AsyncServerExchangeHandler>() {

                    @Override
                    public AsyncServerExchangeHandler get() {
                        return new EchoHandler(10 * 1024 * 1024);
                    }

                })
                .create();
        }

        @Override
        protected void after() {
            log.debug("Shutting down test server");
            if (server != null) {
                try {
                    server.close(CloseMode.GRACEFUL);
                    final List<ExceptionEvent> exceptionLog = server.getExceptionLog();
                    server = null;
                    if (!exceptionLog.isEmpty()) {
                        for (final ExceptionEvent event: exceptionLog) {
                            final Throwable cause = event.getCause();
                            log.error("Unexpected " + cause.getClass() + " at " + event.getTimestamp(), cause);
                        }
                    }
                } catch (final Exception ignore) {
                }
            }
        }

    };

    private HttpAsyncRequester requester;

    @Rule
    public ExternalResource clientResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            log.debug("Starting up test client");
            requester = H2RequesterBootstrap.bootstrap()
                .setVersionPolicy(versionPolicy)
                .setIOReactorConfig(IOReactorConfig.custom()
                    .setSoTimeout(TIMEOUT)
                    .build())
                .setIOSessionListener(LoggingIOSessionListener.INSTANCE)
                .setStreamListener(LoggingHttp1StreamListener.INSTANCE_CLIENT)
                .setStreamListener(LoggingHttp2StreamListener.INSTANCE)
                .setConnPoolListener(LoggingConnPoolListener.INSTANCE)
                .setIOSessionDecorator(LoggingIOSessionDecorator.INSTANCE)
                .create();
        }

        @Override
        protected void after() {
            log.debug("Shutting down test client");
            if (requester != null) {
                try {
                    requester.close(CloseMode.GRACEFUL);
                    final List<ExceptionEvent> exceptionLog = requester.getExceptionLog();
                    requester = null;
                    if (!exceptionLog.isEmpty()) {
                        for (final ExceptionEvent event: exceptionLog) {
                            final Throwable cause = event.getCause();
                            log.error("Unexpected " + cause.getClass() + " at " + event.getTimestamp(), cause);
                        }
                    }
                } catch (final Exception ignore) {
                }
            }
        }

    };

    @Test(timeout = 5_000)
    public void testSimpleRequest() throws Exception {
        final InetSocketAddress address = startClientAndServer();
        final byte[] input = new byte[1024];
        RANDOM.nextBytes(input);
        final Publisher<ByteBuffer> publisher = Flowable.just(ByteBuffer.wrap(input));
        final ReactiveEntityProducer producer = new ReactiveEntityProducer(publisher, input.length, null, null);

        final BasicRequestProducer request = getRequestProducer(address, producer);

        final ReactiveResponseConsumer consumer = new ReactiveResponseConsumer();
        requester.execute(request, consumer, Timeout.ofSeconds(2), null);

        final Message<HttpResponse, Publisher<ByteBuffer>> response = consumer.getResponseFuture().get();

        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final WritableByteChannel writableByteChannel = Channels.newChannel(byteArrayOutputStream);
        for (final ByteBuffer byteBuffer : Observable.fromPublisher(response.getBody()).toList().blockingGet()) {
            writableByteChannel.write(byteBuffer);
        }
        writableByteChannel.close();
        final byte[] output = byteArrayOutputStream.toByteArray();
        Assert.assertArrayEquals(input, output);
    }

    private BasicRequestProducer getRequestProducer(final InetSocketAddress address, final ReactiveEntityProducer producer) {
        return new BasicRequestProducer("POST",
            URI.create("http://localhost:" + address.getPort()), producer);
    }

    @Test(timeout = 20_000)
    public void testLongRunningRequest() throws Exception {
        final InetSocketAddress address = startClientAndServer();
        final AtomicLong requestLength = new AtomicLong(0L);
        final AtomicReference<MessageDigest> requestDigest = new AtomicReference<>(newDigest());
        final Publisher<ByteBuffer> publisher = Flowable.rangeLong(1, 500)
            .map(new Function<Long, ByteBuffer>() {
                @Override
                public ByteBuffer apply(final Long seed) {
                    final Random random = new Random(seed);
                    final byte[] bytes = new byte[1 + random.nextInt(32 * 1024)];
                    requestLength.addAndGet(bytes.length);
                    random.nextBytes(bytes);
                    return ByteBuffer.wrap(bytes);
                }
            })
            .doOnNext(new Consumer<ByteBuffer>() {
                @Override
                public void accept(final ByteBuffer byteBuffer) {
                    requestDigest.get().update(byteBuffer.duplicate());
                }
            });
        final ReactiveEntityProducer producer = new ReactiveEntityProducer(publisher, -1, null, null);
        final BasicRequestProducer request = getRequestProducer(address, producer);

        final ReactiveResponseConsumer consumer = new ReactiveResponseConsumer();
        requester.execute(request, consumer, Timeout.ofSeconds(2), null);
        final Message<HttpResponse, Publisher<ByteBuffer>> response = consumer.getResponseFuture().get();

        final AtomicLong responseLength = new AtomicLong(0);
        final AtomicReference<MessageDigest> responseDigest = new AtomicReference<>(newDigest());
        Flowable.fromPublisher(response.getBody())
            .blockingForEach(new Consumer<ByteBuffer>() {
                @Override
                public void accept(final ByteBuffer byteBuffer) {
                    responseLength.addAndGet(byteBuffer.remaining());
                    responseDigest.get().update(byteBuffer);
                }
            });
        Assert.assertEquals(requestLength.get(), responseLength.get());
        Assert.assertArrayEquals(requestDigest.get().digest(), responseDigest.get().digest());
    }

    @Test(timeout = 5_000)
    public void testRequestError() throws Exception {
        final InetSocketAddress address = startClientAndServer();
        final RuntimeException exceptionThrown = new RuntimeException("Test");
        final Publisher<ByteBuffer> publisher = Flowable.error(exceptionThrown);
        final ReactiveEntityProducer producer = new ReactiveEntityProducer(publisher, 100, null, null);

        final BasicRequestProducer request = getRequestProducer(address, producer);

        final ReactiveResponseConsumer consumer = new ReactiveResponseConsumer();

        final Future<Void> future = requester.execute(request, consumer, Timeout.ofSeconds(1), null);

        try {
            future.get();
            Assert.fail("Expected exception");
        } catch (final ExecutionException ex) {
            Assert.assertTrue(ex.getCause() instanceof HttpStreamResetException);
            Assert.assertSame(exceptionThrown, ex.getCause().getCause());
        }
    }

    @Test(timeout = 5_000)
    public void testRequestTimeout() throws Exception {
        final InetSocketAddress address = startClientAndServer();
        final AtomicBoolean requestPublisherWasCancelled = new AtomicBoolean(false);
        final Publisher<ByteBuffer> publisher = Flowable.<ByteBuffer>never()
            .doOnCancel(new Action() {
                @Override
                public void run() {
                    requestPublisherWasCancelled.set(true);
                }
            });
        final ReactiveEntityProducer producer = new ReactiveEntityProducer(publisher, -1, null, null);
        final BasicRequestProducer request = getRequestProducer(address, producer);

        final ReactiveResponseConsumer consumer = new ReactiveResponseConsumer();
        final Future<Void> future = requester.execute(request, consumer, Timeout.ofSeconds(1), null);

        try {
            future.get();
        } catch (final ExecutionException ex) {
            Assert.assertTrue(requestPublisherWasCancelled.get());
            final Throwable cause = ex.getCause();
            if (versionPolicy == HttpVersionPolicy.FORCE_HTTP_1) {
                Assert.assertTrue("Expected SocketTimeoutException, but got " + cause.getClass().getName(),
                    cause instanceof SocketTimeoutException);
            } else if (versionPolicy == HttpVersionPolicy.FORCE_HTTP_2) {
                Assert.assertTrue(String.format("Expected RST_STREAM, but %s was thrown", cause.getClass().getName()),
                    cause instanceof HttpStreamResetException);
            } else {
                Assert.fail("Unknown HttpVersionPolicy: " + versionPolicy);
            }
        }
    }

    @Test(timeout = 5_000)
    public void testResponseCancellation() throws Exception {
        final InetSocketAddress address = startClientAndServer();
        final AtomicBoolean requestPublisherWasCancelled = new AtomicBoolean(false);
        final AtomicReference<Throwable> requestStreamError = new AtomicReference<>();
        final Publisher<ByteBuffer> publisher = Flowable.rangeLong(Long.MIN_VALUE, Long.MAX_VALUE)
            .map(new Function<Long, ByteBuffer>() {
                @Override
                public ByteBuffer apply(final Long seed) throws Exception {
                    final Random random = new Random(seed);
                    final byte[] bytes = new byte[1 + random.nextInt(1024)];
                    random.nextBytes(bytes);
                    return ByteBuffer.wrap(bytes);
                }
            })
            .doOnCancel(new Action() {
                @Override
                public void run() throws Exception {
                    requestPublisherWasCancelled.set(true);
                }
            })
            .doOnError(new Consumer<Throwable>() {
                @Override
                public void accept(final Throwable throwable) throws Exception {
                    requestStreamError.set(throwable);
                }
            });
        final ReactiveEntityProducer producer = new ReactiveEntityProducer(publisher, -1, null, null);
        final BasicRequestProducer request = getRequestProducer(address, producer);

        final ReactiveResponseConsumer consumer = new ReactiveResponseConsumer();
        final Future<Void> future = requester.execute(request, consumer, Timeout.ofSeconds(1), null);
        final Message<HttpResponse, Publisher<ByteBuffer>> response = consumer.getResponseFuture().get();

        final AtomicBoolean responsePublisherWasCancelled = new AtomicBoolean(false);
        final List<ByteBuffer> outputBuffers = Flowable.fromPublisher(response.getBody())
            .doOnCancel(new Action() {
                @Override
                public void run() throws Exception {
                    responsePublisherWasCancelled.set(true);
                }
            })
            .take(3)
            .toList()
            .blockingGet();
        Assert.assertEquals(3, outputBuffers.size());
        Assert.assertTrue("The response subscription should have been cancelled", responsePublisherWasCancelled.get());
        try {
            future.get();
            Assert.fail("Expected exception");
        } catch (final ExecutionException | CancellationException ex) {
            Assert.assertTrue(ex.getCause() instanceof HttpStreamResetException);
            Assert.assertTrue(requestPublisherWasCancelled.get());
            Assert.assertNull(requestStreamError.get());
        }
    }

    private static MessageDigest newDigest() throws NoSuchAlgorithmException {
        return MessageDigest.getInstance("MD5");
    }

    private InetSocketAddress startClientAndServer() throws InterruptedException, ExecutionException {
        server.start();
        final ListenerEndpoint listener = server.listen(new InetSocketAddress(0)).get();
        final InetSocketAddress address = (InetSocketAddress) listener.getAddress();
        requester.start();
        return address;
    }
}
