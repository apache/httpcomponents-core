/*
 * $HeadURL:https://svn.apache.org/repos/asf/jakarta/httpcomponents/httpcore/trunk/module-niossl/src/test/java/org/apache/http/mockup/TestHttpSSLServer.java $
 * $Revision:575703 $
 * $Date:2007-09-14 16:40:15 +0200 (Fri, 14 Sep 2007) $
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

package org.apache.http.mockup;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.security.KeyStore;
import java.util.List;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import org.apache.http.impl.nio.SSLServerIOEventDispatch;
import org.apache.http.impl.nio.reactor.DefaultListeningIOReactor;
import org.apache.http.impl.nio.reactor.ExceptionEvent;
import org.apache.http.nio.NHttpServiceHandler;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.IOReactorStatus;
import org.apache.http.nio.reactor.ListenerEndpoint;
import org.apache.http.params.HttpParams;

/**
 * Trivial test server based on HttpCore NIO SSL
 * 
 */
public class TestHttpSSLServer {

    private final SSLContext sslcontext;
    private final DefaultListeningIOReactor ioReactor;
    private final HttpParams params;
    
    private volatile IOReactorThread thread;
    private ListenerEndpoint endpoint;
    
    private volatile RequestCount requestCount;
    
    public TestHttpSSLServer(final HttpParams params) throws Exception {
        super();
        this.params = params;
        this.ioReactor = new DefaultListeningIOReactor(2, this.params);
        
        ClassLoader cl = getClass().getClassLoader();
        URL url = cl.getResource("test.keystore");
        KeyStore keystore  = KeyStore.getInstance("jks");
        keystore.load(url.openStream(), "nopassword".toCharArray());
        KeyManagerFactory kmfactory = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
        kmfactory.init(keystore, "nopassword".toCharArray());
        KeyManager[] keymanagers = kmfactory.getKeyManagers(); 
        this.sslcontext = SSLContext.getInstance("TLS");
        this.sslcontext.init(keymanagers, null, null);
    }
    
    public HttpParams getParams() {
        return this.params;
    }
    
    public IOReactorStatus getStatus() {
        return this.ioReactor.getStatus();
    }
    
    public List<ExceptionEvent> getAuditLog() {
        return this.ioReactor.getAuditLog();
    }
    
    public void setRequestCount(final RequestCount requestCount) {
        this.requestCount = requestCount;
    }

    private void execute(final NHttpServiceHandler serviceHandler) throws IOException {
        IOEventDispatch ioEventDispatch = new SSLServerIOEventDispatch(
                serviceHandler, 
                this.sslcontext,
                this.params);
        
        this.ioReactor.execute(ioEventDispatch);
    }

    public ListenerEndpoint getListenerEndpoint() {
        return this.endpoint;
    }

    public void start(final NHttpServiceHandler serviceHandler) {
        this.endpoint = this.ioReactor.listen(new InetSocketAddress(0));
        this.thread = new IOReactorThread(serviceHandler);
        this.thread.start();
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
            if (this.thread != null) {
                this.thread.join(500);
            }
        } catch (InterruptedException ignore) {
        }
    }
    
    private class IOReactorThread extends Thread {

        private final NHttpServiceHandler serviceHandler;
        
        private volatile Exception ex;
        
        public IOReactorThread(final NHttpServiceHandler serviceHandler) {
            super();
            this.serviceHandler = serviceHandler;
        }
        
        @Override
        public void run() {
            try {
                execute(this.serviceHandler);
            } catch (Exception ex) {
                this.ex = ex;
                if (requestCount != null) {
                    requestCount.failure(ex);
                }
            }
        }
        
        public Exception getException() {
            return this.ex;
        }

    }    
    
}
