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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.concurrent.Cancellable;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.nio.DefaultNHttpClientConnection;
import org.apache.http.impl.nio.DefaultNHttpServerConnection;
import org.apache.http.nio.NHttpConnectionFactory;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.nio.protocol.BasicAsyncRequestConsumer;
import org.apache.http.nio.protocol.BasicAsyncRequestHandler;
import org.apache.http.nio.protocol.HttpAsyncExchange;
import org.apache.http.nio.protocol.HttpAsyncRequestConsumer;
import org.apache.http.nio.protocol.HttpAsyncRequestHandler;
import org.apache.http.nio.protocol.HttpAsyncService;
import org.apache.http.nio.protocol.UriHttpAsyncRequestHandlerMapper;
import org.apache.http.nio.reactor.IOReactorStatus;
import org.apache.http.nio.reactor.ListenerEndpoint;
import org.apache.http.nio.testserver.HttpCoreNIOTestBase;
import org.apache.http.nio.testserver.LoggingClientConnectionFactory;
import org.apache.http.nio.testserver.LoggingServerConnectionFactory;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.ImmutableHttpProcessor;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.protocol.ResponseServer;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for handling pipelined requests.
 */
public class TestPipelining extends HttpCoreNIOTestBase {

    protected HttpProcessor serverHttpProc;

    @Before
    public void setUp() throws Exception {
        initServer();
        this.serverHttpProc = new ImmutableHttpProcessor(new HttpResponseInterceptor[] {
                new ResponseServer("TEST-SERVER/1.1"),
                new ResponseContent(),
                new ResponseConnControl()
        });
    }

    @After
    public void tearDown() throws Exception {
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
    public void testBasicPipelining() throws Exception {
        final UriHttpAsyncRequestHandlerMapper registry = new UriHttpAsyncRequestHandlerMapper();
        registry.register("*", new BasicAsyncRequestHandler(new HttpRequestHandler() {

            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                final String content = "thank you very much";
                final NStringEntity entity = new NStringEntity(content, ContentType.DEFAULT_TEXT);
                response.setEntity(entity);
            }

        }));
        final HttpAsyncService serviceHandler = new HttpAsyncService(this.serverHttpProc, registry);
        this.server.start(serviceHandler);

        final ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();

        Assert.assertEquals("Test server status", IOReactorStatus.ACTIVE, this.server.getStatus());

        final InetSocketAddress address = (InetSocketAddress) endpoint.getAddress();
        final Socket socket = new Socket("localhost", address.getPort());
        try {
            final OutputStream outstream = socket.getOutputStream();
            final BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outstream, "US-ASCII"));
            writer.write("GET / HTTP/1.1\r\n");
            writer.write("Host: localhost\r\n");
            writer.write("\r\n");
            writer.write("GET / HTTP/1.1\r\n");
            writer.write("Host: localhost\r\n");
            writer.write("Connection: close\r\n");
            writer.write("\r\n");
            writer.flush();
            final InputStream instream = socket.getInputStream();
            final BufferedReader reader = new BufferedReader(new InputStreamReader(instream, "US-ASCII"));
            final StringBuilder buf = new StringBuilder();
            final char[] tmp = new char[1024];
            int l;
            while ((l = reader.read(tmp)) != -1) {
                buf.append(tmp, 0, l);
            }
            reader.close();
            writer.close();
//            String expected =
//                    "HTTP/1.1 200 OK\r\n" +
//                    "Server: TEST-SERVER/1.1\r\n" +
//                    "Content-Length: 19\r\n" +
//                    "Content-Type: text/plain; charset=ISO-8859-1\r\n" +
//                    "\r\n" +
//                    "thank you very much" +
//                    "HTTP/1.1 200 OK\r\n" +
//                    "Server: TEST-SERVER/1.1\r\n" +
//                    "Content-Length: 19\r\n" +
//                    "Content-Type: text/plain; charset=ISO-8859-1\r\n" +
//                    "Connection: close\r\n" +
//                    "\r\n" +
//                    "thank you very much";
            final String expected =
                    "HTTP/1.1 400 Bad Request\r\n" +
                    "Connection: Close\r\n" +
                    "Server: TEST-SERVER/1.1\r\n" +
                    "Content-Length: 70\r\n" +
                    "Content-Type: text/plain; charset=ISO-8859-1\r\n" +
                    "\r\n" +
                    "Out of sequence request message detected (pipelining is not supported)";
            Assert.assertEquals(expected, buf.toString());

        } finally {
            socket.close();
        }

    }

    @Test
    public void testPipeliningWithCancellable() throws Exception {

        final CountDownLatch latch = new CountDownLatch(1);
        final Cancellable cancellable = new Cancellable() {

            public boolean cancel() {
                latch.countDown();
                return true;
            }
        };

        final UriHttpAsyncRequestHandlerMapper registry = new UriHttpAsyncRequestHandlerMapper();
        registry.register("/long", new HttpAsyncRequestHandler<HttpRequest>() {

            public HttpAsyncRequestConsumer<HttpRequest> processRequest(final HttpRequest request,
                    final HttpContext context) {
                return new BasicAsyncRequestConsumer();
            }

            public void handle(
                    final HttpRequest request,
                    final HttpAsyncExchange httpexchange,
                    final HttpContext context) throws HttpException, IOException {
                httpexchange.setCallback(cancellable);
                // do not submit a response;
            }

        });
        registry.register("/short", new BasicAsyncRequestHandler(new HttpRequestHandler() {

            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                final String content = "thank you very much";
                final NStringEntity entity = new NStringEntity(content, ContentType.DEFAULT_TEXT);
                response.setEntity(entity);
            }

        }));
        final HttpAsyncService serviceHandler = new HttpAsyncService(this.serverHttpProc, registry);
        this.server.start(serviceHandler);

        final ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();

        Assert.assertEquals("Test server status", IOReactorStatus.ACTIVE, this.server.getStatus());

        final InetSocketAddress address = (InetSocketAddress) endpoint.getAddress();
        final Socket socket = new Socket("localhost", address.getPort());
        try {
            final OutputStream outstream = socket.getOutputStream();
            final BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outstream, "US-ASCII"));
            writer.write("GET /long HTTP/1.1\r\n");
            writer.write("Host: localhost\r\n");
            writer.write("\r\n");
            writer.write("GET /short HTTP/1.1\r\n");
            writer.write("Host: localhost\r\n");
            writer.write("Connection: close\r\n");
            writer.write("\r\n");
            writer.flush();
            final InputStream instream = socket.getInputStream();
            final BufferedReader reader = new BufferedReader(new InputStreamReader(instream, "US-ASCII"));
            final StringBuilder buf = new StringBuilder();
            final char[] tmp = new char[1024];
            int l;
            while ((l = reader.read(tmp)) != -1) {
                buf.append(tmp, 0, l);
            }
            reader.close();
            writer.close();

            final String expected =
                    "HTTP/1.1 400 Bad Request\r\n" +
                    "Connection: Close\r\n" +
                    "Server: TEST-SERVER/1.1\r\n" +
                    "Content-Length: 70\r\n" +
                    "Content-Type: text/plain; charset=ISO-8859-1\r\n" +
                    "\r\n" +
                    "Out of sequence request message detected (pipelining is not supported)";
            Assert.assertEquals(expected, buf.toString());

        } finally {
            socket.close();
        }

        Assert.assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

}
