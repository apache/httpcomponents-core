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
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.http.concurrent.BasicFuture;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOReactorStatus;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.reactor.SessionRequest;
import org.apache.http.nio.reactor.SessionRequestCallback;
import org.apache.http.pool.PoolEntry;
import org.apache.http.pool.PoolStats;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mockito;

public class TestNIOConnPool {

    static class LocalPoolEntry extends PoolEntry<String, IOSession> {

        private boolean closed;

        public LocalPoolEntry(final String route, final IOSession conn) {
            super(null, route, conn);
        }

        @Override
        public void close() {
            if (this.closed) {
                return;
            }
            this.closed = true;
            getConnection().close();
        }

        @Override
        public boolean isClosed() {
            return this.closed;
        }

    }

    static class LocalConnFactory implements NIOConnFactory<String, IOSession> {

        @Override
        public IOSession create(final String route, final IOSession session) throws IOException {
            return session;
        }

    }

    static class LocalAddressResolver implements SocketAddressResolver<String> {

        @Override
        public SocketAddress resolveLocalAddress(final String route) {
            return null;
        }

        @Override
        public SocketAddress resolveRemoteAddress(final String route) {
            return InetSocketAddress.createUnresolved(route, 80);
        }

    }

    static class LocalSessionPool extends AbstractNIOConnPool<String, IOSession, LocalPoolEntry> {

        public LocalSessionPool(
                final ConnectingIOReactor ioreactor, final int defaultMaxPerRoute, final int maxTotal) {
            super(ioreactor, new LocalConnFactory(), new LocalAddressResolver(), defaultMaxPerRoute, maxTotal);
        }

        public LocalSessionPool(
                final ConnectingIOReactor ioreactor,
                final SocketAddressResolver<String> addressResolver,
                final int defaultMaxPerRoute, final int maxTotal) {
            super(ioreactor, new LocalConnFactory(), addressResolver, defaultMaxPerRoute, maxTotal);
        }

        @Override
        protected LocalPoolEntry createEntry(final String route, final IOSession session) {
            return new LocalPoolEntry(route, session);
        }

    }

    @Test
    public void testEmptyPool() throws Exception {
        final ConnectingIOReactor ioreactor = Mockito.mock(ConnectingIOReactor.class);
        final LocalSessionPool pool = new LocalSessionPool(ioreactor, 2, 10);
        final PoolStats totals = pool.getTotalStats();
        Assert.assertEquals(0, totals.getAvailable());
        Assert.assertEquals(0, totals.getLeased());
        Assert.assertEquals(0, totals.getPending());
        Assert.assertEquals(10, totals.getMax());
        Assert.assertEquals(Collections.emptySet(), pool.getRoutes());
        final PoolStats stats = pool.getStats("somehost");
        Assert.assertEquals(0, stats.getAvailable());
        Assert.assertEquals(0, stats.getLeased());
        Assert.assertEquals(0, stats.getPending());
        Assert.assertEquals(2, stats.getMax());
        Assert.assertEquals("[leased: []][available: []][pending: []]", pool.toString());
    }

    @Test
    public void testInternalLeaseRequest() throws Exception {
        final LeaseRequest<String, IOSession, LocalPoolEntry> leaseRequest =
            new LeaseRequest<String, IOSession, LocalPoolEntry>("somehost", null, 0, 0,
                    new BasicFuture<LocalPoolEntry>(null));
        Assert.assertEquals("[somehost][null]", leaseRequest.toString());
    }

    @Test
    public void testInvalidConstruction() throws Exception {
        final ConnectingIOReactor ioreactor = Mockito.mock(ConnectingIOReactor.class);
        try {
            new LocalSessionPool(null, 1, 1);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException expected) {
        }
        try {
            new LocalSessionPool(ioreactor, -1, 1);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException expected) {
        }
        try {
            new LocalSessionPool(ioreactor, 1, -1);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException expected) {
        }
    }

    @Test
    public void testSuccessfulConnect() throws Exception {
        final IOSession iosession = Mockito.mock(IOSession.class);
        final SessionRequest sessionRequest = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest.getAttachment()).thenReturn("somehost");
        Mockito.when(sessionRequest.getSession()).thenReturn(iosession);
        final ConnectingIOReactor ioreactor = Mockito.mock(ConnectingIOReactor.class);
        Mockito.when(ioreactor.connect(
                Matchers.any(SocketAddress.class),
                Matchers.any(SocketAddress.class),
                Matchers.any(), Matchers.any(SessionRequestCallback.class))).
                thenReturn(sessionRequest);
        final LocalSessionPool pool = new LocalSessionPool(ioreactor, 2, 10);
        final Future<LocalPoolEntry> future = pool.lease("somehost", null, 100, TimeUnit.MILLISECONDS, null);
        Mockito.verify(sessionRequest).setConnectTimeout(100);

        PoolStats totals = pool.getTotalStats();
        Assert.assertEquals(0, totals.getAvailable());
        Assert.assertEquals(0, totals.getLeased());
        Assert.assertEquals(1, totals.getPending());

        pool.requestCompleted(sessionRequest);

        Assert.assertTrue(future.isDone());
        Assert.assertFalse(future.isCancelled());
        final LocalPoolEntry entry = future.get();
        Assert.assertNotNull(entry);

        totals = pool.getTotalStats();
        Assert.assertEquals(0, totals.getAvailable());
        Assert.assertEquals(1, totals.getLeased());
        Assert.assertEquals(0, totals.getPending());
    }

    @Test
    public void testFailedConnect() throws Exception {
        final SessionRequest sessionRequest = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest.getAttachment()).thenReturn("somehost");
        Mockito.when(sessionRequest.getException()).thenReturn(new IOException());
        final ConnectingIOReactor ioreactor = Mockito.mock(ConnectingIOReactor.class);
        Mockito.when(ioreactor.connect(
                Matchers.any(SocketAddress.class),
                Matchers.any(SocketAddress.class),
                Matchers.any(), Matchers.any(SessionRequestCallback.class))).
                thenReturn(sessionRequest);
        final LocalSessionPool pool = new LocalSessionPool(ioreactor, 2, 10);
        final Future<LocalPoolEntry> future = pool.lease("somehost", null);

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
        } catch (final ExecutionException ex) {
            Assert.assertTrue(ex.getCause() instanceof IOException);
        }

        totals = pool.getTotalStats();
        Assert.assertEquals(0, totals.getAvailable());
        Assert.assertEquals(0, totals.getLeased());
        Assert.assertEquals(0, totals.getPending());
    }

    @Test
    public void testCencelledConnect() throws Exception {
        final IOSession iosession = Mockito.mock(IOSession.class);
        final SessionRequest sessionRequest = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest.getAttachment()).thenReturn("somehost");
        Mockito.when(sessionRequest.getSession()).thenReturn(iosession);
        final ConnectingIOReactor ioreactor = Mockito.mock(ConnectingIOReactor.class);
        Mockito.when(ioreactor.connect(
                Matchers.any(SocketAddress.class),
                Matchers.any(SocketAddress.class),
                Matchers.any(), Matchers.any(SessionRequestCallback.class))).
                thenReturn(sessionRequest);
        Mockito.when(ioreactor.getStatus()).thenReturn(IOReactorStatus.ACTIVE);
        final LocalSessionPool pool = new LocalSessionPool(ioreactor, 2, 10);
        final Future<LocalPoolEntry> future = pool.lease("somehost", null);

        PoolStats totals = pool.getTotalStats();
        Assert.assertEquals(0, totals.getAvailable());
        Assert.assertEquals(0, totals.getLeased());
        Assert.assertEquals(1, totals.getPending());

        pool.requestCancelled(sessionRequest);

        Assert.assertTrue(future.isDone());
        Assert.assertTrue(future.isCancelled());
        try {
            future.get();
            Assert.fail("CancellationException expected");
        } catch (final CancellationException ignore) {
        }

        totals = pool.getTotalStats();
        Assert.assertEquals(0, totals.getAvailable());
        Assert.assertEquals(0, totals.getLeased());
        Assert.assertEquals(0, totals.getPending());
    }

    @Test
    public void testTimeoutConnect() throws Exception {
        final IOSession iosession = Mockito.mock(IOSession.class);
        final SessionRequest sessionRequest = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest.getAttachment()).thenReturn("somehost");
        Mockito.when(sessionRequest.getRemoteAddress())
                .thenReturn(new InetSocketAddress(InetAddress.getByAddress(new byte[]{127, 0, 0, 1}), 80));
        Mockito.when(sessionRequest.getSession()).thenReturn(iosession);
        final ConnectingIOReactor ioreactor = Mockito.mock(ConnectingIOReactor.class);
        Mockito.when(ioreactor.connect(
                Matchers.any(SocketAddress.class),
                Matchers.any(SocketAddress.class),
                Matchers.any(), Matchers.any(SessionRequestCallback.class))).
                thenReturn(sessionRequest);
        final LocalSessionPool pool = new LocalSessionPool(ioreactor, 2, 10);
        final Future<LocalPoolEntry> future = pool.lease("somehost", null);

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
        } catch (final ExecutionException ex) {
            Assert.assertTrue(ex.getCause() instanceof ConnectException);
            Assert.assertEquals("Timeout connecting to [/127.0.0.1:80]", ex.getCause().getMessage());
        }

        totals = pool.getTotalStats();
        Assert.assertEquals(0, totals.getAvailable());
        Assert.assertEquals(0, totals.getLeased());
        Assert.assertEquals(0, totals.getPending());
    }

    @Test
    public void testConnectUnknownHost() throws Exception {
        final SessionRequest sessionRequest = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest.getAttachment()).thenReturn("somehost");
        Mockito.when(sessionRequest.getException()).thenReturn(new IOException());
        final ConnectingIOReactor ioreactor = Mockito.mock(ConnectingIOReactor.class);
        @SuppressWarnings("unchecked")
        final SocketAddressResolver<String> addressResolver = Mockito.mock(SocketAddressResolver.class);
        Mockito.when(addressResolver.resolveRemoteAddress("somehost")).thenThrow(new UnknownHostException());
        final LocalSessionPool pool = new LocalSessionPool(ioreactor, addressResolver, 2, 10);
        final Future<LocalPoolEntry> future = pool.lease("somehost", null);

        Assert.assertTrue(future.isDone());
        Assert.assertFalse(future.isCancelled());
        try {
            future.get();
            Assert.fail("ExecutionException should have been thrown");
        } catch (final ExecutionException ex) {
            Assert.assertTrue(ex.getCause() instanceof UnknownHostException);
        }
    }

    @Test
    public void testLeaseRelease() throws Exception {
        final IOSession iosession1 = Mockito.mock(IOSession.class);
        final SessionRequest sessionRequest1 = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest1.getAttachment()).thenReturn("somehost");
        Mockito.when(sessionRequest1.getSession()).thenReturn(iosession1);

        final IOSession iosession2 = Mockito.mock(IOSession.class);
        final SessionRequest sessionRequest2 = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest2.getAttachment()).thenReturn("otherhost");
        Mockito.when(sessionRequest2.getSession()).thenReturn(iosession2);

        final ConnectingIOReactor ioreactor = Mockito.mock(ConnectingIOReactor.class);
        Mockito.when(ioreactor.connect(
                Matchers.eq(InetSocketAddress.createUnresolved("somehost", 80)),
                Matchers.any(SocketAddress.class),
                Matchers.any(), Matchers.any(SessionRequestCallback.class))).
                thenReturn(sessionRequest1);
        Mockito.when(ioreactor.connect(
                Matchers.eq(InetSocketAddress.createUnresolved("otherhost", 80)),
                Matchers.any(SocketAddress.class),
                Matchers.any(), Matchers.any(SessionRequestCallback.class))).
                thenReturn(sessionRequest2);

        final LocalSessionPool pool = new LocalSessionPool(ioreactor, 2, 10);
        final Future<LocalPoolEntry> future1 = pool.lease("somehost", null);
        pool.requestCompleted(sessionRequest1);
        final Future<LocalPoolEntry> future2 = pool.lease("somehost", null);
        pool.requestCompleted(sessionRequest1);
        final Future<LocalPoolEntry> future3 = pool.lease("otherhost", null);
        pool.requestCompleted(sessionRequest2);

        final LocalPoolEntry entry1 = future1.get();
        Assert.assertNotNull(entry1);
        final LocalPoolEntry entry2 = future2.get();
        Assert.assertNotNull(entry2);
        final LocalPoolEntry entry3 = future3.get();
        Assert.assertNotNull(entry3);

        pool.release(entry1, true);
        pool.release(entry2, true);
        pool.release(entry3, false);
        Mockito.verify(iosession1, Mockito.never()).close();
        Mockito.verify(iosession2, Mockito.times(1)).close();

        final PoolStats totals = pool.getTotalStats();
        Assert.assertEquals(2, totals.getAvailable());
        Assert.assertEquals(0, totals.getLeased());
        Assert.assertEquals(0, totals.getPending());
    }

    @Test
    public void testLeaseIllegal() throws Exception {
        final ConnectingIOReactor ioreactor = Mockito.mock(ConnectingIOReactor.class);
        final LocalSessionPool pool = new LocalSessionPool(ioreactor, 2, 10);
        try {
            pool.lease(null, null, 0, TimeUnit.MILLISECONDS, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException expected) {
        }
        try {
            pool.lease("somehost", null, 0, null, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException expected) {
        }
    }

    @Test
    public void testReleaseUnknownEntry() throws Exception {
        final ConnectingIOReactor ioreactor = Mockito.mock(ConnectingIOReactor.class);
        final LocalSessionPool pool = new LocalSessionPool(ioreactor, 2, 2);
        pool.release(new LocalPoolEntry("somehost", Mockito.mock(IOSession.class)), true);
    }

    @Test
    public void testMaxLimits() throws Exception {
        final IOSession iosession1 = Mockito.mock(IOSession.class);
        final SessionRequest sessionRequest1 = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest1.getAttachment()).thenReturn("somehost");
        Mockito.when(sessionRequest1.getSession()).thenReturn(iosession1);

        final IOSession iosession2 = Mockito.mock(IOSession.class);
        final SessionRequest sessionRequest2 = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest2.getAttachment()).thenReturn("otherhost");
        Mockito.when(sessionRequest2.getSession()).thenReturn(iosession2);

        final ConnectingIOReactor ioreactor = Mockito.mock(ConnectingIOReactor.class);
        Mockito.when(ioreactor.connect(
                Matchers.eq(InetSocketAddress.createUnresolved("somehost", 80)),
                Matchers.any(SocketAddress.class),
                Matchers.any(), Matchers.any(SessionRequestCallback.class))).
                thenReturn(sessionRequest1);
        Mockito.when(ioreactor.connect(
                Matchers.eq(InetSocketAddress.createUnresolved("otherhost", 80)),
                Matchers.any(SocketAddress.class),
                Matchers.any(), Matchers.any(SessionRequestCallback.class))).
                thenReturn(sessionRequest2);

        final LocalSessionPool pool = new LocalSessionPool(ioreactor, 2, 10);
        pool.setMaxPerRoute("somehost", 2);
        pool.setMaxPerRoute("otherhost", 1);
        pool.setMaxTotal(3);

        final Future<LocalPoolEntry> future1 = pool.lease("somehost", null);
        pool.requestCompleted(sessionRequest1);
        final Future<LocalPoolEntry> future2 = pool.lease("somehost", null);
        pool.requestCompleted(sessionRequest1);
        final Future<LocalPoolEntry> future3 = pool.lease("otherhost", null);
        pool.requestCompleted(sessionRequest2);

        final LocalPoolEntry entry1 = future1.get();
        Assert.assertNotNull(entry1);
        final LocalPoolEntry entry2 = future2.get();
        Assert.assertNotNull(entry2);
        final LocalPoolEntry entry3 = future3.get();
        Assert.assertNotNull(entry3);

        pool.release(entry1, true);
        pool.release(entry2, true);
        pool.release(entry3, true);

        final PoolStats totals = pool.getTotalStats();
        Assert.assertEquals(3, totals.getAvailable());
        Assert.assertEquals(0, totals.getLeased());
        Assert.assertEquals(0, totals.getPending());

        final Future<LocalPoolEntry> future4 = pool.lease("somehost", null);
        final Future<LocalPoolEntry> future5 = pool.lease("somehost", null);
        final Future<LocalPoolEntry> future6 = pool.lease("otherhost", null);
        final Future<LocalPoolEntry> future7 = pool.lease("somehost", null);
        final Future<LocalPoolEntry> future8 = pool.lease("somehost", null);
        final Future<LocalPoolEntry> future9 = pool.lease("otherhost", null);

        Assert.assertTrue(future4.isDone());
        final LocalPoolEntry entry4 = future4.get();
        Assert.assertNotNull(entry4);
        Assert.assertTrue(future5.isDone());
        final LocalPoolEntry entry5 = future5.get();
        Assert.assertNotNull(entry5);
        Assert.assertTrue(future6.isDone());
        final LocalPoolEntry entry6 = future6.get();
        Assert.assertNotNull(entry6);
        Assert.assertFalse(future7.isDone());
        Assert.assertFalse(future8.isDone());
        Assert.assertFalse(future9.isDone());

        Mockito.verify(ioreactor, Mockito.times(3)).connect(
                Matchers.any(SocketAddress.class), Matchers.any(SocketAddress.class),
                Matchers.any(), Matchers.any(SessionRequestCallback.class));

        pool.release(entry4, true);
        pool.release(entry5, false);
        pool.release(entry6, true);

        Assert.assertTrue(future7.isDone());
        Assert.assertFalse(future8.isDone());
        Assert.assertTrue(future9.isDone());

        Mockito.verify(ioreactor, Mockito.times(4)).connect(
                Matchers.any(SocketAddress.class), Matchers.any(SocketAddress.class),
                Matchers.any(), Matchers.any(SessionRequestCallback.class));
    }

    @Test
    public void testConnectionRedistributionOnTotalMaxLimit() throws Exception {
        final IOSession iosession1 = Mockito.mock(IOSession.class);
        final SessionRequest sessionRequest1 = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest1.getAttachment()).thenReturn("somehost");
        Mockito.when(sessionRequest1.getSession()).thenReturn(iosession1);

        final IOSession iosession2 = Mockito.mock(IOSession.class);
        final SessionRequest sessionRequest2 = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest2.getAttachment()).thenReturn("somehost");
        Mockito.when(sessionRequest2.getSession()).thenReturn(iosession2);

        final IOSession iosession3 = Mockito.mock(IOSession.class);
        final SessionRequest sessionRequest3 = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest3.getAttachment()).thenReturn("otherhost");
        Mockito.when(sessionRequest3.getSession()).thenReturn(iosession3);

        final IOSession iosession4 = Mockito.mock(IOSession.class);
        final SessionRequest sessionRequest4 = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest4.getAttachment()).thenReturn("otherhost");
        Mockito.when(sessionRequest4.getSession()).thenReturn(iosession4);

        final ConnectingIOReactor ioreactor = Mockito.mock(ConnectingIOReactor.class);
        Mockito.when(ioreactor.connect(
                Matchers.eq(InetSocketAddress.createUnresolved("somehost", 80)),
                Matchers.any(SocketAddress.class),
                Matchers.any(), Matchers.any(SessionRequestCallback.class))).
                thenReturn(sessionRequest1, sessionRequest2, sessionRequest1);
        Mockito.when(ioreactor.connect(
                Matchers.eq(InetSocketAddress.createUnresolved("otherhost", 80)),
                Matchers.any(SocketAddress.class),
                Matchers.any(), Matchers.any(SessionRequestCallback.class))).
                thenReturn(sessionRequest3, sessionRequest4, sessionRequest3);

        final LocalSessionPool pool = new LocalSessionPool(ioreactor, 2, 10);
        pool.setMaxPerRoute("somehost", 2);
        pool.setMaxPerRoute("otherhost", 2);
        pool.setMaxTotal(2);

        final Future<LocalPoolEntry> future1 = pool.lease("somehost", null);
        final Future<LocalPoolEntry> future2 = pool.lease("somehost", null);
        final Future<LocalPoolEntry> future3 = pool.lease("otherhost", null);
        final Future<LocalPoolEntry> future4 = pool.lease("otherhost", null);

        Mockito.verify(ioreactor, Mockito.times(2)).connect(
                Matchers.eq(InetSocketAddress.createUnresolved("somehost", 80)),
                Matchers.any(SocketAddress.class),
                Matchers.any(), Matchers.any(SessionRequestCallback.class));

        Mockito.verify(ioreactor, Mockito.never()).connect(
                Matchers.eq(InetSocketAddress.createUnresolved("otherhost", 80)),
                Matchers.any(SocketAddress.class),
                Matchers.any(), Matchers.any(SessionRequestCallback.class));

        pool.requestCompleted(sessionRequest1);
        pool.requestCompleted(sessionRequest2);

        Assert.assertTrue(future1.isDone());
        final LocalPoolEntry entry1 = future1.get();
        Assert.assertNotNull(entry1);
        Assert.assertTrue(future2.isDone());
        final LocalPoolEntry entry2 = future2.get();
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
                Matchers.eq(InetSocketAddress.createUnresolved("somehost", 80)),
                Matchers.any(SocketAddress.class),
                Matchers.any(), Matchers.any(SessionRequestCallback.class));

        Mockito.verify(ioreactor, Mockito.times(2)).connect(
                Matchers.eq(InetSocketAddress.createUnresolved("otherhost", 80)),
                Matchers.any(SocketAddress.class),
                Matchers.any(), Matchers.any(SessionRequestCallback.class));

        pool.requestCompleted(sessionRequest3);
        pool.requestCompleted(sessionRequest4);

        Assert.assertTrue(future3.isDone());
        final LocalPoolEntry entry3 = future3.get();
        Assert.assertNotNull(entry3);
        Assert.assertTrue(future4.isDone());
        final LocalPoolEntry entry4 = future4.get();
        Assert.assertNotNull(entry4);

        totals = pool.getTotalStats();
        Assert.assertEquals(0, totals.getAvailable());
        Assert.assertEquals(2, totals.getLeased());
        Assert.assertEquals(0, totals.getPending());

        final Future<LocalPoolEntry> future5 = pool.lease("somehost", null);
        final Future<LocalPoolEntry> future6 = pool.lease("otherhost", null);

        Mockito.verify(ioreactor, Mockito.times(2)).connect(
                Matchers.eq(InetSocketAddress.createUnresolved("somehost", 80)),
                Matchers.any(SocketAddress.class),
                Matchers.any(), Matchers.any(SessionRequestCallback.class));

        Mockito.verify(ioreactor, Mockito.times(2)).connect(
                Matchers.eq(InetSocketAddress.createUnresolved("otherhost", 80)),
                Matchers.any(SocketAddress.class),
                Matchers.any(), Matchers.any(SessionRequestCallback.class));

        pool.release(entry3, true);
        pool.release(entry4, true);

        Mockito.verify(ioreactor, Mockito.times(3)).connect(
                Matchers.eq(InetSocketAddress.createUnresolved("somehost", 80)),
                Matchers.any(SocketAddress.class),
                Matchers.any(), Matchers.any(SessionRequestCallback.class));

        Mockito.verify(ioreactor, Mockito.times(2)).connect(
                Matchers.eq(InetSocketAddress.createUnresolved("otherhost", 80)),
                Matchers.any(SocketAddress.class),
                Matchers.any(), Matchers.any(SessionRequestCallback.class));

        pool.requestCompleted(sessionRequest1);

        Assert.assertTrue(future5.isDone());
        final LocalPoolEntry entry5 = future5.get();
        Assert.assertNotNull(entry5);
        Assert.assertTrue(future6.isDone());
        final LocalPoolEntry entry6 = future6.get();
        Assert.assertNotNull(entry6);

        totals = pool.getTotalStats();
        Assert.assertEquals(0, totals.getAvailable());
        Assert.assertEquals(2, totals.getLeased());
        Assert.assertEquals(0, totals.getPending());

        pool.release(entry5, true);
        pool.release(entry6, true);

        Mockito.verify(ioreactor, Mockito.times(3)).connect(
                Matchers.eq(InetSocketAddress.createUnresolved("somehost", 80)),
                Matchers.any(SocketAddress.class),
                Matchers.any(), Matchers.any(SessionRequestCallback.class));

        Mockito.verify(ioreactor, Mockito.times(2)).connect(
                Matchers.eq(InetSocketAddress.createUnresolved("otherhost", 80)),
                Matchers.any(SocketAddress.class),
                Matchers.any(), Matchers.any(SessionRequestCallback.class));

        totals = pool.getTotalStats();
        Assert.assertEquals(2, totals.getAvailable());
        Assert.assertEquals(0, totals.getLeased());
        Assert.assertEquals(0, totals.getPending());
    }

    @Test
    public void testStatefulConnectionRedistributionOnPerRouteMaxLimit() throws Exception {
        final IOSession iosession1 = Mockito.mock(IOSession.class);
        final SessionRequest sessionRequest1 = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest1.getAttachment()).thenReturn("somehost");
        Mockito.when(sessionRequest1.getSession()).thenReturn(iosession1);

        final IOSession iosession2 = Mockito.mock(IOSession.class);
        final SessionRequest sessionRequest2 = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest2.getAttachment()).thenReturn("somehost");
        Mockito.when(sessionRequest2.getSession()).thenReturn(iosession2);

        final IOSession iosession3 = Mockito.mock(IOSession.class);
        final SessionRequest sessionRequest3 = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest3.getAttachment()).thenReturn("somehost");
        Mockito.when(sessionRequest3.getSession()).thenReturn(iosession3);

        final ConnectingIOReactor ioreactor = Mockito.mock(ConnectingIOReactor.class);
        Mockito.when(ioreactor.connect(
                Matchers.eq(InetSocketAddress.createUnresolved("somehost", 80)),
                Matchers.any(SocketAddress.class),
                Matchers.any(), Matchers.any(SessionRequestCallback.class))).
                thenReturn(sessionRequest1, sessionRequest2, sessionRequest3);

        final LocalSessionPool pool = new LocalSessionPool(ioreactor, 2, 10);
        pool.setMaxPerRoute("somehost", 2);
        pool.setMaxTotal(2);

        final Future<LocalPoolEntry> future1 = pool.lease("somehost", null);
        final Future<LocalPoolEntry> future2 = pool.lease("somehost", null);

        Mockito.verify(ioreactor, Mockito.times(2)).connect(
                Matchers.eq(InetSocketAddress.createUnresolved("somehost", 80)),
                Matchers.any(SocketAddress.class),
                Matchers.any(), Matchers.any(SessionRequestCallback.class));

        pool.requestCompleted(sessionRequest1);
        pool.requestCompleted(sessionRequest2);

        Assert.assertTrue(future1.isDone());
        final LocalPoolEntry entry1 = future1.get();
        Assert.assertNotNull(entry1);
        Assert.assertTrue(future2.isDone());
        final LocalPoolEntry entry2 = future2.get();
        Assert.assertNotNull(entry2);

        PoolStats totals = pool.getTotalStats();
        Assert.assertEquals(0, totals.getAvailable());
        Assert.assertEquals(2, totals.getLeased());
        Assert.assertEquals(0, totals.getPending());

        entry1.setState("some-stuff");
        pool.release(entry1, true);
        entry2.setState("some-stuff");
        pool.release(entry2, true);

        final Future<LocalPoolEntry> future3 = pool.lease("somehost", "some-stuff");
        final Future<LocalPoolEntry> future4 = pool.lease("somehost", "some-stuff");

        Assert.assertTrue(future1.isDone());
        final LocalPoolEntry entry3 = future3.get();
        Assert.assertNotNull(entry3);
        Assert.assertTrue(future4.isDone());
        final LocalPoolEntry entry4 = future4.get();
        Assert.assertNotNull(entry4);

        Mockito.verify(ioreactor, Mockito.times(2)).connect(
                Matchers.eq(InetSocketAddress.createUnresolved("somehost", 80)),
                Matchers.any(SocketAddress.class),
                Matchers.any(), Matchers.any(SessionRequestCallback.class));

        pool.release(entry3, true);
        pool.release(entry4, true);

        totals = pool.getTotalStats();
        Assert.assertEquals(2, totals.getAvailable());
        Assert.assertEquals(0, totals.getLeased());
        Assert.assertEquals(0, totals.getPending());

        final Future<LocalPoolEntry> future5 = pool.lease("somehost", "some-other-stuff");

        Assert.assertFalse(future5.isDone());

        Mockito.verify(ioreactor, Mockito.times(3)).connect(
                Matchers.eq(InetSocketAddress.createUnresolved("somehost", 80)),
                Matchers.any(SocketAddress.class),
                Matchers.any(), Matchers.any(SessionRequestCallback.class));

        Mockito.verify(iosession2).close();
        Mockito.verify(iosession1, Mockito.never()).close();

        totals = pool.getTotalStats();
        Assert.assertEquals(1, totals.getAvailable());
        Assert.assertEquals(0, totals.getLeased());
        Assert.assertEquals(1, totals.getPending());
    }

    @Test
    public void testCreateNewIfExpired() throws Exception {
        final IOSession iosession1 = Mockito.mock(IOSession.class);
        Mockito.when(iosession1.isClosed()).thenReturn(Boolean.TRUE);
        final SessionRequest sessionRequest1 = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest1.getAttachment()).thenReturn("somehost");
        Mockito.when(sessionRequest1.getSession()).thenReturn(iosession1);

        final ConnectingIOReactor ioreactor = Mockito.mock(ConnectingIOReactor.class);
        Mockito.when(ioreactor.connect(
                Matchers.eq(InetSocketAddress.createUnresolved("somehost", 80)),
                Matchers.any(SocketAddress.class),
                Matchers.any(), Matchers.any(SessionRequestCallback.class))).
                thenReturn(sessionRequest1);

        final LocalSessionPool pool = new LocalSessionPool(ioreactor, 2, 2);

        final Future<LocalPoolEntry> future1 = pool.lease("somehost", null);

        Mockito.verify(ioreactor, Mockito.times(1)).connect(
                Matchers.any(SocketAddress.class), Matchers.any(SocketAddress.class),
                Matchers.any(), Matchers.any(SessionRequestCallback.class));

        pool.requestCompleted(sessionRequest1);

        Assert.assertTrue(future1.isDone());
        final LocalPoolEntry entry1 = future1.get();
        Assert.assertNotNull(entry1);

        entry1.updateExpiry(1, TimeUnit.MILLISECONDS);
        pool.release(entry1, true);

        Thread.sleep(200L);

        final Future<LocalPoolEntry> future2 = pool.lease("somehost", null);

        Assert.assertFalse(future2.isDone());

        Mockito.verify(iosession1).close();
        Mockito.verify(ioreactor, Mockito.times(2)).connect(
                Matchers.any(SocketAddress.class), Matchers.any(SocketAddress.class),
                Matchers.any(), Matchers.any(SessionRequestCallback.class));

        final PoolStats totals = pool.getTotalStats();
        Assert.assertEquals(0, totals.getAvailable());
        Assert.assertEquals(0, totals.getLeased());
        Assert.assertEquals(1, totals.getPending());
        Assert.assertEquals(Collections.singleton("somehost"), pool.getRoutes());
        final PoolStats stats = pool.getStats("somehost");
        Assert.assertEquals(0, stats.getAvailable());
        Assert.assertEquals(0, stats.getLeased());
        Assert.assertEquals(1, stats.getPending());
    }

    @Test
    public void testCloseExpired() throws Exception {
        final IOSession iosession1 = Mockito.mock(IOSession.class);
        Mockito.when(iosession1.isClosed()).thenReturn(Boolean.TRUE);
        final SessionRequest sessionRequest1 = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest1.getAttachment()).thenReturn("somehost");
        Mockito.when(sessionRequest1.getSession()).thenReturn(iosession1);

        final IOSession iosession2 = Mockito.mock(IOSession.class);
        final SessionRequest sessionRequest2 = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest2.getAttachment()).thenReturn("somehost");
        Mockito.when(sessionRequest2.getSession()).thenReturn(iosession2);

        final ConnectingIOReactor ioreactor = Mockito.mock(ConnectingIOReactor.class);
        Mockito.when(ioreactor.connect(
                Matchers.any(SocketAddress.class), Matchers.any(SocketAddress.class),
                Matchers.any(), Matchers.any(SessionRequestCallback.class))).
                thenReturn(sessionRequest1, sessionRequest2);

        final LocalSessionPool pool = new LocalSessionPool(ioreactor, 2, 2);

        final Future<LocalPoolEntry> future1 = pool.lease("somehost", null);
        final Future<LocalPoolEntry> future2 = pool.lease("somehost", null);

        pool.requestCompleted(sessionRequest1);
        pool.requestCompleted(sessionRequest2);

        Assert.assertTrue(future1.isDone());
        final LocalPoolEntry entry1 = future1.get();
        Assert.assertNotNull(entry1);
        Assert.assertTrue(future2.isDone());
        final LocalPoolEntry entry2 = future2.get();
        Assert.assertNotNull(entry2);

        entry1.updateExpiry(1, TimeUnit.MILLISECONDS);
        pool.release(entry1, true);

        Thread.sleep(200);

        entry2.updateExpiry(1000, TimeUnit.SECONDS);
        pool.release(entry2, true);

        pool.closeExpired();

        Mockito.verify(iosession1).close();
        Mockito.verify(iosession2, Mockito.never()).close();

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
        final IOSession iosession1 = Mockito.mock(IOSession.class);
        final SessionRequest sessionRequest1 = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest1.getAttachment()).thenReturn("somehost");
        Mockito.when(sessionRequest1.getSession()).thenReturn(iosession1);

        final IOSession iosession2 = Mockito.mock(IOSession.class);
        final SessionRequest sessionRequest2 = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest2.getAttachment()).thenReturn("somehost");
        Mockito.when(sessionRequest2.getSession()).thenReturn(iosession2);

        final ConnectingIOReactor ioreactor = Mockito.mock(ConnectingIOReactor.class);
        Mockito.when(ioreactor.connect(
                Matchers.any(SocketAddress.class), Matchers.any(SocketAddress.class),
                Matchers.any(), Matchers.any(SessionRequestCallback.class))).
                thenReturn(sessionRequest1, sessionRequest2);

        final LocalSessionPool pool = new LocalSessionPool(ioreactor, 2, 2);

        final Future<LocalPoolEntry> future1 = pool.lease("somehost", null);
        final Future<LocalPoolEntry> future2 = pool.lease("somehost", null);

        pool.requestCompleted(sessionRequest1);
        pool.requestCompleted(sessionRequest2);

        Assert.assertTrue(future1.isDone());
        final LocalPoolEntry entry1 = future1.get();
        Assert.assertNotNull(entry1);
        Assert.assertTrue(future2.isDone());
        final LocalPoolEntry entry2 = future2.get();
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
        final IOSession iosession1 = Mockito.mock(IOSession.class);
        Mockito.when(iosession1.isClosed()).thenReturn(Boolean.TRUE);
        final SessionRequest sessionRequest1 = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest1.getAttachment()).thenReturn("somehost");
        Mockito.when(sessionRequest1.getSession()).thenReturn(iosession1);

        final ConnectingIOReactor ioreactor = Mockito.mock(ConnectingIOReactor.class);
        Mockito.when(ioreactor.connect(
                Matchers.any(SocketAddress.class), Matchers.any(SocketAddress.class),
                Matchers.any(), Matchers.any(SessionRequestCallback.class))).
                thenReturn(sessionRequest1);

        final LocalSessionPool pool = new LocalSessionPool(ioreactor, 1, 1);

        final Future<LocalPoolEntry> future1 = pool.lease("somehost", null, 0, TimeUnit.MILLISECONDS, null);
        final Future<LocalPoolEntry> future2 = pool.lease("somehost", null, 0, TimeUnit.MILLISECONDS, null);
        final Future<LocalPoolEntry> future3 = pool.lease("somehost", null, 10, TimeUnit.MILLISECONDS, null);

        pool.requestCompleted(sessionRequest1);

        Assert.assertTrue(future1.isDone());
        final LocalPoolEntry entry1 = future1.get();
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
        final ConnectingIOReactor ioreactor = Mockito.mock(ConnectingIOReactor.class);
        final LocalSessionPool pool = new LocalSessionPool(ioreactor, 2, 2);
        pool.closeIdle(50, null);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testGetStatsInvalid() throws Exception {
        final ConnectingIOReactor ioreactor = Mockito.mock(ConnectingIOReactor.class);
        final LocalSessionPool pool = new LocalSessionPool(ioreactor, 2, 2);
        pool.getStats(null);
    }

    @Test
    public void testSetMaxInvalid() throws Exception {
        final ConnectingIOReactor ioreactor = Mockito.mock(ConnectingIOReactor.class);
        final LocalSessionPool pool = new LocalSessionPool(ioreactor, 2, 2);
        try {
            pool.setMaxTotal(-1);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException expected) {
        }
        try {
            pool.setMaxPerRoute(null, 1);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException expected) {
        }
        try {
            pool.setDefaultMaxPerRoute(-1);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException expected) {
        }
    }

    @Test
    public void testSetMaxPerRoute() throws Exception {
        final ConnectingIOReactor ioreactor = Mockito.mock(ConnectingIOReactor.class);
        final LocalSessionPool pool = new LocalSessionPool(ioreactor, 2, 2);
        pool.setMaxPerRoute("somehost", 1);
        Assert.assertEquals(1, pool.getMaxPerRoute("somehost"));
        pool.setMaxPerRoute("somehost", 0);
        Assert.assertEquals(0, pool.getMaxPerRoute("somehost"));
        pool.setMaxPerRoute("somehost", -1);
        Assert.assertEquals(2, pool.getMaxPerRoute("somehost"));
    }

    @Test
    public void testShutdown() throws Exception {
        final ConnectingIOReactor ioreactor = Mockito.mock(ConnectingIOReactor.class);
        final LocalSessionPool pool = new LocalSessionPool(ioreactor, 2, 2);
        pool.shutdown(1000);
        Mockito.verify(ioreactor, Mockito.times(1)).shutdown(1000);
        pool.shutdown(1000);
        Mockito.verify(ioreactor, Mockito.times(1)).shutdown(1000);
        try {
            pool.lease("somehost", null);
            Assert.fail("IllegalStateException should have been thrown");
        } catch (final IllegalStateException expected) {
        }
        // Ignored if shut down
        pool.release(new LocalPoolEntry("somehost", Mockito.mock(IOSession.class)), true);
        pool.requestCompleted(Mockito.mock(SessionRequest.class));
        pool.requestFailed(Mockito.mock(SessionRequest.class));
        pool.requestCancelled(Mockito.mock(SessionRequest.class));
        pool.requestTimeout(Mockito.mock(SessionRequest.class));
    }

    @Test
    public void testLeaseRequestCanceled() throws Exception {
        final IOSession iosession1 = Mockito.mock(IOSession.class);
        Mockito.when(iosession1.isClosed()).thenReturn(Boolean.TRUE);
        final SessionRequest sessionRequest1 = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest1.getAttachment()).thenReturn("somehost");
        Mockito.when(sessionRequest1.getSession()).thenReturn(iosession1);

        final ConnectingIOReactor ioreactor = Mockito.mock(ConnectingIOReactor.class);
        Mockito.when(ioreactor.connect(
                Matchers.any(SocketAddress.class), Matchers.any(SocketAddress.class),
                Matchers.any(), Matchers.any(SessionRequestCallback.class))).
                thenReturn(sessionRequest1);
        Mockito.when(ioreactor.getStatus()).thenReturn(IOReactorStatus.ACTIVE);

        final LocalSessionPool pool = new LocalSessionPool(ioreactor, 1, 1);

        final Future<LocalPoolEntry> future1 = pool.lease("somehost", null, 0, TimeUnit.MILLISECONDS, null);
        future1.cancel(true);

        pool.requestCompleted(sessionRequest1);

        Assert.assertTrue(future1.isDone());
        try {
            future1.get();
            Assert.fail("CancellationException expected");
        } catch (final CancellationException ignore) {
        }

        final PoolStats totals = pool.getTotalStats();
        Assert.assertEquals(1, totals.getAvailable());
        Assert.assertEquals(0, totals.getLeased());
    }

    @Test
    public void testLeaseRequestCanceledWhileConnecting() throws Exception {
        final IOSession iosession1 = Mockito.mock(IOSession.class);
        Mockito.when(iosession1.isClosed()).thenReturn(Boolean.TRUE);
        final SessionRequest sessionRequest1 = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest1.getAttachment()).thenReturn("somehost");
        Mockito.when(sessionRequest1.getSession()).thenReturn(iosession1);

        final ConnectingIOReactor ioreactor = Mockito.mock(ConnectingIOReactor.class);
        Mockito.when(ioreactor.connect(
                Matchers.any(SocketAddress.class), Matchers.any(SocketAddress.class),
                Matchers.any(), Matchers.any(SessionRequestCallback.class))).
                thenReturn(sessionRequest1);
        Mockito.when(ioreactor.getStatus()).thenReturn(IOReactorStatus.ACTIVE);

        final LocalSessionPool pool = new LocalSessionPool(ioreactor, 1, 1);

        final Future<LocalPoolEntry> future1 = pool.lease("somehost", null, 0, TimeUnit.MILLISECONDS, null);

        pool.requestCompleted(sessionRequest1);

        Assert.assertTrue(future1.isDone());
        final LocalPoolEntry entry1 = future1.get();
        Assert.assertNotNull(entry1);

        final Future<LocalPoolEntry> future2 = pool.lease("somehost", null, 0, TimeUnit.MILLISECONDS, null);
        future2.cancel(true);

        pool.release(entry1, true);

        final PoolStats totals = pool.getTotalStats();
        Assert.assertEquals(1, totals.getAvailable());
        Assert.assertEquals(0, totals.getLeased());
    }

}
