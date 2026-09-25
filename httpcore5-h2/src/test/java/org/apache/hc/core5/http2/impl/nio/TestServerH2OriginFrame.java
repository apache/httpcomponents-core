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

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.locks.ReentrantLock;

import javax.net.ssl.SSLSession;

import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.frame.DefaultFrameFactory;
import org.apache.hc.core5.http2.frame.FrameFactory;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

class TestServerH2OriginFrame {

    @Test
    void sendsConfiguredOriginSetImmediatelyAfterConnect() throws Exception {
        final FrameFactory frameFactory = Mockito.spy(new DefaultFrameFactory());
        final ServerH2StreamMultiplexer multiplexer = newMultiplexer(
                true,
                frameFactory,
                Arrays.asList(
                        new HttpHost("https", "assets.example", 443),
                        new HttpHost("https", "api.example", 8443)));

        multiplexer.onConnect();

        Mockito.verify(frameFactory).createOrigin(ArgumentMatchers.any(ByteBuffer.class));
    }

    @Test
    void sendsEmptyOriginSetAdvertisement() throws Exception {
        final FrameFactory frameFactory = Mockito.spy(new DefaultFrameFactory());
        final ServerH2StreamMultiplexer multiplexer = newMultiplexer(
                true, frameFactory, Collections.emptyList());

        multiplexer.sendOriginSet(Collections.emptyList());

        Mockito.verify(frameFactory).createOrigin(ArgumentMatchers.argThat(payload -> payload.remaining() == 0));
    }

    @Test
    void neverSendsOriginFrameOverH2c() throws Exception {
        final FrameFactory frameFactory = Mockito.spy(new DefaultFrameFactory());
        final ServerH2StreamMultiplexer multiplexer = newMultiplexer(
                false, frameFactory, Collections.singleton(new HttpHost("https", "assets.example", 443)));

        multiplexer.onConnect();

        Mockito.verify(frameFactory, Mockito.never()).createOrigin(ArgumentMatchers.any());
    }

    private static ServerH2StreamMultiplexer newMultiplexer(
            final boolean tls,
            final FrameFactory frameFactory,
            final java.util.Collection<HttpHost> origins) {
        final ProtocolIOSession ioSession = Mockito.mock(ProtocolIOSession.class);
        Mockito.when(ioSession.getLock()).thenReturn(new ReentrantLock());
        if (tls) {
            Mockito.when(ioSession.getTlsDetails()).thenReturn(
                    new TlsDetails(Mockito.mock(SSLSession.class), "h2"));
        }
        final HttpProcessor httpProcessor = Mockito.mock(HttpProcessor.class);
        @SuppressWarnings("unchecked")
        final HandlerFactory<AsyncServerExchangeHandler> exchangeHandlerFactory =
                (HandlerFactory<AsyncServerExchangeHandler>) Mockito.mock(HandlerFactory.class);
        return new ServerH2StreamMultiplexer(
                ioSession,
                frameFactory,
                httpProcessor,
                exchangeHandlerFactory,
                CharCodingConfig.DEFAULT,
                H2Config.DEFAULT,
                null,
                origins);
    }
}
