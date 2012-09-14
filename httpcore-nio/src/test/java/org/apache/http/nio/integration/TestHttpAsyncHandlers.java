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
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;

import org.apache.http.HttpCoreNIOTestBase;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.LoggingClientConnectionFactory;
import org.apache.http.LoggingServerConnectionFactory;
import org.apache.http.ProtocolVersion;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.nio.DefaultNHttpClientConnection;
import org.apache.http.impl.nio.DefaultNHttpServerConnection;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.nio.NHttpConnectionFactory;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.nio.protocol.BasicAsyncRequestConsumer;
import org.apache.http.nio.protocol.BasicAsyncRequestHandler;
import org.apache.http.nio.protocol.BasicAsyncRequestProducer;
import org.apache.http.nio.protocol.BasicAsyncResponseConsumer;
import org.apache.http.nio.protocol.BasicAsyncResponseProducer;
import org.apache.http.nio.protocol.HttpAsyncExchange;
import org.apache.http.nio.protocol.HttpAsyncExpectationVerifier;
import org.apache.http.nio.protocol.HttpAsyncRequestConsumer;
import org.apache.http.nio.protocol.HttpAsyncRequestExecutor;
import org.apache.http.nio.protocol.HttpAsyncRequestHandler;
import org.apache.http.nio.protocol.HttpAsyncRequestHandlerRegistry;
import org.apache.http.nio.protocol.HttpAsyncRequestHandlerResolver;
import org.apache.http.nio.protocol.HttpAsyncRequester;
import org.apache.http.nio.protocol.HttpAsyncService;
import org.apache.http.nio.reactor.IOReactorStatus;
import org.apache.http.nio.reactor.ListenerEndpoint;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.ImmutableHttpProcessor;
import org.apache.http.protocol.RequestConnControl;
import org.apache.http.protocol.RequestExpectContinue;
import org.apache.http.protocol.RequestTargetHost;
import org.apache.http.protocol.RequestUserAgent;
import org.apache.http.util.EntityUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * HttpCore NIO integration tests for async handlers.
 */
public class TestHttpAsyncHandlers extends HttpCoreNIOTestBase {

    @Before
    public void setUp() throws Exception {
        initServer();
        initClient();
        initConnPool();
    }

    @After
    public void tearDown() throws Exception {
        shutDownConnPool();
        shutDownClient();
        shutDownServer();
    }

    @Override
    protected NHttpConnectionFactory<DefaultNHttpServerConnection> createServerConnectionFactory(
            final HttpParams params) throws Exception {
        return new LoggingServerConnectionFactory(params);
    }

    @Override
    protected NHttpConnectionFactory<DefaultNHttpClientConnection> createClientConnectionFactory(
            final HttpParams params) throws Exception {
        return new LoggingClientConnectionFactory(params);
    }

    private InetSocketAddress start(
            final HttpAsyncRequestHandlerResolver requestHandlerResolver,
            final HttpAsyncExpectationVerifier expectationVerifier) throws Exception {
        HttpAsyncService serviceHandler = new HttpAsyncService(
                this.serverHttpProc,
                new DefaultConnectionReuseStrategy(),
                new DefaultHttpResponseFactory(),
                requestHandlerResolver,
                expectationVerifier,
                this.serverParams);
        HttpAsyncRequestExecutor clientHandler = new HttpAsyncRequestExecutor();
        this.server.start(serviceHandler);
        this.client.start(clientHandler);

        ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();

        Assert.assertEquals("Test server status", IOReactorStatus.ACTIVE, this.server.getStatus());
        return (InetSocketAddress) endpoint.getAddress();
    }

    private static String createRequestUri(final String pattern, int count) {
        return pattern + "x" + count;
    }

    private static String createExpectedString(final String pattern, int count) {
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < count; i++) {
            buffer.append(pattern);
        }
        return buffer.toString();
    }

    @Test
    public void testHttpGets() throws Exception {
        HttpAsyncRequestHandlerRegistry registry = new HttpAsyncRequestHandlerRegistry();
        registry.register("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));
        InetSocketAddress address = start(registry, null);

        this.connpool.setDefaultMaxPerRoute(3);
        this.connpool.setMaxTotal(3);

        String pattern = RndTestPatternGenerator.generateText();
        int count = RndTestPatternGenerator.generateCount(1000);

        HttpHost target = new HttpHost("localhost", address.getPort());
        String expectedPattern = createExpectedString(pattern, count);

        Queue<Future<HttpResponse>> queue = new ConcurrentLinkedQueue<Future<HttpResponse>>();
        for (int i = 0; i < 30; i++) {
            BasicHttpRequest request = new BasicHttpRequest("GET", createRequestUri(pattern, count));
            Future<HttpResponse> future = this.executor.execute(
                    new BasicAsyncRequestProducer(target, request),
                    new BasicAsyncResponseConsumer(),
                    this.connpool);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            Future<HttpResponse> future = queue.remove();
            HttpResponse response = future.get();
            Assert.assertNotNull(response);
            Assert.assertEquals(expectedPattern, EntityUtils.toString(response.getEntity()));
        }
    }

    @Test
    public void testHttpHeads() throws Exception {
        HttpAsyncRequestHandlerRegistry registry = new HttpAsyncRequestHandlerRegistry();
        registry.register("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));
        InetSocketAddress address = start(registry, null);

        this.connpool.setDefaultMaxPerRoute(3);
        this.connpool.setMaxTotal(3);

        String pattern = RndTestPatternGenerator.generateText();
        int count = RndTestPatternGenerator.generateCount(1000);

        HttpHost target = new HttpHost("localhost", address.getPort());

        Queue<Future<HttpResponse>> queue = new ConcurrentLinkedQueue<Future<HttpResponse>>();
        for (int i = 0; i < 30; i++) {
            BasicHttpRequest request = new BasicHttpRequest("HEAD", createRequestUri(pattern, count));
            Future<HttpResponse> future = this.executor.execute(
                    new BasicAsyncRequestProducer(target, request),
                    new BasicAsyncResponseConsumer(),
                    this.connpool);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            Future<HttpResponse> future = queue.remove();
            HttpResponse response = future.get();
            Assert.assertNotNull(response);
            Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        }
    }

    @Test
    public void testHttpPostsWithContentLength() throws Exception {
        HttpAsyncRequestHandlerRegistry registry = new HttpAsyncRequestHandlerRegistry();
        registry.register("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));
        InetSocketAddress address = start(registry, null);

        this.connpool.setDefaultMaxPerRoute(3);
        this.connpool.setMaxTotal(3);

        String pattern = RndTestPatternGenerator.generateText();
        int count = RndTestPatternGenerator.generateCount(1000);

        HttpHost target = new HttpHost("localhost", address.getPort());
        String expectedPattern = createExpectedString(pattern, count);

        Queue<Future<HttpResponse>> queue = new ConcurrentLinkedQueue<Future<HttpResponse>>();
        for (int i = 0; i < 30; i++) {
            BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest(
                    "POST", createRequestUri(pattern, count));
            NStringEntity entity = new NStringEntity(expectedPattern, ContentType.DEFAULT_TEXT);
            request.setEntity(entity);
            Future<HttpResponse> future = this.executor.execute(
                    new BasicAsyncRequestProducer(target, request),
                    new BasicAsyncResponseConsumer(),
                    this.connpool);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            Future<HttpResponse> future = queue.remove();
            HttpResponse response = future.get();
            Assert.assertNotNull(response);
            Assert.assertEquals(expectedPattern, EntityUtils.toString(response.getEntity()));
        }
    }

    @Test
    public void testHttpPostsChunked() throws Exception {
        HttpAsyncRequestHandlerRegistry registry = new HttpAsyncRequestHandlerRegistry();
        registry.register("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));
        InetSocketAddress address = start(registry, null);

        this.connpool.setDefaultMaxPerRoute(3);
        this.connpool.setMaxTotal(3);

        String pattern = RndTestPatternGenerator.generateText();
        int count = RndTestPatternGenerator.generateCount(1000);

        HttpHost target = new HttpHost("localhost", address.getPort());
        String expectedPattern = createExpectedString(pattern, count);

        Queue<Future<HttpResponse>> queue = new ConcurrentLinkedQueue<Future<HttpResponse>>();
        for (int i = 0; i < 30; i++) {
            BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest(
                    "POST", createRequestUri(pattern, count));
            NStringEntity entity = new NStringEntity(expectedPattern, ContentType.DEFAULT_TEXT);
            entity.setChunked(true);
            request.setEntity(entity);
            Future<HttpResponse> future = this.executor.execute(
                    new BasicAsyncRequestProducer(target, request),
                    new BasicAsyncResponseConsumer(),
                    this.connpool);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            Future<HttpResponse> future = queue.remove();
            HttpResponse response = future.get();
            Assert.assertNotNull(response);
            Assert.assertEquals(expectedPattern, EntityUtils.toString(response.getEntity()));
        }
    }

    @Test
    public void testHttpPostsHTTP10() throws Exception {
        HttpAsyncRequestHandlerRegistry registry = new HttpAsyncRequestHandlerRegistry();
        registry.register("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));
        InetSocketAddress address = start(registry, null);

        this.connpool.setDefaultMaxPerRoute(3);
        this.connpool.setMaxTotal(3);

        String pattern = RndTestPatternGenerator.generateText();
        int count = RndTestPatternGenerator.generateCount(1000);

        HttpHost target = new HttpHost("localhost", address.getPort());
        String expectedPattern = createExpectedString(pattern, count);

        Queue<Future<HttpResponse>> queue = new ConcurrentLinkedQueue<Future<HttpResponse>>();
        for (int i = 0; i < 30; i++) {
            BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest(
                    "POST", createRequestUri(pattern, count), HttpVersion.HTTP_1_0);
            NStringEntity entity = new NStringEntity(expectedPattern, ContentType.DEFAULT_TEXT);
            request.setEntity(entity);
            Future<HttpResponse> future = this.executor.execute(
                    new BasicAsyncRequestProducer(target, request),
                    new BasicAsyncResponseConsumer(),
                    this.connpool);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            Future<HttpResponse> future = queue.remove();
            HttpResponse response = future.get();
            Assert.assertNotNull(response);
            Assert.assertEquals(expectedPattern, EntityUtils.toString(response.getEntity()));
        }
    }

    @Test
    public void testHttpPostsNoEntity() throws Exception {
        HttpAsyncRequestHandlerRegistry registry = new HttpAsyncRequestHandlerRegistry();
        registry.register("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));
        InetSocketAddress address = start(registry, null);

        this.connpool.setDefaultMaxPerRoute(3);
        this.connpool.setMaxTotal(3);

        String pattern = RndTestPatternGenerator.generateText();
        int count = RndTestPatternGenerator.generateCount(1000);

        HttpHost target = new HttpHost("localhost", address.getPort());

        Queue<Future<HttpResponse>> queue = new ConcurrentLinkedQueue<Future<HttpResponse>>();
        for (int i = 0; i < 30; i++) {
            BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest(
                    "POST", createRequestUri(pattern, count));
            request.setEntity(null);
            Future<HttpResponse> future = this.executor.execute(
                    new BasicAsyncRequestProducer(target, request),
                    new BasicAsyncResponseConsumer(),
                    this.connpool);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            Future<HttpResponse> future = queue.remove();
            HttpResponse response = future.get();
            Assert.assertNotNull(response);
            Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        }
    }

    @Test
    public void testHttpPostNoContentLength() throws Exception {
        HttpAsyncRequestHandlerRegistry registry = new HttpAsyncRequestHandlerRegistry();
        registry.register("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));
        
        this.clientHttpProc = new ImmutableHttpProcessor(new HttpRequestInterceptor[] {
                new RequestTargetHost(),
                new RequestConnControl(),
                new RequestUserAgent(),
                new RequestExpectContinue()});
        
        this.executor = new HttpAsyncRequester(
                this.clientHttpProc,
                new DefaultConnectionReuseStrategy(),
                this.clientParams);
        
        InetSocketAddress address = start(registry, null);

        this.connpool.setDefaultMaxPerRoute(3);
        this.connpool.setMaxTotal(3);

        String pattern = RndTestPatternGenerator.generateText();
        int count = RndTestPatternGenerator.generateCount(1000);

        HttpHost target = new HttpHost("localhost", address.getPort());

        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest(
                "POST", createRequestUri(pattern, count));
        request.setEntity(null);

        Future<HttpResponse> future = this.executor.execute(
                new BasicAsyncRequestProducer(target, request),
                new BasicAsyncResponseConsumer(),
                this.connpool);

        HttpResponse response = future.get();
        Assert.assertNotNull(response);
        Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
    }

    @Test
    public void testHttpPostIdentity() throws Exception {
        HttpAsyncRequestHandlerRegistry registry = new HttpAsyncRequestHandlerRegistry();
        registry.register("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));
        
        this.clientHttpProc = new ImmutableHttpProcessor(new HttpRequestInterceptor[] {
                new HttpRequestInterceptor() {
                    
                    public void process(
                            final HttpRequest request, 
                            final HttpContext context) throws HttpException, IOException {
                        request.addHeader(HTTP.TRANSFER_ENCODING, "identity");
                    }

                },
                new RequestTargetHost(),
                new RequestConnControl(),
                new RequestUserAgent(),
                new RequestExpectContinue()});
        
        this.executor = new HttpAsyncRequester(
                this.clientHttpProc,
                new DefaultConnectionReuseStrategy(),
                this.clientParams);
        
        InetSocketAddress address = start(registry, null);

        this.connpool.setDefaultMaxPerRoute(3);
        this.connpool.setMaxTotal(3);

        String pattern = RndTestPatternGenerator.generateText();
        int count = RndTestPatternGenerator.generateCount(1000);

        HttpHost target = new HttpHost("localhost", address.getPort());

        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest(
                "POST", createRequestUri(pattern, count));
        request.setEntity(null);

        Future<HttpResponse> future = this.executor.execute(
                new BasicAsyncRequestProducer(target, request),
                new BasicAsyncResponseConsumer(),
                this.connpool);

        HttpResponse response = future.get();
        Assert.assertNotNull(response);
        Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
    }

    @Test
    public void testHttpPostsWithExpectContinue() throws Exception {
        HttpAsyncRequestHandlerRegistry registry = new HttpAsyncRequestHandlerRegistry();
        registry.register("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));
        InetSocketAddress address = start(registry, null);

        this.connpool.setDefaultMaxPerRoute(3);
        this.connpool.setMaxTotal(3);

        String pattern = RndTestPatternGenerator.generateText();
        int count = RndTestPatternGenerator.generateCount(1000);

        HttpHost target = new HttpHost("localhost", address.getPort());
        String expectedPattern = createExpectedString(pattern, count);

        Queue<Future<HttpResponse>> queue = new ConcurrentLinkedQueue<Future<HttpResponse>>();
        for (int i = 0; i < 30; i++) {
            BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest(
                    "POST", createRequestUri(pattern, count));
            request.getParams().setBooleanParameter(CoreProtocolPNames.USE_EXPECT_CONTINUE, true);
            NStringEntity entity = new NStringEntity(expectedPattern, ContentType.DEFAULT_TEXT);
            request.setEntity(entity);
            Future<HttpResponse> future = this.executor.execute(
                    new BasicAsyncRequestProducer(target, request),
                    new BasicAsyncResponseConsumer(),
                    this.connpool);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            Future<HttpResponse> future = queue.remove();
            HttpResponse response = future.get();
            Assert.assertNotNull(response);
            Assert.assertEquals(expectedPattern, EntityUtils.toString(response.getEntity()));
        }
    }

    @Test
    public void testHttpPostsWithExpectationVerification() throws Exception {
        HttpAsyncExpectationVerifier expectationVerifier = new HttpAsyncExpectationVerifier() {

            public void verify(
                    final HttpAsyncExchange httpexchange,
                    final HttpContext context) throws HttpException {
                HttpRequest request = httpexchange.getRequest();
                ProtocolVersion ver = request.getRequestLine().getProtocolVersion();
                String s = request.getRequestLine().getUri();
                if (!s.equals("AAAAAx10")) {
                    if (!ver.lessEquals(HttpVersion.HTTP_1_1)) {
                        ver = HttpVersion.HTTP_1_1;
                    }
                    BasicHttpResponse response = new BasicHttpResponse(ver,
                            HttpStatus.SC_EXPECTATION_FAILED, "Expectation failed");
                    response.setEntity(new NStringEntity("Expectation failed", ContentType.TEXT_PLAIN));
                    httpexchange.submitResponse(new BasicAsyncResponseProducer(response));
                } else {
                    httpexchange.submitResponse();
                }
            }

        };

        HttpAsyncRequestHandlerRegistry registry = new HttpAsyncRequestHandlerRegistry();
        registry.register("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));
        InetSocketAddress address = start(registry, expectationVerifier);

        BasicHttpEntityEnclosingRequest request1 = new BasicHttpEntityEnclosingRequest(
                "POST", createRequestUri("AAAAA", 10));
        request1.getParams().setBooleanParameter(CoreProtocolPNames.USE_EXPECT_CONTINUE, true);
        request1.setEntity(new NStringEntity(createExpectedString("AAAAA", 10)));
        BasicHttpEntityEnclosingRequest request2 = new BasicHttpEntityEnclosingRequest(
                "POST", createRequestUri("AAAAA", 10));
        request2.getParams().setBooleanParameter(CoreProtocolPNames.USE_EXPECT_CONTINUE, true);
        request2.setEntity(new NStringEntity(createExpectedString("AAAAA", 10)));
        BasicHttpEntityEnclosingRequest request3 = new BasicHttpEntityEnclosingRequest(
                "POST", createRequestUri("BBBBB", 10));
        request3.getParams().setBooleanParameter(CoreProtocolPNames.USE_EXPECT_CONTINUE, true);
        request3.setEntity(new NStringEntity(createExpectedString("BBBBB", 10)));

        HttpRequest[] requests = new HttpRequest[] { request1, request2, request3 };

        HttpHost target = new HttpHost("localhost", address.getPort());

        Queue<Future<HttpResponse>> queue = new ConcurrentLinkedQueue<Future<HttpResponse>>();
        for (int i = 0; i < requests.length; i++) {
            Future<HttpResponse> future = this.executor.execute(
                    new BasicAsyncRequestProducer(target, requests[i]),
                    new BasicAsyncResponseConsumer(),
                    this.connpool);
            queue.add(future);
        }

        Future<HttpResponse> future1 = queue.remove();
        HttpResponse response1 = future1.get();
        Assert.assertEquals(HttpStatus.SC_OK, response1.getStatusLine().getStatusCode());

        Future<HttpResponse> future2 = queue.remove();
        HttpResponse response2 = future2.get();
        Assert.assertEquals(HttpStatus.SC_OK, response2.getStatusLine().getStatusCode());

        Future<HttpResponse> future3 = queue.remove();
        HttpResponse response3 = future3.get();
        Assert.assertEquals(HttpStatus.SC_EXPECTATION_FAILED, response3.getStatusLine().getStatusCode());
    }

    @Test
    public void testHttpHeadsDelayedResponse() throws Exception {

        class DelayedRequestHandler implements HttpAsyncRequestHandler<HttpRequest> {

            private final SimpleRequestHandler requestHandler;

            public DelayedRequestHandler() {
                super();
                this.requestHandler = new SimpleRequestHandler();
            }

            public HttpAsyncRequestConsumer<HttpRequest> processRequest(
                    final HttpRequest request,
                    final HttpContext context) {
                return new BasicAsyncRequestConsumer();
            }

            public void handle(
                    final HttpRequest request,
                    final HttpAsyncExchange httpexchange,
                    final HttpContext context) throws HttpException, IOException {
                ProtocolVersion ver = request.getRequestLine().getProtocolVersion();
                if (!ver.lessEquals(HttpVersion.HTTP_1_1)) {
                    ver = HttpVersion.HTTP_1_1;
                }
                final BasicHttpResponse response = new BasicHttpResponse(ver, HttpStatus.SC_OK, "OK");
                new Thread() {
                    @Override
                    public void run() {
                        // Wait a bit, to make sure this is delayed.
                        try { Thread.sleep(100); } catch(InterruptedException ie) {}
                        // Set the entity after delaying...
                        try {
                            requestHandler.handle(request, response, context);
                        } catch (Exception ex) {
                            response.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
                        }
                        httpexchange.submitResponse(new BasicAsyncResponseProducer(response));
                    }
                }.start();
            }

        }

        HttpAsyncRequestHandlerRegistry registry = new HttpAsyncRequestHandlerRegistry();
        registry.register("*", new DelayedRequestHandler());
        InetSocketAddress address = start(registry, null);

        this.connpool.setDefaultMaxPerRoute(3);
        this.connpool.setMaxTotal(3);

        String pattern = RndTestPatternGenerator.generateText();
        int count = RndTestPatternGenerator.generateCount(1000);

        HttpHost target = new HttpHost("localhost", address.getPort());

        Queue<Future<HttpResponse>> queue = new ConcurrentLinkedQueue<Future<HttpResponse>>();
        for (int i = 0; i < 30; i++) {
            BasicHttpRequest request = new BasicHttpRequest("HEAD", createRequestUri(pattern, count));
            Future<HttpResponse> future = this.executor.execute(
                    new BasicAsyncRequestProducer(target, request),
                    new BasicAsyncResponseConsumer(),
                    this.connpool);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            Future<HttpResponse> future = queue.remove();
            HttpResponse response = future.get();
            Assert.assertNotNull(response);
            Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        }
    }

    @Test
    public void testHttpPostsWithExpectationVerificationDelayedResponse() throws Exception {
        HttpAsyncExpectationVerifier expectationVerifier = new HttpAsyncExpectationVerifier() {

            public void verify(
                    final HttpAsyncExchange httpexchange,
                    final HttpContext context) throws HttpException {
                new Thread() {
                    @Override
                    public void run() {
                        // Wait a bit, to make sure this is delayed.
                        try { Thread.sleep(100); } catch(InterruptedException ie) {}
                        // Set the entity after delaying...
                        HttpRequest request = httpexchange.getRequest();
                        ProtocolVersion ver = request.getRequestLine().getProtocolVersion();
                        String s = request.getRequestLine().getUri();
                        if (!s.equals("AAAAAx10")) {
                            if (!ver.lessEquals(HttpVersion.HTTP_1_1)) {
                                ver = HttpVersion.HTTP_1_1;
                            }
                            BasicHttpResponse response = new BasicHttpResponse(ver,
                                    HttpStatus.SC_EXPECTATION_FAILED, "Expectation failed");
                            response.setEntity(new NStringEntity("Expectation failed", ContentType.TEXT_PLAIN));
                            httpexchange.submitResponse(new BasicAsyncResponseProducer(response));
                        } else {
                            httpexchange.submitResponse();
                        }
                    }
                }.start();
            }

        };

        HttpAsyncRequestHandlerRegistry registry = new HttpAsyncRequestHandlerRegistry();
        registry.register("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));
        InetSocketAddress address = start(registry, expectationVerifier);

        BasicHttpEntityEnclosingRequest request1 = new BasicHttpEntityEnclosingRequest(
                "POST", createRequestUri("AAAAA", 10));
        request1.getParams().setBooleanParameter(CoreProtocolPNames.USE_EXPECT_CONTINUE, true);
        request1.setEntity(new NStringEntity(createExpectedString("AAAAA", 10)));
        BasicHttpEntityEnclosingRequest request2 = new BasicHttpEntityEnclosingRequest(
                "POST", createRequestUri("AAAAA", 10));
        request2.getParams().setBooleanParameter(CoreProtocolPNames.USE_EXPECT_CONTINUE, true);
        request2.setEntity(new NStringEntity(createExpectedString("AAAAA", 10)));
        BasicHttpEntityEnclosingRequest request3 = new BasicHttpEntityEnclosingRequest(
                "POST", createRequestUri("BBBBB", 10));
        request3.getParams().setBooleanParameter(CoreProtocolPNames.USE_EXPECT_CONTINUE, true);
        request3.setEntity(new NStringEntity(createExpectedString("BBBBB", 10)));

        HttpRequest[] requests = new HttpRequest[] { request1, request2, request3 };

        HttpHost target = new HttpHost("localhost", address.getPort());

        Queue<Future<HttpResponse>> queue = new ConcurrentLinkedQueue<Future<HttpResponse>>();
        for (int i = 0; i < requests.length; i++) {
            Future<HttpResponse> future = this.executor.execute(
                    new BasicAsyncRequestProducer(target, requests[i]),
                    new BasicAsyncResponseConsumer(),
                    this.connpool);
            queue.add(future);
        }

        Future<HttpResponse> future1 = queue.remove();
        HttpResponse response1 = future1.get();
        Assert.assertEquals(HttpStatus.SC_OK, response1.getStatusLine().getStatusCode());

        Future<HttpResponse> future2 = queue.remove();
        HttpResponse response2 = future2.get();
        Assert.assertEquals(HttpStatus.SC_OK, response2.getStatusLine().getStatusCode());

        Future<HttpResponse> future3 = queue.remove();
        HttpResponse response3 = future3.get();
        Assert.assertEquals(HttpStatus.SC_EXPECTATION_FAILED, response3.getStatusLine().getStatusCode());
    }

    @Test
    public void testHttpExceptionInHandler() throws Exception {

        class FailingRequestHandler implements HttpAsyncRequestHandler<HttpRequest> {

            public FailingRequestHandler() {
                super();
            }

            public HttpAsyncRequestConsumer<HttpRequest> processRequest(
                    final HttpRequest request,
                    final HttpContext context) {
                return new BasicAsyncRequestConsumer();
            }

            public void handle(
                    final HttpRequest request,
                    final HttpAsyncExchange httpexchange,
                    final HttpContext context) throws HttpException, IOException {
                throw new HttpException("Boom");
            }

        }

        HttpAsyncRequestHandlerRegistry registry = new HttpAsyncRequestHandlerRegistry();
        registry.register("*", new FailingRequestHandler());
        InetSocketAddress address = start(registry, null);

        this.connpool.setDefaultMaxPerRoute(3);
        this.connpool.setMaxTotal(3);

        String pattern = RndTestPatternGenerator.generateText();
        int count = RndTestPatternGenerator.generateCount(1000);

        HttpHost target = new HttpHost("localhost", address.getPort());

        Queue<Future<HttpResponse>> queue = new ConcurrentLinkedQueue<Future<HttpResponse>>();
        for (int i = 0; i < 1; i++) {
            BasicHttpRequest request = new BasicHttpRequest("GET", createRequestUri(pattern, count));
            Future<HttpResponse> future = this.executor.execute(
                    new BasicAsyncRequestProducer(target, request),
                    new BasicAsyncResponseConsumer(),
                    this.connpool);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            Future<HttpResponse> future = queue.remove();
            HttpResponse response = future.get();
            Assert.assertNotNull(response);
            Assert.assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getStatusLine().getStatusCode());
        }
    }

    @Test
    public void testNoServiceHandler() throws Exception {
        HttpAsyncRequestHandlerRegistry registry = new HttpAsyncRequestHandlerRegistry();
        InetSocketAddress address = start(registry, null);

        this.connpool.setDefaultMaxPerRoute(3);
        this.connpool.setMaxTotal(3);

        String pattern = RndTestPatternGenerator.generateText();
        int count = RndTestPatternGenerator.generateCount(1000);

        HttpHost target = new HttpHost("localhost", address.getPort());

        Queue<Future<HttpResponse>> queue = new ConcurrentLinkedQueue<Future<HttpResponse>>();
        for (int i = 0; i < 30; i++) {
            BasicHttpRequest request = new BasicHttpRequest("GET", createRequestUri(pattern, count));
            Future<HttpResponse> future = this.executor.execute(
                    new BasicAsyncRequestProducer(target, request),
                    new BasicAsyncResponseConsumer(),
                    this.connpool);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            Future<HttpResponse> future = queue.remove();
            HttpResponse response = future.get();
            Assert.assertNotNull(response);
            Assert.assertEquals(HttpStatus.SC_NOT_IMPLEMENTED, response.getStatusLine().getStatusCode());
        }
    }

    @Test
    public void testResponseNoContent() throws Exception {
        HttpAsyncRequestHandlerRegistry registry = new HttpAsyncRequestHandlerRegistry();
        registry.register("*", new BasicAsyncRequestHandler(new HttpRequestHandler() {
            
            public void handle(
                    final HttpRequest request, 
                    final HttpResponse response, 
                    final HttpContext context) throws HttpException, IOException {
                response.setStatusCode(HttpStatus.SC_NO_CONTENT);
            }
        
        }));
        InetSocketAddress address = start(registry, null);

        this.connpool.setDefaultMaxPerRoute(3);
        this.connpool.setMaxTotal(3);

        HttpHost target = new HttpHost("localhost", address.getPort());

        Queue<Future<HttpResponse>> queue = new ConcurrentLinkedQueue<Future<HttpResponse>>();
        for (int i = 0; i < 30; i++) {
            BasicHttpRequest request = new BasicHttpRequest("GET", "/");
            Future<HttpResponse> future = this.executor.execute(
                    new BasicAsyncRequestProducer(target, request),
                    new BasicAsyncResponseConsumer(),
                    this.connpool);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            Future<HttpResponse> future = queue.remove();
            HttpResponse response = future.get();
            Assert.assertNotNull(response);
            Assert.assertNull(response.getEntity());
        }
    }

}
