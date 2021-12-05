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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class DefaultThreadFactoryTest {


    @Test
    void newThread() {

        final ThreadFactory defaultThreadFactory = new DefaultThreadFactory("I/O server dispatch", true);
        final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(defaultThreadFactory);
        final CountDownLatch lockHeld = new CountDownLatch(1);
        final Thread thread = defaultThreadFactory.newThread(() -> {
            try {
                lockHeld.countDown();
                /* Wait for the workers to complete. */
                lockHeld.await(100, TimeUnit.MILLISECONDS);

            } catch (final InterruptedException ignored) {
            }
        });
        assertNotNull(thread);
        scheduledExecutorService.execute(thread);
        thread.start();
        assertTrue(thread.isAlive());
    }
}