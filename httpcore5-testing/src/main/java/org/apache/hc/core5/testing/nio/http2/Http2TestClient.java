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

package org.apache.hc.core5.testing.nio.http2;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.logging.LogFactory;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http2.bootstrap.nio.AsyncRequester;
import org.apache.hc.core5.http.protocol.DefaultHttpProcessor;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.RequestExpectContinue;
import org.apache.hc.core5.http.protocol.RequestUserAgent;
import org.apache.hc.core5.http.protocol.UriPatternMatcher;
import org.apache.hc.core5.http2.H2ConnectionException;
import org.apache.hc.core5.http2.H2Error;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.nio.AsyncPushConsumer;
import org.apache.hc.core5.http2.nio.HandlerFactory;
import org.apache.hc.core5.http2.nio.Supplier;
import org.apache.hc.core5.http2.protocol.H2RequestConnControl;
import org.apache.hc.core5.http2.protocol.H2RequestContent;
import org.apache.hc.core5.http2.protocol.H2RequestTargetHost;
import org.apache.hc.core5.util.Args;

public class Http2TestClient extends AsyncRequester {

    private final UriPatternMatcher<Supplier<AsyncPushConsumer>> pushHandlerMatcher;

    public Http2TestClient() throws IOException {
        super(new InternalHttpErrorListener(LogFactory.getLog(Http2TestClient.class)));
        this.pushHandlerMatcher = new UriPatternMatcher<>();
    }

    private AsyncPushConsumer createHandler(final HttpRequest request) throws HttpException, IOException {

        final HttpHost authority;
        try {
            authority = HttpHost.create(request.getAuthority());
        } catch (IllegalArgumentException ex) {
            throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, ex.getMessage());
        }
        if (!"localhost".equalsIgnoreCase(authority.getHostName())) {
            throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "Not authoritative");
        }
        String path = request.getPath();
        final int i = path.indexOf("?");
        if (i != -1) {
            path = path.substring(0, i - 1);
        }
        final Supplier<AsyncPushConsumer> supplier = pushHandlerMatcher.lookup(path);
        if (supplier != null) {
            return supplier.get();
        } else {
            return null;
        }
    }

    public void registerHandler(final String uriPattern, final Supplier<AsyncPushConsumer> supplier) {
        Args.notNull(uriPattern, "URI pattern");
        Args.notNull(supplier, "Supplier");
        pushHandlerMatcher.register(uriPattern, supplier);
    }

    public void start() throws Exception {
        start(H2Config.DEFAULT);
    }

    public void start(final H2Config h2Config) throws IOException {
        final HttpProcessor httpProcessor = new DefaultHttpProcessor(
                new H2RequestContent(),
                new H2RequestTargetHost(),
                new H2RequestConnControl(),
                new RequestUserAgent("TEST-CLIENT/1.1"),
                new RequestExpectContinue());
        start(new InternalClientHttp2EventHandlerFactory(
                httpProcessor,
                new HandlerFactory<AsyncPushConsumer>() {

                    @Override
                    public AsyncPushConsumer create(
                            final HttpRequest request, final HttpContext context) throws HttpException, IOException {
                        return createHandler(request);
                    }

                },
                StandardCharsets.US_ASCII,
                h2Config));
    }

}
