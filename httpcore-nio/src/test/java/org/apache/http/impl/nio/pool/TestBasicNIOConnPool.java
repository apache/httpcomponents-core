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
import org.apache.http.nio.NHttpClientConnection;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TestBasicNIOConnPool {

    private HttpParams params;
    private BasicNIOConnFactory connFactory;
    private BasicNIOConnPool pool;
    private HttpHost route;
    @Mock private ConnectingIOReactor reactor;
    @Mock private IOSession session;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        route = new HttpHost("localhost", 80, "http");
        params = new BasicHttpParams();
        connFactory = new BasicNIOConnFactory(params);
        pool = new BasicNIOConnPool(reactor, connFactory, params);
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test(expected=IllegalArgumentException.class)
    public void testNullConstructor() throws Exception {
        pool = new BasicNIOConnPool(null, new BasicHttpParams());
    }

    @Test(expected=IllegalArgumentException.class)
    public void testNullConstructor2() throws Exception {
        pool = new BasicNIOConnPool(reactor, null);
    }

    @Test
    public void testCreateConnection() throws Exception {
        connFactory.create(route, session);
    }

    @Test
    public void testCreateEntry() throws Exception {
        NHttpClientConnection conn = connFactory.create(route, session);
        BasicNIOPoolEntry entry = pool.createEntry(route, conn);
        entry.close();
    }
}
