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
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.UnresolvedAddressException;
import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.http.nio.command.CommandSupport;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.io.SocketTimeoutExceptionFactory;
import org.apache.hc.core5.net.InetAddressUtils;
import org.apache.hc.core5.util.Timeout;

/**
 * Implements the client side of SOCKS protocol version 5 as per https://tools.ietf.org/html/rfc1928. Supports SOCKS username/password
 * authentication as per https://tools.ietf.org/html/rfc1929.
 */
final class SocksProxyProtocolHandler implements IOEventHandler {

    private static final int MAX_COMMAND_CONNECT_LENGTH = 22;

    private static final byte CLIENT_VERSION = 5;

    private static final byte NO_AUTHENTICATION_REQUIRED = 0;

    private static final byte USERNAME_PASSWORD = 2;

    private static final byte USERNAME_PASSWORD_VERSION = 1;

    private static final byte SUCCESS = 0;

    private static final byte COMMAND_CONNECT = 1;

    private static final byte ATYP_DOMAINNAME = 3;


    private enum State {
        SEND_AUTH, RECEIVE_AUTH_METHOD, SEND_USERNAME_PASSWORD, RECEIVE_AUTH, SEND_CONNECT, RECEIVE_RESPONSE_CODE, RECEIVE_ADDRESS_TYPE, RECEIVE_ADDRESS, COMPLETE
    }

    private final ProtocolIOSession ioSession;
    private final Object attachment;
    private final InetSocketAddress targetAddress;
    private final String username;
    private final String password;
    private final IOEventHandlerFactory eventHandlerFactory;

    // a 32 byte buffer is enough for all usual SOCKS negotiations, we expand it if necessary during the processing
    private ByteBuffer buffer = ByteBuffer.allocate(32);
    private State state = State.SEND_AUTH;
    private int remainingResponseSize = -1;

    SocksProxyProtocolHandler(final ProtocolIOSession ioSession, final Object attachment, final InetSocketAddress targetAddress,
            final String username, final String password, final IOEventHandlerFactory eventHandlerFactory) {
        this.ioSession = ioSession;
        this.attachment = attachment;
        this.targetAddress = targetAddress;
        this.username = username;
        this.password = password;
        this.eventHandlerFactory = eventHandlerFactory;
    }

    @Override
    public void connected(final IOSession session) throws IOException {
        this.buffer.put(CLIENT_VERSION);
        this.buffer.put((byte) 1);
        this.buffer.put(NO_AUTHENTICATION_REQUIRED);
        this.buffer.flip();
        session.setEventMask(SelectionKey.OP_WRITE);
    }

    @Override
    public void outputReady(final IOSession session) throws IOException {
        switch (this.state) {
            case SEND_AUTH:
                if (writeAndPrepareRead(session, 2)) {
                    session.setEventMask(SelectionKey.OP_READ);
                    this.state = State.RECEIVE_AUTH_METHOD;
                }
                break;
            case SEND_USERNAME_PASSWORD:
                if (writeAndPrepareRead(session, 2)) {
                    session.setEventMask(SelectionKey.OP_READ);
                    this.state = State.RECEIVE_AUTH;
                }
                break;
            case SEND_CONNECT:
                if (writeAndPrepareRead(session, 2)) {
                    session.setEventMask(SelectionKey.OP_READ);
                    this.state = State.RECEIVE_RESPONSE_CODE;
                }
                break;
            case RECEIVE_AUTH_METHOD:
            case RECEIVE_AUTH:
            case RECEIVE_ADDRESS:
            case RECEIVE_ADDRESS_TYPE:
            case RECEIVE_RESPONSE_CODE:
                session.setEventMask(SelectionKey.OP_READ);
                break;
            case COMPLETE:
                break;
        }
    }

    @Override
    public void inputReady(final IOSession session, final ByteBuffer src) throws IOException {
        if (src != null) {
            try {
                this.buffer.put(src);
            } catch (final BufferOverflowException ex) {
                throw new IOException("Unexpected input data");
            }
        }
        switch (this.state) {
            case RECEIVE_AUTH_METHOD:
                if (fillBuffer(session)) {
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
                        session.setEventMask(SelectionKey.OP_WRITE);
                        this.state = State.SEND_USERNAME_PASSWORD;
                    } else if (serverMethod == NO_AUTHENTICATION_REQUIRED) {
                        prepareConnectCommand();
                        session.setEventMask(SelectionKey.OP_WRITE);
                        this.state = State.SEND_CONNECT;
                    } else {
                        throw new IOException("SOCKS server return unsupported authentication method: " + serverMethod);
                    }
                }
                break;
            case RECEIVE_AUTH:
                if (fillBuffer(session)) {
                    this.buffer.flip();
                    this.buffer.get(); // skip server auth version
                    final byte status = this.buffer.get();
                    if (status != SUCCESS) {
                        throw new IOException("Authentication failed for external SOCKS proxy");
                    }
                    prepareConnectCommand();
                    session.setEventMask(SelectionKey.OP_WRITE);
                    this.state = State.SEND_CONNECT;
                }
                break;
            case RECEIVE_RESPONSE_CODE:
                if (fillBuffer(session)) {
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
                    this.buffer.limit(3);
                    this.state = State.RECEIVE_ADDRESS_TYPE;
                    // deliberate fall-through
                } else {
                    break;
                }
            case RECEIVE_ADDRESS_TYPE:
                if (fillBuffer(session)) {
                    this.buffer.flip();
                    this.buffer.get(); // reserved byte that has no purpose
                    final byte aType = this.buffer.get();
                    final int addressSize;
                    if (aType == InetAddressUtils.IPV4) {
                        addressSize = 4;
                    } else if (aType == InetAddressUtils.IPV6) {
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
                    // deliberate fall-through
                } else {
                    break;
                }
            case RECEIVE_ADDRESS:
                if (fillBuffer(session)) {
                    this.buffer.clear();
                    this.state = State.COMPLETE;
                    final IOEventHandler newHandler = this.eventHandlerFactory.createHandler(this.ioSession, this.attachment);
                    this.ioSession.upgrade(newHandler);
                    newHandler.connected(this.ioSession);
                }
                break;
            case SEND_AUTH:
            case SEND_USERNAME_PASSWORD:
            case SEND_CONNECT:
                session.setEventMask(SelectionKey.OP_WRITE);
                break;
            case COMPLETE:
                break;
        }
    }

    private void prepareConnectCommand() throws IOException {
        final InetAddress address = this.targetAddress.getAddress();
        final int port = this.targetAddress.getPort();
        if (address == null || port == 0) {
            throw new UnresolvedAddressException();
        }

        this.buffer.clear();
        setBufferLimit(MAX_COMMAND_CONNECT_LENGTH);
        this.buffer.put(CLIENT_VERSION);
        this.buffer.put(COMMAND_CONNECT);
        this.buffer.put((byte) 0); // reserved
        if (address instanceof Inet4Address) {
            this.buffer.put(InetAddressUtils.IPV4);
            this.buffer.put(address.getAddress());
        } else if (address instanceof Inet6Address) {
            this.buffer.put(InetAddressUtils.IPV6);
            this.buffer.put(address.getAddress());
        } else {
            throw new IOException("Unsupported remote address class: " + address.getClass().getName());
        }
        this.buffer.putShort((short) port);
        this.buffer.flip();
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

    private boolean writeAndPrepareRead(final ByteChannel channel, final int readSize) throws IOException {
        if (writeBuffer(channel)) {
            this.buffer.clear();
            setBufferLimit(readSize);
            return true;
        }
        return false;
    }

    private boolean writeBuffer(final ByteChannel channel) throws IOException {
        if (this.buffer.hasRemaining()) {
            channel.write(this.buffer);
        }
        return !this.buffer.hasRemaining();
    }

    private boolean fillBuffer(final ByteChannel channel) throws IOException {
        if (this.buffer.hasRemaining()) {
            channel.read(this.buffer);
        }
        return !this.buffer.hasRemaining();
    }

    @Override
    public void timeout(final IOSession session, final Timeout timeout) throws IOException {
        exception(session, SocketTimeoutExceptionFactory.create(timeout));
    }

    @Override
    public void exception(final IOSession session, final Exception cause) {
        session.close(CloseMode.IMMEDIATE);
        CommandSupport.failCommands(session, cause);
    }

    @Override
    public void disconnected(final IOSession session) {
        CommandSupport.cancelCommands(session);
    }

}
