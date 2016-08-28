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

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.entity.ByteArrayEntity;
import org.apache.hc.core5.http.entity.ContentType;
import org.apache.hc.core5.http.entity.EntityUtils;
import org.apache.hc.core5.http.impl.nio.BasicAsyncRequestConsumer;
import org.apache.hc.core5.http.impl.nio.BasicAsyncRequestHandler;
import org.apache.hc.core5.http.impl.nio.BasicAsyncResponseProducer;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
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
import org.apache.hc.core5.http.testserver.nio.HttpClientNio;
import org.apache.hc.core5.http.testserver.nio.HttpCoreNIOTestBase;
import org.apache.hc.core5.http.testserver.nio.HttpServerNio;
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
public class TestAsyncHttp extends HttpCoreNIOTestBase {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> protocols() {
        return Arrays.asList(new Object[][]{
                { ProtocolScheme.http },
                { ProtocolScheme.https },
        });
    }

    public TestAsyncHttp(final ProtocolScheme scheme) {
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

        final Queue<Future<ClassicHttpResponse>> queue = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < 30; i++) {
            final BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", createRequestUri(pattern, count));
            final Future<ClassicHttpResponse> future = this.client.execute(target, request);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            final Future<ClassicHttpResponse> future = queue.remove();
            final ClassicHttpResponse response = future.get();
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

        final Queue<Future<ClassicHttpResponse>> queue = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < 30; i++) {
            final BasicClassicHttpRequest request = new BasicClassicHttpRequest("HEAD", createRequestUri(pattern, count));
            final Future<ClassicHttpResponse> future = this.client.execute(target, request);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            final Future<ClassicHttpResponse> future = queue.remove();
            final ClassicHttpResponse response = future.get();
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

        final Queue<Future<ClassicHttpResponse>> queue = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < 30; i++) {
            final BasicClassicHttpRequest request = new BasicClassicHttpRequest(
                    "POST", createRequestUri(pattern, count));
            final NStringEntity entity = new NStringEntity(expectedPattern, ContentType.DEFAULT_TEXT);
            request.setEntity(entity);
            final Future<ClassicHttpResponse> future = this.client.execute(target, request);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            final Future<ClassicHttpResponse> future = queue.remove();
            final ClassicHttpResponse response = future.get();
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

        final Queue<Future<ClassicHttpResponse>> queue = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < 30; i++) {
            final BasicClassicHttpRequest request = new BasicClassicHttpRequest(
                    "POST", createRequestUri(pattern, count));
            final NStringEntity entity = new NStringEntity(expectedPattern, ContentType.DEFAULT_TEXT);
            entity.setChunked(true);
            request.setEntity(entity);
            final Future<ClassicHttpResponse> future = this.client.execute(target, request);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            final Future<ClassicHttpResponse> future = queue.remove();
            final ClassicHttpResponse response = future.get();
            Assert.assertNotNull(response);
            Assert.assertEquals(expectedPattern, EntityUtils.toString(response.getEntity()));
        }
    }

    @Test
    public void testHttpPostsHTTP10() throws Exception {
        this.server.registerHandler("*", new BasicAsyncRequestHandler(new HttpRequestHandler() {

            @Override
            public void handle(
                    final ClassicHttpRequest request,
                    final ClassicHttpResponse response,
                    final HttpContext context) throws HttpException, IOException {

                final HttpEntity incoming = request.getEntity();
                if (incoming != null) {
                    final byte[] data = EntityUtils.toByteArray(incoming);
                    final ByteArrayEntity outgoing = new ByteArrayEntity(data);
                    outgoing.setChunked(false);
                    response.setEntity(outgoing);
                }
                if (HttpVersion.HTTP_1_0.equals(request.getVersion())) {
                    response.addHeader("Version", "1.0");
                }
            }

        }));
        final HttpHost target = start();

        this.client.setMaxPerRoute(3);
        this.client.setMaxTotal(3);

        final String pattern = RndTestPatternGenerator.generateText();
        final int count = RndTestPatternGenerator.generateCount(1000);

        final String expectedPattern = createExpectedString(pattern, count);

        final Queue<Future<ClassicHttpResponse>> queue = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < 30; i++) {
            final BasicClassicHttpRequest request = new BasicClassicHttpRequest("POST", createRequestUri(pattern, count));
            request.setVersion(HttpVersion.HTTP_1_0);
            final NStringEntity entity = new NStringEntity(expectedPattern, ContentType.DEFAULT_TEXT);
            request.setEntity(entity);
            final Future<ClassicHttpResponse> future = this.client.execute(target, request);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            final Future<ClassicHttpResponse> future = queue.remove();
            final ClassicHttpResponse response = future.get();
            Assert.assertNotNull(response);
            Assert.assertEquals(HttpVersion.HTTP_1_1, response.getVersion());
            final Header h1 = response.getFirstHeader("Version");
            Assert.assertNotNull(h1);
            Assert.assertEquals("1.0", h1.getValue());
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

        final Queue<Future<ClassicHttpResponse>> queue = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < 30; i++) {
            final BasicClassicHttpRequest request = new BasicClassicHttpRequest(
                    "POST", createRequestUri(pattern, count));
            request.setEntity(null);
            final Future<ClassicHttpResponse> future = this.client.execute(target, request);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            final Future<ClassicHttpResponse> future = queue.remove();
            final ClassicHttpResponse response = future.get();
            Assert.assertNotNull(response);
            Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        }
    }

    @Test
    public void testHttpPostNoContentLength() throws Exception {
        this.server.registerHandler("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));

        // Rewire client
        this.client = new HttpClientNio(
                new ImmutableHttpProcessor(
                        new RequestTargetHost(),
                        new RequestConnControl(),
                        new RequestUserAgent(),
                        new RequestExpectContinue()),
                createHttpAsyncRequestExecutor(),
                createClientConnectionFactory(),
                createClientIOReactorConfig());

        final HttpHost target = start();

        this.client.setMaxPerRoute(3);
        this.client.setMaxTotal(3);

        final String pattern = RndTestPatternGenerator.generateText();
        final int count = RndTestPatternGenerator.generateCount(1000);

        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(
                "POST", createRequestUri(pattern, count));
        request.setEntity(null);

        final Future<ClassicHttpResponse> future = this.client.execute(target, request);

        final ClassicHttpResponse response = future.get();
        Assert.assertNotNull(response);
        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
    }

    @Test
    public void testHttpPostIdentity() throws Exception {
        this.server.registerHandler("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));

        // Rewire client
        this.client = new HttpClientNio(
                new ImmutableHttpProcessor(
                        new HttpRequestInterceptor() {

                            @Override
                            public void process(
                                    final ClassicHttpRequest request,
                                    final HttpContext context) throws HttpException, IOException {
                                request.addHeader(HttpHeaders.TRANSFER_ENCODING, "identity");
                            }

                        },
                        new RequestTargetHost(),
                        new RequestConnControl(),
                        new RequestUserAgent(),
                        new RequestExpectContinue()),
                createHttpAsyncRequestExecutor(),
                createClientConnectionFactory(),
                createClientIOReactorConfig());

        final HttpHost target = start();

        this.client.setMaxPerRoute(3);
        this.client.setMaxTotal(3);

        final String pattern = RndTestPatternGenerator.generateText();
        final int count = RndTestPatternGenerator.generateCount(1000);

        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(
                "POST", createRequestUri(pattern, count));
        request.setEntity(null);

        final Future<ClassicHttpResponse> future = this.client.execute(target, request);

        final ClassicHttpResponse response = future.get();
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

        final Queue<Future<ClassicHttpResponse>> queue = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < 30; i++) {
            final BasicClassicHttpRequest request = new BasicClassicHttpRequest(
                    "POST", createRequestUri(pattern, count));
            final NStringEntity entity = new NStringEntity(expectedPattern, ContentType.DEFAULT_TEXT);
            request.setEntity(entity);

            final HttpContext context = new BasicHttpContext();
            final Future<ClassicHttpResponse> future = this.client.execute(target, request, context);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            final Future<ClassicHttpResponse> future = queue.remove();
            final ClassicHttpResponse response = future.get();
            Assert.assertNotNull(response);
            Assert.assertEquals(expectedPattern, EntityUtils.toString(response.getEntity()));
        }
    }

    @Test
    public void testHttpPostsWithExpectationVerification() throws Exception {
        final HttpAsyncExpectationVerifier expectationVerifier = new HttpAsyncExpectationVerifier() {

            @Override
            public void verify(
                    final HttpAsyncExchange httpexchange,
                    final HttpContext context) throws HttpException {
                final ClassicHttpRequest request = httpexchange.getRequest();
                final String s = request.getPath();
                if (!s.equals("AAAAAx10")) {
                    final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_EXPECTATION_FAILED, "Expectation failed");
                    response.setEntity(new NStringEntity("Expectation failed", ContentType.TEXT_PLAIN));
                    httpexchange.submitResponse(new BasicAsyncResponseProducer(response));
                } else {
                    httpexchange.submitResponse();
                }
            }

        };
        // Rewire server
        this.server = new HttpServerNio(
                createServerHttpProcessor(),
                createServerConnectionFactory(),
                expectationVerifier,
                createServerIOReactorConfig());

        this.server.registerHandler("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));

        final HttpHost target = start();

        final BasicClassicHttpRequest request1 = new BasicClassicHttpRequest(
                "POST", createRequestUri("AAAAA", 10));
        request1.setEntity(new NStringEntity(createExpectedString("AAAAA", 10)));
        final BasicClassicHttpRequest request2 = new BasicClassicHttpRequest(
                "POST", createRequestUri("AAAAA", 10));
        request2.setEntity(new NStringEntity(createExpectedString("AAAAA", 10)));
        final BasicClassicHttpRequest request3 = new BasicClassicHttpRequest(
                "POST", createRequestUri("BBBBB", 10));
        request3.setEntity(new NStringEntity(createExpectedString("BBBBB", 10)));

        final ClassicHttpRequest[] requests = new ClassicHttpRequest[] { request1, request2, request3 };

        final Queue<Future<ClassicHttpResponse>> queue = new ConcurrentLinkedQueue<>();
        for (final ClassicHttpRequest request : requests) {
            final HttpContext context = new BasicHttpContext();
            final Future<ClassicHttpResponse> future = this.client.execute(target, request, context);
            queue.add(future);
        }

        final Future<ClassicHttpResponse> future1 = queue.remove();
        final ClassicHttpResponse response1 = future1.get();
        Assert.assertEquals(HttpStatus.SC_OK, response1.getCode());

        final Future<ClassicHttpResponse> future2 = queue.remove();
        final ClassicHttpResponse response2 = future2.get();
        Assert.assertEquals(HttpStatus.SC_OK, response2.getCode());

        final Future<ClassicHttpResponse> future3 = queue.remove();
        final ClassicHttpResponse response3 = future3.get();
        Assert.assertEquals(HttpStatus.SC_EXPECTATION_FAILED, response3.getCode());
    }

    @Test
    public void testHttpHeadsDelayedResponse() throws Exception {

        class DelayedRequestHandler implements HttpAsyncRequestHandler<ClassicHttpRequest> {

            private final SimpleRequestHandler requestHandler;

            public DelayedRequestHandler() {
                super();
                this.requestHandler = new SimpleRequestHandler();
            }

            @Override
            public HttpAsyncRequestConsumer<ClassicHttpRequest> processRequest(
                    final ClassicHttpRequest request,
                    final HttpContext context) {
                return new BasicAsyncRequestConsumer();
            }

            @Override
            public void handle(
                    final ClassicHttpRequest request,
                    final HttpAsyncExchange httpexchange,
                    final HttpContext context) throws HttpException, IOException {
                final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
                new Thread() {
                    @Override
                    public void run() {
                        // Wait a bit, to make sure this is delayed.
                        try { Thread.sleep(100); } catch(final InterruptedException ie) {}
                        // Set the entity after delaying...
                        try {
                            requestHandler.handle(request, response, context);
                        } catch (final Exception ex) {
                            response.setCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
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

        final Queue<Future<ClassicHttpResponse>> queue = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < 30; i++) {
            final BasicClassicHttpRequest request = new BasicClassicHttpRequest("HEAD", createRequestUri(pattern, count));
            final Future<ClassicHttpResponse> future = this.client.execute(target, request);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            final Future<ClassicHttpResponse> future = queue.remove();
            final ClassicHttpResponse response = future.get();
            Assert.assertNotNull(response);
            Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        }
    }

    @Test
    public void testHttpPostsWithExpectationVerificationDelayedResponse() throws Exception {
        final HttpAsyncExpectationVerifier expectationVerifier = new HttpAsyncExpectationVerifier() {

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
                        final ClassicHttpRequest request = httpexchange.getRequest();
                        final String s = request.getPath();
                        if (!s.equals("AAAAAx10")) {
                            final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_EXPECTATION_FAILED, "Expectation failed");
                            response.setEntity(new NStringEntity("Expectation failed", ContentType.TEXT_PLAIN));
                            httpexchange.submitResponse(new BasicAsyncResponseProducer(response));
                        } else {
                            httpexchange.submitResponse();
                        }
                    }
                }.start();
            }

        };
        // Rewire server
        this.server = new HttpServerNio(
                createServerHttpProcessor(),
                createServerConnectionFactory(),
                expectationVerifier,
                createServerIOReactorConfig());
        this.server.registerHandler("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));

        final HttpHost target = start();

        final BasicClassicHttpRequest request1 = new BasicClassicHttpRequest(
                "POST", createRequestUri("AAAAA", 10));
        request1.setEntity(new NStringEntity(createExpectedString("AAAAA", 10)));
        final BasicClassicHttpRequest request2 = new BasicClassicHttpRequest(
                "POST", createRequestUri("AAAAA", 10));
        request2.setEntity(new NStringEntity(createExpectedString("AAAAA", 10)));
        final BasicClassicHttpRequest request3 = new BasicClassicHttpRequest(
                "POST", createRequestUri("BBBBB", 10));
        request3.setEntity(new NStringEntity(createExpectedString("BBBBB", 10)));

        final ClassicHttpRequest[] requests = new ClassicHttpRequest[] { request1, request2, request3 };

        final Queue<Future<ClassicHttpResponse>> queue = new ConcurrentLinkedQueue<>();
        for (final ClassicHttpRequest request : requests) {
            final HttpContext context = new BasicHttpContext();
            final Future<ClassicHttpResponse> future = this.client.execute(target, request, context);
            queue.add(future);
        }

        final Future<ClassicHttpResponse> future1 = queue.remove();
        final ClassicHttpResponse response1 = future1.get();
        Assert.assertEquals(HttpStatus.SC_OK, response1.getCode());

        final Future<ClassicHttpResponse> future2 = queue.remove();
        final ClassicHttpResponse response2 = future2.get();
        Assert.assertEquals(HttpStatus.SC_OK, response2.getCode());

        final Future<ClassicHttpResponse> future3 = queue.remove();
        final ClassicHttpResponse response3 = future3.get();
        Assert.assertEquals(HttpStatus.SC_EXPECTATION_FAILED, response3.getCode());
    }

    @Test
    public void testHttpPostsFailedExpectionContentLengthNonReusableConnection() throws Exception {
        final HttpAsyncExpectationVerifier expectationVerifier = new HttpAsyncExpectationVerifier() {

            @Override
            public void verify(
            final HttpAsyncExchange httpexchange,
            final HttpContext context) throws HttpException {
                new Thread() {
                    @Override
                    public void run() {
                        final ClassicHttpRequest request = httpexchange.getRequest();
                        final String s = request.getPath();
                        if (!s.equals("AAAAAx10")) {
                            final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_EXPECTATION_FAILED, "Expectation failed");
                            response.setEntity(new NStringEntity("Expectation failed", ContentType.TEXT_PLAIN));
                            httpexchange.submitResponse(new BasicAsyncResponseProducer(response));
                        } else {
                            httpexchange.submitResponse();
                        }
                    }
                }.start();
            }

        };
        // Rewire server
        this.server = new HttpServerNio(
                createServerHttpProcessor(),
                createServerConnectionFactory(),
                expectationVerifier,
                createServerIOReactorConfig());
        this.server.registerHandler("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));

        this.client.setMaxPerRoute(1);
        this.client.setMaxTotal(1);

        final HttpHost target = start();

        for (int i = 0; i < 3; i++) {
            final BasicClassicHttpRequest request1 = new BasicClassicHttpRequest("POST", createRequestUri("AAAAA", 10));
            final HttpEntity entity1 = new NStringEntity(createExpectedString("AAAAA", 10));
            request1.setEntity(entity1);

            final HttpContext context = new BasicHttpContext();
            final Future<ClassicHttpResponse> future1 = this.client.execute(target, request1, context);
            final ClassicHttpResponse response1 = future1.get();
            Assert.assertEquals(HttpStatus.SC_OK, response1.getCode());

            final BasicClassicHttpRequest request2 = new BasicClassicHttpRequest("POST", createRequestUri("BBBBB", 10));
            final HttpEntity entity2 = new NStringEntity(createExpectedString("BBBBB", 500));
            request2.setEntity(entity2);

            final Future<ClassicHttpResponse> future2 = this.client.execute(target, request2, context);
            final ClassicHttpResponse response2 = future2.get();
            Assert.assertEquals(HttpStatus.SC_EXPECTATION_FAILED, response2.getCode());
        }
    }

    @Test
    public void testHttpPostsFailedExpectionConnectionReuse() throws Exception {
        final HttpAsyncExpectationVerifier expectationVerifier = new HttpAsyncExpectationVerifier() {

            @Override
            public void verify(
                    final HttpAsyncExchange httpexchange,
                    final HttpContext context) throws HttpException {
                new Thread() {
                    @Override
                    public void run() {
                        final ClassicHttpRequest request = httpexchange.getRequest();
                        final String s = request.getPath();
                        if (!s.equals("AAAAAx10")) {
                            final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_EXPECTATION_FAILED, "Expectation failed");
                            response.setEntity(new NStringEntity("Expectation failed", ContentType.TEXT_PLAIN));
                            httpexchange.submitResponse(new BasicAsyncResponseProducer(response));
                        } else {
                            httpexchange.submitResponse();
                        }
                    }
                }.start();
            }

        };
        // Rewire server
        this.server = new HttpServerNio(
                createServerHttpProcessor(),
                createServerConnectionFactory(),
                expectationVerifier,
                createServerIOReactorConfig());
        this.server.registerHandler("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));

        this.client.setMaxPerRoute(1);
        this.client.setMaxTotal(1);

        final HttpHost target = start();

        for (int i = 0; i < 10; i++) {
            final BasicClassicHttpRequest request1 = new BasicClassicHttpRequest("POST", createRequestUri("AAAAA", 10));
            final NStringEntity entity1 = new NStringEntity(createExpectedString("AAAAA", 10));
            entity1.setChunked(i % 2 == 0);
            request1.setEntity(entity1);

            final HttpContext context = new BasicHttpContext();
            final Future<ClassicHttpResponse> future1 = this.client.execute(target, request1, context);
            final ClassicHttpResponse response1 = future1.get();
            Assert.assertEquals(HttpStatus.SC_OK, response1.getCode());

            final BasicClassicHttpRequest request2 = new BasicClassicHttpRequest("POST", createRequestUri("BBBBB", 10));
            final NStringEntity entity2 = new NStringEntity(createExpectedString("BBBBB", 10));
            entity2.setChunked(i % 2 == 0);
            request2.setEntity(entity2);

            final Future<ClassicHttpResponse> future2 = this.client.execute(target, request2, context);
            final ClassicHttpResponse response2 = future2.get();
            Assert.assertEquals(HttpStatus.SC_EXPECTATION_FAILED, response2.getCode());
        }
    }

    @Test
    public void testHttpPostsFailedExpectionConnectionReuseLateResponseBody() throws Exception {
        final HttpAsyncExpectationVerifier expectationVerifier = new HttpAsyncExpectationVerifier() {

            @Override
            public void verify(
                    final HttpAsyncExchange httpexchange,
                    final HttpContext context) throws HttpException {
                new Thread() {
                    @Override
                    public void run() {
                        final ClassicHttpRequest request = httpexchange.getRequest();
                        final String s = request.getPath();
                        if (!s.equals("AAAAAx10")) {
                            final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_EXPECTATION_FAILED, "Expectation failed");
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

        };
        // Rewire server
        this.server = new HttpServerNio(
                createServerHttpProcessor(),
                createServerConnectionFactory(),
                expectationVerifier,
                createServerIOReactorConfig());
        this.server.registerHandler("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));

        this.client.setMaxPerRoute(1);
        this.client.setMaxTotal(1);

        final HttpHost target = start();

        for (int i = 0; i < 10; i++) {
            final BasicClassicHttpRequest request1 = new BasicClassicHttpRequest("POST", createRequestUri("AAAAA", 10));
            final NStringEntity entity1 = new NStringEntity(createExpectedString("AAAAA", 10));
            entity1.setChunked(i % 2 == 0);
            request1.setEntity(entity1);

            final HttpContext context = new BasicHttpContext();
            final Future<ClassicHttpResponse> future1 = this.client.execute(target, request1, context);
            final ClassicHttpResponse response1 = future1.get();
            Assert.assertEquals(HttpStatus.SC_OK, response1.getCode());

            final BasicClassicHttpRequest request2 = new BasicClassicHttpRequest("POST", createRequestUri("BBBBB", 10));
            final NStringEntity entity2 = new NStringEntity(createExpectedString("BBBBB", 10));
            entity2.setChunked(i % 2 == 0);
            request2.setEntity(entity2);

            final Future<ClassicHttpResponse> future2 = this.client.execute(target, request2, context);
            final ClassicHttpResponse response2 = future2.get();
            Assert.assertEquals(HttpStatus.SC_EXPECTATION_FAILED, response2.getCode());
        }
    }

    @Test
    public void testHttpExceptionInHandler() throws Exception {

        class FailingRequestHandler implements HttpAsyncRequestHandler<ClassicHttpRequest> {

            public FailingRequestHandler() {
                super();
            }

            @Override
            public HttpAsyncRequestConsumer<ClassicHttpRequest> processRequest(
                    final ClassicHttpRequest request,
                    final HttpContext context) {
                return new BasicAsyncRequestConsumer();
            }

            @Override
            public void handle(
                    final ClassicHttpRequest request,
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

        final Queue<Future<ClassicHttpResponse>> queue = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < 1; i++) {
            final BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", createRequestUri(pattern, count));
            final Future<ClassicHttpResponse> future = this.client.execute(target, request);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            final Future<ClassicHttpResponse> future = queue.remove();
            final ClassicHttpResponse response = future.get();
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

        final Queue<Future<ClassicHttpResponse>> queue = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < 30; i++) {
            final BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", createRequestUri(pattern, count));
            final Future<ClassicHttpResponse> future = this.client.execute(target, request);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            final Future<ClassicHttpResponse> future = queue.remove();
            final ClassicHttpResponse response = future.get();
            Assert.assertNotNull(response);
            Assert.assertEquals(HttpStatus.SC_NOT_IMPLEMENTED, response.getCode());
        }
    }

    @Test
    public void testResponseNoContent() throws Exception {
        this.server.registerHandler("*", new BasicAsyncRequestHandler(new HttpRequestHandler() {

            @Override
            public void handle(
                    final ClassicHttpRequest request,
                    final ClassicHttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                response.setCode(HttpStatus.SC_NO_CONTENT);
            }

        }));
        final HttpHost target = start();

        this.client.setMaxPerRoute(3);
        this.client.setMaxTotal(3);

        final Queue<Future<ClassicHttpResponse>> queue = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < 30; i++) {
            final BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/");
            final Future<ClassicHttpResponse> future = this.client.execute(target, request);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            final Future<ClassicHttpResponse> future = queue.remove();
            final ClassicHttpResponse response = future.get();
            Assert.assertNotNull(response);
            Assert.assertNull(response.getEntity());
        }
    }

    @Test
    public void testAbsentHostHeader() throws Exception {
        this.server.registerHandler("*", new BasicAsyncRequestHandler(new HttpRequestHandler() {

            @Override
            public void handle(
                    final ClassicHttpRequest request,
                    final ClassicHttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                response.setCode(HttpStatus.SC_OK);
                response.setEntity(new NStringEntity("All is well", StandardCharsets.US_ASCII));
            }

        }));

        // Rewire client
        this.client = new HttpClientNio(
                new ImmutableHttpProcessor(new RequestContent(), new RequestConnControl()),
                createHttpAsyncRequestExecutor(),
                createClientConnectionFactory(),
                createClientIOReactorConfig());

        final HttpHost target = start();

        this.client.setMaxPerRoute(3);
        this.client.setMaxTotal(3);

        final BasicClassicHttpRequest request1 = new BasicClassicHttpRequest("GET", "/");
        request1.setVersion(HttpVersion.HTTP_1_0);
        final Future<ClassicHttpResponse> future1 = this.client.execute(target, request1);
        final ClassicHttpResponse response1 = future1.get();
        Assert.assertNotNull(response1);
        Assert.assertEquals(200, response1.getCode());
        final BasicClassicHttpRequest request2 = new BasicClassicHttpRequest("GET", "/");
        final Future<ClassicHttpResponse> future2 = this.client.execute(target, request2);
        final ClassicHttpResponse response2 = future2.get();
        Assert.assertNotNull(response2);
        Assert.assertEquals(400, response2.getCode());
    }

}
