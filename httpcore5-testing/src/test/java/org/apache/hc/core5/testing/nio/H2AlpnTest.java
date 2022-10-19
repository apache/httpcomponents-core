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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityProducer;
import org.apache.hc.core5.http.nio.ssl.BasicServerTlsStrategy;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.http2.impl.nio.ProtocolNegotiationException;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2MultiplexingRequester;
import org.apache.hc.core5.http2.ssl.H2ClientTlsStrategy;
import org.apache.hc.core5.http2.ssl.H2ServerTlsStrategy;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.apache.hc.core5.testing.SSLTestContexts;
import org.apache.hc.core5.testing.nio.extension.H2AsyncServerResource;
import org.apache.hc.core5.testing.nio.extension.H2MultiplexingRequesterResource;
import org.apache.hc.core5.util.Timeout;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class H2AlpnTest {

    private static final Timeout TIMEOUT = Timeout.ofMinutes(1);

    private final boolean strictALPN;
    private final boolean h2Allowed;

    @RegisterExtension
    private final H2AsyncServerResource serverResource;
    @RegisterExtension
    private final H2MultiplexingRequesterResource clientResource;

    public H2AlpnTest(final boolean strictALPN, final boolean h2Allowed) throws Exception {
        this.strictALPN = strictALPN;
        this.h2Allowed = h2Allowed;

        final TlsStrategy serverTlsStrategy = h2Allowed ?
                new H2ServerTlsStrategy(SSLTestContexts.createServerSSLContext()) :
                new BasicServerTlsStrategy(SSLTestContexts.createServerSSLContext());

        this.serverResource = new H2AsyncServerResource(bootstrap -> bootstrap
                .setIOReactorConfig(
                        IOReactorConfig.custom()
                                .setSoTimeout(TIMEOUT)
                                .build())
                .setTlsStrategy(serverTlsStrategy)
                .register("*", () -> new EchoHandler(2048))
        );

        final TlsStrategy clientTlsStrategy = new H2ClientTlsStrategy(SSLTestContexts.createClientSSLContext());

        this.clientResource = new H2MultiplexingRequesterResource(bootstrap -> bootstrap
                .setIOReactorConfig(IOReactorConfig.custom()
                        .setSoTimeout(TIMEOUT)
                        .build())
                .setTlsStrategy(clientTlsStrategy)
                .setStrictALPNHandshake(strictALPN)
        );
    }

    @Test
    public void testALPN() throws Exception {
        final HttpAsyncServer server = serverResource.start();
        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(0), URIScheme.HTTPS);
        final ListenerEndpoint listener = future.get();
        final InetSocketAddress address = (InetSocketAddress) listener.getAddress();
        final H2MultiplexingRequester requester = clientResource.start();

        final HttpHost target = new HttpHost(URIScheme.HTTPS.id, "localhost", address.getPort());
        final Future<Message<HttpResponse, String>> resultFuture1 = requester.execute(
            new BasicRequestProducer(Method.POST, target, "/stuff",
                new StringAsyncEntityProducer("some stuff", ContentType.TEXT_PLAIN)),
            new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), TIMEOUT, null);
        final Message<HttpResponse, String> message1;
        try {
            message1 = resultFuture1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        } catch (final ExecutionException e) {
            final Throwable cause = e.getCause();
            assertFalse(h2Allowed, "h2 negotiation was enabled, but h2 was not negotiated");
            assertTrue(cause instanceof ProtocolNegotiationException);
            assertEquals("ALPN: missing application protocol", cause.getMessage());
            assertTrue(strictALPN, "strict ALPN mode was not enabled, but the client negotiator still threw");
            return;
        }

        assertTrue(h2Allowed, "h2 negotiation was disabled, but h2 was negotiated");
        assertThat(message1, CoreMatchers.notNullValue());
        final HttpResponse response1 = message1.getHead();
        assertThat(response1.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
        final String body1 = message1.getBody();
        assertThat(body1, CoreMatchers.equalTo("some stuff"));
    }

}
