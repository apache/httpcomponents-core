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

import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http2.H2ConnectionException;
import org.apache.hc.core5.http2.H2Error;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.frame.DefaultFrameFactory;
import org.apache.hc.core5.http2.nio.AsyncExchangeHandler;
import org.apache.hc.core5.http2.nio.HandlerFactory;
import org.apache.hc.core5.reactor.IOEventHandler;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.util.Args;

/**
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE_CONDITIONAL)
public class ServerHttpProtocolNegotiator implements IOEventHandler {

    final static byte[] PREFACE = ClientHttpProtocolNegotiator.PREFACE;

    private final HttpProcessor httpProcessor;
    private final HandlerFactory<AsyncExchangeHandler> exchangeHandlerFactory;
    private final Charset charset;
    private final H2Config h2Config;
    private final Http2StreamListener streamListener;
    private final HttpErrorListener errorListener;
    private final ByteBuffer bytebuf;

    public ServerHttpProtocolNegotiator(
            final HttpProcessor httpProcessor,
            final HandlerFactory<AsyncExchangeHandler> exchangeHandlerFactory,
            final Charset charset,
            final H2Config h2Config,
            final Http2StreamListener streamListener,
            final HttpErrorListener errorListener) {
        this.httpProcessor = Args.notNull(httpProcessor, "HTTP processor");
        this.exchangeHandlerFactory = Args.notNull(exchangeHandlerFactory, "Exchange handler factory");
        this.charset = charset != null ? charset : StandardCharsets.US_ASCII;
        this.h2Config = h2Config != null ? h2Config : H2Config.DEFAULT;
        this.streamListener = streamListener;
        this.errorListener = errorListener;
        this.bytebuf = ByteBuffer.allocate(1024);
    }

    protected ServerHttp2StreamMultiplexer createStreamMultiplexer(final IOSession ioSession) {
        return new ServerHttp2StreamMultiplexer(ioSession, DefaultFrameFactory.INSTANCE, httpProcessor,
                exchangeHandlerFactory, charset, h2Config, streamListener);
    }

    @Override
    public void connected(final IOSession session) {
    }

    @Override
    public void inputReady(final IOSession session) {
        try {
            if (bytebuf.position() < PREFACE.length) {
                session.channel().read(bytebuf);
            }
            if (bytebuf.position() >= PREFACE.length) {
                bytebuf.flip();
                for (int i = 0; i < PREFACE.length; i++) {
                    if (bytebuf.get() != PREFACE[i]) {
                        throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "Unexpected HTTP/2 preface");
                    }
                }
                final ServerHttp2StreamMultiplexer streamMultiplexer = createStreamMultiplexer(session);
                streamMultiplexer.onConnect(bytebuf.hasRemaining() ? bytebuf : null);
                streamMultiplexer.onInput();
                session.setHandler(new ServerHttp2IOEventHandler(streamMultiplexer, errorListener));
            }
        } catch (Exception ex) {
            session.close();
            if (errorListener != null) {
                errorListener.onError(ex);
            }
        }
    }

    @Override
    public void outputReady(final IOSession session) {
    }

    @Override
    public void timeout(final IOSession session) {
        session.close();
        if (errorListener != null) {
            errorListener.onError(new SocketTimeoutException());
        }
    }

    @Override
    public void disconnected(final IOSession session) {
    }

}
