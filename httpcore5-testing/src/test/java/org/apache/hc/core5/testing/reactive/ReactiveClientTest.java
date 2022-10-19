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

import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Observable;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStreamResetException;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.reactive.ReactiveEntityProducer;
import org.apache.hc.core5.reactive.ReactiveResponseConsumer;
import org.apache.hc.core5.reactive.ReactiveServerExchangeHandler;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.apache.hc.core5.testing.nio.extension.H2AsyncRequesterResource;
import org.apache.hc.core5.testing.nio.extension.H2AsyncServerResource;
import org.apache.hc.core5.testing.reactive.Reactive3TestUtils.StreamDescription;
import org.apache.hc.core5.util.TextUtils;
import org.apache.hc.core5.util.Timeout;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.reactivestreams.Publisher;

public abstract class ReactiveClientTest {

    private static final Timeout SOCKET_TIMEOUT = Timeout.ofSeconds(30);
    private static final Timeout RESULT_TIMEOUT = Timeout.ofSeconds(60);

    private static final Random RANDOM = new Random();

    private final HttpVersionPolicy versionPolicy;
    @RegisterExtension
    private final H2AsyncServerResource serverResource;
    @RegisterExtension
    private final H2AsyncRequesterResource clientResource;

    public ReactiveClientTest(final HttpVersionPolicy httpVersionPolicy) {
        this.versionPolicy = httpVersionPolicy;
        this.serverResource = new H2AsyncServerResource(bootstrap -> bootstrap
                .setVersionPolicy(versionPolicy)
                .setIOReactorConfig(
                        IOReactorConfig.custom()
                                .setSoTimeout(SOCKET_TIMEOUT)
                                .build())
                .register("*", () -> new ReactiveServerExchangeHandler(new ReactiveEchoProcessor()))
        );
        this.clientResource = new H2AsyncRequesterResource(bootstrap -> bootstrap
                .setVersionPolicy(versionPolicy)
                .setIOReactorConfig(IOReactorConfig.custom()
                        .setSoTimeout(SOCKET_TIMEOUT)
                        .build())
        );
    }

    @Test
    public void testSimpleRequest() throws Exception {
        final InetSocketAddress address = startServer();
        final HttpAsyncRequester requester = clientResource.start();
        final byte[] input = new byte[1024];
        RANDOM.nextBytes(input);
        final Publisher<ByteBuffer> publisher = Flowable.just(ByteBuffer.wrap(input));
        final ReactiveEntityProducer producer = new ReactiveEntityProducer(publisher, input.length, null, null);

        final BasicRequestProducer request = getRequestProducer(address, producer);

        final ReactiveResponseConsumer consumer = new ReactiveResponseConsumer();
        requester.execute(request, consumer, SOCKET_TIMEOUT, null);

        final Message<HttpResponse, Publisher<ByteBuffer>> response = consumer.getResponseFuture()
                .get(RESULT_TIMEOUT.getDuration(), RESULT_TIMEOUT.getTimeUnit());

        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final WritableByteChannel writableByteChannel = Channels.newChannel(byteArrayOutputStream);
        for (final ByteBuffer byteBuffer : Observable.fromPublisher(response.getBody()).toList().blockingGet()) {
            writableByteChannel.write(byteBuffer);
        }
        writableByteChannel.close();
        final byte[] output = byteArrayOutputStream.toByteArray();
        Assertions.assertArrayEquals(input, output);
    }

    private BasicRequestProducer getRequestProducer(final InetSocketAddress address, final ReactiveEntityProducer producer) {
        return new BasicRequestProducer(Method.POST, URI.create("http://localhost:" + address.getPort()), producer);
    }

    @Test
    public void testLongRunningRequest() throws Exception {
        final InetSocketAddress address = startServer();
        final HttpAsyncRequester requester = clientResource.start();
        final long expectedLength = 6_554_200L;
        final AtomicReference<String> expectedHash = new AtomicReference<>();
        final Flowable<ByteBuffer> stream = Reactive3TestUtils.produceStream(expectedLength, expectedHash);
        final ReactiveEntityProducer producer = new ReactiveEntityProducer(stream, -1, null, null);
        final BasicRequestProducer request = getRequestProducer(address, producer);

        final ReactiveResponseConsumer consumer = new ReactiveResponseConsumer();
        requester.execute(request, consumer, SOCKET_TIMEOUT, null);
        final Message<HttpResponse, Publisher<ByteBuffer>> response = consumer.getResponseFuture()
                .get(RESULT_TIMEOUT.getDuration(), RESULT_TIMEOUT.getTimeUnit());
        final StreamDescription desc = Reactive3TestUtils.consumeStream(response.getBody()).blockingGet();

        Assertions.assertEquals(expectedLength, desc.length);
        Assertions.assertEquals(expectedHash.get(), TextUtils.toHexString(desc.md.digest()));
    }

    @Test
    public void testManySmallBuffers() throws Exception {
        // This test is not flaky. If it starts randomly failing, then there is a problem with how
        // ReactiveDataConsumer signals capacity with its capacity channel. The situations in which
        // this kind of bug manifests depend on the ordering of several events on different threads
        // so it's unlikely to consistently occur.
        final InetSocketAddress address = startServer();
        final HttpAsyncRequester requester = clientResource.start();
        for (int i = 0; i < 10; i++) {
            final long expectedLength = 1_024_000;
            final int maximumBlockSize = 1024;
            final AtomicReference<String> expectedHash = new AtomicReference<>();
            final Publisher<ByteBuffer> stream = Reactive3TestUtils.produceStream(expectedLength, maximumBlockSize, expectedHash);
            final ReactiveEntityProducer producer = new ReactiveEntityProducer(stream, -1, null, null);
            final BasicRequestProducer request = getRequestProducer(address, producer);

            final ReactiveResponseConsumer consumer = new ReactiveResponseConsumer();
            requester.execute(request, consumer, SOCKET_TIMEOUT, null);
            final Message<HttpResponse, Publisher<ByteBuffer>> response = consumer.getResponseFuture()
                    .get(RESULT_TIMEOUT.getDuration(), RESULT_TIMEOUT.getTimeUnit());
            final StreamDescription desc = Reactive3TestUtils.consumeStream(response.getBody()).blockingGet();

            Assertions.assertEquals(expectedLength, desc.length);
            Assertions.assertEquals(expectedHash.get(), TextUtils.toHexString(desc.md.digest()));
        }
    }

    @Test
    public void testRequestError() throws Exception {
        final InetSocketAddress address = startServer();
        final HttpAsyncRequester requester = clientResource.start();
        final RuntimeException exceptionThrown = new RuntimeException("Test");
        final Publisher<ByteBuffer> publisher = Flowable.error(exceptionThrown);
        final ReactiveEntityProducer producer = new ReactiveEntityProducer(publisher, 100, null, null);

        final BasicRequestProducer request = getRequestProducer(address, producer);

        final ReactiveResponseConsumer consumer = new ReactiveResponseConsumer();

        final Future<Void> future = requester.execute(request, consumer, SOCKET_TIMEOUT, null);

        final ExecutionException exception = Assertions.assertThrows(ExecutionException.class, () ->
                future.get(RESULT_TIMEOUT.getDuration(), RESULT_TIMEOUT.getTimeUnit()));
        Assertions.assertTrue(exception.getCause() instanceof HttpStreamResetException);
        Assertions.assertSame(exceptionThrown, exception.getCause().getCause());
    }

    @Test
    public void testRequestTimeout() throws Exception {
        final InetSocketAddress address = startServer();
        final HttpAsyncRequester requester = clientResource.start();
        final AtomicBoolean requestPublisherWasCancelled = new AtomicBoolean(false);
        final Publisher<ByteBuffer> publisher = Flowable.<ByteBuffer>never()
                .doOnCancel(() -> requestPublisherWasCancelled.set(true));
        final ReactiveEntityProducer producer = new ReactiveEntityProducer(publisher, -1, null, null);
        final BasicRequestProducer request = getRequestProducer(address, producer);

        final ReactiveResponseConsumer consumer = new ReactiveResponseConsumer();
        final Future<Void> future = requester.execute(request, consumer, Timeout.ofSeconds(1), null);

        final ExecutionException exception = Assertions.assertThrows(ExecutionException.class, () ->
                future.get(RESULT_TIMEOUT.getDuration(), RESULT_TIMEOUT.getTimeUnit()));
        Assertions.assertTrue(requestPublisherWasCancelled.get());
        final Throwable cause = exception.getCause();
        if (versionPolicy == HttpVersionPolicy.FORCE_HTTP_1) {
            Assertions.assertTrue(cause instanceof SocketTimeoutException, "Expected SocketTimeoutException, but got " + cause.getClass().getName());
        } else if (versionPolicy == HttpVersionPolicy.FORCE_HTTP_2) {
            Assertions.assertTrue(cause instanceof HttpStreamResetException, format("Expected RST_STREAM, but %s was thrown", cause.getClass().getName()));
        } else {
            Assertions.fail("Unknown HttpVersionPolicy: " + versionPolicy);
        }
    }

    @Test
    public void testResponseCancellation() throws Exception {
        final InetSocketAddress address = startServer();
        final HttpAsyncRequester requester = clientResource.start();
        final AtomicBoolean requestPublisherWasCancelled = new AtomicBoolean(false);
        final AtomicReference<Throwable> requestStreamError = new AtomicReference<>();
        final Publisher<ByteBuffer> stream = Reactive3TestUtils.produceStream(Long.MAX_VALUE, 1024, null)
                .doOnCancel(() -> requestPublisherWasCancelled.set(true))
                .doOnError(requestStreamError::set);
        final ReactiveEntityProducer producer = new ReactiveEntityProducer(stream, -1, null, null);
        final BasicRequestProducer request = getRequestProducer(address, producer);

        final ReactiveResponseConsumer consumer = new ReactiveResponseConsumer();
        final Future<Void> future = requester.execute(request, consumer, SOCKET_TIMEOUT, null);
        final Message<HttpResponse, Publisher<ByteBuffer>> response = consumer.getResponseFuture()
                .get(RESULT_TIMEOUT.getDuration(), RESULT_TIMEOUT.getTimeUnit());

        final AtomicBoolean responsePublisherWasCancelled = new AtomicBoolean(false);
        final List<ByteBuffer> outputBuffers = Flowable.fromPublisher(response.getBody())
                .doOnCancel(() -> responsePublisherWasCancelled.set(true))
                .take(3)
                .toList()
                .blockingGet();
        Assertions.assertEquals(3, outputBuffers.size());
        Assertions.assertTrue(responsePublisherWasCancelled.get(), "The response subscription should have been cancelled");
        final Exception exception = Assertions.assertThrows(Exception.class, () ->
                future.get(RESULT_TIMEOUT.getDuration(), RESULT_TIMEOUT.getTimeUnit()));
        assertThat(exception, CoreMatchers.anyOf(
                CoreMatchers.instanceOf(CancellationException.class),
                CoreMatchers.instanceOf(ExecutionException.class)));
        Assertions.assertTrue(exception.getCause() instanceof HttpStreamResetException);
        Assertions.assertTrue(requestPublisherWasCancelled.get());
        Assertions.assertNull(requestStreamError.get());
    }

    private InetSocketAddress startServer() throws IOException, InterruptedException, ExecutionException {
        final HttpAsyncServer server = serverResource.start();
        final ListenerEndpoint listener = server.listen(new InetSocketAddress(0), URIScheme.HTTP).get();
        final InetSocketAddress address = (InetSocketAddress) listener.getAddress();
        return address;
    }
}
