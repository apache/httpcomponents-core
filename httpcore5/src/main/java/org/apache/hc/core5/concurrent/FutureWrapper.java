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
package org.apache.hc.core5.concurrent;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.hc.core5.util.Args;

/**
 * Future wrapper that can cancel an external operation by calling
 * {@link Cancellable#cancel()} if cancelled itself.
 *
 * @param <T> the future result type of an asynchronous operation.
 * @since 5.0
 */
public final class FutureWrapper<T> implements Future<T> {

    private final Future<T> future;
    private final Cancellable cancellable;

    public FutureWrapper(final Future<T> future, final Cancellable cancellable) {
        super();
        this.future = Args.notNull(future, "Future");
        this.cancellable = cancellable;
    }

    @Override
    public boolean cancel(final boolean mayInterruptIfRunning) {
        boolean cancelled;
        try {
            if (cancellable != null) {
                cancellable.cancel();
            }
        } finally {
            cancelled = future.cancel(mayInterruptIfRunning);
        }
        return cancelled;
    }
    @Override
    public boolean isCancelled() {
        return future.isCancelled();
    }

    @Override
    public boolean isDone() {
        return future.isDone();
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        return future.get();
    }

    @Override
    public T get(final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return future.get(timeout, unit);
    }

    @Override
    public String toString() {
        return future.toString();
    }

}
