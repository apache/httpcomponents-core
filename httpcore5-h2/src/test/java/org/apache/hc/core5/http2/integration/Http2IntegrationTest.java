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

package org.apache.hc.core5.http2.integration;

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
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.StringTokenizer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.entity.ContentType;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http2.H2Error;
import org.apache.hc.core5.http2.H2StreamResetException;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.nio.AbstractAsyncPushHandler;
import org.apache.hc.core5.http2.nio.AbstractAsyncServerExchangeHandler;
import org.apache.hc.core5.http2.nio.AbstractClassicServerExchangeHandler;
import org.apache.hc.core5.http2.nio.AsyncPushConsumer;
import org.apache.hc.core5.http2.nio.AsyncResponseProducer;
import org.apache.hc.core5.http2.nio.AsyncResponseTrigger;
import org.apache.hc.core5.http2.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http2.nio.BasicPushProducer;
import org.apache.hc.core5.http2.nio.BasicRequestProducer;
import org.apache.hc.core5.http2.nio.BasicResponseConsumer;
import org.apache.hc.core5.http2.nio.BasicResponseProducer;
import org.apache.hc.core5.http2.nio.CapacityChannel;
import org.apache.hc.core5.http2.nio.DataStreamChannel;
import org.apache.hc.core5.http2.nio.ExpectationChannel;
import org.apache.hc.core5.http2.nio.ResponseChannel;
import org.apache.hc.core5.http2.nio.StreamChannel;
import org.apache.hc.core5.http2.nio.Supplier;
import org.apache.hc.core5.http2.nio.command.ClientCommandEndpoint;
import org.apache.hc.core5.http2.nio.entity.AbstractCharAsyncEntityProducer;
import org.apache.hc.core5.http2.nio.entity.AbstractClassicEntityConsumer;
import org.apache.hc.core5.http2.nio.entity.AbstractClassicEntityProducer;
import org.apache.hc.core5.http2.nio.entity.NoopEntityConsumer;
import org.apache.hc.core5.http2.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http2.nio.entity.StringAsyncEntityProducer;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class Http2IntegrationTest extends InternalServerTestBase {

    private Http2TestClient client;

    @Before
    public void setup() throws Exception {
        client = new Http2TestClient();
    }

    @After
    public void cleanup() throws Exception {
        if (client != null) {
            client.shutdown(3, TimeUnit.SECONDS);
        }
    }

    private URI createRequestURI(final InetSocketAddress serverEndpoint, final String path) {
        try {
            return new URI("http", null, "localhost", serverEndpoint.getPort(), path, null, null);
        } catch (URISyntaxException e) {
            throw new IllegalStateException();
        }
    }

    static class SingleLineEntityProducer extends StringAsyncEntityProducer {

        SingleLineEntityProducer(final String message) {
            super(message, ContentType.TEXT_PLAIN);
        }

    }

    static class SingleLineResponseHandler extends AbstractAsyncServerExchangeHandler<String> {

        private final String message;

        SingleLineResponseHandler(final String message) {
            super(new StringAsyncEntityConsumer());
            this.message = message;
        }

        @Override
        protected void handle(
                final Message<HttpRequest, String> request,
                final AsyncResponseTrigger responseTrigger) throws IOException, HttpException {
            responseTrigger.submitResponse(new BasicResponseProducer(
                    HttpStatus.SC_OK, new SingleLineEntityProducer(message)));
        }

    }

    @Test
    public void testSimpleGet() throws Exception {
        server.registerHandler("/hello", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new SingleLineResponseHandler("Hi there");
            }

        });
        final InetSocketAddress serverEndpoint = server.start();

        client.start();
        final Future<ClientCommandEndpoint> connectFuture = client.connect("localhost", serverEndpoint.getPort(), 5000);
        final ClientCommandEndpoint streamEndpoint = connectFuture.get();

        final HttpRequest request1 = new BasicHttpRequest("GET", createRequestURI(serverEndpoint, "/hello"));
        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                new BasicRequestProducer(request1, null),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> result1 = future1.get(5, TimeUnit.SECONDS);
        Assert.assertNotNull(result1);
        final HttpResponse response1 = result1.getHead();
        final String entity1 = result1.getBody();
        Assert.assertNotNull(response1);
        Assert.assertEquals(200, response1.getCode());
        Assert.assertEquals("Hi there", entity1);
    }

    static class MultiLineEntityProducer extends AbstractCharAsyncEntityProducer {

        private final String text;
        private final int total;
        private final CharBuffer charbuf;

        private int count;

        MultiLineEntityProducer(final String text, final int total) {
            super(1024, ContentType.TEXT_PLAIN);
            this.text = text;
            this.total = total;
            this.charbuf = CharBuffer.allocate(4096);
            this.count = 0;
        }

        @Override
        public long getContentLength() {
            return -1;
        }

        @Override
        protected void dataStart(final StreamChannel<CharBuffer> channel) throws IOException {
            produceData(channel);
        }

        @Override
        public int available() {
            return Integer.MAX_VALUE;
        }

        @Override
        protected void produceData(final StreamChannel<CharBuffer> channel) throws IOException {
            while (charbuf.remaining() > text.length() + 2 && count < total) {
                charbuf.put(text + "\r\n");
                count++;
            }
            if (charbuf.position() > 0) {
                charbuf.flip();
                channel.write(charbuf);
                charbuf.compact();
            }
            if (count >= total && charbuf.position() == 0) {
                channel.endStream();
            }
        }

        @Override
        public void releaseResources() {
        }

    }

    static class MultiLineResponseHandler extends AbstractAsyncServerExchangeHandler<String> {

        private final String message;
        private final int count;

        MultiLineResponseHandler(final String message, final int count) {
            super(new StringAsyncEntityConsumer());
            this.message = message;
            this.count = count;
        }

        @Override
        protected void handle(
                final Message<HttpRequest, String> request,
                final AsyncResponseTrigger responseTrigger) throws IOException, HttpException {
            final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_OK);
            responseTrigger.submitResponse(new BasicResponseProducer(
                    response,
                    new MultiLineEntityProducer(message, count)));
        }

    }

    @Test
    public void testLargeGet() throws Exception {
        server.registerHandler("/", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new MultiLineResponseHandler("0123456789abcdef", 5000);
            }

        });
        final InetSocketAddress serverEndpoint = server.start();

        client.start();
        final Future<ClientCommandEndpoint> connectFuture = client.connect("localhost", serverEndpoint.getPort(), 5000);
        final ClientCommandEndpoint streamEndpoint = connectFuture.get();

        final HttpRequest request1 = new BasicHttpRequest("GET", createRequestURI(serverEndpoint, "/"));
        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                new BasicRequestProducer(request1, null),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);

        final HttpRequest request2 = new BasicHttpRequest("GET", createRequestURI(serverEndpoint, "/"));
        final Future<Message<HttpResponse, String>> future2 = streamEndpoint.execute(
                new BasicRequestProducer(request2, null),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer(512)), null);

        final Message<HttpResponse, String> result1 = future1.get(5, TimeUnit.SECONDS);
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

        final Message<HttpResponse, String> result2 = future2.get(5, TimeUnit.SECONDS);
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
        server.registerHandler("/hello", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new SingleLineResponseHandler("Hi back");
            }

        });
        final InetSocketAddress serverEndpoint = server.start();

        client.start();
        final Future<ClientCommandEndpoint> connectFuture = client.connect("localhost", serverEndpoint.getPort(), 5000);
        final ClientCommandEndpoint streamEndpoint = connectFuture.get();

        final HttpRequest request1 = new BasicHttpRequest("POST", createRequestURI(serverEndpoint, "/hello"));
        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                new BasicRequestProducer(request1, new SingleLineEntityProducer("Hi there")),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> result1 = future1.get(5, TimeUnit.SECONDS);
        Assert.assertNotNull(result1);
        final HttpResponse response1 = result1.getHead();
        final String entity1 = result1.getBody();
        Assert.assertNotNull(response1);
        Assert.assertEquals(200, response1.getCode());
        Assert.assertEquals("Hi back", entity1);
    }

    static class EchoHandler implements AsyncServerExchangeHandler {

        private volatile ByteBuffer buffer;
        private volatile CapacityChannel inputCapacityChannel;
        private volatile DataStreamChannel outputDataChannel;
        private volatile boolean endStream;

        EchoHandler(final int bufferSize) {
            this.buffer = ByteBuffer.allocate(bufferSize);
        }

        private void ensureCapacity(final int chunk) {
            if (buffer.remaining() < chunk) {
                final ByteBuffer oldBuffer = buffer;
                oldBuffer.flip();
                buffer = ByteBuffer.allocate(oldBuffer.remaining() + (chunk > 2048 ? chunk : 2048));
                buffer.put(oldBuffer);
            }
        }

        @Override
        public void verify(
                final HttpRequest request,
                final EntityDetails entityDetails,
                final ExpectationChannel expectationChannel) throws HttpException, IOException {
            expectationChannel.sendContinue();
        }

        @Override
        public void handleRequest(
                final HttpRequest request,
                final EntityDetails entityDetails,
                final ResponseChannel responseChannel) throws HttpException, IOException {
            final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_OK);
            responseChannel.sendResponse(response, entityDetails);
        }

        @Override
        public void consume(final ByteBuffer src) throws IOException {
            if (buffer.position() == 0) {
                if (outputDataChannel != null) {
                    outputDataChannel.write(src);
                }
            }
            if (src.hasRemaining()) {
                ensureCapacity(src.remaining());
                buffer.put(src);
                if (outputDataChannel != null) {
                    outputDataChannel.requestOutput();
                }
            }
        }

        @Override
        public int capacity() {
            return buffer.remaining();
        }

        @Override
        public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
            if (buffer.hasRemaining()) {
                capacityChannel.update(buffer.remaining());
                inputCapacityChannel = null;
            } else {
                inputCapacityChannel = capacityChannel;
            }
        }

        @Override
        public void streamEnd(final List<Header> trailers) throws HttpException, IOException {
            endStream = true;
            if (buffer.position() == 0) {
                if (outputDataChannel != null) {
                    outputDataChannel.endStream();
                }
            } else {
                if (outputDataChannel != null) {
                    outputDataChannel.requestOutput();
                }
            }
        }

        @Override
        public int available() {
            return buffer.position();
        }

        @Override
        public void produce(final DataStreamChannel channel) throws IOException {
            outputDataChannel = channel;
            buffer.flip();
            if (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            buffer.compact();
            if (buffer.position() == 0 && endStream) {
                channel.endStream();
            }
            final CapacityChannel capacityChannel = inputCapacityChannel;
            if (capacityChannel != null && buffer.hasRemaining()) {
                capacityChannel.update(buffer.remaining());
            }
        }

        @Override
        public void failed(final Exception cause) {
        }

        @Override
        public void releaseResources() {
        }

    }

    @Test
    public void testLargePost() throws Exception {
        server.registerHandler("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new EchoHandler(2048);
            }

        });
        final InetSocketAddress serverEndpoint = server.start();

        client.start();
        final Future<ClientCommandEndpoint> connectFuture = client.connect("localhost", serverEndpoint.getPort(), 5000);
        final ClientCommandEndpoint streamEndpoint = connectFuture.get();

        client.start();

        final HttpRequest request1 = new BasicHttpRequest("POST", createRequestURI(serverEndpoint, "/echo"));
        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                new BasicRequestProducer(request1, new MultiLineEntityProducer("0123456789abcdef", 5000)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> result1 = future1.get(5, TimeUnit.SECONDS);
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
        server.registerHandler("/", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new MultiLineResponseHandler("0123456789abcd", 3);
            }

        });
        final InetSocketAddress serverEndpoint = server.start();

        client.start(H2Config.custom().setInitialWindowSize(16).build());
        final Future<ClientCommandEndpoint> connectFuture = client.connect("localhost", serverEndpoint.getPort(), 5000);
        final ClientCommandEndpoint streamEndpoint = connectFuture.get();

        final HttpRequest request1 = new BasicHttpRequest("GET", createRequestURI(serverEndpoint, "/"));
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

                        final StringBuffer buffer = new StringBuffer();
                        try {
                            final byte[] tmp = new byte[16];
                            int l;
                            while ((l = inputStream.read(tmp)) != -1) {
                                buffer.append(charset.decode(ByteBuffer.wrap(tmp, 0, l)));
                                Thread.sleep(500);
                            }
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            throw new InterruptedIOException(ex.getMessage());
                        }
                        return buffer.toString();
                    }
                }),
                null);

        final Message<HttpResponse, String> result1 = future1.get(5, TimeUnit.SECONDS);
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
        server.registerHandler("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new EchoHandler(2048);
            }

        });
        final InetSocketAddress serverEndpoint = server.start();

        client.start();
        final Future<ClientCommandEndpoint> connectFuture = client.connect("localhost", serverEndpoint.getPort(), 5000);
        final ClientCommandEndpoint streamEndpoint = connectFuture.get();

        final HttpRequest request1 = new BasicHttpRequest("POST", createRequestURI(serverEndpoint, "/echo"));
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
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            throw new InterruptedIOException(ex.getMessage());
                        }
                    }

                }),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> result1 = future1.get(5, TimeUnit.SECONDS);
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
        server.registerHandler("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new AbstractClassicServerExchangeHandler(2048, Executors.newSingleThreadExecutor()) {

                    @Override
                    protected void handle(
                            final HttpRequest request,
                            final InputStream requestStream,
                            final HttpResponse response,
                            final OutputStream responseStream) throws IOException, HttpException {

                        if (!"/hello".equals(request.getPath())) {
                            response.setCode(HttpStatus.SC_NOT_FOUND);
                            return;
                        }
                        if (!"POST".equalsIgnoreCase(request.getMethod())) {
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
                            } catch (InterruptedException ex) {
                                Thread.currentThread().interrupt();
                                throw new InterruptedIOException(ex.getMessage());
                            }
                        }
                    }
                };
            }

        });
        final InetSocketAddress serverEndpoint = server.start();

        client.start(H2Config.custom()
                .setInitialWindowSize(512)
                .build());

        final Future<ClientCommandEndpoint> connectFuture = client.connect("localhost", serverEndpoint.getPort(), 5000);
        final ClientCommandEndpoint streamEndpoint = connectFuture.get();

        final HttpRequest request1 = new BasicHttpRequest("POST", createRequestURI(serverEndpoint, "/hello"));
        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                new BasicRequestProducer(request1, new MultiLineEntityProducer("0123456789abcd", 2000)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> result1 = future1.get(5, TimeUnit.SECONDS);
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
        server.registerHandler("/hello", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new AbstractAsyncServerExchangeHandler<Void>(new NoopEntityConsumer()) {

                    @Override
                    protected void handle(
                            final Message<HttpRequest, Void> request,
                            final AsyncResponseTrigger responseTrigger) throws IOException, HttpException {

                        responseTrigger.pushPromise(
                                new BasicHttpRequest("GET", createRequestURI(serverEndpoint, "/stuff")),
                                new BasicPushProducer(new MultiLineEntityProducer("Pushing lots of stuff", 500)));
                        responseTrigger.submitResponse(new BasicResponseProducer(
                                HttpStatus.SC_OK,
                                new SingleLineEntityProducer("Hi there")));
                    }
                };
            }

        });

        client.start(H2Config.custom().setPushEnabled(true).build());

        final BlockingQueue<Message<HttpResponse, String>> pushMessageQueue = new LinkedBlockingDeque<>();
        client.registerHandler("*", new Supplier<AsyncPushConsumer>() {

            @Override
            public AsyncPushConsumer get() {
                return new AbstractAsyncPushHandler<String>(new BasicResponseConsumer<>(new StringAsyncEntityConsumer())) {

                    @Override
                    protected void handleResponse(
                            final HttpRequest promise,
                            final Message<HttpResponse, String> responseMessage) throws IOException, HttpException {
                        try {
                            pushMessageQueue.put(responseMessage);
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            throw new InterruptedIOException(ex.getMessage());
                        }
                    }

                };
            }

        });

        final Future<ClientCommandEndpoint> connectFuture = client.connect("localhost", serverEndpoint.getPort(), 5000);
        final ClientCommandEndpoint streamEndpoint = connectFuture.get();

        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                new BasicRequestProducer("GET", createRequestURI(serverEndpoint, "/hello")),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> result1 = future1.get(5, TimeUnit.SECONDS);
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
        server.registerHandler("/hello", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new AbstractAsyncServerExchangeHandler<Void>(new NoopEntityConsumer()) {

                    @Override
                    protected void handle(
                            final Message<HttpRequest, Void> request,
                            final AsyncResponseTrigger responseTrigger) throws IOException, HttpException {

                        responseTrigger.pushPromise(
                                new BasicHttpRequest("GET", createRequestURI(serverEndpoint, "/stuff")),
                                new BasicPushProducer(new SingleLineEntityProducer("Pushing all sorts of stuff")) {

                            @Override
                            public void failed(final Exception cause) {
                                pushResultQueue.add(cause);
                                super.failed(cause);
                            }

                        });
                        responseTrigger.pushPromise(
                                new BasicHttpRequest("GET", createRequestURI(serverEndpoint, "/more-stuff")),
                                new BasicPushProducer(new MultiLineEntityProducer("Pushing lots of stuff", 500)) {

                            @Override
                            public void failed(final Exception cause) {
                                pushResultQueue.add(cause);
                                super.failed(cause);
                            }

                        });
                        responseTrigger.submitResponse(new BasicResponseProducer(
                                HttpStatus.SC_OK,
                                new SingleLineEntityProducer("Hi there")));
                    }
                };
            }

        });

        client.start(H2Config.custom().setPushEnabled(true).build());

        final Future<ClientCommandEndpoint> connectFuture = client.connect("localhost", serverEndpoint.getPort(), 5000);
        final ClientCommandEndpoint streamEndpoint = connectFuture.get();

        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                new BasicRequestProducer("GET", createRequestURI(serverEndpoint, "/hello")),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> result1 = future1.get(5, TimeUnit.SECONDS);
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
        server.registerHandler("/", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new MultiLineResponseHandler("0123456789abcdef", 2000);
            }

        });
        final InetSocketAddress serverEndpoint = server.start(H2Config.custom().setMaxConcurrentStreams(20).build());

        client.start(H2Config.custom().setMaxConcurrentStreams(20).build());
        final Future<ClientCommandEndpoint> connectFuture = client.connect("localhost", serverEndpoint.getPort(), 5000);
        final ClientCommandEndpoint streamEndpoint = connectFuture.get();

        final Queue<Future<Message<HttpResponse, Void>>> queue = new LinkedList<>();
        for (int i = 0; i < 2000; i++) {
            final HttpRequest request1 = new BasicHttpRequest("GET", createRequestURI(serverEndpoint, "/"));
            final Future<Message<HttpResponse, Void>> future = streamEndpoint.execute(
                    new BasicRequestProducer(request1, null),
                    new BasicResponseConsumer<>(new NoopEntityConsumer()), null);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            final Future<Message<HttpResponse, Void>> future = queue.remove();
            final Message<HttpResponse, Void> result = future.get(5, TimeUnit.SECONDS);
            Assert.assertNotNull(result);
            final HttpResponse response = result.getHead();
            Assert.assertNotNull(response);
            Assert.assertEquals(200, response.getCode());
        }
    }

    @Test
    public void testExpecationFailed() throws Exception {
        server.registerHandler("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new AbstractAsyncServerExchangeHandler<String>(new StringAsyncEntityConsumer()) {

                    @Override
                    protected AsyncResponseProducer verify(final HttpRequest request) throws IOException, HttpException {
                        final Header h = request.getFirstHeader("password");
                        if (h != null && "secret".equals(h.getValue())) {
                            return null;
                        } else {
                            return new BasicResponseProducer(HttpStatus.SC_UNAUTHORIZED, "You shall not pass");
                        }
                    }

                    @Override
                    protected void handle(
                            final Message<HttpRequest, String> request,
                            final AsyncResponseTrigger responseTrigger) throws IOException, HttpException {
                        responseTrigger.submitResponse(
                                new BasicResponseProducer(HttpStatus.SC_OK, "All is well"));

                    }
                };
            }

        });
        final InetSocketAddress serverEndpoint = server.start();

        client.start();
        final Future<ClientCommandEndpoint> connectFuture = client.connect("localhost", serverEndpoint.getPort(), 5000);
        final ClientCommandEndpoint streamEndpoint = connectFuture.get();

        client.start();

        final HttpRequest request1 = new BasicHttpRequest("POST", createRequestURI(serverEndpoint, "/echo"));
        request1.addHeader("password", "secret");
        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                new BasicRequestProducer(request1, new MultiLineEntityProducer("0123456789abcdef", 5000)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> result1 = future1.get(5, TimeUnit.SECONDS);
        Assert.assertNotNull(result1);
        final HttpResponse response1 = result1.getHead();
        Assert.assertNotNull(response1);
        Assert.assertEquals(200, response1.getCode());
        Assert.assertNotNull("All is well", result1.getBody());

        final HttpRequest request2 = new BasicHttpRequest("POST", createRequestURI(serverEndpoint, "/echo"));
        final Future<Message<HttpResponse, String>> future2 = streamEndpoint.execute(
                new BasicRequestProducer(request2, new MultiLineEntityProducer("0123456789abcdef", 5000)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> result2 = future2.get(5, TimeUnit.SECONDS);
        Assert.assertNotNull(result2);
        final HttpResponse response2 = result2.getHead();
        Assert.assertNotNull(response2);
        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response2.getCode());
        Assert.assertNotNull("You shall not pass", result2.getBody());
    }

    @Test
    public void testPrematureResponse() throws Exception {
        server.registerHandler("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new AsyncServerExchangeHandler() {

                    private final AtomicReference<AsyncResponseProducer> responseProducer = new AtomicReference<>(null);
                    private final AtomicBoolean dataStarted = new AtomicBoolean(false);

                    @Override
                    public void verify(
                            final HttpRequest request,
                            final EntityDetails entityDetails,
                            final ExpectationChannel expectationChannel) throws HttpException, IOException {
                        expectationChannel.sendContinue();
                    }

                    @Override
                    public int capacity() {
                        return Integer.MAX_VALUE;
                    }

                    @Override
                    public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
                        capacityChannel.update(Integer.MAX_VALUE);
                    }

                    @Override
                    public void consume(final ByteBuffer src) throws IOException {
                    }

                    @Override
                    public void streamEnd(final List<Header> trailers) throws HttpException, IOException {
                    }

                    @Override
                    public void handleRequest(
                            final HttpRequest request,
                            final EntityDetails entityDetails,
                            final ResponseChannel responseChannel) throws HttpException, IOException {
                        final AsyncResponseProducer producer;
                        final Header h = request.getFirstHeader("password");
                        if (h != null && "secret".equals(h.getValue())) {
                            producer = new BasicResponseProducer(HttpStatus.SC_OK, "All is well");
                        } else {
                            producer = new BasicResponseProducer(HttpStatus.SC_UNAUTHORIZED, "You shall not pass");
                        }
                        responseProducer.set(producer);
                        responseChannel.sendResponse(producer.produceResponse(), producer.getEntityDetails());
                    }

                    @Override
                    public int available() {
                        final AsyncResponseProducer producer = this.responseProducer.get();
                        return producer.available();
                    }

                    @Override
                    public void produce(final DataStreamChannel channel) throws IOException {
                        final AsyncResponseProducer producer = this.responseProducer.get();
                        if (dataStarted.compareAndSet(false, true)) {
                            producer.dataStart(channel);
                        }
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
        final Future<ClientCommandEndpoint> connectFuture = client.connect("localhost", serverEndpoint.getPort(), 5000);
        final ClientCommandEndpoint streamEndpoint = connectFuture.get();

        client.start();

        final HttpRequest request1 = new BasicHttpRequest("POST", createRequestURI(serverEndpoint, "/echo"));
        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                new BasicRequestProducer(request1, new MultiLineEntityProducer("0123456789abcdef", 5000)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> result1 = future1.get(5, TimeUnit.SECONDS);
        Assert.assertNotNull(result1);
        final HttpResponse response1 = result1.getHead();
        Assert.assertNotNull(response1);
        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response1.getCode());
        Assert.assertNotNull("You shall not pass", result1.getBody());
    }

}
