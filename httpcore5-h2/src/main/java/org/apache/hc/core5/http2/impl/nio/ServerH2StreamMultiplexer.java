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
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.RequestHeaderFieldsTooLargeException;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.AsyncPushProducer;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
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
import org.apache.hc.core5.http2.hpack.HeaderListConstraintException;
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
public class ServerH2StreamMultiplexer extends AbstractH2StreamMultiplexer {

    private final HandlerFactory<AsyncServerExchangeHandler> exchangeHandlerFactory;
    private final List<HttpHost> configuredOriginSet;

    /**
     * @since 5.5
     */
    public ServerH2StreamMultiplexer(
            final ProtocolIOSession ioSession,
            final FrameFactory frameFactory,
            final HttpProcessor httpProcessor,
            final HandlerFactory<AsyncServerExchangeHandler> exchangeHandlerFactory,
            final CharCodingConfig charCodingConfig,
            final H2Config h2Config,
            final H2StreamListener streamListener,
            final Collection<HttpHost> originSet) {
        super(ioSession, frameFactory, StreamIdGenerator.EVEN, httpProcessor, charCodingConfig, h2Config, streamListener);
        this.exchangeHandlerFactory = Args.notNull(exchangeHandlerFactory, "Handler factory");
        this.configuredOriginSet = originSet != null ? H2OriginFrameCodec.normalize(originSet) : null;
    }

    public ServerH2StreamMultiplexer(
            final ProtocolIOSession ioSession,
            final FrameFactory frameFactory,
            final HttpProcessor httpProcessor,
            final HandlerFactory<AsyncServerExchangeHandler> exchangeHandlerFactory,
            final CharCodingConfig charCodingConfig,
            final H2Config h2Config,
            final H2StreamListener streamListener) {
        this(ioSession, frameFactory, httpProcessor, exchangeHandlerFactory, charCodingConfig, h2Config,
                streamListener, null);
    }

    public ServerH2StreamMultiplexer(
            final ProtocolIOSession ioSession,
            final HttpProcessor httpProcessor,
            final HandlerFactory<AsyncServerExchangeHandler> exchangeHandlerFactory,
            final CharCodingConfig charCodingConfig,
            final H2Config h2Config) {
        this(ioSession, DefaultFrameFactory.INSTANCE, httpProcessor, exchangeHandlerFactory, charCodingConfig, h2Config, null);
    }

    @Override
    void validateSetting(final H2Param param, final int value) throws H2ConnectionException {
        if (param == H2Param.ENABLE_PUSH) {
            if (value != 0 && value != 1) {
                throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "Illegal ENABLE_PUSH setting: " + value);
            }
        }
    }

    @Override
    H2Setting[] generateSettings(final H2Config localConfig) {
        return new H2Setting[] {
                new H2Setting(H2Param.HEADER_TABLE_SIZE, localConfig.getHeaderTableSize()),
                new H2Setting(H2Param.MAX_CONCURRENT_STREAMS, localConfig.getMaxConcurrentStreams()),
                new H2Setting(H2Param.INITIAL_WINDOW_SIZE, localConfig.getInitialWindowSize()),
                new H2Setting(H2Param.MAX_FRAME_SIZE, localConfig.getMaxFrameSize()),
                new H2Setting(H2Param.MAX_HEADER_LIST_SIZE, localConfig.getMaxHeaderListSize())
        };
    }

    @Override
    void acceptHeaderFrame() {
    }

    @Override
    void acceptPushRequest() {
    }

    @Override
    void acceptPushFrame() throws H2ConnectionException {
        throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "Push not supported");
    }

    @Override
    void onConnectComplete() throws IOException {
        if (configuredOriginSet != null) {
            sendOriginSet(configuredOriginSet);
        }
    }

    /**
     * Sends an ORIGIN advertisement. Entries are split across frames
     * when necessary. An empty collection sends an empty ORIGIN frame, which
     * initializes the peer's Origin Set with the connection's initial origin.
     * No frame is sent on cleartext HTTP/2 connections.
     *
     * @param origins origins to advertise.
     * @throws IOException in case of an I/O error.
     * @since 5.5
     */
    public void sendOriginSet(final Collection<HttpHost> origins) throws IOException {
        Args.notNull(origins, "Origins");
        if (!getLocalConfig().isOriginFrameEnabled() || getSSLSession() == null) {
            return;
        }
        for (final ByteBuffer payload : H2OriginFrameCodec.encode(origins, getMaxFramePayloadSize())) {
            commitConnectionFrame(getFrameFactory().createOrigin(payload));
        }
    }

    @Override
    H2StreamHandler incomingRequest(final H2StreamChannel channel) {
        final HttpCoreContext context = HttpCoreContext.create();
        context.setSSLSession(getSSLSession());
        context.setEndpointDetails(getEndpointDetails());
        return new ServerH2StreamHandler(channel, getHttpProcessor(), getConnMetrics(), exchangeHandlerFactory, context);
    }

    @Override
    H2StreamHandler outgoingRequest(
            final H2StreamChannel channel,
            final AsyncClientExchangeHandler exchangeHandler,
            final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
            final HttpContext context) throws IOException {
        throw new H2ConnectionException(H2Error.INTERNAL_ERROR, "Illegal attempt to send a request");
    }

    @Override
    H2StreamHandler incomingPushPromise(final H2StreamChannel channel,
                                        final HandlerFactory<AsyncPushConsumer> pushHandlerFactory) throws IOException {
        throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "Illegal incoming push promise");
    }

    @Override
    H2StreamHandler outgoingPushPromise(final H2StreamChannel channel,
                                        final AsyncPushProducer pushProducer) throws IOException {
        final HttpCoreContext context = HttpCoreContext.create();
        context.setSSLSession(getSSLSession());
        context.setEndpointDetails(getEndpointDetails());
        return new ServerPushH2StreamHandler(channel, getHttpProcessor(), getConnMetrics(), pushProducer, context);
    }

    @Override
    boolean allowGracefulAbort(final H2Stream stream) {
        return false;
    }

    @Override
    List<Header> decodeHeaders(final ByteBuffer payload) throws HttpException, IOException {
        try {
            return super.decodeHeaders(payload);
        } catch (final HeaderListConstraintException ex) {
            throw new RequestHeaderFieldsTooLargeException(ex.getMessage(), ex);
        }
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
