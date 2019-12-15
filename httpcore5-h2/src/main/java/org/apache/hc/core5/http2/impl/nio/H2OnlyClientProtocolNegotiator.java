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
import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.impl.nio.HttpConnectionEventHandler;
import org.apache.hc.core5.http.nio.command.CommandSupport;
import org.apache.hc.core5.http2.ssl.ApplicationProtocol;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.io.SocketTimeoutExceptionFactory;
import org.apache.hc.core5.reactor.IOEventHandler;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TextUtils;
import org.apache.hc.core5.util.Timeout;

/**
 * I/O event handler for events fired by {@link ProtocolIOSession} that implements
 * client side of the HTTP/2 protocol negotiation handshake always forcing the choice
 * of HTTP/2.
 *
 * @since 5.0
 */
@Internal
public class H2OnlyClientProtocolNegotiator implements HttpConnectionEventHandler {

    private final ProtocolIOSession ioSession;
    private final ClientH2StreamMultiplexerFactory http2StreamHandlerFactory;
    private final boolean strictALPNHandshake;

    private final ByteBuffer preface;

    public H2OnlyClientProtocolNegotiator(
            final ProtocolIOSession ioSession,
            final ClientH2StreamMultiplexerFactory http2StreamHandlerFactory,
            final boolean strictALPNHandshake) {
        this.ioSession = Args.notNull(ioSession, "I/O session");
        this.http2StreamHandlerFactory = Args.notNull(http2StreamHandlerFactory, "HTTP/2 stream handler factory");
        this.strictALPNHandshake = strictALPNHandshake;
        this.preface = ByteBuffer.wrap(ClientHttpProtocolNegotiator.PREFACE);
    }

    @Override
    public void connected(final IOSession session) {
        try {
            final TlsDetails tlsDetails = ioSession.getTlsDetails();
            if (tlsDetails != null) {
                final String applicationProtocol = tlsDetails.getApplicationProtocol();
                if (TextUtils.isEmpty(applicationProtocol)) {
                    if (strictALPNHandshake) {
                        throw new HttpException("ALPN: missing application protocol");
                    }
                } else {
                    if (!ApplicationProtocol.HTTP_2.id.equals(applicationProtocol)) {
                        throw new HttpException("ALPN: unexpected application protocol '" + applicationProtocol + "'");
                    }
                }
            }
            writePreface(session);
        } catch (final Exception ex) {
            session.close(CloseMode.IMMEDIATE);
            exception(session, ex);
        }
    }

    private void writePreface(final IOSession session) throws IOException  {
        if (preface.hasRemaining()) {
            final ByteChannel channel = session;
            channel.write(preface);
        }
        if (!preface.hasRemaining()) {
            final ClientH2StreamMultiplexer streamMultiplexer = http2StreamHandlerFactory.create(ioSession);
            final IOEventHandler newHandler = new ClientH2IOEventHandler(streamMultiplexer);
            newHandler.connected(session);
            ioSession.upgrade(newHandler);
        }
    }

    @Override
    public void inputReady(final IOSession session, final ByteBuffer src) {
        outputReady(session);
    }

    @Override
    public void outputReady(final IOSession session) {
        try {
            if (preface != null) {
                writePreface(session);
            } else {
                session.close(CloseMode.GRACEFUL);
            }
        } catch (final IOException ex) {
            session.close(CloseMode.IMMEDIATE);
            exception(session, ex);
        }
    }

    @Override
    public void timeout(final IOSession session, final Timeout timeout) {
        exception(session, SocketTimeoutExceptionFactory.create(timeout));
    }

    @Override
    public void exception(final IOSession session, final Exception cause) {
        try {
            CommandSupport.failCommands(session, cause);
        } finally {
            session.close(CloseMode.IMMEDIATE);
        }
    }

    @Override
    public void disconnected(final IOSession session) {
        CommandSupport.cancelCommands(session);
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
    public void setSocketTimeout(final Timeout timeout) {
        ioSession.setSocketTimeout(timeout);
    }

    @Override
    public Timeout getSocketTimeout() {
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

}
