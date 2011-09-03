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

import org.apache.http.impl.nio.DefaultClientIODispatch;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.impl.nio.reactor.ExceptionEvent;
import org.apache.http.nio.NHttpClientHandler;
import org.apache.http.nio.NHttpClientIOTarget;
import org.apache.http.nio.NHttpConnectionFactory;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.IOReactorExceptionHandler;
import org.apache.http.nio.reactor.IOReactorStatus;
import org.apache.http.nio.reactor.SessionRequest;

public class HttpClientNio {

    private final DefaultConnectingIOReactor ioReactor;
    private final NHttpConnectionFactory<NHttpClientIOTarget> connFactory;

    private volatile IOReactorThread thread;

    public HttpClientNio(
            final NHttpConnectionFactory<NHttpClientIOTarget> connFactory) throws IOException {
        super();
        this.ioReactor = new DefaultConnectingIOReactor();
        this.connFactory = connFactory;
    }

    public void setExceptionHandler(final IOReactorExceptionHandler exceptionHandler) {
        this.ioReactor.setExceptionHandler(exceptionHandler);
    }

    private void execute(final NHttpClientHandler clientHandler) throws IOException {
        IOEventDispatch ioEventDispatch = new DefaultClientIODispatch(clientHandler, this.connFactory);
        this.ioReactor.execute(ioEventDispatch);
    }

    public SessionRequest openConnection(final InetSocketAddress address, final Object attachment) {
        return this.ioReactor.connect(address, null, attachment, null);
    }

    public void start(final NHttpClientHandler clientHandler) {
        this.thread = new IOReactorThread(clientHandler);
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
        this.ioReactor.shutdown();
        try {
            join(500);
        } catch (InterruptedException ignore) {
        }
    }

    private class IOReactorThread extends Thread {

        private final NHttpClientHandler clientHandler;

        private volatile Exception ex;

        public IOReactorThread(final NHttpClientHandler clientHandler) {
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
