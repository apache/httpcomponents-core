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
package org.apache.hc.core5.http2.impl.nio.bootstrap;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.concurrent.Cancellable;
import org.apache.hc.core5.concurrent.CancellableDependency;

final class CancellableExecution implements CancellableDependency {

    private final AtomicBoolean cancelled;
    private final AtomicReference<Cancellable> dependencyRef;

    CancellableExecution() {
        this.cancelled = new AtomicBoolean(false);
        this.dependencyRef = new AtomicReference<>(null);
    }

    @Override
    public void setDependency(final Cancellable cancellable) {
        dependencyRef.set(cancellable);
        if (cancelled.get()) {
            final Cancellable dependency = dependencyRef.getAndSet(null);
            if (dependency != null) {
                dependency.cancel();
            }
        }
    }

    @Override
    public boolean isCancelled() {
        return cancelled.get();
    }

    @Override
    public boolean cancel() {
        if (cancelled.compareAndSet(false, true)) {
            final Cancellable dependency = dependencyRef.getAndSet(null);
            if (dependency != null) {
                dependency.cancel();
            }
            return true;
        }
        return false;
    }

}
