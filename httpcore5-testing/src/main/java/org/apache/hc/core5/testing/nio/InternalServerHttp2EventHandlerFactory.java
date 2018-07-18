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

package org.apache.hc.core5.testing.nio;

import javax.net.ssl.SSLContext;

import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.config.H1Config;
import org.apache.hc.core5.http.impl.HttpProcessors;
import org.apache.hc.core5.http.impl.nio.ServerHttp1StreamDuplexerFactory;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.impl.Http2Processors;
import org.apache.hc.core5.http2.impl.nio.ServerHttp2StreamMultiplexerFactory;
import org.apache.hc.core5.http2.impl.nio.ServerHttpProtocolNegotiator;
import org.apache.hc.core5.reactor.IOEventHandler;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.util.Args;

class InternalServerHttp2EventHandlerFactory implements IOEventHandlerFactory {

    private final HttpProcessor httpProcessor;
    private final HandlerFactory<AsyncServerExchangeHandler> exchangeHandlerFactory;
    private final HttpVersionPolicy versionPolicy;
    private final H2Config h2Config;
    private final H1Config h1Config;
    private final CharCodingConfig charCodingConfig;
    private final SSLContext sslContext;

    public InternalServerHttp2EventHandlerFactory(
            final HttpProcessor httpProcessor,
            final HandlerFactory<AsyncServerExchangeHandler> exchangeHandlerFactory,
            final HttpVersionPolicy versionPolicy,
            final H2Config h2Config,
            final H1Config h1Config,
            final CharCodingConfig charCodingConfig,
            final SSLContext sslContext) {
        this.httpProcessor = Args.notNull(httpProcessor, "HTTP processor");
        this.exchangeHandlerFactory = Args.notNull(exchangeHandlerFactory, "Exchange handler factory");
        this.versionPolicy = versionPolicy != null ? versionPolicy : HttpVersionPolicy.NEGOTIATE;
        this.h2Config = h2Config != null ? h2Config : H2Config.DEFAULT;
        this.h1Config = h1Config != null ? h1Config : H1Config.DEFAULT;
        this.charCodingConfig = charCodingConfig != null ? charCodingConfig : CharCodingConfig.DEFAULT;
        this.sslContext = sslContext;
    }

    @Override
    public IOEventHandler createHandler(final ProtocolIOSession ioSession, final Object attachment) {
        if (sslContext != null) {
            ioSession.startTls(sslContext, null, null ,null, null);
        }
        final ServerHttp1StreamDuplexerFactory http1StreamHandlerFactory = new ServerHttp1StreamDuplexerFactory(
                httpProcessor != null ? httpProcessor : HttpProcessors.server(),
                exchangeHandlerFactory,
                h1Config,
                charCodingConfig,
                LoggingHttp1StreamListener.INSTANCE_SERVER);
        final ServerHttp2StreamMultiplexerFactory http2StreamHandlerFactory = new ServerHttp2StreamMultiplexerFactory(
                httpProcessor != null ? httpProcessor : Http2Processors.server(),
                exchangeHandlerFactory,
                h2Config,
                charCodingConfig,
                LoggingHttp2StreamListener.INSTANCE);
        return new ServerHttpProtocolNegotiator(
                        ioSession,
                        http1StreamHandlerFactory,
                        http2StreamHandlerFactory,
                        versionPolicy);
    }

}
