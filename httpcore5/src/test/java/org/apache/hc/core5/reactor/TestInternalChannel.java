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
package org.apache.hc.core5.reactor;

import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestInternalChannel {

    private static class TestChannel extends InternalChannel {
        final AtomicBoolean closed = new AtomicBoolean();
        final AtomicReference<CloseMode> closeMode = new AtomicReference<>();
        final AtomicBoolean timedOut = new AtomicBoolean();
        final AtomicReference<Exception> exceptionRef = new AtomicReference<>();
        Timeout timeout = Timeout.ofMilliseconds(1);
        long lastEventTime = 0;
        boolean throwCancelled;
        boolean throwRuntime;

        @Override
        void onIOEvent(final int ops) throws IOException {
            if (throwCancelled) {
                throw new CancelledKeyException();
            }
            if (throwRuntime) {
                throw new IOException("boom");
            }
        }

        @Override
        void onTimeout(final Timeout timeout) {
            timedOut.set(true);
        }

        @Override
        void onException(final Exception cause) {
            exceptionRef.set(cause);
        }

        @Override
        Timeout getTimeout() {
            return timeout;
        }

        @Override
        long getLastEventTime() {
            return lastEventTime;
        }

        @Override
        public void close() {
            closed.set(true);
        }

        @Override
        public void close(final CloseMode closeMode) {
            closed.set(true);
            this.closeMode.set(closeMode);
        }
    }

    @Test
    void handleIoEventClosesOnCancelledKey() {
        try (TestChannel channel = new TestChannel()) {
            channel.throwCancelled = true;

            channel.handleIOEvent(0);

            Assertions.assertTrue(channel.closed.get());
            Assertions.assertEquals(CloseMode.GRACEFUL, channel.closeMode.get());
        }
    }

    @Test
    void handleIoEventClosesOnException() {
        try (TestChannel channel = new TestChannel()) {
            channel.throwRuntime = true;

            channel.handleIOEvent(0);

            Assertions.assertTrue(channel.closed.get());
            Assertions.assertEquals(CloseMode.GRACEFUL, channel.closeMode.get());
            Assertions.assertNotNull(channel.exceptionRef.get());
        }
    }

    @Test
    void checkTimeoutInvokesCallback() {
        try (TestChannel channel = new TestChannel()) {
            channel.lastEventTime = 0;
            channel.timeout = Timeout.ofMilliseconds(1);

            final boolean result = channel.checkTimeout(10);

            Assertions.assertFalse(result);
            Assertions.assertTrue(channel.timedOut.get());
        }
    }

    @Test
    void checkTimeoutSkipsWhenDisabled() {
        try (TestChannel channel = new TestChannel()) {
            channel.timeout = Timeout.DISABLED;

            final boolean result = channel.checkTimeout(System.currentTimeMillis());

            Assertions.assertTrue(result);
            Assertions.assertFalse(channel.timedOut.get());
        }
    }

}
