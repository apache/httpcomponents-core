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
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TestReactorBasics {

    @Test
    void channelEntryToString() throws Exception {
        try (SocketChannel channel = SocketChannel.open()) {
            final ChannelEntry entry = new ChannelEntry(channel, "att");
            Assertions.assertThat(entry.toString().contains("attachment=att")).isTrue();
        }
    }

    @Test
    void eventMaskConstantsMatchSelectionKey() {
        Assertions.assertThat(EventMask.READ).isEqualTo(SelectionKey.OP_READ);
        Assertions.assertThat(EventMask.WRITE).isEqualTo(SelectionKey.OP_WRITE);
        Assertions.assertThat(EventMask.READ_WRITE).isEqualTo(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
    }

    @Test
    void endpointParametersExposeValues() {
        final EndpointParameters params = new EndpointParameters("https", "example.com", 8443, "tag");
        Assertions.assertThat(params.getScheme()).isEqualTo("https");
        Assertions.assertThat(params.getHostName()).isEqualTo("example.com");
        Assertions.assertThat(params.getPort()).isEqualTo(8443);
        Assertions.assertThat(params.getAttachment()).isEqualTo("tag");

        final EndpointParameters fromHost = new EndpointParameters(new HttpHost("http", "localhost", 80), null);
        Assertions.assertThat(fromHost.getScheme()).isEqualTo("http");
        Assertions.assertThat(fromHost.getHostName()).isEqualTo("localhost");
        Assertions.assertThat(fromHost.getPort()).isEqualTo(80);
    }

    @Test
    void ioReactorShutdownExceptionKeepsMessage() {
        final IOReactorShutdownException ex = new IOReactorShutdownException("down");
        Assertions.assertThat(ex.getMessage()).isEqualTo("down");
    }

    @Test
    void ioReactorStatusValuesPresent() {
        Assertions.assertThat(IOReactorStatus.INACTIVE).isNotNull();
        Assertions.assertThat(IOReactorStatus.ACTIVE).isNotNull();
        Assertions.assertThat(IOReactorStatus.SHUTTING_DOWN).isNotNull();
        Assertions.assertThat(IOReactorStatus.SHUT_DOWN).isNotNull();
        Assertions.assertThat(IOReactorStatus.ACTIVE).isNotEqualTo(IOReactorStatus.INACTIVE);
    }

    @Test
    void commandPriorityEnumValuesPresent() {
        Assertions.assertThat(Command.Priority.NORMAL).isNotNull();
        Assertions.assertThat(Command.Priority.IMMEDIATE).isNotNull();
    }

}
