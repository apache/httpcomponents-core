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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.AsyncEntityConsumer;
import org.apache.hc.core5.http.nio.AsyncRequestConsumer;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncServerRequestHandler;
import org.apache.hc.core5.http.nio.entity.AsyncEntityProducers;
import org.apache.hc.core5.http.nio.entity.NoopEntityConsumer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.support.AbstractServerExchangeHandler;
import org.apache.hc.core5.http.nio.support.BasicRequestConsumer;
import org.apache.hc.core5.http.nio.support.BasicResponseProducer;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2ServerBootstrap;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.net.URLEncodedUtils;
import org.apache.hc.core5.reactor.IOReactorConfig;
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
 * Server: Apache-HttpCore/5.0-beta8-SNAPSHOT (Java/1.8.0_202)
 * Transfer-Encoding: chunked
 * Content-Type: text/plain; charset=ISO-8859-1
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

        final HttpAsyncServer server = H2ServerBootstrap.bootstrap()
                .setH2Config(H2Config.DEFAULT)
                .setIOReactorConfig(IOReactorConfig.DEFAULT)
                .setVersionPolicy(HttpVersionPolicy.NEGOTIATE) // fallback to HTTP/1 as needed

                // wildcard path matcher:
                .register("*", new Supplier<AsyncServerExchangeHandler>() {
                    @Override
                    public AsyncServerExchangeHandler get() {
                        return new CustomServerExchangeHandler();
                    }
                })
                .create();


        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println("HTTP server shutting down");
                server.close(CloseMode.GRACEFUL);
            }
        }));

        server.start();
        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(port));
        final ListenerEndpoint listenerEndpoint = future.get();
        System.out.println("Listening on " + listenerEndpoint.getAddress());
        server.awaitShutdown(TimeValue.ofDays(Long.MAX_VALUE));
    }

    static class CustomServerExchangeHandler extends AbstractServerExchangeHandler<Message<HttpRequest, String>> {


        @Override
        protected AsyncRequestConsumer<Message<HttpRequest, String>> supplyConsumer(
                final HttpRequest request,
                final EntityDetails entityDetails,
                final HttpContext context) {
            // if there's no body don't try to parse entity:
            AsyncEntityConsumer entityConsumer = new NoopEntityConsumer();

            if (entityDetails != null) {
                entityConsumer = new StringAsyncEntityConsumer();
            }
            //noinspection unchecked
            return new BasicRequestConsumer<>(entityConsumer);

        }

        @Override
        protected void handle(final Message<HttpRequest, String> requestMessage,
                              final AsyncServerRequestHandler.ResponseTrigger responseTrigger,
                              final HttpContext context) throws HttpException, IOException {

            final HttpCoreContext coreContext = HttpCoreContext.adapt(context);
            final EndpointDetails endpoint = coreContext.getEndpointDetails();
            final HttpRequest req = requestMessage.getHead();
            final String httpEntity = requestMessage.getBody();

            // generic success response:
            final HttpResponse resp = new BasicHttpResponse(200);

            // recording the request
            System.out.println(String.format("[%s] %s %s %s", new Date(),
                    endpoint.getRemoteAddress(),
                    req.getMethod(),
                    req.getPath()));

            // Request without an entity - GET/HEAD/DELETE
            if (httpEntity == null) {
                responseTrigger.submitResponse(
                        new BasicResponseProducer(resp), context);
                return;
            }

            // Request with an entity - POST/PUT
            final Header cth = req.getHeader(HttpHeaders.CONTENT_TYPE);
            final ContentType contentType = cth != null ? ContentType.parse(cth.getValue()) : null;
            String name = "stranger";
            if (contentType != null && contentType.isSameMimeType(ContentType.APPLICATION_FORM_URLENCODED)) {

                // decoding the form entity into key/value pairs:
                final List<NameValuePair> args = URLEncodedUtils.parse(httpEntity, contentType.getCharset());
                if (!args.isEmpty()) {
                    name = args.get(0).getValue();
                }
            }

            // composing greeting:
            final String greeting = String.format("Hello %s\n", name);
            responseTrigger.submitResponse(
                    new BasicResponseProducer(resp, AsyncEntityProducers.create(greeting)), context);
        }
    }

}

