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

import javax.net.ssl.SSLSession;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.impl.ConnectionListener;
import org.apache.hc.core5.http.impl.nio.HttpConnectionEventHandler;
import org.apache.hc.core5.http.impl.nio.ServerHttp1IOEventHandler;
import org.apache.hc.core5.http.impl.nio.ServerHttp1StreamDuplexer;
import org.apache.hc.core5.http.impl.nio.ServerHttp1StreamDuplexerFactory;
import org.apache.hc.core5.http2.H2ConnectionException;
import org.apache.hc.core5.http2.H2Error;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.ssl.ApplicationProtocols;
import org.apache.hc.core5.io.ShutdownType;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.TlsCapableIOSession;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.hc.core5.util.Args;

/**
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE_CONDITIONAL)
public class ServerHttpProtocolNegotiator implements HttpConnectionEventHandler {

    final static byte[] PREFACE = ClientHttpProtocolNegotiator.PREFACE;

    private final TlsCapableIOSession ioSession;
    private final ServerHttp1StreamDuplexerFactory http1StreamHandlerFactory;
    private final ServerHttp2StreamMultiplexerFactory http2StreamHandlerFactory;
    private final HttpVersionPolicy versionPolicy;
    private final ConnectionListener connectionListener;
    private final ByteBuffer bytebuf;

    private volatile boolean expectValidH2Preface;

    public ServerHttpProtocolNegotiator(
            final TlsCapableIOSession ioSession,
            final ServerHttp1StreamDuplexerFactory http1StreamHandlerFactory,
            final ServerHttp2StreamMultiplexerFactory http2StreamHandlerFactory,
            final HttpVersionPolicy versionPolicy,
            final ConnectionListener connectionListener) {
        this.ioSession = Args.notNull(ioSession, "I/O session");
        this.http1StreamHandlerFactory = Args.notNull(http1StreamHandlerFactory, "HTTP/1.1 stream handler factory");
        this.http2StreamHandlerFactory = Args.notNull(http2StreamHandlerFactory, "HTTP/2 stream handler factory");
        this.versionPolicy = versionPolicy != null ? versionPolicy : HttpVersionPolicy.NEGOTIATE;
        this.connectionListener = connectionListener;
        this.bytebuf = ByteBuffer.allocate(1024);
    }

    @Override
    public void connected(final IOSession session) {
        try {
            switch (versionPolicy) {
                case NEGOTIATE:
                    final TlsDetails tlsDetails = ioSession.getTlsDetails();
                    if (tlsDetails != null) {
                        if (ApplicationProtocols.HTTP_2.id.equals(tlsDetails.getApplicationProtocol())) {
                            expectValidH2Preface = true;
                            // Proceed with the H2 preface
                            break;
                        }
                    }
                    break;
                case FORCE_HTTP_1:
                    final ServerHttp1StreamDuplexer http1StreamHandler = http1StreamHandlerFactory.create(ioSession);
                    session.setHandler(new ServerHttp1IOEventHandler(http1StreamHandler));
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
            if (bytebuf.position() < PREFACE.length) {
                session.channel().read(bytebuf);
            }
            if (bytebuf.position() >= PREFACE.length) {
                bytebuf.flip();

                boolean validH2Preface = true;
                for (int i = 0; i < PREFACE.length; i++) {
                    if (bytebuf.get() != PREFACE[i]) {
                        if (expectValidH2Preface) {
                            throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "Unexpected HTTP/2 preface");
                        } else {
                            validH2Preface = false;
                        }
                    }
                }
                if (validH2Preface) {
                    final ServerHttp2StreamMultiplexer http2StreamHandler = http2StreamHandlerFactory.create(ioSession);
                    session.setHandler(new ServerHttp2IOEventHandler(http2StreamHandler));
                    http2StreamHandler.onConnect(bytebuf.hasRemaining() ? bytebuf : null);
                    http2StreamHandler.onInput();
                } else {
                    final ServerHttp1StreamDuplexer http1StreamHandler = http1StreamHandlerFactory.create(ioSession);
                    session.setHandler(new ServerHttp1IOEventHandler(http1StreamHandler));
                    bytebuf.rewind();
                    http1StreamHandler.onConnect(bytebuf);
                    http1StreamHandler.onInput();
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
    public void timeout(final IOSession session) {
        exception(session, new SocketTimeoutException());
    }

    @Override
    public void exception(final IOSession session, final Exception cause) {
        session.shutdown(ShutdownType.IMMEDIATE);
        if (connectionListener != null) {
            connectionListener.onError(this, cause);
        }
    }

    @Override
    public void disconnected(final IOSession session) {
        if (connectionListener != null) {
            connectionListener.onDisconnect(this);
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
    public void shutdown(final ShutdownType shutdownType) {
        ioSession.shutdown(shutdownType);
    }

}
