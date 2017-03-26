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

package org.apache.hc.core5.http.impl.bootstrap;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.http.ExceptionListener;
import org.apache.hc.core5.reactor.AbstractMultiworkerIOReactor;
import org.apache.hc.core5.reactor.ExceptionEvent;
import org.apache.hc.core5.reactor.IOReactorStatus;

abstract class IOReactorExecutor<T extends AbstractMultiworkerIOReactor> implements AutoCloseable {

    enum Status { READY, RUNNING, TERMINATED }

    private final T ioReactor;
    private final ExceptionListener exceptionListener;
    private final ExecutorService executorService;
    private final AtomicReference<Status> status;

    IOReactorExecutor(
            final T ioReactor,
            final ExceptionListener exceptionListener,
            final ThreadFactory threadFactory) {
        super();
        this.ioReactor = ioReactor;
        this.exceptionListener = exceptionListener;
        this.executorService = Executors.newSingleThreadExecutor(threadFactory);
        this.status = new AtomicReference<>(Status.READY);
    }

    protected void execute() {
        if (status.compareAndSet(Status.READY, Status.RUNNING)) {
            executorService.execute(new Runnable() {

                @Override
                public void run() {
                    try {
                        ioReactor.execute();
                    } catch (final Exception ex) {
                        if (exceptionListener != null) {
                            exceptionListener.onError(ex);
                        }
                    }
                }
            });
        }
    }

    T reactor() {
        return ioReactor;
    }

    public IOReactorStatus getStatus() {
        return ioReactor.getStatus();
    }

    public List<ExceptionEvent> getAuditLog() {
        return ioReactor.getAuditLog();
    }

    public void awaitShutdown(final long deadline, final TimeUnit timeUnit) throws InterruptedException {
        ioReactor.awaitShutdown(deadline, timeUnit);
    }

    public void initiateShutdown() {
        ioReactor.initiateShutdown();
    }

    public void shutdown(final long graceTime, final TimeUnit timeUnit) {
        ioReactor.initiateShutdown();
        ioReactor.shutdown(graceTime, timeUnit);
    }

    @Override
    public void close() throws Exception {
        shutdown(5, TimeUnit.SECONDS);
    }

}
