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
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.ContentLengthStrategy;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.config.H1Config;
import org.apache.hc.core5.http.impl.ConnectionListener;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.http.impl.DefaultContentLengthStrategy;
import org.apache.hc.core5.http.impl.Http1StreamListener;
import org.apache.hc.core5.http.nio.NHttpMessageParserFactory;
import org.apache.hc.core5.http.nio.NHttpMessageWriterFactory;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.reactor.TlsCapableIOSession;
import org.apache.hc.core5.util.Args;

/**
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public final class ClientHttp1StreamDuplexerFactory {

    private final HttpProcessor httpProcessor;
    private final H1Config h1Config;
    private final CharCodingConfig charCodingConfig;
    private final ConnectionReuseStrategy connectionReuseStrategy;
    private final NHttpMessageParserFactory<HttpResponse> responseParserFactory;
    private final NHttpMessageWriterFactory<HttpRequest> requestWriterFactory;
    private final ContentLengthStrategy incomingContentStrategy;
    private final ContentLengthStrategy outgoingContentStrategy;
    private final ConnectionListener connectionListener;
    private final Http1StreamListener streamListener;

    public ClientHttp1StreamDuplexerFactory(
            final HttpProcessor httpProcessor,
            final H1Config h1Config,
            final CharCodingConfig charCodingConfig,
            final ConnectionReuseStrategy connectionReuseStrategy,
            final NHttpMessageParserFactory<HttpResponse> responseParserFactory,
            final NHttpMessageWriterFactory<HttpRequest> requestWriterFactory,
            final ContentLengthStrategy incomingContentStrategy,
            final ContentLengthStrategy outgoingContentStrategy,
            final ConnectionListener connectionListener,
            final Http1StreamListener streamListener) {
        this.httpProcessor = Args.notNull(httpProcessor, "HTTP processor");
        this.h1Config = h1Config != null ? h1Config : H1Config.DEFAULT;
        this.charCodingConfig = charCodingConfig !=  null ? charCodingConfig : CharCodingConfig.DEFAULT;
        this.connectionReuseStrategy = connectionReuseStrategy != null ? connectionReuseStrategy :
                DefaultConnectionReuseStrategy.INSTANCE;
        this.responseParserFactory = responseParserFactory != null ? responseParserFactory :
                new DefaultHttpResponseParserFactory(h1Config);
        this.requestWriterFactory = requestWriterFactory != null ? requestWriterFactory :
                DefaultHttpRequestWriterFactory.INSTANCE;
        this.incomingContentStrategy = incomingContentStrategy != null ? incomingContentStrategy :
                DefaultContentLengthStrategy.INSTANCE;
        this.outgoingContentStrategy = outgoingContentStrategy != null ? outgoingContentStrategy :
                DefaultContentLengthStrategy.INSTANCE;
        this.connectionListener = connectionListener;
        this.streamListener = streamListener;
    }

    public ClientHttp1StreamDuplexerFactory(
            final HttpProcessor httpProcessor,
            final H1Config h1Config,
            final CharCodingConfig charCodingConfig,
            final ConnectionReuseStrategy connectionReuseStrategy,
            final NHttpMessageParserFactory<HttpResponse> responseParserFactory,
            final NHttpMessageWriterFactory<HttpRequest> requestWriterFactory,
            final ConnectionListener connectionListener,
            final Http1StreamListener streamListener) {
        this(httpProcessor, h1Config, charCodingConfig, connectionReuseStrategy,
                responseParserFactory, requestWriterFactory, null ,null, connectionListener, streamListener);
    }

    public ClientHttp1StreamDuplexerFactory(
            final HttpProcessor httpProcessor,
            final H1Config h1Config,
            final CharCodingConfig charCodingConfig,
            final ConnectionListener connectionListener,
            final Http1StreamListener streamListener) {
        this(httpProcessor, h1Config, charCodingConfig, null, null, null, connectionListener, streamListener);
    }

    public ClientHttp1StreamDuplexerFactory(
            final HttpProcessor httpProcessor,
            final H1Config h1Config,
            final CharCodingConfig charCodingConfig) {
        this(httpProcessor, h1Config, charCodingConfig, null, null);
    }

    public ClientHttp1StreamDuplexer create(final TlsCapableIOSession ioSession) {
        return new ClientHttp1StreamDuplexer(
                ioSession,
                httpProcessor,
                h1Config,
                charCodingConfig,
                connectionReuseStrategy,
                responseParserFactory.create(),
                requestWriterFactory.create(),
                incomingContentStrategy,
                outgoingContentStrategy,
                connectionListener,
                streamListener);
    }

}
