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
import java.io.InterruptedIOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.util.Args;

/**
 * {@link IOReactor} implementation.
 *
 * @since 4.0
 */
class IOReactorImpl implements IOReactor {

    private final IOReactorConfig reactorConfig;
    private final IOEventHandlerFactory eventHandlerFactory;
    private final Selector selector;
    private final Queue<ManagedIOSession> closedSessions;
    private final Queue<PendingSession> pendingSessions;
    private final AtomicReference<IOReactorStatus> status;
    private final Object shutdownMutex;
    private final IOReactorExceptionHandler exceptionHandler;

    private volatile long lastTimeoutCheck;

    IOReactorImpl(
            final IOEventHandlerFactory eventHandlerFactory,
            final IOReactorConfig reactorConfig,
            final IOReactorExceptionHandler exceptionHandler) {
        super();
        this.reactorConfig = Args.notNull(reactorConfig, "I/O reactor config");
        this.eventHandlerFactory = Args.notNull(eventHandlerFactory, "Event handler factory");
        this.exceptionHandler = exceptionHandler;
        this.closedSessions = new ConcurrentLinkedQueue<>();
        this.pendingSessions = new ConcurrentLinkedQueue<>();
        try {
            this.selector = Selector.open();
        } catch (final IOException ex) {
            throw new IllegalStateException("Unexpected failure opening I/O selector", ex);
        }
        this.shutdownMutex = new Object();
        this.status = new AtomicReference<>(IOReactorStatus.INACTIVE);
    }

    @Override
    public IOReactorStatus getStatus() {
        return this.status.get();
    }

    void enqueuePendingSession(final SocketChannel socketChannel, final SessionRequestImpl sessionRequest) {
        Args.notNull(socketChannel, "SocketChannel");
        this.pendingSessions.add(new PendingSession(socketChannel, sessionRequest));
        this.selector.wakeup();
    }

    /**
     * Activates the I/O reactor. The I/O reactor will start reacting to I/O
     * events and and dispatch I/O event notifications to the {@link IOEventHandler}
     * associated with the given I/O session.
     * <p>
     * This method will enter the infinite I/O select loop on
     * the {@link Selector} instance associated with this I/O reactor.
     * <p>
     * The method will remain blocked unto the I/O reactor is shut down or the
     * execution thread is interrupted.
     *
     * @throws InterruptedIOException if the dispatch thread is interrupted.
     * @throws IOReactorException in case if a non-recoverable I/O error.
     */

    @Override
    public void execute() throws InterruptedIOException, IOReactorException {
        if (this.status.compareAndSet(IOReactorStatus.INACTIVE, IOReactorStatus.ACTIVE)) {
            doExecute();
        }
    }

    private void doExecute() throws InterruptedIOException, IOReactorException {

        final long selectTimeout = this.reactorConfig.getSelectInterval();
        try {
            while (!Thread.currentThread().isInterrupted()) {

                final int readyCount;
                try {
                    readyCount = this.selector.select(selectTimeout);
                } catch (final InterruptedIOException ex) {
                    throw ex;
                } catch (final IOException ex) {
                    throw new IOReactorException("Unexpected selector failure", ex);
                }

                if (this.status.get().compareTo(IOReactorStatus.SHUTTING_DOWN) >= 0) {
                    closePendingSessions();
                }
                if (this.status.get().compareTo(IOReactorStatus.SHUT_DOWN) == 0) {
                    break;
                }

                // Process selected I/O events
                if (readyCount > 0) {
                    processEvents(this.selector.selectedKeys());
                }

                // Validate active channels
                validate(this.selector.keys());

                // Process closed sessions
                processClosedSessions();

                // If active process new channels
                if (this.status.get().compareTo(IOReactorStatus.ACTIVE) == 0) {
                    processPendingSessions();
                }

                // Exit select loop if graceful shutdown has been completed
                if (this.status.get().compareTo(IOReactorStatus.SHUT_DOWN) > 0 && this.selector.keys().isEmpty()) {
                    break;
                }
            }

        } catch (final ClosedSelectorException ignore) {
        } finally {
            try {
                closePendingSessions();
                closeActiveChannels();
                processClosedSessions();
            } finally {
                this.status.set(IOReactorStatus.SHUT_DOWN);
                synchronized (this.shutdownMutex) {
                    this.shutdownMutex.notifyAll();
                }
            }
        }
    }

    private void validate(final Set<SelectionKey> keys) {
        final long currentTime = System.currentTimeMillis();
        if( (currentTime - this.lastTimeoutCheck) >= this.reactorConfig.getSelectInterval()) {
            this.lastTimeoutCheck = currentTime;
            if (keys != null) {
                for (final SelectionKey key : keys) {
                    timeoutCheck(key, currentTime);
                }
            }
        }
    }

    private void processEvents(final Set<SelectionKey> selectedKeys) {
        for (final SelectionKey key : selectedKeys) {
            processEvent(key);
        }
        selectedKeys.clear();
    }

    private void handleRuntimeException(final RuntimeException ex) {
        if (this.exceptionHandler == null || !this.exceptionHandler.handle(ex)) {
            throw ex;
        }
    }

    private void processEvent(final SelectionKey key) {
        final ManagedIOSession session = (ManagedIOSession) key.attachment();
        try {
            if (key.isReadable()) {
                session.updateAccessTime();
                session.onInputReady();
            }
            if (key.isWritable()) {
                session.updateAccessTime();
                session.onOutputReady();
            }
        } catch (final CancelledKeyException ex) {
            session.shutdown();
        } catch (final RuntimeException ex) {
            session.shutdown();
            handleRuntimeException(ex);
        }
    }

    private void processPendingSessions() throws IOReactorException {
        PendingSession pendingSession;
        while ((pendingSession = this.pendingSessions.poll()) != null) {
            final ManagedIOSession session;
            try {
                final SocketChannel socketChannel = pendingSession.socketChannel;
                final SessionRequestImpl sessionRequest = pendingSession.sessionRequest;
                socketChannel.configureBlocking(false);
                final SelectionKey key = socketChannel.register(this.selector, SelectionKey.OP_READ);
                session = new ManagedIOSession(
                        sessionRequest != null ?  sessionRequest.getRemoteEndpoint() : null,
                        new IOSessionImpl(key, socketChannel),
                        closedSessions);
                session.setHandler(this.eventHandlerFactory.createHandler(session));
                session.setSocketTimeout(this.reactorConfig.getSoTimeout());
                key.attach(session);
            } catch (final ClosedChannelException ex) {
                final SessionRequestImpl sessionRequest = pendingSession.sessionRequest;
                if (sessionRequest != null) {
                    sessionRequest.failed(ex);
                }
                return;
            } catch (final IOException ex) {
                throw new IOReactorException("Failure registering channel with the selector", ex);
            }
            try {
                final SessionRequestImpl sessionRequest = pendingSession.sessionRequest;
                if (sessionRequest != null) {
                    sessionRequest.completed(session);
                }
                try {
                    session.onConnected();
                } catch (final RuntimeException ex) {
                    handleRuntimeException(ex);
                }
            } catch (final CancelledKeyException ex) {
                session.shutdown();
            }
        }
    }

    private void processClosedSessions() {
        for (;;) {
            final ManagedIOSession session = this.closedSessions.poll();
            if (session == null) {
                break;
            }
            try {
                session.onDisconnected();
            } catch (final CancelledKeyException ex) {
                // ignore and move on
            } catch (final RuntimeException ex) {
                handleRuntimeException(ex);
            }
        }
    }

    private void timeoutCheck(final SelectionKey key, final long now) {
        final ManagedIOSession session = (ManagedIOSession) key.attachment();
        if (session != null) {
            try {
                final int timeout = session.getSocketTimeout();
                if (timeout > 0) {
                    if (session.getLastAccessTime() + timeout < now) {
                        session.onTimeout();
                    }
                }
            } catch (final CancelledKeyException ex) {
                session.shutdown();
            } catch (final RuntimeException ex) {
                session.shutdown();
                handleRuntimeException(ex);
            }
        }
    }

    private void closePendingSessions() {
        PendingSession pendingSession;
        while ((pendingSession = this.pendingSessions.poll()) != null) {
            final SessionRequestImpl sessionRequest = pendingSession.sessionRequest;
            if (sessionRequest != null) {
                sessionRequest.cancel();
            }
            final SocketChannel channel = pendingSession.socketChannel;
            try {
                channel.close();
            } catch (final IOException ignore) {
            }
        }
    }

    private void closeActiveChannels() {
        try {
            final Set<SelectionKey> keys = this.selector.keys();
            for (final SelectionKey key : keys) {
                final ManagedIOSession session = (ManagedIOSession) key.attachment();
                if (session != null) {
                    session.close();
                }
            }
            this.selector.close();
        } catch (final IOException ignore) {
        }
    }

    void enumSessions(final Callback<IOSession> callback) {
        if (this.selector.isOpen()) {
            try {
                final Set<SelectionKey> keys = this.selector.keys();
                for (final SelectionKey key : keys) {
                    final ManagedIOSession session = (ManagedIOSession) key.attachment();
                    if (session != null) {
                        try {
                            callback.execute(session);
                        } catch (CancelledKeyException ex) {
                            session.close();
                        }
                    }
                }
            } catch (ClosedSelectorException ignore) {
            }
        }
    }

    @Override
    public void awaitShutdown(final long timeout, final TimeUnit timeUnit) throws InterruptedException {
        Args.notNull(timeUnit, "Time unit");
        final long timeoutMs = timeUnit.toMillis(timeout);
        final long deadline = System.currentTimeMillis() + timeoutMs;
        long remaining = timeoutMs;
        synchronized (this.shutdownMutex) {
            while (this.status.get().compareTo(IOReactorStatus.SHUT_DOWN) < 0) {
                this.shutdownMutex.wait(remaining);
                remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    return;
                }
            }
        }
    }

    @Override
    public void initiateShutdown() {
        if (this.status.compareAndSet(IOReactorStatus.ACTIVE, IOReactorStatus.SHUT_DOWN)) {
            selector.wakeup();
        }
    }

    void forceShutdown() {
        this.status.set(IOReactorStatus.SHUT_DOWN);
        this.selector.wakeup();
    }

    @Override
    public void shutdown(final long graceTime, final TimeUnit timeUnit) {
        initiateShutdown();
        try {
            awaitShutdown(graceTime, timeUnit);
            forceShutdown();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static class PendingSession {

        final SocketChannel socketChannel;
        final SessionRequestImpl sessionRequest;

        private PendingSession(final SocketChannel socketChannel, final SessionRequestImpl sessionRequest) {
            this.socketChannel = socketChannel;
            this.sessionRequest = sessionRequest;
        }

    }

}
