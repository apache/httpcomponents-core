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

import java.util.concurrent.atomic.AtomicMarkableReference;

import org.apache.hc.core5.util.Args;

/**
 * {@link Cancellable} that has a dependency on another {@link Cancellable}
 * process or operation. Dependent process or operation will get cancelled
 * if this {@link Cancellable} itself is cancelled.
 *
 * @since 5.0
 */
public final class ComplexCancellable implements CancellableDependency {

    private final AtomicMarkableReference<Cancellable> dependencyRef;

    public ComplexCancellable() {
        this.dependencyRef = new AtomicMarkableReference<>(null, false);
    }

    @Override
    public boolean isCancelled() {
        return dependencyRef.isMarked();
    }

    @Override
    public void setDependency(final Cancellable dependency) {
        Args.notNull(dependency, "dependency");
        final Cancellable actualDependency = dependencyRef.getReference();
        if (!dependencyRef.compareAndSet(actualDependency, dependency, false, false)) {
            dependency.cancel();
        }
    }

    @Override
    public boolean cancel() {
        while (!dependencyRef.isMarked()) {
            final Cancellable actualDependency = dependencyRef.getReference();
            if (dependencyRef.compareAndSet(actualDependency, actualDependency, false, true)) {
                if (actualDependency != null) {
                    actualDependency.cancel();
                }
                return true;
            }
        }
        return false;
    }

}
