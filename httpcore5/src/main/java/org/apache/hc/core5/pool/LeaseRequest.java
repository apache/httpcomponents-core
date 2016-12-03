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
package org.apache.hc.core5.pool;

import java.io.Closeable;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.concurrent.BasicFuture;

@Contract(threading = ThreadingBehavior.SAFE_CONDITIONAL)
class LeaseRequest<T, C extends Closeable> {

    private final T route;
    private final Object state;
    private final long deadline;
    private final BasicFuture<PoolEntry<T, C>> future;
    private final AtomicBoolean completed;
    private volatile PoolEntry<T, C> result;
    private volatile Exception ex;

    /**
     * Constructor
     *
     * @param route route
     * @param state state
     * @param leaseTimeout timeout to wait in a request queue until kicked off
     * @param future future callback
     */
    public LeaseRequest(
            final T route,
            final Object state,
            final long leaseTimeout,
            final BasicFuture<PoolEntry<T, C>> future) {
        super();
        this.route = route;
        this.state = state;
        this.deadline = leaseTimeout > 0 ? System.currentTimeMillis() + leaseTimeout :
                Long.MAX_VALUE;
        this.future = future;
        this.completed = new AtomicBoolean(false);
    }

    public T getRoute() {
        return this.route;
    }

    public Object getState() {
        return this.state;
    }

    public long getDeadline() {
        return this.deadline;
    }

    public boolean isDone() {
        return this.completed.get();
    }

    public void failed(final Exception ex) {
        if (this.completed.compareAndSet(false, true)) {
            this.ex = ex;
        }
    }

    public void completed(final PoolEntry<T, C> result) {
        if (this.completed.compareAndSet(false, true)) {
            this.result = result;
        }
    }

    public BasicFuture<PoolEntry<T, C>> getFuture() {
        return this.future;
    }

    public PoolEntry<T, C> getResult() {
        return this.result;
    }

    public Exception getException() {
        return this.ex;
    }

    @Override
    public String toString() {
        final StringBuilder buffer = new StringBuilder();
        buffer.append("[");
        buffer.append(this.route);
        buffer.append("][");
        buffer.append(this.state);
        buffer.append("]");
        return buffer.toString();
    }

}
