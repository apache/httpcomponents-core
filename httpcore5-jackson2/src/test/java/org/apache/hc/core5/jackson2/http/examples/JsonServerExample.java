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
package org.apache.hc.core5.jackson2.http.examples;

import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.bootstrap.AsyncServerBootstrap;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.impl.routing.RequestRouter;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpDateGenerator;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.jackson2.http.AsyncJsonServerPipeline;
import org.apache.hc.core5.jackson2.http.RequestData;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.apache.hc.core5.util.TimeValue;

/**
 * Example of asynchronous embedded JSON server.
 */
public class JsonServerExample {

    /**
     * Example command line args: {@code 8080}
     */
    public static void main(final String[] args) throws Exception {
        int port = 8080;
        if (args.length >= 1) {
            port = Integer.parseInt(args[1]);
        }

        final IOReactorConfig config = IOReactorConfig.custom()
                .setSoTimeout(15, TimeUnit.SECONDS)
                .setTcpNoDelay(true)
                .build();

        final ObjectMapper objectMapper = new ObjectMapper();

        final Supplier<AsyncServerExchangeHandler> exchangeHandlerSupplier = AsyncJsonServerPipeline.assemble(objectMapper)
                // Read GET / HEAD requests by consuming content stream as JSON nodes
                .request(Method.GET, Method.HEAD, Method.POST, Method.PUT, Method.PATCH)
                .asJsonNode()
                // Write out responses by streaming out content of JSON object
                .response()
                .asObject(RequestData.class)
                // Map exceptions to a response message
                .errorMessage(Throwable::getMessage)
                // Generate a response to a request
                .handle((m, context) -> {
                    final HttpRequest request = m.head();
                    final RequestData rd = new RequestData();
                    try {
                        rd.setUrl(request.getUri());
                    } catch (final URISyntaxException ex) {
                        throw new ProtocolException("Invalid request URI");
                    }
                    rd.generateHeaders(request.getHeaders());
                    rd.setJson(m.body());
                    rd.setData(Objects.toString(m.error()));

                    final HttpCoreContext coreContext = HttpCoreContext.cast(context);
                    final EndpointDetails endpointDetails = coreContext.getEndpointDetails();

                    final InetSocketAddress remoteAddress = (InetSocketAddress) endpointDetails.getRemoteAddress();
                    rd.setOrigin(Objects.toString(remoteAddress.getAddress()));

                    return Message.of(new BasicHttpResponse(HttpStatus.SC_OK), rd);
                })
                .supplier();

        final HttpAsyncServer server = AsyncServerBootstrap.bootstrap()
                .setExceptionCallback(e -> e.printStackTrace())
                .setIOReactorConfig(config)
                .setRequestRouter(RequestRouter.<Supplier<AsyncServerExchangeHandler>>builder()
                        .addRoute(RequestRouter.LOCAL_AUTHORITY, "*", exchangeHandlerSupplier)
                        .resolveAuthority(RequestRouter.LOCAL_AUTHORITY_RESOLVER)
                        .build())
                .create();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            println("HTTP server shutting down");
            server.close(CloseMode.GRACEFUL);
        }));

        server.start();
        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(port), URIScheme.HTTP);
        final ListenerEndpoint listenerEndpoint = future.get();
        println("Listening on " + listenerEndpoint.getAddress());
        server.awaitShutdown(TimeValue.MAX_VALUE);
    }

    static void println(final String msg) {
        System.out.println(HttpDateGenerator.INSTANCE.getCurrentDate() + " | " + msg);
    }

}