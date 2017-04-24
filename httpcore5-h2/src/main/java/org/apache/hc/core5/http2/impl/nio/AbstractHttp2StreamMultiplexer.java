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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.net.ssl.SSLSession;

import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.impl.BasicEndpointDetails;
import org.apache.hc.core5.http.impl.BasicHttpConnectionMetrics;
import org.apache.hc.core5.http.impl.CharCodingSupport;
import org.apache.hc.core5.http.impl.ConnectionListener;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncPushProducer;
import org.apache.hc.core5.http.nio.command.ExecutionCommand;
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
import org.apache.hc.core5.io.ShutdownType;
import org.apache.hc.core5.net.InetAddressUtils;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.TlsCapableIOSession;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.ByteArrayBuffer;

abstract class AbstractHttp2StreamMultiplexer implements HttpConnection {

    private static final long LINGER_TIME = 1000; // 1 second

    enum Mode { CLIENT, SERVER}
    enum ConnectionHandshake { READY, ACTIVE, GRACEFUL_SHUTDOWN, SHUTDOWN}
    enum SettingsHandshake { READY, TRANSMITTED, ACKED }

    private final Mode mode;
    private final TlsCapableIOSession ioSession;
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
    private final Map<Integer, Http2Stream> streamMap;
    private final Queue<AsyncPingHandler> pingHandlers;
    private final AtomicInteger connInputWindow;
    private final AtomicInteger connOutputWindow;
    private final Lock outputLock;
    private final AtomicInteger outputRequests;
    private final AtomicInteger lastStreamId;
    private final ConnectionListener connectionListener;
    private final Http2StreamListener streamListener;

    private ConnectionHandshake connState = ConnectionHandshake.READY;
    private SettingsHandshake localSettingState = SettingsHandshake.READY;
    private SettingsHandshake remoteSettingState = SettingsHandshake.READY;
    private H2Config remoteConfig;
    private int lowMark;

    private Continuation continuation;

    private int processedRemoteStreamId;
    private EndpointDetails endpointDetails;

    AbstractHttp2StreamMultiplexer(
            final Mode mode,
            final TlsCapableIOSession ioSession,
            final FrameFactory frameFactory,
            final StreamIdGenerator idGenerator,
            final HttpProcessor httpProcessor,
            final CharCodingConfig charCodingConfig,
            final H2Config h2Config,
            final ConnectionListener connectionListener,
            final Http2StreamListener streamListener) {
        this.mode = Args.notNull(mode, "Mode");
        this.ioSession = Args.notNull(ioSession, "IO session");
        this.frameFactory = Args.notNull(frameFactory, "Frame factory");
        this.idGenerator = Args.notNull(idGenerator, "Stream id generator");
        this.httpProcessor = Args.notNull(httpProcessor, "HTTP processor");
        this.localConfig = h2Config != null ? h2Config : H2Config.DEFAULT;
        this.inputMetrics = new BasicH2TransportMetrics();
        this.outputMetrics = new BasicH2TransportMetrics();
        this.connMetrics = new BasicHttpConnectionMetrics(inputMetrics, outputMetrics);
        this.inputBuffer = new FrameInputBuffer(this.inputMetrics, this.localConfig.getMaxFrameSize());
        this.outputBuffer = new FrameOutputBuffer(this.outputMetrics, this.localConfig.getMaxFrameSize());
        this.outputQueue = new ConcurrentLinkedDeque<>();
        this.pingHandlers = new ConcurrentLinkedQueue<>();
        this.outputLock = new ReentrantLock();
        this.outputRequests = new AtomicInteger(0);
        this.lastStreamId = new AtomicInteger(0);
        this.hPackEncoder = new HPackEncoder(CharCodingSupport.createEncoder(charCodingConfig));
        this.hPackDecoder = new HPackDecoder(CharCodingSupport.createDecoder(charCodingConfig));
        this.streamMap = new ConcurrentHashMap<>();
        this.connInputWindow = new AtomicInteger(localConfig.getInitialWindowSize());
        this.connOutputWindow = new AtomicInteger(H2Config.DEFAULT.getInitialWindowSize());

        this.hPackDecoder.setMaxTableSize(H2Config.DEFAULT.getInitialWindowSize());
        this.hPackEncoder.setMaxTableSize(this.localConfig.getHeaderTableSize());

        this.remoteConfig = H2Config.DEFAULT;
        this.lowMark = this.remoteConfig.getInitialWindowSize() / 2;
        this.connectionListener = connectionListener;
        this.streamListener = streamListener;
    }

    abstract Http2StreamHandler createRemotelyInitiatedStream(
            Http2StreamChannel channel, HttpProcessor httpProcessor, BasicHttpConnectionMetrics connMetrics) throws IOException;

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
            outputBuffer.write(frame, ioSession.channel());
        } else {
            outputQueue.addLast(frame);
        }
        ioSession.setEvent(SelectionKey.OP_WRITE);
    }

    private void commitFrame(final RawFrame frame) throws IOException {
        Args.notNull(frame, "Frame");
        outputLock.lock();
        try {
            commitFrameInternal(frame);
        } finally {
            outputLock.unlock();
        }
    }

    private void commitHeaders(
            final int streamId, final List<? extends Header> headers, final boolean endStream) throws IOException {
        if (streamListener != null) {
            streamListener.onHeaderOutput(this, streamId, headers);
        }
        final ByteArrayBuffer buf = new ByteArrayBuffer(512);
        hPackEncoder.encodeHeaders(buf, headers);

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

        hPackEncoder.encodeHeaders(buf, headers);

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

    private int streamData(
            final int streamId, final AtomicInteger streamOutputWindow, final ByteBuffer payload) throws IOException {
        if (outputBuffer.isEmpty() && outputQueue.isEmpty()) {
            final int capacity = Math.min(connOutputWindow.get(), streamOutputWindow.get());
            if (capacity <= 0) {
                return 0;
            }
            final int frameSize = Math.max(localConfig.getMaxFrameSize(), remoteConfig.getMaxFrameSize());
            final int maxPayloadSize = Math.min(capacity, frameSize);
            final int chunk;
            if (payload.remaining() <= maxPayloadSize) {
                chunk = payload.remaining();
                final RawFrame dataFrame = frameFactory.createData(streamId, payload, false);
                if (streamListener != null) {
                    streamListener.onFrameOutput(this, streamId, dataFrame);
                }
                outputBuffer.write(dataFrame, ioSession.channel());
            } else {
                chunk = maxPayloadSize;
                final int originalLimit = payload.limit();
                try {
                    payload.limit(payload.position() + chunk);
                    final RawFrame dataFrame = frameFactory.createData(streamId, payload, false);
                    if (streamListener != null) {
                        streamListener.onFrameOutput(this, streamId, dataFrame);
                    }
                    outputBuffer.write(dataFrame, ioSession.channel());
                } finally {
                    payload.limit(originalLimit);
                }
            }
            payload.position(payload.position() + chunk);
            updateOutputWindow(0, connOutputWindow, -chunk);
            updateOutputWindow(streamId, streamOutputWindow, -chunk);
            ioSession.setEvent(SelectionKey.OP_WRITE);
            return chunk;
        } else {
            return 0;
        }
    }

    private void updateInputCapacity(
            final int streamId, final AtomicInteger inputWindow, final int inputCapacity) throws IOException {
        if (inputCapacity > 0) {
            final int streamWinSize = inputWindow.get();
            final int chunk = inputCapacity - streamWinSize;
            if (chunk > 0) {
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

    public final void onConnect(final ByteBuffer prefeed) throws HttpException, IOException {
        if (connectionListener != null) {
            connectionListener.onConnect(this);
        }
        if (prefeed != null) {
            inputBuffer.put(prefeed);
        }
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
    }

    public final void onInput() throws HttpException, IOException {
        if (connState == ConnectionHandshake.SHUTDOWN) {
            ioSession.clearEvent(SelectionKey.OP_READ);
        } else {
            RawFrame frame;
            while ((frame = inputBuffer.read(ioSession.channel())) != null) {
                if (streamListener != null) {
                    streamListener.onFrameInput(this, frame.getStreamId(), frame);
                }
                consumeFrame(frame);
            }
        }
    }

    public final void onOutput() throws HttpException, IOException {
        outputLock.lock();
        try {
            if (!outputBuffer.isEmpty()) {
                outputBuffer.flush(ioSession.channel());
            }
            while (outputBuffer.isEmpty()) {
                final RawFrame frame = outputQueue.poll();
                if (frame != null) {
                    if (streamListener != null) {
                        streamListener.onFrameOutput(this, frame.getStreamId(), frame);
                    }
                    outputBuffer.write(frame, ioSession.channel());
                } else {
                    break;
                }
            }
        } finally {
            outputLock.unlock();
        }

        final int connWinSize = connInputWindow.get();
        if (connWinSize < lowMark) {
            final int delta = this.remoteConfig.getInitialWindowSize() - connWinSize;
            if (delta > 0) {
                final RawFrame windowUpdateFrame = frameFactory.createWindowUpdate(0, delta);
                commitFrame(windowUpdateFrame);
                updateInputWindow(0, connInputWindow, delta);
            }
        }

        if (connState.compareTo(ConnectionHandshake.SHUTDOWN) < 0 && remoteSettingState == SettingsHandshake.ACKED) {

            if (connOutputWindow.get() > 0) {
                produceOutput();
            }
            final int pendingOutputRequests = outputRequests.get();
            boolean outputPending = false;
            if (!streamMap.isEmpty() && connOutputWindow.get() > 0) {
                for (final Iterator<Map.Entry<Integer, Http2Stream>> it = streamMap.entrySet().iterator(); it.hasNext(); ) {
                    final Map.Entry<Integer, Http2Stream> entry = it.next();
                    final Http2Stream stream = entry.getValue();
                    if (!stream.isLocalClosed()
                            && stream.getOutputWindow().get() > 0
                            && stream.isOutputReady()) {
                        outputPending = true;
                        break;
                    }
                }
            }
            if (!outputPending) {
                outputLock.lock();
                try {
                    if (!outputBuffer.isEmpty() || !outputQueue.isEmpty()) {
                        outputPending = true;
                    }
                } finally {
                    outputLock.unlock();
                }
            }
            if (!outputPending && outputRequests.compareAndSet(pendingOutputRequests, 0)) {
                ioSession.clearEvent(SelectionKey.OP_WRITE);
            } else {
                outputRequests.addAndGet(-pendingOutputRequests);
            }
        }

        if (connState.compareTo(ConnectionHandshake.ACTIVE) <= 0
                && (remoteSettingState == SettingsHandshake.ACKED || !localConfig.isSettingAckNeeded())) {
            processPendingCommands();
        }
        if (connState.compareTo(ConnectionHandshake.GRACEFUL_SHUTDOWN) == 0) {
            for (final Iterator<Map.Entry<Integer, Http2Stream>> it = streamMap.entrySet().iterator(); it.hasNext(); ) {
                final Map.Entry<Integer, Http2Stream> entry = it.next();
                final Http2Stream stream = entry.getValue();
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
            outputLock.lock();
            try {
                if (outputBuffer.isEmpty() && outputQueue.isEmpty()) {
                    ioSession.close();
                }
            } finally {
                outputLock.unlock();
            }
        }
    }

    public final void onTimeout() throws HttpException, IOException {
        connState = ConnectionHandshake.SHUTDOWN;

        final RawFrame goAway;
        if (localSettingState != SettingsHandshake.ACKED) {
            goAway = frameFactory.createGoAway(processedRemoteStreamId, H2Error.SETTINGS_TIMEOUT, "Setting timeout");
        } else {
            goAway = frameFactory.createGoAway(processedRemoteStreamId, H2Error.NO_ERROR, "Timeout due to inactivity");
        }
        commitFrame(goAway);
        for (final Iterator<Map.Entry<Integer, Http2Stream>> it = streamMap.entrySet().iterator(); it.hasNext(); ) {
            final Map.Entry<Integer, Http2Stream> entry = it.next();
            final Http2Stream stream = entry.getValue();
            stream.reset(new H2StreamResetException(H2Error.NO_ERROR, "Timeout due to inactivity"));
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
        for (final Iterator<Map.Entry<Integer, Http2Stream>> it = streamMap.entrySet().iterator(); it.hasNext(); ) {
            final Map.Entry<Integer, Http2Stream> entry = it.next();
            final Http2Stream stream = entry.getValue();
            stream.cancel();
        }
        for (;;) {
            final Command command = ioSession.getCommand();
            if (command != null) {
                command.cancel();
            } else {
                break;
            }
        }
        if (connectionListener != null) {
            connectionListener.onDisconnect(this);
        }
    }

    private void processPendingCommands() throws IOException, HttpException {
        while (streamMap.size() < remoteConfig.getMaxConcurrentStreams()) {
            final Command command = ioSession.getCommand();
            if (command == null) {
                break;
            }
            if (command instanceof ShutdownCommand) {
                final ShutdownCommand shutdownCommand = (ShutdownCommand) command;
                if (shutdownCommand.getType() == ShutdownType.IMMEDIATE) {
                    for (final Iterator<Map.Entry<Integer, Http2Stream>> it = streamMap.entrySet().iterator(); it.hasNext(); ) {
                        final Map.Entry<Integer, Http2Stream> entry = it.next();
                        final Http2Stream stream = entry.getValue();
                        stream.cancel();
                    }
                    streamMap.clear();
                    connState = ConnectionHandshake.SHUTDOWN;
                } else {
                    if (connState.compareTo(ConnectionHandshake.ACTIVE) <= 0) {
                        connState = ConnectionHandshake.GRACEFUL_SHUTDOWN;
                        final RawFrame goAway = frameFactory.createGoAway(processedRemoteStreamId, H2Error.NO_ERROR, "Graceful shutdown");
                        commitFrame(goAway);
                    }
                }
                break;
            } else if (command instanceof ExecutionCommand) {
                if (mode == Mode.SERVER) {
                    throw new H2ConnectionException(H2Error.INTERNAL_ERROR, "Illegal attempt to execute a request");
                }
                final ExecutionCommand executionCommand = (ExecutionCommand) command;
                final int streamId = generateStreamId();
                final Http2StreamChannelImpl channel = new Http2StreamChannelImpl(
                        streamId,
                        true,
                        localConfig.getInitialWindowSize(),
                        remoteConfig.getInitialWindowSize());
                final AsyncClientExchangeHandler exchangeHandler = executionCommand.getExchangeHandler();
                final HttpCoreContext context = HttpCoreContext.adapt(executionCommand.getContext());
                context.setAttribute(HttpCoreContext.SSL_SESSION, getSSLSession());
                context.setAttribute(HttpCoreContext.CONNECTION_ENDPOINT, getEndpointDetails());
                final Http2StreamHandler streamHandler = new ClientHttp2StreamHandler(
                        channel,
                        httpProcessor,
                        connMetrics,
                        exchangeHandler,
                        context);
                final Http2Stream stream = new Http2Stream(channel, streamHandler, false);
                streamMap.put(streamId, stream);

                if (stream.isOutputReady()) {
                    stream.produceOutput();
                }

                if (!outputQueue.isEmpty()) {
                    return;
                }
            } else if (command instanceof PingCommand) {
                final PingCommand pingCommand = (PingCommand) command;
                final AsyncPingHandler handler = pingCommand.getHandler();
                pingHandlers.add(handler);
                final RawFrame ping = frameFactory.createPing(handler.getData());
                commitFrame(ping);
            }
        }
    }

    public final void onException(final Exception cause) {
        if (connectionListener != null) {
            connectionListener.onError(this, cause);
        }
        try {
            for (;;) {
                final AsyncPingHandler pingHandler = pingHandlers.poll();
                if (pingHandler != null) {
                    pingHandler.failed(cause);
                } else {
                    break;
                }
            }
            for (final Iterator<Map.Entry<Integer, Http2Stream>> it = streamMap.entrySet().iterator(); it.hasNext(); ) {
                final Map.Entry<Integer, Http2Stream> entry = it.next();
                final Http2Stream stream = entry.getValue();
                stream.reset(cause);
            }
            streamMap.clear();
            for (;;) {
                final Command command = ioSession.getCommand();
                if (command != null) {
                    if (command instanceof ExecutionCommand) {
                        final ExecutionCommand executionCommand = (ExecutionCommand) command;
                        final AsyncClientExchangeHandler exchangeHandler = executionCommand.getExchangeHandler();
                        exchangeHandler.failed(cause);
                        exchangeHandler.releaseResources();
                    } else {
                        command.cancel();
                    }
                } else {
                    break;
                }
            }
            if (!(cause instanceof ConnectionClosedException)) {
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
            connState = ConnectionHandshake.SHUTDOWN;
        } catch (final IOException ignore) {
        } finally {
            ioSession.shutdown(ShutdownType.IMMEDIATE);
        }
    }

    private Http2Stream getValidStream(final int streamId) throws H2ConnectionException {
        if (streamId == 0) {
            throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "Illegal stream id: " + streamId);
        }
        final Http2Stream stream = streamMap.get(streamId);
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
                final Http2Stream stream = getValidStream(streamId);
                try {
                    consumeDataFrame(frame, stream);
                } catch (final H2StreamResetException ex) {
                    stream.localReset(ex);
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
                Http2Stream stream = streamMap.get(streamId);
                if (stream == null) {
                    if (mode != Mode.SERVER) {
                        throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "Illegal HEADERS frame");
                    }

                    updateLastStreamId(streamId);

                    final Http2StreamChannelImpl channel = new Http2StreamChannelImpl(
                            streamId,
                            false,
                            localConfig.getInitialWindowSize(),
                            remoteConfig.getInitialWindowSize());
                    final Http2StreamHandler streamHandler = createRemotelyInitiatedStream(
                            channel, httpProcessor, connMetrics);
                    stream = new Http2Stream(channel, streamHandler, true);
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

                final Http2Stream stream = getValidStream(streamId);
                try {

                    consumeContinuationFrame(frame, stream);
                } catch (final H2StreamResetException ex) {
                    stream.localReset(ex);
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
                    final Http2Stream stream = streamMap.get(streamId);
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
                final Http2Stream stream = streamMap.get(streamId);
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
                    stream.reset(new H2StreamResetException(errorCode, "Stream reset"));
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
                if (mode == Mode.SERVER) {
                    throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "Push not supported");
                }
                if (!localConfig.isPushEnabled()) {
                    throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "Push is disabled");
                }

                final Http2Stream stream = getValidStream(streamId);
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

                final Http2StreamChannelImpl channel = new Http2StreamChannelImpl(
                        promisedStreamId,
                        false,
                        localConfig.getInitialWindowSize(),
                        remoteConfig.getInitialWindowSize());
                final Http2StreamHandler streamHandler = createRemotelyInitiatedStream(
                        channel, httpProcessor, connMetrics);
                final Http2Stream promisedStream = new Http2Stream(channel, streamHandler, true);
                streamMap.put(promisedStreamId, promisedStream);

                try {
                    consumePushPromiseFrame(frame, payload, promisedStream);
                } catch (final H2StreamResetException ex) {
                    promisedStream.localReset(ex);
                }
            }
            break;
            case GOAWAY: {
                if (streamId != 0) {
                    throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "Illegal stream id");
                }
                final ByteBuffer payload = frame.getPayload();
                if (payload == null || payload.remaining() < 8) {
                    throw new H2ConnectionException(H2Error.FRAME_SIZE_ERROR, "Invalid RST_STREAM payload");
                }
                final int processedLocalStreamId = payload.getInt();
                final int errorCode = payload.getInt();
                if (errorCode == H2Error.NO_ERROR.getCode()) {
                    if (connState.compareTo(ConnectionHandshake.ACTIVE) <= 0) {
                        connState = ConnectionHandshake.GRACEFUL_SHUTDOWN;
                        for (final Iterator<Map.Entry<Integer, Http2Stream>> it = streamMap.entrySet().iterator(); it.hasNext(); ) {
                            final Map.Entry<Integer, Http2Stream> entry = it.next();
                            final int activeStreamId = entry.getKey();
                            if (!idGenerator.isSameSide(activeStreamId) && activeStreamId > processedLocalStreamId) {
                                final Http2Stream stream = entry.getValue();
                                stream.cancel();
                                it.remove();
                            }
                        }
                    }
                } else {
                    connState = ConnectionHandshake.SHUTDOWN;
                    for (final Iterator<Map.Entry<Integer, Http2Stream>> it = streamMap.entrySet().iterator(); it.hasNext(); ) {
                        final Map.Entry<Integer, Http2Stream> entry = it.next();
                        final Http2Stream stream = entry.getValue();
                        stream.reset(new H2StreamResetException(errorCode, "Connection terminated by the peer"));
                    }
                    streamMap.clear();
                }
            }
            break;
        }
    }

    private void consumeDataFrame(final RawFrame frame, final Http2Stream stream) throws HttpException, IOException {
        final int streamId = stream.getId();
        final ByteBuffer payload = frame.getPayloadContent();
        if (payload != null) {
            final int frameLength = frame.getLength();
            final int streamWinSize = updateInputWindow(streamId, stream.getInputWindow(), -frameLength);
            if (streamWinSize < lowMark && !stream.isRemoteClosed()) {
                stream.produceInputCapacityUpdate();
            }
            final int connWinSize = updateInputWindow(0, connInputWindow, -frameLength);
            if (connWinSize < lowMark) {
                final int chunk = Integer.MAX_VALUE - connWinSize;
                if (chunk > 0) {
                    final RawFrame windowUpdateFrame = frameFactory.createWindowUpdate(0, chunk);
                    commitFrame(windowUpdateFrame);
                    updateInputWindow(0, connInputWindow, chunk);
                }
            }
        }
        if (stream.isResetLocally()) {
            return;
        }
        if (stream.isRemoteClosed()) {
            throw new H2StreamResetException(H2Error.STREAM_CLOSED, "Stream already closed");
        }
        if (frame.isFlagSet(FrameFlag.END_STREAM)) {
            stream.setRemoteEndStream();
        }
        stream.consumeData(payload);
    }

    private void consumePushPromiseFrame(final RawFrame frame, final ByteBuffer payload, final Http2Stream promisedStream) throws HttpException, IOException {
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

    private void consumeHeaderFrame(final RawFrame frame, final Http2Stream stream) throws HttpException, IOException {
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
            final List<Header> headers = hPackDecoder.decodeHeaders(payload);
            if (stream.isRemoteInitiated() && streamId > processedRemoteStreamId) {
                processedRemoteStreamId = streamId;
            }
            if (streamListener != null) {
                streamListener.onHeaderInput(this, streamId, headers);
            }
            if (connState == ConnectionHandshake.GRACEFUL_SHUTDOWN) {
                throw new H2StreamResetException(H2Error.PROTOCOL_ERROR, "Stream refused");
            }
            if (stream.isResetLocally()) {
                return;
            }
            if (stream.isRemoteClosed()) {
                throw new H2StreamResetException(H2Error.STREAM_CLOSED, "Stream already closed");
            }
            if (frame.isFlagSet(FrameFlag.END_STREAM)) {
                stream.setRemoteEndStream();
            }
            stream.consumeHeader(headers);
        } else {
            continuation.copyPayload(payload);
        }
    }

    private void consumeContinuationFrame(final RawFrame frame, final Http2Stream stream) throws HttpException, IOException {
        final int streamId = frame.getStreamId();
        final ByteBuffer payload = frame.getPayload();
        continuation.copyPayload(payload);
        if (frame.isFlagSet(FrameFlag.END_HEADERS)) {
            final List<Header> headers = hPackDecoder.decodeHeaders(continuation.getContent());
            if (stream.isRemoteInitiated() && streamId > processedRemoteStreamId) {
                processedRemoteStreamId = streamId;
            }
            if (streamListener != null) {
                streamListener.onHeaderInput(this, streamId, headers);
            }
            if (connState == ConnectionHandshake.GRACEFUL_SHUTDOWN) {
                throw new H2StreamResetException(H2Error.PROTOCOL_ERROR, "Stream refused");
            }
            if (stream.isResetLocally()) {
                return;
            }
            if (stream.isRemoteClosed()) {
                throw new H2StreamResetException(H2Error.STREAM_CLOSED, "Stream already closed");
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
        final H2Config.Builder configBuilder = H2Config.custom();
        while (payload.hasRemaining()) {
            final int code = payload.getShort();
            final H2Param param = H2Param.valueOf(code);
            if (param != null) {
                final int value = payload.getInt();
                switch (param) {
                    case HEADER_TABLE_SIZE:
                        configBuilder.setHeaderTableSize(value);
                        hPackDecoder.setMaxTableSize(value);
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
                        final int delta = value - remoteConfig.getInitialWindowSize();
                        if (delta != 0) {
                            updateOutputWindow(0, connOutputWindow, delta);
                            if (!streamMap.isEmpty()) {
                                for (final Iterator<Map.Entry<Integer, Http2Stream>> it = streamMap.entrySet().iterator(); it.hasNext(); ) {
                                    final Map.Entry<Integer, Http2Stream> entry = it.next();
                                    final Http2Stream stream = entry.getValue();
                                    try {
                                        updateOutputWindow(stream.getId(), stream.getOutputWindow(), delta);
                                    } catch (final ArithmeticException ex) {
                                        throw new H2ConnectionException(H2Error.FLOW_CONTROL_ERROR, ex.getMessage());
                                    }
                                }
                            }
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
        remoteConfig = configBuilder.build();
        lowMark = remoteConfig.getInitialWindowSize() / 2;
    }

    private void produceOutput() throws HttpException, IOException {
        for (final Iterator<Map.Entry<Integer, Http2Stream>> it = streamMap.entrySet().iterator(); it.hasNext(); ) {
            final Map.Entry<Integer, Http2Stream> entry = it.next();
            final Http2Stream stream = entry.getValue();
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

    @Override
    public void close() throws IOException {
        ioSession.addFirst(new ShutdownCommand(ShutdownType.GRACEFUL));
    }

    @Override
    public void shutdown(final ShutdownType shutdownType) {
        ioSession.shutdown(shutdownType);
    }

    @Override
    public boolean isOpen() {
        return connState == ConnectionHandshake.ACTIVE;
    }

    @Override
    public void setSocketTimeout(final int timeout) {
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
            endpointDetails = new BasicEndpointDetails(ioSession.getRemoteAddress(), ioSession.getLocalAddress(), connMetrics);
        }
        return endpointDetails;
    }

    @Override
    public int getSocketTimeout() {
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

    private class Http2StreamChannelImpl implements Http2StreamChannel {

        private final int id;
        private final AtomicInteger inputWindow;
        private final AtomicInteger outputWindow;

        private volatile boolean idle;
        private volatile boolean remoteEndStream;
        private volatile boolean localEndStream;

        private volatile long deadline;

        Http2StreamChannelImpl(final int id, final boolean idle, final int initialInputWindowSize, final int initialOutputWindowSize) {
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
            outputLock.lock();
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
                outputLock.unlock();
            }
        }

        @Override
        public void push(final List<Header> headers, final AsyncPushProducer pushProducer) throws HttpException, IOException {
            if (mode == Mode.CLIENT) {
                throw new H2ConnectionException(H2Error.INTERNAL_ERROR, "Illegal attempt to push a response");
            }
            final int promisedStreamId = generateStreamId();
            final Http2StreamChannelImpl channel = new Http2StreamChannelImpl(
                    promisedStreamId,
                    true,
                    localConfig.getInitialWindowSize(),
                    remoteConfig.getInitialWindowSize());
            final HttpCoreContext context = HttpCoreContext.create();
            context.setAttribute(HttpCoreContext.SSL_SESSION, getSSLSession());
            context.setAttribute(HttpCoreContext.CONNECTION_ENDPOINT, getEndpointDetails());
            final Http2StreamHandler streamHandler = new ServerPushHttp2StreamHandler(
                    channel, httpProcessor, connMetrics, pushProducer, context);
            final Http2Stream stream = new Http2Stream(channel, streamHandler, false);
            streamMap.put(promisedStreamId, stream);

            outputLock.lock();
            try {
                if (localEndStream) {
                    stream.releaseResources();
                    return;
                }
                commitPushPromise(id, promisedStreamId, headers);
                idle = false;
            } finally {
                outputLock.unlock();
            }
        }

        @Override
        public void update(final int increment) throws IOException {
            if (remoteEndStream) {
                return;
            }
            updateInputCapacity(id, inputWindow, increment);
        }

        @Override
        public int write(final ByteBuffer payload) throws IOException {
            outputLock.lock();
            try {
                if (localEndStream) {
                    return 0;
                }
                return streamData(id, outputWindow, payload);
            } finally {
                outputLock.unlock();
            }
        }

        @Override
        public void endStream(final List<? extends Header> trailers) throws IOException {
            outputLock.lock();
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
                outputLock.unlock();
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

        void setLocalEndStream() {
            localEndStream = true;
        }

        boolean isLocalClosed() {
            return localEndStream;
        }

        boolean isClosed() {
            return remoteEndStream && localEndStream;
        }

        void close() {
            localEndStream = true;
            remoteEndStream = true;
        }

        void localReset(final int code) throws IOException {
            deadline = System.currentTimeMillis() + LINGER_TIME;
            close();
            if (!idle) {
                outputLock.lock();
                try {
                    final RawFrame resetStream = frameFactory.createResetStream(id, code);
                    commitFrameInternal(resetStream);
                } finally {
                    outputLock.unlock();
                }
            }
        }

        void localReset(final H2Error error) throws IOException {
            localReset(error!= null ? error.getCode() : H2Error.INTERNAL_ERROR.getCode());
        }

        long getDeadline() {
            return deadline;
        }

        @Override
        public String toString() {
            return "[" +
                    "id=" + id +
                    ", inputWindow=" + inputWindow +
                    ", outputWindow=" + outputWindow +
                    ", remoteEndStream=" + remoteEndStream +
                    ", localEndStream=" + localEndStream +
                    ']';
        }

    }

    private static class Http2Stream {

        private final Http2StreamChannelImpl channel;
        private final Http2StreamHandler handler;
        private final boolean remoteInitiated;

        private volatile boolean resetLocally;

        private Http2Stream(
                final Http2StreamChannelImpl channel,
                final Http2StreamHandler handler,
                final boolean remoteInitiated) {
            this.channel = channel;
            this.handler = handler;
            this.remoteInitiated = remoteInitiated;
        }

        int getId() {
            return channel.getId();
        }

        public boolean isRemoteInitiated() {
            return remoteInitiated;
        }

        AtomicInteger getOutputWindow() {
            return channel.getOutputWindow();
        }

        AtomicInteger getInputWindow() {
            return channel.getInputWindow();
        }

        boolean isTerminated() {
            return channel.isClosed() && channel.getDeadline() < System.currentTimeMillis();
        }

        boolean isRemoteClosed() {
            return channel.isRemoteClosed();
        }

        boolean isLocalClosed() {
            return channel.isLocalClosed();
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
            channel.close();
            handler.failed(cause);
        }

        void localReset(final Exception cause, final int code) throws IOException {
            resetLocally = true;
            channel.localReset(code);
            handler.failed(cause);
        }

        void localReset(final Exception cause, final H2Error error) throws IOException {
            localReset(cause, error != null ? error.getCode() : H2Error.INTERNAL_ERROR.getCode());
        }

        void localReset(final H2StreamResetException ex) throws IOException {
            localReset(ex, ex.getCode());
        }

        public boolean isResetLocally() {
            return resetLocally;
        }

        void cancel() {
            channel.close();
            handler.cancel();
        }

        void releaseResources() {
            handler.releaseResources();
            channel.requestOutput();
        }

        @Override
        public String toString() {
            return channel.toString();
        }

    }

}
