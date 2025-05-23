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

package org.apache.hc.core5.http.impl.nio;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.ContentLengthStrategy;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.http.impl.DefaultContentLengthStrategy;
import org.apache.hc.core5.http.impl.Http1StreamListener;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.nio.NHttpMessageParserFactory;
import org.apache.hc.core5.http.nio.NHttpMessageWriterFactory;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.util.Args;

/**
 * {@link ServerHttp1StreamDuplexer} factory.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE_CONDITIONAL)
@Internal
public final class ServerHttp1StreamDuplexerFactory {

    private final HttpProcessor httpProcessor;
    private final HandlerFactory<AsyncServerExchangeHandler> exchangeHandlerFactory;
    private final ConnectionReuseStrategy connectionReuseStrategy;
    private final Http1Config http1Config;
    private final CharCodingConfig charCodingConfig;
    private final NHttpMessageParserFactory<HttpRequest> requestParserFactory;
    private final NHttpMessageWriterFactory<HttpResponse> responseWriterFactory;
    private final ContentLengthStrategy incomingContentStrategy;
    private final ContentLengthStrategy outgoingContentStrategy;
    private final Http1StreamListener streamListener;
    private final Callback<Exception> exceptionCallback;

    public ServerHttp1StreamDuplexerFactory(
            final HttpProcessor httpProcessor,
            final HandlerFactory<AsyncServerExchangeHandler> exchangeHandlerFactory,
            final Http1Config http1Config,
            final CharCodingConfig charCodingConfig,
            final ConnectionReuseStrategy connectionReuseStrategy,
            final NHttpMessageParserFactory<HttpRequest> requestParserFactory,
            final NHttpMessageWriterFactory<HttpResponse> responseWriterFactory,
            final ContentLengthStrategy incomingContentStrategy,
            final ContentLengthStrategy outgoingContentStrategy,
            final Http1StreamListener streamListener,
            final Callback<Exception> exceptionCallback) {
        this.httpProcessor = Args.notNull(httpProcessor, "HTTP processor");
        this.exchangeHandlerFactory = Args.notNull(exchangeHandlerFactory, "Exchange handler factory");
        this.http1Config = http1Config != null ? http1Config : Http1Config.DEFAULT;
        this.charCodingConfig = charCodingConfig != null ? charCodingConfig : CharCodingConfig.DEFAULT;
        this.connectionReuseStrategy = connectionReuseStrategy != null ? connectionReuseStrategy :
                DefaultConnectionReuseStrategy.INSTANCE;
        this.requestParserFactory = requestParserFactory != null ? requestParserFactory :
                new DefaultHttpRequestParserFactory(this.http1Config);
        this.responseWriterFactory = responseWriterFactory != null ? responseWriterFactory :
                new DefaultHttpResponseWriterFactory(this.http1Config);
        this.incomingContentStrategy = incomingContentStrategy != null ? incomingContentStrategy :
                DefaultContentLengthStrategy.INSTANCE;
        this.outgoingContentStrategy = outgoingContentStrategy != null ? outgoingContentStrategy :
                DefaultContentLengthStrategy.INSTANCE;
        this.streamListener = streamListener;
        this.exceptionCallback = exceptionCallback;
    }

    public ServerHttp1StreamDuplexerFactory(
            final HttpProcessor httpProcessor,
            final HandlerFactory<AsyncServerExchangeHandler> exchangeHandlerFactory,
            final Http1Config http1Config,
            final CharCodingConfig charCodingConfig,
            final ConnectionReuseStrategy connectionReuseStrategy,
            final NHttpMessageParserFactory<HttpRequest> requestParserFactory,
            final NHttpMessageWriterFactory<HttpResponse> responseWriterFactory,
            final Http1StreamListener streamListener,
            final Callback<Exception> exceptionCallback) {
        this(httpProcessor, exchangeHandlerFactory, http1Config, charCodingConfig,
                connectionReuseStrategy, requestParserFactory, responseWriterFactory,
                null, null, streamListener, exceptionCallback);
    }

    public ServerHttp1StreamDuplexerFactory(
            final HttpProcessor httpProcessor,
            final HandlerFactory<AsyncServerExchangeHandler> exchangeHandlerFactory,
            final Http1Config http1Config,
            final CharCodingConfig charCodingConfig,
            final Http1StreamListener streamListener,
            final Callback<Exception> exceptionCallback) {
        this(httpProcessor, exchangeHandlerFactory, http1Config, charCodingConfig, null, null ,null,
                streamListener, exceptionCallback);
    }

    public ServerHttp1StreamDuplexer create(final String scheme, final ProtocolIOSession ioSession) {
        return new ServerHttp1StreamDuplexer(ioSession, httpProcessor, exchangeHandlerFactory,
                scheme,
                http1Config,
                charCodingConfig,
                connectionReuseStrategy,
                requestParserFactory.create(),
                responseWriterFactory.create(),
                incomingContentStrategy,
                outgoingContentStrategy,
                streamListener,
                exceptionCallback);
    }

}
