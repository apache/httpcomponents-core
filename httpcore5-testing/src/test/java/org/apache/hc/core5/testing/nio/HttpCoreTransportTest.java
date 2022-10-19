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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Future;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.nio.AsyncClientEndpoint;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityProducer;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.apache.hc.core5.util.Timeout;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;

public abstract class HttpCoreTransportTest {

    private static final Timeout TIMEOUT = Timeout.ofMinutes(1);

    final URIScheme scheme;

    HttpCoreTransportTest(final URIScheme scheme) {
        this.scheme = scheme;
    }

    abstract HttpAsyncServer serverStart() throws IOException;

    abstract HttpAsyncRequester clientStart() throws IOException;

    @Test
    public void testSequentialRequests() throws Exception {
        final HttpAsyncServer server = serverStart();
        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(0), scheme);
        final ListenerEndpoint listener = future.get();
        final InetSocketAddress address = (InetSocketAddress) listener.getAddress();
        final HttpAsyncRequester requester = clientStart();

        final HttpHost target = new HttpHost(scheme.id, "localhost", address.getPort());
        final Future<Message<HttpResponse, String>> resultFuture1 = requester.execute(
                new BasicRequestProducer(Method.POST, target, "/stuff",
                        new StringAsyncEntityProducer("some stuff", ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), TIMEOUT, null);
        final Message<HttpResponse, String> message1 = resultFuture1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        assertThat(message1, CoreMatchers.notNullValue());
        final HttpResponse response1 = message1.getHead();
        assertThat(response1.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
        final String body1 = message1.getBody();
        assertThat(body1, CoreMatchers.equalTo("some stuff"));

        final Future<Message<HttpResponse, String>> resultFuture2 = requester.execute(
                new BasicRequestProducer(Method.POST, target, "/other-stuff",
                        new StringAsyncEntityProducer("some other stuff", ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), TIMEOUT, null);
        final Message<HttpResponse, String> message2 = resultFuture2.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        assertThat(message2, CoreMatchers.notNullValue());
        final HttpResponse response2 = message2.getHead();
        assertThat(response2.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
        final String body2 = message2.getBody();
        assertThat(body2, CoreMatchers.equalTo("some other stuff"));

        final Future<Message<HttpResponse, String>> resultFuture3 = requester.execute(
                new BasicRequestProducer(Method.POST, target, "/more-stuff",
                        new StringAsyncEntityProducer("some more stuff", ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), TIMEOUT, null);
        final Message<HttpResponse, String> message3 = resultFuture3.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        assertThat(message3, CoreMatchers.notNullValue());
        final HttpResponse response3 = message3.getHead();
        assertThat(response3.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
        final String body3 = message3.getBody();
        assertThat(body3, CoreMatchers.equalTo("some more stuff"));
    }

    @Test
    public void testSequentialRequestsNonPersistentConnection() throws Exception {
        final HttpAsyncServer server = serverStart();
        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(0), scheme);
        final ListenerEndpoint listener = future.get();
        final InetSocketAddress address = (InetSocketAddress) listener.getAddress();
        final HttpAsyncRequester requester = clientStart();

        final HttpHost target = new HttpHost(scheme.id, "localhost", address.getPort());
        final Future<Message<HttpResponse, String>> resultFuture1 = requester.execute(
                new BasicRequestProducer(Method.POST, target, "/no-keep-alive/stuff",
                        new StringAsyncEntityProducer("some stuff", ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), TIMEOUT, null);
        final Message<HttpResponse, String> message1 = resultFuture1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        assertThat(message1, CoreMatchers.notNullValue());
        final HttpResponse response1 = message1.getHead();
        assertThat(response1.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
        final String body1 = message1.getBody();
        assertThat(body1, CoreMatchers.equalTo("some stuff"));

        final Future<Message<HttpResponse, String>> resultFuture2 = requester.execute(
                new BasicRequestProducer(Method.POST, target, "/no-keep-alive/other-stuff",
                        new StringAsyncEntityProducer("some other stuff", ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), TIMEOUT, null);
        final Message<HttpResponse, String> message2 = resultFuture2.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        assertThat(message2, CoreMatchers.notNullValue());
        final HttpResponse response2 = message2.getHead();
        assertThat(response2.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
        final String body2 = message2.getBody();
        assertThat(body2, CoreMatchers.equalTo("some other stuff"));

        final Future<Message<HttpResponse, String>> resultFuture3 = requester.execute(
                new BasicRequestProducer(Method.POST, target, "/no-keep-alive/more-stuff",
                        new StringAsyncEntityProducer("some more stuff", ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), TIMEOUT, null);
        final Message<HttpResponse, String> message3 = resultFuture3.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        assertThat(message3, CoreMatchers.notNullValue());
        final HttpResponse response3 = message3.getHead();
        assertThat(response3.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
        final String body3 = message3.getBody();
        assertThat(body3, CoreMatchers.equalTo("some more stuff"));
    }

    @Test
    public void testSequentialRequestsSameEndpoint() throws Exception {
        final HttpAsyncServer server = serverStart();
        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(0), scheme);
        final ListenerEndpoint listener = future.get();
        final InetSocketAddress address = (InetSocketAddress) listener.getAddress();
        final HttpAsyncRequester requester = clientStart();

        final HttpHost target = new HttpHost(scheme.id, "localhost", address.getPort());
        final Future<AsyncClientEndpoint> endpointFuture = requester.connect(target, Timeout.ofSeconds(5));
        final AsyncClientEndpoint endpoint = endpointFuture.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        try {

            final Future<Message<HttpResponse, String>> resultFuture1 = endpoint.execute(
                    new BasicRequestProducer(Method.POST, target, "/stuff",
                            new StringAsyncEntityProducer("some stuff", ContentType.TEXT_PLAIN)),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
            final Message<HttpResponse, String> message1 = resultFuture1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            assertThat(message1, CoreMatchers.notNullValue());
            final HttpResponse response1 = message1.getHead();
            assertThat(response1.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
            final String body1 = message1.getBody();
            assertThat(body1, CoreMatchers.equalTo("some stuff"));

            final Future<Message<HttpResponse, String>> resultFuture2 = endpoint.execute(
                    new BasicRequestProducer(Method.POST, target, "/other-stuff",
                            new StringAsyncEntityProducer("some other stuff", ContentType.TEXT_PLAIN)),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
            final Message<HttpResponse, String> message2 = resultFuture2.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            assertThat(message2, CoreMatchers.notNullValue());
            final HttpResponse response2 = message2.getHead();
            assertThat(response2.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
            final String body2 = message2.getBody();
            assertThat(body2, CoreMatchers.equalTo("some other stuff"));

            final Future<Message<HttpResponse, String>> resultFuture3 = endpoint.execute(
                    new BasicRequestProducer(Method.POST, target, "/more-stuff",
                            new StringAsyncEntityProducer("some more stuff", ContentType.TEXT_PLAIN)),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
            final Message<HttpResponse, String> message3 = resultFuture3.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            assertThat(message3, CoreMatchers.notNullValue());
            final HttpResponse response3 = message3.getHead();
            assertThat(response3.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
            final String body3 = message3.getBody();
            assertThat(body3, CoreMatchers.equalTo("some more stuff"));

        } finally {
            endpoint.releaseAndReuse();
        }
    }

    @Test
    public void testPipelinedRequests() throws Exception {
        final HttpAsyncServer server = serverStart();
        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(0), scheme);
        final ListenerEndpoint listener = future.get();
        final InetSocketAddress address = (InetSocketAddress) listener.getAddress();
        final HttpAsyncRequester requester = clientStart();

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
                assertThat(message, CoreMatchers.notNullValue());
                final HttpResponse response = message.getHead();
                assertThat(response.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
                final String body = message.getBody();
                assertThat(body, CoreMatchers.containsString("stuff"));
            }

        } finally {
            endpoint.releaseAndReuse();
        }
    }

    @Test
    public void testNonPersistentHeads() throws Exception {
        final HttpAsyncServer server = serverStart();
        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(0), scheme);
        final ListenerEndpoint listener = future.get();
        final InetSocketAddress address = (InetSocketAddress) listener.getAddress();
        final HttpAsyncRequester requester = clientStart();

        final HttpHost target = new HttpHost(scheme.id, "localhost", address.getPort());
        final Queue<Future<Message<HttpResponse, String>>> queue = new LinkedList<>();

        for (int i = 0; i < 20; i++) {
            final HttpRequest head = new BasicHttpRequest(Method.HEAD, target, "/no-keep-alive/stuff?p=" + i);
            queue.add(requester.execute(
                    new BasicRequestProducer(head, null),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), TIMEOUT, null));
        }

        while (!queue.isEmpty()) {
            final Future<Message<HttpResponse, String>> resultFuture = queue.remove();
            final Message<HttpResponse, String> message = resultFuture.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            assertThat(message, CoreMatchers.notNullValue());
            final HttpResponse response = message.getHead();
            assertThat(response.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
            assertThat(message.getBody(), CoreMatchers.nullValue());
        }
    }

}
