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

import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.nio.DefaultClientIOEventDispatch;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.nio.protocol.BufferingHttpClientHandler;
import org.apache.http.nio.protocol.HttpRequestExecutionHandler;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.RequestConnControl;
import org.apache.http.protocol.RequestContent;
import org.apache.http.protocol.RequestExpectContinue;
import org.apache.http.protocol.RequestTargetHost;
import org.apache.http.protocol.RequestUserAgent;

public class TestHttpClient extends TestHttpServiceBase {

    private HttpRequestExecutionHandler execHandler;
    
    public TestHttpClient() throws IOException {
        super();
        this.params
            .setIntParameter(HttpConnectionParams.SO_TIMEOUT, 2000)
            .setIntParameter(HttpConnectionParams.CONNECTION_TIMEOUT, 2000)
            .setIntParameter(HttpConnectionParams.SOCKET_BUFFER_SIZE, 8 * 1024)
            .setBooleanParameter(HttpConnectionParams.STALE_CONNECTION_CHECK, false)
            .setBooleanParameter(HttpConnectionParams.TCP_NODELAY, true)
            .setParameter(HttpProtocolParams.USER_AGENT, "TEST-CLIENT/1.1");

        this.ioReactor = new DefaultConnectingIOReactor(2, this.params);
    }
    
    public void setHttpRequestExecutionHandler(final HttpRequestExecutionHandler handler) {
        this.execHandler = handler;
    }
    
    protected void execute() throws IOException {
        BasicHttpProcessor httpproc = new BasicHttpProcessor();
        httpproc.addInterceptor(new RequestContent());
        httpproc.addInterceptor(new RequestTargetHost());
        httpproc.addInterceptor(new RequestConnControl());
        httpproc.addInterceptor(new RequestUserAgent());
        httpproc.addInterceptor(new RequestExpectContinue());
        
        BufferingHttpClientHandler clientHandler = new BufferingHttpClientHandler(
                httpproc,
                this.execHandler,
                new DefaultConnectionReuseStrategy(),
                this.params);
        
        clientHandler.setEventListener(new EventLogger());

        IOEventDispatch ioEventDispatch = new DefaultClientIOEventDispatch(
                clientHandler, 
                this.params);
        
        this.ioReactor.execute(ioEventDispatch);
    }
    
    public void openConnection(final InetSocketAddress address, final Object attachment) {
        ((ConnectingIOReactor) this.ioReactor).connect(
                address, null, attachment, null);
    }
    
}
