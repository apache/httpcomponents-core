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

import java.io.InterruptedIOException;

import org.apache.http.util.concurrent.ThreadFactory;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.IOReactor;
import org.apache.http.nio.reactor.IOReactorException;

public abstract class AbstractMultiworkerIOReactor implements IOReactor {

    private final int workerCount;
    private final ThreadFactory threadFactory;
    private final BaseIOReactor[] ioReactors;
    private final Worker[] workers;
    private final Thread[] threads;

    private volatile boolean shutdown;
    
    private int currentWorker = 0;
    
    public AbstractMultiworkerIOReactor(
            long selectTimeout, 
            int workerCount, 
            final ThreadFactory threadFactory) throws IOReactorException {
        super();
        if (workerCount <= 0) {
            throw new IllegalArgumentException("Worker count may not be negative or zero");
        }
        this.workerCount = workerCount;
        if (threadFactory != null) {
            this.threadFactory = threadFactory;
        } else {
            this.threadFactory = new DefaultThreadFactory();
        }
        this.ioReactors = new BaseIOReactor[workerCount];
        for (int i = 0; i < this.ioReactors.length; i++) {
            this.ioReactors[i] = new BaseIOReactor(selectTimeout);
        }
        this.workers = new Worker[workerCount];
        this.threads = new Thread[workerCount];
    }

    protected void startWorkers(final IOEventDispatch eventDispatch) {
        for (int i = 0; i < this.workerCount; i++) {
            BaseIOReactor ioReactor = this.ioReactors[i];
            this.workers[i] = new Worker(ioReactor, eventDispatch);
            this.threads[i] = this.threadFactory.newThread(this.workers[i]);
        }
        for (int i = 0; i < this.workerCount; i++) {
            if (this.shutdown) {
                return;
            }
            this.threads[i].start();
        }
    }

    protected void stopWorkers(int millis) 
            throws InterruptedIOException, IOReactorException {
        if (this.shutdown) {
            return;
        }
        this.shutdown = true;
        for (int i = 0; i < this.workerCount; i++) {
            BaseIOReactor reactor = this.ioReactors[i];
            if (reactor != null) {
                reactor.shutdown();
            }
        }
        for (int i = 0; i < this.workerCount; i++) {
            try {
                Thread t = this.threads[i];
                if (t != null) {
                    t.join(millis);
                }
            } catch (InterruptedException ex) {
                throw new InterruptedIOException(ex.getMessage());
            }
        }
    }
    
    protected void verifyWorkers() 
            throws InterruptedIOException, IOReactorException {
        if (this.shutdown) {
            return;
        }
        for (int i = 0; i < this.workerCount; i++) {
            Worker worker = this.workers[i];
            Thread thread = this.threads[i];
            if (!thread.isAlive()) {
                if (worker.getReactorException() != null) {
                    throw worker.getReactorException();
                }
                if (worker.getInterruptedException() != null) {
                    throw worker.getInterruptedException();
                }
            }
        }
    }
    
    protected void addChannel(final ChannelEntry entry) {
        // Distribute new channels among the workers
        this.ioReactors[this.currentWorker++ % this.workerCount].addChannel(entry);
    }
        
    static class Worker implements Runnable {

        final BaseIOReactor ioReactor;
        final IOEventDispatch eventDispatch;
        
        private volatile IOReactorException reactorException;
        private volatile InterruptedIOException interruptedException;
        
        public Worker(final BaseIOReactor ioReactor, final IOEventDispatch eventDispatch) {
            super();
            this.ioReactor = ioReactor;
            this.eventDispatch = eventDispatch;
        }
        
        public void run() {
            try {
                this.ioReactor.execute(this.eventDispatch);
            } catch (InterruptedIOException ex) {
                this.interruptedException = ex;
            } catch (IOReactorException ex) {
                this.reactorException = ex;
            } finally {
                try {
                    this.ioReactor.shutdown();
                } catch (IOReactorException ex2) {
                    if (this.reactorException == null) {
                        this.reactorException = ex2;
                    }
                }
            }
        }
        
        public IOReactorException getReactorException() {
            return this.reactorException;
        }

        public InterruptedIOException getInterruptedException() {
            return this.interruptedException;
        }
        
    }

    static class DefaultThreadFactory implements ThreadFactory {

        private static int COUNT = 0;
        
        public Thread newThread(final Runnable r) {
            return new Thread(r, "I/O reactor worker thread " + (++COUNT));
        }
        
    }
    
}
