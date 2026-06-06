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
package org.apache.hc.core5.http2.nio.pool;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.nio.command.StaleCheckCommand;
import org.apache.hc.core5.http2.impl.nio.H2PoolSessionSupport;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.ConnectionInitiator;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TestH2MultiplexingConnPool {

    private static final HttpHost HOST_A = new HttpHost(URIScheme.HTTP.id, "host-a.example.com", 80);
    private static final HttpHost HOST_B = new HttpHost(URIScheme.HTTP.id, "host-b.example.com", 80);
    private static final Timeout CONNECT_TIMEOUT = Timeout.ofSeconds(5);

    static class MockSessionState {

        final AtomicInteger peerMaxStreams;
        final AtomicInteger activeLocalStreams;
        final AtomicBoolean goAwayReceived;
        final AtomicBoolean shutdown;
        volatile H2PoolSessionSupport support;

        MockSessionState(final int peerMaxStreams) {
            this.peerMaxStreams = new AtomicInteger(peerMaxStreams);
            this.activeLocalStreams = new AtomicInteger(0);
            this.goAwayReceived = new AtomicBoolean(false);
            this.shutdown = new AtomicBoolean(false);
        }

        void bind(final H2PoolSessionSupport support) {
            support.updatePeerMaxConcurrentStreams(peerMaxStreams.get());
            support.updateActiveLocalStreams(activeLocalStreams.get());
            support.updateGoAwayReceived(goAwayReceived.get());
            support.updateShutdown(shutdown.get());
            this.support = support;
        }

        void syncToSupport() {
            if (support != null) {
                support.updatePeerMaxConcurrentStreams(peerMaxStreams.get());
                support.updateActiveLocalStreams(activeLocalStreams.get());
                support.updateGoAwayReceived(goAwayReceived.get());
                support.updateShutdown(shutdown.get());
            }
        }
    }

    private IOSession createMockSession() {
        final IOSession session = Mockito.mock(IOSession.class);
        Mockito.when(session.isOpen()).thenReturn(true);
        Mockito.when(session.getLastReadTime()).thenReturn(System.currentTimeMillis());
        Mockito.when(session.getLastWriteTime()).thenReturn(System.currentTimeMillis());
        return session;
    }

    private void setupDeferredConnect(
            final ConnectionInitiator initiator,
            final HttpHost host,
            final AtomicReference<FutureCallback<IOSession>> callbackRef,
            final AtomicReference<H2PoolSessionSupport> supportRef) {
        Mockito.when(initiator.connect(
                        Mockito.eq(host),
                        Mockito.any(InetSocketAddress.class),
                        Mockito.isNull(),
                        Mockito.any(Timeout.class),
                        Mockito.any(),
                        Mockito.any()))
                .thenAnswer(invocation -> {
                    if (supportRef != null) {
                        supportRef.set(invocation.getArgument(4));
                    }
                    final FutureCallback<IOSession> cb = invocation.getArgument(5);
                    callbackRef.set(cb);
                    return new CompletableFuture<>();
                });
    }

    private void setupDeferredConnect(
            final ConnectionInitiator initiator,
            final HttpHost host,
            final AtomicReference<FutureCallback<IOSession>> callbackRef) {
        setupDeferredConnect(initiator, host, callbackRef, null);
    }

    private void setupImmediateConnect(
            final ConnectionInitiator initiator,
            final HttpHost host,
            final IOSession session,
            final MockSessionState state) {
        Mockito.when(initiator.connect(
                        Mockito.eq(host),
                        Mockito.any(InetSocketAddress.class),
                        Mockito.isNull(),
                        Mockito.any(Timeout.class),
                        Mockito.any(),
                        Mockito.any()))
                .thenAnswer(invocation -> {
                    final H2PoolSessionSupport support = invocation.getArgument(4);
                    state.bind(support);
                    final FutureCallback<IOSession> cb = invocation.getArgument(5);
                    cb.completed(session);
                    return CompletableFuture.completedFuture(session);
                });
    }

    @Test
    void testBasicLeaseAndRelease() throws Exception {
        final ConnectionInitiator initiator = Mockito.mock(ConnectionInitiator.class);
        final IOSession session = createMockSession();
        final MockSessionState state = new MockSessionState(100);
        setupImmediateConnect(initiator, HOST_A, session, state);

        final H2MultiplexingConnPool pool = new H2MultiplexingConnPool(initiator, null, null);

        final Future<H2StreamLease> future = pool.lease(HOST_A, CONNECT_TIMEOUT, null);
        Assertions.assertTrue(future.isDone());

        final H2StreamLease lease = future.get();
        Assertions.assertSame(session, lease.getSession());

        lease.releaseReservation();
        pool.close();
    }

    @Test
    void testMultipleStreamsOnOneConnection() throws Exception {
        final ConnectionInitiator initiator = Mockito.mock(ConnectionInitiator.class);
        final IOSession session = createMockSession();
        final MockSessionState state = new MockSessionState(100);
        setupImmediateConnect(initiator, HOST_A, session, state);

        final H2MultiplexingConnPool pool = new H2MultiplexingConnPool(initiator, null, null);

        final H2StreamLease lease1 = pool.lease(HOST_A, CONNECT_TIMEOUT, null).get();
        final H2StreamLease lease2 = pool.lease(HOST_A, CONNECT_TIMEOUT, null).get();
        final H2StreamLease lease3 = pool.lease(HOST_A, CONNECT_TIMEOUT, null).get();

        Assertions.assertSame(session, lease1.getSession());
        Assertions.assertSame(session, lease2.getSession());
        Assertions.assertSame(session, lease3.getSession());

        Mockito.verify(initiator, Mockito.times(1)).connect(
                Mockito.eq(HOST_A),
                Mockito.any(), Mockito.any(), Mockito.any(),
                Mockito.any(), Mockito.any());

        lease1.releaseReservation();
        lease2.releaseReservation();
        lease3.releaseReservation();
        pool.close();
    }

    @Test
    void testSingleFlightDialing() throws Exception {
        final ConnectionInitiator initiator = Mockito.mock(ConnectionInitiator.class);
        final AtomicReference<FutureCallback<IOSession>> connectCb = new AtomicReference<>();
        final AtomicReference<H2PoolSessionSupport> supportRef = new AtomicReference<>();
        setupDeferredConnect(initiator, HOST_A, connectCb, supportRef);

        final H2MultiplexingConnPool pool = new H2MultiplexingConnPool(initiator, null, null, 3, 0);

        final Future<H2StreamLease> f1 = pool.lease(HOST_A, CONNECT_TIMEOUT, null);
        Assertions.assertFalse(f1.isDone());

        final Future<H2StreamLease> f2 = pool.lease(HOST_A, CONNECT_TIMEOUT, null);
        Assertions.assertFalse(f2.isDone());

        Mockito.verify(initiator, Mockito.times(1)).connect(
                Mockito.eq(HOST_A),
                Mockito.any(), Mockito.any(), Mockito.any(),
                Mockito.any(), Mockito.any());

        final MockSessionState state = new MockSessionState(100);
        state.bind(supportRef.get());
        final IOSession session = createMockSession();
        connectCb.get().completed(session);

        Assertions.assertTrue(f1.isDone());
        Assertions.assertTrue(f2.isDone());
        Assertions.assertSame(session, f1.get().getSession());
        Assertions.assertSame(session, f2.get().getSession());

        f1.get().releaseReservation();
        f2.get().releaseReservation();
        pool.close();
    }

    @Test
    void testGlobalLimitBlocksAndCrossRouteWakeup() throws Exception {
        final ConnectionInitiator initiator = Mockito.mock(ConnectionInitiator.class);

        final AtomicReference<FutureCallback<IOSession>> connectCbA = new AtomicReference<>();
        final AtomicReference<H2PoolSessionSupport> supportRefA = new AtomicReference<>();
        setupDeferredConnect(initiator, HOST_A, connectCbA, supportRefA);

        final H2MultiplexingConnPool pool = new H2MultiplexingConnPool(initiator, null, null, 1, 1);

        final Future<H2StreamLease> fA = pool.lease(HOST_A, CONNECT_TIMEOUT, null);

        final MockSessionState stateA = new MockSessionState(100);
        stateA.bind(supportRefA.get());
        final IOSession sessionA = createMockSession();
        connectCbA.get().completed(sessionA);
        Assertions.assertTrue(fA.isDone());

        final AtomicReference<FutureCallback<IOSession>> connectCbB = new AtomicReference<>();
        final AtomicReference<H2PoolSessionSupport> supportRefB = new AtomicReference<>();
        setupDeferredConnect(initiator, HOST_B, connectCbB, supportRefB);

        final Future<H2StreamLease> fB = pool.lease(HOST_B, CONNECT_TIMEOUT, null);
        Assertions.assertFalse(fB.isDone());

        fA.get().releaseReservation();

        Mockito.when(sessionA.isOpen()).thenReturn(false);
        pool.closeExpired();

        Assertions.assertNotNull(connectCbB.get());

        final IOSession sessionB = createMockSession();
        final MockSessionState stateB = new MockSessionState(100);
        stateB.bind(supportRefB.get());
        connectCbB.get().completed(sessionB);

        Assertions.assertTrue(fB.isDone());
        Assertions.assertSame(sessionB, fB.get().getSession());

        fB.get().releaseReservation();
        pool.close();
    }

    @Test
    void testConnectFailureFailsPendingWhenNoLiveEntries() throws Exception {
        final ConnectionInitiator initiator = Mockito.mock(ConnectionInitiator.class);
        final AtomicReference<FutureCallback<IOSession>> connectCb = new AtomicReference<>();
        setupDeferredConnect(initiator, HOST_A, connectCb);

        final H2MultiplexingConnPool pool = new H2MultiplexingConnPool(initiator, null, null);

        final Future<H2StreamLease> f1 = pool.lease(HOST_A, CONNECT_TIMEOUT, null);
        Assertions.assertFalse(f1.isDone());

        connectCb.get().failed(new java.io.IOException("Connection refused"));

        Assertions.assertTrue(f1.isDone());
        Assertions.assertThrows(ExecutionException.class, f1::get);

        pool.close();
    }

    @Test
    void testConnectFailureRetriesWhenLiveEntriesExist() throws Exception {
        final ConnectionInitiator initiator = Mockito.mock(ConnectionInitiator.class);
        final IOSession session1 = createMockSession();
        final MockSessionState state1 = new MockSessionState(1);
        setupImmediateConnect(initiator, HOST_A, session1, state1);

        final H2MultiplexingConnPool pool = new H2MultiplexingConnPool(initiator, null, null, 2, 0);

        final H2StreamLease l1 = pool.lease(HOST_A, CONNECT_TIMEOUT, null).get();
        Assertions.assertSame(session1, l1.getSession());

        final AtomicReference<FutureCallback<IOSession>> connectCb2 = new AtomicReference<>();
        setupDeferredConnect(initiator, HOST_A, connectCb2);

        final Future<H2StreamLease> f2 = pool.lease(HOST_A, CONNECT_TIMEOUT, null);
        Assertions.assertFalse(f2.isDone());
        Assertions.assertNotNull(connectCb2.get());

        final AtomicReference<FutureCallback<IOSession>> connectCb3 = new AtomicReference<>();
        final AtomicReference<H2PoolSessionSupport> supportRef3 = new AtomicReference<>();
        setupDeferredConnect(initiator, HOST_A, connectCb3, supportRef3);

        connectCb2.get().failed(new java.io.IOException("Temporary failure"));

        Assertions.assertFalse(f2.isDone());
        Assertions.assertNotNull(connectCb3.get());

        final IOSession session2 = createMockSession();
        final MockSessionState state2 = new MockSessionState(100);
        state2.bind(supportRef3.get());
        connectCb3.get().completed(session2);

        Assertions.assertTrue(f2.isDone());
        final H2StreamLease l2 = f2.get();
        Assertions.assertSame(session2, l2.getSession());

        l1.releaseReservation();
        l2.releaseReservation();
        pool.close();
    }

    @Test
    void testConnectFailureFreesGlobalSlotForOtherRoutes() throws Exception {
        final ConnectionInitiator initiator = Mockito.mock(ConnectionInitiator.class);

        final H2MultiplexingConnPool pool = new H2MultiplexingConnPool(initiator, null, null, 1, 1);

        final AtomicReference<FutureCallback<IOSession>> connectCbA = new AtomicReference<>();
        setupDeferredConnect(initiator, HOST_A, connectCbA);

        final Future<H2StreamLease> fA = pool.lease(HOST_A, CONNECT_TIMEOUT, null);

        final AtomicReference<FutureCallback<IOSession>> connectCbB = new AtomicReference<>();
        final AtomicReference<H2PoolSessionSupport> supportRefB = new AtomicReference<>();
        setupDeferredConnect(initiator, HOST_B, connectCbB, supportRefB);

        final Future<H2StreamLease> fB = pool.lease(HOST_B, CONNECT_TIMEOUT, null);
        Assertions.assertFalse(fB.isDone());

        connectCbA.get().failed(new java.io.IOException("Connection refused"));

        Assertions.assertTrue(fA.isDone());
        Assertions.assertThrows(ExecutionException.class, fA::get);

        Assertions.assertNotNull(connectCbB.get());

        final IOSession sessionB = createMockSession();
        final MockSessionState stateB = new MockSessionState(100);
        stateB.bind(supportRefB.get());
        connectCbB.get().completed(sessionB);

        Assertions.assertTrue(fB.isDone());
        Assertions.assertSame(sessionB, fB.get().getSession());

        fB.get().releaseReservation();
        pool.close();
    }

    @Test
    void testCloseWhileConnectInFlight() throws Exception {
        final ConnectionInitiator initiator = Mockito.mock(ConnectionInitiator.class);
        final AtomicReference<FutureCallback<IOSession>> connectCb = new AtomicReference<>();
        setupDeferredConnect(initiator, HOST_A, connectCb);

        final H2MultiplexingConnPool pool = new H2MultiplexingConnPool(initiator, null, null);

        final Future<H2StreamLease> f1 = pool.lease(HOST_A, CONNECT_TIMEOUT, null);

        pool.close();

        Assertions.assertTrue(f1.isDone());

        final IOSession lateSession = createMockSession();
        connectCb.get().completed(lateSession);

        Mockito.verify(lateSession).close(Mockito.any(org.apache.hc.core5.io.CloseMode.class));
    }

    @Test
    void testCloseWhileConnectInFlightFailure() throws Exception {
        final ConnectionInitiator initiator = Mockito.mock(ConnectionInitiator.class);
        final AtomicReference<FutureCallback<IOSession>> connectCb = new AtomicReference<>();
        setupDeferredConnect(initiator, HOST_A, connectCb);

        final H2MultiplexingConnPool pool = new H2MultiplexingConnPool(initiator, null, null);

        pool.lease(HOST_A, CONNECT_TIMEOUT, null);
        pool.close();

        connectCb.get().failed(new java.io.IOException("Connection refused"));
    }

    @Test
    void testCancelledWaitersAreSkipped() throws Exception {
        final ConnectionInitiator initiator = Mockito.mock(ConnectionInitiator.class);
        final AtomicReference<FutureCallback<IOSession>> connectCb = new AtomicReference<>();
        final AtomicReference<H2PoolSessionSupport> supportRef = new AtomicReference<>();
        setupDeferredConnect(initiator, HOST_A, connectCb, supportRef);

        final H2MultiplexingConnPool pool = new H2MultiplexingConnPool(initiator, null, null);

        final Future<H2StreamLease> f1 = pool.lease(HOST_A, CONNECT_TIMEOUT, null);
        final Future<H2StreamLease> f2 = pool.lease(HOST_A, CONNECT_TIMEOUT, null);
        final Future<H2StreamLease> f3 = pool.lease(HOST_A, CONNECT_TIMEOUT, null);

        f1.cancel(false);
        f2.cancel(false);

        final IOSession session = createMockSession();
        final MockSessionState state = new MockSessionState(100);
        state.bind(supportRef.get());
        connectCb.get().completed(session);

        Assertions.assertTrue(f3.isDone());
        Assertions.assertSame(session, f3.get().getSession());

        f3.get().releaseReservation();
        pool.close();
    }

    @Test
    void testObserverWakeupDrivesPendingRequests() throws Exception {
        final ConnectionInitiator initiator = Mockito.mock(ConnectionInitiator.class);
        final IOSession session = createMockSession();
        final MockSessionState state = new MockSessionState(1);
        setupImmediateConnect(initiator, HOST_A, session, state);

        final H2MultiplexingConnPool pool = new H2MultiplexingConnPool(initiator, null, null);

        final H2StreamLease lease1 = pool.lease(HOST_A, CONNECT_TIMEOUT, null).get();
        Assertions.assertNotNull(lease1);

        state.activeLocalStreams.set(1);
        state.syncToSupport();
        lease1.releaseReservation();

        final Future<H2StreamLease> f2 = pool.lease(HOST_A, CONNECT_TIMEOUT, null);
        Assertions.assertFalse(f2.isDone());

        state.activeLocalStreams.set(0);
        state.syncToSupport();
        Assertions.assertNotNull(state.support);
        state.support.fireCapacityAvailable();

        Assertions.assertTrue(f2.isDone());
        f2.get().releaseReservation();

        pool.close();
    }

    @Test
    void testGoAwayDraining() throws Exception {
        final ConnectionInitiator initiator = Mockito.mock(ConnectionInitiator.class);
        final IOSession session1 = createMockSession();
        final MockSessionState state1 = new MockSessionState(100);
        setupImmediateConnect(initiator, HOST_A, session1, state1);

        final H2MultiplexingConnPool pool = new H2MultiplexingConnPool(initiator, null, null, 2, 0);

        final H2StreamLease lease1 = pool.lease(HOST_A, CONNECT_TIMEOUT, null).get();
        Assertions.assertSame(session1, lease1.getSession());
        lease1.releaseReservation();

        state1.goAwayReceived.set(true);
        state1.activeLocalStreams.set(0);
        state1.syncToSupport();

        final AtomicReference<FutureCallback<IOSession>> connectCb2 = new AtomicReference<>();
        final AtomicReference<H2PoolSessionSupport> supportRef2 = new AtomicReference<>();
        setupDeferredConnect(initiator, HOST_A, connectCb2, supportRef2);

        Assertions.assertNotNull(state1.support);
        state1.support.fireDraining();

        final Future<H2StreamLease> f2 = pool.lease(HOST_A, CONNECT_TIMEOUT, null);
        Assertions.assertFalse(f2.isDone());
        Assertions.assertNotNull(connectCb2.get());

        final IOSession session2 = createMockSession();
        final MockSessionState state2 = new MockSessionState(100);
        state2.bind(supportRef2.get());
        connectCb2.get().completed(session2);

        Assertions.assertTrue(f2.isDone());
        final H2StreamLease lease2 = f2.get();
        Assertions.assertSame(session2, lease2.getSession());
        Assertions.assertNotSame(session1, lease2.getSession());

        lease2.releaseReservation();
        pool.close();
    }

    @Test
    void testPerRouteLimitRespected() throws Exception {
        final ConnectionInitiator initiator = Mockito.mock(ConnectionInitiator.class);
        final AtomicInteger connectCount = new AtomicInteger();

        Mockito.when(initiator.connect(
                        Mockito.eq(HOST_A),
                        Mockito.any(InetSocketAddress.class),
                        Mockito.isNull(),
                        Mockito.any(Timeout.class),
                        Mockito.any(),
                        Mockito.any()))
                .thenAnswer(invocation -> {
                    connectCount.incrementAndGet();
                    final H2PoolSessionSupport support = invocation.getArgument(4);
                    final MockSessionState state = new MockSessionState(1);
                    state.bind(support);
                    final FutureCallback<IOSession> cb = invocation.getArgument(5);
                    final IOSession s = createMockSession();
                    cb.completed(s);
                    return CompletableFuture.completedFuture(s);
                });

        final H2MultiplexingConnPool pool = new H2MultiplexingConnPool(initiator, null, null, 2, 10);

        final H2StreamLease l1 = pool.lease(HOST_A, CONNECT_TIMEOUT, null).get();
        final H2StreamLease l2 = pool.lease(HOST_A, CONNECT_TIMEOUT, null).get();

        Assertions.assertEquals(2, connectCount.get());

        final Future<H2StreamLease> f3 = pool.lease(HOST_A, CONNECT_TIMEOUT, null);
        Assertions.assertFalse(f3.isDone());
        Assertions.assertEquals(2, connectCount.get());

        l1.releaseReservation();
        Assertions.assertTrue(f3.isDone());

        final H2StreamLease l3 = f3.get();
        Assertions.assertNotNull(l3);
        l2.releaseReservation();
        l3.releaseReservation();
        pool.close();
    }

    @Test
    void testLeaseRetainsReservationUntilReleasedAndStreamCloses() throws Exception {
        final ConnectionInitiator initiator = Mockito.mock(ConnectionInitiator.class);
        final IOSession session = createMockSession();
        final MockSessionState state = new MockSessionState(1);
        setupImmediateConnect(initiator, HOST_A, session, state);

        final H2MultiplexingConnPool pool = new H2MultiplexingConnPool(initiator, null, null, 1, 1);

        final H2StreamLease lease1 = pool.lease(HOST_A, CONNECT_TIMEOUT, null).get();
        Assertions.assertSame(session, lease1.getSession());

        final Future<H2StreamLease> f2 = pool.lease(HOST_A, CONNECT_TIMEOUT, null);
        Assertions.assertFalse(f2.isDone());

        state.activeLocalStreams.set(1);
        state.syncToSupport();
        lease1.releaseReservation();
        Assertions.assertNotNull(state.support);
        state.support.fireCapacityAvailable();
        Assertions.assertFalse(f2.isDone());

        state.activeLocalStreams.set(0);
        state.syncToSupport();
        state.support.fireCapacityAvailable();

        Assertions.assertTrue(f2.isDone());
        final H2StreamLease lease2 = f2.get();
        Assertions.assertSame(session, lease2.getSession());

        lease2.releaseReservation();
        pool.close();
    }

    @Test
    void testCapacityReturnedOnStreamCloseAfterLeaseRelease() throws Exception {
        final ConnectionInitiator initiator = Mockito.mock(ConnectionInitiator.class);
        final IOSession session = createMockSession();
        final MockSessionState state = new MockSessionState(2);
        setupImmediateConnect(initiator, HOST_A, session, state);

        final H2MultiplexingConnPool pool = new H2MultiplexingConnPool(initiator, null, null, 1, 1);

        final H2StreamLease lease1 = pool.lease(HOST_A, CONNECT_TIMEOUT, null).get();
        final H2StreamLease lease2 = pool.lease(HOST_A, CONNECT_TIMEOUT, null).get();
        final Future<H2StreamLease> f3 = pool.lease(HOST_A, CONNECT_TIMEOUT, null);

        Assertions.assertFalse(f3.isDone());

        state.activeLocalStreams.set(2);
        state.syncToSupport();
        lease1.releaseReservation();
        lease2.releaseReservation();
        Assertions.assertNotNull(state.support);
        state.support.fireCapacityAvailable();
        Assertions.assertFalse(f3.isDone());

        state.activeLocalStreams.set(1);
        state.syncToSupport();
        state.support.fireCapacityAvailable();

        Assertions.assertTrue(f3.isDone());
        final H2StreamLease lease3 = f3.get();
        Assertions.assertSame(session, lease3.getSession());

        lease3.releaseReservation();
        pool.close();
    }

    @Test
    void testReplacementDialUnblockedByDrainingEntryAtPerRouteLimitOne() throws Exception {
        final ConnectionInitiator initiator = Mockito.mock(ConnectionInitiator.class);
        final IOSession session1 = createMockSession();
        final MockSessionState state1 = new MockSessionState(100);
        setupImmediateConnect(initiator, HOST_A, session1, state1);

        final H2MultiplexingConnPool pool = new H2MultiplexingConnPool(initiator, null, null, 1, 10);

        final H2StreamLease lease1 = pool.lease(HOST_A, CONNECT_TIMEOUT, null).get();
        Assertions.assertSame(session1, lease1.getSession());
        lease1.releaseReservation();

        state1.goAwayReceived.set(true);
        state1.activeLocalStreams.set(1);
        state1.syncToSupport();

        final AtomicReference<FutureCallback<IOSession>> connectCb2 = new AtomicReference<>();
        final AtomicReference<H2PoolSessionSupport> supportRef2 = new AtomicReference<>();
        setupDeferredConnect(initiator, HOST_A, connectCb2, supportRef2);

        Assertions.assertNotNull(state1.support);
        state1.support.fireDraining();

        final Future<H2StreamLease> f2 = pool.lease(HOST_A, CONNECT_TIMEOUT, null);
        Assertions.assertFalse(f2.isDone());
        Assertions.assertNotNull(connectCb2.get(),
                "Replacement dial must start immediately despite the draining entry at per-route limit 1");

        final IOSession session2 = createMockSession();
        final MockSessionState state2 = new MockSessionState(100);
        state2.bind(supportRef2.get());
        connectCb2.get().completed(session2);

        Assertions.assertTrue(f2.isDone());
        Assertions.assertNotSame(session1, f2.get().getSession());
        Assertions.assertSame(session2, f2.get().getSession());

        f2.get().releaseReservation();
        pool.close();
    }

    @Test
    void testDisconnectFreesGlobalSlotWithoutMaintenance() throws Exception {
        final ConnectionInitiator initiator = Mockito.mock(ConnectionInitiator.class);
        final IOSession sessionA = createMockSession();
        final MockSessionState stateA = new MockSessionState(100);
        setupImmediateConnect(initiator, HOST_A, sessionA, stateA);

        final H2MultiplexingConnPool pool = new H2MultiplexingConnPool(initiator, null, null, 1, 1);

        final H2StreamLease leaseA = pool.lease(HOST_A, CONNECT_TIMEOUT, null).get();
        Assertions.assertSame(sessionA, leaseA.getSession());

        final AtomicReference<FutureCallback<IOSession>> connectCbB = new AtomicReference<>();
        final AtomicReference<H2PoolSessionSupport> supportRefB = new AtomicReference<>();
        setupDeferredConnect(initiator, HOST_B, connectCbB, supportRefB);

        final Future<H2StreamLease> fB = pool.lease(HOST_B, CONNECT_TIMEOUT, null);
        Assertions.assertFalse(fB.isDone());
        Assertions.assertNull(connectCbB.get(),
                "Route B dial must remain blocked while route A holds the only global slot");

        leaseA.releaseReservation();
        Mockito.when(sessionA.isOpen()).thenReturn(false);
        Assertions.assertNotNull(stateA.support);
        stateA.support.fireSessionClosed();

        Assertions.assertNotNull(connectCbB.get(),
                "Route B dial must start after session A disconnects, without closeExpired()");

        final IOSession sessionB = createMockSession();
        final MockSessionState stateB = new MockSessionState(100);
        stateB.bind(supportRefB.get());
        connectCbB.get().completed(sessionB);

        Assertions.assertTrue(fB.isDone());
        Assertions.assertSame(sessionB, fB.get().getSession());

        fB.get().releaseReservation();
        pool.close();
    }

    @Test
    void testInvalidatedReservedEntryRetriesInsteadOfFailing() throws Exception {
        final ConnectionInitiator initiator = Mockito.mock(ConnectionInitiator.class);
        final IOSession session1 = createMockSession();
        final MockSessionState state1 = new MockSessionState(100);
        setupImmediateConnect(initiator, HOST_A, session1, state1);

        final H2MultiplexingConnPool pool = new H2MultiplexingConnPool(
                initiator, null, null, 2, 10);

        final H2StreamLease lease1 = pool.lease(HOST_A, CONNECT_TIMEOUT, null).get();
        lease1.releaseReservation();

        pool.setValidateAfterInactivity(TimeValue.ofMilliseconds(100));

        Mockito.when(session1.getLastReadTime()).thenReturn(System.currentTimeMillis() - 10_000);
        Mockito.when(session1.getLastWriteTime()).thenReturn(System.currentTimeMillis() - 10_000);

        final AtomicReference<Consumer<Boolean>> staleCallback = new AtomicReference<>();
        Mockito.doAnswer(invocation -> {
            final Object cmd = invocation.getArgument(0);
            if (cmd instanceof StaleCheckCommand) {
                staleCallback.set(((StaleCheckCommand) cmd).getCallback());
            }
            return null;
        }).when(session1).enqueue(Mockito.any(Command.class), Mockito.any(Command.Priority.class));

        final AtomicReference<FutureCallback<IOSession>> connectCb2 = new AtomicReference<>();
        final AtomicReference<H2PoolSessionSupport> supportRef2 = new AtomicReference<>();
        setupDeferredConnect(initiator, HOST_A, connectCb2, supportRef2);

        final Future<H2StreamLease> f2 = pool.lease(HOST_A, CONNECT_TIMEOUT, null);
        Assertions.assertFalse(f2.isDone());
        Assertions.assertNotNull(staleCallback.get(),
                "StaleCheckCommand must be issued before finalizeLease");

        state1.goAwayReceived.set(true);
        state1.syncToSupport();
        Assertions.assertNotNull(state1.support);
        state1.support.fireDraining();

        staleCallback.get().accept(Boolean.TRUE);

        Assertions.assertFalse(f2.isDone(),
                "Invalidated reserved entry must not fail the future; pool must retry");
        Assertions.assertNotNull(connectCb2.get(),
                "Retry must trigger a replacement dial");

        final IOSession session2 = createMockSession();
        final MockSessionState state2 = new MockSessionState(100);
        state2.bind(supportRef2.get());
        connectCb2.get().completed(session2);

        Assertions.assertTrue(f2.isDone());
        Assertions.assertSame(session2, f2.get().getSession());

        f2.get().releaseReservation();
        pool.close();
    }

    @Test
    void testEmptyRoutePoolIsPruned() throws Exception {
        final ConnectionInitiator initiator = Mockito.mock(ConnectionInitiator.class);
        final IOSession session = createMockSession();
        final MockSessionState state = new MockSessionState(100);
        setupImmediateConnect(initiator, HOST_A, session, state);

        final H2MultiplexingConnPool pool = new H2MultiplexingConnPool(initiator, null, null);

        final H2StreamLease lease = pool.lease(HOST_A, CONNECT_TIMEOUT, null).get();
        Assertions.assertTrue(pool.getRoutes().contains(HOST_A));

        lease.releaseReservation();
        Mockito.when(session.isOpen()).thenReturn(false);
        pool.closeExpired();

        Assertions.assertFalse(pool.getRoutes().contains(HOST_A),
                "Empty route pool must be pruned from getRoutes()");

        pool.close();
    }

    @Test
    void testRouteLimitOpensExtraConnectionsWhenLeasesSaturateExistingConnections() throws Exception {
        final ConnectionInitiator initiator = Mockito.mock(ConnectionInitiator.class);
        final AtomicInteger connectCount = new AtomicInteger();

        Mockito.when(initiator.connect(
                        Mockito.eq(HOST_A),
                        Mockito.any(InetSocketAddress.class),
                        Mockito.isNull(),
                        Mockito.any(Timeout.class),
                        Mockito.any(),
                        Mockito.any()))
                .thenAnswer(invocation -> {
                    connectCount.incrementAndGet();
                    final H2PoolSessionSupport support = invocation.getArgument(4);
                    final MockSessionState state = new MockSessionState(1);
                    state.bind(support);
                    final FutureCallback<IOSession> cb = invocation.getArgument(5);
                    final IOSession s = createMockSession();
                    cb.completed(s);
                    return CompletableFuture.completedFuture(s);
                });

        final H2MultiplexingConnPool pool = new H2MultiplexingConnPool(initiator, null, null, 2, 10);

        final Future<H2StreamLease> f1 = pool.lease(HOST_A, CONNECT_TIMEOUT, null);
        final Future<H2StreamLease> f2 = pool.lease(HOST_A, CONNECT_TIMEOUT, null);
        final Future<H2StreamLease> f3 = pool.lease(HOST_A, CONNECT_TIMEOUT, null);

        Assertions.assertTrue(f1.isDone());
        Assertions.assertTrue(f2.isDone());
        Assertions.assertFalse(f3.isDone());
        Assertions.assertEquals(2, connectCount.get());
        Assertions.assertNotSame(f1.get().getSession(), f2.get().getSession());

        f1.get().releaseReservation();
        f2.get().releaseReservation();
        pool.close();
    }

}