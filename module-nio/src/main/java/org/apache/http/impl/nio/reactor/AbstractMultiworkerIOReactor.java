/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
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
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadFactory;

import org.apache.http.nio.params.NIOReactorParams;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.IOReactor;
import org.apache.http.nio.reactor.IOReactorException;
import org.apache.http.nio.reactor.IOReactorExceptionHandler;
import org.apache.http.nio.reactor.IOReactorStatus;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

public abstract class AbstractMultiworkerIOReactor implements IOReactor {

    protected volatile IOReactorStatus status;
    
    protected final HttpParams params;
    protected final Selector selector;
    protected final long selectTimeout;

    private final int workerCount;
    private final ThreadFactory threadFactory;
    private final BaseIOReactor[] dispatchers;
    private final Worker[] workers;
    private final Thread[] threads;
    private final long gracePeriod;
    private final Object shutdownMutex;
    
    protected IOReactorExceptionHandler exceptionHandler;
    protected List<ExceptionEvent> auditLog;
    
    private int currentWorker = 0;

    public AbstractMultiworkerIOReactor(
            int workerCount, 
            final ThreadFactory threadFactory,
            final HttpParams params) throws IOReactorException {
        super();
        if (workerCount <= 0) {
            throw new IllegalArgumentException("Worker count may not be negative or zero");
        }
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        try {
            this.selector = Selector.open();
        } catch (IOException ex) {
            throw new IOReactorException("Failure opening selector", ex);
        }
        this.params = params;
        this.selectTimeout = NIOReactorParams.getSelectInterval(params);
        this.gracePeriod = NIOReactorParams.getGracePeriod(params);
        this.shutdownMutex = new Object();
        this.workerCount = workerCount;
        if (threadFactory != null) {
            this.threadFactory = threadFactory;
        } else {
            this.threadFactory = new DefaultThreadFactory();
        }
        this.dispatchers = new BaseIOReactor[workerCount];
        this.workers = new Worker[workerCount];
        this.threads = new Thread[workerCount];
        this.status = IOReactorStatus.INACTIVE;
    }

    public IOReactorStatus getStatus() {
        return this.status;
    }

    public synchronized List<ExceptionEvent> getAuditLog() {
        if (this.auditLog != null) {
            return new ArrayList<ExceptionEvent>(this.auditLog);
        } else {
            return null;
        }
    }

    protected synchronized void addExceptionEvent(final Throwable ex, Date timestamp) {
        if (ex == null) {
            return;
        }
        if (timestamp == null) {
            timestamp = new Date();
        }
        if (this.auditLog == null) {
            this.auditLog = new ArrayList<ExceptionEvent>();
        }
        this.auditLog.add(new ExceptionEvent(ex, timestamp));
    }
    
    protected void addExceptionEvent(final Throwable ex) {
        addExceptionEvent(ex, null);
    }

    public void setExceptionHandler(final IOReactorExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }
    
    protected abstract void processEvents(int count) throws IOReactorException;
    
    protected abstract void cancelRequests() throws IOReactorException;
    
    public void execute(
            final IOEventDispatch eventDispatch) throws InterruptedIOException, IOReactorException {
        if (eventDispatch == null) {
            throw new IllegalArgumentException("Event dispatcher may not be null");
        }

        this.status = IOReactorStatus.ACTIVE;
        
        // Start I/O dispatchers
        for (int i = 0; i < this.dispatchers.length; i++) {
            BaseIOReactor dispatcher = new BaseIOReactor(this.selectTimeout);
            dispatcher.setExceptionHandler(exceptionHandler);
            this.dispatchers[i] = dispatcher;
        }
        for (int i = 0; i < this.workerCount; i++) {
            BaseIOReactor dispatcher = this.dispatchers[i];
            this.workers[i] = new Worker(dispatcher, eventDispatch);
            this.threads[i] = this.threadFactory.newThread(this.workers[i]);
        }
        try {

            for (int i = 0; i < this.workerCount; i++) {
                if (this.status != IOReactorStatus.ACTIVE) {
                    return;
                }
                this.threads[i].start();
            }
            
            for (;;) {
                int readyCount;
                try {
                    readyCount = this.selector.select(this.selectTimeout);
                } catch (InterruptedIOException ex) {
                    throw ex;
                } catch (IOException ex) {
                    throw new IOReactorException("Unexpected selector failure", ex);
                }
                
                if (this.status.compareTo(IOReactorStatus.ACTIVE) > 0) {
                    break;
                }
                processEvents(readyCount);

                // Verify I/O dispatchers
                for (int i = 0; i < this.workerCount; i++) {
                    Worker worker = this.workers[i];
                    Thread thread = this.threads[i];
                    if (!thread.isAlive()) {
                        Exception ex = worker.getException();
                        if (ex != null) {
                            throw new IOReactorException(
                                    "I/O dispatch worker terminated abnormally", ex);
                        }
                    }
                }
            }

        } catch (ClosedSelectorException ex) {
            addExceptionEvent(ex);
        } catch (IOReactorException ex) {
            if (ex.getCause() != null) {
                addExceptionEvent(ex.getCause());
            }
            throw ex;
        } finally {
            doShutdown();
        }
    }

    protected void doShutdown() throws InterruptedIOException {
        if (this.status.compareTo(IOReactorStatus.SHUTTING_DOWN) >= 0) {
            return;
        }
        this.status = IOReactorStatus.SHUTTING_DOWN;
        try {
            cancelRequests();        
        } catch (IOReactorException ex) {
            if (ex.getCause() != null) {
                addExceptionEvent(ex.getCause());
            }
        }
        this.selector.wakeup();
        
        // Close out all channels
        if (this.selector.isOpen()) {
            Set<SelectionKey> keys = this.selector.keys();
            for (Iterator<SelectionKey> it = keys.iterator(); it.hasNext(); ) {
                try {
                    SelectionKey key = it.next();
                    Channel channel = key.channel();
                    if (channel != null) {
                        channel.close();
                    }
                } catch (IOException ex) {
                    addExceptionEvent(ex);
                }
            }
            // Stop dispatching I/O events
            try {
                this.selector.close();
            } catch (IOException ex) {
                addExceptionEvent(ex);
            }
        }

        // Attempt to shut down I/O dispatchers gracefully
        for (int i = 0; i < this.workerCount; i++) {
            BaseIOReactor dispatcher = this.dispatchers[i];
            dispatcher.gracefulShutdown();
        }

        try {
            // Force shut down I/O dispatchers if they fail to terminate
            // in time
            for (int i = 0; i < this.workerCount; i++) {
                BaseIOReactor dispatcher = this.dispatchers[i];
                if (dispatcher.getStatus() != IOReactorStatus.INACTIVE) {
                    dispatcher.awaitShutdown(this.gracePeriod);
                }
                if (dispatcher.getStatus() != IOReactorStatus.SHUT_DOWN) {
                    try {
                        dispatcher.hardShutdown();
                    } catch (IOReactorException ex) {
                        if (ex.getCause() != null) {
                            addExceptionEvent(ex.getCause());
                        }
                    }
                }
            }
            // Join worker threads
            for (int i = 0; i < this.workerCount; i++) {
                Thread t = this.threads[i];
                if (t != null) {
                    t.join(this.gracePeriod);
                }
            }
        } catch (InterruptedException ex) {
            throw new InterruptedIOException(ex.getMessage());
        } finally {
            synchronized (this.shutdownMutex) {
                this.status = IOReactorStatus.SHUT_DOWN;
                this.shutdownMutex.notifyAll();
            }
        }
    }

    protected void addChannel(final ChannelEntry entry) {
        // Distribute new channels among the workers
        int i = Math.abs(this.currentWorker++ % this.workerCount);
        this.dispatchers[i].addChannel(entry);
    }
    
    protected SelectionKey registerChannel(
            final SelectableChannel channel, int ops) throws ClosedChannelException {
        return channel.register(this.selector, ops);
    }

    protected void prepareSocket(final Socket socket) throws IOException {
        socket.setTcpNoDelay(HttpConnectionParams.getTcpNoDelay(this.params));
        socket.setSoTimeout(HttpConnectionParams.getSoTimeout(this.params));
        int linger = HttpConnectionParams.getLinger(this.params);
        if (linger >= 0) {
            socket.setSoLinger(linger > 0, linger);
        }
    }

    protected void awaitShutdown(long timeout) throws InterruptedException {
        synchronized (this.shutdownMutex) {
            long deadline = System.currentTimeMillis() + timeout;
            long remaining = timeout;
            while (this.status != IOReactorStatus.SHUT_DOWN) {
                this.shutdownMutex.wait(remaining);
                if (timeout > 0) {
                    remaining = deadline - System.currentTimeMillis();
                    if (remaining <= 0) {
                        break;
                    }
                }
            }
        }
    }

    public void shutdown() throws IOException {
        shutdown(2000);
    }

    public void shutdown(long waitMs) throws IOException {
        if (this.status != IOReactorStatus.ACTIVE) {
            return;
        }
        this.status = IOReactorStatus.SHUTDOWN_REQUEST;
        this.selector.wakeup();
        try {
            awaitShutdown(waitMs);
        } catch (InterruptedException ignore) {
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
        
        public void run() {
            try {
                this.dispatcher.execute(this.eventDispatch);
            } catch (Exception ex) {
                this.exception = ex;
            }
        }
        
        public Exception getException() {
            return this.exception;
        }

    }

    static class DefaultThreadFactory implements ThreadFactory {

        private static volatile int COUNT = 0;
        
        public Thread newThread(final Runnable r) {
            return new Thread(r, "I/O dispatcher " + (++COUNT));
        }
        
    }
    
}
