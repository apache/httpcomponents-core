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
package org.apache.http.impl.nio.bootstrap;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.http.ExceptionLogger;
import org.apache.http.impl.nio.DefaultHttpServerIODispatch;
import org.apache.http.impl.nio.DefaultNHttpServerConnection;
import org.apache.http.impl.nio.reactor.DefaultListeningIOReactor;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.nio.NHttpConnectionFactory;
import org.apache.http.nio.NHttpServerEventHandler;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.IOReactorException;
import org.apache.http.nio.reactor.IOReactorExceptionHandler;
import org.apache.http.nio.reactor.ListenerEndpoint;

/**
 * @since 4.4
 */
public class HttpServer {

    enum Status { READY, ACTIVE, STOPPING }

    private final int port;
    private final InetAddress ifAddress;
    private final IOReactorConfig ioReactorConfig;
    private final NHttpServerEventHandler serverEventHandler;
    private final NHttpConnectionFactory<? extends DefaultNHttpServerConnection> connectionFactory;
    private final ExceptionLogger exceptionLogger;
    private final ExecutorService listenerExecutorService;
    private final ThreadGroup dispatchThreads;
    private final AtomicReference<Status> status;
    private final DefaultListeningIOReactor ioReactor;

    private volatile ListenerEndpoint endpoint;

    HttpServer(
            final int port,
            final InetAddress ifAddress,
            final IOReactorConfig ioReactorConfig,
            final NHttpServerEventHandler serverEventHandler,
            final NHttpConnectionFactory<? extends DefaultNHttpServerConnection> connectionFactory,
            final ExceptionLogger exceptionLogger) {
        this.port = port;
        this.ifAddress = ifAddress;
        this.ioReactorConfig = ioReactorConfig;
        this.serverEventHandler = serverEventHandler;
        this.connectionFactory = connectionFactory;
        this.exceptionLogger = exceptionLogger;
        this.listenerExecutorService = Executors.newSingleThreadExecutor(
                new ThreadFactoryImpl("HTTP-listener-" + this.port));
        this.dispatchThreads = new ThreadGroup("I/O-dispatchers");
        try {
            this.ioReactor = new DefaultListeningIOReactor(
                    this.ioReactorConfig,
                    new ThreadFactoryImpl("I/O-dispatch", this.dispatchThreads));
        } catch (IOReactorException ex) {
            throw new IllegalStateException(ex);
        }
        this.ioReactor.setExceptionHandler(new IOReactorExceptionHandler() {
            @Override
            public boolean handle(final IOException ex) {
                exceptionLogger.log(ex);
                return false;
            }

            @Override
            public boolean handle(final RuntimeException ex) {
                exceptionLogger.log(ex);
                return false;
            }
        });
        this.status = new AtomicReference<Status>(Status.READY);
    }

    public ListenerEndpoint getEndpoint() {
        return this.endpoint;
    }

    public void start() throws IOException {
        if (this.status.compareAndSet(Status.READY, Status.ACTIVE)) {
            this.endpoint = this.ioReactor.listen(new InetSocketAddress(this.ifAddress, this.port > 0 ? this.port : 0));
            final IOEventDispatch ioEventDispatch = new DefaultHttpServerIODispatch(
                    this.serverEventHandler, this.connectionFactory);
            this.listenerExecutorService.execute(new Runnable() {

                @Override
                public void run() {
                    try {
                        ioReactor.execute(ioEventDispatch);
                    } catch (Exception ex) {
                        exceptionLogger.log(ex);
                    }
                }

            });
        }
    }

    public void awaitTermination(final long timeout, final TimeUnit timeUnit) throws InterruptedException {
        this.listenerExecutorService.awaitTermination(timeout, timeUnit);
    }

    public void shutdown(final long gracePeriod, final TimeUnit timeUnit) {
        if (this.status.compareAndSet(Status.ACTIVE, Status.STOPPING)) {
            try {
                this.ioReactor.shutdown(timeUnit.toMillis(gracePeriod));
            } catch (IOException ex) {
                this.exceptionLogger.log(ex);
            }
            this.listenerExecutorService.shutdown();
        }
    }

}
