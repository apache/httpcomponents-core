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
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

import io.reactivex.Flowable;
import io.reactivex.Notification;
import io.reactivex.Observable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;

/**
 * Example of full-duplex HTTP/1.1 message exchanges using reactive streaming. This demo will stream randomly
 * generated text to the server via a POST request, while writing the response stream's events to standard output.
 * This demo works out-of-the-box with {@link ReactiveFullDuplexServerExample}.
 */
public class ReactiveFullDuplexClientExample {

    public static void main(final String[] args) throws Exception {
        String endpoint = "http://localhost:8080/echo";
        if (args.length >= 1) {
            endpoint = args[0];
        }

        // Create and start requester
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

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                System.out.println("HTTP requester shutting down");
                requester.close(CloseMode.GRACEFUL);
            }
        });
        requester.start();

        final Random random = new Random();
        final Flowable<ByteBuffer> publisher = Flowable.range(1, 100)
            .map(new Function<Integer, ByteBuffer>() {
                @Override
                public ByteBuffer apply(final Integer ignored) {
                    final String str = random.nextDouble() + "\n";
                    return ByteBuffer.wrap(str.getBytes(UTF_8));
                }
            });
        final AsyncRequestProducer requestProducer = AsyncRequestBuilder.post(new URI(endpoint))
                .setEntity(new ReactiveEntityProducer(publisher, -1, ContentType.TEXT_PLAIN, null))
                .build();

        final ReactiveResponseConsumer consumer = new ReactiveResponseConsumer();
        final Future<Void> responseComplete = requester.execute(requestProducer, consumer, Timeout.ofSeconds(30), null);
        final Message<HttpResponse, Publisher<ByteBuffer>> streamingResponse = consumer.getResponseFuture().get();

        System.out.println(streamingResponse.getHead());
        for (final Header header : streamingResponse.getHead().getHeaders()) {
            System.out.println(header);
        }
        System.out.println();

        Observable.fromPublisher(streamingResponse.getBody())
            .map(new Function<ByteBuffer, String>() {
                @Override
                public String apply(final ByteBuffer byteBuffer) {
                    final byte[] string = new byte[byteBuffer.remaining()];
                    byteBuffer.get(string);
                    return new String(string);
                }
            })
            .materialize()
            .forEach(new Consumer<Notification<String>>() {
                @Override
                public void accept(final Notification<String> byteBufferNotification) {
                    System.out.println(byteBufferNotification);
                }
            });

        responseComplete.get(1, TimeUnit.MINUTES);
        System.out.println("Shutting down I/O reactor");
        requester.initiateShutdown();
    }

}
