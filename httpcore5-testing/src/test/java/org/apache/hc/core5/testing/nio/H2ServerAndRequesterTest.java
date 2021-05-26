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
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Future;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.nio.AsyncClientEndpoint;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityProducer;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.http.protocol.UriPatternMatcher;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2RequesterBootstrap;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2ServerBootstrap;
import org.apache.hc.core5.http2.ssl.H2ClientTlsStrategy;
import org.apache.hc.core5.http2.ssl.H2ServerTlsStrategy;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.apache.hc.core5.testing.SSLTestContexts;
import org.apache.hc.core5.testing.classic.LoggingConnPoolListener;
import org.apache.hc.core5.util.Timeout;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(Parameterized.class)
public class H2ServerAndRequesterTest {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> protocols() {
        return Arrays.asList(new Object[][]{
                { URIScheme.HTTP },
                { URIScheme.HTTPS }
        });
    }
    private static final Timeout TIMEOUT = Timeout.ofSeconds(30);

    private final URIScheme scheme;

    public H2ServerAndRequesterTest(final URIScheme scheme) {
        this.scheme = scheme;
    }

    private HttpAsyncServer server;

    @Rule
    public ExternalResource serverResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            log.debug("Starting up test server");
            server = H2ServerBootstrap.bootstrap()
                    .setLookupRegistry(new UriPatternMatcher<>())
                    .setVersionPolicy(HttpVersionPolicy.NEGOTIATE)
                    .setIOReactorConfig(
                            IOReactorConfig.custom()
                                    .setSoTimeout(TIMEOUT)
                                    .build())
                    .setTlsStrategy(scheme == URIScheme.HTTPS  ?
                            new H2ServerTlsStrategy(SSLTestContexts.createServerSSLContext()) : null)
                    .setStreamListener(LoggingHttp1StreamListener.INSTANCE_SERVER)
                    .setStreamListener(LoggingH2StreamListener.INSTANCE)
                    .setIOSessionDecorator(LoggingIOSessionDecorator.INSTANCE)
                    .setExceptionCallback(LoggingExceptionCallback.INSTANCE)
                    .setIOSessionListener(LoggingIOSessionListener.INSTANCE)
                    .register("*", () -> new EchoHandler(2048))
                    .create();
        }

        @Override
        protected void after() {
            log.debug("Shutting down test server");
            if (server != null) {
                server.close(CloseMode.GRACEFUL);
            }
        }

    };

    private HttpAsyncRequester requester;

    @Rule
    public ExternalResource clientResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            log.debug("Starting up test client");
            requester = H2RequesterBootstrap.bootstrap()
                    .setVersionPolicy(HttpVersionPolicy.NEGOTIATE)
                    .setIOReactorConfig(IOReactorConfig.custom()
                            .setSoTimeout(TIMEOUT)
                            .build())
                    .setTlsStrategy(new H2ClientTlsStrategy(SSLTestContexts.createClientSSLContext()))
                    .setStreamListener(LoggingHttp1StreamListener.INSTANCE_CLIENT)
                    .setStreamListener(LoggingH2StreamListener.INSTANCE)
                    .setConnPoolListener(LoggingConnPoolListener.INSTANCE)
                    .setIOSessionDecorator(LoggingIOSessionDecorator.INSTANCE)
                    .setExceptionCallback(LoggingExceptionCallback.INSTANCE)
                    .setIOSessionListener(LoggingIOSessionListener.INSTANCE)
                    .create();
        }

        @Override
        protected void after() {
            log.debug("Shutting down test client");
            if (requester != null) {
                requester.close(CloseMode.GRACEFUL);
            }
        }

    };

    @Test
    public void testSequentialRequests() throws Exception {
        server.start();
        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(0), scheme);
        final ListenerEndpoint listener = future.get();
        final InetSocketAddress address = (InetSocketAddress) listener.getAddress();
        requester.start();

        final HttpHost target = new HttpHost(scheme.id, "localhost", address.getPort());
        final Future<Message<HttpResponse, String>> resultFuture1 = requester.execute(
                new BasicRequestProducer(Method.POST, target, "/stuff",
                        new StringAsyncEntityProducer("some stuff", ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), TIMEOUT, null);
        final Message<HttpResponse, String> message1 = resultFuture1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        MatcherAssert.assertThat(message1, CoreMatchers.notNullValue());
        final HttpResponse response1 = message1.getHead();
        MatcherAssert.assertThat(response1.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
        final String body1 = message1.getBody();
        MatcherAssert.assertThat(body1, CoreMatchers.equalTo("some stuff"));

        final Future<Message<HttpResponse, String>> resultFuture2 = requester.execute(
                new BasicRequestProducer(Method.POST, target, "/other-stuff",
                        new StringAsyncEntityProducer("some other stuff", ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), TIMEOUT, null);
        final Message<HttpResponse, String> message2 = resultFuture2.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        MatcherAssert.assertThat(message2, CoreMatchers.notNullValue());
        final HttpResponse response2 = message2.getHead();
        MatcherAssert.assertThat(response2.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
        final String body2 = message2.getBody();
        MatcherAssert.assertThat(body2, CoreMatchers.equalTo("some other stuff"));

        final Future<Message<HttpResponse, String>> resultFuture3 = requester.execute(
                new BasicRequestProducer(Method.POST, target, "/more-stuff",
                        new StringAsyncEntityProducer("some more stuff", ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), TIMEOUT, null);
        final Message<HttpResponse, String> message3 = resultFuture3.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        MatcherAssert.assertThat(message3, CoreMatchers.notNullValue());
        final HttpResponse response3 = message3.getHead();
        MatcherAssert.assertThat(response3.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
        final String body3 = message3.getBody();
        MatcherAssert.assertThat(body3, CoreMatchers.equalTo("some more stuff"));
    }

    @Test
    public void testSequentialRequestsSameEndpoint() throws Exception {
        server.start();
        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(0), scheme);
        final ListenerEndpoint listener = future.get();
        final InetSocketAddress address = (InetSocketAddress) listener.getAddress();
        requester.start();

        final HttpHost target = new HttpHost(scheme.id, "localhost", address.getPort());
        final Future<AsyncClientEndpoint> endpointFuture = requester.connect(target, Timeout.ofSeconds(5));
        final AsyncClientEndpoint endpoint = endpointFuture.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        try {

            final Future<Message<HttpResponse, String>> resultFuture1 = endpoint.execute(
                    new BasicRequestProducer(Method.POST, target, "/stuff",
                            new StringAsyncEntityProducer("some stuff", ContentType.TEXT_PLAIN)),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
            final Message<HttpResponse, String> message1 = resultFuture1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            MatcherAssert.assertThat(message1, CoreMatchers.notNullValue());
            final HttpResponse response1 = message1.getHead();
            MatcherAssert.assertThat(response1.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
            final String body1 = message1.getBody();
            MatcherAssert.assertThat(body1, CoreMatchers.equalTo("some stuff"));

            final Future<Message<HttpResponse, String>> resultFuture2 = endpoint.execute(
                    new BasicRequestProducer(Method.POST, target, "/other-stuff",
                            new StringAsyncEntityProducer("some other stuff", ContentType.TEXT_PLAIN)),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
            final Message<HttpResponse, String> message2 = resultFuture2.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            MatcherAssert.assertThat(message2, CoreMatchers.notNullValue());
            final HttpResponse response2 = message2.getHead();
            MatcherAssert.assertThat(response2.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
            final String body2 = message2.getBody();
            MatcherAssert.assertThat(body2, CoreMatchers.equalTo("some other stuff"));

            final Future<Message<HttpResponse, String>> resultFuture3 = endpoint.execute(
                    new BasicRequestProducer(Method.POST, target, "/more-stuff",
                            new StringAsyncEntityProducer("some more stuff", ContentType.TEXT_PLAIN)),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
            final Message<HttpResponse, String> message3 = resultFuture3.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            MatcherAssert.assertThat(message3, CoreMatchers.notNullValue());
            final HttpResponse response3 = message3.getHead();
            MatcherAssert.assertThat(response3.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
            final String body3 = message3.getBody();
            MatcherAssert.assertThat(body3, CoreMatchers.equalTo("some more stuff"));

        } finally {
            endpoint.releaseAndReuse();
        }
    }

    @Test
    public void testPipelinedRequests() throws Exception {
        server.start();
        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(0), scheme);
        final ListenerEndpoint listener = future.get();
        final InetSocketAddress address = (InetSocketAddress) listener.getAddress();
        requester.start();

        final HttpHost target = new HttpHost(scheme.id, "localhost", address.getPort());
        final Future<AsyncClientEndpoint> endpointFuture = requester.connect(target, Timeout.ofSeconds(5));
        final AsyncClientEndpoint endpoint = endpointFuture.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        try {

            final Queue<Future<Message<HttpResponse, String>>> queue = new LinkedList<>();

            queue.add(endpoint.execute(
                    new BasicRequestProducer(Method.POST, target, "/stuff",
                            new StringAsyncEntityProducer("some stuff", ContentType.TEXT_PLAIN)),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null));
            queue.add(endpoint.execute(
                    new BasicRequestProducer(Method.POST, target, "/other-stuff",
                            new StringAsyncEntityProducer("some other stuff", ContentType.TEXT_PLAIN)),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null));
            queue.add(endpoint.execute(
                    new BasicRequestProducer(Method.POST, target, "/more-stuff",
                            new StringAsyncEntityProducer("some more stuff", ContentType.TEXT_PLAIN)),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null));

            while (!queue.isEmpty()) {
                final Future<Message<HttpResponse, String>> resultFuture = queue.remove();
                final Message<HttpResponse, String> message = resultFuture.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
                MatcherAssert.assertThat(message, CoreMatchers.notNullValue());
                final HttpResponse response = message.getHead();
                MatcherAssert.assertThat(response.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
                final String body = message.getBody();
                MatcherAssert.assertThat(body, CoreMatchers.containsString("stuff"));
            }

        } finally {
            endpoint.releaseAndReuse();
        }
    }

}
