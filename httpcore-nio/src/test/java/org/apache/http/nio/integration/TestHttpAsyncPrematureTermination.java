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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.http.Consts;
import org.apache.http.HttpConnection;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.nio.protocol.BasicAsyncRequestConsumer;
import org.apache.http.nio.protocol.BasicAsyncResponseProducer;
import org.apache.http.nio.protocol.HttpAsyncExchange;
import org.apache.http.nio.protocol.HttpAsyncRequestConsumer;
import org.apache.http.nio.protocol.HttpAsyncRequestHandler;
import org.apache.http.nio.reactor.ListenerEndpoint;
import org.apache.http.nio.testserver.HttpCoreNIOTestBase;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestHttpAsyncPrematureTermination extends HttpCoreNIOTestBase {

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

    private InetSocketAddress start() throws Exception {
        this.server.start();
        this.client.start();

        final ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();

        return (InetSocketAddress) endpoint.getAddress();
    }

    @Test
    public void testConnectionTerminatedProcessingRequest() throws Exception {
        this.server.registerHandler("*", new HttpAsyncRequestHandler<HttpRequest>() {

            @Override
            public HttpAsyncRequestConsumer<HttpRequest> processRequest(
                    final HttpRequest request,
                    final HttpContext context) throws HttpException, IOException {
                final HttpConnection conn = (HttpConnection) context.getAttribute(
                        HttpCoreContext.HTTP_CONNECTION);
                conn.shutdown();
                return new BasicAsyncRequestConsumer();
            }

            @Override
            public void handle(
                    final HttpRequest request,
                    final HttpAsyncExchange httpExchange,
                    final HttpContext context) throws HttpException, IOException {
                final HttpResponse response = httpExchange.getResponse();
                response.setEntity(new NStringEntity("all is well", ContentType.TEXT_PLAIN));
                httpExchange.submitResponse();
            }

        });
        final InetSocketAddress address = start();
        final HttpHost target = new HttpHost("localhost", address.getPort());

        final CountDownLatch latch = new CountDownLatch(1);

        final FutureCallback<HttpResponse> callback = new FutureCallback<HttpResponse>() {

            @Override
            public void cancelled() {
                latch.countDown();
            }

            @Override
            public void failed(final Exception ex) {
                latch.countDown();
            }

            @Override
            public void completed(final HttpResponse response) {
                Assert.fail();
            }

        };

        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final HttpContext context = new BasicHttpContext();
        this.client.execute(target, request, context, callback);

        Assert.assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void testConnectionTerminatedHandlingRequest() throws Exception {
        final CountDownLatch responseStreamClosed = new CountDownLatch(1);
        final InputStream testInputStream = new ByteArrayInputStream(
                "all is well".getBytes(Consts.ASCII)) {
            @Override
            public void close() throws IOException {
                responseStreamClosed.countDown();
                super.close();
            }
        };
        this.server.registerHandler("*", new HttpAsyncRequestHandler<HttpRequest>() {

            @Override
            public HttpAsyncRequestConsumer<HttpRequest> processRequest(
                    final HttpRequest request,
                    final HttpContext context) throws HttpException, IOException {
                return new BasicAsyncRequestConsumer();
            }

            @Override
            public void handle(
                    final HttpRequest request,
                    final HttpAsyncExchange httpExchange,
                    final HttpContext context) throws HttpException, IOException {
                final HttpConnection conn = (HttpConnection) context.getAttribute(
                        HttpCoreContext.HTTP_CONNECTION);
                conn.shutdown();
                final HttpResponse response = httpExchange.getResponse();
                response.setEntity(new InputStreamEntity(testInputStream, -1));
                httpExchange.submitResponse();
            }

        });
        final InetSocketAddress address = start();
        final HttpHost target = new HttpHost("localhost", address.getPort());

        final CountDownLatch latch = new CountDownLatch(1);

        final FutureCallback<HttpResponse> callback = new FutureCallback<HttpResponse>() {

            @Override
            public void cancelled() {
                latch.countDown();
            }

            @Override
            public void failed(final Exception ex) {
                latch.countDown();
            }

            @Override
            public void completed(final HttpResponse response) {
                Assert.fail();
            }

        };

        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final HttpContext context = new BasicHttpContext();
        this.client.execute(target, request, context, callback);

        Assert.assertTrue(latch.await(5, TimeUnit.SECONDS));
        Assert.assertTrue(responseStreamClosed.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void testConnectionTerminatedSendingResponse() throws Exception {
        this.server.registerHandler("*", new HttpAsyncRequestHandler<HttpRequest>() {

            @Override
            public HttpAsyncRequestConsumer<HttpRequest> processRequest(
                    final HttpRequest request,
                    final HttpContext context) throws HttpException, IOException {
                return new BasicAsyncRequestConsumer();
            }

            @Override
            public void handle(
                    final HttpRequest request,
                    final HttpAsyncExchange httpExchange,
                    final HttpContext context) throws HttpException, IOException {
                final HttpResponse response = httpExchange.getResponse();
                response.setEntity(new NStringEntity("all is well", ContentType.TEXT_PLAIN));
                httpExchange.submitResponse(new BasicAsyncResponseProducer(response) {

                    @Override
                    public synchronized void produceContent(
                            final ContentEncoder encoder,
                            final IOControl ioctrl) throws IOException {
                        ioctrl.shutdown();
                    }

                });
            }

        });
        final InetSocketAddress address = start();
        final HttpHost target = new HttpHost("localhost", address.getPort());

        final CountDownLatch latch = new CountDownLatch(1);

        final FutureCallback<HttpResponse> callback = new FutureCallback<HttpResponse>() {

            @Override
            public void cancelled() {
                latch.countDown();
            }

            @Override
            public void failed(final Exception ex) {
                latch.countDown();
            }

            @Override
            public void completed(final HttpResponse response) {
                Assert.fail();
            }

        };

        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final HttpContext context = new BasicHttpContext();
        this.client.execute(target, request, context, callback);

        Assert.assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

}
