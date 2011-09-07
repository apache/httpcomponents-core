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
package org.apache.http.pool;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import junit.framework.Assert;

import org.apache.http.HttpConnection;
import org.junit.Test;
import org.mockito.Mockito;

public class TestPoolEntry {

    static class MockPoolEntry extends PoolEntry<String, HttpConnection> {

        public MockPoolEntry(final String route,
                long timeToLive, final TimeUnit tunit) {
            super(null, route, Mockito.mock(HttpConnection.class), timeToLive, tunit);
        }

        public MockPoolEntry(final String route, final HttpConnection conn,
                long timeToLive, final TimeUnit tunit) {
            super(null, route, conn, timeToLive, tunit);
        }

        @Override
        public void close() {
            try {
                getConnection().close();
            } catch (IOException ignore) {
            }
        }

        @Override
        public boolean isClosed() {
            return !getConnection().isOpen();
        }

    }

    @Test
    public void testBasics() throws Exception {
        MockPoolEntry entry1 = new MockPoolEntry("route1", 10L, TimeUnit.MILLISECONDS);
        long now = System.currentTimeMillis();
        Assert.assertEquals("route1", entry1.getRoute());
        Assert.assertTrue(now >= entry1.getCreated());
        Assert.assertEquals(entry1.getValidUnit(), entry1.getExpiry());
        Assert.assertEquals(entry1.getCreated() + 10L, entry1.getValidUnit());
    }

    @Test
    public void testInvalidConstruction() throws Exception {
        try {
            new MockPoolEntry(null, Mockito.mock(HttpConnection.class), 0L, TimeUnit.MILLISECONDS);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException expected) {
        }
        try {
            new MockPoolEntry("stuff", null, 0L, TimeUnit.MILLISECONDS);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException expected) {
        }
        try {
            new MockPoolEntry("stuff", Mockito.mock(HttpConnection.class), 0L, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testValidInfinitely() throws Exception {
        MockPoolEntry entry1 = new MockPoolEntry("route1", 0L, TimeUnit.MILLISECONDS);
        Assert.assertEquals(Long.MAX_VALUE, entry1.getValidUnit());
        Assert.assertEquals(entry1.getValidUnit(), entry1.getExpiry());
    }

    @Test
    public void testExpiry() throws Exception {
        MockPoolEntry entry1 = new MockPoolEntry("route1", 0L, TimeUnit.MILLISECONDS);
        Assert.assertEquals(Long.MAX_VALUE, entry1.getExpiry());
        entry1.updateExpiry(50L, TimeUnit.MILLISECONDS);
        Assert.assertEquals(entry1.getUpdated() + 50L, entry1.getExpiry());
        entry1.updateExpiry(0L, TimeUnit.MILLISECONDS);
        Assert.assertEquals(Long.MAX_VALUE, entry1.getExpiry());

        MockPoolEntry entry2 = new MockPoolEntry("route1", 100L, TimeUnit.MILLISECONDS);
        Assert.assertEquals(entry2.getCreated() + 100L, entry2.getExpiry());
        entry2.updateExpiry(150L, TimeUnit.MILLISECONDS);
        Assert.assertEquals(entry2.getCreated() + 100L, entry2.getExpiry());
        entry2.updateExpiry(50L, TimeUnit.MILLISECONDS);
        Assert.assertEquals(entry2.getUpdated() + 50L, entry2.getExpiry());
    }

    @Test(expected=IllegalArgumentException.class)
    public void testInvalidExpiry() throws Exception {
        MockPoolEntry entry1 = new MockPoolEntry("route1", 0L, TimeUnit.MILLISECONDS);
        entry1.updateExpiry(50L, null);
    }

}
