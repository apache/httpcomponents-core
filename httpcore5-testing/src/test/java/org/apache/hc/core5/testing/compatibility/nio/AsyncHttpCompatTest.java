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

package org.apache.hc.core5.testing.compatibility.nio;

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
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.testing.Result;
import org.apache.hc.core5.testing.compatibility.ContainerImages;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public abstract class AsyncHttpCompatTest<T extends HttpAsyncRequester> {

    static final Timeout TIMEOUT = Timeout.ofSeconds(5);

    private final HttpHost target;

    public AsyncHttpCompatTest(final HttpHost target) {
        this.target = target;
    }

    abstract T client();

    @Test
    void test_multiple_request_execution_over_same_connection() throws Exception {
        final HttpAsyncRequester client = client();
        final Future<AsyncClientEndpoint> connectFuture = client.connect(target, TIMEOUT, null, null);
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
    void test_multiple_request_execution_over_multiple_connections() throws Exception {
        final HttpAsyncRequester client = client();
        final int c = 10;
        client.setDefaultMaxPerRoute(10);
        final int n = 20 * c;
        final CountDownLatch countDownLatch = new CountDownLatch(n);
        final Queue<Result<String>> resultQueue = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < n; i++) {
            final HttpRequest httpget = new BasicHttpRequest(Method.GET, target, "/aaa");
            client.execute(
                    target,
                    new BasicRequestProducer(httpget, null),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()),
                    TIMEOUT,
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
    }

    @Test
    void test_multiple_request_execution_large_blob() throws Exception {
        final HttpAsyncRequester client = client();
        final Future<AsyncClientEndpoint> connectFuture = client.connect(target, TIMEOUT, null, null);
        final AsyncClientEndpoint endpoint = connectFuture.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        try {
            final int n = 20;
            final CountDownLatch countDownLatch = new CountDownLatch(n);
            final Queue<Result<String>> resultQueue = new ConcurrentLinkedQueue<>();
            for (int i = 0; i < n; i++) {
                final HttpRequest httpget = new BasicHttpRequest(Method.GET, target, "/blob");
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
                } else {
                    Assertions.fail(result.exception);
                }
            }
        } finally {
            endpoint.releaseAndDiscard();
        }
    }

}
