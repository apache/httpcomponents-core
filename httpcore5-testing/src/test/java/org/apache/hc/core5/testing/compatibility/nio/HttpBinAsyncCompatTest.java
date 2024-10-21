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

import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.function.Consumer;

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
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2AsyncRequester;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2RequesterBootstrap;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.testing.compatibility.Result;
import org.apache.hc.core5.testing.extension.nio.H2AsyncRequesterResource;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class HttpBinAsyncCompatTest {

    static final Timeout TIMEOUT = Timeout.ofSeconds(5);

    private final HttpHost target;
    @RegisterExtension
    private final H2AsyncRequesterResource clientResource;

    public HttpBinAsyncCompatTest(final HttpHost target) {
        this.target = target;
        this.clientResource = new H2AsyncRequesterResource();
        this.clientResource.configure(bootstrap -> bootstrap
                .setIOReactorConfig(IOReactorConfig.custom()
                        .setSoTimeout(TIMEOUT)
                        .build())
                .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_1));
    }

    void configure(final Consumer<H2RequesterBootstrap> customizer) {
        clientResource.configure(customizer);
    }

    H2AsyncRequester client() {
        return clientResource.start();
    }

    @Test
    void test_sequential_request_execution() throws Exception {
        final HttpAsyncRequester client = client();
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
        final HttpAsyncRequester client = client();
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
