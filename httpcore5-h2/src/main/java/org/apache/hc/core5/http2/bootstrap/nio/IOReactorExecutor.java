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

package org.apache.hc.core5.http2.bootstrap.nio;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.http.ExceptionListener;
import org.apache.hc.core5.http2.nio.command.ShutdownCommand;
import org.apache.hc.core5.http2.nio.command.ShutdownType;
import org.apache.hc.core5.reactor.AbstractMultiworkerIOReactor;
import org.apache.hc.core5.reactor.ExceptionEvent;
import org.apache.hc.core5.reactor.IOReactorStatus;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.IOSessionCallback;
import org.apache.hc.core5.util.Asserts;

class IOReactorExecutor<T extends AbstractMultiworkerIOReactor> {

    private final ExceptionListener exceptionListener;
    private final ExecutorService executorService;

    private volatile T ioReactor;

    public IOReactorExecutor(final ExceptionListener exceptionListener) {
        super();
        this.exceptionListener = exceptionListener;
        this.executorService = Executors.newSingleThreadExecutor();
    }

    void startExecution(final T reactor) {
        Asserts.check(ioReactor == null, "I/O reactor has already been started");
        ioReactor = reactor;
        executorService.execute(new Runnable() {

            @Override
            public void run() {
                try {
                    ioReactor.execute();
                } catch (Exception ex) {
                    if (exceptionListener != null) {
                        exceptionListener.onError(ex);
                    }
                }
            }
        });
    }

    private T ensureRunning() {
        Asserts.check(ioReactor != null, "I/O reactor has not been started");
        return ioReactor;
    }

    T reactor() {
        return ensureRunning();
    }

    public IOReactorStatus getStatus() {
        return this.ioReactor.getStatus();
    }

    public List<ExceptionEvent> getAuditLog() {
        return this.ioReactor.getAuditLog();
    }

    public void awaitShutdown(final long deadline, final TimeUnit timeUnit) throws InterruptedException {
        ensureRunning();
        ioReactor.awaitShutdown(deadline, timeUnit);
    }

    public void initiateShutdown() throws IOException {
        ensureRunning();
        ioReactor.initiateShutdown();
        ioReactor.enumSessions(new IOSessionCallback() {

            @Override
            public void execute(final IOSession session) throws IOException {
                session.getCommandQueue().addFirst(new ShutdownCommand(ShutdownType.GRACEFUL));
                session.setEvent(SelectionKey.OP_WRITE);
            }

        });
    }

    public void shutdown(final long graceTime, final TimeUnit timeUnit) throws IOException {
        ensureRunning();
        initiateShutdown();
        ioReactor.shutdown(graceTime, timeUnit);
    }

}
