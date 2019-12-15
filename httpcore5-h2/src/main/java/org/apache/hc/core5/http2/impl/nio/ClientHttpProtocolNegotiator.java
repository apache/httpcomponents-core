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
import java.nio.channels.SelectionKey;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLSession;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.impl.nio.BufferedData;
import org.apache.hc.core5.http.impl.nio.ClientHttp1IOEventHandler;
import org.apache.hc.core5.http.impl.nio.ClientHttp1StreamDuplexer;
import org.apache.hc.core5.http.impl.nio.ClientHttp1StreamDuplexerFactory;
import org.apache.hc.core5.http.impl.nio.HttpConnectionEventHandler;
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
    private final ClientH2StreamMultiplexerFactory http2StreamHandlerFactory;
    private final HttpVersionPolicy versionPolicy;
    private final AtomicReference<HttpConnectionEventHandler> protocolHandlerRef;

    private volatile ByteBuffer preface;
    private volatile BufferedData inBuf;

    public ClientHttpProtocolNegotiator(
            final ProtocolIOSession ioSession,
            final ClientHttp1StreamDuplexerFactory http1StreamHandlerFactory,
            final ClientH2StreamMultiplexerFactory http2StreamHandlerFactory,
            final HttpVersionPolicy versionPolicy) {
        this.ioSession = Args.notNull(ioSession, "I/O session");
        this.http1StreamHandlerFactory = Args.notNull(http1StreamHandlerFactory, "HTTP/1.1 stream handler factory");
        this.http2StreamHandlerFactory = Args.notNull(http2StreamHandlerFactory, "HTTP/2 stream handler factory");
        this.versionPolicy = versionPolicy != null ? versionPolicy : HttpVersionPolicy.NEGOTIATE;
        this.protocolHandlerRef = new AtomicReference<>(null);
    }

    private void startHttp1(final IOSession session) {
        final ClientHttp1StreamDuplexer http1StreamHandler = http1StreamHandlerFactory.create(ioSession);
        final HttpConnectionEventHandler protocolHandler = new ClientHttp1IOEventHandler(http1StreamHandler);
        try {
            ioSession.upgrade(protocolHandler);
            protocolHandlerRef.set(protocolHandler);
            protocolHandler.connected(session);
            if (inBuf != null) {
                protocolHandler.inputReady(session, inBuf.data());
                inBuf.clear();
            }
        } catch (final Exception ex) {
            protocolHandler.exception(session, ex);
            session.close(CloseMode.IMMEDIATE);
        }
    }

    private void startHttp2(final IOSession session) {
        final ClientH2StreamMultiplexer streamMultiplexer = http2StreamHandlerFactory.create(ioSession);
        final HttpConnectionEventHandler protocolHandler = new ClientH2IOEventHandler(streamMultiplexer);
        try {
            ioSession.upgrade(protocolHandler);
            protocolHandlerRef.set(protocolHandler);
            protocolHandler.connected(session);
            if (inBuf != null) {
                protocolHandler.inputReady(session, inBuf.data());
                inBuf.clear();
            }
        } catch (final Exception ex) {
            protocolHandler.exception(session, ex);
            session.close(CloseMode.IMMEDIATE);
        }
    }

    private void writeOutPreface(final IOSession session) throws IOException {
        if (preface.hasRemaining()) {
            final ByteChannel channel = session;
            channel.write(preface);
        }
        if (!preface.hasRemaining()) {
            session.clearEvent(SelectionKey.OP_WRITE);
            startHttp2(session);
        } else {
            session.setEvent(SelectionKey.OP_WRITE);
        }
    }

    @Override
    public void connected(final IOSession session) throws IOException {
        switch (versionPolicy) {
            case NEGOTIATE:
                final TlsDetails tlsDetails = ioSession.getTlsDetails();
                if (tlsDetails != null) {
                    if (ApplicationProtocol.HTTP_2.id.equals(tlsDetails.getApplicationProtocol())) {
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
            writeOutPreface(session);
        }
    }

    @Override
    public void inputReady(final IOSession session, final ByteBuffer src) throws IOException  {
        if (src != null) {
            if (inBuf == null) {
                inBuf = BufferedData.allocate(src.remaining());
            }
            inBuf.put(src);
        }
        outputReady(session);
    }

    @Override
    public void outputReady(final IOSession session) throws IOException {
        if (preface != null) {
            writeOutPreface(session);
        } else {
            session.close(CloseMode.GRACEFUL);
        }
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
                ']';
    }

}
