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

package org.apache.http.impl.nio.mockup;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URL;
import java.security.KeyStore;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.nio.reactor.DefaultListeningIOReactor;
import org.apache.http.impl.nio.reactor.SSLServerIOEventDispatch;
import org.apache.http.nio.protocol.BufferingHttpServiceHandler;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.ListeningIOReactor;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.HttpRequestHandlerRegistry;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.protocol.ResponseDate;
import org.apache.http.protocol.ResponseServer;

/**
 * Trivial test server based on HttpCore NIO SSL
 * 
 * @author Oleg Kalnichevski
 */
public class TestHttpSSLServer extends TestHttpSSLServiceBase {

    private final SSLContext sslcontext;
    private final HttpRequestHandlerRegistry reqistry;
    private volatile SocketAddress address;
    private final Object mutex;
    
    public TestHttpSSLServer() throws Exception {
        super();

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
        
        this.params
            .setIntParameter(HttpConnectionParams.SO_TIMEOUT, 15000)
            .setIntParameter(HttpConnectionParams.SOCKET_BUFFER_SIZE, 8 * 1024)
            .setBooleanParameter(HttpConnectionParams.STALE_CONNECTION_CHECK, false)
            .setBooleanParameter(HttpConnectionParams.TCP_NODELAY, true)
            .setParameter(HttpProtocolParams.ORIGIN_SERVER, "TEST-SSL-SERVER/1.1");
        
        this.reqistry = new HttpRequestHandlerRegistry();
        this.ioReactor = new DefaultListeningIOReactor(2, this.params);
        this.mutex = new Object();
    }
    
    public void registerHandler(
            final String pattern, 
            final HttpRequestHandler handler) {
        this.reqistry.register(pattern, handler);
    }
    
    protected void execute() throws IOException {
        synchronized (this.mutex) {
            this.address = ((ListeningIOReactor) this.ioReactor).listen(new InetSocketAddress(0));
            this.mutex.notifyAll();
        }
        BasicHttpProcessor httpproc = new BasicHttpProcessor();
        httpproc.addInterceptor(new ResponseDate());
        httpproc.addInterceptor(new ResponseServer());
        httpproc.addInterceptor(new ResponseContent());
        httpproc.addInterceptor(new ResponseConnControl());
        
        BufferingHttpServiceHandler serviceHandler = new BufferingHttpServiceHandler(
                httpproc,
                new DefaultHttpResponseFactory(),
                new DefaultConnectionReuseStrategy(),
                this.params);
        
        serviceHandler.setEventListener(new EventLogger());
        
        serviceHandler.setHandlerResolver(this.reqistry);
        
        IOEventDispatch ioEventDispatch = new SSLServerIOEventDispatch(
                serviceHandler, 
                this.sslcontext,
                this.params);
        this.ioReactor.execute(ioEventDispatch);
    }
    
    public SocketAddress getSocketAddress() throws InterruptedException {
        synchronized (this.mutex) {
            while (this.address == null) {
                this.mutex.wait();
            }
        }
        return this.address;
    }
    
}
