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

package org.apache.http.nio.mockup;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;

import org.apache.http.util.concurrent.Executor;

/**
 * Simple {@link Executor} implementation that re-uses existing pooled threads 
 * if available or spawns a new one if not. The {@link Executor#execute(Runnable)} 
 * method of this implementation never blocks.
 * 
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 */
public class SimpleThreadPoolExecutor implements Executor {

    private static final int MAX_WAIT_TIME = 5000;
    
    private final ThreadPool threadPool;
    
    private volatile boolean shutdown;
    
    public SimpleThreadPoolExecutor() {
        super();
        this.threadPool = new ThreadPool();
    }

    public void execute(final Runnable task) {
        if (task == null) {
            return;
        }
        if (this.shutdown) {
            throw new IllegalStateException("Executor shut down");
        }
        WorkerThread worker = (WorkerThread) this.threadPool.lease();
        if (worker == null) {
            worker = new WorkerThread(this.threadPool);
            this.threadPool.add(worker);
            worker.assignTask(task);
            worker.start();
        } else {
            worker.assignTask(task);
        }
    }
    
    public void shutdown() {
        if (this.shutdown) {
            return;
        }
        this.shutdown = true;
        this.threadPool.interruptAll();
    }
    

    static class ThreadPool {
        
        private final Set pool;
        private final Set leased;
        private final LinkedList available;
        
        private volatile boolean shutdown;
        
        public ThreadPool() {
            super();
            this.pool = new HashSet();
            this.leased = new HashSet();
            this.available = new LinkedList();
        }
        
        public void add(final Thread t) {
            synchronized (this) {
                this.pool.add(t);
                this.leased.add(t);
            }
        }
        
        public void remove(final Thread t) {
            synchronized (this) {
                if (this.pool.contains(t)) {
                    this.pool.remove(t);
                    this.leased.remove(t);
                    this.available.remove(t);
                }
            }
        }
        
        public Thread lease() {
            if (this.shutdown) {
                throw new IllegalArgumentException("Thread pool shut down");
            }
            synchronized (this) {
                if (!this.available.isEmpty()) {
                    Thread t = (Thread) this.available.removeLast();
                    this.leased.add(t);
                    return t;
                } else {
                    return null;
                }
            }
        }
        
        public void release(final Thread t) {
            if (this.shutdown) {
                throw new IllegalArgumentException("Thread pool shut down");
            }
            synchronized (this) {
                if (this.leased.contains(t)) {
                    this.leased.remove(t);
                    this.available.addLast(t);
                }
            }
        }
        
        public void interruptAll() {
            if (this.shutdown) {
                return;
            }
            this.shutdown = true;
            synchronized (this) {
                this.available.clear();
                this.leased.clear();
                for (Iterator it = this.pool.iterator(); it.hasNext(); ) {
                    Thread t = (Thread) it.next();
                    t.interrupt();
                }
                this.pool.clear();
            }
        }
        
    }
    
    static class WorkerThread extends Thread {
        
        private static int COUNT;
        
        private final ThreadPool pool;
        private final Object mutex;
        
        private volatile Runnable task;
        private volatile long deadline;
        
        public WorkerThread(
                final ThreadPool pool) {
            super("worker-thread-" + (++COUNT));
            this.pool = pool;
            this.mutex = new Object();
        }
        
        public void assignTask(final Runnable task) {
            if (task == null) {
                return;
            }
            synchronized (this.mutex) {
                if (this.task != null) {
                    throw new IllegalArgumentException("Task already assigned");
                }
                this.task = task;
                this.deadline = System.currentTimeMillis() + MAX_WAIT_TIME;
                this.mutex.notifyAll();
            }
        }

        public void run() {
            try {
                synchronized (this.mutex) {
                    for (;;) {
                        if (this.task == null) {
                            this.mutex.wait(MAX_WAIT_TIME);
                        }
                        if (this.task == null && System.currentTimeMillis() > this.deadline) {
                            break;
                        }
                        if (this.task != null) {
                            this.task.run();
                            this.task = null;
                            this.pool.release(this);
                        }
                    }
                }
            } catch (InterruptedException ex) {
            } finally {
                System.err.println(this + ": terminate");
                this.pool.remove(this);
            }
        }
        
    }
    
}
