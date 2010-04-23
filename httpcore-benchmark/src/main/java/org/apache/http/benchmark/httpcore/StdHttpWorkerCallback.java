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
import java.net.SocketTimeoutException;
import java.util.Queue;

import org.apache.http.ConnectionClosedException;
import org.apache.http.HttpException;

class StdHttpWorkerCallback implements HttpWorkerCallback {

    private final Queue<HttpWorker> queue;

    public StdHttpWorkerCallback(final Queue<HttpWorker> queue) {
        super();
        this.queue = queue;
    }

    public void started(final HttpWorker worker) {
        this.queue.add(worker);
    }

    public void shutdown(final HttpWorker worker) {
        this.queue.remove(worker);
        Exception ex = worker.getException();
        if (ex != null) {
            if (ex instanceof HttpException) {
                System.err.println("HTTP protocol error: " + ex.getMessage());
            } else if (ex instanceof SocketTimeoutException) {
                // ignore
            } else if (ex instanceof ConnectionClosedException) {
                // ignore
            } else if (ex instanceof IOException) {
                System.err.println("I/O error: " + ex);
            } else {
                System.err.println("Unexpected error: " + ex.getMessage());
            }
        }
    }

}