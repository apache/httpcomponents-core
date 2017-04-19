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

import java.io.Closeable;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.concurrent.Cancellable;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.io.ShutdownType;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;

/**
 * {@link IOReactor} implementation.
 *
 * @since 4.0
 */
class IOReactorImpl implements IOReactor {

    private final IOReactorConfig reactorConfig;
    private final IOEventHandlerFactory eventHandlerFactory;
    private final Selector selector;
    private final Queue<InternalIOSession> closedSessions;
    private final Queue<PendingSession> pendingSessions;
    private final AtomicReference<IOReactorStatus> status;
    private final AtomicBoolean shutdownInitiated;
    private final Object shutdownMutex;
    private final IOReactorExceptionHandler exceptionHandler;
    private final Callback<IOSession> sessionShutdownCallback;

    private volatile long lastTimeoutCheck;

    IOReactorImpl(
            final IOEventHandlerFactory eventHandlerFactory,
            final IOReactorConfig reactorConfig,
            final IOReactorExceptionHandler exceptionHandler,
            final Callback<IOSession> sessionShutdownCallback) {
        super();
        this.reactorConfig = Args.notNull(reactorConfig, "I/O reactor config");
        this.eventHandlerFactory = Args.notNull(eventHandlerFactory, "Event handler factory");
        this.exceptionHandler = exceptionHandler;
        this.sessionShutdownCallback = sessionShutdownCallback;
        this.shutdownInitiated = new AtomicBoolean(false);
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

    private void closeQuietly(final Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (final IOException ignore) {
            }
        }
    }

    private void cancelQuietly(final Cancellable cancellable) {
        if (cancellable != null) {
            cancellable.cancel();
        }
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
                    if (this.shutdownInitiated.compareAndSet(false, true)) {
                        initiateSessionShutdown();
                    }
                    closePendingSessions();
                }
                if (this.status.get().compareTo(IOReactorStatus.SHUT_DOWN) == 0) {
                    break;
                }

                // Process selected I/O events
                if (readyCount > 0) {
                    processEvents(this.selector.selectedKeys());
                }

                validateActiveChannels();

                // Process closed sessions
                processClosedSessions();

                // If active process new channels
                if (this.status.get().compareTo(IOReactorStatus.ACTIVE) == 0) {
                    processPendingSessions();
                }

                // Exit select loop if graceful shutdown has been completed
                if (this.status.get().compareTo(IOReactorStatus.SHUTTING_DOWN) == 0
                        && this.selector.keys().isEmpty()) {
                    this.status.set(IOReactorStatus.SHUT_DOWN);
                }
                if (this.status.get().compareTo(IOReactorStatus.SHUT_DOWN) == 0) {
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

    private void initiateSessionShutdown() {
        if (this.sessionShutdownCallback != null) {
            final Set<SelectionKey> keys = this.selector.keys();
            for (final SelectionKey key : keys) {
                final InternalIOSession session = (InternalIOSession) key.attachment();
                if (session != null) {
                    this.sessionShutdownCallback.execute(session);
                }
            }
        }
    }

    private void validateActiveChannels() {
        final long currentTime = System.currentTimeMillis();
        if( (currentTime - this.lastTimeoutCheck) >= this.reactorConfig.getSelectInterval()) {
            this.lastTimeoutCheck = currentTime;
            for (final SelectionKey key : this.selector.keys()) {
                timeoutCheck(key, currentTime);
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
        final InternalIOSession session = (InternalIOSession) key.attachment();
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
            session.shutdown(ShutdownType.GRACEFUL);
        } catch (final RuntimeException ex) {
            session.shutdown(ShutdownType.IMMEDIATE);
            handleRuntimeException(ex);
        }
    }

    private void processPendingSessions() throws IOReactorException {
        PendingSession pendingSession;
        while ((pendingSession = this.pendingSessions.poll()) != null) {
            final InternalIOSession session;
            try {
                final SocketChannel socketChannel = pendingSession.socketChannel;
                final SessionRequestImpl sessionRequest = pendingSession.sessionRequest;
                socketChannel.configureBlocking(false);
                final SelectionKey key = socketChannel.register(this.selector, SelectionKey.OP_READ);
                session = new InternalIOSession(
                        sessionRequest != null ?  sessionRequest.getRemoteEndpoint() : null,
                        new IOSessionImpl(key, socketChannel),
                        closedSessions);
                session.setHandler(this.eventHandlerFactory.createHandler(session,
                        sessionRequest != null ? sessionRequest.getAttachment() : null));
                session.setSocketTimeout(this.reactorConfig.getSoTimeout().toMillisIntBound());
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
                session.shutdown(ShutdownType.GRACEFUL);
            }
        }
    }

    private void processClosedSessions() {
        for (;;) {
            final InternalIOSession session = this.closedSessions.poll();
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
        final InternalIOSession session = (InternalIOSession) key.attachment();
        if (session != null) {
            try {
                final int timeout = session.getSocketTimeout();
                if (timeout > 0) {
                    if (session.getLastAccessTime() + timeout < now) {
                        session.onTimeout();
                    }
                }
            } catch (final CancelledKeyException ex) {
                session.shutdown(ShutdownType.GRACEFUL);
            } catch (final RuntimeException ex) {
                session.shutdown(ShutdownType.IMMEDIATE);
                handleRuntimeException(ex);
            }
        }
    }

    private void closePendingSessions() {
        for (;;) {
            final PendingSession pendingSession = this.pendingSessions.poll();
            if (pendingSession == null) {
                break;
            } else {
                cancelQuietly(pendingSession.sessionRequest);
                closeQuietly(pendingSession.socketChannel);
            }
        }
    }

    private void closeActiveChannels() {
        final Set<SelectionKey> keys = this.selector.keys();
        for (final SelectionKey key : keys) {
            final InternalIOSession session = (InternalIOSession) key.attachment();
            closeQuietly(session);
        }
        closeQuietly(this.selector);
    }

    @Override
    public void awaitShutdown(final TimeValue waitTime) throws InterruptedException {
        Args.notNull(waitTime, "Wait time");
        final long deadline = System.currentTimeMillis() + waitTime.toMillis();
        long remaining = waitTime.toMillis();
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
        if (this.status.compareAndSet(IOReactorStatus.ACTIVE, IOReactorStatus.SHUTTING_DOWN)) {
            selector.wakeup();
        }
    }

    void forceShutdown() {
        this.status.set(IOReactorStatus.SHUT_DOWN);
        this.selector.wakeup();
    }

    @Override
    public void shutdown(final ShutdownType shutdownType) {
        initiateShutdown();
        try {
            if (shutdownType == ShutdownType.GRACEFUL) {
                awaitShutdown(TimeValue.ofSeconds(5));
            }
            forceShutdown();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        shutdown(ShutdownType.GRACEFUL);
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
