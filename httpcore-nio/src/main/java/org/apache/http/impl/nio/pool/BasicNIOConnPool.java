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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import org.apache.http.HttpConnection;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponseFactory;
import org.apache.http.annotation.ThreadSafe;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.nio.DefaultNHttpClientConnection;
import org.apache.http.nio.NHttpClientConnection;
import org.apache.http.nio.pool.AbstractNIOConnPool;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.util.ByteBufferAllocator;
import org.apache.http.nio.util.HeapByteBufferAllocator;
import org.apache.http.params.HttpParams;

@ThreadSafe
public class BasicNIOConnPool extends AbstractNIOConnPool<HttpHost, NHttpClientConnection, BasicNIOPoolEntry> {

    private final HttpResponseFactory responseFactory;
    private final ByteBufferAllocator allocator;
    private final HttpParams params;

    public BasicNIOConnPool(final ConnectingIOReactor ioreactor, final HttpParams params) {
        super(ioreactor, 2, 20);
        if (params == null) {
            throw new IllegalArgumentException("HTTP params may not be null");
        }
        this.responseFactory = new DefaultHttpResponseFactory();
        this.allocator = new HeapByteBufferAllocator();
        this.params = params;
    }

    @Override
    protected SocketAddress resolveRemoteAddress(final HttpHost host) {
        return new InetSocketAddress(host.getHostName(), host.getPort());
    }

    @Override
    protected SocketAddress resolveLocalAddress(final HttpHost host) {
        return null;
    }

    @Override
    protected NHttpClientConnection createConnection(final HttpHost route, final IOSession session) {
        return new DefaultNHttpClientConnection(session,
                this.responseFactory, this.allocator, this.params);
    }

    @Override
    protected BasicNIOPoolEntry createEntry(final HttpHost host, final NHttpClientConnection conn) {
        return new BasicNIOPoolEntry(host, conn);
    }

    @Override
    protected void closeEntry(final BasicNIOPoolEntry entry) {
        HttpConnection conn = entry.getConnection();
        try {
            conn.close();
        } catch (IOException ignore) {
        }
    }

}
