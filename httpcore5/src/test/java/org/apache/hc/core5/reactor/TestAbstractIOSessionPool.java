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

import static org.hamcrest.MatcherAssert.assertThat;

import java.net.UnknownHostException;
import java.util.concurrent.Future;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class TestAbstractIOSessionPool {

    @Mock
    private Future<IOSession> connectFuture;
    @Mock
    private FutureCallback<IOSession> callback1;
    @Mock
    private FutureCallback<IOSession> callback2;
    @Mock
    private IOSession ioSession1;
    @Mock
    private IOSession ioSession2;
    @Captor
    ArgumentCaptor<FutureCallback<IOSession>> connectCallbackCaptor;

    private AbstractIOSessionPool<String> impl;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        impl = Mockito.mock(AbstractIOSessionPool.class, Mockito.withSettings()
                .defaultAnswer(Answers.CALLS_REAL_METHODS)
                .useConstructor());
    }

    @Test
    public void testGetSessions() throws Exception {

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
        assertThat(future1, CoreMatchers.notNullValue());
        assertThat(future1.isDone(), CoreMatchers.equalTo(false));
        assertThat(impl.getRoutes(), CoreMatchers.hasItem("somehost"));

        Mockito.verify(impl).connectSession(
                ArgumentMatchers.eq("somehost"),
                ArgumentMatchers.eq(Timeout.ofSeconds(123L)),
                ArgumentMatchers.any());

        final Future<IOSession> future2 = impl.getSession("somehost", Timeout.ofSeconds(123L), null);
        assertThat(future2, CoreMatchers.notNullValue());
        assertThat(future2.isDone(), CoreMatchers.equalTo(false));
        assertThat(impl.getRoutes(), CoreMatchers.hasItem("somehost"));

        Mockito.verify(impl, Mockito.times(1)).connectSession(
                ArgumentMatchers.eq("somehost"),
                ArgumentMatchers.any(),
                ArgumentMatchers.argThat(callback -> {
                    callback.completed(ioSession1);
                    return true;
                }));

        assertThat(future1.isDone(), CoreMatchers.equalTo(true));
        assertThat(future1.get(), CoreMatchers.sameInstance(ioSession1));

        assertThat(future2.isDone(), CoreMatchers.equalTo(true));
        assertThat(future2.get(), CoreMatchers.sameInstance(ioSession1));

        Mockito.verify(impl, Mockito.times(2)).validateSession(ArgumentMatchers.any(), ArgumentMatchers.any());

        final Future<IOSession> future3 = impl.getSession("somehost", Timeout.ofSeconds(123L), null);

        Mockito.verify(impl, Mockito.times(1)).connectSession(
                ArgumentMatchers.eq("somehost"),
                ArgumentMatchers.any(),
                ArgumentMatchers.any());

        Mockito.verify(impl, Mockito.times(3)).validateSession(ArgumentMatchers.any(), ArgumentMatchers.any());

        assertThat(future3.isDone(), CoreMatchers.equalTo(true));
        assertThat(future3.get(), CoreMatchers.sameInstance(ioSession1));
    }

    @Test
    public void testGetSessionConnectFailure() throws Exception {

        Mockito.when(impl.connectSession(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any())).thenReturn(connectFuture);

        final Future<IOSession> future1 = impl.getSession("somehost", Timeout.ofSeconds(123L), null);
        assertThat(future1, CoreMatchers.notNullValue());
        assertThat(future1.isDone(), CoreMatchers.equalTo(false));
        assertThat(impl.getRoutes(), CoreMatchers.hasItem("somehost"));

        Mockito.verify(impl).connectSession(
                ArgumentMatchers.eq("somehost"),
                ArgumentMatchers.eq(Timeout.ofSeconds(123L)),
                connectCallbackCaptor.capture());

        final Future<IOSession> future2 = impl.getSession("somehost", Timeout.ofSeconds(123L), null);
        assertThat(future2, CoreMatchers.notNullValue());
        assertThat(future2.isDone(), CoreMatchers.equalTo(false));
        assertThat(impl.getRoutes(), CoreMatchers.hasItem("somehost"));

        final FutureCallback<IOSession> connectCallback = connectCallbackCaptor.getValue();
        Assertions.assertNotNull(connectCallback);
        connectCallback.failed(new Exception("Boom"));

        // Ensure connect failure invalidates all pending futures
        assertThat(future1.isDone(), CoreMatchers.equalTo(true));
        assertThat(future2.isDone(), CoreMatchers.equalTo(true));
    }

    @Test
    public void testShutdownPool() throws Exception {
        final AbstractIOSessionPool.PoolEntry entry1 = impl.getPoolEntry("host1");
        assertThat(entry1, CoreMatchers.notNullValue());
        entry1.session = ioSession1;

        final AbstractIOSessionPool.PoolEntry entry2 = impl.getPoolEntry("host2");
        assertThat(entry2, CoreMatchers.notNullValue());
        entry2.session = ioSession2;

        final AbstractIOSessionPool.PoolEntry entry3 = impl.getPoolEntry("host3");
        assertThat(entry3, CoreMatchers.notNullValue());
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
    public void testCloseIdleSessions() throws Exception {
        final AbstractIOSessionPool.PoolEntry entry1 = impl.getPoolEntry("host1");
        assertThat(entry1, CoreMatchers.notNullValue());
        entry1.session = ioSession1;

        final AbstractIOSessionPool.PoolEntry entry2 = impl.getPoolEntry("host2");
        assertThat(entry2, CoreMatchers.notNullValue());
        entry2.session = ioSession2;

        impl.closeIdle(TimeValue.ZERO_MILLISECONDS);

        Mockito.verify(impl).closeSession(ioSession1, CloseMode.GRACEFUL);
        Mockito.verify(impl).closeSession(ioSession2, CloseMode.GRACEFUL);

        assertThat(entry1.session, CoreMatchers.nullValue());
        assertThat(entry2.session, CoreMatchers.nullValue());
    }

    @Test
    public void testEnumSessions() throws Exception {
        final AbstractIOSessionPool.PoolEntry entry1 = impl.getPoolEntry("host1");
        assertThat(entry1, CoreMatchers.notNullValue());
        entry1.session = ioSession1;

        final AbstractIOSessionPool.PoolEntry entry2 = impl.getPoolEntry("host2");
        assertThat(entry2, CoreMatchers.notNullValue());
        entry2.session = ioSession2;

        impl.enumAvailable(ioSession -> ioSession.close(CloseMode.GRACEFUL));
        Mockito.verify(ioSession1).close(CloseMode.GRACEFUL);
        Mockito.verify(ioSession2).close(CloseMode.GRACEFUL);
    }

    @Test
    public void testGetSessionReconnectAfterValidate() throws Exception {
        final AbstractIOSessionPool.PoolEntry entry1 = impl.getPoolEntry("somehost");
        assertThat(entry1, CoreMatchers.notNullValue());
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
    public void testGetSessionReconnectIfClosed() throws Exception {
        final AbstractIOSessionPool.PoolEntry entry1 = impl.getPoolEntry("somehost");
        assertThat(entry1, CoreMatchers.notNullValue());
        entry1.session = ioSession1;

        Mockito.when(ioSession1.isOpen()).thenReturn(false);

        impl.getSession("somehost", Timeout.ofSeconds(123L), null);

        Mockito.verify(impl).connectSession(
                ArgumentMatchers.eq("somehost"),
                ArgumentMatchers.eq(Timeout.ofSeconds(123L)),
                ArgumentMatchers.any());
    }

    @Test
    public void testGetSessionConnectUnknownHost() throws Exception {

        Mockito.when(connectFuture.isDone()).thenReturn(true);
        Mockito.when(impl.connectSession(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.any(),
                ArgumentMatchers.argThat(callback -> {
                    callback.failed(new UnknownHostException("Boom"));
                    return true;
                }))).thenReturn(connectFuture);

        final Future<IOSession> future1 = impl.getSession("somehost", Timeout.ofSeconds(123L), null);
        assertThat(future1, CoreMatchers.notNullValue());
        assertThat(future1.isDone(), CoreMatchers.equalTo(true));

        final Future<IOSession> future2 = impl.getSession("somehost", Timeout.ofSeconds(123L), null);
        assertThat(future2, CoreMatchers.notNullValue());
        assertThat(future2.isDone(), CoreMatchers.equalTo(true));
    }

}
