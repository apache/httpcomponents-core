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

import org.apache.http.HttpCoreNIOTestBase;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.LoggingClientConnectionFactory;
import org.apache.http.LoggingSSLClientConnectionFactory;
import org.apache.http.SSLTestContexts;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.DefaultNHttpClientConnection;
import org.apache.http.impl.nio.DefaultNHttpServerConnection;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.nio.NHttpConnectionFactory;
import org.apache.http.nio.protocol.BasicAsyncRequestProducer;
import org.apache.http.nio.protocol.BasicAsyncResponseConsumer;
import org.apache.http.nio.protocol.HttpAsyncRequestExecutor;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class TestHttpsAsyncTimeout extends HttpCoreNIOTestBase {

    private ServerSocket serverSocket;

    @Before
    public void setUp() throws Exception {
        initClient();
        initConnPool();
    }

    @After
    public void tearDown() throws Exception {
        serverSocket.close();
        shutDownClient();
    }

    @Override
    protected NHttpConnectionFactory<DefaultNHttpServerConnection> createServerConnectionFactory(
            final HttpParams params) throws Exception {
        return null;
    }

    @Override
    protected NHttpConnectionFactory<DefaultNHttpClientConnection> createClientConnectionFactory(
            final HttpParams params) throws Exception {
        return new LoggingClientConnectionFactory(params);
    }

    @Override
    protected NHttpConnectionFactory<DefaultNHttpClientConnection> createClientSSLConnectionFactory(
            final HttpParams params) throws Exception {

        return new LoggingSSLClientConnectionFactory(SSLTestContexts.createClientSSLContext(), params);
    }

    private InetSocketAddress start() throws Exception {

        HttpAsyncRequestExecutor clientHandler = new HttpAsyncRequestExecutor();
        this.client.start(clientHandler);
        serverSocket = new ServerSocket(0);
        return new InetSocketAddress(serverSocket.getInetAddress(), serverSocket.getLocalPort());
    }

    @Test
    public void testHandshakeTimeout() throws Exception {
        // This test creates a server socket and accepts the incoming
        // socket connection without reading any data.  The client should
        // connect, be unable to progress through the handshake, and then
        // time out when SO_TIMEOUT has elapsed.

        InetSocketAddress address = start();
        HttpHost target = new HttpHost("localhost", address.getPort(), "https");

        final CountDownLatch latch = new CountDownLatch(1);

        FutureCallback<HttpResponse> callback = new FutureCallback<HttpResponse>() {

            public void cancelled() {
                latch.countDown();
            }

            public void failed(final Exception ex) {
                latch.countDown();
            }

            public void completed(final HttpResponse response) {
                Assert.fail();
            }

        };

        HttpRequest request = new BasicHttpRequest("GET", "/");
        HttpContext context = new BasicHttpContext();
        this.clientParams.setParameter(CoreConnectionPNames.SO_TIMEOUT, 2000);
        this.executor.execute(
                new BasicAsyncRequestProducer(target, request),
                new BasicAsyncResponseConsumer(),
                this.connpool, context, callback);
        Socket accepted = serverSocket.accept();
        try {
            Assert.assertTrue(latch.await(10, TimeUnit.SECONDS));
        } finally {
            accepted.close();
        }
    }

}
