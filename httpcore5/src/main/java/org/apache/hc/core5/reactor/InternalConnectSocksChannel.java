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
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnresolvedAddressException;

import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.io.SocketTimeoutExceptionFactory;
import org.apache.hc.core5.util.Timeout;

final class InternalConnectSocksChannel extends InternalChannel {

    public static final byte CLIENT_VERSION = 5;

    public static final byte NO_AUTHENTICATION_REQUIRED = 0;

    public static final byte USERNAME_PASSWORD = 2;

    public static final byte USERNAME_PASSWORD_VERSION = 1;

    public static final byte SUCCESS = 0;

    public static final byte COMMAND_CONNECT = 1;

    public static final byte ATYP_IPV4 = 1;

    public static final byte ATYP_DOMAINNAME = 3;

    public static final byte ATYP_IPV6 = 4;

    private static enum State { SEND_AUTH, RECEIVE_AUTH, SEND_CONNECT, RECEIVE_RESPONSE_CODE, RECEIVE_ADDRESS_TYPE, RECEIVE_ADDRESS, COMPLETE }

    private final SelectionKey key;
    private final SocketChannel socketChannel;
    private final IOSessionRequest sessionRequest;
    private final long creationTimeMillis;
    private final InternalDataChannelFactory dataChannelFactory;

    // the largest buffer we can possibly ever need is 0xFF to read a max length domain name address + 2 bytes for the port number
    private ByteBuffer buffer = ByteBuffer.allocate(0xFF + 2);
    private State state = State.SEND_AUTH;
    private int remainingResponseSize = -1;

    InternalConnectSocksChannel(
            final SelectionKey key,
            final SocketChannel socketChannel,
            final IOSessionRequest sessionRequest,
            final InternalDataChannelFactory dataChannelFactory) {
        super();
        this.key = key;
        this.socketChannel = socketChannel;
        this.sessionRequest = sessionRequest;
        this.creationTimeMillis = System.currentTimeMillis();
        this.dataChannelFactory = dataChannelFactory;
    }

    public void doConnect() {
        buffer.put(CLIENT_VERSION);
        buffer.put((byte) 1);
        buffer.put(NO_AUTHENTICATION_REQUIRED);
        buffer.flip();
        this.key.interestOps(SelectionKey.OP_WRITE);
    }

    @Override
    void onIOEvent(final int readyOps) throws IOException {
        switch(this.state) {
            case SEND_AUTH:
                if (writeBuffer(readyOps)) {
                    this.buffer.clear();
                    this.state = State.RECEIVE_AUTH;
                    this.key.interestOps(SelectionKey.OP_READ);
                }
                break;
            case RECEIVE_AUTH:
                fillBuffer(readyOps);
                if (this.buffer.position() >= 2) {
                    this.buffer.flip();
                    final byte serverVersion = this.buffer.get();
                    final byte serverMethod = this.buffer.get();
                    if (serverVersion != CLIENT_VERSION) {
                        throw new IOException("SOCKS server returned unsupported version: " + serverVersion);
                    }
                    if (serverMethod != NO_AUTHENTICATION_REQUIRED) {
                        throw new IOException("SOCKS server return unsupported authentication method: " + serverMethod);
                    }
                    if (this.buffer.hasRemaining()) {
                       throw new IOException("SOCKS server sent unexpected response content");
                    }

                    this.buffer.clear();
                    buildConnectCommand();
                    this.buffer.flip();

                    this.key.interestOps(SelectionKey.OP_WRITE);
                    this.state = State.SEND_CONNECT;
                }
                break;
            case SEND_CONNECT:
                if (writeBuffer(readyOps)) {
                    this.buffer.clear();
                    this.state = State.RECEIVE_RESPONSE_CODE;
                    this.key.interestOps(SelectionKey.OP_READ);
                }
                break;
            case RECEIVE_RESPONSE_CODE:
                fillBuffer(readyOps);
                if (this.buffer.position() < 2) {
                    break;
                } else {
                    this.buffer.flip();
                    final byte serverVersion = this.buffer.get();
                    final byte responseCode = this.buffer.get();
                    if (serverVersion != CLIENT_VERSION) {
                        throw new IOException("SOCKS server returned unsupported version: " + serverVersion);
                    }
                    if (responseCode != SUCCESS) {
                        throw new IOException("SOCKS server was unable to establish connection returned error code: " + responseCode);
                    }
                    this.buffer.compact();
                    this.state = State.RECEIVE_ADDRESS_TYPE;
                }
            case RECEIVE_ADDRESS_TYPE:
                fillBuffer(readyOps);
                if (this.buffer.position() < 3) {
                    break;
                } else {
                    this.buffer.flip();
                    this.buffer.get(); // reserved byte that has no purpose
                    final byte aType = this.buffer.get();
                    final int addressSize;
                    if (aType == ATYP_IPV4) {
                        addressSize = 4;
                    } else if (aType == ATYP_IPV6) {
                        addressSize = 16;
                    } else if (aType == ATYP_DOMAINNAME) {
                        // mask with 0xFF to convert to unsigned byte value
                        addressSize = this.buffer.get() & 0xFF;
                    } else {
                        throw new IOException("SOCKS server returned unsupported address type: " + aType);
                    }
                    this.remainingResponseSize = addressSize + 2;
                    this.buffer.compact();
                    // make sure we only read what we need to, don't read too much
                    this.buffer.limit(this.remainingResponseSize);
                    this.state = State.RECEIVE_ADDRESS;
                }
            case RECEIVE_ADDRESS:
                fillBuffer(readyOps);
                if (this.buffer.position() == this.remainingResponseSize) {
                    this.buffer.clear();
                    state = State.COMPLETE;
                }
                break;
            case COMPLETE:
                break;
        }

        if (this.state == State.COMPLETE) {
            final InternalDataChannel dataChannel = dataChannelFactory.create(
                    key,
                    socketChannel,
                    sessionRequest.remoteEndpoint,
                    sessionRequest.attachment);
            key.attach(dataChannel);
            sessionRequest.completed(dataChannel);
            dataChannel.handleIOEvent(SelectionKey.OP_CONNECT);
        }
    }

    private void buildConnectCommand() throws IOException {
        final InetSocketAddress targetAddress = (InetSocketAddress) sessionRequest.remoteAddress;
        final InetAddress address = targetAddress.getAddress();
        final int port = targetAddress.getPort();
        if (address == null || port == 0) {
            throw new UnresolvedAddressException();
        }

        this.buffer.put(CLIENT_VERSION);
        this.buffer.put(COMMAND_CONNECT);
        this.buffer.put((byte) 0); // reserved
        if (address instanceof Inet4Address) {
            this.buffer.put(ATYP_IPV4);
            this.buffer.put(address.getAddress());
        } else if (address instanceof Inet6Address) {
            this.buffer.put(ATYP_IPV6);
            this.buffer.put(address.getAddress());
        } else {
            throw new IOException("Unsupported remote address class: " + address.getClass().getName());
        }
        this.buffer.putShort((short) port);
    }

    private boolean writeBuffer(final int readyOps) throws IOException {
        if (this.buffer.hasRemaining() && (readyOps & SelectionKey.OP_WRITE) != 0) {
            this.socketChannel.write(this.buffer);
        }
        return !this.buffer.hasRemaining();
    }

    private boolean fillBuffer(final int readyOps) throws IOException {
        if ((readyOps & SelectionKey.OP_READ) != 0) {
            return this.socketChannel.read(this.buffer) > 0;
        }
        return false;
    }

    @Override
    Timeout getTimeout() {
        return sessionRequest.timeout;
    }

    @Override
    long getLastReadTime() {
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
        try {
            close();
        } catch (final IOException ignore) {
        }
    }
}
