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

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.impl.bootstrap.HttpRequester;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.RequesterBootstrap;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.HttpFilterChain;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClassicServerBootstrapFilterTest {

    private static final Timeout TIMEOUT = Timeout.ofSeconds(30);

    private final Logger log = LoggerFactory.getLogger(getClass());

    private HttpServer server;

    @Rule
    public ExternalResource serverResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            log.debug("Starting up test server");
            server = ServerBootstrap.bootstrap()
                    .setSocketConfig(SocketConfig.custom()
                            .setSoTimeout(TIMEOUT)
                            .build())
                    .register("*", new EchoHandler())
                    .addFilterLast("test-filter", (request, responseTrigger, context, chain) ->
                            chain.proceed(request, new HttpFilterChain.ResponseTrigger() {

                                @Override
                                public void sendInformation(
                                        final ClassicHttpResponse response) throws HttpException, IOException {
                                    responseTrigger.sendInformation(response);
                                }

                                @Override
                                public void submitResponse(
                                        final ClassicHttpResponse response) throws HttpException, IOException {
                                    response.setHeader("X-Test-Filter", "active");
                                    responseTrigger.submitResponse(response);
                                }

                            }, context))
                    .setExceptionListener(LoggingExceptionListener.INSTANCE)
                    .setStreamListener(LoggingHttp1StreamListener.INSTANCE)
                    .create();
        }

        @Override
        protected void after() {
            log.debug("Shutting down test server");
            if (server != null) {
                try {
                    server.close(CloseMode.IMMEDIATE);
                } catch (final Exception ignore) {
                }
            }
        }

    };

    private HttpRequester requester;

    @Rule
    public ExternalResource clientResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            log.debug("Starting up test client");
            requester = RequesterBootstrap.bootstrap()
                    .setSocketConfig(SocketConfig.custom()
                            .setSoTimeout(TIMEOUT)
                            .build())
                    .setMaxTotal(2)
                    .setDefaultMaxPerRoute(2)
                    .setStreamListener(LoggingHttp1StreamListener.INSTANCE)
                    .setConnPoolListener(LoggingConnPoolListener.INSTANCE)
                    .create();
        }

        @Override
        protected void after() {
            log.debug("Shutting down test client");
            if (requester != null) {
                try {
                    requester.close(CloseMode.GRACEFUL);
                } catch (final Exception ignore) {
                }
            }
        }

    };

    @Test
    public void testFilters() throws Exception {
        server.start();
        final HttpHost target = new HttpHost("http", "localhost", server.getLocalPort());
        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/filters");
        request.setEntity(new StringEntity("some stuff", ContentType.TEXT_PLAIN));
        try (final ClassicHttpResponse response = requester.execute(target, request, TIMEOUT, context)) {
            MatcherAssert.assertThat(response.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
            final Header testFilterHeader = response.getHeader("X-Test-Filter");
            MatcherAssert.assertThat(testFilterHeader, CoreMatchers.notNullValue());
        }
    }

}
