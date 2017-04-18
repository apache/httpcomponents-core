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

import org.apache.hc.core5.io.GracefullyCloseable;
import org.apache.hc.core5.io.ShutdownType;
import org.apache.hc.core5.util.TimeValue;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class TestRouteSpecificPool {

    private static final String ROUTE = "whatever";

    @Test
    public void testEmptyPool() throws Exception {
        final RoutePool<String, GracefullyCloseable> pool = new RoutePool<>("whatever");
        Assert.assertEquals(ROUTE, pool.getRoute());
        Assert.assertEquals(0, pool.getAllocatedCount());
        Assert.assertEquals(0, pool.getAvailableCount());
        Assert.assertEquals(0, pool.getLeasedCount());
        Assert.assertNull(pool.getLastUsed());
        Assert.assertEquals("[route: whatever][leased: 0][available: 0]", pool.toString());
    }

    @Test
    public void testAdd() throws Exception {
        final RoutePool<String, GracefullyCloseable> pool = new RoutePool<>("whatever");
        final GracefullyCloseable conn = Mockito.mock(GracefullyCloseable.class);
        final PoolEntry<String, GracefullyCloseable> entry = pool.createEntry(TimeValue.ZERO_MILLISECONDS);
        entry.assignConnection(conn);
        Assert.assertEquals(1, pool.getAllocatedCount());
        Assert.assertEquals(0, pool.getAvailableCount());
        Assert.assertEquals(1, pool.getLeasedCount());
        Assert.assertNotNull(entry);
        Assert.assertSame(conn, entry.getConnection());
    }

    @Test
    public void testLeaseRelease() throws Exception {
        final RoutePool<String, GracefullyCloseable> pool = new RoutePool<>("whatever");
        final GracefullyCloseable conn1 = Mockito.mock(GracefullyCloseable.class);
        final PoolEntry<String, GracefullyCloseable> entry1 = pool.createEntry(TimeValue.ZERO_MILLISECONDS);
        entry1.assignConnection(conn1);
        final GracefullyCloseable conn2 = Mockito.mock(GracefullyCloseable.class);
        final PoolEntry<String, GracefullyCloseable> entry2 = pool.createEntry(TimeValue.ZERO_MILLISECONDS);
        entry2.assignConnection(conn2);
        final GracefullyCloseable conn3 = Mockito.mock(GracefullyCloseable.class);
        final PoolEntry<String, GracefullyCloseable> entry3 = pool.createEntry(TimeValue.ZERO_MILLISECONDS);
        entry3.assignConnection(conn3);

        Assert.assertNotNull(entry1);
        Assert.assertNotNull(entry2);
        Assert.assertNotNull(entry3);

        Assert.assertEquals(3, pool.getAllocatedCount());
        Assert.assertEquals(0, pool.getAvailableCount());
        Assert.assertEquals(3, pool.getLeasedCount());

        pool.free(entry1, true);
        pool.free(entry2, false);
        pool.free(entry3, true);

        Assert.assertEquals(2, pool.getAllocatedCount());
        Assert.assertEquals(2, pool.getAvailableCount());
        Assert.assertEquals(0, pool.getLeasedCount());

        Assert.assertSame(entry1, pool.getLastUsed());

        Assert.assertNotNull(pool.getFree(null));
        Assert.assertNotNull(pool.getFree(null));
        Assert.assertNull(pool.getFree(null));

        Assert.assertEquals(2, pool.getAllocatedCount());
        Assert.assertEquals(0, pool.getAvailableCount());
        Assert.assertEquals(2, pool.getLeasedCount());
    }

    @Test
    public void testLeaseOrder() throws Exception {
        final RoutePool<String, GracefullyCloseable> pool = new RoutePool<>("whatever");
        final GracefullyCloseable conn1 = Mockito.mock(GracefullyCloseable.class);
        final PoolEntry<String, GracefullyCloseable> entry1 = pool.createEntry(TimeValue.ZERO_MILLISECONDS);
        entry1.assignConnection(conn1);
        final GracefullyCloseable conn2 = Mockito.mock(GracefullyCloseable.class);
        final PoolEntry<String, GracefullyCloseable> entry2 = pool.createEntry(TimeValue.ZERO_MILLISECONDS);
        entry2.assignConnection(conn2);
        final GracefullyCloseable conn3 = Mockito.mock(GracefullyCloseable.class);
        final PoolEntry<String, GracefullyCloseable> entry3 = pool.createEntry(TimeValue.ZERO_MILLISECONDS);
        entry3.assignConnection(conn3);

        Assert.assertNotNull(entry1);
        Assert.assertNotNull(entry2);
        Assert.assertNotNull(entry3);

        Assert.assertEquals(3, pool.getAllocatedCount());
        Assert.assertEquals(0, pool.getAvailableCount());
        Assert.assertEquals(3, pool.getLeasedCount());

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
        final RoutePool<String, GracefullyCloseable> pool = new RoutePool<>("whatever");
        final GracefullyCloseable conn1 = Mockito.mock(GracefullyCloseable.class);
        final PoolEntry<String, GracefullyCloseable> entry1 = pool.createEntry(TimeValue.ZERO_MILLISECONDS);
        entry1.assignConnection(conn1);
        final GracefullyCloseable conn2 = Mockito.mock(GracefullyCloseable.class);
        final PoolEntry<String, GracefullyCloseable> entry2 = pool.createEntry(TimeValue.ZERO_MILLISECONDS);
        entry2.assignConnection(conn2);
        final GracefullyCloseable conn3 = Mockito.mock(GracefullyCloseable.class);
        final PoolEntry<String, GracefullyCloseable> entry3 = pool.createEntry(TimeValue.ZERO_MILLISECONDS);
        entry3.assignConnection(conn3);

        Assert.assertNotNull(entry1);
        Assert.assertNotNull(entry2);
        Assert.assertNotNull(entry3);

        Assert.assertEquals(3, pool.getAllocatedCount());
        Assert.assertEquals(0, pool.getAvailableCount());
        Assert.assertEquals(3, pool.getLeasedCount());

        entry2.updateState(Boolean.FALSE);
        pool.free(entry1, true);
        pool.free(entry2, true);
        pool.free(entry3, true);

        Assert.assertSame(entry2, pool.getFree(Boolean.FALSE));
        Assert.assertSame(entry3, pool.getFree(Boolean.FALSE));
        Assert.assertSame(entry1, pool.getFree(null));
        Assert.assertSame(null, pool.getFree(null));

        entry1.updateState(Boolean.TRUE);
        entry2.updateState(Boolean.FALSE);
        entry3.updateState(Boolean.TRUE);
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
        final RoutePool<String, GracefullyCloseable> pool = new RoutePool<>("whatever");
        final GracefullyCloseable conn = Mockito.mock(GracefullyCloseable.class);
        final PoolEntry<String, GracefullyCloseable> entry = new PoolEntry<>(ROUTE);
        pool.free(entry, true);
    }

    @Test
    public void testRemove() throws Exception {
        final RoutePool<String, GracefullyCloseable> pool = new RoutePool<>("whatever");
        final GracefullyCloseable conn1 = Mockito.mock(GracefullyCloseable.class);
        final PoolEntry<String, GracefullyCloseable> entry1 = pool.createEntry(TimeValue.ZERO_MILLISECONDS);
        entry1.assignConnection(conn1);
        final GracefullyCloseable conn2 = Mockito.mock(GracefullyCloseable.class);
        final PoolEntry<String, GracefullyCloseable> entry2 = pool.createEntry(TimeValue.ZERO_MILLISECONDS);
        entry2.assignConnection(conn2);
        final GracefullyCloseable conn3 = Mockito.mock(GracefullyCloseable.class);
        final PoolEntry<String, GracefullyCloseable> entry3 = pool.createEntry(TimeValue.ZERO_MILLISECONDS);
        entry3.assignConnection(conn3);

        Assert.assertNotNull(entry1);
        Assert.assertNotNull(entry2);
        Assert.assertNotNull(entry3);

        Assert.assertEquals(3, pool.getAllocatedCount());
        Assert.assertEquals(0, pool.getAvailableCount());
        Assert.assertEquals(3, pool.getLeasedCount());

        Assert.assertTrue(pool.remove(entry2));
        Assert.assertFalse(pool.remove(entry2));

        Assert.assertEquals(2, pool.getAllocatedCount());
        Assert.assertEquals(0, pool.getAvailableCount());
        Assert.assertEquals(2, pool.getLeasedCount());

        pool.free(entry1, true);
        pool.free(entry3, true);

        Assert.assertEquals(2, pool.getAllocatedCount());
        Assert.assertEquals(2, pool.getAvailableCount());
        Assert.assertEquals(0, pool.getLeasedCount());

        Assert.assertTrue(pool.remove(entry1));
        Assert.assertTrue(pool.remove(entry3));

        Assert.assertEquals(0, pool.getAllocatedCount());
        Assert.assertEquals(0, pool.getAvailableCount());
        Assert.assertEquals(0, pool.getLeasedCount());
    }

    @Test(expected=IllegalArgumentException.class)
    public void testReleaseInvalid() throws Exception {
        final RoutePool<String, GracefullyCloseable> pool = new RoutePool<>("whatever");
        pool.free(null, true);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testRemoveInvalid() throws Exception {
        final RoutePool<String, GracefullyCloseable> pool = new RoutePool<>("whatever");
        pool.remove(null);
    }

    @Test
    public void testShutdown() throws Exception {
        final RoutePool<String, GracefullyCloseable> pool = new RoutePool<>("whatever");
        final GracefullyCloseable conn1 = Mockito.mock(GracefullyCloseable.class);
        final PoolEntry<String, GracefullyCloseable> entry1 = pool.createEntry(TimeValue.ZERO_MILLISECONDS);
        entry1.assignConnection(conn1);
        final GracefullyCloseable conn2 = Mockito.mock(GracefullyCloseable.class);
        final PoolEntry<String, GracefullyCloseable> entry2 = pool.createEntry(TimeValue.ZERO_MILLISECONDS);
        entry2.assignConnection(conn2);

        Assert.assertNotNull(entry1);
        Assert.assertNotNull(entry2);

        pool.free(entry1, true);

        Assert.assertEquals(2, pool.getAllocatedCount());
        Assert.assertEquals(1, pool.getAvailableCount());
        Assert.assertEquals(1, pool.getLeasedCount());

        pool.shutdown(ShutdownType.GRACEFUL);

        Assert.assertEquals(0, pool.getAllocatedCount());
        Assert.assertEquals(0, pool.getAvailableCount());
        Assert.assertEquals(0, pool.getLeasedCount());

        Mockito.verify(conn2).shutdown(ShutdownType.GRACEFUL);
        Mockito.verify(conn1).shutdown(ShutdownType.GRACEFUL);
    }

}
