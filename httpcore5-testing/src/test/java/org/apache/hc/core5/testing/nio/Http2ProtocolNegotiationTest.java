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
import java.net.SocketAddress;
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
import org.apache.hc.core5.http2.ssl.SecurePortStrategy;
import org.apache.hc.core5.io.ShutdownType;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.apache.hc.core5.testing.SSLTestContexts;
import org.apache.hc.core5.util.TimeValue;
import org.apache.logging.log4j.LogManager;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;

public class Http2ProtocolNegotiationTest {

    private static final TimeValue TIMEOUT = TimeValue.ofSeconds(30);

    private HttpAsyncServer server;

    @Rule
    public ExternalResource serverResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            server = H2ServerBootstrap.bootstrap()
                    .setTlsStrategy(new H2ServerTlsStrategy(SSLTestContexts.createServerSSLContext(), new SecurePortStrategy() {

                        @Override
                        public boolean isSecure(final SocketAddress localAddress) {
                            return true;
                        }

                    }))
                    .setVersionPolicy(HttpVersionPolicy.NEGOTIATE)
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
                    .setConnectionListener(new InternalConnectionListener("test", LogManager.getLogger(getClass())))
                    .create();
        }

        @Override
        protected void after() {
            if (server != null) {
                try {
                    server.shutdown(ShutdownType.IMMEDIATE);
                    server = null;
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
            requester = H2RequesterBootstrap.bootstrap()
                    .setTlsStrategy(new H2ClientTlsStrategy(SSLTestContexts.createClientSSLContext()))
                    .setVersionPolicy(HttpVersionPolicy.NEGOTIATE)
                    .setIOReactorConfig(IOReactorConfig.custom()
                            .setSoTimeout(TIMEOUT)
                            .build())
                    .create();
        }

        @Override
        protected void after() {
            if (requester != null) {
                try {
                    requester.shutdown(ShutdownType.IMMEDIATE);
                    requester = null;
                } catch (final Exception ignore) {
                }
            }
        }

    };

    private static int version;

    @BeforeClass
    public static void determineJavaVersion() {
        version = 7;
        final String s = System.getProperty("java.version");
        if (s.equals("9-ea")) {
            version = 9;
        }
        final String[] parts = s.split("\\.");
        if (parts.length >= 2) {
            if (parts[0].equals("1")) {
                try {
                    version = Integer.parseInt(parts[1]);
                } catch (NumberFormatException ignore) {
                }
            }
        }
    }

    @Before
    public void checkVersion() {
        Assume.assumeTrue("Java version must be 1.8 or greater", version > 7);
    }

    @Test
    public void testForceHttp1() throws Exception {
        server.start();
        final ListenerEndpoint listener = server.listen(new InetSocketAddress(0));
        listener.waitFor();
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
        final ListenerEndpoint listener = server.listen(new InetSocketAddress(0));
        listener.waitFor();
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
        final ListenerEndpoint listener = server.listen(new InetSocketAddress(0));
        listener.waitFor();
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

        if (version < 9) {
            Assert.assertThat(response1.getVersion(), CoreMatchers.<ProtocolVersion>equalTo(HttpVersion.HTTP_1_1));
        } else {
//            Assert.assertThat("Requires --add-opens java.base/sun.security.ssl=ALL-UNNAMED with Java 1.9+ " +
//                    " in order to enable reflective access to SSLEngine",
//                    response1.getVersion(), CoreMatchers.<ProtocolVersion>equalTo(HttpVersion.HTTP_2));
        }
    }

}
