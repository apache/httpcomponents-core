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
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.impl.ConnectionListener;
import org.apache.hc.core5.http.impl.nio.HttpConnectionEventHandler;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.nio.command.ExecutionCommand;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.frame.DefaultFrameFactory;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.IOEventHandler;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.util.Args;

/**
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE_CONDITIONAL)
public class ClientHttpProtocolNegotiator implements HttpConnectionEventHandler {

    // PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n
    final static byte[] PREFACE = new byte[] {
            0x50, 0x52, 0x49, 0x20, 0x2a, 0x20, 0x48, 0x54, 0x54, 0x50,
            0x2f, 0x32, 0x2e, 0x30, 0x0d, 0x0a, 0x0d, 0x0a, 0x53, 0x4d,
            0x0d, 0x0a, 0x0d, 0x0a};

    private final IOSession ioSession;
    private final HttpProcessor httpProcessor;
    private final CharCodingConfig charCodingConfig;
    private final H2Config h2Config;
    private final HandlerFactory<AsyncPushConsumer> pushHandlerFactory;
    private final ConnectionListener connectionListener;
    private final Http2StreamListener streamListener;
    private final ByteBuffer preface;

    public ClientHttpProtocolNegotiator(
            final IOSession ioSession,
            final HttpProcessor httpProcessor,
            final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
            final CharCodingConfig charCodingConfig,
            final H2Config h2Config,
            final ConnectionListener connectionListener,
            final Http2StreamListener streamListener) {
        this.ioSession = Args.notNull(ioSession, "I/O session");
        this.httpProcessor = Args.notNull(httpProcessor, "HTTP processor");
        this.pushHandlerFactory = pushHandlerFactory;
        this.charCodingConfig = charCodingConfig != null ? charCodingConfig : CharCodingConfig.DEFAULT;
        this.h2Config = h2Config != null ? h2Config : H2Config.DEFAULT;
        this.streamListener = streamListener;
        this.connectionListener = connectionListener;
        this.preface = ByteBuffer.wrap(PREFACE);
    }

    protected ClientHttp2StreamMultiplexer createStreamMultiplexer(final IOSession ioSession) {
        return new ClientHttp2StreamMultiplexer(ioSession, DefaultFrameFactory.INSTANCE, httpProcessor,
                pushHandlerFactory, charCodingConfig, h2Config, connectionListener, streamListener);
    }

    @Override
    public void connected(final IOSession ioSession) {
        outputReady(ioSession);
    }

    @Override
    public void inputReady(final IOSession session) {
    }

    @Override
    public void outputReady(final IOSession session) {
        if (preface.hasRemaining()) {
            try {
                final ByteChannel channel = ioSession.channel();
                channel.write(preface);
            } catch (IOException ex) {
                ioSession.shutdown();
                if (connectionListener != null) {
                    connectionListener.onError(this, ex);
                }
                return;
            }
        }
        if (!preface.hasRemaining()) {
            final ClientHttp2StreamMultiplexer streamMultiplexer = createStreamMultiplexer(ioSession);
            final IOEventHandler newHandler = new ClientHttp2IOEventHandler(streamMultiplexer);
            newHandler.connected(ioSession);
            ioSession.setHandler(newHandler);
        }
    }

    @Override
    public void timeout(final IOSession session) {
        exception(session, new SocketTimeoutException());
    }

    private void failPendingCommands(final Exception cause) {
        for (;;) {
            final Command command = ioSession.getCommand();
            if (command != null) {
                if (command instanceof ExecutionCommand) {
                    final ExecutionCommand executionCommand = (ExecutionCommand) command;
                    final AsyncClientExchangeHandler exchangeHandler = executionCommand.getExchangeHandler();
                    exchangeHandler.failed(cause);
                } else {
                    command.cancel();
                }
            } else {
                break;
            }
        }
    }

    @Override
    public void exception(final IOSession session, final Exception cause) {
        if (connectionListener != null) {
            connectionListener.onError(this, new SocketTimeoutException());
        }
        try {
            failPendingCommands(cause);
        } finally {
            session.shutdown();
        }
    }

    @Override
    public void disconnected(final IOSession session) {
    }

    @Override
    public EndpointDetails getEndpointDetails() {
        return null;
    }

    @Override
    public void setSocketTimeout(final int timeout) {
        ioSession.setSocketTimeout(timeout);
    }

    @Override
    public int getSocketTimeout() {
        return ioSession.getSocketTimeout();
    }

    @Override
    public ProtocolVersion getProtocolVersion() {
        return null;
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return ioSession.getRemoteAddress();
    }

    @Override
    public SocketAddress getLocalAddress() {
        return ioSession.getLocalAddress();
    }

    @Override
    public boolean isOpen() {
        return !ioSession.isClosed();
    }

    @Override
    public void close() throws IOException {
        ioSession.close();
    }

    @Override
    public void shutdown() throws IOException {
        ioSession.shutdown();
    }

}
