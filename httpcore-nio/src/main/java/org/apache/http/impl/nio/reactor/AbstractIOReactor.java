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

package org.apache.http.impl.nio.reactor;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Collections;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.ConcurrentModificationException;

import org.apache.http.nio.reactor.IOReactor;
import org.apache.http.nio.reactor.IOReactorException;
import org.apache.http.nio.reactor.IOReactorStatus;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.util.Args;
import org.apache.http.util.Asserts;

/**
 * Generic implementation of {@link IOReactor} that can used as a subclass
 * for more specialized I/O reactors. It is based on a single {@link Selector}
 * instance.
 *
 * @since 4.0
 */
public abstract class AbstractIOReactor implements IOReactor {

    private volatile IOReactorStatus status;

    private final Object statusMutex;
    private final long selectTimeout;
    private final boolean interestOpsQueueing;
    private Selector selector;
    private final Set<IOSession> sessions;
    private final Queue<InterestOpEntry> interestOpsQueue;
    private final Queue<IOSession> closedSessions;
    private final Queue<ChannelEntry> newChannels;

    private final boolean epollBugWorkaround;

    private final int nioBugTimesThreshold = 10;

    private int nioBugTimes = 0;

    /**
     * Creates new AbstractIOReactor instance.
     *
     * @param selectTimeout the select timeout.
     * @throws IOReactorException in case if a non-recoverable I/O error.
     */
    public AbstractIOReactor(final long selectTimeout, final boolean epollBugWorkaround) throws IOReactorException {
        this(selectTimeout, false, false);
    }

    /**
     * Creates new AbstractIOReactor instance.
     *
     * @param selectTimeout the select timeout.
     * @param interestOpsQueueing Ops queueing flag.
     *
     * @throws IOReactorException in case if a non-recoverable I/O error.
     *
     * @since 4.1
     */
    public AbstractIOReactor(final long selectTimeout, final boolean interestOpsQueueing, final boolean epollBugWorkaround) throws IOReactorException {
        super();
        Args.positive(selectTimeout, "Select timeout");
        this.selectTimeout = selectTimeout;
        this.interestOpsQueueing = interestOpsQueueing;
        this.sessions = Collections.synchronizedSet(new HashSet<IOSession>());
        this.interestOpsQueue = new ConcurrentLinkedQueue<InterestOpEntry>();
        this.closedSessions = new ConcurrentLinkedQueue<IOSession>();
        this.newChannels = new ConcurrentLinkedQueue<ChannelEntry>();
        try {
            this.selector = Selector.open();
        } catch (final IOException ex) {
            throw new IOReactorException("Failure opening selector", ex);
        }
        this.statusMutex = new Object();
        this.status = IOReactorStatus.INACTIVE;
        this.epollBugWorkaround = epollBugWorkaround;
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
     * @param key the selection key.
     * @param session new I/O session.
     */
    protected void sessionCreated(final SelectionKey key, final IOSession session) {
    }

    /**
     * Triggered when a session has been closed.
     * <p>
     * Super-classes can implement this method to react to the event.
     *
     * @param session closed I/O session.
     */
    protected void sessionClosed(final IOSession session) {
    }

    /**
     * Triggered when a session has timed out.
     * <p>
     * Super-classes can implement this method to react to the event.
     *
     * @param session timed out I/O session.
     */
    protected void sessionTimedOut(final IOSession session) {
    }

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

    @Override
    public IOReactorStatus getStatus() {
        return this.status;
    }

    /**
     * Returns {@code true} if interest Ops queueing is enabled, {@code false} otherwise.
     *
     * @since 4.1
     */
    public boolean getInterestOpsQueueing() {
        return this.interestOpsQueueing;
    }

    /**
     * Adds new channel entry. The channel will be asynchronously registered
     * with the selector.
     *
     * @param channelEntry the channel entry.
     */
    public void addChannel(final ChannelEntry channelEntry) {
        Args.notNull(channelEntry, "Channel entry");
        this.newChannels.add(channelEntry);
        this.selector.wakeup();
    }

    /**
     * Activates the I/O reactor. The I/O reactor will start reacting to
     * I/O events and triggering notification methods.
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
     * @see #sessionCreated(SelectionKey, IOSession)
     * @see #sessionClosed(IOSession)
     *
     * @throws InterruptedIOException if the dispatch thread is interrupted.
     * @throws IOReactorException in case if a non-recoverable I/O error.
     */
    protected void execute() throws InterruptedIOException, IOReactorException {
        this.status = IOReactorStatus.ACTIVE;

        try {
            for (;;) {

                final int readyCount;
                long t0 = 0;
                if (epollBugWorkaround) {
                    t0 = System.currentTimeMillis();
                }
                try {
                    readyCount = this.selector.select(selectTimeout);
                } catch (final InterruptedIOException ex) {
                    throw ex;
                } catch (final IOException ex) {
                    throw new IOReactorException("Unexpected selector failure", ex);
                }
                if (epollBugWorkaround && System.currentTimeMillis() - t0 < 100 && readyCount == 0) {
                    nioBugTimes++;
                    if (nioBugTimes == nioBugTimesThreshold) {
                        rebuildSelector();
                    }
                }

                if (this.status == IOReactorStatus.SHUT_DOWN) {
                    // Hard shut down. Exit select loop immediately
                    break;
                }

                if (this.status == IOReactorStatus.SHUTTING_DOWN) {
                    // Graceful shutdown in process
                    // Try to close things out nicely
                    closeSessions();
                    closeNewChannels();
                }

                // Process selected I/O events
                if (readyCount > 0) {
                    nioBugTimes = 0;
                    processEvents(this.selector.selectedKeys());
                }

                // Validate active channels
                validate(this.selector.keys());

                // Process closed sessions
                processClosedSessions();

                // If active process new channels
                if (this.status == IOReactorStatus.ACTIVE) {
                    processNewChannels();
                }

                // Exit select loop if graceful shutdown has been completed
                if (this.status.compareTo(IOReactorStatus.ACTIVE) > 0
                        && this.sessions.isEmpty()) {
                    break;
                }

                if (this.interestOpsQueueing) {
                    // process all pending interestOps() operations
                    processPendingInterestOps();
                }

            }

        } catch (final ClosedSelectorException ignore) {
        } finally {
            hardShutdown();
            synchronized (this.statusMutex) {
                this.statusMutex.notifyAll();
            }
        }
    }

    public void rebuildSelector() {

        final Selector oldSelector = selector;
        final Selector newSelector;
        nioBugTimes = 0;

        if (oldSelector == null) {
            return;
        }

        try {
            newSelector = Selector.open();
        } catch (final Exception e) {
            return;
        }

        // Register all channels to the new Selector.
        for (;;) {
            try {
                for (final SelectionKey key : oldSelector.keys()) {
                    try {
                        if (key.channel().keyFor(newSelector) != null) {
                            continue;
                        }

                        final int interestOps = key.interestOps();
                        key.channel().register(newSelector, interestOps, key.attachment());
                        key.cancel();
                    } catch (final Exception e) {
                        try {
                            newSelector.close();
                        } catch (final IOException ex) {
                            //ignore
                        }
                        return;
                    }
                }
            } catch (final ConcurrentModificationException e) {
                continue;
            }
            break;
        }

        selector = newSelector;

        try {
            // time to close the old selector as everything else is registered to the new one
            oldSelector.close();
        } catch (final Throwable t) {
            //ignore
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
            queueClosedSession(session);
            key.attach(null);
        }
    }

    /**
     * Queues the given I/O session to be processed asynchronously as closed.
     *
     * @param session the closed I/O session.
     */
    protected void queueClosedSession(final IOSession session) {
        if (session != null) {
            this.closedSessions.add(session);
        }
    }

    private void processNewChannels() throws IOReactorException {
        ChannelEntry entry;
        while ((entry = this.newChannels.poll()) != null) {

            final SocketChannel channel;
            final SelectionKey key;
            try {
                channel = entry.getChannel();
                channel.configureBlocking(false);
                key = channel.register(this.selector, SelectionKey.OP_READ);
            } catch (final ClosedChannelException ex) {
                final SessionRequestImpl sessionRequest = entry.getSessionRequest();
                if (sessionRequest != null) {
                    sessionRequest.failed(ex);
                }
                return;

            } catch (final IOException ex) {
                throw new IOReactorException("Failure registering channel " +
                        "with the selector", ex);
            }

            final SessionClosedCallback sessionClosedCallback = new SessionClosedCallback() {

                @Override
                public void sessionClosed(final IOSession session) {
                    queueClosedSession(session);
                }

            };

            InterestOpsCallback interestOpsCallback = null;
            if (this.interestOpsQueueing) {
                interestOpsCallback = new InterestOpsCallback() {

                    @Override
                    public void addInterestOps(final InterestOpEntry entry) {
                        queueInterestOps(entry);
                    }

                };
            }

            final IOSession session;
            try {
                session = new IOSessionImpl(key, interestOpsCallback, sessionClosedCallback);
                int timeout = 0;
                try {
                    timeout = channel.socket().getSoTimeout();
                } catch (final IOException ex) {
                    // Very unlikely to happen and is not fatal
                    // as the protocol layer is expected to overwrite
                    // this value anyways
                }

                session.setAttribute(IOSession.ATTACHMENT_KEY, entry.getAttachment());
                session.setSocketTimeout(timeout);
            } catch (final CancelledKeyException ex) {
                continue;
            }
            try {
                this.sessions.add(session);
                key.attach(session);
                final SessionRequestImpl sessionRequest = entry.getSessionRequest();
                if (sessionRequest != null) {
                    if (!sessionRequest.isTerminated()) {
                        sessionRequest.completed(session);
                    }
                    if (!sessionRequest.isTerminated() && !session.isClosed()) {
                        sessionCreated(key, session);
                    }
                    if (sessionRequest.isTerminated()) {
                        throw new CancelledKeyException();
                    }
                } else {
                    sessionCreated(key, session);
                }
            } catch (final CancelledKeyException ex) {
                session.close();
                key.attach(null);
            }
        }
    }

    private void processClosedSessions() {
        IOSession session;
        while ((session = this.closedSessions.poll()) != null) {
            if (this.sessions.remove(session)) {
                try {
                    sessionClosed(session);
                } catch (final CancelledKeyException ex) {
                    // ignore and move on
                }
            }
        }
    }

    private void processPendingInterestOps() {
        // validity check
        if (!this.interestOpsQueueing) {
            return;
        }
        InterestOpEntry entry;
        while ((entry = this.interestOpsQueue.poll()) != null) {
            // obtain the operation's details
            final SelectionKey key = entry.getSelectionKey();
            final int eventMask = entry.getEventMask();
            if (key.isValid()) {
                key.interestOps(eventMask);
            }
        }
    }

    private boolean queueInterestOps(final InterestOpEntry entry) {
        // validity checks
        Asserts.check(this.interestOpsQueueing, "Interest ops queueing not enabled");
        if (entry == null) {
            return false;
        }

        // add this operation to the interestOps() queue
        this.interestOpsQueue.add(entry);

        return true;
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
                    try {
                        sessionTimedOut(session);
                    } catch (final CancelledKeyException ex) {
                        session.close();
                        key.attach(null);
                    }
                }
            }
        }
    }

    /**
     * Closes out all I/O sessions maintained by this I/O reactor.
     */
    protected void closeSessions() {
        synchronized (this.sessions) {
            for (final IOSession session : this.sessions) {
                session.close();
            }
        }
    }

    /**
     * Closes out all new channels pending registration with the selector of
     * this I/O reactor.
     * @throws IOReactorException - not thrown currently
     */
    protected void closeNewChannels() throws IOReactorException {
        ChannelEntry entry;
        while ((entry = this.newChannels.poll()) != null) {
            final SessionRequestImpl sessionRequest = entry.getSessionRequest();
            if (sessionRequest != null) {
                sessionRequest.cancel();
            }
            final SocketChannel channel = entry.getChannel();
            try {
                channel.close();
            } catch (final IOException ignore) {
            }
        }
    }

    /**
     * Closes out all active channels registered with the selector of
     * this I/O reactor.
     * @throws IOReactorException - not thrown currently
     */
    protected void closeActiveChannels() throws IOReactorException {
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
     * Attempts graceful shutdown of this I/O reactor.
     */
    public void gracefulShutdown() {
        synchronized (this.statusMutex) {
            if (this.status != IOReactorStatus.ACTIVE) {
                // Already shutting down
                return;
            }
            this.status = IOReactorStatus.SHUTTING_DOWN;
        }
        this.selector.wakeup();
    }

    /**
     * Attempts force-shutdown of this I/O reactor.
     */
    public void hardShutdown() throws IOReactorException {
        synchronized (this.statusMutex) {
            if (this.status == IOReactorStatus.SHUT_DOWN) {
                // Already shut down
                return;
            }
            this.status = IOReactorStatus.SHUT_DOWN;
        }

        closeNewChannels();
        closeActiveChannels();
        processClosedSessions();
    }

    /**
     * Blocks for the given period of time in milliseconds awaiting
     * the completion of the reactor shutdown.
     *
     * @param timeout the maximum wait time.
     * @throws InterruptedException if interrupted.
     */
    public void awaitShutdown(final long timeout) throws InterruptedException {
        synchronized (this.statusMutex) {
            final long deadline = System.currentTimeMillis() + timeout;
            long remaining = timeout;
            while (this.status != IOReactorStatus.SHUT_DOWN) {
                this.statusMutex.wait(remaining);
                if (timeout > 0) {
                    remaining = deadline - System.currentTimeMillis();
                    if (remaining <= 0) {
                        break;
                    }
                }
            }
        }
    }

    @Override
    public void shutdown(final long gracePeriod) throws IOReactorException {
        if (this.status != IOReactorStatus.INACTIVE) {
            gracefulShutdown();
            try {
                awaitShutdown(gracePeriod);
            } catch (final InterruptedException ignore) {
            }
        }
        if (this.status != IOReactorStatus.SHUT_DOWN) {
            hardShutdown();
        }
    }

    @Override
    public void shutdown() throws IOReactorException {
        shutdown(1000);
    }

}
