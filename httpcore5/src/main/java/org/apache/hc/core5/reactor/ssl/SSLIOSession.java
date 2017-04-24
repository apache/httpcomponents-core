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

package org.apache.hc.core5.reactor.ssl;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.io.ShutdownType;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.EventMask;
import org.apache.hc.core5.reactor.IOEventHandler;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.ssl.ReflectionSupport;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Asserts;

/**
 * {@code SSLIOSession} is a decorator class intended to transparently extend
 * an {@link IOSession} with transport layer security capabilities based on
 * the SSL/TLS protocol.
 *
 * @since 4.2
 */
@Contract(threading = ThreadingBehavior.SAFE_CONDITIONAL)
public class SSLIOSession implements IOSession {

    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);

    private final NamedEndpoint targetEndpoint;
    private final IOSession session;
    private final SSLEngine sslEngine;
    private final SSLBuffer inEncrypted;
    private final SSLBuffer outEncrypted;
    private final SSLBuffer inPlain;
    private final SSLBuffer outPlain;
    private final ByteChannel channel;
    private final SSLSessionInitializer initializer;
    private final SSLSessionVerifier verifier;
    private final Callback<SSLIOSession> callback;

    private int appEventMask;

    private boolean endOfStream;
    private volatile SSLMode sslMode;
    private volatile int status;
    private volatile boolean initialized;
    private TlsDetails tlsDetails;

    /**
     * Creates new instance of {@code SSLIOSession} class with static SSL buffers.
     *
     * @param targetEndpoint target endpoint (applicable in client mode only). May be {@code null}.
     * @param session I/O session to be decorated with the TLS/SSL capabilities.
     * @param sslMode SSL mode (client or server)
     * @param sslContext SSL context to use for this I/O session.
     * @param initializer optional SSL session initializer. May be {@code null}.
     * @param verifier optional SSL session verifier. May be {@code null}.
     *
     * @since 5.0
     */
    public SSLIOSession(
            final NamedEndpoint targetEndpoint,
            final IOSession session,
            final SSLMode sslMode,
            final SSLContext sslContext,
            final SSLSessionInitializer initializer,
            final SSLSessionVerifier verifier,
            final Callback<SSLIOSession> callback) {
        this(targetEndpoint, session, sslMode, sslContext, SSLBufferManagement.STATIC, initializer, verifier, callback);
    }

    /**
     * Creates new instance of {@code SSLIOSession} class.
     *
     * @param session I/O session to be decorated with the TLS/SSL capabilities.
     * @param sslMode SSL mode (client or server)
     * @param targetEndpoint target endpoint (applicable in client mode only). May be {@code null}.
     * @param sslContext SSL context to use for this I/O session.
     * @param sslBufferManagement buffer management mode
     * @param initializer optional SSL session initializer. May be {@code null}.
     * @param verifier optional SSL session verifier. May be {@code null}.
     *
     * @since 5.0
     */
    public SSLIOSession(
            final NamedEndpoint targetEndpoint,
            final IOSession session,
            final SSLMode sslMode,
            final SSLContext sslContext,
            final SSLBufferManagement sslBufferManagement,
            final SSLSessionInitializer initializer,
            final SSLSessionVerifier verifier,
            final Callback<SSLIOSession> callback) {
        super();
        Args.notNull(session, "IO session");
        Args.notNull(sslContext, "SSL context");
        this.targetEndpoint = targetEndpoint;
        this.session = session;
        this.sslMode = sslMode;
        this.initializer = initializer;
        this.verifier = verifier;
        this.callback = callback;

        this.appEventMask = session.getEventMask();
        if (this.sslMode == SSLMode.CLIENT && targetEndpoint != null) {
            this.sslEngine = sslContext.createSSLEngine(targetEndpoint.getHostName(), targetEndpoint.getPort());
        } else {
            this.sslEngine = sslContext.createSSLEngine();
        }

        final SSLSession sslSession = this.sslEngine.getSession();
        // Allocate buffers for network (encrypted) data
        final int netBufferSize = sslSession.getPacketBufferSize();
        this.inEncrypted = SSLBufferManagement.create(sslBufferManagement, netBufferSize);
        this.outEncrypted = SSLBufferManagement.create(sslBufferManagement, netBufferSize);

        // Allocate buffers for application (unencrypted) data
        final int appBufferSize = sslSession.getApplicationBufferSize();
        this.inPlain = SSLBufferManagement.create(sslBufferManagement, appBufferSize);
        this.outPlain = SSLBufferManagement.create(sslBufferManagement, appBufferSize);
        this.channel = new ByteChannel() {

            @Override
            public int write(final ByteBuffer src) throws IOException {
                return SSLIOSession.this.writePlain(src);
            }

            @Override
            public int read(final ByteBuffer dst) throws IOException {
                return SSLIOSession.this.readPlain(dst);
            }

            @Override
            public void close() throws IOException {
                SSLIOSession.this.close();
            }

            @Override
            public boolean isOpen() {
                return !SSLIOSession.this.isClosed();
            }

        };
    }

    @Override
    public String getId() {
        return session.getId();
    }

    /**
     * Returns {@code true} is the session has been fully initialized,
     * {@code false} otherwise.
     */
    public boolean isInitialized() {
        return this.initialized;
    }

    /**
     * Initializes the session. This method invokes the {@link
     * SSLSessionInitializer#initialize(NamedEndpoint, SSLEngine)} callback
     * if an instance of {@link SSLSessionInitializer} was specified at the construction time.
     *
     * @throws SSLException in case of a SSL protocol exception.
     * @throws IllegalStateException if the session has already been initialized.
     */
    public synchronized void initialize() throws SSLException {
        Asserts.check(!this.initialized, "SSL I/O session already initialized");
        if (this.status >= IOSession.CLOSING) {
            return;
        }
        switch (this.sslMode) {
        case CLIENT:
            this.sslEngine.setUseClientMode(true);
            break;
        case SERVER:
            this.sslEngine.setUseClientMode(false);
            break;
        }
        if (this.initializer != null) {
            this.initializer.initialize(this.targetEndpoint, this.sslEngine);
        }
        this.initialized = true;
        this.sslEngine.beginHandshake();

        this.inEncrypted.release();
        this.outEncrypted.release();
        this.inPlain.release();
        this.outPlain.release();

        doHandshake();
    }

    public synchronized TlsDetails getTlsDetails() {
        return tlsDetails;
    }

    // A works-around for exception handling craziness in Sun/Oracle's SSLEngine
    // implementation.
    //
    // sun.security.pkcs11.wrapper.PKCS11Exception is re-thrown as
    // plain RuntimeException in sun.security.ssl.Handshaker#checkThrown
    private SSLException convert(final RuntimeException ex) {
        Throwable cause = ex.getCause();
        if (cause == null) {
            cause = ex;
        }
        return new SSLException(cause);
    }

    private SSLEngineResult doWrap(final ByteBuffer src, final ByteBuffer dst) throws SSLException {
        try {
            return this.sslEngine.wrap(src, dst);
        } catch (final RuntimeException ex) {
            throw convert(ex);
        }
    }

    private SSLEngineResult doUnwrap(final ByteBuffer src, final ByteBuffer dst) throws SSLException {
        try {
            return this.sslEngine.unwrap(src, dst);
        } catch (final RuntimeException ex) {
            throw convert(ex);
        }
    }

    private void doRunTask() throws SSLException {
        try {
            final Runnable r = this.sslEngine.getDelegatedTask();
            if (r != null) {
                r.run();
            }
        } catch (final RuntimeException ex) {
            throw convert(ex);
        }
    }

    private void doHandshake() throws SSLException {
        boolean handshaking = true;

        SSLEngineResult result = null;
        while (handshaking) {
            switch (this.sslEngine.getHandshakeStatus()) {
            case NEED_WRAP:
                // Generate outgoing handshake data

                // Acquire buffers
                final ByteBuffer outPlainBuf = this.outPlain.acquire();
                final ByteBuffer outEncryptedBuf = this.outEncrypted.acquire();

                // Perform operations
                outPlainBuf.flip();
                result = doWrap(outPlainBuf, outEncryptedBuf);
                outPlainBuf.compact();

                // Release outPlain if empty
                if (outPlainBuf.position() == 0) {
                    this.outPlain.release();
                }

                if (result.getStatus() != Status.OK) {
                    handshaking = false;
                }
                break;
            case NEED_UNWRAP:
                // Process incoming handshake data

                // Acquire buffers
                final ByteBuffer inEncryptedBuf = this.inEncrypted.acquire();
                final ByteBuffer inPlainBuf = this.inPlain.acquire();

                // Perform operations
                inEncryptedBuf.flip();
                result = doUnwrap(inEncryptedBuf, inPlainBuf);
                inEncryptedBuf.compact();

                try {
                    if (!inEncryptedBuf.hasRemaining() && result.getHandshakeStatus() == HandshakeStatus.NEED_UNWRAP) {
                        throw new SSLException("Input buffer is full");
                    }
                } finally {
                    // Release inEncrypted if empty
                    if (inEncryptedBuf.position() == 0) {
                        this.inEncrypted.release();
                    }
                }

                if (this.status >= IOSession.CLOSING) {
                    this.inPlain.release();
                }
                if (result.getStatus() != Status.OK) {
                    handshaking = false;
                }
                break;
            case NEED_TASK:
                doRunTask();
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
            if (this.verifier != null) {
                this.tlsDetails = this.verifier.verify(this.targetEndpoint, this.sslEngine);
            }
            if (this.tlsDetails == null) {
                final SSLSession sslSession = this.sslEngine.getSession();
                final String applicationProtocol = ReflectionSupport.callGetter(this.sslEngine, "ApplicationProtocol", String.class);
                this.tlsDetails = new TlsDetails(sslSession, applicationProtocol);
            }
            if (this.callback != null) {
                this.callback.execute(this);
            }
        }
    }

    private void updateEventMask() {
        // Graceful session termination
        if (this.status == CLOSING && !this.outEncrypted.hasData()) {
            this.sslEngine.closeOutbound();
        }
        if (this.status == CLOSING && this.sslEngine.isOutboundDone()
                && (this.endOfStream || this.sslEngine.isInboundDone())) {
            this.status = CLOSED;
        }
        // Abnormal session termination
        if (this.status == ACTIVE && this.endOfStream
                && this.sslEngine.getHandshakeStatus() == HandshakeStatus.NEED_UNWRAP) {
            this.status = CLOSED;
        }
        if (this.status == CLOSED) {
            this.session.close();
            return;
        }
        // Need to toggle the event mask for this channel?
        final int oldMask = this.session.getEventMask();
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
        if (this.outEncrypted.hasData()) {
            newMask = newMask | EventMask.WRITE;
        }

        // Update the mask if necessary
        if (oldMask != newMask) {
            this.session.setEventMask(newMask);
        }
    }

    private int sendEncryptedData() throws IOException {
        if (!this.outEncrypted.hasData()) {
            // If the buffer isn't acquired or is empty, call write() with an empty buffer.
            // This will ensure that tests performed by write() still take place without
            // having to acquire and release an empty buffer (e.g. connection closed,
            // interrupted thread, etc..)
            return this.session.channel().write(EMPTY_BUFFER);
        }

        // Acquire buffer
        final ByteBuffer outEncryptedBuf = this.outEncrypted.acquire();

        // Perform operation
        int bytesWritten = 0;
        if (outEncryptedBuf.position() > 0) {
            outEncryptedBuf.flip();
            bytesWritten = this.session.channel().write(outEncryptedBuf);
            outEncryptedBuf.compact();
        }

        // Release if empty
        if (outEncryptedBuf.position() == 0) {
            this.outEncrypted.release();
        }
        return bytesWritten;
    }

    private int receiveEncryptedData() throws IOException {
        if (this.endOfStream) {
            return -1;
        }

        // Acquire buffer
        final ByteBuffer inEncryptedBuf = this.inEncrypted.acquire();

        // Perform operation
        final int ret = this.session.channel().read(inEncryptedBuf);

        // Release if empty
        if (inEncryptedBuf.position() == 0) {
            this.inEncrypted.release();
        }
        return ret;
    }

    private boolean decryptData() throws SSLException {
        boolean decrypted = false;
        while (this.inEncrypted.hasData()) {
            // Get buffers
            final ByteBuffer inEncryptedBuf = this.inEncrypted.acquire();
            final ByteBuffer inPlainBuf = this.inPlain.acquire();

            // Perform operations
            inEncryptedBuf.flip();
            final SSLEngineResult result = doUnwrap(inEncryptedBuf, inPlainBuf);
            inEncryptedBuf.compact();

            try {
                if (!inEncryptedBuf.hasRemaining() && result.getHandshakeStatus() == HandshakeStatus.NEED_UNWRAP) {
                    throw new SSLException("Unable to complete SSL handshake");
                }
                final Status status = result.getStatus();
                if (status == Status.OK) {
                    decrypted = true;
                } else {
                    if (status == Status.BUFFER_UNDERFLOW && this.endOfStream) {
                        throw new SSLException("Unable to decrypt incoming data due to unexpected end of stream");
                    }
                    break;
                }
                if (result.getHandshakeStatus() != HandshakeStatus.NOT_HANDSHAKING) {
                    break;
                }
            } finally {
                // Release inEncrypted if empty
                if (this.inEncrypted.acquire().position() == 0) {
                    this.inEncrypted.release();
                }
            }
        }
        return decrypted;
    }

    /**
     * Reads encrypted data and returns whether the channel associated with
     * this session has any decrypted inbound data available for reading.
     *
     * @throws IOException in case of an I/O error.
     */
    public synchronized boolean isAppInputReady() throws IOException {
        do {
            final int bytesRead = receiveEncryptedData();
            if (bytesRead == -1) {
                this.endOfStream = true;
            }
            doHandshake();
            final HandshakeStatus status = this.sslEngine.getHandshakeStatus();
            if (status == HandshakeStatus.NOT_HANDSHAKING || status == HandshakeStatus.FINISHED) {
                decryptData();
            }
        } while (this.sslEngine.getHandshakeStatus() == HandshakeStatus.NEED_TASK);
        // Some decrypted data is available or at the end of stream
        return this.inPlain.hasData() || (this.endOfStream && this.status == ACTIVE);
    }

    /**
     * Returns whether the channel associated with this session is ready to
     * accept outbound unecrypted data for writing.
     *
     * @throws IOException - not thrown currently
     */
    public synchronized boolean isAppOutputReady() throws IOException {
        return (this.appEventMask & SelectionKey.OP_WRITE) > 0
            && this.status == ACTIVE
            && this.sslEngine.getHandshakeStatus() == HandshakeStatus.NOT_HANDSHAKING;
    }

    /**
     * Executes inbound SSL transport operations.
     *
     * @throws IOException - not thrown currently
     */
    public synchronized void inboundTransport() throws IOException {
        updateEventMask();
    }

    /**
     * Sends encrypted data and executes outbound SSL transport operations.
     *
     * @throws IOException in case of an I/O error.
     */
    public synchronized void outboundTransport() throws IOException {
        if (this.session.isClosed()) {
            return;
        }
        sendEncryptedData();
        doHandshake();
        updateEventMask();
    }

    /**
     * Returns whether the session will produce any more inbound data.
     */
    public synchronized boolean isInboundDone() {
        return this.sslEngine.isInboundDone();
    }

    /**
     * Returns whether the session will accept any more outbound data.
     */
    public synchronized boolean isOutboundDone() {
        return this.sslEngine.isOutboundDone();
    }

    private synchronized int writePlain(final ByteBuffer src) throws IOException {
        Args.notNull(src, "Byte buffer");
        if (this.status != ACTIVE) {
            throw new ClosedChannelException();
        }
        if (this.outPlain.hasData()) {
            // Acquire buffers
            final ByteBuffer outPlainBuf = this.outPlain.acquire();
            final ByteBuffer outEncryptedBuf = this.outEncrypted.acquire();

            // Perform operations
            outPlainBuf.flip();
            doWrap(outPlainBuf, outEncryptedBuf);
            outPlainBuf.compact();

            // Release outPlain if empty
            if (outPlainBuf.position() == 0) {
                this.outPlain.release();
            }
        }
        if (!this.outPlain.hasData()) {
            final ByteBuffer outEncryptedBuf = this.outEncrypted.acquire();
            final SSLEngineResult result = doWrap(src, outEncryptedBuf);
            if (result.getStatus() == Status.CLOSED) {
                this.status = CLOSED;
            }
            return result.bytesConsumed();
        }
        return 0;
    }

    private synchronized int readPlain(final ByteBuffer dst) {
        Args.notNull(dst, "Byte buffer");
        if (this.inPlain.hasData()) {
            // Acquire buffer
            final ByteBuffer inPlainBuf = this.inPlain.acquire();

            // Perform opertaions
            inPlainBuf.flip();
            final int n = Math.min(inPlainBuf.remaining(), dst.remaining());
            for (int i = 0; i < n; i++) {
                dst.put(inPlainBuf.get());
            }
            inPlainBuf.compact();

            // Release if empty
            if (inPlainBuf.position() == 0) {
                this.inPlain.release();
            }
            return n;
        }
        if (this.endOfStream) {
            return -1;
        }
        return 0;
    }

    /**
     * @since 5.0
     */
    public synchronized boolean hasInputDate() {
        return this.inPlain.hasData();
    }

    /**
     * @since 5.0
     */
    public synchronized boolean hasOutputDate() {
        return this.outPlain.hasData();
    }

    @Override
    public synchronized void close() {
        if (this.status >= CLOSING) {
            return;
        }
        this.status = CLOSING;
        if (this.session.getSocketTimeout() == 0) {
            this.session.setSocketTimeout(1000);
        }
        try {
            updateEventMask();
        } catch (final CancelledKeyException ex) {
            shutdown(ShutdownType.GRACEFUL);
        }
    }

    @Override
    public synchronized void shutdown(final ShutdownType shutdownType) {
        if (this.status == CLOSED) {
            return;
        }
        this.inEncrypted.release();
        this.outEncrypted.release();
        this.inPlain.release();
        this.outPlain.release();

        this.status = CLOSED;
        this.session.shutdown(shutdownType);
    }

    @Override
    public int getStatus() {
        return this.status;
    }

    @Override
    public boolean isClosed() {
        return this.status >= CLOSING || this.session.isClosed();
    }

    @Override
    public synchronized void addLast(final Command command) {
        this.session.addLast(command);
        setEvent(SelectionKey.OP_WRITE);
    }

    @Override
    public synchronized void addFirst(final Command command) {
        this.session.addFirst(command);
        setEvent(SelectionKey.OP_WRITE);
    }

    @Override
    public Command getCommand() {
        return this.session.getCommand();
    }

    @Override
    public ByteChannel channel() {
        return this.channel;
    }

    @Override
    public SocketAddress getLocalAddress() {
        return this.session.getLocalAddress();
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return this.session.getRemoteAddress();
    }

    @Override
    public synchronized int getEventMask() {
        return this.appEventMask;
    }

    @Override
    public synchronized void setEventMask(final int ops) {
        this.appEventMask = ops;
        updateEventMask();
    }

    @Override
    public synchronized void setEvent(final int op) {
        this.appEventMask = this.appEventMask | op;
        updateEventMask();
    }

    @Override
    public synchronized void clearEvent(final int op) {
        this.appEventMask = this.appEventMask & ~op;
        updateEventMask();
    }

    @Override
    public int getSocketTimeout() {
        return this.session.getSocketTimeout();
    }

    @Override
    public void setSocketTimeout(final int timeout) {
        this.session.setSocketTimeout(timeout);
    }

    @Override
    public IOEventHandler getHandler() {
        return this.session.getHandler();
    }

    @Override
    public void setHandler(final IOEventHandler handler) {
        this.session.setHandler(handler);
    }

    private static void formatOps(final StringBuilder buffer, final int ops) {
        if ((ops & SelectionKey.OP_READ) > 0) {
            buffer.append('r');
        }
        if ((ops & SelectionKey.OP_WRITE) > 0) {
            buffer.append('w');
        }
    }

    @Override
    public String toString() {
        final StringBuilder buffer = new StringBuilder();
        buffer.append(this.session);
        buffer.append("[");
        switch (this.status) {
        case ACTIVE:
            buffer.append("ACTIVE");
            break;
        case CLOSING:
            buffer.append("CLOSING");
            break;
        case CLOSED:
            buffer.append("CLOSED");
            break;
        }
        buffer.append("][");
        formatOps(buffer, this.appEventMask);
        buffer.append("][");
        buffer.append(this.sslEngine.getHandshakeStatus());
        if (this.sslEngine.isInboundDone()) {
            buffer.append("][inbound done][");
        }
        if (this.sslEngine.isOutboundDone()) {
            buffer.append("][outbound done][");
        }
        if (this.endOfStream) {
            buffer.append("][EOF][");
        }
        buffer.append("][");
        buffer.append(!this.inEncrypted.hasData() ? 0 : inEncrypted.acquire().position());
        buffer.append("][");
        buffer.append(!this.inPlain.hasData() ? 0 : inPlain.acquire().position());
        buffer.append("][");
        buffer.append(!this.outEncrypted.hasData() ? 0 : outEncrypted.acquire().position());
        buffer.append("][");
        buffer.append(!this.outPlain.hasData() ? 0 : outPlain.acquire().position());
        buffer.append("]");
        return buffer.toString();
    }

}
