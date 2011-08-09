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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicLong;

import javax.net.ssl.SSLSocketFactory;

import org.apache.http.HttpClientConnection;
import org.apache.http.HttpConnection;
import org.apache.http.HttpHost;
import org.apache.http.annotation.ThreadSafe;
import org.apache.http.impl.DefaultHttpClientConnection;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.pool.AbstractConnPool;

@ThreadSafe
public class BasicConnPool extends AbstractConnPool<HttpHost, HttpClientConnection, BasicPoolEntry> {

    private static AtomicLong COUNTER = new AtomicLong();

    private final SSLSocketFactory sslfactory;
    private final HttpParams params;

    public BasicConnPool(final SSLSocketFactory sslfactory, final HttpParams params) {
        super(2, 20);
        if (params == null) {
            throw new IllegalArgumentException("HTTP params may not be null");
        }
        this.sslfactory = sslfactory;
        this.params = params;
    }

    public BasicConnPool(final HttpParams params) {
        this(null, params);
    }

    @Override
    protected HttpClientConnection createConnection(final HttpHost host) throws IOException {
        DefaultHttpClientConnection conn = new DefaultHttpClientConnection();
        String scheme = host.getSchemeName();
        Socket socket = null;
        if ("http".equalsIgnoreCase(scheme)) {
            socket = new Socket();
        } if ("https".equalsIgnoreCase(scheme)) {
            if (this.sslfactory != null) {
                socket = this.sslfactory.createSocket();
            }
        }
        if (socket == null) {
            throw new IOException(scheme + " scheme is not supported");
        }
        int connTimeout = HttpConnectionParams.getConnectionTimeout(params);
        int soTimeout = HttpConnectionParams.getSoTimeout(params);

        socket.setSoTimeout(soTimeout);
        socket.connect(new InetSocketAddress(host.getHostName(), host.getPort()), connTimeout);
        conn.bind(socket, this.params);
        return conn;
    }

    @Override
    protected BasicPoolEntry createEntry(final HttpHost host, final HttpClientConnection conn) {
        return new BasicPoolEntry(Long.toString(COUNTER.getAndIncrement()), host, conn);
    }

    @Override
    protected void closeEntry(final BasicPoolEntry entry) {
        HttpConnection conn = entry.getConnection();
        try {
            conn.close();
        } catch (IOException ignore) {
        }
    }

}
