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
package org.apache.hc.core5.testing.async;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStreamResetException;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.impl.routing.RequestRouter;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.ResponseChannel;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.support.AsyncRequestBuilder;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.ssl.H2ClientTlsStrategy;
import org.apache.hc.core5.http2.ssl.H2ServerTlsStrategy;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.apache.hc.core5.testing.SSLTestContexts;
import org.apache.hc.core5.testing.extension.nio.H2AsyncRequesterResource;
import org.apache.hc.core5.testing.extension.nio.H2AsyncServerResource;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class AsyncH2SocketTimeoutCoreTest {

    private static final Timeout SOCKET_TIMEOUT = Timeout.ofSeconds(30);
    private static final Timeout REQUEST_TIMEOUT = Timeout.ofSeconds(1);
    private static final Timeout RESULT_TIMEOUT = Timeout.ofSeconds(30);

    @RegisterExtension
    private final H2AsyncServerResource serverResource = new H2AsyncServerResource();

    @RegisterExtension
    private final H2AsyncRequesterResource clientResource = new H2AsyncRequesterResource();

    public AsyncH2SocketTimeoutCoreTest() {
        serverResource.configure(bootstrap -> bootstrap
                .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_2)
                .setTlsStrategy(new H2ServerTlsStrategy(SSLTestContexts.createServerSSLContext()))
                .setIOReactorConfig(
                        IOReactorConfig.custom()
                                .setSoTimeout(SOCKET_TIMEOUT)
                                .build())
                .setRequestRouter(
                        RequestRouter.<Supplier<AsyncServerExchangeHandler>>builder()
                                .addRoute(
                                        RequestRouter.LOCAL_AUTHORITY,
                                        "*",
                                        SimpleDelayingHandler::new)
                                .resolveAuthority(RequestRouter.LOCAL_AUTHORITY_RESOLVER)
                                .build()
                )
        );

        clientResource.configure(bootstrap -> bootstrap
                .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_2)
                .setTlsStrategy(new H2ClientTlsStrategy(SSLTestContexts.createClientSSLContext()))
                .setIOReactorConfig(
                        IOReactorConfig.custom()
                                .setSoTimeout(SOCKET_TIMEOUT)
                                .build())
        );
    }

    @Test
    void testHttp2RequestTimeoutYieldsStreamReset() throws Exception {
        final InetSocketAddress address = startServer();
        final HttpAsyncRequester requester = clientResource.start();

        final URI requestUri = new URI("http://localhost:" + address.getPort() + "/timeout");

        final AsyncRequestProducer requestProducer = AsyncRequestBuilder.get(requestUri).build();
        final BasicResponseConsumer<String> responseConsumer =
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer());

        final Future<Message<HttpResponse, String>> future =
                requester.execute(requestProducer, responseConsumer, REQUEST_TIMEOUT, null);

        final ExecutionException ex = Assertions.assertThrows(
                ExecutionException.class,
                () -> future.get(RESULT_TIMEOUT.getDuration(), RESULT_TIMEOUT.getTimeUnit()));

        final Throwable cause = ex.getCause();
        Assertions.assertInstanceOf(
                HttpStreamResetException.class,
                cause,
                "Expected HttpStreamResetException, but got: " + cause);
    }

    private InetSocketAddress startServer() throws Exception {
        final HttpAsyncServer server = serverResource.start();
        final ListenerEndpoint listener = server.listen(new InetSocketAddress(0), URIScheme.HTTP).get();
        return (InetSocketAddress) listener.getAddress();
    }

    static final class SimpleDelayingHandler implements AsyncServerExchangeHandler {

        private final AtomicBoolean completed = new AtomicBoolean(false);

        @Override
        public void handleRequest(
                final HttpRequest request,
                final EntityDetails entityDetails,
                final ResponseChannel responseChannel,
                final HttpContext context) throws HttpException, IOException {
            // Intentionally do nothing: no response is sent back.
        }

        @Override
        public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
            // Accept any amount of request data.
            capacityChannel.update(Integer.MAX_VALUE);
        }

        @Override
        public void consume(final ByteBuffer src) throws IOException {
            // Discard request body if present.
            if (src != null) {
                src.position(src.limit());
            }
        }

        @Override
        public void streamEnd(final List<? extends Header> trailers)
                throws HttpException, IOException {
            // Nothing special to do on stream end for this test.
        }

        @Override
        public int available() {
            // No response body to produce.
            return 0;
        }

        @Override
        public void produce(final DataStreamChannel channel) throws IOException {
            // In this test we never send a response; just ensure the stream is closed
            // if produce gets called.
            if (completed.compareAndSet(false, true)) {
                channel.endStream();
            }
        }

        @Override
        public void failed(final Exception cause) {
            // No-op for this simple test handler.
        }

        @Override
        public void releaseResources() {
            // No resources to release.
        }

    }

}
