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

import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.impl.nio.HttpErrorListener;
import org.apache.hc.core5.http2.impl.nio.ServerHttp2StreamMultiplexer;
import org.apache.hc.core5.http2.impl.nio.ServerHttpProtocolNegotiator;
import org.apache.hc.core5.http2.nio.AsyncExchangeHandler;
import org.apache.hc.core5.http2.nio.HandlerFactory;
import org.apache.hc.core5.reactor.IOEventHandler;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOSession;

public class InternalServerHttp2EventHandlerFactory implements IOEventHandlerFactory {

    private static final AtomicLong COUNT = new AtomicLong();

    private final HandlerFactory<AsyncExchangeHandler> exchangeHandlerFactory;
    private final Charset charset;
    private final H2Config h2Config;

    public InternalServerHttp2EventHandlerFactory(
            final HandlerFactory<AsyncExchangeHandler> exchangeHandlerFactory,
            final Charset charset,
            final H2Config h2Config) {
        this.exchangeHandlerFactory = exchangeHandlerFactory;
        this.charset = charset;
        this.h2Config = h2Config;
    }

    @Override
    public IOEventHandler createHandler(final IOSession ioSession) {
        final String id = "http2-incoming-" + COUNT.incrementAndGet();
        final Log sessionLog = LogFactory.getLog(ioSession.getClass());
        final InternalHttp2StreamListener streamListener = new InternalHttp2StreamListener(id);
        final HttpErrorListener errorListener = new InternalHttpErrorListener(sessionLog);
        return new ServerHttpProtocolNegotiator(exchangeHandlerFactory, charset, h2Config, streamListener, errorListener) {

            @Override
            protected ServerHttp2StreamMultiplexer createStreamMultiplexer(final IOSession ioSession) {
                return super.createStreamMultiplexer(new LoggingIOSession(ioSession, id, sessionLog));
            }
        };

    }
}
