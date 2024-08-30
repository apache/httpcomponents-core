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

import java.util.concurrent.atomic.AtomicLong;

/**
 * Counts all method calls.
 *
 * @param <T> the future result type consumed by this callback.
 */
public class CountingFutureCallback<T> implements FutureCallback<T> {

    private final AtomicLong count = new AtomicLong();

    @Override
    public void cancelled() {
        count.incrementAndGet();
    }

    @Override
    public void completed(final T result) {
        count.incrementAndGet();
    }

    @Override
    public void failed(final Exception ex) {
        count.incrementAndGet();
    }

    /**
     * The atomic count as an AtomicLong.
     *
     * @return atomic count as an AtomicLong.
     */
    public AtomicLong getAtomicCount() {
        return count;
    }

    /**
     * The atomic count as a long.
     *
     * @return atomic count as a long.
     */
    public long getCount() {
        return count.longValue();
    }

}
