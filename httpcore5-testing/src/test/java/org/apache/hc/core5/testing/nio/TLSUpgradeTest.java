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

import java.net.InetSocketAddress;
import java.util.concurrent.Future;

import org.apache.hc.core5.concurrent.BasicFuture;
import org.apache.hc.core5.concurrent.CompletingFutureContribution;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.impl.routing.RequestRouter;
import org.apache.hc.core5.http.nio.AsyncClientEndpoint;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityProducer;
import org.apache.hc.core5.http.nio.ssl.TlsUpgradeCapable;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.hc.core5.testing.extension.nio.HttpAsyncRequesterResource;
import org.apache.hc.core5.testing.extension.nio.HttpAsyncServerResource;
import org.apache.hc.core5.util.Timeout;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class TLSUpgradeTest {

    private static final Timeout TIMEOUT = Timeout.ofSeconds(30);

    @RegisterExtension
    private final HttpAsyncServerResource serverResource;
    @RegisterExtension
    private final HttpAsyncRequesterResource clientResource;

    public TLSUpgradeTest() {
        this.serverResource = new HttpAsyncServerResource(bootstrap -> bootstrap
                .setIOReactorConfig(
                        IOReactorConfig.custom()
                                .setSoTimeout(TIMEOUT)
                                .build())
                .setRequestRouter(RequestRouter.<Supplier<AsyncServerExchangeHandler>>builder()
                        .addRoute(RequestRouter.LOCAL_AUTHORITY, "*", () -> new EchoHandler(2048))
                        .resolveAuthority(RequestRouter.LOCAL_AUTHORITY_RESOLVER)
                        .build())
        );
        this.clientResource = new HttpAsyncRequesterResource(bootstrap -> bootstrap
                .setIOReactorConfig(IOReactorConfig.custom()
                        .setSoTimeout(TIMEOUT)
                        .build())
        );
    }

    @Test
    void testTLSUpgrade() throws Exception {
        final HttpAsyncServer server = serverResource.start();
        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(0), URIScheme.HTTPS);
        final ListenerEndpoint listener = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        final InetSocketAddress address = (InetSocketAddress) listener.getAddress();
        final HttpAsyncRequester requester = clientResource.start();

        final HttpHost target = new HttpHost(URIScheme.HTTPS.id, "localhost", address.getPort());
        final Future<Message<HttpResponse, String>> resultFuture1 = requester.execute(
                new BasicRequestProducer(Method.POST, target, "/stuff",
                        new StringAsyncEntityProducer("some stuff", ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), TIMEOUT, null);
        final Message<HttpResponse, String> message1 = resultFuture1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        assertThat(message1, CoreMatchers.notNullValue());
        final HttpResponse response1 = message1.getHead();
        assertThat(response1.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
        final String body1 = message1.getBody();
        assertThat(body1, CoreMatchers.equalTo("some stuff"));

        // Connect using plain HTTP scheme
        final Future<AsyncClientEndpoint> endpointFuture = requester.connect(
                new HttpHost(URIScheme.HTTP.id, "localhost", address.getPort()), TIMEOUT);

        final AsyncClientEndpoint clientEndpoint = endpointFuture.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertInstanceOf(TlsUpgradeCapable.class, clientEndpoint);

        // Upgrade to TLS
        final BasicFuture<TlsDetails> tlsFuture = new BasicFuture<>(null);
        ((TlsUpgradeCapable) clientEndpoint).tlsUpgrade(target, new CompletingFutureContribution<>(tlsFuture, ProtocolIOSession::getTlsDetails));

        final TlsDetails tlsDetails = tlsFuture.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(tlsDetails);

        // Execute request over HTTPS
        final Future<Message<HttpResponse, String>> resultFuture2 = clientEndpoint.execute(
                new BasicRequestProducer(Method.POST, target, "/stuff",
                        new StringAsyncEntityProducer("some stuff", ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> message2 = resultFuture2.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        assertThat(message2, CoreMatchers.notNullValue());
        final HttpResponse response2 = message2.getHead();
        assertThat(response2.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
        final String body2 = message2.getBody();
        assertThat(body2, CoreMatchers.equalTo("some stuff"));
    }

}
