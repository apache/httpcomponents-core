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

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpCoreNIOTestBase;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.LoggingClientConnectionFactory;
import org.apache.http.LoggingServerConnectionFactory;
import org.apache.http.concurrent.Cancellable;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.nio.DefaultNHttpClientConnection;
import org.apache.http.impl.nio.DefaultNHttpServerConnection;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.NHttpConnectionFactory;
import org.apache.http.nio.protocol.BasicAsyncRequestConsumer;
import org.apache.http.nio.protocol.HttpAsyncExchange;
import org.apache.http.nio.protocol.HttpAsyncRequestConsumer;
import org.apache.http.nio.protocol.HttpAsyncRequestHandler;
import org.apache.http.nio.protocol.HttpAsyncRequestHandlerRegistry;
import org.apache.http.nio.protocol.HttpAsyncResponseProducer;
import org.apache.http.nio.protocol.HttpAsyncService;
import org.apache.http.nio.reactor.IOReactorStatus;
import org.apache.http.nio.reactor.ListenerEndpoint;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestHttpAsyncHandlerCancellable extends HttpCoreNIOTestBase {

    @Before
    public void setUp() throws Exception {
        initServer();
    }

    @After
    public void tearDown() throws Exception {
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
    public void testResponsePrematureTermination() throws Exception {
        
        final CountDownLatch latch = new CountDownLatch(1);
        final HttpAsyncResponseProducer responseProducer = new HttpAsyncResponseProducer() {
            
            public HttpResponse generateResponse() {
                HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
                BasicHttpEntity entity = new BasicHttpEntity();
                entity.setContentType(ContentType.DEFAULT_BINARY.toString());
                entity.setChunked(true);
                response.setEntity(entity);
                return response;
            }
            
            public void close() throws IOException {
                latch.countDown();
            }
            
            public void responseCompleted(final HttpContext context) {
            }
            
            public void produceContent(
                    final ContentEncoder encoder, final IOControl ioctrl) throws IOException {
                // suspend output
                ioctrl.suspendOutput();
            }
            
            public void failed(final Exception ex) {
            }

        };
        
        HttpAsyncRequestHandlerRegistry registry = new HttpAsyncRequestHandlerRegistry();
        registry.register("*", new HttpAsyncRequestHandler<HttpRequest>() {

            public HttpAsyncRequestConsumer<HttpRequest> processRequest(
                    final HttpRequest request,
                    final HttpContext context) throws HttpException, IOException {
                return new BasicAsyncRequestConsumer();
            }

            public void handle(
                    final HttpRequest data, 
                    final HttpAsyncExchange httpExchange, 
                    final HttpContext context)
                    throws HttpException, IOException {
                httpExchange.submitResponse(responseProducer);
            }
            
        });
        HttpAsyncService serviceHandler = new HttpAsyncService(
                this.serverHttpProc,
                new DefaultConnectionReuseStrategy(),
                new DefaultHttpResponseFactory(),
                registry,
                null,
                this.serverParams);
        this.server.start(serviceHandler);

        ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();

        Assert.assertEquals("Test server status", IOReactorStatus.ACTIVE, this.server.getStatus());
        InetSocketAddress address = (InetSocketAddress) endpoint.getAddress();
        Socket socket = new Socket("localhost", address.getPort());
        try {
            OutputStream outstream = socket.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outstream, "US-ASCII"));
            writer.write("GET /long HTTP/1.1\r\n");
            writer.write("Host: localhost\r\n");
            writer.write("\r\n");
            writer.flush();
            
            Thread.sleep(250);
            
            writer.close();
        } finally {
            socket.close();
        }

        Assert.assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void testRequestCancelled() throws Exception {
        
        final CountDownLatch latch = new CountDownLatch(1);
        final Cancellable cancellable = new Cancellable() {
            
            public boolean cancel() {
                latch.countDown();
                return true;
            }
        };
        
        HttpAsyncRequestHandlerRegistry registry = new HttpAsyncRequestHandlerRegistry();
        registry.register("*", new HttpAsyncRequestHandler<HttpRequest>() {

            public HttpAsyncRequestConsumer<HttpRequest> processRequest(
                    final HttpRequest request,
                    final HttpContext context) throws HttpException, IOException {
                return new BasicAsyncRequestConsumer();
            }

            public void handle(
                    final HttpRequest data, 
                    final HttpAsyncExchange httpExchange, 
                    final HttpContext context)
                    throws HttpException, IOException {
                httpExchange.setCallback(cancellable);
                // do not submit a response;
            }
            
        });
        HttpAsyncService serviceHandler = new HttpAsyncService(
                this.serverHttpProc,
                new DefaultConnectionReuseStrategy(),
                new DefaultHttpResponseFactory(),
                registry,
                null,
                this.serverParams);
        this.server.start(serviceHandler);

        ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();

        Assert.assertEquals("Test server status", IOReactorStatus.ACTIVE, this.server.getStatus());
        InetSocketAddress address = (InetSocketAddress) endpoint.getAddress();
        Socket socket = new Socket("localhost", address.getPort());
        try {
            OutputStream outstream = socket.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outstream, "US-ASCII"));
            writer.write("GET /long HTTP/1.1\r\n");
            writer.write("Host: localhost\r\n");
            writer.write("\r\n");
            writer.flush();
            
            Thread.sleep(250);
            
            writer.close();
        } finally {
            socket.close();
        }

        Assert.assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

}
