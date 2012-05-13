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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import junit.framework.Assert;

import org.apache.http.HttpConnection;
import org.junit.Test;
import org.mockito.Mockito;

public class TestConnPool {

    private static final int GRACE_PERIOD = 10000;

    static interface LocalConnFactory extends ConnFactory<String, HttpConnection> {
    }

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

    static class LocalConnPool extends AbstractConnPool<String, HttpConnection, LocalPoolEntry> {

        public LocalConnPool(
                final ConnFactory<String, HttpConnection> connFactory,
                int defaultMaxPerRoute, int maxTotal) {
            super(connFactory, defaultMaxPerRoute, maxTotal);
        }

        @Override
        protected LocalPoolEntry createEntry(final String route, final HttpConnection conn) {
            return new LocalPoolEntry(route, conn);
        }

    }

    @Test
    public void testEmptyPool() throws Exception {
        LocalConnFactory connFactory = Mockito.mock(LocalConnFactory.class);
        LocalConnPool pool = new LocalConnPool(connFactory, 2, 10);
        pool.setDefaultMaxPerRoute(5);
        pool.setMaxPerRoute("somehost", 3);
        PoolStats totals = pool.getTotalStats();
        Assert.assertEquals(0, totals.getAvailable());
        Assert.assertEquals(0, totals.getLeased());
        Assert.assertEquals(10, totals.getMax());
        PoolStats stats = pool.getStats("somehost");
        Assert.assertEquals(0, stats.getAvailable());
        Assert.assertEquals(0, stats.getLeased());
        Assert.assertEquals(3, stats.getMax());
        Assert.assertEquals("[leased: []][available: []][pending: []]", pool.toString());
    }

    @Test
    public void testInvalidConstruction() throws Exception {
        LocalConnFactory connFactory = Mockito.mock(LocalConnFactory.class);
        try {
            new LocalConnPool(connFactory, -1, 1);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException expected) {
        }
        try {
            new LocalConnPool(connFactory, 1, -1);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testLeaseRelease() throws Exception {
        HttpConnection conn1 = Mockito.mock(HttpConnection.class);
        Mockito.when(conn1.isOpen()).thenReturn(true);
        HttpConnection conn2 = Mockito.mock(HttpConnection.class);
        Mockito.when(conn2.isOpen()).thenReturn(true);

        LocalConnFactory connFactory = Mockito.mock(LocalConnFactory.class);
        Mockito.when(connFactory.create(Mockito.eq("somehost"))).thenReturn(conn1);
        Mockito.when(connFactory.create(Mockito.eq("otherhost"))).thenReturn(conn2);

        LocalConnPool pool = new LocalConnPool(connFactory, 2, 10);
        Future<LocalPoolEntry> future1 = pool.lease("somehost", null);
        LocalPoolEntry entry1 = future1.get(1, TimeUnit.SECONDS);
        Assert.assertNotNull(entry1);
        Future<LocalPoolEntry> future2 = pool.lease("somehost", null);
        LocalPoolEntry entry2 = future2.get(1, TimeUnit.SECONDS);
        Assert.assertNotNull(entry2);
        Future<LocalPoolEntry> future3 = pool.lease("otherhost", null);
        LocalPoolEntry entry3 = future3.get(1, TimeUnit.SECONDS);
        Assert.assertNotNull(entry3);

        PoolStats totals = pool.getTotalStats();
        Assert.assertEquals(0, totals.getAvailable());
        Assert.assertEquals(3, totals.getLeased());

        LocalPoolEntry entry = future1.get();
        Assert.assertSame(entry1, entry);

        pool.release(entry1, true);
        pool.release(entry2, true);
        pool.release(entry3, false);
        Mockito.verify(conn1, Mockito.never()).close();
        Mockito.verify(conn2, Mockito.times(1)).close();

        totals = pool.getTotalStats();
        Assert.assertEquals(2, totals.getAvailable());
        Assert.assertEquals(0, totals.getLeased());
    }

    @Test
    public void testLeaseIllegal() throws Exception {
        LocalConnFactory connFactory = Mockito.mock(LocalConnFactory.class);
        LocalConnPool pool = new LocalConnPool(connFactory, 2, 10);
        try {
            pool.lease(null, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testReleaseUnknownEntry() throws Exception {
        LocalConnFactory connFactory = Mockito.mock(LocalConnFactory.class);
        LocalConnPool pool = new LocalConnPool(connFactory, 2, 10);
        pool.release(new LocalPoolEntry("somehost", Mockito.mock(HttpConnection.class)), true);
    }

    static class GetPoolEntryThread extends Thread {

        private final Future<LocalPoolEntry> future;
        private final long time;
        private final TimeUnit tunit;

        private volatile LocalPoolEntry entry;
        private volatile Exception ex;

        GetPoolEntryThread(final Future<LocalPoolEntry> future, final long time, final TimeUnit tunit) {
            super();
            this.future = future;
            this.time = time;
            this.tunit = tunit;
            setDaemon(true);
        }

        GetPoolEntryThread(final Future<LocalPoolEntry> future) {
            this(future, 1000, TimeUnit.SECONDS);
        }

        @Override
        public void run() {
            try {
                this.entry = this.future.get(this.time, this.tunit);
            } catch (Exception ex) {
                this.ex = ex;
            }
        }

        public boolean isDone() {
            return this.future.isDone();
        }

        public LocalPoolEntry getEntry() {
            return this.entry;
        }

        public Exception getException() {
            return this.ex;
        }

    }

    @Test
    public void testMaxLimits() throws Exception {
        LocalConnFactory connFactory = Mockito.mock(LocalConnFactory.class);

        HttpConnection conn1 = Mockito.mock(HttpConnection.class);
        Mockito.when(conn1.isOpen()).thenReturn(true);
        Mockito.when(connFactory.create(Mockito.eq("somehost"))).thenReturn(conn1);

        HttpConnection conn2 = Mockito.mock(HttpConnection.class);
        Mockito.when(conn2.isOpen()).thenReturn(true);
        Mockito.when(connFactory.create(Mockito.eq("otherhost"))).thenReturn(conn2);

        LocalConnPool pool = new LocalConnPool(connFactory, 2, 10);
        pool.setMaxPerRoute("somehost", 2);
        pool.setMaxPerRoute("otherhost", 1);
        pool.setMaxTotal(3);

        Future<LocalPoolEntry> future1 = pool.lease("somehost", null);
        GetPoolEntryThread t1 = new GetPoolEntryThread(future1);
        t1.start();
        Future<LocalPoolEntry> future2 = pool.lease("somehost", null);
        GetPoolEntryThread t2 = new GetPoolEntryThread(future2);
        t2.start();
        Future<LocalPoolEntry> future3 = pool.lease("otherhost", null);
        GetPoolEntryThread t3 = new GetPoolEntryThread(future3);
        t3.start();

        t1.join(GRACE_PERIOD);
        Assert.assertTrue(future1.isDone());
        LocalPoolEntry entry1 = t1.getEntry();
        Assert.assertNotNull(entry1);
        t2.join(GRACE_PERIOD);
        Assert.assertTrue(future2.isDone());
        LocalPoolEntry entry2 = t2.getEntry();
        Assert.assertNotNull(entry2);
        t3.join(GRACE_PERIOD);
        Assert.assertTrue(future3.isDone());
        LocalPoolEntry entry3 = t3.getEntry();
        Assert.assertNotNull(entry3);

        pool.release(entry1, true);
        pool.release(entry2, true);
        pool.release(entry3, true);

        PoolStats totals = pool.getTotalStats();
        Assert.assertEquals(3, totals.getAvailable());
        Assert.assertEquals(0, totals.getLeased());

        Future<LocalPoolEntry> future4 = pool.lease("somehost", null);
        GetPoolEntryThread t4 = new GetPoolEntryThread(future4);
        t4.start();
        Future<LocalPoolEntry> future5 = pool.lease("somehost", null);
        GetPoolEntryThread t5 = new GetPoolEntryThread(future5);
        t5.start();
        Future<LocalPoolEntry> future6 = pool.lease("otherhost", null);
        GetPoolEntryThread t6 = new GetPoolEntryThread(future6);
        t6.start();

        t4.join(GRACE_PERIOD);
        Assert.assertTrue(future4.isDone());
        LocalPoolEntry entry4 = t4.getEntry();
        Assert.assertNotNull(entry4);
        t5.join(GRACE_PERIOD);
        Assert.assertTrue(future5.isDone());
        LocalPoolEntry entry5 = t5.getEntry();
        Assert.assertNotNull(entry5);
        t6.join(GRACE_PERIOD);
        Assert.assertTrue(future6.isDone());
        LocalPoolEntry entry6 = t6.getEntry();
        Assert.assertNotNull(entry6);

        Future<LocalPoolEntry> future7 = pool.lease("somehost", null);
        GetPoolEntryThread t7 = new GetPoolEntryThread(future7);
        t7.start();
        Future<LocalPoolEntry> future8 = pool.lease("somehost", null);
        GetPoolEntryThread t8 = new GetPoolEntryThread(future8);
        t8.start();
        Future<LocalPoolEntry> future9 = pool.lease("otherhost", null);
        GetPoolEntryThread t9 = new GetPoolEntryThread(future9);
        t9.start();

        Assert.assertFalse(t7.isDone());
        Assert.assertFalse(t8.isDone());
        Assert.assertFalse(t9.isDone());

        Mockito.verify(connFactory, Mockito.times(3)).create(Mockito.any(String.class));

        pool.release(entry4, true);
        pool.release(entry5, false);
        pool.release(entry6, true);

        t7.join();
        Assert.assertTrue(future7.isDone());
        t8.join();
        Assert.assertTrue(future8.isDone());
        t9.join();
        Assert.assertTrue(future9.isDone());

        Mockito.verify(connFactory, Mockito.times(4)).create(Mockito.any(String.class));
    }

    @Test
    public void testConnectionRedistributionOnTotalMaxLimit() throws Exception {
        LocalConnFactory connFactory = Mockito.mock(LocalConnFactory.class);

        HttpConnection conn1 = Mockito.mock(HttpConnection.class);
        Mockito.when(conn1.isOpen()).thenReturn(true);
        HttpConnection conn2 = Mockito.mock(HttpConnection.class);
        Mockito.when(conn2.isOpen()).thenReturn(true);
        HttpConnection conn3 = Mockito.mock(HttpConnection.class);
        Mockito.when(conn3.isOpen()).thenReturn(true);
        Mockito.when(connFactory.create(Mockito.eq("somehost"))).thenReturn(conn1, conn2, conn3);

        HttpConnection conn4 = Mockito.mock(HttpConnection.class);
        Mockito.when(conn4.isOpen()).thenReturn(true);
        HttpConnection conn5 = Mockito.mock(HttpConnection.class);
        Mockito.when(conn5.isOpen()).thenReturn(true);
        Mockito.when(connFactory.create(Mockito.eq("otherhost"))).thenReturn(conn4, conn5);

        LocalConnPool pool = new LocalConnPool(connFactory, 2, 10);
        pool.setMaxPerRoute("somehost", 2);
        pool.setMaxPerRoute("otherhost", 2);
        pool.setMaxTotal(2);

        Future<LocalPoolEntry> future1 = pool.lease("somehost", null);
        GetPoolEntryThread t1 = new GetPoolEntryThread(future1);
        t1.start();
        Future<LocalPoolEntry> future2 = pool.lease("somehost", null);
        GetPoolEntryThread t2 = new GetPoolEntryThread(future2);
        t2.start();

        t1.join(GRACE_PERIOD);
        Assert.assertTrue(future1.isDone());
        LocalPoolEntry entry1 = t1.getEntry();
        Assert.assertNotNull(entry1);
        t2.join(GRACE_PERIOD);
        Assert.assertTrue(future2.isDone());
        LocalPoolEntry entry2 = t2.getEntry();
        Assert.assertNotNull(entry2);

        Future<LocalPoolEntry> future3 = pool.lease("otherhost", null);
        GetPoolEntryThread t3 = new GetPoolEntryThread(future3);
        t3.start();
        Future<LocalPoolEntry> future4 = pool.lease("otherhost", null);
        GetPoolEntryThread t4 = new GetPoolEntryThread(future4);
        t4.start();

        Assert.assertFalse(t3.isDone());
        Assert.assertFalse(t4.isDone());

        Mockito.verify(connFactory, Mockito.times(2)).create(Mockito.eq("somehost"));
        Mockito.verify(connFactory, Mockito.never()).create(Mockito.eq("otherhost"));

        PoolStats totals = pool.getTotalStats();
        Assert.assertEquals(0, totals.getAvailable());
        Assert.assertEquals(2, totals.getLeased());

        pool.release(entry1, true);
        pool.release(entry2, true);

        t3.join(GRACE_PERIOD);
        Assert.assertTrue(future3.isDone());
        LocalPoolEntry entry3 = t3.getEntry();
        Assert.assertNotNull(entry3);
        t4.join(GRACE_PERIOD);
        Assert.assertTrue(future4.isDone());
        LocalPoolEntry entry4 = t4.getEntry();
        Assert.assertNotNull(entry4);

        Mockito.verify(connFactory, Mockito.times(2)).create(Mockito.eq("somehost"));
        Mockito.verify(connFactory, Mockito.times(2)).create(Mockito.eq("otherhost"));

        totals = pool.getTotalStats();
        Assert.assertEquals(0, totals.getAvailable());
        Assert.assertEquals(2, totals.getLeased());

        Future<LocalPoolEntry> future5 = pool.lease("somehost", null);
        GetPoolEntryThread t5 = new GetPoolEntryThread(future5);
        t5.start();
        Future<LocalPoolEntry> future6 = pool.lease("otherhost", null);
        GetPoolEntryThread t6 = new GetPoolEntryThread(future6);
        t6.start();

        pool.release(entry3, true);
        pool.release(entry4, true);

        t5.join(GRACE_PERIOD);
        Assert.assertTrue(future5.isDone());
        LocalPoolEntry entry5 = t5.getEntry();
        Assert.assertNotNull(entry5);
        t6.join(GRACE_PERIOD);
        Assert.assertTrue(future6.isDone());
        LocalPoolEntry entry6 = t6.getEntry();
        Assert.assertNotNull(entry6);

        Mockito.verify(connFactory, Mockito.times(3)).create(Mockito.eq("somehost"));
        Mockito.verify(connFactory, Mockito.times(2)).create(Mockito.eq("otherhost"));

        totals = pool.getTotalStats();
        Assert.assertEquals(0, totals.getAvailable());
        Assert.assertEquals(2, totals.getLeased());

        pool.release(entry5, true);
        pool.release(entry6, true);

        totals = pool.getTotalStats();
        Assert.assertEquals(2, totals.getAvailable());
        Assert.assertEquals(0, totals.getLeased());
    }

    @Test
    public void testStatefulConnectionRedistributionOnPerRouteMaxLimit() throws Exception {
        LocalConnFactory connFactory = Mockito.mock(LocalConnFactory.class);

        HttpConnection conn1 = Mockito.mock(HttpConnection.class);
        Mockito.when(conn1.isOpen()).thenReturn(true);
        HttpConnection conn2 = Mockito.mock(HttpConnection.class);
        Mockito.when(conn2.isOpen()).thenReturn(true);
        HttpConnection conn3 = Mockito.mock(HttpConnection.class);
        Mockito.when(conn3.isOpen()).thenReturn(true);
        Mockito.when(connFactory.create(Mockito.eq("somehost"))).thenReturn(conn1, conn2, conn3);

        LocalConnPool pool = new LocalConnPool(connFactory, 2, 10);
        pool.setMaxPerRoute("somehost", 2);
        pool.setMaxTotal(2);

        Future<LocalPoolEntry> future1 = pool.lease("somehost", null);
        GetPoolEntryThread t1 = new GetPoolEntryThread(future1);
        t1.start();

        t1.join(GRACE_PERIOD);
        Assert.assertTrue(future1.isDone());
        LocalPoolEntry entry1 = t1.getEntry();
        Assert.assertNotNull(entry1);

        Future<LocalPoolEntry> future2 = pool.lease("somehost", null);
        GetPoolEntryThread t2 = new GetPoolEntryThread(future2);
        t2.start();

        t2.join(GRACE_PERIOD);
        Assert.assertTrue(future2.isDone());
        LocalPoolEntry entry2 = t2.getEntry();
        Assert.assertNotNull(entry2);

        PoolStats totals = pool.getTotalStats();
        Assert.assertEquals(0, totals.getAvailable());
        Assert.assertEquals(2, totals.getLeased());
        Assert.assertEquals(0, totals.getPending());

        entry1.setState("some-stuff");
        pool.release(entry1, true);
        entry2.setState("some-stuff");
        pool.release(entry2, true);

        Mockito.verify(connFactory, Mockito.times(2)).create(Mockito.eq("somehost"));

        Future<LocalPoolEntry> future3 = pool.lease("somehost", "some-other-stuff");
        GetPoolEntryThread t3 = new GetPoolEntryThread(future3);
        t3.start();

        t3.join(GRACE_PERIOD);
        Assert.assertTrue(future3.isDone());
        LocalPoolEntry entry3 = t3.getEntry();
        Assert.assertNotNull(entry3);

        Mockito.verify(connFactory, Mockito.times(3)).create(Mockito.eq("somehost"));

        Mockito.verify(conn1).close();
        Mockito.verify(conn2, Mockito.never()).close();

        totals = pool.getTotalStats();
        Assert.assertEquals(1, totals.getAvailable());
        Assert.assertEquals(1, totals.getLeased());

    }

    @Test
    public void testCreateNewIfExpired() throws Exception {
        LocalConnFactory connFactory = Mockito.mock(LocalConnFactory.class);

        HttpConnection conn1 = Mockito.mock(HttpConnection.class);
        Mockito.when(conn1.isOpen()).thenReturn(true);
        Mockito.when(connFactory.create(Mockito.eq("somehost"))).thenReturn(conn1);

        LocalConnPool pool = new LocalConnPool(connFactory, 2, 2);

        Future<LocalPoolEntry> future1 = pool.lease("somehost", null);
        LocalPoolEntry entry1 = future1.get(1, TimeUnit.SECONDS);
        Assert.assertNotNull(entry1);

        Mockito.verify(connFactory, Mockito.times(1)).create(Mockito.eq("somehost"));

        entry1.updateExpiry(1, TimeUnit.MILLISECONDS);
        pool.release(entry1, true);

        Thread.sleep(200L);

        Future<LocalPoolEntry> future2 = pool.lease("somehost", null);
        LocalPoolEntry entry2 = future2.get(1, TimeUnit.SECONDS);
        Assert.assertNotNull(entry2);

        Mockito.verify(connFactory, Mockito.times(2)).create(Mockito.eq("somehost"));

        PoolStats totals = pool.getTotalStats();
        Assert.assertEquals(0, totals.getAvailable());
        Assert.assertEquals(1, totals.getLeased());
        PoolStats stats = pool.getStats("somehost");
        Assert.assertEquals(0, stats.getAvailable());
        Assert.assertEquals(1, stats.getLeased());
    }

    @Test
    public void testCloseExpired() throws Exception {
        LocalConnFactory connFactory = Mockito.mock(LocalConnFactory.class);

        HttpConnection conn1 = Mockito.mock(HttpConnection.class);
        Mockito.when(conn1.isOpen()).thenReturn(Boolean.FALSE);
        HttpConnection conn2 = Mockito.mock(HttpConnection.class);
        Mockito.when(conn2.isOpen()).thenReturn(Boolean.TRUE);

        Mockito.when(connFactory.create(Mockito.eq("somehost"))).thenReturn(conn1, conn2);

        LocalConnPool pool = new LocalConnPool(connFactory, 2, 2);

        Future<LocalPoolEntry> future1 = pool.lease("somehost", null);
        LocalPoolEntry entry1 = future1.get(1, TimeUnit.SECONDS);
        Assert.assertNotNull(entry1);
        Future<LocalPoolEntry> future2 = pool.lease("somehost", null);
        LocalPoolEntry entry2 = future2.get(1, TimeUnit.SECONDS);
        Assert.assertNotNull(entry2);

        entry1.updateExpiry(1, TimeUnit.MILLISECONDS);
        pool.release(entry1, true);

        Thread.sleep(200);

        entry2.updateExpiry(1000, TimeUnit.SECONDS);
        pool.release(entry2, true);

        pool.closeExpired();

        Mockito.verify(conn1).close();
        Mockito.verify(conn2, Mockito.never()).close();

        PoolStats totals = pool.getTotalStats();
        Assert.assertEquals(1, totals.getAvailable());
        Assert.assertEquals(0, totals.getLeased());
        PoolStats stats = pool.getStats("somehost");
        Assert.assertEquals(1, stats.getAvailable());
        Assert.assertEquals(0, stats.getLeased());
    }

    @Test
    public void testLeaseTimeout() throws Exception {
        LocalConnFactory connFactory = Mockito.mock(LocalConnFactory.class);

        HttpConnection conn1 = Mockito.mock(HttpConnection.class);
        Mockito.when(conn1.isOpen()).thenReturn(true);
        Mockito.when(connFactory.create(Mockito.eq("somehost"))).thenReturn(conn1);

        LocalConnPool pool = new LocalConnPool(connFactory, 1, 1);

        Future<LocalPoolEntry> future1 = pool.lease("somehost", null);
        GetPoolEntryThread t1 = new GetPoolEntryThread(future1);
        t1.start();

        t1.join(GRACE_PERIOD);
        Assert.assertTrue(future1.isDone());
        LocalPoolEntry entry1 = t1.getEntry();
        Assert.assertNotNull(entry1);

        Future<LocalPoolEntry> future2 = pool.lease("somehost", null);
        GetPoolEntryThread t2 = new GetPoolEntryThread(future2, 50, TimeUnit.MICROSECONDS);
        t2.start();

        t2.join(GRACE_PERIOD);
        Assert.assertTrue(t2.getException() instanceof TimeoutException);
        Assert.assertFalse(future2.isDone());
        Assert.assertFalse(future2.isCancelled());
    }

    @Test
    public void testLeaseIOException() throws Exception {
        LocalConnFactory connFactory = Mockito.mock(LocalConnFactory.class);
        Mockito.doThrow(new IOException("Oppsie")).when(connFactory).create("somehost");

        LocalConnPool pool = new LocalConnPool(connFactory, 2, 10);

        Future<LocalPoolEntry> future1 = pool.lease("somehost", null);
        GetPoolEntryThread t1 = new GetPoolEntryThread(future1);
        t1.start();

        t1.join(GRACE_PERIOD);
        Assert.assertTrue(future1.isDone());
        Assert.assertTrue(t1.getException() instanceof ExecutionException);
        Assert.assertTrue(t1.getException().getCause() instanceof IOException);
        Assert.assertFalse(future1.isCancelled());
    }

    @Test
    public void testLeaseCancel() throws Exception {
        LocalConnFactory connFactory = Mockito.mock(LocalConnFactory.class);

        HttpConnection conn1 = Mockito.mock(HttpConnection.class);
        Mockito.when(conn1.isOpen()).thenReturn(true);
        Mockito.when(connFactory.create(Mockito.eq("somehost"))).thenReturn(conn1);

        LocalConnPool pool = new LocalConnPool(connFactory, 1, 1);

        Future<LocalPoolEntry> future1 = pool.lease("somehost", null);
        GetPoolEntryThread t1 = new GetPoolEntryThread(future1);
        t1.start();

        t1.join(GRACE_PERIOD);
        Assert.assertTrue(future1.isDone());
        LocalPoolEntry entry1 = t1.getEntry();
        Assert.assertNotNull(entry1);

        Future<LocalPoolEntry> future2 = pool.lease("somehost", null);
        GetPoolEntryThread t2 = new GetPoolEntryThread(future2);
        t2.start();

        Thread.sleep(5);

        Assert.assertFalse(future2.isDone());
        Assert.assertFalse(future2.isCancelled());

        future2.cancel(true);
        t2.join(GRACE_PERIOD);
        Assert.assertTrue(future2.isDone());
        Assert.assertTrue(future2.isCancelled());
        future2.cancel(true);
        future2.cancel(true);
    }

    @Test
    public void testCloseIdle() throws Exception {
        LocalConnFactory connFactory = Mockito.mock(LocalConnFactory.class);

        HttpConnection conn1 = Mockito.mock(HttpConnection.class);
        Mockito.when(conn1.isOpen()).thenReturn(true);
        HttpConnection conn2 = Mockito.mock(HttpConnection.class);
        Mockito.when(conn2.isOpen()).thenReturn(true);

        Mockito.when(connFactory.create(Mockito.eq("somehost"))).thenReturn(conn1, conn2);

        LocalConnPool pool = new LocalConnPool(connFactory, 2, 2);

        Future<LocalPoolEntry> future1 = pool.lease("somehost", null);
        LocalPoolEntry entry1 = future1.get(1, TimeUnit.SECONDS);
        Assert.assertNotNull(entry1);
        Future<LocalPoolEntry> future2 = pool.lease("somehost", null);
        LocalPoolEntry entry2 = future2.get(1, TimeUnit.SECONDS);
        Assert.assertNotNull(entry2);

        entry1.updateExpiry(0, TimeUnit.MILLISECONDS);
        pool.release(entry1, true);

        Thread.sleep(200L);

        entry2.updateExpiry(0, TimeUnit.MILLISECONDS);
        pool.release(entry2, true);

        pool.closeIdle(50, TimeUnit.MILLISECONDS);

        Mockito.verify(conn1).close();
        Mockito.verify(conn2, Mockito.never()).close();

        PoolStats totals = pool.getTotalStats();
        Assert.assertEquals(1, totals.getAvailable());
        Assert.assertEquals(0, totals.getLeased());
        PoolStats stats = pool.getStats("somehost");
        Assert.assertEquals(1, stats.getAvailable());
        Assert.assertEquals(0, stats.getLeased());

        pool.closeIdle(-1, TimeUnit.MILLISECONDS);

        Mockito.verify(conn2).close();

        totals = pool.getTotalStats();
        Assert.assertEquals(0, totals.getAvailable());
        Assert.assertEquals(0, totals.getLeased());
        stats = pool.getStats("somehost");
        Assert.assertEquals(0, stats.getAvailable());
        Assert.assertEquals(0, stats.getLeased());
    }

    @Test(expected=IllegalArgumentException.class)
    public void testCloseIdleInvalid() throws Exception {
        LocalConnFactory connFactory = Mockito.mock(LocalConnFactory.class);
        LocalConnPool pool = new LocalConnPool(connFactory, 2, 2);
        pool.closeIdle(50, null);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testGetStatsInvalid() throws Exception {
        LocalConnFactory connFactory = Mockito.mock(LocalConnFactory.class);
        LocalConnPool pool = new LocalConnPool(connFactory, 2, 2);
        pool.getStats(null);
    }

    @Test
    public void testSetMaxInvalid() throws Exception {
        LocalConnFactory connFactory = Mockito.mock(LocalConnFactory.class);
        LocalConnPool pool = new LocalConnPool(connFactory, 2, 2);
        try {
            pool.setMaxTotal(-1);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException expected) {
        }
        try {
            pool.setMaxPerRoute(null, 1);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException expected) {
        }
        try {
            pool.setMaxPerRoute("somehost", -1);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException expected) {
        }
        try {
            pool.setDefaultMaxPerRoute(-1);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testShutdown() throws Exception {
        LocalConnFactory connFactory = Mockito.mock(LocalConnFactory.class);

        HttpConnection conn1 = Mockito.mock(HttpConnection.class);
        Mockito.when(conn1.isOpen()).thenReturn(true);
        Mockito.when(connFactory.create(Mockito.eq("somehost"))).thenReturn(conn1);
        HttpConnection conn2 = Mockito.mock(HttpConnection.class);
        Mockito.when(conn2.isOpen()).thenReturn(true);
        Mockito.when(connFactory.create(Mockito.eq("otherhost"))).thenReturn(conn2);

        LocalConnPool pool = new LocalConnPool(connFactory, 2, 2);
        Future<LocalPoolEntry> future1 = pool.lease("somehost", null);
        LocalPoolEntry entry1 = future1.get(1, TimeUnit.SECONDS);
        Assert.assertNotNull(entry1);
        Future<LocalPoolEntry> future2 = pool.lease("otherhost", null);
        LocalPoolEntry entry2 = future2.get(1, TimeUnit.SECONDS);
        Assert.assertNotNull(entry2);

        pool.release(entry2, true);

        PoolStats totals = pool.getTotalStats();
        Assert.assertEquals(1, totals.getAvailable());
        Assert.assertEquals(1, totals.getLeased());

        pool.shutdown();
        Assert.assertTrue(pool.isShutdown());
        pool.shutdown();
        pool.shutdown();

        Mockito.verify(conn1, Mockito.atLeastOnce()).close();
        Mockito.verify(conn2, Mockito.atLeastOnce()).close();

        try {
            pool.lease("somehost", null);
            Assert.fail("IllegalStateException should have been thrown");
        } catch (IllegalStateException expected) {
        }
        // Ignored if shut down
        pool.release(new LocalPoolEntry("somehost", Mockito.mock(HttpConnection.class)), true);
    }

}
