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
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLContext;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.io.ShutdownType;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.reactor.ssl.SSLBufferManagement;
import org.apache.hc.core5.reactor.ssl.SSLIOSession;
import org.apache.hc.core5.reactor.ssl.SSLMode;
import org.apache.hc.core5.reactor.ssl.SSLSessionInitializer;
import org.apache.hc.core5.reactor.ssl.SSLSessionVerifier;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.hc.core5.util.Asserts;

/**
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.SAFE)
class InternalIOSession implements TlsCapableIOSession {

    private final NamedEndpoint namedEndpoint;
    private final IOSession ioSession;
    private final AtomicReference<SSLIOSession> tlsSessionRef;
    private final Queue<InternalIOSession> closedSessions;
    private final AtomicBoolean connected;
    private final AtomicBoolean closed;

    private volatile long lastAccessTime;

    InternalIOSession(
            final NamedEndpoint namedEndpoint,
            final IOSession ioSession,
            final Queue<InternalIOSession> closedSessions) {
        this.namedEndpoint = namedEndpoint;
        this.ioSession = ioSession;
        this.closedSessions = closedSessions;
        this.tlsSessionRef = new AtomicReference<>(null);
        this.connected = new AtomicBoolean(false);
        this.closed = new AtomicBoolean(false);
        updateAccessTime();
    }

    @Override
    public String getId() {
        return ioSession.getId();
    }

    void updateAccessTime() {
        this.lastAccessTime = System.currentTimeMillis();
    }

    long getLastAccessTime() {
        return lastAccessTime;
    }

    private IOSession getSessionImpl() {
        final SSLIOSession tlsSession = tlsSessionRef.get();
        if (tlsSession != null) {
            return tlsSession;
        } else {
            return ioSession;
        }
    }

    IOEventHandler getEventHandler() {
        final IOEventHandler handler = ioSession.getHandler();
        Asserts.notNull(handler, "IO event handler");
        return handler;
    }

    void onConnected() {
        try {
            final SSLIOSession tlsSession = tlsSessionRef.get();
            if (tlsSession != null) {
                try {
                    if (!tlsSession.isInitialized()) {
                        tlsSession.initialize();
                    }
                } catch (final Exception ex) {
                    final IOEventHandler handler = getEventHandler();
                    handler.exception(tlsSession, ex);
                }
            } else {
                if (connected.compareAndSet(false, true)) {
                    final IOEventHandler handler = getEventHandler();
                    handler.connected(ioSession);
                }
            }
        } catch (final RuntimeException ex) {
            shutdown(ShutdownType.IMMEDIATE);
            throw ex;
        }
    }

    void onInputReady() {
        try {
            final SSLIOSession tlsSession = tlsSessionRef.get();
            if (tlsSession != null) {
                try {
                    if (!tlsSession.isInitialized()) {
                        tlsSession.initialize();
                    }
                    if (tlsSession.isAppInputReady()) {
                        do {
                            final IOEventHandler handler = getEventHandler();
                            handler.inputReady(tlsSession);
                        } while (tlsSession.hasInputDate());
                    }
                    tlsSession.inboundTransport();
                } catch (final IOException ex) {
                    final IOEventHandler handler = getEventHandler();
                    handler.exception(tlsSession, ex);
                    tlsSession.shutdown(ShutdownType.IMMEDIATE);
                }
            } else {
                final IOEventHandler handler = getEventHandler();
                handler.inputReady(ioSession);
            }
        } catch (final RuntimeException ex) {
            shutdown(ShutdownType.IMMEDIATE);
            throw ex;
        }
    }

    void onOutputReady() {
        try {
            final SSLIOSession tlsSession = tlsSessionRef.get();
            if (tlsSession != null) {
                try {
                    if (!tlsSession.isInitialized()) {
                        tlsSession.initialize();
                    }
                    if (tlsSession.isAppOutputReady()) {
                        final IOEventHandler handler = getEventHandler();
                        handler.outputReady(tlsSession);
                    }
                    tlsSession.outboundTransport();
                } catch (final IOException ex) {
                    final IOEventHandler handler = getEventHandler();
                    handler.exception(tlsSession, ex);
                    tlsSession.shutdown(ShutdownType.IMMEDIATE);
                }
            } else {
                final IOEventHandler handler = getEventHandler();
                handler.outputReady(ioSession);
            }
        } catch (final RuntimeException ex) {
            shutdown(ShutdownType.IMMEDIATE);
            throw ex;
        }
    }

    void onTimeout() {
        try {
            final IOEventHandler handler = getEventHandler();
            handler.timeout(ioSession);
            final SSLIOSession tlsSession = tlsSessionRef.get();
            if (tlsSession != null) {
                if (tlsSession.isOutboundDone() && !tlsSession.isInboundDone()) {
                    // The session failed to terminate cleanly
                    tlsSession.shutdown(ShutdownType.IMMEDIATE);
                }
            }
        } catch (final RuntimeException ex) {
            shutdown(ShutdownType.IMMEDIATE);
            throw ex;
        }
    }

    void onDisconnected() {
        final IOEventHandler handler = getEventHandler();
        handler.disconnected(ioSession);
    }

    @Override
    public void startTls(
            final SSLContext sslContext,
            final SSLBufferManagement sslBufferManagement,
            final SSLSessionInitializer initializer,
            final SSLSessionVerifier verifier) {
        if (!tlsSessionRef.compareAndSet(null, new SSLIOSession(
                namedEndpoint,
                ioSession,
                namedEndpoint != null ? SSLMode.CLIENT : SSLMode.SERVER,
                sslContext,
                sslBufferManagement,
                initializer,
                verifier,
                new Callback<SSLIOSession>() {

                    @Override
                    public void execute(final SSLIOSession sslSession) {
                        if (connected.compareAndSet(false, true)) {
                            final IOEventHandler handler = getEventHandler();
                            handler.connected(sslSession);
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
    public void shutdown(final ShutdownType shutdownType) {
        if (closed.compareAndSet(false, true)) {
            try {
                getSessionImpl().shutdown(shutdownType);
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
    public IOEventHandler getHandler() {
        return ioSession.getHandler();
    }

    @Override
    public void setHandler(final IOEventHandler eventHandler) {
        ioSession.setHandler(eventHandler);
    }

    @Override
    public void addLast(final Command command) {
        getSessionImpl().addLast(command);
    }

    @Override
    public void addFirst(final Command command) {
        getSessionImpl().addFirst(command);
    }

    @Override
    public Command getCommand() {
        return getSessionImpl().getCommand();
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
    public int getSocketTimeout() {
        return ioSession.getSocketTimeout();
    }

    @Override
    public void setSocketTimeout(final int timeout) {
        ioSession.setSocketTimeout(timeout);
    }

    @Override
    public String toString() {
        return getSessionImpl().toString();
    }

}
