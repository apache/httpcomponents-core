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
import java.util.StringTokenizer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElements;
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
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.nio.AsyncRequestConsumer;
import org.apache.hc.core5.http.nio.AsyncResponseProducer;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncServerRequestHandler;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.ResponseChannel;
import org.apache.hc.core5.http.nio.entity.AsyncEntityProducers;
import org.apache.hc.core5.http.nio.entity.DigestingEntityConsumer;
import org.apache.hc.core5.http.nio.entity.DigestingEntityProducer;
import org.apache.hc.core5.http.nio.entity.DiscardingEntityConsumer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityProducer;
import org.apache.hc.core5.http.nio.support.AbstractAsyncPushHandler;
import org.apache.hc.core5.http.nio.support.AbstractServerExchangeHandler;
import org.apache.hc.core5.http.nio.support.AsyncResponseBuilder;
import org.apache.hc.core5.http.nio.support.BasicAsyncServerExpectationDecorator;
import org.apache.hc.core5.http.nio.support.BasicPushProducer;
import org.apache.hc.core5.http.nio.support.BasicRequestConsumer;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.http.nio.support.BasicResponseProducer;
import org.apache.hc.core5.http.nio.support.classic.AbstractClassicEntityConsumer;
import org.apache.hc.core5.http.nio.support.classic.AbstractClassicEntityProducer;
import org.apache.hc.core5.http.nio.support.classic.AbstractClassicServerExchangeHandler;
import org.apache.hc.core5.http.protocol.DefaultHttpProcessor;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http2.H2Error;
import org.apache.hc.core5.http2.H2StreamResetException;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.nio.command.PingCommand;
import org.apache.hc.core5.http2.nio.support.BasicPingHandler;
import org.apache.hc.core5.http2.protocol.H2RequestConnControl;
import org.apache.hc.core5.http2.protocol.H2RequestContent;
import org.apache.hc.core5.http2.protocol.H2RequestTargetHost;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.testing.SSLTestContexts;
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
public class H2IntegrationTest extends InternalH2ServerTestBase {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> protocols() {
        return Arrays.asList(new Object[][]{
                { URIScheme.HTTP },
                { URIScheme.HTTPS }
        });
    }

    public H2IntegrationTest(final URIScheme scheme) {
        super(scheme);
    }

    private static final Timeout TIMEOUT = Timeout.ofSeconds(30);
    private static final Timeout LONG_TIMEOUT = Timeout.ofSeconds(60);

    private H2TestClient client;

    @Before
    public void setup() throws Exception {
        log.debug("Starting up test client");
        client = new H2TestClient(buildReactorConfig(),
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
            return new URI("http", null, "localhost", serverEndpoint.getPort(), path, null, null);
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

        final Queue<Future<Message<HttpResponse, String>>> queue = new LinkedList<>();
        for (int i = 0; i < 10; i++) {
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
    public void testLargeGet() throws Exception {
        server.register("/", () -> new MultiLineResponseHandler("0123456789abcdef", 5000));
        final InetSocketAddress serverEndpoint = server.start();

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(
                "localhost", serverEndpoint.getPort(), TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                new BasicRequestProducer(Method.GET, createRequestURI(serverEndpoint, "/"), null),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);

        final Future<Message<HttpResponse, String>> future2 = streamEndpoint.execute(
                new BasicRequestProducer(Method.GET, createRequestURI(serverEndpoint, "/")),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer(512)), null);

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
    public void testBasicPost() throws Exception {
        server.register("/hello", () -> new SingleLineResponseHandler("Hi back"));
        final InetSocketAddress serverEndpoint = server.start();

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(
                "localhost", serverEndpoint.getPort(), TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final Queue<Future<Message<HttpResponse, String>>> queue = new LinkedList<>();
        for (int i = 0; i < 10; i++) {
            final HttpRequest request = new BasicHttpRequest(Method.POST, createRequestURI(serverEndpoint, "/hello"));
            queue.add(streamEndpoint.execute(
                    new BasicRequestProducer(request, new StringAsyncEntityProducer("Hi there", ContentType.TEXT_PLAIN)),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null));

        }
        while (!queue.isEmpty()) {
            final Future<Message<HttpResponse, String>> future = queue.remove();
            final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assert.assertNotNull(result);
            final HttpResponse response = result.getHead();
            final String entity1 = result.getBody();
            Assert.assertNotNull(response);
            Assert.assertEquals(200, response.getCode());
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

        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                new BasicRequestProducer(Method.POST, createRequestURI(serverEndpoint, "/echo"),
                        new MultiLineEntityProducer("0123456789abcdef", 5000)),
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
    }

    @Test
    public void testSlowResponseConsumer() throws Exception {
        server.register("/", () -> new MultiLineResponseHandler("0123456789abcd", 3));
        final InetSocketAddress serverEndpoint = server.start();

        client.start(H2Config.custom().setInitialWindowSize(16).build());
        final Future<ClientSessionEndpoint> connectFuture = client.connect(
                "localhost", serverEndpoint.getPort(), TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                new BasicRequestProducer(Method.GET, createRequestURI(serverEndpoint, "/"), null),
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
                                Thread.sleep(500);
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

        client.start(H2Config.custom()
                .setInitialWindowSize(512)
                .build());

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
    public void testPush() throws Exception {
        final InetSocketAddress serverEndpoint = server.start();
        server.register("/hello", () -> new MessageExchangeHandler<Void>(new DiscardingEntityConsumer<>()) {

            @Override
            protected void handle(
                    final Message<HttpRequest, Void> request,
                    final AsyncServerRequestHandler.ResponseTrigger responseTrigger,
                    final HttpContext context) throws IOException, HttpException {
                responseTrigger.pushPromise(
                        new BasicHttpRequest(Method.GET, createRequestURI(serverEndpoint, "/stuff")),
                        context,
                        new BasicPushProducer(new MultiLineEntityProducer("Pushing lots of stuff", 500)));
                responseTrigger.submitResponse(
                        AsyncResponseBuilder.create(HttpStatus.SC_OK).setEntity("Hi there", ContentType.TEXT_PLAIN).build(),
                        context);
            }
        });

        client.start(H2Config.custom().setPushEnabled(true).build());

        final BlockingQueue<Message<HttpResponse, String>> pushMessageQueue = new LinkedBlockingDeque<>();

        final Future<ClientSessionEndpoint> connectFuture = client.connect(
                "localhost", serverEndpoint.getPort(), TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                new BasicRequestProducer(Method.GET, createRequestURI(serverEndpoint, "/hello")),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()),
                (request, context) -> new AbstractAsyncPushHandler<Message<HttpResponse, String>>(new BasicResponseConsumer<>(new StringAsyncEntityConsumer())) {

                    @Override
                    protected void handleResponse(
                            final HttpRequest promise,
                            final Message<HttpResponse, String> responseMessage) throws IOException, HttpException {
                        try {
                            pushMessageQueue.put(responseMessage);
                        } catch (final InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            throw new InterruptedIOException(ex.getMessage());
                        }
                    }

                },
                null,
                null);
        final Message<HttpResponse, String> result1 = future1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assert.assertNotNull(result1);
        final HttpResponse response1 = result1.getHead();
        final String entity1 = result1.getBody();
        Assert.assertNotNull(response1);
        Assert.assertEquals(200, response1.getCode());
        Assert.assertEquals("Hi there", entity1);

        final Message<HttpResponse, String> result2 = pushMessageQueue.poll(5, TimeUnit.SECONDS);
        Assert.assertNotNull(result2);
        final HttpResponse response2 = result2.getHead();
        final String entity2 = result2.getBody();
        Assert.assertEquals(200, response2.getCode());
        Assert.assertNotNull(entity2);
        final StringTokenizer t1 = new StringTokenizer(entity2, "\r\n");
        while (t1.hasMoreTokens()) {
            Assert.assertEquals("Pushing lots of stuff", t1.nextToken());
        }
    }

    @Test
    public void testPushRefused() throws Exception {
        final BlockingQueue<Exception> pushResultQueue = new LinkedBlockingDeque<>();
        final InetSocketAddress serverEndpoint = server.start();
        server.register("/hello", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new MessageExchangeHandler<Void>(new DiscardingEntityConsumer<>()) {

                    @Override
                    protected void handle(
                            final Message<HttpRequest, Void> request,
                            final AsyncServerRequestHandler.ResponseTrigger responseTrigger,
                            final HttpContext context) throws IOException, HttpException {

                        responseTrigger.pushPromise(
                                new BasicHttpRequest(Method.GET, createRequestURI(serverEndpoint, "/stuff")),
                                context, new BasicPushProducer(AsyncEntityProducers.create("Pushing all sorts of stuff")) {

                            @Override
                            public void failed(final Exception cause) {
                                pushResultQueue.add(cause);
                                super.failed(cause);
                            }

                        });
                        responseTrigger.pushPromise(
                                new BasicHttpRequest(Method.GET, createRequestURI(serverEndpoint, "/more-stuff")),
                                context, new BasicPushProducer(new MultiLineEntityProducer("Pushing lots of stuff", 500)) {

                            @Override
                            public void failed(final Exception cause) {
                                pushResultQueue.add(cause);
                                super.failed(cause);
                            }

                        });
                        responseTrigger.submitResponse(
                                new BasicResponseProducer(HttpStatus.SC_OK, AsyncEntityProducers.create("Hi there")),
                                context);
                    }
                };
            }

        });

        client.start(H2Config.custom().setPushEnabled(true).build());

        final Future<ClientSessionEndpoint> connectFuture = client.connect(
                "localhost", serverEndpoint.getPort(), TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                new BasicRequestProducer(Method.GET, createRequestURI(serverEndpoint, "/hello")),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> result1 = future1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assert.assertNotNull(result1);
        final HttpResponse response1 = result1.getHead();
        final String entity1 = result1.getBody();
        Assert.assertNotNull(response1);
        Assert.assertEquals(200, response1.getCode());
        Assert.assertEquals("Hi there", entity1);

        final Object result2 = pushResultQueue.poll(5, TimeUnit.SECONDS);
        Assert.assertNotNull(result2);
        Assert.assertTrue(result2 instanceof H2StreamResetException);
        Assert.assertEquals(H2Error.REFUSED_STREAM.getCode(), ((H2StreamResetException) result2).getCode());

        final Object result3 = pushResultQueue.poll(5, TimeUnit.SECONDS);
        Assert.assertNotNull(result3);
        Assert.assertTrue(result3 instanceof H2StreamResetException);
        Assert.assertEquals(H2Error.REFUSED_STREAM.getCode(), ((H2StreamResetException) result3).getCode());
    }

    @Test
    public void testExcessOfConcurrentStreams() throws Exception {
        server.register("/", () -> new MultiLineResponseHandler("0123456789abcdef", 2000));
        final InetSocketAddress serverEndpoint = server.start(H2Config.custom().setMaxConcurrentStreams(20).build());

        client.start(H2Config.custom().setMaxConcurrentStreams(20).build());
        final Future<ClientSessionEndpoint> connectFuture = client.connect(
                "localhost", serverEndpoint.getPort(), TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final Queue<Future<Message<HttpResponse, Void>>> queue = new LinkedList<>();
        for (int i = 0; i < 2000; i++) {
            final HttpRequest request1 = new BasicHttpRequest(Method.GET, createRequestURI(serverEndpoint, "/"));
            final Future<Message<HttpResponse, Void>> future = streamEndpoint.execute(
                    new BasicRequestProducer(request1, null),
                    new BasicResponseConsumer<>(new DiscardingEntityConsumer<>()), null);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            final Future<Message<HttpResponse, Void>> future = queue.remove();
            final Message<HttpResponse, Void> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assert.assertNotNull(result);
            final HttpResponse response = result.getHead();
            Assert.assertNotNull(response);
            Assert.assertEquals(200, response.getCode());
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
        }, H2Config.DEFAULT);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(
                "localhost", serverEndpoint.getPort(), TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final HttpRequest request1 = new BasicHttpRequest(Method.POST, createRequestURI(serverEndpoint, "/echo"));
        request1.addHeader("password", "secret");
        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                new BasicRequestProducer(request1, new MultiLineEntityProducer("0123456789abcdef", 5000)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> result1 = future1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assert.assertNotNull(result1);
        final HttpResponse response1 = result1.getHead();
        Assert.assertNotNull(response1);
        Assert.assertEquals(200, response1.getCode());
        Assert.assertNotNull("All is well", result1.getBody());

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
    }

    @Test
    public void testPrematureResponse() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new AsyncServerExchangeHandler() {

                    private final AtomicReference<AsyncResponseProducer> responseProducer = new AtomicReference<>(null);

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
                    public int available() {
                        final AsyncResponseProducer producer = this.responseProducer.get();
                        return producer.available();
                    }

                    @Override
                    public void produce(final DataStreamChannel channel) throws IOException {
                        final AsyncResponseProducer producer = this.responseProducer.get();
                        producer.produce(channel);
                    }

                    @Override
                    public void failed(final Exception cause) {
                    }

                    @Override
                    public void releaseResources() {
                    }
                };
            }

        });
        final InetSocketAddress serverEndpoint = server.start();

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(
                "localhost", serverEndpoint.getPort(), TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final HttpRequest request1 = new BasicHttpRequest(Method.POST, createRequestURI(serverEndpoint, "/echo"));
        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                new BasicRequestProducer(request1, new MultiLineEntityProducer("0123456789abcdef", 5000)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> result1 = future1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assert.assertNotNull(result1);
        final HttpResponse response1 = result1.getHead();
        Assert.assertNotNull(response1);
        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response1.getCode());
        Assert.assertNotNull("You shall not pass", result1.getBody());
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
    public void testConnectionPing() throws Exception {
        server.register("/hello", () -> new SingleLineResponseHandler("Hi there"));
        final InetSocketAddress serverEndpoint = server.start();

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(
                "localhost", serverEndpoint.getPort(), TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final int n = 10;
        final CountDownLatch latch = new CountDownLatch(n);
        final AtomicInteger count = new AtomicInteger(0);
        for (int i = 0; i < n; i++) {
            streamEndpoint.execute(
                    new BasicRequestProducer(Method.GET, createRequestURI(serverEndpoint, "/hello")),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
            streamEndpoint.execute(new PingCommand(new BasicPingHandler(result -> {
                if (result) {
                    count.incrementAndGet();
                }
                latch.countDown();
            })), Command.Priority.NORMAL);

        }
        Assert.assertTrue(latch.await(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()));
        Assert.assertEquals(n, count.get());
    }

    @Test
    public void testRequestWithInvalidConnectionHeader() throws Exception {
        server.register("/hello", () -> new SingleLineResponseHandler("Hi there"));
        final InetSocketAddress serverEndpoint = server.start();

        client.start();

        final Future<IOSession> sessionFuture = client.requestSession(new HttpHost("localhost", serverEndpoint.getPort()), TIMEOUT, null);
        final IOSession session = sessionFuture.get();
        final ClientSessionEndpoint streamEndpoint = new ClientSessionEndpoint(session);

        final HttpRequest request = new BasicHttpRequest(Method.GET, createRequestURI(serverEndpoint, "/hello"));
        request.addHeader(HttpHeaders.CONNECTION, HeaderElements.CLOSE);
        final HttpCoreContext coreContext = HttpCoreContext.create();
        final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                    new BasicRequestProducer(request, null),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()),
                    coreContext, null);
        final ExecutionException exception = Assert.assertThrows(ExecutionException.class, () ->
                future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()));
        MatcherAssert.assertThat(exception.getCause(), CoreMatchers.instanceOf(ProtocolException.class));

        final EndpointDetails endpointDetails = coreContext.getEndpointDetails();
        MatcherAssert.assertThat(endpointDetails.getRequestCount(), CoreMatchers.equalTo(0L));
    }

    @Test
    public void testHeaderTooLarge() throws Exception {
        server.register("/hello", () -> new SingleLineResponseHandler("Hi there"));
        final InetSocketAddress serverEndpoint = server.start(H2Config.custom()
                .setMaxHeaderListSize(100)
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
        Assert.assertEquals("Maximum header list size exceeded", result1.getBody());
    }

    @Test
    public void testHeaderTooLargePost() throws Exception {
        server.register("/hello", () -> new SingleLineResponseHandler("Hi there"));
        final InetSocketAddress serverEndpoint = server.start(H2Config.custom()
                .setMaxHeaderListSize(100)
                .build());
        client.start(
                new DefaultHttpProcessor(new H2RequestContent(), new H2RequestTargetHost(), new H2RequestConnControl()),
                H2Config.DEFAULT);

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
        Assert.assertEquals("Maximum header list size exceeded", result1.getBody());
    }

}
