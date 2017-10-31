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

import javax.net.ssl.SSLSession;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.impl.nio.HttpConnectionEventHandler;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.command.ExecutionCommand;
import org.apache.hc.core5.http2.H2ConnectionException;
import org.apache.hc.core5.http2.H2Error;
import org.apache.hc.core5.http2.ssl.ApplicationProtocols;
import org.apache.hc.core5.io.ShutdownType;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.IOEventHandler;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.TlsCapableIOSession;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.hc.core5.util.Args;

/**
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE_CONDITIONAL)
public class Http2OnlyClientProtocolNegotiator implements HttpConnectionEventHandler {

    private final TlsCapableIOSession ioSession;
    private final ClientHttp2StreamMultiplexerFactory http2StreamHandlerFactory;

    private final ByteBuffer preface;

    public Http2OnlyClientProtocolNegotiator(
            final TlsCapableIOSession ioSession,
            final ClientHttp2StreamMultiplexerFactory http2StreamHandlerFactory) {
        this.ioSession = Args.notNull(ioSession, "I/O session");
        this.http2StreamHandlerFactory = Args.notNull(http2StreamHandlerFactory, "HTTP/2 stream handler factory");
        this.preface = ByteBuffer.wrap(ClientHttpProtocolNegotiator.PREFACE);
    }

    @Override
    public void connected(final IOSession session) {
        try {
            final TlsDetails tlsDetails = ioSession.getTlsDetails();
            if (tlsDetails != null) {
                final String applicationProtocol = tlsDetails.getApplicationProtocol();
                if (applicationProtocol != null) {
                    if (!ApplicationProtocols.HTTP_2.id.equals(applicationProtocol)) {
                        throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "Unexpected application protocol: " +
                                applicationProtocol);
                    }
                }
            }
            writePreface(session);
        } catch (final Exception ex) {
            session.shutdown(ShutdownType.IMMEDIATE);
            exception(session, ex);
        }
    }

    private void writePreface(final IOSession session) throws IOException  {
        if (preface.hasRemaining()) {
            final ByteChannel channel = session.channel();
            channel.write(preface);
        }
        if (!preface.hasRemaining()) {
            final ClientHttp2StreamMultiplexer streamMultiplexer = http2StreamHandlerFactory.create(ioSession);
            final IOEventHandler newHandler = new ClientHttp2IOEventHandler(streamMultiplexer);
            newHandler.connected(session);
            session.setHandler(newHandler);
        }
    }

    @Override
    public void inputReady(final IOSession session) {
        outputReady(session);
    }

    @Override
    public void outputReady(final IOSession session) {
        try {
            if (preface != null) {
                writePreface(session);
            } else {
                session.shutdown(ShutdownType.IMMEDIATE);
            }
        } catch (final IOException ex) {
            session.shutdown(ShutdownType.IMMEDIATE);
            exception(session, ex);
        }
    }

    @Override
    public void timeout(final IOSession session) {
        exception(session, new SocketTimeoutException());
    }

    @Override
    public void exception(final IOSession session, final Exception cause) {
        try {
            for (;;) {
                final Command command = ioSession.getCommand();
                if (command != null) {
                    if (command instanceof ExecutionCommand) {
                        final ExecutionCommand executionCommand = (ExecutionCommand) command;
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
            session.shutdown(ShutdownType.IMMEDIATE);
        }
    }

    @Override
    public void disconnected(final IOSession session) {
        for (;;) {
            final Command command = ioSession.getCommand();
            if (command != null) {
                if (command instanceof ExecutionCommand) {
                    final ExecutionCommand executionCommand = (ExecutionCommand) command;
                    final AsyncClientExchangeHandler exchangeHandler = executionCommand.getExchangeHandler();
                    exchangeHandler.failed(new ConnectionClosedException("Connection closed"));
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
