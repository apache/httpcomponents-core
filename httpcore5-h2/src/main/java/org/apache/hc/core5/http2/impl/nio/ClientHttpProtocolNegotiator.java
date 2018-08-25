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
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;

import javax.net.ssl.SSLSession;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.impl.nio.ClientHttp1IOEventHandler;
import org.apache.hc.core5.http.impl.nio.ClientHttp1StreamDuplexer;
import org.apache.hc.core5.http.impl.nio.ClientHttp1StreamDuplexerFactory;
import org.apache.hc.core5.http.impl.nio.HttpConnectionEventHandler;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.command.RequestExecutionCommand;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.ssl.ApplicationProtocols;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.io.SocketTimeoutExceptionFactory;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.IOEventHandler;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.hc.core5.util.Args;

/**
 * I/O event handler for events fired by {@link ProtocolIOSession} that implements
 * client side of the HTTP/2 protocol negotiation handshake
 * based on {@link HttpVersionPolicy} configuration.
 *
 * @since 5.0
 */
@Internal
public class ClientHttpProtocolNegotiator implements HttpConnectionEventHandler {

    // PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n
    final static byte[] PREFACE = new byte[] {
            0x50, 0x52, 0x49, 0x20, 0x2a, 0x20, 0x48, 0x54, 0x54, 0x50,
            0x2f, 0x32, 0x2e, 0x30, 0x0d, 0x0a, 0x0d, 0x0a, 0x53, 0x4d,
            0x0d, 0x0a, 0x0d, 0x0a};

    private final ProtocolIOSession ioSession;
    private final ClientHttp1StreamDuplexerFactory http1StreamHandlerFactory;
    private final ClientHttp2StreamMultiplexerFactory http2StreamHandlerFactory;
    private final HttpVersionPolicy versionPolicy;

    private volatile ByteBuffer preface;

    public ClientHttpProtocolNegotiator(
            final ProtocolIOSession ioSession,
            final ClientHttp1StreamDuplexerFactory http1StreamHandlerFactory,
            final ClientHttp2StreamMultiplexerFactory http2StreamHandlerFactory,
            final HttpVersionPolicy versionPolicy) {
        this.ioSession = Args.notNull(ioSession, "I/O session");
        this.http1StreamHandlerFactory = Args.notNull(http1StreamHandlerFactory, "HTTP/1.1 stream handler factory");
        this.http2StreamHandlerFactory = Args.notNull(http2StreamHandlerFactory, "HTTP/2 stream handler factory");
        this.versionPolicy = versionPolicy != null ? versionPolicy : HttpVersionPolicy.NEGOTIATE;
    }

    private void startHttp1(final IOSession session) {
        final ClientHttp1StreamDuplexer http1StreamHandler = http1StreamHandlerFactory.create(ioSession);
        final ClientHttp1IOEventHandler newHandler = new ClientHttp1IOEventHandler(http1StreamHandler);
        try {
            ioSession.upgrade(newHandler);
            newHandler.connected(session);
        } catch (final Exception ex) {
            newHandler.exception(session, ex);
            session.close(CloseMode.IMMEDIATE);
        }
    }

    private void startHttp2(final IOSession session) {
        final ClientHttp2StreamMultiplexer streamMultiplexer = http2StreamHandlerFactory.create(ioSession);
        final IOEventHandler newHandler = new ClientHttp2IOEventHandler(streamMultiplexer);
        try {
            ioSession.upgrade(newHandler);
            newHandler.connected(session);
        } catch (final Exception ex) {
            newHandler.exception(session, ex);
            session.close(CloseMode.IMMEDIATE);
        }
    }

    @Override
    public void connected(final IOSession session) throws IOException {
        switch (versionPolicy) {
            case NEGOTIATE:
                final TlsDetails tlsDetails = ioSession.getTlsDetails();
                if (tlsDetails != null) {
                    if (ApplicationProtocols.HTTP_2.id.equals(tlsDetails.getApplicationProtocol())) {
                        // Proceed with the H2 preface
                        preface = ByteBuffer.wrap(PREFACE);
                    }
                }
                break;
            case FORCE_HTTP_2:
                preface = ByteBuffer.wrap(PREFACE);
                break;
        }
        if (preface == null) {
            startHttp1(session);
        } else {
            if (preface.hasRemaining()) {
                final ByteChannel channel = session.channel();
                channel.write(preface);
            }
            if (!preface.hasRemaining()) {
                startHttp2(session);
            }
        }
    }

    @Override
    public void inputReady(final IOSession session)throws IOException  {
        outputReady(session);
    }

    @Override
    public void outputReady(final IOSession session) throws IOException {
        if (preface != null) {
            if (preface.hasRemaining()) {
                final ByteChannel channel = session.channel();
                channel.write(preface);
            }
            if (!preface.hasRemaining()) {
                startHttp2(session);
            }
        } else {
            session.close(CloseMode.IMMEDIATE);
        }
    }

    @Override
    public void timeout(final IOSession session, final int timeoutMillis) {
        exception(session, SocketTimeoutExceptionFactory.create(timeoutMillis));
    }

    @Override
    public void exception(final IOSession session, final Exception cause) {
        try {
            for (;;) {
                final Command command = ioSession.poll();
                if (command != null) {
                    if (command instanceof RequestExecutionCommand) {
                        final RequestExecutionCommand executionCommand = (RequestExecutionCommand) command;
                        final AsyncClientExchangeHandler exchangeHandler = executionCommand.getExchangeHandler();
                        exchangeHandler.failed(cause);
                        exchangeHandler.releaseResources();
                    } else {
                        command.cancel();
                    }
                } else {
                    break;
                }
            }
        } finally {
            session.close(CloseMode.IMMEDIATE);
        }
    }

    @Override
    public void disconnected(final IOSession session) {
        for (;;) {
            final Command command = ioSession.poll();
            if (command != null) {
                if (command instanceof RequestExecutionCommand) {
                    final RequestExecutionCommand executionCommand = (RequestExecutionCommand) command;
                    final AsyncClientExchangeHandler exchangeHandler = executionCommand.getExchangeHandler();
                    exchangeHandler.failed(new ConnectionClosedException());
                    exchangeHandler.releaseResources();
                } else {
                    command.cancel();
                }
            } else {
                break;
            }
        }
    }

    @Override
    public SSLSession getSSLSession() {
        final TlsDetails tlsDetails = ioSession.getTlsDetails();
        return tlsDetails != null ? tlsDetails.getSSLSession() : null;
    }

    @Override
    public EndpointDetails getEndpointDetails() {
        return null;
    }

    @Override
    public void setSocketTimeoutMillis(final int timeout) {
        ioSession.setSocketTimeoutMillis(timeout);
    }

    @Override
    public int getSocketTimeoutMillis() {
        return ioSession.getSocketTimeoutMillis();
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
    public void close(final CloseMode closeMode) {
        ioSession.close(closeMode);
    }

}
