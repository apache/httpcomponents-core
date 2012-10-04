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
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLSocketFactory;

import org.apache.http.HttpClientConnection;
import org.apache.http.HttpHost;
import org.apache.http.annotation.Immutable;
import org.apache.http.impl.DefaultBHttpClientConnection;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.Config;
import org.apache.http.params.HttpParams;
import org.apache.http.pool.ConnFactory;
import org.apache.http.util.Args;

/**
 * A very basic {@link ConnFactory} implementation that creates
 * {@link HttpClientConnection} instances given a {@link HttpHost} instance.
 *
 * @see HttpHost
 * @since 4.2
 */
@Immutable
public class BasicConnFactory implements ConnFactory<HttpHost, HttpClientConnection> {

    private final SSLSocketFactory sslfactory;
    private final int connectTimeout;
    private final TimeUnit tunit;
    private final HttpParams params;

    /**
     * @deprecated (4.3) use
     *   {@link BasicConnFactory#BasicConnFactory(SSLSocketFactory, int, TimeUnit)}.
     */
    @Deprecated
    public BasicConnFactory(final SSLSocketFactory sslfactory, final HttpParams params) {
        super();
        this.sslfactory = sslfactory;
        this.params = Args.notNull(params, "HTTP params");
        this.connectTimeout = Config.getInt(params, CoreConnectionPNames.CONNECTION_TIMEOUT, 0);
        this.tunit = TimeUnit.MILLISECONDS;
    }

    /**
     * @deprecated (4.3) use {@link BasicConnFactory#BasicConnFactory(int, TimeUnit)}.
     */
    @Deprecated
    public BasicConnFactory(final HttpParams params) {
        this(null, params);
    }

    /**
     * @since 4.3
     */
    public BasicConnFactory(
            final SSLSocketFactory sslfactory,
            int connectTimeout, final TimeUnit tunit) {
        super();
        this.sslfactory = sslfactory;
        this.connectTimeout = connectTimeout;
        this.tunit = tunit != null ? tunit : TimeUnit.MILLISECONDS;
        this.params = null;
    }

    /**
     * @since 4.3
     */
    public BasicConnFactory(int connectTimeout, final TimeUnit tunit) {
        this(null, connectTimeout, tunit);
    }

    /**
     * @deprecated (4.3) no longer used.
     */
    @Deprecated
    protected HttpClientConnection create(final Socket socket, final HttpParams params) throws IOException {
        int bufsize = Config.getInt(params, CoreConnectionPNames.SOCKET_BUFFER_SIZE, 8 * 1024);
        DefaultBHttpClientConnection conn = new DefaultBHttpClientConnection(bufsize);
        conn.bind(socket);
        return conn;
    }

    public HttpClientConnection create(final HttpHost host) throws IOException {
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
        int timeout = (int) tunit.toMillis(Math.min(connectTimeout, Integer.MAX_VALUE));
        socket.setSoTimeout(timeout);
        socket.connect(new InetSocketAddress(host.getHostName(), host.getPort()), timeout);
        if (params != null) {
            return create(socket, this.params);
        } else {
            DefaultBHttpClientConnection conn = new DefaultBHttpClientConnection(8 * 1024);
            conn.bind(socket);
            return conn;
        }
    }

}
