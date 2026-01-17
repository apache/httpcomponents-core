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
package org.apache.hc.core5.reactive.examples;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Observable;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.impl.Http1StreamListener;
import org.apache.hc.core5.http.impl.bootstrap.AsyncRequesterBootstrap;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http.message.RequestLine;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.support.AsyncRequestBuilder;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactive.ReactiveEntityProducer;
import org.apache.hc.core5.reactive.ReactiveResponseConsumer;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.Timeout;
import org.reactivestreams.Publisher;

/**
 * Java 8 compatible client example using ReactiveResponseConsumer's new CompletableFuture API.
 * <p>
 * Uses:
 * - consumer.getResponseCompletableFuture(): response head + body publisher ready
 * - consumer.getResponseCompletionFuture(): exchange fully complete
 */
public final class ReactiveCompletableFuturesExample {

    private ReactiveCompletableFuturesExample() {
    }

    private static <T> CompletableFuture<T> withTimeout(
            final CompletableFuture<T> future,
            final ScheduledExecutorService scheduler,
            final long timeout,
            final TimeUnit unit) {

        final CompletableFuture<T> timeoutFuture = new CompletableFuture<>();
        final java.util.concurrent.ScheduledFuture<?> task = scheduler.schedule(
                () -> timeoutFuture.completeExceptionally(new TimeoutException("Timeout after " + timeout + " " + unit)),
                timeout, unit);

        final CompletableFuture<T> combined = future.applyToEither(timeoutFuture, t -> t);
        combined.whenComplete((v, ex) -> task.cancel(false));
        return combined;
    }

    private static CompletableFuture<String> readBodyAsString(final Publisher<ByteBuffer> publisher) {
        final CompletableFuture<String> bodyFuture = new CompletableFuture<>();

        Observable.fromPublisher(publisher)
                .map(buf -> {
                    final byte[] bytes = new byte[buf.remaining()];
                    buf.get(bytes);
                    return new String(bytes, UTF_8);
                })
                .reduce(new StringBuilder(), StringBuilder::append)
                .map(StringBuilder::toString)
                .subscribe(
                        bodyFuture::complete,
                        bodyFuture::completeExceptionally);

        return bodyFuture;
    }

    public static void main(final String[] args) throws Exception {
        String endpoint = "http://manjaro:8080/echo";
        if (args.length >= 1) {
            endpoint = args[0];
        }

        final HttpAsyncRequester requester = AsyncRequesterBootstrap.bootstrap()
                .setIOReactorConfig(IOReactorConfig.custom().setSoTimeout(5, TimeUnit.SECONDS).build())
                .setStreamListener(new Http1StreamListener() {

                    @Override
                    public void onRequestHead(final HttpConnection connection, final HttpRequest request) {
                        System.out.println(connection.getRemoteAddress() + " " + new RequestLine(request));
                    }

                    @Override
                    public void onResponseHead(final HttpConnection connection, final HttpResponse response) {
                        System.out.println(connection.getRemoteAddress() + " " + new StatusLine(response));
                    }

                    @Override
                    public void onExchangeComplete(final HttpConnection connection, final boolean keepAlive) {
                        if (keepAlive) {
                            System.out.println(connection.getRemoteAddress() + " exchange completed (connection kept alive)");
                        } else {
                            System.out.println(connection.getRemoteAddress() + " exchange completed (connection closed)");
                        }
                    }

                })
                .create();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("HTTP requester shutting down");
            requester.close(CloseMode.GRACEFUL);
        }));

        requester.start();

        final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            final Thread t = new Thread(r, "timeout-scheduler");
            t.setDaemon(true);
            return t;
        });

        try {
            final URI uri = new URI(endpoint);

            final Random random = new Random();
            final Flowable<ByteBuffer> requestBody = Flowable.range(1, 100)
                    .map(i -> ByteBuffer.wrap((i + ":" + random.nextDouble() + "\n").getBytes(UTF_8)));

            final AsyncRequestProducer requestProducer = AsyncRequestBuilder.post(uri)
                    .addHeader("X-Demo", "cfutures-java8")
                    .setEntity(new ReactiveEntityProducer(requestBody, -1, ContentType.TEXT_PLAIN, null))
                    .build();

            final ReactiveResponseConsumer consumer = new ReactiveResponseConsumer();

            requester.execute(requestProducer, consumer, Timeout.ofSeconds(30), null);

            final CompletableFuture<Void> printedAndDrained = consumer.getResponseCompletableFuture()
                    .thenCompose((final Message<HttpResponse, Publisher<ByteBuffer>> streamingResponse) -> {

                        final HttpResponse head = streamingResponse.getHead();
                        final int code = head.getCode();

                        System.out.println(head);
                        for (final Header header : head.getHeaders()) {
                            System.out.println(header);
                        }
                        System.out.println();

                        return readBodyAsString(streamingResponse.getBody()).thenApply(body -> {
                            if (body != null && !body.isEmpty()) {
                                System.out.print(body);
                                if (!body.endsWith("\n")) {
                                    System.out.println();
                                }
                            }
                            if (code >= 400) {
                                System.out.println("Request failed: HTTP " + code + " " + head.getReasonPhrase());
                            }
                            return null;
                        });
                    });

            final CompletableFuture<Void> exchangeDone = consumer.getResponseCompletionFuture();

            final CompletableFuture<Void> both = CompletableFuture.allOf(printedAndDrained, exchangeDone);
            withTimeout(both, scheduler, 60, TimeUnit.SECONDS).get();

            System.out.println("Shutting down I/O reactor");
            requester.initiateShutdown();

        } finally {
            scheduler.shutdownNow();
        }
    }
}
