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

import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLSession;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.nio.ServerHttp1StreamDuplexerFactory;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TestServerHttp1UpgradeHandler {

    @Test
    void upgradeCreatesHandlerAndCompletesCallback() {
        final HttpProcessor httpProcessor = Mockito.mock(HttpProcessor.class);
        @SuppressWarnings("unchecked")
        final HandlerFactory<AsyncServerExchangeHandler> exchangeHandlerFactory =
                (HandlerFactory<AsyncServerExchangeHandler>) Mockito.mock(HandlerFactory.class);
        final ServerHttp1StreamDuplexerFactory factory = new ServerHttp1StreamDuplexerFactory(
                httpProcessor,
                exchangeHandlerFactory,
                Http1Config.DEFAULT,
                CharCodingConfig.DEFAULT,
                null,
                null);

        final ProtocolIOSession ioSession = Mockito.mock(ProtocolIOSession.class);
        Mockito.when(ioSession.poll()).thenReturn(null);
        final SSLSession sslSession = Mockito.mock(SSLSession.class);
        Mockito.when(ioSession.getTlsDetails()).thenReturn(new TlsDetails(sslSession, "h2"));

        final ServerHttp1UpgradeHandler handler = new ServerHttp1UpgradeHandler(factory);
        final AtomicReference<ProtocolIOSession> completed = new AtomicReference<>();

        handler.upgrade(ioSession, new FutureCallback<ProtocolIOSession>() {
            @Override
            public void completed(final ProtocolIOSession result) {
                completed.set(result);
            }

            @Override
            public void failed(final Exception ex) {
            }

            @Override
            public void cancelled() {
            }
        });

        Assertions.assertSame(ioSession, completed.get());
        Mockito.verify(ioSession, Mockito.times(2)).upgrade(Mockito.any());
    }

}
