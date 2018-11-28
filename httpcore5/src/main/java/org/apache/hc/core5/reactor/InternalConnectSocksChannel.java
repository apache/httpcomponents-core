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
import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.io.SocketTimeoutExceptionFactory;
import org.apache.hc.core5.util.Timeout;

/**
 * Implements the client side of SOCKS protocol version 5 as per https://tools.ietf.org/html/rfc1928.
 * Supports SOCKS username/password authentication as per https://tools.ietf.org/html/rfc1929.
 *
 * @author dmap
 */
final class InternalConnectSocksChannel extends InternalChannel {

    private static final int MAX_COMMAND_CONNECT_LENGTH = 22;

    private static final byte CLIENT_VERSION = 5;

    private static final byte NO_AUTHENTICATION_REQUIRED = 0;

    private static final byte USERNAME_PASSWORD = 2;

    private static final byte USERNAME_PASSWORD_VERSION = 1;

    private static final byte SUCCESS = 0;

    private static final byte COMMAND_CONNECT = 1;

    private static final byte ATYP_IPV4 = 1;

    private static final byte ATYP_DOMAINNAME = 3;

    private static final byte ATYP_IPV6 = 4;

    private static enum State { SEND_AUTH, RECEIVE_AUTH_METHOD, SEND_USERNAME_PASSWORD, RECEIVE_AUTH, SEND_CONNECT, RECEIVE_RESPONSE_CODE, RECEIVE_ADDRESS_TYPE, RECEIVE_ADDRESS, COMPLETE }

    private final SelectionKey key;
    private final SocketChannel socketChannel;
    private final IOSessionRequest sessionRequest;
    private final InternalDataChannelFactory dataChannelFactory;
    private final String username;
    private final String password;

    private volatile long lastReadTime;

    // a 32 byte buffer is enough for all usual SOCKS negotiations, we expand it if necessary during the processing
    private ByteBuffer buffer = ByteBuffer.allocate(32);
    private State state = State.SEND_AUTH;

    InternalConnectSocksChannel(
            final SelectionKey key,
            final SocketChannel socketChannel,
            final IOSessionRequest sessionRequest,
            final InternalDataChannelFactory dataChannelFactory,
            final String username,
            final String password) {
        super();
        this.key = key;
        this.socketChannel = socketChannel;
        this.sessionRequest = sessionRequest;
        this.dataChannelFactory = dataChannelFactory;
        this.username = username;
        this.password = password;
        this.lastReadTime = System.currentTimeMillis();
    }

    public void doConnect() {
        buffer.put(CLIENT_VERSION);
        if (this.username != null && this.password != null) {
            buffer.put((byte) 2);
            buffer.put(NO_AUTHENTICATION_REQUIRED);
            buffer.put(USERNAME_PASSWORD);
        } else {
            buffer.put((byte) 1);
            buffer.put(NO_AUTHENTICATION_REQUIRED);
        }
        buffer.flip();
        this.key.interestOps(SelectionKey.OP_WRITE);
    }

    @Override
    void onIOEvent(final int readyOps) throws IOException {
        switch(this.state) {
            case SEND_AUTH:
                if (writeAndPrepareRead(readyOps, 2)) {
                    this.state = State.RECEIVE_AUTH_METHOD;
                }
                break;
            case RECEIVE_AUTH_METHOD:
                if (fillBuffer(readyOps)) {
                    this.buffer.flip();
                    final byte serverVersion = this.buffer.get();
                    final byte serverMethod = this.buffer.get();
                    if (serverVersion != CLIENT_VERSION) {
                        throw new IOException("SOCKS server returned unsupported version: " + serverVersion);
                    }
                    if (serverMethod == USERNAME_PASSWORD) {
                        this.buffer.clear();
                        setBufferLimit(this.username.length() + this.password.length() + 3);
                        this.buffer.put(USERNAME_PASSWORD_VERSION);
                        this.buffer.put((byte) this.username.length());
                        this.buffer.put(this.username.getBytes(StandardCharsets.ISO_8859_1));
                        this.buffer.put((byte) this.password.length());
                        this.buffer.put(this.password.getBytes(StandardCharsets.ISO_8859_1));
                        this.key.interestOps(SelectionKey.OP_WRITE);
                        this.state = State.SEND_USERNAME_PASSWORD;
                    } else if (serverMethod == NO_AUTHENTICATION_REQUIRED) {
                        prepareConnectCommand();
                        this.state = State.SEND_CONNECT;
                    } else {
                        throw new IOException("SOCKS server return unsupported authentication method: " + serverMethod);
                    }
                }
                break;
            case SEND_USERNAME_PASSWORD:
                if (writeAndPrepareRead(readyOps, 2)) {
                    this.state = State.RECEIVE_AUTH;
                }
                break;
            case RECEIVE_AUTH:
                if (fillBuffer(readyOps)) {
                    this.buffer.flip();
                    this.buffer.get(); // skip server auth version
                    final byte status = this.buffer.get();
                    if (status != SUCCESS) {
                        throw new IOException("Authentication failed for external SOCKS proxy");
                    }
                    prepareConnectCommand();
                    this.state = State.SEND_CONNECT;
                }
                break;
            case SEND_CONNECT:
                if (writeAndPrepareRead(readyOps, 2)) {
                    this.state = State.RECEIVE_RESPONSE_CODE;
                }
                break;
            case RECEIVE_RESPONSE_CODE:
                if (fillBuffer(readyOps)) {
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
                    setBufferLimit(3);
                    this.state = State.RECEIVE_ADDRESS_TYPE;
                    // deliberate fall-through
                } else {
                    break;
                }
            case RECEIVE_ADDRESS_TYPE:
                if (fillBuffer(readyOps)) {
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
                    this.buffer.compact();
                    // make sure we only read what we need to, the address + 2 bytes for the port number
                    setBufferLimit(addressSize + 2);
                    this.state = State.RECEIVE_ADDRESS;
                    // deliberate fall-through
                } else {
                    break;
                }
            case RECEIVE_ADDRESS:
                if (fillBuffer(readyOps)) {
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

    private void setBufferLimit(final int newLimit) {
        if (this.buffer.capacity() < newLimit) {
            final ByteBuffer newBuffer = ByteBuffer.allocate(newLimit);
            this.buffer.flip();
            newBuffer.put(this.buffer);
            this.buffer = newBuffer;
        } else {
            this.buffer.limit(newLimit);
        }
    }

    private boolean writeAndPrepareRead(final int readyOps, final int readSize) throws IOException {
        if (writeBuffer(readyOps)) {
            this.buffer.clear();
            setBufferLimit(readSize);
            this.key.interestOps(SelectionKey.OP_READ);
            return true;
        }
        return false;
    }

    private void prepareConnectCommand() throws IOException {
        final InetSocketAddress targetAddress = (InetSocketAddress) sessionRequest.remoteAddress;
        final InetAddress address = targetAddress.getAddress();
        final int port = targetAddress.getPort();
        if (address == null || port == 0) {
            throw new UnresolvedAddressException();
        }

        this.buffer.clear();
        setBufferLimit(MAX_COMMAND_CONNECT_LENGTH);
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
        this.buffer.flip();

        this.key.interestOps(SelectionKey.OP_WRITE);
    }

    private boolean writeBuffer(final int readyOps) throws IOException {
        if (this.buffer.hasRemaining() && (readyOps & SelectionKey.OP_WRITE) != 0) {
            this.socketChannel.write(this.buffer);
        }
        return !this.buffer.hasRemaining();
    }

    private boolean fillBuffer(final int readyOps) throws IOException {
        if (this.buffer.hasRemaining() && (readyOps & SelectionKey.OP_READ) != 0) {
           if (this.socketChannel.read(this.buffer) > 0) {
               this.lastReadTime = System.currentTimeMillis();
           }
        }
        return !this.buffer.hasRemaining();
    }

    @Override
    Timeout getTimeout() {
        return sessionRequest.timeout;
    }

    @Override
    long getLastReadTime() {
        return this.lastReadTime;
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
