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
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.Set;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLServerSocket;

import org.apache.hc.core5.http.ExceptionListener;
import org.apache.hc.core5.http.config.SocketConfig;
import org.apache.hc.core5.http.impl.io.DefaultBHttpServerConnection;
import org.apache.hc.core5.http.impl.io.DefaultBHttpServerConnectionFactory;
import org.apache.hc.core5.http.impl.io.HttpService;
import org.apache.hc.core5.http.io.HttpConnectionFactory;
import org.apache.hc.core5.http.io.HttpServerConnection;
import org.apache.hc.core5.io.GracefullyCloseable;
import org.apache.hc.core5.io.ShutdownType;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;

/**
 * @since 4.4
 */
public class HttpServer implements GracefullyCloseable {

    enum Status { READY, ACTIVE, STOPPING }

    private final int port;
    private final InetAddress ifAddress;
    private final SocketConfig socketConfig;
    private final ServerSocketFactory serverSocketFactory;
    private final HttpService httpService;
    private final HttpConnectionFactory<? extends DefaultBHttpServerConnection> connectionFactory;
    private final SSLServerSetupHandler sslSetupHandler;
    private final ExceptionListener exceptionListener;
    private final ThreadPoolExecutor listenerExecutorService;
    private final ThreadGroup workerThreads;
    private final WorkerPoolExecutor workerExecutorService;
    private final AtomicReference<Status> status;

    private volatile ServerSocket serverSocket;
    private volatile RequestListener requestListener;

    public HttpServer(
            final int port,
            final HttpService httpService,
            final InetAddress ifAddress,
            final SocketConfig socketConfig,
            final ServerSocketFactory serverSocketFactory,
            final HttpConnectionFactory<? extends DefaultBHttpServerConnection> connectionFactory,
            final SSLServerSetupHandler sslSetupHandler,
            final ExceptionListener exceptionListener) {
        this.port = Args.notNegative(port, "Port value is negative");
        this.httpService = Args.notNull(httpService, "HTTP service");
        this.ifAddress = ifAddress;
        this.socketConfig = socketConfig != null ? socketConfig : SocketConfig.DEFAULT;
        this.serverSocketFactory = serverSocketFactory != null ? serverSocketFactory : ServerSocketFactory.getDefault();
        this.connectionFactory = connectionFactory != null ? connectionFactory : DefaultBHttpServerConnectionFactory.INSTANCE;
        this.sslSetupHandler = sslSetupHandler;
        this.exceptionListener = exceptionListener;
        this.listenerExecutorService = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new SynchronousQueue<Runnable>(),
                new ThreadFactoryImpl("HTTP-listener-" + this.port));
        this.workerThreads = new ThreadGroup("HTTP-workers");
        this.workerExecutorService = new WorkerPoolExecutor(
                0, Integer.MAX_VALUE, 1L, TimeUnit.SECONDS,
                new SynchronousQueue<Runnable>(),
                new ThreadFactoryImpl("HTTP-worker", this.workerThreads, false));
        this.status = new AtomicReference<>(Status.READY);
    }

    public InetAddress getInetAddress() {
        final ServerSocket localSocket = this.serverSocket;
        if (localSocket != null) {
            return localSocket.getInetAddress();
        }
        return null;
    }

    public int getLocalPort() {
        final ServerSocket localSocket = this.serverSocket;
        if (localSocket != null) {
            return localSocket.getLocalPort();
        }
        return -1;
    }

    public void start() throws IOException {
        if (this.status.compareAndSet(Status.READY, Status.ACTIVE)) {
            this.serverSocket = this.serverSocketFactory.createServerSocket(
                    this.port, this.socketConfig.getBacklogSize(), this.ifAddress);
            this.serverSocket.setReuseAddress(this.socketConfig.isSoReuseAddress());
            if (this.socketConfig.getRcvBufSize() > 0) {
                this.serverSocket.setReceiveBufferSize(this.socketConfig.getRcvBufSize());
            }
            if (this.sslSetupHandler != null && this.serverSocket instanceof SSLServerSocket) {
                this.sslSetupHandler.initialize((SSLServerSocket) this.serverSocket);
            }
            this.requestListener = new RequestListener(
                    this.socketConfig,
                    this.serverSocket,
                    this.httpService,
                    this.connectionFactory,
                    this.exceptionListener,
                    this.workerExecutorService);
            this.listenerExecutorService.execute(this.requestListener);
        }
    }

    public void stop() {
        if (this.status.compareAndSet(Status.ACTIVE, Status.STOPPING)) {
            this.listenerExecutorService.shutdownNow();
            this.workerExecutorService.shutdown();
            final RequestListener local = this.requestListener;
            if (local != null) {
                try {
                    local.terminate();
                } catch (final IOException ex) {
                    this.exceptionListener.onError(ex);
                }
            }
            this.workerThreads.interrupt();
        }
    }

    public void initiateShutdown() {
        stop();
    }

    public void awaitTermination(final TimeValue waitTime) throws InterruptedException {
        Args.notNull(waitTime, "Wait time");
        this.workerExecutorService.awaitTermination(waitTime.getDuration(), waitTime.getTimeUnit());
    }

    @Override
    public void shutdown(final ShutdownType shutdownType) {
        initiateShutdown();
        if (shutdownType == ShutdownType.GRACEFUL) {
            try {
                awaitTermination(TimeValue.ofSeconds(5));
            } catch (final InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        final Set<Worker> workers = this.workerExecutorService.getWorkers();
        for (final Worker worker: workers) {
            final HttpServerConnection conn = worker.getConnection();
            conn.shutdown(ShutdownType.GRACEFUL);
        }
    }

    @Override
    public void close() {
        shutdown(ShutdownType.GRACEFUL);
    }

}
