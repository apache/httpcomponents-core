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

import static org.hamcrest.MatcherAssert.assertThat;

import java.net.InetSocketAddress;
import java.util.concurrent.Future;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
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
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.apache.hc.core5.testing.nio.extension.H2AsyncRequesterResource;
import org.apache.hc.core5.testing.nio.extension.H2AsyncServerResource;
import org.apache.hc.core5.util.Timeout;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class H2ProtocolNegotiationTest {

    private static final Timeout TIMEOUT = Timeout.ofMinutes(1);

    @RegisterExtension
    private final H2AsyncServerResource serverResource;
    @RegisterExtension
    private final H2AsyncRequesterResource clientResource;

    public H2ProtocolNegotiationTest() {
        this.serverResource = new H2AsyncServerResource(bootstrap -> bootstrap
                .setVersionPolicy(HttpVersionPolicy.NEGOTIATE)
                .setIOReactorConfig(
                        IOReactorConfig.custom()
                                .setSoTimeout(TIMEOUT)
                                .build())
                .register("*", () -> new EchoHandler(2048))
        );
        this.clientResource = new H2AsyncRequesterResource(bootstrap -> bootstrap
                .setVersionPolicy(HttpVersionPolicy.NEGOTIATE)
                .setIOReactorConfig(IOReactorConfig.custom()
                        .setSoTimeout(TIMEOUT)
                        .build())
        );
    }

    @Test
    public void testForceHttp1() throws Exception {
        final HttpAsyncServer server = serverResource.start();
        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(0), URIScheme.HTTPS);
        final ListenerEndpoint listener = future.get();
        final InetSocketAddress address = (InetSocketAddress) listener.getAddress();
        final HttpAsyncRequester requester = clientResource.start();

        final HttpHost target = new HttpHost(URIScheme.HTTPS.id, "localhost", address.getPort());
        final Future<AsyncClientEndpoint> connectFuture = requester.connect(target, TIMEOUT, HttpVersionPolicy.FORCE_HTTP_1, null);
        final AsyncClientEndpoint endpoint = connectFuture.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());

        final Future<Message<HttpResponse, String>> resultFuture1 = endpoint.execute(
                new BasicRequestProducer(Method.POST, target, "/stuff",
                        new StringAsyncEntityProducer("some stuff", ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> message1 = resultFuture1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        assertThat(message1, CoreMatchers.notNullValue());
        final HttpResponse response1 = message1.getHead();
        assertThat(response1.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
        assertThat(response1.getVersion(), CoreMatchers.equalTo(HttpVersion.HTTP_1_1));
    }

    @Test
    public void testForceHttp2() throws Exception {
        final HttpAsyncServer server = serverResource.start();
        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(0), URIScheme.HTTPS);
        final ListenerEndpoint listener = future.get();
        final InetSocketAddress address = (InetSocketAddress) listener.getAddress();
        final HttpAsyncRequester requester = clientResource.start();

        final HttpHost target = new HttpHost(URIScheme.HTTPS.id, "localhost", address.getPort());
        final Future<AsyncClientEndpoint> connectFuture = requester.connect(target, TIMEOUT, HttpVersionPolicy.FORCE_HTTP_2, null);
        final AsyncClientEndpoint endpoint = connectFuture.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());

        final Future<Message<HttpResponse, String>> resultFuture1 = endpoint.execute(
                new BasicRequestProducer(Method.POST, target, "/stuff",
                        new StringAsyncEntityProducer("some stuff", ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> message1 = resultFuture1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        assertThat(message1, CoreMatchers.notNullValue());
        final HttpResponse response1 = message1.getHead();
        assertThat(response1.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
        assertThat(response1.getVersion(), CoreMatchers.equalTo(HttpVersion.HTTP_2));
    }

    @Test
    public void testNegotiateProtocol() throws Exception {
        final HttpAsyncServer server = serverResource.start();
        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(0), URIScheme.HTTPS);
        final ListenerEndpoint listener = future.get();
        final InetSocketAddress address = (InetSocketAddress) listener.getAddress();
        final HttpAsyncRequester requester = clientResource.start();

        final HttpHost target = new HttpHost(URIScheme.HTTPS.id, "localhost", address.getPort());
        final Future<AsyncClientEndpoint> connectFuture = requester.connect(target, TIMEOUT, HttpVersionPolicy.NEGOTIATE, null);
        final AsyncClientEndpoint endpoint = connectFuture.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());

        final Future<Message<HttpResponse, String>> resultFuture1 = endpoint.execute(
                new BasicRequestProducer(Method.POST, target, "/stuff",
                        new StringAsyncEntityProducer("some stuff", ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> message1 = resultFuture1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        assertThat(message1, CoreMatchers.notNullValue());
        final HttpResponse response1 = message1.getHead();
        assertThat(response1.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
        assertThat(response1.getVersion(), CoreMatchers.equalTo(HttpVersion.HTTP_2));
    }

}
