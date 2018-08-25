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

import javax.net.ssl.SSLSession;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.nio.HttpConnectionEventHandler;
import org.apache.hc.core5.http.impl.nio.ServerHttp1IOEventHandler;
import org.apache.hc.core5.http.impl.nio.ServerHttp1StreamDuplexer;
import org.apache.hc.core5.http.impl.nio.ServerHttp1StreamDuplexerFactory;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.ssl.ApplicationProtocols;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.io.SocketTimeoutExceptionFactory;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.hc.core5.util.Args;

/**
 * I/O event handler for events fired by {@link ProtocolIOSession} that implements
 * server side of the HTTP/2 protocol negotiation handshake
 * based on {@link HttpVersionPolicy} configuration.
 *
 * @since 5.0
 */
@Internal
public class ServerHttpProtocolNegotiator implements HttpConnectionEventHandler {

    final static byte[] PREFACE = ClientHttpProtocolNegotiator.PREFACE;

    private final ProtocolIOSession ioSession;
    private final ServerHttp1StreamDuplexerFactory http1StreamHandlerFactory;
    private final ServerHttp2StreamMultiplexerFactory http2StreamHandlerFactory;
    private final HttpVersionPolicy versionPolicy;
    private final ByteBuffer bytebuf;

    private volatile boolean expectValidH2Preface;

    public ServerHttpProtocolNegotiator(
            final ProtocolIOSession ioSession,
            final ServerHttp1StreamDuplexerFactory http1StreamHandlerFactory,
            final ServerHttp2StreamMultiplexerFactory http2StreamHandlerFactory,
            final HttpVersionPolicy versionPolicy) {
        this.ioSession = Args.notNull(ioSession, "I/O session");
        this.http1StreamHandlerFactory = Args.notNull(http1StreamHandlerFactory, "HTTP/1.1 stream handler factory");
        this.http2StreamHandlerFactory = Args.notNull(http2StreamHandlerFactory, "HTTP/2 stream handler factory");
        this.versionPolicy = versionPolicy != null ? versionPolicy : HttpVersionPolicy.NEGOTIATE;
        this.bytebuf = ByteBuffer.allocate(1024);
    }

    @Override
    public void connected(final IOSession session) {
        try {
            final TlsDetails tlsDetails = ioSession.getTlsDetails();
            switch (versionPolicy) {
                case NEGOTIATE:
                    if (tlsDetails != null) {
                        if (ApplicationProtocols.HTTP_2.id.equals(tlsDetails.getApplicationProtocol())) {
                            expectValidH2Preface = true;
                            // Proceed with the H2 preface
                            break;
                        }
                    }
                    break;
                case FORCE_HTTP_1:
                    final ServerHttp1StreamDuplexer http1StreamHandler = http1StreamHandlerFactory.create(
                            tlsDetails != null ? URIScheme.HTTPS.id : URIScheme.HTTP.id,
                            ioSession);
                    ioSession.upgrade(new ServerHttp1IOEventHandler(http1StreamHandler));
                    http1StreamHandler.onConnect(null);
                    break;
            }
        } catch (final Exception ex) {
            exception(session, ex);
        }
    }

    @Override
    public void inputReady(final IOSession session) {
        try {
            boolean endOfStream = false;
            if (bytebuf.position() < PREFACE.length) {
                final int bytesRead = session.channel().read(bytebuf);
                if (bytesRead == -1) {
                    endOfStream = true;
                }
            }
            if (bytebuf.position() >= PREFACE.length) {
                bytebuf.flip();

                boolean validH2Preface = true;
                for (int i = 0; i < PREFACE.length; i++) {
                    if (bytebuf.get() != PREFACE[i]) {
                        if (expectValidH2Preface) {
                            throw new HttpException("Unexpected HTTP/2 preface");
                        }
                        validH2Preface = false;
                    }
                }
                if (validH2Preface) {
                    final ServerHttp2StreamMultiplexer http2StreamHandler = http2StreamHandlerFactory.create(ioSession);
                    ioSession.upgrade(new ServerHttp2IOEventHandler(http2StreamHandler));
                    http2StreamHandler.onConnect(bytebuf.hasRemaining() ? bytebuf : null);
                    http2StreamHandler.onInput();
                } else {
                    final TlsDetails tlsDetails = ioSession.getTlsDetails();
                    final ServerHttp1StreamDuplexer http1StreamHandler = http1StreamHandlerFactory.create(
                            tlsDetails != null ? URIScheme.HTTPS.id : URIScheme.HTTP.id,
                            ioSession);
                    ioSession.upgrade(new ServerHttp1IOEventHandler(http1StreamHandler));
                    bytebuf.rewind();
                    http1StreamHandler.onConnect(bytebuf);
                    http1StreamHandler.onInput();
                }
            } else {
                if (endOfStream) {
                    throw new ConnectionClosedException();
                }
            }
        } catch (final Exception ex) {
            exception(session, ex);
        }
    }

    @Override
    public void outputReady(final IOSession session) {
    }

    @Override
    public void timeout(final IOSession session, final int timeoutMillis) {
        exception(session, SocketTimeoutExceptionFactory.create(timeoutMillis));
    }

    @Override
    public void exception(final IOSession session, final Exception cause) {
        session.close(CloseMode.IMMEDIATE);
    }

    @Override
    public void disconnected(final IOSession session) {
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
