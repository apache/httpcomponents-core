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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.mockup.SimpleEventListener;
import org.apache.http.mockup.SimpleHttpRequestHandlerResolver;
import org.apache.http.nio.entity.NByteArrayEntity;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.nio.reactor.IOReactorStatus;
import org.apache.http.nio.reactor.ListenerEndpoint;
import org.apache.http.nio.reactor.SessionRequest;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.CoreProtocolPNames;
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
import org.apache.http.util.EncodingUtils;
import org.apache.http.util.EntityUtils;

/**
 * HttpCore NIO integration tests using throttling versions of the
 * protocol handlers.
 */
public class TestThrottlingNHttpHandlers extends HttpCoreNIOTestBase {

    // ------------------------------------------------------------ Constructor
    public TestThrottlingNHttpHandlers(String testName) {
        super(testName);
    }

    // ------------------------------------------------------- TestCase Methods

    private ExecutorService execService;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        this.execService = Executors.newCachedThreadPool();
    }

    @Override
    protected void tearDown() {
        super.tearDown();
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
                this.server.getParams());

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
     * over multiple connections.
     */
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
     * This test ensures that an Executor instance
     * (under the control of a ThrottlingHttpServiceHandler)
     * terminates when a connection timeout occurs.
     */
    public void testExecutorTermination() throws Exception {
        final int SHORT_TIMEOUT = 100;
        final int DEFAULT_SERVER_SO_TIMEOUT = 60000;
        this.server.getParams().setIntParameter(
                CoreConnectionPNames.SO_TIMEOUT, SHORT_TIMEOUT);

        // main expectation: the executor spawned by the service must finish.
        final String COMMAND_FINISHED = "CommandFinished";
        final Map<String, Boolean> serverExpectations = Collections.synchronizedMap(
                new HashMap<String, Boolean>());
        serverExpectations.put(COMMAND_FINISHED, Boolean.FALSE);

        // secondary expectation: not strictly necessary, the test will wait for the
        // client to finalize the request
        final String CLIENT_FINALIZED = "ClientFinalized";
        final Map<String, Boolean> clientExpectations = Collections.synchronizedMap(
                new HashMap<String, Boolean>());
        clientExpectations.put(CLIENT_FINALIZED, Boolean.FALSE);

        // runs the command on a separate thread and updates the server expectation
        Executor executor = new Executor() {

            public void execute(final Runnable command) {
                new Thread() {
                    @Override
                    public void run() {
                        command.run();
                        synchronized (serverExpectations) {
                            serverExpectations.put(COMMAND_FINISHED, Boolean.TRUE);
                            serverExpectations.notify();
                        }
                    }
                }.start();
            }

        };

        HttpRequestHandler requestHandler = new HttpRequestHandler() {
            public void handle(
                    HttpRequest request,
                    HttpResponse response,
                    HttpContext context) {
                try {
                    ((HttpEntityEnclosingRequest) request).getEntity().getContent().read();
                    response.setStatusCode(HttpStatus.SC_OK);
                } catch (Exception e){
                }
            }
        };

        // convoluted client-side entity content. The byte expected by the HttpRequest will not
        // be written.
        final PipedOutputStream pipe = new PipedOutputStream();
        final PipedInputStream producer = new PipedInputStream(pipe);
        pipe.close();

        // A POST request enclosing an entity with (supposedly) a content length of 1 byte.
        // the connection will be closed at the end of the request.
        HttpRequestExecutionHandler requestExecutionHandler = new HttpRequestExecutionHandler() {
            public void initalizeContext(final HttpContext context, final Object attachment) {
            }

            public void finalizeContext(HttpContext context) {
                synchronized (clientExpectations) {
                    clientExpectations.put(CLIENT_FINALIZED, Boolean.TRUE);
                    clientExpectations.notifyAll();
                }
            }

            public HttpRequest submitRequest( HttpContext context ) {
                HttpEntityEnclosingRequest post = new BasicHttpEntityEnclosingRequest("POST", "/");
                post.setHeader( "Connection", "Close" );
                post.setEntity(new InputStreamEntity(producer, 1));
                return post;
            }

            public void handleResponse(final HttpResponse response, final HttpContext context) {
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
                executor,
                this.server.getParams());

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
                this.client.getParams());

        clientHandler.setEventListener(
                new SimpleEventListener());

        this.server.start(serviceHandler);
        this.client.start(clientHandler);

        ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();
        InetSocketAddress serverAddress = (InetSocketAddress) endpoint.getAddress();

        assertEquals("Test server status", IOReactorStatus.ACTIVE, this.server.getStatus());

        SessionRequest sessionRequest = this.client.openConnection(
                new InetSocketAddress("localhost", serverAddress.getPort()),
                null);

        sessionRequest.waitFor();
        if (sessionRequest.getException() != null) {
            throw sessionRequest.getException();
        }
        assertNotNull(sessionRequest.getSession());

        assertEquals("Test client status", IOReactorStatus.ACTIVE, this.client.getStatus());

        // wait for the client to invoke finalizeContext().
        synchronized (clientExpectations) {
            if (!clientExpectations.get(CLIENT_FINALIZED).booleanValue()) {
                clientExpectations.wait(DEFAULT_SERVER_SO_TIMEOUT);
                assertTrue(clientExpectations.get(CLIENT_FINALIZED).booleanValue());
            }
        }

        // wait for server to finish the command within a reasonable amount of time.
        // the time constraint is not necessary, it only prevents the test from hanging.
        synchronized (serverExpectations) {
            if (!serverExpectations.get(COMMAND_FINISHED).booleanValue()) {
                serverExpectations.wait(SHORT_TIMEOUT);
                assertTrue(serverExpectations.get(COMMAND_FINISHED).booleanValue());
            }
        }

        this.execService.shutdown();
        this.execService.awaitTermination(10, TimeUnit.SECONDS);
    }

    /**
     * This test case executes a series of simple (non-pipelined) HTTP/1.0
     * POST requests over multiple persistent connections.
     */
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
                this.server.getParams());

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
                this.client.getParams());

        clientHandler.setEventListener(
                new SimpleEventListener());

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
     * over multiple connections.
     */
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
                this.server.getParams());

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
     * This test case tests if the protocol handler can correctly deal
     * with requests with partially consumed content.
     */
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
                this.server.getParams());

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
                assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, testjob.getStatusCode());
                assertEquals("Ooopsie", testjob.getResult());
            } else {
                fail(testjob.getFailureMessage());
            }
        }
    }

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
                        content = new String(outstream.toByteArray(),
                                EntityUtils.getContentCharSet(entity));
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
                this.server.getParams());

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

}
