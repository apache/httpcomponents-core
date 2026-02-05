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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;

class TestComplexCancellable {

    @Test
    void testCancelled() {
        final ComplexCancellable cancellable = new ComplexCancellable();

        final BasicFuture<Object> dependency1 = new BasicFuture<>(null);
        cancellable.setDependency(dependency1);

        assertFalse(cancellable.isCancelled());

        cancellable.cancel();
        assertTrue(cancellable.isCancelled());
        assertTrue(dependency1.isCancelled());

        final BasicFuture<Object> dependency2 = new BasicFuture<>(null);
        cancellable.setDependency(dependency2);
        assertTrue(dependency2.isCancelled());
    }

    @Test
    void testSetDependencyRace() throws InterruptedException {
        final ComplexCancellable cancellable = new ComplexCancellable();
        final BasicFuture<Object> dependency1 = new BasicFuture<>(null);
        final BasicFuture<Object> dependency2 = new BasicFuture<>(null);
        final CountDownLatch latch = new CountDownLatch(2);

        for (int i = 0; i < 2; i++) {
            new Thread(() -> {
                for (int j = 0; j < 5_000; j++) {
                    cancellable.setDependency(dependency1);
                    cancellable.setDependency(dependency2);
                }
                latch.countDown();
            }).start();
        }
        latch.await();

        assertFalse(dependency1.isCancelled());
        assertFalse(dependency2.isCancelled());
    }
}
