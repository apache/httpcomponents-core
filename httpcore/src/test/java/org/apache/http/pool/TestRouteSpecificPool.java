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

import junit.framework.Assert;

import org.apache.http.HttpConnection;
import org.junit.Test;
import org.mockito.Mockito;

public class TestRouteSpecificPool {

    private static final String ROUTE = "whatever";

    static class LocalPoolEntry extends PoolEntry<String, HttpConnection> {

        public LocalPoolEntry(final String route, final HttpConnection conn) {
            super(null, route, conn);
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

    static class LocalRoutePool extends RouteSpecificPool<String, HttpConnection, LocalPoolEntry> {

        public LocalRoutePool() {
            super(ROUTE);
        }

        @Override
        protected LocalPoolEntry createEntry(final HttpConnection conn) {
            return new LocalPoolEntry(getRoute(), conn);
        }

    }

    @Test
    public void testEmptyPool() throws Exception {
        LocalRoutePool pool = new LocalRoutePool();
        Assert.assertEquals(ROUTE, pool.getRoute());
        Assert.assertEquals(0, pool.getAllocatedCount());
        Assert.assertEquals(0, pool.getAvailableCount());
        Assert.assertEquals(0, pool.getLeasedCount());
        Assert.assertEquals(0, pool.getPendingCount());
        Assert.assertNull(pool.getLastUsed());
        Assert.assertEquals("[route: whatever][leased: 0][available: 0][pending: 0]", pool.toString());
    }

    @Test
    public void testAdd() throws Exception {
        LocalRoutePool pool = new LocalRoutePool();
        HttpConnection conn = Mockito.mock(HttpConnection.class);
        PoolEntry<String, HttpConnection> entry = pool.add(conn);
        Assert.assertEquals(1, pool.getAllocatedCount());
        Assert.assertEquals(0, pool.getAvailableCount());
        Assert.assertEquals(1, pool.getLeasedCount());
        Assert.assertEquals(0, pool.getPendingCount());
        Assert.assertNotNull(entry);
        Assert.assertSame(conn, entry.getConnection());
    }

    @Test
    public void testLeaseRelease() throws Exception {
        LocalRoutePool pool = new LocalRoutePool();
        HttpConnection conn1 = Mockito.mock(HttpConnection.class);
        LocalPoolEntry entry1 = pool.add(conn1);
        HttpConnection conn2 = Mockito.mock(HttpConnection.class);
        LocalPoolEntry entry2 = pool.add(conn2);
        HttpConnection conn3 = Mockito.mock(HttpConnection.class);
        LocalPoolEntry entry3 = pool.add(conn3);

        Assert.assertNotNull(entry1);
        Assert.assertNotNull(entry2);
        Assert.assertNotNull(entry3);

        Assert.assertEquals(3, pool.getAllocatedCount());
        Assert.assertEquals(0, pool.getAvailableCount());
        Assert.assertEquals(3, pool.getLeasedCount());
        Assert.assertEquals(0, pool.getPendingCount());

        pool.free(entry1, true);
        pool.free(entry2, false);
        pool.free(entry3, true);

        Assert.assertEquals(2, pool.getAllocatedCount());
        Assert.assertEquals(2, pool.getAvailableCount());
        Assert.assertEquals(0, pool.getLeasedCount());
        Assert.assertEquals(0, pool.getPendingCount());

        Assert.assertSame(entry1, pool.getLastUsed());

        Assert.assertNotNull(pool.getFree(null));
        Assert.assertNotNull(pool.getFree(null));
        Assert.assertNull(pool.getFree(null));

        Assert.assertEquals(2, pool.getAllocatedCount());
        Assert.assertEquals(0, pool.getAvailableCount());
        Assert.assertEquals(2, pool.getLeasedCount());
        Assert.assertEquals(0, pool.getPendingCount());
    }

    @Test
    public void testLeaseOrder() throws Exception {
        LocalRoutePool pool = new LocalRoutePool();
        HttpConnection conn1 = Mockito.mock(HttpConnection.class);
        LocalPoolEntry entry1 = pool.add(conn1);
        HttpConnection conn2 = Mockito.mock(HttpConnection.class);
        LocalPoolEntry entry2 = pool.add(conn2);
        HttpConnection conn3 = Mockito.mock(HttpConnection.class);
        LocalPoolEntry entry3 = pool.add(conn3);

        Assert.assertNotNull(entry1);
        Assert.assertNotNull(entry2);
        Assert.assertNotNull(entry3);

        Assert.assertEquals(3, pool.getAllocatedCount());
        Assert.assertEquals(0, pool.getAvailableCount());
        Assert.assertEquals(3, pool.getLeasedCount());
        Assert.assertEquals(0, pool.getPendingCount());

        pool.free(entry1, true);
        pool.free(entry2, true);
        pool.free(entry3, true);

        Assert.assertSame(entry1, pool.getLastUsed());

        Assert.assertSame(entry3, pool.getFree(null));
        Assert.assertSame(entry2, pool.getFree(null));
        Assert.assertSame(entry1, pool.getFree(null));
    }

    @Test
    public void testLeaseReleaseStateful() throws Exception {
        LocalRoutePool pool = new LocalRoutePool();
        HttpConnection conn1 = Mockito.mock(HttpConnection.class);
        LocalPoolEntry entry1 = pool.add(conn1);
        HttpConnection conn2 = Mockito.mock(HttpConnection.class);
        LocalPoolEntry entry2 = pool.add(conn2);
        HttpConnection conn3 = Mockito.mock(HttpConnection.class);
        LocalPoolEntry entry3 = pool.add(conn3);

        Assert.assertNotNull(entry1);
        Assert.assertNotNull(entry2);
        Assert.assertNotNull(entry3);

        Assert.assertEquals(3, pool.getAllocatedCount());
        Assert.assertEquals(0, pool.getAvailableCount());
        Assert.assertEquals(3, pool.getLeasedCount());
        Assert.assertEquals(0, pool.getPendingCount());

        entry2.setState(Boolean.FALSE);
        pool.free(entry1, true);
        pool.free(entry2, true);
        pool.free(entry3, true);

        Assert.assertSame(entry2, pool.getFree(Boolean.FALSE));
        Assert.assertSame(entry3, pool.getFree(Boolean.FALSE));
        Assert.assertSame(entry1, pool.getFree(null));
        Assert.assertSame(null, pool.getFree(null));

        entry1.setState(Boolean.TRUE);
        entry2.setState(Boolean.FALSE);
        entry3.setState(Boolean.TRUE);
        pool.free(entry1, true);
        pool.free(entry2, true);
        pool.free(entry3, true);

        Assert.assertSame(null, pool.getFree(null));
        Assert.assertSame(entry2, pool.getFree(Boolean.FALSE));
        Assert.assertSame(null, pool.getFree(Boolean.FALSE));
        Assert.assertSame(entry3, pool.getFree(Boolean.TRUE));
        Assert.assertSame(entry1, pool.getFree(Boolean.TRUE));
        Assert.assertSame(null, pool.getFree(Boolean.TRUE));
    }

    @Test(expected=IllegalStateException.class)
    public void testReleaseInvalidEntry() throws Exception {
        LocalRoutePool pool = new LocalRoutePool();
        HttpConnection conn = Mockito.mock(HttpConnection.class);
        LocalPoolEntry entry = new LocalPoolEntry(ROUTE, conn);
        pool.free(entry, true);
    }

    @Test
    public void testRemove() throws Exception {
        LocalRoutePool pool = new LocalRoutePool();
        HttpConnection conn1 = Mockito.mock(HttpConnection.class);
        LocalPoolEntry entry1 = pool.add(conn1);
        HttpConnection conn2 = Mockito.mock(HttpConnection.class);
        LocalPoolEntry entry2 = pool.add(conn2);
        HttpConnection conn3 = Mockito.mock(HttpConnection.class);
        LocalPoolEntry entry3 = pool.add(conn3);

        Assert.assertNotNull(entry1);
        Assert.assertNotNull(entry2);
        Assert.assertNotNull(entry3);

        Assert.assertEquals(3, pool.getAllocatedCount());
        Assert.assertEquals(0, pool.getAvailableCount());
        Assert.assertEquals(3, pool.getLeasedCount());
        Assert.assertEquals(0, pool.getPendingCount());

        Assert.assertTrue(pool.remove(entry2));
        Assert.assertFalse(pool.remove(entry2));

        Assert.assertEquals(2, pool.getAllocatedCount());
        Assert.assertEquals(0, pool.getAvailableCount());
        Assert.assertEquals(2, pool.getLeasedCount());
        Assert.assertEquals(0, pool.getPendingCount());

        pool.free(entry1, true);
        pool.free(entry3, true);

        Assert.assertEquals(2, pool.getAllocatedCount());
        Assert.assertEquals(2, pool.getAvailableCount());
        Assert.assertEquals(0, pool.getLeasedCount());
        Assert.assertEquals(0, pool.getPendingCount());

        Assert.assertTrue(pool.remove(entry1));
        Assert.assertTrue(pool.remove(entry3));

        Assert.assertEquals(0, pool.getAllocatedCount());
        Assert.assertEquals(0, pool.getAvailableCount());
        Assert.assertEquals(0, pool.getLeasedCount());
        Assert.assertEquals(0, pool.getPendingCount());
    }

    @Test(expected=IllegalArgumentException.class)
    public void testReleaseInvalid() throws Exception {
        LocalRoutePool pool = new LocalRoutePool();
        pool.free(null, true);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testRemoveInvalid() throws Exception {
        LocalRoutePool pool = new LocalRoutePool();
        pool.remove(null);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testWaitingThreadQueuing() throws Exception {
        LocalRoutePool pool = new LocalRoutePool();
        PoolEntryFuture<LocalPoolEntry> future1 = Mockito.mock(PoolEntryFuture.class);
        PoolEntryFuture<LocalPoolEntry> future2 = Mockito.mock(PoolEntryFuture.class);

        Assert.assertEquals(0, pool.getPendingCount());
        pool.queue(future1);
        Assert.assertEquals(1, pool.getPendingCount());
        pool.queue(null);
        Assert.assertEquals(1, pool.getPendingCount());
        pool.queue(future2);
        Assert.assertEquals(2, pool.getPendingCount());
        Assert.assertSame(future1, pool.nextPending());
        pool.unqueue(future1);
        Assert.assertEquals(1, pool.getPendingCount());
        Assert.assertSame(future2, pool.nextPending());
        pool.unqueue(null);
        Assert.assertEquals(0, pool.getPendingCount());
        pool.unqueue(future2);
        Assert.assertNull(pool.nextPending());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testShutdown() throws Exception {
        LocalRoutePool pool = new LocalRoutePool();
        HttpConnection conn1 = Mockito.mock(HttpConnection.class);
        LocalPoolEntry entry1 = pool.add(conn1);
        HttpConnection conn2 = Mockito.mock(HttpConnection.class);
        LocalPoolEntry entry2 = pool.add(conn2);

        PoolEntryFuture<LocalPoolEntry> future1 = Mockito.mock(PoolEntryFuture.class);
        pool.queue(future1);

        Assert.assertNotNull(entry1);
        Assert.assertNotNull(entry2);

        pool.free(entry1, true);

        Assert.assertEquals(2, pool.getAllocatedCount());
        Assert.assertEquals(1, pool.getAvailableCount());
        Assert.assertEquals(1, pool.getLeasedCount());
        Assert.assertEquals(1, pool.getPendingCount());

        pool.shutdown();

        Assert.assertEquals(0, pool.getAllocatedCount());
        Assert.assertEquals(0, pool.getAvailableCount());
        Assert.assertEquals(0, pool.getLeasedCount());
        Assert.assertEquals(0, pool.getPendingCount());

        Mockito.verify(future1).cancel(true);
        Mockito.verify(conn2).close();
        Mockito.verify(conn1).close();
    }

}
