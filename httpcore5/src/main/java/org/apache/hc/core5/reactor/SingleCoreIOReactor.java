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
import java.net.SocketOption;
import java.net.UnknownHostException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.function.Decorator;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.io.Closer;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Asserts;
import org.apache.hc.core5.util.Timeout;

import static org.apache.hc.core5.util.ReflectionUtils.getExtendedSocketOptionOrNull;

@Internal
public class SingleCoreIOReactor extends AbstractSingleCoreIOReactor implements ConnectionInitiator {

    public static final String TCP_KEEPIDLE = "TCP_KEEPIDLE";
    public static final String TCP_KEEPINTERVAL = "TCP_KEEPINTERVAL";
    public static final String TCP_KEEPCOUNT = "TCP_KEEPCOUNT";
    private static final int MAX_CHANNEL_REQUESTS = 10000;

    private final IOEventHandlerFactory eventHandlerFactory;
    private final IOReactorConfig reactorConfig;
    private final Decorator<IOSession> ioSessionDecorator;
    private final IOSessionListener sessionListener;
    private final Callback<IOSession> sessionShutdownCallback;
    private final Queue<InternalDataChannel> closedSessions;
    private final Queue<ChannelEntry> channelQueue;
    private final Queue<IOSessionRequest> requestQueue;
    private final AtomicBoolean shutdownInitiated;
    private final long selectTimeoutMillis;
    private volatile long lastTimeoutCheckMillis;

    SingleCoreIOReactor(
            final Callback<Exception> exceptionCallback,
            final IOEventHandlerFactory eventHandlerFactory,
            final IOReactorConfig reactorConfig,
            final Decorator<IOSession> ioSessionDecorator,
            final IOSessionListener sessionListener,
            final Callback<IOSession> sessionShutdownCallback) {
        super(exceptionCallback);
        this.eventHandlerFactory = Args.notNull(eventHandlerFactory, "Event handler factory");
        this.reactorConfig = Args.notNull(reactorConfig, "I/O reactor config");
        this.ioSessionDecorator = ioSessionDecorator;
        this.sessionListener = sessionListener;
        this.sessionShutdownCallback = sessionShutdownCallback;
        this.shutdownInitiated = new AtomicBoolean(false);
        this.closedSessions = new ConcurrentLinkedQueue<>();
        this.channelQueue = new ConcurrentLinkedQueue<>();
        this.requestQueue = new ConcurrentLinkedQueue<>();
        this.selectTimeoutMillis = this.reactorConfig.getSelectInterval().toMilliseconds();
    }

    void enqueueChannel(final ChannelEntry entry) throws IOReactorShutdownException {
        if (getStatus().compareTo(IOReactorStatus.ACTIVE) > 0) {
            throw new IOReactorShutdownException("I/O reactor has been shut down");
        }
        this.channelQueue.add(entry);
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
        while (!Thread.currentThread().isInterrupted()) {

            final int readyCount = this.selector.select(this.selectTimeoutMillis);

            if (getStatus().compareTo(IOReactorStatus.SHUTTING_DOWN) >= 0) {
                if (this.shutdownInitiated.compareAndSet(false, true)) {
                    initiateSessionShutdown();
                }
                closePendingChannels();
            }
            if (getStatus() == IOReactorStatus.SHUT_DOWN) {
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
            if (getStatus() == IOReactorStatus.ACTIVE) {
                processPendingChannels();
                processPendingConnectionRequests();
            }

            // Exit select loop if graceful shutdown has been completed
            if (getStatus() == IOReactorStatus.SHUTTING_DOWN && this.selector.keys().isEmpty()) {
                break;
            }
            if (getStatus() == IOReactorStatus.SHUT_DOWN) {
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
        if ((currentTimeMillis - this.lastTimeoutCheckMillis) >= this.selectTimeoutMillis) {
            this.lastTimeoutCheckMillis = currentTimeMillis;
            for (final SelectionKey key : this.selector.keys()) {
                checkTimeout(key, currentTimeMillis);
            }
        }
    }

    private void processEvents(final Set<SelectionKey> selectedKeys) {
        for (final SelectionKey key : selectedKeys) {
            final InternalChannel channel = (InternalChannel) key.attachment();
            if (channel != null) {
                try {
                    channel.handleIOEvent(key.readyOps());
                } catch (final CancelledKeyException ex) {
                    channel.close(CloseMode.GRACEFUL);
                }
            }
        }
        selectedKeys.clear();
    }

    private void processPendingChannels() throws IOException {
        ChannelEntry entry;
        for (int i = 0; i < MAX_CHANNEL_REQUESTS && (entry = this.channelQueue.poll()) != null; i++) {
            final SocketChannel socketChannel = entry.channel;
            final Object attachment = entry.attachment;
            try {
                prepareSocket(socketChannel);
                socketChannel.configureBlocking(false);
            } catch (final IOException ex) {
                logException(ex);
                try {
                    socketChannel.close();
                } catch (final IOException ex2) {
                    logException(ex2);
                }
                throw ex;
            }
            final SelectionKey key;
            try {
                key = socketChannel.register(this.selector, SelectionKey.OP_READ);
            } catch (final ClosedChannelException ex) {
                return;
            }
            final IOSession ioSession = new IOSessionImpl("a", key, socketChannel);
            final InternalDataChannel dataChannel = new InternalDataChannel(
                    ioSession,
                    null,
                    ioSessionDecorator,
                    sessionListener,
                    closedSessions);
            dataChannel.setSocketTimeout(this.reactorConfig.getSoTimeout());
            dataChannel.upgrade(this.eventHandlerFactory.createHandler(dataChannel, attachment));
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
            final Timeout timeout,
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

    private void prepareSocket(final SocketChannel socketChannel) throws IOException {
        final Socket socket = socketChannel.socket();
        socket.setTcpNoDelay(this.reactorConfig.isTcpNoDelay());
        socket.setKeepAlive(this.reactorConfig.isSoKeepAlive());
        if (this.reactorConfig.getSndBufSize() > 0) {
            socket.setSendBufferSize(this.reactorConfig.getSndBufSize());
        }
        if (this.reactorConfig.getRcvBufSize() > 0) {
            socket.setReceiveBufferSize(this.reactorConfig.getRcvBufSize());
        }
        if (this.reactorConfig.getTrafficClass() > 0) {
            socket.setTrafficClass(this.reactorConfig.getTrafficClass());
        }
        final int linger = this.reactorConfig.getSoLinger().toSecondsIntBound();
        if (linger >= 0) {
            socket.setSoLinger(true, linger);
        }
        if (this.reactorConfig.getTcpKeepIdle() > 0) {
            setExtendedSocketOption(socketChannel, TCP_KEEPIDLE, this.reactorConfig.getTcpKeepIdle());
        }
        if (this.reactorConfig.getTcpKeepInterval() > 0) {
            setExtendedSocketOption(socketChannel, TCP_KEEPINTERVAL, this.reactorConfig.getTcpKeepInterval());
        }
        if (this.reactorConfig.getTcpKeepInterval() > 0) {
            setExtendedSocketOption(socketChannel, TCP_KEEPCOUNT, this.reactorConfig.getTcpKeepCount());
        }
    }

    /**
     * @since 5.3
     */
    <T> void setExtendedSocketOption(final SocketChannel socketChannel,
                                             final String optionName, final T value) throws IOException {
        final SocketOption<T> socketOption = getExtendedSocketOptionOrNull(optionName);
        if (socketOption == null) {
            throw new UnsupportedOperationException(optionName + " is not supported in the current jdk");
        }
        socketChannel.setOption(socketOption, value);
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
                } catch (final IOException | RuntimeException ex) {
                    Closer.closeQuietly(socketChannel);
                    sessionRequest.failed(ex);
                }
            }
        }
    }

    private void processConnectionRequest(final SocketChannel socketChannel, final IOSessionRequest sessionRequest) throws IOException {
        socketChannel.configureBlocking(false);
        prepareSocket(socketChannel);

        validateAddress(sessionRequest.localAddress);
        if (sessionRequest.localAddress != null) {
            final Socket sock = socketChannel.socket();
            sock.setReuseAddress(this.reactorConfig.isSoReuseAddress());
            sock.bind(sessionRequest.localAddress);
        }

        final SocketAddress socksProxyAddress = reactorConfig.getSocksProxyAddress();
        final SocketAddress remoteAddress = socksProxyAddress != null ? socksProxyAddress : sessionRequest.remoteAddress;

        // Run this under a doPrivileged to support lib users that run under a SecurityManager this allows granting connect permissions
        // only to this library
        validateAddress(remoteAddress);
        final boolean connected;
        try {
            connected = AccessController.doPrivileged(
                    (PrivilegedExceptionAction<Boolean>) () -> socketChannel.connect(remoteAddress));
        } catch (final PrivilegedActionException e) {
            Asserts.check(e.getCause() instanceof  IOException,
                    "method contract violation only checked exceptions are wrapped: " + e.getCause());
            // only checked exceptions are wrapped - error and RTExceptions are rethrown by doPrivileged
            throw (IOException) e.getCause();
        }

        final SelectionKey key = socketChannel.register(this.selector, SelectionKey.OP_CONNECT | SelectionKey.OP_READ);
        final IOSession ioSession = new IOSessionImpl("c", key, socketChannel);
        final InternalDataChannel dataChannel = new InternalDataChannel(
                ioSession,
                sessionRequest.remoteEndpoint,
                ioSessionDecorator,
                sessionListener,
                closedSessions);
        dataChannel.setSocketTimeout(reactorConfig.getSoTimeout());
        final InternalChannel connectChannel = new InternalConnectChannel(
                key,
                socketChannel,
                sessionRequest,
                dataChannel,
                eventHandlerFactory,
                reactorConfig);
        if (connected) {
            connectChannel.handleIOEvent(SelectionKey.OP_CONNECT);
        } else {
            key.attach(connectChannel);
            sessionRequest.assign(connectChannel);
        }
    }

    private void closePendingChannels() {
        ChannelEntry entry;
        while ((entry = this.channelQueue.poll()) != null) {
            final SocketChannel socketChannel = entry.channel;
            try {
                socketChannel.close();
            } catch (final IOException ex) {
                logException(ex);
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
