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


import java.util.concurrent.Future;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TestComplexFuture {

    @Test
    void testCancelled() {
        final ComplexFuture<Object> future = new ComplexFuture<>(null);

        final Future<Object> dependency1 = new BasicFuture<>(null);
        future.setDependency(dependency1);

        Assertions.assertThat(future.isDone()).isFalse();

        future.cancel();
        Assertions.assertThat(future.isCancelled()).isTrue();
        Assertions.assertThat(dependency1.isCancelled()).isTrue();

        final Future<Object> dependency2 = new BasicFuture<>(null);
        future.setDependency(dependency2);
        Assertions.assertThat(dependency2.isCancelled()).isTrue();
    }

    @Test
    void testCompleted() {
        final ComplexFuture<Object> future = new ComplexFuture<>(null);

        final Future<Object> dependency1 = new BasicFuture<>(null);
        future.setDependency(dependency1);

        Assertions.assertThat(future.isDone()).isFalse();

        future.completed(Boolean.TRUE);
        Assertions.assertThat(future.isCancelled()).isFalse();
        Assertions.assertThat(dependency1.isCancelled()).isFalse();

        final Future<Object> dependency2 = new BasicFuture<>(null);
        future.setDependency(dependency2);
        Assertions.assertThat(dependency2.isCancelled()).isTrue();
    }

    @Test
    void testCancelledWithCallback() {
        final ComplexFuture<Object> future = new ComplexFuture<>(null);

        final Future<Object> dependency1 = new BasicFuture<>(new FutureContribution<Object>(future) {

            @Override
            public void completed(final Object result) {
            }

        });
        future.setDependency(dependency1);

        Assertions.assertThat(future.isDone()).isFalse();

        future.cancel();
        Assertions.assertThat(future.isCancelled()).isTrue();
        Assertions.assertThat(dependency1.isCancelled()).isTrue();

        final Future<Object> dependency2 = new BasicFuture<>(null);
        future.setDependency(dependency2);
        Assertions.assertThat(dependency2.isCancelled()).isTrue();
    }

    @Test
    void testCanceledAndFailed() {
        final ComplexFuture<Object> future = new ComplexFuture<>(null);
        Assertions.assertThat(future.cancel()).isTrue();
        Assertions.assertThat(future.failed(new Exception())).isFalse();
    }

}
