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
package org.apache.hc.core5.reactive.examples;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.impl.BasicEntityDetails;
import org.apache.hc.core5.http.impl.Http1StreamListener;
import org.apache.hc.core5.http.impl.bootstrap.AsyncServerBootstrap;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.message.RequestLine;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.ResponseChannel;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactive.ReactiveRequestProcessor;
import org.apache.hc.core5.reactive.ReactiveServerExchangeHandler;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.apache.hc.core5.util.TimeValue;
import org.reactivestreams.Publisher;

/**
 * Example of full-duplex HTTP/1.1 message exchanges using reactive streaming. This demo server works out-of-the-box
 * with {@link ReactiveFullDuplexClientExample}; it can also be invoked interactively using telnet.
 */
public class ReactiveFullDuplexServerExample {
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
            .setStreamListener(new Http1StreamListener() {
                @Override
                public void onRequestHead(final HttpConnection connection, final HttpRequest request) {
                    System.out.println(connection.getRemoteAddress() + " " + new RequestLine(request));

                }

                @Override
                public void onResponseHead(final HttpConnection connection, final HttpResponse response) {
                    System.out.println(connection.getRemoteAddress() + " " + new StatusLine(response));
                }

                @Override
                public void onExchangeComplete(final HttpConnection connection, final boolean keepAlive) {
                    if (keepAlive) {
                        System.out.println(connection.getRemoteAddress() + " exchange completed (connection kept alive)");
                    } else {
                        System.out.println(connection.getRemoteAddress() + " exchange completed (connection closed)");
                    }
                }

            })
            .register("/echo", new Supplier<AsyncServerExchangeHandler>() {
                @Override
                public AsyncServerExchangeHandler get() {
                    return new ReactiveServerExchangeHandler(new ReactiveRequestProcessor() {
                        @Override
                        public void processRequest(
                            final HttpRequest request,
                            final EntityDetails entityDetails,
                            final ResponseChannel responseChannel,
                            final HttpContext context,
                            final Publisher<ByteBuffer> requestBody,
                            final Callback<Publisher<ByteBuffer>> responseBodyFuture
                        ) throws HttpException, IOException {
                            if (new BasicHeader(HttpHeaders.EXPECT, HeaderElements.CONTINUE).equals(request.getHeader(HttpHeaders.EXPECT))) {
                                responseChannel.sendInformation(new BasicHttpResponse(100), context);
                            }

                            responseChannel.sendResponse(
                                new BasicHttpResponse(200),
                                new BasicEntityDetails(-1, ContentType.APPLICATION_OCTET_STREAM),
                                context);

                            // Simply using the request publisher as the response publisher will
                            // cause the server to echo the request body.
                            responseBodyFuture.execute(requestBody);
                        }
                    });
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
        server.awaitShutdown(TimeValue.ofDays(Long.MAX_VALUE));
    }
}
