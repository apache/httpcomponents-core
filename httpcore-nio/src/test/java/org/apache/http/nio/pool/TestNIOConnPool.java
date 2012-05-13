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
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import junit.framework.Assert;

import org.apache.http.concurrent.BasicFuture;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.reactor.SessionRequest;
import org.apache.http.nio.reactor.SessionRequestCallback;
import org.apache.http.pool.PoolEntry;
import org.apache.http.pool.PoolStats;
import org.junit.Test;
import org.mockito.Mockito;

public class TestNIOConnPool {

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

    static class LocalConnFactory implements NIOConnFactory<String, IOSession> {

        public IOSession create(String route, IOSession session) throws IOException {
            return session;
        }

    }

    static class LocalSessionPool extends AbstractNIOConnPool<String, IOSession, LocalPoolEntry> {

        public LocalSessionPool(
                final ConnectingIOReactor ioreactor, int defaultMaxPerRoute, int maxTotal) {
            super(ioreactor, new LocalConnFactory(), defaultMaxPerRoute, maxTotal);
        }

        @Override
        protected SocketAddress resolveRemoteAddress(final String route) {
            return InetSocketAddress.createUnresolved(route, 80);
        }

        @Override
        protected SocketAddress resolveLocalAddress(final String route) {
            return InetSocketAddress.createUnresolved(route, 80);
        }

        @Override
        protected LocalPoolEntry createEntry(final String route, final IOSession session) {
            return new LocalPoolEntry(route, session);
        }

    }

    @Test
    public void testEmptyPool() throws Exception {
        ConnectingIOReactor ioreactor = Mockito.mock(ConnectingIOReactor.class);
        LocalSessionPool pool = new LocalSessionPool(ioreactor, 2, 10);
        PoolStats totals = pool.getTotalStats();
        Assert.assertEquals(0, totals.getAvailable());
        Assert.assertEquals(0, totals.getLeased());
        Assert.assertEquals(0, totals.getPending());
        Assert.assertEquals(10, totals.getMax());
        PoolStats stats = pool.getStats("somehost");
        Assert.assertEquals(0, stats.getAvailable());
        Assert.assertEquals(0, stats.getLeased());
        Assert.assertEquals(0, stats.getPending());
        Assert.assertEquals(2, stats.getMax());
        Assert.assertEquals("[leased: []][available: []][pending: []]", pool.toString());
    }

    @Test
    public void testInternalLeaseRequest() throws Exception {
        LeaseRequest<String, IOSession, LocalPoolEntry> leaseRequest =
            new LeaseRequest<String, IOSession, LocalPoolEntry>("somehost", null, 0,
                    new BasicFuture<LocalPoolEntry>(null));
        Assert.assertEquals("[somehost][null]", leaseRequest.toString());
    }

    @Test
    public void testInvalidConstruction() throws Exception {
        ConnectingIOReactor ioreactor = Mockito.mock(ConnectingIOReactor.class);
        try {
            new LocalSessionPool(null, 1, 1);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException expected) {
        }
        try {
            new LocalSessionPool(ioreactor, -1, 1);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException expected) {
        }
        try {
            new LocalSessionPool(ioreactor, 1, -1);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testSuccessfulConnect() throws Exception {
        IOSession iosession = Mockito.mock(IOSession.class);
        SessionRequest sessionRequest = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest.getAttachment()).thenReturn("somehost");
        Mockito.when(sessionRequest.getSession()).thenReturn(iosession);
        ConnectingIOReactor ioreactor = Mockito.mock(ConnectingIOReactor.class);
        Mockito.when(ioreactor.connect(
                Mockito.any(SocketAddress.class),
                Mockito.any(SocketAddress.class),
                Mockito.any(), Mockito.any(SessionRequestCallback.class))).
                thenReturn(sessionRequest);
        LocalSessionPool pool = new LocalSessionPool(ioreactor, 2, 10);
        Future<LocalPoolEntry> future = pool.lease("somehost", null, 100, TimeUnit.MILLISECONDS, null);
        Mockito.verify(sessionRequest).setConnectTimeout(100);

        PoolStats totals = pool.getTotalStats();
        Assert.assertEquals(0, totals.getAvailable());
        Assert.assertEquals(0, totals.getLeased());
        Assert.assertEquals(1, totals.getPending());

        pool.requestCompleted(sessionRequest);

        Assert.assertTrue(future.isDone());
        Assert.assertFalse(future.isCancelled());
        LocalPoolEntry entry = future.get();
        Assert.assertNotNull(entry);

        totals = pool.getTotalStats();
        Assert.assertEquals(0, totals.getAvailable());
        Assert.assertEquals(1, totals.getLeased());
        Assert.assertEquals(0, totals.getPending());
    }

    @Test
    public void testFailedConnect() throws Exception {
        SessionRequest sessionRequest = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest.getAttachment()).thenReturn("somehost");
        Mockito.when(sessionRequest.getException()).thenReturn(new IOException());
        ConnectingIOReactor ioreactor = Mockito.mock(ConnectingIOReactor.class);
        Mockito.when(ioreactor.connect(
                Mockito.any(SocketAddress.class),
                Mockito.any(SocketAddress.class),
                Mockito.any(), Mockito.any(SessionRequestCallback.class))).
                thenReturn(sessionRequest);
        LocalSessionPool pool = new LocalSessionPool(ioreactor, 2, 10);
        Future<LocalPoolEntry> future = pool.lease("somehost", null);

        PoolStats totals = pool.getTotalStats();
        Assert.assertEquals(0, totals.getAvailable());
        Assert.assertEquals(0, totals.getLeased());
        Assert.assertEquals(1, totals.getPending());

        pool.requestFailed(sessionRequest);

        Assert.assertTrue(future.isDone());
        Assert.assertFalse(future.isCancelled());
        try {
            future.get();
            Assert.fail("ExecutionException should have been thrown");
        } catch (ExecutionException ex) {
            Assert.assertTrue(ex.getCause() instanceof IOException);
        }

        totals = pool.getTotalStats();
        Assert.assertEquals(0, totals.getAvailable());
        Assert.assertEquals(0, totals.getLeased());
        Assert.assertEquals(0, totals.getPending());
    }

    @Test
    public void testCencelledConnect() throws Exception {
        IOSession iosession = Mockito.mock(IOSession.class);
        SessionRequest sessionRequest = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest.getAttachment()).thenReturn("somehost");
        Mockito.when(sessionRequest.getSession()).thenReturn(iosession);
        ConnectingIOReactor ioreactor = Mockito.mock(ConnectingIOReactor.class);
        Mockito.when(ioreactor.connect(
                Mockito.any(SocketAddress.class),
                Mockito.any(SocketAddress.class),
                Mockito.any(), Mockito.any(SessionRequestCallback.class))).
                thenReturn(sessionRequest);
        LocalSessionPool pool = new LocalSessionPool(ioreactor, 2, 10);
        Future<LocalPoolEntry> future = pool.lease("somehost", null);

        PoolStats totals = pool.getTotalStats();
        Assert.assertEquals(0, totals.getAvailable());
        Assert.assertEquals(0, totals.getLeased());
        Assert.assertEquals(1, totals.getPending());

        pool.requestCancelled(sessionRequest);

        Assert.assertTrue(future.isDone());
        Assert.assertTrue(future.isCancelled());
        LocalPoolEntry entry = future.get();
        Assert.assertNull(entry);

        totals = pool.getTotalStats();
        Assert.assertEquals(0, totals.getAvailable());
        Assert.assertEquals(0, totals.getLeased());
        Assert.assertEquals(0, totals.getPending());
    }

    @Test
    public void testTimeoutConnect() throws Exception {
        IOSession iosession = Mockito.mock(IOSession.class);
        SessionRequest sessionRequest = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest.getAttachment()).thenReturn("somehost");
        Mockito.when(sessionRequest.getSession()).thenReturn(iosession);
        ConnectingIOReactor ioreactor = Mockito.mock(ConnectingIOReactor.class);
        Mockito.when(ioreactor.connect(
                Mockito.any(SocketAddress.class),
                Mockito.any(SocketAddress.class),
                Mockito.any(), Mockito.any(SessionRequestCallback.class))).
                thenReturn(sessionRequest);
        LocalSessionPool pool = new LocalSessionPool(ioreactor, 2, 10);
        Future<LocalPoolEntry> future = pool.lease("somehost", null);

        PoolStats totals = pool.getTotalStats();
        Assert.assertEquals(0, totals.getAvailable());
        Assert.assertEquals(0, totals.getLeased());
        Assert.assertEquals(1, totals.getPending());

        pool.requestTimeout(sessionRequest);

        Assert.assertTrue(future.isDone());
        Assert.assertFalse(future.isCancelled());
        try {
            future.get();
            Assert.fail("ExecutionException should have been thrown");
        } catch (ExecutionException ex) {
            Assert.assertTrue(ex.getCause() instanceof SocketTimeoutException);
        }

        totals = pool.getTotalStats();
        Assert.assertEquals(0, totals.getAvailable());
        Assert.assertEquals(0, totals.getLeased());
        Assert.assertEquals(0, totals.getPending());
    }

    @Test
    public void testLeaseRelease() throws Exception {
        IOSession iosession1 = Mockito.mock(IOSession.class);
        SessionRequest sessionRequest1 = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest1.getAttachment()).thenReturn("somehost");
        Mockito.when(sessionRequest1.getSession()).thenReturn(iosession1);

        IOSession iosession2 = Mockito.mock(IOSession.class);
        SessionRequest sessionRequest2 = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest2.getAttachment()).thenReturn("otherhost");
        Mockito.when(sessionRequest2.getSession()).thenReturn(iosession2);

        ConnectingIOReactor ioreactor = Mockito.mock(ConnectingIOReactor.class);
        Mockito.when(ioreactor.connect(
                Mockito.eq(InetSocketAddress.createUnresolved("somehost", 80)),
                Mockito.any(SocketAddress.class),
                Mockito.any(), Mockito.any(SessionRequestCallback.class))).
                thenReturn(sessionRequest1);
        Mockito.when(ioreactor.connect(
                Mockito.eq(InetSocketAddress.createUnresolved("otherhost", 80)),
                Mockito.any(SocketAddress.class),
                Mockito.any(), Mockito.any(SessionRequestCallback.class))).
                thenReturn(sessionRequest2);

        LocalSessionPool pool = new LocalSessionPool(ioreactor, 2, 10);
        Future<LocalPoolEntry> future1 = pool.lease("somehost", null);
        pool.requestCompleted(sessionRequest1);
        Future<LocalPoolEntry> future2 = pool.lease("somehost", null);
        pool.requestCompleted(sessionRequest1);
        Future<LocalPoolEntry> future3 = pool.lease("otherhost", null);
        pool.requestCompleted(sessionRequest2);

        LocalPoolEntry entry1 = future1.get();
        Assert.assertNotNull(entry1);
        LocalPoolEntry entry2 = future2.get();
        Assert.assertNotNull(entry2);
        LocalPoolEntry entry3 = future3.get();
        Assert.assertNotNull(entry3);

        pool.release(entry1, true);
        pool.release(entry2, true);
        pool.release(entry3, false);
        Mockito.verify(iosession1, Mockito.never()).close();
        Mockito.verify(iosession2, Mockito.times(1)).close();

        PoolStats totals = pool.getTotalStats();
        Assert.assertEquals(2, totals.getAvailable());
        Assert.assertEquals(0, totals.getLeased());
        Assert.assertEquals(0, totals.getPending());
    }

    @Test
    public void testLeaseIllegal() throws Exception {
        ConnectingIOReactor ioreactor = Mockito.mock(ConnectingIOReactor.class);
        LocalSessionPool pool = new LocalSessionPool(ioreactor, 2, 10);
        try {
            pool.lease(null, null, 0, TimeUnit.MILLISECONDS, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException expected) {
        }
        try {
            pool.lease("somehost", null, 0, null, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testReleaseUnknownEntry() throws Exception {
        ConnectingIOReactor ioreactor = Mockito.mock(ConnectingIOReactor.class);
        LocalSessionPool pool = new LocalSessionPool(ioreactor, 2, 2);
        pool.release(new LocalPoolEntry("somehost", Mockito.mock(IOSession.class)), true);
    }

    @Test
    public void testMaxLimits() throws Exception {
        IOSession iosession1 = Mockito.mock(IOSession.class);
        SessionRequest sessionRequest1 = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest1.getAttachment()).thenReturn("somehost");
        Mockito.when(sessionRequest1.getSession()).thenReturn(iosession1);

        IOSession iosession2 = Mockito.mock(IOSession.class);
        SessionRequest sessionRequest2 = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest2.getAttachment()).thenReturn("otherhost");
        Mockito.when(sessionRequest2.getSession()).thenReturn(iosession2);

        ConnectingIOReactor ioreactor = Mockito.mock(ConnectingIOReactor.class);
        Mockito.when(ioreactor.connect(
                Mockito.eq(InetSocketAddress.createUnresolved("somehost", 80)),
                Mockito.any(SocketAddress.class),
                Mockito.any(), Mockito.any(SessionRequestCallback.class))).
                thenReturn(sessionRequest1);
        Mockito.when(ioreactor.connect(
                Mockito.eq(InetSocketAddress.createUnresolved("otherhost", 80)),
                Mockito.any(SocketAddress.class),
                Mockito.any(), Mockito.any(SessionRequestCallback.class))).
                thenReturn(sessionRequest2);

        LocalSessionPool pool = new LocalSessionPool(ioreactor, 2, 10);
        pool.setMaxPerRoute("somehost", 2);
        pool.setMaxPerRoute("otherhost", 1);
        pool.setMaxTotal(3);

        Future<LocalPoolEntry> future1 = pool.lease("somehost", null);
        pool.requestCompleted(sessionRequest1);
        Future<LocalPoolEntry> future2 = pool.lease("somehost", null);
        pool.requestCompleted(sessionRequest1);
        Future<LocalPoolEntry> future3 = pool.lease("otherhost", null);
        pool.requestCompleted(sessionRequest2);

        LocalPoolEntry entry1 = future1.get();
        Assert.assertNotNull(entry1);
        LocalPoolEntry entry2 = future2.get();
        Assert.assertNotNull(entry2);
        LocalPoolEntry entry3 = future3.get();
        Assert.assertNotNull(entry3);

        pool.release(entry1, true);
        pool.release(entry2, true);
        pool.release(entry3, true);

        PoolStats totals = pool.getTotalStats();
        Assert.assertEquals(3, totals.getAvailable());
        Assert.assertEquals(0, totals.getLeased());
        Assert.assertEquals(0, totals.getPending());

        Future<LocalPoolEntry> future4 = pool.lease("somehost", null);
        Future<LocalPoolEntry> future5 = pool.lease("somehost", null);
        Future<LocalPoolEntry> future6 = pool.lease("otherhost", null);
        Future<LocalPoolEntry> future7 = pool.lease("somehost", null);
        Future<LocalPoolEntry> future8 = pool.lease("somehost", null);
        Future<LocalPoolEntry> future9 = pool.lease("otherhost", null);

        Assert.assertTrue(future4.isDone());
        LocalPoolEntry entry4 = future4.get();
        Assert.assertNotNull(entry4);
        Assert.assertTrue(future5.isDone());
        LocalPoolEntry entry5 = future5.get();
        Assert.assertNotNull(entry5);
        Assert.assertTrue(future6.isDone());
        LocalPoolEntry entry6 = future6.get();
        Assert.assertNotNull(entry6);
        Assert.assertFalse(future7.isDone());
        Assert.assertFalse(future8.isDone());
        Assert.assertFalse(future9.isDone());

        Mockito.verify(ioreactor, Mockito.times(3)).connect(
                Mockito.any(SocketAddress.class), Mockito.any(SocketAddress.class),
                Mockito.any(), Mockito.any(SessionRequestCallback.class));

        pool.release(entry4, true);
        pool.release(entry5, false);
        pool.release(entry6, true);

        Assert.assertTrue(future7.isDone());
        Assert.assertFalse(future8.isDone());
        Assert.assertTrue(future9.isDone());

        Mockito.verify(ioreactor, Mockito.times(4)).connect(
                Mockito.any(SocketAddress.class), Mockito.any(SocketAddress.class),
                Mockito.any(), Mockito.any(SessionRequestCallback.class));
    }

    @Test
    public void testConnectionRedistributionOnTotalMaxLimit() throws Exception {
        IOSession iosession1 = Mockito.mock(IOSession.class);
        SessionRequest sessionRequest1 = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest1.getAttachment()).thenReturn("somehost");
        Mockito.when(sessionRequest1.getSession()).thenReturn(iosession1);

        IOSession iosession2 = Mockito.mock(IOSession.class);
        SessionRequest sessionRequest2 = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest2.getAttachment()).thenReturn("somehost");
        Mockito.when(sessionRequest2.getSession()).thenReturn(iosession2);

        IOSession iosession3 = Mockito.mock(IOSession.class);
        SessionRequest sessionRequest3 = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest3.getAttachment()).thenReturn("otherhost");
        Mockito.when(sessionRequest3.getSession()).thenReturn(iosession3);

        IOSession iosession4 = Mockito.mock(IOSession.class);
        SessionRequest sessionRequest4 = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest4.getAttachment()).thenReturn("otherhost");
        Mockito.when(sessionRequest4.getSession()).thenReturn(iosession4);

        ConnectingIOReactor ioreactor = Mockito.mock(ConnectingIOReactor.class);
        Mockito.when(ioreactor.connect(
                Mockito.eq(InetSocketAddress.createUnresolved("somehost", 80)),
                Mockito.any(SocketAddress.class),
                Mockito.any(), Mockito.any(SessionRequestCallback.class))).
                thenReturn(sessionRequest1, sessionRequest2, sessionRequest1);
        Mockito.when(ioreactor.connect(
                Mockito.eq(InetSocketAddress.createUnresolved("otherhost", 80)),
                Mockito.any(SocketAddress.class),
                Mockito.any(), Mockito.any(SessionRequestCallback.class))).
                thenReturn(sessionRequest3, sessionRequest4, sessionRequest3);

        LocalSessionPool pool = new LocalSessionPool(ioreactor, 2, 10);
        pool.setMaxPerRoute("somehost", 2);
        pool.setMaxPerRoute("otherhost", 2);
        pool.setMaxTotal(2);

        Future<LocalPoolEntry> future1 = pool.lease("somehost", null);
        Future<LocalPoolEntry> future2 = pool.lease("somehost", null);
        Future<LocalPoolEntry> future3 = pool.lease("otherhost", null);
        Future<LocalPoolEntry> future4 = pool.lease("otherhost", null);

        Mockito.verify(ioreactor, Mockito.times(2)).connect(
                Mockito.eq(InetSocketAddress.createUnresolved("somehost", 80)),
                Mockito.any(SocketAddress.class),
                Mockito.any(), Mockito.any(SessionRequestCallback.class));

        Mockito.verify(ioreactor, Mockito.never()).connect(
                Mockito.eq(InetSocketAddress.createUnresolved("otherhost", 80)),
                Mockito.any(SocketAddress.class),
                Mockito.any(), Mockito.any(SessionRequestCallback.class));

        pool.requestCompleted(sessionRequest1);
        pool.requestCompleted(sessionRequest2);

        Assert.assertTrue(future1.isDone());
        LocalPoolEntry entry1 = future1.get();
        Assert.assertNotNull(entry1);
        Assert.assertTrue(future2.isDone());
        LocalPoolEntry entry2 = future2.get();
        Assert.assertNotNull(entry2);

        Assert.assertFalse(future3.isDone());
        Assert.assertFalse(future4.isDone());

        PoolStats totals = pool.getTotalStats();
        Assert.assertEquals(0, totals.getAvailable());
        Assert.assertEquals(2, totals.getLeased());
        Assert.assertEquals(0, totals.getPending());

        pool.release(entry1, true);
        pool.release(entry2, true);

        Mockito.verify(ioreactor, Mockito.times(2)).connect(
                Mockito.eq(InetSocketAddress.createUnresolved("somehost", 80)),
                Mockito.any(SocketAddress.class),
                Mockito.any(), Mockito.any(SessionRequestCallback.class));

        Mockito.verify(ioreactor, Mockito.times(2)).connect(
                Mockito.eq(InetSocketAddress.createUnresolved("otherhost", 80)),
                Mockito.any(SocketAddress.class),
                Mockito.any(), Mockito.any(SessionRequestCallback.class));

        pool.requestCompleted(sessionRequest3);
        pool.requestCompleted(sessionRequest4);

        Assert.assertTrue(future3.isDone());
        LocalPoolEntry entry3 = future3.get();
        Assert.assertNotNull(entry3);
        Assert.assertTrue(future4.isDone());
        LocalPoolEntry entry4 = future4.get();
        Assert.assertNotNull(entry4);

        totals = pool.getTotalStats();
        Assert.assertEquals(0, totals.getAvailable());
        Assert.assertEquals(2, totals.getLeased());
        Assert.assertEquals(0, totals.getPending());

        Future<LocalPoolEntry> future5 = pool.lease("somehost", null);
        Future<LocalPoolEntry> future6 = pool.lease("otherhost", null);

        Mockito.verify(ioreactor, Mockito.times(2)).connect(
                Mockito.eq(InetSocketAddress.createUnresolved("somehost", 80)),
                Mockito.any(SocketAddress.class),
                Mockito.any(), Mockito.any(SessionRequestCallback.class));

        Mockito.verify(ioreactor, Mockito.times(2)).connect(
                Mockito.eq(InetSocketAddress.createUnresolved("otherhost", 80)),
                Mockito.any(SocketAddress.class),
                Mockito.any(), Mockito.any(SessionRequestCallback.class));

        pool.release(entry3, true);
        pool.release(entry4, true);

        Mockito.verify(ioreactor, Mockito.times(3)).connect(
                Mockito.eq(InetSocketAddress.createUnresolved("somehost", 80)),
                Mockito.any(SocketAddress.class),
                Mockito.any(), Mockito.any(SessionRequestCallback.class));

        Mockito.verify(ioreactor, Mockito.times(2)).connect(
                Mockito.eq(InetSocketAddress.createUnresolved("otherhost", 80)),
                Mockito.any(SocketAddress.class),
                Mockito.any(), Mockito.any(SessionRequestCallback.class));

        pool.requestCompleted(sessionRequest1);

        Assert.assertTrue(future5.isDone());
        LocalPoolEntry entry5 = future5.get();
        Assert.assertNotNull(entry5);
        Assert.assertTrue(future6.isDone());
        LocalPoolEntry entry6 = future6.get();
        Assert.assertNotNull(entry6);

        totals = pool.getTotalStats();
        Assert.assertEquals(0, totals.getAvailable());
        Assert.assertEquals(2, totals.getLeased());
        Assert.assertEquals(0, totals.getPending());

        pool.release(entry5, true);
        pool.release(entry6, true);

        Mockito.verify(ioreactor, Mockito.times(3)).connect(
                Mockito.eq(InetSocketAddress.createUnresolved("somehost", 80)),
                Mockito.any(SocketAddress.class),
                Mockito.any(), Mockito.any(SessionRequestCallback.class));

        Mockito.verify(ioreactor, Mockito.times(2)).connect(
                Mockito.eq(InetSocketAddress.createUnresolved("otherhost", 80)),
                Mockito.any(SocketAddress.class),
                Mockito.any(), Mockito.any(SessionRequestCallback.class));

        totals = pool.getTotalStats();
        Assert.assertEquals(2, totals.getAvailable());
        Assert.assertEquals(0, totals.getLeased());
        Assert.assertEquals(0, totals.getPending());
    }

    @Test
    public void testStatefulConnectionRedistributionOnPerRouteMaxLimit() throws Exception {
        IOSession iosession1 = Mockito.mock(IOSession.class);
        SessionRequest sessionRequest1 = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest1.getAttachment()).thenReturn("somehost");
        Mockito.when(sessionRequest1.getSession()).thenReturn(iosession1);

        IOSession iosession2 = Mockito.mock(IOSession.class);
        SessionRequest sessionRequest2 = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest2.getAttachment()).thenReturn("somehost");
        Mockito.when(sessionRequest2.getSession()).thenReturn(iosession2);

        IOSession iosession3 = Mockito.mock(IOSession.class);
        SessionRequest sessionRequest3 = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest3.getAttachment()).thenReturn("somehost");
        Mockito.when(sessionRequest3.getSession()).thenReturn(iosession3);

        ConnectingIOReactor ioreactor = Mockito.mock(ConnectingIOReactor.class);
        Mockito.when(ioreactor.connect(
                Mockito.eq(InetSocketAddress.createUnresolved("somehost", 80)),
                Mockito.any(SocketAddress.class),
                Mockito.any(), Mockito.any(SessionRequestCallback.class))).
                thenReturn(sessionRequest1, sessionRequest2, sessionRequest3);

        LocalSessionPool pool = new LocalSessionPool(ioreactor, 2, 10);
        pool.setMaxPerRoute("somehost", 2);
        pool.setMaxTotal(2);

        Future<LocalPoolEntry> future1 = pool.lease("somehost", null);
        Future<LocalPoolEntry> future2 = pool.lease("somehost", null);

        Mockito.verify(ioreactor, Mockito.times(2)).connect(
                Mockito.eq(InetSocketAddress.createUnresolved("somehost", 80)),
                Mockito.any(SocketAddress.class),
                Mockito.any(), Mockito.any(SessionRequestCallback.class));

        pool.requestCompleted(sessionRequest1);
        pool.requestCompleted(sessionRequest2);

        Assert.assertTrue(future1.isDone());
        LocalPoolEntry entry1 = future1.get();
        Assert.assertNotNull(entry1);
        Assert.assertTrue(future2.isDone());
        LocalPoolEntry entry2 = future2.get();
        Assert.assertNotNull(entry2);

        PoolStats totals = pool.getTotalStats();
        Assert.assertEquals(0, totals.getAvailable());
        Assert.assertEquals(2, totals.getLeased());
        Assert.assertEquals(0, totals.getPending());

        entry1.setState("some-stuff");
        pool.release(entry1, true);
        entry2.setState("some-stuff");
        pool.release(entry2, true);

        Future<LocalPoolEntry> future3 = pool.lease("somehost", "some-stuff");
        Future<LocalPoolEntry> future4 = pool.lease("somehost", "some-stuff");

        Assert.assertTrue(future1.isDone());
        LocalPoolEntry entry3 = future3.get();
        Assert.assertNotNull(entry3);
        Assert.assertTrue(future4.isDone());
        LocalPoolEntry entry4 = future4.get();
        Assert.assertNotNull(entry4);

        Mockito.verify(ioreactor, Mockito.times(2)).connect(
                Mockito.eq(InetSocketAddress.createUnresolved("somehost", 80)),
                Mockito.any(SocketAddress.class),
                Mockito.any(), Mockito.any(SessionRequestCallback.class));

        pool.release(entry3, true);
        pool.release(entry4, true);

        totals = pool.getTotalStats();
        Assert.assertEquals(2, totals.getAvailable());
        Assert.assertEquals(0, totals.getLeased());
        Assert.assertEquals(0, totals.getPending());

        Future<LocalPoolEntry> future5 = pool.lease("somehost", "some-other-stuff");

        Assert.assertFalse(future5.isDone());

        Mockito.verify(ioreactor, Mockito.times(3)).connect(
                Mockito.eq(InetSocketAddress.createUnresolved("somehost", 80)),
                Mockito.any(SocketAddress.class),
                Mockito.any(), Mockito.any(SessionRequestCallback.class));

        Mockito.verify(iosession2).close();
        Mockito.verify(iosession1, Mockito.never()).close();

        totals = pool.getTotalStats();
        Assert.assertEquals(1, totals.getAvailable());
        Assert.assertEquals(0, totals.getLeased());
        Assert.assertEquals(1, totals.getPending());
    }

    @Test
    public void testCreateNewIfExpired() throws Exception {
        IOSession iosession1 = Mockito.mock(IOSession.class);
        Mockito.when(iosession1.isClosed()).thenReturn(Boolean.TRUE);
        SessionRequest sessionRequest1 = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest1.getAttachment()).thenReturn("somehost");
        Mockito.when(sessionRequest1.getSession()).thenReturn(iosession1);

        ConnectingIOReactor ioreactor = Mockito.mock(ConnectingIOReactor.class);
        Mockito.when(ioreactor.connect(
                Mockito.eq(InetSocketAddress.createUnresolved("somehost", 80)),
                Mockito.any(SocketAddress.class),
                Mockito.any(), Mockito.any(SessionRequestCallback.class))).
                thenReturn(sessionRequest1);

        LocalSessionPool pool = new LocalSessionPool(ioreactor, 2, 2);

        Future<LocalPoolEntry> future1 = pool.lease("somehost", null);

        Mockito.verify(ioreactor, Mockito.times(1)).connect(
                Mockito.any(SocketAddress.class), Mockito.any(SocketAddress.class),
                Mockito.any(), Mockito.any(SessionRequestCallback.class));

        pool.requestCompleted(sessionRequest1);

        Assert.assertTrue(future1.isDone());
        LocalPoolEntry entry1 = future1.get();
        Assert.assertNotNull(entry1);

        entry1.updateExpiry(1, TimeUnit.MILLISECONDS);
        pool.release(entry1, true);

        Thread.sleep(200L);

        Future<LocalPoolEntry> future2 = pool.lease("somehost", null);

        Assert.assertFalse(future2.isDone());

        Mockito.verify(iosession1).close();
        Mockito.verify(ioreactor, Mockito.times(2)).connect(
                Mockito.any(SocketAddress.class), Mockito.any(SocketAddress.class),
                Mockito.any(), Mockito.any(SessionRequestCallback.class));

        PoolStats totals = pool.getTotalStats();
        Assert.assertEquals(0, totals.getAvailable());
        Assert.assertEquals(0, totals.getLeased());
        Assert.assertEquals(1, totals.getPending());
        PoolStats stats = pool.getStats("somehost");
        Assert.assertEquals(0, stats.getAvailable());
        Assert.assertEquals(0, stats.getLeased());
        Assert.assertEquals(1, stats.getPending());
    }

    @Test
    public void testCloseExpired() throws Exception {
        IOSession iosession1 = Mockito.mock(IOSession.class);
        Mockito.when(iosession1.isClosed()).thenReturn(Boolean.TRUE);
        SessionRequest sessionRequest1 = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest1.getAttachment()).thenReturn("somehost");
        Mockito.when(sessionRequest1.getSession()).thenReturn(iosession1);

        IOSession iosession2 = Mockito.mock(IOSession.class);
        SessionRequest sessionRequest2 = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest2.getAttachment()).thenReturn("somehost");
        Mockito.when(sessionRequest2.getSession()).thenReturn(iosession2);

        ConnectingIOReactor ioreactor = Mockito.mock(ConnectingIOReactor.class);
        Mockito.when(ioreactor.connect(
                Mockito.any(SocketAddress.class), Mockito.any(SocketAddress.class),
                Mockito.any(), Mockito.any(SessionRequestCallback.class))).
                thenReturn(sessionRequest1, sessionRequest2);

        LocalSessionPool pool = new LocalSessionPool(ioreactor, 2, 2);

        Future<LocalPoolEntry> future1 = pool.lease("somehost", null);
        Future<LocalPoolEntry> future2 = pool.lease("somehost", null);

        pool.requestCompleted(sessionRequest1);
        pool.requestCompleted(sessionRequest2);

        Assert.assertTrue(future1.isDone());
        LocalPoolEntry entry1 = future1.get();
        Assert.assertNotNull(entry1);
        Assert.assertTrue(future2.isDone());
        LocalPoolEntry entry2 = future2.get();
        Assert.assertNotNull(entry2);

        entry1.updateExpiry(1, TimeUnit.MILLISECONDS);
        pool.release(entry1, true);

        Thread.sleep(200);

        entry2.updateExpiry(1000, TimeUnit.SECONDS);
        pool.release(entry2, true);

        pool.closeExpired();

        Mockito.verify(iosession1).close();
        Mockito.verify(iosession2, Mockito.never()).close();

        PoolStats totals = pool.getTotalStats();
        Assert.assertEquals(1, totals.getAvailable());
        Assert.assertEquals(0, totals.getLeased());
        Assert.assertEquals(0, totals.getPending());
        PoolStats stats = pool.getStats("somehost");
        Assert.assertEquals(1, stats.getAvailable());
        Assert.assertEquals(0, stats.getLeased());
        Assert.assertEquals(0, stats.getPending());
    }

    @Test
    public void testCloseIdle() throws Exception {
        IOSession iosession1 = Mockito.mock(IOSession.class);
        SessionRequest sessionRequest1 = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest1.getAttachment()).thenReturn("somehost");
        Mockito.when(sessionRequest1.getSession()).thenReturn(iosession1);

        IOSession iosession2 = Mockito.mock(IOSession.class);
        SessionRequest sessionRequest2 = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest2.getAttachment()).thenReturn("somehost");
        Mockito.when(sessionRequest2.getSession()).thenReturn(iosession2);

        ConnectingIOReactor ioreactor = Mockito.mock(ConnectingIOReactor.class);
        Mockito.when(ioreactor.connect(
                Mockito.any(SocketAddress.class), Mockito.any(SocketAddress.class),
                Mockito.any(), Mockito.any(SessionRequestCallback.class))).
                thenReturn(sessionRequest1, sessionRequest2);

        LocalSessionPool pool = new LocalSessionPool(ioreactor, 2, 2);

        Future<LocalPoolEntry> future1 = pool.lease("somehost", null);
        Future<LocalPoolEntry> future2 = pool.lease("somehost", null);

        pool.requestCompleted(sessionRequest1);
        pool.requestCompleted(sessionRequest2);

        Assert.assertTrue(future1.isDone());
        LocalPoolEntry entry1 = future1.get();
        Assert.assertNotNull(entry1);
        Assert.assertTrue(future2.isDone());
        LocalPoolEntry entry2 = future2.get();
        Assert.assertNotNull(entry2);

        entry1.updateExpiry(0, TimeUnit.MILLISECONDS);
        pool.release(entry1, true);

        Thread.sleep(200L);

        entry2.updateExpiry(0, TimeUnit.MILLISECONDS);
        pool.release(entry2, true);

        pool.closeIdle(50, TimeUnit.MILLISECONDS);

        Mockito.verify(iosession1).close();
        Mockito.verify(iosession2, Mockito.never()).close();

        PoolStats totals = pool.getTotalStats();
        Assert.assertEquals(1, totals.getAvailable());
        Assert.assertEquals(0, totals.getLeased());
        Assert.assertEquals(0, totals.getPending());
        PoolStats stats = pool.getStats("somehost");
        Assert.assertEquals(1, stats.getAvailable());
        Assert.assertEquals(0, stats.getLeased());
        Assert.assertEquals(0, stats.getPending());

        pool.closeIdle(-1, TimeUnit.MILLISECONDS);

        Mockito.verify(iosession2).close();

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
        IOSession iosession1 = Mockito.mock(IOSession.class);
        Mockito.when(iosession1.isClosed()).thenReturn(Boolean.TRUE);
        SessionRequest sessionRequest1 = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest1.getAttachment()).thenReturn("somehost");
        Mockito.when(sessionRequest1.getSession()).thenReturn(iosession1);

        ConnectingIOReactor ioreactor = Mockito.mock(ConnectingIOReactor.class);
        Mockito.when(ioreactor.connect(
                Mockito.any(SocketAddress.class), Mockito.any(SocketAddress.class),
                Mockito.any(), Mockito.any(SessionRequestCallback.class))).
                thenReturn(sessionRequest1);

        LocalSessionPool pool = new LocalSessionPool(ioreactor, 1, 1);

        Future<LocalPoolEntry> future1 = pool.lease("somehost", null, 0, TimeUnit.MILLISECONDS, null);
        Future<LocalPoolEntry> future2 = pool.lease("somehost", null, 0, TimeUnit.MILLISECONDS, null);
        Future<LocalPoolEntry> future3 = pool.lease("somehost", null, 10, TimeUnit.MILLISECONDS, null);

        pool.requestCompleted(sessionRequest1);

        Assert.assertTrue(future1.isDone());
        LocalPoolEntry entry1 = future1.get();
        Assert.assertNotNull(entry1);
        Assert.assertFalse(future2.isDone());
        Assert.assertFalse(future3.isDone());

        Thread.sleep(100);

        pool.validatePendingRequests();

        Assert.assertFalse(future2.isDone());
        Assert.assertTrue(future3.isDone());
    }

    @Test(expected=IllegalArgumentException.class)
    public void testCloseIdleInvalid() throws Exception {
        ConnectingIOReactor ioreactor = Mockito.mock(ConnectingIOReactor.class);
        LocalSessionPool pool = new LocalSessionPool(ioreactor, 2, 2);
        pool.closeIdle(50, null);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testGetStatsInvalid() throws Exception {
        ConnectingIOReactor ioreactor = Mockito.mock(ConnectingIOReactor.class);
        LocalSessionPool pool = new LocalSessionPool(ioreactor, 2, 2);
        pool.getStats(null);
    }

    @Test
    public void testSetMaxInvalid() throws Exception {
        ConnectingIOReactor ioreactor = Mockito.mock(ConnectingIOReactor.class);
        LocalSessionPool pool = new LocalSessionPool(ioreactor, 2, 2);
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
        ConnectingIOReactor ioreactor = Mockito.mock(ConnectingIOReactor.class);
        LocalSessionPool pool = new LocalSessionPool(ioreactor, 2, 2);
        pool.shutdown(1000);
        Mockito.verify(ioreactor, Mockito.times(1)).shutdown(1000);
        pool.shutdown(1000);
        Mockito.verify(ioreactor, Mockito.times(1)).shutdown(1000);
        try {
            pool.lease("somehost", null);
            Assert.fail("IllegalStateException should have been thrown");
        } catch (IllegalStateException expected) {
        }
        // Ignored if shut down
        pool.release(new LocalPoolEntry("somehost", Mockito.mock(IOSession.class)), true);
        pool.requestCompleted(Mockito.mock(SessionRequest.class));
        pool.requestFailed(Mockito.mock(SessionRequest.class));
        pool.requestCancelled(Mockito.mock(SessionRequest.class));
        pool.requestTimeout(Mockito.mock(SessionRequest.class));
    }

}
