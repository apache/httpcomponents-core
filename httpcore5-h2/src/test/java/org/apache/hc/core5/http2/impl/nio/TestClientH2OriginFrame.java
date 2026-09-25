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

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.concurrent.locks.ReentrantLock;

import javax.net.ssl.SSLSession;

import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http2.H2ConnectionException;
import org.apache.hc.core5.http2.H2Error;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.frame.DefaultFrameFactory;
import org.apache.hc.core5.http2.frame.FrameType;
import org.apache.hc.core5.http2.frame.RawFrame;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TestClientH2OriginFrame {

    private static final HttpHost INITIAL = new HttpHost("https", "primary.example", 443);
    private static final HttpHost ALT = new HttpHost("https", "assets.example", 443);

    @Test
    void initialOriginUsesActualTlsPeerPort() throws Exception {
        final ProtocolIOSession ioSession = Mockito.mock(ProtocolIOSession.class);
        Mockito.when(ioSession.getInitialEndpoint()).thenReturn(INITIAL);
        Mockito.when(ioSession.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 8443));
        final SSLSession sslSession = Mockito.mock(SSLSession.class);
        Mockito.when(sslSession.getPeerPort()).thenReturn(8443);
        Mockito.when(ioSession.getTlsDetails()).thenReturn(new TlsDetails(sslSession, "h2"));
        final HttpProcessor httpProcessor = Mockito.mock(HttpProcessor.class);
        @SuppressWarnings("unchecked")
        final HandlerFactory<AsyncPushConsumer> pushHandlerFactory =
                (HandlerFactory<AsyncPushConsumer>) Mockito.mock(HandlerFactory.class);
        final ClientH2StreamMultiplexer multiplexer = new ClientH2StreamMultiplexer(
                ioSession, DefaultFrameFactory.INSTANCE, httpProcessor, pushHandlerFactory,
                H2Config.DEFAULT, CharCodingConfig.DEFAULT, null);

        multiplexer.consumeOriginFrame(new RawFrame(
                FrameType.ORIGIN.getValue(), 0, 0, ByteBuffer.allocate(0)));

        Assertions.assertTrue(multiplexer.getOriginSet().contains(
                new HttpHost("https", "primary.example", 8443)));
    }

    @Test
    void dispatchesOriginFrameFromHttp2WireInput() throws Exception {
        final ClientH2StreamMultiplexer multiplexer = newMultiplexer(true, H2Config.DEFAULT);
        multiplexer.onConnect();
        multiplexer.onInput(wireFrame(FrameType.SETTINGS.getValue(), 0, 0, null));
        final ByteBuffer payload = H2OriginFrameCodec.encode(Collections.singleton(ALT), 16384).get(0);

        multiplexer.onInput(wireFrame(FrameType.ORIGIN.getValue(), 0, 0, payload));

        Assertions.assertTrue(multiplexer.getOriginSet().contains(ALT));
    }

    @Test
    void processesOriginFrameOnTlsConnection() throws Exception {
        final ClientH2StreamMultiplexer multiplexer = newMultiplexer(true, H2Config.DEFAULT);

        multiplexer.consumeOriginFrame(originFrame(0, 0, ALT));

        Assertions.assertTrue(multiplexer.isOriginSetInitialized());
        Assertions.assertTrue(multiplexer.getOriginSet().contains(INITIAL));
        Assertions.assertTrue(multiplexer.getOriginSet().contains(ALT));
        Assertions.assertTrue(multiplexer.isOriginAllowed(ALT));
    }

    @Test
    void highFlagBitsDoNotChangeProcessing() throws Exception {
        final ClientH2StreamMultiplexer multiplexer = newMultiplexer(true, H2Config.DEFAULT);
        multiplexer.consumeOriginFrame(originFrame(0, 0x10, ALT));
        Assertions.assertTrue(multiplexer.isOriginSetInitialized());
    }

    @Test
    void ignoresFrameWithBackwardIncompatibleFlag() throws Exception {
        final ClientH2StreamMultiplexer multiplexer = newMultiplexer(true, H2Config.DEFAULT);
        multiplexer.consumeOriginFrame(originFrame(0, 0x01, ALT));
        Assertions.assertFalse(multiplexer.isOriginSetInitialized());
    }

    @Test
    void ignoresFrameOnNonZeroStream() throws Exception {
        final ClientH2StreamMultiplexer multiplexer = newMultiplexer(true, H2Config.DEFAULT);
        multiplexer.consumeOriginFrame(originFrame(3, 0, ALT));
        Assertions.assertFalse(multiplexer.isOriginSetInitialized());
    }

    @Test
    void ignoresFrameOverH2c() throws Exception {
        final ClientH2StreamMultiplexer multiplexer = newMultiplexer(false, H2Config.DEFAULT);
        multiplexer.consumeOriginFrame(originFrame(0, 0, ALT));
        Assertions.assertFalse(multiplexer.isOriginSetInitialized());
    }

    @Test
    void proxyPolicyCanDisableOriginFrames() throws Exception {
        final H2Config config = H2Config.custom().setOriginFrameEnabled(false).build();
        final ClientH2StreamMultiplexer multiplexer = newMultiplexer(true, config);
        multiplexer.consumeOriginFrame(originFrame(0, 0, ALT));
        Assertions.assertFalse(multiplexer.isOriginSetInitialized());
    }

    @Test
    void exceedingConfiguredSetLimitFailsConnection() throws Exception {
        final H2Config config = H2Config.custom().setMaxOriginSetSize(1).build();
        final ClientH2StreamMultiplexer multiplexer = newMultiplexer(true, config);

        final H2ConnectionException ex = Assertions.assertThrows(H2ConnectionException.class, () ->
                multiplexer.consumeOriginFrame(originFrame(0, 0, ALT)));

        Assertions.assertEquals(H2Error.ENHANCE_YOUR_CALM.getCode(), ex.getCode());
        Assertions.assertFalse(multiplexer.isOriginSetInitialized());
    }

    private static RawFrame originFrame(
            final int streamId, final int flags, final HttpHost origin) {
        final ByteBuffer payload = H2OriginFrameCodec.encode(Collections.singleton(origin), 16384).get(0);
        return new RawFrame(FrameType.ORIGIN.getValue(), flags, streamId, payload);
    }

    private static ByteBuffer wireFrame(
            final int type, final int flags, final int streamId, final ByteBuffer payload) {
        final int length = payload != null ? payload.remaining() : 0;
        final ByteBuffer frame = ByteBuffer.allocate(9 + length);
        frame.put((byte) (length >>> 16));
        frame.put((byte) (length >>> 8));
        frame.put((byte) length);
        frame.put((byte) type);
        frame.put((byte) flags);
        frame.putInt(streamId);
        if (payload != null) {
            frame.put(payload.duplicate());
        }
        frame.flip();
        return frame;
    }

    private static ClientH2StreamMultiplexer newMultiplexer(final boolean tls, final H2Config config) {
        final ProtocolIOSession ioSession = Mockito.mock(ProtocolIOSession.class);
        Mockito.when(ioSession.getLock()).thenReturn(new ReentrantLock());
        Mockito.when(ioSession.getInitialEndpoint()).thenReturn(INITIAL);
        Mockito.when(ioSession.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 443));
        if (tls) {
            Mockito.when(ioSession.getTlsDetails()).thenReturn(
                    new TlsDetails(Mockito.mock(SSLSession.class), "h2"));
        }
        final HttpProcessor httpProcessor = Mockito.mock(HttpProcessor.class);
        @SuppressWarnings("unchecked")
        final HandlerFactory<AsyncPushConsumer> pushHandlerFactory =
                (HandlerFactory<AsyncPushConsumer>) Mockito.mock(HandlerFactory.class);
        return new ClientH2StreamMultiplexer(
                ioSession,
                DefaultFrameFactory.INSTANCE,
                httpProcessor,
                pushHandlerFactory,
                config,
                CharCodingConfig.DEFAULT,
                null);
    }
}
