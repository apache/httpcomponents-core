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
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.io.Closer;
import org.apache.hc.core5.io.SocketTimeoutExceptionFactory;
import org.apache.hc.core5.util.Timeout;

final class InternalConnectChannel extends InternalChannel {

    private final SelectionKey key;
    private final SocketChannel socketChannel;
    private final IOSessionRequest sessionRequest;
    private final InternalDataChannel dataChannel;
    private final IOEventHandlerFactory eventHandlerFactory;
    private final IOReactorConfig reactorConfig;
    private final long creationTimeMillis;

    InternalConnectChannel(
            final SelectionKey key,
            final SocketChannel socketChannel,
            final IOSessionRequest sessionRequest,
            final InternalDataChannel dataChannel,
            final IOEventHandlerFactory eventHandlerFactory,
            final IOReactorConfig reactorConfig) {
        super();
        this.key = key;
        this.socketChannel = socketChannel;
        this.sessionRequest = sessionRequest;
        this.dataChannel = dataChannel;
        this.eventHandlerFactory = eventHandlerFactory;
        this.reactorConfig = reactorConfig;
        this.creationTimeMillis = System.currentTimeMillis();
    }

    @Override
    void onIOEvent(final int readyOps) throws IOException {
        if ((readyOps & SelectionKey.OP_CONNECT) != 0) {
            if (socketChannel.isConnectionPending()) {
                socketChannel.finishConnect();
            }
            //check out connectTimeout
            final long now = System.currentTimeMillis();
            if (checkTimeout(now)) {
                key.attach(dataChannel);
                if (reactorConfig.getSocksProxyAddress() == null) {
                    dataChannel.upgrade(eventHandlerFactory.createHandler(dataChannel, sessionRequest.attachment));
                    sessionRequest.completed(dataChannel);
                    dataChannel.handleIOEvent(SelectionKey.OP_CONNECT);
                } else {
                    final IOEventHandler ioEventHandler = new SocksProxyProtocolHandler(
                            dataChannel, sessionRequest, eventHandlerFactory, reactorConfig);
                    dataChannel.upgrade(ioEventHandler);
                    ioEventHandler.connected(dataChannel);
                }
            }
        }
    }

    @Override
    Timeout getTimeout() {
        return sessionRequest.timeout;
    }

    @Override
    long getLastEventTime() {
        return creationTimeMillis;
    }

    @Override
    void onTimeout(final Timeout timeout) throws IOException {
        sessionRequest.failed(SocketTimeoutExceptionFactory.create(timeout));
        close();
    }

    @Override
    void onException(final Exception cause) {
        sessionRequest.failed(cause);
    }

    @Override
    public void close() throws IOException {
        key.cancel();
        socketChannel.close();
    }

    @Override
    public void close(final CloseMode closeMode) {
        key.cancel();
        Closer.closeQuietly(socketChannel);
    }

    @Override
    public String toString() {
        return sessionRequest.toString();
    }

}
