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

import java.io.IOException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.nio.AsyncClientEndpoint;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.entity.DiscardingEntityConsumer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityProducer;
import org.apache.hc.core5.http.nio.support.AbstractAsyncPushHandler;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2RequesterBootstrap;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.testing.classic.LoggingConnPoolListener;
import org.apache.hc.core5.testing.nio.LoggingExceptionCallback;
import org.apache.hc.core5.testing.nio.LoggingH2StreamListener;
import org.apache.hc.core5.testing.nio.LoggingHttp1StreamListener;
import org.apache.hc.core5.testing.nio.LoggingIOSessionDecorator;
import org.apache.hc.core5.testing.nio.LoggingIOSessionListener;
import org.apache.hc.core5.util.TextUtils;
import org.apache.hc.core5.util.Timeout;

public class H2CompatibilityTest {

    private final HttpAsyncRequester client;

    public static void main(final String... args) throws Exception {

        final HttpHost[] h2servers = new HttpHost[]{
                new HttpHost("http", "localhost", 8080),
                new HttpHost("http", "localhost", 8081)
        };

        final HttpHost httpbin = new HttpHost("http", "localhost", 8082);

        final H2CompatibilityTest test = new H2CompatibilityTest();
        try {
            test.start();
            for (final HttpHost h2server : h2servers) {
                test.executeH2(h2server);
            }
            test.executeHttpBin(httpbin);
        } finally {
            test.shutdown();
        }
    }

    H2CompatibilityTest() throws Exception {
        this.client = H2RequesterBootstrap.bootstrap()
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
                .create();
    }

    void start() throws Exception {
        client.start();
    }

    void shutdown() throws Exception {
        client.close(CloseMode.GRACEFUL);
    }

    private static final Timeout TIMEOUT = Timeout.ofSeconds(5);

    void executeH2(final HttpHost target) throws Exception {
        {
            System.out.println("*** HTTP/2 simple request execution ***");
            final Future<AsyncClientEndpoint> connectFuture = client.connect(target, TIMEOUT, HttpVersionPolicy.FORCE_HTTP_2, null);
            try {
                final AsyncClientEndpoint endpoint = connectFuture.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());

                final CountDownLatch countDownLatch = new CountDownLatch(1);
                final HttpRequest httpget = new BasicHttpRequest(Method.GET, target, "/status.html");
                endpoint.execute(
                        new BasicRequestProducer(httpget, null),
                        new BasicResponseConsumer<>(new StringAsyncEntityConsumer()),
                        new FutureCallback<Message<HttpResponse, String>>() {

                            @Override
                            public void completed(final Message<HttpResponse, String> responseMessage) {
                                final HttpResponse response = responseMessage.getHead();
                                final int code = response.getCode();
                                if (code == HttpStatus.SC_OK) {
                                    logResult(TestResult.OK, target, httpget, response,
                                            Objects.toString(response.getFirstHeader("server")));
                                } else {
                                    logResult(TestResult.NOK, target, httpget, response, "(status " + code + ")");
                                }
                                countDownLatch.countDown();
                            }

                            @Override
                            public void failed(final Exception ex) {
                                logResult(TestResult.NOK, target, httpget, null, "(" + ex.getMessage() + ")");
                                countDownLatch.countDown();
                            }

                            @Override
                            public void cancelled() {
                                logResult(TestResult.NOK, target, httpget, null, "(cancelled)");
                                countDownLatch.countDown();
                            }

                        });
                if (!countDownLatch.await(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit())) {
                    logResult(TestResult.NOK, target, null, null, "(single request execution failed to complete in time)");
                }
            } catch (final ExecutionException ex) {
                final Throwable cause = ex.getCause();
                logResult(TestResult.NOK, target, null, null, "(" + cause.getMessage() + ")");
            } catch (final TimeoutException ex) {
                logResult(TestResult.NOK, target, null, null, "(time out)");
            }
        }
        {
            System.out.println("*** HTTP/2 multiplexed request execution ***");
            final Future<AsyncClientEndpoint> connectFuture = client.connect(target, TIMEOUT, HttpVersionPolicy.FORCE_HTTP_2, null);
            try {
                final AsyncClientEndpoint endpoint = connectFuture.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());

                final int reqCount = 20;
                final CountDownLatch countDownLatch = new CountDownLatch(reqCount);
                for (int i = 0; i < reqCount; i++) {
                    final HttpRequest httpget = new BasicHttpRequest(Method.GET, target, "/status.html");
                    endpoint.execute(
                            new BasicRequestProducer(httpget, null),
                            new BasicResponseConsumer<>(new StringAsyncEntityConsumer()),
                            new FutureCallback<Message<HttpResponse, String>>() {

                                @Override
                                public void completed(final Message<HttpResponse, String> responseMessage) {
                                    final HttpResponse response = responseMessage.getHead();
                                    final int code = response.getCode();
                                    if (code == HttpStatus.SC_OK) {
                                        logResult(TestResult.OK, target, httpget, response,
                                                "multiplexed / " + response.getFirstHeader("server"));
                                    } else {
                                        logResult(TestResult.NOK, target, httpget, response, "(status " + code + ")");
                                    }
                                    countDownLatch.countDown();
                                }

                                @Override
                                public void failed(final Exception ex) {
                                    logResult(TestResult.NOK, target, httpget, null, "(" + ex.getMessage() + ")");
                                    countDownLatch.countDown();
                                }

                                @Override
                                public void cancelled() {
                                    logResult(TestResult.NOK, target, httpget, null, "(cancelled)");
                                    countDownLatch.countDown();
                                }

                            });
                }
                if (!countDownLatch.await(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit())) {
                    logResult(TestResult.NOK, target, null, null, "(multiplexed request execution failed to complete in time)");
                }
            } catch (final ExecutionException ex) {
                final Throwable cause = ex.getCause();
                logResult(TestResult.NOK, target, null, null, "(" + cause.getMessage() + ")");
            } catch (final TimeoutException ex) {
                logResult(TestResult.NOK, target, null, null, "(time out)");
            }
        }
        {
            System.out.println("*** HTTP/2 request execution with push ***");
            final Future<AsyncClientEndpoint> connectFuture = client.connect(target, TIMEOUT, HttpVersionPolicy.FORCE_HTTP_2, null);
            try {
                final AsyncClientEndpoint endpoint = connectFuture.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());

                final CountDownLatch countDownLatch = new CountDownLatch(5);
                final HttpRequest httpget = new BasicHttpRequest(Method.GET, target, "/index.html");
                final Future<Message<HttpResponse, String>> future = endpoint.execute(
                        new BasicRequestProducer(httpget, null),
                        new BasicResponseConsumer<>(new StringAsyncEntityConsumer()),
                        (request, context) -> new AbstractAsyncPushHandler<Message<HttpResponse, Void>>(
                                new BasicResponseConsumer<>(new DiscardingEntityConsumer<>())) {

                            @Override
                            protected void handleResponse(
                                    final HttpRequest promise,
                                    final Message<HttpResponse, Void> responseMessage) throws IOException, HttpException {
                                final HttpResponse response = responseMessage.getHead();
                                logResult(TestResult.OK, target, promise, response,
                                        "pushed / " + response.getFirstHeader("server"));
                                countDownLatch.countDown();
                            }

                            @Override
                            protected void handleError(
                                    final HttpRequest promise,
                                    final Exception cause) {
                                logResult(TestResult.NOK, target, promise, null, "(" + cause.getMessage() + ")");
                                countDownLatch.countDown();
                            }
                        },
                        null,
                        null);
                final Message<HttpResponse, String> message = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
                final HttpResponse response = message.getHead();
                final int code = response.getCode();
                if (code == HttpStatus.SC_OK) {
                    logResult(TestResult.OK, target, httpget, response,
                            Objects.toString(response.getFirstHeader("server")));
                    if (!countDownLatch.await(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit())) {
                        logResult(TestResult.NOK, target, null, null, "Push messages not received");
                    }
                } else {
                    logResult(TestResult.NOK, target, httpget, response, "(status " + code + ")");
                }
            } catch (final ExecutionException ex) {
                final Throwable cause = ex.getCause();
                logResult(TestResult.NOK, target, null, null, "(" + cause.getMessage() + ")");
            } catch (final TimeoutException ex) {
                logResult(TestResult.NOK, target, null, null, "(time out)");
            }
        }
    }

    void executeHttpBin(final HttpHost target) throws Exception {
        {
            System.out.println("*** httpbin.org HTTP/1.1 simple request execution ***");

            final List<Message<HttpRequest, AsyncEntityProducer>> requestMessages = Arrays.asList(
                    new Message<>(
                            new BasicHttpRequest(Method.GET, target, "/headers"),
                            null),
                    new Message<>(
                            new BasicHttpRequest(Method.POST, target, "/anything"),
                            new StringAsyncEntityProducer("some important message", ContentType.TEXT_PLAIN)),
                    new Message<>(
                            new BasicHttpRequest(Method.PUT, target, "/anything"),
                            new StringAsyncEntityProducer("some important message", ContentType.TEXT_PLAIN)),
                    new Message<>(
                            new BasicHttpRequest(Method.GET, target, "/drip"),
                            null),
                    new Message<>(
                            new BasicHttpRequest(Method.GET, target, "/bytes/20000"),
                            null),
                    new Message<>(
                            new BasicHttpRequest(Method.GET, target, "/delay/2"),
                            null),
                    new Message<>(
                            new BasicHttpRequest(Method.POST, target, "/delay/2"),
                            new StringAsyncEntityProducer("some important message", ContentType.TEXT_PLAIN)),
                    new Message<>(
                            new BasicHttpRequest(Method.PUT, target, "/delay/2"),
                            new StringAsyncEntityProducer("some important message", ContentType.TEXT_PLAIN))
            );

            for (final Message<HttpRequest, AsyncEntityProducer> message : requestMessages) {
                final CountDownLatch countDownLatch = new CountDownLatch(1);
                final HttpRequest request = message.getHead();
                final AsyncEntityProducer entityProducer = message.getBody();
                client.execute(
                        new BasicRequestProducer(request, entityProducer),
                        new BasicResponseConsumer<>(new StringAsyncEntityConsumer(CharCodingConfig.custom()
                                .setCharset(StandardCharsets.US_ASCII)
                                .setMalformedInputAction(CodingErrorAction.IGNORE)
                                .setUnmappableInputAction(CodingErrorAction.REPLACE)
                                .build())),
                        TIMEOUT,
                        new FutureCallback<Message<HttpResponse, String>>() {

                            @Override
                            public void completed(final Message<HttpResponse, String> responseMessage) {
                                final HttpResponse response = responseMessage.getHead();
                                final int code = response.getCode();
                                if (code == HttpStatus.SC_OK) {
                                    logResult(TestResult.OK, target, request, response,
                                            Objects.toString(response.getFirstHeader("server")));
                                } else {
                                    logResult(TestResult.NOK, target, request, response, "(status " + code + ")");
                                }
                                countDownLatch.countDown();
                            }

                            @Override
                            public void failed(final Exception ex) {
                                logResult(TestResult.NOK, target, request, null, "(" + ex.getMessage() + ")");
                                countDownLatch.countDown();
                            }

                            @Override
                            public void cancelled() {
                                logResult(TestResult.NOK, target, request, null, "(cancelled)");
                                countDownLatch.countDown();
                            }
                        });
                if (!countDownLatch.await(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit())) {
                    logResult(TestResult.NOK, target, null, null, "(httpbin.org tests failed to complete in time)");
                }
            }
        }
        {
            System.out.println("*** httpbin.org HTTP/1.1 pipelined request execution ***");

            final Future<AsyncClientEndpoint> connectFuture = client.connect(target, TIMEOUT);
            final AsyncClientEndpoint streamEndpoint = connectFuture.get();

            final int n = 10;
            final CountDownLatch countDownLatch = new CountDownLatch(n);
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
                                final HttpResponse response = responseMessage.getHead();
                                final int code = response.getCode();
                                if (code == HttpStatus.SC_OK) {
                                    logResult(TestResult.OK, target, request, response,
                                            "pipelined / " + response.getFirstHeader("server"));
                                } else {
                                    logResult(TestResult.NOK, target, request, response, "(status " + code + ")");
                                }
                                countDownLatch.countDown();
                            }

                            @Override
                            public void failed(final Exception ex) {
                                logResult(TestResult.NOK, target, request, null, "(" + ex.getMessage() + ")");
                                countDownLatch.countDown();
                            }

                            @Override
                            public void cancelled() {
                                logResult(TestResult.NOK, target, request, null, "(cancelled)");
                                countDownLatch.countDown();
                            }
                        });
            }
            if (!countDownLatch.await(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit())) {
                logResult(TestResult.NOK, target, null, null, "(httpbin.org tests failed to complete in time)");
            }
        }
    }

    enum TestResult {OK, NOK}

    private void logResult(
            final TestResult result,
            final HttpHost httpHost,
            final HttpRequest request,
            final HttpResponse response,
            final String message) {
        final StringBuilder buf = new StringBuilder();
        buf.append(result);
        if (buf.length() == 2) {
            buf.append(" ");
        }
        buf.append(": ").append(httpHost).append(" ");
        if (response != null) {
            buf.append(response.getVersion()).append(" ");
        }
        if (request != null) {
            buf.append(request.getMethod()).append(" ").append(request.getRequestUri());
        }
        if (message != null && !TextUtils.isBlank(message)) {
            buf.append(" -> ").append(message);
        }
        System.out.println(buf);
    }

}
