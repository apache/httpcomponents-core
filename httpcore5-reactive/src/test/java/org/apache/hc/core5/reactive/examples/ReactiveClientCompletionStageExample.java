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

import java.net.InetAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
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

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Observable;

/**
 * Client demo using CompletionStage accessors on ReactiveResponseConsumer (Java 8).
 */
public final class ReactiveClientCompletionStageExample {

    private ReactiveClientCompletionStageExample() {
    }

    private static <T> CompletionStage<T> withTimeout(
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

    private static boolean isLoopbackHost(final String host) {
        if (host == null) {
            return false;
        }
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
    }

    private static URI normalizeAuthority(final URI uri) {
        if (!isLoopbackHost(uri.getHost())) {
            return uri;
        }
        try {
            final InetAddress local = InetAddress.getLocalHost();
            final String canonical = local.getCanonicalHostName();
            if (canonical != null && !canonical.isEmpty() && !isLoopbackHost(canonical)) {
                return new URI(uri.getScheme(), null, canonical, uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment());
            }
            final String addr = local.getHostAddress();
            if (addr != null && !addr.isEmpty() && !isLoopbackHost(addr)) {
                return new URI(uri.getScheme(), null, addr, uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment());
            }
        } catch (final Exception ignore) {
            // fall back
        }
        return uri;
    }

    public static void main(final String[] args) throws Exception {
        String endpoint = "http://localhost:8080/echo";
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
                            System.out.println(connection.getRemoteAddress()
                                    + " exchange completed (connection kept alive)");
                        } else {
                            System.out.println(connection.getRemoteAddress()
                                    + " exchange completed (connection closed)");
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
            final URI uri = normalizeAuthority(new URI(endpoint));

            final Random random = new Random();
            final Flowable<ByteBuffer> requestBody = Flowable.range(1, 100)
                    .map(i -> ByteBuffer.wrap((i + ":" + random.nextDouble() + "\n").getBytes(UTF_8)));

            final AsyncRequestProducer requestProducer = AsyncRequestBuilder.post(uri)
                    .setEntity(new ReactiveEntityProducer(requestBody, -1, ContentType.TEXT_PLAIN, null))
                    .build();

            final ReactiveResponseConsumer consumer = new ReactiveResponseConsumer();
            requester.execute(requestProducer, consumer, Timeout.ofSeconds(30), null);

            consumer.getFailureStage().whenComplete((t, ex) -> {
                if (ex != null) {
                    ex.printStackTrace();
                } else if (t != null) {
                    System.out.println("Request failed: " + t);
                }
            });

            final CompletableFuture<Void> printedAndDrained = new CompletableFuture<>();

            consumer.getResponseStage().whenComplete((msg, ex) -> {
                if (ex != null) {
                    printedAndDrained.completeExceptionally(ex);
                    return;
                }
                try {
                    System.out.println(msg.getHead());
                    for (final Header h : msg.getHead().getHeaders()) {
                        System.out.println(h);
                    }
                    System.out.println();

                    readBodyAsString(msg.getBody()).whenComplete((body, ex2) -> {
                        if (ex2 != null) {
                            printedAndDrained.completeExceptionally(ex2);
                        } else {
                            if (body != null && !body.isEmpty()) {
                                System.out.print(body);
                                if (!body.endsWith("\n")) {
                                    System.out.println();
                                }
                            }
                            printedAndDrained.complete(null);
                        }
                    });
                } catch (final RuntimeException e) {
                    printedAndDrained.completeExceptionally(e);
                }
            });

            final CompletableFuture<Void> exchangeDone = consumer.getResponseCompletionStage().toCompletableFuture();

            final CompletableFuture<Void> both = CompletableFuture.allOf(printedAndDrained, exchangeDone);
            withTimeout(both, scheduler, 60, TimeUnit.SECONDS).toCompletableFuture().get();

            System.out.println("Shutting down I/O reactor");
            requester.initiateShutdown();

        } finally {
            scheduler.shutdownNow();
        }
    }
}
