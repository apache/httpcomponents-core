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
package org.apache.http.impl.nio.pool;

import org.apache.http.HttpHost;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.nio.NHttpClientConnection;
import org.apache.http.nio.pool.NIOConnFactory;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.reactor.SessionRequest;
import org.apache.http.nio.reactor.SessionRequestCallback;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.net.SocketAddress;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class TestBasicNIOConnPool {

    static class LocalPool extends BasicNIOConnPool {

        public LocalPool(
                final ConnectingIOReactor ioreactor,
                final NIOConnFactory<HttpHost, NHttpClientConnection> connFactory,
                final int connectTimeout) {
            super(ioreactor, connFactory, connectTimeout);
        }

        public LocalPool(
                final ConnectingIOReactor ioreactor,
                final ConnectionConfig config) {
            super(ioreactor, config);
        }

        @Override
        public void requestCompleted(final SessionRequest request) {
            super.requestCompleted(request);
        }

    }

    private BasicNIOConnFactory connFactory;
    private LocalPool pool;
    private HttpHost route;
    @Mock private ConnectingIOReactor reactor;
    @Mock private IOSession session;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        route = new HttpHost("localhost", 80, "http");
        connFactory = new BasicNIOConnFactory(ConnectionConfig.DEFAULT);
        pool = new LocalPool(reactor, connFactory, 0);
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test(expected=IllegalArgumentException.class)
    public void testNullConstructor() throws Exception {
        pool = new LocalPool(null, ConnectionConfig.DEFAULT);
    }

    @Test
    public void testCreateConnection() throws Exception {
        connFactory.create(route, session);
    }

    @Test
    public void testCreateEntry() throws Exception {
        final NHttpClientConnection conn = connFactory.create(route, session);
        final BasicNIOPoolEntry entry = pool.createEntry(route, conn);
        entry.close();
    }

    @Test
    public void testTimeoutOnLeaseRelease() throws Exception {
        final HttpHost host = new HttpHost("somehost");
        final SessionRequest sessionRequest = Mockito.mock(SessionRequest.class);
        Mockito.when(sessionRequest.getSession()).thenReturn(session);
        Mockito.when(sessionRequest.getAttachment()).thenReturn(host);
        Mockito.when(reactor.connect(
                Matchers.any(SocketAddress.class),
                Matchers.any(SocketAddress.class),
                Matchers.eq(host),
                Matchers.any(SessionRequestCallback.class))).
                thenReturn(sessionRequest);

        Mockito.when(session.getSocketTimeout()).thenReturn(999);

        final Future<BasicNIOPoolEntry> future1 = pool.lease(host, null, 10, TimeUnit.SECONDS, null);
        Mockito.verify(sessionRequest).setConnectTimeout(10000);

        pool.requestCompleted(sessionRequest);

        final BasicNIOPoolEntry entry1 = future1.get();
        final NHttpClientConnection conn1 = entry1.getConnection();
        Assert.assertNotNull(entry1);
        Assert.assertNotNull(conn1);
        Assert.assertEquals(999, entry1.getSocketTimeout());
        Assert.assertEquals(999, conn1.getSocketTimeout());

        Mockito.when(session.getSocketTimeout()).thenReturn(888);

        pool.release(entry1, true);
        Assert.assertEquals(888, entry1.getSocketTimeout());
        Mockito.verify(session).setSocketTimeout(0);

        final Future<BasicNIOPoolEntry> future2 = pool.lease(host, null, 10, TimeUnit.SECONDS, null);
        final BasicNIOPoolEntry entry2 = future2.get();
        final NHttpClientConnection conn2 = entry2.getConnection();
        Assert.assertNotNull(entry2);
        Assert.assertNotNull(conn2);

        Assert.assertEquals(888, entry1.getSocketTimeout());
        Mockito.verify(session).setSocketTimeout(888);
    }


}
