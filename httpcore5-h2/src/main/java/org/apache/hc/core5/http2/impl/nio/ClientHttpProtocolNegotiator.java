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
import java.nio.channels.ByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ExceptionListener;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http2.H2ConnectionException;
import org.apache.hc.core5.http2.H2Error;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.frame.DefaultFrameFactory;
import org.apache.hc.core5.http2.nio.AsyncPushConsumer;
import org.apache.hc.core5.http2.nio.HandlerFactory;
import org.apache.hc.core5.reactor.IOEventHandler;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.util.Args;

/**
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE_CONDITIONAL)
public class ClientHttpProtocolNegotiator implements IOEventHandler {

    // PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n
    final static byte[] PREFACE = new byte[] {
            0x50, 0x52, 0x49, 0x20, 0x2a, 0x20, 0x48, 0x54, 0x54, 0x50,
            0x2f, 0x32, 0x2e, 0x30, 0x0d, 0x0a, 0x0d, 0x0a, 0x53, 0x4d,
            0x0d, 0x0a, 0x0d, 0x0a};

    private final HttpProcessor httpProcessor;
    private final Charset charset;
    private final H2Config h2Config;
    private final HandlerFactory<AsyncPushConsumer> pushHandlerFactory;
    private final Http2StreamListener streamListener;
    private final ExceptionListener errorListener;

    public ClientHttpProtocolNegotiator(
            final HttpProcessor httpProcessor,
            final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
            final Charset charset,
            final H2Config h2Config,
            final Http2StreamListener streamListener,
            final ExceptionListener errorListener) {
        this.httpProcessor = Args.notNull(httpProcessor, "HTTP processor");
        this.pushHandlerFactory = pushHandlerFactory;
        this.charset = charset != null ? charset : StandardCharsets.US_ASCII;
        this.h2Config = h2Config != null ? h2Config : H2Config.DEFAULT;
        this.streamListener = streamListener;
        this.errorListener = errorListener;
    }

    protected ClientHttp2StreamMultiplexer createStreamMultiplexer(final IOSession ioSession) {
        return new ClientHttp2StreamMultiplexer(ioSession, DefaultFrameFactory.INSTANCE, httpProcessor,
                pushHandlerFactory, charset, h2Config, streamListener);
    }

    @Override
    public void connected(final IOSession ioSession) {
        try {
            final ByteChannel channel = ioSession.channel();
            final ByteBuffer preface = ByteBuffer.wrap(PREFACE);
            final int bytesWritten = channel.write(preface);
            if (bytesWritten != PREFACE.length) {
                throw new H2ConnectionException(H2Error.INTERNAL_ERROR, "HTTP/2 preface failed");
            }
        } catch (IOException ex) {
            ioSession.shutdown();
            if (errorListener != null) {
                errorListener.onError(ex);
            }
            return;
        }
        final ClientHttp2StreamMultiplexer streamMultiplexer = createStreamMultiplexer(ioSession);
        final IOEventHandler newHandler = new ClientHttp2IOEventHandler(streamMultiplexer, errorListener);
        newHandler.connected(ioSession);
        ioSession.setHandler(newHandler);
    }

    @Override
    public void inputReady(final IOSession session) {
    }

    @Override
    public void outputReady(final IOSession session) {
    }

    @Override
    public void timeout(final IOSession session) {
    }

    @Override
    public void disconnected(final IOSession session) {
    }

}
