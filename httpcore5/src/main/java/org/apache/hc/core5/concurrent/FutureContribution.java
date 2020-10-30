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

/**
 * Convenience base class for {@link FutureCallback}s that contribute a result
 * of the operation to another {@link BasicFuture}.
 *
 * @param <T> the future result type of an asynchronous operation.
 * @since 5.1
 */
public abstract class FutureContribution<T> implements FutureCallback<T> {

    private final BasicFuture<?> future;

    public FutureContribution(final BasicFuture<?> future) {
        this.future = future;
    }

    @Override
    public final void failed(final Exception ex) {
        if (future != null) {
            future.failed(ex);
        }
    }

    @Override
    public final void cancelled() {
        if (future != null) {
            future.cancel();
        }
    }

}
