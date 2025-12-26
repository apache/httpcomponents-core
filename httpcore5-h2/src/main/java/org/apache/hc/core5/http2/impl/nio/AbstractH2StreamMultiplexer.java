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
import java.net.SocketAddress;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.charset.StandardCharsets;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSession;

import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpStreamResetException;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.RequestNotExecutedException;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.impl.BasicEndpointDetails;
import org.apache.hc.core5.http.impl.BasicHttpConnectionMetrics;
import org.apache.hc.core5.http.impl.CharCodingSupport;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.AsyncPushProducer;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.nio.command.CommandSupport;
import org.apache.hc.core5.http.nio.command.RequestExecutionCommand;
import org.apache.hc.core5.http.nio.command.ShutdownCommand;
import org.apache.hc.core5.http.nio.command.StaleCheckCommand;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http2.H2ConnectionException;
import org.apache.hc.core5.http2.H2Error;
import org.apache.hc.core5.http2.H2StreamResetException;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.config.H2Param;
import org.apache.hc.core5.http2.config.H2Setting;
import org.apache.hc.core5.http2.frame.FrameFactory;
import org.apache.hc.core5.http2.frame.FrameFlag;
import org.apache.hc.core5.http2.frame.FrameType;
import org.apache.hc.core5.http2.frame.RawFrame;
import org.apache.hc.core5.http2.frame.StreamIdGenerator;
import org.apache.hc.core5.http2.hpack.HPackDecoder;
import org.apache.hc.core5.http2.hpack.HPackEncoder;
import org.apache.hc.core5.http2.impl.BasicH2TransportMetrics;
import org.apache.hc.core5.http2.nio.AsyncPingHandler;
import org.apache.hc.core5.http2.nio.command.PingCommand;
import org.apache.hc.core5.http2.nio.command.PushResponseCommand;
import org.apache.hc.core5.http2.priority.PriorityParamsParser;
import org.apache.hc.core5.http2.priority.PriorityValue;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.ByteArrayBuffer;
import org.apache.hc.core5.util.Identifiable;
import org.apache.hc.core5.util.Timeout;

abstract class AbstractH2StreamMultiplexer implements Identifiable, HttpConnection {

    private static final long CONNECTION_WINDOW_LOW_MARK = 10 * 1024 * 1024;

    enum ConnectionHandshake { READY, ACTIVE, GRACEFUL_SHUTDOWN, SHUTDOWN }
    enum SettingsHandshake { READY, TRANSMITTED, ACKED }

    private final ProtocolIOSession ioSession;
    private final FrameFactory frameFactory;
    private final HttpProcessor httpProcessor;
    private final H2Config localConfig;
    private final BasicH2TransportMetrics inputMetrics;
    private final BasicH2TransportMetrics outputMetrics;
    private final BasicHttpConnectionMetrics connMetrics;
    private final FrameInputBuffer inputBuffer;
    private final FrameOutputBuffer outputBuffer;
    private final Deque<RawFrame> outputQueue;
    private final HPackEncoder hPackEncoder;
    private final HPackDecoder hPackDecoder;
    private final H2Streams streams;
    private final Queue<AsyncPingHandler> pingHandlers;
    private final AtomicInteger connInputWindow;
    private final AtomicInteger connOutputWindow;
    private final AtomicInteger outputRequests;
    private final H2StreamListener streamListener;

    private ConnectionHandshake connState = ConnectionHandshake.READY;
    private SettingsHandshake localSettingState = SettingsHandshake.READY;
    private SettingsHandshake remoteSettingState = SettingsHandshake.READY;

    private int initInputWinSize;
    private int initOutputWinSize;
    private int lowMark;

    private volatile H2Config remoteConfig;

    private Continuation continuation;

    private EndpointDetails endpointDetails;
    private boolean goAwayReceived;

    private final Map<Integer, PriorityValue> priorities = new ConcurrentHashMap<>();
    private volatile boolean peerNoRfc7540Priorities;

    AbstractH2StreamMultiplexer(
            final ProtocolIOSession ioSession,
            final FrameFactory frameFactory,
            final StreamIdGenerator idGenerator,
            final HttpProcessor httpProcessor,
            final CharCodingConfig charCodingConfig,
            final H2Config h2Config,
            final H2StreamListener streamListener) {
        this.ioSession = Args.notNull(ioSession, "IO session");
        this.frameFactory = Args.notNull(frameFactory, "Frame factory");
        this.httpProcessor = Args.notNull(httpProcessor, "HTTP processor");
        this.streams = new H2Streams(idGenerator);
        this.localConfig = h2Config != null ? h2Config : H2Config.DEFAULT;
        this.inputMetrics = new BasicH2TransportMetrics();
        this.outputMetrics = new BasicH2TransportMetrics();
        this.connMetrics = new BasicHttpConnectionMetrics(this.inputMetrics, this.outputMetrics);
        this.inputBuffer = new FrameInputBuffer(this.inputMetrics, this.localConfig.getMaxFrameSize());
        this.outputBuffer = new FrameOutputBuffer(this.outputMetrics, this.localConfig.getMaxFrameSize());
        this.outputQueue = new ConcurrentLinkedDeque<>();
        this.pingHandlers = new ConcurrentLinkedQueue<>();
        this.outputRequests = new AtomicInteger(0);
        this.hPackEncoder = new HPackEncoder(H2Config.INIT.getHeaderTableSize(), CharCodingSupport.createEncoder(charCodingConfig));
        this.hPackDecoder = new HPackDecoder(H2Config.INIT.getHeaderTableSize(), CharCodingSupport.createDecoder(charCodingConfig));
        this.remoteConfig = H2Config.INIT;
        this.connInputWindow = new AtomicInteger(H2Config.INIT.getInitialWindowSize());
        this.connOutputWindow = new AtomicInteger(H2Config.INIT.getInitialWindowSize());

        this.initInputWinSize = H2Config.INIT.getInitialWindowSize();
        this.initOutputWinSize = H2Config.INIT.getInitialWindowSize();

        this.hPackDecoder.setMaxListSize(H2Config.INIT.getMaxHeaderListSize());

        this.lowMark = H2Config.INIT.getInitialWindowSize() / 2;
        this.streamListener = streamListener;
    }

    @Override
    public String getId() {
        return ioSession.getId();
    }

    BasicHttpConnectionMetrics getConnMetrics() {
        return connMetrics;
    }

    HttpProcessor getHttpProcessor() {
        return httpProcessor;
    }

    void submitCommand(final Command command) {
        ioSession.enqueue(command, Command.Priority.NORMAL);
    }

    abstract void validateSetting(H2Param param, int value) throws H2ConnectionException;

    abstract H2Setting[] generateSettings(H2Config localConfig);

    abstract void acceptHeaderFrame() throws H2ConnectionException;

    abstract void acceptPushRequest() throws H2ConnectionException;

    abstract void acceptPushFrame() throws H2ConnectionException;

    abstract H2StreamHandler incomingRequest(H2StreamChannel channel) throws IOException;

    abstract H2StreamHandler incomingPushPromise(H2StreamChannel channel,
                                                 HandlerFactory<AsyncPushConsumer> pushHandlerFactory) throws IOException;

    abstract H2StreamHandler outgoingRequest(H2StreamChannel channel,
                                             AsyncClientExchangeHandler exchangeHandler,
                                             HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
                                             HttpContext context) throws IOException;

    abstract H2StreamHandler outgoingPushPromise(H2StreamChannel channel,
                                                 AsyncPushProducer pushProducer) throws IOException;

    abstract boolean allowGracefulAbort(H2Stream stream);

    private int updateWindow(final AtomicInteger window, final int delta) throws ArithmeticException {
        for (;;) {
            final int current = window.get();
            final long newValue = (long) current + delta;
            if (Math.abs(newValue) > 0x7fffffffL) {
                throw new ArithmeticException("Update causes flow control window to exceed " + Integer.MAX_VALUE);
            }
            if (window.compareAndSet(current, (int) newValue)) {
                return (int) newValue;
            }
        }
    }

    private int updateWindowMax(final AtomicInteger window) throws ArithmeticException {
        for (;;) {
            final int current = window.get();
            if (window.compareAndSet(current, Integer.MAX_VALUE)) {
                return Integer.MAX_VALUE - current;
            }
        }
    }

    private int updateInputWindow(
            final int streamId, final AtomicInteger window, final int delta) throws ArithmeticException {
        final int newSize = updateWindow(window, delta);
        if (streamListener != null) {
            streamListener.onInputFlowControl(this, streamId, delta, newSize);
        }
        return newSize;
    }

    private int updateOutputWindow(
            final int streamId, final AtomicInteger window, final int delta) throws ArithmeticException {
        final int newSize = updateWindow(window, delta);
        if (streamListener != null) {
            streamListener.onOutputFlowControl(this, streamId, delta, newSize);
        }
        return newSize;
    }

    private void commitFrameInternal(final RawFrame frame) throws IOException {
        if (outputBuffer.isEmpty() && outputQueue.isEmpty()) {
            if (streamListener != null) {
                streamListener.onFrameOutput(this, frame.getStreamId(), frame);
            }
            outputBuffer.write(frame, ioSession);
        } else {
            outputQueue.addLast(frame);
        }
        ioSession.setEvent(SelectionKey.OP_WRITE);
    }

    private void commitFrame(final RawFrame frame) throws IOException {
        Args.notNull(frame, "Frame");
        ioSession.getLock().lock();
        try {
            commitFrameInternal(frame);
        } finally {
            ioSession.getLock().unlock();
        }
    }

    private void commitHeaders(
            final int streamId, final List<? extends Header> headers, final boolean endStream) throws IOException {
        if (streamListener != null) {
            streamListener.onHeaderOutput(this, streamId, headers);
        }
        final ByteArrayBuffer buf = new ByteArrayBuffer(512);
        hPackEncoder.encodeHeaders(buf, headers, localConfig.isCompressionEnabled());

        int off = 0;
        int remaining = buf.length();
        boolean continuation = false;

        while (remaining > 0) {
            final int chunk = Math.min(remoteConfig.getMaxFrameSize(), remaining);
            final ByteBuffer payload = ByteBuffer.wrap(buf.array(), off, chunk);

            remaining -= chunk;
            off += chunk;

            final boolean endHeaders = remaining == 0;
            final RawFrame frame;
            if (!continuation) {
                frame = frameFactory.createHeaders(streamId, payload, endHeaders, endStream);
                continuation = true;
            } else {
                frame = frameFactory.createContinuation(streamId, payload, endHeaders);
            }
            commitFrameInternal(frame);
        }
    }

    private void commitPushPromise(
            final int streamId, final int promisedStreamId, final List<Header> headers) throws IOException {
        if (headers == null || headers.isEmpty()) {
            throw new H2ConnectionException(H2Error.INTERNAL_ERROR, "Message headers are missing");
        }
        if (streamListener != null) {
            streamListener.onHeaderOutput(this, streamId, headers);
        }
        final ByteArrayBuffer buf = new ByteArrayBuffer(512);
        buf.append((byte)(promisedStreamId >> 24));
        buf.append((byte)(promisedStreamId >> 16));
        buf.append((byte)(promisedStreamId >> 8));
        buf.append((byte) promisedStreamId);

        hPackEncoder.encodeHeaders(buf, headers, localConfig.isCompressionEnabled());

        int off = 0;
        int remaining = buf.length();
        boolean continuation = false;

        while (remaining > 0) {
            final int chunk = Math.min(remoteConfig.getMaxFrameSize(), remaining);
            final ByteBuffer payload = ByteBuffer.wrap(buf.array(), off, chunk);

            remaining -= chunk;
            off += chunk;

            final boolean endHeaders = remaining == 0;
            final RawFrame frame;
            if (!continuation) {
                frame = frameFactory.createPushPromise(streamId, payload, endHeaders);
                continuation = true;
            } else {
                frame = frameFactory.createContinuation(streamId, payload, endHeaders);
            }
            commitFrameInternal(frame);
        }
    }

    private void streamDataFrame(
            final int streamId,
            final AtomicInteger streamOutputWindow,
            final ByteBuffer payload,
            final int chunk) throws IOException {
        final RawFrame dataFrame = frameFactory.createData(streamId, payload, false);
        if (streamListener != null) {
            streamListener.onFrameOutput(this, streamId, dataFrame);
        }
        updateOutputWindow(0, connOutputWindow, -chunk);
        updateOutputWindow(streamId, streamOutputWindow, -chunk);
        outputBuffer.write(dataFrame, ioSession);
    }

    private int streamData(
            final int streamId, final AtomicInteger streamOutputWindow, final ByteBuffer payload) throws IOException {
        if (outputBuffer.isEmpty() && outputQueue.isEmpty()) {
            final int capacity = Math.min(connOutputWindow.get(), streamOutputWindow.get());
            if (capacity <= 0) {
                return 0;
            }
            final int maxPayloadSize = Math.min(capacity, outputBuffer.getMaxFramePayloadSize());
            final int chunk;
            if (payload.remaining() <= maxPayloadSize) {
                chunk = payload.remaining();
                streamDataFrame(streamId, streamOutputWindow, payload, chunk);
            } else {
                chunk = maxPayloadSize;
                final int originalLimit = payload.limit();
                try {
                    payload.limit(payload.position() + chunk);
                    streamDataFrame(streamId, streamOutputWindow, payload, chunk);
                } finally {
                    payload.limit(originalLimit);
                }
            }
            payload.position(payload.position() + chunk);
            ioSession.setEvent(SelectionKey.OP_WRITE);
            return chunk;
        }
        return 0;
    }

    private void incrementInputCapacity(
            final int streamId, final AtomicInteger inputWindow, final int inputCapacity) throws IOException {
        if (inputCapacity > 0) {
            final int streamWinSize = inputWindow.get();
            final int remainingCapacity = Integer.MAX_VALUE - streamWinSize;
            final int chunk = Math.min(inputCapacity, remainingCapacity);
            if (chunk != 0) {
                updateInputWindow(streamId, inputWindow, chunk);
                final RawFrame windowUpdateFrame = frameFactory.createWindowUpdate(streamId, chunk);
                commitFrame(windowUpdateFrame);
            }
        }
    }

    void requestSessionOutput() {
        outputRequests.incrementAndGet();
        ioSession.setEvent(SelectionKey.OP_WRITE);
    }

    public final void onConnect() throws HttpException, IOException {
        connState = ConnectionHandshake.ACTIVE;
        final RawFrame settingsFrame = frameFactory.createSettings(generateSettings(localConfig));

        commitFrame(settingsFrame);
        localSettingState = SettingsHandshake.TRANSMITTED;
        maximizeWindow(0, connInputWindow);

        if (streamListener != null) {
            final int initInputWindow = connInputWindow.get();
            streamListener.onInputFlowControl(this, 0, initInputWindow, initInputWindow);
            final int initOutputWindow = connOutputWindow.get();
            streamListener.onOutputFlowControl(this, 0, initOutputWindow, initOutputWindow);
        }
    }

    public final void onInput(final ByteBuffer src) throws HttpException, IOException {
        if (connState == ConnectionHandshake.SHUTDOWN) {
            ioSession.clearEvent(SelectionKey.OP_READ);
        } else {
            for (;;) {
                final RawFrame frame = inputBuffer.read(src, ioSession);
                if (frame != null) {
                    if (streamListener != null) {
                        streamListener.onFrameInput(this, frame.getStreamId(), frame);
                    }
                    consumeFrame(frame);
                } else {
                    if (inputBuffer.isEndOfStream()) {
                        if (connState == ConnectionHandshake.ACTIVE) {
                            final RawFrame goAway = frameFactory.createGoAway(streams.getLastRemoteId(), H2Error.NO_ERROR, "Unexpected end of stream");
                            commitFrame(goAway);
                        }
                        connState = ConnectionHandshake.SHUTDOWN;
                        requestSessionOutput();
                    }
                    break;
                }
            }
        }
    }

    public final void onOutput() throws HttpException, IOException {
        ioSession.getLock().lock();
        try {
            if (!outputBuffer.isEmpty()) {
                outputBuffer.flush(ioSession);
            }
            while (outputBuffer.isEmpty()) {
                final RawFrame frame = outputQueue.poll();
                if (frame != null) {
                    if (streamListener != null) {
                        streamListener.onFrameOutput(this, frame.getStreamId(), frame);
                    }
                    outputBuffer.write(frame, ioSession);
                } else {
                    break;
                }
            }
        } finally {
            ioSession.getLock().unlock();
        }

        if (connState.compareTo(ConnectionHandshake.SHUTDOWN) < 0) {

            if (connOutputWindow.get() > 0 && remoteSettingState == SettingsHandshake.ACKED) {
                produceOutput();
            }
            final int pendingOutputRequests = outputRequests.get();
            boolean outputPending = false;
            if (!streams.isEmpty() && connOutputWindow.get() > 0) {
                for (final Iterator<H2Stream> it = streams.iterator(); it.hasNext(); ) {
                    final H2Stream stream = it.next();
                    if (!stream.isLocalClosed()
                            && stream.getOutputWindow().get() > 0
                            && stream.isOutputReady()) {
                        outputPending = true;
                        break;
                    }
                }
            }
            ioSession.getLock().lock();
            try {
                if (!outputPending && outputBuffer.isEmpty() && outputQueue.isEmpty()
                        && outputRequests.compareAndSet(pendingOutputRequests, 0)) {
                    ioSession.clearEvent(SelectionKey.OP_WRITE);
                } else {
                    outputRequests.addAndGet(-pendingOutputRequests);
                }
            } finally {
                ioSession.getLock().unlock();
            }
        }

        if (connState.compareTo(ConnectionHandshake.ACTIVE) <= 0 && remoteSettingState == SettingsHandshake.ACKED) {
            while (streams.getLocalCount() < remoteConfig.getMaxConcurrentStreams()) {
                final Command command = ioSession.poll();
                if (command == null) {
                    break;
                }
                if (command instanceof ShutdownCommand) {
                    executeShutdown((ShutdownCommand) command);
                } else if (command instanceof PingCommand) {
                    executePing((PingCommand) command);
                } else if (command instanceof RequestExecutionCommand) {
                    executeRequest((RequestExecutionCommand) command);
                } else if (command instanceof PushResponseCommand) {
                    executePush((PushResponseCommand) command);
                } else if (command instanceof StaleCheckCommand) {
                    executeStaleCheck((StaleCheckCommand) command);
                }
                if (!outputQueue.isEmpty()) {
                    return;
                }
            }
        }
        if (connState.compareTo(ConnectionHandshake.GRACEFUL_SHUTDOWN) == 0) {
            int liveStreams = 0;
            for (final Iterator<H2Stream> it = streams.iterator(); it.hasNext(); ) {
                final H2Stream stream = it.next();
                if (stream.isClosedPastLingerDeadline()) {
                    streams.dropStreamId(stream.getId());
                    it.remove();
                } else {
                    if (streams.isSameSide(stream.getId()) || stream.getId() <= streams.getLastRemoteId()) {
                        liveStreams++;
                    }
                }
            }
            if (liveStreams == 0) {
                connState = ConnectionHandshake.SHUTDOWN;
            }
        }
        if (connState.compareTo(ConnectionHandshake.GRACEFUL_SHUTDOWN) >= 0) {
            for (;;) {
                final Command command = ioSession.poll();
                if (command == null) {
                    break;
                }
                if (command instanceof ShutdownCommand) {
                    final ShutdownCommand shutdownCommand = (ShutdownCommand) command;
                    if (shutdownCommand.getType() == CloseMode.IMMEDIATE) {
                        connState = ConnectionHandshake.SHUTDOWN;
                    }
                } else {
                    command.cancel();
                }
            }
        }
        if (connState.compareTo(ConnectionHandshake.SHUTDOWN) >= 0) {
            streams.shutdownAndReleaseAll();
            ioSession.getLock().lock();
            try {
                if (outputBuffer.isEmpty() && outputQueue.isEmpty()) {
                    ioSession.close();
                }
            } finally {
                ioSession.getLock().unlock();
            }
        }
    }

    public final void onTimeout(final Timeout timeout) throws HttpException, IOException {
        connState = ConnectionHandshake.SHUTDOWN;

        final RawFrame goAway;
        if (localSettingState != SettingsHandshake.ACKED) {
            goAway = frameFactory.createGoAway(streams.getLastRemoteId(), H2Error.SETTINGS_TIMEOUT,
                    "Setting timeout (" + timeout + ")");
        } else {
            goAway = frameFactory.createGoAway(streams.getLastRemoteId(), H2Error.NO_ERROR,
                    "Timeout due to inactivity (" + timeout + ")");
        }
        commitFrame(goAway);
        for (final Iterator<H2Stream> it = streams.iterator(); it.hasNext(); ) {
            final H2Stream stream = it.next();
            stream.fail(new H2StreamResetException(H2Error.NO_ERROR, "Timeout due to inactivity (" + timeout + ")"));
        }
        streams.shutdownAndReleaseAll();
    }

    public final void onDisconnect() {
        for (;;) {
            final AsyncPingHandler pingHandler = pingHandlers.poll();
            if (pingHandler != null) {
                pingHandler.cancel();
            } else {
                break;
            }
        }
        streams.shutdownAndReleaseAll();
        CommandSupport.cancelCommands(ioSession);
    }

    private void executeShutdown(final ShutdownCommand shutdownCommand) throws IOException {
        if (shutdownCommand.getType() == CloseMode.IMMEDIATE) {
            streams.shutdownAndReleaseAll();
            connState = ConnectionHandshake.SHUTDOWN;
        } else {
            if (connState.compareTo(ConnectionHandshake.ACTIVE) <= 0) {
                final RawFrame goAway = frameFactory.createGoAway(streams.getLastRemoteId(), H2Error.NO_ERROR, "Graceful shutdown");
                commitFrame(goAway);
                connState = streams.isEmpty() ? ConnectionHandshake.SHUTDOWN : ConnectionHandshake.GRACEFUL_SHUTDOWN;
            }
        }
    }

    private void executePing(final PingCommand pingCommand) throws IOException {
        final AsyncPingHandler handler = pingCommand.getHandler();
        pingHandlers.add(handler);
        final RawFrame ping = frameFactory.createPing(handler.getData());
        commitFrame(ping);
    }

    private void executeStaleCheck(final StaleCheckCommand staleCheckCommand) {
        final Consumer<Boolean> callback = staleCheckCommand.getCallback();
        callback.accept(ioSession.isOpen() &&
                connState.compareTo(ConnectionHandshake.ACTIVE) == 0);
    }

    private void executeRequest(final RequestExecutionCommand requestExecutionCommand) throws IOException, HttpException {
        final int streamId = streams.generateStreamId();
        final H2StreamChannel channel = createChannel(streamId);
        final H2Stream stream = streams.createActive(channel, outgoingRequest(channel,
                requestExecutionCommand.getExchangeHandler(),
                requestExecutionCommand.getPushHandlerFactory(),
                requestExecutionCommand.getContext()));

        if (streamListener != null) {
            final int initInputWindow = stream.getInputWindow().get();
            streamListener.onInputFlowControl(this, streamId, initInputWindow, initInputWindow);
            final int initOutputWindow = stream.getOutputWindow().get();
            streamListener.onOutputFlowControl(this, streamId, initOutputWindow, initOutputWindow);
        }
        requestExecutionCommand.initiated(stream);
        if (stream.isOutputReady()) {
            stream.produceOutput();
        }
    }

    private void executePush(final PushResponseCommand pushResponseCommand) throws IOException, HttpException {
        if (pushResponseCommand.isCancelled()) {
            return;
        }
        final H2Stream stream = streams.lookupSeen(pushResponseCommand.getStreamId());
        if (stream != null && stream.isReserved()) {
            if (!stream.isLocalClosed()) {
                stream.activate();
                if (stream.isOutputReady()) {
                    stream.produceOutput();
                }
            } else {
                stream.abort();
            }
        }
    }

    public final void onException(final Exception cause) {
        try {
            for (;;) {
                final AsyncPingHandler pingHandler = pingHandlers.poll();
                if (pingHandler != null) {
                    pingHandler.failed(cause);
                } else {
                    break;
                }
            }

            CommandSupport.cancelCommands(ioSession);
            streams.shutdownAndReleaseAll(cause);

            if (!(cause instanceof ConnectionClosedException)) {
                if (connState.compareTo(ConnectionHandshake.GRACEFUL_SHUTDOWN) <= 0) {
                    final H2Error errorCode;
                    if (cause instanceof H2ConnectionException) {
                        errorCode = H2Error.getByCode(((H2ConnectionException) cause).getCode());
                    } else if (cause instanceof ProtocolException) {
                        errorCode = H2Error.PROTOCOL_ERROR;
                    } else {
                        errorCode = H2Error.INTERNAL_ERROR;
                    }
                    final RawFrame goAway = frameFactory.createGoAway(streams.getLastRemoteId(), errorCode, cause.getMessage());
                    commitFrame(goAway);
                }
            }
        } catch (final IOException ignore) {
        } finally {
            connState = ConnectionHandshake.SHUTDOWN;
            final CloseMode closeMode;
            if (cause instanceof ConnectionClosedException) {
                closeMode = CloseMode.GRACEFUL;
            } else if (cause instanceof SSLHandshakeException) {
                closeMode = CloseMode.GRACEFUL;
            } else if (cause instanceof IOException) {
                closeMode = CloseMode.IMMEDIATE;
            } else {
                closeMode = CloseMode.GRACEFUL;
            }
            ioSession.close(closeMode);
        }
    }

    private void consumeFrame(final RawFrame frame) throws HttpException, IOException {
        final FrameType frameType = FrameType.valueOf(frame.getType());
        final int streamId = frame.getStreamId();
        if (continuation != null && frameType != FrameType.CONTINUATION) {
            throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "CONTINUATION frame expected");
        }
        switch (frameType) {
            case DATA: {
                if (streamId == 0) {
                    throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "Illegal stream id: " + streamId);
                }
                final H2Stream stream = streams.lookupValid(streamId);
                try {
                    consumeDataFrame(frame, stream);
                } catch (final H2StreamResetException ex) {
                    stream.localReset(ex);
                } catch (final HttpStreamResetException ex) {
                    stream.localReset(ex, ex.getCause() != null ? H2Error.INTERNAL_ERROR : H2Error.CANCEL);
                }

                if (stream.isClosed()) {
                    stream.releaseResources();
                    requestSessionOutput();
                }
            }
            break;
            case HEADERS: {
                if (streamId == 0) {
                    throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "Illegal stream id: " + streamId);
                }
                H2Stream stream = streams.lookupValidOrNull(streamId);
                if (stream == null) {
                    acceptHeaderFrame();
                    if (streams.isSameSide(streamId)) {
                        throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "Illegal stream id: " + streamId);
                    }
                    if (goAwayReceived ) {
                        throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "GOAWAY received");
                    }

                    final H2StreamChannel channel = createChannel(streamId);
                    if (connState.compareTo(ConnectionHandshake.ACTIVE) <= 0) {
                        stream = streams.createActive(channel, incomingRequest(channel));
                        streams.resetIfExceedsMaxConcurrentLimit(stream, localConfig.getMaxConcurrentStreams());
                    } else {
                        channel.localReset(H2Error.REFUSED_STREAM);
                        stream = streams.createActive(channel, NoopH2StreamHandler.INSTANCE);
                    }
                } else if (stream.isLocalClosed() && stream.isRemoteClosed()) {
                    throw new H2ConnectionException(H2Error.STREAM_CLOSED, "Stream closed");
                } else if (stream.isReserved()) {
                    stream.activate();
                    streams.resetIfExceedsMaxConcurrentLimit(stream, localConfig.getMaxConcurrentStreams());
                }
                try {
                    consumeHeaderFrame(frame, stream);
                    if (stream.isOutputReady()) {
                        stream.produceOutput();
                    }
                } catch (final H2StreamResetException ex) {
                    stream.localReset(ex);
                } catch (final HttpStreamResetException ex) {
                    stream.localReset(ex, ex.getCause() != null ? H2Error.INTERNAL_ERROR : H2Error.CANCEL);
                } catch (final HttpException ex) {
                    stream.handle(ex);
                }

                if (stream.isClosed()) {
                    stream.releaseResources();
                    requestSessionOutput();
                }
            }
            break;
            case CONTINUATION: {
                if (streamId == 0) {
                    throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "Illegal stream id: " + streamId);
                }
                if (continuation == null) {
                    throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "Unexpected CONTINUATION frame");
                }
                if (streamId != continuation.streamId) {
                    throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "Unexpected CONTINUATION stream id: " + streamId);
                }

                final H2Stream stream = streams.lookupValid(streamId);
                try {
                    consumeContinuationFrame(frame, stream);
                } catch (final H2StreamResetException ex) {
                    stream.localReset(ex);
                } catch (final HttpStreamResetException ex) {
                    stream.localReset(ex, ex.getCause() != null ? H2Error.INTERNAL_ERROR : H2Error.CANCEL);
                }

                if (stream.isClosed()) {
                    stream.releaseResources();
                    requestSessionOutput();
                }
            }
            break;
            case WINDOW_UPDATE: {
                final ByteBuffer payload = frame.getPayload();
                if (payload == null || payload.remaining() != 4) {
                    throw new H2ConnectionException(H2Error.FRAME_SIZE_ERROR, "Invalid WINDOW_UPDATE frame payload");
                }
                final int delta = payload.getInt();
                if (delta <= 0) {
                    throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "Invalid WINDOW_UPDATE delta");
                }
                if (streamId == 0) {
                    try {
                        updateOutputWindow(0, connOutputWindow, delta);
                    } catch (final ArithmeticException ex) {
                        throw new H2ConnectionException(H2Error.FLOW_CONTROL_ERROR, ex.getMessage());
                    }
                } else {
                    final H2Stream stream = streams.lookup(streamId);
                    if (stream != null) {
                        try {
                            updateOutputWindow(streamId, stream.getOutputWindow(), delta);
                        } catch (final ArithmeticException ex) {
                            throw new H2ConnectionException(H2Error.FLOW_CONTROL_ERROR, ex.getMessage());
                        }
                    }
                }
                ioSession.setEvent(SelectionKey.OP_WRITE);
            }
            break;
            case RST_STREAM: {
                if (streamId == 0) {
                    throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "Illegal stream id: " + streamId);
                }
                final H2Stream stream = streams.lookupSeen(streamId);
                if (stream != null) {
                    final ByteBuffer payload = frame.getPayload();
                    if (payload == null || payload.remaining() != 4) {
                        throw new H2ConnectionException(H2Error.FRAME_SIZE_ERROR, "Invalid RST_STREAM frame payload");
                    }
                    final int errorCode = payload.getInt();
                    if (errorCode == H2Error.NO_ERROR.getCode() && allowGracefulAbort(stream)) {
                        stream.abortGracefully();
                        requestSessionOutput();
                    } else {
                        stream.fail(new H2StreamResetException(errorCode, "Stream reset (" + errorCode + ")"));
                        requestSessionOutput();
                    }
                }
            }
            break;
            case PING: {
                if (streamId != 0) {
                    throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "Illegal stream id");
                }
                final ByteBuffer ping = frame.getPayloadContent();
                if (ping == null || ping.remaining() != 8) {
                    throw new H2ConnectionException(H2Error.FRAME_SIZE_ERROR, "Invalid PING frame payload");
                }
                if (frame.isFlagSet(FrameFlag.ACK)) {
                    final AsyncPingHandler pingHandler = pingHandlers.poll();
                    if (pingHandler != null) {
                        pingHandler.consumeResponse(ping);
                    }
                } else {
                    final ByteBuffer pong = ByteBuffer.allocate(ping.remaining());
                    pong.put(ping);
                    pong.flip();
                    final RawFrame response = frameFactory.createPingAck(pong);
                    commitFrame(response);
                }
            }
            break;
            case SETTINGS: {
                if (streamId != 0) {
                    throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "Illegal stream id");
                }
                if (frame.isFlagSet(FrameFlag.ACK)) {
                    if (localSettingState == SettingsHandshake.TRANSMITTED) {
                        localSettingState = SettingsHandshake.ACKED;
                        ioSession.setEvent(SelectionKey.OP_WRITE);
                        applyLocalSettings();
                    }
                } else {
                    final ByteBuffer payload = frame.getPayload();
                    if (payload != null) {
                        if ((payload.remaining() % 6) != 0) {
                            throw new H2ConnectionException(H2Error.FRAME_SIZE_ERROR, "Invalid SETTINGS payload");
                        }
                        consumeSettingsFrame(payload);
                        remoteSettingState = SettingsHandshake.TRANSMITTED;
                    }
                    final RawFrame response = frameFactory.createSettingsAck();
                    commitFrame(response);
                    remoteSettingState = SettingsHandshake.ACKED;
                }
            }
            break;
            case PRIORITY:
                break;
            case PUSH_PROMISE: {
                acceptPushFrame();
                if (streamId == 0) {
                    throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "Illegal stream id: " + streamId);
                }

                if (goAwayReceived ) {
                    throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "GOAWAY received");
                }

                if (!localConfig.isPushEnabled()) {
                    throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "Push is disabled");
                }

                final H2Stream stream = streams.lookupValid(streamId);
                if (stream.isRemoteClosed()) {
                    stream.localReset(new H2StreamResetException(H2Error.STREAM_CLOSED, "Stream closed"));
                    break;
                }

                final ByteBuffer payload = frame.getPayloadContent();
                if (payload == null || payload.remaining() < 4) {
                    throw new H2ConnectionException(H2Error.FRAME_SIZE_ERROR, "Invalid PUSH_PROMISE payload");
                }
                final int promisedStreamId = payload.getInt();
                if (promisedStreamId == 0 || streams.isSameSide(promisedStreamId)) {
                    throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "Illegal promised stream id: " + promisedStreamId);
                }
                if (streams.lookupValidOrNull(promisedStreamId) != null) {
                    throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "Stream already open: " + promisedStreamId);
                }

                final H2StreamChannel channel = createChannel(promisedStreamId);
                final H2Stream promisedStream;
                if (connState.compareTo(ConnectionHandshake.ACTIVE) <= 0) {
                    promisedStream = streams.createReserved(channel, incomingPushPromise(channel, stream.getPushHandlerFactory()));
                } else {
                    channel.localReset(H2Error.REFUSED_STREAM);
                    promisedStream = streams.createActive(channel, NoopH2StreamHandler.INSTANCE);
                }
                try {
                    consumePushPromiseFrame(frame, payload, promisedStream);
                } catch (final H2StreamResetException ex) {
                    promisedStream.localReset(ex);
                } catch (final HttpStreamResetException ex) {
                    promisedStream.localReset(ex, ex.getCause() != null ? H2Error.INTERNAL_ERROR : H2Error.NO_ERROR);
                }
            }
            break;
            case GOAWAY: {
                if (streamId != 0) {
                    throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "Illegal stream id");
                }
                final ByteBuffer payload = frame.getPayload();
                if (payload == null || payload.remaining() < 8) {
                    throw new H2ConnectionException(H2Error.FRAME_SIZE_ERROR, "Invalid GOAWAY payload");
                }
                final int processedLocalStreamId = payload.getInt();
                final int errorCode = payload.getInt();
                goAwayReceived = true;
                if (errorCode == H2Error.NO_ERROR.getCode()) {
                    if (connState.compareTo(ConnectionHandshake.ACTIVE) <= 0) {
                        for (final Iterator<H2Stream> it = streams.iterator(); it.hasNext(); ) {
                            final H2Stream stream = it.next();
                            final int activeStreamId = stream.getId();
                            if (!streams.isSameSide(activeStreamId) && activeStreamId > processedLocalStreamId) {
                                stream.fail(new RequestNotExecutedException());
                                it.remove();
                            }
                        }
                    }
                    connState = streams.isEmpty() ? ConnectionHandshake.SHUTDOWN : ConnectionHandshake.GRACEFUL_SHUTDOWN;
                } else {
                    for (final Iterator<H2Stream> it = streams.iterator(); it.hasNext(); ) {
                        final H2Stream stream = it.next();
                        stream.fail(new H2StreamResetException(errorCode, "Connection terminated by the peer (" + errorCode + ")"));
                    }
                    streams.shutdownAndReleaseAll();
                    connState = ConnectionHandshake.SHUTDOWN;
                }
            }
            ioSession.setEvent(SelectionKey.OP_WRITE);
            break;
            case PRIORITY_UPDATE: {
                if (streamId != 0) {
                    throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "PRIORITY_UPDATE must be on stream 0");
                }
                final ByteBuffer payload = frame.getPayload();
                if (payload == null || payload.remaining() < 4) {
                    throw new H2ConnectionException(H2Error.FRAME_SIZE_ERROR, "Invalid PRIORITY_UPDATE payload");
                }
                final int prioritizedId = payload.getInt() & 0x7fffffff;
                final int len = payload.remaining();
                final String field;
                if (len > 0) {
                    final byte[] b = new byte[len];
                    payload.get(b);
                    field = new String(b, StandardCharsets.US_ASCII);
                } else {
                    field = "";
                }
                final PriorityValue pv = PriorityParamsParser.parse(field).toValueWithDefaults();
                priorities.put(prioritizedId, pv);
                requestSessionOutput();
            }
            break;
        }
    }

    private void consumeDataFrame(final RawFrame frame, final H2Stream stream) throws HttpException, IOException {
        if (stream.isRemoteClosed()) {
            throw new H2StreamResetException(H2Error.STREAM_CLOSED, "Stream already closed");
        }
        final int streamId = stream.getId();
        final ByteBuffer payload = frame.getPayloadContent();
        if (payload != null) {
            final int frameLength = frame.getLength();
            final int streamWinSize = updateInputWindow(streamId, stream.getInputWindow(), -frameLength);
            if (streamWinSize < lowMark && !stream.isRemoteClosed()) {
                stream.produceInputCapacityUpdate();
            }
            final int connWinSize = updateInputWindow(0, connInputWindow, -frameLength);
            if (connWinSize < CONNECTION_WINDOW_LOW_MARK) {
                maximizeWindow(0, connInputWindow);
            }
        }
        stream.consumeData(payload, frame.isFlagSet(FrameFlag.END_STREAM));
    }

    private void maximizeWindow(final int streamId, final AtomicInteger window) throws IOException {
        final int delta = updateWindowMax(window);
        if (delta > 0) {
            final RawFrame windowUpdateFrame = frameFactory.createWindowUpdate(streamId, delta);
            commitFrame(windowUpdateFrame);
        }
    }

    private void consumePushPromiseFrame(final RawFrame frame, final ByteBuffer payload, final H2Stream promisedStream) throws HttpException, IOException {
        final int promisedStreamId = promisedStream.getId();

        if (!frame.isFlagSet(FrameFlag.END_HEADERS)) {
            continuation = new Continuation(promisedStreamId, frame.getType(), true,
                    localConfig.getMaxContinuations());
        }
        if (continuation == null) {
            final List<Header> headers = hPackDecoder.decodeHeaders(payload);
            if (streamListener != null) {
                streamListener.onHeaderInput(this, promisedStreamId, headers);
            }
            promisedStream.consumePromise(headers);
        } else {
            continuation.copyPayload(payload);
        }
    }

    List<Header> decodeHeaders(final ByteBuffer payload) throws HttpException {
        return hPackDecoder.decodeHeaders(payload);
    }

    private void consumeHeaderFrame(final RawFrame frame, final H2Stream stream) throws HttpException, IOException {
        if (stream.isRemoteClosed()) {
            throw new H2StreamResetException(H2Error.STREAM_CLOSED, "Stream already closed");
        }
        final int streamId = stream.getId();
        if (!frame.isFlagSet(FrameFlag.END_HEADERS)) {
            continuation = new Continuation(streamId, frame.getType(), frame.isFlagSet(FrameFlag.END_STREAM),
                    localConfig.getMaxContinuations());
        }
        final ByteBuffer payload = frame.getPayloadContent();
        if (frame.isFlagSet(FrameFlag.PRIORITY)) {
            payload.getInt();
            payload.get();
        }
        if (continuation == null) {
            final List<Header> headers = decodeHeaders(payload);
            if (streamListener != null) {
                streamListener.onHeaderInput(this, streamId, headers);
            }
            recordPriorityFromHeaders(streamId, headers);
            stream.consumeHeader(headers, frame.isFlagSet(FrameFlag.END_STREAM));
        } else {
            continuation.copyPayload(payload);
        }
    }

    private void consumeContinuationFrame(final RawFrame frame, final H2Stream stream) throws HttpException, IOException {
        if (stream.isRemoteClosed()) {
            throw new H2StreamResetException(H2Error.STREAM_CLOSED, "Stream already closed");
        }
        final int streamId = frame.getStreamId();
        final ByteBuffer payload = frame.getPayload();
        continuation.copyPayload(payload);
        if (frame.isFlagSet(FrameFlag.END_HEADERS)) {
            final List<Header> headers = decodeHeaders(continuation.getContent());
            if (streamListener != null) {
                streamListener.onHeaderInput(this, streamId, headers);
            }
            recordPriorityFromHeaders(streamId, headers);
            if (continuation.type == FrameType.PUSH_PROMISE.getValue()) {
                stream.consumePromise(headers);
            } else {
                stream.consumeHeader(headers, continuation.endStream);
            }
            continuation = null;
        }
    }

    private void consumeSettingsFrame(final ByteBuffer payload) throws IOException {
        final H2Config.Builder configBuilder = H2Config.initial();
        while (payload.hasRemaining()) {
            final int code = payload.getShort();
            final int value = payload.getInt();
            final H2Param param = H2Param.valueOf(code);
            if (param != null) {
                validateSetting(param, value);
                switch (param) {
                    case HEADER_TABLE_SIZE:
                        try {
                            configBuilder.setHeaderTableSize(value);
                        } catch (final IllegalArgumentException ex) {
                            throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, ex.getMessage());
                        }
                        break;
                    case MAX_CONCURRENT_STREAMS:
                        try {
                            configBuilder.setMaxConcurrentStreams(value);
                        } catch (final IllegalArgumentException ex) {
                            throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, ex.getMessage());
                        }
                        break;
                    case ENABLE_PUSH:
                        configBuilder.setPushEnabled(value == 1);
                        break;
                    case INITIAL_WINDOW_SIZE:
                        try {
                            configBuilder.setInitialWindowSize(value);
                        } catch (final IllegalArgumentException ex) {
                            throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, ex.getMessage());
                        }
                        break;
                    case MAX_FRAME_SIZE:
                        try {
                            configBuilder.setMaxFrameSize(value);
                        } catch (final IllegalArgumentException ex) {
                            throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, ex.getMessage());
                        }
                        break;
                    case MAX_HEADER_LIST_SIZE:
                        try {
                            configBuilder.setMaxHeaderListSize(value);
                        } catch (final IllegalArgumentException ex) {
                            throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, ex.getMessage());
                        }
                        break;
                    case SETTINGS_NO_RFC7540_PRIORITIES:
                        peerNoRfc7540Priorities = value == 1;
                        break;
                }
            }
        }
        applyRemoteSettings(configBuilder.build());
    }

    private void produceOutput() throws HttpException, IOException {
        for (final Iterator<H2Stream> it = streams.iterator(); it.hasNext(); ) {
            final H2Stream stream = it.next();
            if (!stream.isLocalClosed() && !stream.isReserved() && stream.getOutputWindow().get() > 0) {
                stream.produceOutput();
            }
            if (stream.isClosedPastLingerDeadline()) {
                streams.dropStreamId(stream.getId());
                stream.releaseResources();
                it.remove();
                requestSessionOutput();
            }
            if (!outputQueue.isEmpty()) {
                break;
            }
        }
    }

    private void applyRemoteSettings(final H2Config config) throws H2ConnectionException {
        remoteConfig = config;

        hPackEncoder.setMaxTableSize(remoteConfig.getHeaderTableSize());
        final int delta = remoteConfig.getInitialWindowSize() - initOutputWinSize;
        initOutputWinSize = remoteConfig.getInitialWindowSize();

        final int maxFrameSize = remoteConfig.getMaxFrameSize();
        if (maxFrameSize < outputBuffer.getMaxFramePayloadSize()) {
            try {
                outputBuffer.resize(maxFrameSize);
            } catch (final BufferOverflowException ex) {
                throw new H2ConnectionException(H2Error.INTERNAL_ERROR, "Failure resizing the frame output buffer");
            }
        }

        if (delta != 0) {
            if (!streams.isEmpty()) {
                for (final Iterator<H2Stream> it = streams.iterator(); it.hasNext(); ) {
                    final H2Stream stream = it.next();
                    try {
                        updateOutputWindow(stream.getId(), stream.getOutputWindow(), delta);
                    } catch (final ArithmeticException ex) {
                        throw new H2ConnectionException(H2Error.FLOW_CONTROL_ERROR, ex.getMessage());
                    }
                }
            }
        }
    }

    private void applyLocalSettings() throws H2ConnectionException {
        hPackDecoder.setMaxTableSize(localConfig.getHeaderTableSize());
        hPackDecoder.setMaxListSize(localConfig.getMaxHeaderListSize());

        final int delta = localConfig.getInitialWindowSize() - initInputWinSize;
        initInputWinSize = localConfig.getInitialWindowSize();

        if (delta != 0 && !streams.isEmpty()) {
            for (final Iterator<H2Stream> it = streams.iterator(); it.hasNext(); ) {
                final H2Stream stream = it.next();
                try {
                    updateInputWindow(stream.getId(), stream.getInputWindow(), delta);
                } catch (final ArithmeticException ex) {
                    throw new H2ConnectionException(H2Error.FLOW_CONTROL_ERROR, ex.getMessage());
                }
            }
        }
        lowMark = initInputWinSize / 2;
    }

    @Override
    public void close() throws IOException {
        ioSession.enqueue(ShutdownCommand.GRACEFUL, Command.Priority.IMMEDIATE);
    }

    @Override
    public void close(final CloseMode closeMode) {
        ioSession.close(closeMode);
    }

    @Override
    public boolean isOpen() {
        return connState == ConnectionHandshake.ACTIVE;
    }

    @Override
    public void setSocketTimeout(final Timeout timeout) {
        ioSession.setSocketTimeout(timeout);
    }

    @Override
    public SSLSession getSSLSession() {
        final TlsDetails tlsDetails = ioSession.getTlsDetails();
        return tlsDetails != null ? tlsDetails.getSSLSession() : null;
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
    public Timeout getSocketTimeout() {
        return ioSession.getSocketTimeout();
    }

    @Override
    public ProtocolVersion getProtocolVersion() {
        return HttpVersion.HTTP_2;
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return ioSession.getRemoteAddress();
    }

    @Override
    public SocketAddress getLocalAddress() {
        return ioSession.getLocalAddress();
    }

    void appendState(final StringBuilder buf) {
        buf.append("connState=").append(connState)
                .append(", connInputWindow=").append(connInputWindow)
                .append(", connOutputWindow=").append(connOutputWindow)
                .append(", outputQueue=").append(outputQueue.size())
                .append(", streams.localCoubt=").append(streams.getLocalCount())
                .append(", streams.remoteCount=").append(streams.getRemoteCount())
                .append(", streams.lastLocal=").append(streams.getLastLocalId())
                .append(", streams.lastRemote=").append(streams.getLastRemoteId());
    }

    private static class Continuation {

        final int streamId;
        final int type;
        final boolean endStream;
        final ByteArrayBuffer headerBuffer;
        final int maxContinuation;
        final boolean enforceMacContinuations;

        private int count;

        private Continuation(final int streamId, final int type, final boolean endStream, final int maxContinuation) {
            this.streamId = streamId;
            this.type = type;
            this.endStream = endStream;
            this.maxContinuation = maxContinuation;
            this.enforceMacContinuations = maxContinuation < Integer.MAX_VALUE;
            this.headerBuffer = new ByteArrayBuffer(1024);
        }

        void copyPayload(final ByteBuffer payload) throws H2ConnectionException {
            if (payload == null) {
                return;
            }
            if (enforceMacContinuations && count > maxContinuation) {
                throw new H2ConnectionException(H2Error.ENHANCE_YOUR_CALM, "Excessive number of continuation frames");
            }
            count++;
            final int originalLength = headerBuffer.length();
            final int toCopy = payload.remaining();
            headerBuffer.ensureCapacity(toCopy);
            payload.get(headerBuffer.array(), originalLength, toCopy);
            headerBuffer.setLength(originalLength + toCopy);
        }

        ByteBuffer getContent() {
            return ByteBuffer.wrap(headerBuffer.array(), 0, headerBuffer.length());
        }

    }

    H2StreamChannel createChannel(final int streamId) {
        return new H2StreamChannelImpl(streamId, initInputWinSize, initOutputWinSize);
    }

    H2Stream createStream(final H2StreamChannel channel, final H2StreamHandler streamHandler) throws H2ConnectionException {
        return streams.createActive(channel, streamHandler);
    }

    private void recordPriorityFromHeaders(final int streamId, final List<? extends Header> headers) {
        if (headers == null || headers.isEmpty()) {
            return;
        }
        for (final Header h : headers) {
            if (HttpHeaders.PRIORITY.equalsIgnoreCase(h.getName())) {
                final PriorityValue pv = PriorityParamsParser.parse(h.getValue()).toValueWithDefaults();
                priorities.put(streamId, pv);
                break;
            }
        }
    }

    class H2StreamChannelImpl implements H2StreamChannel {

        private final int id;
        private final AtomicInteger inputWindow;
        private final AtomicInteger outputWindow;

        private volatile boolean localClosed;
        private volatile long localResetTime;

        H2StreamChannelImpl(final int id, final int initialInputWindowSize, final int initialOutputWindowSize) {
            this.id = id;
            this.inputWindow = new AtomicInteger(initialInputWindowSize);
            this.outputWindow = new AtomicInteger(initialOutputWindowSize);
        }

        @Override
        public int getId() {
            return id;
        }

        @Override
        public AtomicInteger getOutputWindow() {
            return outputWindow;
        }

        @Override
        public AtomicInteger getInputWindow() {
            return inputWindow;
        }

        void ensureNotClosed() throws H2ConnectionException {
            if (localClosed) {
                throw new H2ConnectionException(H2Error.INTERNAL_ERROR, "Stream already closed locally");
            }
        }

        @Override
        public void submit(final List<Header> headers, final boolean endStream) throws IOException {
            ioSession.getLock().lock();
            try {
                if (headers == null || headers.isEmpty()) {
                    throw new H2ConnectionException(H2Error.INTERNAL_ERROR, "Message headers are missing");
                }
                if (isLocalReset()) {
                    return;
                }
                ensureNotClosed();
                if (peerNoRfc7540Priorities && streams.isSameSide(id)) {
                    for (final Header h : headers) {
                        if (HttpHeaders.PRIORITY.equalsIgnoreCase(h.getName())) {
                            final byte[] ascii = h.getValue() != null
                                    ? h.getValue().getBytes(StandardCharsets.US_ASCII)
                                    : new byte[0];
                            final ByteArrayBuffer b = new ByteArrayBuffer(4 + ascii.length);
                            b.append((byte) (id >> 24));
                            b.append((byte) (id >> 16));
                            b.append((byte) (id >> 8));
                            b.append((byte) id);
                            b.append(ascii, 0, ascii.length);
                            final ByteBuffer pl = ByteBuffer.wrap(b.array(), 0, b.length());
                            final RawFrame priUpd = new RawFrame(FrameType.PRIORITY_UPDATE.getValue(), 0, 0, pl);
                            commitFrameInternal(priUpd);
                            break;
                        }
                    }
                }
                commitHeaders(id, headers, endStream);
                if (endStream) {
                    localClosed = true;
                }
            } finally {
                ioSession.getLock().unlock();
            }
        }

        @Override
        public void push(final List<Header> headers, final AsyncPushProducer pushProducer) throws HttpException, IOException {
            acceptPushRequest();
            ioSession.getLock().lock();
            try {
                if (isLocalReset()) {
                    return;
                }
                ensureNotClosed();
                final int promisedStreamId = streams.generateStreamId();
                final H2StreamChannel channel = createChannel(promisedStreamId);
                final H2Stream stream = streams.createReserved(channel, outgoingPushPromise(channel, pushProducer));

                commitPushPromise(id, promisedStreamId, headers);
                stream.markRemoteClosed();
                submitCommand(new PushResponseCommand(promisedStreamId));
            } finally {
                ioSession.getLock().unlock();
            }
        }

        @Override
        public void update(final int increment) throws IOException {
            incrementInputCapacity(0, connInputWindow, increment);
            incrementInputCapacity(id, inputWindow, increment);
        }

        @Override
        public int write(final ByteBuffer payload) throws IOException {
            ioSession.getLock().lock();
            try {
                if (isLocalReset()) {
                    return 0;
                }
                ensureNotClosed();
                return streamData(id, outputWindow, payload);
            } finally {
                ioSession.getLock().unlock();
            }
        }

        @Override
        public void endStream(final List<? extends Header> trailers) throws IOException {
            ioSession.getLock().lock();
            try {
                if (isLocalReset()) {
                    return;
                }
                ensureNotClosed();
                localClosed = true;
                if (trailers != null && !trailers.isEmpty()) {
                    commitHeaders(id, trailers, true);
                } else {
                    final RawFrame frame = frameFactory.createData(id, null, true);
                    commitFrameInternal(frame);
                }
            } finally {
                ioSession.getLock().unlock();
            }
        }

        @Override
        public void endStream() throws IOException {
            endStream(null);
        }

        @Override
        public void requestOutput() {
            requestSessionOutput();
        }

        @Override
        public boolean isLocalClosed() {
            return localClosed;
        }

        @Override
        public void markLocalClosed() {
            localClosed = true;
        }

        @Override
        public boolean localReset(final int code) throws IOException {
            ioSession.getLock().lock();
            try {
                if (isLocalReset()) {
                    return false;
                }
                localClosed = true;
                localResetTime = System.currentTimeMillis();

                final RawFrame resetStream = frameFactory.createResetStream(id, code);
                commitFrameInternal(resetStream);
                return true;
            } finally {
                ioSession.getLock().unlock();
            }
        }

        @Override
        public long getLocalResetTime() {
            return localResetTime;
        }

        @Override
        public boolean cancel() {
            try {
                return localReset(H2Error.CANCEL);
            } catch (final IOException ignore) {
                return false;
            }
        }

        @Override
        public String toString() {
            final StringBuilder buf = new StringBuilder();
            buf.append("[")
                    .append("id=").append(id)
                    .append(", connState=").append(connState)
                    .append(", inputWindow=").append(inputWindow)
                    .append(", outputWindow=").append(outputWindow)
                    .append(", localClosed=").append(localClosed)
                    .append("]");
            return buf.toString();
        }

    }

}