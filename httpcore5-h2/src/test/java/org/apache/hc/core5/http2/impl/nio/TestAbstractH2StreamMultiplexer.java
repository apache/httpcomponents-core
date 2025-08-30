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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.Lock;

import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.impl.BasicHttpConnectionMetrics;
import org.apache.hc.core5.http.impl.CharCodingSupport;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.nio.command.ExecutableCommand;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http2.H2ConnectionException;
import org.apache.hc.core5.http2.H2Error;
import org.apache.hc.core5.http2.H2StreamResetException;
import org.apache.hc.core5.http2.WritableByteChannelMock;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.config.H2Param;
import org.apache.hc.core5.http2.config.H2Setting;
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
            super(ioSession, frameFactory, idGenerator, httpProcessor, charCodingConfig, h2Config, streamListener);
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

}

