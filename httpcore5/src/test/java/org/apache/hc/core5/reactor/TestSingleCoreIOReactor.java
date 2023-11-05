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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.SocketOption;
import java.nio.channels.SocketChannel;

import static org.apache.hc.core5.reactor.SingleCoreIOReactor.TCP_KEEPCOUNT;
import static org.apache.hc.core5.reactor.SingleCoreIOReactor.TCP_KEEPIDLE;
import static org.apache.hc.core5.reactor.SingleCoreIOReactor.TCP_KEEPINTERVAL;
import static org.apache.hc.core5.util.ReflectionUtils.determineJRELevel;
import static org.apache.hc.core5.util.ReflectionUtils.getExtendedSocketOptionOrNull;
import static org.mockito.Mockito.mock;

public class TestSingleCoreIOReactor {
    @Test
    public void testSetExtendedSocketOption() throws IOException {
        final SingleCoreIOReactor reactor = new SingleCoreIOReactor(null, mock(IOEventHandlerFactory.class), IOReactorConfig.DEFAULT, null, null, null);
        final SocketChannel socketChannel = SocketChannel.open();
        final SocketOption<Integer> tcpKeepIdle;
        final SocketOption<Integer> tcpKeepInterval;
        final SocketOption<Integer> tcpKeepCount;
        // Partial versions of jdk1.8 contain TCP_KEEPIDLE, TCP_KEEPINTERVAL, TCP_KEEPCOUNT.
        if (determineJRELevel() > 8) {
            reactor.setExtendedSocketOption(socketChannel, TCP_KEEPIDLE, 100);
            reactor.setExtendedSocketOption(socketChannel, TCP_KEEPINTERVAL, 10);
            reactor.setExtendedSocketOption(socketChannel, TCP_KEEPCOUNT, 10);

            tcpKeepIdle = getExtendedSocketOptionOrNull(TCP_KEEPIDLE);
            tcpKeepInterval = getExtendedSocketOptionOrNull(TCP_KEEPINTERVAL);
            tcpKeepCount = getExtendedSocketOptionOrNull(TCP_KEEPCOUNT);
            Assertions.assertEquals(100, socketChannel.getOption(tcpKeepIdle));
            Assertions.assertEquals(10, socketChannel.getOption(tcpKeepInterval));
            Assertions.assertEquals(10, socketChannel.getOption(tcpKeepCount));
        }
    }
}
