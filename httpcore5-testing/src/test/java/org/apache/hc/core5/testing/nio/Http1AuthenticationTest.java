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

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
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
import org.apache.hc.core5.http.impl.bootstrap.AsyncRequesterBootstrap;
import org.apache.hc.core5.http.impl.bootstrap.AsyncServerBootstrap;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.impl.bootstrap.StandardFilters;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.BasicRequestProducer;
import org.apache.hc.core5.http.nio.BasicResponseConsumer;
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityProducer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.support.AbstractAsyncServerAuthFilter;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.ShutdownType;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.apache.hc.core5.testing.classic.LoggingConnPoolListener;
import org.apache.hc.core5.testing.classic.LoggingHttp1StreamListener;
import org.apache.hc.core5.util.Timeout;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class Http1AuthenticationTest {

    @Parameterized.Parameters(name = "respond immediately on auth failure: {0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                { Boolean.FALSE },
                { Boolean.TRUE }
        });
    }

    private static final Timeout TIMEOUT = Timeout.ofSeconds(30);

    private final Logger log = LogManager.getLogger(getClass());

    private final boolean respondImmediately;
    private HttpAsyncServer server;

    public Http1AuthenticationTest(final Boolean respondImmediately) {
        this.respondImmediately = respondImmediately;
    }

    @Rule
    public ExternalResource serverResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            log.debug("Starting up test server");
            server = AsyncServerBootstrap.bootstrap()
                    .setIOReactorConfig(
                            IOReactorConfig.custom()
                                    .setSoTimeout(TIMEOUT)
                                    .build())
                    .register("*", new Supplier<AsyncServerExchangeHandler>() {

                        @Override
                        public AsyncServerExchangeHandler get() {
                            return new EchoHandler(2048);
                        }

                    })
                    .replaceFilter(StandardFilters.EXPECT_CONTINUE.name(), new AbstractAsyncServerAuthFilter<String>(respondImmediately) {

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
                            return new BasicAsyncEntityProducer("You shall not pass!!!");
                        }
                    })
                    .setIOSessionListener(LoggingIOSessionListener.INSTANCE)
                    .setStreamListener(LoggingHttp1StreamListener.INSTANCE)
                    .setIOSessionDecorator(LoggingIOSessionDecorator.INSTANCE)
                    .create();
        }

        @Override
        protected void after() {
            log.debug("Shutting down test server");
            if (server != null) {
                try {
                    server.shutdown(ShutdownType.IMMEDIATE);
                } catch (final Exception ignore) {
                }
            }
        }

    };

    private HttpAsyncRequester requester;

    @Rule
    public ExternalResource clientResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            log.debug("Starting up test client");
            requester = AsyncRequesterBootstrap.bootstrap()
                    .setIOReactorConfig(IOReactorConfig.custom()
                            .setSoTimeout(TIMEOUT)
                            .build())
                    .setMaxTotal(2)
                    .setDefaultMaxPerRoute(2)
                    .setIOSessionListener(LoggingIOSessionListener.INSTANCE)
                    .setStreamListener(LoggingHttp1StreamListener.INSTANCE)
                    .setConnPoolListener(LoggingConnPoolListener.INSTANCE)
                    .setIOSessionDecorator(LoggingIOSessionDecorator.INSTANCE)
                    .create();
        }

        @Override
        protected void after() {
            log.debug("Shutting down test client");
            if (requester != null) {
                try {
                    requester.shutdown(ShutdownType.GRACEFUL);
                } catch (final Exception ignore) {
                }
            }
        }

    };

    @Test
    public void testGetRequestAuthentication() throws Exception {
        server.start();
        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(0));
        final ListenerEndpoint listener = future.get();
        final InetSocketAddress address = (InetSocketAddress) listener.getAddress();
        requester.start();

        final HttpHost target = new HttpHost("localhost", address.getPort());

        final HttpRequest request1 = new BasicHttpRequest("GET", target, "/stuff");
        final Future<Message<HttpResponse, String>> resultFuture1 = requester.execute(
                new BasicRequestProducer(request1, null),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), TIMEOUT, null);
        final Message<HttpResponse, String> message1 = resultFuture1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assert.assertThat(message1, CoreMatchers.notNullValue());
        final HttpResponse response1 = message1.getHead();
        Assert.assertThat(response1.getCode(), CoreMatchers.equalTo(HttpStatus.SC_UNAUTHORIZED));
        final String body1 = message1.getBody();
        Assert.assertThat(body1, CoreMatchers.equalTo("You shall not pass!!!"));

        final HttpRequest request2 = new BasicHttpRequest("GET", target, "/stuff");
        request2.setHeader(HttpHeaders.AUTHORIZATION, "let me pass");
        final Future<Message<HttpResponse, String>> resultFuture2 = requester.execute(
                new BasicRequestProducer(request2, null),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), TIMEOUT, null);
        final Message<HttpResponse, String> message2 = resultFuture2.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assert.assertThat(message2, CoreMatchers.notNullValue());
        final HttpResponse response2 = message2.getHead();
        Assert.assertThat(response2.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
        final String body2 = message2.getBody();
        Assert.assertThat(body2, CoreMatchers.equalTo(""));
    }

    @Test
    public void testPostRequestAuthentication() throws Exception {
        server.start();
        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(0));
        final ListenerEndpoint listener = future.get();
        final InetSocketAddress address = (InetSocketAddress) listener.getAddress();
        requester.start();

        final HttpHost target = new HttpHost("localhost", address.getPort());
        final Random rnd = new Random();
        final byte[] stuff = new byte[10240];
        for (int i = 0; i < stuff.length; i++) {
            stuff[i] = (byte)('a' + rnd.nextInt(10));
        }
        final HttpRequest request1 = new BasicHttpRequest("POST", target, "/stuff");
        final Future<Message<HttpResponse, String>> resultFuture1 = requester.execute(
                new BasicRequestProducer(request1, new BasicAsyncEntityProducer(stuff, ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), TIMEOUT, null);
        final Message<HttpResponse, String> message1 = resultFuture1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assert.assertThat(message1, CoreMatchers.notNullValue());
        final HttpResponse response1 = message1.getHead();
        Assert.assertThat(response1.getCode(), CoreMatchers.equalTo(HttpStatus.SC_UNAUTHORIZED));
        final String body1 = message1.getBody();
        Assert.assertThat(body1, CoreMatchers.equalTo("You shall not pass!!!"));

        final HttpRequest request2 = new BasicHttpRequest("POST", target, "/stuff");
        request2.setHeader(HttpHeaders.AUTHORIZATION, "let me pass");
        final Future<Message<HttpResponse, String>> resultFuture2 = requester.execute(
                new BasicRequestProducer(request2, new BasicAsyncEntityProducer(stuff, ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), TIMEOUT, null);
        final Message<HttpResponse, String> message2 = resultFuture2.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assert.assertThat(message2, CoreMatchers.notNullValue());
        final HttpResponse response2 = message2.getHead();
        Assert.assertThat(response2.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
        final String body2 = message2.getBody();
        Assert.assertThat(body2, CoreMatchers.equalTo(new String(stuff, StandardCharsets.US_ASCII)));
    }

    @Test
    public void testPostRequestAuthenticationNoExpectContinue() throws Exception {
        server.start();
        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(0));
        final ListenerEndpoint listener = future.get();
        final InetSocketAddress address = (InetSocketAddress) listener.getAddress();
        requester.start();

        final HttpHost target = new HttpHost("localhost", address.getPort());
        final Random rnd = new Random();
        final byte[] stuff = new byte[10240];
        for (int i = 0; i < stuff.length; i++) {
            stuff[i] = (byte)('a' + rnd.nextInt(10));
        }

        final HttpRequest request1 = new BasicHttpRequest("POST", target, "/stuff");
        request1.setVersion(HttpVersion.HTTP_1_0);
        final Future<Message<HttpResponse, String>> resultFuture1 = requester.execute(
                new BasicRequestProducer(request1, new BasicAsyncEntityProducer(stuff, ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), TIMEOUT, null);
        final Message<HttpResponse, String> message1 = resultFuture1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assert.assertThat(message1, CoreMatchers.notNullValue());
        final HttpResponse response1 = message1.getHead();
        Assert.assertThat(response1.getCode(), CoreMatchers.equalTo(HttpStatus.SC_UNAUTHORIZED));
        final String body1 = message1.getBody();
        Assert.assertThat(body1, CoreMatchers.equalTo("You shall not pass!!!"));

        final HttpRequest request2 = new BasicHttpRequest("POST", target, "/stuff");
        request2.setVersion(HttpVersion.HTTP_1_0);
        request2.setHeader(HttpHeaders.AUTHORIZATION, "let me pass");
        final Future<Message<HttpResponse, String>> resultFuture2 = requester.execute(
                new BasicRequestProducer(request2, new BasicAsyncEntityProducer(stuff, ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), TIMEOUT, null);
        final Message<HttpResponse, String> message2 = resultFuture2.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assert.assertThat(message2, CoreMatchers.notNullValue());
        final HttpResponse response2 = message2.getHead();
        Assert.assertThat(response2.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
        final String body2 = message2.getBody();
        Assert.assertThat(body2, CoreMatchers.equalTo(new String(stuff, StandardCharsets.US_ASCII)));
    }

}
