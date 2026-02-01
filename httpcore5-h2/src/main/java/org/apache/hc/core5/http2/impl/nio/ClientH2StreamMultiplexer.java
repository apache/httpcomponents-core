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
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.AsyncPushProducer;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http2.H2ConnectionException;
import org.apache.hc.core5.http2.H2Error;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.config.H2Param;
import org.apache.hc.core5.http2.config.H2Setting;
import org.apache.hc.core5.http2.frame.DefaultFrameFactory;
import org.apache.hc.core5.http2.frame.FrameFactory;
import org.apache.hc.core5.http2.frame.StreamIdGenerator;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.util.Timeout;

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
            final H2StreamListener streamListener,
            final Timeout validateAfterInactivity) {
        super(ioSession, frameFactory, StreamIdGenerator.ODD, httpProcessor, charCodingConfig, h2Config, streamListener,
                validateAfterInactivity);
        this.pushHandlerFactory = pushHandlerFactory;
    }

    public ClientH2StreamMultiplexer(
            final ProtocolIOSession ioSession,
            final FrameFactory frameFactory,
            final HttpProcessor httpProcessor,
            final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
            final H2Config h2Config,
            final CharCodingConfig charCodingConfig,
            final H2StreamListener streamListener) {
        this(ioSession, frameFactory, httpProcessor, pushHandlerFactory, h2Config, charCodingConfig, streamListener, null);
    }

    public ClientH2StreamMultiplexer(
            final ProtocolIOSession ioSession,
            final HttpProcessor httpProcessor,
            final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
            final H2Config h2Config,
            final CharCodingConfig charCodingConfig) {
        this(ioSession, DefaultFrameFactory.INSTANCE, httpProcessor, pushHandlerFactory, h2Config, charCodingConfig, null, null);
    }

    public ClientH2StreamMultiplexer(
            final ProtocolIOSession ioSession,
            final HttpProcessor httpProcessor,
            final H2Config h2Config,
            final CharCodingConfig charCodingConfig) {
        this(ioSession, httpProcessor, null, h2Config, charCodingConfig);
    }

    @Override
    void validateSetting(final H2Param param, final int value) throws H2ConnectionException {
        if (param == H2Param.ENABLE_PUSH && value == 1) {
            throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "Illegal ENABLE_PUSH setting");
        }
    }

    @Override
    H2Setting[] generateSettings(final H2Config localConfig) {
        return new H2Setting[] {
                new H2Setting(H2Param.HEADER_TABLE_SIZE, localConfig.getHeaderTableSize()),
                new H2Setting(H2Param.ENABLE_PUSH, localConfig.isPushEnabled() ? 1 : 0),
                new H2Setting(H2Param.MAX_CONCURRENT_STREAMS, localConfig.getMaxConcurrentStreams()),
                new H2Setting(H2Param.INITIAL_WINDOW_SIZE, localConfig.getInitialWindowSize()),
                new H2Setting(H2Param.MAX_FRAME_SIZE, localConfig.getMaxFrameSize()),
                new H2Setting(H2Param.MAX_HEADER_LIST_SIZE, localConfig.getMaxHeaderListSize()),
                new H2Setting(H2Param.SETTINGS_NO_RFC7540_PRIORITIES, 1)
        };
    }

    @Override
    void acceptHeaderFrame() throws H2ConnectionException {
        throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "Illegal HEADERS frame");
    }

    @Override
    void acceptPushFrame() {
    }

    @Override
    void acceptPushRequest() throws H2ConnectionException {
        throw new H2ConnectionException(H2Error.INTERNAL_ERROR, "Illegal attempt to push a response");
    }

    @Override
    H2StreamHandler outgoingRequest(
            final H2StreamChannel channel,
            final AsyncClientExchangeHandler exchangeHandler,
            final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
            final HttpContext context) {
        final HttpCoreContext coreContext = HttpCoreContext.castOrCreate(context);
        coreContext.setSSLSession(getSSLSession());
        coreContext.setEndpointDetails(getEndpointDetails());
        return new ClientH2StreamHandler(channel, getHttpProcessor(), getConnMetrics(), exchangeHandler,
                pushHandlerFactory != null ? pushHandlerFactory : this.pushHandlerFactory,
                coreContext);
    }

    @Override
    H2StreamHandler incomingRequest(final H2StreamChannel channel) throws IOException {
        throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "Illegal incoming request");
    }

    @Override
    H2StreamHandler outgoingPushPromise(final H2StreamChannel channel, final AsyncPushProducer pushProducer) throws IOException {
        throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "Illegal attempt to send push promise");
    }

    @Override
    H2StreamHandler incomingPushPromise(final H2StreamChannel channel,
                                        final HandlerFactory<AsyncPushConsumer> pushHandlerFactory) {
        final HttpCoreContext context = HttpCoreContext.create();
        context.setSSLSession(getSSLSession());
        context.setEndpointDetails(getEndpointDetails());
        return new ClientPushH2StreamHandler(channel, getHttpProcessor(), getConnMetrics(),
                pushHandlerFactory != null ? pushHandlerFactory : this.pushHandlerFactory,
                context);
    }

    @Override
    boolean allowGracefulAbort(final H2Stream stream) {
        return stream.isRemoteClosed() && !stream.isLocalClosed();
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

