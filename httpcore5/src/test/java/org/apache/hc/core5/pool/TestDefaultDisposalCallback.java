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

import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.http.SocketModalCloseable;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestDefaultDisposalCallback {

    private static class TestCloseable implements SocketModalCloseable {
        private Timeout socketTimeout;
        private final AtomicReference<CloseMode> closedWith = new AtomicReference<>();

        TestCloseable(final Timeout socketTimeout) {
            this.socketTimeout = socketTimeout;
        }

        @Override
        public void close() {
            close(CloseMode.GRACEFUL);
        }

        @Override
        public void close(final CloseMode closeMode) {
            closedWith.set(closeMode);
        }

        @Override
        public Timeout getSocketTimeout() {
            return socketTimeout;
        }

        @Override
        public void setSocketTimeout(final Timeout timeout) {
            socketTimeout = timeout;
        }
    }

    @Test
    void setsDefaultTimeoutWhenMissingOrTooLarge() {
        final DefaultDisposalCallback<TestCloseable> callback = new DefaultDisposalCallback<>();
        final TestCloseable closeable = new TestCloseable(null);

        callback.execute(closeable, CloseMode.IMMEDIATE);

        Assertions.assertEquals(Timeout.ofSeconds(1), closeable.getSocketTimeout());
        Assertions.assertEquals(CloseMode.IMMEDIATE, closeable.closedWith.get());

        final TestCloseable closeableTooLarge = new TestCloseable(Timeout.ofSeconds(5));
        callback.execute(closeableTooLarge, CloseMode.GRACEFUL);
        Assertions.assertEquals(Timeout.ofSeconds(1), closeableTooLarge.getSocketTimeout());
        Assertions.assertEquals(CloseMode.GRACEFUL, closeableTooLarge.closedWith.get());
    }

    @Test
    void keepsReasonableTimeout() {
        final DefaultDisposalCallback<TestCloseable> callback = new DefaultDisposalCallback<>();
        final TestCloseable closeable = new TestCloseable(Timeout.ofMilliseconds(500));

        callback.execute(closeable, CloseMode.GRACEFUL);

        Assertions.assertEquals(Timeout.ofMilliseconds(500), closeable.getSocketTimeout());
        Assertions.assertEquals(CloseMode.GRACEFUL, closeable.closedWith.get());
    }

    @Test
    void normalizesZeroOrNegativeTimeout() {
        final DefaultDisposalCallback<TestCloseable> callback = new DefaultDisposalCallback<>();
        final TestCloseable closeable = new TestCloseable(Timeout.ofMilliseconds(0));

        callback.execute(closeable, CloseMode.IMMEDIATE);

        Assertions.assertEquals(Timeout.ofSeconds(1), closeable.getSocketTimeout());
    }

}
