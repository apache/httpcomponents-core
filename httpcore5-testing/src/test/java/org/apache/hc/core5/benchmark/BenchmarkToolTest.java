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
package org.apache.hc.core5.benchmark;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.nio.AsyncRequestConsumer;
import org.apache.hc.core5.http.nio.AsyncServerRequestHandler;
import org.apache.hc.core5.http.nio.entity.DiscardingEntityConsumer;
import org.apache.hc.core5.http.nio.support.AsyncResponseBuilder;
import org.apache.hc.core5.http.nio.support.BasicRequestConsumer;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2ServerBootstrap;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class BenchmarkToolTest {

    public static Stream<Arguments> protocols() {
        return Stream.of(
                Arguments.of(HttpVersionPolicy.NEGOTIATE),
                Arguments.of(HttpVersionPolicy.FORCE_HTTP_2)
        );
    }

    private HttpAsyncServer server;
    private InetSocketAddress address;

    public void setup(final HttpVersionPolicy versionPolicy) throws Exception {
        server = H2ServerBootstrap.bootstrap()
                .register("/", new AsyncServerRequestHandler<Message<HttpRequest, Void>>() {

                    @Override
                    public AsyncRequestConsumer<Message<HttpRequest, Void>> prepare(
                            final HttpRequest request,
                            final EntityDetails entityDetails,
                            final HttpContext context) throws HttpException {
                        return new BasicRequestConsumer<>(entityDetails != null ? new DiscardingEntityConsumer<>() : null);
                    }

                    @Override
                    public void handle(
                            final Message<HttpRequest, Void> requestObject,
                            final ResponseTrigger responseTrigger,
                            final HttpContext context) throws HttpException, IOException {
                        responseTrigger.submitResponse(
                                AsyncResponseBuilder.create(HttpStatus.SC_OK)
                                        .setEntity("0123456789ABCDEF")
                                        .build(),
                                context);
                    }

                })
                .setVersionPolicy(versionPolicy)
                .create();
        server.start();
        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(0), URIScheme.HTTP);
        final ListenerEndpoint listener = future.get();
        address = (InetSocketAddress) listener.getAddress();
    }

    @AfterEach
    public void shutdown() throws Exception {
        if (server != null) {
            server.close(CloseMode.IMMEDIATE);
        }
    }


    @ParameterizedTest(name = "{0}")
    @MethodSource("protocols")
    public void testBasics(final HttpVersionPolicy versionPolicy) throws Exception {
        setup(versionPolicy);
        final BenchmarkConfig config = BenchmarkConfig.custom()
                .setKeepAlive(true)
                .setMethod(Method.POST.name())
                .setPayloadText("0123456789ABCDEF")
                .setUri(new URIBuilder()
                        .setScheme(URIScheme.HTTP.id)
                        .setHost("localhost")
                        .setPort(address .getPort())
                        .build())
                .setConcurrencyLevel(3)
                .setForceHttp2(versionPolicy == HttpVersionPolicy.FORCE_HTTP_2)
                .setRequests(100)
                .build();
        final HttpBenchmark httpBenchmark = new HttpBenchmark(config);
        final Results results = httpBenchmark.execute();
        Assertions.assertNotNull(results);
        Assertions.assertEquals(100, results.getSuccessCount());
        Assertions.assertEquals(0, results.getFailureCount());
        Assertions.assertEquals(16, results.getContentLength());
        Assertions.assertEquals(3, results.getConcurrencyLevel());
        Assertions.assertEquals(100 * 16, results.getTotalContentBytesRecvd());
        if (versionPolicy == HttpVersionPolicy.FORCE_HTTP_2) {
            Assertions.assertEquals(HttpVersion.HTTP_2, results.getProtocolVersion());
        }
    }

}
