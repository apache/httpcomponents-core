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
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.WritableByteChannel;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;

import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpMessage;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.impl.CharCodingSupport;
import org.apache.hc.core5.http.config.H1Config;
import org.apache.hc.core5.http.impl.BasicEndpointDetails;
import org.apache.hc.core5.http.impl.BasicHttpConnectionMetrics;
import org.apache.hc.core5.http.impl.BasicHttpTransportMetrics;
import org.apache.hc.core5.http.impl.ConnectionListener;
import org.apache.hc.core5.http.nio.ContentDecoder;
import org.apache.hc.core5.http.nio.ContentEncoder;
import org.apache.hc.core5.http.nio.NHttpMessageParser;
import org.apache.hc.core5.http.nio.NHttpMessageWriter;
import org.apache.hc.core5.http.nio.ResourceHolder;
import org.apache.hc.core5.http.nio.SessionInputBuffer;
import org.apache.hc.core5.http.nio.SessionOutputBuffer;
import org.apache.hc.core5.http.nio.command.ExecutionCommand;
import org.apache.hc.core5.http.nio.command.ShutdownCommand;
import org.apache.hc.core5.http.nio.command.ShutdownType;
import org.apache.hc.core5.net.InetAddressUtils;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.EventMask;
import org.apache.hc.core5.reactor.IOEventHandler;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.ssl.SSLBufferManagement;
import org.apache.hc.core5.reactor.ssl.SSLSessionInitializer;
import org.apache.hc.core5.reactor.ssl.SSLSessionVerifier;
import org.apache.hc.core5.reactor.ssl.TransportSecurityLayer;
import org.apache.hc.core5.util.Args;

abstract class AbstractHttp1StreamDuplexer<IncomingMessage extends HttpMessage, OutgoingMessage extends HttpMessage>
        implements ResourceHolder, UpgradeableHttpConnection {

    private enum ConnectionState { READY, ACTIVE, GRACEFUL_SHUTDOWN, SHUTDOWN}

    private final IOSession ioSession;
    private final H1Config h1Config;
    private final SessionInputBufferImpl inbuf;
    private final SessionOutputBufferImpl outbuf;
    private final BasicHttpTransportMetrics inTransportMetrics;
    private final BasicHttpTransportMetrics outTransportMetrics;
    private final BasicHttpConnectionMetrics connMetrics;
    private final NHttpMessageParser<IncomingMessage> incomingMessageParser;
    private final NHttpMessageWriter<OutgoingMessage> outgoingMessageWriter;
    private final ConnectionListener connectionListener;
    private final Lock outputLock;
    private final AtomicInteger outputRequests;

    private volatile Message<IncomingMessage, ContentDecoder> incomingMessage;
    private volatile Message<OutgoingMessage, ContentEncoder> outgoingMessage;
    private volatile ConnectionState connState = ConnectionState.READY;

    private volatile ProtocolVersion version;

    AbstractHttp1StreamDuplexer(
            final IOSession ioSession,
            final H1Config h1Config,
            final CharCodingConfig charCodingConfig,
            final NHttpMessageParser<IncomingMessage> incomingMessageParser,
            final NHttpMessageWriter<OutgoingMessage> outgoingMessageWriter,
            final ConnectionListener connectionListener) {
        this.ioSession = Args.notNull(ioSession, "I/O session");
        this.h1Config = h1Config != null ? h1Config : H1Config.DEFAULT;
        final int bufferSize = this.h1Config.getBufferSize();
        this.inbuf = new SessionInputBufferImpl(bufferSize, bufferSize < 512 ? bufferSize : 512,
                this.h1Config.getMaxLineLength(),
                CharCodingSupport.createDecoder(charCodingConfig));
        this.outbuf = new SessionOutputBufferImpl(bufferSize, bufferSize < 512 ? bufferSize : 512,
                CharCodingSupport.createEncoder(charCodingConfig));
        this.inTransportMetrics = new BasicHttpTransportMetrics();
        this.outTransportMetrics = new BasicHttpTransportMetrics();
        this.connMetrics = new BasicHttpConnectionMetrics(inTransportMetrics, outTransportMetrics);
        this.incomingMessageParser = incomingMessageParser;
        this.outgoingMessageWriter = outgoingMessageWriter;
        this.connectionListener = connectionListener;
        this.outputLock = new ReentrantLock();
        this.outputRequests = new AtomicInteger(0);
        this.connState = ConnectionState.READY;
    }

    void doTerminate(final Exception exception) {
        connState = ConnectionState.SHUTDOWN;
        try {
            terminate(exception);
        } finally {
            ioSession.close();
        }
    }

    abstract void terminate(final Exception exception);

    abstract void updateInputMetrics(IncomingMessage incomingMessage, BasicHttpConnectionMetrics connMetrics);

    abstract void updateOutputMetrics(OutgoingMessage outgoingMessage, BasicHttpConnectionMetrics connMetrics);

    abstract void consumeHeader(IncomingMessage messageHead, boolean endStream) throws HttpException, IOException;

    abstract ContentDecoder handleIncomingMessage(
            IncomingMessage incomingMessage,
            ReadableByteChannel channel,
            SessionInputBuffer buffer,
            BasicHttpTransportMetrics metrics) throws HttpException;

    abstract ContentEncoder handleOutgoingMessage(
            OutgoingMessage outgoingMessage,
            WritableByteChannel channel,
            SessionOutputBuffer buffer,
            BasicHttpTransportMetrics metrics) throws HttpException;

    abstract int consumeData(ContentDecoder contentDecoder) throws HttpException, IOException;

    abstract boolean isOutputReady();

    abstract void produceOutput() throws HttpException, IOException;

    abstract void execute(ExecutionCommand executionCommand) throws HttpException, IOException;

    abstract void inputEnd() throws HttpException, IOException;

    abstract void outputEnd() throws HttpException, IOException;

    abstract boolean inputIdle();

    abstract boolean outputIdle();

    abstract boolean handleTimeout();

    private void processCommands() throws HttpException, IOException {
        for (;;) {
            final Command command = ioSession.getCommand();
            if (command == null) {
                return;
            }
            if (command instanceof ShutdownCommand) {
                final ShutdownCommand shutdownCommand = (ShutdownCommand) command;
                requestShutdown(shutdownCommand.getType());
            } else if (command instanceof ExecutionCommand) {
                if (connState.compareTo(ConnectionState.GRACEFUL_SHUTDOWN) >= 0) {
                    command.cancel();
                } else {
                    execute((ExecutionCommand) command);
                    return;
                }
            } else {
                throw new HttpException("Unexpected command: " + command.getClass());
            }
        }
    }

    public final void onConnect() throws HttpException, IOException {
        if (connectionListener != null) {
            connectionListener.onConnect(this);
        }
        connState = ConnectionState.ACTIVE;
        processCommands();
    }

    public final void onInput() throws HttpException, IOException {
        while (connState.compareTo(ConnectionState.SHUTDOWN) < 0) {
            int totalBytesRead = 0;
            int messagesReceived = 0;
            if (incomingMessage == null) {

                if (connState.compareTo(ConnectionState.GRACEFUL_SHUTDOWN) >= 0 && inputIdle()) {
                    ioSession.clearEvent(SelectionKey.OP_READ);
                    return;
                }

                int bytesRead;
                do {
                    bytesRead = inbuf.fill(ioSession.channel());
                    if (bytesRead > 0) {
                        totalBytesRead += bytesRead;
                        inTransportMetrics.incrementBytesTransferred(bytesRead);
                    }
                    final IncomingMessage messageHead = incomingMessageParser.parse(inbuf, bytesRead == -1);
                    if (messageHead != null) {
                        messagesReceived++;
                        incomingMessageParser.reset();

                        this.version = messageHead.getVersion();

                        updateInputMetrics(messageHead, connMetrics);
                        final ContentDecoder contentDecoder = handleIncomingMessage(messageHead, ioSession.channel(), inbuf, inTransportMetrics);
                        consumeHeader(messageHead, contentDecoder == null);
                        if (contentDecoder != null) {
                            incomingMessage = new Message<>(messageHead, contentDecoder);
                            break;
                        } else {
                            inputEnd();
                            ioSession.setEvent(SelectionKey.OP_READ);
                        }
                    }
                } while (bytesRead > 0);

                if (bytesRead == -1 && !inbuf.hasData()) {
                    if (incomingMessage == null && outgoingMessage == null) {
                        requestShutdown(ShutdownType.IMMEDIATE);
                    } else {
                        doTerminate(new ConnectionClosedException("Connection closed by peer"));
                    }
                    return;
                }
            }

            if (incomingMessage != null) {
                final ContentDecoder contentDecoder = incomingMessage.getBody();
                final int bytesRead = consumeData(contentDecoder);
                if (bytesRead > 0) {
                    totalBytesRead += bytesRead;
                }
                if (contentDecoder.isCompleted()) {
                    incomingMessage = null;
                    inputEnd();
                    ioSession.setEvent(SelectionKey.OP_READ);
                }
            }
            if (totalBytesRead == 0 && messagesReceived == 0) {
                break;
            }
        }
    }

    public final void onOutput() throws IOException, HttpException {
        outputLock.lock();
        try {
            if (outbuf.hasData()) {
                final int bytesWritten = outbuf.flush(ioSession.channel());
                if (bytesWritten > 0) {
                    outTransportMetrics.incrementBytesTransferred(bytesWritten);
                }
            }
        } finally {
            outputLock.unlock();
        }
        if (connState.compareTo(ConnectionState.SHUTDOWN) < 0) {
            if (isOutputReady()) {
                produceOutput();
            } else {
                final int pendingOutputRequests = outputRequests.get();
                final boolean outputPending;
                outputLock.lock();
                try {
                    outputPending = outbuf.hasData();
                } finally {
                    outputLock.unlock();
                }
                if (!outputPending && outputRequests.compareAndSet(pendingOutputRequests, 0)) {
                    ioSession.clearEvent(SelectionKey.OP_WRITE);
                } else {
                    outputRequests.addAndGet(-pendingOutputRequests);
                }
            }

            outputLock.lock();
            final boolean outputEnd;
            try {
                outputEnd = outgoingMessage == null && !outbuf.hasData();
            } finally {
                outputLock.unlock();
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
            cancelPendingCommands();
            releaseResources();
        }
    }

    public final void onTimeout() throws IOException, HttpException {
        if (!handleTimeout()) {
            doTerminate(new SocketTimeoutException());
        }
    }

    public final void onException(final Exception ex) {
        doTerminate(ex);
        if (connectionListener != null) {
            connectionListener.onError(this, ex);
        }
    }

    public final void onDisconnect() {
        cancelPendingCommands();
        releaseResources();
        if (connectionListener != null) {
            connectionListener.onDisconnect(this);
        }
    }

    private void cancelPendingCommands() {
        for (;;) {
            final Command command = ioSession.getCommand();
            if (command != null) {
                command.cancel();
            } else {
                break;
            }
        }
    }

    void requestShutdown(final ShutdownType shutdownType) {
        switch (shutdownType) {
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

    void commitMessageHead(final OutgoingMessage messageHead, final boolean endStream) throws HttpException, IOException {
        outputLock.lock();
        try {
            outgoingMessageWriter.write(messageHead, outbuf);
            updateOutputMetrics(messageHead, connMetrics);
            if (!endStream) {
                final ContentEncoder contentEncoder = handleOutgoingMessage(messageHead, ioSession.channel(), outbuf, outTransportMetrics);
                if (contentEncoder != null) {
                    outgoingMessage = new Message<>(messageHead, contentEncoder);
                }
            }
            outgoingMessageWriter.reset();
            ioSession.setEvent(EventMask.WRITE);
        } finally {
            outputLock.unlock();
        }
    }

    void requestSessionInput() {
        ioSession.setEvent(SelectionKey.OP_READ);
    }

    void suspendSessionInput() {
        ioSession.clearEvent(SelectionKey.OP_READ);
    }

    void requestSessionOutput() {
        outputRequests.incrementAndGet();
        ioSession.setEvent(SelectionKey.OP_WRITE);
    }

    int getSessionTimeout() {
        return ioSession.getSocketTimeout();
    }

    void setSessionTimeout(final int timeout) {
        ioSession.setSocketTimeout(timeout);
    }

    void suspendSessionOutput() {
        ioSession.clearEvent(SelectionKey.OP_WRITE);
    }

    int streamOutput(final ByteBuffer src) throws IOException {
        outputLock.lock();
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
            outputLock.unlock();
        }
    }

    enum MessageDelineation { NONE, CHUNK_CODED, MESSAGE_HEAD}

    MessageDelineation endOutputStream(final List<? extends Header> trailers) throws IOException {
        outputLock.lock();
        try {
            if (outgoingMessage == null) {
                return MessageDelineation.NONE;
            }
            final ContentEncoder contentEncoder = outgoingMessage.getBody();
            contentEncoder.complete(trailers);
            ioSession.setEvent(SelectionKey.OP_WRITE);
            outgoingMessage = null;
            if (contentEncoder instanceof ChunkEncoder) {
                return MessageDelineation.CHUNK_CODED;
            } else {
                return MessageDelineation.MESSAGE_HEAD;
            }
        } finally {
            outputLock.unlock();
        }
    }

    boolean isOutputCompleted() {
        outputLock.lock();
        try {
            if (outgoingMessage == null) {
                return true;
            }
            final ContentEncoder contentEncoder = outgoingMessage.getBody();
            return contentEncoder.isCompleted();
        } finally {
            outputLock.unlock();
        }
    }

    @Override
    public void close() throws IOException {
        ioSession.addFirst(new ShutdownCommand(ShutdownType.GRACEFUL));
    }

    @Override
    public void shutdown() throws IOException {
        ioSession.addFirst(new ShutdownCommand(ShutdownType.IMMEDIATE));
    }

    @Override
    public boolean isOpen() {
        return connState == ConnectionState.ACTIVE;
    }

    @Override
    public void setSocketTimeout(final int timeout) {
        ioSession.setSocketTimeout(timeout);
    }

    @Override
    public EndpointDetails getEndpointDetails() {
        return new BasicEndpointDetails(ioSession.getRemoteAddress(), ioSession.getLocalAddress(), connMetrics);
    }

    @Override
    public int getSocketTimeout() {
        return ioSession.getSocketTimeout();
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
        if (ioSession instanceof TransportSecurityLayer) {
            return ((TransportSecurityLayer) ioSession).getSSLSession();
        } else {
            return null;
        }
    }

    @Override
    public void start(
            final SSLContext sslContext,
            final SSLBufferManagement sslBufferManagement,
            final SSLSessionInitializer initializer,
            final SSLSessionVerifier verifier) throws UnsupportedOperationException {
        if (ioSession instanceof TransportSecurityLayer) {
            ((TransportSecurityLayer) ioSession).start(sslContext, sslBufferManagement, initializer, verifier);
        } else {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public void upgrade(final IOEventHandler eventHandler) {
        ioSession.setHandler(eventHandler);
    }

    @Override
    public String toString() {
        final SocketAddress remoteAddress = ioSession.getRemoteAddress();
        final SocketAddress localAddress = ioSession.getLocalAddress();
        final StringBuilder buffer = new StringBuilder();
        InetAddressUtils.formatAddress(buffer, localAddress);
        buffer.append("->");
        InetAddressUtils.formatAddress(buffer, remoteAddress);
        return buffer.toString();
    }

}
