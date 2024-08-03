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

package org.apache.hc.core5.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.net.ServerSocketFactory;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketOption;

import static org.apache.hc.core5.reactor.SingleCoreIOReactor.TCP_KEEPCOUNT;
import static org.apache.hc.core5.reactor.SingleCoreIOReactor.TCP_KEEPIDLE;
import static org.apache.hc.core5.reactor.SingleCoreIOReactor.TCP_KEEPINTERVAL;
import static org.apache.hc.core5.util.ReflectionUtils.callGetter;
import static org.apache.hc.core5.util.ReflectionUtils.determineJRELevel;
import static org.apache.hc.core5.util.ReflectionUtils.setOption;
import static org.apache.hc.core5.util.ReflectionUtils.getExtendedSocketOptionOrNull;

public class TestReflectionUtils {

    @Test
    public void testGetExtendedSocketOptionOrNull() {
        testGetExtendedSocketOption(TCP_KEEPIDLE);
        testGetExtendedSocketOption(TCP_KEEPINTERVAL);
        testGetExtendedSocketOption(TCP_KEEPCOUNT);
    }

    private void testGetExtendedSocketOption(final String option) {
        final SocketOption socketOption = getExtendedSocketOptionOrNull(option);
        // 1.Partial versions of jdk1.8 contain TCP_KEEPIDLE, TCP_KEEPINTERVAL, TCP_KEEPCOUNT.
        // 2. Windows may not support TCP_KEEPIDLE, TCP_KEEPINTERVAL, TCP_KEEPCOUNT.
        if (determineJRELevel() > 8 && isWindows() == false) {
            Assertions.assertNotNull(socketOption);
        }
    }

    @Test
    public void testSetOption() throws IOException {
        if (determineJRELevel() > 8 && isWindows() == false) {
            {
                // test Socket
                final Socket sock = new Socket();
                setOption(sock, TCP_KEEPIDLE, 20);
                setOption(sock, TCP_KEEPINTERVAL, 21);
                setOption(sock, TCP_KEEPCOUNT, 22);

                final SocketOption<Integer> tcpKeepIdle = getExtendedSocketOptionOrNull(TCP_KEEPIDLE);
                assert tcpKeepIdle != null;
                Assertions.assertEquals(20, callGetter(sock, "Option", tcpKeepIdle, SocketOption.class, Integer.class));

                final SocketOption<Integer> tcpKeepInterval = getExtendedSocketOptionOrNull(TCP_KEEPINTERVAL);
                assert tcpKeepInterval != null;
                Assertions.assertEquals(21, callGetter(sock, "Option", tcpKeepInterval, SocketOption.class, Integer.class));

                final SocketOption<Integer> tcpKeepCount = getExtendedSocketOptionOrNull(TCP_KEEPCOUNT);
                assert tcpKeepCount != null;
                Assertions.assertEquals(22, callGetter(sock, "Option", tcpKeepCount, SocketOption.class, Integer.class));
            }

            {
                // test ServerSocket
                final ServerSocket serverSocket = ServerSocketFactory.getDefault().createServerSocket();
                setOption(serverSocket, TCP_KEEPIDLE, 20);
                setOption(serverSocket, TCP_KEEPINTERVAL, 21);
                setOption(serverSocket, TCP_KEEPCOUNT, 22);

                final SocketOption<Integer> tcpKeepIdle = getExtendedSocketOptionOrNull(TCP_KEEPIDLE);
                assert tcpKeepIdle != null;
                Assertions.assertEquals(20, callGetter(serverSocket, "Option", tcpKeepIdle, SocketOption.class, Integer.class));

                final SocketOption<Integer> tcpKeepInterval = getExtendedSocketOptionOrNull(TCP_KEEPINTERVAL);
                assert tcpKeepInterval != null;
                Assertions.assertEquals(21, callGetter(serverSocket, "Option", tcpKeepInterval, SocketOption.class, Integer.class));

                final SocketOption<Integer> tcpKeepCount = getExtendedSocketOptionOrNull(TCP_KEEPCOUNT);
                assert tcpKeepCount != null;
                Assertions.assertEquals(22, callGetter(serverSocket, "Option", tcpKeepCount, SocketOption.class, Integer.class));
            }
        }
    }

    public static boolean isWindows() {
        return System.getProperty("os.name").contains("Windows");
    }
}
