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
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.WritableByteChannel;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLSession;

import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.ContentLengthStrategy;
import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpMessage;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.BasicEndpointDetails;
import org.apache.hc.core5.http.impl.BasicHttpConnectionMetrics;
import org.apache.hc.core5.http.impl.BasicHttpTransportMetrics;
import org.apache.hc.core5.http.impl.CharCodingSupport;
import org.apache.hc.core5.http.impl.DefaultContentLengthStrategy;
import org.apache.hc.core5.http.impl.IncomingEntityDetails;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.ContentDecoder;
import org.apache.hc.core5.http.nio.ContentEncoder;
import org.apache.hc.core5.http.nio.NHttpMessageParser;
import org.apache.hc.core5.http.nio.NHttpMessageWriter;
import org.apache.hc.core5.http.nio.SessionInputBuffer;
import org.apache.hc.core5.http.nio.SessionOutputBuffer;
import org.apache.hc.core5.http.nio.command.CommandSupport;
import org.apache.hc.core5.http.nio.command.RequestExecutionCommand;
import org.apache.hc.core5.http.nio.command.ShutdownCommand;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.io.SocketTimeoutExceptionFactory;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.EventMask;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Identifiable;
import org.apache.hc.core5.util.Timeout;

abstract class AbstractHttp1StreamDuplexer<IncomingMessage extends HttpMessage, OutgoingMessage extends HttpMessage>
        implements Identifiable, HttpConnection {

    private enum ConnectionState { READY, ACTIVE, GRACEFUL_SHUTDOWN, SHUTDOWN}

    private final ProtocolIOSession ioSession;
    private final Http1Config http1Config;
    private final SessionInputBufferImpl inbuf;
    private final SessionOutputBufferImpl outbuf;
    private final BasicHttpTransportMetrics inTransportMetrics;
    private final BasicHttpTransportMetrics outTransportMetrics;
    private final BasicHttpConnectionMetrics connMetrics;
    private final NHttpMessageParser<IncomingMessage> incomingMessageParser;
    private final NHttpMessageWriter<OutgoingMessage> outgoingMessageWriter;
    private final ContentLengthStrategy incomingContentStrategy;
    private final ContentLengthStrategy outgoingContentStrategy;
    private final ByteBuffer contentBuffer;
    private final AtomicInteger outputRequests;

    private volatile Message<IncomingMessage, ContentDecoder> incomingMessage;
    private volatile Message<OutgoingMessage, ContentEncoder> outgoingMessage;
    private volatile ConnectionState connState;
    private volatile CapacityWindow capacityWindow;

    private volatile ProtocolVersion version;
    private volatile EndpointDetails endpointDetails;

    AbstractHttp1StreamDuplexer(
            final ProtocolIOSession ioSession,
            final Http1Config http1Config,
            final CharCodingConfig charCodingConfig,
            final NHttpMessageParser<IncomingMessage> incomingMessageParser,
            final NHttpMessageWriter<OutgoingMessage> outgoingMessageWriter,
            final ContentLengthStrategy incomingContentStrategy,
            final ContentLengthStrategy outgoingContentStrategy) {
        this.ioSession = Args.notNull(ioSession, "I/O session");
        this.http1Config = http1Config != null ? http1Config : Http1Config.DEFAULT;
        final int bufferSize = this.http1Config.getBufferSize();
        this.inbuf = new SessionInputBufferImpl(bufferSize, bufferSize < 512 ? bufferSize : 512,
                this.http1Config.getMaxLineLength(),
                CharCodingSupport.createDecoder(charCodingConfig));
        this.outbuf = new SessionOutputBufferImpl(bufferSize, bufferSize < 512 ? bufferSize : 512,
                CharCodingSupport.createEncoder(charCodingConfig));
        this.inTransportMetrics = new BasicHttpTransportMetrics();
        this.outTransportMetrics = new BasicHttpTransportMetrics();
        this.connMetrics = new BasicHttpConnectionMetrics(inTransportMetrics, outTransportMetrics);
        this.incomingMessageParser = incomingMessageParser;
        this.outgoingMessageWriter = outgoingMessageWriter;
        this.incomingContentStrategy = incomingContentStrategy != null ? incomingContentStrategy :
                DefaultContentLengthStrategy.INSTANCE;
        this.outgoingContentStrategy = outgoingContentStrategy != null ? outgoingContentStrategy :
                DefaultContentLengthStrategy.INSTANCE;
        this.contentBuffer = ByteBuffer.allocate(this.http1Config.getBufferSize());
        this.outputRequests = new AtomicInteger(0);
        this.connState = ConnectionState.READY;
    }

    @Override
    public String getId() {
        return ioSession.getId();
    }

    void shutdownSession(final CloseMode closeMode) {
        if (closeMode == CloseMode.GRACEFUL) {
            connState = ConnectionState.GRACEFUL_SHUTDOWN;
            ioSession.enqueue(ShutdownCommand.GRACEFUL, Command.Priority.NORMAL);
        } else {
            connState = ConnectionState.SHUTDOWN;
            ioSession.close();
        }
    }

    void shutdownSession(final Exception cause) {
        connState = ConnectionState.SHUTDOWN;
        try {
            terminate(cause);
        } finally {
            final CloseMode closeMode;
            if (cause instanceof ConnectionClosedException) {
                closeMode = CloseMode.GRACEFUL;
            } else if (cause instanceof IOException) {
                closeMode = CloseMode.IMMEDIATE;
            } else {
                closeMode = CloseMode.GRACEFUL;
            }
            ioSession.close(closeMode);
        }
    }

    abstract void disconnected();

    abstract void terminate(final Exception exception);

    abstract void updateInputMetrics(IncomingMessage incomingMessage, BasicHttpConnectionMetrics connMetrics);

    abstract void updateOutputMetrics(OutgoingMessage outgoingMessage, BasicHttpConnectionMetrics connMetrics);

    abstract void consumeHeader(IncomingMessage messageHead, EntityDetails entityDetails) throws HttpException, IOException;

    abstract boolean handleIncomingMessage(IncomingMessage incomingMessage) throws HttpException;

    abstract boolean handleOutgoingMessage(OutgoingMessage outgoingMessage) throws HttpException;

    abstract ContentDecoder createContentDecoder(
            long contentLength,
            ReadableByteChannel channel,
            SessionInputBuffer buffer,
            BasicHttpTransportMetrics metrics) throws HttpException;

    abstract ContentEncoder createContentEncoder(
            long contentLength,
            WritableByteChannel channel,
            SessionOutputBuffer buffer,
            BasicHttpTransportMetrics metrics) throws HttpException;

    abstract void consumeData(ByteBuffer src) throws HttpException, IOException;

    abstract void updateCapacity(CapacityChannel capacityChannel) throws HttpException, IOException;

    abstract void dataEnd(List<? extends Header> trailers) throws HttpException, IOException;

    abstract boolean isOutputReady();

    abstract void produceOutput() throws HttpException, IOException;

    abstract void execute(RequestExecutionCommand executionCommand) throws HttpException, IOException;

    abstract void inputEnd() throws HttpException, IOException;

    abstract void outputEnd() throws HttpException, IOException;

    abstract boolean inputIdle();

    abstract boolean outputIdle();

    abstract boolean handleTimeout();

    private void processCommands() throws HttpException, IOException {
        for (;;) {
            final Command command = ioSession.poll();
            if (command == null) {
                return;
            }
            if (command instanceof ShutdownCommand) {
                final ShutdownCommand shutdownCommand = (ShutdownCommand) command;
                requestShutdown(shutdownCommand.getType());
            } else if (command instanceof RequestExecutionCommand) {
                if (connState.compareTo(ConnectionState.GRACEFUL_SHUTDOWN) >= 0) {
                    command.cancel();
                } else {
                    execute((RequestExecutionCommand) command);
                    return;
                }
            } else {
                throw new HttpException("Unexpected command: " + command.getClass());
            }
        }
    }

    public final void onConnect() throws HttpException, IOException {
        if (connState == ConnectionState.READY) {
            connState = ConnectionState.ACTIVE;
            processCommands();
        }
    }

    IncomingMessage parseMessageHead(final boolean endOfStream) throws IOException, HttpException {
        final IncomingMessage messageHead = incomingMessageParser.parse(inbuf, endOfStream);
        if (messageHead != null) {
            incomingMessageParser.reset();
        }
        return messageHead;
    }

    public final void onInput(final ByteBuffer src) throws HttpException, IOException {
        if (src != null) {
            inbuf.put(src);
        }

        if (connState.compareTo(ConnectionState.GRACEFUL_SHUTDOWN) >= 0 && inbuf.hasData() && inputIdle()) {
            ioSession.clearEvent(SelectionKey.OP_READ);
            return;
        }

        boolean endOfStream = false;
        if (incomingMessage == null) {
            final int bytesRead = inbuf.fill(ioSession);
            if (bytesRead > 0) {
                inTransportMetrics.incrementBytesTransferred(bytesRead);
            }
            endOfStream = bytesRead == -1;
        }

        do {
            if (incomingMessage == null) {

                final IncomingMessage messageHead = parseMessageHead(endOfStream);
                if (messageHead != null) {
                    this.version = messageHead.getVersion();

                    updateInputMetrics(messageHead, connMetrics);
                    final ContentDecoder contentDecoder;
                    if (handleIncomingMessage(messageHead)) {
                        final long len = incomingContentStrategy.determineLength(messageHead);
                        contentDecoder = createContentDecoder(len, ioSession, inbuf, inTransportMetrics);
                        consumeHeader(messageHead, contentDecoder != null ? new IncomingEntityDetails(messageHead, len) : null);
                    } else {
                        consumeHeader(messageHead, null);
                        contentDecoder = null;
                    }
                    capacityWindow = new CapacityWindow(http1Config.getInitialWindowSize(), ioSession);
                    if (contentDecoder != null) {
                        incomingMessage = new Message<>(messageHead, contentDecoder);
                    } else {
                        inputEnd();
                        if (connState.compareTo(ConnectionState.ACTIVE) == 0) {
                            ioSession.setEvent(SelectionKey.OP_READ);
                        }
                    }
                } else {
                    break;
                }
            }

            if (incomingMessage != null) {
                final ContentDecoder contentDecoder = incomingMessage.getBody();

                // At present the consumer can be forced to consume data
                // over its declared capacity in order to avoid having
                // unprocessed message body content stuck in the session
                // input buffer
                final int bytesRead = contentDecoder.read(contentBuffer);
                if (bytesRead > 0) {
                    contentBuffer.flip();
                    consumeData(contentBuffer);
                    contentBuffer.clear();
                    final int capacity = capacityWindow.removeCapacity(bytesRead);
                    if (capacity <= 0) {
                        if (!contentDecoder.isCompleted()) {
                            updateCapacity(capacityWindow);
                        }
                    }
                }
                if (contentDecoder.isCompleted()) {
                    dataEnd(contentDecoder.getTrailers());
                    capacityWindow.close();
                    incomingMessage = null;
                    ioSession.setEvent(SelectionKey.OP_READ);
                    inputEnd();
                }
                if (bytesRead == 0) {
                    break;
                }
            }
        } while (inbuf.hasData());

        if (endOfStream && !inbuf.hasData()) {
            if (outputIdle() && inputIdle()) {
                requestShutdown(CloseMode.GRACEFUL);
            } else {
                shutdownSession(new ConnectionClosedException("Connection closed by peer"));
            }
        }
    }

    public final void onOutput() throws IOException, HttpException {
        ioSession.getLock().lock();
        try {
            if (outbuf.hasData()) {
                final int bytesWritten = outbuf.flush(ioSession);
                if (bytesWritten > 0) {
                    outTransportMetrics.incrementBytesTransferred(bytesWritten);
                }
            }
        } finally {
            ioSession.getLock().unlock();
        }
        if (connState.compareTo(ConnectionState.SHUTDOWN) < 0) {
            final int pendingOutputRequests = outputRequests.get();
            produceOutput();
            final boolean outputPending = isOutputReady();
            final boolean outputEnd;
            ioSession.getLock().lock();
            try {
                if (!outputPending && !outbuf.hasData() && outputRequests.compareAndSet(pendingOutputRequests, 0)) {
                    ioSession.clearEvent(SelectionKey.OP_WRITE);
                } else {
                    outputRequests.addAndGet(-pendingOutputRequests);
                }
                outputEnd = outgoingMessage == null && !outbuf.hasData();
            } finally {
                ioSession.getLock().unlock();
            }
            if (outputEnd) {
                outputEnd();
                if (connState.compareTo(ConnectionState.ACTIVE) == 0) {
                    processCommands();
                } else if (connState.compareTo(ConnectionState.GRACEFUL_SHUTDOWN) >= 0 && inputIdle() && outputIdle()) {
                    connState = ConnectionState.SHUTDOWN;
                }
            }
        }
        if (connState.compareTo(ConnectionState.SHUTDOWN) >= 0) {
            ioSession.close();
        }
    }

    public final void onTimeout(final Timeout timeout) throws IOException, HttpException {
        if (!handleTimeout()) {
            onException(SocketTimeoutExceptionFactory.create(timeout));
        }
    }

    public final void onException(final Exception ex) {
        shutdownSession(ex);
        CommandSupport.failCommands(ioSession, ex);
    }

    public final void onDisconnect() {
        disconnected();
        CommandSupport.cancelCommands(ioSession);
    }

    void requestShutdown(final CloseMode closeMode) {
        switch (closeMode) {
            case GRACEFUL:
                if (connState == ConnectionState.ACTIVE) {
                    connState = ConnectionState.GRACEFUL_SHUTDOWN;
                }
                break;
            case IMMEDIATE:
                connState = ConnectionState.SHUTDOWN;
                break;
        }
        ioSession.setEvent(SelectionKey.OP_WRITE);
    }

    void commitMessageHead(
            final OutgoingMessage messageHead,
            final boolean endStream,
            final FlushMode flushMode) throws HttpException, IOException {
        ioSession.getLock().lock();
        try {
            outgoingMessageWriter.write(messageHead, outbuf);
            updateOutputMetrics(messageHead, connMetrics);
            if (!endStream) {
                final ContentEncoder contentEncoder;
                if (handleOutgoingMessage(messageHead)) {
                    final long len = outgoingContentStrategy.determineLength(messageHead);
                    contentEncoder = createContentEncoder(len, ioSession, outbuf, outTransportMetrics);
                } else {
                    contentEncoder = null;
                }
                if (contentEncoder != null) {
                    outgoingMessage = new Message<>(messageHead, contentEncoder);
                }
            }
            outgoingMessageWriter.reset();
            if (flushMode == FlushMode.IMMEDIATE) {
                outbuf.flush(ioSession);
            }
            ioSession.setEvent(EventMask.WRITE);
        } finally {
            ioSession.getLock().unlock();
        }
    }

    void requestSessionInput() {
        ioSession.setEvent(SelectionKey.OP_READ);
    }

    void requestSessionOutput() {
        outputRequests.incrementAndGet();
        ioSession.setEvent(SelectionKey.OP_WRITE);
    }

    Timeout getSessionTimeout() {
        return ioSession.getSocketTimeout();
    }

    void setSessionTimeout(final Timeout timeout) {
        ioSession.setSocketTimeout(timeout);
    }

    void suspendSessionInput() {
        ioSession.clearEvent(SelectionKey.OP_READ);
    }

    void suspendSessionOutput() throws IOException {
        ioSession.getLock().lock();
        try {
            if (outbuf.hasData()) {
                outbuf.flush(ioSession);
            } else {
                ioSession.clearEvent(SelectionKey.OP_WRITE);
            }
        } finally {
            ioSession.getLock().unlock();
        }
    }

    int streamOutput(final ByteBuffer src) throws IOException {
        ioSession.getLock().lock();
        try {
            if (outgoingMessage == null) {
                throw new ClosedChannelException();
            }
            final ContentEncoder contentEncoder = outgoingMessage.getBody();
            final int bytesWritten = contentEncoder.write(src);
            if (bytesWritten > 0) {
                ioSession.setEvent(SelectionKey.OP_WRITE);
            }
            return bytesWritten;
        } finally {
            ioSession.getLock().unlock();
        }
    }

    enum MessageDelineation { NONE, CHUNK_CODED, MESSAGE_HEAD}

    MessageDelineation endOutputStream(final List<? extends Header> trailers) throws IOException {
        ioSession.getLock().lock();
        try {
            if (outgoingMessage == null) {
                return MessageDelineation.NONE;
            }
            final ContentEncoder contentEncoder = outgoingMessage.getBody();
            contentEncoder.complete(trailers);
            ioSession.setEvent(SelectionKey.OP_WRITE);
            outgoingMessage = null;
            return contentEncoder instanceof ChunkEncoder
                            ? MessageDelineation.CHUNK_CODED
                            : MessageDelineation.MESSAGE_HEAD;
        } finally {
            ioSession.getLock().unlock();
        }
    }

    boolean isOutputCompleted() {
        ioSession.getLock().lock();
        try {
            if (outgoingMessage == null) {
                return true;
            }
            final ContentEncoder contentEncoder = outgoingMessage.getBody();
            return contentEncoder.isCompleted();
        } finally {
            ioSession.getLock().unlock();
        }
    }

    @Override
    public void close() throws IOException {
        ioSession.enqueue(ShutdownCommand.GRACEFUL, Command.Priority.NORMAL);
    }

    @Override
    public void close(final CloseMode closeMode) {
        ioSession.enqueue(new ShutdownCommand(closeMode), Command.Priority.IMMEDIATE);
    }

    @Override
    public boolean isOpen() {
        return connState == ConnectionState.ACTIVE;
    }

    @Override
    public Timeout getSocketTimeout() {
        return ioSession.getSocketTimeout();
    }

    @Override
    public void setSocketTimeout(final Timeout timeout) {
        ioSession.setSocketTimeout(timeout);
    }

    @Override
    public EndpointDetails getEndpointDetails() {
        if (endpointDetails == null) {
            endpointDetails = new BasicEndpointDetails(
                    ioSession.getRemoteAddress(),
                    ioSession.getLocalAddress(),
                    connMetrics,
                    ioSession.getSocketTimeout());
        }
        return endpointDetails;
    }

    @Override
    public ProtocolVersion getProtocolVersion() {
        return version;
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return ioSession.getRemoteAddress();
    }

    @Override
    public SocketAddress getLocalAddress() {
        return ioSession.getLocalAddress();
    }

    @Override
    public SSLSession getSSLSession() {
        final TlsDetails tlsDetails = ioSession.getTlsDetails();
        return tlsDetails != null ? tlsDetails.getSSLSession() : null;
    }

    void appendState(final StringBuilder buf) {
        buf.append("connState=").append(connState)
                .append(", inbuf=").append(inbuf)
                .append(", outbuf=").append(outbuf)
                .append(", inputWindow=").append(capacityWindow != null ? capacityWindow.getWindow() : 0);
    }

    static class CapacityWindow implements CapacityChannel {
        private final IOSession ioSession;
        private final Object lock;
        private int window;
        private boolean closed;

        CapacityWindow(final int window, final IOSession ioSession) {
            this.window = window;
            this.ioSession = ioSession;
            this.lock = new Object();
        }

        @Override
        public void update(final int increment) throws IOException {
            synchronized (lock) {
                if (closed) {
                    return;
                }
                if (increment > 0) {
                    updateWindow(increment);
                    ioSession.setEvent(SelectionKey.OP_READ);
                }
            }
        }

        /**
         * Internal method for removing capacity. We don't need to check
         * if this channel is closed in it.
         */
        int removeCapacity(final int delta) {
            synchronized (lock) {
                updateWindow(-delta);
                if (window <= 0) {
                    ioSession.clearEvent(SelectionKey.OP_READ);
                }
                return window;
            }
        }

        private void updateWindow(final int delta) {
            int newValue = window + delta;
            // Math.addExact
            if (((window ^ newValue) & (delta ^ newValue)) < 0) {
                newValue = delta < 0 ? Integer.MIN_VALUE : Integer.MAX_VALUE;
            }
            window = newValue;
        }

        /**
         * Closes the capacity channel, preventing user code from accidentally requesting
         * read events outside of the context of the request the channel was created for
         */
        void close() {
            synchronized (lock) {
                closed = true;
            }
        }

        // visible for testing
        int getWindow() {
            return window;
        }
    }
}
