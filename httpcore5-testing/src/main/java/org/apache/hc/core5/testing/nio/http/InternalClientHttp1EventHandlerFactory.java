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

package org.apache.hc.core5.testing.nio.http;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicLong;

import javax.net.ssl.SSLContext;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.ContentLengthStrategy;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.config.H1Config;
import org.apache.hc.core5.http.impl.ConnectionListener;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.http.impl.DefaultContentLengthStrategy;
import org.apache.hc.core5.http.impl.Http1StreamListener;
import org.apache.hc.core5.http.impl.nio.ClientHttp1IOEventHandler;
import org.apache.hc.core5.http.impl.nio.ClientHttp1StreamDuplexer;
import org.apache.hc.core5.http.impl.nio.DefaultHttpRequestWriterFactory;
import org.apache.hc.core5.http.impl.nio.DefaultHttpResponseParserFactory;
import org.apache.hc.core5.http.message.RequestLine;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.http.nio.NHttpMessageParser;
import org.apache.hc.core5.http.nio.NHttpMessageParserFactory;
import org.apache.hc.core5.http.nio.NHttpMessageWriter;
import org.apache.hc.core5.http.nio.NHttpMessageWriterFactory;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.reactor.IOEventHandler;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.ssl.TransportSecurityLayer;
import org.apache.hc.core5.testing.nio.LoggingIOEventHandler;
import org.apache.hc.core5.testing.nio.LoggingIOSession;
import org.apache.hc.core5.util.Args;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
class InternalClientHttp1EventHandlerFactory implements IOEventHandlerFactory {

    private static final AtomicLong COUNT = new AtomicLong();

    private final HttpProcessor httpProcessor;
    private final H1Config h1Config;
    private final CharCodingConfig charCodingConfig;
    private final ConnectionReuseStrategy connectionReuseStrategy;
    private final SSLContext sslContext;
    private final NHttpMessageParserFactory<HttpResponse> responseParserFactory;
    private final NHttpMessageWriterFactory<HttpRequest> requestWriterFactory;

    InternalClientHttp1EventHandlerFactory(
            final HttpProcessor httpProcessor,
            final H1Config h1Config,
            final CharCodingConfig charCodingConfig,
            final ConnectionReuseStrategy connectionReuseStrategy,
            final SSLContext sslContext) {
        this.httpProcessor = Args.notNull(httpProcessor, "HTTP processor");
        this.h1Config = h1Config != null ? h1Config : H1Config.DEFAULT;
        this.charCodingConfig = charCodingConfig != null ? charCodingConfig : CharCodingConfig.DEFAULT;
        this.connectionReuseStrategy = connectionReuseStrategy != null ? connectionReuseStrategy :
                DefaultConnectionReuseStrategy.INSTANCE;
        this.sslContext = sslContext;
        this.responseParserFactory = new DefaultHttpResponseParserFactory(this.h1Config);
        this.requestWriterFactory = DefaultHttpRequestWriterFactory.INSTANCE;
    }

    protected ClientHttp1StreamDuplexer createClientHttp1StreamDuplexer(
            final IOSession ioSession,
            final HttpProcessor httpProcessor,
            final H1Config h1Config,
            final CharCodingConfig charCodingConfig,
            final ConnectionReuseStrategy connectionReuseStrategy,
            final NHttpMessageParser<HttpResponse> incomingMessageParser,
            final NHttpMessageWriter<HttpRequest> outgoingMessageWriter,
            final ContentLengthStrategy incomingContentStrategy,
            final ContentLengthStrategy outgoingContentStrategy,
            final ConnectionListener connectionListener,
            final Http1StreamListener streamListener) {
        return new ClientHttp1StreamDuplexer(ioSession, httpProcessor, h1Config, charCodingConfig,
                connectionReuseStrategy, incomingMessageParser, outgoingMessageWriter,
                incomingContentStrategy, outgoingContentStrategy,
                connectionListener, streamListener);
    }

    @Override
    public IOEventHandler createHandler(final IOSession ioSession, final Object attachment) {
        final String id = "http1-outgoing-" + COUNT.incrementAndGet();
        if (sslContext != null && ioSession instanceof TransportSecurityLayer) {
            ((TransportSecurityLayer) ioSession).start(sslContext, null ,null, null);
        }
        final Logger sessionLog = LogManager.getLogger(ioSession.getClass());
        final Logger wireLog = LogManager.getLogger("org.apache.hc.core5.http.wire");
        final Logger headerLog = LogManager.getLogger("org.apache.hc.core5.http.headers");
        final ClientHttp1StreamDuplexer streamDuplexer = createClientHttp1StreamDuplexer(
                new LoggingIOSession(ioSession, id, sessionLog, wireLog),
                httpProcessor,
                h1Config,
                charCodingConfig,
                connectionReuseStrategy,
                responseParserFactory.create(),
                requestWriterFactory.create(),
                DefaultContentLengthStrategy.INSTANCE,
                DefaultContentLengthStrategy.INSTANCE,
                new ConnectionListener() {

                    @Override
                    public void onConnect(final HttpConnection connection) {
                        if (sessionLog.isDebugEnabled()) {
                            sessionLog.debug(id + ": "  + connection + " connected");
                        }
                    }

                    @Override
                    public void onDisconnect(final HttpConnection connection) {
                        if (sessionLog.isDebugEnabled()) {
                            sessionLog.debug(id + ": "  + connection + " disconnected");
                        }
                    }

                    @Override
                    public void onError(final HttpConnection connection, final Exception ex) {
                        if (ex instanceof ConnectionClosedException) {
                            return;
                        }
                        sessionLog.error(id + ": "  + ex.getMessage(), ex);
                    }

                },
                new Http1StreamListener() {

                    @Override
                    public void onRequestHead(final HttpConnection connection, final HttpRequest request) {
                        if (headerLog.isDebugEnabled()) {
                            headerLog.debug(id + " >> " + new RequestLine(request));
                            for (final Iterator<Header> it = request.headerIterator(); it.hasNext(); ) {
                                headerLog.debug(id + " >> " + it.next());
                            }
                        }
                    }

                    @Override
                    public void onResponseHead(final HttpConnection connection, final HttpResponse response) {
                        if (headerLog.isDebugEnabled()) {
                            headerLog.debug(id + " << " + new StatusLine(response));
                            for (final Iterator<Header> it = response.headerIterator(); it.hasNext(); ) {
                                headerLog.debug(id + " << " + it.next());
                            }
                        }
                    }

                    @Override
                    public void onExchangeComplete(final HttpConnection connection, final boolean keepAlive) {
                        if (sessionLog.isDebugEnabled()) {
                            if (keepAlive) {
                                sessionLog.debug(id + " Connection is kept alive");
                            } else {
                                sessionLog.debug(id + " Connection is not kept alive");
                            }
                        }
                    }

                });
        return new LoggingIOEventHandler(new ClientHttp1IOEventHandler(streamDuplexer), id, sessionLog);
    }

}
