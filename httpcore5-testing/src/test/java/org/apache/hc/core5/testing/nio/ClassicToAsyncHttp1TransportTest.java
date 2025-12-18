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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Random;
import java.util.concurrent.Future;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.http.nio.support.classic.ClassicToAsyncRequestProducer;
import org.apache.hc.core5.http.nio.support.classic.ClassicToAsyncResponseConsumer;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

abstract class ClassicToAsyncHttp1TransportTest extends ClassicToAsyncTransportTest {

    public ClassicToAsyncHttp1TransportTest(final URIScheme scheme, final HttpVersion version) {
        super(scheme, version);
    }

    @ValueSource(ints = {0, 2048, 10240})
    @ParameterizedTest(name = "{displayName}; content length: {0}")
    void test_request_handling_no_keep_alive(final int contentSize) throws Exception {
        final HttpAsyncServer server = serverResource.start();
        registerHandler("/echo", () -> new EchoHandler(1024));

        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), scheme);
        final ListenerEndpoint listener = future.get();
        final InetSocketAddress address = (InetSocketAddress) listener.getAddress();
        final HttpAsyncRequester requester = clientResource.start();

        final HttpHost target = new HttpHost(scheme.id, "localhost", address.getPort());

        final int n = 10;

        for (int i = 0; i < n; i++) {
            final byte[] temp = new byte[contentSize];
            new Random(System.currentTimeMillis()).nextBytes(temp);

            final ClassicHttpRequest request = ClassicRequestBuilder.post()
                    .setHttpHost(target)
                    .setPath("/echo/")
                    .addHeader(HttpHeaders.CONNECTION, "Close")
                    .setEntity(new ByteArrayEntity(temp, ContentType.DEFAULT_BINARY))
                    .build();

            final ClassicToAsyncRequestProducer requestProducer = new ClassicToAsyncRequestProducer(request, TIMEOUT);
            final ClassicToAsyncResponseConsumer responseConsumer = new ClassicToAsyncResponseConsumer(TIMEOUT);

            requester.execute(requestProducer, responseConsumer, TIMEOUT, null);

            requestProducer.blockWaiting().execute();

            try (ClassicHttpResponse response = responseConsumer.blockWaiting()) {
                Assertions.assertEquals(200, response.getCode());
                final byte[] bytes = EntityUtils.toByteArray(response.getEntity());
                Assertions.assertNotNull(bytes);
                Assertions.assertArrayEquals(temp, bytes);
            }
        }
    }

}
