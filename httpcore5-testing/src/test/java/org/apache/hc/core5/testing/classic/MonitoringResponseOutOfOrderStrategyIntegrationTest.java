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

package org.apache.hc.core5.testing.classic;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.bootstrap.HttpRequester;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.io.DefaultBHttpClientConnectionFactory;
import org.apache.hc.core5.http.impl.io.MonitoringResponseOutOfOrderStrategy;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.io.entity.AbstractHttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.testing.classic.extension.HttpRequesterResource;
import org.apache.hc.core5.testing.classic.extension.HttpServerResource;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class MonitoringResponseOutOfOrderStrategyIntegrationTest {

    // Use a 16k buffer for consistent results across systems
    private static final int BUFFER_SIZE = 16 * 1024;
    private static final Timeout TIMEOUT = Timeout.ofSeconds(3);

    private final URIScheme scheme;

    @RegisterExtension
    private final HttpServerResource serverResource;

    @RegisterExtension
    private final HttpRequesterResource clientResource;

    public MonitoringResponseOutOfOrderStrategyIntegrationTest(final URIScheme scheme) {
        this.scheme = scheme;

        this.serverResource = new HttpServerResource(scheme, bootstrap -> bootstrap
                .setSocketConfig(SocketConfig.custom()
                        .setSoTimeout(TIMEOUT)
                        .setSndBufSize(BUFFER_SIZE)
                        .setRcvBufSize(BUFFER_SIZE)
                        .setSoKeepAlive(false)
                        .build())
                .register("*", (request, response, context) -> {
                    response.setCode(400);
                    response.setEntity(new AllOnesHttpEntity(200000));
                }));

        this.clientResource = new HttpRequesterResource(bootstrap -> bootstrap
                .setSocketConfig(SocketConfig.custom()
                        .setSoTimeout(TIMEOUT)
                        .setRcvBufSize(BUFFER_SIZE)
                        .setSndBufSize(BUFFER_SIZE)
                        .setSoKeepAlive(false)
                        .build())
                .setConnectionFactory(DefaultBHttpClientConnectionFactory.builder()
                        .responseOutOfOrderStrategy(MonitoringResponseOutOfOrderStrategy.INSTANCE)
                        .build()));
    }

    @Test
    @org.junit.jupiter.api.Timeout(value = 1, unit = TimeUnit.MINUTES)// Failures may hang
    public void testResponseOutOfOrderWithDefaultStrategy() throws Exception {
        final HttpServer server = serverResource.start();
        final HttpRequester requester = clientResource.start();

        final HttpCoreContext context = HttpCoreContext.create();
        final HttpHost host = new HttpHost(scheme.id, "localhost", server.getLocalPort());

        final ClassicHttpRequest post = new BasicClassicHttpRequest(Method.POST, "/");
        post.setEntity(new AllOnesHttpEntity(200000));

        try (final ClassicHttpResponse response = requester.execute(host, post, TIMEOUT, context)) {
            Assertions.assertEquals(400, response.getCode());
            EntityUtils.consumeQuietly(response.getEntity());
        }
    }

    private static final class AllOnesHttpEntity extends AbstractHttpEntity {
        private long remaining;

        protected AllOnesHttpEntity(final long length) {
            super(ContentType.APPLICATION_OCTET_STREAM, null, true);
            this.remaining = length;
        }

        @Override
        public InputStream getContent() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void writeTo(final OutputStream outStream) throws IOException {
            final byte[] buf = new byte[1024];
            while (remaining > 0) {
                final int writeLength = (int) Math.min(remaining, buf.length);
                outStream.write(buf, 0, writeLength);
                outStream.flush();
                remaining -= writeLength;
            }
        }

        @Override
        public boolean isStreaming() {
            return true;
        }

        @Override
        public void close() {
        }

        @Override
        public long getContentLength() {
            return -1L;
        }
    }
}
