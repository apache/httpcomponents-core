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

package org.apache.hc.core5.http2.hpack;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TestOutboundDynamicTable {

    @Test
    void testBasics() {

        final OutboundDynamicTable table = new OutboundDynamicTable();
        Assertions.assertThat(table.getMaxSize()).isEqualTo(Integer.MAX_VALUE);
        Assertions.assertThat(table.getCurrentSize()).isEqualTo(0);

        final HPackHeader header1 = new HPackHeader("h", "1");
        table.add(header1);
        Assertions.assertThat(table.dynamicLength()).isEqualTo(1);
        Assertions.assertThat(table.staticLength()).isEqualTo(61);
        Assertions.assertThat(table.length()).isEqualTo(62);
        Assertions.assertThat(table.getHeader(62)).isSameAs(header1);
        Assertions.assertThat(table.getCurrentSize()).isEqualTo(34);
    }

    @Test
    void testEviction() {

        final OutboundDynamicTable table = new OutboundDynamicTable();

        table.add(new HPackHeader("h", "1"));
        table.add(new HPackHeader("h", "2"));

        Assertions.assertThat(table.getCurrentSize()).isEqualTo(68);

        table.setMaxSize(256);
        Assertions.assertThat(table.getCurrentSize()).isEqualTo(68);
        table.setMaxSize(67);
        Assertions.assertThat(table.getCurrentSize()).isEqualTo(34);
        table.setMaxSize(10);
        Assertions.assertThat(table.getCurrentSize()).isEqualTo(0);
    }

}

