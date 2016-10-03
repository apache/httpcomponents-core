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

package org.apache.hc.core5.http2.integration;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import org.apache.commons.logging.LogFactory;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http2.bootstrap.nio.AsyncServer;
import org.apache.hc.core5.http.protocol.DefaultHttpProcessor;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.ResponseDate;
import org.apache.hc.core5.http.protocol.ResponseServer;
import org.apache.hc.core5.http.protocol.UriPatternMatcher;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http2.nio.FixedResponseExchangeHandler;
import org.apache.hc.core5.http2.nio.HandlerFactory;
import org.apache.hc.core5.http2.nio.Supplier;
import org.apache.hc.core5.http2.protocol.H2RequestValidateHost;
import org.apache.hc.core5.http2.protocol.H2ResponseConnControl;
import org.apache.hc.core5.http2.protocol.H2ResponseContent;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.apache.hc.core5.util.Args;

public class Http2TestServer extends AsyncServer {

    private final UriPatternMatcher<Supplier<AsyncServerExchangeHandler>> responseHandlerMatcher;

    public Http2TestServer() throws IOException {
        super(new InternalHttpErrorListener(LogFactory.getLog(Http2TestClient.class)));
        this.responseHandlerMatcher = new UriPatternMatcher<>();
    }

    public InetSocketAddress start() throws Exception {
        return start(H2Config.DEFAULT);
    }

    private AsyncServerExchangeHandler createHandler(final HttpRequest request) throws HttpException {

        final HttpHost authority;
        try {
            authority = HttpHost.create(request.getAuthority());
        } catch (IllegalArgumentException ex) {
            return new FixedResponseExchangeHandler(HttpStatus.SC_BAD_REQUEST, "Invalid authority");
        }
        if (!"localhost".equalsIgnoreCase(authority.getHostName())) {
            return new FixedResponseExchangeHandler(HttpStatus.SC_MISDIRECTED_REQUEST, "Not authoritative");
        }
        String path = request.getPath();
        final int i = path.indexOf("?");
        if (i != -1) {
            path = path.substring(0, i - 1);
        }
        final Supplier<AsyncServerExchangeHandler> supplier = responseHandlerMatcher.lookup(path);
        if (supplier != null) {
            return supplier.get();
        }
        return new FixedResponseExchangeHandler(HttpStatus.SC_NOT_FOUND, "Resource not found");
    }

    public void registerHandler(final String uriPattern, final Supplier<AsyncServerExchangeHandler> supplier) {
        Args.notNull(uriPattern, "URI pattern");
        Args.notNull(supplier, "Supplier");
        responseHandlerMatcher.register(uriPattern, supplier);
    }

    public InetSocketAddress start(final H2Config h2Config) throws Exception {
        final HttpProcessor httpProcessor = new DefaultHttpProcessor(
                new HttpRequestInterceptor[] {
                        new H2RequestValidateHost()
                },
                new HttpResponseInterceptor[]{
                        new ResponseDate(),
                        new ResponseServer("TEST-SERVER/1.1"),
                        new H2ResponseContent(),
                        new H2ResponseConnControl()
                });
        start(new InternalServerHttp2EventHandlerFactory(
                httpProcessor,
                new HandlerFactory<AsyncServerExchangeHandler>() {

                    @Override
                    public AsyncServerExchangeHandler create(
                            final HttpRequest request,
                            final HttpContext context) throws HttpException {
                        return createHandler(request);
                    }

                },
                StandardCharsets.US_ASCII,
                h2Config));
        final ListenerEndpoint listener = listen(new InetSocketAddress(0));
        listener.waitFor();
        return (InetSocketAddress) listener.getAddress();
    }

}
