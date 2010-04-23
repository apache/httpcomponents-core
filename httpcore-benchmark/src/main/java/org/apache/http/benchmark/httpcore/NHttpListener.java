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
import java.net.InetSocketAddress;

import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.ListenerEndpoint;
import org.apache.http.nio.reactor.ListeningIOReactor;

public class NHttpListener extends Thread {

    private final ListeningIOReactor ioreactor;
    private final IOEventDispatch ioEventDispatch;

    private volatile Exception exception;

    public NHttpListener(
            final ListeningIOReactor ioreactor,
            final IOEventDispatch ioEventDispatch) throws IOException {
        super();
        this.ioreactor = ioreactor;
        this.ioEventDispatch = ioEventDispatch;
    }

    @Override
    public void run() {
        try {
            this.ioreactor.execute(this.ioEventDispatch);
        } catch (Exception ex) {
            this.exception = ex;
        }
    }

    public void listen(final InetSocketAddress address) throws InterruptedException {
        ListenerEndpoint endpoint = this.ioreactor.listen(address);
        endpoint.waitFor();
    }

    public void terminate() {
        try {
            this.ioreactor.shutdown();
        } catch (IOException ex) {
        }
    }

    public Exception getException() {
        return this.exception;
    }

    public void awaitTermination(long millis) throws InterruptedException {
        this.join(millis);
    }

}
