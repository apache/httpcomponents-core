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

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.impl.routing.RequestRouter;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.entity.CharSequenceAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.entity.DiscardingEntityConsumer;
import org.apache.hc.core5.http.nio.support.AbstractServerExchangeHandler;
import org.apache.hc.core5.http.nio.support.AsyncServerPipeline;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2ServerBootstrap;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.net.WWWFormCodec;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.apache.hc.core5.util.TimeValue;

/**
 * Example HTTP2 server that reads an entity body and responds back with a greeting.
 *
 * <pre>
 * {@code
 * $ curl  -id name=bob localhost:8080
 * HTTP/1.1 200 OK
 * Date: Sat, 25 May 2019 03:44:49 GMT
 * Server: Apache-HttpCore/xxxxx
 * Transfer-Encoding: chunked
 * Content-Type: text/plain; charset=UTF-8
 *
 * Hello bob
 * }</pre>
 * <p>
 * This examples uses a {@link AbstractServerExchangeHandler} for the basic request / response processing cycle.
 */
public class H2GreetingServer {
    public static void main(final String[] args) throws ExecutionException, InterruptedException {
        int port = 8080;
        if (args.length >= 1) {
            port = Integer.parseInt(args[0]);
        }

        final Supplier<AsyncServerExchangeHandler> exchangeHandlerSupplier = AsyncServerPipeline.assemble()
                // Represent request as string
                .request(Method.GET, Method.POST)
                .<List<NameValuePair>>consumeContent(contentType -> {
                    if (contentType != null && contentType.isSameMimeType(ContentType.APPLICATION_FORM_URLENCODED)) {
                        return () -> new CharSequenceAsyncEntityConsumer<>(cs ->
                                WWWFormCodec.parse(cs, StandardCharsets.UTF_8));
                    } else {
                        // Discard content that cannot be correctly processed
                        return DiscardingEntityConsumer::new;
                    }
                })
                // Represent response as string
                .response()
                .asString(ContentType.TEXT_PLAIN)
                // Generate a response to the request
                .handle((r, c) -> {
                    final HttpCoreContext context = HttpCoreContext.cast(c);
                    final EndpointDetails endpoint = context.getEndpointDetails();
                    final HttpRequest req = r.head();

                    // recording the request
                    System.out.printf("[%s] %s %s %s%n", Instant.now(),
                            endpoint != null ? endpoint.getRemoteAddress() : null,
                            req.getMethod(),
                            req.getPath());

                    String name = null;
                    final List<NameValuePair> params = r.body();
                    if (params != null && !params.isEmpty()) {
                        final Map<String, String> paramMap = params.stream()
                                .collect(Collectors.toMap(NameValuePair::getName, NameValuePair::getValue));
                        name = paramMap.get("name");
                    }
                    if (name == null) {
                        name = "stranger";
                    }

                    // composing greeting:
                    final String greeting = String.format("Hello %s\n", name);
                    return Message.of(new BasicHttpResponse(HttpStatus.SC_OK), greeting);

                })
                .supplier();

        final HttpAsyncServer server = H2ServerBootstrap.bootstrap()
                .setVersionPolicy(HttpVersionPolicy.NEGOTIATE) // fallback to HTTP/1 as needed
                .setRequestRouter(RequestRouter.<Supplier<AsyncServerExchangeHandler>>builder()
                        // wildcard path matcher:
                        .addRoute(RequestRouter.LOCAL_AUTHORITY, "*", exchangeHandlerSupplier)
                        .resolveAuthority(RequestRouter.LOCAL_AUTHORITY_RESOLVER)
                        .build())
                .create();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("HTTP server shutting down");
            server.close(CloseMode.GRACEFUL);
        }));

        server.start();
        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(port), URIScheme.HTTP);
        final ListenerEndpoint listenerEndpoint = future.get();
        System.out.println("Listening on " + listenerEndpoint.getAddress());
        server.awaitShutdown(TimeValue.ofDays(Long.MAX_VALUE));
    }

}

