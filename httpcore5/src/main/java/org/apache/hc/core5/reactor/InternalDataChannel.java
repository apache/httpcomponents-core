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
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;

import javax.net.ssl.SSLContext;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Decorator;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.reactor.ssl.SSLBufferMode;
import org.apache.hc.core5.reactor.ssl.SSLIOSession;
import org.apache.hc.core5.reactor.ssl.SSLMode;
import org.apache.hc.core5.reactor.ssl.SSLSessionInitializer;
import org.apache.hc.core5.reactor.ssl.SSLSessionVerifier;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.hc.core5.reactor.ssl.TransportSecurityLayer;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Asserts;
import org.apache.hc.core5.util.Timeout;

final class InternalDataChannel extends InternalChannel implements ProtocolIOSession {

    private final IOSession ioSession;
    private final NamedEndpoint initialEndpoint;
    private final Decorator<IOSession> ioSessionDecorator;
    private final IOSessionListener sessionListener;
    private final Queue<InternalDataChannel> closedSessions;
    private final AtomicReference<SSLIOSession> tlsSessionRef;
    private final AtomicReference<IOSession> currentSessionRef;
    private final AtomicReference<FutureCallback<TransportSecurityLayer>> tlsHandshakeCallbackRef;
    private final ConcurrentMap<String, ProtocolUpgradeHandler> protocolUpgradeHandlerMap;
    private final AtomicBoolean closed;

    InternalDataChannel(
            final IOSession ioSession,
            final NamedEndpoint initialEndpoint,
            final Decorator<IOSession> ioSessionDecorator,
            final IOSessionListener sessionListener,
            final Queue<InternalDataChannel> closedSessions) {
        this.ioSession = ioSession;
        this.initialEndpoint = initialEndpoint;
        this.closedSessions = closedSessions;
        this.ioSessionDecorator = ioSessionDecorator;
        this.sessionListener = sessionListener;
        this.tlsSessionRef = new AtomicReference<>(null);
        this.currentSessionRef = new AtomicReference<>(
                ioSessionDecorator != null ? ioSessionDecorator.decorate(ioSession) : ioSession);
        this.tlsHandshakeCallbackRef = new AtomicReference<>(null);
        this.protocolUpgradeHandlerMap = new ConcurrentHashMap<>();
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
        final IOSession currentSession = currentSessionRef.get();
        return currentSession.getHandler();
    }

    @Override
    public void upgrade(final IOEventHandler handler) {
        final IOSession currentSession = currentSessionRef.get();
        currentSession.upgrade(handler);
    }

    private IOEventHandler ensureHandler(final IOSession session) {
        final IOEventHandler handler = session.getHandler();
        Asserts.notNull(handler, "IO event handler");
        return handler;
    }

    @Override
    void onIOEvent(final int readyOps) throws IOException {
        if ((readyOps & SelectionKey.OP_CONNECT) != 0) {
            final IOSession currentSession = currentSessionRef.get();
            currentSession.clearEvent(SelectionKey.OP_CONNECT);
            if (tlsSessionRef.get() == null) {
                if (sessionListener != null) {
                    sessionListener.connected(currentSession);
                }
                final IOEventHandler handler = ensureHandler(currentSession);
                handler.connected(currentSession);
            }
        }
        if ((readyOps & SelectionKey.OP_READ) != 0) {
            final IOSession currentSession = currentSessionRef.get();
            currentSession.updateReadTime();
            if (sessionListener != null) {
                sessionListener.inputReady(currentSession);
            }
            final IOEventHandler handler = ensureHandler(currentSession);
            handler.inputReady(currentSession, null);
        }
        if ((readyOps & SelectionKey.OP_WRITE) != 0
                || (ioSession.getEventMask() & SelectionKey.OP_WRITE) != 0) {
            final IOSession currentSession = currentSessionRef.get();
            currentSession.updateWriteTime();
            if (sessionListener != null) {
                sessionListener.outputReady(currentSession);
            }
            final IOEventHandler handler = ensureHandler(currentSession);
            handler.outputReady(currentSession);
        }
    }

    @Override
    Timeout getTimeout() {
        final IOSession currentSession = currentSessionRef.get();
        return currentSession.getSocketTimeout();
    }

    @Override
    void onTimeout(final Timeout timeout) throws IOException {
        final IOSession currentSession = currentSessionRef.get();
        if (sessionListener != null) {
            sessionListener.timeout(currentSession);
        }
        final IOEventHandler handler = ensureHandler(currentSession);
        handler.timeout(currentSession, timeout);
    }

    @Override
    void onException(final Exception cause) {
        final IOSession currentSession = currentSessionRef.get();
        if (sessionListener != null) {
            sessionListener.exception(currentSession, cause);
        }
        final IOEventHandler handler = currentSession.getHandler();
        if (handler != null) {
            handler.exception(currentSession, cause);
        }
        final FutureCallback<?> callback = tlsHandshakeCallbackRef.getAndSet(null);
        if (callback != null) {
            callback.failed(cause);
        }
    }

    void onTLSSessionStart(final SSLIOSession sslSession) {
        final IOSession currentSession = currentSessionRef.get();
        if (sessionListener != null) {
            sessionListener.connected(currentSession);
        }
        final FutureCallback<TransportSecurityLayer> callback = tlsHandshakeCallbackRef.getAndSet(null);
        if (callback != null) {
            callback.completed(this);
        }
    }

    void onTLSSessionEnd(final SSLIOSession sslSession) {
        if (closed.compareAndSet(false, true)) {
            closedSessions.add(this);
        }
    }

    void disconnected() {
        final IOSession currentSession = currentSessionRef.get();
        if (sessionListener != null) {
            sessionListener.disconnected(currentSession);
        }
        final IOEventHandler handler = currentSession.getHandler();
        if (handler != null) {
            handler.disconnected(currentSession);
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
        startTls(sslContext, endpoint, sslBufferMode, initializer, verifier, handshakeTimeout, null);
    }

    @Override
    public void startTls(
            final SSLContext sslContext,
            final NamedEndpoint endpoint,
            final SSLBufferMode sslBufferMode,
            final SSLSessionInitializer initializer,
            final SSLSessionVerifier verifier,
            final Timeout handshakeTimeout,
            final FutureCallback<TransportSecurityLayer> callback) {
        final SSLIOSession sslioSession = new SSLIOSession(
                endpoint != null ? endpoint : initialEndpoint,
                ioSession,
                initialEndpoint != null ? SSLMode.CLIENT : SSLMode.SERVER,
                sslContext,
                sslBufferMode,
                initializer,
                verifier,
                this::onTLSSessionStart,
                this::onTLSSessionEnd,
                handshakeTimeout);
        if (tlsSessionRef.compareAndSet(null, sslioSession)) {
            currentSessionRef.set(ioSessionDecorator != null ? ioSessionDecorator.decorate(sslioSession) : sslioSession);
            tlsHandshakeCallbackRef.set(callback);
        } else {
            throw new IllegalStateException("TLS already activated");
        }
        try {
            if (sessionListener != null) {
                sessionListener.startTls(sslioSession);
            }
            sslioSession.beginHandshake(this);
        } catch (final Exception ex) {
            onException(ex);
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
        final IOSession currentSession = currentSessionRef.get();
        if (closeMode == CloseMode.IMMEDIATE) {
            closed.set(true);
            currentSession.close(closeMode);
        } else {
            if (closed.compareAndSet(false, true)) {
                try {
                    currentSession.close(closeMode);
                } finally {
                    closedSessions.add(this);
                }
            }
        }
    }

    @Override
    public IOSession.Status getStatus() {
        final IOSession currentSession = currentSessionRef.get();
        return currentSession.getStatus();
    }

    @Override
    public boolean isOpen() {
        final IOSession currentSession = currentSessionRef.get();
        return currentSession.isOpen();
    }

    @Override
    public void enqueue(final Command command, final Command.Priority priority) {
        final IOSession currentSession = currentSessionRef.get();
        currentSession.enqueue(command, priority);
    }

    @Override
    public boolean hasCommands() {
        final IOSession currentSession = currentSessionRef.get();
        return currentSession.hasCommands();
    }

    @Override
    public Command poll() {
        final IOSession currentSession = currentSessionRef.get();
        return currentSession.poll();
    }

    @Override
    public ByteChannel channel() {
        final IOSession currentSession = currentSessionRef.get();
        return currentSession.channel();
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
        final IOSession currentSession = currentSessionRef.get();
        return currentSession.getEventMask();
    }

    @Override
    public void setEventMask(final int ops) {
        final IOSession currentSession = currentSessionRef.get();
        currentSession.setEventMask(ops);
    }

    @Override
    public void setEvent(final int op) {
        final IOSession currentSession = currentSessionRef.get();
        currentSession.setEvent(op);
    }

    @Override
    public void clearEvent(final int op) {
        final IOSession currentSession = currentSessionRef.get();
        currentSession.clearEvent(op);
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
        final IOSession currentSession = currentSessionRef.get();
        return currentSession.read(dst);
    }

    @Override
    public int write(final ByteBuffer src) throws IOException {
        final IOSession currentSession = currentSessionRef.get();
        return currentSession.write(src);
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
    public void switchProtocol(final String protocolId, final FutureCallback<ProtocolIOSession> callback) {
        Args.notEmpty(protocolId, "Application protocol ID");
        final ProtocolUpgradeHandler upgradeHandler = protocolUpgradeHandlerMap.get(protocolId.toLowerCase(Locale.ROOT));
        if (upgradeHandler != null) {
            upgradeHandler.upgrade(this, callback);
        } else {
            throw new IllegalStateException("Unsupported protocol: " + protocolId);
        }
    }

    @Override
    public void registerProtocol(final String protocolId, final ProtocolUpgradeHandler upgradeHandler) {
        Args.notEmpty(protocolId, "Application protocol ID");
        Args.notNull(upgradeHandler, "Protocol upgrade handler");
        protocolUpgradeHandlerMap.put(protocolId.toLowerCase(Locale.ROOT), upgradeHandler);
    }

    @Override
    public String toString() {
        final IOSession currentSession = currentSessionRef.get();
        if (currentSession != null) {
            return currentSession.toString();
        } else {
            return ioSession.toString();
        }
    }

}
