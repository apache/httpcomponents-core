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

package org.apache.hc.core5.http2.impl.nio;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.impl.ConnectionListener;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.ssl.TransportSecurityLayer;
import org.apache.hc.core5.util.Args;

/**
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class ServerHttpProtocolNegotiatorFactory implements IOEventHandlerFactory {

    private final HttpProcessor httpProcessor;
    private final HandlerFactory<AsyncServerExchangeHandler> exchangeHandlerFactory;
    private final CharCodingConfig charCodingConfig;
    private final H2Config h2Config;
    private final TlsStrategy tlsStrategy;
    private final ConnectionListener connectionListener;
    private final Http2StreamListener streamListener;

    public ServerHttpProtocolNegotiatorFactory(
            final HttpProcessor httpProcessor,
            final HandlerFactory<AsyncServerExchangeHandler> exchangeHandlerFactory,
            final CharCodingConfig charCodingConfig,
            final H2Config h2Config,
            final TlsStrategy tlsStrategy,
            final ConnectionListener connectionListener,
            final Http2StreamListener streamListener) {
        this.httpProcessor = Args.notNull(httpProcessor, "HTTP processor");
        this.exchangeHandlerFactory = Args.notNull(exchangeHandlerFactory, "Exchange handler factory");
        this.charCodingConfig = charCodingConfig != null ? charCodingConfig : CharCodingConfig.DEFAULT;
        this.h2Config = h2Config != null ? h2Config : H2Config.DEFAULT;
        this.tlsStrategy = tlsStrategy;
        this.connectionListener = connectionListener;
        this.streamListener = streamListener;
    }

    public ServerHttpProtocolNegotiatorFactory(
            final HttpProcessor httpProcessor,
            final HandlerFactory<AsyncServerExchangeHandler> exchangeHandlerFactory,
            final TlsStrategy tlsStrategy,
            final ConnectionListener connectionListener,
            final Http2StreamListener streamListener) {
        this(httpProcessor, exchangeHandlerFactory, null, null, tlsStrategy, connectionListener, streamListener);
    }

    @Override
    public ServerHttpProtocolNegotiator createHandler(final IOSession ioSession, final Object attachment) {
        if (tlsStrategy != null && ioSession instanceof TransportSecurityLayer) {
            tlsStrategy.upgrade(
                    (TransportSecurityLayer) ioSession,
                    null,
                    ioSession.getLocalAddress(),
                    ioSession.getRemoteAddress());
        }
        return new ServerHttpProtocolNegotiator(ioSession, httpProcessor, exchangeHandlerFactory,
                charCodingConfig, h2Config, connectionListener, streamListener);
    }

}
