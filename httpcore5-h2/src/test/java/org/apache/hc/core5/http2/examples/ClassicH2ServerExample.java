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

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.hc.core5.annotation.Experimental;
import org.apache.hc.core5.concurrent.DefaultThreadFactory;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.impl.routing.RequestRouter;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.EntityTemplate;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.nio.support.classic.ClassicToAsyncServerExchangeHandler;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.frame.RawFrame;
import org.apache.hc.core5.http2.impl.nio.H2StreamListener;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2ServerBootstrap;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.apache.hc.core5.util.TimeValue;

/**
 * Example of asynchronous embedded HTTP/2 server with a classic I/O API compatibility
 * bridge that enables the use of standard {@link java.io.InputStream} / {@link java.io.OutputStream}
 * based data consumers / producers.
 * <p>>
 * Execution of individual message exchanges is delegated to an {@link java.util.concurrent.Executor}
 * backed by a pool of threads.
 */
@Experimental
public class ClassicH2ServerExample {

    public static void main(final String[] args) throws Exception {
        int port = 8080;
        if (args.length >= 1) {
            port = Integer.parseInt(args[0]);
        }

        final IOReactorConfig config = IOReactorConfig.custom()
                .setSoTimeout(15, TimeUnit.SECONDS)
                .setTcpNoDelay(true)
                .build();

        final ExecutorService executorService = Executors.newFixedThreadPool(
                25,
                new DefaultThreadFactory("worker-pool", true));

        final HttpRequestHandler requestHandler = (request, response, context) -> {
            try {
                final HttpEntity requestEntity = request.getEntity();
                if (requestEntity != null) {
                    EntityUtils.consume(requestEntity);
                }
                final Map<String, String> queryParams = new URIBuilder(request.getUri()).getQueryParams().stream()
                        .collect(Collectors.toMap(
                                NameValuePair::getName,
                                NameValuePair::getValue,
                                (s, s2) -> s));
                final int n = Integer.parseInt(queryParams.getOrDefault("n", "10"));
                final String p = queryParams.getOrDefault("pattern", "huh?");
                final HttpEntity responseEntity = new EntityTemplate(
                        ContentType.TEXT_PLAIN.withCharset(StandardCharsets.UTF_8),
                        outputStream -> {
                            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
                                for (int i = 0; i < n; i++) {
                                    writer.write(p);
                                    writer.write("\n");
                                }
                            }
                        });
                response.setEntity(responseEntity);
            } catch (final URISyntaxException ex) {
                throw new ProtocolException("Invalid request URI", ex);
            } catch (final NumberFormatException ex) {
                throw new ProtocolException("Invalid query parameter", ex);
            }
        };

        final RequestRouter<HttpRequestHandler> requestRouter = RequestRouter.<HttpRequestHandler>builder()
                .resolveAuthority(RequestRouter.LOCAL_AUTHORITY_RESOLVER)
                .addRoute(RequestRouter.LOCAL_AUTHORITY, "*", requestHandler)
                .build();

        final HttpAsyncServer server = H2ServerBootstrap.bootstrap()
                .setIOReactorConfig(config)
                .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_2)
                .setStreamListener(new H2StreamListener() {

                    @Override
                    public void onHeaderInput(final HttpConnection connection, final int streamId, final List<? extends Header> headers) {
                        for (int i = 0; i < headers.size(); i++) {
                            System.out.println(connection.getRemoteAddress() + " (" + streamId + ") << " + headers.get(i));
                        }
                    }

                    @Override
                    public void onHeaderOutput(final HttpConnection connection, final int streamId, final List<? extends Header> headers) {
                        for (int i = 0; i < headers.size(); i++) {
                            System.out.println(connection.getRemoteAddress() + " (" + streamId + ") >> " + headers.get(i));
                        }
                    }

                    @Override
                    public void onFrameInput(final HttpConnection connection, final int streamId, final RawFrame frame) {
                    }

                    @Override
                    public void onFrameOutput(final HttpConnection connection, final int streamId, final RawFrame frame) {
                    }

                    @Override
                    public void onInputFlowControl(final HttpConnection connection, final int streamId, final int delta, final int actualSize) {
                    }

                    @Override
                    public void onOutputFlowControl(final HttpConnection connection, final int streamId, final int delta, final int actualSize) {
                    }

                })
                .setRequestRouter((request, context) -> {
                    final HttpRequestHandler handler = requestRouter.resolve(request, context);
                    return () -> new ClassicToAsyncServerExchangeHandler(
                            executorService,
                            handler,
                            e -> e.printStackTrace(System.out));
                })
                .setExceptionCallback(e -> e.printStackTrace(System.out))
                .create();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("HTTP server shutting down");
            server.close(CloseMode.GRACEFUL);
            executorService.shutdownNow();
        }));

        server.start();
        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(port), URIScheme.HTTP);
        final ListenerEndpoint listenerEndpoint = future.get();
        System.out.print("Listening on " + listenerEndpoint.getAddress());
        server.awaitShutdown(TimeValue.ofDays(Long.MAX_VALUE));
    }

}
