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

package org.apache.hc.core5.compatibility.http2;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.Queue;
import java.util.StringTokenizer;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.impl.nio.bootstrap.ClientEndpoint;
import org.apache.hc.core5.http.impl.nio.entity.AbstractClassicEntityConsumer;
import org.apache.hc.core5.http.impl.nio.entity.AbstractClassicEntityProducer;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.nio.BasicRequestProducer;
import org.apache.hc.core5.http.nio.BasicResponseConsumer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityProducer;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.testing.nio.http2.Http2TestClient;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class JettyHttp2CompatibilityTest extends JettyServerTestBase {

    private static final long TIMEOUT = 5;

    private Http2TestClient client;

    @Before
    public void setup() throws Exception {
        client = new Http2TestClient(IOReactorConfig.DEFAULT, null);
    }

    @After
    public void cleanup() throws Exception {
        if (client != null) {
            client.shutdown(3, TimeUnit.SECONDS);
        }
    }

    private URI createRequestURI(final URI serverEndpoint, final String path) throws URISyntaxException {
        return new URI(serverEndpoint.getScheme(), serverEndpoint.getAuthority(), path, null, null);
    }

    static class SingleLineResponseHandler extends AbstractHandler {

        private final String message;

        SingleLineResponseHandler(final String message) {
            this.message = message;
        }

        @Override
        public void handle(
                final String target,
                final Request baseRequest,
                final HttpServletRequest request,
                final HttpServletResponse response) throws IOException, ServletException {
            response.setContentType("text/html; charset=utf-8");
            response.setStatus(HttpServletResponse.SC_OK);
            final PrintWriter out = response.getWriter();
            out.print(message);
            out.flush();
            baseRequest.setHandled(true);
        }

    }

    @Test
    public void testSimpleGet() throws Exception {
        final ContextHandler contextHandler = new ContextHandler();
        contextHandler.setHandler(new SingleLineResponseHandler("Hi there"));
        server.setHandler(contextHandler);
        server.start();

        final URI serverEndpoint = server.getURI();

        client.start();
        final Future<ClientEndpoint> connectFuture = client.connect(
                "localhost", serverEndpoint.getPort(), TIMEOUT, TimeUnit.SECONDS);
        final ClientEndpoint streamEndpoint = connectFuture.get();

        final Queue<Future<Message<HttpResponse, String>>> queue = new LinkedList<>();
        for (int i = 0; i < 10; i++) {
            queue.add(streamEndpoint.execute(
                    new BasicRequestProducer("GET", createRequestURI(serverEndpoint, "/hello")),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null));

        }
        while (!queue.isEmpty()) {
            final Future<Message<HttpResponse, String>> future = queue.remove();
            final Message<HttpResponse, String> result = future.get(TIMEOUT, TimeUnit.SECONDS);
            Assert.assertNotNull(result);
            final HttpResponse response = result.getHead();
            final String entity = result.getBody();
            Assert.assertNotNull(response);
            Assert.assertEquals(200, response.getCode());
            Assert.assertEquals("Hi there", entity);
        }
    }

    static class MultiLineResponseHandler extends AbstractHandler {

        private final String message;
        private final int count;

        MultiLineResponseHandler(final String message, final int count) {
            this.message = message;
            this.count = count;
        }

        @Override
        public void handle(
                final String target,
                final Request baseRequest,
                final HttpServletRequest request,
                final HttpServletResponse response) throws IOException, ServletException {
            response.setContentType("text/html; charset=utf-8");
            response.setStatus(HttpServletResponse.SC_OK);
            final PrintWriter out = response.getWriter();
            for (int i = 0; i < count; i++) {
                out.println(message);
            }
            out.flush();
            baseRequest.setHandled(true);
        }

    }

    @Test
    public void testLargeGet() throws Exception {
        final ContextHandler contextHandler = new ContextHandler();
        contextHandler.setHandler(new MultiLineResponseHandler("0123456789abcdef", 5000));
        server.setHandler(contextHandler);
        server.start();

        final URI serverEndpoint = server.getURI();

        client.start();
        final Future<ClientEndpoint> connectFuture = client.connect(
                "localhost", serverEndpoint.getPort(), TIMEOUT, TimeUnit.SECONDS);
        final ClientEndpoint streamEndpoint = connectFuture.get();

        final HttpRequest request1 = new BasicHttpRequest("GET", createRequestURI(serverEndpoint, "/"));
        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                new BasicRequestProducer(request1, null),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);

        final HttpRequest request2 = new BasicHttpRequest("GET", createRequestURI(serverEndpoint, "/"));
        final Future<Message<HttpResponse, String>> future2 = streamEndpoint.execute(
                new BasicRequestProducer(request2, null),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer(512)), null);

        final Message<HttpResponse, String> result1 = future1.get();
        Assert.assertNotNull(result1);
        final HttpResponse response1 = result1.getHead();
        Assert.assertNotNull(response1);
        Assert.assertEquals(200, response1.getCode());
        final String s1 = result1.getBody();
        Assert.assertNotNull(s1);
        final StringTokenizer t1 = new StringTokenizer(s1, "\r\n");
        while (t1.hasMoreTokens()) {
            Assert.assertEquals("0123456789abcdef", t1.nextToken());
        }

        final Message<HttpResponse, String> result2 = future2.get();
        Assert.assertNotNull(result2);
        final HttpResponse response2 = result2.getHead();
        Assert.assertNotNull(response2);
        Assert.assertEquals(200, response2.getCode());
        final String s2 = result2.getBody();
        Assert.assertNotNull(s2);
        final StringTokenizer t2 = new StringTokenizer(s2, "\r\n");
        while (t2.hasMoreTokens()) {
            Assert.assertEquals("0123456789abcdef", t2.nextToken());
        }
    }

    @Test
    public void testBasicPost() throws Exception {
        final ContextHandler contextHandler = new ContextHandler();
        contextHandler.setHandler(new SingleLineResponseHandler("Hi back"));
        server.setHandler(contextHandler);
        server.start();

        final URI serverEndpoint = server.getURI();

        client.start();
        final Future<ClientEndpoint> connectFuture = client.connect(
                "localhost", serverEndpoint.getPort(), TIMEOUT, TimeUnit.SECONDS);
        final ClientEndpoint streamEndpoint = connectFuture.get();

        final Queue<Future<Message<HttpResponse, String>>> queue = new LinkedList<>();
        for (int i = 0; i < 10; i++) {
            final HttpRequest request = new BasicHttpRequest("POST", createRequestURI(serverEndpoint, "/hello"));
            queue.add(streamEndpoint.execute(
                    new BasicRequestProducer(request, new StringAsyncEntityProducer("Hi there", ContentType.TEXT_PLAIN)),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null));

        }
        while (!queue.isEmpty()) {
            final Future<Message<HttpResponse, String>> future = queue.remove();
            final Message<HttpResponse, String> result = future.get(TIMEOUT, TimeUnit.SECONDS);
            Assert.assertNotNull(result);
            final HttpResponse response = result.getHead();
            final String entity1 = result.getBody();
            Assert.assertNotNull(response);
            Assert.assertEquals(200, response.getCode());
            Assert.assertEquals("Hi back", entity1);
        }
    }

    static class EchoHandler extends AbstractHandler {

        @Override
        public void handle(
                final String target,
                final Request baseRequest,
                final HttpServletRequest request,
                final HttpServletResponse response) throws IOException, ServletException {

            response.setContentType("text/html; charset=utf-8");
            response.setStatus(HttpServletResponse.SC_OK);

            final BufferedReader reader = request.getReader();
            final PrintWriter out = response.getWriter();
            String line;
            while ((line = reader.readLine()) != null) {
                out.println(line);
            }
            out.flush();
            baseRequest.setHandled(true);
        }

    }

    @Test
    public void testSlowResponseConsumer() throws Exception {
        final ContextHandler contextHandler = new ContextHandler();
        contextHandler.setHandler(new MultiLineResponseHandler("0123456789abcdef", 3));
        server.setHandler(contextHandler);
        server.start();

        final URI serverEndpoint = server.getURI();

        client = new Http2TestClient();
        client.start(H2Config.custom().setInitialWindowSize(16).build());
        final Future<ClientEndpoint> connectFuture = client.connect(
                "localhost", serverEndpoint.getPort(), TIMEOUT, TimeUnit.SECONDS);
        final ClientEndpoint streamEndpoint = connectFuture.get();

        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                new BasicRequestProducer("GET", createRequestURI(serverEndpoint, "/"), null),
                new BasicResponseConsumer<>(new AbstractClassicEntityConsumer<String>(16, Executors.newSingleThreadExecutor()) {

                    @Override
                    protected String consumeData(
                            final ContentType contentType, final InputStream inputStream) throws IOException {
                        Charset charset = contentType != null ? contentType.getCharset() : null;
                        if (charset == null) {
                            charset = StandardCharsets.US_ASCII;
                        }

                        final StringBuilder buffer = new StringBuilder();
                        try {
                            final byte[] tmp = new byte[16];
                            int l;
                            while ((l = inputStream.read(tmp)) != -1) {
                                buffer.append(charset.decode(ByteBuffer.wrap(tmp, 0, l)));
                                Thread.sleep(500);
                            }
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            throw new InterruptedIOException(ex.getMessage());
                        }
                        return buffer.toString();
                    }
                }),
                null);

        final Message<HttpResponse, String> result1 = future1.get(TIMEOUT, TimeUnit.SECONDS);
        Assert.assertNotNull(result1);
        final HttpResponse response1 = result1.getHead();
        Assert.assertNotNull(response1);
        Assert.assertEquals(200, response1.getCode());
        final String s1 = result1.getBody();
        Assert.assertNotNull(s1);
        final StringTokenizer t1 = new StringTokenizer(s1, "\r\n");
        while (t1.hasMoreTokens()) {
            Assert.assertEquals("0123456789abcdef", t1.nextToken());
        }
    }

    @Test
    public void testSlowRequestProducer() throws Exception {
        final ContextHandler contextHandler = new ContextHandler();
        contextHandler.setHandler(new EchoHandler());
        server.setHandler(contextHandler);
        server.start();

        final URI serverEndpoint = server.getURI();

        client.start();
        final Future<ClientEndpoint> connectFuture = client.connect(
                "localhost", serverEndpoint.getPort(), TIMEOUT, TimeUnit.SECONDS);
        final ClientEndpoint streamEndpoint = connectFuture.get();

        final HttpRequest request1 = new BasicHttpRequest("POST", createRequestURI(serverEndpoint, "/echo"));
        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                new BasicRequestProducer(request1, new AbstractClassicEntityProducer(4096, ContentType.TEXT_PLAIN, Executors.newSingleThreadExecutor()) {

                    @Override
                    protected void produceData(final ContentType contentType, final OutputStream outputStream) throws IOException {
                        Charset charset = contentType.getCharset();
                        if (charset == null) {
                            charset = StandardCharsets.US_ASCII;
                        }
                        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, charset))) {
                            for (int i = 0; i < 500; i++) {
                                if (i % 100 == 0) {
                                    writer.flush();
                                    Thread.sleep(500);
                                }
                                writer.write("0123456789abcdef\r\n");
                            }
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            throw new InterruptedIOException(ex.getMessage());
                        }
                    }

                }),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> result1 = future1.get();
        Assert.assertNotNull(result1);
        final HttpResponse response1 = result1.getHead();
        Assert.assertNotNull(response1);
        Assert.assertEquals(200, response1.getCode());
        final String s1 = result1.getBody();
        Assert.assertNotNull(s1);
        final StringTokenizer t1 = new StringTokenizer(s1, "\r\n");
        while (t1.hasMoreTokens()) {
            Assert.assertEquals("0123456789abcdef", t1.nextToken());
        };
    }

}
