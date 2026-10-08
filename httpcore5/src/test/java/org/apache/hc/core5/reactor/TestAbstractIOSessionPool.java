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
package org.apache.hc.core5.reactor;


import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.Future;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TestAbstractIOSessionPool {

    @Mock
    Future<IOSession> connectFuture;
    @Mock
    FutureCallback<IOSession> callback1;
    @Mock
    FutureCallback<IOSession> callback2;
    @Mock
    IOSession ioSession1;
    @Mock
    IOSession ioSession2;
    @Captor
    ArgumentCaptor<FutureCallback<IOSession>> connectCallbackCaptor;

    Clock clock;

    AbstractIOSessionPool<String> impl;

    @BeforeEach
    void prepareMocks() {
        clock = Clock.fixed(
                LocalDateTime.of(2026, Month.SEPTEMBER, 30, 13, 30).toInstant(ZoneOffset.UTC),
                ZoneId.of("UTC"));

        impl = Mockito.mock(AbstractIOSessionPool.class, Mockito.withSettings()
                .defaultAnswer(Answers.CALLS_REAL_METHODS)
                .useConstructor(clock));
    }

    @Test
    void testGetSessions() throws Exception {

        Mockito.when(impl.connectSession(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any())).thenReturn(connectFuture);

        Mockito.doAnswer(invocation -> {
            final Callback<Boolean> callback = invocation.getArgument(1);
            callback.execute(true);
            return null;
        }).when(impl).validateSession(ArgumentMatchers.any(), ArgumentMatchers.any());

        Mockito.when(ioSession1.isOpen()).thenReturn(true);

        final Future<IOSession> future1 = impl.getSession("somehost", Timeout.ofSeconds(123L), null);
        Assertions.assertNotNull(future1);
        Assertions.assertFalse(future1.isDone());
        Assertions.assertTrue(impl.getRoutes().contains("somehost"));

        Mockito.verify(impl).connectSession(
                ArgumentMatchers.eq("somehost"),
                ArgumentMatchers.eq(Timeout.ofSeconds(123L)),
                ArgumentMatchers.any());

        final Future<IOSession> future2 = impl.getSession("somehost", Timeout.ofSeconds(123L), null);
        Assertions.assertNotNull(future2);
        Assertions.assertFalse(future2.isDone());
        Assertions.assertTrue(impl.getRoutes().contains("somehost"));

        Mockito.verify(impl, Mockito.times(1)).connectSession(
                ArgumentMatchers.eq("somehost"),
                ArgumentMatchers.any(),
                ArgumentMatchers.argThat(callback -> {
                    callback.completed(ioSession1);
                    return true;
                }));

        Assertions.assertTrue(future1.isDone());
        Assertions.assertSame(ioSession1, future1.get());

        Assertions.assertTrue(future2.isDone());
        Assertions.assertSame(ioSession1, future2.get());

        Mockito.verify(impl, Mockito.times(2)).validateSession(ArgumentMatchers.any(), ArgumentMatchers.any());

        final Future<IOSession> future3 = impl.getSession("somehost", Timeout.ofSeconds(123L), null);

        Mockito.verify(impl, Mockito.times(1)).connectSession(
                ArgumentMatchers.eq("somehost"),
                ArgumentMatchers.any(),
                ArgumentMatchers.any());

        Mockito.verify(impl, Mockito.times(3)).validateSession(ArgumentMatchers.any(), ArgumentMatchers.any());

        Assertions.assertTrue(future3.isDone());
        Assertions.assertSame(ioSession1, future3.get());
    }

    @Test
    void testGetSessionConnectFailure() {

        Mockito.when(impl.connectSession(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any())).thenReturn(connectFuture);

        final Future<IOSession> future1 = impl.getSession("somehost", Timeout.ofSeconds(123L), null);
        Assertions.assertNotNull(future1);
        Assertions.assertFalse(future1.isDone());
        Assertions.assertTrue(impl.getRoutes().contains("somehost"));

        Mockito.verify(impl).connectSession(
                ArgumentMatchers.eq("somehost"),
                ArgumentMatchers.eq(Timeout.ofSeconds(123L)),
                connectCallbackCaptor.capture());

        final Future<IOSession> future2 = impl.getSession("somehost", Timeout.ofSeconds(123L), null);
        Assertions.assertNotNull(future2);
        Assertions.assertFalse(future2.isDone());
        Assertions.assertTrue(impl.getRoutes().contains("somehost"));

        final FutureCallback<IOSession> connectCallback = connectCallbackCaptor.getValue();
        Assertions.assertNotNull(connectCallback);
        connectCallback.failed(new Exception("Boom"));

        // Ensure connect failure invalidates all pending futures
        Assertions.assertTrue(future1.isDone());
        Assertions.assertTrue(future2.isDone());
    }

    @Test
    void testShutdownPool() {
        final AbstractIOSessionPool.PoolEntry entry1 = impl.getPoolEntry("host1");
        Assertions.assertNotNull(entry1);
        entry1.session = ioSession1;

        final AbstractIOSessionPool.PoolEntry entry2 = impl.getPoolEntry("host2");
        Assertions.assertNotNull(entry2);
        entry2.session = ioSession2;

        final AbstractIOSessionPool.PoolEntry entry3 = impl.getPoolEntry("host3");
        Assertions.assertNotNull(entry3);
        entry3.sessionFuture = connectFuture;
        entry3.requestQueue.add(callback1);
        entry3.requestQueue.add(callback2);

        impl.close(CloseMode.GRACEFUL);

        Mockito.verify(impl).closeSession(ioSession1, CloseMode.GRACEFUL);
        Mockito.verify(impl).closeSession(ioSession2, CloseMode.GRACEFUL);
        Mockito.verify(connectFuture).cancel(ArgumentMatchers.anyBoolean());
        Mockito.verify(callback1).cancelled();
        Mockito.verify(callback2).cancelled();
    }

    @Test
    void testEnumSessions() {
        final AbstractIOSessionPool.PoolEntry entry1 = impl.getPoolEntry("host1");
        Assertions.assertNotNull(entry1);
        entry1.session = ioSession1;

        final AbstractIOSessionPool.PoolEntry entry2 = impl.getPoolEntry("host2");
        Assertions.assertNotNull(entry2);
        entry2.session = ioSession2;

        impl.enumAvailable(ioSession -> ioSession.close(CloseMode.GRACEFUL));
        Mockito.verify(ioSession1).close(CloseMode.GRACEFUL);
        Mockito.verify(ioSession2).close(CloseMode.GRACEFUL);
    }

    @Test
    void testGetSessionReconnectAfterValidate() {
        final AbstractIOSessionPool.PoolEntry entry1 = impl.getPoolEntry("somehost");
        Assertions.assertNotNull(entry1);
        entry1.session = ioSession1;

        Mockito.when(ioSession1.isOpen()).thenReturn(true);
        Mockito.doAnswer(invocation -> {
            final Callback<Boolean> callback = invocation.getArgument(1);
            callback.execute(false);
            return null;
        }).when(impl).validateSession(ArgumentMatchers.any(), ArgumentMatchers.any());

        impl.getSession("somehost", Timeout.ofSeconds(123L), null);

        Mockito.verify(impl, Mockito.times(1)).connectSession(
                ArgumentMatchers.eq("somehost"),
                ArgumentMatchers.eq(Timeout.ofSeconds(123L)),
                ArgumentMatchers.any());
    }

    @Test
    void testGetSessionReconnectIfClosed() {
        final AbstractIOSessionPool.PoolEntry entry1 = impl.getPoolEntry("somehost");
        Assertions.assertNotNull(entry1);
        entry1.session = ioSession1;

        Mockito.when(ioSession1.isOpen()).thenReturn(false);

        impl.getSession("somehost", Timeout.ofSeconds(123L), null);

        Mockito.verify(impl).connectSession(
                ArgumentMatchers.eq("somehost"),
                ArgumentMatchers.eq(Timeout.ofSeconds(123L)),
                ArgumentMatchers.any());
    }

    @Test
    void testGetSessionConnectUnknownHost() {
        Mockito.when(impl.connectSession(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.any(),
                ArgumentMatchers.argThat(callback -> {
                    callback.failed(new UnknownHostException("Boom"));
                    return true;
                }))).thenReturn(connectFuture);

        final Future<IOSession> future1 = impl.getSession("somehost", Timeout.ofSeconds(123L), null);
        Assertions.assertNotNull(future1);
        Assertions.assertTrue(future1.isDone());

        final Future<IOSession> future2 = impl.getSession("somehost", Timeout.ofSeconds(123L), null);
        Assertions.assertNotNull(future2);
        Assertions.assertTrue(future2.isDone());
    }

    @Test
    void testCloseIdleOnly() {
        final AbstractIOSessionPool.PoolEntry entry1 = impl.getPoolEntry("host1");
        Assertions.assertNotNull(entry1);
        entry1.session = ioSession1;

        final AbstractIOSessionPool.PoolEntry entry2 = impl.getPoolEntry("host2");
        Assertions.assertNotNull(entry2);
        entry2.session = ioSession2;

        Mockito.doReturn(true).when(impl).isIdle(ioSession1);
        Mockito.doReturn(Instant.now(clock).minusSeconds(1).toEpochMilli())
                .when(ioSession1).getLastEventTime();
        Mockito.doReturn(false).when(impl).isIdle(ioSession2);

        impl.closeIdle(null);

        Mockito.verify(impl).closeSession(ioSession1, CloseMode.GRACEFUL);
        Mockito.verify(impl, Mockito.never()).closeSession(Mockito.same(ioSession2), Mockito.any());
    }

    @Test
    void testCloseIdlePastDeadline() {
        final AbstractIOSessionPool.PoolEntry entry1 = impl.getPoolEntry("host1");
        Assertions.assertNotNull(entry1);
        entry1.session = ioSession1;

        final AbstractIOSessionPool.PoolEntry entry2 = impl.getPoolEntry("host2");
        Assertions.assertNotNull(entry2);
        entry2.session = ioSession2;

        Mockito.doReturn(true).when(impl).isIdle(ioSession1);
        Mockito.doReturn(Instant.now(clock).minusSeconds(1).toEpochMilli())
                .when(ioSession1).getLastEventTime();
        Mockito.doReturn(true).when(impl).isIdle(ioSession2);
        Mockito.doReturn(Instant.now(clock).toEpochMilli())
                .when(ioSession2).getLastEventTime();

        impl.closeIdle(TimeValue.ofSeconds(1));

        Mockito.verify(impl).closeSession(ioSession1, CloseMode.GRACEFUL);
        Mockito.verify(impl, Mockito.never()).closeSession(Mockito.same(ioSession2), Mockito.any());
    }

}
