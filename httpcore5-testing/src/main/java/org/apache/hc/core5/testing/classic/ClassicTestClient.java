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

package org.apache.hc.core5.testing.classic;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.H1Config;
import org.apache.hc.core5.http.impl.io.DefaultBHttpClientConnection;
import org.apache.hc.core5.http.impl.bootstrap.HttpRequester;
import org.apache.hc.core5.http.impl.bootstrap.RequesterBootstrap;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.util.Asserts;
import org.apache.hc.core5.util.LangUtils;

public class ClassicTestClient {

    private final DefaultBHttpClientConnection connection;
    private volatile HttpProcessor httpProcessor;
    private volatile int timeout;
    private volatile HttpHost host;

    private volatile HttpRequester requester;

    public ClassicTestClient() {
        super();
        this.connection = new DefaultBHttpClientConnection(H1Config.DEFAULT);
    }

    public void setHttpProcessor(final HttpProcessor httpProcessor) {
        this.httpProcessor = httpProcessor;
    }

    public int getTimeout() {
        return this.timeout;
    }

    public void setTimeout(final int timeout) {
        this.timeout = timeout;
    }

    public void start() {
        Asserts.check(this.requester == null, "Client already running");
        this.requester = RequesterBootstrap.bootstrap()
                .setHttpProcessor(httpProcessor)
                .create();

    }

    public void shutdown() {
        try {
            this.connection.close();
        } catch (IOException ignore) {
        }
    }

    public ClassicHttpResponse execute(
            final HttpHost targetHost,
            final ClassicHttpRequest request,
            final HttpContext context) throws HttpException, IOException {
        Asserts.check(this.requester != null, "Client not running");
        if (LangUtils.equals(this.host, targetHost)) {
            this.connection.close();
        }
        this.host = targetHost;
        if (!this.connection.isOpen()) {
            final Socket socket = new Socket();
            socket.connect(new InetSocketAddress(this.host.getHostName(), this.host.getPort()), this.timeout);
            this.connection.bind(socket);
            this.connection.setSocketTimeout(this.timeout);
        }
        if (request.getAuthority() == null) {
            request.setAuthority(new URIAuthority(targetHost));
        }
        request.setScheme(targetHost.getSchemeName());
        return this.requester.execute(this.connection, request, context);
    }

    public boolean keepAlive(
            final ClassicHttpRequest request,
            final ClassicHttpResponse response,
            final HttpContext context) throws IOException {
        return this.requester.keepAlive(this.connection, request, response, context);
    }

}
