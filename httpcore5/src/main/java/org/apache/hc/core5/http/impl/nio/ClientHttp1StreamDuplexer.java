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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.ContentLengthStrategy;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.LengthRequiredException;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.config.H1Config;
import org.apache.hc.core5.http.impl.BasicHttpConnectionMetrics;
import org.apache.hc.core5.http.impl.BasicHttpTransportMetrics;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.http.impl.Http1StreamListener;
import org.apache.hc.core5.http.message.MessageSupport;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.ContentDecoder;
import org.apache.hc.core5.http.nio.ContentEncoder;
import org.apache.hc.core5.http.nio.NHttpMessageParser;
import org.apache.hc.core5.http.nio.NHttpMessageWriter;
import org.apache.hc.core5.http.nio.SessionInputBuffer;
import org.apache.hc.core5.http.nio.SessionOutputBuffer;
import org.apache.hc.core5.http.nio.command.RequestExecutionCommand;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.net.InetAddressUtils;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Asserts;

/**
 * I/O event handler for events fired by {@link ProtocolIOSession} that implements
 * client side HTTP/1.1 messaging protocol with full support for
 * duplexed message transmission and message pipelining.
 *
 * @since 5.0
 */
@Internal
public class ClientHttp1StreamDuplexer extends AbstractHttp1StreamDuplexer<HttpResponse, HttpRequest> {

    private final HttpProcessor httpProcessor;
    private final ConnectionReuseStrategy connectionReuseStrategy;
    private final H1Config h1Config;
    private final Http1StreamListener streamListener;
    private final Queue<ClientHttp1StreamHandler> pipeline;
    private final Http1StreamChannel<HttpRequest> outputChannel;

    private volatile ClientHttp1StreamHandler outgoing;
    private volatile ClientHttp1StreamHandler incoming;

    public ClientHttp1StreamDuplexer(
            final ProtocolIOSession ioSession,
            final HttpProcessor httpProcessor,
            final H1Config h1Config,
            final CharCodingConfig charCodingConfig,
            final ConnectionReuseStrategy connectionReuseStrategy,
            final NHttpMessageParser<HttpResponse> incomingMessageParser,
            final NHttpMessageWriter<HttpRequest> outgoingMessageWriter,
            final ContentLengthStrategy incomingContentStrategy,
            final ContentLengthStrategy outgoingContentStrategy,
            final Http1StreamListener streamListener) {
        super(ioSession, h1Config, charCodingConfig, incomingMessageParser, outgoingMessageWriter, incomingContentStrategy, outgoingContentStrategy);
        this.httpProcessor = Args.notNull(httpProcessor, "HTTP processor");
        this.h1Config = h1Config != null ? h1Config : H1Config.DEFAULT;
        this.connectionReuseStrategy = connectionReuseStrategy != null ? connectionReuseStrategy :
                DefaultConnectionReuseStrategy.INSTANCE;
        this.streamListener = streamListener;
        this.pipeline = new ConcurrentLinkedQueue<>();
        this.outputChannel = new Http1StreamChannel<HttpRequest>() {

            @Override
            public void close() {
                shutdownSession(CloseMode.IMMEDIATE);
            }

            @Override
            public void submit(final HttpRequest request, final boolean endStream) throws HttpException, IOException {
                if (streamListener != null) {
                    streamListener.onRequestHead(ClientHttp1StreamDuplexer.this, request);
                }
                commitMessageHead(request, endStream);
            }

            @Override
            public void suspendOutput() {
                suspendSessionOutput();
            }

            @Override
            public void requestOutput() {
                requestSessionOutput();
            }

            @Override
            public int getSocketTimeoutMillis() {
                return getSessionTimeoutMillis();
            }

            @Override
            public void setSocketTimeoutMillis(final int timeout) {
                setSessionTimeoutMillis(timeout);
            }

            @Override
            public int write(final ByteBuffer src) throws IOException {
                return streamOutput(src);
            }

            @Override
            public void complete(final List<? extends Header> trailers) throws IOException {
                endOutputStream(trailers);
            }

            @Override
            public boolean isCompleted() {
                return isOutputCompleted();
            }

            @Override
            public boolean abortGracefully() throws IOException {
                final MessageDelineation messageDelineation = endOutputStream(null);
                if (messageDelineation == MessageDelineation.MESSAGE_HEAD) {
                    requestShutdown(CloseMode.GRACEFUL);
                    return false;
                }
                return true;
            }

            @Override
            public void activate() throws HttpException, IOException {
            }

        };
    }

    @Override
    void terminate(final Exception exception) {
        if (incoming != null) {
            incoming.failed(exception);
            incoming.releaseResources();
            incoming = null;
        }
        if (outgoing != null) {
            outgoing.failed(exception);
            outgoing.releaseResources();
            outgoing = null;
        }
        for (;;) {
            final ClientHttp1StreamHandler handler = pipeline.poll();
            if (handler != null) {
                handler.failed(exception);
                handler.releaseResources();
            } else {
                break;
            }
        }
    }

    @Override
    void disconnected() {
        if (incoming != null) {
            if (!incoming.isCompleted()) {
                incoming.failed(new ConnectionClosedException());
            }
            incoming.releaseResources();
            incoming = null;
        }
        if (outgoing != null) {
            if (!outgoing.isCompleted()) {
                outgoing.failed(new ConnectionClosedException());
            }
            outgoing.releaseResources();
            outgoing = null;
        }
        for (;;) {
            final ClientHttp1StreamHandler handler = pipeline.poll();
            if (handler != null) {
                handler.failed(new ConnectionClosedException());
                handler.releaseResources();
            } else {
                break;
            }
        }
    }

    @Override
    void updateInputMetrics(final HttpResponse response, final BasicHttpConnectionMetrics connMetrics) {
        if (response.getCode() >= 200) {
            connMetrics.incrementRequestCount();
        }
    }

    @Override
    void updateOutputMetrics(final HttpRequest request, final BasicHttpConnectionMetrics connMetrics) {
        connMetrics.incrementRequestCount();
    }

    @Override
    protected boolean handleIncomingMessage(final HttpResponse response) throws HttpException {

        if (incoming == null) {
            incoming = pipeline.poll();
        }
        if (incoming == null) {
            throw new HttpException("Unexpected response");
        }
        return MessageSupport.canResponseHaveBody(incoming.getRequestMethod(), response);
    }

    @Override
    protected ContentDecoder createContentDecoder(
            final long len,
            final ReadableByteChannel channel,
            final SessionInputBuffer buffer,
            final BasicHttpTransportMetrics metrics) throws HttpException {

        if (len >= 0) {
            return new LengthDelimitedDecoder(channel, buffer, metrics, len);
        } else if (len == ContentLengthStrategy.CHUNKED) {
            return new ChunkDecoder(channel, buffer, h1Config, metrics);
        } else {
            return new IdentityDecoder(channel, buffer, metrics);
        }
    }

    @Override
    protected boolean handleOutgoingMessage(final HttpRequest request) throws HttpException {
        return true;
    }

    @Override
    protected ContentEncoder createContentEncoder(
            final long len,
            final WritableByteChannel channel,
            final SessionOutputBuffer buffer,
            final BasicHttpTransportMetrics metrics) throws HttpException {
        if (len >= 0) {
            return new LengthDelimitedEncoder(channel, buffer, metrics, len, h1Config.getChunkSizeHint());
        } else if (len == ContentLengthStrategy.CHUNKED) {
            final int chunkSizeHint = h1Config.getChunkSizeHint() >= 0 ? h1Config.getChunkSizeHint() : 2048;
            return new ChunkEncoder(channel, buffer, metrics, chunkSizeHint);
        } else {
            throw new LengthRequiredException();
        }
    }

    @Override
    boolean inputIdle() {
        return incoming == null;
    }

    @Override
    boolean outputIdle() {
        return outgoing == null && pipeline.isEmpty();
    }

    @Override
    void outputEnd() throws HttpException, IOException {
        if (outgoing != null) {
            if (outgoing.isCompleted()) {
                outgoing.releaseResources();
            }
            outgoing = null;
        }
    }

    @Override
    void execute(final RequestExecutionCommand executionCommand) throws HttpException, IOException {
        final AsyncClientExchangeHandler exchangeHandler = executionCommand.getExchangeHandler();
        final HttpCoreContext context = HttpCoreContext.adapt(executionCommand.getContext());
        context.setAttribute(HttpCoreContext.SSL_SESSION, getSSLSession());
        context.setAttribute(HttpCoreContext.CONNECTION_ENDPOINT, getEndpointDetails());
        final ClientHttp1StreamHandler handler = new ClientHttp1StreamHandler(
                outputChannel,
                httpProcessor,
                h1Config,
                connectionReuseStrategy,
                exchangeHandler,
                context);
        pipeline.add(handler);
        outgoing = handler;

        if (handler.isOutputReady()) {
            handler.produceOutput();
        }
    }

    @Override
    boolean isOutputReady() {
        return outgoing != null && outgoing.isOutputReady();
    }

    @Override
    void produceOutput() throws HttpException, IOException {
        if (outgoing != null) {
            outgoing.produceOutput();
        }
    }

    @Override
    void consumeHeader(final HttpResponse response, final EntityDetails entityDetails) throws HttpException, IOException {
        if (streamListener != null) {
            streamListener.onResponseHead(this, response);
        }
        Asserts.notNull(incoming, "Response stream handler");
        incoming.consumeHeader(response, entityDetails);
    }

    @Override
    int consumeData(final ByteBuffer src) throws HttpException, IOException {
        Asserts.notNull(incoming, "Response stream handler");
        return incoming.consumeData(src);
    }

    @Override
    void updateCapacity(final CapacityChannel capacityChannel) throws HttpException, IOException {
        Asserts.notNull(incoming, "Response stream handler");
        incoming.updateCapacity(capacityChannel);
    }

    @Override
    void dataEnd(final List<? extends Header> trailers) throws HttpException, IOException {
        Asserts.notNull(incoming, "Response stream handler");
        incoming.dataEnd(trailers);
    }

    @Override
    void inputEnd() throws HttpException, IOException {
        if (incoming != null && incoming.isResponseFinal()) {
            if (streamListener != null) {
                streamListener.onExchangeComplete(this, isOpen());
            }
            if (incoming.isCompleted()) {
                incoming.releaseResources();
            }
            incoming = null;
        }
    }

    @Override
    boolean handleTimeout() {
        return outgoing != null && outgoing.handleTimeout();
    }

    @Override
    public String toString() {
        final StringBuilder buffer = new StringBuilder();
        InetAddressUtils.formatAddress(buffer, getLocalAddress());
        buffer.append("->");
        InetAddressUtils.formatAddress(buffer, getRemoteAddress());
        return buffer.toString();
    }

}
