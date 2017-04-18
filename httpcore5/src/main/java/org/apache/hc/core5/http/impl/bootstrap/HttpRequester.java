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
package org.apache.hc.core5.http.impl.bootstrap;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLSocketFactory;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.ConnectionRequestTimeoutException;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.SocketConfig;
import org.apache.hc.core5.http.impl.io.DefaultBHttpClientConnectionFactory;
import org.apache.hc.core5.http.impl.io.HttpRequestExecutor;
import org.apache.hc.core5.http.io.EofSensorInputStream;
import org.apache.hc.core5.http.io.EofSensorWatcher;
import org.apache.hc.core5.http.io.HttpClientConnection;
import org.apache.hc.core5.http.io.HttpConnectionFactory;
import org.apache.hc.core5.http.io.ResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.HttpEntityWrapper;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.io.GracefullyCloseable;
import org.apache.hc.core5.io.ShutdownType;
import org.apache.hc.core5.pool.ConnPoolControl;
import org.apache.hc.core5.pool.ControlledConnPool;
import org.apache.hc.core5.pool.PoolEntry;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;

/**
 * @since 5.0
 */
public class HttpRequester implements GracefullyCloseable {

    private final HttpRequestExecutor requestExecutor;
    private final HttpProcessor httpProcessor;
    private final ControlledConnPool<HttpHost, HttpClientConnection> connPool;
    private final SocketConfig socketConfig;
    private final HttpConnectionFactory<? extends HttpClientConnection> connectFactory;
    private final SSLSocketFactory sslSocketFactory;

    public HttpRequester(
            final HttpRequestExecutor requestExecutor,
            final HttpProcessor httpProcessor,
            final ControlledConnPool<HttpHost, HttpClientConnection> connPool,
            final SocketConfig socketConfig,
            final HttpConnectionFactory<? extends HttpClientConnection> connectFactory,
            final SSLSocketFactory sslSocketFactory) {
        this.requestExecutor = Args.notNull(requestExecutor, "Request executor");
        this.httpProcessor = Args.notNull(httpProcessor, "HTTP processor");
        this.connPool = Args.notNull(connPool, "Connection pool");
        this.socketConfig = socketConfig != null ? socketConfig : SocketConfig.DEFAULT;
        this.connectFactory = connectFactory != null ? connectFactory : DefaultBHttpClientConnectionFactory.INSTANCE;
        this.sslSocketFactory = sslSocketFactory != null ? sslSocketFactory : (SSLSocketFactory) SSLSocketFactory.getDefault();
    }

    public ClassicHttpResponse execute(
            final HttpClientConnection connection,
            final ClassicHttpRequest request,
            final HttpContext context) throws HttpException, IOException {
        Args.notNull(connection, "HTTP connection");
        Args.notNull(request, "HTTP request");
        Args.notNull(context, "HTTP context");
        if (!connection.isOpen()) {
            throw new ConnectionClosedException("Connection is closed");
        }
        requestExecutor.preProcess(request, httpProcessor, context);
        final ClassicHttpResponse response = requestExecutor.execute(request, connection, context);
        requestExecutor.postProcess(response, httpProcessor, context);
        return response;
    }

    public boolean keepAlive(
            final HttpClientConnection connection,
            final ClassicHttpRequest request,
            final ClassicHttpResponse response,
            final HttpContext context) throws IOException {
        final boolean keepAlive = requestExecutor.keepAlive(request, response, connection, context);
        if (!keepAlive) {
            connection.close();
        }
        return keepAlive;
    }

    public <T> T execute(
            final HttpClientConnection connection,
            final ClassicHttpRequest request,
            final HttpContext context,
            final ResponseHandler<T> responseHandler) throws HttpException, IOException {
        try (final ClassicHttpResponse response = execute(connection, request, context)) {
            final T result = responseHandler.handleResponse(response);
            EntityUtils.consume(response.getEntity());
            final boolean keepAlive = requestExecutor.keepAlive(request, response, connection, context);
            if (!keepAlive) {
                connection.close();
            }
            return result;
        } catch (HttpException | IOException | RuntimeException ex) {
            connection.shutdown(ShutdownType.IMMEDIATE);
            throw ex;
        }
    }

    private Socket createSocket(final HttpHost targetHost) throws IOException {
        final Socket sock = new Socket();
        sock.setSoTimeout(socketConfig.getSoTimeout().toMillisIntBound());
        sock.setReuseAddress(socketConfig.isSoReuseAddress());
        sock.setTcpNoDelay(socketConfig.isTcpNoDelay());
        sock.setKeepAlive(socketConfig.isSoKeepAlive());
        if (socketConfig.getRcvBufSize() > 0) {
            sock.setReceiveBufferSize(socketConfig.getRcvBufSize());
        }
        if (socketConfig.getSndBufSize() > 0) {
            sock.setSendBufferSize(socketConfig.getSndBufSize());
        }
        final int linger = socketConfig.getSoLinger().toMillisIntBound();
        if (linger >= 0) {
            sock.setSoLinger(true, linger);
        }

        final String scheme = targetHost.getSchemeName();
        int port = targetHost.getPort();
        if (port < 0) {
            if ("http".equalsIgnoreCase(scheme)) {
                port = 80;
            } else if ("https".equalsIgnoreCase(scheme)) {
                port = 443;
            }
        }
        final InetSocketAddress targetAddress;
        if (targetHost.getAddress() != null) {
            targetAddress = new InetSocketAddress(targetHost.getAddress(), port);
        } else {
            targetAddress = new InetSocketAddress(targetHost.getHostName(), port);
        }
        sock.connect(targetAddress, socketConfig.getSoTimeout().toMillisIntBound());

        if ("https".equalsIgnoreCase(scheme)) {
            return sslSocketFactory.createSocket(sock, targetHost.getHostName(), port, true);
        } else {
            return sock;
        }
    }

    public ClassicHttpResponse execute(
            final HttpHost targetHost,
            final ClassicHttpRequest request,
            final TimeValue connectTimeout,
            final HttpContext context) throws HttpException, IOException {
        Args.notNull(targetHost, "HTTP host");
        Args.notNull(request, "HTTP request");
        final Future<PoolEntry<HttpHost, HttpClientConnection>> leaseFuture = connPool.lease(targetHost, null, null);
        final PoolEntry<HttpHost, HttpClientConnection> poolEntry;
        final TimeValue timeout = connectTimeout != null ? connectTimeout : TimeValue.ZERO_MILLISECONDS;

        try {
            poolEntry = leaseFuture.get(timeout.getDuration(), timeout.getTimeUnit());
        } catch (final InterruptedException ex) {
            throw new InterruptedIOException(ex.getMessage());
        } catch (final ExecutionException ex) {
            throw new HttpException("Unexpected failure leasing connection", ex);
        } catch (final TimeoutException ex) {
            throw new ConnectionRequestTimeoutException("Connection request timeout");
        }
        final PoolEntryHolder connectionHolder = new PoolEntryHolder(poolEntry);
        try {
            HttpClientConnection connection = poolEntry.getConnection();
            if (connection == null) {
                final Socket socket = createSocket(targetHost);
                connection = connectFactory.createConnection(socket);
                poolEntry.assignConnection(connection);
            }
            final ClassicHttpResponse response = execute(connection, request, context);
            final HttpEntity entity = response.getEntity();
            if (entity != null) {
                response.setEntity(new HttpEntityWrapper(entity) {

                    private void releaseConnection() throws IOException {
                        try {
                            final HttpClientConnection localConn = connectionHolder.getConnection();
                            if (localConn != null) {
                                if (requestExecutor.keepAlive(request, response, localConn, context)) {
                                    if (super.isStreaming()) {
                                        final InputStream content = super.getContent();
                                        if (content != null) {
                                            content.close();
                                        }
                                    }
                                    connectionHolder.releaseConnection();
                                }
                            }
                        } finally {
                            connectionHolder.discardConnection();
                        }
                    }

                    private void abortConnection() {
                        connectionHolder.discardConnection();
                    }

                    @Override
                    public boolean isStreaming() {
                        return true;
                    }

                    @Override
                    public InputStream getContent() throws IOException {
                        return new EofSensorInputStream(super.getContent(), new EofSensorWatcher() {

                            @Override
                            public boolean eofDetected(final InputStream wrapped) throws IOException {
                                releaseConnection();
                                return false;
                            }

                            @Override
                            public boolean streamClosed(final InputStream wrapped) throws IOException {
                                releaseConnection();
                                return false;
                            }

                            @Override
                            public boolean streamAbort(final InputStream wrapped) throws IOException {
                                abortConnection();
                                return false;
                            }

                        });
                    }

                    @Override
                    public void writeTo(final OutputStream outstream) throws IOException {
                        try {
                            if (outstream != null) {
                                super.writeTo(outstream);
                            }
                            close();
                        } catch (final IOException | RuntimeException ex) {
                            abortConnection();
                        }
                    }

                    @Override
                    public void close() throws IOException {
                        releaseConnection();
                    }

                });
            }
            return response;
        } catch (HttpException | IOException | RuntimeException ex) {
            connectionHolder.discardConnection();
            throw ex;
        }
    }

    public <T> T  execute(
            final HttpHost targetHost,
            final ClassicHttpRequest request,
            final TimeValue connectTimeout,
            final HttpContext context,
            final ResponseHandler<T> responseHandler) throws HttpException, IOException {
        try (final ClassicHttpResponse response = execute(targetHost, request, connectTimeout, context)) {
            final T result = responseHandler.handleResponse(response);
            EntityUtils.consume(response.getEntity());
            return result;
        }
    }

    public ConnPoolControl<HttpHost> getConnPoolControl() {
        return connPool;
    }

    @Override
    public void shutdown(final ShutdownType shutdownType) {
        connPool.shutdown(shutdownType);
    }

    @Override
    public void close() throws IOException {
        connPool.close();
    }

    private class PoolEntryHolder {

        private final AtomicReference<PoolEntry<HttpHost, HttpClientConnection>> poolEntryRef;

        PoolEntryHolder(final PoolEntry<HttpHost, HttpClientConnection> poolEntry) {
            this.poolEntryRef = new AtomicReference<>(poolEntry);
        }

        HttpClientConnection getConnection() {
            final PoolEntry<HttpHost, HttpClientConnection> poolEntry = poolEntryRef.get();
            return poolEntry != null ? poolEntry.getConnection() : null;
        }

        void releaseConnection() {
            final PoolEntry<HttpHost, HttpClientConnection> poolEntry = poolEntryRef.getAndSet(null);
            if (poolEntry != null) {
                final HttpClientConnection connection = poolEntry.getConnection();
                connPool.release(poolEntry, connection != null && connection.isOpen());
            }
        }

        void discardConnection() {
            final PoolEntry<HttpHost, HttpClientConnection> poolEntry = poolEntryRef.getAndSet(null);
            if (poolEntry != null) {
                poolEntry.discardConnection(ShutdownType.IMMEDIATE);
                connPool.release(poolEntry, false);
            }
        }

    }

}
