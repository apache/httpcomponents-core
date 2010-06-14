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

package org.apache.http.benchmark.httpcore;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ServerSocket;
import java.net.Socket;

import org.apache.http.impl.DefaultHttpServerConnection;
import org.apache.http.protocol.HttpService;

class HttpListener extends Thread {

    private final ServerSocket serversocket;
    private final HttpService httpservice;
    private final HttpWorkerCallback workercallback;

    private volatile boolean shutdown;
    private volatile Exception exception;

    public HttpListener(
            final ServerSocket serversocket,
            final HttpService httpservice,
            final HttpWorkerCallback workercallback) {
        super();
        this.serversocket = serversocket;
        this.httpservice = httpservice;
        this.workercallback = workercallback;
    }

    public boolean isShutdown() {
        return this.shutdown;
    }

    public Exception getException() {
        return this.exception;
    }

    @Override
    public void run() {
        while (!Thread.interrupted() && !this.shutdown) {
            try {
                // Set up HTTP connection
                Socket socket = this.serversocket.accept();
                DefaultHttpServerConnection conn = new DefaultHttpServerConnection();
                conn.bind(socket, this.httpservice.getParams());

                // Start worker thread
                HttpWorker t = new HttpWorker(this.httpservice, conn, this.workercallback);
                t.start();
            } catch (InterruptedIOException ex) {
                terminate();
            } catch (IOException ex) {
                this.exception = ex;
                terminate();
            }
        }
    }

    public void terminate() {
        if (this.shutdown) {
            return;
        }
        this.shutdown = true;
        try {
            this.serversocket.close();
        } catch (IOException ex) {
            if (this.exception != null) {
                this.exception = ex;
            }
        }
    }

    public void awaitTermination(long millis) throws InterruptedException {
        this.join(millis);
    }

}