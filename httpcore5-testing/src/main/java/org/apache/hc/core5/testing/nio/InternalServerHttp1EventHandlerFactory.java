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

import java.util.Objects;

import javax.net.ssl.SSLContext;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.ContentLengthStrategy;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.http.impl.DefaultContentLengthStrategy;
import org.apache.hc.core5.http.impl.Http1StreamListener;
import org.apache.hc.core5.http.impl.nio.DefaultHttpRequestParserFactory;
import org.apache.hc.core5.http.impl.nio.DefaultHttpResponseWriterFactory;
import org.apache.hc.core5.http.impl.nio.ServerHttp1IOEventHandler;
import org.apache.hc.core5.http.impl.nio.ServerHttp1StreamDuplexer;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.nio.NHttpMessageParser;
import org.apache.hc.core5.http.nio.NHttpMessageParserFactory;
import org.apache.hc.core5.http.nio.NHttpMessageWriter;
import org.apache.hc.core5.http.nio.NHttpMessageWriterFactory;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.reactor.IOEventHandler;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.reactor.ssl.SSLSessionInitializer;
import org.apache.hc.core5.reactor.ssl.SSLSessionVerifier;

/**
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
class InternalServerHttp1EventHandlerFactory implements IOEventHandlerFactory {

    private final HttpProcessor httpProcessor;
    private final HandlerFactory<AsyncServerExchangeHandler> exchangeHandlerFactory;
    private final Http1Config http1Config;
    private final CharCodingConfig charCodingConfig;
    private final ConnectionReuseStrategy connectionReuseStrategy;
    private final SSLContext sslContext;
    private final SSLSessionInitializer sslSessionInitializer;
    private final SSLSessionVerifier sslSessionVerifier;
    private final NHttpMessageParserFactory<HttpRequest> requestParserFactory;
    private final NHttpMessageWriterFactory<HttpResponse> responseWriterFactory;

    InternalServerHttp1EventHandlerFactory(
            final HttpProcessor httpProcessor,
            final HandlerFactory<AsyncServerExchangeHandler> exchangeHandlerFactory,
            final Http1Config http1Config,
            final CharCodingConfig charCodingConfig,
            final ConnectionReuseStrategy connectionReuseStrategy,
            final SSLContext sslContext,
            final SSLSessionInitializer sslSessionInitializer,
            final SSLSessionVerifier sslSessionVerifier) {
        this.httpProcessor = Objects.requireNonNull(httpProcessor, "HTTP processor");
        this.exchangeHandlerFactory = Objects.requireNonNull(exchangeHandlerFactory, "Exchange handler factory");
        this.http1Config = http1Config != null ? http1Config : Http1Config.DEFAULT;
        this.charCodingConfig = charCodingConfig != null ? charCodingConfig : CharCodingConfig.DEFAULT;
        this.connectionReuseStrategy = connectionReuseStrategy != null ? connectionReuseStrategy :
                DefaultConnectionReuseStrategy.INSTANCE;
        this.sslContext = sslContext;
        this.sslSessionInitializer = sslSessionInitializer;
        this.sslSessionVerifier = sslSessionVerifier;
        this.requestParserFactory = new DefaultHttpRequestParserFactory(this.http1Config);
        this.responseWriterFactory = DefaultHttpResponseWriterFactory.INSTANCE;
    }

    protected ServerHttp1StreamDuplexer createServerHttp1StreamDuplexer(
            final ProtocolIOSession ioSession,
            final HttpProcessor httpProcessor,
            final HandlerFactory<AsyncServerExchangeHandler> exchangeHandlerFactory,
            final Http1Config http1Config,
            final CharCodingConfig charCodingConfig,
            final ConnectionReuseStrategy connectionReuseStrategy,
            final NHttpMessageParser<HttpRequest> incomingMessageParser,
            final NHttpMessageWriter<HttpResponse> outgoingMessageWriter,
            final ContentLengthStrategy incomingContentStrategy,
            final ContentLengthStrategy outgoingContentStrategy,
            final Http1StreamListener streamListener) {
        return new ServerHttp1StreamDuplexer(ioSession, httpProcessor, exchangeHandlerFactory,
                sslContext != null ? URIScheme.HTTPS.id : URIScheme.HTTP.id, http1Config,
                charCodingConfig, connectionReuseStrategy, incomingMessageParser, outgoingMessageWriter,
                incomingContentStrategy, outgoingContentStrategy, streamListener);
    }

    @Override
    public IOEventHandler createHandler(final ProtocolIOSession ioSession, final Object attachment) {
        if (sslContext != null) {
            ioSession.startTls(sslContext, null, null ,sslSessionInitializer, sslSessionVerifier, null);
        }
        final ServerHttp1StreamDuplexer streamDuplexer = createServerHttp1StreamDuplexer(
                ioSession,
                httpProcessor,
                exchangeHandlerFactory,
                http1Config,
                charCodingConfig,
                connectionReuseStrategy,
                requestParserFactory.create(),
                responseWriterFactory.create(),
                DefaultContentLengthStrategy.INSTANCE,
                DefaultContentLengthStrategy.INSTANCE,
                LoggingHttp1StreamListener.INSTANCE_SERVER);
        return new ServerHttp1IOEventHandler(streamDuplexer);
    }

}
