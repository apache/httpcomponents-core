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
import java.net.SocketTimeoutException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.io.ShutdownType;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.WheelTimeout;

final class InternalConnectChannel extends InternalChannel {

    private final SelectionKey key;
    private final SocketChannel socketChannel;
    private final IOSessionRequest sessionRequest;
    private final long creationTime;
    private final InternalDataChannelFactory dataChannelFactory;

    InternalConnectChannel(
            final SelectionKey key,
            final SocketChannel socketChannel,
            final IOSessionRequest sessionRequest,
            final InternalDataChannelFactory dataChannelFactory) {
        super();
        this.key = key;
        this.socketChannel = socketChannel;
        this.sessionRequest = sessionRequest;
        this.creationTime = System.currentTimeMillis();
        this.dataChannelFactory = dataChannelFactory;
    }

    @Override
    void onIOEvent(final int readyOps) throws IOException {
        if ((readyOps & SelectionKey.OP_CONNECT) != 0) {
            if (socketChannel.isConnectionPending()) {
                socketChannel.finishConnect();
            }
            //if cas return true,that means connect timeout task has not been triggered,timeoutTask can be canceled.
            if (timeOutState.compareAndSet(TimeOutState.NOTSET, TimeOutState.CANCEL)) {
                getWheelTimeOut().cancel();//cancle connect timeout task
                setWheelTimeOut(null);
                final InternalDataChannel dataChannel = dataChannelFactory.create(
                        key,
                        socketChannel,
                        sessionRequest.remoteEndpoint,
                        sessionRequest.attachment);
                //use HashedWheelTimer to trigger read timeout task
                if(dataChannel.getSocketTimeout() > 0){
                    final WheelTimeout readWheelTimeout =  SingleCoreIOReactor.timeWheel.newTimeout(new ReadTimeoutTask(dataChannel), dataChannel.getSocketTimeout(), TimeUnit.MILLISECONDS);
                    dataChannel.setWheelTimeOut(readWheelTimeout);
                }
                key.attach(dataChannel);
                sessionRequest.completed(dataChannel);
                dataChannel.handleIOEvent(SelectionKey.OP_CONNECT);
            }
        }
    }

    @Override
    int getTimeout() {
        return TimeValue.defaultsToZeroMillis(sessionRequest.timeout).toMillisIntBound();
    }

    @Override
    long getLastReadTime() {
        return creationTime;
    }

    @Override
    void onTimeout() throws IOException {
        sessionRequest.failed(new SocketTimeoutException());
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
    public void shutdown(final ShutdownType shutdownType) {
        try {
            close();
        } catch (final IOException ignore) {
        }
    }
}
