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

package org.apache.hc.core5.reactor;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.function.Decorator;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.io.Closer;
import org.apache.hc.core5.util.TimeValue;

class SingleCoreIOReactor extends AbstractSingleCoreIOReactor implements ConnectionInitiator {

    private static final int MAX_CHANNEL_REQUESTS = 10000;

    private final IOEventHandlerFactory eventHandlerFactory;
    private final IOReactorConfig reactorConfig;
    private final Decorator<IOSession> ioSessionDecorator;
    private final IOSessionListener sessionListener;
    private final Callback<IOSession> sessionShutdownCallback;
    private final Queue<InternalDataChannel> closedSessions;
    private final Queue<SocketChannel> channelQueue;
    private final Queue<IOSessionRequest> requestQueue;
    private final AtomicBoolean shutdownInitiated;

    private volatile long lastTimeoutCheckMillis;

    SingleCoreIOReactor(
            final Queue<ExceptionEvent> auditLog,
            final IOEventHandlerFactory eventHandlerFactory,
            final IOReactorConfig reactorConfig,
            final Decorator<IOSession> ioSessionDecorator,
            final IOSessionListener sessionListener,
            final Callback<IOSession> sessionShutdownCallback) {
        super(auditLog);
        this.eventHandlerFactory = Args.notNull(eventHandlerFactory, "Event handler factory");
        this.reactorConfig = Args.notNull(reactorConfig, "I/O reactor config");
        this.ioSessionDecorator = ioSessionDecorator;
        this.sessionListener = sessionListener;
        this.sessionShutdownCallback = sessionShutdownCallback;
        this.shutdownInitiated = new AtomicBoolean(false);
        this.closedSessions = new ConcurrentLinkedQueue<>();
        this.channelQueue = new ConcurrentLinkedQueue<>();
        this.requestQueue = new ConcurrentLinkedQueue<>();
    }

    void enqueueChannel(final SocketChannel socketChannel) throws IOReactorShutdownException {
        Args.notNull(socketChannel, "SocketChannel");
        if (getStatus().compareTo(IOReactorStatus.ACTIVE) > 0) {
            throw new IOReactorShutdownException("I/O reactor has been shut down");
        }
        this.channelQueue.add(socketChannel);
        this.selector.wakeup();
    }

    @Override
    void doTerminate() {
        closePendingChannels();
        closePendingConnectionRequests();
        processClosedSessions();
    }

    @Override
    void doExecute() throws IOException {
        final long selectTimeoutMillis = this.reactorConfig.getSelectIntervalMillis();
        while (!Thread.currentThread().isInterrupted()) {

            final int readyCount = this.selector.select(selectTimeoutMillis);

            if (getStatus().compareTo(IOReactorStatus.SHUTTING_DOWN) >= 0) {
                if (this.shutdownInitiated.compareAndSet(false, true)) {
                    initiateSessionShutdown();
                }
                closePendingChannels();
            }
            if (getStatus().compareTo(IOReactorStatus.SHUT_DOWN) == 0) {
                break;
            }

            // Process selected I/O events
            if (readyCount > 0) {
                processEvents(this.selector.selectedKeys());
            }

            validateActiveChannels();

            // Process closed sessions
            processClosedSessions();

            // If active process new channels
            if (getStatus().compareTo(IOReactorStatus.ACTIVE) == 0) {
                processPendingChannels();
                processPendingConnectionRequests();
            }

            // Exit select loop if graceful shutdown has been completed
            if (getStatus().compareTo(IOReactorStatus.SHUTTING_DOWN) == 0 && this.selector.keys().isEmpty()) {
                break;
            }
            if (getStatus().compareTo(IOReactorStatus.SHUT_DOWN) == 0) {
                break;
            }
        }
    }

    private void initiateSessionShutdown() {
        if (this.sessionShutdownCallback != null) {
            final Set<SelectionKey> keys = this.selector.keys();
            for (final SelectionKey key : keys) {
                final InternalChannel channel = (InternalChannel) key.attachment();
                if (channel instanceof InternalDataChannel) {
                    this.sessionShutdownCallback.execute((InternalDataChannel) channel);
                }
            }
        }
    }

    private void validateActiveChannels() {
        final long currentTimeMillis = System.currentTimeMillis();
        if ((currentTimeMillis - this.lastTimeoutCheckMillis) >= this.reactorConfig.getSelectIntervalMillis()) {
            this.lastTimeoutCheckMillis = currentTimeMillis;
            for (final SelectionKey key : this.selector.keys()) {
                checkTimeout(key, currentTimeMillis);
            }
        }
    }

    private void processEvents(final Set<SelectionKey> selectedKeys) {
        for (final SelectionKey key : selectedKeys) {
            final InternalChannel channel = (InternalChannel) key.attachment();
            try {
                channel.handleIOEvent(key.readyOps());
            } catch (final CancelledKeyException ex) {
                channel.close(CloseMode.GRACEFUL);
            }
        }
        selectedKeys.clear();
    }

    private void processPendingChannels() throws IOException {
        SocketChannel socketChannel;
        for (int i = 0; i < MAX_CHANNEL_REQUESTS && (socketChannel = this.channelQueue.poll()) != null; i++) {
            try {
                prepareSocket(socketChannel.socket());
                socketChannel.configureBlocking(false);
            } catch (final IOException ex) {
                addExceptionEvent(ex);
                try {
                    socketChannel.close();
                } catch (final IOException ex2) {
                    addExceptionEvent(ex2);
                }
                throw ex;
            }
            final SelectionKey key;
            try {
                key = socketChannel.register(this.selector, SelectionKey.OP_READ);
            } catch (final ClosedChannelException ex) {
                return;
            }
            IOSession ioSession = new IOSessionImpl(key, socketChannel);
            if (ioSessionDecorator != null) {
                ioSession = ioSessionDecorator.decorate(ioSession);
            }
            final InternalDataChannel dataChannel = new InternalDataChannel(ioSession, null, sessionListener, closedSessions);
            dataChannel.upgrade(this.eventHandlerFactory.createHandler(dataChannel, null));
            dataChannel.setSocketTimeoutMillis(this.reactorConfig.getSoTimeout().toMillisIntBound());
            key.attach(dataChannel);
            dataChannel.handleIOEvent(SelectionKey.OP_CONNECT);
        }
    }

    private void processClosedSessions() {
        for (;;) {
            final InternalDataChannel dataChannel = this.closedSessions.poll();
            if (dataChannel == null) {
                break;
            }
            try {
                dataChannel.disconnected();
            } catch (final CancelledKeyException ex) {
                // ignore and move on
            }
        }
    }

    private void checkTimeout(final SelectionKey key, final long nowMillis) {
        final InternalChannel channel = (InternalChannel) key.attachment();
        if (channel != null) {
            channel.checkTimeout(nowMillis);
        }
    }

    @Override
    public Future<IOSession> connect(
            final NamedEndpoint remoteEndpoint,
            final SocketAddress remoteAddress,
            final SocketAddress localAddress,
            final TimeValue timeout,
            final Object attachment,
            final FutureCallback<IOSession> callback) throws IOReactorShutdownException {
        Args.notNull(remoteEndpoint, "Remote endpoint");
        final IOSessionRequest sessionRequest = new IOSessionRequest(
                remoteEndpoint,
                remoteAddress != null ? remoteAddress : new InetSocketAddress(remoteEndpoint.getHostName(), remoteEndpoint.getPort()),
                localAddress,
                timeout,
                attachment,
                callback);

        this.requestQueue.add(sessionRequest);
        this.selector.wakeup();

        return sessionRequest;
    }

    private void prepareSocket(final Socket socket) throws IOException {
        socket.setTcpNoDelay(this.reactorConfig.isTcpNoDelay());
        socket.setKeepAlive(this.reactorConfig.isSoKeepalive());
        if (this.reactorConfig.getSndBufSize() > 0) {
            socket.setSendBufferSize(this.reactorConfig.getSndBufSize());
        }
        if (this.reactorConfig.getRcvBufSize() > 0) {
            socket.setReceiveBufferSize(this.reactorConfig.getRcvBufSize());
        }
        final int linger = this.reactorConfig.getSoLinger().toSecondsIntBound();
        if (linger >= 0) {
            socket.setSoLinger(true, linger);
        }
    }

    private void validateAddress(final SocketAddress address) throws UnknownHostException {
        if (address instanceof InetSocketAddress) {
            final InetSocketAddress endpoint = (InetSocketAddress) address;
            if (endpoint.isUnresolved()) {
                throw new UnknownHostException(endpoint.getHostName());
            }
        }
    }

    private void processPendingConnectionRequests() {
        IOSessionRequest sessionRequest;
        for (int i = 0; i < MAX_CHANNEL_REQUESTS && (sessionRequest = this.requestQueue.poll()) != null; i++) {
            if (!sessionRequest.isCancelled()) {
                final SocketChannel socketChannel;
                try {
                    socketChannel = SocketChannel.open();
                } catch (final IOException ex) {
                    sessionRequest.failed(ex);
                    return;
                }
                try {
                    processConnectionRequest(socketChannel, sessionRequest);
                } catch (final IOException | SecurityException ex) {
                    Closer.closeQuietly(socketChannel);
                    sessionRequest.failed(ex);
                }
            }
        }
    }

    private void processConnectionRequest(final SocketChannel socketChannel, final IOSessionRequest sessionRequest) throws IOException {
        validateAddress(sessionRequest.localAddress);
        validateAddress(sessionRequest.remoteAddress);

        socketChannel.configureBlocking(false);
        prepareSocket(socketChannel.socket());

        if (sessionRequest.localAddress != null) {
            final Socket sock = socketChannel.socket();
            sock.setReuseAddress(this.reactorConfig.isSoReuseAddress());
            sock.bind(sessionRequest.localAddress);
        }
        final boolean connected = socketChannel.connect(sessionRequest.remoteAddress);
        final SelectionKey key = socketChannel.register(this.selector, SelectionKey.OP_CONNECT | SelectionKey.OP_READ);
        final InternalChannel channel = new InternalConnectChannel(key, socketChannel, sessionRequest, new InternalDataChannelFactory() {

            @Override
            public InternalDataChannel create(
                    final SelectionKey key,
                    final SocketChannel socketChannel,
                    final NamedEndpoint namedEndpoint,
                    final Object attachment) {
                IOSession ioSession = new IOSessionImpl(key, socketChannel);
                if (ioSessionDecorator != null) {
                    ioSession = ioSessionDecorator.decorate(ioSession);
                }
                final InternalDataChannel dataChannel = new InternalDataChannel(ioSession, namedEndpoint, sessionListener, closedSessions);
                dataChannel.upgrade(eventHandlerFactory.createHandler(dataChannel, attachment));
                dataChannel.setSocketTimeoutMillis(reactorConfig.getSoTimeout().toMillisIntBound());
                return dataChannel;
            }

        });
        if (connected) {
            channel.handleIOEvent(SelectionKey.OP_CONNECT);
        } else {
            key.attach(channel);
            sessionRequest.assign(channel);
        }
    }

    private void closePendingChannels() {
        SocketChannel socketChannel;
        while ((socketChannel = this.channelQueue.poll()) != null) {
            try {
                socketChannel.close();
            } catch (final IOException ex) {
                addExceptionEvent(ex);
            }
        }
    }

    private void closePendingConnectionRequests() {
        IOSessionRequest sessionRequest;
        while ((sessionRequest = this.requestQueue.poll()) != null) {
            sessionRequest.cancel();
        }
    }

}
