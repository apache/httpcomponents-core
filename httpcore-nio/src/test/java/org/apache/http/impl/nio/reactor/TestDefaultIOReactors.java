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

import org.apache.http.HttpCoreNIOTestBase;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.LoggingClientConnectionFactory;
import org.apache.http.LoggingServerConnectionFactory;
import org.apache.http.OoopsieRuntimeException;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.nio.DefaultNHttpClientConnection;
import org.apache.http.impl.nio.DefaultNHttpServerConnection;
import org.apache.http.impl.nio.pool.BasicNIOPoolEntry;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.nio.NHttpClientConnection;
import org.apache.http.nio.NHttpConnectionFactory;
import org.apache.http.nio.NHttpServerConnection;
import org.apache.http.nio.protocol.BasicAsyncRequestProducer;
import org.apache.http.nio.protocol.BasicAsyncResponseConsumer;
import org.apache.http.nio.protocol.BasicAsyncRequestHandler;
import org.apache.http.nio.protocol.HttpAsyncRequestExecutor;
import org.apache.http.nio.protocol.HttpAsyncRequestHandlerRegistry;
import org.apache.http.nio.protocol.HttpAsyncService;
import org.apache.http.nio.reactor.IOReactorException;
import org.apache.http.nio.reactor.IOReactorExceptionHandler;
import org.apache.http.nio.reactor.IOReactorStatus;
import org.apache.http.nio.reactor.ListenerEndpoint;
import org.apache.http.params.HttpParams;
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

    @Test
    public void testGracefulShutdown() throws Exception {
        final int connNo = 10;
        final CountDownLatch openServerConns = new CountDownLatch(connNo);
        final CountDownLatch openClientConns = new CountDownLatch(connNo);
        final AtomicInteger closedClientConns = new AtomicInteger(0);
        final AtomicInteger closedServerConns = new AtomicInteger(0);

        this.connpool.setDefaultMaxPerRoute(connNo);
        this.connpool.setMaxTotal(connNo);

        HttpAsyncRequestHandlerRegistry registry = new HttpAsyncRequestHandlerRegistry();
        HttpAsyncService serviceHandler = new HttpAsyncService(
                this.serverHttpProc,
                new DefaultConnectionReuseStrategy(),
                registry,
                this.serverParams) {

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
        HttpAsyncRequestExecutor clientHandler = new HttpAsyncRequestExecutor() {

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

        ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();
        InetSocketAddress address = (InetSocketAddress) endpoint.getAddress();
        HttpHost target = new HttpHost("localhost", address.getPort());

        Assert.assertEquals("Test server status", IOReactorStatus.ACTIVE, this.server.getStatus());

        Queue<Future<BasicNIOPoolEntry>> queue = new LinkedList<Future<BasicNIOPoolEntry>>();
        for (int i = 0; i < connNo; i++) {
            queue.add(this.connpool.lease(target, null));
        }

        while (!queue.isEmpty()) {
            Future<BasicNIOPoolEntry> future = queue.remove();
            BasicNIOPoolEntry poolEntry = future.get();
            Assert.assertNotNull(poolEntry);
        }

        Assert.assertEquals("Test client status", IOReactorStatus.ACTIVE, this.client.getStatus());

        openClientConns.await(15, TimeUnit.SECONDS);
        openServerConns.await(15, TimeUnit.SECONDS);

        this.connpool.shutdown(2000);
        this.client.shutdown();
        this.server.shutdown();

        Assert.assertEquals("Client connections that should have been closed", connNo, closedClientConns.get());
        Assert.assertEquals("Server connections that should have been closed", connNo, closedServerConns.get());
    }

    @Test
    public void testRuntimeException() throws Exception {

        HttpRequestHandler requestHandler = new HttpRequestHandler() {

            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                throw new OoopsieRuntimeException();
            }

        };

        HttpAsyncRequestHandlerRegistry registry = new HttpAsyncRequestHandlerRegistry();
        registry.register("*", new BasicAsyncRequestHandler(requestHandler));
        HttpAsyncService serviceHandler = new HttpAsyncService(
                this.serverHttpProc,
                new DefaultConnectionReuseStrategy(),
                registry,
                this.serverParams) {

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
        HttpAsyncRequestExecutor clientHandler = new HttpAsyncRequestExecutor();
        this.server.start(serviceHandler);
        this.client.start(clientHandler);

        ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();
        InetSocketAddress address = (InetSocketAddress) endpoint.getAddress();
        HttpHost target = new HttpHost("localhost", address.getPort());

        Assert.assertEquals("Test server status", IOReactorStatus.ACTIVE, this.server.getStatus());

        BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        this.executor.execute(
                new BasicAsyncRequestProducer(target, request),
                new BasicAsyncResponseConsumer(),
                this.connpool);

        this.server.join(20000);

        Exception ex = this.server.getException();
        Assert.assertNotNull(ex);
        Assert.assertTrue(ex instanceof IOReactorException);
        Assert.assertNotNull(ex.getCause());
        Assert.assertTrue(ex.getCause() instanceof OoopsieRuntimeException);

        List<ExceptionEvent> auditlog = this.server.getAuditLog();
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

        HttpRequestHandler requestHandler = new HttpRequestHandler() {

            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                throw new OoopsieRuntimeException();
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

        HttpAsyncRequestHandlerRegistry registry = new HttpAsyncRequestHandlerRegistry();
        registry.register("*", new BasicAsyncRequestHandler(requestHandler));
        HttpAsyncService serviceHandler = new HttpAsyncService(
                this.serverHttpProc,
                new DefaultConnectionReuseStrategy(),
                registry,
                this.serverParams) {

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
        HttpAsyncRequestExecutor clientHandler = new HttpAsyncRequestExecutor();
        this.server.setExceptionHandler(exceptionHandler);
        this.server.start(serviceHandler);
        this.client.start(clientHandler);

        ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();
        InetSocketAddress address = (InetSocketAddress) endpoint.getAddress();
        HttpHost target = new HttpHost("localhost", address.getPort());

        Assert.assertEquals("Test server status", IOReactorStatus.ACTIVE, this.server.getStatus());

        BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        this.executor.execute(
                new BasicAsyncRequestProducer(target, request),
                new BasicAsyncResponseConsumer(),
                this.connpool);

        this.server.join(20000);

        Exception ex = this.server.getException();
        Assert.assertNotNull(ex);
        Assert.assertTrue(ex instanceof IOReactorException);
        Assert.assertNotNull(ex.getCause());
        Assert.assertTrue(ex.getCause() instanceof OoopsieRuntimeException);

        List<ExceptionEvent> auditlog = this.server.getAuditLog();
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

        HttpRequestHandler requestHandler = new HttpRequestHandler() {

            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                throw new OoopsieRuntimeException();
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

        HttpAsyncRequestHandlerRegistry registry = new HttpAsyncRequestHandlerRegistry();
        registry.register("*", new BasicAsyncRequestHandler(requestHandler));
        HttpAsyncService serviceHandler = new HttpAsyncService(
                this.serverHttpProc,
                new DefaultConnectionReuseStrategy(),
                registry,
                this.serverParams) {

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
        HttpAsyncRequestExecutor clientHandler = new HttpAsyncRequestExecutor();
        this.server.setExceptionHandler(exceptionHandler);
        this.server.start(serviceHandler);
        this.client.start(clientHandler);

        ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();
        InetSocketAddress address = (InetSocketAddress) endpoint.getAddress();
        HttpHost target = new HttpHost("localhost", address.getPort());

        Assert.assertEquals("Test server status", IOReactorStatus.ACTIVE, this.server.getStatus());

        BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        this.executor.execute(
                new BasicAsyncRequestProducer(target, request),
                new BasicAsyncResponseConsumer(),
                this.connpool);

        requestConns.await();
        Assert.assertEquals(0, requestConns.getCount());

        this.server.join(1000);

        Assert.assertEquals(IOReactorStatus.ACTIVE, this.server.getStatus());
        Assert.assertNull(this.server.getException());

        this.client.shutdown();
        this.server.shutdown();
    }

}
