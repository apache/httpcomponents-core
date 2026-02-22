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
package org.apache.hc.core5.http2.impl.nio;

import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.nio.ClientHttp1IOEventHandler;
import org.apache.hc.core5.http.impl.nio.ClientHttp1StreamDuplexerFactory;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.ssl.ApplicationProtocol;
import org.apache.hc.core5.reactor.EndpointParameters;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TestClientHttpProtocolNegotiationStarter {

    private static ClientHttp1StreamDuplexerFactory http1Factory() {
        final HttpProcessor httpProcessor = Mockito.mock(HttpProcessor.class);
        return new ClientHttp1StreamDuplexerFactory(httpProcessor, Http1Config.DEFAULT, CharCodingConfig.DEFAULT);
    }

    private static ClientH2StreamMultiplexerFactory http2Factory() {
        final HttpProcessor httpProcessor = Mockito.mock(HttpProcessor.class);
        @SuppressWarnings("unchecked")
        final HandlerFactory<AsyncPushConsumer> pushHandlerFactory =
                (HandlerFactory<AsyncPushConsumer>) Mockito.mock(HandlerFactory.class);
        return new ClientH2StreamMultiplexerFactory(httpProcessor, pushHandlerFactory, null, null, null);
    }

    @Test
    void forceHttp2UsesPrefaceHandlerAndTlsUpgrade() {
        final TlsStrategy tlsStrategy = Mockito.mock(TlsStrategy.class);
        final ClientHttpProtocolNegotiationStarter starter = new ClientHttpProtocolNegotiationStarter(
                http1Factory(),
                http2Factory(),
                HttpVersionPolicy.FORCE_HTTP_2,
                tlsStrategy,
                Timeout.ofSeconds(1),
                (Callback<Exception>) ex -> {
                });

        final ProtocolIOSession ioSession = Mockito.mock(ProtocolIOSession.class);
        final EndpointParameters endpoint = new EndpointParameters(URIScheme.HTTPS.id, "localhost", 8443,
                HttpVersionPolicy.FORCE_HTTP_2);

        final Object handler = starter.createHandler(ioSession, endpoint);

        Assertions.assertTrue(handler instanceof ClientH2PrefaceHandler);
        Mockito.verify(ioSession).registerProtocol(Mockito.eq(ApplicationProtocol.HTTP_1_1.id), Mockito.any());
        Mockito.verify(ioSession).registerProtocol(Mockito.eq(ApplicationProtocol.HTTP_2.id), Mockito.any());
        Mockito.verify(tlsStrategy).upgrade(ioSession, endpoint, HttpVersionPolicy.FORCE_HTTP_2, Timeout.ofSeconds(1), null);
    }

    @Test
    void forceHttp1UsesHttp1Handler() {
        final ClientHttpProtocolNegotiationStarter starter = new ClientHttpProtocolNegotiationStarter(
                http1Factory(),
                http2Factory(),
                HttpVersionPolicy.FORCE_HTTP_1,
                null,
                null,
                null);

        final ProtocolIOSession ioSession = Mockito.mock(ProtocolIOSession.class);
        final Object handler = starter.createHandler(ioSession, null);

        Assertions.assertTrue(handler instanceof ClientHttp1IOEventHandler);
    }

    @Test
    void negotiateUsesProtocolNegotiator() {
        final ClientHttpProtocolNegotiationStarter starter = new ClientHttpProtocolNegotiationStarter(
                http1Factory(),
                http2Factory(),
                HttpVersionPolicy.NEGOTIATE,
                null,
                null,
                null);

        final ProtocolIOSession ioSession = Mockito.mock(ProtocolIOSession.class);
        final Object handler = starter.createHandler(ioSession, null);

        Assertions.assertTrue(handler instanceof HttpProtocolNegotiator);
    }

    @Test
    void forceHttp2TlsMissingAlpnFailsStrictHandshake() throws Exception {
        final ClientHttpProtocolNegotiationStarter starter = new ClientHttpProtocolNegotiationStarter(
                http1Factory(),
                http2Factory(),
                HttpVersionPolicy.FORCE_HTTP_2,
                null,
                null,
                null);

        final ProtocolIOSession ioSession = Mockito.mock(ProtocolIOSession.class);
        Mockito.when(ioSession.getTlsDetails()).thenReturn(new TlsDetails(null, null));
        final ClientH2PrefaceHandler handler = (ClientH2PrefaceHandler) starter.createHandler(ioSession, null);

        Assertions.assertThrows(ProtocolNegotiationException.class, () -> handler.connected(ioSession));
    }

    @Test
    void forceHttp2TlsUnexpectedAlpnFailsHandshake() throws Exception {
        final ClientHttpProtocolNegotiationStarter starter = new ClientHttpProtocolNegotiationStarter(
                http1Factory(),
                http2Factory(),
                HttpVersionPolicy.FORCE_HTTP_2,
                null,
                null,
                null);

        final ProtocolIOSession ioSession = Mockito.mock(ProtocolIOSession.class);
        Mockito.when(ioSession.getTlsDetails()).thenReturn(new TlsDetails(null, ApplicationProtocol.HTTP_1_1.id));
        final ClientH2PrefaceHandler handler = (ClientH2PrefaceHandler) starter.createHandler(ioSession, null);

        Assertions.assertThrows(ProtocolNegotiationException.class, () -> handler.connected(ioSession));
    }

}
