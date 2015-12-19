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

package org.apache.hc.core5.http.integration;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.entity.ContentType;
import org.apache.hc.core5.http.entity.EntityUtils;
import org.apache.hc.core5.http.impl.nio.BasicAsyncRequestConsumer;
import org.apache.hc.core5.http.impl.nio.BasicAsyncRequestHandler;
import org.apache.hc.core5.http.impl.nio.BasicAsyncResponseProducer;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.ContentEncoder;
import org.apache.hc.core5.http.nio.HttpAsyncExchange;
import org.apache.hc.core5.http.nio.HttpAsyncExpectationVerifier;
import org.apache.hc.core5.http.nio.HttpAsyncRequestConsumer;
import org.apache.hc.core5.http.nio.HttpAsyncRequestHandler;
import org.apache.hc.core5.http.nio.IOControl;
import org.apache.hc.core5.http.nio.entity.NStringEntity;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.ImmutableHttpProcessor;
import org.apache.hc.core5.http.protocol.RequestConnControl;
import org.apache.hc.core5.http.protocol.RequestContent;
import org.apache.hc.core5.http.protocol.RequestExpectContinue;
import org.apache.hc.core5.http.protocol.RequestTargetHost;
import org.apache.hc.core5.http.protocol.RequestUserAgent;
import org.apache.hc.core5.http.testserver.nio.HttpCoreNIOTestBase;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * HttpCore NIO integration tests for async handlers.
 */
@RunWith(Parameterized.class)
public class TestHttpAsyncHandlers extends HttpCoreNIOTestBase {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> protocols() {
        return Arrays.asList(new Object[][]{
                { ProtocolScheme.http },
                { ProtocolScheme.https },
        });
    }

    public TestHttpAsyncHandlers(final ProtocolScheme scheme) {
        super(scheme);
    }

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

    private HttpHost start() throws IOException, InterruptedException {
        this.server.start();
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

        final Queue<Future<HttpResponse>> queue = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < 30; i++) {
            final BasicHttpRequest request = new BasicHttpRequest("GET", createRequestUri(pattern, count));
            final Future<HttpResponse> future = this.client.execute(target, request);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            final Future<HttpResponse> future = queue.remove();
            final HttpResponse response = future.get();
            Assert.assertNotNull(response);
            Assert.assertEquals(expectedPattern, EntityUtils.toString(response.getEntity()));
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

        final Queue<Future<HttpResponse>> queue = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < 30; i++) {
            final BasicHttpRequest request = new BasicHttpRequest("HEAD", createRequestUri(pattern, count));
            final Future<HttpResponse> future = this.client.execute(target, request);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            final Future<HttpResponse> future = queue.remove();
            final HttpResponse response = future.get();
            Assert.assertNotNull(response);
            Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        }
    }

    @Test
    public void testHttpPostsWithContentLength() throws Exception {
        this.server.registerHandler("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));
        final HttpHost target = start();

        this.client.setMaxPerRoute(3);
        this.client.setMaxTotal(3);

        final String pattern = RndTestPatternGenerator.generateText();
        final int count = RndTestPatternGenerator.generateCount(1000);

        final String expectedPattern = createExpectedString(pattern, count);

        final Queue<Future<HttpResponse>> queue = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < 30; i++) {
            final BasicHttpRequest request = new BasicHttpRequest(
                    "POST", createRequestUri(pattern, count));
            final NStringEntity entity = new NStringEntity(expectedPattern, ContentType.DEFAULT_TEXT);
            request.setEntity(entity);
            final Future<HttpResponse> future = this.client.execute(target, request);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            final Future<HttpResponse> future = queue.remove();
            final HttpResponse response = future.get();
            Assert.assertNotNull(response);
            Assert.assertEquals(expectedPattern, EntityUtils.toString(response.getEntity()));
        }
    }

    @Test
    public void testHttpPostsChunked() throws Exception {
        this.server.registerHandler("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));
        final HttpHost target = start();

        this.client.setMaxPerRoute(3);
        this.client.setMaxTotal(3);

        final String pattern = RndTestPatternGenerator.generateText();
        final int count = RndTestPatternGenerator.generateCount(1000);

        final String expectedPattern = createExpectedString(pattern, count);

        final Queue<Future<HttpResponse>> queue = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < 30; i++) {
            final BasicHttpRequest request = new BasicHttpRequest(
                    "POST", createRequestUri(pattern, count));
            final NStringEntity entity = new NStringEntity(expectedPattern, ContentType.DEFAULT_TEXT);
            entity.setChunked(true);
            request.setEntity(entity);
            final Future<HttpResponse> future = this.client.execute(target, request);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            final Future<HttpResponse> future = queue.remove();
            final HttpResponse response = future.get();
            Assert.assertNotNull(response);
            Assert.assertEquals(expectedPattern, EntityUtils.toString(response.getEntity()));
        }
    }

    @Test
    public void testHttpPostsHTTP10() throws Exception {
        this.server.registerHandler("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));
        final HttpHost target = start();

        this.client.setMaxPerRoute(3);
        this.client.setMaxTotal(3);

        final String pattern = RndTestPatternGenerator.generateText();
        final int count = RndTestPatternGenerator.generateCount(1000);

        final String expectedPattern = createExpectedString(pattern, count);

        final Queue<Future<HttpResponse>> queue = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < 30; i++) {
            final BasicHttpRequest request = new BasicHttpRequest(
                    "POST", createRequestUri(pattern, count), HttpVersion.HTTP_1_0);
            final NStringEntity entity = new NStringEntity(expectedPattern, ContentType.DEFAULT_TEXT);
            request.setEntity(entity);
            final Future<HttpResponse> future = this.client.execute(target, request);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            final Future<HttpResponse> future = queue.remove();
            final HttpResponse response = future.get();
            Assert.assertNotNull(response);
            Assert.assertEquals(expectedPattern, EntityUtils.toString(response.getEntity()));
        }
    }

    @Test
    public void testHttpPostsNoEntity() throws Exception {
        this.server.registerHandler("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));
        final HttpHost target = start();

        this.client.setMaxPerRoute(3);
        this.client.setMaxTotal(3);

        final String pattern = RndTestPatternGenerator.generateText();
        final int count = RndTestPatternGenerator.generateCount(1000);

        final Queue<Future<HttpResponse>> queue = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < 30; i++) {
            final BasicHttpRequest request = new BasicHttpRequest(
                    "POST", createRequestUri(pattern, count));
            request.setEntity(null);
            final Future<HttpResponse> future = this.client.execute(target, request);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            final Future<HttpResponse> future = queue.remove();
            final HttpResponse response = future.get();
            Assert.assertNotNull(response);
            Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        }
    }

    @Test
    public void testHttpPostNoContentLength() throws Exception {
        this.server.registerHandler("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));

        this.client.setHttpProcessor(new ImmutableHttpProcessor(
                new RequestTargetHost(),
                new RequestConnControl(),
                new RequestUserAgent(),
                new RequestExpectContinue()));

        final HttpHost target = start();

        this.client.setMaxPerRoute(3);
        this.client.setMaxTotal(3);

        final String pattern = RndTestPatternGenerator.generateText();
        final int count = RndTestPatternGenerator.generateCount(1000);

        final BasicHttpRequest request = new BasicHttpRequest(
                "POST", createRequestUri(pattern, count));
        request.setEntity(null);

        final Future<HttpResponse> future = this.client.execute(target, request);

        final HttpResponse response = future.get();
        Assert.assertNotNull(response);
        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
    }

    @Test
    public void testHttpPostIdentity() throws Exception {
        this.server.registerHandler("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));

        this.client.setHttpProcessor(new ImmutableHttpProcessor(
                new HttpRequestInterceptor() {

                    @Override
                    public void process(
                            final HttpRequest request,
                            final HttpContext context) throws HttpException, IOException {
                        request.addHeader(HttpHeaders.TRANSFER_ENCODING, "identity");
                    }

                },
                new RequestTargetHost(),
                new RequestConnControl(),
                new RequestUserAgent(),
                new RequestExpectContinue()));

        final HttpHost target = start();

        this.client.setMaxPerRoute(3);
        this.client.setMaxTotal(3);

        final String pattern = RndTestPatternGenerator.generateText();
        final int count = RndTestPatternGenerator.generateCount(1000);

        final BasicHttpRequest request = new BasicHttpRequest(
                "POST", createRequestUri(pattern, count));
        request.setEntity(null);

        final Future<HttpResponse> future = this.client.execute(target, request);

        final HttpResponse response = future.get();
        Assert.assertNotNull(response);
        Assert.assertEquals(HttpStatus.SC_NOT_IMPLEMENTED, response.getCode());
    }

    @Test
    public void testHttpPostsWithExpectContinue() throws Exception {
        this.server.registerHandler("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));
        final HttpHost target = start();

        this.client.setMaxPerRoute(3);
        this.client.setMaxTotal(3);

        final String pattern = RndTestPatternGenerator.generateText();
        final int count = RndTestPatternGenerator.generateCount(1000);

        final String expectedPattern = createExpectedString(pattern, count);

        final Queue<Future<HttpResponse>> queue = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < 30; i++) {
            final BasicHttpRequest request = new BasicHttpRequest(
                    "POST", createRequestUri(pattern, count));
            final NStringEntity entity = new NStringEntity(expectedPattern, ContentType.DEFAULT_TEXT);
            request.setEntity(entity);

            final HttpContext context = new BasicHttpContext();
            final Future<HttpResponse> future = this.client.execute(target, request, context);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            final Future<HttpResponse> future = queue.remove();
            final HttpResponse response = future.get();
            Assert.assertNotNull(response);
            Assert.assertEquals(expectedPattern, EntityUtils.toString(response.getEntity()));
        }
    }

    @Test
    public void testHttpPostsWithExpectationVerification() throws Exception {
        this.server.registerHandler("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));
        this.server.setExpectationVerifier(new HttpAsyncExpectationVerifier() {

            @Override
            public void verify(
                    final HttpAsyncExchange httpexchange,
                    final HttpContext context) throws HttpException {
                final HttpRequest request = httpexchange.getRequest();
                ProtocolVersion ver = request.getRequestLine().getProtocolVersion();
                final String s = request.getRequestLine().getUri();
                if (!s.equals("AAAAAx10")) {
                    if (!ver.lessEquals(HttpVersion.HTTP_1_1)) {
                        ver = HttpVersion.HTTP_1_1;
                    }
                    final BasicHttpResponse response = new BasicHttpResponse(ver,
                            HttpStatus.SC_EXPECTATION_FAILED, "Expectation failed");
                    response.setEntity(new NStringEntity("Expectation failed", ContentType.TEXT_PLAIN));
                    httpexchange.submitResponse(new BasicAsyncResponseProducer(response));
                } else {
                    httpexchange.submitResponse();
                }
            }

        });

        final HttpHost target = start();

        final BasicHttpRequest request1 = new BasicHttpRequest(
                "POST", createRequestUri("AAAAA", 10));
        request1.setEntity(new NStringEntity(createExpectedString("AAAAA", 10)));
        final BasicHttpRequest request2 = new BasicHttpRequest(
                "POST", createRequestUri("AAAAA", 10));
        request2.setEntity(new NStringEntity(createExpectedString("AAAAA", 10)));
        final BasicHttpRequest request3 = new BasicHttpRequest(
                "POST", createRequestUri("BBBBB", 10));
        request3.setEntity(new NStringEntity(createExpectedString("BBBBB", 10)));

        final HttpRequest[] requests = new HttpRequest[] { request1, request2, request3 };

        final Queue<Future<HttpResponse>> queue = new ConcurrentLinkedQueue<>();
        for (final HttpRequest request : requests) {
            final HttpContext context = new BasicHttpContext();
            final Future<HttpResponse> future = this.client.execute(target, request, context);
            queue.add(future);
        }

        final Future<HttpResponse> future1 = queue.remove();
        final HttpResponse response1 = future1.get();
        Assert.assertEquals(HttpStatus.SC_OK, response1.getCode());

        final Future<HttpResponse> future2 = queue.remove();
        final HttpResponse response2 = future2.get();
        Assert.assertEquals(HttpStatus.SC_OK, response2.getCode());

        final Future<HttpResponse> future3 = queue.remove();
        final HttpResponse response3 = future3.get();
        Assert.assertEquals(HttpStatus.SC_EXPECTATION_FAILED, response3.getCode());
    }

    @Test
    public void testHttpHeadsDelayedResponse() throws Exception {

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
                ProtocolVersion ver = request.getRequestLine().getProtocolVersion();
                if (!ver.lessEquals(HttpVersion.HTTP_1_1)) {
                    ver = HttpVersion.HTTP_1_1;
                }
                final BasicHttpResponse response = new BasicHttpResponse(ver, HttpStatus.SC_OK, "OK");
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

        final String pattern = RndTestPatternGenerator.generateText();
        final int count = RndTestPatternGenerator.generateCount(1000);

        final Queue<Future<HttpResponse>> queue = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < 30; i++) {
            final BasicHttpRequest request = new BasicHttpRequest("HEAD", createRequestUri(pattern, count));
            final Future<HttpResponse> future = this.client.execute(target, request);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            final Future<HttpResponse> future = queue.remove();
            final HttpResponse response = future.get();
            Assert.assertNotNull(response);
            Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        }
    }

    @Test
    public void testHttpPostsWithExpectationVerificationDelayedResponse() throws Exception {
        this.server.registerHandler("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));
        this.server.setExpectationVerifier(new HttpAsyncExpectationVerifier() {

            @Override
            public void verify(
                    final HttpAsyncExchange httpexchange,
                    final HttpContext context) throws HttpException {
                new Thread() {
                    @Override
                    public void run() {
                        // Wait a bit, to make sure this is delayed.
                        try {
                            Thread.sleep(100);
                        } catch (final InterruptedException ie) {
                        }
                        // Set the entity after delaying...
                        final HttpRequest request = httpexchange.getRequest();
                        ProtocolVersion ver = request.getRequestLine().getProtocolVersion();
                        final String s = request.getRequestLine().getUri();
                        if (!s.equals("AAAAAx10")) {
                            if (!ver.lessEquals(HttpVersion.HTTP_1_1)) {
                                ver = HttpVersion.HTTP_1_1;
                            }
                            final BasicHttpResponse response = new BasicHttpResponse(ver,
                                    HttpStatus.SC_EXPECTATION_FAILED, "Expectation failed");
                            response.setEntity(new NStringEntity("Expectation failed", ContentType.TEXT_PLAIN));
                            httpexchange.submitResponse(new BasicAsyncResponseProducer(response));
                        } else {
                            httpexchange.submitResponse();
                        }
                    }
                }.start();
            }

        });
        final HttpHost target = start();

        final BasicHttpRequest request1 = new BasicHttpRequest(
                "POST", createRequestUri("AAAAA", 10));
        request1.setEntity(new NStringEntity(createExpectedString("AAAAA", 10)));
        final BasicHttpRequest request2 = new BasicHttpRequest(
                "POST", createRequestUri("AAAAA", 10));
        request2.setEntity(new NStringEntity(createExpectedString("AAAAA", 10)));
        final BasicHttpRequest request3 = new BasicHttpRequest(
                "POST", createRequestUri("BBBBB", 10));
        request3.setEntity(new NStringEntity(createExpectedString("BBBBB", 10)));

        final HttpRequest[] requests = new HttpRequest[] { request1, request2, request3 };

        final Queue<Future<HttpResponse>> queue = new ConcurrentLinkedQueue<>();
        for (final HttpRequest request : requests) {
            final HttpContext context = new BasicHttpContext();
            final Future<HttpResponse> future = this.client.execute(target, request, context);
            queue.add(future);
        }

        final Future<HttpResponse> future1 = queue.remove();
        final HttpResponse response1 = future1.get();
        Assert.assertEquals(HttpStatus.SC_OK, response1.getCode());

        final Future<HttpResponse> future2 = queue.remove();
        final HttpResponse response2 = future2.get();
        Assert.assertEquals(HttpStatus.SC_OK, response2.getCode());

        final Future<HttpResponse> future3 = queue.remove();
        final HttpResponse response3 = future3.get();
        Assert.assertEquals(HttpStatus.SC_EXPECTATION_FAILED, response3.getCode());
    }

    @Test
    public void testHttpPostsFailedExpectionContentLengthNonReusableConnection() throws Exception {
        this.server.registerHandler("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));
        this.server.setExpectationVerifier(new HttpAsyncExpectationVerifier() {

            @Override
            public void verify(
                    final HttpAsyncExchange httpexchange,
                    final HttpContext context) throws HttpException {
                new Thread() {
                    @Override
                    public void run() {
                        final HttpRequest request = httpexchange.getRequest();
                        ProtocolVersion ver = request.getRequestLine().getProtocolVersion();
                        final String s = request.getRequestLine().getUri();
                        if (!s.equals("AAAAAx10")) {
                            if (!ver.lessEquals(HttpVersion.HTTP_1_1)) {
                                ver = HttpVersion.HTTP_1_1;
                            }
                            final BasicHttpResponse response = new BasicHttpResponse(ver,
                                    HttpStatus.SC_EXPECTATION_FAILED, "Expectation failed");
                            response.setEntity(new NStringEntity("Expectation failed", ContentType.TEXT_PLAIN));
                            httpexchange.submitResponse(new BasicAsyncResponseProducer(response));
                        } else {
                            httpexchange.submitResponse();
                        }
                    }
                }.start();
            }

        });

        this.client.setMaxPerRoute(1);
        this.client.setMaxTotal(1);

        final HttpHost target = start();

        for (int i = 0; i < 3; i++) {
            final BasicHttpRequest request1 = new BasicHttpRequest("POST", createRequestUri("AAAAA", 10));
            final HttpEntity entity1 = new NStringEntity(createExpectedString("AAAAA", 10));
            request1.setEntity(entity1);

            final HttpContext context = new BasicHttpContext();
            final Future<HttpResponse> future1 = this.client.execute(target, request1, context);
            final HttpResponse response1 = future1.get();
            Assert.assertEquals(HttpStatus.SC_OK, response1.getCode());

            final BasicHttpRequest request2 = new BasicHttpRequest("POST", createRequestUri("BBBBB", 10));
            final HttpEntity entity2 = new NStringEntity(createExpectedString("BBBBB", 500));
            request2.setEntity(entity2);

            final Future<HttpResponse> future2 = this.client.execute(target, request2, context);
            final HttpResponse response2 = future2.get();
            Assert.assertEquals(HttpStatus.SC_EXPECTATION_FAILED, response2.getCode());
        }
    }

    @Test
    public void testHttpPostsFailedExpectionConnectionReuse() throws Exception {
        this.server.registerHandler("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));
        this.server.setExpectationVerifier(new HttpAsyncExpectationVerifier() {

            @Override
            public void verify(
                    final HttpAsyncExchange httpexchange,
                    final HttpContext context) throws HttpException {
                new Thread() {
                    @Override
                    public void run() {
                        final HttpRequest request = httpexchange.getRequest();
                        ProtocolVersion ver = request.getRequestLine().getProtocolVersion();
                        final String s = request.getRequestLine().getUri();
                        if (!s.equals("AAAAAx10")) {
                            if (!ver.lessEquals(HttpVersion.HTTP_1_1)) {
                                ver = HttpVersion.HTTP_1_1;
                            }
                            final BasicHttpResponse response = new BasicHttpResponse(ver,
                                    HttpStatus.SC_EXPECTATION_FAILED, "Expectation failed");
                            response.setEntity(new NStringEntity("Expectation failed", ContentType.TEXT_PLAIN));
                            httpexchange.submitResponse(new BasicAsyncResponseProducer(response));
                        } else {
                            httpexchange.submitResponse();
                        }
                    }
                }.start();
            }

        });

        this.client.setMaxPerRoute(1);
        this.client.setMaxTotal(1);

        final HttpHost target = start();

        for (int i = 0; i < 10; i++) {
            final BasicHttpRequest request1 = new BasicHttpRequest("POST", createRequestUri("AAAAA", 10));
            final NStringEntity entity1 = new NStringEntity(createExpectedString("AAAAA", 10));
            entity1.setChunked(i % 2 == 0);
            request1.setEntity(entity1);

            final HttpContext context = new BasicHttpContext();
            final Future<HttpResponse> future1 = this.client.execute(target, request1, context);
            final HttpResponse response1 = future1.get();
            Assert.assertEquals(HttpStatus.SC_OK, response1.getCode());

            final BasicHttpRequest request2 = new BasicHttpRequest("POST", createRequestUri("BBBBB", 10));
            final NStringEntity entity2 = new NStringEntity(createExpectedString("BBBBB", 10));
            entity2.setChunked(i % 2 == 0);
            request2.setEntity(entity2);

            final Future<HttpResponse> future2 = this.client.execute(target, request2, context);
            final HttpResponse response2 = future2.get();
            Assert.assertEquals(HttpStatus.SC_EXPECTATION_FAILED, response2.getCode());
        }
    }

    @Test
    public void testHttpPostsFailedExpectionConnectionReuseLateResponseBody() throws Exception {
        this.server.registerHandler("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));
        this.server.setExpectationVerifier(new HttpAsyncExpectationVerifier() {

            @Override
            public void verify(
                    final HttpAsyncExchange httpexchange,
                    final HttpContext context) throws HttpException {
                new Thread() {
                    @Override
                    public void run() {
                        final HttpRequest request = httpexchange.getRequest();
                        ProtocolVersion ver = request.getRequestLine().getProtocolVersion();
                        final String s = request.getRequestLine().getUri();
                        if (!s.equals("AAAAAx10")) {
                            if (!ver.lessEquals(HttpVersion.HTTP_1_1)) {
                                ver = HttpVersion.HTTP_1_1;
                            }
                            final BasicHttpResponse response = new BasicHttpResponse(ver,
                                    HttpStatus.SC_EXPECTATION_FAILED, "Expectation failed");
                            response.setEntity(new NStringEntity("Expectation failed", ContentType.TEXT_PLAIN));

                            final AtomicInteger count = new AtomicInteger(0);
                            httpexchange.submitResponse(new BasicAsyncResponseProducer(response) {
                                @Override
                                public void produceContent(final ContentEncoder encoder, final IOControl ioctrl) throws IOException {
                                    if (count.incrementAndGet() < 3) {
                                        try {
                                            Thread.sleep(50);
                                        } catch (final InterruptedException ignore) {
                                        }
                                    } else {
                                        super.produceContent(encoder, ioctrl);
                                    }
                                }
                            });
                        } else {
                            httpexchange.submitResponse();
                        }
                    }
                }.start();
            }

        });

        this.client.setMaxPerRoute(1);
        this.client.setMaxTotal(1);

        final HttpHost target = start();

        for (int i = 0; i < 10; i++) {
            final BasicHttpRequest request1 = new BasicHttpRequest("POST", createRequestUri("AAAAA", 10));
            final NStringEntity entity1 = new NStringEntity(createExpectedString("AAAAA", 10));
            entity1.setChunked(i % 2 == 0);
            request1.setEntity(entity1);

            final HttpContext context = new BasicHttpContext();
            final Future<HttpResponse> future1 = this.client.execute(target, request1, context);
            final HttpResponse response1 = future1.get();
            Assert.assertEquals(HttpStatus.SC_OK, response1.getCode());

            final BasicHttpRequest request2 = new BasicHttpRequest("POST", createRequestUri("BBBBB", 10));
            final NStringEntity entity2 = new NStringEntity(createExpectedString("BBBBB", 10));
            entity2.setChunked(i % 2 == 0);
            request2.setEntity(entity2);

            final Future<HttpResponse> future2 = this.client.execute(target, request2, context);
            final HttpResponse response2 = future2.get();
            Assert.assertEquals(HttpStatus.SC_EXPECTATION_FAILED, response2.getCode());
        }
    }

    @Test
    public void testHttpExceptionInHandler() throws Exception {

        class FailingRequestHandler implements HttpAsyncRequestHandler<HttpRequest> {

            public FailingRequestHandler() {
                super();
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
                throw new HttpException("Boom");
            }

        }

        this.server.registerHandler("*", new FailingRequestHandler());
        final HttpHost target = start();

        this.client.setMaxPerRoute(3);
        this.client.setMaxTotal(3);

        final String pattern = RndTestPatternGenerator.generateText();
        final int count = RndTestPatternGenerator.generateCount(1000);

        final Queue<Future<HttpResponse>> queue = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < 1; i++) {
            final BasicHttpRequest request = new BasicHttpRequest("GET", createRequestUri(pattern, count));
            final Future<HttpResponse> future = this.client.execute(target, request);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            final Future<HttpResponse> future = queue.remove();
            final HttpResponse response = future.get();
            Assert.assertNotNull(response);
            Assert.assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getCode());
        }
    }

    @Test
    public void testNoServiceHandler() throws Exception {
        final HttpHost target = start();

        this.client.setMaxPerRoute(3);
        this.client.setMaxTotal(3);

        final String pattern = RndTestPatternGenerator.generateText();
        final int count = RndTestPatternGenerator.generateCount(1000);

        final Queue<Future<HttpResponse>> queue = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < 30; i++) {
            final BasicHttpRequest request = new BasicHttpRequest("GET", createRequestUri(pattern, count));
            final Future<HttpResponse> future = this.client.execute(target, request);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            final Future<HttpResponse> future = queue.remove();
            final HttpResponse response = future.get();
            Assert.assertNotNull(response);
            Assert.assertEquals(HttpStatus.SC_NOT_IMPLEMENTED, response.getCode());
        }
    }

    @Test
    public void testResponseNoContent() throws Exception {
        this.server.registerHandler("*", new BasicAsyncRequestHandler(new HttpRequestHandler() {

            @Override
            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                response.setStatusCode(HttpStatus.SC_NO_CONTENT);
            }

        }));
        final HttpHost target = start();

        this.client.setMaxPerRoute(3);
        this.client.setMaxTotal(3);

        final Queue<Future<HttpResponse>> queue = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < 30; i++) {
            final BasicHttpRequest request = new BasicHttpRequest("GET", "/");
            final Future<HttpResponse> future = this.client.execute(target, request);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            final Future<HttpResponse> future = queue.remove();
            final HttpResponse response = future.get();
            Assert.assertNotNull(response);
            Assert.assertNull(response.getEntity());
        }
    }

    @Test
    public void testAbsentHostHeader() throws Exception {
        this.server.registerHandler("*", new BasicAsyncRequestHandler(new HttpRequestHandler() {

            @Override
            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                response.setStatusCode(HttpStatus.SC_OK);
                response.setEntity(new NStringEntity("All is well", StandardCharsets.US_ASCII));
            }

        }));
        this.client.setHttpProcessor(new ImmutableHttpProcessor(new RequestContent(), new RequestConnControl()));
        final HttpHost target = start();

        this.client.setMaxPerRoute(3);
        this.client.setMaxTotal(3);

        final BasicHttpRequest request1 = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_0);
        final Future<HttpResponse> future1 = this.client.execute(target, request1);
        final HttpResponse response1 = future1.get();
        Assert.assertNotNull(response1);
        Assert.assertEquals(200, response1.getCode());
        final BasicHttpRequest request2 = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        final Future<HttpResponse> future2 = this.client.execute(target, request2);
        final HttpResponse response2 = future2.get();
        Assert.assertNotNull(response2);
        Assert.assertEquals(400, response2.getCode());
    }

}
