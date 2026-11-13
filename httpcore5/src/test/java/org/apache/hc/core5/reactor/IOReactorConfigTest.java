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
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class IOReactorConfigTest {
    @Test
    void testCustomIOReactorConfig() {
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
                .setTcpKeepIdle(100)
                .setTcpKeepInterval(12)
                .setTcpKeepCount(4)
                .setSocksProxyAddress(new InetSocketAddress(8888))
                .setSocksProxyUsername("socksProxyUsername")
                .setSocksProxyPassword("socksProxyPassword")
                .build();

        Assertions.assertThat(reactorConfig.getSelectInterval()).isEqualTo(TimeValue.ofMilliseconds(500));
        Assertions.assertThat(reactorConfig.getIoThreadCount()).isEqualTo(2);
        Assertions.assertThat(reactorConfig.getSoTimeout()).isEqualTo(Timeout.ofSeconds(10));
        Assertions.assertThat(reactorConfig.isSoReuseAddress()).isTrue();
        Assertions.assertThat(reactorConfig.getSoLinger()).isEqualTo(TimeValue.ofSeconds(30));
        Assertions.assertThat(reactorConfig.isSoKeepAlive()).isTrue();
        Assertions.assertThat(reactorConfig.isTcpNoDelay()).isFalse();
        Assertions.assertThat(reactorConfig.getTrafficClass()).isEqualTo(0x02);
        Assertions.assertThat(reactorConfig.getSndBufSize()).isEqualTo(32767);
        Assertions.assertThat(reactorConfig.getRcvBufSize()).isEqualTo(8192);
        Assertions.assertThat(reactorConfig.getBacklogSize()).isEqualTo(5);
        Assertions.assertThat(reactorConfig.getTcpKeepIdle()).isEqualTo(100);
        Assertions.assertThat(reactorConfig.getTcpKeepInterval()).isEqualTo(12);
        Assertions.assertThat(reactorConfig.getTcpKeepCount()).isEqualTo(4);
        Assertions.assertThat(reactorConfig.getSocksProxyAddress()).isEqualTo(new InetSocketAddress(8888));
        Assertions.assertThat(reactorConfig.getSocksProxyUsername()).isEqualTo("socksProxyUsername");
        Assertions.assertThat(reactorConfig.getSocksProxyPassword()).isEqualTo("socksProxyPassword");
    }
}
