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

import org.apache.http.HttpServerConnection;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpService;

class HttpWorker extends Thread {

    private final HttpService httpservice;
    private final HttpServerConnection conn;
    private final HttpWorkerCallback workercallback;

    private volatile boolean shutdown;
    private volatile Exception exception;

    public HttpWorker(
            final HttpService httpservice,
            final HttpServerConnection conn,
            final HttpWorkerCallback workercallback) {
        super();
        this.httpservice = httpservice;
        this.conn = conn;
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
        this.workercallback.started(this);
        try {
            HttpContext context = new BasicHttpContext();
            while (!Thread.interrupted() && !this.shutdown) {
                this.httpservice.handleRequest(this.conn, context);
            }
        } catch (Exception ex) {
            this.exception = ex;
        } finally {
            terminate();
            this.workercallback.shutdown(this);
        }
    }

    public void terminate() {
        if (this.shutdown) {
            return;
        }
        this.shutdown = true;
        try {
            this.conn.shutdown();
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