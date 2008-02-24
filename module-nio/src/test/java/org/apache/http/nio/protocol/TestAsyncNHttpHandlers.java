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
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
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
import org.apache.http.mockup.ByteSequence;
import org.apache.http.mockup.RequestCount;
import org.apache.http.mockup.ResponseSequence;
import org.apache.http.mockup.SimpleEventListener;
import org.apache.http.mockup.SimpleNHttpRequestHandlerResolver;
import org.apache.http.mockup.TestHttpClient;
import org.apache.http.mockup.TestHttpServer;
import org.apache.http.nio.NHttpClientHandler;
import org.apache.http.nio.NHttpConnection;
import org.apache.http.nio.NHttpServiceHandler;
import org.apache.http.nio.entity.BufferingNHttpEntity;
import org.apache.http.nio.entity.ConsumingNHttpEntity;
import org.apache.http.nio.entity.NByteArrayEntity;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.nio.reactor.IOReactorExceptionHandler;
import org.apache.http.nio.reactor.ListenerEndpoint;
import org.apache.http.nio.util.HeapByteBufferAllocator;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.ExecutionContext;
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
import org.apache.http.util.EntityUtils;

/**
 * HttpCore NIO integration tests for async handlers.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 * @author <a href="mailto:sberlin at gmail.com">Sam Berlin</a>
 *
 * @version $Id$
 */
public class TestAsyncNHttpHandlers extends TestCase {

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

    private TestHttpServer server;
    private TestHttpClient client;

    @Override
    protected void setUp() throws Exception {
        HttpParams serverParams = new BasicHttpParams();
        serverParams
            .setIntParameter(CoreConnectionPNames.SO_TIMEOUT, 5000)
            .setIntParameter(CoreConnectionPNames.SOCKET_BUFFER_SIZE, 8 * 1024)
            .setBooleanParameter(CoreConnectionPNames.STALE_CONNECTION_CHECK, false)
            .setBooleanParameter(CoreConnectionPNames.TCP_NODELAY, true)
            .setParameter(CoreProtocolPNames.ORIGIN_SERVER, "TEST-SERVER/1.1");

        this.server = new TestHttpServer(serverParams);
        this.server.setExceptionHandler(new IOReactorExceptionHandler() {

            public boolean handle(IOException ex) {
                ex.printStackTrace();
                return false;
            }

            public boolean handle(RuntimeException ex) {
                ex.printStackTrace();
                return false;
            }

        });

        HttpParams clientParams = new BasicHttpParams();
        clientParams
            .setIntParameter(CoreConnectionPNames.SO_TIMEOUT, 5000)
            .setIntParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, 2000)
            .setIntParameter(CoreConnectionPNames.SOCKET_BUFFER_SIZE, 8 * 1024)
            .setBooleanParameter(CoreConnectionPNames.STALE_CONNECTION_CHECK, false)
            .setBooleanParameter(CoreConnectionPNames.TCP_NODELAY, true)
            .setParameter(CoreProtocolPNames.USER_AGENT, "TEST-CLIENT/1.1");

        this.client = new TestHttpClient(clientParams);
        this.client.setExceptionHandler(new IOReactorExceptionHandler() {

            public boolean handle(IOException ex) {
                ex.printStackTrace();
                return false;
            }

            public boolean handle(RuntimeException ex) {
                ex.printStackTrace();
                return false;
            }

        });
    }

    @Override
    protected void tearDown() throws Exception {
        this.server.shutdown();
        this.client.shutdown();
    }

    private NHttpServiceHandler createHttpServiceHandler(
            final NHttpRequestHandler requestHandler,
            final HttpExpectationVerifier expectationVerifier) {
        BasicHttpProcessor httpproc = new BasicHttpProcessor();
        httpproc.addInterceptor(new ResponseDate());
        httpproc.addInterceptor(new ResponseServer());
        httpproc.addInterceptor(new ResponseContent());
        httpproc.addInterceptor(new ResponseConnControl());

        AsyncNHttpServiceHandler serviceHandler = new AsyncNHttpServiceHandler(
                httpproc,
                new DefaultHttpResponseFactory(),
                new DefaultConnectionReuseStrategy(),
                this.server.getParams());

        serviceHandler.setHandlerResolver(
                new SimpleNHttpRequestHandlerResolver(requestHandler));
        serviceHandler.setExpectationVerifier(expectationVerifier);
        serviceHandler.setEventListener(new SimpleEventListener());

        return serviceHandler;
    }

    private NHttpClientHandler createHttpClientHandler(
            final NHttpRequestExecutionHandler requestExecutionHandler) {
        BasicHttpProcessor httpproc = new BasicHttpProcessor();
        httpproc.addInterceptor(new RequestContent());
        httpproc.addInterceptor(new RequestTargetHost());
        httpproc.addInterceptor(new RequestConnControl());
        httpproc.addInterceptor(new RequestUserAgent());
        httpproc.addInterceptor(new RequestExpectContinue());

        AsyncNHttpClientHandler clientHandler = new AsyncNHttpClientHandler(
                httpproc,
                requestExecutionHandler,
                new DefaultConnectionReuseStrategy(),
                this.client.getParams());

        clientHandler.setEventListener(new SimpleEventListener());
        return clientHandler;
    }

    /**
     * This test case executes a series of simple (non-pipelined) GET requests
     * over multiple connections. This uses non-blocking output entities.
     */
    public void testSimpleHttpGets() throws Exception {

        final int connNo = 3;
        final int reqNo = 20;
        final RequestCount requestCount = new RequestCount(connNo * reqNo);
        final ByteSequence requestData = new ByteSequence();
        requestData.rnd(reqNo);

        List<ByteSequence> responseData = new ArrayList<ByteSequence>(connNo);
        for (int i = 0; i < connNo; i++) {
            responseData.add(new ByteSequence());
        }

        NHttpRequestHandler requestHandler = new SimpleNHttpRequestHandler() {

            public ConsumingNHttpEntity entityRequest(
                    final HttpEntityEnclosingRequest request,
                    final HttpContext context) {
                return null;
            }

            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                String s = request.getRequestLine().getUri();
                URI uri;
                try {
                    uri = new URI(s);
                } catch (URISyntaxException ex) {
                    throw new HttpException("Invalid request URI: " + s);
                }
                int index = Integer.parseInt(uri.getQuery());
                byte[] bytes = requestData.getBytes(index);
                NByteArrayEntity entity = new NByteArrayEntity(bytes);
                response.setEntity(entity);
            }

        };

        NHttpRequestExecutionHandler requestExecutionHandler = new NHttpRequestExecutionHandler() {

            public void initalizeContext(final HttpContext context, final Object attachment) {
                context.setAttribute("LIST", (ByteSequence) attachment);
                context.setAttribute("REQ-COUNT", new Integer(0));
                context.setAttribute("RES-COUNT", new Integer(0));
            }

            public void finalizeContext(final HttpContext context) {
            }

            public HttpRequest submitRequest(final HttpContext context) {
                int i = ((Integer) context.getAttribute("REQ-COUNT")).intValue();
                BasicHttpRequest get = null;
                if (i < reqNo) {
                    get = new BasicHttpRequest("GET", "/?" + i);
                    context.setAttribute("REQ-COUNT", new Integer(i + 1));
                }
                return get;
            }

            public ConsumingNHttpEntity responseEntity(
                    final HttpResponse response,
                    final HttpContext context) throws IOException {
                return new BufferingNHttpEntity(response.getEntity(),
                        new HeapByteBufferAllocator());
            }

            public void handleResponse(final HttpResponse response, final HttpContext context) {
                NHttpConnection conn = (NHttpConnection) context.getAttribute(
                        ExecutionContext.HTTP_CONNECTION);

                ByteSequence list = (ByteSequence) context.getAttribute("LIST");
                int i = ((Integer) context.getAttribute("RES-COUNT")).intValue();
                i++;
                context.setAttribute("RES-COUNT", new Integer(i));

                try {
                    HttpEntity entity = response.getEntity();
                    byte[] data = EntityUtils.toByteArray(entity);
                    list.addBytes(data);
                    requestCount.decrement();
                } catch (IOException ex) {
                    requestCount.abort();
                    return;
                }

                if (i < reqNo) {
                    conn.requestInput();
                }
            }

        };

        NHttpServiceHandler serviceHandler = createHttpServiceHandler(
                requestHandler,
                null);

        NHttpClientHandler clientHandler = createHttpClientHandler(
                requestExecutionHandler);

        this.server.start(serviceHandler);
        this.client.start(clientHandler);

        ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();
        InetSocketAddress serverAddress = (InetSocketAddress) endpoint.getAddress();

        for (int i = 0; i < responseData.size(); i++) {
            this.client.openConnection(
                    new InetSocketAddress("localhost", serverAddress.getPort()),
                    responseData.get(i));
        }

        requestCount.await(10000);
        assertEquals(0, requestCount.getValue());

        this.client.shutdown();
        this.server.shutdown();

        for (int c = 0; c < responseData.size(); c++) {
            ByteSequence receivedPackets = responseData.get(c);
            ByteSequence expectedPackets = requestData;
            assertEquals(expectedPackets.size(), receivedPackets.size());
            for (int p = 0; p < requestData.size(); p++) {
                byte[] expected = requestData.getBytes(p);
                byte[] received = receivedPackets.getBytes(p);

                assertEquals(expected.length, received.length);
                for (int i = 0; i < expected.length; i++) {
                    assertEquals(expected[i], received[i]);
                }
            }
        }

    }

    /**
     * This test case executes a series of simple (non-pipelined) POST requests
     * with content length delimited content over multiple connections.
     * It uses purely asynchronous handlers.
     */
    public void testSimpleHttpPostsWithContentLength() throws Exception {

        final int connNo = 3;
        final int reqNo = 20;
        final RequestCount requestCount = new RequestCount(connNo * reqNo);
        final ByteSequence requestData = new ByteSequence();
        requestData.rnd(reqNo);

        List<ByteSequence> responseData = new ArrayList<ByteSequence>(connNo);
        for (int i = 0; i < connNo; i++) {
            responseData.add(new ByteSequence());
        }

        NHttpRequestHandler requestHandler = new SimpleNHttpRequestHandler() {

            public ConsumingNHttpEntity entityRequest(
                    final HttpEntityEnclosingRequest request,
                    final HttpContext context) throws HttpException, IOException {
                return new BufferingNHttpEntity(
                        request.getEntity(),
                        new HeapByteBufferAllocator());
            }

            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                if (request instanceof HttpEntityEnclosingRequest) {
                    HttpEntity entity = ((HttpEntityEnclosingRequest) request).getEntity();
                    byte[] b = EntityUtils.toByteArray(entity);
                    response.setEntity(new NByteArrayEntity(b));
                } else {
                    NStringEntity outgoing = new NStringEntity("No content");
                    response.setEntity(outgoing);
                }
            }
        };

        NHttpRequestExecutionHandler requestExecutionHandler = new NHttpRequestExecutionHandler() {

            public void initalizeContext(final HttpContext context, final Object attachment) {
                context.setAttribute("LIST", (ByteSequence) attachment);
                context.setAttribute("REQ-COUNT", new Integer(0));
                context.setAttribute("RES-COUNT", new Integer(0));
            }

            public void finalizeContext(final HttpContext context) {
            }

            public ConsumingNHttpEntity responseEntity(
                    final HttpResponse response,
                    final HttpContext context) throws IOException {
                return new BufferingNHttpEntity(response.getEntity(),
                        new HeapByteBufferAllocator());
            }

            public HttpRequest submitRequest(final HttpContext context) {
                int i = ((Integer) context.getAttribute("REQ-COUNT")).intValue();
                BasicHttpEntityEnclosingRequest post = null;
                if (i < reqNo) {
                    post = new BasicHttpEntityEnclosingRequest("POST", "/?" + i);

                    byte[] data = requestData.getBytes(i);
                    NByteArrayEntity outgoing = new NByteArrayEntity(data);
                    post.setEntity(outgoing);

                    context.setAttribute("REQ-COUNT", new Integer(i + 1));
                }
                return post;
            }

            public void handleResponse(final HttpResponse response, final HttpContext context) {
                NHttpConnection conn = (NHttpConnection) context.getAttribute(
                        ExecutionContext.HTTP_CONNECTION);

                ByteSequence list = (ByteSequence) context.getAttribute("LIST");
                int i = ((Integer) context.getAttribute("RES-COUNT")).intValue();
                i++;
                context.setAttribute("RES-COUNT", new Integer(i));

                try {
                    HttpEntity entity = response.getEntity();
                    byte[] data = EntityUtils.toByteArray(entity);
                    list.addBytes(data);
                    requestCount.decrement();
                } catch (IOException ex) {
                    requestCount.abort();
                    return;
                }

                if (i < reqNo) {
                    conn.requestInput();
                }
            }

        };

        NHttpServiceHandler serviceHandler = createHttpServiceHandler(
                requestHandler,
                null);

        NHttpClientHandler clientHandler = createHttpClientHandler(
                requestExecutionHandler);

        this.server.start(serviceHandler);
        this.client.start(clientHandler);

        ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();
        InetSocketAddress serverAddress = (InetSocketAddress) endpoint.getAddress();

        for (int i = 0; i < responseData.size(); i++) {
            this.client.openConnection(
                    new InetSocketAddress("localhost", serverAddress.getPort()),
                    responseData.get(i));
        }

        requestCount.await(10000);
        assertEquals(0, requestCount.getValue());

        this.client.shutdown();
        this.server.shutdown();

        for (int c = 0; c < responseData.size(); c++) {
            ByteSequence receivedPackets = responseData.get(c);
            ByteSequence expectedPackets = requestData;
            assertEquals(expectedPackets.size(), receivedPackets.size());
            for (int p = 0; p < requestData.size(); p++) {
                byte[] expected = requestData.getBytes(p);
                byte[] received = receivedPackets.getBytes(p);

                assertEquals(expected.length, received.length);
                for (int i = 0; i < expected.length; i++) {
                    assertEquals(expected[i], received[i]);
                }
            }
        }

    }


    /**
     * This test case executes a series of simple (non-pipelined) POST requests
     * with chunk coded content content over multiple connections.  This tests
     * with nonblocking handlers & nonblocking entities.
     */
    public void testSimpleHttpPostsChunked() throws Exception {
        final int connNo = 3;
        final int reqNo = 20;
        final RequestCount requestCount = new RequestCount(connNo * reqNo);
        final ByteSequence requestData = new ByteSequence();
        requestData.rnd(reqNo);

        List<ByteSequence> responseData = new ArrayList<ByteSequence>(connNo);
        for (int i = 0; i < connNo; i++) {
            responseData.add(new ByteSequence());
        }

        NHttpRequestHandler requestHandler = new SimpleNHttpRequestHandler() {

            public ConsumingNHttpEntity entityRequest(
                    final HttpEntityEnclosingRequest request,
                    final HttpContext context) throws HttpException, IOException {
                return new BufferingNHttpEntity(
                        request.getEntity(),
                        new HeapByteBufferAllocator());
            }

            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                if (request instanceof HttpEntityEnclosingRequest) {
                    HttpEntity entity = ((HttpEntityEnclosingRequest) request).getEntity();
                    byte[] b = EntityUtils.toByteArray(entity);
                    NByteArrayEntity outgoing = new NByteArrayEntity(b);
                    outgoing.setChunked(true);
                    response.setEntity(outgoing);
                } else {
                    NStringEntity outgoing = new NStringEntity("No content");
                    response.setEntity(outgoing);
                }
            }
        };

        NHttpRequestExecutionHandler requestExecutionHandler = new NHttpRequestExecutionHandler() {

            public void initalizeContext(final HttpContext context, final Object attachment) {
                context.setAttribute("LIST", (ByteSequence) attachment);
                context.setAttribute("REQ-COUNT", new Integer(0));
                context.setAttribute("RES-COUNT", new Integer(0));
            }

            public void finalizeContext(final HttpContext context) {
            }

            public ConsumingNHttpEntity responseEntity(
                    final HttpResponse response,
                    final HttpContext context) throws IOException {
                return new BufferingNHttpEntity(response.getEntity(),
                        new HeapByteBufferAllocator());
            }

            public HttpRequest submitRequest(final HttpContext context) {
                int i = ((Integer) context.getAttribute("REQ-COUNT")).intValue();
                BasicHttpEntityEnclosingRequest post = null;
                if (i < reqNo) {
                    post = new BasicHttpEntityEnclosingRequest("POST", "/?" + i);
                    byte[] data = requestData.getBytes(i);
                    NByteArrayEntity outgoing = new NByteArrayEntity(data);
                    outgoing.setChunked(true);
                    post.setEntity(outgoing);

                    context.setAttribute("REQ-COUNT", new Integer(i + 1));
                }
                return post;
            }

            public void handleResponse(final HttpResponse response, final HttpContext context) {
                NHttpConnection conn = (NHttpConnection) context.getAttribute(
                        ExecutionContext.HTTP_CONNECTION);

                ByteSequence list = (ByteSequence) context.getAttribute("LIST");
                int i = ((Integer) context.getAttribute("RES-COUNT")).intValue();
                i++;
                context.setAttribute("RES-COUNT", new Integer(i));

                try {
                    HttpEntity entity = response.getEntity();
                    byte[] data = EntityUtils.toByteArray(entity);
                    list.addBytes(data);
                    requestCount.decrement();
                } catch (IOException ex) {
                    requestCount.abort();
                    return;
                }

                if (i < reqNo) {
                    conn.requestInput();
                }
            }

        };

        NHttpServiceHandler serviceHandler = createHttpServiceHandler(
                requestHandler,
                null);

        NHttpClientHandler clientHandler = createHttpClientHandler(
                requestExecutionHandler);

        this.server.start(serviceHandler);
        this.client.start(clientHandler);

        ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();
        InetSocketAddress serverAddress = (InetSocketAddress) endpoint.getAddress();

        for (int i = 0; i < responseData.size(); i++) {
            this.client.openConnection(
                    new InetSocketAddress("localhost", serverAddress.getPort()),
                    responseData.get(i));
        }

        requestCount.await(10000);
        if (requestCount.isAborted()) {
            System.out.println("Test case aborted");
        }
        assertEquals(0, requestCount.getValue());

        this.client.shutdown();
        this.server.shutdown();

        for (int c = 0; c < responseData.size(); c++) {
            ByteSequence receivedPackets = responseData.get(c);
            ByteSequence expectedPackets = requestData;
            assertEquals(expectedPackets.size(), receivedPackets.size());
            for (int p = 0; p < requestData.size(); p++) {
                byte[] expected = requestData.getBytes(p);
                byte[] received = receivedPackets.getBytes(p);

                assertEquals(expected.length, received.length);
                for (int i = 0; i < expected.length; i++) {
                    assertEquals(expected[i], received[i]);
                }
            }
        }

    }

    /**
     * This test case executes a series of simple (non-pipelined) HTTP/1.0
     * POST requests over multiple persistent connections. This tests with nonblocking
     * handlers & entities.
     */
    public void testSimpleHttpPostsHTTP10() throws Exception {

        final int connNo = 3;
        final int reqNo = 20;
        final RequestCount requestCount = new RequestCount(connNo * reqNo);
        final ByteSequence requestData = new ByteSequence();
        requestData.rnd(reqNo);

        List<ByteSequence> responseData = new ArrayList<ByteSequence>(connNo);
        for (int i = 0; i < connNo; i++) {
            responseData.add(new ByteSequence());
        }

        NHttpRequestHandler requestHandler = new SimpleNHttpRequestHandler() {

            public ConsumingNHttpEntity entityRequest(
                    final HttpEntityEnclosingRequest request,
                    final HttpContext context) throws HttpException, IOException {
                return new BufferingNHttpEntity(
                        request.getEntity(),
                        new HeapByteBufferAllocator());
            }

            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                if (request instanceof HttpEntityEnclosingRequest) {
                    HttpEntity entity = ((HttpEntityEnclosingRequest) request).getEntity();
                    byte[] b = EntityUtils.toByteArray(entity);
                    NByteArrayEntity outgoing = new NByteArrayEntity(b);
                    outgoing.setChunked(false);
                    response.setEntity(outgoing);
                } else {
                    NStringEntity outgoing = new NStringEntity("No content");
                    response.setEntity(outgoing);
                }
            }
        };

        // Set protocol level to HTTP/1.0
        this.client.getParams().setParameter(
                CoreProtocolPNames.PROTOCOL_VERSION, HttpVersion.HTTP_1_0);

        NHttpRequestExecutionHandler requestExecutionHandler = new NHttpRequestExecutionHandler() {

            public void initalizeContext(final HttpContext context, final Object attachment) {
                context.setAttribute("LIST", (ByteSequence) attachment);
                context.setAttribute("REQ-COUNT", new Integer(0));
                context.setAttribute("RES-COUNT", new Integer(0));
            }

            public void finalizeContext(final HttpContext context) {
            }

            public ConsumingNHttpEntity responseEntity(
                    final HttpResponse response,
                    final HttpContext context) throws IOException {
                return new BufferingNHttpEntity(response.getEntity(),
                        new HeapByteBufferAllocator());
            }

            public HttpRequest submitRequest(final HttpContext context) {
                int i = ((Integer) context.getAttribute("REQ-COUNT")).intValue();
                BasicHttpEntityEnclosingRequest post = null;
                if (i < reqNo) {
                    post = new BasicHttpEntityEnclosingRequest("POST", "/?" + i);
                    byte[] data = requestData.getBytes(i);
                    NByteArrayEntity outgoing = new NByteArrayEntity(data);
                    post.setEntity(outgoing);

                    context.setAttribute("REQ-COUNT", new Integer(i + 1));
                }
                return post;
            }

            public void handleResponse(final HttpResponse response, final HttpContext context) {
                NHttpConnection conn = (NHttpConnection) context.getAttribute(
                        ExecutionContext.HTTP_CONNECTION);

                ByteSequence list = (ByteSequence) context.getAttribute("LIST");
                int i = ((Integer) context.getAttribute("RES-COUNT")).intValue();
                i++;
                context.setAttribute("RES-COUNT", new Integer(i));

                try {
                    HttpEntity entity = response.getEntity();
                    byte[] data = EntityUtils.toByteArray(entity);
                    list.addBytes(data);
                    requestCount.decrement();
                } catch (IOException ex) {
                    requestCount.abort();
                    return;
                }

                if (i < reqNo) {
                    conn.requestInput();
                }
            }

        };

        NHttpServiceHandler serviceHandler = createHttpServiceHandler(
                requestHandler,
                null);

        NHttpClientHandler clientHandler = createHttpClientHandler(
                requestExecutionHandler);

        this.server.start(serviceHandler);
        this.client.start(clientHandler);

        ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();
        InetSocketAddress serverAddress = (InetSocketAddress) endpoint.getAddress();

        for (int i = 0; i < responseData.size(); i++) {
            this.client.openConnection(
                    new InetSocketAddress("localhost", serverAddress.getPort()),
                    responseData.get(i));
        }

        requestCount.await(10000);
        assertEquals(0, requestCount.getValue());

        this.client.shutdown();
        this.server.shutdown();

        for (int c = 0; c < responseData.size(); c++) {
            ByteSequence receivedPackets = responseData.get(c);
            ByteSequence expectedPackets = requestData;
            assertEquals(expectedPackets.size(), receivedPackets.size());
            for (int p = 0; p < requestData.size(); p++) {
                byte[] expected = requestData.getBytes(p);
                byte[] received = receivedPackets.getBytes(p);

                assertEquals(expected.length, received.length);
                for (int i = 0; i < expected.length; i++) {
                    assertEquals(expected[i], received[i]);
                }
            }
        }

    }

    /**
     * This test case executes a series of simple (non-pipelined) POST requests
     * over multiple connections using the 'expect: continue' handshake.  This test
     * uses nonblocking handlers & entities.
     */
    public void testHttpPostsWithExpectContinue() throws Exception {

        final int connNo = 3;
        final int reqNo = 20;
        final RequestCount requestCount = new RequestCount(connNo * reqNo);
        final ByteSequence requestData = new ByteSequence();
        requestData.rnd(reqNo);

        List<ByteSequence> responseData = new ArrayList<ByteSequence>(connNo);
        for (int i = 0; i < connNo; i++) {
            responseData.add(new ByteSequence());
        }

        NHttpRequestHandler requestHandler = new SimpleNHttpRequestHandler() {

            public ConsumingNHttpEntity entityRequest(
                    final HttpEntityEnclosingRequest request,
                    final HttpContext context) throws HttpException, IOException {
                return new BufferingNHttpEntity(
                        request.getEntity(),
                        new HeapByteBufferAllocator());
            }

            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                if (request instanceof HttpEntityEnclosingRequest) {
                    HttpEntity entity = ((HttpEntityEnclosingRequest) request).getEntity();
                    byte[] b = EntityUtils.toByteArray(entity);
                    NByteArrayEntity outgoing = new NByteArrayEntity(b);
                    outgoing.setChunked(true);
                    response.setEntity(outgoing);
                } else {
                    NStringEntity outgoing = new NStringEntity("No content");
                    response.setEntity(outgoing);
                }
            }
        };

        // Activate 'expect: continue' handshake
        this.client.getParams().setBooleanParameter(CoreProtocolPNames.USE_EXPECT_CONTINUE, true);

        NHttpRequestExecutionHandler requestExecutionHandler = new NHttpRequestExecutionHandler() {

            public void initalizeContext(final HttpContext context, final Object attachment) {
                context.setAttribute("LIST", (ByteSequence) attachment);
                context.setAttribute("REQ-COUNT", new Integer(0));
                context.setAttribute("RES-COUNT", new Integer(0));
            }

            public void finalizeContext(final HttpContext context) {
            }

            public ConsumingNHttpEntity responseEntity(
                    final HttpResponse response,
                    final HttpContext context) throws IOException {
                return new BufferingNHttpEntity(response.getEntity(),
                        new HeapByteBufferAllocator());
            }

            public HttpRequest submitRequest(final HttpContext context) {
                int i = ((Integer) context.getAttribute("REQ-COUNT")).intValue();
                BasicHttpEntityEnclosingRequest post = null;
                if (i < reqNo) {
                    post = new BasicHttpEntityEnclosingRequest("POST", "/?" + i);
                    byte[] data = requestData.getBytes(i);
                    NByteArrayEntity outgoing = new NByteArrayEntity(data);
                    outgoing.setChunked(true);
                    post.setEntity(outgoing);

                    context.setAttribute("REQ-COUNT", new Integer(i + 1));
                }
                return post;
            }

            public void handleResponse(final HttpResponse response, final HttpContext context) {
                NHttpConnection conn = (NHttpConnection) context.getAttribute(
                        ExecutionContext.HTTP_CONNECTION);

                ByteSequence list = (ByteSequence) context.getAttribute("LIST");
                int i = ((Integer) context.getAttribute("RES-COUNT")).intValue();
                i++;
                context.setAttribute("RES-COUNT", new Integer(i));

                try {
                    HttpEntity entity = response.getEntity();
                    byte[] data = EntityUtils.toByteArray(entity);
                    list.addBytes(data);
                    requestCount.decrement();
                } catch (IOException ex) {
                    requestCount.abort();
                    return;
                }

                if (i < reqNo) {
                    conn.requestInput();
                }
            }

        };

        NHttpServiceHandler serviceHandler = createHttpServiceHandler(
                requestHandler,
                null);

        NHttpClientHandler clientHandler = createHttpClientHandler(
                requestExecutionHandler);

        this.server.start(serviceHandler);
        this.client.start(clientHandler);

        ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();
        InetSocketAddress serverAddress = (InetSocketAddress) endpoint.getAddress();

        for (int i = 0; i < responseData.size(); i++) {
            this.client.openConnection(
                    new InetSocketAddress("localhost", serverAddress.getPort()),
                    responseData.get(i));
        }

        requestCount.await(10000);
        assertEquals(0, requestCount.getValue());

        this.client.shutdown();
        this.server.shutdown();

        for (int c = 0; c < responseData.size(); c++) {
            ByteSequence receivedPackets = responseData.get(c);
            ByteSequence expectedPackets = requestData;
            assertEquals(expectedPackets.size(), receivedPackets.size());
            for (int p = 0; p < requestData.size(); p++) {
                byte[] expected = requestData.getBytes(p);
                byte[] received = receivedPackets.getBytes(p);

                assertEquals(expected.length, received.length);
                for (int i = 0; i < expected.length; i++) {
                    assertEquals(expected[i], received[i]);
                }
            }
        }

    }

    /**
     * This test case executes a series of simple (non-pipelined) POST requests
     * over multiple connections that do not meet the target server expectations.
     * This test uses nonblocking entities.
     */
    public void testHttpPostsWithExpectationVerification() throws Exception {

        final int reqNo = 3;
        final RequestCount requestCount = new RequestCount(reqNo);
        final ResponseSequence responses = new ResponseSequence();

        NHttpRequestHandler requestHandler = new SimpleNHttpRequestHandler() {

            public ConsumingNHttpEntity entityRequest(
                    final HttpEntityEnclosingRequest request,
                    final HttpContext context) throws HttpException, IOException {
                return new BufferingNHttpEntity(
                        request.getEntity(),
                        new HeapByteBufferAllocator());
            }

            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                NStringEntity outgoing = new NStringEntity("No content");
                response.setEntity(outgoing);
            }
        };

        HttpExpectationVerifier expectationVerifier = new HttpExpectationVerifier() {

            public void verify(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException {
                Header someheader = request.getFirstHeader("Secret");
                if (someheader != null) {
                    int secretNumber;
                    try {
                        secretNumber = Integer.parseInt(someheader.getValue());
                    } catch (NumberFormatException ex) {
                        response.setStatusCode(HttpStatus.SC_BAD_REQUEST);
                        return;
                    }
                    if (secretNumber < 2) {
                        response.setStatusCode(HttpStatus.SC_EXPECTATION_FAILED);
                        NByteArrayEntity outgoing = new NByteArrayEntity(
                                EncodingUtils.getAsciiBytes("Wrong secret number"));
                        response.setEntity(outgoing);
                    }
                }
            }

        };

        // Activate 'expect: continue' handshake
        this.client.getParams().setBooleanParameter(CoreProtocolPNames.USE_EXPECT_CONTINUE, true);

        NHttpRequestExecutionHandler requestExecutionHandler = new NHttpRequestExecutionHandler() {

            public void initalizeContext(final HttpContext context, final Object attachment) {
                context.setAttribute("LIST", (ResponseSequence) attachment);
                context.setAttribute("REQ-COUNT", new Integer(0));
                context.setAttribute("RES-COUNT", new Integer(0));
            }

            public void finalizeContext(final HttpContext context) {
            }

            public ConsumingNHttpEntity responseEntity(
                    final HttpResponse response,
                    final HttpContext context) throws IOException {
                return new BufferingNHttpEntity(response.getEntity(),
                        new HeapByteBufferAllocator());
            }

            public HttpRequest submitRequest(final HttpContext context) {
                int i = ((Integer) context.getAttribute("REQ-COUNT")).intValue();
                BasicHttpEntityEnclosingRequest post = null;
                if (i < reqNo) {
                    post = new BasicHttpEntityEnclosingRequest("POST", "/");
                    post.addHeader("Secret", Integer.toString(i));
                    NByteArrayEntity outgoing = new NByteArrayEntity(
                            EncodingUtils.getAsciiBytes("No content"));
                    post.setEntity(outgoing);

                    context.setAttribute("REQ-COUNT", new Integer(i + 1));
                }
                return post;
            }

            public void handleResponse(final HttpResponse response, final HttpContext context) {
                NHttpConnection conn = (NHttpConnection) context.getAttribute(
                        ExecutionContext.HTTP_CONNECTION);

                ResponseSequence list = (ResponseSequence) context.getAttribute("LIST");
                int i = ((Integer) context.getAttribute("RES-COUNT")).intValue();
                i++;
                context.setAttribute("RES-COUNT", new Integer(i));

                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    try {
                        EntityUtils.toByteArray(entity);
                    } catch (IOException ex) {
                        requestCount.abort();
                        return;
                    }
                }

                list.addResponse(response);
                requestCount.decrement();

                if (i < reqNo) {
                    conn.requestInput();
                }
            }

        };

        NHttpServiceHandler serviceHandler = createHttpServiceHandler(
                requestHandler,
                expectationVerifier);

        NHttpClientHandler clientHandler = createHttpClientHandler(
                requestExecutionHandler);

        this.server.start(serviceHandler);
        this.client.start(clientHandler);

        ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();
        InetSocketAddress serverAddress = (InetSocketAddress) endpoint.getAddress();

        this.client.openConnection(
                new InetSocketAddress("localhost", serverAddress.getPort()),
                responses);

        requestCount.await(1000);

        this.client.shutdown();
        this.server.shutdown();

        assertEquals(reqNo, responses.size());
        HttpResponse response = responses.getResponse(0);
        assertEquals(HttpStatus.SC_EXPECTATION_FAILED, response.getStatusLine().getStatusCode());
        response = responses.getResponse(1);
        assertEquals(HttpStatus.SC_EXPECTATION_FAILED, response.getStatusLine().getStatusCode());
        response = responses.getResponse(2);
        assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
    }

    /**
     * This test case executes a series of simple (non-pipelined) HEAD requests
     * over multiple connections. With nonblocking entities.
     */
    public void testSimpleHttpHeads() throws Exception {

        final int connNo = 3;
        final int reqNo = 20;
        final RequestCount requestCount = new RequestCount(connNo * reqNo * 2);

        final ByteSequence requestData = new ByteSequence();
        requestData.rnd(reqNo);

        List<ResponseSequence> responseData1 = new ArrayList<ResponseSequence>(connNo);
        for (int i = 0; i < connNo; i++) {
            responseData1.add(new ResponseSequence());
        }
        List<ResponseSequence> responseData2 = new ArrayList<ResponseSequence>(connNo);
        for (int i = 0; i < connNo; i++) {
            responseData2.add(new ResponseSequence());
        }

        final String[] method = new String[1];

        NHttpRequestHandler requestHandler = new SimpleNHttpRequestHandler() {

            public ConsumingNHttpEntity entityRequest(
                    final HttpEntityEnclosingRequest request,
                    final HttpContext context) throws HttpException, IOException {
                return new BufferingNHttpEntity(
                        request.getEntity(),
                        new HeapByteBufferAllocator());
            }

            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                String s = request.getRequestLine().getUri();
                URI uri;
                try {
                    uri = new URI(s);
                } catch (URISyntaxException ex) {
                    throw new HttpException("Invalid request URI: " + s);
                }
                int index = Integer.parseInt(uri.getQuery());
                byte[] bytes = requestData.getBytes(index);
                NByteArrayEntity entity = new NByteArrayEntity(bytes);
                response.setEntity(entity);
            }

        };

        NHttpRequestExecutionHandler requestExecutionHandler = new NHttpRequestExecutionHandler() {

            public void initalizeContext(final HttpContext context, final Object attachment) {
                context.setAttribute("LIST", (ResponseSequence) attachment);
                context.setAttribute("REQ-COUNT", new Integer(0));
                context.setAttribute("RES-COUNT", new Integer(0));
            }

            public void finalizeContext(final HttpContext context) {
            }

            public ConsumingNHttpEntity responseEntity(
                    final HttpResponse response,
                    final HttpContext context) throws IOException {
                return new BufferingNHttpEntity(response.getEntity(),
                        new HeapByteBufferAllocator());
            }

            public HttpRequest submitRequest(final HttpContext context) {
                int i = ((Integer) context.getAttribute("REQ-COUNT")).intValue();
                BasicHttpRequest request = null;
                if (i < reqNo) {
                    request = new BasicHttpRequest(method[0], "/?" + i);
                    context.setAttribute("REQ-COUNT", new Integer(i + 1));
                }
                return request;
            }

            public void handleResponse(final HttpResponse response, final HttpContext context) {
                NHttpConnection conn = (NHttpConnection) context.getAttribute(
                        ExecutionContext.HTTP_CONNECTION);

                ResponseSequence list = (ResponseSequence) context.getAttribute("LIST");
                int i = ((Integer) context.getAttribute("RES-COUNT")).intValue();
                i++;
                context.setAttribute("RES-COUNT", new Integer(i));

                list.addResponse(response);
                requestCount.decrement();

                if (i < reqNo) {
                    conn.requestInput();
                }
            }

        };

        NHttpServiceHandler serviceHandler = createHttpServiceHandler(
                requestHandler,
                null);

        NHttpClientHandler clientHandler = createHttpClientHandler(
                requestExecutionHandler);

        this.server.start(serviceHandler);
        this.client.start(clientHandler);

        ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();
        InetSocketAddress serverAddress = (InetSocketAddress) endpoint.getAddress();

        method[0] = "GET";

        for (int i = 0; i < responseData1.size(); i++) {
            this.client.openConnection(
                    new InetSocketAddress("localhost", serverAddress.getPort()),
                    responseData1.get(i));
        }

        requestCount.await(connNo * reqNo, 10000);
        assertEquals(connNo * reqNo, requestCount.getValue());

        method[0] = "HEAD";

        for (int i = 0; i < responseData2.size(); i++) {
            this.client.openConnection(
                    new InetSocketAddress("localhost", serverAddress.getPort()),
                    responseData2.get(i));
        }


        requestCount.await(10000);
        assertEquals(0, requestCount.getValue());

        this.client.shutdown();
        this.server.shutdown();

        for (int c = 0; c < connNo; c++) {
            ResponseSequence getResponses = responseData1.get(c);
            ResponseSequence headResponses = responseData2.get(c);
            ByteSequence expectedPackets = requestData;
            assertEquals(expectedPackets.size(), headResponses.size());
            assertEquals(expectedPackets.size(), getResponses.size());
            for (int p = 0; p < requestData.size(); p++) {
                HttpResponse getResponse = getResponses.getResponse(p);
                HttpResponse headResponse = headResponses.getResponse(p);
                assertEquals(null, headResponse.getEntity());

                Header[] getHeaders = getResponse.getAllHeaders();
                Header[] headHeaders = headResponse.getAllHeaders();
                assertEquals(getHeaders.length, headHeaders.length);
                for (int j = 0; j < getHeaders.length; j++) {
                    if ("Date".equals(getHeaders[j].getName())) {
                        continue;
                    }
                    assertEquals(getHeaders[j].toString(), headHeaders[j].toString());
                }
            }
        }
    }

}
