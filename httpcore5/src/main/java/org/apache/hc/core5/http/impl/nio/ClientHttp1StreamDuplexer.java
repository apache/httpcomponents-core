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
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.ContentLengthStrategy;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.LengthRequiredException;
import org.apache.hc.core5.http.config.ConnectionConfig;
import org.apache.hc.core5.http.config.H1Config;
import org.apache.hc.core5.http.impl.BasicHttpConnectionMetrics;
import org.apache.hc.core5.http.impl.BasicHttpTransportMetrics;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.http.impl.DefaultContentLengthStrategy;
import org.apache.hc.core5.http.nio.ContentDecoder;
import org.apache.hc.core5.http.nio.ContentEncoder;
import org.apache.hc.core5.http.nio.NHttpMessageParser;
import org.apache.hc.core5.http.nio.NHttpMessageWriter;
import org.apache.hc.core5.http.nio.SessionInputBuffer;
import org.apache.hc.core5.http.nio.SessionOutputBuffer;
import org.apache.hc.core5.http.nio.command.ExecutionCommand;
import org.apache.hc.core5.http.nio.command.ShutdownType;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Asserts;

public class ClientHttp1StreamDuplexer extends AbstractHttp1StreamDuplexer<HttpResponse, HttpRequest> {

    private final HttpProcessor httpProcessor;
    private final ConnectionReuseStrategy connectionReuseStrategy;
    private final int fragmentSizeHint;
    private final H1Config h1Config;
    private final ContentLengthStrategy incomingContentStrategy;
    private final ContentLengthStrategy outgoingContentStrategy;
    private final Http1StreamListener streamListener;
    private final ByteBuffer contentBuffer;
    private final Queue<ClientHttp1StreamHandler> pipeline;
    private final Http1StreamChannel<HttpRequest> outputChannel;

    private volatile boolean inconsistent;
    private volatile ClientHttp1StreamHandler outgoing;
    private volatile ClientHttp1StreamHandler incoming;

    public ClientHttp1StreamDuplexer(
            final IOSession ioSession,
            final HttpProcessor httpProcessor,
            final H1Config h1Config,
            final ConnectionConfig connectionConfig,
            final ConnectionReuseStrategy connectionReuseStrategy,
            final NHttpMessageParser<HttpResponse> incomingMessageParser,
            final NHttpMessageWriter<HttpRequest> outgoingMessageWriter,
            final ContentLengthStrategy incomingContentStrategy,
            final ContentLengthStrategy outgoingContentStrategy,
            final ConnectionListener connectionListener,
            final Http1StreamListener streamListener) {
        super(ioSession, connectionConfig, incomingMessageParser, outgoingMessageWriter, connectionListener);
        this.httpProcessor = Args.notNull(httpProcessor, "HTTP processor");
        final int bufferSize = connectionConfig.getBufferSize();
        final int fragmentSizeHint = connectionConfig.getFragmentSizeHint();
        this.fragmentSizeHint = fragmentSizeHint >= 0 ? fragmentSizeHint : bufferSize;
        this.h1Config = h1Config != null ? h1Config : H1Config.DEFAULT;
        this.connectionReuseStrategy = connectionReuseStrategy != null ? connectionReuseStrategy :
                DefaultConnectionReuseStrategy.INSTANCE;
        this.incomingContentStrategy = incomingContentStrategy != null ? incomingContentStrategy :
                DefaultContentLengthStrategy.INSTANCE;
        this.outgoingContentStrategy = outgoingContentStrategy != null ? outgoingContentStrategy :
                DefaultContentLengthStrategy.INSTANCE;
        this.streamListener = streamListener;
        this.contentBuffer = ByteBuffer.allocate(connectionConfig.getBufferSize());
        this.pipeline = new ConcurrentLinkedQueue<>();
        this.outputChannel = new Http1StreamChannel<HttpRequest>() {

            @Override
            public void submit(final HttpRequest request, final boolean endStream) throws HttpException, IOException {
                if (streamListener != null) {
                    streamListener.onRequestHead(ClientHttp1StreamDuplexer.this, request);
                }
                commitMessageHead(request, endStream);
            }

            @Override
            public void update(final int increment) throws IOException {
                if (increment > 0) {
                    requestSessionInput();
                }
            }

            @Override
            public void suspendInput() {
                suspendSessionInput();
            }

            @Override
            public void requestInput() {
                requestSessionInput();
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
            public int write(final ByteBuffer src) throws IOException {
                return streamOutput(src);
            }

            @Override
            public void complete() throws IOException {
                endOutputStream();
            }

            @Override
            public boolean isCompleted() {
                return isOutputCompleted();
            }

            @Override
            public void abortOutput() throws IOException {
                final MessageDelineation messageDelineation = endOutputStream();
                if (messageDelineation == MessageDelineation.MESSAGE_HEAD) {
                    inconsistent = true;
                    requestShutdown(ShutdownType.GRACEFUL);
                }
            }

            @Override
            public void activate() throws HttpException, IOException {
            }

        };
    }

    @Override
    public void releaseResources() {
        if (incoming != null) {
            incoming.releaseResources();
            incoming = null;
        }
        if (outgoing != null) {
            outgoing.releaseResources();
            outgoing = null;
        }
        for (;;) {
            final ClientHttp1StreamHandler handler = pipeline.poll();
            if (handler != null) {
                handler.releaseResources();
            } else {
                break;
            }
        }
    }

    @Override
    void terminate(final Exception exception) {
        if (incoming != null) {
            incoming.failed(exception);
            incoming = null;
        }
        if (outgoing != null) {
            outgoing.failed(exception);
            outgoing = null;
        }
        for (;;) {
            final ClientHttp1StreamHandler handler = pipeline.poll();
            if (handler != null) {
                handler.failed(exception);
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
    protected ContentDecoder handleIncomingMessage(
            final HttpResponse response,
            final ReadableByteChannel channel,
            final SessionInputBuffer buffer,
            final BasicHttpTransportMetrics metrics) throws HttpException {

        if (incoming == null) {
            incoming = pipeline.poll();
        }
        if (incoming == null) {
            throw new HttpException("Unexpected response");
        }

        if (incoming.isHeadRequest()) {
            return null;
        }
        final int status = response.getCode();
        if (status < HttpStatus.SC_SUCCESS || status == HttpStatus.SC_NO_CONTENT || status == HttpStatus.SC_NOT_MODIFIED) {
            return null;
        }
        final long len = incomingContentStrategy.determineLength(response);
        if (len >= 0) {
            return new LengthDelimitedDecoder(channel, buffer, metrics, len);
        } else if (len == ContentLengthStrategy.CHUNKED) {
            return new ChunkDecoder(channel, buffer, h1Config, metrics);
        } else {
            return new IdentityDecoder(channel, buffer, metrics);
        }
    }

    @Override
    protected ContentEncoder handleOutgoingMessage(
            final HttpRequest request,
            final WritableByteChannel channel,
            final SessionOutputBuffer buffer,
            final BasicHttpTransportMetrics metrics) throws HttpException {
        final long len = outgoingContentStrategy.determineLength(request);
        if (len >= 0) {
            return new LengthDelimitedEncoder(channel, buffer, metrics, len, fragmentSizeHint);
        } else if (len == ContentLengthStrategy.CHUNKED) {
            return new ChunkEncoder(channel, buffer, metrics, fragmentSizeHint, null);
        } else {
            throw new LengthRequiredException("Length required");
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
    void execute(final ExecutionCommand executionCommand) throws HttpException, IOException {
        final ClientHttp1StreamHandler handler = new ClientHttp1StreamHandler(
                this,
                outputChannel,
                httpProcessor,
                h1Config,
                connectionReuseStrategy,
                executionCommand.getExchangeHandler(),
                executionCommand.getContext(),
                contentBuffer);
        if (handler.isOutputReady()) {
            handler.produceOutput();
        }
        pipeline.add(handler);
        outgoing = handler;
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
    void consumeHeader(final HttpResponse response, final boolean endStream) throws HttpException, IOException {
        if (streamListener != null) {
            streamListener.onResponseHead(this, response);
        }
        Asserts.notNull(incoming, "Response stream handler");
        incoming.consumeHeader(response, endStream);
    }

    @Override
    void consumeData(final ContentDecoder contentDecoder) throws HttpException, IOException {
        Asserts.notNull(incoming, "Response stream handler");
        incoming.consumeData(contentDecoder);
    }

    @Override
    void inputEnd() throws HttpException, IOException {
        Asserts.notNull(incoming, "Response stream handler");
        if (incoming.isResponseCompleted()) {
            final boolean keepAlive = !inconsistent && incoming.keepAlive();
            if (incoming.isCompleted()) {
                incoming.releaseResources();
            }
            incoming = null;
            if (streamListener != null) {
                streamListener.onExchangeComplete(this, keepAlive);
            }
            if (!keepAlive) {
                if (outgoing != null && outgoing.isCompleted()) {
                    outgoing.releaseResources();
                    outgoing = null;
                }
                if (outgoing == null && pipeline.isEmpty()) {
                    requestShutdown(ShutdownType.IMMEDIATE);
                } else {
                    doTerminate(new ConnectionClosedException("Connection cannot be kept alive"));
                }
            }
        }
    }

    @Override
    boolean handleTimeout() {
        return outgoing != null && outgoing.handleTimeout();
    }

}
