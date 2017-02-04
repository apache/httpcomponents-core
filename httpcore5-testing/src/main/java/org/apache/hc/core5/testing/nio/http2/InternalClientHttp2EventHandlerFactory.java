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

import java.util.concurrent.atomic.AtomicLong;

import javax.net.ssl.SSLContext;

import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.impl.ConnectionListener;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.impl.nio.ClientHttp2StreamMultiplexer;
import org.apache.hc.core5.http2.impl.nio.ClientHttpProtocolNegotiator;
import org.apache.hc.core5.reactor.IOEventHandler;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.ssl.TransportSecurityLayer;
import org.apache.hc.core5.testing.nio.LoggingIOEventHandler;
import org.apache.hc.core5.testing.nio.LoggingIOSession;
import org.apache.hc.core5.util.Args;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class InternalClientHttp2EventHandlerFactory implements IOEventHandlerFactory {

    private static final AtomicLong COUNT = new AtomicLong();

    private final HttpProcessor httpProcessor;
    private final HandlerFactory<AsyncPushConsumer> exchangeHandlerFactory;
    private final CharCodingConfig charCodingConfig;
    private final H2Config h2Config;
    private final SSLContext sslContext;

    InternalClientHttp2EventHandlerFactory(
            final HttpProcessor httpProcessor,
            final HandlerFactory<AsyncPushConsumer> exchangeHandlerFactory,
            final CharCodingConfig charCodingConfig,
            final H2Config h2Config,
            final SSLContext sslContext) {
        this.httpProcessor = Args.notNull(httpProcessor, "HTTP processor");
        this.exchangeHandlerFactory = exchangeHandlerFactory;
        this.charCodingConfig = charCodingConfig;
        this.h2Config = h2Config;
        this.sslContext = sslContext;
    }

    @Override
    public IOEventHandler createHandler(final IOSession ioSession, final Object attachment) {
        final String id = "http2-outgoing-" + COUNT.incrementAndGet();
        if (sslContext != null && ioSession instanceof TransportSecurityLayer) {
            ((TransportSecurityLayer) ioSession).start(sslContext, null ,null, null);
        }
        final Logger sessionLog = LogManager.getLogger(ioSession.getClass());
        return new LoggingIOEventHandler(new ClientHttpProtocolNegotiator(
                ioSession, httpProcessor, exchangeHandlerFactory, charCodingConfig, h2Config,
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

                }, new InternalHttp2StreamListener(id)) {

            @Override
            protected ClientHttp2StreamMultiplexer createStreamMultiplexer(final IOSession ioSession) {
                return super.createStreamMultiplexer(new LoggingIOSession(ioSession, id, sessionLog));
            }
        }, id, sessionLog);

   }

}
