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


import org.junit.jupiter.api.Assertions;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.Future;

import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.impl.bootstrap.StandardFilter;
import org.apache.hc.core5.http.impl.routing.RequestRouter;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.entity.AsyncEntityProducers;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.support.AbstractAsyncServerAuthFilter;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http2.ssl.H2ClientTlsStrategy;
import org.apache.hc.core5.http2.ssl.H2ServerTlsStrategy;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.apache.hc.core5.testing.SSLTestContexts;
import org.apache.hc.core5.testing.extension.nio.HttpAsyncRequesterResource;
import org.apache.hc.core5.testing.extension.nio.HttpAsyncServerResource;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

abstract class Http1AuthenticationTest {

    private static final Timeout TIMEOUT = Timeout.ofMinutes(1);

    @RegisterExtension
    private final HttpAsyncServerResource serverResource;
    @RegisterExtension
    private final HttpAsyncRequesterResource clientResource;

    public Http1AuthenticationTest(final boolean respondImmediately) {
        this.serverResource = new HttpAsyncServerResource();
        this.serverResource.configure(bootstrap -> bootstrap
                .setTlsStrategy(new H2ServerTlsStrategy(SSLTestContexts.createServerSSLContext()))
                .setIOReactorConfig(
                        IOReactorConfig.custom()
                                .setSoTimeout(TIMEOUT)
                                .build())
                .setRequestRouter(RequestRouter.<Supplier<AsyncServerExchangeHandler>>builder()
                        .addRoute(RequestRouter.LOCAL_AUTHORITY, "*", () -> new EchoHandler(2048))
                        .resolveAuthority(RequestRouter.LOCAL_AUTHORITY_RESOLVER)
                        .build())
                .replaceFilter(StandardFilter.EXPECT_CONTINUE.name(), new AbstractAsyncServerAuthFilter<String>(respondImmediately) {

                    @Override
                    protected String parseChallengeResponse(
                            final String challenge, final HttpContext context) throws HttpException {
                        return challenge;
                    }

                    @Override
                    protected boolean authenticate(
                            final String challengeResponse,
                            final URIAuthority authority,
                            final String requestUri,
                            final HttpContext context) {
                        return challengeResponse != null && challengeResponse.equals("let me pass");
                    }

                    @Override
                    protected String generateChallenge(
                            final String challengeResponse,
                            final URIAuthority authority,
                            final String requestUri,
                            final HttpContext context) {
                        return "who goes there?";
                    }

                    @Override
                    protected AsyncEntityProducer generateResponseContent(final HttpResponse unauthorized) {
                        return AsyncEntityProducers.create("You shall not pass!!!");
                    }
                })
        );
        this.clientResource = new HttpAsyncRequesterResource();
        this.clientResource.configure(bootstrap -> bootstrap
                .setTlsStrategy(new H2ClientTlsStrategy(SSLTestContexts.createClientSSLContext()))
                .setIOReactorConfig(IOReactorConfig.custom()
                        .setSoTimeout(TIMEOUT)
                        .build())
        );
    }

    @Test
    void testGetRequestAuthentication() throws Exception {
        final HttpAsyncServer server = serverResource.start();
        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), URIScheme.HTTP);
        final ListenerEndpoint listener = future.get();
        final InetSocketAddress address = (InetSocketAddress) listener.getAddress();
        final HttpAsyncRequester requester = clientResource.start();

        final HttpHost target = new HttpHost("localhost", address.getPort());

        final HttpRequest request1 = new BasicHttpRequest(Method.GET, target, "/stuff");
        final Future<Message<HttpResponse, String>> resultFuture1 = requester.execute(
                new BasicRequestProducer(request1, null),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), TIMEOUT, null);
        final Message<HttpResponse, String> message1 = resultFuture1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(message1);
        final HttpResponse response1 = message1.head();
        Assertions.assertEquals(HttpStatus.SC_UNAUTHORIZED, response1.getCode());
        final String body1 = message1.body();
        Assertions.assertEquals("You shall not pass!!!", body1);

        final HttpRequest request2 = new BasicHttpRequest(Method.GET, target, "/stuff");
        request2.setHeader(HttpHeaders.AUTHORIZATION, "let me pass");
        final Future<Message<HttpResponse, String>> resultFuture2 = requester.execute(
                new BasicRequestProducer(request2, null),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), TIMEOUT, null);
        final Message<HttpResponse, String> message2 = resultFuture2.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(message2);
        final HttpResponse response2 = message2.head();
        Assertions.assertEquals(HttpStatus.SC_OK, response2.getCode());
        final String body2 = message2.body();
        Assertions.assertEquals("", body2);
    }

    @Test
    void testPostRequestAuthentication() throws Exception {
        final HttpAsyncServer server = serverResource.start();
        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), URIScheme.HTTP);
        final ListenerEndpoint listener = future.get();
        final InetSocketAddress address = (InetSocketAddress) listener.getAddress();
        final HttpAsyncRequester requester = clientResource.start();

        final HttpHost target = new HttpHost("localhost", address.getPort());
        final Random rnd = new Random();
        final byte[] stuff = new byte[10240];
        for (int i = 0; i < stuff.length; i++) {
            stuff[i] = (byte) ('a' + rnd.nextInt(10));
        }
        final HttpRequest request1 = new BasicHttpRequest(Method.POST, target, "/stuff");
        final Future<Message<HttpResponse, String>> resultFuture1 = requester.execute(
                new BasicRequestProducer(request1, AsyncEntityProducers.create(stuff, ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), TIMEOUT, null);
        final Message<HttpResponse, String> message1 = resultFuture1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(message1);
        final HttpResponse response1 = message1.head();
        Assertions.assertEquals(HttpStatus.SC_UNAUTHORIZED, response1.getCode());
        final String body1 = message1.body();
        Assertions.assertEquals("You shall not pass!!!", body1);

        final HttpRequest request2 = new BasicHttpRequest(Method.POST, target, "/stuff");
        request2.setHeader(HttpHeaders.AUTHORIZATION, "let me pass");
        final Future<Message<HttpResponse, String>> resultFuture2 = requester.execute(
                new BasicRequestProducer(request2, AsyncEntityProducers.create(stuff, ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), TIMEOUT, null);
        final Message<HttpResponse, String> message2 = resultFuture2.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(message2);
        final HttpResponse response2 = message2.head();
        Assertions.assertEquals(HttpStatus.SC_OK, response2.getCode());
        final String body2 = message2.body();
        Assertions.assertEquals(new String(stuff, StandardCharsets.US_ASCII), body2);
    }

    @Test
    void testPostRequestAuthenticationNoExpectContinue() throws Exception {
        final HttpAsyncServer server = serverResource.start();
        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), URIScheme.HTTP);
        final ListenerEndpoint listener = future.get();
        final InetSocketAddress address = (InetSocketAddress) listener.getAddress();
        final HttpAsyncRequester requester = clientResource.start();

        final HttpHost target = new HttpHost("localhost", address.getPort());
        final Random rnd = new Random();
        final byte[] stuff = new byte[10240];
        for (int i = 0; i < stuff.length; i++) {
            stuff[i] = (byte) ('a' + rnd.nextInt(10));
        }

        final HttpRequest request1 = new BasicHttpRequest(Method.POST, target, "/stuff");
        request1.setVersion(HttpVersion.HTTP_1_0);
        final Future<Message<HttpResponse, String>> resultFuture1 = requester.execute(
                new BasicRequestProducer(request1, AsyncEntityProducers.create(stuff, ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), TIMEOUT, null);
        final Message<HttpResponse, String> message1 = resultFuture1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(message1);
        final HttpResponse response1 = message1.head();
        Assertions.assertEquals(HttpStatus.SC_UNAUTHORIZED, response1.getCode());
        final String body1 = message1.body();
        Assertions.assertEquals("You shall not pass!!!", body1);

        final HttpRequest request2 = new BasicHttpRequest(Method.POST, target, "/stuff");
        request2.setVersion(HttpVersion.HTTP_1_0);
        request2.setHeader(HttpHeaders.AUTHORIZATION, "let me pass");
        final Future<Message<HttpResponse, String>> resultFuture2 = requester.execute(
                new BasicRequestProducer(request2, AsyncEntityProducers.create(stuff, ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), TIMEOUT, null);
        final Message<HttpResponse, String> message2 = resultFuture2.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(message2);
        final HttpResponse response2 = message2.head();
        Assertions.assertEquals(HttpStatus.SC_OK, response2.getCode());
        final String body2 = message2.body();
        Assertions.assertEquals(new String(stuff, StandardCharsets.US_ASCII), body2);
    }

}
