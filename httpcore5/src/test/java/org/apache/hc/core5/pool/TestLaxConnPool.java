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

import java.util.Collections;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

public class TestLaxConnPool {

    @Test
    public void testEmptyPool() throws Exception {
        final LaxConnPool<String, HttpConnection> pool = new LaxConnPool<>(2);
        final PoolStats totals = pool.getTotalStats();
        Assert.assertEquals(0, totals.getAvailable());
        Assert.assertEquals(0, totals.getLeased());
        Assert.assertEquals(0, totals.getPending());
        Assert.assertEquals(0, totals.getMax());
        Assert.assertEquals(Collections.emptySet(), pool.getRoutes());
        final PoolStats stats = pool.getStats("somehost");
        Assert.assertEquals(0, stats.getAvailable());
        Assert.assertEquals(0, stats.getLeased());
        Assert.assertEquals(0, stats.getPending());
        Assert.assertEquals(2, stats.getMax());
        Assert.assertEquals("[leased: 0][available: 0][pending: 0]", pool.toString());
    }

    @Test
    public void testInvalidConstruction() throws Exception {
        Assert.assertThrows(IllegalArgumentException.class, () ->
                new LaxConnPool<String, HttpConnection>(-1));
    }

    @Test
    public void testLeaseRelease() throws Exception {
        final HttpConnection conn1 = Mockito.mock(HttpConnection.class);
        final HttpConnection conn2 = Mockito.mock(HttpConnection.class);
        final HttpConnection conn3 = Mockito.mock(HttpConnection.class);

        final LaxConnPool<String, HttpConnection> pool = new LaxConnPool<>(2);
        final Future<PoolEntry<String, HttpConnection>> future1 = pool.lease("somehost", null);
        final Future<PoolEntry<String, HttpConnection>> future2 = pool.lease("somehost", null);
        final Future<PoolEntry<String, HttpConnection>> future3 = pool.lease("otherhost", null);

        final PoolEntry<String, HttpConnection> entry1 = future1.get();
        Assert.assertNotNull(entry1);
        entry1.assignConnection(conn1);
        final PoolEntry<String, HttpConnection> entry2 = future2.get();
        Assert.assertNotNull(entry2);
        entry2.assignConnection(conn2);
        final PoolEntry<String, HttpConnection> entry3 = future3.get();
        Assert.assertNotNull(entry3);
        entry3.assignConnection(conn3);

        pool.release(entry1, true);
        pool.release(entry2, true);
        pool.release(entry3, false);
        Mockito.verify(conn1, Mockito.never()).close(ArgumentMatchers.any());
        Mockito.verify(conn2, Mockito.never()).close(ArgumentMatchers.any());
        Mockito.verify(conn3, Mockito.times(1)).close(CloseMode.GRACEFUL);

        final PoolStats totals = pool.getTotalStats();
        Assert.assertEquals(2, totals.getAvailable());
        Assert.assertEquals(0, totals.getLeased());
        Assert.assertEquals(0, totals.getPending());
    }

    @Test
    public void testLeaseInvalid() throws Exception {
        final LaxConnPool<String, HttpConnection> pool = new LaxConnPool<>(2);
        Assert.assertThrows(NullPointerException.class, () ->
                pool.lease(null, null, Timeout.ZERO_MILLISECONDS, null));
    }

    @Test
    public void testReleaseUnknownEntry() throws Exception {
        final LaxConnPool<String, HttpConnection> pool = new LaxConnPool<>(2);
        Assert.assertThrows(IllegalStateException.class, () ->
                pool.release(new PoolEntry<>("somehost"), true));
    }

    @Test
    public void testMaxLimits() throws Exception {
        final HttpConnection conn1 = Mockito.mock(HttpConnection.class);
        final HttpConnection conn2 = Mockito.mock(HttpConnection.class);
        final HttpConnection conn3 = Mockito.mock(HttpConnection.class);

        final LaxConnPool<String, HttpConnection> pool = new LaxConnPool<>(2);
        pool.setMaxPerRoute("somehost", 2);
        pool.setMaxPerRoute("otherhost", 1);

        final Future<PoolEntry<String, HttpConnection>> future1 = pool.lease("somehost", null);
        final Future<PoolEntry<String, HttpConnection>> future2 = pool.lease("somehost", null);
        final Future<PoolEntry<String, HttpConnection>> future3 = pool.lease("otherhost", null);

        final PoolEntry<String, HttpConnection> entry1 = future1.get();
        Assert.assertNotNull(entry1);
        entry1.assignConnection(conn1);
        final PoolEntry<String, HttpConnection> entry2 = future2.get();
        Assert.assertNotNull(entry2);
        entry2.assignConnection(conn2);
        final PoolEntry<String, HttpConnection> entry3 = future3.get();
        Assert.assertNotNull(entry3);
        entry3.assignConnection(conn3);

        pool.release(entry1, true);
        pool.release(entry2, true);
        pool.release(entry3, true);

        final PoolStats totals = pool.getTotalStats();
        Assert.assertEquals(3, totals.getAvailable());
        Assert.assertEquals(0, totals.getLeased());
        Assert.assertEquals(0, totals.getPending());

        final Future<PoolEntry<String, HttpConnection>> future4 = pool.lease("somehost", null);
        final Future<PoolEntry<String, HttpConnection>> future5 = pool.lease("somehost", null);
        final Future<PoolEntry<String, HttpConnection>> future6 = pool.lease("otherhost", null);
        final Future<PoolEntry<String, HttpConnection>> future7 = pool.lease("somehost", null);
        final Future<PoolEntry<String, HttpConnection>> future8 = pool.lease("somehost", null);
        final Future<PoolEntry<String, HttpConnection>> future9 = pool.lease("otherhost", null);

        Assert.assertTrue(future4.isDone());
        final PoolEntry<String, HttpConnection> entry4 = future4.get();
        Assert.assertNotNull(entry4);
        Assert.assertSame(conn2, entry4.getConnection());

        Assert.assertTrue(future5.isDone());
        final PoolEntry<String, HttpConnection> entry5 = future5.get();
        Assert.assertNotNull(entry5);
        Assert.assertSame(conn1, entry5.getConnection());

        Assert.assertTrue(future6.isDone());
        final PoolEntry<String, HttpConnection> entry6 = future6.get();
        Assert.assertNotNull(entry6);
        Assert.assertSame(conn3, entry6.getConnection());

        Assert.assertFalse(future7.isDone());
        Assert.assertFalse(future8.isDone());
        Assert.assertFalse(future9.isDone());

        pool.release(entry4, true);
        pool.release(entry5, false);
        pool.release(entry6, true);

        Assert.assertTrue(future7.isDone());
        final PoolEntry<String, HttpConnection> entry7 = future7.get();
        Assert.assertNotNull(entry7);
        Assert.assertSame(conn2, entry7.getConnection());

        Assert.assertTrue(future8.isDone());
        final PoolEntry<String, HttpConnection> entry8 = future8.get();
        Assert.assertNotNull(entry8);
        Assert.assertNull(entry8.getConnection());

        Assert.assertTrue(future9.isDone());
        final PoolEntry<String, HttpConnection> entry9 = future9.get();
        Assert.assertNotNull(entry9);
        Assert.assertSame(conn3, entry9.getConnection());
    }

    @Test
    public void testCreateNewIfExpired() throws Exception {
        final HttpConnection conn1 = Mockito.mock(HttpConnection.class);

        final LaxConnPool<String, HttpConnection> pool = new LaxConnPool<>(2);

        final Future<PoolEntry<String, HttpConnection>> future1 = pool.lease("somehost", null);

        Assert.assertTrue(future1.isDone());
        final PoolEntry<String, HttpConnection> entry1 = future1.get();
        Assert.assertNotNull(entry1);
        entry1.assignConnection(conn1);

        entry1.updateExpiry(TimeValue.of(1, TimeUnit.MILLISECONDS));
        pool.release(entry1, true);

        Thread.sleep(200L);

        final Future<PoolEntry<String, HttpConnection>> future2 = pool.lease("somehost", null);

        Assert.assertTrue(future2.isDone());

        Mockito.verify(conn1).close(CloseMode.GRACEFUL);

        final PoolStats totals = pool.getTotalStats();
        Assert.assertEquals(0, totals.getAvailable());
        Assert.assertEquals(1, totals.getLeased());
        Assert.assertEquals(Collections.singleton("somehost"), pool.getRoutes());
        final PoolStats stats = pool.getStats("somehost");
        Assert.assertEquals(0, stats.getAvailable());
        Assert.assertEquals(1, stats.getLeased());
    }

    @Test
    public void testCloseExpired() throws Exception {
        final HttpConnection conn1 = Mockito.mock(HttpConnection.class);
        final HttpConnection conn2 = Mockito.mock(HttpConnection.class);

        final LaxConnPool<String, HttpConnection> pool = new LaxConnPool<>(2);

        final Future<PoolEntry<String, HttpConnection>> future1 = pool.lease("somehost", null);
        final Future<PoolEntry<String, HttpConnection>> future2 = pool.lease("somehost", null);

        Assert.assertTrue(future1.isDone());
        final PoolEntry<String, HttpConnection> entry1 = future1.get();
        Assert.assertNotNull(entry1);
        entry1.assignConnection(conn1);
        Assert.assertTrue(future2.isDone());
        final PoolEntry<String, HttpConnection> entry2 = future2.get();
        Assert.assertNotNull(entry2);
        entry2.assignConnection(conn2);

        entry1.updateExpiry(TimeValue.of(1, TimeUnit.MILLISECONDS));
        pool.release(entry1, true);

        Thread.sleep(200);

        entry2.updateExpiry(TimeValue.of(1000, TimeUnit.SECONDS));
        pool.release(entry2, true);

        pool.closeExpired();

        Mockito.verify(conn1).close(CloseMode.GRACEFUL);
        Mockito.verify(conn2, Mockito.never()).close(ArgumentMatchers.any());

        final PoolStats totals = pool.getTotalStats();
        Assert.assertEquals(1, totals.getAvailable());
        Assert.assertEquals(0, totals.getLeased());
        Assert.assertEquals(0, totals.getPending());
        final PoolStats stats = pool.getStats("somehost");
        Assert.assertEquals(1, stats.getAvailable());
        Assert.assertEquals(0, stats.getLeased());
        Assert.assertEquals(0, stats.getPending());
    }

    @Test
    public void testCloseIdle() throws Exception {
        final HttpConnection conn1 = Mockito.mock(HttpConnection.class);
        final HttpConnection conn2 = Mockito.mock(HttpConnection.class);

        final LaxConnPool<String, HttpConnection> pool = new LaxConnPool<>(2);

        final Future<PoolEntry<String, HttpConnection>> future1 = pool.lease("somehost", null);
        final Future<PoolEntry<String, HttpConnection>> future2 = pool.lease("somehost", null);

        Assert.assertTrue(future1.isDone());
        final PoolEntry<String, HttpConnection> entry1 = future1.get();
        Assert.assertNotNull(entry1);
        entry1.assignConnection(conn1);
        Assert.assertTrue(future2.isDone());
        final PoolEntry<String, HttpConnection> entry2 = future2.get();
        Assert.assertNotNull(entry2);
        entry2.assignConnection(conn2);

        entry1.updateState(null);
        pool.release(entry1, true);

        Thread.sleep(200L);

        entry2.updateState(null);
        pool.release(entry2, true);

        pool.closeIdle(TimeValue.of(50, TimeUnit.MILLISECONDS));

        Mockito.verify(conn1).close(CloseMode.GRACEFUL);
        Mockito.verify(conn2, Mockito.never()).close(ArgumentMatchers.any());

        PoolStats totals = pool.getTotalStats();
        Assert.assertEquals(1, totals.getAvailable());
        Assert.assertEquals(0, totals.getLeased());
        Assert.assertEquals(0, totals.getPending());
        PoolStats stats = pool.getStats("somehost");
        Assert.assertEquals(1, stats.getAvailable());
        Assert.assertEquals(0, stats.getLeased());
        Assert.assertEquals(0, stats.getPending());

        pool.closeIdle(TimeValue.of(-1, TimeUnit.MILLISECONDS));

        Mockito.verify(conn2).close(CloseMode.GRACEFUL);

        totals = pool.getTotalStats();
        Assert.assertEquals(0, totals.getAvailable());
        Assert.assertEquals(0, totals.getLeased());
        Assert.assertEquals(0, totals.getPending());
        stats = pool.getStats("somehost");
        Assert.assertEquals(0, stats.getAvailable());
        Assert.assertEquals(0, stats.getLeased());
        Assert.assertEquals(0, stats.getPending());
    }

    @Test
    public void testLeaseRequestTimeout() throws Exception {
        final HttpConnection conn1 = Mockito.mock(HttpConnection.class);

        final LaxConnPool<String, HttpConnection> pool = new LaxConnPool<>(1);

        final Future<PoolEntry<String, HttpConnection>> future1 = pool.lease("somehost", null, Timeout.ofMilliseconds(0), null);
        final Future<PoolEntry<String, HttpConnection>> future2 = pool.lease("somehost", null, Timeout.ofMilliseconds(0), null);
        final Future<PoolEntry<String, HttpConnection>> future3 = pool.lease("somehost", null, Timeout.ofMilliseconds(10), null);

        Assert.assertTrue(future1.isDone());
        final PoolEntry<String, HttpConnection> entry1 = future1.get();
        Assert.assertNotNull(entry1);
        entry1.assignConnection(conn1);
        Assert.assertFalse(future2.isDone());
        Assert.assertFalse(future3.isDone());

        Thread.sleep(100);

        pool.validatePendingRequests();

        Assert.assertFalse(future2.isDone());
        Assert.assertTrue(future3.isDone());
    }

    @Test
    public void testLeaseRequestCanceled() throws Exception {
        final LaxConnPool<String, HttpConnection> pool = new LaxConnPool<>(1);

        final Future<PoolEntry<String, HttpConnection>> future1 = pool.lease("somehost", null, Timeout.ofMilliseconds(0), null);

        Assert.assertTrue(future1.isDone());
        final PoolEntry<String, HttpConnection> entry1 = future1.get();
        Assert.assertNotNull(entry1);
        entry1.assignConnection(Mockito.mock(HttpConnection.class));

        final Future<PoolEntry<String, HttpConnection>> future2 = pool.lease("somehost", null, Timeout.ofMilliseconds(0), null);
        future2.cancel(true);

        pool.release(entry1, true);

        final PoolStats totals = pool.getTotalStats();
        Assert.assertEquals(1, totals.getAvailable());
        Assert.assertEquals(0, totals.getLeased());
    }

    @Test
    public void testGetStatsInvalid() throws Exception {
        final LaxConnPool<String, HttpConnection> pool = new LaxConnPool<>(2);
        Assert.assertThrows(NullPointerException.class, () ->
                pool.getStats(null));
    }

    @Test
    public void testSetMaxInvalid() throws Exception {
        final LaxConnPool<String, HttpConnection> pool = new LaxConnPool<>(2);
        Assert.assertThrows(NullPointerException.class, () -> pool.setMaxPerRoute(null, 1));
        Assert.assertThrows(IllegalArgumentException.class, () -> pool.setDefaultMaxPerRoute(-1));
    }

    @Test
    public void testShutdown() throws Exception {
        final LaxConnPool<String, HttpConnection> pool = new LaxConnPool<>(2);
        pool.close(CloseMode.GRACEFUL);
        Assert.assertThrows(IllegalStateException.class, () -> pool.lease("somehost", null));
        // Ignored if shut down
        pool.release(new PoolEntry<>("somehost"), true);
    }

}
