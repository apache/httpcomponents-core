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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.io.Closer;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;

class MultiCoreIOReactor implements IOReactor {

    private final IOReactor[] ioReactors;
    private final Thread[] threads;
    private final AtomicReference<IOReactorStatus> status;
    private final AtomicBoolean terminated;

    MultiCoreIOReactor(final IOReactor[] ioReactors, final Thread[] threads) {
        super();
        this.ioReactors = ioReactors.clone();
        this.threads = threads.clone();
        this.status = new AtomicReference<>(IOReactorStatus.INACTIVE);
        this.terminated = new AtomicBoolean();
    }

    @Override
    public IOReactorStatus getStatus() {
        return this.status.get();
    }

    /**
     * Activates all worker I/O reactors.
     * The I/O main reactor will start reacting to I/O events and triggering
     * notification methods. The worker I/O reactor in their turn will start
     * reacting to I/O events and dispatch I/O event notifications to the
     * {@link IOEventHandler} associated with the given I/O session.
     */
    public final void start() {
        if (this.status.compareAndSet(IOReactorStatus.INACTIVE, IOReactorStatus.ACTIVE)) {
            for (int i = 0; i < this.threads.length; i++) {
                this.threads[i].start();
            }
        }
    }

    @Override
    public final void initiateShutdown() {
        if (this.status.compareAndSet(IOReactorStatus.INACTIVE, IOReactorStatus.SHUT_DOWN) ||
                this.status.compareAndSet(IOReactorStatus.ACTIVE, IOReactorStatus.SHUTTING_DOWN)) {
            for (int i = 0; i < this.ioReactors.length; i++) {
                final IOReactor ioReactor = this.ioReactors[i];
                ioReactor.initiateShutdown();
            }
        }
    }

    @Override
    public final void awaitShutdown(final TimeValue waitTime) throws InterruptedException {
        Args.notNull(waitTime, "Wait time");
        final long deadline = System.currentTimeMillis() + waitTime.toMilliseconds();
        long remaining = waitTime.toMilliseconds();
        for (int i = 0; i < this.ioReactors.length; i++) {
            final IOReactor ioReactor = this.ioReactors[i];
            if (ioReactor.getStatus().compareTo(IOReactorStatus.SHUT_DOWN) < 0) {
                ioReactor.awaitShutdown(TimeValue.of(remaining, TimeUnit.MILLISECONDS));
                remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    return;
                }
            }
        }
        for (int i = 0; i < this.threads.length; i++) {
            final Thread thread = this.threads[i];
            thread.join(remaining);
            remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                return;
            }
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
        if (this.terminated.compareAndSet(false, true)) {
            for (int i = 0; i < this.ioReactors.length; i++) {
                Closer.close(this.ioReactors[i], CloseMode.IMMEDIATE);
            }
            for (int i = 0; i < this.threads.length; i++) {
                this.threads[i].interrupt();
            }
        }
    }

    @Override
    public final void close() {
        close(CloseMode.GRACEFUL);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [status=" + status + "]";
    }

}
