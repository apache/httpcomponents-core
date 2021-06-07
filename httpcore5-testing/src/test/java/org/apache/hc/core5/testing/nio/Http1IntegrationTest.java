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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.ContentLengthStrategy;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.MalformedChunkCodingException;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.BasicHttpTransportMetrics;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.http.impl.Http1StreamListener;
import org.apache.hc.core5.http.impl.HttpProcessors;
import org.apache.hc.core5.http.impl.nio.AbstractContentEncoder;
import org.apache.hc.core5.http.impl.nio.ServerHttp1StreamDuplexer;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.AsyncRequestConsumer;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.AsyncResponseProducer;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncServerRequestHandler;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.ContentEncoder;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.nio.NHttpMessageParser;
import org.apache.hc.core5.http.nio.NHttpMessageWriter;
import org.apache.hc.core5.http.nio.ResponseChannel;
import org.apache.hc.core5.http.nio.SessionOutputBuffer;
import org.apache.hc.core5.http.nio.entity.AsyncEntityProducers;
import org.apache.hc.core5.http.nio.entity.DigestingEntityConsumer;
import org.apache.hc.core5.http.nio.entity.DigestingEntityProducer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityProducer;
import org.apache.hc.core5.http.nio.support.AbstractServerExchangeHandler;
import org.apache.hc.core5.http.nio.support.AsyncRequestBuilder;
import org.apache.hc.core5.http.nio.support.BasicAsyncServerExpectationDecorator;
import org.apache.hc.core5.http.nio.support.BasicRequestConsumer;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.http.nio.support.BasicResponseProducer;
import org.apache.hc.core5.http.nio.support.classic.AbstractClassicEntityConsumer;
import org.apache.hc.core5.http.nio.support.classic.AbstractClassicEntityProducer;
import org.apache.hc.core5.http.nio.support.classic.AbstractClassicServerExchangeHandler;
import org.apache.hc.core5.http.protocol.DefaultHttpProcessor;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.RequestConnControl;
import org.apache.hc.core5.http.protocol.RequestContent;
import org.apache.hc.core5.http.protocol.RequestTargetHost;
import org.apache.hc.core5.http.protocol.RequestValidateHost;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.testing.SSLTestContexts;
import org.apache.hc.core5.util.CharArrayBuffer;
import org.apache.hc.core5.util.TextUtils;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(Parameterized.class)
public class Http1IntegrationTest extends InternalHttp1ServerTestBase {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> protocols() {
        return Arrays.asList(new Object[][]{
                { URIScheme.HTTP },
                { URIScheme.HTTPS }
        });
    }

    public Http1IntegrationTest(final URIScheme scheme) {
        super(scheme);
    }

    private static final Timeout TIMEOUT = Timeout.ofSeconds(30);
    private static final Timeout LONG_TIMEOUT = Timeout.ofSeconds(60);

    private Http1TestClient client;

    @Before
    public void setup() throws Exception {
        log.debug("Starting up test client");
        client = new Http1TestClient(
                buildReactorConfig(),
                scheme == URIScheme.HTTPS ? SSLTestContexts.createClientSSLContext() : null, null, null);
    }

    protected IOReactorConfig buildReactorConfig() {
        return IOReactorConfig.DEFAULT;
    }

    @After
    public void cleanup() throws Exception {
        log.debug("Shutting down test client");
        if (client != null) {
            client.shutdown(TimeValue.ofSeconds(5));
        }
    }

    private URI createRequestURI(final InetSocketAddress serverEndpoint, final String path) {
        try {
            return new URI(scheme.id, null, "localhost", serverEndpoint.getPort(), path, null, null);
        } catch (final URISyntaxException e) {
            throw new IllegalStateException();
        }
    }

    @Test
    public void testSimpleGet() throws Exception {
        server.register("/hello", () -> new SingleLineResponseHandler("Hi there"));
        final InetSocketAddress serverEndpoint = server.start();

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(
                "localhost", serverEndpoint.getPort(), TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        for (int i = 0; i < 5; i++) {
            final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                    new BasicRequestProducer(Method.GET, createRequestURI(serverEndpoint, "/hello")),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
            final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assert.assertNotNull(result);
            final HttpResponse response1 = result.getHead();
            final String entity1 = result.getBody();
            Assert.assertNotNull(response1);
            Assert.assertEquals(200, response1.getCode());
            Assert.assertEquals("Hi there", entity1);
        }
    }

    @Test
    public void testSimpleGetConnectionClose() throws Exception {
        server.register("/hello", () -> new SingleLineResponseHandler("Hi there"));
        final InetSocketAddress serverEndpoint = server.start();

        client.start();
        final URI requestURI = createRequestURI(serverEndpoint, "/hello");
        for (int i = 0; i < 5; i++) {
            final Future<ClientSessionEndpoint> connectFuture = client.connect(
                    "localhost", serverEndpoint.getPort(), TIMEOUT);
            try (final ClientSessionEndpoint streamEndpoint = connectFuture.get()) {
                final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                        AsyncRequestBuilder.get(requestURI)
                                .addHeader(HttpHeaders.CONNECTION, "close")
                                .build(),
                        new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
                final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
                Assert.assertNotNull(result);
                final HttpResponse response1 = result.getHead();
                final String entity1 = result.getBody();
                Assert.assertNotNull(response1);
                Assert.assertEquals(200, response1.getCode());
                Assert.assertEquals("Hi there", entity1);
            }
        }
    }

    @Test
    public void testSimpleGetIdentityTransfer() throws Exception {
        server.register("/hello", () -> new SingleLineResponseHandler("Hi there"));
        final HttpProcessor httpProcessor = new DefaultHttpProcessor(new RequestValidateHost());
        final InetSocketAddress serverEndpoint = server.start(httpProcessor, Http1Config.DEFAULT);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect("localhost", serverEndpoint.getPort(), TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                new BasicRequestProducer(Method.GET, createRequestURI(serverEndpoint, "/hello")),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assert.assertNotNull(result);
        final HttpResponse response = result.getHead();
        final String entity = result.getBody();
        Assert.assertNotNull(response);
        Assert.assertEquals(200, response.getCode());
        Assert.assertEquals("Hi there", entity);
    }

    @Test
    public void testSimpleGetsPipelined() throws Exception {
        server.register("/hello", () -> new SingleLineResponseHandler("Hi there"));
        final InetSocketAddress serverEndpoint = server.start();

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(
                "localhost", serverEndpoint.getPort(), TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final Queue<Future<Message<HttpResponse, String>>> queue = new LinkedList<>();
        for (int i = 0; i < 5; i++) {
            queue.add(streamEndpoint.execute(
                    new BasicRequestProducer(Method.GET, createRequestURI(serverEndpoint, "/hello")),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null));
        }
        while (!queue.isEmpty()) {
            final Future<Message<HttpResponse, String>> future = queue.remove();
            final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assert.assertNotNull(result);
            final HttpResponse response = result.getHead();
            final String entity = result.getBody();
            Assert.assertNotNull(response);
            Assert.assertEquals(200, response.getCode());
            Assert.assertEquals("Hi there", entity);
        }
    }

    @Test
    public void testLargeGet() throws Exception {
        server.register("/", () -> new MultiLineResponseHandler("0123456789abcdef", 5000));
        final InetSocketAddress serverEndpoint = server.start();

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(
                "localhost", serverEndpoint.getPort(), TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                new BasicRequestProducer(Method.GET, createRequestURI(serverEndpoint, "/")),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);

        final Message<HttpResponse, String> result1 = future1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assert.assertNotNull(result1);
        final HttpResponse response1 = result1.getHead();
        Assert.assertNotNull(response1);
        Assert.assertEquals(200, response1.getCode());
        final String s1 = result1.getBody();
        Assert.assertNotNull(s1);
        final StringTokenizer t1 = new StringTokenizer(s1, "\r\n");
        while (t1.hasMoreTokens()) {
            Assert.assertEquals("0123456789abcdef", t1.nextToken());
        }

        final Future<Message<HttpResponse, String>> future2 = streamEndpoint.execute(
                new BasicRequestProducer(Method.GET, createRequestURI(serverEndpoint, "/")),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer(512)), null);

        final Message<HttpResponse, String> result2 = future2.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assert.assertNotNull(result2);
        final HttpResponse response2 = result2.getHead();
        Assert.assertNotNull(response2);
        Assert.assertEquals(200, response2.getCode());
        final String s2 = result2.getBody();
        Assert.assertNotNull(s2);
        final StringTokenizer t2 = new StringTokenizer(s2, "\r\n");
        while (t2.hasMoreTokens()) {
            Assert.assertEquals("0123456789abcdef", t2.nextToken());
        }
    }

    @Test
    public void testLargeGetsPipelined() throws Exception {
        server.register("/", () -> new MultiLineResponseHandler("0123456789abcdef", 2000));
        final InetSocketAddress serverEndpoint = server.start();

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(
                "localhost", serverEndpoint.getPort(), TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final Queue<Future<Message<HttpResponse, String>>> queue = new LinkedList<>();
        for (int i = 0; i < 5; i++) {
            queue.add(streamEndpoint.execute(
                    new BasicRequestProducer(Method.GET, createRequestURI(serverEndpoint, "/")),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null));
        }
        while (!queue.isEmpty()) {
            final Future<Message<HttpResponse, String>> future = queue.remove();
            final Message<HttpResponse, String> result = future.get(LONG_TIMEOUT.getDuration(), LONG_TIMEOUT.getTimeUnit());
            Assert.assertNotNull(result);
            final HttpResponse response = result.getHead();
            Assert.assertNotNull(response);
            Assert.assertEquals(200, response.getCode());
            final String entity = result.getBody();
            Assert.assertNotNull(entity);
            final StringTokenizer t = new StringTokenizer(entity, "\r\n");
            while (t.hasMoreTokens()) {
                Assert.assertEquals("0123456789abcdef", t.nextToken());
            }
        }
    }

    @Test
    public void testBasicPost() throws Exception {
        server.register("/hello", () -> new SingleLineResponseHandler("Hi back"));
        final InetSocketAddress serverEndpoint = server.start();

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(
                "localhost", serverEndpoint.getPort(), TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        for (int i = 0; i < 5; i++) {
            final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                    new BasicRequestProducer(Method.POST, createRequestURI(serverEndpoint, "/hello"),
                            AsyncEntityProducers.create("Hi there")),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
            final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assert.assertNotNull(result);
            final HttpResponse response1 = result.getHead();
            final String entity1 = result.getBody();
            Assert.assertNotNull(response1);
            Assert.assertEquals(200, response1.getCode());
            Assert.assertEquals("Hi back", entity1);
        }
    }

    @Test
    public void testBasicPostPipelined() throws Exception {
        server.register("/hello", () -> new SingleLineResponseHandler("Hi back"));
        final InetSocketAddress serverEndpoint = server.start();

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(
                "localhost", serverEndpoint.getPort(), TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final Queue<Future<Message<HttpResponse, String>>> queue = new LinkedList<>();
        for (int i = 0; i < 5; i++) {
            queue.add(streamEndpoint.execute(
                    new BasicRequestProducer(Method.POST, createRequestURI(serverEndpoint, "/hello"),
                            AsyncEntityProducers.create("Hi there")),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null));
        }
        while (!queue.isEmpty()) {
            final Future<Message<HttpResponse, String>> future = queue.remove();
            final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assert.assertNotNull(result);
            final HttpResponse response = result.getHead();
            final String entity = result.getBody();
            Assert.assertNotNull(response);
            Assert.assertEquals(200, response.getCode());
            Assert.assertEquals("Hi back", entity);
        }
    }

    @Test
    public void testHttp10Post() throws Exception {
        server.register("/hello", () -> new SingleLineResponseHandler("Hi back"));
        final InetSocketAddress serverEndpoint = server.start();

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(
                "localhost", serverEndpoint.getPort(), TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        for (int i = 0; i < 5; i++) {
            final HttpRequest request = new BasicHttpRequest(Method.POST, createRequestURI(serverEndpoint, "/hello"));
            request.setVersion(HttpVersion.HTTP_1_0);
            final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                    new BasicRequestProducer(request, AsyncEntityProducers.create("Hi there")),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
            final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assert.assertNotNull(result);
            final HttpResponse response1 = result.getHead();
            final String entity1 = result.getBody();
            Assert.assertNotNull(response1);
            Assert.assertEquals(200, response1.getCode());
            Assert.assertEquals("Hi back", entity1);
        }
    }

    @Test
    public void testNoEntityPost() throws Exception {
        server.register("/hello", () -> new SingleLineResponseHandler("Hi back"));
        final InetSocketAddress serverEndpoint = server.start();

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(
                "localhost", serverEndpoint.getPort(), TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        for (int i = 0; i < 5; i++) {
            final HttpRequest request = new BasicHttpRequest(Method.POST, createRequestURI(serverEndpoint, "/hello"));
            final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                    new BasicRequestProducer(request, null),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
            final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assert.assertNotNull(result);
            final HttpResponse response1 = result.getHead();
            final String entity1 = result.getBody();
            Assert.assertNotNull(response1);
            Assert.assertEquals(200, response1.getCode());
            Assert.assertEquals("Hi back", entity1);
        }
    }

    @Test
    public void testLargePost() throws Exception {
        server.register("*", () -> new EchoHandler(2048));
        final InetSocketAddress serverEndpoint = server.start();

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(
                "localhost", serverEndpoint.getPort(), TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        for (int i = 0; i < 5; i++) {
            final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                    new BasicRequestProducer(Method.POST, createRequestURI(serverEndpoint, "/echo"),
                            new MultiLineEntityProducer("0123456789abcdef", 5000)),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
            final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assert.assertNotNull(result);
            final HttpResponse response = result.getHead();
            Assert.assertNotNull(response);
            Assert.assertEquals(200, response.getCode());
            final String entity = result.getBody();
            Assert.assertNotNull(entity);
            final StringTokenizer t = new StringTokenizer(entity, "\r\n");
            while (t.hasMoreTokens()) {
                Assert.assertEquals("0123456789abcdef", t.nextToken());
            }
        }
    }

    @Test
    public void testPostsPipelinedLargeResponse() throws Exception {
        server.register("/", () -> new MultiLineResponseHandler("0123456789abcdef", 2000));
        final InetSocketAddress serverEndpoint = server.start();

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(
                "localhost", serverEndpoint.getPort(), TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final Queue<Future<Message<HttpResponse, String>>> queue = new LinkedList<>();
        for (int i = 0; i < 2; i++) {
            queue.add(streamEndpoint.execute(
                    new BasicRequestProducer(Method.POST, createRequestURI(serverEndpoint, "/"),
                            AsyncEntityProducers.create("Hi there")),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null));
        }
        while (!queue.isEmpty()) {
            final Future<Message<HttpResponse, String>> future = queue.remove();
            final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assert.assertNotNull(result);
            final HttpResponse response = result.getHead();
            Assert.assertNotNull(response);
            Assert.assertEquals(200, response.getCode());
            final String entity = result.getBody();
            Assert.assertNotNull(entity);
            final StringTokenizer t = new StringTokenizer(entity, "\r\n");
            while (t.hasMoreTokens()) {
                Assert.assertEquals("0123456789abcdef", t.nextToken());
            }
        }
    }


    @Test
    public void testLargePostsPipelined() throws Exception {
        server.register("*", () -> new EchoHandler(2048));
        final InetSocketAddress serverEndpoint = server.start();

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(
                "localhost", serverEndpoint.getPort(), TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final Queue<Future<Message<HttpResponse, String>>> queue = new LinkedList<>();
        for (int i = 0; i < 5; i++) {
            queue.add(streamEndpoint.execute(
                    new BasicRequestProducer(Method.POST, createRequestURI(serverEndpoint, "/echo"),
                            new MultiLineEntityProducer("0123456789abcdef", 5000)),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null));
        }
        while (!queue.isEmpty()) {
            final Future<Message<HttpResponse, String>> future = queue.remove();
            final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assert.assertNotNull(result);
            final HttpResponse response = result.getHead();
            Assert.assertNotNull(response);
            Assert.assertEquals(200, response.getCode());
            final String entity = result.getBody();
            Assert.assertNotNull(entity);
            final StringTokenizer t = new StringTokenizer(entity, "\r\n");
            while (t.hasMoreTokens()) {
                Assert.assertEquals("0123456789abcdef", t.nextToken());
            }
        }
    }

    @Test
    public void testSimpleHead() throws Exception {
        server.register("/hello", () -> new SingleLineResponseHandler("Hi there"));
        final InetSocketAddress serverEndpoint = server.start();

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(
                "localhost", serverEndpoint.getPort(), TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        for (int i = 0; i < 5; i++) {
            final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                    new BasicRequestProducer(Method.HEAD, createRequestURI(serverEndpoint, "/hello")),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
            final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assert.assertNotNull(result);
            final HttpResponse response1 = result.getHead();
            Assert.assertNotNull(response1);
            Assert.assertEquals(200, response1.getCode());
            Assert.assertNull(result.getBody());
        }
    }

    @Test
    public void testSimpleHeadConnectionClose() throws Exception {
        server.register("/hello", () -> new SingleLineResponseHandler("Hi there"));
        final InetSocketAddress serverEndpoint = server.start();

        client.start();
        final URI requestURI = createRequestURI(serverEndpoint, "/hello");
        for (int i = 0; i < 5; i++) {
            final Future<ClientSessionEndpoint> connectFuture = client.connect(
                    "localhost", serverEndpoint.getPort(), TIMEOUT);
            try (final ClientSessionEndpoint streamEndpoint = connectFuture.get()) {
                final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                        AsyncRequestBuilder.head(requestURI)
                                .addHeader(HttpHeaders.CONNECTION, "close")
                                .build(),
                        new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
                final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
                Assert.assertNotNull(result);
                final HttpResponse response1 = result.getHead();
                Assert.assertNotNull(response1);
                Assert.assertEquals(200, response1.getCode());
                Assert.assertNull(result.getBody());
            }
        }
    }

    @Test
    public void testHeadPipelined() throws Exception {
        server.register("/hello", () -> new SingleLineResponseHandler("Hi there"));
        final InetSocketAddress serverEndpoint = server.start();

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(
                "localhost", serverEndpoint.getPort(), TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final Queue<Future<Message<HttpResponse, String>>> queue = new LinkedList<>();
        for (int i = 0; i < 5; i++) {
            queue.add(streamEndpoint.execute(
                    new BasicRequestProducer(Method.HEAD, createRequestURI(serverEndpoint, "/hello")),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null));
        }
        while (!queue.isEmpty()) {
            final Future<Message<HttpResponse, String>> future = queue.remove();
            final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assert.assertNotNull(result);
            final HttpResponse response1 = result.getHead();
            Assert.assertNotNull(response1);
            Assert.assertEquals(200, response1.getCode());
            Assert.assertNull(result.getBody());
        }
    }

    @Test
    public void testExpectationFailed() throws Exception {
        server.register("*", () -> new MessageExchangeHandler<String>(new StringAsyncEntityConsumer()) {

            @Override
            protected void handle(
                    final Message<HttpRequest, String> request,
                    final AsyncServerRequestHandler.ResponseTrigger responseTrigger,
                    final HttpContext context) throws IOException, HttpException {
                responseTrigger.submitResponse(new BasicResponseProducer(HttpStatus.SC_OK, "All is well"), context);

            }
        });
        final InetSocketAddress serverEndpoint = server.start(null, handler -> new BasicAsyncServerExpectationDecorator(handler) {

            @Override
            protected AsyncResponseProducer verify(final HttpRequest request, final HttpContext context) throws IOException, HttpException {
                final Header h = request.getFirstHeader("password");
                if (h != null && "secret".equals(h.getValue())) {
                    return null;
                } else {
                    return new BasicResponseProducer(HttpStatus.SC_UNAUTHORIZED, "You shall not pass");
                }
            }
        }, Http1Config.DEFAULT);

        client.start();
        final Future<IOSession> sessionFuture = client.requestSession(
                new HttpHost("localhost", serverEndpoint.getPort()), TIMEOUT, null);
        final IOSession ioSession = sessionFuture.get();
        final ClientSessionEndpoint streamEndpoint = new ClientSessionEndpoint(ioSession);

        final HttpRequest request1 = new BasicHttpRequest(Method.POST, createRequestURI(serverEndpoint, "/echo"));
        request1.addHeader("password", "secret");
        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                new BasicRequestProducer(request1, new MultiLineEntityProducer("0123456789abcdef", 1000)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> result1 = future1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assert.assertNotNull(result1);
        final HttpResponse response1 = result1.getHead();
        Assert.assertNotNull(response1);
        Assert.assertEquals(200, response1.getCode());
        Assert.assertNotNull("All is well", result1.getBody());

        Assert.assertTrue(ioSession.isOpen());

        final HttpRequest request2 = new BasicHttpRequest(Method.POST, createRequestURI(serverEndpoint, "/echo"));
        final Future<Message<HttpResponse, String>> future2 = streamEndpoint.execute(
                new BasicRequestProducer(request2, new MultiLineEntityProducer("0123456789abcdef", 5000)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> result2 = future2.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assert.assertNotNull(result2);
        final HttpResponse response2 = result2.getHead();
        Assert.assertNotNull(response2);
        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response2.getCode());
        Assert.assertNotNull("You shall not pass", result2.getBody());

        Assert.assertTrue(ioSession.isOpen());

        final HttpRequest request3 = new BasicHttpRequest(Method.POST, createRequestURI(serverEndpoint, "/echo"));
        request3.addHeader("password", "secret");
        final Future<Message<HttpResponse, String>> future3 = streamEndpoint.execute(
                new BasicRequestProducer(request3, new MultiLineEntityProducer("0123456789abcdef", 1000)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> result3 = future3.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assert.assertNotNull(result3);
        final HttpResponse response3 = result3.getHead();
        Assert.assertNotNull(response3);
        Assert.assertEquals(200, response3.getCode());
        Assert.assertNotNull("All is well", result3.getBody());

        Assert.assertTrue(ioSession.isOpen());

        final HttpRequest request4 = new BasicHttpRequest(Method.POST, createRequestURI(serverEndpoint, "/echo"));
        final Future<Message<HttpResponse, String>> future4 = streamEndpoint.execute(
                new BasicRequestProducer(request4, AsyncEntityProducers.create("blah")),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> result4 = future4.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assert.assertNotNull(result4);
        final HttpResponse response4 = result4.getHead();
        Assert.assertNotNull(response4);
        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response4.getCode());
        Assert.assertNotNull("You shall not pass", result4.getBody());

        Assert.assertFalse(ioSession.isOpen());
    }

    @Test
    public void testExpectationFailedCloseConnection() throws Exception {
        server.register("*", () -> new MessageExchangeHandler<String>(new StringAsyncEntityConsumer()) {

            @Override
            protected void handle(
                    final Message<HttpRequest, String> request,
                    final AsyncServerRequestHandler.ResponseTrigger responseTrigger,
                    final HttpContext context) throws IOException, HttpException {
                responseTrigger.submitResponse(new BasicResponseProducer(HttpStatus.SC_OK, "All is well"), context);

            }
        });
        final InetSocketAddress serverEndpoint = server.start(null, handler -> new BasicAsyncServerExpectationDecorator(handler) {

            @Override
            protected AsyncResponseProducer verify(final HttpRequest request, final HttpContext context) throws IOException, HttpException {
                final Header h = request.getFirstHeader("password");
                if (h != null && "secret".equals(h.getValue())) {
                    return null;
                } else {
                    final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_UNAUTHORIZED);
                    response.addHeader(HttpHeaders.CONNECTION, HeaderElements.CLOSE);
                    return new BasicResponseProducer(response, "You shall not pass");
                }
            }
        }, Http1Config.DEFAULT);

        client.start();
        final Future<IOSession> sessionFuture = client.requestSession(
                new HttpHost("localhost", serverEndpoint.getPort()), TIMEOUT, null);
        final IOSession ioSession = sessionFuture.get();
        final ClientSessionEndpoint streamEndpoint = new ClientSessionEndpoint(ioSession);

        final HttpRequest request1 = new BasicHttpRequest(Method.POST, createRequestURI(serverEndpoint, "/echo"));
        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                new BasicRequestProducer(request1, new MultiBinEntityProducer(
                        new byte[] {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'},
                        100000,
                        ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> result1 = future1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assert.assertNotNull(result1);
        final HttpResponse response1 = result1.getHead();
        Assert.assertNotNull(response1);
        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response1.getCode());
        Assert.assertNotNull("You shall not pass", result1.getBody());

        Assert.assertFalse(streamEndpoint.isOpen());
    }

    @Test
    public void testDelayedExpectationVerification() throws Exception {
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

                Executors.newSingleThreadExecutor().execute(() -> {
                    try {
                        if (entityDetails != null) {
                            final Header h = request.getFirstHeader(HttpHeaders.EXPECT);
                            if (h != null && HeaderElements.CONTINUE.equalsIgnoreCase(h.getValue())) {
                                Thread.sleep(random.nextInt(1000));
                                responseChannel.sendInformation(new BasicHttpResponse(HttpStatus.SC_CONTINUE), context);
                            }
                            final HttpResponse response = new BasicHttpResponse(200);
                            synchronized (entityProducer) {
                                responseChannel.sendResponse(response, entityProducer, context);
                            }
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
                synchronized (entityProducer) {
                    return entityProducer.available();
                }
            }

            @Override
            public void produce(final DataStreamChannel channel) throws IOException {
                synchronized (entityProducer) {
                    entityProducer.produce(channel);
                }
            }

            @Override
            public void failed(final Exception cause) {
            }

            @Override
            public void releaseResources() {
            }

        });
        final InetSocketAddress serverEndpoint = server.start();

        client.start(Http1Config.custom().setWaitForContinueTimeout(Timeout.ofMilliseconds(100)).build());
        final Future<ClientSessionEndpoint> connectFuture = client.connect(
                "localhost", serverEndpoint.getPort(), TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final Queue<Future<Message<HttpResponse, String>>> queue = new LinkedList<>();
        for (int i = 0; i < 5; i++) {
            queue.add(streamEndpoint.execute(
                    new BasicRequestProducer(Method.POST, createRequestURI(serverEndpoint, "/"),
                            AsyncEntityProducers.create("Some important message")),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null));
        }
        while (!queue.isEmpty()) {
            final Future<Message<HttpResponse, String>> future = queue.remove();
            final Message<HttpResponse, String> result = future.get(LONG_TIMEOUT.getDuration(), LONG_TIMEOUT.getTimeUnit());
            Assert.assertNotNull(result);
            final HttpResponse response = result.getHead();
            Assert.assertNotNull(response);
            Assert.assertEquals(200, response.getCode());
            Assert.assertNotNull("All is well", result.getBody());
        }
    }

    @Test
    public void testPrematureResponse() throws Exception {
        server.register("*", () -> new AsyncServerExchangeHandler() {

            private final AtomicReference<AsyncResponseProducer> responseProducer = new AtomicReference<>(null);

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

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(
                "localhost", serverEndpoint.getPort(), TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        for (int i = 0; i < 3; i++) {
            final HttpRequest request1 = new BasicHttpRequest(Method.POST, createRequestURI(serverEndpoint, "/echo"));
            final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                    new BasicRequestProducer(request1, new MultiLineEntityProducer("0123456789abcdef", 100000)),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
            final Message<HttpResponse, String> result1 = future1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assert.assertNotNull(result1);
            final HttpResponse response1 = result1.getHead();
            Assert.assertNotNull(response1);
            Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response1.getCode());
            Assert.assertNotNull("You shall not pass", result1.getBody());

            Assert.assertTrue(streamEndpoint.isOpen());
        }
        final HttpRequest request1 = new BasicHttpRequest(Method.POST, createRequestURI(serverEndpoint, "/echo"));
        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                new BasicRequestProducer(request1, new MultiBinEntityProducer(
                        new byte[] {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'},
                        100000,
                        ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> result1 = future1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assert.assertNotNull(result1);
        final HttpResponse response1 = result1.getHead();
        Assert.assertNotNull(response1);
        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response1.getCode());
        Assert.assertNotNull("You shall not pass", result1.getBody());

        Assert.assertFalse(streamEndpoint.isOpen());
    }

    @Test
    public void testSlowResponseConsumer() throws Exception {
        server.register("/", () -> new MultiLineResponseHandler("0123456789abcd", 100));
        final InetSocketAddress serverEndpoint = server.start();

        client.start(Http1Config.custom().setBufferSize(256).build());

        final Future<ClientSessionEndpoint> connectFuture = client.connect(
                "localhost", serverEndpoint.getPort(), TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final HttpRequest request1 = new BasicHttpRequest(Method.GET, createRequestURI(serverEndpoint, "/"));
        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                new BasicRequestProducer(request1, null),
                new BasicResponseConsumer<>(new AbstractClassicEntityConsumer<String>(16, Executors.newSingleThreadExecutor()) {

                    @Override
                    protected String consumeData(
                            final ContentType contentType, final InputStream inputStream) throws IOException {
                        Charset charset = contentType != null ? contentType.getCharset() : null;
                        if (charset == null) {
                            charset = StandardCharsets.US_ASCII;
                        }

                        final StringBuilder buffer = new StringBuilder();
                        try {
                            final byte[] tmp = new byte[16];
                            int l;
                            while ((l = inputStream.read(tmp)) != -1) {
                                buffer.append(charset.decode(ByteBuffer.wrap(tmp, 0, l)));
                                Thread.sleep(50);
                            }
                        } catch (final InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            throw new InterruptedIOException(ex.getMessage());
                        }
                        return buffer.toString();
                    }
                }),
                null);

        final Message<HttpResponse, String> result1 = future1.get(LONG_TIMEOUT.getDuration(), LONG_TIMEOUT.getTimeUnit());
        Assert.assertNotNull(result1);
        final HttpResponse response1 = result1.getHead();
        Assert.assertNotNull(response1);
        Assert.assertEquals(200, response1.getCode());
        final String s1 = result1.getBody();
        Assert.assertNotNull(s1);
        final StringTokenizer t1 = new StringTokenizer(s1, "\r\n");
        while (t1.hasMoreTokens()) {
            Assert.assertEquals("0123456789abcd", t1.nextToken());
        }
    }

    @Test
    public void testSlowRequestProducer() throws Exception {
        server.register("*", () -> new EchoHandler(2048));
        final InetSocketAddress serverEndpoint = server.start();

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(
                "localhost", serverEndpoint.getPort(), TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final HttpRequest request1 = new BasicHttpRequest(Method.POST, createRequestURI(serverEndpoint, "/echo"));
        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                new BasicRequestProducer(request1, new AbstractClassicEntityProducer(4096, ContentType.TEXT_PLAIN, Executors.newSingleThreadExecutor()) {

                    @Override
                    protected void produceData(final ContentType contentType, final OutputStream outputStream) throws IOException {
                        Charset charset = contentType.getCharset();
                        if (charset == null) {
                            charset = StandardCharsets.US_ASCII;
                        }
                        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, charset))) {
                            for (int i = 0; i < 500; i++) {
                                if (i % 100 == 0) {
                                    writer.flush();
                                    Thread.sleep(500);
                                }
                                writer.write("0123456789abcdef\r\n");
                            }
                        } catch (final InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            throw new InterruptedIOException(ex.getMessage());
                        }
                    }

                }),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> result1 = future1.get(LONG_TIMEOUT.getDuration(), LONG_TIMEOUT.getTimeUnit());
        Assert.assertNotNull(result1);
        final HttpResponse response1 = result1.getHead();
        Assert.assertNotNull(response1);
        Assert.assertEquals(200, response1.getCode());
        final String s1 = result1.getBody();
        Assert.assertNotNull(s1);
        final StringTokenizer t1 = new StringTokenizer(s1, "\r\n");
        while (t1.hasMoreTokens()) {
            Assert.assertEquals("0123456789abcdef", t1.nextToken());
        }
    }

    @Test
    public void testSlowResponseProducer() throws Exception {
        server.register("*", () -> new AbstractClassicServerExchangeHandler(2048, Executors.newSingleThreadExecutor()) {

            @Override
            protected void handle(
                    final HttpRequest request,
                    final InputStream requestStream,
                    final HttpResponse response,
                    final OutputStream responseStream,
                    final HttpContext context) throws IOException, HttpException {

                if (!"/hello".equals(request.getPath())) {
                    response.setCode(HttpStatus.SC_NOT_FOUND);
                    return;
                }
                if (!Method.POST.name().equalsIgnoreCase(request.getMethod())) {
                    response.setCode(HttpStatus.SC_NOT_IMPLEMENTED);
                    return;
                }
                if (requestStream == null) {
                    return;
                }
                final Header h1 = request.getFirstHeader(HttpHeaders.CONTENT_TYPE);
                final ContentType contentType = h1 != null ? ContentType.parse(h1.getValue()) : null;
                Charset charset = contentType != null ? contentType.getCharset() : null;
                if (charset == null) {
                    charset = StandardCharsets.US_ASCII;
                }
                response.setCode(HttpStatus.SC_OK);
                response.setHeader(h1);
                try (final BufferedReader reader = new BufferedReader(new InputStreamReader(requestStream, charset));
                     final BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(responseStream, charset))) {
                    try {
                        String l;
                        int count = 0;
                        while ((l = reader.readLine()) != null) {
                            writer.write(l);
                            writer.write("\r\n");
                            count++;
                            if (count % 500 == 0) {
                                Thread.sleep(500);
                            }
                        }
                        writer.flush();
                    } catch (final InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new InterruptedIOException(ex.getMessage());
                    }
                }
            }
        });
        final InetSocketAddress serverEndpoint = server.start();

        client.start(Http1Config.custom().setBufferSize(256).build());

        final Future<ClientSessionEndpoint> connectFuture = client.connect(
                "localhost", serverEndpoint.getPort(), TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final HttpRequest request1 = new BasicHttpRequest(Method.POST, createRequestURI(serverEndpoint, "/hello"));
        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                new BasicRequestProducer(request1, new MultiLineEntityProducer("0123456789abcd", 2000)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> result1 = future1.get(LONG_TIMEOUT.getDuration(), LONG_TIMEOUT.getTimeUnit());
        Assert.assertNotNull(result1);
        final HttpResponse response1 = result1.getHead();
        Assert.assertNotNull(response1);
        Assert.assertEquals(200, response1.getCode());
        final String s1 = result1.getBody();
        Assert.assertNotNull(s1);
        final StringTokenizer t1 = new StringTokenizer(s1, "\r\n");
        while (t1.hasMoreTokens()) {
            Assert.assertEquals("0123456789abcd", t1.nextToken());
        }
    }

    @Test
    public void testPipelinedConnectionClose() throws Exception {
        server.register("/hello*", () -> new SingleLineResponseHandler("Hi back"));
        final InetSocketAddress serverEndpoint = server.start();

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(
                "localhost", serverEndpoint.getPort(), TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                new BasicRequestProducer(Method.POST, createRequestURI(serverEndpoint, "/hello-1"),
                        AsyncEntityProducers.create("Hi there")),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final HttpRequest request2 = new BasicHttpRequest(Method.POST, createRequestURI(serverEndpoint, "/hello-2"));
        request2.addHeader(HttpHeaders.CONNECTION, "close");
        final Future<Message<HttpResponse, String>> future2 = streamEndpoint.execute(
                new BasicRequestProducer(request2,
                        AsyncEntityProducers.create("Hi there")),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Future<Message<HttpResponse, String>> future3 = streamEndpoint.execute(
                new BasicRequestProducer(Method.POST, createRequestURI(serverEndpoint, "/hello-3"),
                        AsyncEntityProducers.create("Hi there")),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);

        final Message<HttpResponse, String> result1 = future1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assert.assertNotNull(result1);
        final HttpResponse response1 = result1.getHead();
        final String entity1 = result1.getBody();
        Assert.assertNotNull(response1);
        Assert.assertEquals(200, response1.getCode());
        Assert.assertEquals("Hi back", entity1);

        final Message<HttpResponse, String> result2 = future2.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assert.assertNotNull(result2);
        final HttpResponse response2 = result2.getHead();
        final String entity2 = result2.getBody();
        Assert.assertNotNull(response2);
        Assert.assertEquals(200, response2.getCode());
        Assert.assertEquals("Hi back", entity2);

        final Exception exception = Assert.assertThrows(Exception.class, () ->
                future3.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()));
        MatcherAssert.assertThat(exception, CoreMatchers.anyOf(
                CoreMatchers.instanceOf(CancellationException.class),
                CoreMatchers.instanceOf(ExecutionException.class)));

        final Future<Message<HttpResponse, String>> future4 = streamEndpoint.execute(
                new BasicRequestProducer(Method.POST, createRequestURI(serverEndpoint, "/hello-3"),
                        AsyncEntityProducers.create("Hi there")),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Exception exception2 = Assert.assertThrows(Exception.class, () ->
                future4.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()));
        MatcherAssert.assertThat(exception2, CoreMatchers.anyOf(
                CoreMatchers.instanceOf(CancellationException.class),
                CoreMatchers.instanceOf(ExecutionException.class)));
    }

    @Test
    public void testPipelinedInvalidRequest() throws Exception {
        server.register("/hello*", () -> new SingleLineResponseHandler("Hi back"));
        final InetSocketAddress serverEndpoint = server.start();

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(
                "localhost", serverEndpoint.getPort(), TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                new BasicRequestProducer(Method.POST, createRequestURI(serverEndpoint, "/hello-1"),
                        AsyncEntityProducers.create("Hi there")),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final HttpRequest request2 = new BasicHttpRequest(Method.POST, createRequestURI(serverEndpoint, "/hello-2"));
        request2.addHeader(HttpHeaders.HOST, "blah:blah");
        final Future<Message<HttpResponse, String>> future2 = streamEndpoint.execute(
                new BasicRequestProducer(request2,
                        AsyncEntityProducers.create("Hi there")),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Future<Message<HttpResponse, String>> future3 = streamEndpoint.execute(
                new BasicRequestProducer(Method.POST, createRequestURI(serverEndpoint, "/hello-3"),
                        AsyncEntityProducers.create("Hi there")),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);

        final Message<HttpResponse, String> result1 = future1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assert.assertNotNull(result1);
        final HttpResponse response1 = result1.getHead();
        final String entity1 = result1.getBody();
        Assert.assertNotNull(response1);
        Assert.assertEquals(200, response1.getCode());
        Assert.assertEquals("Hi back", entity1);

        final Message<HttpResponse, String> result2 = future2.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assert.assertNotNull(result2);
        final HttpResponse response2 = result2.getHead();
        final String entity2 = result2.getBody();
        Assert.assertNotNull(response2);
        Assert.assertEquals(400, response2.getCode());
        Assert.assertTrue(entity2.length() > 0);


        final Exception exception = Assert.assertThrows(Exception.class, () ->
                future3.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()));
        MatcherAssert.assertThat(exception, CoreMatchers.anyOf(
                CoreMatchers.instanceOf(CancellationException.class),
                CoreMatchers.instanceOf(ExecutionException.class)));
    }

    private static final byte[] GARBAGE = "garbage".getBytes(StandardCharsets.US_ASCII);

    private static class BrokenChunkEncoder extends AbstractContentEncoder {

        private final CharArrayBuffer lineBuffer;
        private boolean done;

        BrokenChunkEncoder(
                final WritableByteChannel channel,
                final SessionOutputBuffer buffer,
                final BasicHttpTransportMetrics metrics) {
            super(channel, buffer, metrics);
            lineBuffer = new CharArrayBuffer(16);
        }

        @Override
        public void complete(final List<? extends Header> trailers) throws IOException {
            super.complete(trailers);
        }

        @Override
        public int write(final ByteBuffer src) throws IOException {
            final int chunk;
            if (!done) {
                lineBuffer.clear();
                lineBuffer.append(Integer.toHexString(GARBAGE.length * 10));
                buffer().writeLine(lineBuffer);
                buffer().write(ByteBuffer.wrap(GARBAGE));
                done = true;
                chunk = GARBAGE.length;
            } else {
                chunk = 0;
            }
            final long bytesWritten = buffer().flush(channel());
            if (bytesWritten > 0) {
                metrics().incrementBytesTransferred(bytesWritten);
            }
            if (!buffer().hasData()) {
                channel().close();
            }
            return chunk;
        }

    }

    @Test
    public void testTruncatedChunk() throws Exception {
        final InetSocketAddress serverEndpoint = server.start(new InternalServerHttp1EventHandlerFactory(
                HttpProcessors.server(),
                (request, context) -> new MessageExchangeHandler<String>(new StringAsyncEntityConsumer()) {

                    @Override
                    protected void handle(
                            final Message<HttpRequest, String> request,
                            final AsyncServerRequestHandler.ResponseTrigger responseTrigger,
                            final HttpContext context) throws IOException, HttpException {
                        responseTrigger.submitResponse(
                                new BasicResponseProducer(new StringAsyncEntityProducer("useful stuff")), context);
                    }

                },
                Http1Config.DEFAULT,
                CharCodingConfig.DEFAULT,
                DefaultConnectionReuseStrategy.INSTANCE,
                scheme == URIScheme.HTTPS ? SSLTestContexts.createServerSSLContext() : null, null, null) {

            @Override
            protected ServerHttp1StreamDuplexer createServerHttp1StreamDuplexer(
                    final ProtocolIOSession ioSession,
                    final HttpProcessor httpProcessor,
                    final HandlerFactory<AsyncServerExchangeHandler> exchangeHandlerFactory,
                    final Http1Config http1Config,
                    final CharCodingConfig connectionConfig,
                    final ConnectionReuseStrategy connectionReuseStrategy,
                    final NHttpMessageParser<HttpRequest> incomingMessageParser,
                    final NHttpMessageWriter<HttpResponse> outgoingMessageWriter,
                    final ContentLengthStrategy incomingContentStrategy,
                    final ContentLengthStrategy outgoingContentStrategy,
                    final Http1StreamListener streamListener) {
                return new ServerHttp1StreamDuplexer(ioSession, httpProcessor, exchangeHandlerFactory,
                        scheme.id,
                        http1Config, connectionConfig, connectionReuseStrategy,
                        incomingMessageParser, outgoingMessageWriter,
                        incomingContentStrategy, outgoingContentStrategy,
                        streamListener) {

                    @Override
                    protected ContentEncoder createContentEncoder(
                            final long len,
                            final WritableByteChannel channel,
                            final SessionOutputBuffer buffer,
                            final BasicHttpTransportMetrics metrics) throws HttpException {
                        if (len == ContentLengthStrategy.CHUNKED) {
                            return new BrokenChunkEncoder(channel, buffer, metrics);
                        } else {
                            return super.createContentEncoder(len, channel, buffer, metrics);
                        }
                    }

                };
            }

        });

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(
                "localhost", serverEndpoint.getPort(), TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final AsyncRequestProducer requestProducer = new BasicRequestProducer(Method.GET, createRequestURI(serverEndpoint, "/hello"));
        final StringAsyncEntityConsumer entityConsumer = new StringAsyncEntityConsumer() {

            @Override
            public void releaseResources() {
                // Do not clear internal content buffer
            }

        };
        final BasicResponseConsumer<String> responseConsumer = new BasicResponseConsumer<>(entityConsumer);
        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(requestProducer, responseConsumer, null);
        final ExecutionException exception = Assert.assertThrows(ExecutionException.class, () ->
                future1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()));
        final Throwable cause = exception.getCause();
        Assert.assertTrue(cause instanceof MalformedChunkCodingException);
        Assert.assertEquals("garbage", entityConsumer.generateContent());
    }

    @Test
    public void testExceptionInHandler() throws Exception {
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

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(
                "localhost", serverEndpoint.getPort(), TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                new BasicRequestProducer(Method.GET, createRequestURI(serverEndpoint, "/hello")),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assert.assertNotNull(result);
        final HttpResponse response1 = result.getHead();
        final String entity1 = result.getBody();
        Assert.assertNotNull(response1);
        Assert.assertEquals(500, response1.getCode());
        Assert.assertEquals("Boom", entity1);
    }

    @Test
    public void testNoServiceHandler() throws Exception {
        final InetSocketAddress serverEndpoint = server.start();

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(
                "localhost", serverEndpoint.getPort(), TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                new BasicRequestProducer(Method.GET, createRequestURI(serverEndpoint, "/hello")),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assert.assertNotNull(result);
        final HttpResponse response1 = result.getHead();
        final String entity1 = result.getBody();
        Assert.assertNotNull(response1);
        Assert.assertEquals(404, response1.getCode());
        Assert.assertEquals("Resource not found", entity1);
    }

    @Test
    public void testResponseNoContent() throws Exception {
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

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(
                "localhost", serverEndpoint.getPort(), TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                new BasicRequestProducer(Method.GET, createRequestURI(serverEndpoint, "/hello")),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assert.assertNotNull(result);
        final HttpResponse response1 = result.getHead();
        Assert.assertNotNull(response1);
        Assert.assertEquals(204, response1.getCode());
        Assert.assertNull(result.getBody());
    }

    @Test
    public void testAbsentHostHeader() throws Exception {
        server.register("/hello", () -> new SingleLineResponseHandler("Hi there"));
        final InetSocketAddress serverEndpoint = server.start();

        client.start(new DefaultHttpProcessor(new RequestContent(), new RequestConnControl()), Http1Config.DEFAULT);

        final Future<ClientSessionEndpoint> connectFuture = client.connect(
                "localhost", serverEndpoint.getPort(), TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final HttpRequest request1 = new BasicHttpRequest(Method.GET, createRequestURI(serverEndpoint, "/hello"));
        request1.setVersion(HttpVersion.HTTP_1_0);
        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                new BasicRequestProducer(request1, null),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> result1 = future1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assert.assertNotNull(result1);
        final HttpResponse response1 = result1.getHead();
        Assert.assertNotNull(response1);
        Assert.assertEquals(200, response1.getCode());
        Assert.assertEquals("Hi there", result1.getBody());

        final HttpRequest request2 = new BasicHttpRequest(Method.GET, createRequestURI(serverEndpoint, "/hello"));
        request2.setVersion(HttpVersion.HTTP_1_1);
        final Future<Message<HttpResponse, String>> future2 = streamEndpoint.execute(
                new BasicRequestProducer(request2, null),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> result2 = future2.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assert.assertNotNull(result2);
        final HttpResponse response2 = result2.getHead();
        Assert.assertNotNull(response2);
        Assert.assertEquals(400, response2.getCode());
        Assert.assertEquals("Host header is absent", result2.getBody());
    }

    @Test
    public void testMessageWithTrailers() throws Exception {
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

        client.start();

        final Future<ClientSessionEndpoint> connectFuture = client.connect(
                "localhost", serverEndpoint.getPort(), TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final HttpRequest request1 = new BasicHttpRequest(Method.GET, createRequestURI(serverEndpoint, "/hello"));
        final DigestingEntityConsumer<String> entityConsumer = new DigestingEntityConsumer<>("MD5", new StringAsyncEntityConsumer());
        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                new BasicRequestProducer(request1, null),
                new BasicResponseConsumer<>(entityConsumer), null);
        final Message<HttpResponse, String> result1 = future1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assert.assertNotNull(result1);
        final HttpResponse response1 = result1.getHead();
        Assert.assertNotNull(response1);
        Assert.assertEquals(200, response1.getCode());
        Assert.assertEquals("Hello back with some trailers", result1.getBody());

        final List<Header> trailers = entityConsumer.getTrailers();
        Assert.assertNotNull(trailers);
        Assert.assertEquals(2, trailers.size());
        final Map<String, String> map = new HashMap<>();
        for (final Header header: trailers) {
            map.put(header.getName().toLowerCase(Locale.ROOT), header.getValue());
        }
        final String digest = TextUtils.toHexString(entityConsumer.getDigest());
        Assert.assertEquals("MD5", map.get("digest-algo"));
        Assert.assertEquals(digest, map.get("digest"));
    }

    @Test
    public void testProtocolException() throws Exception {
        server.register("/boom", () -> new AsyncServerExchangeHandler() {

            private final StringAsyncEntityProducer entityProducer = new StringAsyncEntityProducer("Everyting is OK");

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

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(
                "localhost", serverEndpoint.getPort(), TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();
        final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                new BasicRequestProducer(Method.GET, createRequestURI(serverEndpoint, "/boom")),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assert.assertNotNull(result);
        final HttpResponse response1 = result.getHead();
        final String entity1 = result.getBody();
        Assert.assertNotNull(response1);
        Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response1.getCode());
        Assert.assertEquals("Boom!!!", entity1);
    }

    @Test
    public void testHeaderTooLarge() throws Exception {
        server.register("/hello", () -> new SingleLineResponseHandler("Hi there"));
        final InetSocketAddress serverEndpoint = server.start(null, Http1Config.custom()
                .setMaxLineLength(100)
                .build());
        client.start();

        final Future<ClientSessionEndpoint> connectFuture = client.connect(
                "localhost", serverEndpoint.getPort(), TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final HttpRequest request1 = new BasicHttpRequest(Method.GET, createRequestURI(serverEndpoint, "/hello"));
        request1.setHeader("big-f-header", "1234567890123456789012345678901234567890123456789012345678901234567890" +
                "1234567890123456789012345678901234567890");
        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                new BasicRequestProducer(request1, null),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> result1 = future1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assert.assertNotNull(result1);
        final HttpResponse response1 = result1.getHead();
        Assert.assertNotNull(response1);
        Assert.assertEquals(431, response1.getCode());
        Assert.assertEquals("Maximum line length limit exceeded", result1.getBody());
    }

    @Test
    public void testHeaderTooLargePost() throws Exception {
        server.register("/hello", () -> new SingleLineResponseHandler("Hi there"));
        final InetSocketAddress serverEndpoint = server.start(null, Http1Config.custom()
                .setMaxLineLength(100)
                .build());
        client.start(
                new DefaultHttpProcessor(new RequestContent(), new RequestTargetHost(), new RequestConnControl()), null);

        final Future<ClientSessionEndpoint> connectFuture = client.connect(
                "localhost", serverEndpoint.getPort(), TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final HttpRequest request1 = new BasicHttpRequest(Method.POST, createRequestURI(serverEndpoint, "/hello"));
        request1.setHeader("big-f-header", "1234567890123456789012345678901234567890123456789012345678901234567890" +
                "1234567890123456789012345678901234567890");

        final byte[] b = new byte[2048];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) ('a' + i % 10);
        }

        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                new BasicRequestProducer(request1, AsyncEntityProducers.create(b, ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> result1 = future1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assert.assertNotNull(result1);
        final HttpResponse response1 = result1.getHead();
        Assert.assertNotNull(response1);
        Assert.assertEquals(431, response1.getCode());
        Assert.assertEquals("Maximum line length limit exceeded", result1.getBody());
    }

}
