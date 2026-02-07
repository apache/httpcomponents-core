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
import java.nio.channels.SocketChannel;

import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestReactorBasics {

    @Test
    void channelEntryToString() throws Exception {
        try (SocketChannel channel = SocketChannel.open()) {
            final ChannelEntry entry = new ChannelEntry(channel, "att");
            Assertions.assertTrue(entry.toString().contains("attachment=att"));
        }
    }

    @Test
    void eventMaskConstantsMatchSelectionKey() {
        Assertions.assertEquals(SelectionKey.OP_READ, EventMask.READ);
        Assertions.assertEquals(SelectionKey.OP_WRITE, EventMask.WRITE);
        Assertions.assertEquals(SelectionKey.OP_READ | SelectionKey.OP_WRITE, EventMask.READ_WRITE);
    }

    @Test
    void endpointParametersExposeValues() {
        final EndpointParameters params = new EndpointParameters("https", "example.com", 8443, "tag");
        Assertions.assertEquals("https", params.getScheme());
        Assertions.assertEquals("example.com", params.getHostName());
        Assertions.assertEquals(8443, params.getPort());
        Assertions.assertEquals("tag", params.getAttachment());

        final EndpointParameters fromHost = new EndpointParameters(new HttpHost("http", "localhost", 80), null);
        Assertions.assertEquals("http", fromHost.getScheme());
        Assertions.assertEquals("localhost", fromHost.getHostName());
        Assertions.assertEquals(80, fromHost.getPort());
    }

    @Test
    void ioReactorShutdownExceptionKeepsMessage() {
        final IOReactorShutdownException ex = new IOReactorShutdownException("down");
        Assertions.assertEquals("down", ex.getMessage());
    }

    @Test
    void ioReactorStatusValuesPresent() {
        Assertions.assertNotNull(IOReactorStatus.INACTIVE);
        Assertions.assertNotNull(IOReactorStatus.ACTIVE);
        Assertions.assertNotNull(IOReactorStatus.SHUTTING_DOWN);
        Assertions.assertNotNull(IOReactorStatus.SHUT_DOWN);
        Assertions.assertNotEquals(IOReactorStatus.INACTIVE, IOReactorStatus.ACTIVE);
    }

    @Test
    void commandPriorityEnumValuesPresent() {
        Assertions.assertNotNull(Command.Priority.NORMAL);
        Assertions.assertNotNull(Command.Priority.IMMEDIATE);
    }

}
