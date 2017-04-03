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
package org.apache.hc.core5.pool;

import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.io.ShutdownType;
import org.apache.hc.core5.util.TimeValue;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class TestPoolEntry {

    @Test
    public void testBasics() throws Exception {
        final PoolEntry<String, HttpConnection> entry1 = new PoolEntry<>("route1", TimeValue.of(10L, TimeUnit.MILLISECONDS));

        Assert.assertEquals("route1", entry1.getRoute());
        Assert.assertEquals(0, entry1.getUpdated());
        Assert.assertEquals(0, entry1.getExpiry());

        entry1.assignConnection(Mockito.mock(HttpConnection.class));
        final long now = System.currentTimeMillis();
        Assert.assertEquals("route1", entry1.getRoute());
        Assert.assertTrue(now >= entry1.getUpdated());
        Assert.assertEquals(entry1.getValidityDeadline(), entry1.getExpiry());
        Assert.assertEquals(entry1.getUpdated() + 10L, entry1.getValidityDeadline());

        entry1.discardConnection(ShutdownType.IMMEDIATE);
        Assert.assertEquals(0, entry1.getUpdated());
        Assert.assertEquals(0, entry1.getExpiry());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidConstruction() throws Exception {
        new PoolEntry<String, HttpConnection>(null);
    }

    @Test
    public void testValidInfinitely() throws Exception {
        final PoolEntry<String, HttpConnection> entry1 = new PoolEntry<>("route1", TimeValue.ZERO_MILLIS);
        entry1.assignConnection(Mockito.mock(HttpConnection.class));
        Assert.assertEquals(Long.MAX_VALUE, entry1.getValidityDeadline());
        Assert.assertEquals(entry1.getValidityDeadline(), entry1.getExpiry());
    }

    @Test
    public void testExpiry() throws Exception {
        final PoolEntry<String, HttpConnection> entry1 = new PoolEntry<>("route1", TimeValue.ZERO_MILLIS);
        entry1.assignConnection(Mockito.mock(HttpConnection.class));
        Assert.assertEquals(Long.MAX_VALUE, entry1.getExpiry());
        entry1.updateExpiry(TimeValue.of(50L, TimeUnit.MILLISECONDS));
        Assert.assertEquals(entry1.getUpdated() + 50L, entry1.getExpiry());
        entry1.updateExpiry(TimeValue.ZERO_MILLIS);
        Assert.assertEquals(Long.MAX_VALUE, entry1.getExpiry());

        final PoolEntry<String, HttpConnection> entry2 = new PoolEntry<>("route1", TimeValue.of(100L, TimeUnit.MILLISECONDS));
        entry2.assignConnection(Mockito.mock(HttpConnection.class));
        Assert.assertEquals(entry2.getUpdated() + 100L, entry2.getExpiry());
        entry2.updateExpiry(TimeValue.of(150L, TimeUnit.MILLISECONDS));
        Assert.assertEquals(entry2.getUpdated() + 100L, entry2.getExpiry());
        entry2.updateExpiry(TimeValue.of(50L, TimeUnit.MILLISECONDS));
        Assert.assertEquals(entry2.getUpdated() + 50L, entry2.getExpiry());
    }

    @Test(expected=IllegalArgumentException.class)
    public void testInvalidExpiry() throws Exception {
        final PoolEntry<String, HttpConnection> entry = new PoolEntry<>("route1", TimeValue.of(0L, TimeUnit.MILLISECONDS));
        entry.updateExpiry(null);
    }

    @Test
    public void testExpiryDoesNotOverflow() {
        final PoolEntry<String, HttpConnection> entry = new PoolEntry<>("route1", TimeValue.of(Long.MAX_VALUE, TimeUnit.MILLISECONDS));
        entry.assignConnection(Mockito.mock(HttpConnection.class));
        Assert.assertEquals(Long.MAX_VALUE, entry.getValidityDeadline());
    }

}
