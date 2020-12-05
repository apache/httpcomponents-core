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
import java.net.Proxy;
import java.net.Socket;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.function.Resolver;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.ConnectionRequestTimeoutException;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.DefaultAddressResolver;
import org.apache.hc.core5.http.impl.io.DefaultBHttpClientConnectionFactory;
import org.apache.hc.core5.http.impl.io.HttpRequestExecutor;
import org.apache.hc.core5.http.io.EofSensorInputStream;
import org.apache.hc.core5.http.io.EofSensorWatcher;
import org.apache.hc.core5.http.io.HttpClientConnection;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.HttpConnectionFactory;
import org.apache.hc.core5.http.io.HttpResponseInformationCallback;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.HttpEntityWrapper;
import org.apache.hc.core5.http.io.ssl.SSLSessionVerifier;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.io.Closer;
import org.apache.hc.core5.io.ModalCloseable;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.pool.ConnPoolControl;
import org.apache.hc.core5.pool.ManagedConnPool;
import org.apache.hc.core5.pool.PoolEntry;
import org.apache.hc.core5.pool.PoolStats;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Asserts;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

/**
 * HTTP/1.1 client side message exchange initiator.
 *
 * @since 5.0
 */
public class HttpRequester implements ConnPoolControl<HttpHost>, ModalCloseable {

    private final HttpRequestExecutor requestExecutor;
    private final HttpProcessor httpProcessor;
    private final ManagedConnPool<HttpHost, HttpClientConnection> connPool;
    private final SocketConfig socketConfig;
    private final HttpConnectionFactory<? extends HttpClientConnection> connectFactory;
    private final SSLSocketFactory sslSocketFactory;
    private final Callback<SSLParameters> sslSetupHandler;
    private final SSLSessionVerifier sslSessionVerifier;
    private final Resolver<HttpHost, InetSocketAddress> addressResolver;

    /**
     * Use {@link RequesterBootstrap} to create instances of this class.
     */
    @Internal
    public HttpRequester(
            final HttpRequestExecutor requestExecutor,
            final HttpProcessor httpProcessor,
            final ManagedConnPool<HttpHost, HttpClientConnection> connPool,
            final SocketConfig socketConfig,
            final HttpConnectionFactory<? extends HttpClientConnection> connectFactory,
            final SSLSocketFactory sslSocketFactory,
            final Callback<SSLParameters> sslSetupHandler,
            final SSLSessionVerifier sslSessionVerifier,
            final Resolver<HttpHost, InetSocketAddress> addressResolver) {
        this.requestExecutor = Args.notNull(requestExecutor, "Request executor");
        this.httpProcessor = Args.notNull(httpProcessor, "HTTP processor");
        this.connPool = Args.notNull(connPool, "Connection pool");
        this.socketConfig = socketConfig != null ? socketConfig : SocketConfig.DEFAULT;
        this.connectFactory = connectFactory != null ? connectFactory : new DefaultBHttpClientConnectionFactory(
                Http1Config.DEFAULT, CharCodingConfig.DEFAULT);
        this.sslSocketFactory = sslSocketFactory != null ? sslSocketFactory : (SSLSocketFactory) SSLSocketFactory.getDefault();
        this.sslSetupHandler = sslSetupHandler;
        this.sslSessionVerifier = sslSessionVerifier;
        this.addressResolver = addressResolver != null ? addressResolver : DefaultAddressResolver.INSTANCE;
    }

    @Override
    public PoolStats getTotalStats() {
        return connPool.getTotalStats();
    }

    @Override
    public PoolStats getStats(final HttpHost route) {
        return connPool.getStats(route);
    }

    @Override
    public void setMaxTotal(final int max) {
        connPool.setMaxTotal(max);
    }

    @Override
    public int getMaxTotal() {
        return connPool.getMaxTotal();
    }

    @Override
    public void setDefaultMaxPerRoute(final int max) {
        connPool.setDefaultMaxPerRoute(max);
    }

    @Override
    public int getDefaultMaxPerRoute() {
        return connPool.getDefaultMaxPerRoute();
    }

    @Override
    public void setMaxPerRoute(final HttpHost route, final int max) {
        connPool.setMaxPerRoute(route, max);
    }

    @Override
    public int getMaxPerRoute(final HttpHost route) {
        return connPool.getMaxPerRoute(route);
    }

    @Override
    public void closeIdle(final TimeValue idleTime) {
        connPool.closeIdle(idleTime);
    }

    @Override
    public void closeExpired() {
        connPool.closeExpired();
    }

    @Override
    public Set<HttpHost> getRoutes() {
        return connPool.getRoutes();
    }

    public ClassicHttpResponse execute(
            final HttpClientConnection connection,
            final ClassicHttpRequest request,
            final HttpResponseInformationCallback informationCallback,
            final HttpContext context) throws HttpException, IOException {
        Args.notNull(connection, "HTTP connection");
        Args.notNull(request, "HTTP request");
        Args.notNull(context, "HTTP context");
        if (!connection.isOpen()) {
            throw new ConnectionClosedException();
        }
        requestExecutor.preProcess(request, httpProcessor, context);
        final ClassicHttpResponse response = requestExecutor.execute(request, connection, informationCallback, context);
        requestExecutor.postProcess(response, httpProcessor, context);
        return response;
    }

    public ClassicHttpResponse execute(
            final HttpClientConnection connection,
            final ClassicHttpRequest request,
            final HttpContext context) throws HttpException, IOException {
        return execute(connection, request, null, context);
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
            final HttpClientResponseHandler<T> responseHandler) throws HttpException, IOException {
        try (final ClassicHttpResponse response = execute(connection, request, context)) {
            final T result = responseHandler.handleResponse(response);
            EntityUtils.consume(response.getEntity());
            final boolean keepAlive = requestExecutor.keepAlive(request, response, connection, context);
            if (!keepAlive) {
                connection.close();
            }
            return result;
        } catch (final HttpException | IOException | RuntimeException ex) {
            connection.close(CloseMode.IMMEDIATE);
            throw ex;
        }
    }

    private Socket createSocket(final HttpHost targetHost) throws IOException {
        final Socket sock;
        if (socketConfig.getSocksProxyAddress() != null) {
            sock = new Socket(new Proxy(Proxy.Type.SOCKS, socketConfig.getSocksProxyAddress()));
        } else {
            sock = new Socket();
        }
        sock.setSoTimeout(socketConfig.getSoTimeout().toMillisecondsIntBound());
        sock.setReuseAddress(socketConfig.isSoReuseAddress());
        sock.setTcpNoDelay(socketConfig.isTcpNoDelay());
        sock.setKeepAlive(socketConfig.isSoKeepAlive());
        if (socketConfig.getRcvBufSize() > 0) {
            sock.setReceiveBufferSize(socketConfig.getRcvBufSize());
        }
        if (socketConfig.getSndBufSize() > 0) {
            sock.setSendBufferSize(socketConfig.getSndBufSize());
        }
        final int linger = socketConfig.getSoLinger().toMillisecondsIntBound();
        if (linger >= 0) {
            sock.setSoLinger(true, linger);
        }

        final InetSocketAddress targetAddress = addressResolver.resolve(targetHost);
        // Run this under a doPrivileged to support lib users that run under a SecurityManager this allows granting connect permissions
        // only to this library
        try {
            AccessController.doPrivileged((PrivilegedExceptionAction<Object>) () -> {
                sock.connect(targetAddress, socketConfig.getSoTimeout().toMillisecondsIntBound());
                return null;
            });
        } catch (final PrivilegedActionException e) {
            Asserts.check(e.getCause() instanceof  IOException,
                    "method contract violation only checked exceptions are wrapped: " + e.getCause());
            // only checked exceptions are wrapped - error and RTExceptions are rethrown by doPrivileged
            throw (IOException) e.getCause();
        }
        if (URIScheme.HTTPS.same(targetHost.getSchemeName())) {
            final SSLSocket sslSocket = (SSLSocket) sslSocketFactory.createSocket(
                    sock, targetHost.getHostName(), targetAddress.getPort(), true);
            if (this.sslSetupHandler != null) {
                final SSLParameters sslParameters = sslSocket.getSSLParameters();
                this.sslSetupHandler.execute(sslParameters);
                sslSocket.setSSLParameters(sslParameters);
            }
            try {
                sslSocket.startHandshake();
                final SSLSession session = sslSocket.getSession();
                if (session == null) {
                    throw new SSLHandshakeException("SSL session not available");
                }
                if (sslSessionVerifier != null) {
                    sslSessionVerifier.verify(targetHost, session);
                }
            } catch (final IOException ex) {
                Closer.closeQuietly(sslSocket);
                throw ex;
            }
            return sslSocket;
        }
        return sock;
    }

    public ClassicHttpResponse execute(
            final HttpHost targetHost,
            final ClassicHttpRequest request,
            final HttpResponseInformationCallback informationCallback,
            final Timeout connectTimeout,
            final HttpContext context) throws HttpException, IOException {
        Args.notNull(targetHost, "HTTP host");
        Args.notNull(request, "HTTP request");
        final Future<PoolEntry<HttpHost, HttpClientConnection>> leaseFuture = connPool.lease(targetHost, null, connectTimeout, null);
        final PoolEntry<HttpHost, HttpClientConnection> poolEntry;
        final Timeout timeout = Timeout.defaultsToDisabled(connectTimeout);
        try {
            poolEntry = leaseFuture.get(timeout.getDuration(), timeout.getTimeUnit());
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
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
            if (request.getAuthority() == null) {
                request.setAuthority(new URIAuthority(targetHost.getHostName(), targetHost.getPort()));
            }
            final ClassicHttpResponse response = execute(connection, request, informationCallback, context);
            final HttpEntity entity = response.getEntity();
            if (entity != null) {
                response.setEntity(new HttpEntityWrapper(entity) {

                    private void releaseConnection() throws IOException {
                        try {
                            final HttpClientConnection localConn = connectionHolder.getConnection();
                            if (localConn != null) {
                                if (requestExecutor.keepAlive(request, response, localConn, context)) {
                                    if (super.isStreaming()) {
                                        Closer.close(super.getContent());
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
                    public void writeTo(final OutputStream outStream) throws IOException {
                        try {
                            if (outStream != null) {
                                super.writeTo(outStream);
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
            } else {
                final HttpClientConnection localConn = connectionHolder.getConnection();
                if (!requestExecutor.keepAlive(request, response, localConn, context)) {
                    localConn.close();
                }
                connectionHolder.releaseConnection();
            }
            return response;
        } catch (final HttpException | IOException | RuntimeException ex) {
            connectionHolder.discardConnection();
            throw ex;
        }
    }

    public ClassicHttpResponse execute(
            final HttpHost targetHost,
            final ClassicHttpRequest request,
            final Timeout connectTimeout,
            final HttpContext context) throws HttpException, IOException {
        return execute(targetHost, request, null, connectTimeout, context);
    }

    public <T> T  execute(
            final HttpHost targetHost,
            final ClassicHttpRequest request,
            final Timeout connectTimeout,
            final HttpContext context,
            final HttpClientResponseHandler<T> responseHandler) throws HttpException, IOException {
        try (final ClassicHttpResponse response = execute(targetHost, request, null, connectTimeout, context)) {
            final T result = responseHandler.handleResponse(response);
            EntityUtils.consume(response.getEntity());
            return result;
        }
    }

    public ConnPoolControl<HttpHost> getConnPoolControl() {
        return connPool;
    }

    @Override
    public void close(final CloseMode closeMode) {
        connPool.close(closeMode);
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
                poolEntry.discardConnection(CloseMode.GRACEFUL);
                connPool.release(poolEntry, false);
            }
        }

    }

}
