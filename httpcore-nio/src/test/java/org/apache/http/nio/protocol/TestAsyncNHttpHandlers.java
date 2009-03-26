/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
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
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.apache.http.HttpCoreNIOTestBase;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
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
import org.apache.http.nio.reactor.ListenerEndpoint;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpExpectationVerifier;
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
 *
 *
 * @version $Id$
 */
public class TestAsyncNHttpHandlers extends HttpCoreNIOTestBase {

    // ------------------------------------------------------------ Constructor
    public TestAsyncNHttpHandlers(String testName) {
        super(testName);
    }

    // ------------------------------------------------------------------- Main
    public static void main(String args[]) {
        String[] testCaseName = { TestAsyncNHttpHandlers.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    // ------------------------------------------------------- TestCase Methods

    public static Test suite() {
        return new TestSuite(TestAsyncNHttpHandlers.class);
    }

    private void executeStandardTest(
            final NHttpRequestHandler requestHandler,
            final NHttpRequestExecutionHandler requestExecutionHandler) throws Exception {
        int connNo = 3;
        int reqNo = 20;
        TestJob[] jobs = new TestJob[connNo * reqNo];
        for (int i = 0; i < jobs.length; i++) {
            jobs[i] = new TestJob(); 
        }
        Queue<TestJob> queue = new ConcurrentLinkedQueue<TestJob>();
        for (int i = 0; i < jobs.length; i++) {
            queue.add(jobs[i]); 
        }

        BasicHttpProcessor serverHttpProc = new BasicHttpProcessor();
        serverHttpProc.addInterceptor(new ResponseDate());
        serverHttpProc.addInterceptor(new ResponseServer());
        serverHttpProc.addInterceptor(new ResponseContent());
        serverHttpProc.addInterceptor(new ResponseConnControl());

        AsyncNHttpServiceHandler serviceHandler = new AsyncNHttpServiceHandler(
                serverHttpProc,
                new DefaultHttpResponseFactory(),
                new DefaultConnectionReuseStrategy(),
                this.server.getParams());

        serviceHandler.setHandlerResolver(
                new SimpleNHttpRequestHandlerResolver(requestHandler));
        serviceHandler.setEventListener(
                new SimpleEventListener());

        BasicHttpProcessor clientHttpProc = new BasicHttpProcessor();
        clientHttpProc.addInterceptor(new RequestContent());
        clientHttpProc.addInterceptor(new RequestTargetHost());
        clientHttpProc.addInterceptor(new RequestConnControl());
        clientHttpProc.addInterceptor(new RequestUserAgent());
        clientHttpProc.addInterceptor(new RequestExpectContinue());

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

        for (int i = 0; i < connNo; i++) {
            this.client.openConnection(
                    new InetSocketAddress("localhost", serverAddress.getPort()),
                    queue);
        }

        for (int i = 0; i < jobs.length; i++) {
            TestJob testjob = jobs[i];
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
        NHttpRequestExecutionHandler requestExecutionHandler = new TestRequestExecutionHandler() {

            @Override
            protected HttpRequest generateRequest(TestJob testjob) {
                String s = testjob.getPattern() + "x" + testjob.getCount(); 
                return new BasicHttpRequest("GET", s);
            }
            
        };
        executeStandardTest(new TestRequestHandler(), requestExecutionHandler);
    }

    /**
     * This test case executes a series of simple (non-pipelined) POST requests
     * with content length delimited content over multiple connections.
     * It uses purely asynchronous handlers.
     */
    public void testHttpPostsWithContentLength() throws Exception {
        NHttpRequestExecutionHandler requestExecutionHandler = new TestRequestExecutionHandler() {

            @Override
            protected HttpRequest generateRequest(TestJob testjob) {
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
        executeStandardTest(new TestRequestHandler(), requestExecutionHandler);
    }

    /**
     * This test case executes a series of simple (non-pipelined) POST requests
     * with chunk coded content content over multiple connections.  This tests
     * with nonblocking handlers & nonblocking entities.
     */
    public void testHttpPostsChunked() throws Exception {
        NHttpRequestExecutionHandler requestExecutionHandler = new TestRequestExecutionHandler() {

            @Override
            protected HttpRequest generateRequest(TestJob testjob) {
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
        executeStandardTest(new TestRequestHandler(), requestExecutionHandler);
    }

    /**
     * This test case executes a series of simple (non-pipelined) HTTP/1.0
     * POST requests over multiple persistent connections. This tests with nonblocking
     * handlers & entities.
     */
    public void testHttpPostsHTTP10() throws Exception {
        NHttpRequestExecutionHandler requestExecutionHandler = new TestRequestExecutionHandler() {

            @Override
            protected HttpRequest generateRequest(TestJob testjob) {
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
        executeStandardTest(new TestRequestHandler(), requestExecutionHandler);
    }

    /**
     * This test case executes a series of simple (non-pipelined) POST requests
     * over multiple connections using the 'expect: continue' handshake.  This test
     * uses nonblocking handlers & entities.
     */
    public void testHttpPostsWithExpectContinue() throws Exception {
        NHttpRequestExecutionHandler requestExecutionHandler = new TestRequestExecutionHandler() {

            @Override
            protected HttpRequest generateRequest(TestJob testjob) {
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
        executeStandardTest(new TestRequestHandler(), requestExecutionHandler);
    }

    /**
     * This test case executes a series of simple (non-pipelined) POST requests
     * one of which does not meet the target server expectations.
     * This test uses nonblocking entities.
     */
    public void testHttpPostsWithExpectationVerification() throws Exception {
        TestJob[] jobs = new TestJob[3];
        jobs[0] = new TestJob("AAAAA", 10); 
        jobs[1] = new TestJob("AAAAA", 10); 
        jobs[2] = new TestJob("BBBBB", 20); 
        Queue<TestJob> queue = new ConcurrentLinkedQueue<TestJob>();
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

        NHttpRequestExecutionHandler requestExecutionHandler = new TestRequestExecutionHandler() {

            @Override
            protected HttpRequest generateRequest(TestJob testjob) {
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
        
        BasicHttpProcessor serverHttpProc = new BasicHttpProcessor();
        serverHttpProc.addInterceptor(new ResponseDate());
        serverHttpProc.addInterceptor(new ResponseServer());
        serverHttpProc.addInterceptor(new ResponseContent());
        serverHttpProc.addInterceptor(new ResponseConnControl());

        AsyncNHttpServiceHandler serviceHandler = new AsyncNHttpServiceHandler(
                serverHttpProc,
                new DefaultHttpResponseFactory(),
                new DefaultConnectionReuseStrategy(),
                this.server.getParams());

        serviceHandler.setHandlerResolver(
                new SimpleNHttpRequestHandlerResolver(new TestRequestHandler()));
        serviceHandler.setExpectationVerifier(
                expectationVerifier);
        serviceHandler.setEventListener(
                new SimpleEventListener());

        BasicHttpProcessor clientHttpProc = new BasicHttpProcessor();
        clientHttpProc.addInterceptor(new RequestContent());
        clientHttpProc.addInterceptor(new RequestTargetHost());
        clientHttpProc.addInterceptor(new RequestConnControl());
        clientHttpProc.addInterceptor(new RequestUserAgent());
        clientHttpProc.addInterceptor(new RequestExpectContinue());

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

        this.client.openConnection(
                new InetSocketAddress("localhost", serverAddress.getPort()),
                queue);

        for (int i = 0; i < 2; i++) {
            TestJob testjob = jobs[i];
            testjob.waitFor();
            if (testjob.isSuccessful()) {
                assertEquals(testjob.getExpected(), testjob.getResult());
            } else {
                fail(testjob.getFailureMessage());
            }
        }
        TestJob failedExpectation = jobs[2];
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
        TestJob[] jobs = new TestJob[connNo * reqNo];
        for (int i = 0; i < jobs.length; i++) {
            jobs[i] = new TestJob(); 
        }
        Queue<TestJob> queue = new ConcurrentLinkedQueue<TestJob>();
        for (int i = 0; i < jobs.length; i++) {
            queue.add(jobs[i]); 
        }

        NHttpRequestExecutionHandler requestExecutionHandler = new TestRequestExecutionHandler() {

            @Override
            protected HttpRequest generateRequest(TestJob testjob) {
                String s = testjob.getPattern() + "x" + testjob.getCount(); 
                return new BasicHttpRequest("HEAD", s);
            }
            
        };
        
        BasicHttpProcessor serverHttpProc = new BasicHttpProcessor();
        serverHttpProc.addInterceptor(new ResponseDate());
        serverHttpProc.addInterceptor(new ResponseServer());
        serverHttpProc.addInterceptor(new ResponseContent());
        serverHttpProc.addInterceptor(new ResponseConnControl());

        AsyncNHttpServiceHandler serviceHandler = new AsyncNHttpServiceHandler(
                serverHttpProc,
                new DefaultHttpResponseFactory(),
                new DefaultConnectionReuseStrategy(),
                this.server.getParams());

        serviceHandler.setHandlerResolver(
                new SimpleNHttpRequestHandlerResolver(new TestRequestHandler()));
        serviceHandler.setEventListener(
                new SimpleEventListener());

        BasicHttpProcessor clientHttpProc = new BasicHttpProcessor();
        clientHttpProc.addInterceptor(new RequestContent());
        clientHttpProc.addInterceptor(new RequestTargetHost());
        clientHttpProc.addInterceptor(new RequestConnControl());
        clientHttpProc.addInterceptor(new RequestUserAgent());
        clientHttpProc.addInterceptor(new RequestExpectContinue());

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

        for (int i = 0; i < connNo; i++) {
            this.client.openConnection(
                    new InetSocketAddress("localhost", serverAddress.getPort()),
                    queue);
        }

        for (int i = 0; i < jobs.length; i++) {
            TestJob testjob = jobs[i];
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

        NHttpRequestExecutionHandler requestExecutionHandler = new TestRequestExecutionHandler() {

            @Override
            protected HttpRequest generateRequest(TestJob testjob) {
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

        NHttpRequestExecutionHandler requestExecutionHandler = new TestRequestExecutionHandler() {

            @Override
            protected HttpRequest generateRequest(TestJob testjob) {
                String s = testjob.getPattern() + "x" + testjob.getCount(); 
                return new BasicHttpRequest("GET", s);
            }
            
        };

        int connNo = 3;
        int reqNo = 20;
        TestJob[] jobs = new TestJob[connNo * reqNo];
        for (int i = 0; i < jobs.length; i++) {
            jobs[i] = new TestJob(); 
        }
        Queue<TestJob> queue = new ConcurrentLinkedQueue<TestJob>();
        for (int i = 0; i < jobs.length; i++) {
            queue.add(jobs[i]); 
        }

        BasicHttpProcessor serverHttpProc = new BasicHttpProcessor();
        serverHttpProc.addInterceptor(new ResponseDate());
        serverHttpProc.addInterceptor(new ResponseServer());
        serverHttpProc.addInterceptor(new ResponseContent());
        serverHttpProc.addInterceptor(new ResponseConnControl());

        AsyncNHttpServiceHandler serviceHandler = new AsyncNHttpServiceHandler(
                serverHttpProc,
                new DefaultHttpResponseFactory(),
                new DefaultConnectionReuseStrategy(),
                this.server.getParams());

        serviceHandler.setHandlerResolver(
                new SimpleNHttpRequestHandlerResolver(requestHandler));
        serviceHandler.setEventListener(
                new SimpleEventListener());

        BasicHttpProcessor clientHttpProc = new BasicHttpProcessor();
        clientHttpProc.addInterceptor(new RequestContent());
        clientHttpProc.addInterceptor(new RequestTargetHost());
        clientHttpProc.addInterceptor(new RequestConnControl());
        clientHttpProc.addInterceptor(new RequestUserAgent());
        clientHttpProc.addInterceptor(new RequestExpectContinue());

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

        for (int i = 0; i < connNo; i++) {
            this.client.openConnection(
                    new InetSocketAddress("localhost", serverAddress.getPort()),
                    queue);
        }

        for (int i = 0; i < jobs.length; i++) {
            TestJob testjob = jobs[i];
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
        
        NHttpRequestExecutionHandler requestExecutionHandler = new TestRequestExecutionHandler() {

            @Override
            protected HttpRequest generateRequest(TestJob testjob) {
                String s = testjob.getPattern() + "x" + testjob.getCount(); 
                return new BasicHttpRequest("GET", s);
            }
            
        };

        int connNo = 3;
        int reqNo = 20;
        TestJob[] jobs = new TestJob[connNo * reqNo];
        for (int i = 0; i < jobs.length; i++) {
            jobs[i] = new TestJob(); 
        }
        Queue<TestJob> queue = new ConcurrentLinkedQueue<TestJob>();
        for (int i = 0; i < jobs.length; i++) {
            queue.add(jobs[i]); 
        }

        BasicHttpProcessor serverHttpProc = new BasicHttpProcessor();
        serverHttpProc.addInterceptor(new ResponseDate());
        serverHttpProc.addInterceptor(new ResponseServer());
        serverHttpProc.addInterceptor(new ResponseContent());
        serverHttpProc.addInterceptor(new ResponseConnControl());

        AsyncNHttpServiceHandler serviceHandler = new AsyncNHttpServiceHandler(
                serverHttpProc,
                new DefaultHttpResponseFactory(),
                new DefaultConnectionReuseStrategy(),
                this.server.getParams());

        serviceHandler.setHandlerResolver(
                new SimpleNHttpRequestHandlerResolver(requestHandler));
        serviceHandler.setEventListener(
                new SimpleEventListener());

        BasicHttpProcessor clientHttpProc = new BasicHttpProcessor();
        clientHttpProc.addInterceptor(new RequestContent());
        clientHttpProc.addInterceptor(new RequestTargetHost());
        clientHttpProc.addInterceptor(new RequestConnControl());
        clientHttpProc.addInterceptor(new RequestUserAgent());
        clientHttpProc.addInterceptor(new RequestExpectContinue());

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

        for (int i = 0; i < connNo; i++) {
            this.client.openConnection(
                    new InetSocketAddress("localhost", serverAddress.getPort()),
                    queue);
        }

        for (int i = 0; i < jobs.length; i++) {
            TestJob testjob = jobs[i];
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
        NHttpRequestExecutionHandler requestExecutionHandler = new TestRequestExecutionHandler() {

            @Override
            protected HttpRequest generateRequest(TestJob testjob) {
                String s = testjob.getPattern() + "x" + testjob.getCount();
                return new BasicHttpRequest("GET", s);
            }
            
        };

        int connNo = 5;
        TestJob[] jobs = new TestJob[connNo];
        for (int i = 0; i < jobs.length; i++) {
            jobs[i] = new TestJob(); 
        }
        Queue<TestJob> queue = new ConcurrentLinkedQueue<TestJob>();
        for (int i = 0; i < jobs.length; i++) {
            queue.add(jobs[i]); 
        }

        BasicHttpProcessor serverHttpProc = new BasicHttpProcessor();
        serverHttpProc.addInterceptor(new ResponseDate());
        serverHttpProc.addInterceptor(new ResponseServer());
        serverHttpProc.addInterceptor(new ResponseContent());
        serverHttpProc.addInterceptor(new ResponseConnControl());

        AsyncNHttpServiceHandler serviceHandler = new AsyncNHttpServiceHandler(
                serverHttpProc,
                new DefaultHttpResponseFactory(),
                new DefaultConnectionReuseStrategy(),
                this.server.getParams());

        serviceHandler.setEventListener(
                new SimpleEventListener());

        BasicHttpProcessor clientHttpProc = new BasicHttpProcessor();
        clientHttpProc.addInterceptor(new RequestContent());
        clientHttpProc.addInterceptor(new RequestTargetHost());
        clientHttpProc.addInterceptor(new RequestConnControl());
        clientHttpProc.addInterceptor(new RequestUserAgent());
        clientHttpProc.addInterceptor(new RequestExpectContinue());

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

        for (int i = 0; i < connNo; i++) {
            this.client.openConnection(
                    new InetSocketAddress("localhost", serverAddress.getPort()),
                    queue);
        }

        for (int i = 0; i < jobs.length; i++) {
            TestJob testjob = jobs[i];

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
        NHttpRequestExecutionHandler requestExecutionHandler = new TestRequestExecutionHandler() {

            @Override
            protected HttpRequest generateRequest(TestJob testjob) {
                String s = testjob.getPattern() + "x" + testjob.getCount(); 
                HttpEntityEnclosingRequest r = new BasicHttpEntityEnclosingRequest("POST", s);
                r.setEntity(null);
                return r;
            }
            
        };
        
        int connNo = 3;
        int reqNo = 20;
        TestJob[] jobs = new TestJob[connNo * reqNo];
        for (int i = 0; i < jobs.length; i++) {
            jobs[i] = new TestJob(); 
        }
        Queue<TestJob> queue = new ConcurrentLinkedQueue<TestJob>();
        for (int i = 0; i < jobs.length; i++) {
            queue.add(jobs[i]); 
        }

        BasicHttpProcessor serverHttpProc = new BasicHttpProcessor();
        serverHttpProc.addInterceptor(new ResponseDate());
        serverHttpProc.addInterceptor(new ResponseServer());
        serverHttpProc.addInterceptor(new ResponseContent());
        serverHttpProc.addInterceptor(new ResponseConnControl());

        AsyncNHttpServiceHandler serviceHandler = new AsyncNHttpServiceHandler(
                serverHttpProc,
                new DefaultHttpResponseFactory(),
                new DefaultConnectionReuseStrategy(),
                this.server.getParams());

        serviceHandler.setHandlerResolver(
                new SimpleNHttpRequestHandlerResolver(new TestRequestHandler()));
        serviceHandler.setEventListener(
                new SimpleEventListener());

        BasicHttpProcessor clientHttpProc = new BasicHttpProcessor();
        clientHttpProc.addInterceptor(new RequestContent());
        clientHttpProc.addInterceptor(new RequestTargetHost());
        clientHttpProc.addInterceptor(new RequestConnControl());
        clientHttpProc.addInterceptor(new RequestUserAgent());
        clientHttpProc.addInterceptor(new RequestExpectContinue());

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

        for (int i = 0; i < connNo; i++) {
            this.client.openConnection(
                    new InetSocketAddress("localhost", serverAddress.getPort()),
                    queue);
        }

        for (int i = 0; i < jobs.length; i++) {
            TestJob testjob = jobs[i];
            testjob.waitFor();
            if (testjob.isSuccessful()) {
                assertEquals(HttpStatus.SC_OK, testjob.getStatusCode());
                assertEquals("", testjob.getResult());
            } else {
                fail(testjob.getFailureMessage());
            }
        }
    }
}
