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

import java.io.IOException;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.impl.BasicHttpConnectionMetrics;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.nio.command.ExecutableCommand;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http2.H2ConnectionException;
import org.apache.hc.core5.http2.H2Error;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.frame.DefaultFrameFactory;
import org.apache.hc.core5.http2.frame.FrameFactory;
import org.apache.hc.core5.http2.frame.StreamIdGenerator;
import org.apache.hc.core5.net.InetAddressUtils;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.util.Args;

/**
 * I/O event handler for events fired by {@link ProtocolIOSession} that implements
 * server side HTTP/2 messaging protocol with full support for
 * multiplexed message transmission.
 *
 * @since 5.0
 */
@Internal
public class ServerHttp2StreamMultiplexer extends AbstractHttp2StreamMultiplexer {

    private final HandlerFactory<AsyncServerExchangeHandler> exchangeHandlerFactory;

    public ServerHttp2StreamMultiplexer(
            final ProtocolIOSession ioSession,
            final FrameFactory frameFactory,
            final HttpProcessor httpProcessor,
            final HandlerFactory<AsyncServerExchangeHandler> exchangeHandlerFactory,
            final CharCodingConfig charCodingConfig,
            final H2Config h2Config,
            final Http2StreamListener streamListener) {
        super(ioSession, frameFactory, StreamIdGenerator.EVEN, httpProcessor, charCodingConfig, h2Config, streamListener);
        this.exchangeHandlerFactory = Args.notNull(exchangeHandlerFactory, "Handler factory");
    }

    public ServerHttp2StreamMultiplexer(
            final ProtocolIOSession ioSession,
            final HttpProcessor httpProcessor,
            final HandlerFactory<AsyncServerExchangeHandler> exchangeHandlerFactory,
            final CharCodingConfig charCodingConfig,
            final H2Config h2Config) {
        this(ioSession, DefaultFrameFactory.INSTANCE, httpProcessor, exchangeHandlerFactory, charCodingConfig, h2Config, null);
    }

    @Override
    void acceptHeaderFrame() throws H2ConnectionException {
    }

    @Override
    void acceptPushRequest() throws H2ConnectionException {
    }

    @Override
    void acceptPushFrame() throws H2ConnectionException {
        throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "Push not supported");
    }

    @Override
    Http2StreamHandler createRemotelyInitiatedStream(
            final Http2StreamChannel channel,
            final HttpProcessor httpProcessor,
            final BasicHttpConnectionMetrics connMetrics,
            final HandlerFactory<AsyncPushConsumer> pushHandlerFactory) throws IOException {
        final HttpCoreContext context = HttpCoreContext.create();
        context.setAttribute(HttpCoreContext.SSL_SESSION, getSSLSession());
        context.setAttribute(HttpCoreContext.CONNECTION_ENDPOINT, getEndpointDetails());
        return new ServerHttp2StreamHandler(channel, httpProcessor, connMetrics, exchangeHandlerFactory, context);
    }

    @Override
    Http2StreamHandler createLocallyInitiatedStream(
            final ExecutableCommand command,
            final Http2StreamChannel channel,
            final HttpProcessor httpProcessor,
            final BasicHttpConnectionMetrics connMetrics) throws IOException {
        throw new H2ConnectionException(H2Error.INTERNAL_ERROR, "Illegal attempt to execute a request");
    }

    @Override
    public String toString() {
        final StringBuilder buffer = new StringBuilder();
        InetAddressUtils.formatAddress(buffer, getRemoteAddress());
        buffer.append("->");
        InetAddressUtils.formatAddress(buffer, getLocalAddress());
        return buffer.toString();
    }

}
