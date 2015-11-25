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

package org.apache.hc.core5.http.integration;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.concurrent.Cancellable;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.entity.BasicHttpEntity;
import org.apache.hc.core5.http.entity.ContentType;
import org.apache.hc.core5.http.impl.nio.BasicAsyncRequestConsumer;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.ContentEncoder;
import org.apache.hc.core5.http.nio.HttpAsyncExchange;
import org.apache.hc.core5.http.nio.HttpAsyncRequestConsumer;
import org.apache.hc.core5.http.nio.HttpAsyncRequestHandler;
import org.apache.hc.core5.http.nio.HttpAsyncResponseProducer;
import org.apache.hc.core5.http.nio.IOControl;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.testserver.nio.HttpCoreNIOTestBase;
import org.apache.hc.core5.reactor.ListenerEndpoint;
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

    @Test
    public void testResponsePrematureTermination() throws Exception {

        final CountDownLatch latch = new CountDownLatch(1);
        final HttpAsyncResponseProducer responseProducer = new HttpAsyncResponseProducer() {

            @Override
            public HttpResponse generateResponse() {
                final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
                final BasicHttpEntity entity = new BasicHttpEntity();
                entity.setContentType(ContentType.DEFAULT_BINARY.toString());
                entity.setChunked(true);
                response.setEntity(entity);
                return response;
            }

            @Override
            public void close() throws IOException {
                latch.countDown();
            }

            @Override
            public void responseCompleted(final HttpContext context) {
            }

            @Override
            public void produceContent(
                    final ContentEncoder encoder, final IOControl ioctrl) throws IOException {
                // suspend output
                ioctrl.suspendOutput();
            }

            @Override
            public void failed(final Exception ex) {
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
                    final HttpRequest data,
                    final HttpAsyncExchange httpExchange,
                    final HttpContext context)
                    throws HttpException, IOException {
                httpExchange.submitResponse(responseProducer);
            }

        });
        this.server.start();

        final ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();

        final InetSocketAddress address = (InetSocketAddress) endpoint.getAddress();
        try (final Socket socket = new Socket("localhost", address.getPort())) {
            final OutputStream outstream = socket.getOutputStream();
            final BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outstream, "US-ASCII"));
            writer.write("GET /long HTTP/1.1\r\n");
            writer.write("Host: localhost\r\n");
            writer.write("\r\n");
            writer.flush();

            Thread.sleep(250);

            writer.close();
        }

        Assert.assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void testRequestCancelled() throws Exception {

        final CountDownLatch latch = new CountDownLatch(1);
        final Cancellable cancellable = new Cancellable() {

            @Override
            public boolean cancel() {
                latch.countDown();
                return true;
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
                    final HttpRequest data,
                    final HttpAsyncExchange httpExchange,
                    final HttpContext context)
                    throws HttpException, IOException {
                httpExchange.setCallback(cancellable);
                // do not submit a response;
            }

        });
        this.server.start();

        final ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();

        final InetSocketAddress address = (InetSocketAddress) endpoint.getAddress();
        try (final Socket socket = new Socket("localhost", address.getPort())) {
            final OutputStream outstream = socket.getOutputStream();
            final BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outstream, "US-ASCII"));
            writer.write("GET /long HTTP/1.1\r\n");
            writer.write("Host: localhost\r\n");
            writer.write("\r\n");
            writer.flush();

            Thread.sleep(250);

            writer.close();
        }

        Assert.assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

}
