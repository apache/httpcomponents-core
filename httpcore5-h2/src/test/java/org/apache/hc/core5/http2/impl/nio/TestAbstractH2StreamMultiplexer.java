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

import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.impl.BasicHttpConnectionMetrics;
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
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class TestAbstractH2StreamMultiplexer {

    @Mock
    ProtocolIOSession protocolIOSession;
    @Mock
    HttpProcessor httpProcessor;
    @Mock
    H2StreamListener h2StreamListener;

    @BeforeEach
    public void prepareMocks() {
        MockitoAnnotations.openMocks(this);
    }

    static class H2StreamMultiplexerImpl extends AbstractH2StreamMultiplexer {

        public H2StreamMultiplexerImpl(
                final ProtocolIOSession ioSession,
                final FrameFactory frameFactory,
                final StreamIdGenerator idGenerator,
                final HttpProcessor httpProcessor,
                final CharCodingConfig charCodingConfig,
                final H2Config h2Config,
                final H2StreamListener streamListener) {
            super(ioSession, frameFactory, idGenerator, httpProcessor, charCodingConfig, h2Config, streamListener);
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
            return null;
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
    public void testInputOneFrame() throws Exception {
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
                DefaultFrameFactory.INSTANCE,
                StreamIdGenerator.ODD,
                httpProcessor,
                CharCodingConfig.DEFAULT,
                H2Config.custom()
                        .setMaxFrameSize(FrameConsts.MIN_FRAME_SIZE)
                        .build(),
                h2StreamListener);

        Assertions.assertThrows(H2ConnectionException.class, () ->
                streamMultiplexer.onInput(ByteBuffer.wrap(bytes)));
        Mockito.verify(h2StreamListener).onFrameInput(
                Mockito.same(streamMultiplexer),
                Mockito.eq(1),
                Mockito.any());

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
                    Mockito.same(streamMultiplexer),
                    Mockito.eq(1),
                    Mockito.any());
        });
    }

    @Test
    public void testInputMultipleFrames() throws Exception {
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
                DefaultFrameFactory.INSTANCE,
                StreamIdGenerator.ODD,
                httpProcessor,
                CharCodingConfig.DEFAULT,
                H2Config.custom()
                        .setMaxFrameSize(FrameConsts.MIN_FRAME_SIZE)
                        .build(),
                h2StreamListener);

        Assertions.assertThrows(H2ConnectionException.class, () ->
                streamMultiplexer.onInput(ByteBuffer.wrap(bytes)));
        Mockito.verify(h2StreamListener).onFrameInput(
                Mockito.same(streamMultiplexer),
                Mockito.eq(1),
                Mockito.any());

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
                    Mockito.same(streamMultiplexer),
                    Mockito.eq(1),
                    Mockito.any());
        });
    }

}

