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
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLSession;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.nio.BufferedData;
import org.apache.hc.core5.http.impl.nio.HttpConnectionEventHandler;
import org.apache.hc.core5.http.impl.nio.ServerHttp1IOEventHandler;
import org.apache.hc.core5.http.impl.nio.ServerHttp1StreamDuplexer;
import org.apache.hc.core5.http.impl.nio.ServerHttp1StreamDuplexerFactory;
import org.apache.hc.core5.http.nio.command.CommandSupport;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.ssl.ApplicationProtocol;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.io.SocketTimeoutExceptionFactory;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Timeout;

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
    private final ServerH2StreamMultiplexerFactory http2StreamHandlerFactory;
    private final HttpVersionPolicy versionPolicy;
    private final BufferedData inBuf;
    private final AtomicReference<HttpConnectionEventHandler> protocolHandlerRef;

    private volatile boolean expectValidH2Preface;

    public ServerHttpProtocolNegotiator(
            final ProtocolIOSession ioSession,
            final ServerHttp1StreamDuplexerFactory http1StreamHandlerFactory,
            final ServerH2StreamMultiplexerFactory http2StreamHandlerFactory,
            final HttpVersionPolicy versionPolicy) {
        this.ioSession = Args.notNull(ioSession, "I/O session");
        this.http1StreamHandlerFactory = Args.notNull(http1StreamHandlerFactory, "HTTP/1.1 stream handler factory");
        this.http2StreamHandlerFactory = Args.notNull(http2StreamHandlerFactory, "HTTP/2 stream handler factory");
        this.versionPolicy = versionPolicy != null ? versionPolicy : HttpVersionPolicy.NEGOTIATE;
        this.inBuf = BufferedData.allocate(1024);
        this.protocolHandlerRef = new AtomicReference<>(null);
    }

    @Override
    public void connected(final IOSession session) {
        try {
            final TlsDetails tlsDetails = ioSession.getTlsDetails();
            switch (versionPolicy) {
                case NEGOTIATE:
                    if (tlsDetails != null &&
                            ApplicationProtocol.HTTP_2.id.equals(tlsDetails.getApplicationProtocol())) {
                        expectValidH2Preface = true;
                    }
                    break;
                case FORCE_HTTP_2:
                    if (tlsDetails == null ||
                            !ApplicationProtocol.HTTP_1_1.id.equals(tlsDetails.getApplicationProtocol())) {
                        expectValidH2Preface = true;
                    }
                    break;
                case FORCE_HTTP_1:
                    final ServerHttp1StreamDuplexer http1StreamHandler = http1StreamHandlerFactory.create(
                            tlsDetails != null ? URIScheme.HTTPS.id : URIScheme.HTTP.id,
                            ioSession);
                    final HttpConnectionEventHandler protocolHandler = new ServerHttp1IOEventHandler(http1StreamHandler);
                    ioSession.upgrade(protocolHandler);
                    protocolHandlerRef.set(protocolHandler);
                    http1StreamHandler.onConnect();
                    break;
            }
        } catch (final Exception ex) {
            exception(session, ex);
        }
    }

    @Override
    public void inputReady(final IOSession session, final ByteBuffer src) {
        try {
            if (src != null) {
                inBuf.put(src);
            }
            boolean endOfStream = false;
            if (inBuf.length() < PREFACE.length) {
                final int bytesRead = inBuf.readFrom(session);
                if (bytesRead == -1) {
                    endOfStream = true;
                }
            }
            final ByteBuffer data = inBuf.data();
            if (data.remaining() >= PREFACE.length) {
                boolean validH2Preface = true;
                for (int i = 0; i < PREFACE.length; i++) {
                    if (data.get() != PREFACE[i]) {
                        if (expectValidH2Preface) {
                            throw new HttpException("Unexpected HTTP/2 preface");
                        }
                        validH2Preface = false;
                    }
                }
                if (validH2Preface) {
                    final ServerH2StreamMultiplexer http2StreamHandler = http2StreamHandlerFactory.create(ioSession);
                    final HttpConnectionEventHandler protocolHandler = new ServerH2IOEventHandler(http2StreamHandler);
                    ioSession.upgrade(protocolHandler);
                    protocolHandlerRef.set(protocolHandler);
                    http2StreamHandler.onConnect();
                    http2StreamHandler.onInput(data.hasRemaining() ? data : null);
                } else {
                    final TlsDetails tlsDetails = ioSession.getTlsDetails();
                    final ServerHttp1StreamDuplexer http1StreamHandler = http1StreamHandlerFactory.create(
                            tlsDetails != null ? URIScheme.HTTPS.id : URIScheme.HTTP.id,
                            ioSession);
                    final HttpConnectionEventHandler protocolHandler = new ServerHttp1IOEventHandler(http1StreamHandler);
                    ioSession.upgrade(protocolHandler);
                    protocolHandlerRef.set(protocolHandler);
                    data.rewind();
                    http1StreamHandler.onConnect();
                    http1StreamHandler.onInput(data);
                }
            } else {
                if (endOfStream) {
                    throw new ConnectionClosedException();
                }
            }
            data.clear();
        } catch (final Exception ex) {
            exception(session, ex);
        }
    }

    @Override
    public void outputReady(final IOSession session) {
    }

    @Override
    public void timeout(final IOSession session, final Timeout timeout) {
        exception(session, SocketTimeoutExceptionFactory.create(timeout));
    }

    @Override
    public void exception(final IOSession session, final Exception cause) {
        session.close(CloseMode.IMMEDIATE);
        final HttpConnectionEventHandler protocolHandler = protocolHandlerRef.get();
        if (protocolHandler != null) {
            protocolHandler.exception(session, cause);
        } else {
            CommandSupport.failCommands(session, cause);
        }
    }

    @Override
    public void disconnected(final IOSession session) {
        final HttpConnectionEventHandler protocolHandler = protocolHandlerRef.getAndSet(null);
        if (protocolHandler != null) {
            protocolHandler.disconnected(ioSession);
        } else {
            CommandSupport.cancelCommands(session);
        }
    }

    @Override
    public SSLSession getSSLSession() {
        final TlsDetails tlsDetails = ioSession.getTlsDetails();
        return tlsDetails != null ? tlsDetails.getSSLSession() : null;
    }

    @Override
    public EndpointDetails getEndpointDetails() {
        final HttpConnectionEventHandler protocolHandler = protocolHandlerRef.get();
        return protocolHandler != null ? protocolHandler.getEndpointDetails() : null;
    }

    @Override
    public void setSocketTimeout(final Timeout timeout) {
        ioSession.setSocketTimeout(timeout);
    }

    @Override
    public Timeout getSocketTimeout() {
        return ioSession.getSocketTimeout();
    }

    @Override
    public ProtocolVersion getProtocolVersion() {
        final HttpConnectionEventHandler protocolHandler = protocolHandlerRef.get();
        return protocolHandler != null ? protocolHandler.getProtocolVersion() : null;
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
        return ioSession.isOpen();
    }

    @Override
    public void close() throws IOException {
        ioSession.close();
    }

    @Override
    public void close(final CloseMode closeMode) {
        ioSession.close(closeMode);
    }

    @Override
    public String toString() {
        return "[" +
                "versionPolicy=" + versionPolicy +
                ", expectValidH2Preface=" + expectValidH2Preface +
                ']';
    }

}
