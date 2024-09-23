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

package org.apache.hc.core5.io;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketOption;

import javax.net.ServerSocketFactory;

import org.apache.hc.core5.util.ReflectionUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestSocketSupport {

    @Test
    void testGetExtendedSocketOptionOrNull() {
        testGetExtendedSocketOption(SocketSupport.TCP_KEEPIDLE);
        testGetExtendedSocketOption(SocketSupport.TCP_KEEPINTERVAL);
        testGetExtendedSocketOption(SocketSupport.TCP_KEEPCOUNT);
    }

    private <T> void testGetExtendedSocketOption(final String option) {
        final SocketOption<T> socketOption = SocketSupport.getExtendedSocketOptionOrNull(option);
        // 1.Partial versions of jdk1.8 contain TCP_KEEPIDLE, TCP_KEEPINTERVAL, TCP_KEEPCOUNT.
        // 2. Windows may not support TCP_KEEPIDLE, TCP_KEEPINTERVAL, TCP_KEEPCOUNT.
        if (ReflectionUtils.determineJRELevel() > 8 && !isWindows()) {
            Assertions.assertNotNull(socketOption);
        }
    }

    @Test
    void testSetOption() throws IOException {
        if (ReflectionUtils.determineJRELevel() > 8 && isWindows() == false) {
            {
                // test Socket
                final Socket sock = new Socket();
                SocketSupport.setOption(sock, SocketSupport.TCP_KEEPIDLE, 20);
                SocketSupport.setOption(sock, SocketSupport.TCP_KEEPINTERVAL, 21);
                SocketSupport.setOption(sock, SocketSupport.TCP_KEEPCOUNT, 22);

                final SocketOption<Integer> tcpKeepIdle = SocketSupport.getExtendedSocketOptionOrNull(SocketSupport.TCP_KEEPIDLE);
                assert tcpKeepIdle != null;
                Assertions.assertEquals(20, ReflectionUtils.callGetter(sock, "Option", tcpKeepIdle, SocketOption.class, Integer.class));

                final SocketOption<Integer> tcpKeepInterval = SocketSupport.getExtendedSocketOptionOrNull(SocketSupport.TCP_KEEPINTERVAL);
                assert tcpKeepInterval != null;
                Assertions.assertEquals(21, ReflectionUtils.callGetter(sock, "Option", tcpKeepInterval, SocketOption.class, Integer.class));

                final SocketOption<Integer> tcpKeepCount = SocketSupport.getExtendedSocketOptionOrNull(SocketSupport.TCP_KEEPCOUNT);
                assert tcpKeepCount != null;
                Assertions.assertEquals(22, ReflectionUtils.callGetter(sock, "Option", tcpKeepCount, SocketOption.class, Integer.class));
            }

            {
                // test ServerSocket
                final ServerSocket serverSocket = ServerSocketFactory.getDefault().createServerSocket();
                SocketSupport.setOption(serverSocket, SocketSupport.TCP_KEEPIDLE, 20);
                SocketSupport.setOption(serverSocket, SocketSupport.TCP_KEEPINTERVAL, 21);
                SocketSupport.setOption(serverSocket, SocketSupport.TCP_KEEPCOUNT, 22);

                final SocketOption<Integer> tcpKeepIdle = SocketSupport.getExtendedSocketOptionOrNull(SocketSupport.TCP_KEEPIDLE);
                assert tcpKeepIdle != null;
                Assertions.assertEquals(20, ReflectionUtils.callGetter(serverSocket, "Option", tcpKeepIdle, SocketOption.class, Integer.class));

                final SocketOption<Integer> tcpKeepInterval = SocketSupport.getExtendedSocketOptionOrNull(SocketSupport.TCP_KEEPINTERVAL);
                assert tcpKeepInterval != null;
                Assertions.assertEquals(21, ReflectionUtils.callGetter(serverSocket, "Option", tcpKeepInterval, SocketOption.class, Integer.class));

                final SocketOption<Integer> tcpKeepCount = SocketSupport.getExtendedSocketOptionOrNull(SocketSupport.TCP_KEEPCOUNT);
                assert tcpKeepCount != null;
                Assertions.assertEquals(22, ReflectionUtils.callGetter(serverSocket, "Option", tcpKeepCount, SocketOption.class, Integer.class));
            }
        }
    }

    public static boolean isWindows() {
        return System.getProperty("os.name").contains("Windows");
    }

}
