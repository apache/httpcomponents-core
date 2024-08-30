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
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.http.ExceptionListener;
import org.apache.hc.core5.http.impl.io.HttpService;
import org.apache.hc.core5.http.io.HttpConnectionFactory;
import org.apache.hc.core5.http.io.HttpServerConnection;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.io.Closer;
import org.apache.hc.core5.io.SocketSupport;

class RequestListener implements Runnable {

    private final SocketConfig socketConfig;
    private final ServerSocket serverSocket;
    private final HttpService httpService;
    private final HttpConnectionFactory<? extends HttpServerConnection> connectionFactory;
    private final SSLSocketFactory sslSocketFactory;
    private final Callback<SSLParameters> sslSetupHandler;
    private final ExceptionListener exceptionListener;
    private final ExecutorService executorService;
    private final AtomicBoolean terminated;

    public RequestListener(
            final SocketConfig socketConfig,
            final ServerSocket serversocket,
            final HttpService httpService,
            final HttpConnectionFactory<? extends HttpServerConnection> connectionFactory,
            final SSLSocketFactory sslSocketFactory,
            final Callback<SSLParameters> sslSetupHandler,
            final ExceptionListener exceptionListener,
            final ExecutorService executorService) {
        this.socketConfig = socketConfig;
        this.serverSocket = serversocket;
        this.httpService = httpService;
        this.connectionFactory = connectionFactory;
        this.sslSocketFactory = sslSocketFactory;
        this.sslSetupHandler = sslSetupHandler;
        this.exceptionListener = exceptionListener;
        this.executorService = executorService;
        this.terminated = new AtomicBoolean();
    }

    private HttpServerConnection createConnection(final Socket socket) throws IOException {
        socket.setSoTimeout(this.socketConfig.getSoTimeout().toMillisecondsIntBound());
        socket.setKeepAlive(this.socketConfig.isSoKeepAlive());
        socket.setTcpNoDelay(this.socketConfig.isTcpNoDelay());
        if (this.socketConfig.getRcvBufSize() > 0) {
            socket.setReceiveBufferSize(this.socketConfig.getRcvBufSize());
        }
        if (this.socketConfig.getSndBufSize() > 0) {
            socket.setSendBufferSize(this.socketConfig.getSndBufSize());
        }
        if (this.socketConfig.getSoLinger().toSeconds() >= 0) {
            socket.setSoLinger(true, this.socketConfig.getSoLinger().toSecondsIntBound());
        }
        if (this.socketConfig.getTcpKeepIdle() > 0) {
            SocketSupport.setOption(this.serverSocket, SocketSupport.TCP_KEEPIDLE, this.socketConfig.getTcpKeepIdle());
        }
        if (this.socketConfig.getTcpKeepInterval() > 0) {
            SocketSupport.setOption(this.serverSocket, SocketSupport.TCP_KEEPINTERVAL, this.socketConfig.getTcpKeepInterval());
        }
        if (this.socketConfig.getTcpKeepCount() > 0) {
            SocketSupport.setOption(this.serverSocket, SocketSupport.TCP_KEEPCOUNT, this.socketConfig.getTcpKeepCount());
        }
        if (!(socket instanceof SSLSocket) && sslSocketFactory != null) {
            final SSLSocket sslSocket = (SSLSocket) sslSocketFactory.createSocket(socket, null, -1, false);
            sslSocket.setUseClientMode(false);
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
                return this.connectionFactory.createConnection(sslSocket, socket);
            } catch (final IOException ex) {
                Closer.closeQuietly(sslSocket);
                throw ex;
            }
        }
        return this.connectionFactory.createConnection(socket);
    }

    @Override
    public void run() {
        try {
            while (!isTerminated() && !Thread.interrupted()) {
                final Socket socket = this.serverSocket.accept();
                try {
                    final HttpServerConnection conn = createConnection(socket);
                    final Worker worker = new Worker(this.httpService, conn, this.exceptionListener);
                    this.executorService.execute(worker);
                } catch (final IOException | RuntimeException ex) {
                    Closer.closeQuietly(socket);
                    throw ex;
                }
            }
        } catch (final Exception ex) {
            this.exceptionListener.onError(ex);
        }
    }

    public boolean isTerminated() {
        return this.terminated.get();
    }

    public void terminate() throws IOException {
        if (this.terminated.compareAndSet(false, true)) {
            this.serverSocket.close();
        }
    }

}
