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

package org.apache.hc.core5.testing.nio;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.RequestNotExecutedException;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.impl.routing.RequestRouter;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.EntityTemplate;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.http.nio.support.classic.ClassicToAsyncRequestProducer;
import org.apache.hc.core5.http.nio.support.classic.ClassicToAsyncResponseConsumer;
import org.apache.hc.core5.http.nio.support.classic.ClassicToAsyncServerExchangeHandler;
import org.apache.hc.core5.http.support.BasicRequestBuilder;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.ssl.H2ClientTlsStrategy;
import org.apache.hc.core5.http2.ssl.H2ServerTlsStrategy;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.apache.hc.core5.testing.Result;
import org.apache.hc.core5.testing.SSLTestContexts;
import org.apache.hc.core5.testing.extension.classic.ExecutorResource;
import org.apache.hc.core5.testing.extension.nio.H2AsyncRequesterResource;
import org.apache.hc.core5.testing.extension.nio.H2AsyncServerResource;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

abstract class ClassicToAsyncTransportTest {

    static final Timeout TIMEOUT = Timeout.ofMinutes(1);

    final URIScheme scheme;
    @RegisterExtension
    final H2AsyncServerResource serverResource;
    @RegisterExtension
    final H2AsyncRequesterResource clientResource;
    @RegisterExtension
    final ExecutorResource executorResource;

    public ClassicToAsyncTransportTest(final URIScheme scheme, final HttpVersion version) {
        this.scheme = scheme;
        this.serverResource = new H2AsyncServerResource();
        this.serverResource.configure(bootstrap -> bootstrap
                .setVersionPolicy(version.lessEquals(HttpVersion.HTTP_1_1) ? HttpVersionPolicy.FORCE_HTTP_1 : HttpVersionPolicy.FORCE_HTTP_2)
                .setTlsStrategy(new H2ServerTlsStrategy(SSLTestContexts.createServerSSLContext()))
                .setIOReactorConfig(
                        IOReactorConfig.custom()
                                .setSoTimeout(TIMEOUT)
                                .build())
                .setRequestRouter(RequestRouter.<Supplier<AsyncServerExchangeHandler>>builder()
                        .addRoute(RequestRouter.LOCAL_AUTHORITY, "*", () -> new EchoHandler(2048))
                        .resolveAuthority(RequestRouter.LOCAL_AUTHORITY_RESOLVER)
                        .build())
        );
        this.clientResource = new H2AsyncRequesterResource();
        this.clientResource.configure(bootstrap -> bootstrap
                .setVersionPolicy(version.lessEquals(HttpVersion.HTTP_1_1) ? HttpVersionPolicy.FORCE_HTTP_1 : HttpVersionPolicy.FORCE_HTTP_2)
                .setTlsStrategy(new H2ClientTlsStrategy(SSLTestContexts.createClientSSLContext()))
                .setIOReactorConfig(IOReactorConfig.custom()
                        .setSoTimeout(TIMEOUT)
                        .build())
        );
        this.executorResource = new ExecutorResource(5);
    }

    void registerHandler(
            final String pathPattern,
            final Supplier<AsyncServerExchangeHandler> requestHandlerSupplier) {
        serverResource.configure(bootstrap -> bootstrap
                .setRequestRouter(RequestRouter.<Supplier<AsyncServerExchangeHandler>>builder()
                        .addRoute(RequestRouter.LOCAL_AUTHORITY, pathPattern, requestHandlerSupplier)
                        .resolveAuthority(RequestRouter.LOCAL_AUTHORITY_RESOLVER)
                        .build())
        );
    }

    void registerHandler(final String pathPattern, final HttpRequestHandler requestHandler) {
        registerHandler(pathPattern, () -> new ClassicToAsyncServerExchangeHandler(
                executorResource.getExecutorService(),
                requestHandler,
                LoggingExceptionCallback.INSTANCE));
    }

    @Test
    void test_request_execution() throws Exception {
        final HttpAsyncServer server = serverResource.start();
        registerHandler("/echo", () -> new EchoHandler(1024));

        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(0), scheme);
        final ListenerEndpoint listener = future.get();
        final InetSocketAddress address = (InetSocketAddress) listener.getAddress();
        final HttpAsyncRequester requester = clientResource.start();

        final HttpHost target = new HttpHost(scheme.id, "localhost", address.getPort());

        final int n = 10;

        for (int i = 0; i < n; i++) {
            final ClassicHttpRequest request1 = ClassicRequestBuilder.post()
                    .setHttpHost(target)
                    .setPath("/echo")
                    .setEntity(new EntityTemplate(
                            -1,
                            ContentType.TEXT_PLAIN.withCharset(StandardCharsets.UTF_8),
                            null,
                            outputStream -> {
                                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
                                    for (int ii = 0; ii < 500; ii++) {
                                        writer.write("0123456789abcdef");
                                        writer.newLine();
                                    }
                                }
                            }))
                    .build();
            final ClassicToAsyncRequestProducer requestProducer = new ClassicToAsyncRequestProducer(request1, 16, TIMEOUT);
            final ClassicToAsyncResponseConsumer responseConsumer = new ClassicToAsyncResponseConsumer(16, TIMEOUT);

            requester.execute(requestProducer, responseConsumer, TIMEOUT, null);

            requestProducer.blockWaiting().execute();

            try (ClassicHttpResponse response = responseConsumer.blockWaiting()) {
                final HttpEntity entity = response.getEntity();
                final ContentType contentType = ContentType.parse(entity.getContentType());
                final Charset charset = ContentType.getCharset(contentType, StandardCharsets.UTF_8);

                try (final InputStream inputStream = entity.getContent()) {
                    final StringBuilder buffer = new StringBuilder();
                    final byte[] tmp = new byte[16];
                    int l;
                    while ((l = inputStream.read(tmp)) != -1) {
                        buffer.append(charset.decode(ByteBuffer.wrap(tmp, 0, l)));
                    }
                    final StringTokenizer t1 = new StringTokenizer(buffer.toString(), "\r\n");
                    while (t1.hasMoreTokens()) {
                        Assertions.assertEquals("0123456789abcdef", t1.nextToken());
                    }
                }
            }
        }
    }

    @ParameterizedTest(name = "method {0}")
    @ValueSource(strings = {"GET", "POST", "HEAD"})
    void test_request_handling(final String method) throws Exception {
        registerHandler("/hello", (request, response, context) -> {
            final HttpEntity requestEntity = request.getEntity();
            if (requestEntity != null) {
                EntityUtils.consume(requestEntity);
            }
            final ContentType contentType = ContentType.TEXT_PLAIN.withCharset(StandardCharsets.UTF_8);
            final Charset charset = contentType.getCharset();
            final HttpEntity responseEntity = new EntityTemplate(
                    contentType,
                    outputStream -> {
                        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, charset))) {
                            for (int i = 0; i < 500; i++) {
                                writer.write("0123456789abcdef\r\n");
                            }
                        }
                    });
            response.setEntity(responseEntity);
        });

        final HttpAsyncServer server = serverResource.start();
        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(0), scheme);
        final ListenerEndpoint listener = future.get();
        final InetSocketAddress address = (InetSocketAddress) listener.getAddress();
        final HttpAsyncRequester requester = clientResource.start();

        final HttpHost target = new HttpHost(scheme.id, "localhost", address.getPort());

        final int n = 10;

        final CountDownLatch countDownLatch = new CountDownLatch(n);
        final Queue<Result<String>> resultQueue = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < n; i++) {
            final BasicHttpRequest request1 = BasicRequestBuilder.create(method)
                    .setHttpHost(target)
                    .setPath("/hello")
                    .build();
            final AsyncEntityProducer entityProducer = Method.POST.isSame(method) ?
                    new MultiLineEntityProducer("xxxxxxxxxxxx", 250) : null;

            requester.execute(
                    new BasicRequestProducer(request1, entityProducer),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()),
                    TIMEOUT,
                    new FutureCallback<Message<HttpResponse, String>>() {

                        @Override
                        public void completed(final Message<HttpResponse, String> responseMessage) {
                            resultQueue.add(new Result<>(
                                    request1,
                                    responseMessage.getHead(),
                                    responseMessage.getBody()));
                            countDownLatch.countDown();
                        }

                        @Override
                        public void failed(final Exception ex) {
                            resultQueue.add(new Result<>(request1, ex));
                            countDownLatch.countDown();
                        }

                        @Override
                        public void cancelled() {
                            failed(new RequestNotExecutedException());
                        }

                    });
        }

        Assertions.assertTrue(countDownLatch.await(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()), "Request executions have not completed in time");
        for (final Result<String> result : resultQueue) {
            if (result.isOK()) {
                Assertions.assertNotNull(result.response);
                Assertions.assertEquals(HttpStatus.SC_OK, result.response.getCode(), "Response message returned non 200 status");
                if (Method.HEAD.isSame(method)) {
                    Assertions.assertNull(result.content);
                } else {
                    Assertions.assertNotNull(result.content);
                    final StringTokenizer t1 = new StringTokenizer(result.content, "\r\n");
                    while (t1.hasMoreTokens()) {
                        Assertions.assertEquals("0123456789abcdef", t1.nextToken());
                    }
                }
            } else {
                Assertions.fail(result.exception);
            }
        }
    }

    @Test
    void test_request_handling_full_streaming() throws Exception {
        registerHandler("/echo", (request, response, context) -> {
            final HttpEntity requestEntity = request.getEntity();
            final ContentType contentType = requestEntity != null ? ContentType.parseLenient(requestEntity.getContentType()) : ContentType.TEXT_PLAIN;
            final Charset charset = contentType.getCharset(StandardCharsets.UTF_8);
            final HttpEntity responseEntity;
            if (requestEntity != null) {
                responseEntity = new EntityTemplate(
                        contentType,
                        outputStream -> {
                            try (BufferedReader reader = new BufferedReader(new InputStreamReader(requestEntity.getContent()));
                                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, charset))) {
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    writer.write(line);
                                    writer.newLine();
                                }
                            }
                        });
            } else {
                responseEntity = null;
            }
            response.setEntity(responseEntity);
        });

        final HttpAsyncServer server = serverResource.start();
        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(0), scheme);
        final ListenerEndpoint listener = future.get();
        final InetSocketAddress address = (InetSocketAddress) listener.getAddress();
        final HttpAsyncRequester requester = clientResource.start();

        final HttpHost target = new HttpHost(scheme.id, "localhost", address.getPort());

        final int n = 10;

        final CountDownLatch countDownLatch = new CountDownLatch(n);
        final Queue<Result<String>> resultQueue = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < n; i++) {
            final BasicHttpRequest request1 = BasicRequestBuilder.post()
                    .setHttpHost(target)
                    .setPath("/echo")
                    .build();
            final AsyncEntityProducer entityProducer = new MultiLineEntityProducer("0123456789abcdef", 500);

            requester.execute(
                    new BasicRequestProducer(request1, entityProducer),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()),
                    TIMEOUT,
                    new FutureCallback<Message<HttpResponse, String>>() {

                        @Override
                        public void completed(final Message<HttpResponse, String> responseMessage) {
                            resultQueue.add(new Result<>(
                                    request1,
                                    responseMessage.getHead(),
                                    responseMessage.getBody()));
                            countDownLatch.countDown();
                        }

                        @Override
                        public void failed(final Exception ex) {
                            resultQueue.add(new Result<>(request1, ex));
                            countDownLatch.countDown();
                        }

                        @Override
                        public void cancelled() {
                            failed(new RequestNotExecutedException());
                        }

                    });
        }

        Assertions.assertTrue(countDownLatch.await(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()), "Request executions have not completed in time");
        for (final Result<String> result : resultQueue) {
            if (result.isOK()) {
                Assertions.assertNotNull(result.response);
                Assertions.assertEquals(HttpStatus.SC_OK, result.response.getCode(), "Response message returned non 200 status");
                Assertions.assertNotNull(result.content);
                final StringTokenizer t1 = new StringTokenizer(result.content, "\r\n");
                while (t1.hasMoreTokens()) {
                    Assertions.assertEquals("0123456789abcdef", t1.nextToken());
                }
            } else {
                Assertions.fail(result.exception);
            }
        }
    }

}
