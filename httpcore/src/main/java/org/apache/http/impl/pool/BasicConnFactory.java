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
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;

import javax.net.ssl.SSLSocketFactory;

import org.apache.http.HttpClientConnection;
import org.apache.http.HttpHost;
import org.apache.http.annotation.Immutable;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.config.SocketConfig;
import org.apache.http.impl.DefaultBHttpClientConnection;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParamConfig;
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
@SuppressWarnings("deprecation")
@Immutable
public class BasicConnFactory implements ConnFactory<HttpHost, HttpClientConnection> {

    private final SSLSocketFactory sslfactory;
    private final int connectTimeout;
    private final SocketConfig sconfig;
    private final ConnectionConfig cconfig;

    /**
     * @deprecated (4.3) use
     *   {@link BasicConnFactory#BasicConnFactory(SSLSocketFactory, int, SocketConfig, ConnectionConfig)}.
     */
    @Deprecated
    public BasicConnFactory(final SSLSocketFactory sslfactory, final HttpParams params) {
        super();
        Args.notNull(params, "HTTP params");
        this.sslfactory = sslfactory;
        this.connectTimeout = HttpConnectionParams.getConnectionTimeout(params);
        this.sconfig = HttpParamConfig.getSocketConfig(params);
        this.cconfig = HttpParamConfig.getConnectionConfig(params);
    }

    /**
     * @deprecated (4.3) use
     *   {@link BasicConnFactory#BasicConnFactory(int, SocketConfig, ConnectionConfig)}.
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
            final int connectTimeout,
            final SocketConfig sconfig,
            final ConnectionConfig cconfig) {
        super();
        this.sslfactory = sslfactory;
        this.connectTimeout = connectTimeout;
        this.sconfig = sconfig != null ? sconfig : SocketConfig.DEFAULT;
        this.cconfig = cconfig != null ? cconfig : ConnectionConfig.DEFAULT;
    }

    /**
     * @since 4.3
     */
    public BasicConnFactory(
            final int connectTimeout, final SocketConfig sconfig, final ConnectionConfig cconfig) {
        this(null, connectTimeout, sconfig, cconfig);
    }

    /**
     * @since 4.3
     */
    public BasicConnFactory(final SocketConfig sconfig, final ConnectionConfig cconfig) {
        this(null, 0, sconfig, cconfig);
    }

    /**
     * @since 4.3
     */
    public BasicConnFactory() {
        this(null, 0, SocketConfig.DEFAULT, ConnectionConfig.DEFAULT);
    }

    /**
     * @deprecated (4.3) no longer used.
     */
    @Deprecated
    protected HttpClientConnection create(final Socket socket, final HttpParams params) throws IOException {
        final int bufsize = params.getIntParameter(CoreConnectionPNames.SOCKET_BUFFER_SIZE, 8 * 1024);
        final DefaultBHttpClientConnection conn = new DefaultBHttpClientConnection(bufsize);
        conn.bind(socket);
        return conn;
    }

    public HttpClientConnection create(final HttpHost host) throws IOException {
        final String scheme = host.getSchemeName();
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
        socket.setSoTimeout(this.sconfig.getSoTimeout());
        socket.connect(new InetSocketAddress(host.getHostName(), host.getPort()), this.connectTimeout);
        socket.setTcpNoDelay(this.sconfig.isTcpNoDelay());
        final int linger = this.sconfig.getSoLinger();
        if (linger >= 0) {
            socket.setSoLinger(linger > 0, linger);
        }
        CharsetDecoder chardecoder = null;
        CharsetEncoder charencoder = null;
        final Charset charset = this.cconfig.getCharset();
        final CodingErrorAction malformedInputAction = this.cconfig.getMalformedInputAction() != null ?
                this.cconfig.getMalformedInputAction() : CodingErrorAction.REPORT;
        final CodingErrorAction unmappableInputAction = this.cconfig.getUnmappableInputAction() != null ?
                this.cconfig.getUnmappableInputAction() : CodingErrorAction.REPORT;
        if (charset != null) {
            chardecoder = charset.newDecoder();
            chardecoder.onMalformedInput(malformedInputAction);
            chardecoder.onUnmappableCharacter(unmappableInputAction);
            charencoder = charset.newEncoder();
            charencoder.onMalformedInput(malformedInputAction);
            charencoder.onUnmappableCharacter(unmappableInputAction);
        }
        final DefaultBHttpClientConnection conn = new DefaultBHttpClientConnection(
                this.cconfig.getBufferSize(),
                this.cconfig.getFragmentSizeHint(),
                chardecoder, charencoder,
                this.cconfig.getMessageConstraints(),
                null, null, null, null);
        conn.bind(socket);
        return conn;
    }

}
