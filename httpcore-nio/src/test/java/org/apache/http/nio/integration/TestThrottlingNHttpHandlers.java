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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.http.HttpCoreNIOTestBase;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.LoggingClientConnectionFactory;
import org.apache.http.LoggingServerConnectionFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.nio.DefaultNHttpClientConnection;
import org.apache.http.impl.nio.DefaultNHttpServerConnection;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.nio.NHttpConnectionFactory;
import org.apache.http.nio.entity.NByteArrayEntity;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.nio.protocol.HttpRequestExecutionHandler;
import org.apache.http.nio.protocol.ThrottlingHttpClientHandler;
import org.apache.http.nio.protocol.ThrottlingHttpServiceHandler;
import org.apache.http.nio.reactor.IOReactorStatus;
import org.apache.http.nio.reactor.ListenerEndpoint;
import org.apache.http.nio.reactor.SessionRequest;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpExpectationVerifier;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.ImmutableHttpProcessor;
import org.apache.http.protocol.RequestConnControl;
import org.apache.http.protocol.RequestContent;
import org.apache.http.protocol.RequestExpectContinue;
import org.apache.http.protocol.RequestTargetHost;
import org.apache.http.protocol.RequestUserAgent;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.protocol.ResponseDate;
import org.apache.http.protocol.ResponseServer;
import org.apache.http.testserver.SimpleEventListener;
import org.apache.http.testserver.SimpleHttpRequestHandlerResolver;
import org.apache.http.util.EncodingUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * HttpCore NIO integration tests using throttling versions of the
 * protocol handlers.
 */
@Deprecated
public class TestThrottlingNHttpHandlers extends HttpCoreNIOTestBase {

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

    private ExecutorService execService;

    @Before
    public void initExecService() throws Exception {
        this.execService = Executors.newCachedThreadPool();
    }

    @After
    public void shutDownExecService() {
        this.execService.shutdownNow();
    }

    private void executeStandardTest(
            final HttpRequestHandler requestHandler,
            final HttpRequestExecutionHandler requestExecutionHandler) throws Exception {
        int connNo = 3;
        int reqNo = 20;
        Job[] jobs = new Job[connNo * reqNo];
        for (int i = 0; i < jobs.length; i++) {
            jobs[i] = new Job();
        }
        Queue<Job> queue = new ConcurrentLinkedQueue<Job>();
        for (int i = 0; i < jobs.length; i++) {
            queue.add(jobs[i]);
        }

        ThrottlingHttpServiceHandler serviceHandler = new ThrottlingHttpServiceHandler(
                this.serverHttpProc,
                new DefaultHttpResponseFactory(),
                new DefaultConnectionReuseStrategy(),
                this.execService,
                this.serverParams);

        serviceHandler.setHandlerResolver(
                new SimpleHttpRequestHandlerResolver(requestHandler));
        serviceHandler.setEventListener(
                new SimpleEventListener());

        ThrottlingHttpClientHandler clientHandler = new ThrottlingHttpClientHandler(
                this.clientHttpProc,
                requestExecutionHandler,
                new DefaultConnectionReuseStrategy(),
                this.execService,
                this.clientParams);

        clientHandler.setEventListener(
                new SimpleEventListener());

        this.server.start(serviceHandler);
        this.client.start(clientHandler);

        ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();
        InetSocketAddress serverAddress = (InetSocketAddress) endpoint.getAddress();

        Assert.assertEquals("Test server status", IOReactorStatus.ACTIVE, this.server.getStatus());

        Queue<SessionRequest> connRequests = new LinkedList<SessionRequest>();
        for (int i = 0; i < connNo; i++) {
            SessionRequest sessionRequest = this.client.openConnection(
                    new InetSocketAddress("localhost", serverAddress.getPort()),
                    queue);
            connRequests.add(sessionRequest);
        }

        while (!connRequests.isEmpty()) {
            SessionRequest sessionRequest = connRequests.remove();
            sessionRequest.waitFor();
            if (sessionRequest.getException() != null) {
                throw sessionRequest.getException();
            }
            Assert.assertNotNull(sessionRequest.getSession());
        }

        Assert.assertEquals("Test client status", IOReactorStatus.ACTIVE, this.client.getStatus());

        for (int i = 0; i < jobs.length; i++) {
            Job testjob = jobs[i];
            testjob.waitFor();
            if (testjob.isSuccessful()) {
                Assert.assertEquals(HttpStatus.SC_OK, testjob.getStatusCode());
                Assert.assertEquals(testjob.getExpected(), testjob.getResult());
            } else {
                Assert.fail(testjob.getFailureMessage());
            }
        }
    }

    /**
     * This test case executes a series of simple (non-pipelined) GET requests
     * over multiple connections.
     */
    @Test
    public void testSimpleHttpGets() throws Exception {
        HttpRequestExecutionHandler requestExecutionHandler = new RequestExecutionHandler() {

            @Override
            protected HttpRequest generateRequest(Job testjob) {
                String s = testjob.getPattern() + "x" + testjob.getCount();
                return new BasicHttpRequest("GET", s);
            }

        };
        executeStandardTest(new RequestHandler(), requestExecutionHandler);
    }

    /**
     * This test case executes a series of simple (non-pipelined) POST requests
     * with content length delimited content over multiple connections.
     */
    @Test
    public void testSimpleHttpPostsWithContentLength() throws Exception {
        HttpRequestExecutionHandler requestExecutionHandler = new RequestExecutionHandler() {

            @Override
            protected HttpRequest generateRequest(Job testjob) {
                String s = testjob.getPattern() + "x" + testjob.getCount();
                HttpEntityEnclosingRequest r = new BasicHttpEntityEnclosingRequest("POST", s);
                NStringEntity entity = null;
                try {
                    entity = new NStringEntity(testjob.getExpected(), "US-ASCII");
                    entity.setChunked(false);
                } catch (UnsupportedEncodingException ignore) {
                }
                r.setEntity(entity);
                return r;
            }

        };
        executeStandardTest(new RequestHandler(), requestExecutionHandler);
    }

    /**
     * This test case executes a series of simple (non-pipelined) POST requests
     * with chunk coded content content over multiple connections.
     */
    @Test
    public void testSimpleHttpPostsChunked() throws Exception {
        HttpRequestExecutionHandler requestExecutionHandler = new RequestExecutionHandler() {

            @Override
            protected HttpRequest generateRequest(Job testjob) {
                String s = testjob.getPattern() + "x" + testjob.getCount();
                HttpEntityEnclosingRequest r = new BasicHttpEntityEnclosingRequest("POST", s);
                NStringEntity entity = null;
                try {
                    entity = new NStringEntity(testjob.getExpected(), "US-ASCII");
                    entity.setChunked(true);
                } catch (UnsupportedEncodingException ignore) {
                }
                r.setEntity(entity);
                return r;
            }

        };
        executeStandardTest(new RequestHandler(), requestExecutionHandler);
    }

    /**
     * This test case executes a series of simple (non-pipelined) HTTP/1.0
     * POST requests over multiple persistent connections.
     */
    @Test
    public void testSimpleHttpPostsHTTP10() throws Exception {
        HttpRequestExecutionHandler requestExecutionHandler = new RequestExecutionHandler() {

            @Override
            protected HttpRequest generateRequest(Job testjob) {
                String s = testjob.getPattern() + "x" + testjob.getCount();
                HttpEntityEnclosingRequest r = new BasicHttpEntityEnclosingRequest("POST", s,
                        HttpVersion.HTTP_1_0);
                NStringEntity entity = null;
                try {
                    entity = new NStringEntity(testjob.getExpected(), "US-ASCII");
                } catch (UnsupportedEncodingException ignore) {
                }
                r.setEntity(entity);
                return r;
            }

        };
        executeStandardTest(new RequestHandler(), requestExecutionHandler);
    }

    /**
     * This test case executes a series of simple (non-pipelined) POST requests
     * over multiple connections using the 'expect: continue' handshake.
     */
    @Test
    public void testHttpPostsWithExpectContinue() throws Exception {
        HttpRequestExecutionHandler requestExecutionHandler = new RequestExecutionHandler() {

            @Override
            protected HttpRequest generateRequest(Job testjob) {
                String s = testjob.getPattern() + "x" + testjob.getCount();
                HttpEntityEnclosingRequest r = new BasicHttpEntityEnclosingRequest("POST", s);
                NStringEntity entity = null;
                try {
                    entity = new NStringEntity(testjob.getExpected(), "US-ASCII");
                } catch (UnsupportedEncodingException ignore) {
                }
                r.setEntity(entity);
                r.getParams().setBooleanParameter(CoreProtocolPNames.USE_EXPECT_CONTINUE, true);
                return r;
            }

        };
        executeStandardTest(new RequestHandler(), requestExecutionHandler);
    }

    /**
     * This test case executes a series of simple (non-pipelined) POST requests
     * over multiple connections that do not meet the target server expectations.
     */
    @Test
    public void testHttpPostsWithExpectationVerification() throws Exception {
        Job[] jobs = new Job[3];
        jobs[0] = new Job("AAAAA", 10);
        jobs[1] = new Job("AAAAA", 10);
        jobs[2] = new Job("BBBBB", 20);
        Queue<Job> queue = new ConcurrentLinkedQueue<Job>();
        for (int i = 0; i < jobs.length; i++) {
            queue.add(jobs[i]);
        }

        HttpExpectationVerifier expectationVerifier = new HttpExpectationVerifier() {

            public void verify(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException {
                String s = request.getRequestLine().getUri();
                if (!s.equals("AAAAAx10")) {
                    response.setStatusCode(HttpStatus.SC_EXPECTATION_FAILED);
                    NByteArrayEntity outgoing = new NByteArrayEntity(
                            EncodingUtils.getAsciiBytes("Expectation failed"));
                    response.setEntity(outgoing);
                }
            }

        };

        HttpRequestExecutionHandler requestExecutionHandler = new RequestExecutionHandler() {

            @Override
            protected HttpRequest generateRequest(Job testjob) {
                String s = testjob.getPattern() + "x" + testjob.getCount();
                HttpEntityEnclosingRequest r = new BasicHttpEntityEnclosingRequest("POST", s);
                NStringEntity entity = null;
                try {
                    entity = new NStringEntity(testjob.getExpected(), "US-ASCII");
                } catch (UnsupportedEncodingException ignore) {
                }
                r.setEntity(entity);
                r.getParams().setBooleanParameter(CoreProtocolPNames.USE_EXPECT_CONTINUE, true);
                return r;
            }

        };

        HttpProcessor serverHttpProc = new ImmutableHttpProcessor(new HttpResponseInterceptor[] {
                new ResponseDate(),
                new ResponseServer(),
                new ResponseContent(),
                new ResponseConnControl()
        });

        ThrottlingHttpServiceHandler serviceHandler = new ThrottlingHttpServiceHandler(
                serverHttpProc,
                new DefaultHttpResponseFactory(),
                new DefaultConnectionReuseStrategy(),
                this.execService,
                this.serverParams);

        serviceHandler.setHandlerResolver(
                new SimpleHttpRequestHandlerResolver(new RequestHandler()));
        serviceHandler.setExpectationVerifier(
                expectationVerifier);
        serviceHandler.setEventListener(
                new SimpleEventListener());

        HttpProcessor clientHttpProc = new ImmutableHttpProcessor(new HttpRequestInterceptor[] {
                new RequestContent(),
                new RequestTargetHost(),
                new RequestConnControl(),
                new RequestUserAgent(),
                new RequestExpectContinue()});

        ThrottlingHttpClientHandler clientHandler = new ThrottlingHttpClientHandler(
                clientHttpProc,
                requestExecutionHandler,
                new DefaultConnectionReuseStrategy(),
                this.execService,
                this.clientParams);

        clientHandler.setEventListener(
                new SimpleEventListener());

        this.server.start(serviceHandler);
        this.client.start(clientHandler);

        ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();
        InetSocketAddress serverAddress = (InetSocketAddress) endpoint.getAddress();

        Assert.assertEquals("Test server status", IOReactorStatus.ACTIVE, this.server.getStatus());

        SessionRequest sessionRequest = this.client.openConnection(
                new InetSocketAddress("localhost", serverAddress.getPort()),
                queue);

        sessionRequest.waitFor();
        if (sessionRequest.getException() != null) {
            throw sessionRequest.getException();
        }
        Assert.assertNotNull(sessionRequest.getSession());

        Assert.assertEquals("Test client status", IOReactorStatus.ACTIVE, this.client.getStatus());

        for (int i = 0; i < 2; i++) {
            Job testjob = jobs[i];
            testjob.waitFor();
            if (testjob.isSuccessful()) {
                Assert.assertEquals(testjob.getExpected(), testjob.getResult());
            } else {
                Assert.fail(testjob.getFailureMessage());
            }
        }
        Job failedExpectation = jobs[2];
        failedExpectation.waitFor();
        Assert.assertEquals(HttpStatus.SC_EXPECTATION_FAILED, failedExpectation.getStatusCode());
    }

    /**
     * This test case executes a series of simple (non-pipelined) HEAD requests
     * over multiple connections.
     */
    @Test
    public void testSimpleHttpHeads() throws Exception {
        int connNo = 3;
        int reqNo = 20;
        Job[] jobs = new Job[connNo * reqNo];
        for (int i = 0; i < jobs.length; i++) {
            jobs[i] = new Job();
        }
        Queue<Job> queue = new ConcurrentLinkedQueue<Job>();
        for (int i = 0; i < jobs.length; i++) {
            queue.add(jobs[i]);
        }

        HttpRequestExecutionHandler requestExecutionHandler = new RequestExecutionHandler() {

            @Override
            protected HttpRequest generateRequest(Job testjob) {
                String s = testjob.getPattern() + "x" + testjob.getCount();
                return new BasicHttpRequest("HEAD", s);
            }

        };

        HttpProcessor serverHttpProc = new ImmutableHttpProcessor(new HttpResponseInterceptor[] {
                new ResponseDate(),
                new ResponseServer(),
                new ResponseContent(),
                new ResponseConnControl()
        });

        ThrottlingHttpServiceHandler serviceHandler = new ThrottlingHttpServiceHandler(
                serverHttpProc,
                new DefaultHttpResponseFactory(),
                new DefaultConnectionReuseStrategy(),
                this.execService,
                this.serverParams);

        serviceHandler.setHandlerResolver(
                new SimpleHttpRequestHandlerResolver(new RequestHandler()));
        serviceHandler.setEventListener(
                new SimpleEventListener());

        HttpProcessor clientHttpProc = new ImmutableHttpProcessor(new HttpRequestInterceptor[] {
                new RequestContent(),
                new RequestTargetHost(),
                new RequestConnControl(),
                new RequestUserAgent(),
                new RequestExpectContinue()});

        ThrottlingHttpClientHandler clientHandler = new ThrottlingHttpClientHandler(
                clientHttpProc,
                requestExecutionHandler,
                new DefaultConnectionReuseStrategy(),
                this.execService,
                this.clientParams);

        clientHandler.setEventListener(new SimpleEventListener());

        this.server.start(serviceHandler);
        this.client.start(clientHandler);

        ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();
        InetSocketAddress serverAddress = (InetSocketAddress) endpoint.getAddress();

        Assert.assertEquals("Test server status", IOReactorStatus.ACTIVE, this.server.getStatus());

        Queue<SessionRequest> connRequests = new LinkedList<SessionRequest>();
        for (int i = 0; i < connNo; i++) {
            SessionRequest sessionRequest = this.client.openConnection(
                    new InetSocketAddress("localhost", serverAddress.getPort()),
                    queue);
            connRequests.add(sessionRequest);
        }

        while (!connRequests.isEmpty()) {
            SessionRequest sessionRequest = connRequests.remove();
            sessionRequest.waitFor();
            if (sessionRequest.getException() != null) {
                throw sessionRequest.getException();
            }
            Assert.assertNotNull(sessionRequest.getSession());
        }

        Assert.assertEquals("Test client status", IOReactorStatus.ACTIVE, this.client.getStatus());

        for (int i = 0; i < jobs.length; i++) {
            Job testjob = jobs[i];
            testjob.waitFor();
            if (testjob.getFailureMessage() != null) {
                Assert.fail(testjob.getFailureMessage());
            }
            Assert.assertEquals(HttpStatus.SC_OK, testjob.getStatusCode());
            Assert.assertNull(testjob.getResult());
        }
    }

    /**
     * This test case tests if the protocol handler can correctly deal
     * with requests with partially consumed content.
     */
    @Test
    public void testSimpleHttpPostsContentNotConsumed() throws Exception {
        HttpRequestHandler requestHandler = new HttpRequestHandler() {

            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {

                // Request content body has not been consumed!!!
                response.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
                NStringEntity outgoing = new NStringEntity("Ooopsie");
                response.setEntity(outgoing);
            }

        };
        HttpRequestExecutionHandler requestExecutionHandler = new RequestExecutionHandler() {

            @Override
            protected HttpRequest generateRequest(Job testjob) {
                String s = testjob.getPattern() + "x" + testjob.getCount();
                HttpEntityEnclosingRequest r = new BasicHttpEntityEnclosingRequest("POST", s);
                NStringEntity entity = null;
                try {
                    entity = new NStringEntity(testjob.getExpected(), "US-ASCII");
                    entity.setChunked(testjob.getCount() % 2 == 0);
                } catch (UnsupportedEncodingException ignore) {
                }
                r.setEntity(entity);
                return r;
            }

        };
        int connNo = 3;
        int reqNo = 20;
        Job[] jobs = new Job[connNo * reqNo];
        for (int i = 0; i < jobs.length; i++) {
            jobs[i] = new Job();
        }
        Queue<Job> queue = new ConcurrentLinkedQueue<Job>();
        for (int i = 0; i < jobs.length; i++) {
            queue.add(jobs[i]);
        }

        HttpProcessor serverHttpProc = new ImmutableHttpProcessor(new HttpResponseInterceptor[] {
                new ResponseDate(),
                new ResponseServer(),
                new ResponseContent(),
                new ResponseConnControl()
        });

        ThrottlingHttpServiceHandler serviceHandler = new ThrottlingHttpServiceHandler(
                serverHttpProc,
                new DefaultHttpResponseFactory(),
                new DefaultConnectionReuseStrategy(),
                this.execService,
                this.serverParams);

        serviceHandler.setHandlerResolver(
                new SimpleHttpRequestHandlerResolver(requestHandler));
        serviceHandler.setEventListener(
                new SimpleEventListener());

        HttpProcessor clientHttpProc = new ImmutableHttpProcessor(new HttpRequestInterceptor[] {
                new RequestContent(),
                new RequestTargetHost(),
                new RequestConnControl(),
                new RequestUserAgent(),
                new RequestExpectContinue()});

        ThrottlingHttpClientHandler clientHandler = new ThrottlingHttpClientHandler(
                clientHttpProc,
                requestExecutionHandler,
                new DefaultConnectionReuseStrategy(),
                this.execService,
                this.clientParams);

        clientHandler.setEventListener(
                new SimpleEventListener());

        this.server.start(serviceHandler);
        this.client.start(clientHandler);

        ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();
        InetSocketAddress serverAddress = (InetSocketAddress) endpoint.getAddress();

        Assert.assertEquals("Test server status", IOReactorStatus.ACTIVE, this.server.getStatus());

        Queue<SessionRequest> connRequests = new LinkedList<SessionRequest>();
        for (int i = 0; i < connNo; i++) {
            SessionRequest sessionRequest = this.client.openConnection(
                    new InetSocketAddress("localhost", serverAddress.getPort()),
                    queue);
            connRequests.add(sessionRequest);
        }

        while (!connRequests.isEmpty()) {
            SessionRequest sessionRequest = connRequests.remove();
            sessionRequest.waitFor();
            if (sessionRequest.getException() != null) {
                throw sessionRequest.getException();
            }
            Assert.assertNotNull(sessionRequest.getSession());
        }

        Assert.assertEquals("Test client status", IOReactorStatus.ACTIVE, this.client.getStatus());

        for (int i = 0; i < jobs.length; i++) {
            Job testjob = jobs[i];
            testjob.waitFor();
            if (testjob.isSuccessful()) {
                Assert.assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, testjob.getStatusCode());
                Assert.assertEquals("Ooopsie", testjob.getResult());
            } else {
                Assert.fail(testjob.getFailureMessage());
            }
        }
    }

    @Test
    public void testInputThrottling() throws Exception {
        HttpRequestExecutionHandler requestExecutionHandler = new HttpRequestExecutionHandler() {

            public void initalizeContext(final HttpContext context, final Object attachment) {
                context.setAttribute("queue", attachment);
            }

            public HttpRequest submitRequest(final HttpContext context) {

                @SuppressWarnings("unchecked")
                Queue<Job> queue = (Queue<Job>) context.getAttribute("queue");
                if (queue == null) {
                    throw new IllegalStateException("Queue is null");
                }

                Job testjob = queue.poll();
                context.setAttribute("job", testjob);

                if (testjob != null) {
                    String s = testjob.getPattern() + "x" + testjob.getCount();
                    HttpEntityEnclosingRequest r = new BasicHttpEntityEnclosingRequest("POST", s);
                    StringEntity entity = null;
                    try {
                        entity = new StringEntity(testjob.getExpected(), "US-ASCII");
                        entity.setChunked(testjob.getCount() % 2 == 0);
                    } catch (UnsupportedEncodingException ignore) {
                    }
                    r.setEntity(entity);
                    return r;
                } else {
                    return null;
                }
            }

            public void handleResponse(final HttpResponse response, final HttpContext context) {
                Job testjob = (Job) context.removeAttribute("job");
                if (testjob == null) {
                    throw new IllegalStateException("TestJob is null");
                }

                int statusCode = response.getStatusLine().getStatusCode();
                String content = null;

                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    try {
                        // Simulate slow response handling in order to cause the
                        // internal content buffer to fill up, forcing the
                        // protocol handler to throttle input rate
                        ByteArrayOutputStream outstream = new ByteArrayOutputStream();
                        InputStream instream = entity.getContent();
                        byte[] tmp = new byte[2048];
                        int l;
                        while((l = instream.read(tmp)) != -1) {
                            Thread.sleep(1);
                            outstream.write(tmp, 0, l);
                        }
                        ContentType contentType = ContentType.getOrDefault(entity);
                        Charset charset = contentType.getCharset();
                        if (charset == null) {
                            charset = HTTP.DEF_CONTENT_CHARSET;
                        }
                        content = new String(outstream.toByteArray(), charset.name());
                    } catch (InterruptedException ex) {
                        content = "Interrupted: " + ex.getMessage();
                    } catch (IOException ex) {
                        content = "I/O exception: " + ex.getMessage();
                    }
                }
                testjob.setResult(statusCode, content);
            }

            public void finalizeContext(final HttpContext context) {
                Job testjob = (Job) context.removeAttribute("job");
                if (testjob != null) {
                    testjob.fail("Request failed");
                }
            }

        };
        int connNo = 3;
        int reqNo = 20;
        Job[] jobs = new Job[connNo * reqNo];
        for (int i = 0; i < jobs.length; i++) {
            jobs[i] = new Job(10000);
        }
        Queue<Job> queue = new ConcurrentLinkedQueue<Job>();
        for (int i = 0; i < jobs.length; i++) {
            queue.add(jobs[i]);
        }

        HttpProcessor serverHttpProc = new ImmutableHttpProcessor(new HttpResponseInterceptor[] {
                new ResponseDate(),
                new ResponseServer(),
                new ResponseContent(),
                new ResponseConnControl()
        });

        ThrottlingHttpServiceHandler serviceHandler = new ThrottlingHttpServiceHandler(
                serverHttpProc,
                new DefaultHttpResponseFactory(),
                new DefaultConnectionReuseStrategy(),
                this.execService,
                this.serverParams);

        serviceHandler.setHandlerResolver(
                new SimpleHttpRequestHandlerResolver(new RequestHandler()));
        serviceHandler.setEventListener(
                new SimpleEventListener());

        HttpProcessor clientHttpProc = new ImmutableHttpProcessor(new HttpRequestInterceptor[] {
                new RequestContent(),
                new RequestTargetHost(),
                new RequestConnControl(),
                new RequestUserAgent(),
                new RequestExpectContinue()});

        ThrottlingHttpClientHandler clientHandler = new ThrottlingHttpClientHandler(
                clientHttpProc,
                requestExecutionHandler,
                new DefaultConnectionReuseStrategy(),
                this.execService,
                this.clientParams);

        clientHandler.setEventListener(
                new SimpleEventListener());

        this.server.start(serviceHandler);
        this.client.start(clientHandler);

        ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();
        InetSocketAddress serverAddress = (InetSocketAddress) endpoint.getAddress();

        Assert.assertEquals("Test server status", IOReactorStatus.ACTIVE, this.server.getStatus());

        Queue<SessionRequest> connRequests = new LinkedList<SessionRequest>();
        for (int i = 0; i < connNo; i++) {
            SessionRequest sessionRequest = this.client.openConnection(
                    new InetSocketAddress("localhost", serverAddress.getPort()),
                    queue);
            connRequests.add(sessionRequest);
        }

        while (!connRequests.isEmpty()) {
            SessionRequest sessionRequest = connRequests.remove();
            sessionRequest.waitFor();
            if (sessionRequest.getException() != null) {
                throw sessionRequest.getException();
            }
            Assert.assertNotNull(sessionRequest.getSession());
        }

        Assert.assertEquals("Test client status", IOReactorStatus.ACTIVE, this.client.getStatus());

        for (int i = 0; i < jobs.length; i++) {
            Job testjob = jobs[i];
            testjob.waitFor();
            if (testjob.isSuccessful()) {
                Assert.assertEquals(HttpStatus.SC_OK, testjob.getStatusCode());
                Assert.assertEquals(testjob.getExpected(), testjob.getResult());
            } else {
                Assert.fail(testjob.getFailureMessage());
            }
        }
    }

}
