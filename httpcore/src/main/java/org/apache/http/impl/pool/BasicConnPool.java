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

import java.util.concurrent.atomic.AtomicLong;

import org.apache.http.HttpClientConnection;
import org.apache.http.HttpHost;
import org.apache.http.annotation.ThreadSafe;
import org.apache.http.params.HttpParams;
import org.apache.http.pool.AbstractConnPool;
import org.apache.http.pool.ConnFactory;
import org.apache.http.pool.ConnPool;

/**
 * A very basic {@link ConnPool} implementation that represents a pool
 * of {@link HttpClientConnection} connections identified by
 * an {@link HttpHost} instance containing basic details about the connection
 * such as the scheme name, the host name and the port). This pool implementation,
 * however, cannot differentiate direct and proxied connections.
 *
 * @see HttpHost
 * @since 4.2
 */
@ThreadSafe
public class BasicConnPool extends AbstractConnPool<HttpHost, HttpClientConnection, BasicPoolEntry> {

    private static AtomicLong COUNTER = new AtomicLong();

    public BasicConnPool(final ConnFactory<HttpHost, HttpClientConnection> connFactory) {
        super(connFactory, 2, 20);
    }

    public BasicConnPool(final HttpParams params) {
        super(new BasicConnFactory(params), 2, 20);
    }

    @Override
    protected BasicPoolEntry createEntry(
            final HttpHost host,
            final HttpClientConnection conn) {
        return new BasicPoolEntry(Long.toString(COUNTER.getAndIncrement()), host, conn);
    }

}
