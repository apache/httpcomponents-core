/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 *
 *  Copyright 1999-2006 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.nio.impl.reactor;

import java.io.IOException;
import java.io.InterruptedIOException;

import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.IOReactor;

public abstract class AbstractMultiworkerIOReactor implements IOReactor {

    private final int workerCount;
    private final BaseIOReactor[] ioReactors;
    private final WorkerThread[] threads;
    
    private int currentWorker = 0;
    
    public AbstractMultiworkerIOReactor(int workerCount) throws IOException {
        super();
        if (workerCount <= 0) {
            throw new IllegalArgumentException("Worker count may not be negative or zero");
        }
        this.workerCount = workerCount;
        this.ioReactors = new BaseIOReactor[workerCount];
        this.threads = new WorkerThread[workerCount];
        for (int i = 0; i < this.ioReactors.length; i++) {
            this.ioReactors[i] = new BaseIOReactor();
        }
    }

    protected void startWorkers(final IOEventDispatch eventDispatch) {
        for (int i = 0; i < this.workerCount; i++) {
            BaseIOReactor ioReactor = this.ioReactors[i];
            this.threads[i] = new WorkerThread(ioReactor, eventDispatch);
        }
        for (int i = 0; i < this.workerCount; i++) {
            this.threads[i].start();
        }
    }

    protected void stopWorkers(int millis) throws IOException {
        for (int i = 0; i < this.workerCount; i++) {
            this.ioReactors[i].shutdown();
        }
        for (int i = 0; i < this.workerCount; i++) {
            try {
                this.threads[i].join(millis);
            } catch (InterruptedException ex) {
                throw new InterruptedIOException(ex.getMessage());
            }
        }
    }
    
    protected void verifyWorkers() throws IOException {
        for (int i = 0; i < this.workerCount; i++) {
            WorkerThread worker = this.threads[i];
            if (!worker.isAlive()) {
                IOException ex = worker.getException();
                if (ex != null) {
                    throw ex;
                }
            }
        }
    }
    
    protected void addChannel(final ChannelEntry entry) throws IOException {
        // Distribute new channels among the workers
        this.ioReactors[this.currentWorker++ % this.workerCount].addChannel(entry);
    }
        
    static class WorkerThread extends Thread {

        final BaseIOReactor ioReactor;
        final IOEventDispatch eventDispatch;
        
        private volatile IOException exception;
        
        public WorkerThread(final BaseIOReactor ioReactor, final IOEventDispatch eventDispatch) {
            super();
            this.ioReactor = ioReactor;
            this.eventDispatch = eventDispatch;
        }
        
        public void run() {
            try {
                this.ioReactor.execute(this.eventDispatch);
            } catch (IOException ex) {
                this.exception = ex;
            } finally {
                try {
                    this.ioReactor.shutdown();
                } catch (IOException ex2) {
                    this.exception = ex2;
                }
            }
        }
        
        public IOException getException() {
            return this.exception;
        }
        
    }

}
