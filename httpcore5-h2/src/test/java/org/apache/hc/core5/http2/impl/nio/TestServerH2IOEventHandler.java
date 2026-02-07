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
package org.apache.hc.core5.http2.impl.nio;

import java.net.InetSocketAddress;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TestServerH2IOEventHandler {

    @Test
    void toStringIncludesState() throws Exception {
        final ServerH2StreamMultiplexer multiplexer = Mockito.mock(ServerH2StreamMultiplexer.class);
        Mockito.when(multiplexer.getRemoteAddress()).thenReturn(new InetSocketAddress("localhost", 9443));
        Mockito.when(multiplexer.getLocalAddress()).thenReturn(new InetSocketAddress("localhost", 8443));
        Mockito.doAnswer(invocation -> {
            final StringBuilder builder = invocation.getArgument(0);
            builder.append("state");
            return null;
        }).when(multiplexer).appendState(Mockito.any(StringBuilder.class));

        final ServerH2IOEventHandler handler = new ServerH2IOEventHandler(multiplexer);
        final String text;
        try {
            text = handler.toString();
        } finally {
            handler.close();
        }

        Assertions.assertTrue(text.contains("state"));
        Assertions.assertTrue(text.contains("->"));
    }

}
