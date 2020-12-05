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
import java.net.BindException;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hc.core5.concurrent.BasicFuture;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.io.Closer;

class SingleCoreListeningIOReactor extends AbstractSingleCoreIOReactor implements ConnectionAcceptor {

    private final IOReactorConfig reactorConfig;
    private final Callback<ChannelEntry> callback;
    private final Queue<ListenerEndpointRequest> requestQueue;
    private final ConcurrentMap<ListenerEndpointImpl, Boolean> endpoints;
    private final AtomicBoolean paused;
    private final long selectTimeoutMillis;

    SingleCoreListeningIOReactor(
            final Callback<Exception> exceptionCallback,
            final IOReactorConfig ioReactorConfig,
            final Callback<ChannelEntry> callback) {
        super(exceptionCallback);
        this.reactorConfig = ioReactorConfig != null ? ioReactorConfig : IOReactorConfig.DEFAULT;
        this.callback = callback;
        this.requestQueue = new ConcurrentLinkedQueue<>();
        this.endpoints = new ConcurrentHashMap<>();
        this.paused = new AtomicBoolean(false);
        this.selectTimeoutMillis = this.reactorConfig.getSelectInterval().toMilliseconds();
    }

    @Override
    void doTerminate() {
        ListenerEndpointRequest request;
        while ((request = this.requestQueue.poll()) != null) {
            request.cancel();
        }
    }

    @Override
    protected final void doExecute() throws IOException {
        while (!Thread.currentThread().isInterrupted()) {
            if (getStatus() != IOReactorStatus.ACTIVE) {
                break;
            }

            final int readyCount = this.selector.select(this.selectTimeoutMillis);

            if (getStatus() != IOReactorStatus.ACTIVE) {
                break;
            }

            processEvents(readyCount);
        }
    }

    private void processEvents(final int readyCount) throws IOException {
        if (!this.paused.get()) {
            processSessionRequests();
        }

        if (readyCount > 0) {
            final Set<SelectionKey> selectedKeys = this.selector.selectedKeys();
            for (final SelectionKey key : selectedKeys) {

                processEvent(key);

            }
            selectedKeys.clear();
        }
    }

    private void processEvent(final SelectionKey key) throws IOException {
        try {

            if (key.isAcceptable()) {

                final ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
                for (;;) {
                    final SocketChannel socketChannel = serverChannel.accept();
                    if (socketChannel == null) {
                        break;
                    }
                    final ListenerEndpointRequest endpointRequest = (ListenerEndpointRequest) key.attachment();
                    this.callback.execute(new ChannelEntry(socketChannel, endpointRequest.attachment));
                }
            }

        } catch (final CancelledKeyException ex) {
            final ListenerEndpointImpl endpoint = (ListenerEndpointImpl) key.attachment();
            this.endpoints.remove(endpoint);
            key.attach(null);
        }
    }

    public Future<ListenerEndpoint> listen(
            final SocketAddress address, final Object attachment, final FutureCallback<ListenerEndpoint> callback) {
        if (getStatus().compareTo(IOReactorStatus.SHUTTING_DOWN) >= 0) {
            throw new IOReactorShutdownException("I/O reactor has been shut down");
        }
        final BasicFuture<ListenerEndpoint> future = new BasicFuture<>(callback);
        this.requestQueue.add(new ListenerEndpointRequest(address, attachment, future));
        this.selector.wakeup();
        return future;
    }

    @Override
    public Future<ListenerEndpoint> listen(final SocketAddress address, final FutureCallback<ListenerEndpoint> callback) {
        return listen(address, null, callback);
    }

    private void processSessionRequests() throws IOException {
        ListenerEndpointRequest request;
        while ((request = this.requestQueue.poll()) != null) {
            if (request.isCancelled()) {
                continue;
            }
            final SocketAddress address = request.address;
            final ServerSocketChannel serverChannel = ServerSocketChannel.open();
            try {
                final ServerSocket socket = serverChannel.socket();
                socket.setReuseAddress(this.reactorConfig.isSoReuseAddress());
                if (this.reactorConfig.getRcvBufSize() > 0) {
                    socket.setReceiveBufferSize(this.reactorConfig.getRcvBufSize());
                }
                serverChannel.configureBlocking(false);

                try {
                    socket.bind(address, this.reactorConfig.getBacklogSize());
                } catch (final BindException ex) {
                    final BindException detailedEx = new BindException(
                            String.format("Socket bind failure for socket %s, address=%s, BacklogSize=%d: %s", socket,
                                    address, this.reactorConfig.getBacklogSize(), ex));
                    detailedEx.setStackTrace(ex.getStackTrace());
                    throw detailedEx;
                }

                final SelectionKey key = serverChannel.register(this.selector, SelectionKey.OP_ACCEPT);
                key.attach(request);
                final ListenerEndpointImpl endpoint = new ListenerEndpointImpl(key, request.attachment, socket.getLocalSocketAddress());
                this.endpoints.put(endpoint, Boolean.TRUE);
                request.completed(endpoint);
            } catch (final IOException ex) {
                Closer.closeQuietly(serverChannel);
                request.failed(ex);
            }
        }
    }

    @Override
    public Set<ListenerEndpoint> getEndpoints() {
        final Set<ListenerEndpoint> set = new HashSet<>();
        final Iterator<ListenerEndpointImpl> it = this.endpoints.keySet().iterator();
        while (it.hasNext()) {
            final ListenerEndpoint endpoint = it.next();
            if (!endpoint.isClosed()) {
                set.add(endpoint);
            } else {
                it.remove();
            }
        }
        return set;
    }

    @Override
    public void pause() throws IOException {
        if (paused.compareAndSet(false, true)) {
            final Iterator<ListenerEndpointImpl> it = this.endpoints.keySet().iterator();
            while (it.hasNext()) {
                final ListenerEndpointImpl endpoint = it.next();
                if (!endpoint.isClosed()) {
                    endpoint.close();
                    this.requestQueue.add(new ListenerEndpointRequest(endpoint.address, endpoint.attachment, null));
                }
                it.remove();
            }
        }
    }

    @Override
    public void resume() throws IOException {
        if (paused.compareAndSet(true, false)) {
            this.selector.wakeup();
        }
    }

}
