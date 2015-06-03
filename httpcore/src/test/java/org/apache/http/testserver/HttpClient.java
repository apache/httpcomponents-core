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
import java.net.Socket;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpClientConnection;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.impl.DefaultBHttpClientConnection;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.http.protocol.ImmutableHttpProcessor;
import org.apache.http.protocol.RequestConnControl;
import org.apache.http.protocol.RequestContent;
import org.apache.http.protocol.RequestExpectContinue;
import org.apache.http.protocol.RequestTargetHost;
import org.apache.http.protocol.RequestUserAgent;

public class HttpClient {

    private final HttpProcessor httpproc;
    private final HttpRequestExecutor httpexecutor;
    private final ConnectionReuseStrategy connStrategy;
    private final HttpCoreContext context;

    private volatile int timeout;

    public HttpClient(final HttpProcessor httpproc) {
        super();
        this.httpproc = httpproc;
        this.connStrategy = DefaultConnectionReuseStrategy.INSTANCE;
        this.httpexecutor = new HttpRequestExecutor();
        this.context = new HttpCoreContext();
    }

    public HttpClient() {
        this(new ImmutableHttpProcessor(
                new RequestContent(),
                new RequestTargetHost(),
                new RequestConnControl(),
                new RequestUserAgent("TEST-CLIENT/1.1"),
                new RequestExpectContinue(true)));
    }

    public HttpContext getContext() {
        return this.context;
    }

    public int getTimeout() {
        return this.timeout;
    }

    public void setTimeout(final int timeout) {
        this.timeout = timeout;
    }

    public DefaultBHttpClientConnection createConnection() {
        return new LoggingBHttpClientConnection(8 * 1024);
    }

    public void connect(final HttpHost host, final DefaultBHttpClientConnection conn) throws IOException {
        final Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host.getHostName(), host.getPort()), this.timeout);
        conn.bind(socket);
        conn.setSocketTimeout(this.timeout);
    }

    public HttpResponse execute(
            final HttpRequest request,
            final HttpHost targetHost,
            final HttpClientConnection conn) throws HttpException, IOException {
        this.context.setTargetHost(targetHost);
        this.httpexecutor.preProcess(request, this.httpproc, this.context);
        final HttpResponse response = this.httpexecutor.execute(request, conn, this.context);
        this.httpexecutor.postProcess(response, this.httpproc, this.context);
        return response;
    }

    public boolean keepAlive(final HttpResponse response) {
        return this.connStrategy.keepAlive(response, this.context);
    }

}
