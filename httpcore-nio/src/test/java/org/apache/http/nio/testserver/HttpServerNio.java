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

package org.apache.http.nio.testserver;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;

import org.apache.http.HttpResponseInterceptor;
import org.apache.http.impl.nio.DefaultHttpServerIODispatch;
import org.apache.http.impl.nio.DefaultNHttpServerConnection;
import org.apache.http.impl.nio.reactor.DefaultListeningIOReactor;
import org.apache.http.impl.nio.reactor.ExceptionEvent;
import org.apache.http.nio.NHttpConnectionFactory;
import org.apache.http.nio.NHttpServerEventHandler;
import org.apache.http.nio.protocol.HttpAsyncExpectationVerifier;
import org.apache.http.nio.protocol.HttpAsyncRequestHandlerMapper;
import org.apache.http.nio.protocol.HttpAsyncService;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.IOReactorExceptionHandler;
import org.apache.http.nio.reactor.IOReactorStatus;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.reactor.ListenerEndpoint;
import org.apache.http.nio.reactor.ListeningIOReactor;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.ImmutableHttpProcessor;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.protocol.ResponseDate;
import org.apache.http.protocol.ResponseServer;

public class HttpServerNio {

    public static final HttpProcessor DEFAULT_HTTP_PROC = new ImmutableHttpProcessor(
            new HttpResponseInterceptor[] {
                    new ResponseDate(),
                    new ResponseServer("TEST-SERVER/1.1"),
                    new ResponseContent(),
                    new ResponseConnControl()});

    private final DefaultListeningIOReactor ioReactor;
    private final NHttpConnectionFactory<DefaultNHttpServerConnection> connFactory;

    private volatile IOReactorThread thread;
    private volatile ListenerEndpoint endpoint;
    private volatile int timeout;

    public HttpServerNio(
            final NHttpConnectionFactory<DefaultNHttpServerConnection> connFactory) throws IOException {
        super();
        this.ioReactor = new DefaultListeningIOReactor();
        this.connFactory = connFactory;
    }

    public int getTimeout() {
        return this.timeout;
    }

    public void setTimeout(final int timeout) {
        this.timeout = timeout;
    }

    public void setExceptionHandler(final IOReactorExceptionHandler exceptionHandler) {
        this.ioReactor.setExceptionHandler(exceptionHandler);
    }

    private void execute(final NHttpServerEventHandler serviceHandler) throws IOException {
        final IOEventDispatch ioEventDispatch = new DefaultHttpServerIODispatch(serviceHandler, this.connFactory) {

            @Override
            protected DefaultNHttpServerConnection createConnection(final IOSession session) {
                final DefaultNHttpServerConnection conn = super.createConnection(session);
                conn.setSocketTimeout(timeout);
                return conn;
            }

        };
        this.ioReactor.execute(ioEventDispatch);
    }

    public ListenerEndpoint getListenerEndpoint() {
        return this.endpoint;
    }

    public void setEndpoint(final ListenerEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    public void start(final NHttpServerEventHandler serviceHandler) {
        this.endpoint = this.ioReactor.listen(new InetSocketAddress(0));
        this.thread = new IOReactorThread(serviceHandler);
        this.thread.start();
    }

    public void start(
            final HttpProcessor protocolProcessor,
            final HttpAsyncRequestHandlerMapper handlerMapper,
            final HttpAsyncExpectationVerifier expectationVerifier) {
        start(new HttpAsyncService(protocolProcessor != null ? protocolProcessor :
            DEFAULT_HTTP_PROC, null, null, handlerMapper, expectationVerifier));
    }

    public void start(
            final HttpAsyncRequestHandlerMapper handlerMapper,
            final HttpAsyncExpectationVerifier expectationVerifier) {
        start(null, handlerMapper, expectationVerifier);
    }

    public void start(final HttpAsyncRequestHandlerMapper handlerMapper) {
        start(null, handlerMapper, null);
    }

    public ListeningIOReactor getIoReactor() {
        return this.ioReactor;
    }

    public IOReactorStatus getStatus() {
        return this.ioReactor.getStatus();
    }

    public List<ExceptionEvent> getAuditLog() {
        return this.ioReactor.getAuditLog();
    }

    public void join(final long timeout) throws InterruptedException {
        if (this.thread != null) {
            this.thread.join(timeout);
        }
    }

    public Exception getException() {
        if (this.thread != null) {
            return this.thread.getException();
        } else {
            return null;
        }
    }

    public void shutdown() throws IOException {
        this.ioReactor.shutdown();
        try {
            join(500);
        } catch (final InterruptedException ignore) {
        }
    }

    private class IOReactorThread extends Thread {

        private final NHttpServerEventHandler serviceHandler;

        private volatile Exception ex;

        public IOReactorThread(final NHttpServerEventHandler serviceHandler) {
            super();
            this.serviceHandler = serviceHandler;
        }

        @Override
        public void run() {
            try {
                execute(this.serviceHandler);
            } catch (final Exception ex) {
                this.ex = ex;
            }
        }

        public Exception getException() {
            return this.ex;
        }

    }

}
