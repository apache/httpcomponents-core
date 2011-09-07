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
package org.apache.http.impl.pool;

import static org.junit.Assert.assertEquals;

import java.net.ServerSocket;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocketFactory;

import org.apache.http.HttpClientConnection;
import org.apache.http.HttpHost;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.HttpParams;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TestBasicConnPool {

    private BasicConnFactory connFactory;
    private BasicConnPool pool;
    private HttpHost host;
    private HttpParams params;
    private HttpClientConnection conn;

    private ServerSocket server;
    private int serverPort;

    private SSLServerSocket sslServer;
    private int sslServerPort;

    @Before
    public void setUp() throws Exception {
        // setup an "http" server
        server = new ServerSocket(0);
        serverPort = server.getLocalPort();

        // setup an "https" server
        sslServer = (SSLServerSocket) SSLServerSocketFactory.getDefault().createServerSocket(0);
        sslServerPort = sslServer.getLocalPort();

        params = new BasicHttpParams();
        params.setIntParameter(CoreConnectionPNames.SO_TIMEOUT, 100);
        params.setIntParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, 100);
        connFactory = new BasicConnFactory(params);
        pool = new BasicConnPool(connFactory);
    }

    @After
    public void tearDown() throws Exception {
        server.close();
        if(conn != null) {
            conn.close();
            conn.shutdown();
        }
    }

    @Test(expected=IllegalArgumentException.class)
    public void testNullConstructor1() throws Exception {
        new BasicConnPool((HttpParams) null);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testNullConstructor2() throws Exception {
        new BasicConnPool((BasicConnFactory) null);
    }

    @Test
    public void testHttpCreateConnection() throws Exception {
        host = new HttpHost("localhost", serverPort, "http");
        conn = connFactory.create(host);

        assertEquals(true, conn.isOpen());
        assertEquals(100, conn.getSocketTimeout());
    }

    @Test
    public void testHttpsCreateConnection() throws Exception {
        connFactory = new BasicConnFactory((SSLSocketFactory)SSLSocketFactory.getDefault(), params);
        host = new HttpHost("localhost", sslServerPort, "https");
        conn = connFactory.create(host);

        assertEquals(true, conn.isOpen());
        assertEquals(100, conn.getSocketTimeout());
    }

    @Test
    public void testHttpCreateEntry() throws Exception {
        host = new HttpHost("localhost", serverPort, "http");
        conn = connFactory.create(host);

        BasicPoolEntry entry = pool.createEntry(host, conn);

        assertEquals(conn, entry.getConnection());
        assertEquals("localhost", entry.getRoute().getHostName());

        entry.close();
    }

}
