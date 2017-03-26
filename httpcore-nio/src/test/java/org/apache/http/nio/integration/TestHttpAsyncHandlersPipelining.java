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

package org.apache.http.nio.integration;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.http.ConnectionClosedException;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.nio.protocol.BasicAsyncRequestConsumer;
import org.apache.http.nio.protocol.BasicAsyncRequestHandler;
import org.apache.http.nio.protocol.BasicAsyncRequestProducer;
import org.apache.http.nio.protocol.BasicAsyncResponseConsumer;
import org.apache.http.nio.protocol.BasicAsyncResponseProducer;
import org.apache.http.nio.protocol.HttpAsyncExchange;
import org.apache.http.nio.protocol.HttpAsyncRequestConsumer;
import org.apache.http.nio.protocol.HttpAsyncRequestHandler;
import org.apache.http.nio.protocol.HttpAsyncRequestProducer;
import org.apache.http.nio.protocol.HttpAsyncResponseConsumer;
import org.apache.http.nio.reactor.ListenerEndpoint;
import org.apache.http.nio.testserver.HttpCoreNIOTestBase;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.ImmutableHttpProcessor;
import org.apache.http.protocol.RequestConnControl;
import org.apache.http.protocol.RequestContent;
import org.apache.http.protocol.RequestTargetHost;
import org.apache.http.protocol.RequestUserAgent;
import org.apache.http.util.EntityUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * HttpCore NIO integration tests for pipelined request processing.
 */
@RunWith(Parameterized.class)
public class TestHttpAsyncHandlersPipelining extends HttpCoreNIOTestBase {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> protocols() {
        return Arrays.asList(new Object[][]{
                {ProtocolScheme.http},
                {ProtocolScheme.https},
        });
    }

    public TestHttpAsyncHandlersPipelining(final ProtocolScheme scheme) {
        super(scheme);
    }

    public static final HttpProcessor DEFAULT_HTTP_PROC = new ImmutableHttpProcessor(
            new RequestContent(),
            new RequestTargetHost(),
            new RequestConnControl(),
            new RequestUserAgent("TEST-CLIENT/1.1"));

    @Before
    public void setUp() throws Exception {
        initServer();
        initClient();
    }

    @After
    public void tearDown() throws Exception {
        shutDownClient();
        shutDownServer();
    }

    private HttpHost start() throws Exception {
        this.server.start();
        this.client.setHttpProcessor(DEFAULT_HTTP_PROC);
        this.client.start();

        final ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();

        final InetSocketAddress address = (InetSocketAddress) endpoint.getAddress();
        return new HttpHost("localhost", address.getPort(), getScheme().name());
    }

    private static String createRequestUri(final String pattern, final int count) {
        return pattern + "x" + count;
    }

    private static String createExpectedString(final String pattern, final int count) {
        final StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < count; i++) {
            buffer.append(pattern);
        }
        return buffer.toString();
    }

    @Test
    public void testHttpGets() throws Exception {
        this.server.registerHandler("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));
        final HttpHost target = start();

        this.client.setMaxPerRoute(3);
        this.client.setMaxTotal(3);

        final String pattern = RndTestPatternGenerator.generateText();
        final int count = RndTestPatternGenerator.generateCount(1000);

        final String expectedPattern = createExpectedString(pattern, count);

        final Queue<Future<List<HttpResponse>>> queue = new ConcurrentLinkedQueue<Future<List<HttpResponse>>>();
        for (int i = 0; i < 10; i++) {
            final String requestUri = createRequestUri(pattern, count);
            final Future<List<HttpResponse>> future = this.client.executePipelined(target,
                    new BasicHttpRequest("GET", requestUri),
                    new BasicHttpRequest("GET", requestUri),
                    new BasicHttpRequest("GET", requestUri));
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            final Future<List<HttpResponse>> future = queue.remove();
            final List<HttpResponse> responses = future.get();
            Assert.assertNotNull(responses);
            Assert.assertEquals(3, responses.size());
            for (final HttpResponse response: responses) {
                Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
                Assert.assertEquals(expectedPattern, EntityUtils.toString(response.getEntity()));
            }
        }
    }

    @Test
    public void testHttpHeads() throws Exception {
        this.server.registerHandler("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));
        final HttpHost target = start();

        this.client.setMaxPerRoute(3);
        this.client.setMaxTotal(3);

        final String pattern = RndTestPatternGenerator.generateText();
        final int count = RndTestPatternGenerator.generateCount(1000);

        final Queue<Future<List<HttpResponse>>> queue = new ConcurrentLinkedQueue<Future<List<HttpResponse>>>();
        for (int i = 0; i < 10; i++) {
            final String requestUri = createRequestUri(pattern, count);
            final HttpRequest head1 = new BasicHttpRequest("HEAD", requestUri);
            final HttpRequest head2 = new BasicHttpRequest("HEAD", requestUri);
            final BasicHttpEntityEnclosingRequest post1 = new BasicHttpEntityEnclosingRequest("POST", requestUri);
            post1.setEntity(new NStringEntity("stuff", ContentType.TEXT_PLAIN));
            final Future<List<HttpResponse>> future = this.client.executePipelined(target, head1, head2, post1);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            final Future<List<HttpResponse>> future = queue.remove();
            final List<HttpResponse> responses = future.get();
            Assert.assertNotNull(responses);
            Assert.assertEquals(3, responses.size());
            for (final HttpResponse response: responses) {
                Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
            }
        }
    }

    @Test
    public void testHttpPosts() throws Exception {
        this.server.registerHandler("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));
        final HttpHost target = start();

        this.client.setMaxPerRoute(3);
        this.client.setMaxTotal(3);

        final String pattern = RndTestPatternGenerator.generateText();
        final int count = RndTestPatternGenerator.generateCount(1000);

        final String expectedPattern = createExpectedString(pattern, count);

        final Queue<Future<List<HttpResponse>>> queue = new ConcurrentLinkedQueue<Future<List<HttpResponse>>>();
        for (int i = 0; i < 10; i++) {
            final String requestUri = createRequestUri(pattern, count);
            final HttpEntityEnclosingRequest request1 = new BasicHttpEntityEnclosingRequest("POST", requestUri);
            final NStringEntity entity1 = new NStringEntity(expectedPattern, ContentType.DEFAULT_TEXT);
            entity1.setChunked(RndTestPatternGenerator.generateBoolean());
            request1.setEntity(entity1);
            final HttpEntityEnclosingRequest request2 = new BasicHttpEntityEnclosingRequest("POST", requestUri);
            final NStringEntity entity2 = new NStringEntity(expectedPattern, ContentType.DEFAULT_TEXT);
            entity2.setChunked(RndTestPatternGenerator.generateBoolean());
            request2.setEntity(entity2);
            final HttpEntityEnclosingRequest request3 = new BasicHttpEntityEnclosingRequest("POST", requestUri);
            final NStringEntity entity3 = new NStringEntity(expectedPattern, ContentType.DEFAULT_TEXT);
            entity3.setChunked(RndTestPatternGenerator.generateBoolean());
            request3.setEntity(entity3);
            final Future<List<HttpResponse>> future = this.client.executePipelined(target,
                    request1, request2, request3);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            final Future<List<HttpResponse>> future = queue.remove();
            final List<HttpResponse> responses = future.get();
            Assert.assertNotNull(responses);
            Assert.assertEquals(3, responses.size());
            for (final HttpResponse response: responses) {
                Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
                Assert.assertEquals(expectedPattern, EntityUtils.toString(response.getEntity()));
            }
        }
    }

    @Test
    public void testHttpDelayedResponse() throws Exception {

        class DelayedRequestHandler implements HttpAsyncRequestHandler<HttpRequest> {

            private final SimpleRequestHandler requestHandler;

            public DelayedRequestHandler() {
                super();
                this.requestHandler = new SimpleRequestHandler();
            }

            @Override
            public HttpAsyncRequestConsumer<HttpRequest> processRequest(
                    final HttpRequest request,
                    final HttpContext context) {
                return new BasicAsyncRequestConsumer();
            }

            @Override
            public void handle(
                    final HttpRequest request,
                    final HttpAsyncExchange httpexchange,
                    final HttpContext context) throws HttpException, IOException {
                final BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
                new Thread() {
                    @Override
                    public void run() {
                        // Wait a bit, to make sure this is delayed.
                        try { Thread.sleep(100); } catch(final InterruptedException ie) {}
                        // Set the entity after delaying...
                        try {
                            requestHandler.handle(request, response, context);
                        } catch (final Exception ex) {
                            response.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
                        }
                        httpexchange.submitResponse(new BasicAsyncResponseProducer(response));
                    }
                }.start();
            }

        }

        this.server.registerHandler("*", new DelayedRequestHandler());
        final HttpHost target = start();

        this.client.setMaxPerRoute(3);
        this.client.setMaxTotal(3);

        final String pattern1 = RndTestPatternGenerator.generateText();
        final String pattern2 = RndTestPatternGenerator.generateText();
        final String pattern3 = RndTestPatternGenerator.generateText();
        final int count = RndTestPatternGenerator.generateCount(1000);

        final String expectedPattern1 = createExpectedString(pattern1, count);
        final String expectedPattern2 = createExpectedString(pattern2, count);
        final String expectedPattern3 = createExpectedString(pattern3, count);

        final Queue<Future<List<HttpResponse>>> queue = new ConcurrentLinkedQueue<Future<List<HttpResponse>>>();
        for (int i = 0; i < 1; i++) {
            final HttpRequest request1 = new BasicHttpRequest("GET", createRequestUri(pattern1, count));
            final HttpEntityEnclosingRequest request2 = new BasicHttpEntityEnclosingRequest("POST",
                    createRequestUri(pattern2, count));
            final NStringEntity entity2 = new NStringEntity(expectedPattern2, ContentType.DEFAULT_TEXT);
            entity2.setChunked(RndTestPatternGenerator.generateBoolean());
            request2.setEntity(entity2);
            final HttpEntityEnclosingRequest request3 = new BasicHttpEntityEnclosingRequest("POST",
                    createRequestUri(pattern3, count));
            final NStringEntity entity3 = new NStringEntity(expectedPattern3, ContentType.DEFAULT_TEXT);
            entity3.setChunked(RndTestPatternGenerator.generateBoolean());
            request3.setEntity(entity3);
            final Future<List<HttpResponse>> future = this.client.executePipelined(target,
                    request1, request2, request3);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            final Future<List<HttpResponse>> future = queue.remove();
            final List<HttpResponse> responses = future.get();
            Assert.assertNotNull(responses);
            Assert.assertEquals(3, responses.size());
            for (final HttpResponse response: responses) {
                Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
            }
            Assert.assertEquals(expectedPattern1, EntityUtils.toString(responses.get(0).getEntity()));
            Assert.assertEquals(expectedPattern2, EntityUtils.toString(responses.get(1).getEntity()));
            Assert.assertEquals(expectedPattern3, EntityUtils.toString(responses.get(2).getEntity()));
        }
    }

    @Test
    public void testUnexpectedConnectionClosure() throws Exception {
        this.server.registerHandler("*", new BasicAsyncRequestHandler(new HttpRequestHandler() {

            @Override
            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                response.setStatusCode(HttpStatus.SC_OK);
                response.setEntity(new StringEntity("all is well", ContentType.TEXT_PLAIN));
            }

        }));
        this.server.registerHandler("/boom", new BasicAsyncRequestHandler(new HttpRequestHandler() {

            @Override
            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                response.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
                response.setHeader(HttpHeaders.CONNECTION, "Close");
                response.setEntity(new StringEntity("boooooom!!!!!", ContentType.TEXT_PLAIN));
            }

        }));
        final HttpHost target = start();

        this.client.setMaxPerRoute(3);
        this.client.setMaxTotal(3);

        for (int i = 0; i < 3; i++) {

            final HttpAsyncRequestProducer p1 = new BasicAsyncRequestProducer(target, new BasicHttpRequest("GET", "/"));
            final HttpAsyncRequestProducer p2 = new BasicAsyncRequestProducer(target, new BasicHttpRequest("GET", "/"));
            final HttpAsyncRequestProducer p3 = new BasicAsyncRequestProducer(target, new BasicHttpRequest("GET", "/boom"));
            final List<HttpAsyncRequestProducer> requestProducers = new ArrayList<HttpAsyncRequestProducer>();
            requestProducers.add(p1);
            requestProducers.add(p2);
            requestProducers.add(p3);

            final HttpAsyncResponseConsumer<HttpResponse> c1 = new BasicAsyncResponseConsumer();
            final HttpAsyncResponseConsumer<HttpResponse> c2 = new BasicAsyncResponseConsumer();
            final HttpAsyncResponseConsumer<HttpResponse> c3 = new BasicAsyncResponseConsumer();
            final List<HttpAsyncResponseConsumer<HttpResponse>> responseConsumers = new ArrayList<HttpAsyncResponseConsumer<HttpResponse>>();
            responseConsumers.add(c1);
            responseConsumers.add(c2);
            responseConsumers.add(c3);

            final Future<List<HttpResponse>> future = this.client.executePipelined(target, requestProducers, responseConsumers, null, null);
            try {
                future.get();
            } catch (final ExecutionException ex) {
                final Throwable cause = ex.getCause();
                Assert.assertTrue(cause instanceof ConnectionClosedException);
            }

            Assert.assertTrue(c1.isDone());
            Assert.assertNotNull(c1.getResult());
            Assert.assertTrue(c2.isDone());
            Assert.assertNotNull(c2.getResult());
            Assert.assertTrue(c2.isDone());
            Assert.assertNotNull(c3.getResult());
        }
    }

}
