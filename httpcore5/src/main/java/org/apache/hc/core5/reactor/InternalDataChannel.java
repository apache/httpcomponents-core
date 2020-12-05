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
import java.nio.ByteBuffer;
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
import org.apache.hc.core5.util.Timeout;

final class InternalDataChannel extends InternalChannel implements ProtocolIOSession {

    private final IOSession ioSession;
    private final NamedEndpoint initialEndpoint;
    private final IOSessionListener sessionListener;
    private final AtomicReference<SSLIOSession> tlsSessionRef;
    private final Queue<InternalDataChannel> closedSessions;
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
        this.closed = new AtomicBoolean(false);
    }

    @Override
    public String getId() {
        return ioSession.getId();
    }

    @Override
    public NamedEndpoint getInitialEndpoint() {
        return initialEndpoint;
    }

    @Override
    public IOEventHandler getHandler() {
        return ioSession.getHandler();
    }

    @Override
    public void upgrade(final IOEventHandler handler) {
        ioSession.upgrade(handler);
    }

    private IOSession getSessionImpl() {
        final SSLIOSession tlsSession = tlsSessionRef.get();
        return tlsSession != null ? tlsSession : ioSession;
    }

    private IOEventHandler ensureHandler(final IOSession session) {
        final IOEventHandler handler = session.getHandler();
        Asserts.notNull(handler, "IO event handler");
        return handler;
    }

    @Override
    void onIOEvent(final int readyOps) throws IOException {
        final SSLIOSession tlsSession = tlsSessionRef.get();
        final IOSession currentSession = tlsSession != null ? tlsSession : ioSession;
        if ((readyOps & SelectionKey.OP_CONNECT) != 0) {
            currentSession.clearEvent(SelectionKey.OP_CONNECT);
            if (tlsSession == null) {
                if (sessionListener != null) {
                    sessionListener.connected(this);
                }
                final IOEventHandler handler = ensureHandler(currentSession);
                handler.connected(this);
            }
        }
        if ((readyOps & SelectionKey.OP_READ) != 0) {
            currentSession.updateReadTime();
            if (sessionListener != null) {
                sessionListener.inputReady(this);
            }
            final IOEventHandler handler = ensureHandler(currentSession);
            handler.inputReady(this, null);
        }
        if ((readyOps & SelectionKey.OP_WRITE) != 0
                || (ioSession.getEventMask() & SelectionKey.OP_WRITE) != 0) {
            currentSession.updateWriteTime();
            if (sessionListener != null) {
                sessionListener.outputReady(this);
            }
            final IOEventHandler handler = ensureHandler(currentSession);
            handler.outputReady(this);
        }
    }

    @Override
    Timeout getTimeout() {
        return ioSession.getSocketTimeout();
    }

    @Override
    void onTimeout(final Timeout timeout) throws IOException {
        if (sessionListener != null) {
            sessionListener.timeout(this);
        }
        final IOSession currentSession = getSessionImpl();
        final IOEventHandler handler = ensureHandler(currentSession);
        handler.timeout(this, timeout);
    }

    @Override
    void onException(final Exception cause) {
        if (sessionListener != null) {
            sessionListener.exception(this, cause);
        }
        final IOSession currentSession = getSessionImpl();
        final IOEventHandler handler = currentSession.getHandler();
        if (handler != null) {
            handler.exception(this, cause);
        }
    }

    void onTLSSessionStart(final SSLIOSession sslSession) {
        if (sessionListener != null) {
            sessionListener.connected(this);
        }
    }

    void onTLSSessionEnd() {
        if (closed.compareAndSet(false, true)) {
            closedSessions.add(this);
        }
    }

    void disconnected() {
        if (sessionListener != null) {
            sessionListener.disconnected(this);
        }
        final SSLIOSession tlsSession = tlsSessionRef.get();
        final IOSession currentSession = tlsSession != null ? tlsSession : ioSession;
        final IOEventHandler handler = currentSession.getHandler();
        if (handler != null) {
            handler.disconnected(this);
        }
    }

    @Override
    public void startTls(
            final SSLContext sslContext,
            final NamedEndpoint endpoint,
            final SSLBufferMode sslBufferMode,
            final SSLSessionInitializer initializer,
            final SSLSessionVerifier verifier,
            final Timeout handshakeTimeout) {
        if (tlsSessionRef.compareAndSet(null, new SSLIOSession(
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
                        onTLSSessionStart(sslSession);
                    }

                },
                new Callback<SSLIOSession>() {

                    @Override
                    public void execute(final SSLIOSession sslSession) {
                        onTLSSessionEnd();
                    }

                },
                handshakeTimeout))) {
            if (sessionListener != null) {
                sessionListener.startTls(this);
            }
        } else {
            throw new IllegalStateException("TLS already activated");
        }
    }

    @SuppressWarnings("resource")
    @Override
    public TlsDetails getTlsDetails() {
        final SSLIOSession sslIoSession = tlsSessionRef.get();
        return sslIoSession != null ? sslIoSession.getTlsDetails() : null;
    }

    @Override
    public Lock getLock() {
        return ioSession.getLock();
    }

    @Override
    public void close() {
        close(CloseMode.GRACEFUL);
    }

    @Override
    public void close(final CloseMode closeMode) {
        if (closeMode == CloseMode.IMMEDIATE) {
            closed.set(true);
            getSessionImpl().close(closeMode);
        } else {
            if (closed.compareAndSet(false, true)) {
                try {
                    getSessionImpl().close(closeMode);
                } finally {
                    closedSessions.add(this);
                }
            }
        }
    }

    @Override
    public IOSession.Status getStatus() {
        return getSessionImpl().getStatus();
    }

    @Override
    public boolean isOpen() {
        return getSessionImpl().isOpen();
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
    public Timeout getSocketTimeout() {
        return ioSession.getSocketTimeout();
    }

    @Override
    public void setSocketTimeout(final Timeout timeout) {
        ioSession.setSocketTimeout(timeout);
    }

    @Override
    public int read(final ByteBuffer dst) throws IOException {
        return getSessionImpl().read(dst);
    }

    @Override
    public int write(final ByteBuffer src) throws IOException {
        return getSessionImpl().write(src);
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
    public long getLastReadTime() {
        return ioSession.getLastReadTime();
    }

    @Override
    public long getLastWriteTime() {
        return ioSession.getLastWriteTime();
    }

    @Override
    public long getLastEventTime() {
        return ioSession.getLastEventTime();
    }

    @Override
    public String toString() {
        final SSLIOSession tlsSession = tlsSessionRef.get();
        if (tlsSession != null) {
            return tlsSession.toString();
        } else {
            return ioSession.toString();
        }
    }

}
