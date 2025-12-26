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


import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import javax.net.ssl.SSLSession;

import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.frame.DefaultFrameFactory;
import org.apache.hc.core5.http2.frame.FrameType;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ServerH2OriginRFC8336Test {

    private ProtocolIOSession ioSession;
    private HttpProcessor httpProcessor;
    private HandlerFactory<AsyncServerExchangeHandler> exchangeHandlerFactory;

    @BeforeEach
    void setUp() {
        ioSession = mock(ProtocolIOSession.class, RETURNS_DEEP_STUBS);
        when(ioSession.getId()).thenReturn("test-conn");
        when(ioSession.getLock()).thenReturn(new ReentrantLock());
        when(ioSession.isOpen()).thenReturn(true);
        when(ioSession.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 443));
        httpProcessor = mock(HttpProcessor.class);
        @SuppressWarnings("unchecked") final HandlerFactory<AsyncServerExchangeHandler> f = (HandlerFactory<AsyncServerExchangeHandler>) mock(HandlerFactory.class);
        exchangeHandlerFactory = f;
    }

    private ServerH2StreamMultiplexer newMuxWithTLS() {
        final SSLSession sslSession = mock(SSLSession.class);
        final TlsDetails tlsDetails = new TlsDetails(sslSession, "h2");
        when(ioSession.getTlsDetails()).thenReturn(tlsDetails);

        return new ServerH2StreamMultiplexer(
                ioSession,
                DefaultFrameFactory.INSTANCE,
                httpProcessor,
                exchangeHandlerFactory,
                CharCodingConfig.DEFAULT,
                H2Config.DEFAULT,
                null
        );
    }

    private ServerH2StreamMultiplexer newMuxWithoutTLS() {
        when(ioSession.getTlsDetails()).thenReturn(null);
        return new ServerH2StreamMultiplexer(
                ioSession,
                DefaultFrameFactory.INSTANCE,
                httpProcessor,
                exchangeHandlerFactory,
                CharCodingConfig.DEFAULT,
                H2Config.DEFAULT,
                null
        );
    }

    private static ByteBuffer makeOriginPayload(final List<String> origins) {
        int total = 0;
        for (final String s : origins) {
            final byte[] b = s.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            total += 2 + b.length;
        }
        final ByteBuffer pl = ByteBuffer.allocate(total);
        for (final String s : origins) {
            final byte[] b = s.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            pl.putShort((short) (b.length & 0xFFFF));
            pl.put(b);
        }
        pl.flip();
        return pl;
    }

    private static ByteBuffer h2Frame(final byte type, final byte flags, final int streamId, final ByteBuffer payload) {
        final int len = payload == null ? 0 : payload.remaining();
        final ByteBuffer buf = ByteBuffer.allocate(9 + len);
        buf.put((byte) ((len >>> 16) & 0xFF));
        buf.put((byte) ((len >>> 8) & 0xFF));
        buf.put((byte) (len & 0xFF));
        buf.put(type);
        buf.put(flags);
        buf.putInt(streamId & 0x7FFFFFFF);
        if (payload != null) {
            buf.put(payload.slice());
        }
        buf.flip();
        return buf;
    }

    @Test
    void originFrame_overTLS_parsesAndStoresHosts() throws Exception {
        final ServerH2StreamMultiplexer mux = newMuxWithTLS();

        final ByteBuffer payload = makeOriginPayload(Arrays.asList(
                "https://a.example:443",
                "https://b.example:8443",
                "https://c.example" // no port -> default 443
        ));
        final ByteBuffer frame = h2Frame((byte) FrameType.ORIGIN.getValue(), (byte) 0x00, 0, payload);

        mux.onInput(frame);

        final Set<HttpHost> set = ((AbstractH2StreamMultiplexer) mux).getOriginSetSnapshot();
        assertTrue(set.contains(new HttpHost("https", "a.example", 443)));
        assertTrue(set.contains(new HttpHost("https", "b.example", 8443)));
        assertTrue(set.contains(new HttpHost("https", "c.example", 443)));
    }

    @Test
    void originFrame_withoutTLS_isIgnored() throws Exception {
        final ServerH2StreamMultiplexer mux = newMuxWithoutTLS();

        final ByteBuffer payload = makeOriginPayload(Arrays.asList("https://ignored.example:443"));
        final ByteBuffer frame = h2Frame((byte) FrameType.ORIGIN.getValue(), (byte) 0x00, 0, payload);

        mux.onInput(frame);

        final Set<HttpHost> set = ((AbstractH2StreamMultiplexer) mux).getOriginSetSnapshot();
        assertFalse(set.contains(new HttpHost("https", "ignored.example", 443)));
    }

    @Test
    void originFrame_withNonZeroLowerFlags_isIgnored() throws Exception {
        final ServerH2StreamMultiplexer mux = newMuxWithTLS();

        final ByteBuffer payload = makeOriginPayload(Collections.singletonList("https://flags.example:443"));
        // lower 4 bits non-zero -> ignore per RFC 8336
        final ByteBuffer frame = h2Frame((byte) FrameType.ORIGIN.getValue(), (byte) 0x01, 0, payload);

        mux.onInput(frame);

        final Set<HttpHost> set = ((AbstractH2StreamMultiplexer) mux).getOriginSetSnapshot();
        assertFalse(set.contains(new HttpHost("https", "flags.example", 443)));
    }

    @Test
    void sendOrigin_enqueuesAndSignalsWrite() throws Exception {
        final ServerH2StreamMultiplexer mux = newMuxWithTLS();

        mux.sendOrigin(Collections.singletonList("https://emit.example:443"));

        verify(ioSession, atLeastOnce()).setEvent(SelectionKey.OP_WRITE);
    }

    @Test
    void sendOrigin_emptyPayload_okAndSignalsWrite() throws Exception {
        final ServerH2StreamMultiplexer mux = newMuxWithTLS();

        mux.sendOrigin(java.util.Collections.emptyList());

        verify(ioSession, atLeastOnce()).setEvent(SelectionKey.OP_WRITE);
    }
}
