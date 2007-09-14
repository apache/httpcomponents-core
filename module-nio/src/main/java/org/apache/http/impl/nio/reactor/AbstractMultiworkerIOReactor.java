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
import java.util.Iterator;
import java.util.Set;

import org.apache.http.util.concurrent.ThreadFactory;
import org.apache.http.nio.params.HttpNIOParams;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.IOReactor;
import org.apache.http.nio.reactor.IOReactorException;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

public abstract class AbstractMultiworkerIOReactor implements IOReactor {

    protected volatile int status;
    
    protected final HttpParams params;
    protected final Selector selector;
    protected final long selectTimeout;

    private final int workerCount;
    private final ThreadFactory threadFactory;
    private final BaseIOReactor[] dispatchers;
    private final Worker[] workers;
    private final Thread[] threads;
    
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
            throw new IllegalArgumentException("HTTP parameters may not be negative or zero");
        }
        try {
            this.selector = Selector.open();
        } catch (IOException ex) {
            throw new IOReactorException("Failure opening selector", ex);
        }
        this.params = params;
        this.selectTimeout = HttpNIOParams.getSelectInterval(params);
        this.workerCount = workerCount;
        if (threadFactory != null) {
            this.threadFactory = threadFactory;
        } else {
            this.threadFactory = new DefaultThreadFactory();
        }
        this.dispatchers = new BaseIOReactor[workerCount];
        for (int i = 0; i < this.dispatchers.length; i++) {
            this.dispatchers[i] = new BaseIOReactor(selectTimeout);
        }
        this.workers = new Worker[workerCount];
        this.threads = new Thread[workerCount];
        this.status = INACTIVE;
    }

    public int getStatus() {
        return this.status;
    }

    protected abstract void processEvents(int count) throws IOReactorException;
    
    public void execute(
            final IOEventDispatch eventDispatch) throws InterruptedIOException, IOReactorException {
        if (eventDispatch == null) {
            throw new IllegalArgumentException("Event dispatcher may not be null");
        }

        this.status = ACTIVE;
        
        // Start I/O dispatchers
        for (int i = 0; i < this.workerCount; i++) {
            BaseIOReactor dispatcher = this.dispatchers[i];
            this.workers[i] = new Worker(dispatcher, eventDispatch);
            this.threads[i] = this.threadFactory.newThread(this.workers[i]);
        }
        for (int i = 0; i < this.workerCount; i++) {
            if (this.status != ACTIVE) {
                return;
            }
            this.threads[i].start();
        }
        
        for (;;) {

            int readyCount;
            try {
                readyCount = this.selector.select(this.selectTimeout);
            } catch (ClosedSelectorException ex) {
                return;
            } catch (InterruptedIOException ex) {
                throw ex;
            } catch (IOException ex) {
                throw new IOReactorException("Unexpected selector failure", ex);
            }
            
            if (this.status == SHUT_DOWN) {
                break;
            }
            processEvents(readyCount);

            // Verify I/O dispatchers
            for (int i = 0; i < this.workerCount; i++) {
                Worker worker = this.workers[i];
                Thread thread = this.threads[i];
                if (!thread.isAlive()) {
                    Exception ex = worker.getException();
                    if (ex instanceof IOReactorException) {
                        throw (IOReactorException) ex;
                    } else if (ex instanceof InterruptedIOException) {
                        throw (InterruptedIOException) ex;
                    } else if (ex instanceof RuntimeException) {
                        throw (RuntimeException) ex;
                    } else {
                        throw new IOReactorException(ex.getMessage(), ex);
                    }
                }
            }
        }
    }

    public void shutdown(long gracePeriod) throws IOException {
        if (this.status > ACTIVE) {
            return;
        }
        this.status = SHUTTING_DOWN;        
        
        // Close out all channels
        Set keys = this.selector.keys();
        for (Iterator it = keys.iterator(); it.hasNext(); ) {
            try {
                SelectionKey key = (SelectionKey) it.next();
                Channel channel = key.channel();
                if (channel != null) {
                    channel.close();
                }
            } catch (IOException ignore) {
            }
        }
        // Stop dispatching I/O events
        this.selector.close();

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
                if (dispatcher.getStatus() != INACTIVE) {
                    dispatcher.awaitShutdown(gracePeriod);
                }
                if (dispatcher.getStatus() != SHUT_DOWN) {
                    dispatcher.hardShutdown();
                }
            }
            // Join worker threads
            for (int i = 0; i < this.workerCount; i++) {
                Thread t = this.threads[i];
                if (t != null) {
                    t.join(gracePeriod);
                }
            }
        } catch (InterruptedException ex) {
            throw new InterruptedIOException(ex.getMessage());
        } finally {
            this.status = SHUT_DOWN;        
        }
    }

    public void shutdown() throws IOException {
        shutdown(500);
    }
    
    protected void addChannel(final ChannelEntry entry) {
        // Distribute new channels among the workers
        this.dispatchers[this.currentWorker++ % this.workerCount].addChannel(entry);
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
            } catch (InterruptedIOException ex) {
                this.exception = ex;
            } catch (IOReactorException ex) {
                this.exception = ex;
            } catch (RuntimeException ex) {
                this.exception = ex;
            } finally {
                try {
                    if (this.dispatcher.getStatus() != SHUT_DOWN) {
                        this.dispatcher.closeChannels();
                    }
                } catch (IOReactorException ex2) {
                    if (this.exception == null) {
                        this.exception = ex2;
                    }
                }
            }
        }
        
        public Exception getException() {
            return this.exception;
        }

    }

    static class DefaultThreadFactory implements ThreadFactory {

        private static int COUNT = 0;
        
        public Thread newThread(final Runnable r) {
            return new Thread(r, "I/O dispatcher " + (++COUNT));
        }
        
    }
    
}
