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
package org.apache.hc.core5.http.examples;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.impl.bootstrap.AsyncServerBootstrap;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.impl.bootstrap.StandardFilter;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.AsyncFilterChain;
import org.apache.hc.core5.http.nio.AsyncFilterHandler;
import org.apache.hc.core5.http.nio.AsyncPushProducer;
import org.apache.hc.core5.http.nio.AsyncRequestConsumer;
import org.apache.hc.core5.http.nio.AsyncServerRequestHandler;
import org.apache.hc.core5.http.nio.entity.AsyncEntityProducers;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.support.AbstractAsyncServerAuthFilter;
import org.apache.hc.core5.http.nio.support.AsyncResponseBuilder;
import org.apache.hc.core5.http.nio.support.BasicRequestConsumer;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.apache.hc.core5.util.TimeValue;

/**
 * Example of using asynchronous I/O request filters with an embedded HTTP/1.1 server.
 */
public class AsyncServerFilterExample {

    public static void main(final String[] args) throws Exception {
        int port = 8080;
        if (args.length >= 1) {
            port = Integer.parseInt(args[0]);
        }

        final IOReactorConfig config = IOReactorConfig.custom()
                .setSoTimeout(15, TimeUnit.SECONDS)
                .setTcpNoDelay(true)
                .build();

        final HttpAsyncServer server = AsyncServerBootstrap.bootstrap()
                .setIOReactorConfig(config)

                // Replace standard expect-continue handling with a custom auth filter

                .replaceFilter(StandardFilter.EXPECT_CONTINUE.name(), new AbstractAsyncServerAuthFilter<String>(true) {

                    @Override
                    protected String parseChallengeResponse(
                            final String authorizationValue, final HttpContext context) throws HttpException {
                        return authorizationValue;
                    }

                    @Override
                    protected boolean authenticate(
                            final String challengeResponse,
                            final URIAuthority authority,
                            final String requestUri,
                            final HttpContext context) {
                        return "let me pass".equals(challengeResponse);
                    }

                    @Override
                    protected String generateChallenge(
                            final String challengeResponse,
                            final URIAuthority authority,
                            final String requestUri,
                            final HttpContext context) {
                        return "who goes there?";
                    }

                })

                // Add a custom request filter at the beginning of the processing pipeline

                .addFilterFirst("my-filter", new AsyncFilterHandler() {

                    @Override
                    public AsyncDataConsumer handle(
                            final HttpRequest request,
                            final EntityDetails entityDetails,
                            final HttpContext context,
                            final AsyncFilterChain.ResponseTrigger responseTrigger,
                            final AsyncFilterChain chain) throws HttpException, IOException {
                        if (request.getRequestUri().equals("/back-door")) {
                            responseTrigger.submitResponse(
                                    new BasicHttpResponse(HttpStatus.SC_OK),
                                    AsyncEntityProducers.create("Welcome"));
                            return null;
                        }
                        return chain.proceed(request, entityDetails, context, new AsyncFilterChain.ResponseTrigger() {

                            @Override
                            public void sendInformation(
                                    final HttpResponse response) throws HttpException, IOException {
                                responseTrigger.sendInformation(response);
                            }

                            @Override
                            public void submitResponse(
                                    final HttpResponse response, final AsyncEntityProducer entityProducer) throws HttpException, IOException {
                                response.addHeader("X-Filter", "My-Filter");
                                responseTrigger.submitResponse(response, entityProducer);
                            }

                            @Override
                            public void pushPromise(
                                    final HttpRequest promise, final AsyncPushProducer responseProducer) throws HttpException, IOException {
                                responseTrigger.pushPromise(promise, responseProducer);
                            }

                        });
                    }

                })

                // Application request handler

                .register("*", new AsyncServerRequestHandler<Message<HttpRequest, String>>() {

                    @Override
                    public AsyncRequestConsumer<Message<HttpRequest, String>> prepare(
                            final HttpRequest request,
                            final EntityDetails entityDetails,
                            final HttpContext context) throws HttpException {
                        return new BasicRequestConsumer<>(entityDetails != null ? new StringAsyncEntityConsumer() : null);
                    }

                    @Override
                    public void handle(
                            final Message<HttpRequest, String> requestMessage,
                            final ResponseTrigger responseTrigger,
                            final HttpContext context) throws HttpException, IOException {
                        // do something useful
                        responseTrigger.submitResponse(
                                AsyncResponseBuilder.create(HttpStatus.SC_OK)
                                        .setEntity("Hello")
                                        .build(),
                                context);
                    }
                })
                .create();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                System.out.println("HTTP server shutting down");
                server.close(CloseMode.GRACEFUL);
            }
        });

        server.start();
        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(port));
        final ListenerEndpoint listenerEndpoint = future.get();
        System.out.print("Listening on " + listenerEndpoint.getAddress());
        server.awaitShutdown(TimeValue.MAX_VALUE);
    }

}
