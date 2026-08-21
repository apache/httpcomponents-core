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
package org.apache.hc.core5.http2.examples;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.impl.routing.RequestRouter;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityProducer;
import org.apache.hc.core5.http.nio.support.AsyncServerPipeline;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2MultiplexingRequester;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2MultiplexingRequesterBootstrap;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2ServerBootstrap;
import org.apache.hc.core5.http2.nio.pool.H2PoolPolicy;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

/**
 * Self-contained example of HTTP/2 request execution using the
 * stream-capacity-aware multiplexing pool. Starts an embedded H2
 * server with {@code MAX_CONCURRENT_STREAMS = 10}, then fires 50
 * concurrent requests through a single requester configured with
 * {@link H2PoolPolicy#MULTIPLEXING}. The pool tracks the peer's
 * stream limit and opens additional connections when existing ones
 * are saturated.
 */
public class H2MultiplexingPoolExample {

    public static void main(final String[] args) throws Exception {

        final Timeout timeout = Timeout.ofSeconds(30);
        final int totalRequests = 50;


        final H2Config serverH2Config = H2Config.custom()
                .setPushEnabled(false)
                .setMaxConcurrentStreams(10)
                .build();

        final HttpAsyncServer server = H2ServerBootstrap.bootstrap()
                .setIOReactorConfig(IOReactorConfig.custom()
                        .setSoTimeout(timeout)
                        .build())
                .setH2Config(serverH2Config)
                .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_2)
                .setRequestRouter(RequestRouter.<Supplier<AsyncServerExchangeHandler>>builder()
                        .addRoute(RequestRouter.LOCAL_AUTHORITY, "*",
                                AsyncServerPipeline.assemble()
                                        .request()
                                        .consumeContent(ct -> StringAsyncEntityConsumer::new)
                                        .response()
                                        .asString(ContentType.TEXT_PLAIN)
                                        .handle((request, context) -> {
                                            final String body = request.body() != null
                                                    ? request.body() : "";
                                            return Message.of(
                                                    new BasicHttpResponse(HttpStatus.SC_OK),
                                                    "echo: " + body);
                                        })
                                        .supplier())
                        .resolveAuthority(RequestRouter.LOCAL_AUTHORITY_RESOLVER)
                        .build())
                .create();

        server.start();
        final Future<ListenerEndpoint> listenerFuture = server.listen(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                URIScheme.HTTP);
        final ListenerEndpoint listener = listenerFuture.get();
        final InetSocketAddress address = (InetSocketAddress) listener.getAddress();
        final HttpHost target = new HttpHost(
                URIScheme.HTTP.id, "localhost", address.getPort());

        System.out.println("Server listening on " + address);


        final H2MultiplexingRequester requester =
                H2MultiplexingRequesterBootstrap.bootstrap()
                        .setIOReactorConfig(IOReactorConfig.custom()
                                .setSoTimeout(timeout)
                                .build())
                        .setH2Config(H2Config.custom()
                                .setPushEnabled(false)
                                .build())
                        .setH2PoolPolicy(H2PoolPolicy.MULTIPLEXING)
                        .create();
        requester.start();

        // ------ fire requests ------

        final List<Future<Message<HttpResponse, String>>> futures =
                new ArrayList<>(totalRequests);
        for (int i = 0; i < totalRequests; i++) {
            futures.add(requester.execute(
                    new BasicRequestProducer("POST", target, "/echo",
                            new StringAsyncEntityProducer(
                                    "msg-" + i, ContentType.TEXT_PLAIN)),
                    new BasicResponseConsumer<>(
                            new StringAsyncEntityConsumer()),
                    timeout, null));
        }

        System.out.println("Fired " + totalRequests + " concurrent requests");


        int ok = 0;
        int fail = 0;
        for (int i = 0; i < totalRequests; i++) {
            try {
                final Message<HttpResponse, String> result =
                        futures.get(i).get(
                                timeout.getDuration(),
                                timeout.getTimeUnit());
                System.out.println("[" + i + "] "
                        + result.head().getCode() + " "
                        + result.body());
                ok++;
            } catch (final Exception ex) {
                System.err.println("[" + i + "] " + ex);
                fail++;
            }
        }

        System.out.println();
        System.out.println("Done: " + ok + " ok, " + fail + " failed");


        requester.initiateShutdown();
        requester.awaitShutdown(TimeValue.ofSeconds(5));
        requester.close(CloseMode.IMMEDIATE);

        server.initiateShutdown();
        server.awaitShutdown(TimeValue.ofSeconds(5));
        server.close(CloseMode.IMMEDIATE);
    }

}
