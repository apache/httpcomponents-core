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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class TestMaxCommandsPerSession {

    @Test
    void testCommandQueueCap() throws Exception {
        final ServerSocketChannel server = ServerSocketChannel.open();
        server.bind(new InetSocketAddress("127.0.0.1", 0));
        final InetSocketAddress address = (InetSocketAddress) server.getLocalAddress();

        final SocketChannel client = SocketChannel.open(address);
        client.configureBlocking(false);

        final SocketChannel accepted = server.accept();
        assertNotNull(accepted);

        final Selector selector = Selector.open();
        final SelectionKey key = client.register(selector, 0);

        final IOSessionImpl session = new IOSessionImpl("t", key, client, null, 2);

        final AtomicInteger cancelled = new AtomicInteger(0);

        final Command c1 = () -> {
            cancelled.incrementAndGet();
            return true;
        };
        final Command c2 = () -> {
            cancelled.incrementAndGet();
            return true;
        };
        final Command rejected = () -> {
            cancelled.incrementAndGet();
            return true;
        };

        assertDoesNotThrow(() -> session.enqueue(c1, Command.Priority.NORMAL));
        assertDoesNotThrow(() -> session.enqueue(c2, Command.Priority.NORMAL));

        assertThrows(RejectedExecutionException.class, () -> session.enqueue(rejected, Command.Priority.NORMAL));
        assertEquals(1, cancelled.get());

        assertNotNull(session.poll());

        final Command retry = () -> {
            cancelled.incrementAndGet();
            return true;
        };

        assertDoesNotThrow(() -> session.enqueue(retry, Command.Priority.NORMAL));

        accepted.close();
        client.close();
        selector.close();
        server.close();
    }

}
