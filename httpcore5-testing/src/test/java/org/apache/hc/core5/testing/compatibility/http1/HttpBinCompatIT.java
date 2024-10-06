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

package org.apache.hc.core5.testing.compatibility.http1;

import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.RequestNotExecutedException;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.nio.AsyncClientEndpoint;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityProducer;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2RequesterBootstrap;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.testing.classic.LoggingConnPoolListener;
import org.apache.hc.core5.testing.compatibility.ContainerImages;
import org.apache.hc.core5.testing.compatibility.Result;
import org.apache.hc.core5.testing.nio.LoggingExceptionCallback;
import org.apache.hc.core5.testing.nio.LoggingH2StreamListener;
import org.apache.hc.core5.testing.nio.LoggingHttp1StreamListener;
import org.apache.hc.core5.testing.nio.LoggingIOSessionDecorator;
import org.apache.hc.core5.testing.nio.LoggingIOSessionListener;
import org.apache.hc.core5.testing.nio.LoggingReactorMetricsListener;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class HttpBinCompatIT {

    static final Timeout TIMEOUT = Timeout.ofSeconds(5);

    @Container
    static final GenericContainer<?> CONTAINER = ContainerImages.httpBin();

    static HttpHost targetHost() {
        return new HttpHost("http",
                CONTAINER.getHost(),
                CONTAINER.getMappedPort(ContainerImages.HTTP_PORT));
    }

    HttpAsyncRequester client;

    @BeforeEach
    void start() throws Exception {
        client = H2RequesterBootstrap.bootstrap()
                .setIOReactorConfig(IOReactorConfig.custom()
                        .setSoTimeout(TIMEOUT)
                        .build())
                .setStreamListener(LoggingHttp1StreamListener.INSTANCE_CLIENT)
                .setStreamListener(LoggingH2StreamListener.INSTANCE)
                .setConnPoolListener(LoggingConnPoolListener.INSTANCE)
                .setIOSessionDecorator(LoggingIOSessionDecorator.INSTANCE)
                .setExceptionCallback(LoggingExceptionCallback.INSTANCE)
                .setIOSessionListener(LoggingIOSessionListener.INSTANCE)
                .setIOReactorMetricsListener(LoggingReactorMetricsListener.INSTANCE)
                .create();
        client.start();
    }

    @AfterEach
    void shutdown() throws Exception {
        if (client != null) {
            client.close(CloseMode.GRACEFUL);
        }
    }

    @AfterAll
    static void cleanup() {
        CONTAINER.close();
    }

    @Test
    void test_sequential_request_execution() throws Exception {
        final HttpHost target = targetHost();
        final List<Message<HttpRequest, AsyncEntityProducer>> requestMessages = Arrays.asList(
                new Message<>(new BasicHttpRequest(Method.GET, target, "/headers")),
                new Message<>(
                        new BasicHttpRequest(Method.POST, target, "/anything"),
                        new StringAsyncEntityProducer("some important message", ContentType.TEXT_PLAIN)),
                new Message<>(
                        new BasicHttpRequest(Method.PUT, target, "/anything"),
                        new StringAsyncEntityProducer("some important message", ContentType.TEXT_PLAIN)),
                new Message<>(new BasicHttpRequest(Method.GET, target, "/drip")),
                new Message<>(new BasicHttpRequest(Method.GET, target, "/bytes/20000")),
                new Message<>(new BasicHttpRequest(Method.GET, target, "/delay/2")),
                new Message<>(
                        new BasicHttpRequest(Method.POST, target, "/delay/2"),
                        new StringAsyncEntityProducer("some important message", ContentType.TEXT_PLAIN)),
                new Message<>(
                        new BasicHttpRequest(Method.PUT, target, "/delay/2"),
                        new StringAsyncEntityProducer("some important message", ContentType.TEXT_PLAIN))
        );

        for (final Message<HttpRequest, AsyncEntityProducer> message : requestMessages) {
            final HttpRequest request = message.getHead();
            final AsyncEntityProducer entityProducer = message.getBody();
            final Future<Message<HttpResponse, String>> messageFuture = client.execute(
                    new BasicRequestProducer(request, entityProducer),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer(CharCodingConfig.custom()
                            .setCharset(StandardCharsets.US_ASCII)
                            .setMalformedInputAction(CodingErrorAction.IGNORE)
                            .setUnmappableInputAction(CodingErrorAction.REPLACE)
                            .build())),
                    TIMEOUT,
                    null);
            final Message<HttpResponse, String> response = messageFuture.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertEquals(HttpStatus.SC_OK, response.getHead().getCode());
        }
    }

    @Test
    void test_pipelined_request_execution() throws Exception {
        final HttpHost target = targetHost();
        final Future<AsyncClientEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final AsyncClientEndpoint streamEndpoint = connectFuture.get();
        try {
            final int n = 20;
            final CountDownLatch countDownLatch = new CountDownLatch(n);
            final Queue<Result<String>> resultQueue = new ConcurrentLinkedQueue<>();
            for (int i = 0; i < n; i++) {

                final HttpRequest request;
                final AsyncEntityProducer entityProducer;
                if (i % 2 == 0) {
                    request = new BasicHttpRequest(Method.GET, target, "/headers");
                    entityProducer = null;
                } else {
                    request = new BasicHttpRequest(Method.POST, target, "/anything");
                    entityProducer = new StringAsyncEntityProducer("some important message", ContentType.TEXT_PLAIN);
                }

                streamEndpoint.execute(
                        new BasicRequestProducer(request, entityProducer),
                        new BasicResponseConsumer<>(new StringAsyncEntityConsumer(CharCodingConfig.custom()
                                .setCharset(StandardCharsets.US_ASCII)
                                .setMalformedInputAction(CodingErrorAction.IGNORE)
                                .setUnmappableInputAction(CodingErrorAction.REPLACE)
                                .build())),
                        new FutureCallback<Message<HttpResponse, String>>() {

                            @Override
                            public void completed(final Message<HttpResponse, String> responseMessage) {
                                resultQueue.add(new Result<>(
                                        request,
                                        responseMessage.getHead(),
                                        responseMessage.getBody()));
                                countDownLatch.countDown();
                            }

                            @Override
                            public void failed(final Exception ex) {
                                resultQueue.add(new Result<>(request, ex));
                                countDownLatch.countDown();
                            }

                            @Override
                            public void cancelled() {
                                failed(new RequestNotExecutedException());
                            }

                        });
            }
            Assertions.assertTrue(countDownLatch.await(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()), "Request executions have not completed in time");
            for (final Result<String> result : resultQueue) {
                if (result.isOK()) {
                    Assertions.assertNotNull(result.response);
                    Assertions.assertEquals(HttpStatus.SC_OK, result.response.getCode(), "Response message returned non 200 status");
                } else {
                    Assertions.fail(result.exception);
                }
            }
        } finally {
            streamEndpoint.releaseAndDiscard();
        }
    }

}
