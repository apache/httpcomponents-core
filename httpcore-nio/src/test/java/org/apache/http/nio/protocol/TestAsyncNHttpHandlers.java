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

package org.apache.http.nio.protocol;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.http.HttpCoreNIOTestBase;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.mockup.SimpleEventListener;
import org.apache.http.mockup.SimpleNHttpRequestHandlerResolver;
import org.apache.http.nio.entity.ConsumingNHttpEntity;
import org.apache.http.nio.entity.NByteArrayEntity;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.nio.reactor.IOReactorStatus;
import org.apache.http.nio.reactor.ListenerEndpoint;
import org.apache.http.nio.reactor.SessionRequest;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpExpectationVerifier;
import org.apache.http.protocol.HttpProcessor;
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
import org.apache.http.util.EncodingUtils;

/**
 * HttpCore NIO integration tests for async handlers.
 */
public class TestAsyncNHttpHandlers extends HttpCoreNIOTestBase {

    // ------------------------------------------------------------ Constructor
    public TestAsyncNHttpHandlers(String testName) {
        super(testName);
    }

    // ------------------------------------------------------- TestCase Methods

    private void executeStandardTest(
            final NHttpRequestHandler requestHandler,
            final NHttpRequestExecutionHandler requestExecutionHandler) throws Exception {
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

        AsyncNHttpServiceHandler serviceHandler = new AsyncNHttpServiceHandler(
                serverHttpProc,
                new DefaultHttpResponseFactory(),
                new DefaultConnectionReuseStrategy(),
                this.server.getParams());

        serviceHandler.setHandlerResolver(
                new SimpleNHttpRequestHandlerResolver(requestHandler));
        serviceHandler.setEventListener(
                new SimpleEventListener());

        HttpProcessor clientHttpProc = new ImmutableHttpProcessor(new HttpRequestInterceptor[] {
                new RequestContent(),
                new RequestTargetHost(),
                new RequestConnControl(),
                new RequestUserAgent(),
                new RequestExpectContinue()});

        AsyncNHttpClientHandler clientHandler = new AsyncNHttpClientHandler(
                clientHttpProc,
                requestExecutionHandler,
                new DefaultConnectionReuseStrategy(),
                this.client.getParams());

        clientHandler.setEventListener(
                new SimpleEventListener());

        this.server.start(serviceHandler);
        this.client.start(clientHandler);

        ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();
        InetSocketAddress serverAddress = (InetSocketAddress) endpoint.getAddress();

        assertEquals("Test server status", IOReactorStatus.ACTIVE, this.server.getStatus());

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
            assertNotNull(sessionRequest.getSession());
        }

        assertEquals("Test client status", IOReactorStatus.ACTIVE, this.client.getStatus());

        for (int i = 0; i < jobs.length; i++) {
            Job testjob = jobs[i];
            testjob.waitFor();
            if (testjob.isSuccessful()) {
                assertEquals(HttpStatus.SC_OK, testjob.getStatusCode());
                assertEquals(testjob.getExpected(), testjob.getResult());
            } else {
                fail(testjob.getFailureMessage());
            }
        }
    }

    /**
     * This test case executes a series of simple (non-pipelined) GET requests
     * over multiple connections. This uses non-blocking output entities.
     */
    public void testHttpGets() throws Exception {
        NHttpRequestExecutionHandler requestExecutionHandler = new RequestExecutionHandler() {

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
     * It uses purely asynchronous handlers.
     */
    public void testHttpPostsWithContentLength() throws Exception {
        NHttpRequestExecutionHandler requestExecutionHandler = new RequestExecutionHandler() {

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
     * with chunk coded content content over multiple connections.  This tests
     * with nonblocking handlers & nonblocking entities.
     */
    public void testHttpPostsChunked() throws Exception {
        NHttpRequestExecutionHandler requestExecutionHandler = new RequestExecutionHandler() {

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
     * POST requests over multiple persistent connections. This tests with nonblocking
     * handlers & entities.
     */
    public void testHttpPostsHTTP10() throws Exception {
        NHttpRequestExecutionHandler requestExecutionHandler = new RequestExecutionHandler() {

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
     * over multiple connections using the 'expect: continue' handshake.  This test
     * uses nonblocking handlers & entities.
     */
    public void testHttpPostsWithExpectContinue() throws Exception {
        NHttpRequestExecutionHandler requestExecutionHandler = new RequestExecutionHandler() {

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
     * one of which does not meet the target server expectations.
     * This test uses nonblocking entities.
     */
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

        NHttpRequestExecutionHandler requestExecutionHandler = new RequestExecutionHandler() {

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

        AsyncNHttpServiceHandler serviceHandler = new AsyncNHttpServiceHandler(
                serverHttpProc,
                new DefaultHttpResponseFactory(),
                new DefaultConnectionReuseStrategy(),
                this.server.getParams());

        serviceHandler.setHandlerResolver(
                new SimpleNHttpRequestHandlerResolver(new RequestHandler()));
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

        AsyncNHttpClientHandler clientHandler = new AsyncNHttpClientHandler(
                clientHttpProc,
                requestExecutionHandler,
                new DefaultConnectionReuseStrategy(),
                this.client.getParams());

        clientHandler.setEventListener(new SimpleEventListener());

        this.server.start(serviceHandler);
        this.client.start(clientHandler);

        ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();
        InetSocketAddress serverAddress = (InetSocketAddress) endpoint.getAddress();

        assertEquals("Test server status", IOReactorStatus.ACTIVE, this.server.getStatus());

        SessionRequest sessionRequest = this.client.openConnection(
                new InetSocketAddress("localhost", serverAddress.getPort()),
                queue);

        sessionRequest.waitFor();
        if (sessionRequest.getException() != null) {
            throw sessionRequest.getException();
        }
        assertNotNull(sessionRequest.getSession());

        assertEquals("Test client status", IOReactorStatus.ACTIVE, this.client.getStatus());

        for (int i = 0; i < 2; i++) {
            Job testjob = jobs[i];
            testjob.waitFor();
            if (testjob.isSuccessful()) {
                assertEquals(testjob.getExpected(), testjob.getResult());
            } else {
                fail(testjob.getFailureMessage());
            }
        }
        Job failedExpectation = jobs[2];
        failedExpectation.waitFor();
        assertEquals(HttpStatus.SC_EXPECTATION_FAILED, failedExpectation.getStatusCode());
    }

    /**
     * This test case executes a series of simple (non-pipelined) HEAD requests
     * over multiple connections. This test uses nonblocking entities.
     */
    public void testHttpHeads() throws Exception {
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

        NHttpRequestExecutionHandler requestExecutionHandler = new RequestExecutionHandler() {

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

        AsyncNHttpServiceHandler serviceHandler = new AsyncNHttpServiceHandler(
                serverHttpProc,
                new DefaultHttpResponseFactory(),
                new DefaultConnectionReuseStrategy(),
                this.server.getParams());

        serviceHandler.setHandlerResolver(
                new SimpleNHttpRequestHandlerResolver(new RequestHandler()));
        serviceHandler.setEventListener(
                new SimpleEventListener());

        HttpProcessor clientHttpProc = new ImmutableHttpProcessor(new HttpRequestInterceptor[] {
                new RequestContent(),
                new RequestTargetHost(),
                new RequestConnControl(),
                new RequestUserAgent(),
                new RequestExpectContinue()});

        AsyncNHttpClientHandler clientHandler = new AsyncNHttpClientHandler(
                clientHttpProc,
                requestExecutionHandler,
                new DefaultConnectionReuseStrategy(),
                this.client.getParams());

        clientHandler.setEventListener(new SimpleEventListener());

        this.server.start(serviceHandler);
        this.client.start(clientHandler);

        ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();
        InetSocketAddress serverAddress = (InetSocketAddress) endpoint.getAddress();

        assertEquals("Test server status", IOReactorStatus.ACTIVE, this.server.getStatus());

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
            assertNotNull(sessionRequest.getSession());
        }

        assertEquals("Test client status", IOReactorStatus.ACTIVE, this.client.getStatus());

        for (int i = 0; i < jobs.length; i++) {
            Job testjob = jobs[i];
            testjob.waitFor();
            if (testjob.getFailureMessage() != null) {
                fail(testjob.getFailureMessage());
            }
            assertEquals(HttpStatus.SC_OK, testjob.getStatusCode());
            assertNull(testjob.getResult());
        }
    }

    /**
     * This test executes a series of delayed GETs, ensuring the
     * {@link NHttpResponseTrigger} works correctly.
     */
    public void testDelayedHttpGets() throws Exception {

        NHttpRequestHandler requestHandler = new NHttpRequestHandler() {

            public ConsumingNHttpEntity entityRequest(
                    final HttpEntityEnclosingRequest request,
                    final HttpContext context) {
                return null;
            }

            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final NHttpResponseTrigger trigger,
                    final HttpContext context) throws HttpException, IOException {
                String s = request.getRequestLine().getUri();
                int idx = s.indexOf('x');
                if (idx == -1) {
                    throw new HttpException("Unexpected request-URI format");
                }
                String pattern = s.substring(0, idx);
                int count = Integer.parseInt(s.substring(idx + 1, s.length()));

                StringBuilder buffer = new StringBuilder();
                for (int i = 0; i < count; i++) {
                    buffer.append(pattern);
                }
                final String content = buffer.toString();

                new Thread() {
                    @Override
                    public void run() {
                        // Wait a bit, to make sure this is delayed.
                        try { Thread.sleep(10); } catch(InterruptedException ie) {}
                        // Set the entity after delaying...
                        try {
                            NStringEntity entity = new NStringEntity(content, "US-ASCII");
                            response.setEntity(entity);
                        }  catch (UnsupportedEncodingException ex) {
                        }
                        trigger.submitResponse(response);
                    }
                }.start();
            }

        };

        NHttpRequestExecutionHandler requestExecutionHandler = new RequestExecutionHandler() {

            @Override
            protected HttpRequest generateRequest(Job testjob) {
                String s = testjob.getPattern() + "x" + testjob.getCount();
                return new BasicHttpRequest("GET", s);
            }

        };
        executeStandardTest(requestHandler, requestExecutionHandler);
    }

    /**
     * This test ensures that HttpExceptions work correctly when immediate.
     */
    public void testHttpException() throws Exception {

        NHttpRequestHandler requestHandler = new SimpleNHttpRequestHandler() {

            public ConsumingNHttpEntity entityRequest(
                    final HttpEntityEnclosingRequest request,
                    final HttpContext context) {
                return null;
            }

            @Override
            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                throw new HttpException(request.getRequestLine().getUri());
            }

        };

        NHttpRequestExecutionHandler requestExecutionHandler = new RequestExecutionHandler() {

            @Override
            protected HttpRequest generateRequest(Job testjob) {
                String s = testjob.getPattern() + "x" + testjob.getCount();
                return new BasicHttpRequest("GET", s);
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

        AsyncNHttpServiceHandler serviceHandler = new AsyncNHttpServiceHandler(
                serverHttpProc,
                new DefaultHttpResponseFactory(),
                new DefaultConnectionReuseStrategy(),
                this.server.getParams());

        serviceHandler.setHandlerResolver(
                new SimpleNHttpRequestHandlerResolver(requestHandler));
        serviceHandler.setEventListener(
                new SimpleEventListener());

        HttpProcessor clientHttpProc = new ImmutableHttpProcessor(new HttpRequestInterceptor[] {
                new RequestContent(),
                new RequestTargetHost(),
                new RequestConnControl(),
                new RequestUserAgent(),
                new RequestExpectContinue()});

        AsyncNHttpClientHandler clientHandler = new AsyncNHttpClientHandler(
                clientHttpProc,
                requestExecutionHandler,
                new DefaultConnectionReuseStrategy(),
                this.client.getParams());

        clientHandler.setEventListener(new SimpleEventListener());

        this.server.start(serviceHandler);
        this.client.start(clientHandler);

        ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();
        InetSocketAddress serverAddress = (InetSocketAddress) endpoint.getAddress();

        assertEquals("Test server status", IOReactorStatus.ACTIVE, this.server.getStatus());

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
            assertNotNull(sessionRequest.getSession());
        }

        assertEquals("Test client status", IOReactorStatus.ACTIVE, this.client.getStatus());

        for (int i = 0; i < jobs.length; i++) {
            Job testjob = jobs[i];
            testjob.waitFor();
            if (testjob.isSuccessful()) {
                assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, testjob.getStatusCode());
                assertEquals(testjob.getPattern() + "x" + testjob.getCount(), testjob.getResult());
            } else {
                fail(testjob.getFailureMessage());
            }
        }
    }

    /**
     * This test ensures that HttpExceptions work correctly when they are delayed by a trigger.
     */
    public void testDelayedHttpException() throws Exception {

        NHttpRequestHandler requestHandler = new NHttpRequestHandler() {

            public ConsumingNHttpEntity entityRequest(
                    final HttpEntityEnclosingRequest request,
                    final HttpContext context) {
                return null;
            }
            public void handle(final HttpRequest request, HttpResponse response,
                    final NHttpResponseTrigger trigger, HttpContext context)
                    throws HttpException, IOException {
                new Thread() {
                    @Override
                    public void run() {
                        try { Thread.sleep(10); } catch(InterruptedException ie) {}
                        trigger.handleException(
                                new HttpException(request.getRequestLine().getUri()));
                    }
                }.start();
            }

        };

        NHttpRequestExecutionHandler requestExecutionHandler = new RequestExecutionHandler() {

            @Override
            protected HttpRequest generateRequest(Job testjob) {
                String s = testjob.getPattern() + "x" + testjob.getCount();
                return new BasicHttpRequest("GET", s);
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

        AsyncNHttpServiceHandler serviceHandler = new AsyncNHttpServiceHandler(
                serverHttpProc,
                new DefaultHttpResponseFactory(),
                new DefaultConnectionReuseStrategy(),
                this.server.getParams());

        serviceHandler.setHandlerResolver(
                new SimpleNHttpRequestHandlerResolver(requestHandler));
        serviceHandler.setEventListener(
                new SimpleEventListener());

        HttpProcessor clientHttpProc = new ImmutableHttpProcessor(new HttpRequestInterceptor[] {
                new RequestContent(),
                new RequestTargetHost(),
                new RequestConnControl(),
                new RequestUserAgent(),
                new RequestExpectContinue()});

        AsyncNHttpClientHandler clientHandler = new AsyncNHttpClientHandler(
                clientHttpProc,
                requestExecutionHandler,
                new DefaultConnectionReuseStrategy(),
                this.client.getParams());

        clientHandler.setEventListener(new SimpleEventListener());

        this.server.start(serviceHandler);
        this.client.start(clientHandler);

        ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();
        InetSocketAddress serverAddress = (InetSocketAddress) endpoint.getAddress();

        assertEquals("Test server status", IOReactorStatus.ACTIVE, this.server.getStatus());

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
            assertNotNull(sessionRequest.getSession());
        }

        assertEquals("Test client status", IOReactorStatus.ACTIVE, this.client.getStatus());

        for (int i = 0; i < jobs.length; i++) {
            Job testjob = jobs[i];
            testjob.waitFor();
            if (testjob.isSuccessful()) {
                assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, testjob.getStatusCode());
                assertEquals(testjob.getPattern() + "x" + testjob.getCount(), testjob.getResult());
            } else {
                fail(testjob.getFailureMessage());
            }
        }
    }

    /**
     * This test makes sure that if no service handler is installed, things still work.
     */
    public void testNoServiceHandler() throws Exception {
        NHttpRequestExecutionHandler requestExecutionHandler = new RequestExecutionHandler() {

            @Override
            protected HttpRequest generateRequest(Job testjob) {
                String s = testjob.getPattern() + "x" + testjob.getCount();
                return new BasicHttpRequest("GET", s);
            }

        };

        int connNo = 5;
        Job[] jobs = new Job[connNo];
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

        AsyncNHttpServiceHandler serviceHandler = new AsyncNHttpServiceHandler(
                serverHttpProc,
                new DefaultHttpResponseFactory(),
                new DefaultConnectionReuseStrategy(),
                this.server.getParams());

        serviceHandler.setEventListener(
                new SimpleEventListener());

        HttpProcessor clientHttpProc = new ImmutableHttpProcessor(new HttpRequestInterceptor[] {
                new RequestContent(),
                new RequestTargetHost(),
                new RequestConnControl(),
                new RequestUserAgent(),
                new RequestExpectContinue()});

        AsyncNHttpClientHandler clientHandler = new AsyncNHttpClientHandler(
                clientHttpProc,
                requestExecutionHandler,
                new DefaultConnectionReuseStrategy(),
                this.client.getParams());

        clientHandler.setEventListener(new SimpleEventListener());

        this.server.start(serviceHandler);
        this.client.start(clientHandler);

        ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();
        InetSocketAddress serverAddress = (InetSocketAddress) endpoint.getAddress();

        assertEquals("Test server status", IOReactorStatus.ACTIVE, this.server.getStatus());

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
            assertNotNull(sessionRequest.getSession());
        }

        assertEquals("Test client status", IOReactorStatus.ACTIVE, this.client.getStatus());

        for (int i = 0; i < jobs.length; i++) {
            Job testjob = jobs[i];

            testjob.waitFor();
            if (testjob.isSuccessful()) {
                assertEquals(HttpStatus.SC_NOT_IMPLEMENTED, testjob.getStatusCode());
                assertEquals("", testjob.getResult());
            } else {
                fail(testjob.getFailureMessage());
            }
        }
    }

    /**
     * This test case executes a series of simple (non-pipelined) POST requests
     * with no entities on the client side, to ensure they are sent properly,
     * and the server can read them.
     */
    public void testHttpPostWithNoEntities() throws Exception {
        NHttpRequestExecutionHandler requestExecutionHandler = new RequestExecutionHandler() {

            @Override
            protected HttpRequest generateRequest(Job testjob) {
                String s = testjob.getPattern() + "x" + testjob.getCount();
                HttpEntityEnclosingRequest r = new BasicHttpEntityEnclosingRequest("POST", s);
                r.setEntity(null);
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

        AsyncNHttpServiceHandler serviceHandler = new AsyncNHttpServiceHandler(
                serverHttpProc,
                new DefaultHttpResponseFactory(),
                new DefaultConnectionReuseStrategy(),
                this.server.getParams());

        serviceHandler.setHandlerResolver(
                new SimpleNHttpRequestHandlerResolver(new RequestHandler()));
        serviceHandler.setEventListener(
                new SimpleEventListener());

        HttpProcessor clientHttpProc = new ImmutableHttpProcessor(new HttpRequestInterceptor[] {
                new RequestContent(),
                new RequestTargetHost(),
                new RequestConnControl(),
                new RequestUserAgent(),
                new RequestExpectContinue()});

        AsyncNHttpClientHandler clientHandler = new AsyncNHttpClientHandler(
                clientHttpProc,
                requestExecutionHandler,
                new DefaultConnectionReuseStrategy(),
                this.client.getParams());

        clientHandler.setEventListener(new SimpleEventListener());

        this.server.start(serviceHandler);
        this.client.start(clientHandler);

        ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();
        InetSocketAddress serverAddress = (InetSocketAddress) endpoint.getAddress();

        assertEquals("Test server status", IOReactorStatus.ACTIVE, this.server.getStatus());

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
            assertNotNull(sessionRequest.getSession());
        }

        assertEquals("Test client status", IOReactorStatus.ACTIVE, this.client.getStatus());

        for (int i = 0; i < jobs.length; i++) {
            Job testjob = jobs[i];
            testjob.waitFor();
            if (testjob.isSuccessful()) {
                assertEquals(HttpStatus.SC_OK, testjob.getStatusCode());
                assertEquals("", testjob.getResult());
            } else {
                fail(testjob.getFailureMessage());
            }
        }
    }

    public void testNoRequestHandler() throws Exception {
        NHttpRequestExecutionHandler requestExecutionHandler = new RequestExecutionHandler() {

            @Override
            protected HttpRequest generateRequest(Job testjob) {
                String s = testjob.getPattern() + "x" + testjob.getCount();
                HttpEntityEnclosingRequest r = new BasicHttpEntityEnclosingRequest("POST", s);
                NByteArrayEntity entity = new NByteArrayEntity(new byte[] {1,2,3,4,5} );
                entity.setChunked(false);
                r.setEntity(entity);
                return r;
            }

        };

        int connNo = 3;
        Job[] jobs = new Job[connNo];
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

        AsyncNHttpServiceHandler serviceHandler = new AsyncNHttpServiceHandler(
                serverHttpProc,
                new DefaultHttpResponseFactory(),
                new DefaultConnectionReuseStrategy(),
                this.server.getParams());

        serviceHandler.setHandlerResolver(
                new SimpleNHttpRequestHandlerResolver(null));
        serviceHandler.setEventListener(
                new SimpleEventListener());

        HttpProcessor clientHttpProc = new ImmutableHttpProcessor(new HttpRequestInterceptor[] {
                new RequestContent(),
                new RequestTargetHost(),
                new RequestConnControl(),
                new RequestUserAgent(),
                new RequestExpectContinue()});

        AsyncNHttpClientHandler clientHandler = new AsyncNHttpClientHandler(
                clientHttpProc,
                requestExecutionHandler,
                new DefaultConnectionReuseStrategy(),
                this.client.getParams());

        clientHandler.setEventListener(new SimpleEventListener());

        this.server.start(serviceHandler);
        this.client.start(clientHandler);

        ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();
        InetSocketAddress serverAddress = (InetSocketAddress) endpoint.getAddress();

        assertEquals("Test server status", IOReactorStatus.ACTIVE, this.server.getStatus());

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
            assertNotNull(sessionRequest.getSession());
        }

        assertEquals("Test client status", IOReactorStatus.ACTIVE, this.client.getStatus());

        for (int i = 0; i < jobs.length; i++) {
            Job testjob = jobs[i];
            testjob.waitFor();
            if (testjob.isSuccessful()) {
                assertEquals(HttpStatus.SC_NOT_IMPLEMENTED, testjob.getStatusCode());
            } else {
                fail(testjob.getFailureMessage());
            }
        }
    }
}
