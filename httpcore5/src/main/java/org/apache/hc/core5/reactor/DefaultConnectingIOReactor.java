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
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadFactory;

import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Asserts;

/**
 * Default implementation of {@link ConnectingIOReactor}. This class extends
 * {@link AbstractMultiworkerIOReactor} with capability to connect to remote
 * hosts.
 *
 * @since 4.0
 */
public class DefaultConnectingIOReactor extends AbstractMultiworkerIOReactor
        implements ConnectingIOReactor {

    private final Queue<SessionRequestImpl> requestQueue;

    private final long selectInterval;
    private long lastTimeoutCheck;

    public DefaultConnectingIOReactor(
            final IOEventHandlerFactory eventHandlerFactory,
            final IOReactorConfig reactorConfig,
            final ThreadFactory threadFactory,
            final Callback<IOSession> sessionShutdownCallback) throws IOReactorException {
        super(eventHandlerFactory, reactorConfig, threadFactory, sessionShutdownCallback);
        this.requestQueue = new ConcurrentLinkedQueue<>();
        this.selectInterval = this.reactorConfig.getSelectInterval();
        this.lastTimeoutCheck = System.currentTimeMillis();
    }

    public DefaultConnectingIOReactor(
            final IOEventHandlerFactory eventHandlerFactory,
            final IOReactorConfig config,
            final Callback<IOSession> sessionShutdownCallback) throws IOReactorException {
        this(eventHandlerFactory, config, null, sessionShutdownCallback);
    }

    /**
     * Creates an instance of DefaultConnectingIOReactor with default configuration.
     *
     * @throws IOReactorException in case if a non-recoverable I/O error.
     *
     * @since 5.0
     */
    public DefaultConnectingIOReactor(
            final IOEventHandlerFactory eventHandlerFactory) throws IOReactorException {
        this(eventHandlerFactory, null, null);
    }

    @Override
    protected void cancelRequests() {
        SessionRequestImpl request;
        while ((request = this.requestQueue.poll()) != null) {
            request.cancel();
        }
    }

    @Override
    protected void processEvents(final int readyCount) throws IOReactorException {
        processSessionRequests();

        if (readyCount > 0) {
            final Set<SelectionKey> selectedKeys = this.selector.selectedKeys();
            for (final SelectionKey key : selectedKeys) {

                processEvent(key);

            }
            selectedKeys.clear();
        }

        final long currentTime = System.currentTimeMillis();
        if ((currentTime - this.lastTimeoutCheck) >= this.selectInterval) {
            this.lastTimeoutCheck = currentTime;
            final Set<SelectionKey> keys = this.selector.keys();
            processTimeouts(keys);
        }
    }

    private void processEvent(final SelectionKey key) {
        try {

            if (key.isConnectable()) {

                final SocketChannel socketChannel = (SocketChannel) key.channel();
                // Get request handle
                final SessionRequestHandle requestHandle = (SessionRequestHandle) key.attachment();
                final SessionRequestImpl sessionRequest = requestHandle.getSessionRequest();

                // Finish connection process
                try {
                    socketChannel.finishConnect();
                } catch (final IOException ex) {
                    sessionRequest.failed(ex);
                }
                key.cancel();
                key.attach(null);
                if (!sessionRequest.isCompleted()) {
                    enqueuePendingSession(socketChannel, sessionRequest);
                } else {
                    try {
                        socketChannel.close();
                    } catch (final IOException ignore) {
                    }
                }
            }

        } catch (final CancelledKeyException ex) {
            final SessionRequestHandle requestHandle = (SessionRequestHandle) key.attachment();
            key.attach(null);
            if (requestHandle != null) {
                final SessionRequestImpl sessionRequest = requestHandle.getSessionRequest();
                if (sessionRequest != null) {
                    sessionRequest.cancel();
                }
            }
        }
    }

    private void processTimeouts(final Set<SelectionKey> keys) {
        final long now = System.currentTimeMillis();
        for (final SelectionKey key : keys) {
            final Object attachment = key.attachment();

            if (attachment instanceof SessionRequestHandle) {
                final SessionRequestHandle handle = (SessionRequestHandle) key.attachment();
                final SessionRequestImpl sessionRequest = handle.getSessionRequest();
                final int timeout = sessionRequest.getConnectTimeout();
                if (timeout > 0) {
                    if (handle.getRequestTime() + timeout < now) {
                        sessionRequest.timeout();
                    }
                }
            }

        }
    }

    @Override
    public SessionRequest connect(
            final NamedEndpoint remoteEndpoint,
            final SocketAddress remoteAddress,
            final SocketAddress localAddress,
            final Object attachment,
            final SessionRequestCallback callback) {
        Args.notNull(remoteEndpoint, "Remote endpoint");
        final IOReactorStatus status = getStatus();
        Asserts.check(status == IOReactorStatus.INACTIVE || status == IOReactorStatus.ACTIVE, "I/O reactor has been shut down");
        final SessionRequestImpl sessionRequest = new SessionRequestImpl(
                remoteEndpoint,
                remoteAddress != null ? remoteAddress : new InetSocketAddress(remoteEndpoint.getHostName(), remoteEndpoint.getPort()),
                localAddress,
                attachment,
                callback);

        this.requestQueue.add(sessionRequest);
        this.selector.wakeup();

        return sessionRequest;
    }

    private void validateAddress(final SocketAddress address) throws UnknownHostException {
        if (address == null) {
            return;
        }
        if (address instanceof InetSocketAddress) {
            final InetSocketAddress endpoint = (InetSocketAddress) address;
            if (endpoint.isUnresolved()) {
                throw new UnknownHostException(endpoint.getHostName());
            }
        }
    }

    private void processSessionRequests() throws IOReactorException {
        SessionRequestImpl request;
        while ((request = this.requestQueue.poll()) != null) {
            if (request.isCompleted()) {
                continue;
            }
            final SocketChannel socketChannel;
            try {
                socketChannel = SocketChannel.open();
            } catch (final IOException ex) {
                request.failed(ex);
                return;
            }
            try {
                validateAddress(request.getLocalAddress());
                validateAddress(request.getRemoteAddress());

                socketChannel.configureBlocking(false);
                prepareSocket(socketChannel.socket());

                if (request.getLocalAddress() != null) {
                    final Socket sock = socketChannel.socket();
                    sock.setReuseAddress(this.reactorConfig.isSoReuseAddress());
                    sock.bind(request.getLocalAddress());
                }
                final boolean connected = socketChannel.connect(request.getRemoteAddress());
                if (connected) {
                    enqueuePendingSession(socketChannel, request);
                    continue;
                }
            } catch (final IOException ex) {
                closeChannel(socketChannel);
                request.failed(ex);
                return;
            }

            final SessionRequestHandle requestHandle = new SessionRequestHandle(request);
            try {
                final SelectionKey key = socketChannel.register(this.selector, SelectionKey.OP_CONNECT,
                        requestHandle);
                request.setKey(key);
            } catch (final IOException ex) {
                closeChannel(socketChannel);
                throw new IOReactorException("Failure registering channel " +
                        "with the selector", ex);
            }
        }
    }

}
