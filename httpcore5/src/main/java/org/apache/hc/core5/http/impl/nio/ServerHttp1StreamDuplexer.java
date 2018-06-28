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

import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.ContentLengthStrategy;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.config.H1Config;
import org.apache.hc.core5.http.impl.BasicHttpConnectionMetrics;
import org.apache.hc.core5.http.impl.BasicHttpTransportMetrics;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.http.impl.Http1StreamListener;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.ContentDecoder;
import org.apache.hc.core5.http.nio.ContentEncoder;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.nio.NHttpMessageParser;
import org.apache.hc.core5.http.nio.NHttpMessageWriter;
import org.apache.hc.core5.http.nio.SessionInputBuffer;
import org.apache.hc.core5.http.nio.SessionOutputBuffer;
import org.apache.hc.core5.http.nio.command.ExecutionCommand;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.io.ShutdownType;
import org.apache.hc.core5.net.InetAddressUtils;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Asserts;

public class ServerHttp1StreamDuplexer extends AbstractHttp1StreamDuplexer<HttpRequest, HttpResponse> {

    private final String scheme;
    private final HttpProcessor httpProcessor;
    private final HandlerFactory<AsyncServerExchangeHandler> exchangeHandlerFactory;
    private final H1Config h1Config;
    private final ConnectionReuseStrategy connectionReuseStrategy;
    private final Http1StreamListener streamListener;
    private final Queue<ServerHttp1StreamHandler> pipeline;
    private final Http1StreamChannel<HttpResponse> outputChannel;

    private volatile ServerHttp1StreamHandler outgoing;
    private volatile ServerHttp1StreamHandler incoming;

    public ServerHttp1StreamDuplexer(
            final ProtocolIOSession ioSession,
            final HttpProcessor httpProcessor,
            final HandlerFactory<AsyncServerExchangeHandler> exchangeHandlerFactory,
            final String scheme,
            final H1Config h1Config,
            final CharCodingConfig charCodingConfig,
            final ConnectionReuseStrategy connectionReuseStrategy,
            final NHttpMessageParser<HttpRequest> incomingMessageParser,
            final NHttpMessageWriter<HttpResponse> outgoingMessageWriter,
            final ContentLengthStrategy incomingContentStrategy,
            final ContentLengthStrategy outgoingContentStrategy,
            final Http1StreamListener streamListener) {
        super(ioSession, h1Config, charCodingConfig, incomingMessageParser, outgoingMessageWriter, incomingContentStrategy, outgoingContentStrategy);
        this.httpProcessor = Args.notNull(httpProcessor, "HTTP processor");
        this.exchangeHandlerFactory = Args.notNull(exchangeHandlerFactory, "Exchange handler factory");
        this.scheme = scheme;
        this.h1Config = h1Config != null ? h1Config : H1Config.DEFAULT;
        this.connectionReuseStrategy = connectionReuseStrategy != null ? connectionReuseStrategy :
                DefaultConnectionReuseStrategy.INSTANCE;
        this.streamListener = streamListener;
        this.pipeline = new ConcurrentLinkedQueue<>();
        this.outputChannel = new Http1StreamChannel<HttpResponse>() {

            @Override
            public void close() {
                shutdown(ShutdownType.IMMEDIATE);
            }

            @Override
            public void submit(final HttpResponse response, final boolean endStream) throws HttpException, IOException {
                if (streamListener != null) {
                    streamListener.onResponseHead(ServerHttp1StreamDuplexer.this, response);
                }
                commitMessageHead(response, endStream);
            }

            @Override
            public void requestOutput() {
                requestSessionOutput();
            }

            @Override
            public void suspendOutput() {
                suspendSessionOutput();
            }

            @Override
            public int getSocketTimeout() {
                return getSessionTimeout();
            }

            @Override
            public void setSocketTimeout(final int timeout) {
                setSessionTimeout(timeout);
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
                return messageDelineation != MessageDelineation.MESSAGE_HEAD;
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
            final ServerHttp1StreamHandler handler = pipeline.poll();
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
                incoming.failed(new ConnectionClosedException("Connection closed"));
            }
            incoming.releaseResources();
            incoming = null;
        }
        if (outgoing != null) {
            if (!outgoing.isCompleted()) {
                outgoing.failed(new ConnectionClosedException("Connection closed"));
            }
            outgoing.releaseResources();
            outgoing = null;
        }
        for (;;) {
            final ServerHttp1StreamHandler handler = pipeline.poll();
            if (handler != null) {
                handler.failed(new ConnectionClosedException("Connection closed"));
                handler.releaseResources();
            } else {
                break;
            }
        }
    }

    @Override
    void updateInputMetrics(final HttpRequest request, final BasicHttpConnectionMetrics connMetrics) {
        connMetrics.incrementRequestCount();
    }

    @Override
    void updateOutputMetrics(final HttpResponse response, final BasicHttpConnectionMetrics connMetrics) {
        if (response.getCode() >= 200) {
            connMetrics.incrementRequestCount();
        }
    }

    @Override
    protected boolean handleIncomingMessage(final HttpRequest request) throws HttpException {
        return true;
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
            return null;
        }
    }

    @Override
    protected boolean handleOutgoingMessage(final HttpResponse response) throws HttpException {
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
            return new IdentityEncoder(channel, buffer, metrics, h1Config.getChunkSizeHint());
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
    void consumeHeader(final HttpRequest request, final EntityDetails entityDetails) throws HttpException, IOException {
        if (streamListener != null) {
            streamListener.onRequestHead(this, request);
        }
        final ServerHttp1StreamHandler streamHandler;
        final HttpCoreContext context = HttpCoreContext.create();
        context.setAttribute(HttpCoreContext.SSL_SESSION, getSSLSession());
        context.setAttribute(HttpCoreContext.CONNECTION_ENDPOINT, getEndpointDetails());
        if (outgoing == null) {
            streamHandler = new ServerHttp1StreamHandler(
                    outputChannel,
                    httpProcessor,
                    connectionReuseStrategy,
                    exchangeHandlerFactory,
                    context);
            outgoing = streamHandler;
        } else {
            streamHandler = new ServerHttp1StreamHandler(
                    new DelayedOutputChannel(outputChannel),
                    httpProcessor,
                    connectionReuseStrategy,
                    exchangeHandlerFactory,
                    context);
            pipeline.add(streamHandler);
        }
        request.setScheme(scheme);
        streamHandler.consumeHeader(request, entityDetails);
        incoming = streamHandler;
    }

    @Override
    int consumeData(final ByteBuffer src) throws HttpException, IOException {
        Asserts.notNull(incoming, "Request stream handler");
        return incoming.consumeData(src);
    }

    @Override
    void updateCapacity(final CapacityChannel capacityChannel) throws HttpException, IOException {
        Asserts.notNull(incoming, "Request stream handler");
        incoming.updateCapacity(capacityChannel);
    }

    @Override
    void dataEnd(final List<? extends Header> trailers) throws HttpException, IOException {
        Asserts.notNull(incoming, "Request stream handler");
        incoming.dataEnd(trailers);
    }

    @Override
    void inputEnd() throws HttpException, IOException {
        if (incoming != null) {
            if (incoming.isCompleted()) {
                incoming.releaseResources();
            }
            incoming = null;
        }
    }

    @Override
    void execute(final ExecutionCommand executionCommand) throws HttpException {
        throw new HttpException("Illegal command: " + executionCommand.getClass());
    }

    @Override
    boolean isOutputReady() {
        return outgoing != null && outgoing.isOutputReady();
    }

    @Override
    void produceOutput() throws HttpException, IOException {
        Asserts.notNull(outgoing, "Response stream handler");
        outgoing.produceOutput();
    }

    @Override
    void outputEnd() throws HttpException, IOException {
        if (outgoing != null && outgoing.isResponseFinal()) {
            if (streamListener != null) {
                streamListener.onExchangeComplete(this, isOpen());
            }
            if (outgoing.isCompleted()) {
                outgoing.releaseResources();
            }
            outgoing = null;
        }
        if (outgoing == null && isOpen()) {
            final ServerHttp1StreamHandler handler = pipeline.poll();
            if (handler != null) {
                outgoing = handler;
                handler.activateChannel();
                if (handler.isOutputReady()) {
                    handler.produceOutput();
                }
            }
        }
    }

    @Override
    boolean handleTimeout() {
        return false;
    }

    @Override
    public String toString() {
        final StringBuilder buffer = new StringBuilder();
        InetAddressUtils.formatAddress(buffer, getRemoteAddress());
        buffer.append("->");
        InetAddressUtils.formatAddress(buffer, getLocalAddress());
        return buffer.toString();
    }

    private static class DelayedOutputChannel implements Http1StreamChannel<HttpResponse> {

        private final Http1StreamChannel<HttpResponse> channel;

        private volatile boolean direct;
        private volatile HttpResponse delayedResponse;
        private volatile boolean completed;

        private DelayedOutputChannel(final Http1StreamChannel<HttpResponse> channel) {
            this.channel = channel;
        }

        @Override
        public void close() {
            channel.close();
        }

        @Override
        public void submit(final HttpResponse response, final boolean endStream) throws HttpException, IOException {
            synchronized (this) {
                if (direct) {
                    channel.submit(response, endStream);
                } else {
                    delayedResponse = response;
                    completed = endStream;
                }
            }
        }

        @Override
        public void suspendOutput() {
            channel.suspendOutput();
        }

        @Override
        public void requestOutput() {
            channel.requestOutput();
        }

        @Override
        public int getSocketTimeout() {
            return channel.getSocketTimeout();
        }

        @Override
        public void setSocketTimeout(final int timeout) {
            channel.setSocketTimeout(timeout);
        }

        @Override
        public int write(final ByteBuffer src) throws IOException {
            synchronized (this) {
                if (direct) {
                    return channel.write(src);
                } else {
                    return 0;
                }
            }
        }

        @Override
        public void complete(final List<? extends Header> trailers) throws IOException {
            synchronized (this) {
                if (direct) {
                    channel.complete(trailers);
                } else {
                    completed = true;
                }
            }
        }

        @Override
        public boolean abortGracefully() throws IOException {
            synchronized (this) {
                if (direct) {
                    return channel.abortGracefully();
                } else {
                    completed = true;
                    return true;
                }
            }
        }

        @Override
        public boolean isCompleted() {
            synchronized (this) {
                if (direct) {
                    return channel.isCompleted();
                } else {
                    return completed;
                }
            }
        }

        @Override
        public void activate() throws IOException, HttpException {
            synchronized (this) {
                direct = true;
                if (delayedResponse != null) {
                    channel.submit(delayedResponse, completed);
                    delayedResponse = null;
                }
            }
        }

    }

}
