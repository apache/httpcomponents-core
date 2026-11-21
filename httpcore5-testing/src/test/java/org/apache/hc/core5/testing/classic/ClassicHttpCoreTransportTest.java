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

package org.apache.hc.core5.testing.classic;


import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.bootstrap.HttpRequester;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.testing.Result;
import org.apache.hc.core5.testing.extension.classic.ExecutorResource;
import org.apache.hc.core5.util.Timeout;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

abstract class ClassicHttpCoreTransportTest {

    private static final Timeout TIMEOUT = Timeout.ofMinutes(1);

    @RegisterExtension
    final ExecutorResource executorResource;

    private final URIScheme scheme;

    public ClassicHttpCoreTransportTest(final URIScheme scheme) {
        this.scheme = scheme;
        this.executorResource = new ExecutorResource(5);
    }

    URIScheme scheme() {
        return scheme;
    }

    abstract HttpServer serverStart() throws IOException;

    abstract HttpRequester clientStart() throws IOException;

    @ValueSource(ints = {0, 2048, 10240})
    @ParameterizedTest(name = "{displayName}; content length: {0}")
    void testSequentialGetRequests(final int contentSize) throws Exception {
        final HttpServer server = serverStart();
        final HttpRequester requester = clientStart();

        final HttpHost target = new HttpHost(scheme.id, "localhost", server.getLocalPort());
        final HttpCoreContext context = HttpCoreContext.create();

        final int reqCount = 20;

        for (int i = 0; i < reqCount; i++) {
            final ClassicHttpRequest request = ClassicRequestBuilder.get()
                    .setPath("/random/" + contentSize)
                    .build();
            try (final ClassicHttpResponse response = requester.execute(target, request, TIMEOUT, context)) {
                Assertions.assertThat(response.getCode()).isEqualTo(HttpStatus.SC_OK);
                final String body = EntityUtils.toString(response.getEntity(), Integer.MAX_VALUE);
                Assertions.assertThat(body).isNotNull();
                Assertions.assertThat(body.length()).isEqualTo(contentSize);
            }
        }
    }

    @Test
    void testSequentialHeadRequests() throws Exception {
        final HttpServer server = serverStart();
        final HttpRequester requester = clientStart();

        final HttpHost target = new HttpHost(scheme.id, "localhost", server.getLocalPort());
        final HttpCoreContext context = HttpCoreContext.create();

        final int reqCount = 20;

        for (int i = 0; i < reqCount; i++) {
            final ClassicHttpRequest request = ClassicRequestBuilder.head()
                    .setPath("/random/2048")
                    .build();
            try (final ClassicHttpResponse response = requester.execute(target, request, TIMEOUT, context)) {
                Assertions.assertThat(response.getCode()).isEqualTo(HttpStatus.SC_OK);
                Assertions.assertThat(response.getEntity()).isNull();
            }
        }
    }

    @ValueSource(ints = {0, 2048, 10240})
    @ParameterizedTest(name = "{displayName}; content length: {0}")
    void testSequentialPostRequests(final int contentSize) throws Exception {
        final HttpServer server = serverStart();
        final HttpRequester requester = clientStart();

        final HttpHost target = new HttpHost(scheme.id, "localhost", server.getLocalPort());
        final HttpCoreContext context = HttpCoreContext.create();

        final int reqCount = 20;

        for (int i = 0; i < reqCount; i++) {
            final ClassicHttpRequest request = ClassicRequestBuilder.post()
                    .setPath("/echo")
                    .setEntity(new RandomEntity(contentSize))
                    .build();
            try (final ClassicHttpResponse response = requester.execute(target, request, TIMEOUT, context)) {
                Assertions.assertThat(response.getCode()).isEqualTo(HttpStatus.SC_OK);
                final String body = EntityUtils.toString(response.getEntity(), Integer.MAX_VALUE);
                Assertions.assertThat(body).isNotNull();
                Assertions.assertThat(body.length()).isEqualTo(contentSize);
            }
        }
    }

    @ValueSource(ints = {0, 2048, 10240})
    @ParameterizedTest(name = "{displayName}; content length: {0}")
    void testConcurrentPostRequests(final int contentSize) throws Exception {
        final HttpServer server = serverStart();
        final HttpRequester requester = clientStart();

        final HttpHost target = new HttpHost(scheme.id, "localhost", server.getLocalPort());

        final int connCount = 5;
        final int reqCount = connCount * 20;

        final ExecutorService executorService = executorResource.getExecutorService();
        final CountDownLatch countDownLatch = new CountDownLatch(connCount);
        final AtomicLong n = new AtomicLong(reqCount);
        final Queue<Result<String>> resultQueue = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < connCount; i++) {
            executorService.execute(() -> {
                try {
                    while (n.decrementAndGet() > 0) {
                        final HttpCoreContext context = HttpCoreContext.create();
                        final ClassicHttpRequest request = ClassicRequestBuilder.post()
                                .setPath("/echo")
                                .setEntity(new RandomEntity(contentSize))
                                .build();
                        try {
                            requester.execute(target, request, TIMEOUT, context, response -> {
                                resultQueue.add(new Result<>(
                                        request,
                                        response,
                                        EntityUtils.toString(response.getEntity(), Integer.MAX_VALUE)));
                                return null;
                            });
                        } catch (final Exception ex) {
                            resultQueue.add(new Result<>(request, ex));
                        }
                    }
                } finally {
                    countDownLatch.countDown();
                }
            });
        }
        Assertions.assertThat(countDownLatch.await(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit())).isTrue();
        for (final Result<String> result : resultQueue) {
            if (result.isOK()) {
                Assertions.assertThat(result.response).isNotNull();
                Assertions.assertThat(result.response.getCode()).isEqualTo(HttpStatus.SC_OK);
            } else {
                Assertions.fail(result.exception);
            }
        }
    }

    @Test
    void testSequentialInvalidGetRequests() throws Exception {
        final HttpServer server = serverStart();
        final HttpRequester requester = clientStart();

        final HttpHost target = new HttpHost(scheme.id, "localhost", server.getLocalPort());
        final HttpCoreContext context = HttpCoreContext.create();

        final int reqCount = 20;

        for (int i = 0; i < reqCount; i++) {
            final ClassicHttpRequest request = ClassicRequestBuilder.get()
                    .setPath("/random/boom")
                    .build();
            try (final ClassicHttpResponse response = requester.execute(target, request, TIMEOUT, context)) {
                Assertions.assertThat(response.getCode()).isEqualTo(HttpStatus.SC_BAD_REQUEST);
                final String body = EntityUtils.toString(response.getEntity(), Integer.MAX_VALUE);
                Assertions.assertThat(body).isNotNull().isEqualTo("Invalid request path: /random/boom");
            }
        }
    }

}
