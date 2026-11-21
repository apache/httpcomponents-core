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


import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.nio.AsyncClientEndpoint;
import org.apache.hc.core5.http.nio.support.AsyncClientPipeline;
import org.apache.hc.core5.http.nio.support.AsyncRequestBuilder;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.apache.hc.core5.util.Timeout;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

abstract class HttpCoreTransportTest {

    private static final Timeout TIMEOUT = Timeout.ofMinutes(1);

    final URIScheme scheme;

    public HttpCoreTransportTest(final URIScheme scheme) {
        this.scheme = scheme;
    }

    abstract HttpAsyncServer serverStart() throws IOException;

    abstract HttpAsyncRequester clientStart() throws IOException;

    HttpHost connectToListener() throws Exception {
        final HttpAsyncServer server = serverStart();
        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), scheme);
        final ListenerEndpoint listener = future.get();
        final InetSocketAddress address = (InetSocketAddress) listener.getAddress();
        return new HttpHost(scheme.id, "localhost", address.getPort());
    }

    @ValueSource(ints = {0, 2048, 10240})
    @ParameterizedTest(name = "{displayName}; content length: {0}")
    void testSequentialGetRequests(final int contentSize) throws Exception {
        final HttpHost target = connectToListener();
        final HttpAsyncRequester requester = clientStart();

        final int reqCount = 20;

        for (int i = 0; i < reqCount; i++) {
            final CompletableFuture<Message<HttpResponse, String>> resultFuture = new CompletableFuture<>();
            requester.execute(
                    AsyncClientPipeline.assemble()
                            .request()
                            .get(target, "/random/" + contentSize)
                            .response()
                            .asString()
                            .result(resultFuture)
                            .create(),
                    TIMEOUT,
                    HttpCoreContext.create());
            final Message<HttpResponse, String> response = resultFuture.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertThat(response).isNotNull();
            Assertions.assertThat(response.head())
                    .isNotNull()
                    .extracting(HttpResponse::getCode).isEqualTo(200);
            Assertions.assertThat(response.body())
                    .isNotNull()
                    .hasSize(contentSize);
        }
    }

    @Test
    void testSequentialHeadRequests() throws Exception {
        final HttpHost target = connectToListener();
        final HttpAsyncRequester requester = clientStart();

        final int reqCount = 20;

        for (int i = 0; i < reqCount; i++) {
            final CompletableFuture<Message<HttpResponse, String>> resultFuture = new CompletableFuture<>();
            requester.execute(
                    AsyncClientPipeline.assemble()
                            .request()
                            .produce(AsyncRequestBuilder.head()
                                    .setHttpHost(target)
                                    .setPath("/random/2048")
                                    .build())
                            .response()
                            .asString()
                            .result(resultFuture)
                            .create(),
                    TIMEOUT,
                    HttpCoreContext.create());
            final Message<HttpResponse, String> response = resultFuture.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertThat(response).isNotNull();
            Assertions.assertThat(response.head())
                    .isNotNull()
                    .extracting(HttpResponse::getCode).isEqualTo(200);
            Assertions.assertThat(response.body())
                    .isNull();
        }
    }

    @ValueSource(ints = {0, 2048, 10240})
    @ParameterizedTest(name = "{displayName}; content length: {0}")
    void testConcurrentGetRequests(final int contentSize) throws Exception {
        final HttpHost target = connectToListener();
        final HttpAsyncRequester requester = clientStart();

        final int reqCount = 20;

        final Queue<Future<Message<HttpResponse, String>>> queue = new LinkedList<>();
        for (int i = 0; i < reqCount; i++) {
            final CompletableFuture<Message<HttpResponse, String>> resultFuture = new CompletableFuture<>();
            requester.execute(
                    AsyncClientPipeline.assemble()
                            .request()
                            .get(target, "/random/" + contentSize)
                            .response()
                            .asString()
                            .result(resultFuture)
                            .create(),
                    TIMEOUT,
                    HttpCoreContext.create());
            queue.add(resultFuture);
        }

        while (!queue.isEmpty()) {
            final Future<Message<HttpResponse, String>> future = queue.remove();
            final Message<HttpResponse, String> response = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertThat(response).isNotNull();
            Assertions.assertThat(response.head())
                    .isNotNull()
                    .extracting(HttpResponse::getCode).isEqualTo(200);
            Assertions.assertThat(response.body())
                    .isNotNull()
                    .hasSize(contentSize);
        }
    }

    @ValueSource(ints = {0, 2048, 10240})
    @ParameterizedTest(name = "{displayName}; content length: {0}")
    void testSequentialPostRequests(final int contentSize) throws Exception {
        final HttpHost target = connectToListener();
        final HttpAsyncRequester requester = clientStart();

        final int reqCount = 20;

        for (int i = 0; i < reqCount; i++) {
            final CompletableFuture<Message<HttpResponse, String>> resultFuture = new CompletableFuture<>();
            requester.execute(
                    AsyncClientPipeline.assemble()
                            .request()
                            .post(target, "/echo")
                            .produceContent(new RandomBinAsyncEntityProducer(contentSize))
                            .response()
                            .asString()
                            .result(resultFuture)
                            .create(),
                    TIMEOUT,
                    HttpCoreContext.create());
            final Message<HttpResponse, String> response = resultFuture.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertThat(response).isNotNull();
            Assertions.assertThat(response.head())
                    .isNotNull()
                    .extracting(HttpResponse::getCode).isEqualTo(200);
            Assertions.assertThat(response.body())
                    .isNotNull()
                    .hasSize(contentSize);
        }
    }

    @ValueSource(ints = {0, 2048, 10240})
    @ParameterizedTest(name = "{displayName}; content length: {0}")
    void testConcurrentPostRequests(final int contentSize) throws Exception {
        final HttpHost target = connectToListener();
        final HttpAsyncRequester requester = clientStart();

        final int reqCount = 20;

        final Queue<Future<Message<HttpResponse, String>>> queue = new LinkedList<>();
        for (int i = 0; i < reqCount; i++) {
            final CompletableFuture<Message<HttpResponse, String>> resultFuture = new CompletableFuture<>();
            requester.execute(
                    AsyncClientPipeline.assemble()
                            .request()
                            .post(target, "/echo")
                            .produceContent(new RandomBinAsyncEntityProducer(contentSize))
                            .response()
                            .asString()
                            .result(resultFuture)
                            .create(),
                    TIMEOUT,
                    HttpCoreContext.create());
            queue.add(resultFuture);
        }

        while (!queue.isEmpty()) {
            final Future<Message<HttpResponse, String>> future = queue.remove();
            final Message<HttpResponse, String> response = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertThat(response).isNotNull();
            Assertions.assertThat(response.head())
                    .isNotNull()
                    .extracting(HttpResponse::getCode).isEqualTo(200);
            Assertions.assertThat(response.body())
                    .isNotNull()
                    .hasSize(contentSize);
        }
    }

    @ValueSource(ints = {0, 2048, 10240})
    @ParameterizedTest(name = "{displayName}; content length: {0}")
    void testGetPipelining(final int contentSize) throws Exception {
        final HttpHost target = connectToListener();
        final HttpAsyncRequester requester = clientStart();

        final Future<AsyncClientEndpoint> endpointFuture = requester.connect(target, Timeout.ofSeconds(5));
        final AsyncClientEndpoint endpoint = endpointFuture.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        try {
            final int reqCount = 20;

            final Queue<Future<Message<HttpResponse, String>>> queue = new LinkedList<>();
            for (int i = 0; i < reqCount; i++) {
                final CompletableFuture<Message<HttpResponse, String>> resultFuture = new CompletableFuture<>();
                requester.execute(
                        AsyncClientPipeline.assemble()
                                .request()
                                .get(target, "/random/" + contentSize)
                                .response()
                                .asString()
                                .result(resultFuture)
                                .create(),
                        TIMEOUT,
                        HttpCoreContext.create());
                queue.add(resultFuture);
            }

            while (!queue.isEmpty()) {
                final Future<Message<HttpResponse, String>> future = queue.remove();
                final Message<HttpResponse, String> response = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
                Assertions.assertThat(response).isNotNull();
                Assertions.assertThat(response.head())
                        .isNotNull()
                        .extracting(HttpResponse::getCode).isEqualTo(200);
                Assertions.assertThat(response.body())
                        .isNotNull()
                        .hasSize(contentSize);
            }
        } finally {
            endpoint.releaseAndReuse();
        }
    }

    @ValueSource(ints = {0, 2048, 10240})
    @ParameterizedTest(name = "{displayName}; content length: {0}")
    void testPostPipelining(final int contentSize) throws Exception {
        final HttpHost target = connectToListener();
        final HttpAsyncRequester requester = clientStart();

        final Future<AsyncClientEndpoint> endpointFuture = requester.connect(target, Timeout.ofSeconds(5));
        final AsyncClientEndpoint endpoint = endpointFuture.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        try {
            final int reqCount = 20;

            final Queue<Future<Message<HttpResponse, String>>> queue = new LinkedList<>();
            for (int i = 0; i < reqCount; i++) {
                final CompletableFuture<Message<HttpResponse, String>> resultFuture = new CompletableFuture<>();
                requester.execute(
                        AsyncClientPipeline.assemble()
                                .request()
                                .post(target, "/echo")
                                .produceContent(new RandomBinAsyncEntityProducer(contentSize))
                                .response()
                                .asString()
                                .result(resultFuture)
                                .create(),
                        TIMEOUT,
                        HttpCoreContext.create());
                queue.add(resultFuture);
            }

            while (!queue.isEmpty()) {
                final Future<Message<HttpResponse, String>> future = queue.remove();
                final Message<HttpResponse, String> response = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
                Assertions.assertThat(response).isNotNull();
                Assertions.assertThat(response.head())
                        .isNotNull()
                        .extracting(HttpResponse::getCode).isEqualTo(200);
                Assertions.assertThat(response.body())
                        .isNotNull()
                        .hasSize(contentSize);
            }
        } finally {
            endpoint.releaseAndReuse();
        }
    }

    @Test
    void testSequentialInvalidGetRequests() throws Exception {
        final HttpHost target = connectToListener();
        final HttpAsyncRequester requester = clientStart();

        final int reqCount = 20;

        for (int i = 0; i < reqCount; i++) {
            final CompletableFuture<Message<HttpResponse, String>> resultFuture = new CompletableFuture<>();
            requester.execute(
                    AsyncClientPipeline.assemble()
                            .request()
                            .get(target, "/random/boom")
                            .response()
                            .asString()
                            .result(resultFuture)
                            .create(),
                    TIMEOUT,
                    HttpCoreContext.create());
            final Message<HttpResponse, String> response = resultFuture.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertThat(response).isNotNull();
            Assertions.assertThat(response.head())
                    .isNotNull()
                    .extracting(HttpResponse::getCode).isEqualTo(400);
            Assertions.assertThat(response.body())
                    .isNotNull()
                    .isEqualTo("Invalid request path: /random/boom");
        }
    }

}
