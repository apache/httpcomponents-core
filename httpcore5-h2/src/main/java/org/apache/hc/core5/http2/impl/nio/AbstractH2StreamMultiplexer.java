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
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.charset.CharacterCodingException;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLSession;

import org.apache.hc.core5.concurrent.CancellableDependency;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStreamResetException;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.impl.BasicEndpointDetails;
import org.apache.hc.core5.http.impl.BasicHttpConnectionMetrics;
import org.apache.hc.core5.http.impl.CharCodingSupport;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.AsyncPushProducer;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.nio.command.ExecutableCommand;
import org.apache.hc.core5.http.nio.command.ShutdownCommand;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
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
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.ByteArrayBuffer;
import org.apache.hc.core5.util.Identifiable;
import org.apache.hc.core5.util.Timeout;

abstract class AbstractH2StreamMultiplexer implements Identifiable, HttpConnection {

    private static final long LINGER_TIME = 1000; // 1 second
    private static final long CONNECTION_WINDOW_LOW_MARK = 10 * 1024 * 1024; // 10 MiB

    enum ConnectionHandshake { READY, ACTIVE, GRACEFUL_SHUTDOWN, SHUTDOWN}
    enum SettingsHandshake { READY, TRANSMITTED, ACKED }

    private final ProtocolIOSession ioSession;
    private final FrameFactory frameFactory;
    private final StreamIdGenerator idGenerator;
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
    private final Map<Integer, H2Stream> streamMap;
    private final Queue<AsyncPingHandler> pingHandlers;
    private final AtomicInteger connInputWindow;
    private final AtomicInteger connOutputWindow;
    private final AtomicInteger outputRequests;
    private final AtomicInteger lastStreamId;
    private final H2StreamListener streamListener;

    private ConnectionHandshake connState = ConnectionHandshake.READY;
    private SettingsHandshake localSettingState = SettingsHandshake.READY;
    private SettingsHandshake remoteSettingState = SettingsHandshake.READY;

    private int initInputWinSize;
    private int initOutputWinSize;
    private int lowMark;

    private volatile H2Config remoteConfig;

    private Continuation continuation;

    private int processedRemoteStreamId;
    private EndpointDetails endpointDetails;

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
        this.idGenerator = Args.notNull(idGenerator, "Stream id generator");
        this.httpProcessor = Args.notNull(httpProcessor, "HTTP processor");
        this.localConfig = h2Config != null ? h2Config : H2Config.DEFAULT;
        this.inputMetrics = new BasicH2TransportMetrics();
        this.outputMetrics = new BasicH2TransportMetrics();
        this.connMetrics = new BasicHttpConnectionMetrics(this.inputMetrics, this.outputMetrics);
        this.inputBuffer = new FrameInputBuffer(this.inputMetrics, this.localConfig.getMaxFrameSize());
        this.outputBuffer = new FrameOutputBuffer(this.outputMetrics, this.localConfig.getMaxFrameSize());
        this.outputQueue = new ConcurrentLinkedDeque<>();
        this.pingHandlers = new ConcurrentLinkedQueue<>();
        this.outputRequests = new AtomicInteger(0);
        this.lastStreamId = new AtomicInteger(0);
        this.hPackEncoder = new HPackEncoder(CharCodingSupport.createEncoder(charCodingConfig));
        this.hPackDecoder = new HPackDecoder(CharCodingSupport.createDecoder(charCodingConfig));
        this.streamMap = new ConcurrentHashMap<>();
        this.remoteConfig = H2Config.INIT;
        this.connInputWindow = new AtomicInteger(H2Config.INIT.getInitialWindowSize());
        this.connOutputWindow = new AtomicInteger(H2Config.INIT.getInitialWindowSize());

        this.initInputWinSize = H2Config.INIT.getInitialWindowSize();
        this.initOutputWinSize = H2Config.INIT.getInitialWindowSize();

        this.hPackDecoder.setMaxTableSize(H2Config.INIT.getHeaderTableSize());
        this.hPackEncoder.setMaxTableSize(H2Config.INIT.getHeaderTableSize());
        this.hPackDecoder.setMaxListSize(H2Config.INIT.getMaxHeaderListSize());

        this.lowMark = H2Config.INIT.getInitialWindowSize() / 2;
        this.streamListener = streamListener;
    }

    @Override
    public String getId() {
        return ioSession.getId();
    }

    abstract void acceptHeaderFrame() throws H2ConnectionException;

    abstract void acceptPushRequest() throws H2ConnectionException;

    abstract void acceptPushFrame() throws H2ConnectionException;

    abstract H2StreamHandler createRemotelyInitiatedStream(
            H2StreamChannel channel,
            HttpProcessor httpProcessor,
            BasicHttpConnectionMetrics connMetrics,
            HandlerFactory<AsyncPushConsumer> pushHandlerFactory) throws IOException;

    abstract H2StreamHandler createLocallyInitiatedStream(
            ExecutableCommand command,
            H2StreamChannel channel,
            HttpProcessor httpProcessor,
            BasicHttpConnectionMetrics connMetrics) throws IOException;

    private int updateWindow(final AtomicInteger window, final int delta) throws ArithmeticException {
        for (;;) {
            final int current = window.get();
            long newValue = (long) current + delta;

            //TODO: work-around for what looks like a bug in Ngnix (1.11)
            // Tolerate if the update window exceeded by one
            if (newValue == 0x80000000L) {
                newValue = Integer.MAX_VALUE;
            }
            //TODO: needs to be removed

            if (Math.abs(newValue) > 0x7fffffffL) {
                throw new ArithmeticException("Update causes flow control window to exceed " + Integer.MAX_VALUE);
            }
            if (window.compareAndSet(current, (int) newValue)) {
                return (int) newValue;
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
        buf.append((byte)(promisedStreamId));

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
            final int maxPayloadSize = Math.min(capacity, remoteConfig.getMaxFrameSize());
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
                final RawFrame windowUpdateFrame = frameFactory.createWindowUpdate(streamId, chunk);
                commitFrame(windowUpdateFrame);
                updateInputWindow(streamId, inputWindow, chunk);
            }
        }
    }

    private void requestSessionOutput() {
        outputRequests.incrementAndGet();
        ioSession.setEvent(SelectionKey.OP_WRITE);
    }

    private void updateLastStreamId(final int streamId) {
        final int currentId = lastStreamId.get();
        if (streamId > currentId) {
            lastStreamId.compareAndSet(currentId, streamId);
        }
    }

    private int generateStreamId() {
        for (;;) {
            final int currentId = lastStreamId.get();
            final int newStreamId = idGenerator.generate(currentId);
            if (lastStreamId.compareAndSet(currentId, newStreamId)) {
                return newStreamId;
            }
        }
    }

    public final void onConnect() throws HttpException, IOException {
        connState = ConnectionHandshake.ACTIVE;
        final RawFrame settingsFrame = frameFactory.createSettings(
                new H2Setting(H2Param.HEADER_TABLE_SIZE, localConfig.getHeaderTableSize()),
                new H2Setting(H2Param.ENABLE_PUSH, localConfig.isPushEnabled() ? 1 : 0),
                new H2Setting(H2Param.MAX_CONCURRENT_STREAMS, localConfig.getMaxConcurrentStreams()),
                new H2Setting(H2Param.INITIAL_WINDOW_SIZE, localConfig.getInitialWindowSize()),
                new H2Setting(H2Param.MAX_FRAME_SIZE, localConfig.getMaxFrameSize()),
                new H2Setting(H2Param.MAX_HEADER_LIST_SIZE, localConfig.getMaxHeaderListSize()));

        commitFrame(settingsFrame);
        localSettingState = SettingsHandshake.TRANSMITTED;
        maximizeConnWindow(connInputWindow.get());

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
            if (src != null) {
                inputBuffer.put(src);
            }
            RawFrame frame;
            while ((frame = inputBuffer.read(ioSession)) != null) {
                if (streamListener != null) {
                    streamListener.onFrameInput(this, frame.getStreamId(), frame);
                }
                consumeFrame(frame);
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
            if (!streamMap.isEmpty() && connOutputWindow.get() > 0) {
                for (final Iterator<Map.Entry<Integer, H2Stream>> it = streamMap.entrySet().iterator(); it.hasNext(); ) {
                    final Map.Entry<Integer, H2Stream> entry = it.next();
                    final H2Stream stream = entry.getValue();
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
            processPendingCommands();
        }
        if (connState.compareTo(ConnectionHandshake.GRACEFUL_SHUTDOWN) == 0) {
            for (final Iterator<Map.Entry<Integer, H2Stream>> it = streamMap.entrySet().iterator(); it.hasNext(); ) {
                final Map.Entry<Integer, H2Stream> entry = it.next();
                final H2Stream stream = entry.getValue();
                if (stream.isLocalClosed() && stream.isRemoteClosed()) {
                    stream.releaseResources();
                    it.remove();
                }
            }
            if (streamMap.isEmpty()) {
                connState = ConnectionHandshake.SHUTDOWN;
            }
        }
        if (connState.compareTo(ConnectionHandshake.SHUTDOWN) >= 0) {
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
            goAway = frameFactory.createGoAway(processedRemoteStreamId, H2Error.SETTINGS_TIMEOUT,
                            "Setting timeout (" + timeout + ")");
        } else {
            goAway = frameFactory.createGoAway(processedRemoteStreamId, H2Error.NO_ERROR,
                            "Timeout due to inactivity (" + timeout + ")");
        }
        commitFrame(goAway);
        for (final Iterator<Map.Entry<Integer, H2Stream>> it = streamMap.entrySet().iterator(); it.hasNext(); ) {
            final Map.Entry<Integer, H2Stream> entry = it.next();
            final H2Stream stream = entry.getValue();
            stream.reset(new H2StreamResetException(H2Error.NO_ERROR, "Timeout due to inactivity (" + timeout + ")"));
        }
        streamMap.clear();
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
        for (final Iterator<Map.Entry<Integer, H2Stream>> it = streamMap.entrySet().iterator(); it.hasNext(); ) {
            final Map.Entry<Integer, H2Stream> entry = it.next();
            final H2Stream stream = entry.getValue();
            stream.cancel();
        }
        for (;;) {
            final Command command = ioSession.poll();
            if (command != null) {
                if (command instanceof ExecutableCommand) {
                    ((ExecutableCommand) command).failed(new ConnectionClosedException());
                } else {
                    command.cancel();
                }
            } else {
                break;
            }
        }
    }

    private void processPendingCommands() throws IOException, HttpException {
        while (streamMap.size() < remoteConfig.getMaxConcurrentStreams()) {
            final Command command = ioSession.poll();
            if (command == null) {
                break;
            }
            if (command instanceof ShutdownCommand) {
                final ShutdownCommand shutdownCommand = (ShutdownCommand) command;
                if (shutdownCommand.getType() == CloseMode.IMMEDIATE) {
                    for (final Iterator<Map.Entry<Integer, H2Stream>> it = streamMap.entrySet().iterator(); it.hasNext(); ) {
                        final Map.Entry<Integer, H2Stream> entry = it.next();
                        final H2Stream stream = entry.getValue();
                        stream.cancel();
                    }
                    streamMap.clear();
                    connState = ConnectionHandshake.SHUTDOWN;
                } else {
                    if (connState.compareTo(ConnectionHandshake.ACTIVE) <= 0) {
                        final RawFrame goAway = frameFactory.createGoAway(processedRemoteStreamId, H2Error.NO_ERROR, "Graceful shutdown");
                        commitFrame(goAway);
                        connState = streamMap.isEmpty() ? ConnectionHandshake.SHUTDOWN : ConnectionHandshake.GRACEFUL_SHUTDOWN;
                    }
                }
                break;
            } else if (command instanceof PingCommand) {
                final PingCommand pingCommand = (PingCommand) command;
                final AsyncPingHandler handler = pingCommand.getHandler();
                pingHandlers.add(handler);
                final RawFrame ping = frameFactory.createPing(handler.getData());
                commitFrame(ping);
            } else if (command instanceof ExecutableCommand) {
                final int streamId = generateStreamId();
                final H2StreamChannelImpl channel = new H2StreamChannelImpl(
                        streamId, true, initInputWinSize, initOutputWinSize);
                final ExecutableCommand executableCommand = (ExecutableCommand) command;
                final H2StreamHandler streamHandler = createLocallyInitiatedStream(
                        executableCommand, channel, httpProcessor, connMetrics);

                final H2Stream stream = new H2Stream(channel, streamHandler, false);
                streamMap.put(streamId, stream);

                if (streamListener != null) {
                    final int initInputWindow = stream.getInputWindow().get();
                    streamListener.onInputFlowControl(this, streamId, initInputWindow, initInputWindow);
                    final int initOutputWindow = stream.getOutputWindow().get();
                    streamListener.onOutputFlowControl(this, streamId, initOutputWindow, initOutputWindow);
                }

                if (stream.isOutputReady()) {
                    stream.produceOutput();
                }
                final CancellableDependency cancellableDependency = executableCommand.getCancellableDependency();
                if (cancellableDependency != null) {
                    cancellableDependency.setDependency(stream::abort);
                }
                if (!outputQueue.isEmpty()) {
                    return;
                }
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
            for (;;) {
                final Command command = ioSession.poll();
                if (command != null) {
                    if (command instanceof ExecutableCommand) {
                        ((ExecutableCommand) command).failed(new ConnectionClosedException());
                    } else {
                        command.cancel();
                    }
                } else {
                    break;
                }
            }
            for (final Iterator<Map.Entry<Integer, H2Stream>> it = streamMap.entrySet().iterator(); it.hasNext(); ) {
                final Map.Entry<Integer, H2Stream> entry = it.next();
                final H2Stream stream = entry.getValue();
                stream.reset(cause);
            }
            streamMap.clear();
            if (!(cause instanceof ConnectionClosedException)) {
                if (connState.compareTo(ConnectionHandshake.GRACEFUL_SHUTDOWN) <= 0) {
                    final H2Error errorCode;
                    if (cause instanceof H2ConnectionException) {
                        errorCode = H2Error.getByCode(((H2ConnectionException) cause).getCode());
                    } else if (cause instanceof ProtocolException){
                        errorCode = H2Error.PROTOCOL_ERROR;
                    } else {
                        errorCode = H2Error.INTERNAL_ERROR;
                    }
                    final RawFrame goAway = frameFactory.createGoAway(processedRemoteStreamId, errorCode, cause.getMessage());
                    commitFrame(goAway);
                }
            }
        } catch (final IOException ignore) {
        } finally {
            connState = ConnectionHandshake.SHUTDOWN;
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

    private H2Stream getValidStream(final int streamId) throws H2ConnectionException {
        if (streamId == 0) {
            throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "Illegal stream id: " + streamId);
        }
        final H2Stream stream = streamMap.get(streamId);
        if (stream == null) {
            if (streamId <= lastStreamId.get()) {
                throw new H2ConnectionException(H2Error.STREAM_CLOSED, "Stream closed");
            } else {
                throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "Unexpected stream id: " + streamId);
            }
        }
        return stream;
    }

    private void consumeFrame(final RawFrame frame) throws HttpException, IOException {
        final FrameType frameType = FrameType.valueOf(frame.getType());
        final int streamId = frame.getStreamId();
        if (continuation != null && frameType != FrameType.CONTINUATION) {
            throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "CONTINUATION frame expected");
        }
        if (connState.compareTo(ConnectionHandshake.GRACEFUL_SHUTDOWN) >= 0) {
            if (streamId > processedRemoteStreamId && !idGenerator.isSameSide(streamId)) {
                // ignore the frame
                return;
            }
        }
        switch (frameType) {
            case DATA: {
                final H2Stream stream = getValidStream(streamId);
                try {
                    consumeDataFrame(frame, stream);
                } catch (final H2StreamResetException ex) {
                    stream.localReset(ex);
                } catch (final HttpStreamResetException ex) {
                    stream.localReset(ex, ex.getCause() != null ? H2Error.INTERNAL_ERROR : H2Error.CANCEL);
                }

                if (stream.isTerminated()) {
                    streamMap.remove(streamId);
                    stream.releaseResources();
                }
            }
            break;
            case HEADERS: {
                if (streamId == 0) {
                    throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "Illegal stream id: " + streamId);
                }
                H2Stream stream = streamMap.get(streamId);
                if (stream == null) {
                    acceptHeaderFrame();

                    updateLastStreamId(streamId);

                    final H2StreamChannelImpl channel = new H2StreamChannelImpl(
                            streamId, false, initInputWinSize, initOutputWinSize);
                    final H2StreamHandler streamHandler = createRemotelyInitiatedStream(
                            channel, httpProcessor, connMetrics, null);
                    stream = new H2Stream(channel, streamHandler, true);
                    if (stream.isOutputReady()) {
                        stream.produceOutput();
                    }
                    streamMap.put(streamId, stream);
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

                if (stream.isTerminated()) {
                    streamMap.remove(streamId);
                    stream.releaseResources();
                }
            }
            break;
            case CONTINUATION: {
                if (continuation == null) {
                    throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "Unexpected CONTINUATION frame");
                }
                if (streamId != continuation.streamId) {
                    throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "Unexpected CONTINUATION stream id: " + streamId);
                }

                final H2Stream stream = getValidStream(streamId);
                try {

                    consumeContinuationFrame(frame, stream);
                } catch (final H2StreamResetException ex) {
                    stream.localReset(ex);
                } catch (final HttpStreamResetException ex) {
                    stream.localReset(ex, ex.getCause() != null ? H2Error.INTERNAL_ERROR : H2Error.CANCEL);
                }

                if (stream.isTerminated()) {
                    streamMap.remove(streamId);
                    stream.releaseResources();
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
                    final H2Stream stream = streamMap.get(streamId);
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
                final H2Stream stream = streamMap.get(streamId);
                if (stream == null) {
                    if (streamId > lastStreamId.get()) {
                        throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "Unexpected stream id: " + streamId);
                    }
                } else {
                    final ByteBuffer payload = frame.getPayload();
                    if (payload == null || payload.remaining() != 4) {
                        throw new H2ConnectionException(H2Error.FRAME_SIZE_ERROR, "Invalid RST_STREAM frame payload");
                    }
                    final int errorCode = payload.getInt();
                    stream.reset(new H2StreamResetException(errorCode, "Stream reset (" + errorCode + ")"));
                    streamMap.remove(streamId);
                    stream.releaseResources();
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
                    // Send ACK
                    final RawFrame response = frameFactory.createSettingsAck();
                    commitFrame(response);
                    remoteSettingState = SettingsHandshake.ACKED;
                }
            }
            break;
            case PRIORITY:
                // Stream priority not supported
                break;
            case PUSH_PROMISE: {
                acceptPushFrame();

                if (!localConfig.isPushEnabled()) {
                    throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "Push is disabled");
                }

                final H2Stream stream = getValidStream(streamId);
                if (stream.isRemoteClosed()) {
                    stream.localReset(new H2StreamResetException(H2Error.STREAM_CLOSED, "Stream closed"));
                    break;
                }

                final ByteBuffer payload = frame.getPayloadContent();
                if (payload == null || payload.remaining() < 4) {
                    throw new H2ConnectionException(H2Error.FRAME_SIZE_ERROR, "Invalid PUSH_PROMISE payload");
                }
                final int promisedStreamId = payload.getInt();
                if (promisedStreamId == 0 || idGenerator.isSameSide(promisedStreamId)) {
                    throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "Illegal promised stream id: " + promisedStreamId);
                }
                if (streamMap.get(promisedStreamId) != null) {
                    throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "Unexpected promised stream id: " + promisedStreamId);
                }

                updateLastStreamId(promisedStreamId);

                final H2StreamChannelImpl channel = new H2StreamChannelImpl(
                        promisedStreamId, false, initInputWinSize, initOutputWinSize);
                final H2StreamHandler streamHandler = createRemotelyInitiatedStream(
                        channel, httpProcessor, connMetrics, stream.getPushHandlerFactory());
                final H2Stream promisedStream = new H2Stream(channel, streamHandler, true);
                streamMap.put(promisedStreamId, promisedStream);

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
                if (errorCode == H2Error.NO_ERROR.getCode()) {
                    if (connState.compareTo(ConnectionHandshake.ACTIVE) <= 0) {
                        for (final Iterator<Map.Entry<Integer, H2Stream>> it = streamMap.entrySet().iterator(); it.hasNext(); ) {
                            final Map.Entry<Integer, H2Stream> entry = it.next();
                            final int activeStreamId = entry.getKey();
                            if (!idGenerator.isSameSide(activeStreamId) && activeStreamId > processedLocalStreamId) {
                                final H2Stream stream = entry.getValue();
                                stream.cancel();
                                it.remove();
                            }
                        }
                    }
                    connState = streamMap.isEmpty() ? ConnectionHandshake.SHUTDOWN : ConnectionHandshake.GRACEFUL_SHUTDOWN;
                } else {
                    for (final Iterator<Map.Entry<Integer, H2Stream>> it = streamMap.entrySet().iterator(); it.hasNext(); ) {
                        final Map.Entry<Integer, H2Stream> entry = it.next();
                        final H2Stream stream = entry.getValue();
                        stream.reset(new H2StreamResetException(errorCode, "Connection terminated by the peer (" + errorCode + ")"));
                    }
                    streamMap.clear();
                    connState = ConnectionHandshake.SHUTDOWN;
                }
            }
            ioSession.setEvent(SelectionKey.OP_WRITE);
            break;
        }
    }

    private void consumeDataFrame(final RawFrame frame, final H2Stream stream) throws HttpException, IOException {
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
                maximizeConnWindow(connWinSize);
            }
        }
        if (stream.isRemoteClosed()) {
            throw new H2StreamResetException(H2Error.STREAM_CLOSED, "Stream already closed");
        }
        if (frame.isFlagSet(FrameFlag.END_STREAM)) {
            stream.setRemoteEndStream();
        }
        if (stream.isLocalReset()) {
            return;
        }
        stream.consumeData(payload);
    }

    private void maximizeConnWindow(final int connWinSize) throws IOException {
        final int delta = Integer.MAX_VALUE - connWinSize;
        if (delta > 0) {
            final RawFrame windowUpdateFrame = frameFactory.createWindowUpdate(0, delta);
            commitFrame(windowUpdateFrame);
            updateInputWindow(0, connInputWindow, delta);
        }
    }

    private void consumePushPromiseFrame(final RawFrame frame, final ByteBuffer payload, final H2Stream promisedStream) throws HttpException, IOException {
        final int promisedStreamId = promisedStream.getId();
        if (!frame.isFlagSet(FrameFlag.END_HEADERS)) {
            continuation = new Continuation(promisedStreamId, frame.getType(), true);
        }
        if (continuation == null) {
            final List<Header> headers = hPackDecoder.decodeHeaders(payload);
            if (promisedStreamId > processedRemoteStreamId) {
                processedRemoteStreamId = promisedStreamId;
            }
            if (streamListener != null) {
                streamListener.onHeaderInput(this, promisedStreamId, headers);
            }
            if (connState == ConnectionHandshake.GRACEFUL_SHUTDOWN) {
                throw new H2StreamResetException(H2Error.REFUSED_STREAM, "Stream refused");
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
        final int streamId = stream.getId();
        if (!frame.isFlagSet(FrameFlag.END_HEADERS)) {
            continuation = new Continuation(streamId, frame.getType(), frame.isFlagSet(FrameFlag.END_STREAM));
        }
        final ByteBuffer payload = frame.getPayloadContent();
        if (frame.isFlagSet(FrameFlag.PRIORITY)) {
            // Priority not supported
            payload.getInt();
            payload.get();
        }
        if (continuation == null) {
            final List<Header> headers = decodeHeaders(payload);
            if (stream.isRemoteInitiated() && streamId > processedRemoteStreamId) {
                processedRemoteStreamId = streamId;
            }
            if (streamListener != null) {
                streamListener.onHeaderInput(this, streamId, headers);
            }
            if (stream.isRemoteClosed()) {
                throw new H2StreamResetException(H2Error.STREAM_CLOSED, "Stream already closed");
            }
            if (stream.isLocalReset()) {
                return;
            }
            if (connState == ConnectionHandshake.GRACEFUL_SHUTDOWN) {
                throw new H2StreamResetException(H2Error.PROTOCOL_ERROR, "Stream refused");
            }
            if (frame.isFlagSet(FrameFlag.END_STREAM)) {
                stream.setRemoteEndStream();
            }
            stream.consumeHeader(headers);
        } else {
            continuation.copyPayload(payload);
        }
    }

    private void consumeContinuationFrame(final RawFrame frame, final H2Stream stream) throws HttpException, IOException {
        final int streamId = frame.getStreamId();
        final ByteBuffer payload = frame.getPayload();
        continuation.copyPayload(payload);
        if (frame.isFlagSet(FrameFlag.END_HEADERS)) {
            final List<Header> headers = decodeHeaders(continuation.getContent());
            if (stream.isRemoteInitiated() && streamId > processedRemoteStreamId) {
                processedRemoteStreamId = streamId;
            }
            if (streamListener != null) {
                streamListener.onHeaderInput(this, streamId, headers);
            }
            if (connState == ConnectionHandshake.GRACEFUL_SHUTDOWN) {
                throw new H2StreamResetException(H2Error.PROTOCOL_ERROR, "Stream refused");
            }
            if (stream.isRemoteClosed()) {
                throw new H2StreamResetException(H2Error.STREAM_CLOSED, "Stream already closed");
            }
            if (stream.isLocalReset()) {
                return;
            }
            if (continuation.endStream) {
                stream.setRemoteEndStream();
            }
            if (continuation.type == FrameType.PUSH_PROMISE.getValue()) {
                stream.consumePromise(headers);
            } else {
                stream.consumeHeader(headers);
            }
            continuation = null;
        }
    }

    private void consumeSettingsFrame(final ByteBuffer payload) throws HttpException, IOException {
        final H2Config.Builder configBuilder = H2Config.initial();
        while (payload.hasRemaining()) {
            final int code = payload.getShort();
            final int value = payload.getInt();
            final H2Param param = H2Param.valueOf(code);
            if (param != null) {
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
                }
            }
        }
        applyRemoteSettings(configBuilder.build());
    }

    private void produceOutput() throws HttpException, IOException {
        for (final Iterator<Map.Entry<Integer, H2Stream>> it = streamMap.entrySet().iterator(); it.hasNext(); ) {
            final Map.Entry<Integer, H2Stream> entry = it.next();
            final H2Stream stream = entry.getValue();
            if (!stream.isLocalClosed() && stream.getOutputWindow().get() > 0) {
                stream.produceOutput();
            }
            if (stream.isTerminated()) {
                it.remove();
                stream.releaseResources();
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

        if (delta != 0) {
            if (!streamMap.isEmpty()) {
                for (final Iterator<Map.Entry<Integer, H2Stream>> it = streamMap.entrySet().iterator(); it.hasNext(); ) {
                    final Map.Entry<Integer, H2Stream> entry = it.next();
                    final H2Stream stream = entry.getValue();
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

        if (delta != 0 && !streamMap.isEmpty()) {
            for (final Iterator<Map.Entry<Integer, H2Stream>> it = streamMap.entrySet().iterator(); it.hasNext(); ) {
                final Map.Entry<Integer, H2Stream> entry = it.next();
                final H2Stream stream = entry.getValue();
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
                .append(", streamMap=").append(streamMap.size())
                .append(", processedRemoteStreamId=").append(processedRemoteStreamId);
    }

    private static class Continuation {

        final int streamId;
        final int type;
        final boolean endStream;
        final ByteArrayBuffer headerBuffer;

        private Continuation(final int streamId, final int type, final boolean endStream) {
            this.streamId = streamId;
            this.type = type;
            this.endStream = endStream;
            this.headerBuffer = new ByteArrayBuffer(1024);
        }

        void copyPayload(final ByteBuffer payload) {
            if (payload == null) {
                return;
            }
            headerBuffer.ensureCapacity(payload.remaining());
            payload.get(headerBuffer.array(), headerBuffer.length(), payload.remaining());
        }

        ByteBuffer getContent() {
            return ByteBuffer.wrap(headerBuffer.array(), 0, headerBuffer.length());
        }

    }

    private class H2StreamChannelImpl implements H2StreamChannel {

        private final int id;
        private final AtomicInteger inputWindow;
        private final AtomicInteger outputWindow;

        private volatile boolean idle;
        private volatile boolean remoteEndStream;
        private volatile boolean localEndStream;

        private volatile long deadline;

        H2StreamChannelImpl(final int id, final boolean idle, final int initialInputWindowSize, final int initialOutputWindowSize) {
            this.id = id;
            this.idle = idle;
            this.inputWindow = new AtomicInteger(initialInputWindowSize);
            this.outputWindow = new AtomicInteger(initialOutputWindowSize);
        }

        int getId() {
            return id;
        }

        AtomicInteger getOutputWindow() {
            return outputWindow;
        }

        AtomicInteger getInputWindow() {
            return inputWindow;
        }

        @Override
        public void submit(final List<Header> headers, final boolean endStream) throws IOException {
            ioSession.getLock().lock();
            try {
                if (headers == null || headers.isEmpty()) {
                    throw new H2ConnectionException(H2Error.INTERNAL_ERROR, "Message headers are missing");
                }
                if (localEndStream) {
                    return;
                }
                idle = false;
                commitHeaders(id, headers, endStream);
                if (endStream) {
                    localEndStream = true;
                }
            } finally {
                ioSession.getLock().unlock();
            }
        }

        @Override
        public void push(final List<Header> headers, final AsyncPushProducer pushProducer) throws HttpException, IOException {
            acceptPushRequest();
            final int promisedStreamId = generateStreamId();
            final H2StreamChannelImpl channel = new H2StreamChannelImpl(
                    promisedStreamId,
                    true,
                    localConfig.getInitialWindowSize(),
                    remoteConfig.getInitialWindowSize());
            final HttpCoreContext context = HttpCoreContext.create();
            context.setAttribute(HttpCoreContext.SSL_SESSION, getSSLSession());
            context.setAttribute(HttpCoreContext.CONNECTION_ENDPOINT, getEndpointDetails());
            final H2StreamHandler streamHandler = new ServerPushH2StreamHandler(
                    channel, httpProcessor, connMetrics, pushProducer, context);
            final H2Stream stream = new H2Stream(channel, streamHandler, false);
            streamMap.put(promisedStreamId, stream);

            ioSession.getLock().lock();
            try {
                if (localEndStream) {
                    stream.releaseResources();
                    return;
                }
                commitPushPromise(id, promisedStreamId, headers);
                idle = false;
            } finally {
                ioSession.getLock().unlock();
            }
        }

        @Override
        public void update(final int increment) throws IOException {
            if (remoteEndStream) {
                return;
            }
            incrementInputCapacity(0, connInputWindow, increment);
            incrementInputCapacity(id, inputWindow, increment);
        }

        @Override
        public int write(final ByteBuffer payload) throws IOException {
            ioSession.getLock().lock();
            try {
                if (localEndStream) {
                    return 0;
                }
                return streamData(id, outputWindow, payload);
            } finally {
                ioSession.getLock().unlock();
            }
        }

        @Override
        public void endStream(final List<? extends Header> trailers) throws IOException {
            ioSession.getLock().lock();
            try {
                if (localEndStream) {
                    return;
                }
                localEndStream = true;
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

        boolean isRemoteClosed() {
            return remoteEndStream;
        }

        void setRemoteEndStream() {
            remoteEndStream = true;
        }

        boolean isLocalClosed() {
            return localEndStream;
        }

        void setLocalEndStream() {
            localEndStream = true;
        }

        boolean isLocalReset() {
            return deadline > 0;
        }

        boolean isResetDeadline() {
            final long l = deadline;
            return l > 0 && l < System.currentTimeMillis();
        }

        boolean localReset(final int code) throws IOException {
            ioSession.getLock().lock();
            try {
                if (localEndStream) {
                    return false;
                }
                localEndStream = true;
                deadline = System.currentTimeMillis() + LINGER_TIME;
                if (!idle) {
                    final RawFrame resetStream = frameFactory.createResetStream(id, code);
                    commitFrameInternal(resetStream);
                    return true;
                }
                return false;
            } finally {
                ioSession.getLock().unlock();
            }
        }

        boolean localReset(final H2Error error) throws IOException {
            return localReset(error!= null ? error.getCode() : H2Error.INTERNAL_ERROR.getCode());
        }

        @Override
        public boolean cancel() {
            try {
                return localReset(H2Error.CANCEL);
            } catch (final IOException ignore) {
                return false;
            }
        }

        void appendState(final StringBuilder buf) {
            buf.append("id=").append(id)
                    .append(", connState=").append(connState)
                    .append(", inputWindow=").append(inputWindow)
                    .append(", outputWindow=").append(outputWindow)
                    .append(", localEndStream=").append(localEndStream)
                    .append(", idle=").append(idle);
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

    static class H2Stream {

        private final H2StreamChannelImpl channel;
        private final H2StreamHandler handler;
        private final boolean remoteInitiated;

        private H2Stream(
                final H2StreamChannelImpl channel,
                final H2StreamHandler handler,
                final boolean remoteInitiated) {
            this.channel = channel;
            this.handler = handler;
            this.remoteInitiated = remoteInitiated;
        }

        int getId() {
            return channel.getId();
        }

        boolean isRemoteInitiated() {
            return remoteInitiated;
        }

        AtomicInteger getOutputWindow() {
            return channel.getOutputWindow();
        }

        AtomicInteger getInputWindow() {
            return channel.getInputWindow();
        }

        boolean isTerminated() {
            return channel.isLocalClosed() && (channel.isRemoteClosed() || channel.isResetDeadline());
        }

        boolean isRemoteClosed() {
            return channel.isRemoteClosed();
        }

        boolean isLocalClosed() {
            return channel.isLocalClosed();
        }

        boolean isLocalReset() {
            return channel.isLocalReset();
        }

        void setRemoteEndStream() {
            channel.setRemoteEndStream();
        }

        void consumePromise(final List<Header> headers) throws HttpException, IOException {
            try {
                handler.consumePromise(headers);
                channel.setLocalEndStream();
            } catch (final ProtocolException ex) {
                localReset(ex, H2Error.PROTOCOL_ERROR);
            }
        }

        void consumeHeader(final List<Header> headers) throws HttpException, IOException {
            try {
                handler.consumeHeader(headers, channel.isRemoteClosed());
            } catch (final ProtocolException ex) {
                localReset(ex, H2Error.PROTOCOL_ERROR);
            }
        }

        void consumeData(final ByteBuffer src) throws HttpException, IOException {
            try {
                handler.consumeData(src, channel.isRemoteClosed());
            } catch (final CharacterCodingException ex) {
                localReset(ex, H2Error.INTERNAL_ERROR);
            } catch (final ProtocolException ex) {
                localReset(ex, H2Error.PROTOCOL_ERROR);
            }
        }

        boolean isOutputReady() {
            return handler.isOutputReady();
        }

        void produceOutput() throws HttpException, IOException {
            try {
                handler.produceOutput();
            } catch (final ProtocolException ex) {
                localReset(ex, H2Error.PROTOCOL_ERROR);
            }
        }

        void produceInputCapacityUpdate() throws IOException {
            handler.updateInputCapacity();
        }

        void reset(final Exception cause) {
            channel.setRemoteEndStream();
            channel.setLocalEndStream();
            handler.failed(cause);
        }

        void localReset(final Exception cause, final int code) throws IOException {
            channel.localReset(code);
            handler.failed(cause);
        }

        void localReset(final Exception cause, final H2Error error) throws IOException {
            localReset(cause, error != null ? error.getCode() : H2Error.INTERNAL_ERROR.getCode());
        }

        void localReset(final H2StreamResetException ex) throws IOException {
            localReset(ex, ex.getCode());
        }

        void handle(final HttpException ex) throws IOException, HttpException {
            handler.handle(ex, channel.isRemoteClosed());
        }

        HandlerFactory<AsyncPushConsumer> getPushHandlerFactory() {
            return handler.getPushHandlerFactory();
        }

        void cancel() {
            reset(new CancellationException("HTTP/2 message exchange cancelled"));
        }

        boolean abort() {
            final boolean cancelled = channel.cancel();
            handler.failed(new CancellationException("HTTP/2 message exchange cancelled"));
            return cancelled;
        }

        void releaseResources() {
            handler.releaseResources();
            channel.requestOutput();
        }

        void appendState(final StringBuilder buf) {
            buf.append("channel=[");
            channel.appendState(buf);
            buf.append("]");
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

}
