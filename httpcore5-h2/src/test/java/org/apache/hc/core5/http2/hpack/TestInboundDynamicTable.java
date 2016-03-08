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

import org.junit.Assert;
import org.junit.Test;

public class TestInboundDynamicTable {

    @Test
    public void testBasics() throws Exception {

        final InboundDynamicTable table = new InboundDynamicTable();
        Assert.assertEquals(Integer.MAX_VALUE, table.getMaxSize());
        Assert.assertEquals(0, table.getCurrentSize());

        final HPackHeader header1 = new HPackHeader("h", "1");
        table.add(header1);
        Assert.assertEquals(1, table.dynamicLength());
        Assert.assertEquals(61, table.staticLength());
        Assert.assertEquals(62, table.length());
        Assert.assertSame(header1, table.getHeader(62));
        Assert.assertEquals(34, table.getCurrentSize());
    }

    @Test
    public void testEviction() throws Exception {

        final InboundDynamicTable table = new InboundDynamicTable();

        table.add(new HPackHeader("h", "1"));
        table.add(new HPackHeader("h", "2"));

        Assert.assertEquals(68, table.getCurrentSize());

        table.setMaxSize(256);
        Assert.assertEquals(68, table.getCurrentSize());
        table.setMaxSize(67);
        Assert.assertEquals(34, table.getCurrentSize());
        table.setMaxSize(10);
        Assert.assertEquals(0, table.getCurrentSize());
    }

}

