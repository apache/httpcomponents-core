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

package org.apache.hc.core5.testing.compatibility.http2;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.RequestNotExecutedException;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.nio.AsyncClientEndpoint;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.support.AbstractAsyncPushHandler;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.config.H2Config;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

abstract class AbstractHttp2CompatIT {

    static final Timeout TIMEOUT = Timeout.ofSeconds(5);

    abstract HttpHost targetHost();

    HttpAsyncRequester client;

    abstract void configure(H2RequesterBootstrap bootstrap);

    @BeforeEach
    void start() throws Exception {
        final H2RequesterBootstrap bootstrap = H2RequesterBootstrap.bootstrap()
                .setIOReactorConfig(IOReactorConfig.custom()
                        .setSoTimeout(TIMEOUT)
                        .build())
                .setH2Config(H2Config.custom()
                        .setPushEnabled(true)
                        .build())
                .setStreamListener(LoggingHttp1StreamListener.INSTANCE_CLIENT)
                .setStreamListener(LoggingH2StreamListener.INSTANCE)
                .setConnPoolListener(LoggingConnPoolListener.INSTANCE)
                .setIOSessionDecorator(LoggingIOSessionDecorator.INSTANCE)
                .setExceptionCallback(LoggingExceptionCallback.INSTANCE)
                .setIOSessionListener(LoggingIOSessionListener.INSTANCE)
                .setIOReactorMetricsListener(LoggingReactorMetricsListener.INSTANCE);
        configure(bootstrap);
        client = bootstrap.create();
        client.start();
    }

    @AfterEach
    void shutdown() throws Exception {
        if (client != null) {
            client.close(CloseMode.GRACEFUL);
        }
    }

    @Test
    void test_multiple_request_execution() throws Exception {
        final HttpHost target = targetHost();
        final Future<AsyncClientEndpoint> connectFuture = client.connect(target, TIMEOUT, HttpVersionPolicy.FORCE_HTTP_2, null);
        final AsyncClientEndpoint endpoint = connectFuture.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        try {
            final int n = 20;
            final CountDownLatch countDownLatch = new CountDownLatch(n);
            final Queue<Result<String>> resultQueue = new ConcurrentLinkedQueue<>();
            for (int i = 0; i < n; i++) {
                final HttpRequest httpget = new BasicHttpRequest(Method.GET, target, "/aaa");
                endpoint.execute(
                        new BasicRequestProducer(httpget, null),
                        new BasicResponseConsumer<>(new StringAsyncEntityConsumer()),
                        new FutureCallback<Message<HttpResponse, String>>() {

                            @Override
                            public void completed(final Message<HttpResponse, String> responseMessage) {
                                resultQueue.add(new Result<>(
                                        httpget,
                                        responseMessage.getHead(),
                                        responseMessage.getBody()));
                                countDownLatch.countDown();
                            }

                            @Override
                            public void failed(final Exception ex) {
                                resultQueue.add(new Result<>(httpget, ex));
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
                    Assertions.assertEquals(ContainerImages.AAA, result.content);
                } else {
                    Assertions.fail(result.exception);
                }
            }
        } finally {
            endpoint.releaseAndDiscard();
        }
    }

    @Test
    void test_request_execution_with_push() throws Exception {
        final HttpHost target = targetHost();
        final Future<AsyncClientEndpoint> connectFuture = client.connect(target, TIMEOUT, HttpVersionPolicy.FORCE_HTTP_2, null);
        final AsyncClientEndpoint endpoint = connectFuture.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        try {
            final CountDownLatch countDownLatch = new CountDownLatch(4);
            final Queue<Result<String>> resultQueue = new ConcurrentLinkedQueue<>();
            final HttpRequest httpget = new BasicHttpRequest(Method.GET, target, "/pushy");
            endpoint.execute(
                    new BasicRequestProducer(httpget, null),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()),
                    (request, context) ->
                            new AbstractAsyncPushHandler<Message<HttpResponse, String>>(new BasicResponseConsumer<>(new StringAsyncEntityConsumer())) {

                                @Override
                                protected void handleResponse(final HttpRequest promise, final Message<HttpResponse, String> responseMessage) {
                                    resultQueue.add(new Result<>(
                                            httpget,
                                            responseMessage.getHead(),
                                            responseMessage.getBody()));
                                    countDownLatch.countDown();
                                }

                                @Override
                                protected void handleError(final HttpRequest promise, final Exception cause) {
                                    resultQueue.add(new Result<>(httpget, cause));
                                    countDownLatch.countDown();
                                }

                            },
                    null,
                    new FutureCallback<Message<HttpResponse, String>>() {

                        @Override
                        public void completed(final Message<HttpResponse, String> responseMessage) {
                            resultQueue.add(new Result<>(
                                    httpget,
                                    responseMessage.getHead(),
                                    responseMessage.getBody()));
                            countDownLatch.countDown();
                        }

                        @Override
                        public void failed(final Exception ex) {
                            resultQueue.add(new Result<>(httpget, ex));
                            countDownLatch.countDown();
                        }

                        @Override
                        public void cancelled() {
                            failed(new RequestNotExecutedException());
                        }

                    });
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
            endpoint.releaseAndDiscard();
        }
    }

}
