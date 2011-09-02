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

import javax.net.ssl.SSLSocketFactory;

import org.apache.http.HttpClientConnection;
import org.apache.http.HttpHost;
import org.apache.http.annotation.Immutable;
import org.apache.http.impl.DefaultHttpClientConnection;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.pool.ConnFactory;

/**
 * @since 4.2
 */
@Immutable
public class BasicConnFactory implements ConnFactory<HttpHost, HttpClientConnection> {

    private final SSLSocketFactory sslfactory;
    private final HttpParams params;

    public BasicConnFactory(final SSLSocketFactory sslfactory, final HttpParams params) {
        super();
        if (params == null) {
            throw new IllegalArgumentException("HTTP params may not be null");
        }
        this.sslfactory = sslfactory;
        this.params = params;
    }

    public BasicConnFactory(final HttpParams params) {
        this(null, params);
    }

    protected HttpClientConnection create(final Socket socket, final HttpParams params) throws IOException {
        DefaultHttpClientConnection conn = new DefaultHttpClientConnection();
        conn.bind(socket, params);
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
        int connectTimeout = HttpConnectionParams.getConnectionTimeout(this.params);
        int soTimeout = HttpConnectionParams.getSoTimeout(this.params);

        socket.setSoTimeout(soTimeout);
        socket.connect(new InetSocketAddress(host.getHostName(), host.getPort()), connectTimeout);
        return create(socket, this.params);
    }

}
