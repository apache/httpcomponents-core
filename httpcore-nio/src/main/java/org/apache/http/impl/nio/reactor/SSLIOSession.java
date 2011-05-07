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

package org.apache.http.impl.nio.reactor;

import org.apache.http.nio.reactor.EventMask;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.reactor.SessionBufferStatus;
import org.apache.http.params.HttpParams;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;

/**
 * A decorator class intended to transparently extend an {@link IOSession}
 * with transport layer security capabilities based on the SSL/TLS protocol.
 *
 * @since 4.0
 */
public class SSLIOSession implements IOSession, SessionBufferStatus {

    private final IOSession session;
    private final SSLEngine sslEngine;
    private final ByteBuffer inEncrypted;
    private final ByteBuffer outEncrypted;
    private final ByteBuffer inPlain;
    private final ByteBuffer outPlain;
    private final InternalByteChannel channel;
    private final SSLSetupHandler handler;

    private int appEventMask;
    private SessionBufferStatus appBufferStatus;

    private boolean endOfStream;
    private volatile int status;

    /**
     * @since 4.1
     */
    public SSLIOSession(
            final IOSession session,
            final SSLContext sslContext,
            final SSLSetupHandler handler) {
        super();
        if (session == null) {
            throw new IllegalArgumentException("IO session may not be null");
        }
        if (sslContext == null) {
            throw new IllegalArgumentException("SSL context may not be null");
        }
        this.session = session;
        this.appEventMask = session.getEventMask();
        this.channel = new InternalByteChannel();
        this.handler = handler;

        // Override the status buffer interface
        this.session.setBufferStatus(this);

        SocketAddress address = session.getRemoteAddress();
        if (address instanceof InetSocketAddress) {
            String hostname = ((InetSocketAddress) address).getHostName();
            int port = ((InetSocketAddress) address).getPort();
            this.sslEngine = sslContext.createSSLEngine(hostname, port);
        } else {
            this.sslEngine = sslContext.createSSLEngine();
        }

        // Allocate buffers for network (encrypted) data
        int netBuffersize = this.sslEngine.getSession().getPacketBufferSize();
        this.inEncrypted = ByteBuffer.allocate(netBuffersize);
        this.outEncrypted = ByteBuffer.allocate(netBuffersize);

        // Allocate buffers for application (unencrypted) data
        int appBuffersize = this.sslEngine.getSession().getApplicationBufferSize();
        this.inPlain = ByteBuffer.allocate(appBuffersize);
        this.outPlain = ByteBuffer.allocate(appBuffersize);
    }

    /**
     * @deprecated
     */
    @Deprecated
    public SSLIOSession(
            final IOSession session,
            final SSLContext sslContext,
            final SSLIOSessionHandler handler) {
        this(session, sslContext, handler != null ? new SSLIOSessionHandlerAdaptor(handler) : null);
    }

    public synchronized void bind(
            final SSLMode mode,
            final HttpParams params) throws SSLException {
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        switch (mode) {
        case CLIENT:
            this.sslEngine.setUseClientMode(true);
            break;
        case SERVER:
            this.sslEngine.setUseClientMode(false);
            break;
        }
        if (this.handler != null) {
            this.handler.initalize(this.sslEngine, params);
        }
        this.sslEngine.beginHandshake();
        doHandshake();
    }

    private void doHandshake() throws SSLException {
        boolean handshaking = true;

        SSLEngineResult result = null;
        while (handshaking) {
            switch (this.sslEngine.getHandshakeStatus()) {
            case NEED_WRAP:
                // Generate outgoing handshake data
                this.outPlain.flip();
                result = this.sslEngine.wrap(this.outPlain, this.outEncrypted);
                this.outPlain.compact();
                if (result.getStatus() != Status.OK) {
                    handshaking = false;
                }
                if (result.getStatus() == Status.CLOSED) {
                    this.status = CLOSED;
                }
                break;
            case NEED_UNWRAP:
                // Process incoming handshake data
                this.inEncrypted.flip();
                result = this.sslEngine.unwrap(this.inEncrypted, this.inPlain);
                this.inEncrypted.compact();
                if (result.getStatus() != Status.OK) {
                    handshaking = false;
                }
                if (result.getStatus() == Status.CLOSED) {
                    this.status = CLOSED;
                }
                if (result.getStatus() == Status.BUFFER_UNDERFLOW && this.endOfStream) {
                    this.status = CLOSED;
                }
                break;
            case NEED_TASK:
                Runnable r = this.sslEngine.getDelegatedTask();
                r.run();
                break;
            case NOT_HANDSHAKING:
                handshaking = false;
                break;
            case FINISHED:
                break;
            }
        }

        // The SSLEngine has just finished handshaking. This value is only generated by a call
        // to SSLEngine.wrap()/unwrap() when that call finishes a handshake.
        // It is never generated by SSLEngine.getHandshakeStatus().
        if (result != null && result.getHandshakeStatus() == HandshakeStatus.FINISHED) {
            if (this.handler != null) {
                this.handler.verify(this.session, this.sslEngine.getSession());
            }
        }
    }

    private void updateEventMask() {
        if (this.sslEngine.isInboundDone() && this.sslEngine.isOutboundDone()) {
            this.status = CLOSED;
        }
        if (this.status == CLOSED) {
            this.session.close();
            return;
        }
        // Need to toggle the event mask for this channel?
        int oldMask = this.session.getEventMask();
        int newMask = oldMask;
        switch (this.sslEngine.getHandshakeStatus()) {
        case NEED_WRAP:
            newMask = EventMask.READ_WRITE;
            break;
        case NEED_UNWRAP:
            newMask = EventMask.READ;
            break;
        case NOT_HANDSHAKING:
            newMask = this.appEventMask;
            break;
        case NEED_TASK:
            break;
        case FINISHED:
            break;
        }

        // Do we have encrypted data ready to be sent?
        if (this.outEncrypted.position() > 0) {
            newMask = newMask | EventMask.WRITE;
        }

        // Update the mask if necessary
        if (oldMask != newMask) {
            this.session.setEventMask(newMask);
        }
    }

    private int sendEncryptedData() throws IOException {
        this.outEncrypted.flip();
        int bytesWritten = this.session.channel().write(this.outEncrypted);
        this.outEncrypted.compact();
        return bytesWritten;
    }

    private int receiveEncryptedData() throws IOException {
        return this.session.channel().read(this.inEncrypted);
    }

    private boolean decryptData() throws SSLException {
        boolean decrypted = false;
        SSLEngineResult.Status opStatus = Status.OK;
        while (this.inEncrypted.position() > 0 && opStatus == Status.OK) {
            this.inEncrypted.flip();
            SSLEngineResult result = this.sslEngine.unwrap(this.inEncrypted, this.inPlain);
            this.inEncrypted.compact();

            opStatus = result.getStatus();
            if (opStatus == Status.CLOSED) {
                this.status = CLOSED;
            }
            if (opStatus == Status.BUFFER_UNDERFLOW && this.endOfStream) {
                this.status = CLOSED;
            }
            if (opStatus == Status.OK) {
                decrypted = true;
            }
        }
        return decrypted;
    }

    public synchronized boolean isAppInputReady() throws IOException {
        int bytesRead = receiveEncryptedData();
        if (bytesRead == -1) {
            this.endOfStream = true;
        }
        doHandshake();
        decryptData();
        // Some decrypted data is available or at the end of stream
        return (this.appEventMask & SelectionKey.OP_READ) > 0
            && (this.inPlain.position() > 0
                    || (this.appBufferStatus != null && this.appBufferStatus.hasBufferedInput())
                    || (this.endOfStream && this.status == ACTIVE));
    }

    /**
     * @throws IOException - not thrown currently
     */
    public synchronized boolean isAppOutputReady() throws IOException {
        return (this.appEventMask & SelectionKey.OP_WRITE) > 0
            && this.status == ACTIVE
            && this.sslEngine.getHandshakeStatus() == HandshakeStatus.NOT_HANDSHAKING;
    }

    /**
     * @throws IOException - not thrown currently
     */
    public synchronized void inboundTransport() throws IOException {
        updateEventMask();
    }

    public synchronized void outboundTransport() throws IOException {
        sendEncryptedData();
        doHandshake();
        updateEventMask();
    }

    private synchronized int writePlain(final ByteBuffer src) throws SSLException {
        if (src == null) {
            throw new IllegalArgumentException("Byte buffer may not be null");
        }
        if (this.status != ACTIVE) {
            return -1;
        }
        if (this.outPlain.position() > 0) {
            this.outPlain.flip();
            this.sslEngine.wrap(this.outPlain, this.outEncrypted);
            this.outPlain.compact();
        }
        if (this.outPlain.position() == 0) {
            SSLEngineResult result = this.sslEngine.wrap(src, this.outEncrypted);
            if (result.getStatus() == Status.CLOSED) {
                this.status = CLOSED;
            }
            return result.bytesConsumed();
        } else {
            return 0;
        }
    }

    /**
     * @throws SSLException - not thrown currently
     */
    private synchronized int readPlain(final ByteBuffer dst) throws SSLException {
        if (dst == null) {
            throw new IllegalArgumentException("Byte buffer may not be null");
        }
        if (this.inPlain.position() > 0) {
            this.inPlain.flip();
            int n = Math.min(this.inPlain.remaining(), dst.remaining());
            for (int i = 0; i < n; i++) {
                dst.put(this.inPlain.get());
            }
            this.inPlain.compact();
            return n;
        } else {
            if (this.endOfStream) {
                return -1;
            } else {
                return 0;
            }
        }
    }

    public void close() {
        if (this.status != ACTIVE) {
            return;
        }
        this.status = CLOSING;
        synchronized(this) {
            this.sslEngine.closeOutbound();
            updateEventMask();
        }
    }

    public void shutdown() {
        this.status = CLOSED;
        this.session.shutdown();
    }

    public int getStatus() {
        return this.status;
    }

    public boolean isClosed() {
        return this.status != ACTIVE;
    }

    public synchronized boolean isInboundDone() {
        return this.sslEngine.isInboundDone();
    }

    public synchronized boolean isOutboundDone() {
        return this.sslEngine.isOutboundDone();
    }

    public ByteChannel channel() {
        return this.channel;
    }

    public SocketAddress getLocalAddress() {
        return this.session.getLocalAddress();
    }

    public SocketAddress getRemoteAddress() {
        return this.session.getRemoteAddress();
    }

    public synchronized int getEventMask() {
        return this.appEventMask;
    }

    public synchronized void setEventMask(int ops) {
        this.appEventMask = ops;
        updateEventMask();
    }

    public synchronized void setEvent(int op) {
        this.appEventMask = this.appEventMask | op;
        updateEventMask();
    }

    public synchronized void clearEvent(int op) {
        this.appEventMask = this.appEventMask & ~op;
        updateEventMask();
    }

    public int getSocketTimeout() {
        return this.session.getSocketTimeout();
    }

    public void setSocketTimeout(int timeout) {
        this.session.setSocketTimeout(timeout);
    }

    public synchronized boolean hasBufferedInput() {
        return (this.appBufferStatus != null && this.appBufferStatus.hasBufferedInput())
            || this.inEncrypted.position() > 0
            || this.inPlain.position() > 0;
    }

    public synchronized boolean hasBufferedOutput() {
        return (this.appBufferStatus != null && this.appBufferStatus.hasBufferedOutput())
            || this.outEncrypted.position() > 0
            || this.outPlain.position() > 0;
    }

    public synchronized void setBufferStatus(final SessionBufferStatus status) {
        this.appBufferStatus = status;
    }

    public Object getAttribute(final String name) {
        return this.session.getAttribute(name);
    }

    public Object removeAttribute(final String name) {
        return this.session.removeAttribute(name);
    }

    public void setAttribute(final String name, final Object obj) {
        this.session.setAttribute(name, obj);
    }

    @Override
    public String toString() {
        StringBuffer buffer = new StringBuffer();
        buffer.append(this.session);
        buffer.append("[SSL handshake status: ");
        buffer.append(this.sslEngine.getHandshakeStatus());
        buffer.append("][");
        buffer.append(this.inEncrypted.position());
        buffer.append("][");
        buffer.append(this.inPlain.position());
        buffer.append("][");
        buffer.append(this.outEncrypted.position());
        buffer.append("][");
        buffer.append(this.outPlain.position());
        buffer.append("]");
        return buffer.toString();
    }

    private class InternalByteChannel implements ByteChannel {

        public int write(final ByteBuffer src) throws IOException {
            return SSLIOSession.this.writePlain(src);
        }

        public int read(final ByteBuffer dst) throws IOException {
            return SSLIOSession.this.readPlain(dst);
        }

        public void close() throws IOException {
            SSLIOSession.this.close();
        }

        public boolean isOpen() {
            return !SSLIOSession.this.isClosed();
        }

    }

}
