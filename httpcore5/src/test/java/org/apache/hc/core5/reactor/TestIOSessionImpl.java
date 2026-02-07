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

import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.io.CloseMode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestIOSessionImpl {

    private static class TestCommand implements Command {
        final AtomicBoolean cancelled = new AtomicBoolean();

        @Override
        public boolean cancel() {
            cancelled.set(true);
            return true;
        }
    }

    @Test
    @SuppressWarnings("resource")
    void enqueueUpdatesInterestOpsAndOrders() throws Exception {
        try (Selector selector = Selector.open();
             SocketChannel channel = SocketChannel.open()) {
            channel.configureBlocking(false);
            final SelectionKey key = channel.register(selector, SelectionKey.OP_READ);
            final AtomicReference<IOSession> closedRef = new AtomicReference<>();
            final IOSessionImpl session = new IOSessionImpl("t", key, channel, closedRef::set);
            try {
                final TestCommand normal = new TestCommand();
                final TestCommand immediate = new TestCommand();

                session.enqueue(normal, Command.Priority.NORMAL);
                session.enqueue(immediate, Command.Priority.IMMEDIATE);

                Assertions.assertTrue(session.hasCommands());
                Assertions.assertEquals(2, session.getPendingCommandCount());
                Assertions.assertSame(immediate, session.poll());
                Assertions.assertSame(normal, session.poll());

                Assertions.assertEquals(SelectionKey.OP_READ | SelectionKey.OP_WRITE, key.interestOps());
            } finally {
                session.close(CloseMode.IMMEDIATE);
                Assertions.assertSame(session, closedRef.get());
            }
        }
    }

    @Test
    @SuppressWarnings("resource")
    void enqueueOnClosedCancels() throws Exception {
        try (Selector selector = Selector.open();
             SocketChannel channel = SocketChannel.open()) {
            channel.configureBlocking(false);
            final SelectionKey key = channel.register(selector, SelectionKey.OP_READ);
            final IOSessionImpl session = new IOSessionImpl("t", key, channel, null);
            try {
                session.close(CloseMode.IMMEDIATE);

                final TestCommand command = new TestCommand();
                session.enqueue(command, Command.Priority.NORMAL);

                Assertions.assertTrue(command.cancelled.get());
            } finally {
                session.close(CloseMode.IMMEDIATE);
            }
        }
    }

    @Test
    @SuppressWarnings("resource")
    void eventMaskHelpersWork() throws Exception {
        try (Selector selector = Selector.open();
             SocketChannel channel = SocketChannel.open()) {
            channel.configureBlocking(false);
            final SelectionKey key = channel.register(selector, SelectionKey.OP_READ);
            final IOSessionImpl session = new IOSessionImpl("t", key, channel, null);
            try {
                session.setEvent(SelectionKey.OP_WRITE);
                Assertions.assertEquals(SelectionKey.OP_READ | SelectionKey.OP_WRITE, session.getEventMask());

                session.clearEvent(SelectionKey.OP_READ);
                Assertions.assertEquals(SelectionKey.OP_WRITE, session.getEventMask());

                session.setEventMask(SelectionKey.OP_READ);
                Assertions.assertEquals(SelectionKey.OP_READ, session.getEventMask());
            } finally {
                session.close(CloseMode.IMMEDIATE);
            }
        }
    }

    @Test
    @SuppressWarnings("resource")
    void toStringIncludesStatus() throws Exception {
        try (Selector selector = Selector.open();
             SocketChannel channel = SocketChannel.open()) {
            channel.configureBlocking(false);
            final SelectionKey key = channel.register(selector, SelectionKey.OP_READ);
            final IOSessionImpl session = new IOSessionImpl("t", key, channel, null);
            try {
                final String text = session.toString();
                Assertions.assertTrue(text.contains("ACTIVE"));
            } finally {
                session.close(CloseMode.IMMEDIATE);
            }
        }
    }

}
