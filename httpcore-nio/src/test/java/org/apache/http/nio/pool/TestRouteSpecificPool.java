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
package org.apache.http.nio.pool;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;

import org.apache.http.concurrent.BasicFuture;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.reactor.SessionRequest;
import org.apache.http.pool.PoolEntry;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class TestRouteSpecificPool {

    static class LocalPoolEntry extends PoolEntry<String, IOSession> {

        public LocalPoolEntry(final String route, final IOSession conn) {
            super(null, route, conn);
        }

        @Override
        public void close() {
            getConnection().close();
        }

        @Override
        public boolean isClosed() {
            return getConnection().isClosed();
        }

    }

    static class LocalRoutePool extends RouteSpecificPool<String, IOSession, LocalPoolEntry> {

        public LocalRoutePool() {
            super("whatever");
        }

        @Override
        protected LocalPoolEntry createEntry(final String route, final IOSession session) {
            return new LocalPoolEntry(route, session);
        }

    }

    @Test
    public void testEmptyPool() throws Exception {
        final LocalRoutePool pool = new LocalRoutePool();
        Assert.assertEquals(0, pool.getAllocatedCount());
        Assert.assertEquals(0, pool.getAvailableCount());
        Assert.assertEquals(0, pool.getLeasedCount());
        Assert.assertEquals(0, pool.getPendingCount());
        Assert.assertNull(pool.getLastUsed());
        Assert.assertEquals("[route: whatever][leased: 0][available: 0][pending: 0]", pool.toString());
    }

    @Test
    public void testSuccessfulConnect() throws Exception {
        final LocalRoutePool pool = new LocalRoutePool();
        final IOSession session = Mockito.mock(IOSession.class);
        final SessionRequest sessionRequest = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest.getSession()).thenReturn(session);
        final BasicFuture<LocalPoolEntry> future = new BasicFuture<LocalPoolEntry>(null);
        pool.addPending(sessionRequest, future);
        Assert.assertEquals(1, pool.getAllocatedCount());
        Assert.assertEquals(0, pool.getAvailableCount());
        Assert.assertEquals(0, pool.getLeasedCount());
        Assert.assertEquals(1, pool.getPendingCount());
        final LocalPoolEntry entry = pool.createEntry(sessionRequest, session);
        Assert.assertNotNull(entry);
        Assert.assertSame(session, entry.getConnection());
        Assert.assertFalse(future.isDone());
        Assert.assertFalse(future.isCancelled());
        pool.completed(sessionRequest, entry);
        Assert.assertTrue(future.isDone());
        Assert.assertFalse(future.isCancelled());

        Assert.assertEquals(1, pool.getAllocatedCount());
        Assert.assertEquals(0, pool.getAvailableCount());
        Assert.assertEquals(1, pool.getLeasedCount());
        Assert.assertEquals(0, pool.getPendingCount());
    }

    @Test
    public void testFailedConnect() throws Exception {
        final LocalRoutePool pool = new LocalRoutePool();
        final SessionRequest sessionRequest = Mockito.mock(SessionRequest.class);
        final BasicFuture<LocalPoolEntry> future = new BasicFuture<LocalPoolEntry>(null);
        pool.addPending(sessionRequest, future);
        Assert.assertEquals(1, pool.getAllocatedCount());
        Assert.assertEquals(0, pool.getAvailableCount());
        Assert.assertEquals(0, pool.getLeasedCount());
        Assert.assertEquals(1, pool.getPendingCount());
        pool.failed(sessionRequest, new IOException());
        Assert.assertTrue(future.isDone());
        Assert.assertFalse(future.isCancelled());
        try {
            future.get();
            Assert.fail("ExecutionException should have been thrown");
        } catch (final ExecutionException ex) {
            Assert.assertTrue(ex.getCause() instanceof IOException);
        }
        Assert.assertEquals(0, pool.getAllocatedCount());
        Assert.assertEquals(0, pool.getAvailableCount());
        Assert.assertEquals(0, pool.getLeasedCount());
        Assert.assertEquals(0, pool.getPendingCount());
    }

    @Test
    public void testCancelledConnect() throws Exception {
        final LocalRoutePool pool = new LocalRoutePool();
        final SessionRequest sessionRequest = Mockito.mock(SessionRequest.class);
        final BasicFuture<LocalPoolEntry> future = new BasicFuture<LocalPoolEntry>(null);
        pool.addPending(sessionRequest, future);
        Assert.assertEquals(1, pool.getAllocatedCount());
        Assert.assertEquals(0, pool.getAvailableCount());
        Assert.assertEquals(0, pool.getLeasedCount());
        Assert.assertEquals(1, pool.getPendingCount());
        pool.cancelled(sessionRequest);
        Assert.assertTrue(future.isDone());
        Assert.assertTrue(future.isCancelled());

        Assert.assertEquals(0, pool.getAllocatedCount());
        Assert.assertEquals(0, pool.getAvailableCount());
        Assert.assertEquals(0, pool.getLeasedCount());
        Assert.assertEquals(0, pool.getPendingCount());
    }

    @Test
    public void testConnectTimeout() throws Exception {
        final LocalRoutePool pool = new LocalRoutePool();
        final SessionRequest sessionRequest = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest.getRemoteAddress())
                .thenReturn(new InetSocketAddress(InetAddress.getByAddress(new byte[]{127, 0, 0, 1}), 80));
        final BasicFuture<LocalPoolEntry> future = new BasicFuture<LocalPoolEntry>(null);
        pool.addPending(sessionRequest, future);
        Assert.assertEquals(1, pool.getAllocatedCount());
        Assert.assertEquals(0, pool.getAvailableCount());
        Assert.assertEquals(0, pool.getLeasedCount());
        Assert.assertEquals(1, pool.getPendingCount());
        pool.timeout(sessionRequest);
        Assert.assertTrue(future.isDone());
        Assert.assertFalse(future.isCancelled());
        try {
            future.get();
            Assert.fail("ExecutionException should have been thrown");
        } catch (final ExecutionException ex) {
            Assert.assertTrue(ex.getCause() instanceof ConnectException);
            Assert.assertEquals("Timeout connecting to [/127.0.0.1:80]", ex.getCause().getMessage());
        }
        Assert.assertEquals(0, pool.getAllocatedCount());
        Assert.assertEquals(0, pool.getAvailableCount());
        Assert.assertEquals(0, pool.getLeasedCount());
        Assert.assertEquals(0, pool.getPendingCount());
    }

    @Test
    public void testLeaseRelease() throws Exception {
        final LocalRoutePool pool = new LocalRoutePool();
        final IOSession session1 = Mockito.mock(IOSession.class);
        final SessionRequest sessionRequest1 = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest1.getSession()).thenReturn(session1);
        final BasicFuture<LocalPoolEntry> future1 = new BasicFuture<LocalPoolEntry>(null);
        pool.addPending(sessionRequest1, future1);
        final IOSession session2 = Mockito.mock(IOSession.class);
        final SessionRequest sessionRequest2 = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest2.getSession()).thenReturn(session2);
        final BasicFuture<LocalPoolEntry> future2 = new BasicFuture<LocalPoolEntry>(null);
        pool.addPending(sessionRequest2, future2);
        final IOSession session3 = Mockito.mock(IOSession.class);
        final SessionRequest sessionRequest3 = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest3.getSession()).thenReturn(session3);
        final BasicFuture<LocalPoolEntry> future3 = new BasicFuture<LocalPoolEntry>(null);
        pool.addPending(sessionRequest3, future3);

        Assert.assertEquals(3, pool.getAllocatedCount());
        Assert.assertEquals(0, pool.getAvailableCount());
        Assert.assertEquals(0, pool.getLeasedCount());
        Assert.assertEquals(3, pool.getPendingCount());

        final LocalPoolEntry entry1 = pool.createEntry(sessionRequest1, session1);
        pool.completed(sessionRequest1, entry1);
        Assert.assertNotNull(entry1);
        final LocalPoolEntry entry2 = pool.createEntry(sessionRequest2, session2);
        pool.completed(sessionRequest2, entry2);
        Assert.assertNotNull(entry2);
        final LocalPoolEntry entry3 = pool.createEntry(sessionRequest3, session3);
        pool.completed(sessionRequest3, entry3);
        Assert.assertNotNull(entry3);

        Assert.assertEquals(3, pool.getAllocatedCount());
        Assert.assertEquals(0, pool.getAvailableCount());
        Assert.assertEquals(3, pool.getLeasedCount());
        Assert.assertEquals(0, pool.getPendingCount());

        pool.free(entry1, true);
        pool.free(entry2, false);
        pool.free(entry3, true);

        Assert.assertSame(entry1, pool.getLastUsed());

        Assert.assertEquals(2, pool.getAllocatedCount());
        Assert.assertEquals(2, pool.getAvailableCount());
        Assert.assertEquals(0, pool.getLeasedCount());
        Assert.assertEquals(0, pool.getPendingCount());

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
        final LocalRoutePool pool = new LocalRoutePool();
        final IOSession session1 = Mockito.mock(IOSession.class);
        final SessionRequest sessionRequest1 = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest1.getSession()).thenReturn(session1);
        final BasicFuture<LocalPoolEntry> future1 = new BasicFuture<LocalPoolEntry>(null);
        pool.addPending(sessionRequest1, future1);
        final IOSession session2 = Mockito.mock(IOSession.class);
        final SessionRequest sessionRequest2 = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest2.getSession()).thenReturn(session2);
        final BasicFuture<LocalPoolEntry> future2 = new BasicFuture<LocalPoolEntry>(null);
        pool.addPending(sessionRequest2, future2);
        final IOSession session3 = Mockito.mock(IOSession.class);
        final SessionRequest sessionRequest3 = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest3.getSession()).thenReturn(session3);
        final BasicFuture<LocalPoolEntry> future3 = new BasicFuture<LocalPoolEntry>(null);
        pool.addPending(sessionRequest3, future3);

        Assert.assertEquals(3, pool.getAllocatedCount());
        Assert.assertEquals(0, pool.getAvailableCount());
        Assert.assertEquals(0, pool.getLeasedCount());
        Assert.assertEquals(3, pool.getPendingCount());

        final LocalPoolEntry entry1 = pool.createEntry(sessionRequest1, session1);
        pool.completed(sessionRequest1, entry1);
        Assert.assertNotNull(entry1);
        final LocalPoolEntry entry2 = pool.createEntry(sessionRequest2, session2);
        pool.completed(sessionRequest2, entry2);
        Assert.assertNotNull(entry2);
        final LocalPoolEntry entry3 = pool.createEntry(sessionRequest3, session3);
        pool.completed(sessionRequest3, entry3);
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
        final LocalRoutePool pool = new LocalRoutePool();

        final IOSession session1 = Mockito.mock(IOSession.class);
        final SessionRequest sessionRequest1 = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest1.getSession()).thenReturn(session1);
        final BasicFuture<LocalPoolEntry> future1 = new BasicFuture<LocalPoolEntry>(null);
        pool.addPending(sessionRequest1, future1);
        final IOSession session2 = Mockito.mock(IOSession.class);
        final SessionRequest sessionRequest2 = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest2.getSession()).thenReturn(session2);
        final BasicFuture<LocalPoolEntry> future2 = new BasicFuture<LocalPoolEntry>(null);
        pool.addPending(sessionRequest2, future2);
        final IOSession session3 = Mockito.mock(IOSession.class);
        final SessionRequest sessionRequest3 = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest3.getSession()).thenReturn(session3);
        final BasicFuture<LocalPoolEntry> future3 = new BasicFuture<LocalPoolEntry>(null);
        pool.addPending(sessionRequest3, future3);

        final LocalPoolEntry entry1 = pool.createEntry(sessionRequest1, session1);
        pool.completed(sessionRequest1, entry1);
        Assert.assertNotNull(entry1);
        final LocalPoolEntry entry2 = pool.createEntry(sessionRequest2, session2);
        pool.completed(sessionRequest2, entry2);
        Assert.assertNotNull(entry2);
        final LocalPoolEntry entry3 = pool.createEntry(sessionRequest3, session3);
        pool.completed(sessionRequest3, entry3);
        Assert.assertNotNull(entry3);

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
        final LocalRoutePool pool = new LocalRoutePool();
        final IOSession session = Mockito.mock(IOSession.class);
        final LocalPoolEntry entry = new LocalPoolEntry("whatever", session);
        pool.free(entry, true);
    }

    @Test
    public void testRemove() throws Exception {
        final LocalRoutePool pool = new LocalRoutePool();
        final IOSession session1 = Mockito.mock(IOSession.class);
        final SessionRequest sessionRequest1 = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest1.getSession()).thenReturn(session1);
        final BasicFuture<LocalPoolEntry> future1 = new BasicFuture<LocalPoolEntry>(null);
        pool.addPending(sessionRequest1, future1);
        final IOSession session2 = Mockito.mock(IOSession.class);
        final SessionRequest sessionRequest2 = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest2.getSession()).thenReturn(session2);
        final BasicFuture<LocalPoolEntry> future2 = new BasicFuture<LocalPoolEntry>(null);
        pool.addPending(sessionRequest2, future2);
        final IOSession session3 = Mockito.mock(IOSession.class);
        final SessionRequest sessionRequest3 = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest3.getSession()).thenReturn(session3);
        final BasicFuture<LocalPoolEntry> future3 = new BasicFuture<LocalPoolEntry>(null);
        pool.addPending(sessionRequest3, future3);

        Assert.assertEquals(3, pool.getAllocatedCount());
        Assert.assertEquals(0, pool.getAvailableCount());
        Assert.assertEquals(0, pool.getLeasedCount());
        Assert.assertEquals(3, pool.getPendingCount());

        final LocalPoolEntry entry1 = pool.createEntry(sessionRequest1, session1);
        pool.completed(sessionRequest1, entry1);
        Assert.assertNotNull(entry1);
        final LocalPoolEntry entry2 = pool.createEntry(sessionRequest2, session2);
        pool.completed(sessionRequest2, entry2);
        Assert.assertNotNull(entry2);
        final LocalPoolEntry entry3 = pool.createEntry(sessionRequest3, session3);
        pool.completed(sessionRequest3, entry3);
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
        final LocalRoutePool pool = new LocalRoutePool();
        pool.free(null, true);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testRemoveInvalid() throws Exception {
        final LocalRoutePool pool = new LocalRoutePool();
        pool.remove(null);
    }

    @Test
    public void testShutdown() throws Exception {
        final LocalRoutePool pool = new LocalRoutePool();
        final IOSession session1 = Mockito.mock(IOSession.class);
        final SessionRequest sessionRequest1 = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest1.getSession()).thenReturn(session1);
        final BasicFuture<LocalPoolEntry> future1 = new BasicFuture<LocalPoolEntry>(null);
        pool.addPending(sessionRequest1, future1);
        final IOSession session2 = Mockito.mock(IOSession.class);
        final SessionRequest sessionRequest2 = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest2.getSession()).thenReturn(session2);
        final BasicFuture<LocalPoolEntry> future2 = new BasicFuture<LocalPoolEntry>(null);
        pool.addPending(sessionRequest2, future2);
        final IOSession session3 = Mockito.mock(IOSession.class);
        final SessionRequest sessionRequest3 = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest3.getSession()).thenReturn(session3);
        final BasicFuture<LocalPoolEntry> future3 = new BasicFuture<LocalPoolEntry>(null);
        pool.addPending(sessionRequest3, future3);

        final LocalPoolEntry entry1 = pool.createEntry(sessionRequest1, session1);
        pool.completed(sessionRequest1, entry1);
        Assert.assertNotNull(entry1);
        final LocalPoolEntry entry2 = pool.createEntry(sessionRequest2, session2);
        pool.completed(sessionRequest2, entry2);
        Assert.assertNotNull(entry2);

        pool.free(entry1, true);

        Assert.assertEquals(3, pool.getAllocatedCount());
        Assert.assertEquals(1, pool.getAvailableCount());
        Assert.assertEquals(1, pool.getLeasedCount());
        Assert.assertEquals(1, pool.getPendingCount());

        pool.shutdown();

        Assert.assertEquals(0, pool.getAllocatedCount());
        Assert.assertEquals(0, pool.getAvailableCount());
        Assert.assertEquals(0, pool.getLeasedCount());
        Assert.assertEquals(0, pool.getPendingCount());

        Mockito.verify(sessionRequest3).cancel();
        Mockito.verify(session2).close();
        Mockito.verify(session1).close();
    }

}
