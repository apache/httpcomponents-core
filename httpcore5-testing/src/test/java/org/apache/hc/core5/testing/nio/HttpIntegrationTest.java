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

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.routing.RequestRouter;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.EntityTemplate;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.AsyncRequestConsumer;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.AsyncResponseProducer;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncServerRequestHandler;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.RequestChannel;
import org.apache.hc.core5.http.nio.ResponseChannel;
import org.apache.hc.core5.http.nio.entity.AsyncEntityProducers;
import org.apache.hc.core5.http.nio.entity.DigestingEntityConsumer;
import org.apache.hc.core5.http.nio.entity.DigestingEntityProducer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityProducer;
import org.apache.hc.core5.http.nio.support.AbstractServerExchangeHandler;
import org.apache.hc.core5.http.nio.support.AsyncResponseBuilder;
import org.apache.hc.core5.http.nio.support.BasicAsyncServerExpectationDecorator;
import org.apache.hc.core5.http.nio.support.BasicRequestConsumer;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.http.nio.support.BasicResponseProducer;
import org.apache.hc.core5.http.nio.support.ImmediateResponseExchangeHandler;
import org.apache.hc.core5.http.nio.support.classic.ClassicToAsyncRequestProducer;
import org.apache.hc.core5.http.nio.support.classic.ClassicToAsyncResponseConsumer;
import org.apache.hc.core5.http.nio.support.classic.ClassicToAsyncServerExchangeHandler;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.support.BasicRequestBuilder;
import org.apache.hc.core5.testing.extension.ExecutorResource;
import org.apache.hc.core5.util.TextUtils;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

abstract class HttpIntegrationTest {

    static final Timeout TIMEOUT = Timeout.ofMinutes(1);
    static final Timeout LONG_TIMEOUT = Timeout.ofMinutes(2);
    static final int REQ_NUM = 5;

    final URIScheme scheme;
    @RegisterExtension
    final ExecutorResource executorResource;

    public HttpIntegrationTest(final URIScheme scheme) {
        this.scheme = scheme;
        this.executorResource = new ExecutorResource(5);
    }

    HttpHost target(final InetSocketAddress serverEndpoint) {
        return new HttpHost(scheme.id, null, "localhost", serverEndpoint.getPort());
    }

    protected abstract HttpTestServer server();

    protected abstract HttpTestClient client();

    @Test
    void testGet() throws Exception {
        final HttpTestServer server = server();
        final HttpTestClient client = client();

        server.register("/hello", () -> new SingleLineResponseHandler("Hi there"));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        for (int i = 0; i < REQ_NUM; i++) {
            final BasicHttpRequest request = BasicRequestBuilder.get()
                    .setHttpHost(target)
                    .setPath("/hello")
                    .build();
            final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                    new BasicRequestProducer(request, null),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
            final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertNotNull(result);
            final HttpResponse response1 = result.getHead();
            final String entity1 = result.getBody();
            Assertions.assertNotNull(response1);
            Assertions.assertEquals(200, response1.getCode());
            Assertions.assertEquals("Hi there", entity1);
        }
    }

    @Test
    void testGetsPipelined() throws Exception {
        final HttpTestServer server = server();
        final HttpTestClient client = client();

        server.register("/hello", () -> new SingleLineResponseHandler("Hi there"));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final Queue<Future<Message<HttpResponse, String>>> queue = new LinkedList<>();
        for (int i = 0; i < REQ_NUM; i++) {
            final BasicHttpRequest request = BasicRequestBuilder.get()
                    .setHttpHost(target)
                    .setPath("/hello")
                    .build();
            queue.add(streamEndpoint.execute(
                    new BasicRequestProducer(request, null),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null));
        }
        while (!queue.isEmpty()) {
            final Future<Message<HttpResponse, String>> future = queue.remove();
            final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertNotNull(result);
            final HttpResponse response = result.getHead();
            final String entity = result.getBody();
            Assertions.assertNotNull(response);
            Assertions.assertEquals(200, response.getCode());
            Assertions.assertEquals("Hi there", entity);
        }
    }

    @Test
    void testLargeGet() throws Exception {
        final HttpTestServer server = server();
        final HttpTestClient client = client();

        server.register("/", () -> new MultiLineResponseHandler("0123456789abcdef", 5000));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        for (int i = 0; i < REQ_NUM; i++) {
            final BasicHttpRequest request = BasicRequestBuilder.get()
                    .setHttpHost(target)
                    .setPath("/")
                    .build();
            final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                    new BasicRequestProducer(request, null),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);

            final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertNotNull(result);
            final HttpResponse response = result.getHead();
            Assertions.assertNotNull(response);
            Assertions.assertEquals(200, response.getCode());
            final String s = result.getBody();
            Assertions.assertNotNull(s);
            final StringTokenizer t = new StringTokenizer(s, "\r\n");
            while (t.hasMoreTokens()) {
                Assertions.assertEquals("0123456789abcdef", t.nextToken());
            }
        }
    }

    @Test
    void testLargeGetsPipelined() throws Exception {
        final HttpTestServer server = server();
        final HttpTestClient client = client();

        server.register("/", () -> new MultiLineResponseHandler("0123456789abcdef", 2000));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final Queue<Future<Message<HttpResponse, String>>> queue = new LinkedList<>();
        for (int i = 0; i < REQ_NUM; i++) {
            final BasicHttpRequest request = BasicRequestBuilder.get()
                    .setHttpHost(target)
                    .setPath("/")
                    .build();
            queue.add(streamEndpoint.execute(
                    new BasicRequestProducer(request, null),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null));
        }
        while (!queue.isEmpty()) {
            final Future<Message<HttpResponse, String>> future = queue.remove();
            final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertNotNull(result);
            final HttpResponse response = result.getHead();
            Assertions.assertNotNull(response);
            Assertions.assertEquals(200, response.getCode());
            final String entity = result.getBody();
            Assertions.assertNotNull(entity);
            final StringTokenizer t = new StringTokenizer(entity, "\r\n");
            while (t.hasMoreTokens()) {
                Assertions.assertEquals("0123456789abcdef", t.nextToken());
            }
        }
    }

    @Test
    void testPost() throws Exception {
        final HttpTestServer server = server();
        final HttpTestClient client = client();

        server.register("/hello", () -> new SingleLineResponseHandler("Hi back"));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        for (int i = 0; i < REQ_NUM; i++) {
            final BasicHttpRequest request = BasicRequestBuilder.post()
                    .setHttpHost(target)
                    .setPath("/hello")
                    .build();
            final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                    new BasicRequestProducer(request, AsyncEntityProducers.create("Hi there")),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
            final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertNotNull(result);
            final HttpResponse response1 = result.getHead();
            final String entity1 = result.getBody();
            Assertions.assertNotNull(response1);
            Assertions.assertEquals(200, response1.getCode());
            Assertions.assertEquals("Hi back", entity1);
        }
    }

    @Test
    void testPostPipelined() throws Exception {
        final HttpTestServer server = server();
        final HttpTestClient client = client();

        server.register("/hello", () -> new SingleLineResponseHandler("Hi back"));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final Queue<Future<Message<HttpResponse, String>>> queue = new LinkedList<>();
        for (int i = 0; i < REQ_NUM; i++) {
            final BasicHttpRequest request = BasicRequestBuilder.post()
                    .setHttpHost(target)
                    .setPath("/hello")
                    .build();
            queue.add(streamEndpoint.execute(
                    new BasicRequestProducer(request, AsyncEntityProducers.create("Hi there")),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null));
        }
        while (!queue.isEmpty()) {
            final Future<Message<HttpResponse, String>> future = queue.remove();
            final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertNotNull(result);
            final HttpResponse response = result.getHead();
            final String entity = result.getBody();
            Assertions.assertNotNull(response);
            Assertions.assertEquals(200, response.getCode());
            Assertions.assertEquals("Hi back", entity);
        }
    }

    @Test
    void testLargePostsPipelined() throws Exception {
        final HttpTestServer server = server();
        final HttpTestClient client = client();

        server.register("*", () -> new EchoHandler(2048));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final Queue<Future<Message<HttpResponse, String>>> queue = new LinkedList<>();
        for (int i = 0; i < REQ_NUM; i++) {
            final BasicHttpRequest request = BasicRequestBuilder.post()
                    .setHttpHost(target)
                    .setPath("/echo")
                    .build();
            queue.add(streamEndpoint.execute(
                    new BasicRequestProducer(request, new MultiLineEntityProducer("0123456789abcdef", 5000)),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null));
        }
        while (!queue.isEmpty()) {
            final Future<Message<HttpResponse, String>> future = queue.remove();
            final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertNotNull(result);
            final HttpResponse response = result.getHead();
            Assertions.assertNotNull(response);
            Assertions.assertEquals(200, response.getCode());
            final String entity = result.getBody();
            Assertions.assertNotNull(entity);
            final StringTokenizer t = new StringTokenizer(entity, "\r\n");
            while (t.hasMoreTokens()) {
                Assertions.assertEquals("0123456789abcdef", t.nextToken());
            }
        }
    }

    @Test
    void testEchoEmptyEntity() throws Exception {
        final HttpTestServer server = server();
        final HttpTestClient client = client();

        server.register("*", () -> new EchoHandler(8));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final BasicHttpRequest request = BasicRequestBuilder.post()
                .setHttpHost(target)
                .setPath("/echo")
                .build();
        final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                new BasicRequestProducer(request, AsyncEntityProducers.create("")),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);

        final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(result);
        final HttpResponse response = result.getHead();
        final String entity = result.getBody();
        Assertions.assertNotNull(response);
        Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assertions.assertNotNull(entity);
        Assertions.assertEquals("", entity);
    }

    @Test
    void testEchoLargeEntitySmallBuffer() throws Exception {
        final HttpTestServer server = server();
        final HttpTestClient client = client();

        server.register("*", () -> new EchoHandler(8));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 5000; i++) {
            builder.append("0123456789abcdef");
        }
        final String content = builder.toString();

        final BasicHttpRequest request = BasicRequestBuilder.post()
                .setHttpHost(target)
                .setPath("/echo")
                .build();
        final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                new BasicRequestProducer(request, AsyncEntityProducers.create(content)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);

        final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(result);
        final HttpResponse response = result.getHead();
        final String entity = result.getBody();
        Assertions.assertNotNull(response);
        Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assertions.assertEquals(content, entity);
    }

    @Test
    void testEchoNoEntityRequest() throws Exception {
        final HttpTestServer server = server();
        final HttpTestClient client = client();

        server.register("*", () -> new EchoHandler(8));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final BasicHttpRequest request = BasicRequestBuilder.post()
                .setHttpHost(target)
                .setPath("/echo")
                .build();
        final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                new BasicRequestProducer(request, null),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);

        final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(result);
        final HttpResponse response = result.getHead();
        Assertions.assertNotNull(response);
        Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
        final String body = result.getBody();
        Assertions.assertTrue(body == null || body.isEmpty());
    }

    @Test
    void testEchoHeadNoBody() throws Exception {
        final HttpTestServer server = server();
        final HttpTestClient client = client();

        server.register("*", () -> new EchoHandler(8));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final BasicHttpRequest request = BasicRequestBuilder.head()
                .setHttpHost(target)
                .setPath("/echo")
                .build();
        final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                new BasicRequestProducer(request, null),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);

        final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(result);
        final HttpResponse response = result.getHead();
        Assertions.assertNotNull(response);
        Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assertions.assertNull(result.getBody());
    }

    @Test
    void testEchoManySmallRequests() throws Exception {
        final HttpTestServer server = server();
        final HttpTestClient client = client();

        server.register("*", () -> new EchoHandler(8));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final Queue<Future<Message<HttpResponse, String>>> queue = new LinkedList<>();
        for (int i = 0; i < REQ_NUM; i++) {
            final BasicHttpRequest request = BasicRequestBuilder.post()
                    .setHttpHost(target)
                    .setPath("/echo")
                    .build();
            queue.add(streamEndpoint.execute(
                    new BasicRequestProducer(request, AsyncEntityProducers.create("hi-" + i)),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null));
        }

        for (int i = 0; i < REQ_NUM; i++) {
            final Future<Message<HttpResponse, String>> future = queue.remove();
            final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertNotNull(result);
            final HttpResponse response = result.getHead();
            final String entity = result.getBody();
            Assertions.assertNotNull(response);
            Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
            Assertions.assertEquals("hi-" + i, entity);
        }
    }

    @Test
    void testNoEntityPost() throws Exception {
        final HttpTestServer server = server();
        final HttpTestClient client = client();

        server.register("/hello", () -> new SingleLineResponseHandler("Hi back"));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        for (int i = 0; i < REQ_NUM; i++) {
            final BasicHttpRequest request = BasicRequestBuilder.post()
                    .setHttpHost(target)
                    .setPath("/hello")
                    .build();
            final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                    new BasicRequestProducer(request, null),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
            final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertNotNull(result);
            final HttpResponse response1 = result.getHead();
            final String entity1 = result.getBody();
            Assertions.assertNotNull(response1);
            Assertions.assertEquals(200, response1.getCode());
            Assertions.assertEquals("Hi back", entity1);
        }
    }

    @Test
    void testPostOutOfSequenceResponseOK() throws Exception {
        final HttpTestServer server = server();
        final HttpTestClient client = client();

        server.register("/hello", () -> new ImmediateResponseExchangeHandler(200, "Welcome"));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();

        final Future<ClientSessionEndpoint> connectFuture = client.connect("localhost", serverEndpoint.getPort(), TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        for (int i = 0; i < REQ_NUM; i++) {
            final BasicHttpRequest request = BasicRequestBuilder.post()
                    .setHttpHost(target)
                    .setPath("/hello")
                    .build();
            final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                    new BasicRequestProducer(request, new MultiLineEntityProducer("Hello", 512 * i)),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
            final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());

            Assertions.assertNotNull(result);
            final HttpResponse response = result.getHead();
            final String entity = result.getBody();
            Assertions.assertNotNull(response);
            Assertions.assertEquals(200, response.getCode());
            Assertions.assertEquals("Welcome", entity);
        }
    }

    @Test
    void testHead() throws Exception {
        final HttpTestServer server = server();
        final HttpTestClient client = client();

        server.register("/hello", () -> new SingleLineResponseHandler("Hi there"));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        for (int i = 0; i < REQ_NUM; i++) {
            final BasicHttpRequest request = BasicRequestBuilder.head()
                    .setHttpHost(target)
                    .setPath("/hello")
                    .build();
            final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                    new BasicRequestProducer(request, null),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
            final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertNotNull(result);
            final HttpResponse response1 = result.getHead();
            Assertions.assertNotNull(response1);
            Assertions.assertEquals(200, response1.getCode());
            Assertions.assertNull(result.getBody());
        }
    }

    @Test
    void testHeadPipelined() throws Exception {
        final HttpTestServer server = server();
        final HttpTestClient client = client();

        server.register("/hello", () -> new SingleLineResponseHandler("Hi there"));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final Queue<Future<Message<HttpResponse, String>>> queue = new LinkedList<>();
        for (int i = 0; i < REQ_NUM; i++) {
            final BasicHttpRequest request = BasicRequestBuilder.head()
                    .setHttpHost(target)
                    .setPath("/hello")
                    .build();
            queue.add(streamEndpoint.execute(
                    new BasicRequestProducer(request, null),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null));
        }
        while (!queue.isEmpty()) {
            final Future<Message<HttpResponse, String>> future = queue.remove();
            final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertNotNull(result);
            final HttpResponse response1 = result.getHead();
            Assertions.assertNotNull(response1);
            Assertions.assertEquals(200, response1.getCode());
            Assertions.assertNull(result.getBody());
        }
    }

    @Test
    void testSlowResponseConsumer() throws Exception {
        final HttpTestServer server = server();
        final HttpTestClient client = client();

        server.register("/", () -> new MultiLineResponseHandler("0123456789abcd", 3));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final ClassicHttpRequest request = ClassicRequestBuilder.get()
                .setHttpHost(target)
                .setPath("/")
                .build();
        final ClassicToAsyncResponseConsumer responseConsumer = new ClassicToAsyncResponseConsumer(16, TIMEOUT);

        streamEndpoint.execute(
                new BasicRequestProducer(request, null),
                responseConsumer,
                null);

        final Random random = new Random();

        try (ClassicHttpResponse response = responseConsumer.blockWaiting()) {
            final HttpEntity entity = response.getEntity();
            final ContentType contentType = ContentType.parse(entity.getContentType());
            final Charset charset = ContentType.getCharset(contentType, StandardCharsets.UTF_8);

            try (final InputStream inputStream = entity.getContent()) {
                final StringBuilder buffer = new StringBuilder();
                final byte[] tmp = new byte[16];
                int l;
                while ((l = inputStream.read(tmp)) != -1) {
                    buffer.append(charset.decode(ByteBuffer.wrap(tmp, 0, l)));
                    Thread.sleep(100 + random.nextInt(400));
                }
                final StringTokenizer t1 = new StringTokenizer(buffer.toString(), "\r\n");
                while (t1.hasMoreTokens()) {
                    Assertions.assertEquals("0123456789abcd", t1.nextToken());
                }
            }
        }
    }

    @Test
    void testSlowRequestProducer() throws Exception {
        final HttpTestServer server = server();
        final HttpTestClient client = client();

        server.register("*", () -> new EchoHandler(2048));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final Random random = new Random();

        final ClassicHttpRequest request1 = ClassicRequestBuilder.post()
                .setHttpHost(target)
                .setPath("/echo")
                .setEntity(new EntityTemplate(
                        -1,
                        ContentType.TEXT_PLAIN.withCharset(StandardCharsets.UTF_8),
                        null,
                        outputStream -> {
                            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
                                for (int i = 0; i < 500; i++) {
                                    if (i % 100 == 0) {
                                        writer.flush();
                                        Thread.sleep(100 + random.nextInt(400));
                                    }
                                    writer.write("0123456789abcdef\r\n");
                                }
                            } catch (final InterruptedException ex) {
                                Thread.currentThread().interrupt();
                                throw new InterruptedIOException(ex.getMessage());
                            }
                        }))
                .build();
        final ClassicToAsyncRequestProducer requestProducer = new ClassicToAsyncRequestProducer(request1, 16, TIMEOUT);

        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                requestProducer,
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        requestProducer.blockWaiting().execute();

        final Message<HttpResponse, String> result1 = future1.get(LONG_TIMEOUT.getDuration(), LONG_TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(result1);
        final HttpResponse response1 = result1.getHead();
        Assertions.assertNotNull(response1);
        Assertions.assertEquals(200, response1.getCode());
        final String s1 = result1.getBody();
        Assertions.assertNotNull(s1);
        final StringTokenizer t1 = new StringTokenizer(s1, "\r\n");
        while (t1.hasMoreTokens()) {
            Assertions.assertEquals("0123456789abcdef", t1.nextToken());
        }
    }

    @Test
    void testSlowResponseProducer() throws Exception {
        final HttpTestServer server = server();
        final HttpTestClient client = client();

        final Random random = new Random();

        final HttpRequestHandler requestHandler = (request, response, context) -> {
            final HttpEntity requestEntity = request.getEntity();
            if (requestEntity != null) {
                EntityUtils.consume(requestEntity);
            }
            final HttpEntity responseEntity = new EntityTemplate(
                    ContentType.TEXT_PLAIN.withCharset(StandardCharsets.UTF_8),
                    outputStream -> {
                        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
                            for (int i = 0; i < 500; i++) {
                                if (i % 100 == 0) {
                                    writer.flush();
                                    Thread.sleep(100 + random.nextInt(400));
                                }
                                writer.write("0123456789abcdef\r\n");
                            }
                        } catch (final InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            throw new InterruptedIOException(ex.getMessage());
                        }
                    });
            response.setEntity(responseEntity);
        };

        final RequestRouter<HttpRequestHandler> requestRouter = RequestRouter.<HttpRequestHandler>builder()
                .resolveAuthority(RequestRouter.LOCAL_AUTHORITY_RESOLVER)
                .addRoute(RequestRouter.LOCAL_AUTHORITY, "/hello", requestHandler)
                .build();

        server.register("*", () -> new ClassicToAsyncServerExchangeHandler(
                Executors.newSingleThreadExecutor(),
                requestRouter,
                LoggingExceptionCallback.INSTANCE));

        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final BasicHttpRequest request1 = BasicRequestBuilder.get()
                .setHttpHost(target)
                .setPath("/hello")
                .build();
        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                new BasicRequestProducer(request1, null),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> result1 = future1.get(LONG_TIMEOUT.getDuration(), LONG_TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(result1);
        final HttpResponse response1 = result1.getHead();
        Assertions.assertNotNull(response1);
        Assertions.assertEquals(200, response1.getCode());
        final String s1 = result1.getBody();
        Assertions.assertNotNull(s1);
        final StringTokenizer t1 = new StringTokenizer(s1, "\r\n");
        while (t1.hasMoreTokens()) {
            Assertions.assertEquals("0123456789abcdef", t1.nextToken());
        }
    }

    @Test
    void testPrematureResponse() throws Exception {
        final HttpTestServer server = server();
        final HttpTestClient client = client();

        server.register("*", () -> new AsyncServerExchangeHandler() {

            private final AtomicReference<AsyncResponseProducer> responseProducer = new AtomicReference<>();

            @Override
            public void handleRequest(
                    final HttpRequest request,
                    final EntityDetails entityDetails,
                    final ResponseChannel responseChannel,
                    final HttpContext context) throws HttpException, IOException {
                final AsyncResponseProducer producer;
                final Header h = request.getFirstHeader("password");
                if (h != null && "secret".equals(h.getValue())) {
                    producer = new BasicResponseProducer(HttpStatus.SC_OK, "All is well");
                } else {
                    producer = new BasicResponseProducer(HttpStatus.SC_UNAUTHORIZED, "You shall not pass");
                }
                responseProducer.set(producer);
                producer.sendResponse(responseChannel, context);
            }

            @Override
            public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
                capacityChannel.update(Integer.MAX_VALUE);
            }

            @Override
            public void consume(final ByteBuffer src) throws IOException {
            }

            @Override
            public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
            }

            @Override
            public int available() {
                final AsyncResponseProducer producer = responseProducer.get();
                return producer.available();
            }

            @Override
            public void produce(final DataStreamChannel channel) throws IOException {
                final AsyncResponseProducer producer = responseProducer.get();
                producer.produce(channel);
            }

            @Override
            public void failed(final Exception cause) {
            }

            @Override
            public void releaseResources() {
            }
        });
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        for (int i = 0; i < 3; i++) {
            final BasicHttpRequest request1 = BasicRequestBuilder.post()
                    .setHttpHost(target)
                    .setPath("/echo")
                    .build();
            final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                    new BasicRequestProducer(request1, new MultiLineEntityProducer("0123456789abcdef", 100000)),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
            final Message<HttpResponse, String> result1 = future1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertNotNull(result1);
            final HttpResponse response1 = result1.getHead();
            Assertions.assertNotNull(response1);
            Assertions.assertEquals(HttpStatus.SC_UNAUTHORIZED, response1.getCode());
            Assertions.assertEquals("You shall not pass", result1.getBody());

            Assertions.assertTrue(streamEndpoint.isOpen());
        }
        final BasicHttpRequest request1 = BasicRequestBuilder.post()
                .setHttpHost(target)
                .setPath("/echo")
                .build();
        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                new BasicRequestProducer(request1, new MultiBinEntityProducer(
                        new byte[] {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'},
                        100000,
                        ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> result1 = future1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(result1);
        final HttpResponse response1 = result1.getHead();
        Assertions.assertNotNull(response1);
        Assertions.assertEquals(HttpStatus.SC_UNAUTHORIZED, response1.getCode());
        Assertions.assertEquals("You shall not pass", result1.getBody());
    }

    @Test
    void testExpectationFailed() throws Exception {
        final HttpTestServer server = server();
        final HttpTestClient client = client();

        server.register("*", () -> new MessageExchangeHandler<String>(new StringAsyncEntityConsumer()) {

            @Override
            protected void handle(
                    final Message<HttpRequest, String> request,
                    final AsyncServerRequestHandler.ResponseTrigger responseTrigger,
                    final HttpContext context) throws IOException, HttpException {
                responseTrigger.submitResponse(new BasicResponseProducer(HttpStatus.SC_OK, "All is well"), context);

            }
        });
        server.configure(handler -> new BasicAsyncServerExpectationDecorator(handler) {

            @Override
            protected AsyncResponseProducer verify(final HttpRequest request, final HttpContext context) throws IOException, HttpException {
                final Header h = request.getFirstHeader("password");
                if (h != null && "secret".equals(h.getValue())) {
                    return null;
                } else {
                    return new BasicResponseProducer(HttpStatus.SC_UNAUTHORIZED, "You shall not pass");
                }
            }
        });
        server.configure(handler -> new BasicAsyncServerExpectationDecorator(handler) {

            @Override
            protected AsyncResponseProducer verify(final HttpRequest request, final HttpContext context) throws IOException, HttpException {
                final Header h = request.getFirstHeader("password");
                if (h != null && "secret".equals(h.getValue())) {
                    return null;
                } else {
                    return new BasicResponseProducer(HttpStatus.SC_UNAUTHORIZED, "You shall not pass");
                }
            }
        });
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final BasicHttpRequest request1 = BasicRequestBuilder.post()
                .setHttpHost(target)
                .setPath("/echo")
                .addHeader("password", "secret")
                .build();
        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                new BasicRequestProducer(request1, new MultiLineEntityProducer("0123456789abcdef", 5000)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> result1 = future1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(result1);
        final HttpResponse response1 = result1.getHead();
        Assertions.assertNotNull(response1);
        Assertions.assertEquals(200, response1.getCode());
        Assertions.assertEquals("All is well", result1.getBody());

        final BasicHttpRequest request2 = BasicRequestBuilder.post()
                .setHttpHost(target)
                .setPath("/echo")
                .build();
        final Future<Message<HttpResponse, String>> future2 = streamEndpoint.execute(
                new BasicRequestProducer(request2, new MultiLineEntityProducer("0123456789abcdef", 5000)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> result2 = future2.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(result2);
        final HttpResponse response2 = result2.getHead();
        Assertions.assertNotNull(response2);
        Assertions.assertEquals(HttpStatus.SC_UNAUTHORIZED, response2.getCode());
        Assertions.assertEquals("You shall not pass", result2.getBody());
    }

    @Test
    void testDelayedExpectContinueAck() throws Exception {
        final HttpTestServer server = server();
        final HttpTestClient client = client();

        // Disable 100-continue handshake on the server side
        server.configure(handler -> handler);

        server.register("*", () -> new AsyncServerExchangeHandler() {

            private final Random random = new Random(System.currentTimeMillis());
            private final AsyncEntityProducer entityProducer = AsyncEntityProducers.create(
                    "All is well");

            @Override
            public void handleRequest(
                    final HttpRequest request,
                    final EntityDetails entityDetails,
                    final ResponseChannel responseChannel,
                    final HttpContext context) throws HttpException, IOException {

                executorResource.getExecutorService().execute(() -> {
                    try {
                        if (entityDetails != null) {
                            final Header h = request.getFirstHeader(HttpHeaders.EXPECT);
                            if (h != null && HeaderElements.CONTINUE.equalsIgnoreCase(h.getValue())) {
                                Thread.sleep(random.nextInt(1000));
                                responseChannel.sendInformation(new BasicHttpResponse(HttpStatus.SC_CONTINUE), context);
                            }
                            final HttpResponse response = new BasicHttpResponse(200);
                            responseChannel.sendResponse(response, entityProducer, context);
                        }
                    } catch (final Exception ignore) {
                        // ignore
                    }
                });

            }

            @Override
            public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
                capacityChannel.update(Integer.MAX_VALUE);
            }

            @Override
            public void consume(final ByteBuffer src) throws IOException {
            }

            @Override
            public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
            }

            @Override
            public int available() {
                return entityProducer.available();
            }

            @Override
            public void produce(final DataStreamChannel channel) throws IOException {
                entityProducer.produce(channel);
            }

            @Override
            public void failed(final Exception cause) {
            }

            @Override
            public void releaseResources() {
            }

        });
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final Queue<Future<Message<HttpResponse, String>>> queue = new LinkedList<>();
        for (int i = 0; i < REQ_NUM; i++) {
            final BasicHttpRequest request = BasicRequestBuilder.post()
                    .setHttpHost(target)
                    .setPath("/")
                    .build();
            queue.add(streamEndpoint.execute(
                    new BasicRequestProducer(request, AsyncEntityProducers.create("Some important message")),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null));
        }
        while (!queue.isEmpty()) {
            final Future<Message<HttpResponse, String>> future = queue.remove();
            final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertNotNull(result);
            final HttpResponse response = result.getHead();
            Assertions.assertNotNull(response);
            Assertions.assertEquals(200, response.getCode());
            Assertions.assertEquals("All is well", result.getBody());
        }
    }

    @Test
    void testExceptionInHandler() throws Exception {
        final HttpTestServer server = server();
        final HttpTestClient client = client();

        server.register("/hello", () -> new SingleLineResponseHandler("Hi there") {

            @Override
            protected void handle(
                    final Message<HttpRequest, String> request,
                    final AsyncServerRequestHandler.ResponseTrigger responseTrigger,
                    final HttpContext context) throws IOException, HttpException {
                throw new HttpException("Boom");
            }
        });
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final BasicHttpRequest request = BasicRequestBuilder.get()
                .setHttpHost(target)
                .setPath("/hello")
                .build();
        final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                new BasicRequestProducer(request, null),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(result);
        final HttpResponse response1 = result.getHead();
        final String entity1 = result.getBody();
        Assertions.assertNotNull(response1);
        Assertions.assertEquals(500, response1.getCode());
        Assertions.assertEquals("Boom", entity1);
    }

    @Test
    void testMessageWithTrailers() throws Exception {
        final HttpTestServer server = server();
        final HttpTestClient client = client();

        server.register("/hello", () -> new AbstractServerExchangeHandler<Message<HttpRequest, String>>() {

            @Override
            protected AsyncRequestConsumer<Message<HttpRequest, String>> supplyConsumer(
                    final HttpRequest request,
                    final EntityDetails entityDetails,
                    final HttpContext context) throws HttpException {
                return new BasicRequestConsumer<>(entityDetails != null ? new StringAsyncEntityConsumer() : null);
            }

            @Override
            protected void handle(
                    final Message<HttpRequest, String> requestMessage,
                    final AsyncServerRequestHandler.ResponseTrigger responseTrigger,
                    final HttpContext context) throws HttpException, IOException {
                responseTrigger.submitResponse(new BasicResponseProducer(
                        HttpStatus.SC_OK,
                        new DigestingEntityProducer("MD5",
                                new StringAsyncEntityProducer("Hello back with some trailers"))), context);
            }
        });
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();

        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final BasicHttpRequest request1 = BasicRequestBuilder.get()
                .setHttpHost(target)
                .setPath("/hello")
                .build();
        final DigestingEntityConsumer<String> entityConsumer = new DigestingEntityConsumer<>("MD5", new StringAsyncEntityConsumer());
        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                new BasicRequestProducer(request1, null),
                new BasicResponseConsumer<>(entityConsumer), null);
        final Message<HttpResponse, String> result1 = future1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(result1);
        final HttpResponse response1 = result1.getHead();
        Assertions.assertNotNull(response1);
        Assertions.assertEquals(200, response1.getCode());
        Assertions.assertEquals("Hello back with some trailers", result1.getBody());

        final List<Header> trailers = entityConsumer.getTrailers();
        Assertions.assertNotNull(trailers);
        Assertions.assertEquals(2, trailers.size());
        final Map<String, String> map = new HashMap<>();
        for (final Header header: trailers) {
            map.put(TextUtils.toLowerCase(header.getName()), header.getValue());
        }
        final String digest = TextUtils.toHexString(entityConsumer.getDigest());
        Assertions.assertEquals("MD5", map.get("digest-algo"));
        Assertions.assertEquals(digest, map.get("digest"));
    }

    @Test
    void testNoServiceHandler() throws Exception {
        final HttpTestServer server = server();
        final HttpTestClient client = client();

        server.register("/ehh", () -> new SingleLineResponseHandler("Hi there"));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final BasicHttpRequest request = BasicRequestBuilder.get()
                .setHttpHost(target)
                .setPath("/hello")
                .build();
        final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                new BasicRequestProducer(request, null),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(result);
        final HttpResponse response1 = result.getHead();
        final String entity1 = result.getBody();
        Assertions.assertNotNull(response1);
        Assertions.assertEquals(404, response1.getCode());
        Assertions.assertEquals("Resource not found", entity1);
    }

    @Test
    void testResponseNoContent() throws Exception {
        final HttpTestServer server = server();
        final HttpTestClient client = client();

        server.register("/hello", () -> new SingleLineResponseHandler("Hi there") {

            @Override
            protected void handle(
                    final Message<HttpRequest, String> request,
                    final AsyncServerRequestHandler.ResponseTrigger responseTrigger,
                    final HttpContext context) throws IOException, HttpException {
                final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_NO_CONTENT);
                responseTrigger.submitResponse(new BasicResponseProducer(response), context);
            }
        });
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final BasicHttpRequest request = BasicRequestBuilder.get()
                .setHttpHost(target)
                .setPath("/hello")
                .build();
        final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                new BasicRequestProducer(request, null),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(result);
        final HttpResponse response1 = result.getHead();
        Assertions.assertNotNull(response1);
        Assertions.assertEquals(204, response1.getCode());
        Assertions.assertNull(result.getBody());
    }

    @Test
    void testProtocolException() throws Exception {
        final HttpTestServer server = server();
        final HttpTestClient client = client();

        server.register("/boom", () -> new AsyncServerExchangeHandler() {

            private final StringAsyncEntityProducer entityProducer = new StringAsyncEntityProducer("Everything is OK");

            @Override
            public void releaseResources() {
                entityProducer.releaseResources();
            }

            @Override
            public void handleRequest(
                    final HttpRequest request,
                    final EntityDetails entityDetails,
                    final ResponseChannel responseChannel,
                    final HttpContext context) throws HttpException, IOException {
                final String requestUri = request.getRequestUri();
                if (requestUri.endsWith("boom")) {
                    throw new ProtocolException("Boom!!!");
                }
                responseChannel.sendResponse(new BasicHttpResponse(200), entityProducer, context);
            }

            @Override
            public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
                capacityChannel.update(Integer.MAX_VALUE);
            }

            @Override
            public void consume(final ByteBuffer src) throws IOException {
            }

            @Override
            public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
                // empty
            }

            @Override
            public int available() {
                return entityProducer.available();
            }

            @Override
            public void produce(final DataStreamChannel channel) throws IOException {
                entityProducer.produce(channel);
            }

            @Override
            public void failed(final Exception cause) {
                releaseResources();
            }

        });

        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();
        final BasicHttpRequest request = BasicRequestBuilder.get()
                .setHttpHost(target)
                .setPath("/boom")
                .build();
        final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                new BasicRequestProducer(request, null),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(result);
        final HttpResponse response1 = result.getHead();
        final String entity1 = result.getBody();
        Assertions.assertNotNull(response1);
        Assertions.assertEquals(HttpStatus.SC_BAD_REQUEST, response1.getCode());
        Assertions.assertEquals("Boom!!!", entity1);
    }

    @Test @Disabled
    void testDelayedRequestSubmission() throws Exception {
        final HttpTestServer server = server();
        final HttpTestClient client = client();

        server.register("/hello", () -> new SingleLineResponseHandler("All is well"));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();

        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final Queue<Future<Message<HttpResponse, String>>> queue = new LinkedList<>();
        for (int i = 0; i < REQ_NUM; i++) {
            final BasicHttpRequest request = BasicRequestBuilder.post()
                    .setHttpHost(target)
                    .setPath("/hello")
                    .build();
            final AsyncEntityProducer entityProducer = AsyncEntityProducers.create("Some important message");
            queue.add(streamEndpoint.execute(
                    new AsyncRequestProducer() {

                        private final Random random = new Random(System.currentTimeMillis());

                        @Override
                        public void sendRequest(final RequestChannel channel, final HttpContext context) throws HttpException, IOException {
                            executorResource.getExecutorService().execute(() -> {
                                try {
                                    Thread.sleep(random.nextInt(200));
                                    channel.sendRequest(request, entityProducer, context);
                                } catch (final Exception ignore) {
                                    // ignore
                                }
                            });
                        }

                        @Override
                        public boolean isRepeatable() {
                            return entityProducer.isRepeatable();
                        }

                        @Override
                        public int available() {
                            return entityProducer.available();
                        }

                        @Override
                        public void produce(final DataStreamChannel channel) throws IOException {
                            entityProducer.produce(channel);
                        }

                        @Override
                        public void failed(final Exception cause) {
                            entityProducer.failed(cause);
                        }

                        @Override
                        public void releaseResources() {
                            entityProducer.releaseResources();
                        }

                    },
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null));
        }
        while (!queue.isEmpty()) {
            final Future<Message<HttpResponse, String>> future = queue.remove();
            final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertNotNull(result);
            final HttpResponse response = result.getHead();
            Assertions.assertNotNull(response);
            Assertions.assertEquals(200, response.getCode());
            Assertions.assertEquals("All is well", result.getBody());
        }
    }

    @Test
    void testDelayedResponseSubmission() throws Exception {
        final HttpTestServer server = server();
        final HttpTestClient client = client();

        server.register("/hello", () -> new AbstractServerExchangeHandler<Message<HttpRequest, String>>() {

            private final Random random = new Random(System.currentTimeMillis());

            @Override
            protected AsyncRequestConsumer<Message<HttpRequest, String>> supplyConsumer(
                    final HttpRequest request,
                    final EntityDetails entityDetails,
                    final HttpContext context) throws HttpException {
                return new BasicRequestConsumer<>(entityDetails != null ? new StringAsyncEntityConsumer() : null);
            }

            @Override
            protected void handle(
                    final Message<HttpRequest, String> requestMessage,
                    final AsyncServerRequestHandler.ResponseTrigger responseTrigger,
                    final HttpContext context) throws HttpException, IOException {
                executorResource.getExecutorService().execute(() -> {
                    try {
                        Thread.sleep(random.nextInt(200));
                        responseTrigger.submitResponse(AsyncResponseBuilder.create(HttpStatus.SC_OK)
                                        .setEntity(new MultiLineEntityProducer("All is well", 100))
                                        .build(),
                                context);
                        Thread.sleep(random.nextInt(200));
                    } catch (final Exception ignore) {
                        // ignore
                    }
                });

            }

        });
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();

        final Future<ClientSessionEndpoint> connectFuture = client.connect(
                "localhost", serverEndpoint.getPort(), TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final Queue<Future<Message<HttpResponse, String>>> queue = new LinkedList<>();
        for (int i = 0; i < REQ_NUM; i++) {
            final BasicHttpRequest request = BasicRequestBuilder.get()
                    .setHttpHost(target)
                    .setPath("/hello")
                    .build();
            queue.add(streamEndpoint.execute(
                    new BasicRequestProducer(request, null),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null));
        }
        while (!queue.isEmpty()) {
            final Future<Message<HttpResponse, String>> future = queue.remove();
            final Message<HttpResponse, String> result = future.get(LONG_TIMEOUT.getDuration(), LONG_TIMEOUT.getTimeUnit());
            Assertions.assertNotNull(result);
            final HttpResponse response = result.getHead();
            Assertions.assertNotNull(response);
            Assertions.assertEquals(200, response.getCode());
            Assertions.assertTrue(result.getBody().startsWith("All is well"));
        }
    }

    @Test
    void testDelayedResponseSubmissionNoResponseBody() throws Exception {
        final HttpTestServer server = server();
        final HttpTestClient client = client();

        server.register("/hello", () -> new AbstractServerExchangeHandler<Message<HttpRequest, String>>() {

            private final Random random = new Random(System.currentTimeMillis());

            @Override
            protected AsyncRequestConsumer<Message<HttpRequest, String>> supplyConsumer(
                    final HttpRequest request,
                    final EntityDetails entityDetails,
                    final HttpContext context) throws HttpException {
                return new BasicRequestConsumer<>(entityDetails != null ? new StringAsyncEntityConsumer() : null);
            }

            @Override
            protected void handle(
                    final Message<HttpRequest, String> requestMessage,
                    final AsyncServerRequestHandler.ResponseTrigger responseTrigger,
                    final HttpContext context) throws HttpException, IOException {
                executorResource.getExecutorService().execute(() -> {
                    try {
                        Thread.sleep(random.nextInt(200));
                        responseTrigger.submitResponse(AsyncResponseBuilder.create(200)
                                        .build(),
                                context);
                        Thread.sleep(random.nextInt(200));
                    } catch (final Exception ignore) {
                        // ignore
                    }
                });

            }

        });
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();

        final Future<ClientSessionEndpoint> connectFuture = client.connect(
                "localhost", serverEndpoint.getPort(), TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final Queue<Future<Message<HttpResponse, String>>> queue = new LinkedList<>();
        for (int i = 0; i < REQ_NUM; i++) {
            final BasicHttpRequest request = BasicRequestBuilder.get()
                    .setHttpHost(target)
                    .setPath("/hello")
                    .build();
            queue.add(streamEndpoint.execute(
                    new BasicRequestProducer(request, null),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null));
        }
        while (!queue.isEmpty()) {
            final Future<Message<HttpResponse, String>> future = queue.remove();
            final Message<HttpResponse, String> result = future.get(LONG_TIMEOUT.getDuration(), LONG_TIMEOUT.getTimeUnit());
            Assertions.assertNotNull(result);
            final HttpResponse response = result.getHead();
            Assertions.assertNotNull(response);
            Assertions.assertEquals(200, response.getCode());
        }
    }

    void testHeaderTooLarge(final String method) throws Exception {
        final HttpTestServer server = server();
        final HttpTestClient client = client();

        server.register("/hello", () -> new SingleLineResponseHandler("Hi there"));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();

        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final int n = 1000;
        final StringBuilder buf = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            buf.append('a' + i % 10);
        }
        final String s = buf.toString();

        final BasicHttpRequest request = BasicRequestBuilder.create(method)
                .setHttpHost(target)
                .setPath("/hello")
                .setHeader("big-f-header", s)
                .build();

        final AsyncEntityProducer entityProducer;
        if (Method.POST.isSame(method)) {
            entityProducer = AsyncEntityProducers.create(s, ContentType.TEXT_PLAIN);
        } else {
            entityProducer = null;
        }

        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                new BasicRequestProducer(request, entityProducer),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> result1 = future1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(result1);
        final HttpResponse response1 = result1.getHead();
        Assertions.assertNotNull(response1);
        Assertions.assertEquals(431, response1.getCode());
        Assertions.assertTrue(result1.getBody().contains("Maximum") && result1.getBody().contains("exceeded"));
    }

}
