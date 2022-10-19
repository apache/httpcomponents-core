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

import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.bootstrap.HttpRequester;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.StandardFilter;
import org.apache.hc.core5.http.io.HttpFilterChain;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.testing.classic.extension.HttpRequesterResource;
import org.apache.hc.core5.testing.classic.extension.HttpServerResource;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class ClassicHttp1CoreTransportTest extends ClassicHttpCoreTransportTest {

    private static final Timeout TIMEOUT = Timeout.ofMinutes(1);

    @RegisterExtension
    private final HttpServerResource serverResource;
    @RegisterExtension
    private final HttpRequesterResource clientResource;

    public ClassicHttp1CoreTransportTest(final URIScheme scheme) {
        super(scheme);
        this.serverResource = new HttpServerResource(scheme, bootstrap -> bootstrap
                .setSocketConfig(SocketConfig.custom()
                        .setSoTimeout(TIMEOUT)
                        .build())
                .register("*", new EchoHandler())
                .addFilterBefore(StandardFilter.MAIN_HANDLER.name(), "no-keep-alive", (request, responseTrigger, context, chain) ->
                        chain.proceed(request, new HttpFilterChain.ResponseTrigger() {

                            @Override
                            public void sendInformation(
                                    final ClassicHttpResponse response) throws HttpException, IOException {
                                responseTrigger.sendInformation(response);
                            }

                            @Override
                            public void submitResponse(
                                    final ClassicHttpResponse response) throws HttpException, IOException {
                                if (request.getPath().startsWith("/no-keep-alive")) {
                                    response.setHeader(HttpHeaders.CONNECTION, HeaderElements.CLOSE);
                                }
                                responseTrigger.submitResponse(response);
                            }

                        }, context)));
        this.clientResource = new HttpRequesterResource(bootstrap -> bootstrap
                .setSocketConfig(SocketConfig.custom()
                        .setSoTimeout(TIMEOUT)
                        .build()));
    }

    @Override
    HttpServer serverStart() throws IOException {
        return serverResource.start();
    }

    @Override
    HttpRequester clientStart() throws IOException {
        return clientResource.start();
    }

}
