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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestOutboundDynamicTable {

    @Test
    public void testBasics() throws Exception {

        final OutboundDynamicTable table = new OutboundDynamicTable();
        Assertions.assertEquals(Integer.MAX_VALUE, table.getMaxSize());
        Assertions.assertEquals(0, table.getCurrentSize());

        final HPackHeader header1 = new HPackHeader("h", "1");
        table.add(header1);
        Assertions.assertEquals(1, table.dynamicLength());
        Assertions.assertEquals(61, table.staticLength());
        Assertions.assertEquals(62, table.length());
        Assertions.assertSame(header1, table.getHeader(62));
        Assertions.assertEquals(34, table.getCurrentSize());
    }

    @Test
    public void testEviction() throws Exception {

        final OutboundDynamicTable table = new OutboundDynamicTable();

        table.add(new HPackHeader("h", "1"));
        table.add(new HPackHeader("h", "2"));

        Assertions.assertEquals(68, table.getCurrentSize());

        table.setMaxSize(256);
        Assertions.assertEquals(68, table.getCurrentSize());
        table.setMaxSize(67);
        Assertions.assertEquals(34, table.getCurrentSize());
        table.setMaxSize(10);
        Assertions.assertEquals(0, table.getCurrentSize());
    }

}

