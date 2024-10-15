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

import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.bootstrap.HttpRequester;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.routing.RequestRouter;
import org.apache.hc.core5.http.io.HttpFilterChain;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.testing.SSLTestContexts;
import org.apache.hc.core5.testing.extension.classic.HttpRequesterResource;
import org.apache.hc.core5.testing.extension.classic.HttpServerResource;
import org.apache.hc.core5.util.Timeout;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

abstract class ClassicServerBootstrapFilterTest {

    private static final Timeout TIMEOUT = Timeout.ofMinutes(1);

    private final URIScheme scheme;

    @RegisterExtension
    private final HttpServerResource serverResource;

    @RegisterExtension
    private final HttpRequesterResource clientResource;

    public ClassicServerBootstrapFilterTest(final URIScheme scheme) {
        this.scheme = scheme;
        this.serverResource = new HttpServerResource();
        this.serverResource.configure(bootstrap -> bootstrap
                .setSslContext(scheme == URIScheme.HTTPS ? SSLTestContexts.createServerSSLContext() : null)
                .setSocketConfig(SocketConfig.custom()
                        .setSoTimeout(TIMEOUT)
                        .build())
                .setRequestRouter(RequestRouter.<HttpRequestHandler>builder()
                        .addRoute(RequestRouter.LOCAL_AUTHORITY, "*", new EchoHandler())
                        .resolveAuthority(RequestRouter.LOCAL_AUTHORITY_RESOLVER)
                        .build())
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

                        }, context)));

        this.clientResource = new HttpRequesterResource();
        this.clientResource.configure(bootstrap -> bootstrap
                .setSslContext(SSLTestContexts.createClientSSLContext())
                .setSocketConfig(SocketConfig.custom()
                        .setSoTimeout(TIMEOUT)
                        .build()));
    }

    @Test
    void testFilters() throws Exception {
        final HttpServer server = serverResource.start();
        final HttpRequester requester = clientResource.start();

        server.start();
        final HttpHost target = new HttpHost(scheme.id, "localhost", server.getLocalPort());
        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/filters");
        request.setEntity(new StringEntity("some stuff", ContentType.TEXT_PLAIN));
        try (final ClassicHttpResponse response = requester.execute(target, request, TIMEOUT, context)) {
            assertThat(response.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
            final Header testFilterHeader = response.getHeader("X-Test-Filter");
            assertThat(testFilterHeader, CoreMatchers.notNullValue());
        }
    }

}
