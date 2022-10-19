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

import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Future;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.AsyncFilterChain;
import org.apache.hc.core5.http.nio.AsyncPushProducer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityProducer;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.http.protocol.UriPatternMatcher;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.apache.hc.core5.testing.nio.extension.HttpAsyncRequesterResource;
import org.apache.hc.core5.testing.nio.extension.HttpAsyncServerResource;
import org.apache.hc.core5.util.Timeout;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class AsyncServerBootstrapFilterTest {

    private static final Timeout TIMEOUT = Timeout.ofMinutes(1);

    @RegisterExtension
    private final HttpAsyncServerResource serverResource = new HttpAsyncServerResource(bootstrap -> bootstrap
            .setIOReactorConfig(
                    IOReactorConfig.custom()
                            .setSoTimeout(TIMEOUT)
                            .build())
            .setLookupRegistry(new UriPatternMatcher<>())
            .register("*", () -> new EchoHandler(2048))
            .addFilterLast("test-filter", (request, entityDetails, context, responseTrigger, chain) ->
                    chain.proceed(request, entityDetails, context, new AsyncFilterChain.ResponseTrigger() {

                        @Override
                        public void sendInformation(
                                final HttpResponse response) throws HttpException, IOException {
                            responseTrigger.sendInformation(response);
                        }

                        @Override
                        public void submitResponse(
                                final HttpResponse response,
                                final AsyncEntityProducer entityProducer) throws HttpException, IOException {
                            response.setHeader("X-Test-Filter", "active");
                            responseTrigger.submitResponse(response, entityProducer);
                        }

                        @Override
                        public void pushPromise(
                                final HttpRequest promise,
                                final AsyncPushProducer responseProducer) throws HttpException, IOException {
                            responseTrigger.pushPromise(promise, responseProducer);
                        }

                    })));

    @RegisterExtension
    private final HttpAsyncRequesterResource clientResource = new HttpAsyncRequesterResource((bootstrap) -> bootstrap
            .setIOReactorConfig(IOReactorConfig.custom()
                    .setSoTimeout(TIMEOUT)
                    .build()));


    @Test
    public void testFilters() throws Exception {
        final HttpAsyncServer server = serverResource.start();
        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(0), URIScheme.HTTP);
        final ListenerEndpoint listener = future.get();
        final InetSocketAddress address = (InetSocketAddress) listener.getAddress();
        final HttpAsyncRequester requester = clientResource.start();

        final HttpHost target = new HttpHost("http", "localhost", address.getPort());
        final Future<Message<HttpResponse, String>> resultFuture = requester.execute(
                new BasicRequestProducer(Method.POST, target, "/filters",
                        new StringAsyncEntityProducer("some stuff", ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), TIMEOUT, null);
        final Message<HttpResponse, String> message = resultFuture.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        assertThat(message, CoreMatchers.notNullValue());
        final HttpResponse response = message.getHead();
        assertThat(response.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
        final Header testFilterHeader = response.getHeader("X-Test-Filter");
        assertThat(testFilterHeader, CoreMatchers.notNullValue());
    }

}
