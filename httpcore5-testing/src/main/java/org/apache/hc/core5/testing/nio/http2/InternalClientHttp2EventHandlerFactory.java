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

import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hc.core5.http.ExceptionListener;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.impl.nio.ClientHttp2StreamMultiplexer;
import org.apache.hc.core5.http2.impl.nio.ClientHttpProtocolNegotiator;
import org.apache.hc.core5.http2.nio.AsyncPushConsumer;
import org.apache.hc.core5.http2.nio.HandlerFactory;
import org.apache.hc.core5.reactor.IOEventHandler;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.util.Args;

public class InternalClientHttp2EventHandlerFactory implements IOEventHandlerFactory {

    private static final AtomicLong COUNT = new AtomicLong();

    private final HttpProcessor httpProcessor;
    private final HandlerFactory<AsyncPushConsumer> exchangeHandlerFactory;
    private final Charset charset;
    private final H2Config h2Config;

    public InternalClientHttp2EventHandlerFactory(
            final HttpProcessor httpProcessor,
            final HandlerFactory<AsyncPushConsumer> exchangeHandlerFactory,
            final Charset charset,
            final H2Config h2Config) {
        this.httpProcessor = Args.notNull(httpProcessor, "HTTP processor");
        this.exchangeHandlerFactory = exchangeHandlerFactory;
        this.charset = charset;
        this.h2Config = h2Config;
    }

    @Override
    public IOEventHandler createHandler(final IOSession ioSession) {
        final String id = "http2-outgoing-" + COUNT.incrementAndGet();
        final Log sessionLog = LogFactory.getLog(ioSession.getClass());
        final InternalHttp2StreamListener streamListener = new InternalHttp2StreamListener(id);
        final ExceptionListener errorListener = new InternalHttpErrorListener(sessionLog);
        return new ClientHttpProtocolNegotiator(httpProcessor, exchangeHandlerFactory, charset, h2Config, streamListener, errorListener) {

            @Override
            protected ClientHttp2StreamMultiplexer createStreamMultiplexer(final IOSession ioSession) {
                return super.createStreamMultiplexer(new LoggingIOSession(ioSession, id, sessionLog));
            }
        };

   }

}
