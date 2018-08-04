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

import java.util.concurrent.Future;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

@RunWith(MockitoJUnitRunner.class)
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

    private AbstractIOSessionPool<String> impl;

    @Before
    public void setup() {
        impl = Mockito.mock(AbstractIOSessionPool.class, Mockito.withSettings()
                .defaultAnswer(Answers.CALLS_REAL_METHODS)
                .useConstructor());
    }

    @Test
    public void testGetSessions() throws Exception {

        Mockito.when(impl.connectSession(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.<Timeout>any(),
                ArgumentMatchers.<FutureCallback<IOSession>>any())).thenReturn(connectFuture);

        Mockito.doAnswer(new Answer() {

            @Override
            public Object answer(final InvocationOnMock invocation) throws Throwable {
                final Callback<Boolean> callback = invocation.getArgument(1);
                callback.execute(true);
                return null;
            }

        }).when(impl).validateSession(ArgumentMatchers.<IOSession>any(), ArgumentMatchers.<Callback<Boolean>>any());

        final Future<IOSession> future1 = impl.getSession("somehost", Timeout.ofSeconds(123L), null);
        Assert.assertThat(future1, CoreMatchers.notNullValue());
        Assert.assertThat(future1.isDone(), CoreMatchers.equalTo(false));
        Assert.assertThat(impl.getRoutes(), CoreMatchers.hasItem("somehost"));

        Mockito.verify(impl).connectSession(
                ArgumentMatchers.eq("somehost"),
                ArgumentMatchers.eq(Timeout.ofSeconds(123L)),
                ArgumentMatchers.<FutureCallback<IOSession>>any());

        final Future<IOSession> future2 = impl.getSession("somehost", Timeout.ofSeconds(123L), null);
        Assert.assertThat(future2, CoreMatchers.notNullValue());
        Assert.assertThat(future2.isDone(), CoreMatchers.equalTo(false));
        Assert.assertThat(impl.getRoutes(), CoreMatchers.hasItem("somehost"));

        Mockito.verify(impl, Mockito.times(1)).connectSession(
                ArgumentMatchers.eq("somehost"),
                ArgumentMatchers.<Timeout>any(),
                ArgumentMatchers.argThat(new ArgumentMatcher<FutureCallback<IOSession>>() {

                    @Override
                    public boolean matches(final FutureCallback<IOSession> callback) {
                        callback.completed(ioSession1);
                        return true;
                    }

                }));

        Assert.assertThat(future1.isDone(), CoreMatchers.equalTo(true));
        Assert.assertThat(future1.get(), CoreMatchers.sameInstance(ioSession1));

        Assert.assertThat(future2.isDone(), CoreMatchers.equalTo(true));
        Assert.assertThat(future2.get(), CoreMatchers.sameInstance(ioSession1));

        Mockito.verify(impl, Mockito.times(2)).validateSession(ArgumentMatchers.<IOSession>any(), ArgumentMatchers.<Callback<Boolean>>any());

        final Future<IOSession> future3 = impl.getSession("somehost", Timeout.ofSeconds(123L), null);

        Mockito.verify(impl, Mockito.times(1)).connectSession(
                ArgumentMatchers.eq("somehost"),
                ArgumentMatchers.<Timeout>any(),
                ArgumentMatchers.<FutureCallback<IOSession>>any());

        Mockito.verify(impl, Mockito.times(3)).validateSession(ArgumentMatchers.<IOSession>any(), ArgumentMatchers.<Callback<Boolean>>any());

        Assert.assertThat(future3.isDone(), CoreMatchers.equalTo(true));
        Assert.assertThat(future3.get(), CoreMatchers.sameInstance(ioSession1));
    }

    @Test
    public void testGetSessionFailure() throws Exception {

        Mockito.when(impl.connectSession(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.<Timeout>any(),
                ArgumentMatchers.<FutureCallback<IOSession>>any())).thenReturn(connectFuture);

        final Future<IOSession> future1 = impl.getSession("somehost", Timeout.ofSeconds(123L), null);
        Assert.assertThat(future1, CoreMatchers.notNullValue());
        Assert.assertThat(future1.isDone(), CoreMatchers.equalTo(false));
        Assert.assertThat(impl.getRoutes(), CoreMatchers.hasItem("somehost"));

        Mockito.verify(impl).connectSession(
                ArgumentMatchers.eq("somehost"),
                ArgumentMatchers.eq(Timeout.ofSeconds(123L)),
                ArgumentMatchers.<FutureCallback<IOSession>>any());

        final Future<IOSession> future2 = impl.getSession("somehost", Timeout.ofSeconds(123L), null);
        Assert.assertThat(future2, CoreMatchers.notNullValue());
        Assert.assertThat(future2.isDone(), CoreMatchers.equalTo(false));
        Assert.assertThat(impl.getRoutes(), CoreMatchers.hasItem("somehost"));

        Mockito.verify(impl, Mockito.times(1)).connectSession(
                ArgumentMatchers.eq("somehost"),
                ArgumentMatchers.<Timeout>any(),
                ArgumentMatchers.argThat(new ArgumentMatcher<FutureCallback<IOSession>>() {

                    @Override
                    public boolean matches(final FutureCallback<IOSession> callback) {
                        callback.failed(new Exception("Boom"));
                        return true;
                    }

                }));

        Assert.assertThat(future1.isDone(), CoreMatchers.equalTo(true));
        Assert.assertThat(future2.isDone(), CoreMatchers.equalTo(true));
    }

    @Test
    public void testShutdownPool() throws Exception {
        final AbstractIOSessionPool.PoolEntry entry1 = impl.getPoolEntry("host1");
        Assert.assertThat(entry1, CoreMatchers.notNullValue());
        entry1.session = ioSession1;

        final AbstractIOSessionPool.PoolEntry entry2 = impl.getPoolEntry("host2");
        Assert.assertThat(entry2, CoreMatchers.notNullValue());
        entry2.session = ioSession2;

        final AbstractIOSessionPool.PoolEntry entry3 = impl.getPoolEntry("host3");
        Assert.assertThat(entry3, CoreMatchers.notNullValue());
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
        Assert.assertThat(entry1, CoreMatchers.notNullValue());
        entry1.session = ioSession1;

        final AbstractIOSessionPool.PoolEntry entry2 = impl.getPoolEntry("host2");
        Assert.assertThat(entry2, CoreMatchers.notNullValue());
        entry2.session = ioSession2;

        impl.closeIdle(TimeValue.ofMillis(0L));

        Mockito.verify(impl).closeSession(ioSession1, CloseMode.GRACEFUL);
        Mockito.verify(impl).closeSession(ioSession2, CloseMode.GRACEFUL);

        Assert.assertThat(entry1.session, CoreMatchers.nullValue());
        Assert.assertThat(entry2.session, CoreMatchers.nullValue());
    }

    @Test
    public void testEnumSessions() throws Exception {
        final AbstractIOSessionPool.PoolEntry entry1 = impl.getPoolEntry("host1");
        Assert.assertThat(entry1, CoreMatchers.notNullValue());
        entry1.session = ioSession1;

        final AbstractIOSessionPool.PoolEntry entry2 = impl.getPoolEntry("host2");
        Assert.assertThat(entry2, CoreMatchers.notNullValue());
        entry2.session = ioSession2;

        impl.enumAvailable(new Callback<IOSession>() {

            @Override
            public void execute(final IOSession ioSession) {
                ioSession.close(CloseMode.GRACEFUL);
            }

        });
        Mockito.verify(ioSession1).close(CloseMode.GRACEFUL);
        Mockito.verify(ioSession2).close(CloseMode.GRACEFUL);
    }

    @Test
    public void testGetSessionReconnectAfterValidate() throws Exception {
        final AbstractIOSessionPool.PoolEntry entry1 = impl.getPoolEntry("somehost");
        Assert.assertThat(entry1, CoreMatchers.notNullValue());
        entry1.session = ioSession1;

        Mockito.when(ioSession1.isClosed()).thenReturn(false);
        Mockito.doAnswer(new Answer() {

            @Override
            public Object answer(final InvocationOnMock invocation) throws Throwable {
                final Callback<Boolean> callback = invocation.getArgument(1);
                callback.execute(false);
                return null;
            }

        }).when(impl).validateSession(ArgumentMatchers.<IOSession>any(), ArgumentMatchers.<Callback<Boolean>>any());

        impl.getSession("somehost", Timeout.ofSeconds(123L), null);

        Mockito.verify(impl, Mockito.times(1)).connectSession(
                ArgumentMatchers.eq("somehost"),
                ArgumentMatchers.eq(Timeout.ofSeconds(123L)),
                ArgumentMatchers.<FutureCallback<IOSession>>any());
    }

    @Test
    public void testGetSessionReconnectIfClosed() throws Exception {
        final AbstractIOSessionPool.PoolEntry entry1 = impl.getPoolEntry("somehost");
        Assert.assertThat(entry1, CoreMatchers.notNullValue());
        entry1.session = ioSession1;

        Mockito.when(ioSession1.isClosed()).thenReturn(true);

        impl.getSession("somehost", Timeout.ofSeconds(123L), null);

        Mockito.verify(impl).connectSession(
                ArgumentMatchers.eq("somehost"),
                ArgumentMatchers.eq(Timeout.ofSeconds(123L)),
                ArgumentMatchers.<FutureCallback<IOSession>>any());
    }

}
