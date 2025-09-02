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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.stream.IntStream;

import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
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
import org.apache.hc.core5.http2.frame.DefaultFrameFactory;
import org.apache.hc.core5.http2.frame.FrameConsts;
import org.apache.hc.core5.http2.frame.FrameFactory;
import org.apache.hc.core5.http2.frame.FrameType;
import org.apache.hc.core5.http2.frame.RawFrame;
import org.apache.hc.core5.http2.frame.StreamIdGenerator;
import org.apache.hc.core5.http2.hpack.HPackEncoder;
import org.apache.hc.core5.reactor.Command;
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
    void prepareMocks() throws IOException {
        MockitoAnnotations.openMocks(this);
        Mockito.when(protocolIOSession.getLock()).thenReturn(lock);

        // For Core versions where IOSession has only write(ByteBuffer)
        Mockito.when(protocolIOSession.write(ArgumentMatchers.any(ByteBuffer.class)))
                .thenAnswer(inv -> {
                    final ByteBuffer b = inv.getArgument(0, ByteBuffer.class);
                    final int n = b.remaining();
                    b.position(b.limit());
                    return n;
                });

        Mockito.doNothing().when(protocolIOSession).setEvent(ArgumentMatchers.anyInt());
        Mockito.doNothing().when(protocolIOSession).clearEvent(ArgumentMatchers.anyInt());
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

        void emitPriorityUpdateForTest(final ByteBuffer payload) throws IOException {
            writeExtensionFrame(FrameType.PRIORITY_UPDATE.getValue(), 0, 0, payload);
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

    @Test
    void testPriorityUpdateInputIsRejected() throws Exception {
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

        final WritableByteChannelMock writableChannel = new WritableByteChannelMock(1024);
        final FrameOutputBuffer outbuffer = new FrameOutputBuffer(16 * 1024);

        final byte[] ascii = "u=3,i".getBytes(StandardCharsets.US_ASCII);
        final ByteBuffer payload = ByteBuffer.allocate(4 + ascii.length);
        payload.putInt(1);
        payload.put(ascii);
        payload.flip();

        final RawFrame priUpd = new RawFrame(FrameType.PRIORITY_UPDATE.getValue(), 0, 0, payload);
        outbuffer.write(priUpd, writableChannel);
        final byte[] bytes = writableChannel.toByteArray();

        // New behavior: tolerate/accept PRIORITY_UPDATE input (no exception)
        Assertions.assertDoesNotThrow(() ->
                streamMultiplexer.onInput(ByteBuffer.wrap(bytes)));

        // And we did observe the incoming frame at the listener
        Mockito.verify(h2StreamListener).onFrameInput(
                ArgumentMatchers.same(streamMultiplexer),
                ArgumentMatchers.eq(0),
                ArgumentMatchers.any());
    }


    @Test
    void testWriteExtensionPriorityUpdateIsFlushed() throws Exception {
        final H2Config h2Config = H2Config.custom().build();

        final H2StreamMultiplexerImpl streamMultiplexer = new H2StreamMultiplexerImpl(
                protocolIOSession,
                FRAME_FACTORY,
                StreamIdGenerator.ODD,
                httpProcessor,
                CharCodingConfig.DEFAULT,
                h2Config,
                h2StreamListener,
                () -> streamHandler);

        final byte[] ascii = "u=3,i".getBytes(StandardCharsets.US_ASCII);
        final ByteBuffer payload = ByteBuffer.allocate(4 + ascii.length);
        payload.putInt(1);
        payload.put(ascii);
        payload.flip();

        streamMultiplexer.emitPriorityUpdateForTest(payload);
        streamMultiplexer.onOutput();

        Mockito.verify(protocolIOSession, Mockito.atLeastOnce())
                .write(ArgumentMatchers.any(ByteBuffer.class));
    }


    static final class PriorityHeaderSender implements H2StreamHandler {

        private final H2StreamChannel channel;
        private final List<Header> headers;
        private final boolean endStream;
        private boolean sent;

        PriorityHeaderSender(final H2StreamChannel channel, final List<Header> headers, final boolean endStream) {
            this.channel = channel;
            this.headers = headers;
            this.endStream = endStream;
            this.sent = false;
        }

        @Override
        public HandlerFactory<AsyncPushConsumer> getPushHandlerFactory() {
            return null;
        }

        @Override
        public boolean isOutputReady() {
            return !sent;
        }

        @Override
        public void produceOutput() throws IOException, HttpException {
            if (!sent) {
                channel.submit(headers, endStream);
                sent = true;
            }
        }

        @Override
        public void consumePromise(final List<Header> headers) { }

        @Override
        public void consumeHeader(final List<Header> headers, final boolean endStream) { }

        @Override
        public void updateInputCapacity() { }

        @Override
        public void consumeData(final ByteBuffer src, final boolean endStream) { }

        @Override
        public void handle(final org.apache.hc.core5.http.HttpException ex, final boolean endStream) throws org.apache.hc.core5.http.HttpException, IOException {
            throw ex;
        }

        @Override
        public void failed(final Exception cause) { }

        @Override
        public void releaseResources() { }
    }

    // A multiplexer variant that creates a locally-initiated stream with our PriorityHeaderSender.
    static class H2StreamMultiplexerLocal extends AbstractH2StreamMultiplexer {

        private final List<Header> headersToSend;
        private final boolean endStream;

        H2StreamMultiplexerLocal(
                final ProtocolIOSession ioSession,
                final FrameFactory frameFactory,
                final StreamIdGenerator idGenerator,
                final HttpProcessor httpProcessor,
                final CharCodingConfig charCodingConfig,
                final H2Config h2Config,
                final H2StreamListener streamListener,
                final List<Header> headersToSend,
                final boolean endStream) {
            super(ioSession, frameFactory, idGenerator, httpProcessor, charCodingConfig, h2Config, streamListener);
            this.headersToSend = headersToSend;
            this.endStream = endStream;
        }

        @Override
        void acceptHeaderFrame() throws H2ConnectionException { }

        @Override
        void acceptPushRequest() throws H2ConnectionException { }

        @Override
        void acceptPushFrame() throws H2ConnectionException { }

        @Override
        H2StreamHandler createRemotelyInitiatedStream(
                final H2StreamChannel channel,
                final HttpProcessor httpProcessor,
                final BasicHttpConnectionMetrics connMetrics,
                final HandlerFactory<AsyncPushConsumer> pushHandlerFactory) {
            return null;
        }

        @Override
        H2StreamHandler createLocallyInitiatedStream(
                final ExecutableCommand command,
                final H2StreamChannel channel,
                final HttpProcessor httpProcessor,
                final BasicHttpConnectionMetrics connMetrics) {
            return new PriorityHeaderSender(channel, headersToSend, endStream);
        }
    }

    private static final class FrameStub {
        final int type;
        final int streamId;
        FrameStub(final int type, final int streamId) {
            this.type = type;
            this.streamId = streamId;
        }
    }

    private static List<FrameStub> parseFrames(final byte[] all) {
        final List<FrameStub> out = new ArrayList<>();
        int p = 0;
        while (p + 9 <= all.length) {
            final int len = ((all[p] & 0xff) << 16) | ((all[p + 1] & 0xff) << 8) | (all[p + 2] & 0xff);
            final int type = all[p + 3] & 0xff;
            // flags = all[p + 4] & 0xff;
            final int sid = ((all[p + 5] & 0x7f) << 24) | ((all[p + 6] & 0xff) << 16) | ((all[p + 7] & 0xff) << 8) | (all[p + 8] & 0xff);
            p += 9;
            if (p + len > all.length) break;
            out.add(new FrameStub(type, sid));
            p += len;
        }
        return out;
    }

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

        // Headers including PRIORITY (RFC 9218)
        final List<Header> reqHeaders = Arrays.asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":scheme", "https"),
                new BasicHeader(":path", "/"),
                new BasicHeader(":authority", "example.test"),
                new BasicHeader(HttpHeaders.PRIORITY, "u=3,i")
        );

        final H2Config h2Config = H2Config.custom().build();

        final H2StreamMultiplexerLocal mux = new H2StreamMultiplexerLocal(
                protocolIOSession,
                FRAME_FACTORY,
                StreamIdGenerator.ODD, // locally initiated streams get odd IDs -> 1
                httpProcessor,
                CharCodingConfig.DEFAULT,
                h2Config,
                h2StreamListener,
                reqHeaders,
                true);

        // Start connection (sends our SETTINGS etc.)
        mux.onConnect();
        writes.clear(); // ignore noise from onConnect

        // Make remote send SETTINGS so remoteSettingState -> ACKED AND allow PRIORITY_UPDATE (NO_RFC7540=1)
        final WritableByteChannelMock writable = new WritableByteChannelMock(256);
        final FrameOutputBuffer fob = new FrameOutputBuffer(16 * 1024);
        final ByteBuffer pl = ByteBuffer.allocate(6);
        pl.putShort((short) 0x0009); // SETTINGS_NO_RFC7540_PRIORITIES
        pl.putInt(1);                // value = 1 (peer opts-in)
        pl.flip();
        final RawFrame incomingSettings = new RawFrame(FrameType.SETTINGS.getValue(), 0, 0, pl);
        fob.write(incomingSettings, writable);
        mux.onInput(ByteBuffer.wrap(writable.toByteArray()));
        writes.clear(); // ignore the ACK we just sent

        // Queue a dummy command so a locally-initiated stream gets created
        final ExecutableCommand exec = Mockito.mock(ExecutableCommand.class, Mockito.withSettings().extraInterfaces(Command.class));
        Mockito.when(protocolIOSession.poll()).thenReturn(exec, (Command) null);

        // Drive output (this should trigger PriorityHeaderSender -> channel.submit)
        mux.onOutput();

        // Combine captured writes
        int total = 0;
        for (final byte[] a : writes) total += a.length;
        final byte[] all = new byte[total];
        int pos = 0;
        for (final byte[] a : writes) {
            System.arraycopy(a, 0, all, pos, a.length);
            pos += a.length;
        }

        // Parse frames and assert ordering: PRIORITY_UPDATE (type 0x10, sid 0) BEFORE HEADERS (0x01, sid 1)
        final List<FrameStub> frames = parseFrames(all);

        // Find the first PRIORITY_UPDATE and the first HEADERS for stream 1
        int idxPriUpd = -1;
        int idxHeaders = -1;
        for (int i = 0; i < frames.size(); i++) {
            final FrameStub f = frames.get(i);
            if (idxPriUpd < 0 && f.type == FrameType.PRIORITY_UPDATE.getValue() && f.streamId == 0) {
                idxPriUpd = i;
            }
            if (idxHeaders < 0 && f.type == FrameType.HEADERS.getValue() && f.streamId == 1) {
                idxHeaders = i;
            }
        }

        Assertions.assertTrue(idxPriUpd >= 0, "PRIORITY_UPDATE not emitted");
        Assertions.assertTrue(idxHeaders >= 0, "HEADERS not emitted");
        Assertions.assertTrue(idxPriUpd < idxHeaders, "PRIORITY_UPDATE must be sent before HEADERS");
    }


    @Test
    void testPriorityUpdateSuppressedAfterSettingsWithoutNoH2() throws Exception {
        final List<byte[]> writes = new ArrayList<>();
        Mockito.when(protocolIOSession.write(ArgumentMatchers.any(ByteBuffer.class)))
                .thenAnswer(inv -> { final ByteBuffer b = inv.getArgument(0, ByteBuffer.class);
                    final byte[] c = new byte[b.remaining()]; b.get(c); writes.add(c); return c.length; });
        Mockito.doNothing().when(protocolIOSession).setEvent(ArgumentMatchers.anyInt());
        Mockito.doNothing().when(protocolIOSession).clearEvent(ArgumentMatchers.anyInt());

        final List<Header> reqHeaders = Arrays.asList(
                new BasicHeader(":method","GET"),
                new BasicHeader(":scheme","https"),
                new BasicHeader(":path","/"),
                new BasicHeader(":authority","example.test"),
                new BasicHeader(HttpHeaders.PRIORITY, "u=3") // would normally trigger PRIORITY_UPDATE
        );

        final H2StreamMultiplexerLocal mux = new H2StreamMultiplexerLocal(
                protocolIOSession, FRAME_FACTORY, StreamIdGenerator.ODD,
                httpProcessor, CharCodingConfig.DEFAULT, H2Config.custom().build(),
                h2StreamListener, reqHeaders, true);

        mux.onConnect();
        writes.clear();

        // Inject server SETTINGS WITHOUT 0x9
        final WritableByteChannelMock w = new WritableByteChannelMock(256);
        final FrameOutputBuffer fob = new FrameOutputBuffer(16 * 1024);
        final RawFrame serverSettings = new RawFrame(FrameType.SETTINGS.getValue(), 0, 0, ByteBuffer.allocate(0));
        fob.write(serverSettings, w);
        mux.onInput(ByteBuffer.wrap(w.toByteArray()));
        writes.clear(); // drop our ACK

        // Queue a request (locally-initiated)
        final ExecutableCommand exec = Mockito.mock(ExecutableCommand.class, Mockito.withSettings().extraInterfaces(Command.class));
        Mockito.when(protocolIOSession.poll()).thenReturn(exec, (Command) null);

        mux.onOutput();

        // Stitch writes and parse
        final int total = writes.stream().mapToInt(a -> a.length).sum();
        final byte[] all = new byte[total]; int p = 0;
        for (final byte[] a : writes) { System.arraycopy(a, 0, all, p, a.length); p += a.length; }

        final List<FrameStub> frames = parseFrames(all);
        // Assert: no PRIORITY_UPDATE frames (type 0x10)
        Assertions.assertTrue(frames.stream().noneMatch(f -> f.type == FrameType.PRIORITY_UPDATE.getValue()),
                "PRIORITY_UPDATE must be suppressed after first SETTINGS without NO_RFC7540");
    }


    @Test
    void testPriorityUpdateContinuesAfterSettingsWithNoH2Equals1() throws Exception {
        final List<byte[]> writes = new ArrayList<>();
        Mockito.when(protocolIOSession.write(ArgumentMatchers.any(ByteBuffer.class)))
                .thenAnswer(inv -> { final ByteBuffer b = inv.getArgument(0, ByteBuffer.class);
                    final byte[] c = new byte[b.remaining()]; b.get(c); writes.add(c); return c.length; });
        Mockito.doNothing().when(protocolIOSession).setEvent(ArgumentMatchers.anyInt());
        Mockito.doNothing().when(protocolIOSession).clearEvent(ArgumentMatchers.anyInt());

        final List<Header> reqHeaders = Arrays.asList(
                new BasicHeader(":method","GET"),
                new BasicHeader(":scheme","https"),
                new BasicHeader(":path","/"),
                new BasicHeader(":authority","example.test"),
                new BasicHeader(HttpHeaders.PRIORITY, "u=0,i") // triggers PRIORITY_UPDATE
        );

        final H2StreamMultiplexerLocal mux = new H2StreamMultiplexerLocal(
                protocolIOSession, FRAME_FACTORY, StreamIdGenerator.ODD,
                httpProcessor, CharCodingConfig.DEFAULT, H2Config.custom().build(),
                h2StreamListener, reqHeaders, true);

        mux.onConnect();
        writes.clear();

        // Inject server SETTINGS with 0x9 = 1
        final WritableByteChannelMock w = new WritableByteChannelMock(256);
        final FrameOutputBuffer fob = new FrameOutputBuffer(16 * 1024);
        final ByteBuffer pl = ByteBuffer.allocate(6);
        pl.putShort((short) 0x0009); // SETTINGS_NO_RFC7540_PRIORITIES
        pl.putInt(1);                // value = 1
        pl.flip();
        final RawFrame serverSettings = new RawFrame(FrameType.SETTINGS.getValue(), 0, 0, pl);
        fob.write(serverSettings, w);
        mux.onInput(ByteBuffer.wrap(w.toByteArray()));
        writes.clear(); // drop our ACK

        // Queue a locally-initiated stream
        final ExecutableCommand exec = Mockito.mock(ExecutableCommand.class, Mockito.withSettings().extraInterfaces(Command.class));
        Mockito.when(protocolIOSession.poll()).thenReturn(exec, (Command) null);

        mux.onOutput();

        final int total = writes.stream().mapToInt(a -> a.length).sum();
        final byte[] all = new byte[total]; int p = 0;
        for (final byte[] a : writes) { System.arraycopy(a, 0, all, p, a.length); p += a.length; }

        final List<FrameStub> frames = parseFrames(all);

        final int idxPriUpd = IntStream.range(0, frames.size())
                .filter(i -> frames.get(i).type == FrameType.PRIORITY_UPDATE.getValue() && frames.get(i).streamId == 0)
                .findFirst().orElse(-1);
        Assertions.assertTrue(idxPriUpd >= 0, "PRIORITY_UPDATE should still be emitted when NO_RFC7540=1");
    }


}
