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

import java.io.IOException;

import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.impl.bootstrap.StandardFilter;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.AsyncFilterChain;
import org.apache.hc.core5.http.nio.AsyncPushProducer;
import org.apache.hc.core5.http.protocol.UriPatternMatcher;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.testing.extension.SocksProxyResource;
import org.apache.hc.core5.testing.nio.extension.HttpAsyncRequesterResource;
import org.apache.hc.core5.testing.nio.extension.HttpAsyncServerResource;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class Http1SocksProxyCoreTransportTest extends HttpCoreTransportTest {

    private static final Timeout TIMEOUT = Timeout.ofMinutes(1);

    @RegisterExtension
    @Order(-Integer.MAX_VALUE)
    private final SocksProxyResource proxyResource;
    @RegisterExtension
    private final HttpAsyncServerResource serverResource;
    @RegisterExtension
    private final HttpAsyncRequesterResource clientResource;

    public Http1SocksProxyCoreTransportTest(final URIScheme scheme) {
        super(scheme);
        this.proxyResource = new SocksProxyResource();
        this.serverResource = new HttpAsyncServerResource(bootstrap -> bootstrap
                .setIOReactorConfig(
                        IOReactorConfig.custom()
                                .setSoTimeout(TIMEOUT)
                                .build())
                .setLookupRegistry(new UriPatternMatcher<>())
                .register("*", () -> new EchoHandler(2048))
                .addFilterBefore(StandardFilter.MAIN_HANDLER.name(), "no-keepalive", (request, entityDetails, context, responseTrigger, chain) ->
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
                                if (request.getPath().startsWith("/no-keep-alive")) {
                                    response.setHeader(HttpHeaders.CONNECTION, HeaderElements.CLOSE);
                                }
                                responseTrigger.submitResponse(response, entityProducer);
                            }

                            @Override
                            public void pushPromise(
                                    final HttpRequest promise,
                                    final AsyncPushProducer responseProducer) throws HttpException, IOException {
                                responseTrigger.pushPromise(promise, responseProducer);
                            }

                        }))
        );
        this.clientResource = new HttpAsyncRequesterResource(bootstrap -> bootstrap
                .setIOReactorConfig(IOReactorConfig.custom()
                        .setSocksProxyAddress(proxyResource.proxy().getProxyAddress())
                        .setSoTimeout(TIMEOUT)
                        .build())
        );
    }

    @Override
    HttpAsyncServer serverStart() throws IOException {
        return serverResource.start();
    }

    @Override
    HttpAsyncRequester clientStart() {
        return clientResource.start();
    }

}
