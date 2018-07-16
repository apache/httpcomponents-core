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
import java.util.List;
import java.util.concurrent.Future;

import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.nio.AsyncClientEndpoint;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.BasicRequestProducer;
import org.apache.hc.core5.http.nio.BasicResponseConsumer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityProducer;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2RequesterBootstrap;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2ServerBootstrap;
import org.apache.hc.core5.http2.impl.nio.bootstrap.Http2AsyncRequester;
import org.apache.hc.core5.http2.ssl.H2ClientTlsStrategy;
import org.apache.hc.core5.http2.ssl.H2ServerTlsStrategy;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.ExceptionEvent;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.apache.hc.core5.testing.SSLTestContexts;
import org.apache.hc.core5.testing.TestingSupport;
import org.apache.hc.core5.testing.classic.LoggingConnPoolListener;
import org.apache.hc.core5.testing.classic.LoggingHttp1StreamListener;
import org.apache.hc.core5.util.Timeout;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Http2ProtocolNegotiationTest {

    private static final Timeout TIMEOUT = Timeout.ofSeconds(30);

    private final Logger log = LoggerFactory.getLogger(getClass());

    private HttpAsyncServer server;

    @Rule
    public ExternalResource serverResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            log.debug("Starting up test server");
            server = H2ServerBootstrap.bootstrap()
                    .setTlsStrategy(new H2ServerTlsStrategy(SSLTestContexts.createServerSSLContext(), SecureAllPortsStrategy.INSTANCE))
                    .setVersionPolicy(HttpVersionPolicy.NEGOTIATE)
                    .setIOReactorConfig(
                            IOReactorConfig.custom()
                                    .setSoTimeout(TIMEOUT)
                                    .build())
                    .setTlsStrategy(new H2ServerTlsStrategy(
                            SSLTestContexts.createServerSSLContext(),
                            SecureAllPortsStrategy.INSTANCE))
                    .register("*", new Supplier<AsyncServerExchangeHandler>() {

                        @Override
                        public AsyncServerExchangeHandler get() {
                            return new EchoHandler(2048);
                        }

                    })
                    .setIOSessionListener(LoggingIOSessionListener.INSTANCE)
                    .setStreamListener(LoggingHttp2StreamListener.INSTANCE)
                    .setStreamListener(LoggingHttp1StreamListener.INSTANCE)
                    .setIOSessionDecorator(LoggingIOSessionDecorator.INSTANCE)
                    .create();
        }

        @Override
        protected void after() {
            log.debug("Shutting down test server");
            if (server != null) {
                try {
                    server.close(CloseMode.GRACEFUL);
                    final List<ExceptionEvent> exceptionLog = server.getExceptionLog();
                    server = null;
                    if (!exceptionLog.isEmpty()) {
                        for (final ExceptionEvent event: exceptionLog) {
                            final Throwable cause = event.getCause();
                            log.error("Unexpected " + cause.getClass() + " at " + event.getTimestamp(), cause);
                        }
                    }
                } catch (final Exception ignore) {
                }
            }
        }

    };

    private Http2AsyncRequester requester;

    @Rule
    public ExternalResource clientResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            log.debug("Starting up test client");
            requester = H2RequesterBootstrap.bootstrap()
                    .setTlsStrategy(new H2ClientTlsStrategy(SSLTestContexts.createClientSSLContext()))
                    .setVersionPolicy(HttpVersionPolicy.NEGOTIATE)
                    .setIOReactorConfig(IOReactorConfig.custom()
                            .setSoTimeout(TIMEOUT)
                            .build())
                    .setTlsStrategy(new H2ClientTlsStrategy(SSLTestContexts.createClientSSLContext()))
                    .setIOSessionListener(LoggingIOSessionListener.INSTANCE)
                    .setStreamListener(LoggingHttp2StreamListener.INSTANCE)
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
                    requester.close(CloseMode.GRACEFUL);
                    final List<ExceptionEvent> exceptionLog = requester.getExceptionLog();
                    requester = null;
                    if (!exceptionLog.isEmpty()) {
                        for (final ExceptionEvent event: exceptionLog) {
                            final Throwable cause = event.getCause();
                            log.error("Unexpected " + cause.getClass() + " at " + event.getTimestamp(), cause);
                        }
                    }
                } catch (final Exception ignore) {
                }
            }
        }

    };

    private static int javaVersion;

    @BeforeClass
    public static void determineJavaVersion() {
        javaVersion = TestingSupport.determineJRELevel();
    }

    @Before
    public void checkVersion() {
        Assume.assumeTrue("Java version must be 1.8 or greater", javaVersion > 7);
    }

    @Test
    public void testForceHttp1() throws Exception {
        server.start();
        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(0));
        final ListenerEndpoint listener = future.get();
        final InetSocketAddress address = (InetSocketAddress) listener.getAddress();
        requester.start();

        final HttpHost target = new HttpHost("localhost", address.getPort(), "https");
        final Future<AsyncClientEndpoint> connectFuture = requester.connect(target, TIMEOUT, HttpVersionPolicy.FORCE_HTTP_1, null);
        final AsyncClientEndpoint endpoint = connectFuture.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());

        final Future<Message<HttpResponse, String>> resultFuture1 = endpoint.execute(
                new BasicRequestProducer("POST", target, "/stuff",
                        new StringAsyncEntityProducer("some stuff", ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> message1 = resultFuture1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assert.assertThat(message1, CoreMatchers.notNullValue());
        final HttpResponse response1 = message1.getHead();
        Assert.assertThat(response1.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
        Assert.assertThat(response1.getVersion(), CoreMatchers.<ProtocolVersion>equalTo(HttpVersion.HTTP_1_1));
    }

    @Test
    public void testForceHttp2() throws Exception {
        server.start();
        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(0));
        final ListenerEndpoint listener = future.get();
        final InetSocketAddress address = (InetSocketAddress) listener.getAddress();
        requester.start();

        final HttpHost target = new HttpHost("localhost", address.getPort(), "https");
        final Future<AsyncClientEndpoint> connectFuture = requester.connect(target, TIMEOUT, HttpVersionPolicy.FORCE_HTTP_2, null);
        final AsyncClientEndpoint endpoint = connectFuture.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());

        final Future<Message<HttpResponse, String>> resultFuture1 = endpoint.execute(
                new BasicRequestProducer("POST", target, "/stuff",
                        new StringAsyncEntityProducer("some stuff", ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> message1 = resultFuture1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assert.assertThat(message1, CoreMatchers.notNullValue());
        final HttpResponse response1 = message1.getHead();
        Assert.assertThat(response1.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
        Assert.assertThat(response1.getVersion(), CoreMatchers.<ProtocolVersion>equalTo(HttpVersion.HTTP_2));
    }

    @Test
    public void testNegotiateProtocol() throws Exception {
        server.start();
        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(0));
        final ListenerEndpoint listener = future.get();
        final InetSocketAddress address = (InetSocketAddress) listener.getAddress();
        requester.start();

        final HttpHost target = new HttpHost("localhost", address.getPort(), "https");
        final Future<AsyncClientEndpoint> connectFuture = requester.connect(target, TIMEOUT, HttpVersionPolicy.NEGOTIATE, null);
        final AsyncClientEndpoint endpoint = connectFuture.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());

        final Future<Message<HttpResponse, String>> resultFuture1 = endpoint.execute(
                new BasicRequestProducer("POST", target, "/stuff",
                        new StringAsyncEntityProducer("some stuff", ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> message1 = resultFuture1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assert.assertThat(message1, CoreMatchers.notNullValue());
        final HttpResponse response1 = message1.getHead();
        Assert.assertThat(response1.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));

        if (javaVersion < 9) {
            Assert.assertThat(response1.getVersion(), CoreMatchers.<ProtocolVersion>equalTo(HttpVersion.HTTP_1_1));
        } else {
            Assert.assertThat(response1.getVersion(), CoreMatchers.<ProtocolVersion>equalTo(HttpVersion.HTTP_2));
        }
    }

}
