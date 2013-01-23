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
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.OoopsieRuntimeException;
import org.apache.http.impl.nio.DefaultNHttpClientConnection;
import org.apache.http.impl.nio.DefaultNHttpServerConnection;
import org.apache.http.impl.nio.pool.BasicNIOPoolEntry;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.nio.NHttpClientConnection;
import org.apache.http.nio.NHttpConnectionFactory;
import org.apache.http.nio.NHttpServerConnection;
import org.apache.http.nio.protocol.BasicAsyncRequestHandler;
import org.apache.http.nio.protocol.HttpAsyncRequestExecutor;
import org.apache.http.nio.protocol.HttpAsyncService;
import org.apache.http.nio.protocol.UriHttpAsyncRequestHandlerMapper;
import org.apache.http.nio.reactor.IOReactorException;
import org.apache.http.nio.reactor.IOReactorExceptionHandler;
import org.apache.http.nio.reactor.IOReactorStatus;
import org.apache.http.nio.reactor.ListenerEndpoint;
import org.apache.http.nio.testserver.HttpCoreNIOTestBase;
import org.apache.http.nio.testserver.HttpServerNio;
import org.apache.http.nio.testserver.LoggingClientConnectionFactory;
import org.apache.http.nio.testserver.LoggingServerConnectionFactory;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for basic I/O functionality.
 */
public class TestDefaultIOReactors extends HttpCoreNIOTestBase {

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
    protected NHttpConnectionFactory<DefaultNHttpServerConnection> createServerConnectionFactory() throws Exception {
        return new LoggingServerConnectionFactory();
    }

    @Override
    protected NHttpConnectionFactory<DefaultNHttpClientConnection> createClientConnectionFactory() throws Exception {
        return new LoggingClientConnectionFactory();
    }

    @Test
    public void testGracefulShutdown() throws Exception {
        final int connNo = 10;
        final CountDownLatch openServerConns = new CountDownLatch(connNo);
        final CountDownLatch openClientConns = new CountDownLatch(connNo);
        final AtomicInteger closedClientConns = new AtomicInteger(0);
        final AtomicInteger closedServerConns = new AtomicInteger(0);

        this.client.setMaxPerRoute(connNo);
        this.client.setMaxTotal(connNo);

        final UriHttpAsyncRequestHandlerMapper registry = new UriHttpAsyncRequestHandlerMapper();
        final HttpAsyncService serviceHandler = new HttpAsyncService(
                HttpServerNio.DEFAULT_HTTP_PROC, registry) {

            @Override
            public void connected(final NHttpServerConnection conn) {
                openServerConns.countDown();
                super.connected(conn);
            }

            @Override
            public void closed(final NHttpServerConnection conn) {
                closedServerConns.incrementAndGet();
                super.closed(conn);
            }

        };
        final HttpAsyncRequestExecutor clientHandler = new HttpAsyncRequestExecutor() {

            @Override
            public void connected(
                    final NHttpClientConnection conn,
                    final Object attachment) throws IOException, HttpException {
                openClientConns.countDown();
                super.connected(conn, attachment);
            }

            @Override
            public void closed(final NHttpClientConnection conn) {
                closedClientConns.incrementAndGet();
                super.closed(conn);
            }

        };
        this.server.start(serviceHandler);
        this.client.start(clientHandler);

        final ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();
        final InetSocketAddress address = (InetSocketAddress) endpoint.getAddress();
        final HttpHost target = new HttpHost("localhost", address.getPort());

        Assert.assertEquals("Test server status", IOReactorStatus.ACTIVE, this.server.getStatus());

        final Queue<Future<BasicNIOPoolEntry>> queue = new LinkedList<Future<BasicNIOPoolEntry>>();
        for (int i = 0; i < connNo; i++) {
            queue.add(this.client.lease(target, null));
        }

        while (!queue.isEmpty()) {
            final Future<BasicNIOPoolEntry> future = queue.remove();
            final BasicNIOPoolEntry poolEntry = future.get();
            Assert.assertNotNull(poolEntry);
        }

        Assert.assertEquals("Test client status", IOReactorStatus.ACTIVE, this.client.getStatus());

        openClientConns.await(15, TimeUnit.SECONDS);
        openServerConns.await(15, TimeUnit.SECONDS);

        this.client.shutdown();
        this.server.shutdown();

        Assert.assertEquals("Client connections that should have been closed", connNo, closedClientConns.get());
        Assert.assertEquals("Server connections that should have been closed", connNo, closedServerConns.get());
    }

    @Test
    public void testRuntimeException() throws Exception {

        final HttpRequestHandler requestHandler = new HttpRequestHandler() {

            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                throw new OoopsieRuntimeException();
            }

        };

        final UriHttpAsyncRequestHandlerMapper registry = new UriHttpAsyncRequestHandlerMapper();
        registry.register("*", new BasicAsyncRequestHandler(requestHandler));
        final HttpAsyncService serviceHandler = new HttpAsyncService(
                HttpServerNio.DEFAULT_HTTP_PROC, registry) {

                    @Override
                    public void exception(
                            final NHttpServerConnection conn,
                            final Exception cause) {
                        super.exception(conn, cause);
                        if (cause instanceof RuntimeException) {
                            throw (RuntimeException) cause;
                        }
                    }

        };
        this.server.start(serviceHandler);
        this.client.start();

        final ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();
        final InetSocketAddress address = (InetSocketAddress) endpoint.getAddress();
        final HttpHost target = new HttpHost("localhost", address.getPort());

        Assert.assertEquals("Test server status", IOReactorStatus.ACTIVE, this.server.getStatus());

        final BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        this.client.execute(target, request);

        this.server.join(20000);

        final Exception ex = this.server.getException();
        Assert.assertNotNull(ex);
        Assert.assertTrue(ex instanceof IOReactorException);
        Assert.assertNotNull(ex.getCause());
        Assert.assertTrue(ex.getCause() instanceof OoopsieRuntimeException);

        final List<ExceptionEvent> auditlog = this.server.getAuditLog();
        Assert.assertNotNull(auditlog);
        Assert.assertEquals(1, auditlog.size());

        // I/O reactor shut down itself
        Assert.assertEquals(IOReactorStatus.SHUT_DOWN, this.server.getStatus());

        this.client.shutdown();
        this.server.shutdown();
    }

    @Test
    public void testUnhandledRuntimeException() throws Exception {
        final CountDownLatch requestConns = new CountDownLatch(1);

        final HttpRequestHandler requestHandler = new HttpRequestHandler() {

            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                throw new OoopsieRuntimeException();
            }

        };

        final IOReactorExceptionHandler exceptionHandler = new IOReactorExceptionHandler() {

            public boolean handle(final IOException ex) {
                return false;
            }

            public boolean handle(final RuntimeException ex) {
                requestConns.countDown();
                return false;
            }

        };

        final UriHttpAsyncRequestHandlerMapper registry = new UriHttpAsyncRequestHandlerMapper();
        registry.register("*", new BasicAsyncRequestHandler(requestHandler));
        final HttpAsyncService serviceHandler = new HttpAsyncService(
                HttpServerNio.DEFAULT_HTTP_PROC, registry) {

            @Override
            public void exception(
                    final NHttpServerConnection conn,
                    final Exception cause) {
                super.exception(conn, cause);
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                }
            }

        };
        this.server.setExceptionHandler(exceptionHandler);
        this.server.start(serviceHandler);
        this.client.start();

        final ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();
        final InetSocketAddress address = (InetSocketAddress) endpoint.getAddress();
        final HttpHost target = new HttpHost("localhost", address.getPort());

        Assert.assertEquals("Test server status", IOReactorStatus.ACTIVE, this.server.getStatus());

        final BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        this.client.execute(target, request);

        this.server.join(20000);

        final Exception ex = this.server.getException();
        Assert.assertNotNull(ex);
        Assert.assertTrue(ex instanceof IOReactorException);
        Assert.assertNotNull(ex.getCause());
        Assert.assertTrue(ex.getCause() instanceof OoopsieRuntimeException);

        final List<ExceptionEvent> auditlog = this.server.getAuditLog();
        Assert.assertNotNull(auditlog);
        Assert.assertEquals(1, auditlog.size());

        // I/O reactor shut down itself
        Assert.assertEquals(IOReactorStatus.SHUT_DOWN, this.server.getStatus());

        this.client.shutdown();
        this.server.shutdown();
    }

    @Test
    public void testHandledRuntimeException() throws Exception {
        final CountDownLatch requestConns = new CountDownLatch(1);

        final HttpRequestHandler requestHandler = new HttpRequestHandler() {

            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                throw new OoopsieRuntimeException();
            }

        };

        final IOReactorExceptionHandler exceptionHandler = new IOReactorExceptionHandler() {

            public boolean handle(final IOException ex) {
                return false;
            }

            public boolean handle(final RuntimeException ex) {
                requestConns.countDown();
                return true;
            }

        };

        final UriHttpAsyncRequestHandlerMapper registry = new UriHttpAsyncRequestHandlerMapper();
        registry.register("*", new BasicAsyncRequestHandler(requestHandler));
        final HttpAsyncService serviceHandler = new HttpAsyncService(
                HttpServerNio.DEFAULT_HTTP_PROC, registry) {

            @Override
            public void exception(
                    final NHttpServerConnection conn,
                    final Exception cause) {
                super.exception(conn, cause);
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                }
            }

        };
        this.server.setExceptionHandler(exceptionHandler);
        this.server.start(serviceHandler);
        this.client.start();

        final ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();
        final InetSocketAddress address = (InetSocketAddress) endpoint.getAddress();
        final HttpHost target = new HttpHost("localhost", address.getPort());

        Assert.assertEquals("Test server status", IOReactorStatus.ACTIVE, this.server.getStatus());

        final BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        this.client.execute(target, request);

        requestConns.await();
        Assert.assertEquals(0, requestConns.getCount());

        this.server.join(1000);

        Assert.assertEquals(IOReactorStatus.ACTIVE, this.server.getStatus());
        Assert.assertNull(this.server.getException());

        this.client.shutdown();
        this.server.shutdown();
    }

}
