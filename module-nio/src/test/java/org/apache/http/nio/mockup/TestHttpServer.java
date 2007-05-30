/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
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

package org.apache.http.nio.mockup;

import java.io.IOException;
import java.net.InetSocketAddress;

import org.apache.http.impl.nio.reactor.DefaultListeningIOReactor;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.ListeningIOReactor;
import org.apache.http.params.HttpParams;

/**
 * Trivial test server based on HttpCore NIO
 * 
 * @author Oleg Kalnichevski
 */
public class TestHttpServer {

    private final ListeningIOReactor ioReactor;
    private final HttpParams params;
    private final Object socketMutex;

    private volatile IOReactorThread thread;
    private volatile InetSocketAddress address;
    
    public TestHttpServer(final HttpParams params) throws IOException {
        super();
        this.ioReactor = new DefaultListeningIOReactor(2, params);
        this.params = params;
        this.socketMutex = new Object();
    }

    public HttpParams getParams() {
        return this.params;
    }
    
    private void execute(final IOEventDispatch ioEventDispatch) throws IOException {
        synchronized (this.socketMutex) {
            this.address = (InetSocketAddress) this.ioReactor.listen(
                    new InetSocketAddress(0));
            this.socketMutex.notifyAll();
        }
        this.ioReactor.execute(ioEventDispatch);
    }
    
    public InetSocketAddress getSocketAddress() throws InterruptedException {
        synchronized (this.socketMutex) {
            while (this.address == null) {
                this.socketMutex.wait();
            }
        }
        return this.address;
    }

    public void start(final IOEventDispatch ioEventDispatch) {
        this.thread = new IOReactorThread(ioEventDispatch);
        this.thread.start();
    }
    
    public void shutdown() throws IOException {
        this.ioReactor.shutdown();
        try {
            this.thread.join(500);
        } catch (InterruptedException ignore) {
        }
    }
    
    private class IOReactorThread extends Thread {

        private final IOEventDispatch ioEventDispatch;
        
        public IOReactorThread(final IOEventDispatch ioEventDispatch) {
            super();
            this.ioEventDispatch = ioEventDispatch;
        }
        
        public void run() {
            try {
                execute(this.ioEventDispatch);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

    }    
    
}
