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
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.impl.bootstrap.StandardFilter;
import org.apache.hc.core5.http.io.HttpFilterChain;
import org.apache.hc.core5.http.io.HttpFilterHandler;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.io.support.AbstractHttpServerAuthFilter;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.util.TimeValue;

/**
 * Example of using classic I/O request filters with an embedded HTTP/1.1 server.
 */
public class ClassicServerFilterExample {

    public static void main(final String[] args) throws Exception {
        int port = 8080;
        if (args.length >= 1) {
            port = Integer.parseInt(args[0]);
        }
        final SocketConfig socketConfig = SocketConfig.custom()
                .setSoTimeout(15, TimeUnit.SECONDS)
                .setTcpNoDelay(true)
                .build();

        final HttpServer server = ServerBootstrap.bootstrap()
                .setListenerPort(port)
                .setSocketConfig(socketConfig)

                // Replace standard expect-continue handling with a custom auth filter

                .replaceFilter(StandardFilter.EXPECT_CONTINUE.name(), new AbstractHttpServerAuthFilter<String>(false) {

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

                .addFilterFirst("my-filter", new HttpFilterHandler() {

                    @Override
                    public void handle(final ClassicHttpRequest request,
                                       final HttpFilterChain.ResponseTrigger responseTrigger,
                                       final HttpContext context, final HttpFilterChain chain) throws HttpException, IOException {
                        if (request.getRequestUri().equals("/back-door")) {
                            final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK);
                            response.setEntity(new StringEntity("Welcome", ContentType.TEXT_PLAIN));
                            responseTrigger.submitResponse(response);
                        } else {
                            chain.proceed(request, new HttpFilterChain.ResponseTrigger() {

                                @Override
                                public void sendInformation(final ClassicHttpResponse response) throws HttpException, IOException {
                                    responseTrigger.sendInformation(response);
                                }

                                @Override
                                public void submitResponse(final ClassicHttpResponse response) throws HttpException, IOException {
                                    response.addHeader("X-Filter", "My-Filter");
                                    responseTrigger.submitResponse(response);
                                }

                            }, context);
                        }
                    }

                })

                // Application request handler

                .register("*", new HttpRequestHandler() {

                    @Override
                    public void handle(
                            final ClassicHttpRequest request,
                            final ClassicHttpResponse response,
                            final HttpContext context) throws HttpException, IOException {
                        // do something useful
                        response.setCode(HttpStatus.SC_OK);
                        response.setEntity(new StringEntity("Hello"));
                    }

                })
                .create();

        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                server.close(CloseMode.GRACEFUL);
            }
        });
        System.out.println("Listening on port " + port);

        server.awaitTermination(TimeValue.MAX_VALUE);

    }

}
