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
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Date;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;

abstract class AbstractSingleCoreIOReactor implements IOReactor {

    private final Queue<ExceptionEvent> auditLog;
    private final AtomicReference<IOReactorStatus> status;
    private final Object shutdownMutex;

    final Selector selector;

    AbstractSingleCoreIOReactor(final Queue<ExceptionEvent> auditLog) {
        super();
        this.auditLog = auditLog;
        this.shutdownMutex = new Object();
        this.status = new AtomicReference<>(IOReactorStatus.INACTIVE);
        try {
            this.selector = Selector.open();
        } catch (final IOException ex) {
            throw new IllegalStateException("Unexpected failure opening I/O selector", ex);
        }
    }

    @Override
    public final IOReactorStatus getStatus() {
        return this.status.get();
    }

    void addExceptionEvent(final Throwable ex) {
        this.auditLog.add(new ExceptionEvent(ex, new Date()));
    }

    abstract void doExecute() throws IOException;

    abstract void doTerminate() throws IOException;

    public void execute() {
        if (this.status.compareAndSet(IOReactorStatus.INACTIVE, IOReactorStatus.ACTIVE)) {
            try {
                doExecute();
            } catch (final ClosedSelectorException ignore) {
            } catch (final Exception ex) {
                addExceptionEvent(ex);
            } finally {
                try {
                    doTerminate();
                    final Set<SelectionKey> keys = this.selector.keys();
                    for (final SelectionKey key : keys) {
                        final Closeable closeable = (Closeable) key.attachment();
                        if (closeable != null) {
                            try {
                                closeable.close();
                            } catch (final IOException ex) {
                                addExceptionEvent(ex);
                            }
                        }
                        key.channel().close();
                    }
                    try {
                        this.selector.close();
                    } catch (final IOException ex) {
                        addExceptionEvent(ex);
                    }
                } catch (final Exception ex) {
                    addExceptionEvent(ex);
                } finally {
                    this.status.set(IOReactorStatus.SHUT_DOWN);
                    synchronized (this.shutdownMutex) {
                        this.shutdownMutex.notifyAll();
                    }
                }
            }
        }
    }

    @Override
    public final void awaitShutdown(final TimeValue waitTime) throws InterruptedException {
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
    public final void initiateShutdown() {
        if (this.status.compareAndSet(IOReactorStatus.INACTIVE, IOReactorStatus.SHUT_DOWN)) {
            synchronized (this.shutdownMutex) {
                this.shutdownMutex.notifyAll();
            }
        } else if (this.status.compareAndSet(IOReactorStatus.ACTIVE, IOReactorStatus.SHUTTING_DOWN)) {
            this.selector.wakeup();
        }
    }

    @Override
    public final void close(final CloseMode closeMode) {
        if (closeMode == CloseMode.GRACEFUL) {
            initiateShutdown();
            try {
                awaitShutdown(TimeValue.ofSeconds(5));
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } else {
            final IOReactorStatus previousStatus = this.status.getAndSet(IOReactorStatus.SHUT_DOWN);
            if (previousStatus.compareTo(IOReactorStatus.ACTIVE) == 0) {
                this.selector.wakeup();
            }
            synchronized (this.shutdownMutex) {
                this.shutdownMutex.notifyAll();
            }
        }
    }

    @Override
    public final void close() {
        close(CloseMode.GRACEFUL);
    }

    @Override
    public String toString() {
        return super.toString() + " [status=" + status + "]";
    }

}
