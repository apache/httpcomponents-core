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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Counts down all method calls for a {@link CountDownLatch}.
 *
 * @param <T> the future result type consumed by this callback.
 */
public class CountDownLatchFutureCallback<T> implements FutureCallback<T> {

    private final CountDownLatch countDownLatch;

    /**
     * Constructs a new instance.
     *
     * @param count the number of times {@link CountDownLatch#countDown()} must be invoked before threads can pass through {@link CountDownLatch#await()}
     * @throws IllegalArgumentException if {@code count} is negative.
     */
    public CountDownLatchFutureCallback(final int count) {
        this.countDownLatch = new CountDownLatch(count);
    }

    /**
     * Delegates to {@link CountDownLatch#await()}.
     *
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    public void await() throws InterruptedException {
        countDownLatch.await();
    }

    /**
     * Delegates to {@link CountDownLatch#await(long, TimeUnit)}.
     *
     * @param timeout the maximum time to wait.
     * @param unit    the time unit of the {@code timeout} argument.
     * @return {@code true} if the count reached zero and {@code false} if the waiting time elapsed before the count reached zero.
     * @throws InterruptedException if the current thread is interrupted while waiting.
     */
    public boolean await(final long timeout, final TimeUnit unit) throws InterruptedException {
        return countDownLatch.await(timeout, unit);
    }

    @Override
    public void cancelled() {
        countDownLatch.countDown();
    }

    @Override
    public void completed(final T result) {
        countDownLatch.countDown();
    }

    /**
     * Delegates to {@link CountDownLatch#countDown()}.
     */
    public void countDown() {
        countDownLatch.countDown();
    }

    @Override
    public void failed(final Exception ex) {
        countDownLatch.countDown();
    }

}
