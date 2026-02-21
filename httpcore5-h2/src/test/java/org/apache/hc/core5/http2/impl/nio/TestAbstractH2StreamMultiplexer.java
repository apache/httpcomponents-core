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
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.stream.IntStream;

import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.impl.CharCodingSupport;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.AsyncPushProducer;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http2.H2ConnectionException;
import org.apache.hc.core5.http2.H2Error;
import org.apache.hc.core5.http2.H2StreamResetException;
import org.apache.hc.core5.http2.H2StreamTimeoutException;
import org.apache.hc.core5.http2.WritableByteChannelMock;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.config.H2Param;
import org.apache.hc.core5.http2.config.H2Setting;
import org.apache.hc.core5.http2.frame.DefaultFrameFactory;
import org.apache.hc.core5.http2.frame.FrameConsts;
import org.apache.hc.core5.http2.frame.FrameFactory;
import org.apache.hc.core5.http2.frame.FrameFlag;
import org.apache.hc.core5.http2.frame.FrameType;
import org.apache.hc.core5.http2.frame.RawFrame;
import org.apache.hc.core5.http2.frame.StreamIdGenerator;
import org.apache.hc.core5.http2.hpack.HPackEncoder;
import org.apache.hc.core5.http2.hpack.HPackException;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.util.ByteArrayBuffer;
import org.apache.hc.core5.util.Timeout;
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
    Lock lock;
    @Mock
    HttpProcessor httpProcessor;
    @Mock
    H2StreamListener h2StreamListener;
    @Mock
    H2StreamHandler streamHandler;
    @Captor
    ArgumentCaptor<List<Header>> headersCaptor;
    @Captor
    ArgumentCaptor<Exception> exceptionCaptor;

    @BeforeEach
    void prepareMocks() {
        MockitoAnnotations.openMocks(this);
        Mockito.when(protocolIOSession.getLock()).thenReturn(lock);
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
            this(ioSession, frameFactory, idGenerator, httpProcessor, charCodingConfig, h2Config, streamListener,
                    streamHandlerSupplier, null);
        }

        public H2StreamMultiplexerImpl(
                final ProtocolIOSession ioSession,
                final FrameFactory frameFactory,
                final StreamIdGenerator idGenerator,
                final HttpProcessor httpProcessor,
                final CharCodingConfig charCodingConfig,
                final H2Config h2Config,
                final H2StreamListener streamListener,
                final Supplier<H2StreamHandler> streamHandlerSupplier,
                final Timeout validateAfterInactivity) {
            super(ioSession, frameFactory, idGenerator, httpProcessor, charCodingConfig, h2Config, streamListener,
                    validateAfterInactivity);
            this.streamHandlerSupplier = streamHandlerSupplier;
        }

        @Override
        void validateSetting(final H2Param param, final int value) throws H2ConnectionException {
        }

        @Override
        H2Setting[] generateSettings(final H2Config localConfig) {
            return new H2Setting[] {
                    new H2Setting(H2Param.HEADER_TABLE_SIZE, localConfig.getHeaderTableSize()),
                    new H2Setting(H2Param.ENABLE_PUSH, localConfig.isPushEnabled() ? 1 : 0),
                    new H2Setting(H2Param.MAX_CONCURRENT_STREAMS, localConfig.getMaxConcurrentStreams()),
                    new H2Setting(H2Param.INITIAL_WINDOW_SIZE, localConfig.getInitialWindowSize()),
                    new H2Setting(H2Param.MAX_FRAME_SIZE, localConfig.getMaxFrameSize()),
                    new H2Setting(H2Param.MAX_HEADER_LIST_SIZE, localConfig.getMaxHeaderListSize())
            };
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
        H2StreamHandler incomingRequest(final H2StreamChannel channel) {
            return streamHandlerSupplier.get();
        }

        @Override
        H2StreamHandler outgoingRequest(final H2StreamChannel channel,
                                        final AsyncClientExchangeHandler exchangeHandler,
                                        final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
                                        final HttpContext context) {
            return null;
        }

        @Override
        H2StreamHandler incomingPushPromise(final H2StreamChannel channel,
                                            final HandlerFactory<AsyncPushConsumer> pushHandlerFactory) {
            return streamHandlerSupplier.get();
        }

        @Override
        H2StreamHandler outgoingPushPromise(final H2StreamChannel channel,
                                            final AsyncPushProducer pushProducer) {
            return null;
        }

        @Override
        boolean allowGracefulAbort(final H2Stream stream) {
            return stream.isRemoteClosed() && !stream.isLocalClosed();
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
    void testInputHeaderContinuationFrame() throws Exception {
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
    void testZeroIncrement() throws Exception {
        final H2Config h2Config = H2Config.custom()
                .build();

        final AbstractH2StreamMultiplexer streamMultiplexer = new H2StreamMultiplexerImpl(
                protocolIOSession,
                FRAME_FACTORY,
                StreamIdGenerator.EVEN,
                httpProcessor,
                CharCodingConfig.DEFAULT,
                h2Config,
                h2StreamListener,
                () -> streamHandler);

        final ByteArrayBuffer headerBuf = new ByteArrayBuffer(200);
        final HPackEncoder encoder = new HPackEncoder(h2Config.getHeaderTableSize(),
                CharCodingSupport.createEncoder(CharCodingConfig.DEFAULT));

        final List<Header> headers = Arrays.asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":scheme", "http"),
                new BasicHeader(":path", "/"),
                new BasicHeader(":authority", "www.example.com"));
        encoder.encodeHeaders(headerBuf, headers, h2Config.isCompressionEnabled());

        final WritableByteChannelMock writableChannel = new WritableByteChannelMock(1024);
        final FrameOutputBuffer outBuffer = new FrameOutputBuffer(16 * 1024);

        final RawFrame headerFrame = FRAME_FACTORY.createHeaders(1, ByteBuffer.wrap(headerBuf.array(), 0, headerBuf.length()), true, true);
        outBuffer.write(headerFrame, writableChannel);

        streamMultiplexer.onInput(ByteBuffer.wrap(writableChannel.toByteArray()));

        Mockito.verify(streamHandler).consumeHeader(headersCaptor.capture(), ArgumentMatchers.eq(true));
        Assertions.assertFalse(headersCaptor.getValue().isEmpty());

        writableChannel.reset();
        final ByteBuffer payload = ByteBuffer.allocate(4);
        payload.putInt(0);
        payload.flip();
        final RawFrame incrementFrame = new RawFrame(FrameType.WINDOW_UPDATE.getValue(), 0, 1, payload);
        outBuffer.write(incrementFrame, writableChannel);

        final H2ConnectionException exception = Assertions.assertThrows(H2ConnectionException.class, () ->
                streamMultiplexer.onInput(ByteBuffer.wrap(writableChannel.toByteArray())));
        Assertions.assertEquals(H2Error.PROTOCOL_ERROR, H2Error.getByCode(exception.getCode()));
    }

    @Test
    void testZeroIncrementConnectionWindowUpdate() throws Exception {
        final AbstractH2StreamMultiplexer mux = new H2StreamMultiplexerImpl(
                protocolIOSession,
                FRAME_FACTORY,
                StreamIdGenerator.ODD,
                httpProcessor,
                CharCodingConfig.DEFAULT,
                H2Config.custom().build(),
                h2StreamListener,
                () -> streamHandler);
        try {
            final ByteBuffer payload = ByteBuffer.allocate(4);
            payload.putInt(0);
            payload.flip();
            final RawFrame incrementFrame = new RawFrame(FrameType.WINDOW_UPDATE.getValue(), 0, 0, payload);

            final H2ConnectionException exception = Assertions.assertThrows(H2ConnectionException.class,
                    () -> mux.onInput(ByteBuffer.wrap(encodeFrame(incrementFrame))));
            Assertions.assertEquals(H2Error.PROTOCOL_ERROR, H2Error.getByCode(exception.getCode()));
        } finally {
            mux.close();
        }
    }

    @Test
    void testInvalidInitialWindowSizeSettingIsFlowControlError() throws Exception {
        final AbstractH2StreamMultiplexer mux = new H2StreamMultiplexerImpl(
                protocolIOSession,
                FRAME_FACTORY,
                StreamIdGenerator.ODD,
                httpProcessor,
                CharCodingConfig.DEFAULT,
                H2Config.custom().build(),
                h2StreamListener,
                () -> streamHandler);
        try {
            final ByteBuffer payload = ByteBuffer.allocate(6);
            payload.putShort((short) H2Param.INITIAL_WINDOW_SIZE.getCode());
            payload.putInt(-1);
            payload.flip();
            final RawFrame settingsFrame = new RawFrame(FrameType.SETTINGS.getValue(), 0, 0, payload);

            final H2ConnectionException exception = Assertions.assertThrows(H2ConnectionException.class,
                    () -> mux.onInput(ByteBuffer.wrap(encodeFrame(settingsFrame))));
            Assertions.assertEquals(H2Error.FLOW_CONTROL_ERROR, H2Error.getByCode(exception.getCode()));
        } finally {
            mux.close();
        }
    }

    @Test
    void testIncrementOverflow() throws Exception {
        final H2Config h2Config = H2Config.custom()
                .build();

        final AbstractH2StreamMultiplexer streamMultiplexer = new H2StreamMultiplexerImpl(
                protocolIOSession,
                FRAME_FACTORY,
                StreamIdGenerator.EVEN,
                httpProcessor,
                CharCodingConfig.DEFAULT,
                h2Config,
                h2StreamListener,
                () -> streamHandler);

        final ByteArrayBuffer headerBuf = new ByteArrayBuffer(200);
        final HPackEncoder encoder = new HPackEncoder(h2Config.getHeaderTableSize(),
                CharCodingSupport.createEncoder(CharCodingConfig.DEFAULT));

        final List<Header> headers = Arrays.asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":scheme", "http"),
                new BasicHeader(":path", "/"),
                new BasicHeader(":authority", "www.example.com"));
        encoder.encodeHeaders(headerBuf, headers, h2Config.isCompressionEnabled());

        final WritableByteChannelMock writableChannel = new WritableByteChannelMock(1024);
        final FrameOutputBuffer outBuffer = new FrameOutputBuffer(16 * 1024);

        final RawFrame headerFrame = FRAME_FACTORY.createHeaders(1, ByteBuffer.wrap(headerBuf.array(), 0, headerBuf.length()), true, true);
        outBuffer.write(headerFrame, writableChannel);

        streamMultiplexer.onInput(ByteBuffer.wrap(writableChannel.toByteArray()));

        Mockito.verify(streamHandler).consumeHeader(headersCaptor.capture(), ArgumentMatchers.eq(true));
        Assertions.assertFalse(headersCaptor.getValue().isEmpty());

        writableChannel.reset();
        final RawFrame incrementFrame1 = FRAME_FACTORY.createWindowUpdate(1, 100);
        outBuffer.write(incrementFrame1, writableChannel);

        streamMultiplexer.onInput(ByteBuffer.wrap(writableChannel.toByteArray()));

        writableChannel.reset();
        final RawFrame incrementFrame2 = FRAME_FACTORY.createWindowUpdate(1, 0x7fffffff - 50);
        outBuffer.write(incrementFrame2, writableChannel);
        final H2ConnectionException exception = Assertions.assertThrows(H2ConnectionException.class, () ->
                streamMultiplexer.onInput(ByteBuffer.wrap(writableChannel.toByteArray())));
        Assertions.assertEquals(H2Error.FLOW_CONTROL_ERROR, H2Error.getByCode(exception.getCode()));
    }

    @Test
    void testHeadersAfterEndOfStream() throws Exception {
        final H2Config h2Config = H2Config.custom()
                .build();

        final AbstractH2StreamMultiplexer streamMultiplexer = new H2StreamMultiplexerImpl(
                protocolIOSession,
                FRAME_FACTORY,
                StreamIdGenerator.EVEN,
                httpProcessor,
                CharCodingConfig.DEFAULT,
                h2Config,
                h2StreamListener,
                () -> streamHandler);

        final ByteArrayBuffer headerBuf = new ByteArrayBuffer(200);
        final HPackEncoder encoder = new HPackEncoder(h2Config.getHeaderTableSize(),
                CharCodingSupport.createEncoder(CharCodingConfig.DEFAULT));

        final List<Header> headers = Arrays.asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":scheme", "http"),
                new BasicHeader(":path", "/"),
                new BasicHeader(":authority", "www.example.com"));
        encoder.encodeHeaders(headerBuf, headers, h2Config.isCompressionEnabled());

        final WritableByteChannelMock writableChannel = new WritableByteChannelMock(1024);
        final FrameOutputBuffer outBuffer = new FrameOutputBuffer(16 * 1024);

        final RawFrame headerFrame1 = FRAME_FACTORY.createHeaders(1, ByteBuffer.wrap(headerBuf.array(), 0, headerBuf.length()), true, true);
        outBuffer.write(headerFrame1, writableChannel);

        streamMultiplexer.onInput(ByteBuffer.wrap(writableChannel.toByteArray()));

        Mockito.verify(streamHandler).consumeHeader(headersCaptor.capture(), ArgumentMatchers.eq(true));
        Assertions.assertFalse(headersCaptor.getValue().isEmpty());

        writableChannel.reset();
        final RawFrame headerFrame2 = FRAME_FACTORY.createHeaders(1, ByteBuffer.wrap(headerBuf.array(), 0, headerBuf.length()), true, true);
        outBuffer.write(headerFrame2, writableChannel);

        // Treat the first occurrence as a stream error
        streamMultiplexer.onInput(ByteBuffer.wrap(writableChannel.toByteArray()));
        Mockito.verify(streamHandler).failed(exceptionCaptor.capture());
        Assertions.assertInstanceOf(H2StreamResetException.class, exceptionCaptor.getValue());

        writableChannel.reset();
        final RawFrame headerFrame3 = FRAME_FACTORY.createHeaders(1, ByteBuffer.wrap(headerBuf.array(), 0, headerBuf.length()), true, true);
        outBuffer.write(headerFrame3, writableChannel);

        // Treat subsequent occurrences as a connection-wide protocol error
        final H2ConnectionException exception = Assertions.assertThrows(H2ConnectionException.class, () ->
                streamMultiplexer.onInput(ByteBuffer.wrap(writableChannel.toByteArray())));
        Assertions.assertEquals(H2Error.STREAM_CLOSED, H2Error.getByCode(exception.getCode()));
    }

    @Test
    void testDataAfterEndOfStream() throws Exception {
        final H2Config h2Config = H2Config.custom()
                .build();

        final AbstractH2StreamMultiplexer streamMultiplexer = new H2StreamMultiplexerImpl(
                protocolIOSession,
                FRAME_FACTORY,
                StreamIdGenerator.EVEN,
                httpProcessor,
                CharCodingConfig.DEFAULT,
                h2Config,
                h2StreamListener,
                () -> streamHandler);

        final ByteArrayBuffer headerBuf = new ByteArrayBuffer(200);
        final HPackEncoder encoder = new HPackEncoder(h2Config.getHeaderTableSize(),
                CharCodingSupport.createEncoder(CharCodingConfig.DEFAULT));

        final List<Header> headers = Arrays.asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":scheme", "http"),
                new BasicHeader(":path", "/"),
                new BasicHeader(":authority", "www.example.com"));
        encoder.encodeHeaders(headerBuf, headers, h2Config.isCompressionEnabled());

        final WritableByteChannelMock writableChannel = new WritableByteChannelMock(1024);
        final FrameOutputBuffer outBuffer = new FrameOutputBuffer(16 * 1024);

        final RawFrame headerFrame1 = FRAME_FACTORY.createHeaders(1, ByteBuffer.wrap(headerBuf.array(), 0, headerBuf.length()), true, true);
        outBuffer.write(headerFrame1, writableChannel);

        streamMultiplexer.onInput(ByteBuffer.wrap(writableChannel.toByteArray()));

        Mockito.verify(streamHandler).consumeHeader(headersCaptor.capture(), ArgumentMatchers.eq(true));
        Assertions.assertFalse(headersCaptor.getValue().isEmpty());

        writableChannel.reset();
        final RawFrame dataFrame1 = FRAME_FACTORY.createData(1, ByteBuffer.wrap(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 0}), true);
        outBuffer.write(dataFrame1, writableChannel);

        // Treat the first occurrence as a stream error
        streamMultiplexer.onInput(ByteBuffer.wrap(writableChannel.toByteArray()));
        Mockito.verify(streamHandler).failed(exceptionCaptor.capture());
        Assertions.assertInstanceOf(H2StreamResetException.class, exceptionCaptor.getValue());

        writableChannel.reset();
        final RawFrame dataFrame2 = FRAME_FACTORY.createData(1, ByteBuffer.wrap(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 0}), true);
        outBuffer.write(dataFrame2, writableChannel);

        // Treat subsequent occurrences as a connection-wide protocol error
        final H2ConnectionException exception = Assertions.assertThrows(H2ConnectionException.class, () ->
                streamMultiplexer.onInput(ByteBuffer.wrap(writableChannel.toByteArray())));
        Assertions.assertEquals(H2Error.STREAM_CLOSED, H2Error.getByCode(exception.getCode()));
    }

    @Test
    void testContinuationAfterEndOfStream() throws Exception {
        final H2Config h2Config = H2Config.custom()
                .build();

        final AbstractH2StreamMultiplexer streamMultiplexer = new H2StreamMultiplexerImpl(
                protocolIOSession,
                FRAME_FACTORY,
                StreamIdGenerator.EVEN,
                httpProcessor,
                CharCodingConfig.DEFAULT,
                h2Config,
                h2StreamListener,
                () -> streamHandler);

        final ByteArrayBuffer headerBuf = new ByteArrayBuffer(200);
        final HPackEncoder encoder = new HPackEncoder(h2Config.getHeaderTableSize(),
                CharCodingSupport.createEncoder(CharCodingConfig.DEFAULT));

        final List<Header> headers = Arrays.asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":scheme", "http"),
                new BasicHeader(":path", "/"),
                new BasicHeader(":authority", "www.example.com"));
        encoder.encodeHeaders(headerBuf, headers, h2Config.isCompressionEnabled());

        final WritableByteChannelMock writableChannel = new WritableByteChannelMock(1024);
        final FrameOutputBuffer outBuffer = new FrameOutputBuffer(16 * 1024);

        final RawFrame headerFrame1 = FRAME_FACTORY.createHeaders(1, ByteBuffer.wrap(headerBuf.array(), 0, headerBuf.length()), true, true);
        outBuffer.write(headerFrame1, writableChannel);

        streamMultiplexer.onInput(ByteBuffer.wrap(writableChannel.toByteArray()));

        Mockito.verify(streamHandler).consumeHeader(headersCaptor.capture(), ArgumentMatchers.eq(true));
        Assertions.assertFalse(headersCaptor.getValue().isEmpty());

        writableChannel.reset();
        final RawFrame continuationFrame = FRAME_FACTORY.createContinuation(1, ByteBuffer.wrap(headerBuf.array(), 0, headerBuf.length()), true);
        outBuffer.write(continuationFrame, writableChannel);

        final H2ConnectionException exception = Assertions.assertThrows(H2ConnectionException.class, () ->
                streamMultiplexer.onInput(ByteBuffer.wrap(writableChannel.toByteArray())));
        Assertions.assertEquals(H2Error.PROTOCOL_ERROR, H2Error.getByCode(exception.getCode()));
    }


    @Test
    void testInputHeaderContinuationFramesNoLimit() throws Exception {
        final H2Config h2Config = H2Config.custom()
                .setMaxContinuations(Integer.MAX_VALUE)
                .build();

        final ByteArrayBuffer headerBuf = new ByteArrayBuffer(19);
        final HPackEncoder encoder = new HPackEncoder(H2Config.INIT.getHeaderTableSize(), CharCodingSupport.createEncoder(CharCodingConfig.DEFAULT));
        final List<Header> headers = new ArrayList<>();
        headers.add(new BasicHeader(":status", "200"));
        for (int i = 1; i <= 100; i++) {
            headers.add(new BasicHeader("test-header-key-" + i, "value-" + i));
        }
        encoder.encodeHeaders(headerBuf, headers, h2Config.isCompressionEnabled());

        Assertions.assertTrue(headerBuf.length() > 750);

        final AbstractH2StreamMultiplexer streamMultiplexer = new H2StreamMultiplexerImpl(
                protocolIOSession,
                FRAME_FACTORY,
                StreamIdGenerator.ODD,
                httpProcessor,
                CharCodingConfig.DEFAULT,
                h2Config,
                h2StreamListener,
                () -> streamHandler);

        final WritableByteChannelMock writableChannel = new WritableByteChannelMock(1024);
        final FrameOutputBuffer outBuffer = new FrameOutputBuffer(16 * 1024);

        final RawFrame headerFrame = FRAME_FACTORY.createHeaders(2, ByteBuffer.wrap(headerBuf.array(), 0, 250), false, false);
        outBuffer.write(headerFrame, writableChannel);

        streamMultiplexer.onInput(ByteBuffer.wrap(writableChannel.toByteArray()));

        writableChannel.reset();
        final RawFrame continuationFrame1 = FRAME_FACTORY.createContinuation(2, ByteBuffer.wrap(headerBuf.array(), 250, 250), false);
        outBuffer.write(continuationFrame1, writableChannel);
        streamMultiplexer.onInput(ByteBuffer.wrap(writableChannel.toByteArray()));

        writableChannel.reset();
        final RawFrame continuationFrame2 = FRAME_FACTORY.createContinuation(2, ByteBuffer.wrap(headerBuf.array(), 500, 250), false);
        outBuffer.write(continuationFrame2, writableChannel);
        streamMultiplexer.onInput(ByteBuffer.wrap(writableChannel.toByteArray()));

        writableChannel.reset();
        final RawFrame continuationFrame3 = FRAME_FACTORY.createContinuation(2, ByteBuffer.wrap(headerBuf.array(), 750, headerBuf.length() - 750), true);
        outBuffer.write(continuationFrame3, writableChannel);
        streamMultiplexer.onInput(ByteBuffer.wrap(writableChannel.toByteArray()));

        Mockito.verify(streamHandler).consumeHeader(headersCaptor.capture(), ArgumentMatchers.eq(false));
        Assertions.assertFalse(headersCaptor.getValue().isEmpty());
    }

    @Test
    void testInputHeaderContinuationFramesMaxLimit() throws Exception {
        final H2Config h2Config = H2Config.custom()
                .setMaxContinuations(2)
                .build();

        final ByteArrayBuffer headerBuf = new ByteArrayBuffer(19);
        final HPackEncoder encoder = new HPackEncoder(H2Config.INIT.getHeaderTableSize(), CharCodingSupport.createEncoder(CharCodingConfig.DEFAULT));
        final List<Header> headers = new ArrayList<>();
        headers.add(new BasicHeader(":status", "200"));
        for (int i = 1; i <= 100; i++) {
            headers.add(new BasicHeader("test-header-key-" + i, "value-" + i));
        }
        encoder.encodeHeaders(headerBuf, headers, h2Config.isCompressionEnabled());

        Assertions.assertTrue(headerBuf.length() > 750);

        final AbstractH2StreamMultiplexer streamMultiplexer = new H2StreamMultiplexerImpl(
                protocolIOSession,
                FRAME_FACTORY,
                StreamIdGenerator.ODD,
                httpProcessor,
                CharCodingConfig.DEFAULT,
                h2Config,
                h2StreamListener,
                () -> streamHandler);

        final WritableByteChannelMock writableChannel = new WritableByteChannelMock(1024);
        final FrameOutputBuffer outBuffer = new FrameOutputBuffer(16 * 1024);

        final RawFrame headerFrame = FRAME_FACTORY.createHeaders(2, ByteBuffer.wrap(headerBuf.array(), 0, 250), false, false);
        outBuffer.write(headerFrame, writableChannel);

        streamMultiplexer.onInput(ByteBuffer.wrap(writableChannel.toByteArray()));

        writableChannel.reset();
        final RawFrame continuationFrame1 = FRAME_FACTORY.createContinuation(2, ByteBuffer.wrap(headerBuf.array(), 250, 250), false);
        outBuffer.write(continuationFrame1, writableChannel);
        streamMultiplexer.onInput(ByteBuffer.wrap(writableChannel.toByteArray()));

        writableChannel.reset();
        final RawFrame continuationFrame2 = FRAME_FACTORY.createContinuation(2, ByteBuffer.wrap(headerBuf.array(), 500, 250), false);
        outBuffer.write(continuationFrame2, writableChannel);
        streamMultiplexer.onInput(ByteBuffer.wrap(writableChannel.toByteArray()));

        writableChannel.reset();
        final RawFrame continuationFrame3 = FRAME_FACTORY.createContinuation(2, ByteBuffer.wrap(headerBuf.array(), 750, headerBuf.length() - 750), true);
        outBuffer.write(continuationFrame3, writableChannel);

        Assertions.assertThrows(H2ConnectionException.class, () ->
            streamMultiplexer.onInput(ByteBuffer.wrap(writableChannel.toByteArray())));
    }

    @Test
    void testStreamRemoteReset() throws Exception {
        final H2Config h2Config = H2Config.custom()
                .build();

        final AbstractH2StreamMultiplexer streamMultiplexer = new H2StreamMultiplexerImpl(
                protocolIOSession,
                FRAME_FACTORY,
                StreamIdGenerator.ODD,
                httpProcessor,
                CharCodingConfig.DEFAULT,
                h2Config,
                h2StreamListener,
                () -> streamHandler);

        final H2StreamChannel channel = streamMultiplexer.createChannel(1);
        final H2Stream stream = streamMultiplexer.createStream(channel, streamHandler);

        final ByteArrayBuffer buf = new ByteArrayBuffer(19);
        final HPackEncoder encoder = new HPackEncoder(H2Config.INIT.getHeaderTableSize(), CharCodingSupport.createEncoder(CharCodingConfig.DEFAULT));
        final List<Header> headers = new ArrayList<>();
        headers.add(new BasicHeader(":status", "200"));
        encoder.encodeHeaders(buf, headers, h2Config.isCompressionEnabled());

        final WritableByteChannelMock writableChannel = new WritableByteChannelMock(1024);
        final FrameOutputBuffer outBuffer = new FrameOutputBuffer(16 * 1024);

        final RawFrame headerFrame = FRAME_FACTORY.createHeaders(1, ByteBuffer.wrap(buf.array(), 0, 10), true, false);
        outBuffer.write(headerFrame, writableChannel);
        streamMultiplexer.onInput(ByteBuffer.wrap(writableChannel.toByteArray()));

        Assertions.assertFalse(stream.isRemoteClosed());
        Assertions.assertFalse(stream.isLocalClosed());

        final RawFrame resetFrame = FRAME_FACTORY.createResetStream(1, H2Error.NO_ERROR);
        outBuffer.write(resetFrame, writableChannel);
        streamMultiplexer.onInput(ByteBuffer.wrap(writableChannel.toByteArray()));

        Assertions.assertTrue(stream.isRemoteClosed());
        Assertions.assertTrue(stream.isLocalClosed());

        final ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        Mockito.verify(streamHandler).failed(exceptionCaptor.capture());
        Assertions.assertInstanceOf(H2StreamResetException.class, exceptionCaptor.getValue());
    }

    @Test
    void testStreamRemoteResetNoErrorRemoteAlreadyClosed() throws Exception {
        final H2Config h2Config = H2Config.custom()
                .build();

        final AbstractH2StreamMultiplexer streamMultiplexer = new H2StreamMultiplexerImpl(
                protocolIOSession,
                FRAME_FACTORY,
                StreamIdGenerator.ODD,
                httpProcessor,
                CharCodingConfig.DEFAULT,
                h2Config,
                h2StreamListener,
                () -> streamHandler);

        final H2StreamChannel channel = streamMultiplexer.createChannel(1);
        final H2Stream stream = streamMultiplexer.createStream(channel, streamHandler);

        final ByteArrayBuffer buf = new ByteArrayBuffer(19);
        final HPackEncoder encoder = new HPackEncoder(H2Config.INIT.getHeaderTableSize(), CharCodingSupport.createEncoder(CharCodingConfig.DEFAULT));
        final List<Header> headers = new ArrayList<>();
        headers.add(new BasicHeader(":status", "200"));
        encoder.encodeHeaders(buf, headers, h2Config.isCompressionEnabled());

        final WritableByteChannelMock writableChannel = new WritableByteChannelMock(1024);
        final FrameOutputBuffer outBuffer = new FrameOutputBuffer(16 * 1024);

        final RawFrame headerFrame = FRAME_FACTORY.createHeaders(1, ByteBuffer.wrap(buf.array(), 0, 10), true, false);
        outBuffer.write(headerFrame, writableChannel);
        streamMultiplexer.onInput(ByteBuffer.wrap(writableChannel.toByteArray()));

        writableChannel.reset();
        final RawFrame dataFrame = FRAME_FACTORY.createData(1, ByteBuffer.wrap(new byte[] { 'D', 'o', 'n', 'e'}), true);
        outBuffer.write(dataFrame, writableChannel);
        streamMultiplexer.onInput(ByteBuffer.wrap(writableChannel.toByteArray()));

        Assertions.assertTrue(stream.isRemoteClosed());
        Assertions.assertFalse(stream.isLocalClosed());

        writableChannel.reset();
        final RawFrame resetFrame = FRAME_FACTORY.createResetStream(1, H2Error.NO_ERROR);
        outBuffer.write(resetFrame, writableChannel);
        streamMultiplexer.onInput(ByteBuffer.wrap(writableChannel.toByteArray()));

        Assertions.assertTrue(stream.isRemoteClosed());
        Assertions.assertTrue(stream.isLocalClosed());

        Mockito.verify(streamHandler, Mockito.never()).failed(ArgumentMatchers.any());
    }

    @Test
    void testPriorityUpdateInputAccepted() throws Exception {
        final AbstractH2StreamMultiplexer mux = new H2StreamMultiplexerImpl(
                protocolIOSession,
                FRAME_FACTORY,
                StreamIdGenerator.ODD,
                httpProcessor,
                CharCodingConfig.DEFAULT,
                H2Config.custom().setMaxFrameSize(FrameConsts.MIN_FRAME_SIZE).build(),
                h2StreamListener,
                () -> streamHandler);

        // Make stream id=1 "seen" so lookupSeen(1) does not fail.
        final H2StreamChannel channel = mux.createChannel(1);
        mux.createStream(channel, streamHandler);

        final WritableByteChannelMock writable = new WritableByteChannelMock(1024);
        final FrameOutputBuffer fob = new FrameOutputBuffer(16 * 1024);

        final byte[] ascii = "u=3,i".getBytes(StandardCharsets.US_ASCII);
        final ByteBuffer payload = ByteBuffer.allocate(4 + ascii.length);
        payload.putInt(1); // prioritized stream id = 1
        payload.put(ascii);
        payload.flip();

        final RawFrame priUpd = new RawFrame(FrameType.PRIORITY_UPDATE.getValue(), 0, 0, payload);
        fob.write(priUpd, writable);
        final byte[] bytes = writable.toByteArray();

        Assertions.assertDoesNotThrow(() -> mux.onInput(ByteBuffer.wrap(bytes)));

        Mockito.verify(h2StreamListener).onFrameInput(
                ArgumentMatchers.same(mux),
                ArgumentMatchers.eq(0),
                ArgumentMatchers.any());
    }

    @Test
    void testPriorityUpdateInputRejectedForUnseenStream() throws Exception {
        final AbstractH2StreamMultiplexer mux = new H2StreamMultiplexerImpl(
                protocolIOSession,
                FRAME_FACTORY,
                StreamIdGenerator.ODD,
                httpProcessor,
                CharCodingConfig.DEFAULT,
                H2Config.custom().setMaxFrameSize(FrameConsts.MIN_FRAME_SIZE).build(),
                h2StreamListener,
                () -> streamHandler);

        final WritableByteChannelMock writable = new WritableByteChannelMock(1024);
        final FrameOutputBuffer fob = new FrameOutputBuffer(16 * 1024);

        final byte[] ascii = "u=3,i".getBytes(StandardCharsets.US_ASCII);
        final ByteBuffer payload = ByteBuffer.allocate(4 + ascii.length);
        payload.putInt(1); // prioritized stream id = 1 (unseen)
        payload.put(ascii);
        payload.flip();

        final RawFrame priUpd = new RawFrame(FrameType.PRIORITY_UPDATE.getValue(), 0, 0, payload);
        fob.write(priUpd, writable);
        final byte[] bytes = writable.toByteArray();

        final H2ConnectionException ex = Assertions.assertThrows(
                H2ConnectionException.class,
                () -> mux.onInput(ByteBuffer.wrap(bytes)));

        Assertions.assertEquals("Unexpected stream id: 1", ex.getMessage());
    }


    // Helper: minimal stream handler that sends our headers once
    static final class PriorityHeaderSender implements H2StreamHandler {
        private final H2StreamChannel channel;
        private final List<Header> headers;
        private final boolean endStream;
        private boolean sent;
        PriorityHeaderSender(final H2StreamChannel channel,
                             final List<Header> headers,
                             final boolean endStream) {
            this.channel = channel;
            this.headers = headers;
            this.endStream = endStream;
            this.sent = false;
        }
        @Override public HandlerFactory<AsyncPushConsumer> getPushHandlerFactory() { return null; }
        @Override public boolean isOutputReady() { return !sent; }
        @Override public void produceOutput() throws IOException, HttpException {
            if (!sent) {
                channel.submit(headers, endStream);
                sent = true;
            }
        }
        @Override public void consumePromise(final List<Header> headers) { }
        @Override public void consumeHeader(final List<Header> headers, final boolean endStream) { }
        @Override public void updateInputCapacity() { }
        @Override public void consumeData(final ByteBuffer src, final boolean endStream) { }
        @Override public void handle(final org.apache.hc.core5.http.HttpException ex, final boolean endStream) throws org.apache.hc.core5.http.HttpException, IOException { throw ex; }
        @Override public void failed(final Exception cause) { }
        @Override public void releaseResources() { }
    }

    // 2) Client emits PRIORITY_UPDATE BEFORE HEADERS when Priority header present
    @Test
    void testSubmitWithPriorityHeaderEmitsPriorityUpdateBeforeHeaders() throws Exception {
        // Capture writes
        final List<byte[]> writes = new ArrayList<>();
        Mockito.when(protocolIOSession.write(ArgumentMatchers.any(ByteBuffer.class)))
                .thenAnswer(inv -> {
                    final ByteBuffer b = inv.getArgument(0, ByteBuffer.class);
                    final byte[] copy = new byte[b.remaining()];
                    b.get(copy);
                    writes.add(copy);
                    return copy.length;
                });
        Mockito.doNothing().when(protocolIOSession).setEvent(ArgumentMatchers.anyInt());
        Mockito.doNothing().when(protocolIOSession).clearEvent(ArgumentMatchers.anyInt());

        final H2Config h2Config = H2Config.custom().build();
        final H2StreamMultiplexerImpl mux = new H2StreamMultiplexerImpl(
                protocolIOSession, FRAME_FACTORY, StreamIdGenerator.ODD,
                httpProcessor, CharCodingConfig.DEFAULT, h2Config, h2StreamListener, () -> streamHandler);

        // Start connection (sends our SETTINGS)
        mux.onConnect();
        writes.clear(); // ignore noise from onConnect

        // Pretend server SETTINGS includes NO_RFC7540=1 (opts in to new scheme)
        final WritableByteChannelMock writable = new WritableByteChannelMock(256);
        final FrameOutputBuffer fob = new FrameOutputBuffer(16 * 1024);
        final ByteBuffer pl = ByteBuffer.allocate(6);
        pl.putShort((short) 0x0009); // SETTINGS_NO_RFC7540_PRIORITIES
        pl.putInt(1);
        pl.flip();
        final RawFrame incomingSettings = new RawFrame(FrameType.SETTINGS.getValue(), 0, 0, pl);
        fob.write(incomingSettings, writable);
        mux.onInput(ByteBuffer.wrap(writable.toByteArray()));
        writes.clear(); // drop the ACK we sent

        // Create a locally-initiated stream and a handler that will submit headers with Priority
        final H2StreamChannel ch = mux.createChannel(1);
        final List<Header> reqHeaders = Arrays.asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":scheme", "https"),
                new BasicHeader(":path", "/"),
                new BasicHeader(":authority", "example.test"),
                new BasicHeader(HttpHeaders.PRIORITY, "u=3,i")
        );
         mux.createStream(ch, new PriorityHeaderSender(ch, reqHeaders, true));

        // Drive output so the handler submits
        mux.onOutput();

        // Stitch captured bytes and parse frames
        final int total = writes.stream().mapToInt(a -> a.length).sum();
        final byte[] all = new byte[total];
        int pos = 0;
        for (final byte[] a : writes) { System.arraycopy(a, 0, all, pos, a.length); pos += a.length; }
        final List<FrameStub> frames = parseFrames(all);

        // Find first PRIORITY_UPDATE (type 0x10, sid 0) and first HEADERS for stream 1
        int idxPriUpd = -1, idxHeaders = -1;
        for (int i = 0; i < frames.size(); i++) {
            final FrameStub f = frames.get(i);
            if (idxPriUpd < 0 && f.type == FrameType.PRIORITY_UPDATE.getValue() && f.streamId == 0) idxPriUpd = i;
            if (idxHeaders < 0 && f.type == FrameType.HEADERS.getValue() && f.streamId == 1) idxHeaders = i;
        }
        Assertions.assertTrue(idxPriUpd >= 0, "PRIORITY_UPDATE not emitted");
        Assertions.assertTrue(idxHeaders >= 0, "HEADERS not emitted");
        Assertions.assertTrue(idxPriUpd < idxHeaders, "PRIORITY_UPDATE must precede HEADERS");
    }

    // 3) Optional policy: suppress emission when peer’s first SETTINGS omits 0x9
    @Test
    void testPriorityUpdateSuppressedAfterSettingsWithoutNoH2() throws Exception {
        final List<byte[]> writes = new ArrayList<>();
        Mockito.when(protocolIOSession.write(ArgumentMatchers.any(ByteBuffer.class)))
                .thenAnswer(inv -> { final ByteBuffer b = inv.getArgument(0, ByteBuffer.class);
                    final byte[] c = new byte[b.remaining()]; b.get(c); writes.add(c); return c.length; });
        Mockito.doNothing().when(protocolIOSession).setEvent(ArgumentMatchers.anyInt());
        Mockito.doNothing().when(protocolIOSession).clearEvent(ArgumentMatchers.anyInt());

        final H2StreamMultiplexerImpl mux = new H2StreamMultiplexerImpl(
                protocolIOSession, FRAME_FACTORY, StreamIdGenerator.ODD,
                httpProcessor, CharCodingConfig.DEFAULT, H2Config.custom().build(),
                h2StreamListener, () -> streamHandler);

        mux.onConnect();
        writes.clear();

        // Server SETTINGS without 0x9
        final WritableByteChannelMock w = new WritableByteChannelMock(256);
        final FrameOutputBuffer fob = new FrameOutputBuffer(16 * 1024);
        fob.write(new RawFrame(FrameType.SETTINGS.getValue(), 0, 0, ByteBuffer.allocate(0)), w);
        mux.onInput(ByteBuffer.wrap(w.toByteArray()));
        writes.clear(); // drop our ACK

        // Create local stream that will send Priority header
        final H2StreamChannel ch = mux.createChannel(1);
        final List<Header> reqHeaders = Arrays.asList(
                new BasicHeader(":method","GET"),
                new BasicHeader(":scheme","https"),
                new BasicHeader(":path","/"),
                new BasicHeader(":authority","example.test"),
                new BasicHeader(HttpHeaders.PRIORITY, "u=3")
        );
        mux.createStream(ch, new PriorityHeaderSender(ch, reqHeaders, true));

        mux.onOutput();

        final int total = writes.stream().mapToInt(a -> a.length).sum();
        final byte[] all = new byte[total]; int p = 0;
        for (final byte[] a : writes) { System.arraycopy(a, 0, all, p, a.length); p += a.length; }

        final List<FrameStub> frames = parseFrames(all);
        Assertions.assertTrue(frames.stream().noneMatch(f -> f.type == FrameType.PRIORITY_UPDATE.getValue()),
                "PRIORITY_UPDATE must be suppressed when peer didn't send NO_RFC7540 (policy)");
    }

    // 4) Continue emission when peer sends NO_RFC7540=1
    @Test
    void testPriorityUpdateContinuesAfterSettingsWithNoH2Equals1() throws Exception {
        final List<byte[]> writes = new ArrayList<>();
        Mockito.when(protocolIOSession.write(ArgumentMatchers.any(ByteBuffer.class)))
                .thenAnswer(inv -> { final ByteBuffer b = inv.getArgument(0, ByteBuffer.class);
                    final byte[] c = new byte[b.remaining()]; b.get(c); writes.add(c); return c.length; });
        Mockito.doNothing().when(protocolIOSession).setEvent(ArgumentMatchers.anyInt());
        Mockito.doNothing().when(protocolIOSession).clearEvent(ArgumentMatchers.anyInt());

        final H2StreamMultiplexerImpl mux = new H2StreamMultiplexerImpl(
                protocolIOSession, FRAME_FACTORY, StreamIdGenerator.ODD,
                httpProcessor, CharCodingConfig.DEFAULT, H2Config.custom().build(),
                h2StreamListener, () -> streamHandler);

        mux.onConnect();
        writes.clear();

        // Server SETTINGS with 0x9 = 1
        final WritableByteChannelMock w = new WritableByteChannelMock(256);
        final FrameOutputBuffer fob = new FrameOutputBuffer(16 * 1024);
        final ByteBuffer pl = ByteBuffer.allocate(6);
        pl.putShort((short) 0x0009);
        pl.putInt(1);
        pl.flip();
        fob.write(new RawFrame(FrameType.SETTINGS.getValue(), 0, 0, pl), w);
        mux.onInput(ByteBuffer.wrap(w.toByteArray()));
        writes.clear(); // drop ACK

        final H2StreamChannel ch = mux.createChannel(1);
        final List<Header> reqHeaders = Arrays.asList(
                new BasicHeader(":method","GET"),
                new BasicHeader(":scheme","https"),
                new BasicHeader(":path","/"),
                new BasicHeader(":authority","example.test"),
                new BasicHeader(HttpHeaders.PRIORITY, "u=0,i")
        );
        mux.createStream(ch, new PriorityHeaderSender(ch, reqHeaders, true));

        mux.onOutput();

        final int total = writes.stream().mapToInt(a -> a.length).sum();
        final byte[] all = new byte[total]; int p = 0;
        for (final byte[] a : writes) { System.arraycopy(a, 0, all, p, a.length); p += a.length; }
        final List<FrameStub> frames = parseFrames(all);

        final int idxPriUpd = IntStream.range(0, frames.size())
                .filter(i -> frames.get(i).type == FrameType.PRIORITY_UPDATE.getValue() && frames.get(i).streamId == 0)
                .findFirst().orElse(-1);
        Assertions.assertTrue(idxPriUpd >= 0, "PRIORITY_UPDATE should be emitted when NO_RFC7540=1");
    }

    @Test
    void testStreamIdleTimeoutTriggersH2StreamTimeoutException() throws Exception {
        Mockito.when(protocolIOSession.write(ArgumentMatchers.any(ByteBuffer.class)))
                .thenAnswer(invocation -> {
                    final ByteBuffer buffer = invocation.getArgument(0, ByteBuffer.class);
                    final int remaining = buffer.remaining();
                    buffer.position(buffer.limit());
                    return remaining;
                });
        Mockito.doNothing().when(protocolIOSession).setEvent(ArgumentMatchers.anyInt());
        Mockito.doNothing().when(protocolIOSession).clearEvent(ArgumentMatchers.anyInt());

        final H2Config h2Config = H2Config.custom().build();
        final AbstractH2StreamMultiplexer streamMultiplexer = new H2StreamMultiplexerImpl(
                protocolIOSession,
                FRAME_FACTORY,
                StreamIdGenerator.ODD,
                httpProcessor,
                CharCodingConfig.DEFAULT,
                h2Config,
                h2StreamListener,
                () -> streamHandler);

        final H2StreamChannel channel = streamMultiplexer.createChannel(1);
        final H2Stream stream = streamMultiplexer.createStream(channel, streamHandler);

        stream.setTimeout(Timeout.of(1, TimeUnit.NANOSECONDS));
        stream.activate();

        streamMultiplexer.onOutput();

        Mockito.verify(streamHandler).failed(exceptionCaptor.capture());
        final Exception cause = exceptionCaptor.getValue();
        Assertions.assertInstanceOf(H2StreamTimeoutException.class, cause);

        final H2StreamTimeoutException timeoutEx = (H2StreamTimeoutException) cause;
        Assertions.assertEquals(1, timeoutEx.getStreamId());

        Assertions.assertTrue(stream.isLocalClosed());
        Assertions.assertTrue(stream.isClosed());

        Assertions.assertTrue(timeoutEx.getMessage().contains("idle timeout"));
        Assertions.assertEquals(1L, timeoutEx.getTimeout().toNanoseconds());
        Assertions.assertEquals(1, timeoutEx.getStreamId());

    }

    @Test
    void testOutboundTrailersWithPseudoHeaderRejected() throws Exception {
        final AbstractH2StreamMultiplexer mux = new H2StreamMultiplexerImpl(
                protocolIOSession,
                FRAME_FACTORY,
                StreamIdGenerator.EVEN,
                httpProcessor,
                CharCodingConfig.DEFAULT,
                H2Config.custom().build(),
                h2StreamListener,
                () -> streamHandler);

        final H2StreamChannel channel = mux.createChannel(1);
        mux.createStream(channel, streamHandler);

        final List<Header> trailers = Arrays.asList(
                new BasicHeader(":status", "200"));

        Assertions.assertThrows(H2ConnectionException.class, () -> channel.endStream(trailers));
    }

    @Test
    void testInputUnknownFrameTypeIgnored() throws Exception {
        final WritableByteChannelMock writableChannel = new WritableByteChannelMock(1024);
        final FrameOutputBuffer outBuffer = new FrameOutputBuffer(16 * 1024);

        // 0x0a is not a defined HTTP/2 core frame type in FrameType enum (extension / unknown)
        final RawFrame unknownFrame = new RawFrame(0x0a, 0, 0, ByteBuffer.allocate(0));
        outBuffer.write(unknownFrame, writableChannel);

        final AbstractH2StreamMultiplexer streamMultiplexer = new H2StreamMultiplexerImpl(
                protocolIOSession,
                FRAME_FACTORY,
                StreamIdGenerator.ODD,
                httpProcessor,
                CharCodingConfig.DEFAULT,
                H2Config.custom().build(),
                h2StreamListener,
                () -> streamHandler);

        Assertions.assertDoesNotThrow(() -> streamMultiplexer.onInput(ByteBuffer.wrap(writableChannel.toByteArray())));
    }
    private static byte[] encodeFrame(final RawFrame frame) throws IOException {
        final WritableByteChannelMock writableChannel = new WritableByteChannelMock(256);
        final FrameOutputBuffer outBuffer = new FrameOutputBuffer(16 * 1024);
        outBuffer.write(frame, writableChannel);
        return writableChannel.toByteArray();
    }

    private static void feedFrame(final AbstractH2StreamMultiplexer mux, final RawFrame frame) throws Exception {
        mux.onInput(ByteBuffer.wrap(encodeFrame(frame)));
    }

    private static void completeSettingsHandshake(final AbstractH2StreamMultiplexer mux) throws Exception {
        // Remote SETTINGS (non-ACK) -> mux replies with SETTINGS ACK and marks remoteSettingState ACKED
        final RawFrame remoteSettings = FRAME_FACTORY.createSettings(new H2Setting[] {
                new H2Setting(H2Param.MAX_FRAME_SIZE, FrameConsts.MIN_FRAME_SIZE)
        });
        feedFrame(mux, remoteSettings);

        // Remote ACK of our SETTINGS -> localSettingState ACKED
        feedFrame(mux, new RawFrame(FrameType.SETTINGS.getValue(), FrameFlag.ACK.getValue(), 0, null));
    }

    private static final class FrameStub {
        final int type;
        final int flags;
        final int streamId;
        final byte[] payload;

        FrameStub(final int type, final int flags, final int streamId, final byte[] payload) {
            this.type = type;
            this.flags = flags;
            this.streamId = streamId;
            this.payload = payload;
        }

        boolean isPing() {
            return type == FrameType.PING.getValue();
        }

        boolean isGoAway() {
            return type == FrameType.GOAWAY.getValue();
        }

        boolean isAck() {
            return (flags & FrameFlag.ACK.getValue()) != 0;
        }
    }

    private static List<FrameStub> parseFrames(final byte[] all) {
        final List<FrameStub> out = new ArrayList<>();
        int p = 0;
        while (p + 9 <= all.length) {
            final int len = ((all[p] & 0xff) << 16) | ((all[p + 1] & 0xff) << 8) | (all[p + 2] & 0xff);
            final int type = all[p + 3] & 0xff;
            final int flags = all[p + 4] & 0xff;
            final int sid = ((all[p + 5] & 0x7f) << 24) | ((all[p + 6] & 0xff) << 16)
                    | ((all[p + 7] & 0xff) << 8) | (all[p + 8] & 0xff);
            p += 9;
            if (p + len > all.length) {
                break;
            }
            final byte[] payload = new byte[len];
            System.arraycopy(all, p, payload, 0, len);
            out.add(new FrameStub(type, flags, sid, payload));
            p += len;
        }
        return out;
    }

    private static byte[] concat(final List<byte[]> writes) {
        final int total = writes.stream().mapToInt(a -> a.length).sum();
        final byte[] all = new byte[total];
        int p = 0;
        for (final byte[] a : writes) {
            System.arraycopy(a, 0, all, p, a.length);
            p += a.length;
        }
        return all;
    }


    @Test
    void testKeepAliveNotActiveBeforeSettingsHandshake() throws Exception {
        final List<byte[]> writes = new ArrayList<>();
        Mockito.when(protocolIOSession.write(ArgumentMatchers.any(ByteBuffer.class)))
                .thenAnswer(inv -> {
                    final ByteBuffer b = inv.getArgument(0, ByteBuffer.class);
                    final byte[] copy = new byte[b.remaining()];
                    b.get(copy);
                    writes.add(copy);
                    return copy.length;
                });
        Mockito.doNothing().when(protocolIOSession).setEvent(ArgumentMatchers.anyInt());
        Mockito.doNothing().when(protocolIOSession).clearEvent(ArgumentMatchers.anyInt());

        final Timeout idleTime = Timeout.ofMilliseconds(5);

        final AbstractH2StreamMultiplexer mux = new H2StreamMultiplexerImpl(
                protocolIOSession, FRAME_FACTORY, StreamIdGenerator.ODD,
                httpProcessor, CharCodingConfig.DEFAULT, H2Config.DEFAULT, h2StreamListener, () -> streamHandler,
                idleTime);

        mux.onConnect();
        writes.clear();

        // BEFORE SETTINGS handshake is fully ACKed, keepalive must NOT run
        mux.onTimeout(idleTime);

        final List<FrameStub> frames = parseFrames(concat(writes));
        Assertions.assertTrue(frames.stream().noneMatch(FrameStub::isPing), "Must not emit PING before handshake");
        Assertions.assertTrue(frames.stream().anyMatch(FrameStub::isGoAway), "Default timeout path must emit GOAWAY");
    }

    @Test
    void testValidateAfterInactivityDoesNotArmSocketTimeoutOnHandshake() throws Exception {
        Mockito.doNothing().when(protocolIOSession).setEvent(ArgumentMatchers.anyInt());
        Mockito.doNothing().when(protocolIOSession).clearEvent(ArgumentMatchers.anyInt());
        Mockito.when(protocolIOSession.write(ArgumentMatchers.any(ByteBuffer.class))).thenReturn(0);

        final Timeout validateAfterInactivity = Timeout.ofMilliseconds(50);

        final AbstractH2StreamMultiplexer mux = new H2StreamMultiplexerImpl(
                protocolIOSession, FRAME_FACTORY, StreamIdGenerator.ODD,
                httpProcessor, CharCodingConfig.DEFAULT, H2Config.DEFAULT, h2StreamListener, () -> streamHandler,
                validateAfterInactivity);

        mux.onConnect();
        completeSettingsHandshake(mux);

        Mockito.verify(protocolIOSession, Mockito.never()).setSocketTimeout(ArgumentMatchers.eq(validateAfterInactivity));
    }

    @Test
    void testValidateAfterInactivitySendsPingAndSetsAckTimeout() throws Exception {
        final List<byte[]> writes = new ArrayList<>();
        Mockito.when(protocolIOSession.write(ArgumentMatchers.any(ByteBuffer.class)))
                .thenAnswer(inv -> {
                    final ByteBuffer b = inv.getArgument(0, ByteBuffer.class);
                    final byte[] copy = new byte[b.remaining()];
                    b.get(copy);
                    writes.add(copy);
                    return copy.length;
                });
        Mockito.doNothing().when(protocolIOSession).setEvent(ArgumentMatchers.anyInt());
        Mockito.doNothing().when(protocolIOSession).clearEvent(ArgumentMatchers.anyInt());

        Mockito.when(protocolIOSession.hasCommands()).thenReturn(true);
        Mockito.when(protocolIOSession.getSocketTimeout()).thenReturn(Timeout.ofSeconds(30));

        final Timeout validateAfterInactivity = Timeout.ofMilliseconds(1);

        final AbstractH2StreamMultiplexer mux = new H2StreamMultiplexerImpl(
                protocolIOSession, FRAME_FACTORY, StreamIdGenerator.ODD,
                httpProcessor, CharCodingConfig.DEFAULT, H2Config.DEFAULT, h2StreamListener, () -> streamHandler,
                validateAfterInactivity);

        mux.onConnect();
        completeSettingsHandshake(mux);

        writes.clear();
        makeMuxIdle(mux, validateAfterInactivity);

        mux.onOutput(); // <-- TRIGGER

        Mockito.verify(protocolIOSession, Mockito.atLeastOnce())
                .setSocketTimeout(ArgumentMatchers.eq(Timeout.ofSeconds(5)));

        final List<FrameStub> frames = parseFrames(concat(writes));
        Assertions.assertTrue(frames.stream().anyMatch(f -> f.isPing() && !f.isAck()), "Must emit pre-flight PING");
        Assertions.assertTrue(mux.isOpen());
    }

    @Test
    void testValidateAfterInactivityPingAckRestoresPreviousTimeout() throws Exception {
        final List<byte[]> writes = new ArrayList<>();
        Mockito.when(protocolIOSession.write(ArgumentMatchers.any(ByteBuffer.class)))
                .thenAnswer(inv -> {
                    final ByteBuffer b = inv.getArgument(0, ByteBuffer.class);
                    final byte[] copy = new byte[b.remaining()];
                    b.get(copy);
                    writes.add(copy);
                    return copy.length;
                });
        Mockito.doNothing().when(protocolIOSession).setEvent(ArgumentMatchers.anyInt());
        Mockito.doNothing().when(protocolIOSession).clearEvent(ArgumentMatchers.anyInt());

        Mockito.when(protocolIOSession.hasCommands()).thenReturn(true);
        final Timeout previousTimeout = Timeout.ofSeconds(30);
        Mockito.when(protocolIOSession.getSocketTimeout()).thenReturn(previousTimeout);

        final Timeout validateAfterInactivity = Timeout.ofMilliseconds(1);

        final AbstractH2StreamMultiplexer mux = new H2StreamMultiplexerImpl(
                protocolIOSession, FRAME_FACTORY, StreamIdGenerator.ODD,
                httpProcessor, CharCodingConfig.DEFAULT, H2Config.DEFAULT, h2StreamListener, () -> streamHandler,
                validateAfterInactivity);

        mux.onConnect();
        completeSettingsHandshake(mux);

        writes.clear();
        makeMuxIdle(mux, validateAfterInactivity);

        mux.onOutput(); // emits PING

        final List<FrameStub> frames = parseFrames(concat(writes));
        final FrameStub ping = frames.stream().filter(f -> f.isPing() && !f.isAck()).findFirst().orElse(null);
        Assertions.assertNotNull(ping, "Expected a pre-flight PING frame");
        Assertions.assertEquals(8, ping.payload.length);

        final RawFrame pingAck = new RawFrame(
                FrameType.PING.getValue(),
                FrameFlag.ACK.getValue(),
                0,
                ByteBuffer.wrap(ping.payload));

        feedFrame(mux, pingAck);

        Mockito.verify(protocolIOSession, Mockito.atLeastOnce()).setSocketTimeout(ArgumentMatchers.eq(previousTimeout));
    }

    @Test
    void testKeepAliveAckTimeoutShutsDownAndFailsStreams() throws Exception {
        final List<byte[]> writes = new ArrayList<>();
        Mockito.when(protocolIOSession.write(ArgumentMatchers.any(ByteBuffer.class)))
                .thenAnswer(inv -> {
                    final ByteBuffer b = inv.getArgument(0, ByteBuffer.class);
                    final byte[] copy = new byte[b.remaining()];
                    b.get(copy);
                    writes.add(copy);
                    return copy.length;
                });
        Mockito.doNothing().when(protocolIOSession).setEvent(ArgumentMatchers.anyInt());
        Mockito.doNothing().when(protocolIOSession).clearEvent(ArgumentMatchers.anyInt());

        final Timeout idleTime = Timeout.ofMilliseconds(5);
        final Timeout ackTimeout = Timeout.ofSeconds(5);

        final AbstractH2StreamMultiplexer mux = new H2StreamMultiplexerImpl(
                protocolIOSession, FRAME_FACTORY, StreamIdGenerator.ODD,
                httpProcessor, CharCodingConfig.DEFAULT, H2Config.DEFAULT, h2StreamListener, () -> streamHandler,
                idleTime);

        mux.onConnect();
        completeSettingsHandshake(mux);

        // Ensure at least one live stream to be failed
        final H2StreamChannel channel = mux.createChannel(1);
        mux.createStream(channel, streamHandler);

        writes.clear();
        Thread.sleep(idleTime.toMilliseconds() + 10);
        mux.onTimeout(idleTime); // send PING, awaiting ACK
        writes.clear();

        // No ACK arrives -> next timeout closes via keepalive path (GOAWAY + fail streams)
        mux.onTimeout(ackTimeout);

        final List<FrameStub> frames = parseFrames(concat(writes));
        Assertions.assertTrue(frames.stream().anyMatch(FrameStub::isGoAway), "Must emit GOAWAY on ping ACK timeout");

        Mockito.verify(streamHandler, Mockito.atLeastOnce()).failed(exceptionCaptor.capture());
        Assertions.assertInstanceOf(H2StreamResetException.class, exceptionCaptor.getValue());

        Assertions.assertFalse(mux.isOpen(), "Connection must not be open after keepalive shutdown");
    }

    @Test
    void testKeepAliveDisabledNeverEmitsPing() throws Exception {
        final List<byte[]> writes = new ArrayList<>();
        Mockito.when(protocolIOSession.write(ArgumentMatchers.any(ByteBuffer.class)))
                .thenAnswer(inv -> {
                    final ByteBuffer b = inv.getArgument(0, ByteBuffer.class);
                    final byte[] copy = new byte[b.remaining()];
                    b.get(copy);
                    writes.add(copy);
                    return copy.length;
                });
        Mockito.doNothing().when(protocolIOSession).setEvent(ArgumentMatchers.anyInt());
        Mockito.doNothing().when(protocolIOSession).clearEvent(ArgumentMatchers.anyInt());

        final AbstractH2StreamMultiplexer mux = new H2StreamMultiplexerImpl(
                protocolIOSession, FRAME_FACTORY, StreamIdGenerator.ODD,
                httpProcessor, CharCodingConfig.DEFAULT, H2Config.DEFAULT, h2StreamListener, () -> streamHandler,
                Timeout.DISABLED);

        mux.onConnect();
        writes.clear();

        mux.onTimeout(Timeout.ofMilliseconds(1));

        final List<FrameStub> frames = parseFrames(concat(writes));
        Assertions.assertTrue(frames.stream().noneMatch(FrameStub::isPing), "Disabled policy must never emit PING");
    }

    private static long getValidateAfterInactivityGranularityMillis() throws Exception {
        final Field field = AbstractH2StreamMultiplexer.class.getDeclaredField("VALIDATE_AFTER_INACTIVITY_GRANULARITY_MILLIS");
        field.setAccessible(true);
        return field.getLong(null);
    }

    private static void setLastActivityTime(final AbstractH2StreamMultiplexer mux, final long millis) throws Exception {
        final Field field = AbstractH2StreamMultiplexer.class.getDeclaredField("lastActivityTime");
        field.setAccessible(true);
        field.setLong(mux, millis);
    }

    private static void makeMuxIdle(final AbstractH2StreamMultiplexer mux, final Timeout validateAfterInactivity) throws Exception {
        final long granularityMillis = getValidateAfterInactivityGranularityMillis();
        final long configuredMillis = validateAfterInactivity != null ? validateAfterInactivity.toMilliseconds() : 0;
        final long effectiveMillis = configuredMillis > 0 ? Math.max(configuredMillis, granularityMillis) : 0;
        setLastActivityTime(mux, System.currentTimeMillis() - effectiveMillis - 10);
    }

    @Test
    void testHpackDecodeFailureInHeadersCausesConnectionCompressionError() throws Exception {
        final AbstractH2StreamMultiplexer mux = new H2StreamMultiplexerImpl(
                protocolIOSession,
                FRAME_FACTORY,
                StreamIdGenerator.ODD, // peer-initiated streams are EVEN (e.g. 2)
                httpProcessor,
                CharCodingConfig.DEFAULT,
                H2Config.custom().build(),
                h2StreamListener,
                () -> streamHandler);

        final WritableByteChannelMock writableChannel = new WritableByteChannelMock(256);
        final FrameOutputBuffer outBuffer = new FrameOutputBuffer(16 * 1024);

        // Invalid HPACK: indexed header field representation with index = 0 (illegal).
        final byte[] invalidHeaderBlock = new byte[] { (byte) 0x80 };

        final RawFrame headersFrame = FRAME_FACTORY.createHeaders(
                2,
                ByteBuffer.wrap(invalidHeaderBlock),
                true,   // END_HEADERS
                false);

        outBuffer.write(headersFrame, writableChannel);

        final H2ConnectionException exception = Assertions.assertThrows(H2ConnectionException.class, () ->
                mux.onInput(ByteBuffer.wrap(writableChannel.toByteArray())));

        Assertions.assertEquals(H2Error.COMPRESSION_ERROR, H2Error.getByCode(exception.getCode()));

        // Must not be delegated to stream-level handlers.
        Mockito.verify(streamHandler, Mockito.never())
                .consumeHeader(ArgumentMatchers.anyList(), ArgumentMatchers.anyBoolean());
        Mockito.verify(streamHandler, Mockito.never())
                .handle(ArgumentMatchers.any(HttpException.class), ArgumentMatchers.anyBoolean());
        Mockito.verify(streamHandler, Mockito.never())
                .failed(ArgumentMatchers.any(Exception.class));
    }

    @Test
    void testHpackDecodeFailureInPushPromiseCausesConnectionCompressionError() throws Exception {
        final AbstractH2StreamMultiplexer mux = new H2StreamMultiplexerImpl(
                protocolIOSession,
                FRAME_FACTORY,
                StreamIdGenerator.ODD,
                httpProcessor,
                CharCodingConfig.DEFAULT,
                H2Config.custom().setPushEnabled(true).build(),
                h2StreamListener,
                () -> streamHandler);

        // Create an existing local stream (client-initiated, odd) that can receive PUSH_PROMISE.
        final H2StreamChannel channel = mux.createChannel(1);
        mux.createStream(channel, streamHandler);

        final ByteBuffer payload = ByteBuffer.allocate(5);
        payload.putInt(2);          // promised stream id (peer/server uses even)
    }

    @Test
    void testHpackExceptionInHeadersMappedToConnectionCompressionError() throws Exception {
        final AbstractH2StreamMultiplexer mux = new H2StreamMultiplexerImpl(
                protocolIOSession,
                FRAME_FACTORY,
                StreamIdGenerator.ODD,
                httpProcessor,
                CharCodingConfig.DEFAULT,
                H2Config.custom().build(),
                h2StreamListener,
                () -> streamHandler);

        final WritableByteChannelMock writableChannel = new WritableByteChannelMock(256);
        final FrameOutputBuffer outBuffer = new FrameOutputBuffer(16 * 1024);

        // HPACK: literal header field with incremental indexing (0x40), "new name".
        // Next byte is the name string length (here: 10), but we provide no bytes -> truncated.
        // This should throw HPackException.
        final byte[] invalidHeaderBlock = new byte[] { (byte) 0x40, (byte) 0x0a };

        final RawFrame headersFrame = FRAME_FACTORY.createHeaders(
                2,
                ByteBuffer.wrap(invalidHeaderBlock),
                true,   // END_HEADERS
                false);

        outBuffer.write(headersFrame, writableChannel);

        final H2ConnectionException ex = Assertions.assertThrows(H2ConnectionException.class, () ->
                mux.onInput(ByteBuffer.wrap(writableChannel.toByteArray())));

        Assertions.assertEquals(H2Error.COMPRESSION_ERROR, H2Error.getByCode(ex.getCode()));
        Assertions.assertInstanceOf(HPackException.class, ex.getCause());

        // Must not be handled at stream level
        Mockito.verify(streamHandler, Mockito.never())
                .consumeHeader(ArgumentMatchers.anyList(), ArgumentMatchers.anyBoolean());
        Mockito.verify(streamHandler, Mockito.never())
                .handle(ArgumentMatchers.any(HttpException.class), ArgumentMatchers.anyBoolean());
        Mockito.verify(streamHandler, Mockito.never())
                .failed(ArgumentMatchers.any(Exception.class));
    }


    @Test
    void testInputSettingsAckWithNonEmptyPayloadRejected() throws Exception {
        final AbstractH2StreamMultiplexer mux = new H2StreamMultiplexerImpl(
                protocolIOSession,
                FRAME_FACTORY,
                StreamIdGenerator.ODD,
                httpProcessor,
                CharCodingConfig.DEFAULT,
                H2Config.custom().build(),
                h2StreamListener,
                () -> streamHandler);

        // SETTINGS (0x04), ACK (0x01), streamId=0, length=6 (INVALID for ACK)
        // payload is one dummy setting (content irrelevant)
        final byte[] bytes = new byte[] {
                0x00, 0x00, 0x06,       // length = 6
                0x04,                   // type = SETTINGS
                0x01,                   // flags = ACK
                0x00, 0x00, 0x00, 0x00, // stream id = 0
                0x00, 0x01,             // setting id
                0x00, 0x00, 0x00, 0x01  // setting value
        };

        final H2ConnectionException ex = Assertions.assertThrows(
                H2ConnectionException.class,
                () -> mux.onInput(ByteBuffer.wrap(bytes)));

        Assertions.assertEquals(H2Error.FRAME_SIZE_ERROR, H2Error.getByCode(ex.getCode()));
    }

    @Test
    void testWindowUpdateReservedBitIgnored() throws Exception {
        final AbstractH2StreamMultiplexer mux = new H2StreamMultiplexerImpl(
                protocolIOSession,
                FRAME_FACTORY,
                StreamIdGenerator.ODD,
                httpProcessor,
                CharCodingConfig.DEFAULT,
                H2Config.custom().build(),
                h2StreamListener,
                () -> streamHandler);

        final ByteBuffer payload = ByteBuffer.allocate(4);
        payload.putInt(0x80000001);
        payload.flip();

        final RawFrame windowUpdate = new RawFrame(FrameType.WINDOW_UPDATE.getValue(), 0, 0, payload);

        Assertions.assertDoesNotThrow(() -> mux.onInput(ByteBuffer.wrap(encodeFrame(windowUpdate))));
    }

    @Test
    void testPingReservedBitInStreamIdIgnored() throws Exception {
        final AbstractH2StreamMultiplexer mux = new H2StreamMultiplexerImpl(
                protocolIOSession,
                FRAME_FACTORY,
                StreamIdGenerator.ODD,
                httpProcessor,
                CharCodingConfig.DEFAULT,
                H2Config.custom().build(),
                h2StreamListener,
                () -> streamHandler);

        final ByteBuffer payload = ByteBuffer.wrap(new byte[8]);
        final RawFrame ping = new RawFrame(FrameType.PING.getValue(), 0, 0x80000000, payload);

        Assertions.assertDoesNotThrow(() -> mux.onInput(ByteBuffer.wrap(encodeFrame(ping))));
    }

    @Test
    void testPushPromiseReservedBitInPromisedStreamIdIgnored() throws Exception {
        final H2Config h2Config = H2Config.custom()
                .setPushEnabled(true)
                .build();

        final AbstractH2StreamMultiplexer mux = new H2StreamMultiplexerImpl(
                protocolIOSession,
                FRAME_FACTORY,
                StreamIdGenerator.ODD,
                httpProcessor,
                CharCodingConfig.DEFAULT,
                h2Config,
                h2StreamListener,
                () -> streamHandler);

        final H2StreamChannel s1 = mux.createChannel(1);
        mux.createStream(s1, streamHandler);

        final ByteArrayBuffer hbuf = new ByteArrayBuffer(256);
        final HPackEncoder encoder = new HPackEncoder(
                H2Config.INIT.getHeaderTableSize(),
                CharCodingSupport.createEncoder(CharCodingConfig.DEFAULT));

        final List<Header> reqHeaders = Arrays.asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":scheme", "https"),
                new BasicHeader(":authority", "example.test"),
                new BasicHeader(":path", "/pushed"));

        encoder.encodeHeaders(hbuf, reqHeaders, h2Config.isCompressionEnabled());

        final ByteBuffer payload = ByteBuffer.allocate(4 + hbuf.length());
        payload.putInt(0x80000002);
        payload.put(hbuf.array(), 0, hbuf.length());
        payload.flip();

        final RawFrame pp = new RawFrame(
                FrameType.PUSH_PROMISE.getValue(),
                FrameFlag.END_HEADERS.getValue(),
                1,
                payload);

        Assertions.assertDoesNotThrow(() -> mux.onInput(ByteBuffer.wrap(encodeFrame(pp))));

        Mockito.verify(h2StreamListener).onHeaderInput(
                ArgumentMatchers.same(mux),
                ArgumentMatchers.eq(2),
                ArgumentMatchers.anyList());

        Mockito.verify(streamHandler).consumePromise(ArgumentMatchers.anyList());
    }

    @Test
    void testGoAwayReservedBitInLastStreamIdAffectsStreamCulling() throws Exception {
        final H2Config h2Config = H2Config.custom().build();

        final AbstractH2StreamMultiplexer mux = new H2StreamMultiplexerImpl(
                protocolIOSession,
                FRAME_FACTORY,
                StreamIdGenerator.ODD, // local=odd, remote=even
                httpProcessor,
                CharCodingConfig.DEFAULT,
                h2Config,
                h2StreamListener,
                () -> streamHandler);

        // Create 3 remote (even) streams by feeding inbound HEADERS on 2,4,6.
        final ByteArrayBuffer headerBuf = new ByteArrayBuffer(256);
        final HPackEncoder encoder = new HPackEncoder(
                H2Config.INIT.getHeaderTableSize(),
                CharCodingSupport.createEncoder(CharCodingConfig.DEFAULT));

        final List<Header> headers = Arrays.asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":scheme", "https"),
                new BasicHeader(":path", "/"),
                new BasicHeader(":authority", "example.test"));
        encoder.encodeHeaders(headerBuf, headers, h2Config.isCompressionEnabled());

        final RawFrame h2 = FRAME_FACTORY.createHeaders(2,
                ByteBuffer.wrap(headerBuf.array(), 0, headerBuf.length()),
                true,  // END_HEADERS
                false  // END_STREAM
        );
        final RawFrame h4 = FRAME_FACTORY.createHeaders(4,
                ByteBuffer.wrap(headerBuf.array(), 0, headerBuf.length()),
                true,
                false
        );
        final RawFrame h6 = FRAME_FACTORY.createHeaders(6,
                ByteBuffer.wrap(headerBuf.array(), 0, headerBuf.length()),
                true,
                false
        );

        feedFrame(mux, h2);
        feedFrame(mux, h4);
        feedFrame(mux, h6);

        // GOAWAY last-stream-id = 4, but with reserved MSB set.
        // Correct masking keeps streams <= 4 (2 and 4) and drops only stream 6.
        final ByteBuffer goAwayPayload = ByteBuffer.allocate(8);
        goAwayPayload.putInt(0x80000004); // reserved bit set, last-stream-id = 4
        goAwayPayload.putInt(H2Error.NO_ERROR.getCode());
        goAwayPayload.flip();

        final RawFrame goAway = new RawFrame(FrameType.GOAWAY.getValue(), 0, 0, goAwayPayload);

        Assertions.assertDoesNotThrow(() -> mux.onInput(ByteBuffer.wrap(encodeFrame(goAway))));

        Mockito.verify(streamHandler, Mockito.times(1)).failed(exceptionCaptor.capture());
        Assertions.assertInstanceOf(org.apache.hc.core5.http.RequestNotExecutedException.class, exceptionCaptor.getValue());
    }

}