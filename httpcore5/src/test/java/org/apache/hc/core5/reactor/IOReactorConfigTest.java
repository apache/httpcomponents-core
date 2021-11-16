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

import java.net.InetSocketAddress;

import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class IOReactorConfigTest {
    @Test
    public void testCustomIOReactorConfig() throws Exception {
        final IOReactorConfig reactorConfig = IOReactorConfig.custom()
                .setSelectInterval(TimeValue.ofMilliseconds(500))
                .setIoThreadCount(2)
                .setSoTimeout(Timeout.ofSeconds(10))
                .setSoReuseAddress(true)
                .setSoLinger(TimeValue.ofSeconds(30))
                .setSoKeepAlive(true)
                .setTcpNoDelay(false)
                .setTrafficClass(0x02)
                .setSndBufSize(32767)
                .setRcvBufSize(8192)
                .setBacklogSize(5)
                .setSocksProxyAddress(new InetSocketAddress(8888))
                .setSocksProxyUsername("socksProxyUsername")
                .setSocksProxyPassword("socksProxyPassword")
                .build();

        Assertions.assertEquals(TimeValue.ofMilliseconds(500), reactorConfig.getSelectInterval());
        Assertions.assertEquals(2, reactorConfig.getIoThreadCount());
        Assertions.assertEquals(Timeout.ofSeconds(10), reactorConfig.getSoTimeout());
        Assertions.assertTrue(reactorConfig.isSoReuseAddress());
        Assertions.assertEquals(TimeValue.ofSeconds(30), reactorConfig.getSoLinger());
        Assertions.assertTrue(reactorConfig.isSoKeepAlive());
        Assertions.assertFalse(reactorConfig.isTcpNoDelay());
        Assertions.assertEquals(0x02, reactorConfig.getTrafficClass());
        Assertions.assertEquals(32767, reactorConfig.getSndBufSize());
        Assertions.assertEquals(8192, reactorConfig.getRcvBufSize());
        Assertions.assertEquals(5, reactorConfig.getBacklogSize());
        Assertions.assertEquals(new InetSocketAddress(8888), reactorConfig.getSocksProxyAddress());
        Assertions.assertEquals("socksProxyUsername", reactorConfig.getSocksProxyUsername());
        Assertions.assertEquals("socksProxyPassword", reactorConfig.getSocksProxyPassword());
    }
}
