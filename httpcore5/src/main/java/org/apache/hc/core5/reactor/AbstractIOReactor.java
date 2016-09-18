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

import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Asserts;

/**
 * Generic implementation of {@link IOReactor} that can used as a subclass
 * for more specialized I/O reactors. It is based on a single {@link Selector}
 * instance.
 *
 * @since 4.0
 */
abstract class AbstractIOReactor implements IOReactor {

    private final IOReactorConfig reactorConfig;
    private final IOEventHandlerFactory eventHandlerFactory;
    private final Selector selector;
    private final Queue<IOSession> closedSessions;
    private final Queue<PendingSession> pendingSessions;
    private final Object shutdownMutex;

    private final AtomicReference<IOReactorStatus> status;

    /**
     * Creates new AbstractIOReactor instance.
     *
     * @param eventHandlerFactory the event handler factory.
     * @param reactorConfig the reactor configuration.
     */
    public AbstractIOReactor(final IOEventHandlerFactory eventHandlerFactory, final IOReactorConfig reactorConfig) {
        super();
        this.reactorConfig = Args.notNull(reactorConfig, "I/O reactor config");
        this.eventHandlerFactory = Args.notNull(eventHandlerFactory, "Event handler factory");
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

    /**
     * Triggered when the key signals {@link SelectionKey#OP_ACCEPT} readiness.
     * <p>
     * Super-classes can implement this method to react to the event.
     *
     * @param key the selection key.
     */
    protected abstract void acceptable(SelectionKey key);

    /**
     * Triggered when the key signals {@link SelectionKey#OP_CONNECT} readiness.
     * <p>
     * Super-classes can implement this method to react to the event.
     *
     * @param key the selection key.
     */
    protected abstract void connectable(SelectionKey key);

    /**
     * Triggered when the key signals {@link SelectionKey#OP_READ} readiness.
     * <p>
     * Super-classes can implement this method to react to the event.
     *
     * @param key the selection key.
     */
    protected abstract void readable(SelectionKey key);

    /**
     * Triggered when the key signals {@link SelectionKey#OP_WRITE} readiness.
     * <p>
     * Super-classes can implement this method to react to the event.
     *
     * @param key the selection key.
     */
    protected abstract void writable(SelectionKey key);

    /**
     * Triggered to validate keys currently registered with the selector. This
     * method is called after each I/O select loop.
     * <p>
     * Super-classes can implement this method to run validity checks on
     * active sessions and include additional processing that needs to be
     * executed after each I/O select loop.
     *
     * @param keys all selection keys registered with the selector.
     */
    protected abstract void validate(Set<SelectionKey> keys);

    /**
     * Triggered when new session has been created.
     * <p>
     * Super-classes can implement this method to react to the event.
     *
     * @param session new I/O session.
     */
    protected abstract void sessionCreated(final IOSession session);

    /**
     * Triggered when a session has been closed.
     * <p>
     * Super-classes can implement this method to react to the event.
     *
     * @param session closed I/O session.
     */
    protected abstract void sessionClosed(final IOSession session);

    /**
     * Triggered when a session has timed out.
     * <p>
     * Super-classes can implement this method to react to the event.
     *
     * @param session timed out I/O session.
     */
    protected abstract void sessionTimedOut(final IOSession session);

    /**
     * Obtains {@link IOSession} instance associated with the given selection
     * key.
     *
     * @param key the selection key.
     * @return I/O session.
     */
    protected IOSession getSession(final SelectionKey key) {
        return (IOSession) key.attachment();
    }

    protected IOEventHandler ensureEventHandler(final IOSession ioSession) {
        Asserts.notNull(ioSession, "IO session");
        final IOEventHandler handler = ioSession.getHandler();
        Asserts.notNull(handler, "IO event handler");
        return handler;
    }

    @Override
    public IOReactorStatus getStatus() {
        return this.status.get();
    }

    /**
     * Enqueues pending session. The socket channel will be asynchronously registered
     * with the selector.
     *
     * @param socketChannel the new socketChannel.
     * @param sessionRequest the session request if applicable.
     */
    public void enqueuePendingSession(final SocketChannel socketChannel, final SessionRequestImpl sessionRequest) {
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
     * @see #acceptable(SelectionKey)
     * @see #connectable(SelectionKey)
     * @see #readable(SelectionKey)
     * @see #writable(SelectionKey)
     * @see #timeoutCheck(SelectionKey, long)
     * @see #validate(Set)
     * @see #sessionCreated(IOSession)
     * @see #sessionClosed(IOSession)
     *
     * @throws InterruptedIOException if the dispatch thread is interrupted.
     * @throws IOReactorException in case if a non-recoverable I/O error.
     */

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

    private void processEvents(final Set<SelectionKey> selectedKeys) {
        for (final SelectionKey key : selectedKeys) {

            processEvent(key);

        }
        selectedKeys.clear();
    }

    /**
     * Processes new event on the given selection key.
     *
     * @param key the selection key that triggered an event.
     */
    protected void processEvent(final SelectionKey key) {
        final IOSessionImpl session = (IOSessionImpl) key.attachment();
        try {
            if (key.isAcceptable()) {
                acceptable(key);
            }
            if (key.isConnectable()) {
                connectable(key);
            }
            if (key.isReadable()) {
                session.resetLastRead();
                readable(key);
            }
            if (key.isWritable()) {
                session.resetLastWrite();
                writable(key);
            }
        } catch (final CancelledKeyException ex) {
            session.shutdown();
        }
    }

    private void processPendingSessions() throws IOReactorException {
        PendingSession pendingSession;
        while ((pendingSession = this.pendingSessions.poll()) != null) {
            final IOSession session;
            try {
                final SocketChannel socketChannel = pendingSession.socketChannel;
                socketChannel.configureBlocking(false);
                final SelectionKey key = socketChannel.register(this.selector, SelectionKey.OP_READ);
                session = new IOSessionImpl(key, socketChannel, this.closedSessions);
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
                    session.setAttribute(IOSession.ATTACHMENT_KEY, sessionRequest.getAttachment());
                    sessionRequest.completed(session);
                }
                sessionCreated(session);
            } catch (final CancelledKeyException ex) {
                session.shutdown();
            }
        }
    }

    private void processClosedSessions() {
        IOSession session;
        while ((session = this.closedSessions.poll()) != null) {
            try {
                sessionClosed(session);
            } catch (final CancelledKeyException ex) {
                // ignore and move on
            }
        }
    }

    /**
     * Triggered to verify whether the I/O session associated with the
     * given selection key has not timed out.
     * <p>
     * Super-classes can implement this method to react to the event.
     *
     * @param key the selection key.
     * @param now current time as long value.
     */
    protected void timeoutCheck(final SelectionKey key, final long now) {
        final IOSessionImpl session = (IOSessionImpl) key.attachment();
        if (session != null) {
            final int timeout = session.getSocketTimeout();
            if (timeout > 0) {
                if (session.getLastAccessTime() + timeout < now) {
                    sessionTimedOut(session);
                }
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

    /**
     * Closes out all active channels registered with the selector of
     * this I/O reactor.
     */
    protected void closeActiveChannels() {
        try {
            final Set<SelectionKey> keys = this.selector.keys();
            for (final SelectionKey key : keys) {
                final IOSession session = getSession(key);
                if (session != null) {
                    session.close();
                }
            }
            this.selector.close();
        } catch (final IOException ignore) {
        }
    }

    /**
     * Enumerates all active sessions
     *
     * @since 5.0
     */
    protected void enumSessions(final IOSessionCallback callback) throws IOException {
        if (this.selector.isOpen()) {
            final Set<SelectionKey> keys = this.selector.keys();
            for (final SelectionKey key : keys) {
                final IOSession session = getSession(key);
                if (session != null) {
                    callback.execute(session);
                }
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
