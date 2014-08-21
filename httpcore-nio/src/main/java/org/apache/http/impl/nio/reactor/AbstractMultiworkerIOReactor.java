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
import java.net.Socket;
import java.nio.channels.Channel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.http.annotation.ThreadSafe;
import org.apache.http.nio.params.NIOReactorPNames;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.IOReactor;
import org.apache.http.nio.reactor.IOReactorException;
import org.apache.http.nio.reactor.IOReactorExceptionHandler;
import org.apache.http.nio.reactor.IOReactorStatus;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.util.Args;
import org.apache.http.util.Asserts;

/**
 * Generic implementation of {@link IOReactor} that can run multiple
 * {@link BaseIOReactor} instance in separate worker threads and distribute
 * newly created I/O session equally across those I/O reactors for a more
 * optimal resource utilization and a better I/O performance. Usually it is
 * recommended to have one worker I/O reactor per physical CPU core.
 * <p>
 * <strong>Important note about exception handling</strong>
 * <p>
 * Protocol specific exceptions as well as those I/O exceptions thrown in the
 * course of interaction with the session's channel are to be expected are to be
 * dealt with by specific protocol handlers. These exceptions may result in
 * termination of an individual session but should not affect the I/O reactor
 * and all other active sessions. There are situations, however, when the I/O
 * reactor itself encounters an internal problem such as an I/O exception in
 * the underlying NIO classes or an unhandled runtime exception. Those types of
 * exceptions are usually fatal and will cause the I/O reactor to shut down
 * automatically.
 * <p>
 * There is a possibility to override this behavior and prevent I/O reactors
 * from shutting down automatically in case of a runtime exception or an I/O
 * exception in internal classes. This can be accomplished by providing a custom
 * implementation of the {@link IOReactorExceptionHandler} interface.
 * <p>
 * If an I/O reactor is unable to automatically recover from an I/O or a runtime
 * exception it will enter the shutdown mode. First off, it cancel all pending
 * new session requests. Then it will attempt to close all active I/O sessions
 * gracefully giving them some time to flush pending output data and terminate
 * cleanly. Lastly, it will forcibly shut down those I/O sessions that still
 * remain active after the grace period. This is a fairly complex process, where
 * many things can fail at the same time and many different exceptions can be
 * thrown in the course of the shutdown process. The I/O reactor will record all
 * exceptions thrown during the shutdown process, including the original one
 * that actually caused the shutdown in the first place, in an audit log. One
 * can obtain the audit log using {@link #getAuditLog()}, examine exceptions
 * thrown by the I/O reactor prior and in the course of the reactor shutdown
 * and decide whether it is safe to restart the I/O reactor.
 *
 * @since 4.0
 */
@SuppressWarnings("deprecation")
@ThreadSafe // public methods only
public abstract class AbstractMultiworkerIOReactor implements IOReactor {

    protected volatile IOReactorStatus status;

    /**
     * @deprecated (4.2)
     */
    @Deprecated
    protected final HttpParams params;
    protected final IOReactorConfig config;
    protected final Selector selector;
    protected final long selectTimeout;
    protected final boolean interestOpsQueueing;

    private final int workerCount;
    private final ThreadFactory threadFactory;
    private final BaseIOReactor[] dispatchers;
    private final Worker[] workers;
    private final Thread[] threads;
    private final Object statusLock;

    //TODO: make final
    protected IOReactorExceptionHandler exceptionHandler;
    protected List<ExceptionEvent> auditLog;

    private int currentWorker = 0;

    /**
     * Creates an instance of AbstractMultiworkerIOReactor with the given configuration.
     *
     * @param config I/O reactor configuration.
     * @param threadFactory the factory to create threads.
     *   Can be {@code null}.
     * @throws IOReactorException in case if a non-recoverable I/O error.
     *
     * @since 4.2
     */
    public AbstractMultiworkerIOReactor(
            final IOReactorConfig config,
            final ThreadFactory threadFactory) throws IOReactorException {
        super();
        this.config = config != null ? config : IOReactorConfig.DEFAULT;
        this.params = new BasicHttpParams();
        try {
            this.selector = Selector.open();
        } catch (final IOException ex) {
            throw new IOReactorException("Failure opening selector", ex);
        }
        this.selectTimeout = this.config.getSelectInterval();
        this.interestOpsQueueing = this.config.isInterestOpQueued();
        this.statusLock = new Object();
        if (threadFactory != null) {
            this.threadFactory = threadFactory;
        } else {
            this.threadFactory = new DefaultThreadFactory();
        }
        this.auditLog = new ArrayList<ExceptionEvent>();
        this.workerCount = this.config.getIoThreadCount();
        this.dispatchers = new BaseIOReactor[workerCount];
        this.workers = new Worker[workerCount];
        this.threads = new Thread[workerCount];
        this.status = IOReactorStatus.INACTIVE;
    }

    /**
     * Creates an instance of AbstractMultiworkerIOReactor with default configuration.
     *
     * @throws IOReactorException in case if a non-recoverable I/O error.
     *
     * @since 4.2
     */
    public AbstractMultiworkerIOReactor() throws IOReactorException {
        this(null, null);
    }

    @Deprecated
    static IOReactorConfig convert(final int workerCount, final HttpParams params) {
        Args.notNull(params, "HTTP parameters");
        return IOReactorConfig.custom()
            .setSelectInterval(params.getLongParameter(NIOReactorPNames.SELECT_INTERVAL, 1000))
            .setShutdownGracePeriod(params.getLongParameter(NIOReactorPNames.GRACE_PERIOD, 500))
            .setInterestOpQueued(params.getBooleanParameter(NIOReactorPNames.SELECT_INTERVAL, false))
            .setIoThreadCount(workerCount)
            .setSoTimeout(params.getIntParameter(CoreConnectionPNames.SO_TIMEOUT, 0))
            .setConnectTimeout(params.getIntParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, 0))
            .setSoTimeout(params.getIntParameter(CoreConnectionPNames.SO_TIMEOUT, 0))
            .setSoReuseAddress(params.getBooleanParameter(CoreConnectionPNames.SO_REUSEADDR, false))
            .setSoKeepAlive(params.getBooleanParameter(CoreConnectionPNames.SO_KEEPALIVE, false))
            .setSoLinger(params.getIntParameter(CoreConnectionPNames.SO_LINGER, -1))
            .setTcpNoDelay(params.getBooleanParameter(CoreConnectionPNames.TCP_NODELAY, true))
            .build();
    }

    /**
     * Creates an instance of AbstractMultiworkerIOReactor.
     *
     * @param workerCount number of worker I/O reactors.
     * @param threadFactory the factory to create threads.
     *   Can be {@code null}.
     * @param params HTTP parameters.
     * @throws IOReactorException in case if a non-recoverable I/O error.
     *
     * @deprecated (4.2) use {@link AbstractMultiworkerIOReactor#AbstractMultiworkerIOReactor(IOReactorConfig, ThreadFactory)}
     */
    @Deprecated
    public AbstractMultiworkerIOReactor(
            final int workerCount,
            final ThreadFactory threadFactory,
            final HttpParams params) throws IOReactorException {
        this(convert(workerCount, params), threadFactory);
    }

    @Override
    public IOReactorStatus getStatus() {
        return this.status;
    }

    /**
     * Returns the audit log containing exceptions thrown by the I/O reactor
     * prior and in the course of the reactor shutdown.
     *
     * @return audit log.
     */
    public List<ExceptionEvent> getAuditLog() {
        synchronized (this.auditLog) {
            return new ArrayList<ExceptionEvent>(this.auditLog);
        }
    }

    /**
     * Adds the given {@link Throwable} object with the given time stamp
     * to the audit log.
     *
     * @param ex the exception thrown by the I/O reactor.
     * @param timestamp the time stamp of the exception. Can be
     * {@code null} in which case the current date / time will be used.
     */
    protected synchronized void addExceptionEvent(final Throwable ex, final Date timestamp) {
        if (ex == null) {
            return;
        }
        synchronized (this.auditLog) {
            this.auditLog.add(new ExceptionEvent(ex, timestamp != null ? timestamp : new Date()));
        }
    }

    /**
     * Adds the given {@link Throwable} object to the audit log.
     *
     * @param ex the exception thrown by the I/O reactor.
     */
    protected void addExceptionEvent(final Throwable ex) {
        addExceptionEvent(ex, null);
    }

    /**
     * Sets exception handler for this I/O reactor.
     *
     * @param exceptionHandler the exception handler.
     */
    public void setExceptionHandler(final IOReactorExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }

    /**
     * Triggered to process I/O events registered by the main {@link Selector}.
     * <p>
     * Super-classes can implement this method to react to the event.
     *
     * @param count event count.
     * @throws IOReactorException in case if a non-recoverable I/O error.
     */
    protected abstract void processEvents(int count) throws IOReactorException;

    /**
     * Triggered to cancel pending session requests.
     * <p>
     * Super-classes can implement this method to react to the event.
     *
     * @throws IOReactorException in case if a non-recoverable I/O error.
     */
    protected abstract void cancelRequests() throws IOReactorException;

    /**
     * Activates the main I/O reactor as well as all worker I/O reactors.
     * The I/O main reactor will start reacting to I/O events and triggering
     * notification methods. The worker I/O reactor in their turn will start
     * reacting to I/O events and dispatch I/O event notifications to the given
     * {@link IOEventDispatch} interface.
     * <p>
     * This method will enter the infinite I/O select loop on
     * the {@link Selector} instance associated with this I/O reactor and used
     * to manage creation of new I/O channels. Once a new I/O channel has been
     * created the processing of I/O events on that channel will be delegated
     * to one of the worker I/O reactors.
     * <p>
     * The method will remain blocked unto the I/O reactor is shut down or the
     * execution thread is interrupted.
     *
     * @see #processEvents(int)
     * @see #cancelRequests()
     *
     * @throws InterruptedIOException if the dispatch thread is interrupted.
     * @throws IOReactorException in case if a non-recoverable I/O error.
     */
    @Override
    public void execute(
            final IOEventDispatch eventDispatch) throws InterruptedIOException, IOReactorException {
        Args.notNull(eventDispatch, "Event dispatcher");
        synchronized (this.statusLock) {
            if (this.status.compareTo(IOReactorStatus.SHUTDOWN_REQUEST) >= 0) {
                this.status = IOReactorStatus.SHUT_DOWN;
                this.statusLock.notifyAll();
                return;
            }
            Asserts.check(this.status.compareTo(IOReactorStatus.INACTIVE) == 0,
                    "Illegal state %s", this.status);
            this.status = IOReactorStatus.ACTIVE;
            // Start I/O dispatchers
            for (int i = 0; i < this.dispatchers.length; i++) {
                final BaseIOReactor dispatcher = new BaseIOReactor(this.selectTimeout, this.interestOpsQueueing);
                dispatcher.setExceptionHandler(exceptionHandler);
                this.dispatchers[i] = dispatcher;
            }
            for (int i = 0; i < this.workerCount; i++) {
                final BaseIOReactor dispatcher = this.dispatchers[i];
                this.workers[i] = new Worker(dispatcher, eventDispatch);
                this.threads[i] = this.threadFactory.newThread(this.workers[i]);
            }
        }
        try {

            for (int i = 0; i < this.workerCount; i++) {
                if (this.status != IOReactorStatus.ACTIVE) {
                    return;
                }
                this.threads[i].start();
            }

            for (;;) {
                final int readyCount;
                try {
                    readyCount = this.selector.select(this.selectTimeout);
                } catch (final InterruptedIOException ex) {
                    throw ex;
                } catch (final IOException ex) {
                    throw new IOReactorException("Unexpected selector failure", ex);
                }

                if (this.status.compareTo(IOReactorStatus.ACTIVE) == 0) {
                    processEvents(readyCount);
                }

                // Verify I/O dispatchers
                for (int i = 0; i < this.workerCount; i++) {
                    final Worker worker = this.workers[i];
                    final Exception ex = worker.getException();
                    if (ex != null) {
                        throw new IOReactorException(
                                "I/O dispatch worker terminated abnormally", ex);
                    }
                }

                if (this.status.compareTo(IOReactorStatus.ACTIVE) > 0) {
                    break;
                }
            }

        } catch (final ClosedSelectorException ex) {
            addExceptionEvent(ex);
        } catch (final IOReactorException ex) {
            if (ex.getCause() != null) {
                addExceptionEvent(ex.getCause());
            }
            throw ex;
        } finally {
            doShutdown();
            synchronized (this.statusLock) {
                this.status = IOReactorStatus.SHUT_DOWN;
                this.statusLock.notifyAll();
            }
        }
    }

    /**
     * Activates the shutdown sequence for this reactor. This method will cancel
     * all pending session requests, close out all active I/O channels,
     * make an attempt to terminate all worker I/O reactors gracefully,
     * and finally force-terminate those I/O reactors that failed to
     * terminate after the specified grace period.
     *
     * @throws InterruptedIOException if the shutdown sequence has been
     *   interrupted.
     */
    protected void doShutdown() throws InterruptedIOException {
        synchronized (this.statusLock) {
            if (this.status.compareTo(IOReactorStatus.SHUTTING_DOWN) >= 0) {
                return;
            }
            this.status = IOReactorStatus.SHUTTING_DOWN;
        }
        try {
            cancelRequests();
        } catch (final IOReactorException ex) {
            if (ex.getCause() != null) {
                addExceptionEvent(ex.getCause());
            }
        }
        this.selector.wakeup();

        // Close out all channels
        if (this.selector.isOpen()) {
            for (final SelectionKey key : this.selector.keys()) {
                try {
                    final Channel channel = key.channel();
                    if (channel != null) {
                        channel.close();
                    }
                } catch (final IOException ex) {
                    addExceptionEvent(ex);
                }
            }
            // Stop dispatching I/O events
            try {
                this.selector.close();
            } catch (final IOException ex) {
                addExceptionEvent(ex);
            }
        }

        // Attempt to shut down I/O dispatchers gracefully
        for (int i = 0; i < this.workerCount; i++) {
            final BaseIOReactor dispatcher = this.dispatchers[i];
            dispatcher.gracefulShutdown();
        }

        final long gracePeriod = this.config.getShutdownGracePeriod();

        try {
            // Force shut down I/O dispatchers if they fail to terminate
            // in time
            for (int i = 0; i < this.workerCount; i++) {
                final BaseIOReactor dispatcher = this.dispatchers[i];
                if (dispatcher.getStatus() != IOReactorStatus.INACTIVE) {
                    dispatcher.awaitShutdown(gracePeriod);
                }
                if (dispatcher.getStatus() != IOReactorStatus.SHUT_DOWN) {
                    try {
                        dispatcher.hardShutdown();
                    } catch (final IOReactorException ex) {
                        if (ex.getCause() != null) {
                            addExceptionEvent(ex.getCause());
                        }
                    }
                }
            }
            // Join worker threads
            for (int i = 0; i < this.workerCount; i++) {
                final Thread t = this.threads[i];
                if (t != null) {
                    t.join(gracePeriod);
                }
            }
        } catch (final InterruptedException ex) {
            throw new InterruptedIOException(ex.getMessage());
        }
    }

    /**
     * Assigns the given channel entry to one of the worker I/O reactors.
     *
     * @param entry the channel entry.
     */
    protected void addChannel(final ChannelEntry entry) {
        // Distribute new channels among the workers
        final int i = Math.abs(this.currentWorker++ % this.workerCount);
        this.dispatchers[i].addChannel(entry);
    }

    /**
     * Registers the given channel with the main {@link Selector}.
     *
     * @param channel the channel.
     * @param ops interest ops.
     * @return  selection key.
     * @throws ClosedChannelException if the channel has been already closed.
     */
    protected SelectionKey registerChannel(
            final SelectableChannel channel, final int ops) throws ClosedChannelException {
        return channel.register(this.selector, ops);
    }

    /**
     * Prepares the given {@link Socket} by resetting some of its properties.
     *
     * @param socket the socket
     * @throws IOException in case of an I/O error.
     */
    protected void prepareSocket(final Socket socket) throws IOException {
        socket.setTcpNoDelay(this.config.isTcpNoDelay());
        socket.setKeepAlive(this.config.isSoKeepalive());
        if (this.config.getSoTimeout() > 0) {
            socket.setSoTimeout(this.config.getSoTimeout());
        }
        if (this.config.getSndBufSize() > 0) {
            socket.setSendBufferSize(this.config.getSndBufSize());
        }
        if (this.config.getRcvBufSize() > 0) {
            socket.setReceiveBufferSize(this.config.getRcvBufSize());
        }
        final int linger = this.config.getSoLinger();
        if (linger >= 0) {
            socket.setSoLinger(true, linger);
        }
    }

    /**
     * Blocks for the given period of time in milliseconds awaiting
     * the completion of the reactor shutdown. If the value of
     * {@code timeout} is set to {@code 0} this method blocks
     * indefinitely.
     *
     * @param timeout the maximum wait time.
     * @throws InterruptedException if interrupted.
     */
    protected void awaitShutdown(final long timeout) throws InterruptedException {
        synchronized (this.statusLock) {
            final long deadline = System.currentTimeMillis() + timeout;
            long remaining = timeout;
            while (this.status != IOReactorStatus.SHUT_DOWN) {
                this.statusLock.wait(remaining);
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
    public void shutdown() throws IOException {
        shutdown(2000);
    }

    @Override
    public void shutdown(final long waitMs) throws IOException {
        synchronized (this.statusLock) {
            if (this.status.compareTo(IOReactorStatus.ACTIVE) > 0) {
                return;
            }
            if (this.status.compareTo(IOReactorStatus.INACTIVE) == 0) {
                this.status = IOReactorStatus.SHUT_DOWN;
                cancelRequests();
                this.selector.close();
                return;
            }
            this.status = IOReactorStatus.SHUTDOWN_REQUEST;
        }
        this.selector.wakeup();
        try {
            awaitShutdown(waitMs);
        } catch (final InterruptedException ignore) {
        }
    }

    static void closeChannel(final Channel channel) {
        try {
            channel.close();
        } catch (final IOException ignore) {
        }
    }

    static class Worker implements Runnable {

        final BaseIOReactor dispatcher;
        final IOEventDispatch eventDispatch;

        private volatile Exception exception;

        public Worker(final BaseIOReactor dispatcher, final IOEventDispatch eventDispatch) {
            super();
            this.dispatcher = dispatcher;
            this.eventDispatch = eventDispatch;
        }

        @Override
        public void run() {
            try {
                this.dispatcher.execute(this.eventDispatch);
            } catch (final Exception ex) {
                this.exception = ex;
            }
        }

        public Exception getException() {
            return this.exception;
        }

    }

    static class DefaultThreadFactory implements ThreadFactory {

        private final static AtomicLong COUNT = new AtomicLong(1);

        @Override
        public Thread newThread(final Runnable r) {
            return new Thread(r, "I/O dispatcher " + COUNT.getAndIncrement());
        }

    }

}
