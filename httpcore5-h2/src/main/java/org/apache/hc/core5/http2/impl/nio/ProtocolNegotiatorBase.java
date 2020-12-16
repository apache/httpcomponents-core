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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLSession;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.impl.nio.HttpConnectionEventHandler;
import org.apache.hc.core5.http.nio.command.CommandSupport;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.io.SocketTimeoutExceptionFactory;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Timeout;

abstract class ProtocolNegotiatorBase implements HttpConnectionEventHandler {

    final ProtocolIOSession ioSession;
    private final AtomicReference<HttpConnectionEventHandler> protocolHandlerRef;
    private final FutureCallback<ProtocolIOSession> resultCallback;
    private final AtomicBoolean completed;

    ProtocolNegotiatorBase(
            final ProtocolIOSession ioSession,
            final FutureCallback<ProtocolIOSession> resultCallback) {
        this.ioSession = Args.notNull(ioSession, "I/O session");
        this.protocolHandlerRef = new AtomicReference<>(null);
        this.resultCallback = resultCallback;
        this.completed = new AtomicBoolean();
    }

    void startProtocol(final HttpConnectionEventHandler protocolHandler, final ByteBuffer data) throws IOException {
        protocolHandlerRef.set(protocolHandler);
        ioSession.upgrade(protocolHandler);
        protocolHandler.connected(ioSession);
        if (data != null && data.hasRemaining()) {
            protocolHandler.inputReady(ioSession, data);
        }
        if (completed.compareAndSet(false, true) && resultCallback != null) {
            resultCallback.completed(ioSession);
        }
    }

    @Override
    public void timeout(final IOSession session, final Timeout timeout) {
        exception(session, SocketTimeoutExceptionFactory.create(timeout));
    }

    @Override
    public void exception(final IOSession session, final Exception cause) {
        final HttpConnectionEventHandler protocolHandler = protocolHandlerRef.get();
        try {
            session.close(CloseMode.IMMEDIATE);
            if (protocolHandler != null) {
                protocolHandler.exception(session, cause);
            } else {
                CommandSupport.failCommands(session, cause);
            }
        } catch (final Exception ex) {
            if (completed.compareAndSet(false, true) && resultCallback != null) {
                resultCallback.failed(ex);
            }
        }
    }

    @Override
    public void disconnected(final IOSession session) {
        final HttpConnectionEventHandler protocolHandler = protocolHandlerRef.getAndSet(null);
        try {
            if (protocolHandler != null) {
                protocolHandler.disconnected(ioSession);
            } else {
                CommandSupport.cancelCommands(session);
            }
        } finally {
            if (completed.compareAndSet(false, true) && resultCallback != null) {
                resultCallback.failed(new ConnectionClosedException());
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
