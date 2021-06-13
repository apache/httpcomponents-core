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
import org.junit.Assert;
import org.junit.Test;

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

        Assert.assertEquals(TimeValue.ofMilliseconds(500), reactorConfig.getSelectInterval());
        Assert.assertEquals(2, reactorConfig.getIoThreadCount());
        Assert.assertEquals(Timeout.ofSeconds(10), reactorConfig.getSoTimeout());
        Assert.assertTrue(reactorConfig.isSoReuseAddress());
        Assert.assertEquals(TimeValue.ofSeconds(30), reactorConfig.getSoLinger());
        Assert.assertTrue(reactorConfig.isSoKeepalive());
        Assert.assertFalse(reactorConfig.isTcpNoDelay());
        Assert.assertEquals(0x02, reactorConfig.getTrafficClass());
        Assert.assertEquals(32767, reactorConfig.getSndBufSize());
        Assert.assertEquals(8192, reactorConfig.getRcvBufSize());
        Assert.assertEquals(5, reactorConfig.getBacklogSize());
        Assert.assertEquals(new InetSocketAddress(8888), reactorConfig.getSocksProxyAddress());
        Assert.assertEquals("socksProxyUsername", reactorConfig.getSocksProxyUsername());
        Assert.assertEquals("socksProxyPassword", reactorConfig.getSocksProxyPassword());
    }
}
