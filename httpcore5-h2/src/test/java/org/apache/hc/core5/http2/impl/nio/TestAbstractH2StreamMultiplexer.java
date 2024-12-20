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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.impl.BasicHttpConnectionMetrics;
import org.apache.hc.core5.http.impl.CharCodingSupport;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.nio.command.ExecutableCommand;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http2.H2ConnectionException;
import org.apache.hc.core5.http2.WritableByteChannelMock;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.frame.DefaultFrameFactory;
import org.apache.hc.core5.http2.frame.FrameConsts;
import org.apache.hc.core5.http2.frame.FrameFactory;
import org.apache.hc.core5.http2.frame.FrameType;
import org.apache.hc.core5.http2.frame.RawFrame;
import org.apache.hc.core5.http2.frame.StreamIdGenerator;
import org.apache.hc.core5.http2.hpack.HPackEncoder;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.util.ByteArrayBuffer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

class TestAbstractH2StreamMultiplexer {

    private static final FrameFactory FRAME_FACTORY = DefaultFrameFactory.INSTANCE;

    @Mock
    ProtocolIOSession protocolIOSession;
    @Mock
    HttpProcessor httpProcessor;
    @Mock
    H2StreamListener h2StreamListener;
    @Mock
    H2StreamHandler streamHandler;
    @Captor
    ArgumentCaptor<List<Header>> headersCaptor;

    @BeforeEach
    void prepareMocks() {
        MockitoAnnotations.openMocks(this);
    }

    static class H2StreamMultiplexerImpl extends AbstractH2StreamMultiplexer {

        private Supplier<H2StreamHandler> streamHandlerSupplier;

        public H2StreamMultiplexerImpl(
                final ProtocolIOSession ioSession,
                final FrameFactory frameFactory,
                final StreamIdGenerator idGenerator,
                final HttpProcessor httpProcessor,
                final CharCodingConfig charCodingConfig,
                final H2Config h2Config,
                final H2StreamListener streamListener,
                final Supplier<H2StreamHandler> streamHandlerSupplier) {
            super(ioSession, frameFactory, idGenerator, httpProcessor, charCodingConfig, h2Config, streamListener);
            this.streamHandlerSupplier = streamHandlerSupplier;
        }

        @Override
        void acceptHeaderFrame() throws H2ConnectionException {
        }

        @Override
        void acceptPushRequest() throws H2ConnectionException {
        }

        @Override
        void acceptPushFrame() throws H2ConnectionException {
        }

        @Override
        H2StreamHandler createRemotelyInitiatedStream(
                final H2StreamChannel channel,
                final HttpProcessor httpProcessor,
                final BasicHttpConnectionMetrics connMetrics,
                final HandlerFactory<AsyncPushConsumer> pushHandlerFactory) throws IOException {
            return streamHandlerSupplier.get();
        }

        @Override
        H2StreamHandler createLocallyInitiatedStream(
                final ExecutableCommand command,
                final H2StreamChannel channel,
                final HttpProcessor httpProcessor,
                final BasicHttpConnectionMetrics connMetrics) throws IOException {
            return null;
        }
    }

    @Test
    void testInputOneFrame() throws Exception {
        final WritableByteChannelMock writableChannel = new WritableByteChannelMock(1024);
        final FrameOutputBuffer outbuffer = new FrameOutputBuffer(16 * 1024);

        final byte[] data = new byte[FrameConsts.MIN_FRAME_SIZE];
        for (int i = 0; i < FrameConsts.MIN_FRAME_SIZE; i++) {
            data[i] = (byte)(i % 16);
        }

        final RawFrame frame = new RawFrame(FrameType.DATA.getValue(), 0, 1, ByteBuffer.wrap(data));
        outbuffer.write(frame, writableChannel);
        final byte[] bytes = writableChannel.toByteArray();

        final AbstractH2StreamMultiplexer streamMultiplexer = new H2StreamMultiplexerImpl(
                protocolIOSession,
                FRAME_FACTORY,
                StreamIdGenerator.ODD,
                httpProcessor,
                CharCodingConfig.DEFAULT,
                H2Config.custom()
                        .setMaxFrameSize(FrameConsts.MIN_FRAME_SIZE)
                        .build(),
                h2StreamListener,
                () -> streamHandler);

        Assertions.assertThrows(H2ConnectionException.class, () ->
                streamMultiplexer.onInput(ByteBuffer.wrap(bytes)));
        Mockito.verify(h2StreamListener).onFrameInput(
                ArgumentMatchers.same(streamMultiplexer),
                ArgumentMatchers.eq(1),
                ArgumentMatchers.any());

        Assertions.assertThrows(H2ConnectionException.class, () -> {
            int pos = 0;
            int remaining = bytes.length;
            while (remaining > 0) {
                final int chunk = Math.min(2048, remaining);
                streamMultiplexer.onInput(ByteBuffer.wrap(bytes, pos, chunk));
                pos += chunk;
                remaining -= chunk;
            }

            Mockito.verify(h2StreamListener).onFrameInput(
                    ArgumentMatchers.same(streamMultiplexer),
                    ArgumentMatchers.eq(1),
                    ArgumentMatchers.any());
        });
    }

    @Test
    void testInputMultipleFrames() throws Exception {
        final WritableByteChannelMock writableChannel = new WritableByteChannelMock(1024);
        final FrameOutputBuffer outbuffer = new FrameOutputBuffer(16 * 1024);

        final byte[] data = new byte[FrameConsts.MIN_FRAME_SIZE];
        for (int i = 0; i < FrameConsts.MIN_FRAME_SIZE; i++) {
            data[i] = (byte)(i % 16);
        }

        final RawFrame frame1 = new RawFrame(FrameType.DATA.getValue(), 0, 1, ByteBuffer.wrap(data));
        outbuffer.write(frame1, writableChannel);
        final RawFrame frame2 = new RawFrame(FrameType.DATA.getValue(), 0, 1, ByteBuffer.wrap(data));
        outbuffer.write(frame2, writableChannel);
        final byte[] bytes = writableChannel.toByteArray();

        final AbstractH2StreamMultiplexer streamMultiplexer = new H2StreamMultiplexerImpl(
                protocolIOSession,
                FRAME_FACTORY,
                StreamIdGenerator.ODD,
                httpProcessor,
                CharCodingConfig.DEFAULT,
                H2Config.custom()
                        .setMaxFrameSize(FrameConsts.MIN_FRAME_SIZE)
                        .build(),
                h2StreamListener,
                () -> streamHandler);

        Assertions.assertThrows(H2ConnectionException.class, () ->
                streamMultiplexer.onInput(ByteBuffer.wrap(bytes)));
        Mockito.verify(h2StreamListener).onFrameInput(
                ArgumentMatchers.same(streamMultiplexer),
                ArgumentMatchers.eq(1),
                ArgumentMatchers.any());

        Assertions.assertThrows(H2ConnectionException.class, () -> {
            int pos = 0;
            int remaining = bytes.length;
            while (remaining > 0) {
                final int chunk = Math.min(4096, remaining);
                streamMultiplexer.onInput(ByteBuffer.wrap(bytes, pos, chunk));
                pos += chunk;
                remaining -= chunk;
            }

            Mockito.verify(h2StreamListener).onFrameInput(
                    ArgumentMatchers.same(streamMultiplexer),
                    ArgumentMatchers.eq(1),
                    ArgumentMatchers.any());
        });
    }

    @Test
    void testInputHeaderContinuationFrame() throws IOException, HttpException {
        final H2Config h2Config = H2Config.custom().setMaxFrameSize(FrameConsts.MIN_FRAME_SIZE)
                .build();

        final ByteArrayBuffer buf = new ByteArrayBuffer(19);
        final HPackEncoder encoder = new HPackEncoder(H2Config.INIT.getHeaderTableSize(), CharCodingSupport.createEncoder(CharCodingConfig.DEFAULT));
        final List<Header> headers = new ArrayList<>();
        headers.add(new BasicHeader("test-header-key", "value"));
        headers.add(new BasicHeader(":status", "200"));
        encoder.encodeHeaders(buf, headers, h2Config.isCompressionEnabled());

        final WritableByteChannelMock writableChannel = new WritableByteChannelMock(1024);
        final FrameOutputBuffer outBuffer = new FrameOutputBuffer(16 * 1024);

        final RawFrame headerFrame = FRAME_FACTORY.createHeaders(2, ByteBuffer.wrap(buf.array(), 0, 10), false, false);
        outBuffer.write(headerFrame, writableChannel);
        final RawFrame continuationFrame = FRAME_FACTORY.createContinuation(2, ByteBuffer.wrap(buf.array(), 10, 9), true);
        outBuffer.write(continuationFrame, writableChannel);
        final byte[] bytes = writableChannel.toByteArray();

        final AbstractH2StreamMultiplexer streamMultiplexer = new H2StreamMultiplexerImpl(
                protocolIOSession,
                FRAME_FACTORY,
                StreamIdGenerator.ODD,
                httpProcessor,
                CharCodingConfig.DEFAULT,
                h2Config,
                h2StreamListener,
                () -> streamHandler);

        streamMultiplexer.onInput(ByteBuffer.wrap(bytes));
        Mockito.verify(streamHandler).consumeHeader(headersCaptor.capture(), ArgumentMatchers.eq(false));
        Assertions.assertFalse(headersCaptor.getValue().isEmpty());
    }


    @Test
    void testUpdateWindowBehaviorComparison() {
        AtomicInteger window = new AtomicInteger(1);  // Start with window at 1

        // Test with new implementation where overflow should be capped at Integer.MAX_VALUE
        int resultNew = updateWindowNew(window, Integer.MAX_VALUE);
        System.out.println("New implementation: Current window size after attempted overflow: " + resultNew);
        Assertions.assertEquals(Integer.MAX_VALUE, resultNew, "New implementation should cap at Integer.MAX_VALUE");

        // Reset for old implementation test
        window = new AtomicInteger(1);

        // Test with old implementation where an overflow should throw an ArithmeticException
        try {
            updateWindowOld(window, Integer.MAX_VALUE);
            Assertions.fail("Old implementation should throw ArithmeticException for overflow");
        } catch (final ArithmeticException e) {
            System.out.println("Old implementation: ArithmeticException caught: " + e.getMessage());
        }

        // Test non-overflow scenario for both implementations
        window = new AtomicInteger(1);
        final int resultNewSafe = updateWindowNew(window, Integer.MAX_VALUE / 2 - 1);
        System.out.println("New safe update result: " + resultNewSafe);
        window = new AtomicInteger(1); // Reset for old test
        final int resultOldSafe = updateWindowOld(window, Integer.MAX_VALUE / 2 - 1);
        System.out.println("Old safe update result: " + resultOldSafe);

        Assertions.assertEquals(Integer.MAX_VALUE / 2, resultNewSafe, "New implementation safe update");
        Assertions.assertEquals(Integer.MAX_VALUE / 2, resultOldSafe, "Old implementation safe update");
        // Test capping behavior from below Integer.MAX_VALUE with new implementation
        window = new AtomicInteger(Integer.MAX_VALUE - 1);
        resultNew = updateWindowNew(window, 2);
        System.out.println("New implementation: Current window size after attempted overflow from below: " + resultNew);
        Assertions.assertEquals(Integer.MAX_VALUE, resultNew, "New implementation should cap at Integer.MAX_VALUE from below");

        // Test overflow from below with old implementation
        window = new AtomicInteger(Integer.MAX_VALUE - 1);
        try {
            updateWindowOld(window, 2);
            Assertions.fail("Old implementation should throw ArithmeticException for overflow from below");
        } catch (final ArithmeticException e) {
            System.out.println("Old implementation: ArithmeticException caught for overflow from below: " + e.getMessage());
        }
    }

    // New implementation of updateWindow method with capping
        private int updateWindowNew(final AtomicInteger window, final int delta) {
        for (;;) {
            final int current = window.get();
            long newValue = (long) current + delta;

            if (newValue > Integer.MAX_VALUE) {
                newValue = Integer.MAX_VALUE;
            } else if (newValue < Integer.MIN_VALUE) {
                newValue = Integer.MIN_VALUE;
            }

            if (window.compareAndSet(current, (int) newValue)) {
                return (int) newValue;
            }
        }
    }

    // Old implementation of updateWindow method which throws exception on overflow
    private int updateWindowOld(final AtomicInteger window, final int delta) throws ArithmeticException {
        for (;;) {
            final int current = window.get();
            System.out.println("Old - Current: " + current + ", Delta: " + delta);
            final long newValue = (long) current + delta;
            System.out.println("Old - New value before check: " + newValue);
            if (Math.abs(newValue) > 0x7fffffffL) {
                throw new ArithmeticException("Update causes flow control window to exceed " + Integer.MAX_VALUE);
            }
            if (window.compareAndSet(current, (int) newValue)) {
                System.out.println("Old - New value set: " + (int) newValue);
                return (int) newValue;
            }
        }
    }
}

