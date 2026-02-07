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


import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.ContentLengthStrategy;
import org.apache.hc.core5.http.ContentType;
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
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.BasicHttpTransportMetrics;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.http.impl.Http1StreamListener;
import org.apache.hc.core5.http.impl.HttpProcessors;
import org.apache.hc.core5.http.impl.nio.AbstractContentEncoder;
import org.apache.hc.core5.http.impl.nio.AbstractMessageWriter;
import org.apache.hc.core5.http.impl.nio.DefaultHttpRequestFactory;
import org.apache.hc.core5.http.impl.nio.DefaultHttpRequestParser;
import org.apache.hc.core5.http.impl.nio.ServerHttp1StreamDuplexer;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.message.BasicLineFormatter;
import org.apache.hc.core5.http.message.LineFormatter;
import org.apache.hc.core5.http.message.RequestLine;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.AsyncResponseProducer;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncServerRequestHandler;
import org.apache.hc.core5.http.nio.ContentEncoder;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.nio.NHttpMessageParser;
import org.apache.hc.core5.http.nio.NHttpMessageWriter;
import org.apache.hc.core5.http.nio.SessionOutputBuffer;
import org.apache.hc.core5.http.nio.entity.AsyncEntityProducers;
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityProducer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityProducer;
import org.apache.hc.core5.http.nio.support.AsyncRequestBuilder;
import org.apache.hc.core5.http.nio.support.BasicAsyncServerExpectationDecorator;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.http.nio.support.BasicResponseProducer;
import org.apache.hc.core5.http.nio.support.ImmediateResponseExchangeHandler;
import org.apache.hc.core5.http.protocol.DefaultHttpProcessor;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.RequestValidateHost;
import org.apache.hc.core5.http.support.BasicRequestBuilder;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.testing.SSLTestContexts;
import org.apache.hc.core5.testing.extension.nio.Http1TestResources;
import org.apache.hc.core5.util.CharArrayBuffer;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

abstract class Http1IntegrationTest extends HttpIntegrationTest {

    @RegisterExtension
    private final Http1TestResources resources;

    public Http1IntegrationTest(final URIScheme scheme) {
        super(scheme);
        this.resources = new Http1TestResources(scheme, TIMEOUT);
    }

    @Override
    protected HttpTestServer server() {
        return resources.server();
    }

    @Override
    protected HttpTestClient client() {
        return resources.client();
    }

    @Test
    void testGetConnectionClose() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

        server.register("/hello", () -> new SingleLineResponseHandler("Hi there"));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        for (int i = 0; i < REQ_NUM; i++) {
            final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
            try (final ClientSessionEndpoint streamEndpoint = connectFuture.get()) {
                final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                        AsyncRequestBuilder.get()
                                .setHttpHost(target)
                                .setPath("/hello")
                                .addHeader(HttpHeaders.CONNECTION, "close")
                                .build(),
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
    }

    @Test
    void testGetIdentityTransfer() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

        server.register("/hello", () -> new SingleLineResponseHandler("Hi there"));
        server.configure(new DefaultHttpProcessor(new RequestValidateHost()));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();

        for (int i = 0; i < REQ_NUM; i++) {
            final Future<ClientSessionEndpoint> connectFuture = client.connect("localhost", serverEndpoint.getPort(), TIMEOUT);
            final ClientSessionEndpoint streamEndpoint = connectFuture.get();

            final BasicHttpRequest request = BasicRequestBuilder.get()
                    .setHttpHost(target)
                    .setPath("/hello")
                    .build();
            final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                    new BasicRequestProducer(request, null),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
            final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());

            streamEndpoint.close();

            Assertions.assertNotNull(result);
            final HttpResponse response = result.getHead();
            final String entity = result.getBody();
            Assertions.assertNotNull(response);
            Assertions.assertEquals(200, response.getCode());
            Assertions.assertEquals("Hi there", entity);
        }

    }

    @Test
    void testPostIdentityTransfer() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

        server.register("/hello", () -> new SingleLineResponseHandler("Hi there"));
        server.configure(new DefaultHttpProcessor(new RequestValidateHost()));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();

        for (int i = 0; i < REQ_NUM; i++) {
            final Future<ClientSessionEndpoint> connectFuture = client.connect("localhost", serverEndpoint.getPort(), TIMEOUT);
            final ClientSessionEndpoint streamEndpoint = connectFuture.get();

            final BasicHttpRequest request = BasicRequestBuilder.post()
                    .setHttpHost(target)
                    .setPath("/hello")
                    .build();
            final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                    new BasicRequestProducer(request, new MultiLineEntityProducer("Hello", 16 * i)),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
            final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());

            streamEndpoint.close();

            Assertions.assertNotNull(result);
            final HttpResponse response = result.getHead();
            final String entity = result.getBody();
            Assertions.assertNotNull(response);
            Assertions.assertEquals(200, response.getCode());
            Assertions.assertEquals("Hi there", entity);
        }
    }

    @Test
    void testPostIdentityTransferOutOfSequenceResponseNotOK() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

        server.register("/hello", () -> new ImmediateResponseExchangeHandler(500, "Go away"));
        server.configure(new DefaultHttpProcessor(new RequestValidateHost()));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();

        for (int i = 0; i < REQ_NUM; i++) {
            final Future<ClientSessionEndpoint> connectFuture = client.connect("localhost", serverEndpoint.getPort(), TIMEOUT);
            final ClientSessionEndpoint streamEndpoint = connectFuture.get();

            final BasicHttpRequest request = BasicRequestBuilder.post()
                    .setHttpHost(target)
                    .setPath("/hello")
                    .build();
            final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                    new BasicRequestProducer(request, new MultiLineEntityProducer("Hello", 16 * i)),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
            final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());

            streamEndpoint.close();

            Assertions.assertNotNull(result);
            final HttpResponse response = result.getHead();
            final String entity = result.getBody();
            Assertions.assertNotNull(response);
            Assertions.assertEquals(500, response.getCode());
            Assertions.assertEquals("Go away", entity);
        }
    }

    @Test
    void testHTTP10Post() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

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
            request.setVersion(HttpVersion.HTTP_1_0);
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
    void testHTTP11FeaturesDisabledWithHTTP10Requests() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

        server.register("/hello", () -> new SingleLineResponseHandler("Hi back"));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final BasicHttpRequest request = BasicRequestBuilder.post()
                .setHttpHost(target)
                .setPath("/hello")
                .build();
        request.setVersion(HttpVersion.HTTP_1_0);
        final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                new BasicRequestProducer(request, new BasicAsyncEntityProducer(new byte[] {'a', 'b', 'c'}, null, true)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final ExecutionException exception = Assertions.assertThrows(ExecutionException.class, future::get);
        Assertions.assertInstanceOf(ProtocolException.class, exception.getCause());
    }

    @Test
    void testHeadConnectionClose() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

        server.register("/hello", () -> new SingleLineResponseHandler("Hi there"));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        for (int i = 0; i < REQ_NUM; i++) {
            final Future<ClientSessionEndpoint> connectFuture = client.connect(
                    "localhost", serverEndpoint.getPort(), TIMEOUT);
            try (final ClientSessionEndpoint streamEndpoint = connectFuture.get()) {
                final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                        AsyncRequestBuilder.head()
                                .setHttpHost(target)
                                .setPath("/hello")
                                .addHeader(HttpHeaders.CONNECTION, "close")
                                .build(),
                        new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
                final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
                Assertions.assertNotNull(result);
                final HttpResponse response1 = result.getHead();
                Assertions.assertNotNull(response1);
                Assertions.assertEquals(200, response1.getCode());
                Assertions.assertNull(result.getBody());
            }
        }
    }

    @Test
    @Override
    void testExpectationFailed() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

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
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<IOSession> sessionFuture = client.requestSession(
                new HttpHost("localhost", serverEndpoint.getPort()), TIMEOUT, null);
        final IOSession ioSession = sessionFuture.get();
        try (final ClientSessionEndpoint streamEndpoint = new ClientSessionEndpoint(ioSession)) {

            final BasicHttpRequest request1 = BasicRequestBuilder.post()
                    .setHttpHost(target)
                    .setPath("/echo")
                    .addHeader("password", "secret")
                    .build();
            final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                    new BasicRequestProducer(request1, new MultiLineEntityProducer("0123456789abcdef", 1000)),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
            final Message<HttpResponse, String> result1 = future1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertNotNull(result1);
            final HttpResponse response1 = result1.getHead();
            Assertions.assertNotNull(response1);
            Assertions.assertEquals(200, response1.getCode());
            Assertions.assertEquals("All is well", result1.getBody());

            Assertions.assertTrue(ioSession.isOpen());

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

            Assertions.assertTrue(ioSession.isOpen());

            final BasicHttpRequest request3 = BasicRequestBuilder.post()
                    .setHttpHost(target)
                    .setPath("/echo")
                    .addHeader("password", "secret")
                    .build();
            final Future<Message<HttpResponse, String>> future3 = streamEndpoint.execute(
                    new BasicRequestProducer(request3, new MultiLineEntityProducer("0123456789abcdef", 1000)),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
            final Message<HttpResponse, String> result3 = future3.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertNotNull(result3);
            final HttpResponse response3 = result3.getHead();
            Assertions.assertNotNull(response3);
            Assertions.assertEquals(200, response3.getCode());
            Assertions.assertEquals("All is well", result3.getBody());

            Assertions.assertTrue(ioSession.isOpen());

            final BasicHttpRequest request4 = BasicRequestBuilder.post()
                    .setHttpHost(target)
                    .setPath("/echo")
                    .build();
            final Future<Message<HttpResponse, String>> future4 = streamEndpoint.execute(
                    new BasicRequestProducer(request4, AsyncEntityProducers.create("blah")),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
            final Message<HttpResponse, String> result4 = future4.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertNotNull(result4);
            final HttpResponse response4 = result4.getHead();
            Assertions.assertNotNull(response4);
            Assertions.assertEquals(HttpStatus.SC_UNAUTHORIZED, response4.getCode());
            Assertions.assertEquals("You shall not pass", result4.getBody());

            Assertions.assertFalse(ioSession.isOpen());
        }
    }

    @Test
    void testExpectationFailedCloseConnection() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

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
                    final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_UNAUTHORIZED);
                    response.addHeader(HttpHeaders.CONNECTION, HeaderElements.CLOSE);
                    return new BasicResponseProducer(response, "You shall not pass");
                }
            }
        });
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<IOSession> sessionFuture = client.requestSession(
                new HttpHost("localhost", serverEndpoint.getPort()), TIMEOUT, null);
        final IOSession ioSession = sessionFuture.get();
        try (final ClientSessionEndpoint streamEndpoint = new ClientSessionEndpoint(ioSession)) {

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

            Assertions.assertFalse(streamEndpoint.isOpen());
        }
    }

    @Test
    void testMissingExpectContinueAckClientContinues() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

        // Disable 100-continue handshake on the server side
        server.configure(handler -> handler);

        server.register("/hello", () -> new SingleLineResponseHandler("Hi there back"));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.configure(Http1Config.custom()
                .setWaitForContinueTimeout(Timeout.ofMilliseconds(100))
                .build());
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
            Assertions.assertEquals("Hi there back", entity1);
        }
    }

    @Test
    void testSlowResponseConsumer() throws Exception {
        final Http1TestClient client = resources.client();

        client.configure(Http1Config.custom()
                .setBufferSize(256)
                .build());
        client.start();

        super.testSlowResponseConsumer();
    }

    @Test
    void testSlowResponseProducer() throws Exception {
        final Http1TestClient client = resources.client();

        client.configure(Http1Config.custom()
                .setBufferSize(256)
                .build());
        client.start();

        super.testSlowResponseProducer();
    }

    @Test
    void testPipelinedConnectionClose() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

        server.register("/hello*", () -> new SingleLineResponseHandler("Hi back"));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final BasicHttpRequest request1 = BasicRequestBuilder.post()
                .setHttpHost(target)
                .setPath("/hello-1")
                .build();
        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                new BasicRequestProducer(request1, AsyncEntityProducers.create("Hi there")),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);

        final BasicHttpRequest request2 = BasicRequestBuilder.post()
                .setHttpHost(target)
                .setPath("/hello-2")
                .addHeader(HttpHeaders.CONNECTION, "close")
                .build();
        final Future<Message<HttpResponse, String>> future2 = streamEndpoint.execute(
                new BasicRequestProducer(request2,
                        AsyncEntityProducers.create("Hi there")),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);

        final BasicHttpRequest request3 = BasicRequestBuilder.post()
                .setHttpHost(target)
                .setPath("/hello-3")
                .build();
        final Future<Message<HttpResponse, String>> future3 = streamEndpoint.execute(
                new BasicRequestProducer(request3, AsyncEntityProducers.create("Hi there")),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);

        final Message<HttpResponse, String> result1 = future1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(result1);
        final HttpResponse response1 = result1.getHead();
        final String entity1 = result1.getBody();
        Assertions.assertNotNull(response1);
        Assertions.assertEquals(200, response1.getCode());
        Assertions.assertEquals("Hi back", entity1);

        final Message<HttpResponse, String> result2 = future2.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(result2);
        final HttpResponse response2 = result2.getHead();
        final String entity2 = result2.getBody();
        Assertions.assertNotNull(response2);
        Assertions.assertEquals(200, response2.getCode());
        Assertions.assertEquals("Hi back", entity2);

        final Exception exception = Assertions.assertThrows(Exception.class, () ->
                future3.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()));
        Assertions.assertTrue(exception instanceof CancellationException || exception instanceof ExecutionException);

        final BasicHttpRequest request4 = BasicRequestBuilder.post()
                .setHttpHost(target)
                .setPath("/hello-3")
                .build();
        final Future<Message<HttpResponse, String>> future4 = streamEndpoint.execute(
                new BasicRequestProducer(request4, AsyncEntityProducers.create("Hi there")),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Exception exception2 = Assertions.assertThrows(Exception.class, () ->
                future4.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()));
        Assertions.assertTrue(exception2 instanceof CancellationException || exception2 instanceof ExecutionException);
    }

    @Test
    void testPipelinedInvalidRequest() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

        server.register("/hello*", () -> new SingleLineResponseHandler("Hi back"));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final BasicHttpRequest request1 = BasicRequestBuilder.post()
                .setHttpHost(target)
                .setPath("/hello-1")
                .build();
        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                new BasicRequestProducer(request1, AsyncEntityProducers.create("Hi there")),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);

        final BasicHttpRequest request2 = BasicRequestBuilder.post()
                .setHttpHost(target)
                .setPath("/hello-2")
                .addHeader(HttpHeaders.HOST, "blah:blah")
                .build();
        final Future<Message<HttpResponse, String>> future2 = streamEndpoint.execute(
                new BasicRequestProducer(request2,
                        AsyncEntityProducers.create("Hi there")),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);

        final BasicHttpRequest request3 = BasicRequestBuilder.post()
                .setHttpHost(target)
                .setPath("/hello-3")
                .build();
        final Future<Message<HttpResponse, String>> future3 = streamEndpoint.execute(
                new BasicRequestProducer(request3, AsyncEntityProducers.create("Hi there")),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);

        final Message<HttpResponse, String> result1 = future1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(result1);
        final HttpResponse response1 = result1.getHead();
        final String entity1 = result1.getBody();
        Assertions.assertNotNull(response1);
        Assertions.assertEquals(200, response1.getCode());
        Assertions.assertEquals("Hi back", entity1);

        final Message<HttpResponse, String> result2 = future2.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(result2);
        final HttpResponse response2 = result2.getHead();
        final String entity2 = result2.getBody();
        Assertions.assertNotNull(response2);
        Assertions.assertEquals(400, response2.getCode());
        Assertions.assertFalse(entity2.isEmpty());


        final Exception exception = Assertions.assertThrows(Exception.class, () ->
                future3.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()));
        Assertions.assertTrue(exception instanceof CancellationException || exception instanceof ExecutionException);
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
    void testTruncatedChunk() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

        final InetSocketAddress serverEndpoint = server.startExecution(new InternalServerHttp1EventHandlerFactory(
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
                null,
                null,
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
                    final Http1StreamListener streamListener,
                    final Callback<Exception> exceptionCallback) {
                return new ServerHttp1StreamDuplexer(ioSession, httpProcessor, exchangeHandlerFactory,
                        scheme.id,
                        http1Config, connectionConfig, connectionReuseStrategy,
                        incomingMessageParser, outgoingMessageWriter,
                        incomingContentStrategy, outgoingContentStrategy,
                        streamListener,
                        exceptionCallback) {

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

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final BasicHttpRequest request = BasicRequestBuilder.get()
                .setHttpHost(target)
                .setPath("/hello")
                .build();
        final AsyncRequestProducer requestProducer = new BasicRequestProducer(request, null);
        final StringAsyncEntityConsumer entityConsumer = new StringAsyncEntityConsumer() {

            @Override
            public void releaseResources() {
                // Do not clear internal content buffer
            }

        };
        final BasicResponseConsumer<String> responseConsumer = new BasicResponseConsumer<>(entityConsumer);
        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(requestProducer, responseConsumer, null);
        final ExecutionException exception = Assertions.assertThrows(ExecutionException.class, () ->
                future1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()));
        final Throwable cause = exception.getCause();
        Assertions.assertInstanceOf(MalformedChunkCodingException.class, cause);
        Assertions.assertEquals("garbage", entityConsumer.generateContent());
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "POST"})
    void testHeaderTooLarge(final String method) throws Exception {
        final Http1TestServer server = resources.server();

        server.configure(Http1Config.custom()
                .setMaxLineLength(100)
                .build());
        super.testHeaderTooLarge(method);
    }

    @Test
    void testInvalidRequestMessage() throws Exception {
        final Http1Config http1Config = Http1Config.DEFAULT;
        final Http1TestServer server = resources.server();
        server.configure(http1Config);
        server.configure(() -> new DefaultHttpRequestParser<HttpRequest>(http1Config, DefaultHttpRequestFactory.INSTANCE) {

            @Override
            protected HttpRequest createMessage(final CharArrayBuffer buffer) throws HttpException {
                throw new RuntimeException("Ka-boom");
            }

        });

        server.register("/hello", () -> new SingleLineResponseHandler("Hi there"));

        final InetSocketAddress serverEndpoint = server.start();
        final HttpHost target = target(serverEndpoint);

        final Http1TestClient client = resources.client();

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(
                "localhost", serverEndpoint.getPort(), TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final BasicHttpRequest request = BasicRequestBuilder.get()
                .setHttpHost(target)
                .setPath("/hello")
                .build();

        final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                new BasicRequestProducer(request, null),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final ExecutionException executionException = Assertions.assertThrows(ExecutionException.class, () ->
                future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()));
        Assertions.assertInstanceOf(ConnectionClosedException.class, executionException.getCause());
    }

    @Test
    void testInvalidProtocolVersion() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

        server.register("/hello", () -> new SingleLineResponseHandler("Hi there"));
        final InetSocketAddress serverEndpoint = server.start();
        final HttpHost target = target(serverEndpoint);

        final LineFormatter lineFormatter = BasicLineFormatter.INSTANCE;
        client.configure(() -> new AbstractMessageWriter<HttpRequest>(lineFormatter) {

            @Override
            protected void writeHeadLine(final HttpRequest message, final CharArrayBuffer lineBuf) throws IOException {
                lineBuf.clear();
                lineFormatter.formatRequestLine(lineBuf, new RequestLine(
                    message.getMethod(),
                    message.getRequestUri(),
                    new HttpVersion(2, 1)));
            }

        });
        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(
            "localhost", serverEndpoint.getPort(), TIMEOUT);
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
        final HttpResponse response = result.getHead();
        Assertions.assertNotNull(response);
        Assertions.assertEquals(505, response.getCode());
    }

}
