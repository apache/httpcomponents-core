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

    private static final int MAX_DNS_NAME_LENGTH = 255;

    private static final int MAX_COMMAND_CONNECT_LENGTH = 6 + MAX_DNS_NAME_LENGTH + 1;

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

    private final InternalDataChannel dataChannel;
    private final IOSessionRequest sessionRequest;
    private final IOEventHandlerFactory eventHandlerFactory;
    private final IOReactorConfig reactorConfig;

    private ByteBuffer buffer = ByteBuffer.allocate(512);
    private State state = State.SEND_AUTH;
    SocksProxyProtocolHandler(final InternalDataChannel dataChannel,
                              final IOSessionRequest sessionRequest,
                              final IOEventHandlerFactory eventHandlerFactory,
                              final IOReactorConfig reactorConfig) {
        this.dataChannel = dataChannel;
        this.sessionRequest = sessionRequest;
        this.eventHandlerFactory = eventHandlerFactory;
        this.reactorConfig = reactorConfig;
    }

    @Override
    public void connected(final IOSession session) throws IOException {
        this.buffer.put(CLIENT_VERSION);
        if (this.reactorConfig.getSocksProxyUsername() != null && this.reactorConfig.getSocksProxyPassword() != null) {
            this.buffer.put((byte) 2);
            this.buffer.put(NO_AUTHENTICATION_REQUIRED);
            this.buffer.put(USERNAME_PASSWORD);
        } else {
            this.buffer.put((byte) 1);
            this.buffer.put(NO_AUTHENTICATION_REQUIRED);
        }
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

    private byte[] cred(final String cred) throws IOException {
        if (cred == null) {
            return new byte[] {};
        }
        // These will remain with ISO-8859-1 since the RFC does not mention any string
        // to octet encoding. So neither one is wrong or right.
        final byte[] bytes = cred.getBytes(StandardCharsets.ISO_8859_1);
        if (bytes.length >= 255) {
            throw new IOException("SOCKS username / password are too long");
        }
        return bytes;
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
                        final byte[] username = cred(reactorConfig.getSocksProxyUsername());
                        final byte[] password = cred(reactorConfig.getSocksProxyPassword());
                        setBufferLimit(username.length + password.length + 3);
                        this.buffer.put(USERNAME_PASSWORD_VERSION);
                        this.buffer.put((byte) username.length);
                        this.buffer.put(username);
                        this.buffer.put((byte) password.length);
                        this.buffer.put(password);
                        this.buffer.flip();
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
                    switch (responseCode) {
                        case SUCCESS:
                            break;
                        case 1:
                            throw new IOException("SOCKS: General SOCKS server failure");
                        case 2:
                            throw new IOException("SOCKS5: Connection not allowed by ruleset");
                        case 3:
                            throw new IOException("SOCKS5: Network unreachable");
                        case 4:
                            throw new IOException("SOCKS5: Host unreachable");
                        case 5:
                            throw new IOException("SOCKS5: Connection refused");
                        case 6:
                            throw new IOException("SOCKS5: TTL expired");
                        case 7:
                            throw new IOException("SOCKS5: Command not supported");
                        case 8:
                            throw new IOException("SOCKS5: Address type not supported");
                        default:
                            throw new IOException("SOCKS5: Unexpected SOCKS response code " + responseCode);
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
                    final int remainingResponseSize = addressSize + 2;
                    this.buffer.compact();
                    // make sure we only read what we need to, don't read too much
                    this.buffer.limit(remainingResponseSize);
                    this.state = State.RECEIVE_ADDRESS;
                    // deliberate fall-through
                } else {
                    break;
                }
            case RECEIVE_ADDRESS:
                if (fillBuffer(session)) {
                    this.buffer.clear();
                    this.state = State.COMPLETE;
                    final IOEventHandler newHandler = this.eventHandlerFactory.createHandler(dataChannel, sessionRequest.attachment);
                    dataChannel.upgrade(newHandler);
                    sessionRequest.completed(dataChannel);
                    dataChannel.handleIOEvent(SelectionKey.OP_CONNECT);
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
        this.buffer.clear();
        setBufferLimit(MAX_COMMAND_CONNECT_LENGTH);
        this.buffer.put(CLIENT_VERSION);
        this.buffer.put(COMMAND_CONNECT);
        this.buffer.put((byte) 0); // reserved
        if (!(sessionRequest.remoteAddress instanceof InetSocketAddress)) {
            throw new IOException("Unsupported address class: " + sessionRequest.remoteAddress.getClass());
        }
        final InetSocketAddress targetAddress = ((InetSocketAddress) sessionRequest.remoteAddress);
        if (targetAddress.isUnresolved()) {
            this.buffer.put(ATYP_DOMAINNAME);
            final String hostName = targetAddress.getHostName();
            final byte[] hostnameBytes = hostName.getBytes(StandardCharsets.US_ASCII);
            if (hostnameBytes.length > MAX_DNS_NAME_LENGTH) {
                throw new IOException("Host name exceeds " + MAX_DNS_NAME_LENGTH + " bytes");
            }
            this.buffer.put((byte) hostnameBytes.length);
            this.buffer.put(hostnameBytes);
        } else {
            final InetAddress address = targetAddress.getAddress();
            if (address instanceof Inet4Address) {
                this.buffer.put(InetAddressUtils.IPV4);
            } else if (address instanceof Inet6Address) {
                this.buffer.put(InetAddressUtils.IPV6);
            } else {
                throw new IOException("Unsupported remote address class: " + address.getClass().getName());
            }
            this.buffer.put(address.getAddress());
        }
        final int port = targetAddress.getPort();
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
        try {
            sessionRequest.failed(cause);
        } finally {
            session.close(CloseMode.IMMEDIATE);
            CommandSupport.failCommands(session, cause);
        }
    }

    @Override
    public void disconnected(final IOSession session) {
        sessionRequest.cancel();
        CommandSupport.cancelCommands(session);
    }

}
