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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;

import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.AsyncPushProducer;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http2.H2ConnectionException;
import org.apache.hc.core5.http2.H2Error;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.config.H2Param;
import org.apache.hc.core5.http2.config.H2Setting;
import org.apache.hc.core5.http2.frame.DefaultFrameFactory;
import org.apache.hc.core5.http2.frame.FrameFactory;
import org.apache.hc.core5.http2.frame.FrameFlag;
import org.apache.hc.core5.http2.frame.FrameType;
import org.apache.hc.core5.http2.frame.RawFrame;
import org.apache.hc.core5.http2.frame.StreamIdGenerator;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Verifies that a peer flooding control frames while never reading the socket cannot drive the
 * per-connection output queue without bound. The output channel is held blocked so every control
 * response accumulates in the queue, reproducing the condition under which the server would
 * otherwise retain one buffered frame per inbound control frame.
 */
@ExtendWith(MockitoExtension.class)
class TestH2ControlFrameFloodOutputQueue {

    private static final FrameFactory FRAME_FACTORY = DefaultFrameFactory.INSTANCE;

    @Mock(strictness = Mock.Strictness.LENIENT)
    ProtocolIOSession ioSession;
    @Mock
    Lock lock;
    @Mock
    HttpProcessor httpProcessor;
    @Mock
    H2StreamHandler streamHandler;

    private volatile boolean writeBlocked;
    private final AtomicInteger pingAckOut = new AtomicInteger();

    @BeforeEach
    void prepareMocks() throws Exception {
        Mockito.when(ioSession.getLock()).thenReturn(lock);
        // When blocked, every write reports zero bytes flushed; otherwise the whole buffer drains.
        Mockito.lenient().when(ioSession.write(ArgumentMatchers.any(ByteBuffer.class))).thenAnswer(inv -> {
            final ByteBuffer src = inv.getArgument(0, ByteBuffer.class);
            if (writeBlocked) {
                return 0;
            }
            final int n = src.remaining();
            src.position(src.limit());
            return n;
        });
    }

    private final H2StreamListener frameCountingListener = new H2StreamListener() {
        @Override
        public void onHeaderInput(final HttpConnection c, final int id, final List<? extends Header> h) {
        }

        @Override
        public void onHeaderOutput(final HttpConnection c, final int id, final List<? extends Header> h) {
        }

        @Override
        public void onFrameInput(final HttpConnection c, final int id, final RawFrame f) {
        }

        @Override
        public void onFrameOutput(final HttpConnection c, final int id, final RawFrame f) {
            if (f.getType() == FrameType.PING.getValue() && f.isFlagSet(FrameFlag.ACK)) {
                pingAckOut.incrementAndGet();
            }
        }

        @Override
        public void onInputFlowControl(final HttpConnection c, final int id, final int d, final int s) {
        }

        @Override
        public void onOutputFlowControl(final HttpConnection c, final int id, final int d, final int s) {
        }
    };

    // Uses the default H2Config control-frame bound so this behaviour test compiles and runs both
    // with and without the production change under test.
    private AbstractH2StreamMultiplexer newMux() {
        return new Mux(ioSession, FRAME_FACTORY, StreamIdGenerator.ODD, httpProcessor, CharCodingConfig.DEFAULT,
                H2Config.custom().setPushEnabled(false).build(),
                frameCountingListener, () -> streamHandler);
    }

    private static byte[] pingFrame() {
        return new byte[] {0, 0, 8, 6, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    }

    private static byte[] emptySettingsFrame() {
        return new byte[] {0, 0, 0, 4, 0, 0, 0, 0, 0};
    }

    @Test
    @Timeout(30)
    void testControlFrameFloodIsRejectedWithEnhanceYourCalm() throws Exception {
        final AbstractH2StreamMultiplexer mux = newMux();
        writeBlocked = true;
        mux.onConnect();
        mux.onInput(ByteBuffer.wrap(emptySettingsFrame()));

        final byte[] ping = pingFrame();
        final H2ConnectionException ex = Assertions.assertThrows(H2ConnectionException.class, () -> {
            for (int i = 0; i < 100_000; i++) {
                mux.onInput(ByteBuffer.wrap(ping.clone()));
            }
        });
        Assertions.assertEquals(H2Error.ENHANCE_YOUR_CALM.getCode(), ex.getCode());
    }

    @Test
    @Timeout(30)
    void testBurstUnderLimitIsFullyAcknowledged() throws Exception {
        final AbstractH2StreamMultiplexer mux = newMux();
        writeBlocked = true;
        mux.onConnect();
        mux.onInput(ByteBuffer.wrap(emptySettingsFrame()));

        final int burst = 200;
        final byte[] ping = pingFrame();
        Assertions.assertDoesNotThrow(() -> {
            for (int i = 0; i < burst; i++) {
                mux.onInput(ByteBuffer.wrap(ping.clone()));
            }
        });

        // The socket becomes writable; every queued acknowledgement must be flushed.
        writeBlocked = false;
        mux.onOutput();
        Assertions.assertEquals(burst, pingAckOut.get());

        // The connection remains usable: a further PING is acknowledged normally.
        mux.onInput(ByteBuffer.wrap(ping.clone()));
        Assertions.assertEquals(burst + 1, pingAckOut.get());
    }

    @Test
    @Timeout(30)
    void testReadingPeerIsNeverLimited() throws Exception {
        final AbstractH2StreamMultiplexer mux = newMux();
        writeBlocked = false;
        mux.onConnect();
        mux.onInput(ByteBuffer.wrap(emptySettingsFrame()));

        final int count = 2000;
        final byte[] ping = pingFrame();
        Assertions.assertDoesNotThrow(() -> {
            for (int i = 0; i < count; i++) {
                mux.onInput(ByteBuffer.wrap(ping.clone()));
            }
        });
        Assertions.assertEquals(count, pingAckOut.get());
    }

    static final class Mux extends AbstractH2StreamMultiplexer {

        private final Supplier<H2StreamHandler> supplier;

        Mux(final ProtocolIOSession ioSession, final FrameFactory frameFactory, final StreamIdGenerator idGenerator,
            final HttpProcessor httpProcessor, final CharCodingConfig charCodingConfig, final H2Config h2Config,
            final H2StreamListener streamListener, final Supplier<H2StreamHandler> supplier) {
            super(ioSession, frameFactory, idGenerator, httpProcessor, charCodingConfig, h2Config, streamListener);
            this.supplier = supplier;
        }

        @Override
        void validateSetting(final H2Param param, final int value) {
        }

        @Override
        H2Setting[] generateSettings(final H2Config localConfig) {
            return new H2Setting[] {new H2Setting(H2Param.ENABLE_PUSH, 0)};
        }

        @Override
        void acceptHeaderFrame() {
        }

        @Override
        void acceptPushRequest() {
        }

        @Override
        void acceptPushFrame() {
        }

        @Override
        H2StreamHandler incomingRequest(final H2StreamChannel channel) {
            return supplier.get();
        }

        @Override
        H2StreamHandler outgoingRequest(final H2StreamChannel channel, final AsyncClientExchangeHandler exchangeHandler,
                                        final HandlerFactory<AsyncPushConsumer> pushHandlerFactory, final HttpContext context) {
            return null;
        }

        @Override
        H2StreamHandler incomingPushPromise(final H2StreamChannel channel,
                                            final HandlerFactory<AsyncPushConsumer> pushHandlerFactory) {
            return supplier.get();
        }

        @Override
        H2StreamHandler outgoingPushPromise(final H2StreamChannel channel, final AsyncPushProducer pushProducer) {
            return null;
        }

        @Override
        boolean allowGracefulAbort(final H2Stream stream) {
            return false;
        }
    }
}
