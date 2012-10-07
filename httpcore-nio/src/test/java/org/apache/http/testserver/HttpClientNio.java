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

package org.apache.http.testserver;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpHost;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.DefaultHttpClientIODispatch;
import org.apache.http.impl.nio.DefaultNHttpClientConnection;
import org.apache.http.impl.nio.pool.BasicNIOConnFactory;
import org.apache.http.impl.nio.pool.BasicNIOConnPool;
import org.apache.http.impl.nio.pool.BasicNIOPoolEntry;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.impl.nio.reactor.ExceptionEvent;
import org.apache.http.nio.NHttpClientHandler;
import org.apache.http.nio.NHttpClientEventHandler;
import org.apache.http.nio.NHttpConnectionFactory;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.IOReactorExceptionHandler;
import org.apache.http.nio.reactor.IOReactorStatus;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.reactor.SessionRequest;

@SuppressWarnings("deprecation")
public class HttpClientNio {

    private final DefaultConnectingIOReactor ioReactor;
    private final NHttpConnectionFactory<DefaultNHttpClientConnection> connFactory;
    private final BasicNIOConnPool connpool;

    private volatile IOReactorThread thread;
    private volatile int timeout;

    public HttpClientNio(
            final NHttpConnectionFactory<DefaultNHttpClientConnection> connFactory) throws IOException {
        super();
        this.ioReactor = new DefaultConnectingIOReactor();
        this.connFactory = connFactory;
        this.connpool = new BasicNIOConnPool(this.ioReactor, new BasicNIOConnFactory(connFactory));
    }

    public int getTimeout() {
        return this.timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public void setMaxTotal(int max) {
        this.connpool.setMaxTotal(max);
    }

    public void setMaxPerRoute(int max) {
        this.connpool.setDefaultMaxPerRoute(max);
    }

    public Future<BasicNIOPoolEntry> lease(
            final HttpHost host,
            final FutureCallback<BasicNIOPoolEntry> callback) {
        return this.connpool.lease(host, null, this.timeout, TimeUnit.MILLISECONDS, callback);
    }

    public void release(final BasicNIOPoolEntry poolEntry, boolean reusable) {
        this.connpool.release(poolEntry, reusable);
    }

    public BasicNIOConnPool getConnPool() {
        return this.connpool;
    }

    public void setExceptionHandler(final IOReactorExceptionHandler exceptionHandler) {
        this.ioReactor.setExceptionHandler(exceptionHandler);
    }

    private void execute(final NHttpClientEventHandler clientHandler) throws IOException {
        IOEventDispatch ioEventDispatch = new DefaultHttpClientIODispatch(clientHandler, this.connFactory) {

            @Override
            protected DefaultNHttpClientConnection createConnection(IOSession session) {
                DefaultNHttpClientConnection conn = super.createConnection(session);
                conn.setSocketTimeout(timeout);
                return conn;
            }

        };
        this.ioReactor.execute(ioEventDispatch);
    }

    public SessionRequest openConnection(final InetSocketAddress address, final Object attachment) {
        SessionRequest sessionRequest = this.ioReactor.connect(address, null, attachment, null);
        sessionRequest.setConnectTimeout(this.timeout);
        return sessionRequest;
    }

    public void start(final NHttpClientEventHandler clientHandler) {
        this.thread = new IOReactorThread(clientHandler);
        this.thread.start();
    }

    public void start(final NHttpClientHandler handler) {
        this.thread = new IOReactorThread(new NHttpClientEventHandlerAdaptor(handler));
        this.thread.start();
    }

    public ConnectingIOReactor getIoReactor() {
        return this.ioReactor;
    }

    public IOReactorStatus getStatus() {
        return this.ioReactor.getStatus();
    }

    public List<ExceptionEvent> getAuditLog() {
        return this.ioReactor.getAuditLog();
    }

    public void join(long timeout) throws InterruptedException {
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
        this.connpool.shutdown(2000);
        try {
            join(500);
        } catch (InterruptedException ignore) {
        }
    }

    private class IOReactorThread extends Thread {

        private final NHttpClientEventHandler clientHandler;

        private volatile Exception ex;

        public IOReactorThread(final NHttpClientEventHandler clientHandler) {
            super();
            this.clientHandler = clientHandler;
        }

        @Override
        public void run() {
            try {
                execute(this.clientHandler);
            } catch (Exception ex) {
                this.ex = ex;
            }
        }

        public Exception getException() {
            return this.ex;
        }

    }

}
