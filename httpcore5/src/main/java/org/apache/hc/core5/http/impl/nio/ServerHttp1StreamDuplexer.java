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
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.config.Http1Config;
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
import org.apache.hc.core5.http.nio.command.RequestExecutionCommand;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Asserts;
import org.apache.hc.core5.util.Timeout;

/**
 * I/O event handler for events fired by {@link ProtocolIOSession} that implements
 * server side HTTP/1.1 messaging protocol with full support for
 * duplexed message transmission and message pipelining.
 *
 * @since 5.0
 */
@Internal
public class ServerHttp1StreamDuplexer extends AbstractHttp1StreamDuplexer<HttpRequest, HttpResponse> {

    private final String scheme;
    private final HttpProcessor httpProcessor;
    private final HandlerFactory<AsyncServerExchangeHandler> exchangeHandlerFactory;
    private final Http1Config http1Config;
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
            final Http1Config http1Config,
            final CharCodingConfig charCodingConfig,
            final ConnectionReuseStrategy connectionReuseStrategy,
            final NHttpMessageParser<HttpRequest> incomingMessageParser,
            final NHttpMessageWriter<HttpResponse> outgoingMessageWriter,
            final ContentLengthStrategy incomingContentStrategy,
            final ContentLengthStrategy outgoingContentStrategy,
            final Http1StreamListener streamListener) {
        super(ioSession, http1Config, charCodingConfig, incomingMessageParser, outgoingMessageWriter,
                incomingContentStrategy, outgoingContentStrategy);
        this.httpProcessor = Args.notNull(httpProcessor, "HTTP processor");
        this.exchangeHandlerFactory = Args.notNull(exchangeHandlerFactory, "Exchange handler factory");
        this.scheme = scheme;
        this.http1Config = http1Config != null ? http1Config : Http1Config.DEFAULT;
        this.connectionReuseStrategy = connectionReuseStrategy != null ? connectionReuseStrategy :
                DefaultConnectionReuseStrategy.INSTANCE;
        this.streamListener = streamListener;
        this.pipeline = new ConcurrentLinkedQueue<>();
        this.outputChannel = new Http1StreamChannel<HttpResponse>() {

            @Override
            public void close() {
                ServerHttp1StreamDuplexer.this.close(CloseMode.GRACEFUL);
            }

            @Override
            public void submit(
                    final HttpResponse response,
                    final boolean endStream,
                    final FlushMode flushMode) throws HttpException, IOException {
                if (streamListener != null) {
                    streamListener.onResponseHead(ServerHttp1StreamDuplexer.this, response);
                }
                commitMessageHead(response, endStream, flushMode);
            }

            @Override
            public void requestOutput() {
                requestSessionOutput();
            }

            @Override
            public void suspendOutput() throws IOException {
                suspendSessionOutput();
            }

            @Override
            public Timeout getSocketTimeout() {
                return getSessionTimeout();
            }

            @Override
            public void setSocketTimeout(final Timeout timeout) {
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
                // empty
            }

            @Override
            public String toString() {
                return "Http1StreamChannel[" + ServerHttp1StreamDuplexer.this + "]";
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
            final ServerHttp1StreamHandler handler = pipeline.poll();
            if (handler != null) {
                handler.failed(new ConnectionClosedException());
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
        if (response.getCode() >= HttpStatus.SC_OK) {
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
            return new ChunkDecoder(channel, buffer, http1Config, metrics);
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
        final int chunkSizeHint = http1Config.getChunkSizeHint() >= 0 ? http1Config.getChunkSizeHint() : 2048;
        if (len >= 0) {
            return new LengthDelimitedEncoder(channel, buffer, metrics, len, chunkSizeHint);
        } else if (len == ContentLengthStrategy.CHUNKED) {
            return new ChunkEncoder(channel, buffer, metrics, chunkSizeHint);
        } else {
            return new IdentityEncoder(channel, buffer, metrics, chunkSizeHint);
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
    HttpRequest parseMessageHead(final boolean endOfStream) throws IOException, HttpException {
        try {
            return super.parseMessageHead(endOfStream);
        } catch (final HttpException ex) {
            terminateExchange(ex);
            return null;
        }
    }

    void terminateExchange(final HttpException ex) throws HttpException, IOException {
        suspendSessionInput();
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
        streamHandler.terminateExchange(ex);
        incoming = null;
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
    void consumeData(final ByteBuffer src) throws HttpException, IOException {
        Asserts.notNull(incoming, "Request stream handler");
        incoming.consumeData(src);
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
    void execute(final RequestExecutionCommand executionCommand) throws HttpException {
        throw new HttpException("Illegal command: " + executionCommand.getClass());
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
    void outputEnd() throws HttpException, IOException {
        if (outgoing != null && outgoing.isResponseFinal()) {
            if (streamListener != null) {
                streamListener.onExchangeComplete(this, outgoing.keepAlive());
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
    void appendState(final StringBuilder buf) {
        super.appendState(buf);
        buf.append(", incoming=[");
        if (incoming != null) {
            incoming.appendState(buf);
        }
        buf.append("], outgoing=[");
        if (outgoing != null) {
            outgoing.appendState(buf);
        }
        buf.append("], pipeline=");
        buf.append(pipeline.size());
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        buf.append("[");
        appendState(buf);
        buf.append("]");
        return buf.toString();
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
        public void submit(
                final HttpResponse response,
                final boolean endStream,
                final FlushMode flushMode) throws HttpException, IOException {
            synchronized (this) {
                if (direct) {
                    channel.submit(response, endStream, flushMode);
                } else {
                    delayedResponse = response;
                    completed = endStream;
                }
            }
        }

        @Override
        public void suspendOutput() throws IOException {
            channel.suspendOutput();
        }

        @Override
        public void requestOutput() {
            channel.requestOutput();
        }

        @Override
        public Timeout getSocketTimeout() {
            return channel.getSocketTimeout();
        }

        @Override
        public void setSocketTimeout(final Timeout timeout) {
            channel.setSocketTimeout(timeout);
        }

        @Override
        public int write(final ByteBuffer src) throws IOException {
            synchronized (this) {
                return direct ? channel.write(src) : 0;
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
                }
                completed = true;
                return true;
            }
        }

        @Override
        public boolean isCompleted() {
            synchronized (this) {
                return direct ? channel.isCompleted() : completed;
            }
        }

        @Override
        public void activate() throws IOException, HttpException {
            synchronized (this) {
                direct = true;
                if (delayedResponse != null) {
                    channel.submit(delayedResponse, completed, completed ? FlushMode.IMMEDIATE : FlushMode.BUFFER);
                    delayedResponse = null;
                }
            }
        }

    }

}
