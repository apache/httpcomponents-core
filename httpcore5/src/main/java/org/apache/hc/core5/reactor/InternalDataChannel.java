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

package org.apache.hc.core5.reactor;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;

import javax.net.ssl.SSLContext;

import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.reactor.ssl.SSLBufferMode;
import org.apache.hc.core5.reactor.ssl.SSLIOSession;
import org.apache.hc.core5.reactor.ssl.SSLMode;
import org.apache.hc.core5.reactor.ssl.SSLSessionInitializer;
import org.apache.hc.core5.reactor.ssl.SSLSessionVerifier;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.hc.core5.util.Asserts;

final class InternalDataChannel extends InternalChannel implements ProtocolIOSession {

    private final IOSession ioSession;
    private final NamedEndpoint initialEndpoint;
    private final IOSessionListener sessionListener;
    private final AtomicReference<SSLIOSession> tlsSessionRef;
    private final Queue<InternalDataChannel> closedSessions;
    private final AtomicReference<IOEventHandler> handlerRef;
    private final AtomicBoolean connected;
    private final AtomicBoolean closed;

    InternalDataChannel(
            final IOSession ioSession,
            final NamedEndpoint initialEndpoint,
            final IOSessionListener sessionListener,
            final Queue<InternalDataChannel> closedSessions) {
        this.ioSession = ioSession;
        this.initialEndpoint = initialEndpoint;
        this.closedSessions = closedSessions;
        this.sessionListener = sessionListener;
        this.tlsSessionRef = new AtomicReference<>(null);
        this.handlerRef = new AtomicReference<>(null);
        this.connected = new AtomicBoolean(false);
        this.closed = new AtomicBoolean(false);
    }

    @Override
    public String getId() {
        return ioSession.getId();
    }


    @Override
    public IOEventHandler getHandler() {
        return handlerRef.get();
    }

    @Override
    public void upgrade(final IOEventHandler handler) {
        handlerRef.set(handler);
    }

    private IOEventHandler ensureHandler() {
        final IOEventHandler handler = handlerRef.get();
        Asserts.notNull(handler, "IO event handler");
        return handler;
    }

    @Override
    void onIOEvent(final int readyOps) throws IOException {
        final SSLIOSession tlsSession = tlsSessionRef.get();
        if (tlsSession != null) {
            if (!tlsSession.isInitialized()) {
                tlsSession.initialize();
                if (sessionListener != null) {
                    sessionListener.tlsStarted(tlsSession);
                }
            }
            if ((readyOps & SelectionKey.OP_CONNECT) != 0) {
                ioSession.clearEvent(SelectionKey.OP_CONNECT);
            }
            if ((readyOps & SelectionKey.OP_READ) != 0) {
                ioSession.updateReadTime();
                do {
                    tlsSession.resetReadCount();
                    if (tlsSession.isAppInputReady()) {
                        if (sessionListener != null) {
                            sessionListener.inputReady(this);
                        }
                        final IOEventHandler handler = ensureHandler();
                        handler.inputReady(this);
                    }
                    tlsSession.inboundTransport();
                    if (sessionListener != null) {
                        sessionListener.tlsInbound(tlsSession);
                    }
                } while (tlsSession.getReadCount() > 0);
            }
            if ((readyOps & SelectionKey.OP_WRITE) != 0) {
                ioSession.updateWriteTime();
                if (tlsSession.isAppOutputReady()) {
                    if (sessionListener != null) {
                        sessionListener.outputReady(this);
                    }
                    final IOEventHandler handler = ensureHandler();
                    handler.outputReady(this);
                }
                tlsSession.outboundTransport();
                if (sessionListener != null) {
                    sessionListener.tlsOutbound(tlsSession);
                }
            }
        } else {
            if ((readyOps & SelectionKey.OP_CONNECT) != 0) {
                ioSession.clearEvent(SelectionKey.OP_CONNECT);
                if (connected.compareAndSet(false, true)) {
                    if (sessionListener != null) {
                        sessionListener.connected(this);
                    }
                    final IOEventHandler handler = ensureHandler();
                    handler.connected(this);
                }
            }
            if ((readyOps & SelectionKey.OP_READ) != 0) {
                ioSession.updateReadTime();
                if (sessionListener != null) {
                    sessionListener.inputReady(this);
                }
                final IOEventHandler handler = ensureHandler();
                handler.inputReady(this);
            }
            if ((readyOps & SelectionKey.OP_WRITE) != 0) {
                ioSession.updateWriteTime();
                if (sessionListener != null) {
                    sessionListener.outputReady(this);
                }
                final IOEventHandler handler = ensureHandler();
                handler.outputReady(this);
            }
        }
    }

    @Override
    int getTimeoutMillis() {
        return ioSession.getSocketTimeoutMillis();
    }

    @Override
    void onTimeout(final int timeoutMillis) throws IOException {
        final IOEventHandler handler = ensureHandler();
        handler.timeout(this, timeoutMillis);
        final SSLIOSession tlsSession = tlsSessionRef.get();
        if (tlsSession != null) {
            if (tlsSession.isOutboundDone() && !tlsSession.isInboundDone()) {
                // The session failed to terminate cleanly
                tlsSession.close(CloseMode.IMMEDIATE);
            }
        }
    }

    @Override
    void onException(final Exception cause) {
        final IOEventHandler handler = ensureHandler();
        if (sessionListener != null) {
            sessionListener.exception(this, cause);
        }
        handler.exception(this, cause);
    }

    void disconnected() {
        if (sessionListener != null) {
            sessionListener.disconnected(this);
        }
        final IOEventHandler handler = ensureHandler();
        handler.disconnected(this);
    }

    @Override
    public void startTls(
            final SSLContext sslContext,
            final NamedEndpoint endpoint,
            final SSLBufferMode sslBufferMode,
            final SSLSessionInitializer initializer,
            final SSLSessionVerifier verifier) {
        if (!tlsSessionRef.compareAndSet(null, new SSLIOSession(
                endpoint != null ? endpoint : initialEndpoint,
                ioSession,
                initialEndpoint != null ? SSLMode.CLIENT : SSLMode.SERVER,
                sslContext,
                sslBufferMode,
                initializer,
                verifier,
                new Callback<SSLIOSession>() {

                    @Override
                    public void execute(final SSLIOSession sslSession) {
                        if (connected.compareAndSet(false, true)) {
                            final IOEventHandler handler = ensureHandler();
                            try {
                                if (sessionListener != null) {
                                    sessionListener.connected(InternalDataChannel.this);
                                }
                                handler.connected(InternalDataChannel.this);
                            } catch (final Exception ex) {
                                if (sessionListener != null) {
                                    sessionListener.exception(InternalDataChannel.this, ex);
                                }
                                handler.exception(InternalDataChannel.this, ex);
                            }
                        }
                    }

                }))) {
            throw new IllegalStateException("TLS already activated");
        }
    }

    @Override
    public TlsDetails getTlsDetails() {
        final SSLIOSession sslIoSession = tlsSessionRef.get();
        return sslIoSession != null ? sslIoSession.getTlsDetails() : null;
    }

    @Override
    public Lock lock() {
        return ioSession.lock();
    }

    private IOSession getSessionImpl() {
        final SSLIOSession tlsSession = tlsSessionRef.get();
        return tlsSession != null ? tlsSession : ioSession;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                getSessionImpl().close();
            } finally {
                closedSessions.add(this);
            }
        }
    }

    @Override
    public void close(final CloseMode closeMode) {
        if (closed.compareAndSet(false, true)) {
            try {
                getSessionImpl().close(closeMode);
            } finally {
                closedSessions.add(this);
            }
        }
    }

    @Override
    public int getStatus() {
        return getSessionImpl().getStatus();
    }

    @Override
    public boolean isClosed() {
        return getSessionImpl().isClosed();
    }

    @Override
    public void enqueue(final Command command, final Command.Priority priority) {
        getSessionImpl().enqueue(command, priority);
    }

    @Override
    public boolean hasCommands() {
        return getSessionImpl().hasCommands();
    }

    @Override
    public Command poll() {
        return getSessionImpl().poll();
    }

    @Override
    public ByteChannel channel() {
        return getSessionImpl().channel();
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
    public int getEventMask() {
        return getSessionImpl().getEventMask();
    }

    @Override
    public void setEventMask(final int ops) {
        getSessionImpl().setEventMask(ops);
    }

    @Override
    public void setEvent(final int op) {
        getSessionImpl().setEvent(op);
    }

    @Override
    public void clearEvent(final int op) {
        getSessionImpl().clearEvent(op);
    }

    @Override
    public int getSocketTimeoutMillis() {
        return ioSession.getSocketTimeoutMillis();
    }

    @Override
    public void setSocketTimeoutMillis(final int timeout) {
        ioSession.setSocketTimeoutMillis(timeout);
    }

    @Override
    public void updateReadTime() {
        ioSession.updateReadTime();
    }

    @Override
    public void updateWriteTime() {
        ioSession.updateWriteTime();
    }

    @Override
    public long getLastReadTimeMillis() {
        return ioSession.getLastReadTimeMillis();
    }

    @Override
    public long getLastWriteTime() {
        return ioSession.getLastWriteTime();
    }

    @Override
    public String toString() {
        return getSessionImpl().toString();
    }

}
