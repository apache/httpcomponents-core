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

import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.params.HttpParams;

public class TestHttpClient {

    private final ConnectingIOReactor ioReactor;
    private final HttpParams params;
    
    private volatile IOReactorThread thread;

    public TestHttpClient(final HttpParams params) throws IOException {
        super();
        this.ioReactor = new DefaultConnectingIOReactor(2, params);
        this.params = params;
    }

    public HttpParams getParams() {
        return this.params;
    }
    
    private void execute(final IOEventDispatch ioEventDispatch) throws IOException {
        this.ioReactor.execute(ioEventDispatch);
    }
    
    public void openConnection(final InetSocketAddress address, final Object attachment) {
        this.ioReactor.connect(address, null, attachment, null);
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
