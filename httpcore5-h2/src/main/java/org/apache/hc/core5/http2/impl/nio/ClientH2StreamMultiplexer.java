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
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.nio.command.ExecutableCommand;
import org.apache.hc.core5.http.nio.command.RequestExecutionCommand;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http2.H2ConnectionException;
import org.apache.hc.core5.http2.H2Error;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.frame.DefaultFrameFactory;
import org.apache.hc.core5.http2.frame.FrameFactory;
import org.apache.hc.core5.http2.frame.StreamIdGenerator;
import org.apache.hc.core5.reactor.ProtocolIOSession;

/**
 * I/O event handler for events fired by {@link ProtocolIOSession} that implements
 * client side HTTP/2 messaging protocol with full support for
 * multiplexed message transmission.
 *
 * @since 5.0
 */
@Internal
public class ClientH2StreamMultiplexer extends AbstractH2StreamMultiplexer {

    private final HandlerFactory<AsyncPushConsumer> pushHandlerFactory;

    public ClientH2StreamMultiplexer(
            final ProtocolIOSession ioSession,
            final FrameFactory frameFactory,
            final HttpProcessor httpProcessor,
            final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
            final H2Config h2Config,
            final CharCodingConfig charCodingConfig,
            final H2StreamListener streamListener) {
        super(ioSession, frameFactory, StreamIdGenerator.ODD, httpProcessor, charCodingConfig, h2Config, streamListener);
        this.pushHandlerFactory = pushHandlerFactory;
    }

    public ClientH2StreamMultiplexer(
            final ProtocolIOSession ioSession,
            final HttpProcessor httpProcessor,
            final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
            final H2Config h2Config,
            final CharCodingConfig charCodingConfig) {
        this(ioSession, DefaultFrameFactory.INSTANCE, httpProcessor, pushHandlerFactory, h2Config, charCodingConfig, null);
    }

    public ClientH2StreamMultiplexer(
            final ProtocolIOSession ioSession,
            final HttpProcessor httpProcessor,
            final H2Config h2Config,
            final CharCodingConfig charCodingConfig) {
        this(ioSession, httpProcessor, null, h2Config, charCodingConfig);
    }

    @Override
    void acceptHeaderFrame() throws H2ConnectionException {
        throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "Illegal HEADERS frame");
    }

    @Override
    void acceptPushFrame() throws H2ConnectionException {
    }

    @Override
    void acceptPushRequest() throws H2ConnectionException {
        throw new H2ConnectionException(H2Error.INTERNAL_ERROR, "Illegal attempt to push a response");
    }

    @Override
    H2StreamHandler createLocallyInitiatedStream(
            final ExecutableCommand command,
            final H2StreamChannel channel,
            final HttpProcessor httpProcessor,
            final BasicHttpConnectionMetrics connMetrics) throws IOException {
        if (command instanceof RequestExecutionCommand) {
            final RequestExecutionCommand executionCommand = (RequestExecutionCommand) command;
            final AsyncClientExchangeHandler exchangeHandler = executionCommand.getExchangeHandler();
            final HandlerFactory<AsyncPushConsumer> pushHandlerFactory = executionCommand.getPushHandlerFactory();
            final HttpCoreContext context = HttpCoreContext.adapt(executionCommand.getContext());
            context.setAttribute(HttpCoreContext.SSL_SESSION, getSSLSession());
            context.setAttribute(HttpCoreContext.CONNECTION_ENDPOINT, getEndpointDetails());
            return new ClientH2StreamHandler(channel, httpProcessor, connMetrics, exchangeHandler,
                    pushHandlerFactory != null ? pushHandlerFactory : this.pushHandlerFactory,
                    context);
        }
        throw new H2ConnectionException(H2Error.INTERNAL_ERROR, "Unexpected executable command");
    }

    @Override
    H2StreamHandler createRemotelyInitiatedStream(
            final H2StreamChannel channel,
            final HttpProcessor httpProcessor,
            final BasicHttpConnectionMetrics connMetrics,
            final HandlerFactory<AsyncPushConsumer> pushHandlerFactory) throws IOException {
        final HttpCoreContext context = HttpCoreContext.create();
        context.setAttribute(HttpCoreContext.SSL_SESSION, getSSLSession());
        context.setAttribute(HttpCoreContext.CONNECTION_ENDPOINT, getEndpointDetails());
        return new ClientPushH2StreamHandler(channel, httpProcessor, connMetrics,
                pushHandlerFactory != null ? pushHandlerFactory : this.pushHandlerFactory,
                context);
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        buf.append("[");
        appendState(buf);
        buf.append("]");
        return buf.toString();
    }

}

