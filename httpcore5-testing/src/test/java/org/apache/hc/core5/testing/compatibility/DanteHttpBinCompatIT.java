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

package org.apache.hc.core5.testing.compatibility;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.testing.compatibility.classic.SocksHttpBinClassicCompatTest;
import org.apache.hc.core5.testing.compatibility.nio.SocksHttpBinAsyncCompatTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class DanteHttpBinCompatIT {

    private static Network NETWORK = Network.newNetwork();
    @Container
    static final GenericContainer<?> DANTE_CONTAINER = ContainerImages.dante(NETWORK);
    @Container
    static final GenericContainer<?> HTTP_BIN_CONTAINER = ContainerImages.httpBin(NETWORK);

    @AfterAll
    static void cleanup() {
        DANTE_CONTAINER.close();
        HTTP_BIN_CONTAINER.close();
    }

    static SocketAddress socketContainerAddress() {
        return new InetSocketAddress(DANTE_CONTAINER.getHost(), DANTE_CONTAINER.getMappedPort(ContainerImages.SOCKS_PORT));
    }

    static HttpHost targetInternalHost() {
        return new HttpHost(URIScheme.HTTP.id, ContainerImages.HTTPBIN, ContainerImages.HTTP_PORT);
    }

    static String SOCKS_USER = "socks";
    static String SOCKS_PW = "nopassword";

    @Nested
    @DisplayName("Classic, SOCKS proxy")
    class Classic extends SocksHttpBinClassicCompatTest {

        public Classic() throws Exception {
            super(targetInternalHost(), socketContainerAddress(), SOCKS_USER, SOCKS_PW);
        }

    }

    @Nested
    @DisplayName("Async, SOCKS proxy")
    class Async extends SocksHttpBinAsyncCompatTest {

        public Async() throws Exception {
            super(targetInternalHost(), socketContainerAddress(), SOCKS_USER, SOCKS_PW);
        }

    }

}
