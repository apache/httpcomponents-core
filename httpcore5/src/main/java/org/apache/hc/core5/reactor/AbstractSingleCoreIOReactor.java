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
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.io.Closer;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;

abstract class AbstractSingleCoreIOReactor implements IOReactor {

    private final Callback<Exception> exceptionCallback;
    private final AtomicReference<IOReactorStatus> status;
    private final AtomicBoolean terminated;
    private final Object shutdownMutex;

    final Selector selector;

    AbstractSingleCoreIOReactor(final Callback<Exception> exceptionCallback) {
        super();
        this.exceptionCallback = exceptionCallback;
        this.shutdownMutex = new Object();
        this.status = new AtomicReference<>(IOReactorStatus.INACTIVE);
        this.terminated = new AtomicBoolean();
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

    void logException(final Exception ex) {
        if (exceptionCallback != null) {
            exceptionCallback.execute(ex);
        }
    }

    abstract void doExecute() throws IOException;

    abstract void doTerminate() throws IOException;

    public void execute() {
        if (this.status.compareAndSet(IOReactorStatus.INACTIVE, IOReactorStatus.ACTIVE)) {
            try {
                doExecute();
            } catch (final ClosedSelectorException ignore) {
                // ignore
            } catch (final Exception ex) {
                logException(ex);
            } finally {
                try {
                    doTerminate();
                } catch (final Exception ex) {
                    logException(ex);
                } finally {
                    close(CloseMode.IMMEDIATE);
                }
            }
        }
    }

    @Override
    public final void awaitShutdown(final TimeValue waitTime) throws InterruptedException {
        Args.notNull(waitTime, "Wait time");
        final long deadline = System.currentTimeMillis() + waitTime.toMilliseconds();
        long remaining = waitTime.toMilliseconds();
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
        }
        this.status.set(IOReactorStatus.SHUT_DOWN);
        if (terminated.compareAndSet(false, true)) {
            try {
                final Set<SelectionKey> keys = this.selector.keys();
                for (final SelectionKey key : keys) {
                    try {
                        Closer.close((Closeable) key.attachment());
                    } catch (final IOException ex) {
                        logException(ex);
                    }
                    key.channel().close();
                }
                selector.close();
            } catch (final Exception ex) {
                logException(ex);
            }
        }
        synchronized (this.shutdownMutex) {
            this.shutdownMutex.notifyAll();
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
