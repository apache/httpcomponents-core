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

package org.apache.http.impl.nio.reactor;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.http.HttpCoreNIOTestBase;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.OoopsieRuntimeException;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.mockup.SimpleEventListener;
import org.apache.http.mockup.SimpleHttpRequestHandlerResolver;
import org.apache.http.nio.NHttpConnection;
import org.apache.http.nio.protocol.BufferingHttpClientHandler;
import org.apache.http.nio.protocol.BufferingHttpServiceHandler;
import org.apache.http.nio.protocol.EventListener;
import org.apache.http.nio.protocol.HttpRequestExecutionHandler;
import org.apache.http.nio.reactor.IOReactorException;
import org.apache.http.nio.reactor.IOReactorExceptionHandler;
import org.apache.http.nio.reactor.IOReactorStatus;
import org.apache.http.nio.reactor.ListenerEndpoint;
import org.apache.http.nio.reactor.SessionRequest;
import org.apache.http.protocol.HttpContext;
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

/**
 * Tests for basic I/O functionality.
 */
public class TestDefaultIOReactors extends HttpCoreNIOTestBase {

    // ------------------------------------------------------------ Constructor
    public TestDefaultIOReactors(String testName) {
        super(testName);
    }

    // ------------------------------------------------------- TestCase Methods

    public void testGracefulShutdown() throws Exception {

        // Open some connection and make sure
        // they get cleanly closed upon shutdown

        final int connNo = 10;
        final CountDownLatch requestConns = new CountDownLatch(connNo);
        final AtomicInteger closedServerConns = new AtomicInteger(0);
        final AtomicInteger openServerConns = new AtomicInteger(0);
        final AtomicInteger closedClientConns = new AtomicInteger(0);
        final AtomicInteger openClientConns = new AtomicInteger(0);

        HttpRequestHandler requestHandler = new HttpRequestHandler() {

            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
            }

        };

        HttpRequestExecutionHandler requestExecutionHandler = new HttpRequestExecutionHandler() {

            public void initalizeContext(final HttpContext context, final Object attachment) {
            }

            public void finalizeContext(final HttpContext context) {
                while (requestConns.getCount() > 0) {
                    requestConns.countDown();
                }
            }

            public HttpRequest submitRequest(final HttpContext context) {
                Boolean b = ((Boolean) context.getAttribute("done"));
                if (b == null) {
                    BasicHttpRequest get = new BasicHttpRequest("GET", "/");
                    context.setAttribute("done", Boolean.TRUE);
                    return get;
                } else {
                    return null;
                }
            }

            public void handleResponse(final HttpResponse response, final HttpContext context) {
                requestConns.countDown();
            }

        };

        EventListener serverEventListener = new SimpleEventListener() {

            @Override
            public void connectionOpen(NHttpConnection conn) {
                openServerConns.incrementAndGet();
                super.connectionOpen(conn);
            }

            @Override
            public void connectionClosed(NHttpConnection conn) {
                closedServerConns.incrementAndGet();
                super.connectionClosed(conn);
            }

        };

        HttpProcessor serverHttpProc = new ImmutableHttpProcessor(new HttpResponseInterceptor[] {
                new ResponseDate(),
                new ResponseServer(),
                new ResponseContent(),
                new ResponseConnControl()
        });

        BufferingHttpServiceHandler serviceHandler = new BufferingHttpServiceHandler(
                serverHttpProc,
                new DefaultHttpResponseFactory(),
                new DefaultConnectionReuseStrategy(),
                this.server.getParams());

        serviceHandler.setHandlerResolver(
                new SimpleHttpRequestHandlerResolver(requestHandler));
        serviceHandler.setEventListener(
                serverEventListener);

        EventListener clientEventListener = new SimpleEventListener() {

            @Override
            public void connectionOpen(NHttpConnection conn) {
                openClientConns.incrementAndGet();
                super.connectionOpen(conn);
            }

            @Override
            public void connectionClosed(NHttpConnection conn) {
                closedClientConns.incrementAndGet();
                super.connectionClosed(conn);
            }

        };

        HttpProcessor clientHttpProc = new ImmutableHttpProcessor(new HttpRequestInterceptor[] {
                new RequestContent(),
                new RequestTargetHost(),
                new RequestConnControl(),
                new RequestUserAgent(),
                new RequestExpectContinue()});

        BufferingHttpClientHandler clientHandler = new BufferingHttpClientHandler(
                clientHttpProc,
                requestExecutionHandler,
                new DefaultConnectionReuseStrategy(),
                this.client.getParams());

        clientHandler.setEventListener(
                clientEventListener);

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
                    null);
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

        requestConns.await();
        assertEquals(0, requestConns.getCount());

        this.client.shutdown();
        this.server.shutdown();

        assertEquals(openClientConns.get(), closedClientConns.get());
        assertEquals(openServerConns.get(), closedServerConns.get());
    }

    public void testRuntimeException() throws Exception {

        HttpRequestHandler requestHandler = new HttpRequestHandler() {

            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                throw new OoopsieRuntimeException();
            }

        };

        HttpRequestExecutionHandler requestExecutionHandler = new HttpRequestExecutionHandler() {

            public void initalizeContext(final HttpContext context, final Object attachment) {
            }

            public void finalizeContext(final HttpContext context) {
            }

            public HttpRequest submitRequest(final HttpContext context) {
                Boolean b = ((Boolean) context.getAttribute("done"));
                if (b == null) {
                    BasicHttpRequest get = new BasicHttpRequest("GET", "/");
                    context.setAttribute("done", Boolean.TRUE);
                    return get;
                } else {
                    return null;
                }
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

        BufferingHttpServiceHandler serviceHandler = new BufferingHttpServiceHandler(
                serverHttpProc,
                new DefaultHttpResponseFactory(),
                new DefaultConnectionReuseStrategy(),
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

        BufferingHttpClientHandler clientHandler = new BufferingHttpClientHandler(
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

        this.client.openConnection(
                new InetSocketAddress("localhost", serverAddress.getPort()),
                null);

        this.server.join(20000);

        Exception ex = this.server.getException();
        assertNotNull(ex);
        assertTrue(ex instanceof IOReactorException);
        assertNotNull(ex.getCause());
        assertTrue(ex.getCause() instanceof OoopsieRuntimeException);

        List<ExceptionEvent> auditlog = this.server.getAuditLog();
        assertNotNull(auditlog);
        assertEquals(1, auditlog.size());

        // I/O reactor shut down itself
        assertEquals(IOReactorStatus.SHUT_DOWN, this.server.getStatus());

        this.client.shutdown();
        this.server.shutdown();
    }

    public void testUnhandledRuntimeException() throws Exception {

        final CountDownLatch requestConns = new CountDownLatch(1);

        HttpRequestHandler requestHandler = new HttpRequestHandler() {

            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                throw new OoopsieRuntimeException();
            }

        };

        HttpRequestExecutionHandler requestExecutionHandler = new HttpRequestExecutionHandler() {

            public void initalizeContext(final HttpContext context, final Object attachment) {
            }

            public void finalizeContext(final HttpContext context) {
            }

            public HttpRequest submitRequest(final HttpContext context) {
                Boolean b = ((Boolean) context.getAttribute("done"));
                if (b == null) {
                    BasicHttpRequest get = new BasicHttpRequest("GET", "/");
                    context.setAttribute("done", Boolean.TRUE);
                    return get;
                } else {
                    return null;
                }
            }

            public void handleResponse(final HttpResponse response, final HttpContext context) {
            }

        };

        IOReactorExceptionHandler exceptionHandler = new IOReactorExceptionHandler() {

            public boolean handle(final IOException ex) {
                return false;
            }

            public boolean handle(final RuntimeException ex) {
                requestConns.countDown();
                return false;
            }

        };

        HttpProcessor serverHttpProc = new ImmutableHttpProcessor(new HttpResponseInterceptor[] {
                new ResponseDate(),
                new ResponseServer(),
                new ResponseContent(),
                new ResponseConnControl()
        });

        BufferingHttpServiceHandler serviceHandler = new BufferingHttpServiceHandler(
                serverHttpProc,
                new DefaultHttpResponseFactory(),
                new DefaultConnectionReuseStrategy(),
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

        BufferingHttpClientHandler clientHandler = new BufferingHttpClientHandler(
                clientHttpProc,
                requestExecutionHandler,
                new DefaultConnectionReuseStrategy(),
                this.client.getParams());

        this.server.setExceptionHandler(exceptionHandler);
        this.server.start(serviceHandler);
        this.client.start(clientHandler);

        ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();
        InetSocketAddress serverAddress = (InetSocketAddress) endpoint.getAddress();

        this.client.openConnection(
                new InetSocketAddress("localhost", serverAddress.getPort()),
                null);

        requestConns.await();
        assertEquals(0, requestConns.getCount());

        this.server.join(20000);

        Exception ex = this.server.getException();
        assertNotNull(ex);
        assertTrue(ex instanceof IOReactorException);
        assertNotNull(ex.getCause());
        assertTrue(ex.getCause() instanceof OoopsieRuntimeException);

        List<ExceptionEvent> auditlog = this.server.getAuditLog();
        assertNotNull(auditlog);
        assertEquals(1, auditlog.size());

        // I/O reactor shut down itself
        assertEquals(IOReactorStatus.SHUT_DOWN, this.server.getStatus());

        this.client.shutdown();
        this.server.shutdown();
    }

    public void testHandledRuntimeException() throws Exception {

        final CountDownLatch requestConns = new CountDownLatch(1);

        HttpRequestHandler requestHandler = new HttpRequestHandler() {

            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                throw new OoopsieRuntimeException();
            }

        };

        HttpRequestExecutionHandler requestExecutionHandler = new HttpRequestExecutionHandler() {

            public void initalizeContext(final HttpContext context, final Object attachment) {
            }

            public void finalizeContext(final HttpContext context) {
            }

            public HttpRequest submitRequest(final HttpContext context) {
                Boolean b = ((Boolean) context.getAttribute("done"));
                if (b == null) {
                    BasicHttpRequest get = new BasicHttpRequest("GET", "/");
                    context.setAttribute("done", Boolean.TRUE);
                    return get;
                } else {
                    return null;
                }
            }

            public void handleResponse(final HttpResponse response, final HttpContext context) {
            }

        };

        IOReactorExceptionHandler exceptionHandler = new IOReactorExceptionHandler() {

            public boolean handle(final IOException ex) {
                return false;
            }

            public boolean handle(final RuntimeException ex) {
                requestConns.countDown();
                return true;
            }

        };

        HttpProcessor serverHttpProc = new ImmutableHttpProcessor(new HttpResponseInterceptor[] {
                new ResponseDate(),
                new ResponseServer(),
                new ResponseContent(),
                new ResponseConnControl()
        });

        BufferingHttpServiceHandler serviceHandler = new BufferingHttpServiceHandler(
                serverHttpProc,
                new DefaultHttpResponseFactory(),
                new DefaultConnectionReuseStrategy(),
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

        BufferingHttpClientHandler clientHandler = new BufferingHttpClientHandler(
                clientHttpProc,
                requestExecutionHandler,
                new DefaultConnectionReuseStrategy(),
                this.client.getParams());

        this.server.setExceptionHandler(exceptionHandler);

        this.server.start(serviceHandler);
        this.client.start(clientHandler);

        ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();
        InetSocketAddress serverAddress = (InetSocketAddress) endpoint.getAddress();

        this.client.openConnection(
                new InetSocketAddress("localhost", serverAddress.getPort()),
                null);

        requestConns.await();
        assertEquals(0, requestConns.getCount());

        this.server.join(1000);

        assertEquals(IOReactorStatus.ACTIVE, this.server.getStatus());
        assertNull(this.server.getException());

        this.client.shutdown();
        this.server.shutdown();
    }

}
