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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.EventMask;
import org.apache.hc.core5.reactor.IOEventHandler;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Asserts;
import org.apache.hc.core5.util.Timeout;

/**
 * {@code SSLIOSession} is a decorator class intended to transparently extend
 * an {@link IOSession} with transport layer security capabilities based on
 * the SSL/TLS protocol.
 *
 * @since 4.2
 */
@Contract(threading = ThreadingBehavior.SAFE_CONDITIONAL)
@Internal
public class SSLIOSession implements IOSession {

    enum TLSHandShakeState { READY, INITIALIZED, HANDSHAKING, COMPLETE }

    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);

    private final NamedEndpoint targetEndpoint;
    private final IOSession session;
    private final SSLEngine sslEngine;
    private final SSLManagedBuffer inEncrypted;
    private final SSLManagedBuffer outEncrypted;
    private final SSLManagedBuffer inPlain;
    private final SSLSessionInitializer initializer;
    private final SSLSessionVerifier verifier;
    private final Callback<SSLIOSession> sessionStartCallback;
    private final Callback<SSLIOSession> sessionEndCallback;
    private final Timeout connectTimeout;
    private final SSLMode sslMode;
    private final AtomicInteger outboundClosedCount;
    private final AtomicReference<TLSHandShakeState> handshakeStateRef;
    private final IOEventHandler internalEventHandler;

    private int appEventMask;

    private volatile boolean endOfStream;
    private volatile Status status = Status.ACTIVE;
    private volatile Timeout socketTimeout;
    private volatile TlsDetails tlsDetails;

    /**
     * Creates new instance of {@code SSLIOSession} class.
     *
     * @param session I/O session to be decorated with the TLS/SSL capabilities.
     * @param sslMode SSL mode (client or server)
     * @param targetEndpoint target endpoint (applicable in client mode only). May be {@code null}.
     * @param sslContext SSL context to use for this I/O session.
     * @param sslBufferMode buffer management mode
     * @param initializer optional SSL session initializer. May be {@code null}.
     * @param verifier optional SSL session verifier. May be {@code null}.
     * @param connectTimeout timeout to apply for the TLS/SSL handshake. May be {@code null}.
     *
     * @since 5.0
     */
    public SSLIOSession(
            final NamedEndpoint targetEndpoint,
            final IOSession session,
            final SSLMode sslMode,
            final SSLContext sslContext,
            final SSLBufferMode sslBufferMode,
            final SSLSessionInitializer initializer,
            final SSLSessionVerifier verifier,
            final Callback<SSLIOSession> sessionStartCallback,
            final Callback<SSLIOSession> sessionEndCallback,
            final Timeout connectTimeout) {
        super();
        Args.notNull(session, "IO session");
        Args.notNull(sslContext, "SSL context");
        this.targetEndpoint = targetEndpoint;
        this.session = session;
        this.sslMode = sslMode;
        this.initializer = initializer;
        this.verifier = verifier;
        this.sessionStartCallback = sessionStartCallback;
        this.sessionEndCallback = sessionEndCallback;

        this.appEventMask = session.getEventMask();
        if (this.sslMode == SSLMode.CLIENT && targetEndpoint != null) {
            this.sslEngine = sslContext.createSSLEngine(targetEndpoint.getHostName(), targetEndpoint.getPort());
        } else {
            this.sslEngine = sslContext.createSSLEngine();
        }

        final SSLSession sslSession = this.sslEngine.getSession();
        // Allocate buffers for network (encrypted) data
        final int netBufferSize = sslSession.getPacketBufferSize();
        this.inEncrypted = SSLManagedBuffer.create(sslBufferMode, netBufferSize);
        this.outEncrypted = SSLManagedBuffer.create(sslBufferMode, netBufferSize);

        // Allocate buffers for application (unencrypted) data
        final int appBufferSize = sslSession.getApplicationBufferSize();
        this.inPlain = SSLManagedBuffer.create(sslBufferMode, appBufferSize);
        this.outboundClosedCount = new AtomicInteger(0);
        this.handshakeStateRef = new AtomicReference<>(TLSHandShakeState.READY);
        this.connectTimeout = connectTimeout;
        this.internalEventHandler = new IOEventHandler() {

            @Override
            public void connected(final IOSession protocolSession) throws IOException {
                beginHandshake(protocolSession);
            }

            @Override
            public void inputReady(final IOSession protocolSession, final ByteBuffer src) throws IOException {
                receiveEncryptedData();
                doHandshake(protocolSession);
                decryptData(protocolSession);
                updateEventMask();
            }

            @Override
            public void outputReady(final IOSession protocolSession) throws IOException {
                encryptData(protocolSession);
                sendEncryptedData();
                doHandshake(protocolSession);
                updateEventMask();
            }

            @Override
            public void timeout(final IOSession protocolSession, final Timeout timeout) throws IOException {
                if (sslEngine.isInboundDone() && !sslEngine.isInboundDone()) {
                    // The session failed to terminate cleanly
                    close(CloseMode.IMMEDIATE);
                }
                ensureHandler().timeout(protocolSession, timeout);
            }

            @Override
            public void exception(final IOSession protocolSession, final Exception cause) {
                final IOEventHandler handler = session.getHandler();
                if (handshakeStateRef.get() != TLSHandShakeState.COMPLETE) {
                    session.close(CloseMode.GRACEFUL);
                    close(CloseMode.IMMEDIATE);
                }
                if (handler != null) {
                    handler.exception(protocolSession, cause);
                }
            }

            @Override
            public void disconnected(final IOSession protocolSession) {
                final IOEventHandler handler = session.getHandler();
                if (handler != null) {
                    handler.disconnected(protocolSession);
                }
            }

        };

    }

    private IOEventHandler ensureHandler() {
        final IOEventHandler handler = session.getHandler();
        Asserts.notNull(handler, "IO event handler");
        return handler;
    }

    @Override
    public IOEventHandler getHandler() {
        return internalEventHandler;
    }

    public void beginHandshake(final IOSession protocolSession) throws IOException {
        if (handshakeStateRef.compareAndSet(TLSHandShakeState.READY, TLSHandShakeState.INITIALIZED)) {
            initialize(protocolSession);
        }
    }

    private void initialize(final IOSession protocolSession) throws IOException {
        // Save the initial socketTimeout of the underlying IOSession, to be restored after the handshake is finished
        this.socketTimeout = this.session.getSocketTimeout();
        if (connectTimeout != null) {
            this.session.setSocketTimeout(connectTimeout);
        }

        this.session.getLock().lock();
        try {
            if (this.status.compareTo(Status.CLOSING) >= 0) {
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
            this.handshakeStateRef.set(TLSHandShakeState.HANDSHAKING);
            this.sslEngine.beginHandshake();

            this.inEncrypted.release();
            this.outEncrypted.release();
            doHandshake(protocolSession);
        } finally {
            this.session.getLock().unlock();
        }
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

    private void doRunTask() {
        final Runnable r = this.sslEngine.getDelegatedTask();
        if (r != null) {
            r.run();
        }
    }

    private void doHandshake(final IOSession protocolSession) throws IOException {
        boolean handshaking = true;

        SSLEngineResult result = null;
        while (handshaking) {
             HandshakeStatus handshakeStatus = this.sslEngine.getHandshakeStatus();

            // Work-around for what appears to be a bug in Conscrypt SSLEngine that does not
            // transition into the handshaking state upon #closeOutbound() call but still
            // has some handshake data stuck in its internal buffer.
            if (handshakeStatus == HandshakeStatus.NOT_HANDSHAKING && outboundClosedCount.get() > 0) {
                handshakeStatus = HandshakeStatus.NEED_WRAP;
            }

            switch (handshakeStatus) {
            case NEED_WRAP:
                // Generate outgoing handshake data

                this.session.getLock().lock();
                try {
                    // Acquire buffers
                    final ByteBuffer outEncryptedBuf = this.outEncrypted.acquire();

                    // Just wrap an empty buffer because there is no data to write.
                    result = doWrap(EMPTY_BUFFER, outEncryptedBuf);

                    if (result.getStatus() != SSLEngineResult.Status.OK || result.getHandshakeStatus() == HandshakeStatus.NEED_WRAP) {
                        handshaking = false;
                    }
                    break;
                } finally {
                    this.session.getLock().unlock();
                }
            case NEED_UNWRAP:
                // Process incoming handshake data

                // Acquire buffers
                final ByteBuffer inEncryptedBuf = this.inEncrypted.acquire();
                final ByteBuffer inPlainBuf = this.inPlain.acquire();

                // Perform operations
                inEncryptedBuf.flip();
                try {
                    result = doUnwrap(inEncryptedBuf, inPlainBuf);
                } finally {
                    inEncryptedBuf.compact();
                }

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

                if (this.status.compareTo(Status.CLOSING) >= 0) {
                    this.inPlain.release();
                }
                if (result.getStatus() != SSLEngineResult.Status.OK) {
                    handshaking = false;
                }
                break;
            case NEED_TASK:
                doRunTask();
                break;
            case NOT_HANDSHAKING:
                handshaking = false;
                break;
            }
        }

        // The SSLEngine has just finished handshaking. This value is only generated by a call
        // to SSLEngine.wrap()/unwrap() when that call finishes a handshake.
        // It is never generated by SSLEngine.getHandshakeStatus().
        if (result != null && result.getHandshakeStatus() == HandshakeStatus.FINISHED) {
            this.session.setSocketTimeout(this.socketTimeout);
            if (this.verifier != null) {
                this.tlsDetails = this.verifier.verify(this.targetEndpoint, this.sslEngine);
            }
            if (this.tlsDetails == null) {
                final SSLSession sslSession = this.sslEngine.getSession();
                final String applicationProtocol = this.sslEngine.getApplicationProtocol();
                this.tlsDetails = new TlsDetails(sslSession, applicationProtocol);
            }

            ensureHandler().connected(protocolSession);

            if (this.sessionStartCallback != null) {
                this.sessionStartCallback.execute(this);
            }
        }
    }

    private void updateEventMask() {
        this.session.getLock().lock();
        try {
            // Graceful session termination
            if (this.status == Status.ACTIVE
                    && (this.endOfStream || this.sslEngine.isInboundDone())) {
                this.status = Status.CLOSING;
            }
            if (this.status == Status.CLOSING && !this.outEncrypted.hasData()) {
                this.sslEngine.closeOutbound();
                this.outboundClosedCount.incrementAndGet();
            }
            if (this.status == Status.CLOSING && this.sslEngine.isOutboundDone()
                    && (this.endOfStream || this.sslEngine.isInboundDone())) {
                this.status = Status.CLOSED;
            }
            // Abnormal session termination
            if (this.status.compareTo(Status.CLOSING) <= 0 && this.endOfStream
                    && this.sslEngine.getHandshakeStatus() == HandshakeStatus.NEED_UNWRAP) {
                this.status = Status.CLOSED;
            }
            if (this.status == Status.CLOSED) {
                this.session.close();
                if (sessionEndCallback != null) {
                    sessionEndCallback.execute(this);
                }
                return;
            }
            // Is there a task pending?
            if (this.sslEngine.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
                doRunTask();
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
            }

            if (this.endOfStream && !this.inPlain.hasData()) {
                newMask = newMask & ~EventMask.READ;
            } else if (this.status == Status.CLOSING) {
                newMask = newMask | EventMask.READ;
            }

            // Do we have encrypted data ready to be sent?
            if (this.outEncrypted.hasData()) {
                newMask = newMask | EventMask.WRITE;
            } else if (this.sslEngine.isOutboundDone()) {
                newMask = newMask & ~EventMask.WRITE;
            }

            // Update the mask if necessary
            if (oldMask != newMask) {
                this.session.setEventMask(newMask);
            }
        } finally {
            this.session.getLock().unlock();
        }
    }

    private int sendEncryptedData() throws IOException {
        this.session.getLock().lock();
        try {
            if (!this.outEncrypted.hasData()) {
                // If the buffer isn't acquired or is empty, call write() with an empty buffer.
                // This will ensure that tests performed by write() still take place without
                // having to acquire and release an empty buffer (e.g. connection closed,
                // interrupted thread, etc..)
                return this.session.write(EMPTY_BUFFER);
            }

            // Acquire buffer
            final ByteBuffer outEncryptedBuf = this.outEncrypted.acquire();

            // Clear output buffer if the session has been closed
            // in case there is still `close_notify` data stuck in it
            if (this.status == Status.CLOSED) {
                outEncryptedBuf.clear();
            }

            // Perform operation
            int bytesWritten = 0;
            if (outEncryptedBuf.position() > 0) {
                outEncryptedBuf.flip();
                try {
                    bytesWritten = this.session.write(outEncryptedBuf);
                } finally {
                    outEncryptedBuf.compact();
                }
            }

            // Release if empty
            if (outEncryptedBuf.position() == 0) {
                this.outEncrypted.release();
            }
            return bytesWritten;
        } finally {
            this.session.getLock().unlock();
        }
    }

    private int receiveEncryptedData() throws IOException {
        if (this.endOfStream) {
            return -1;
        }

        // Acquire buffer
        final ByteBuffer inEncryptedBuf = this.inEncrypted.acquire();

        // Perform operation
        final int bytesRead = this.session.read(inEncryptedBuf);

        // Release if empty
        if (inEncryptedBuf.position() == 0) {
            this.inEncrypted.release();
        }
        if (bytesRead == -1) {
            this.endOfStream = true;
        }
        return bytesRead;
    }

    private void decryptData(final IOSession protocolSession) throws IOException {
        final HandshakeStatus handshakeStatus = sslEngine.getHandshakeStatus();
        if ((handshakeStatus == HandshakeStatus.NOT_HANDSHAKING || handshakeStatus == HandshakeStatus.FINISHED)
                && inEncrypted.hasData()) {
            final ByteBuffer inEncryptedBuf = inEncrypted.acquire();
            inEncryptedBuf.flip();
            try {
                while (inEncryptedBuf.hasRemaining()) {
                    final ByteBuffer inPlainBuf = inPlain.acquire();
                    try {
                        final SSLEngineResult result = doUnwrap(inEncryptedBuf, inPlainBuf);
                        if (!inEncryptedBuf.hasRemaining() && result.getHandshakeStatus() == HandshakeStatus.NEED_UNWRAP) {
                            throw new SSLException("Unable to complete SSL handshake");
                        }
                        if (sslEngine.isInboundDone()) {
                            endOfStream = true;
                        }
                        if (inPlainBuf.hasRemaining()) {
                            inPlainBuf.flip();
                            try {
                                ensureHandler().inputReady(protocolSession, inPlainBuf.hasRemaining() ? inPlainBuf : null);
                            } finally {
                                inPlainBuf.clear();
                            }
                        }
                        if (result.getStatus() != SSLEngineResult.Status.OK) {
                            if (result.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW && endOfStream) {
                                throw new SSLException("Unable to decrypt incoming data due to unexpected end of stream");
                            }
                            break;
                        }
                    } finally {
                        inPlain.release();
                    }
                }
            } finally {
                inEncryptedBuf.compact();
                // Release inEncrypted if empty
                if (inEncryptedBuf.position() == 0) {
                    inEncrypted.release();
                }
            }
        }
    }

    private void encryptData(final IOSession protocolSession) throws IOException {
        final boolean appReady;
        this.session.getLock().lock();
        try {
            appReady = (this.appEventMask & SelectionKey.OP_WRITE) > 0
                    && this.status == Status.ACTIVE
                    && this.sslEngine.getHandshakeStatus() == HandshakeStatus.NOT_HANDSHAKING;
        } finally {
            this.session.getLock().unlock();
        }
        if (appReady) {
            ensureHandler().outputReady(protocolSession);
        }
    }

    @Override
    public int write(final ByteBuffer src) throws IOException {
        Args.notNull(src, "Byte buffer");
        this.session.getLock().lock();
        try {
            if (this.status != Status.ACTIVE) {
                throw new ClosedChannelException();
            }
            if (this.handshakeStateRef.get() == TLSHandShakeState.READY) {
                return 0;
            }
            final ByteBuffer outEncryptedBuf = this.outEncrypted.acquire();
            final SSLEngineResult result = doWrap(src, outEncryptedBuf);
            return result.bytesConsumed();
        } finally {
            this.session.getLock().unlock();
        }
    }

    @Override
    public int read(final ByteBuffer dst) {
        return endOfStream ? -1 : 0;
    }

    @Override
    public String getId() {
        return session.getId();
    }

    @Override
    public Lock getLock() {
        return this.session.getLock();
    }

    @Override
    public void upgrade(final IOEventHandler handler) {
        this.session.upgrade(handler);
    }

    public TlsDetails getTlsDetails() {
        return tlsDetails;
    }

    @Override
    public boolean isOpen() {
        return this.status == Status.ACTIVE && this.session.isOpen();
    }

    @Override
    public void close() {
        close(CloseMode.GRACEFUL);
    }

    @Override
    public void close(final CloseMode closeMode) {
        this.session.getLock().lock();
        try {
            if (closeMode == CloseMode.GRACEFUL) {
                if (this.status.compareTo(Status.CLOSING) >= 0) {
                    return;
                }
                this.status = Status.CLOSING;
                if (this.session.getSocketTimeout().isDisabled()) {
                    this.session.setSocketTimeout(Timeout.ofMilliseconds(1000));
                }
                try {
                    // Catch all unchecked exceptions in case something goes wrong
                    // in the JSSE provider. For instance
                    // com.android.org.conscrypt.NativeCrypto#SSL_get_shutdown can
                    // throw NPE at this point
                    updateEventMask();
                } catch (final CancelledKeyException ex) {
                    this.session.close(CloseMode.GRACEFUL);
                } catch (final Exception ex) {
                    this.session.close(CloseMode.IMMEDIATE);
                }
            } else {
                if (this.status == Status.CLOSED) {
                    return;
                }
                this.inEncrypted.release();
                this.outEncrypted.release();
                this.inPlain.release();

                this.status = Status.CLOSED;
                this.session.close(closeMode);
            }
        } finally {
            this.session.getLock().unlock();
        }
    }

    @Override
    public Status getStatus() {
        return this.status;
    }

    @Override
    public void enqueue(final Command command, final Command.Priority priority) {
        this.session.getLock().lock();
        try {
            this.session.enqueue(command, priority);
            setEvent(SelectionKey.OP_WRITE);
        } finally {
            this.session.getLock().unlock();
        }
    }

    @Override
    public boolean hasCommands() {
        return this.session.hasCommands();
    }

    @Override
    public Command poll() {
        return this.session.poll();
    }

    @Override
    public ByteChannel channel() {
        return this.session.channel();
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
    public int getEventMask() {
        this.session.getLock().lock();
        try {
            return this.appEventMask;
        } finally {
            this.session.getLock().unlock();
        }
    }

    @Override
    public void setEventMask(final int ops) {
        this.session.getLock().lock();
        try {
            this.appEventMask = ops;
            updateEventMask();
        } finally {
            this.session.getLock().unlock();
        }
    }

    @Override
    public void setEvent(final int op) {
        this.session.getLock().lock();
        try {
            this.appEventMask = this.appEventMask | op;
            updateEventMask();
        } finally {
            this.session.getLock().unlock();
        }
    }

    @Override
    public void clearEvent(final int op) {
        this.session.getLock().lock();
        try {
            this.appEventMask = this.appEventMask & ~op;
            updateEventMask();
        } finally {
            this.session.getLock().unlock();
        }
    }

    @Override
    public Timeout getSocketTimeout() {
        return this.session.getSocketTimeout();
    }

    @Override
    public void setSocketTimeout(final Timeout timeout) {
        this.socketTimeout = timeout;
        if (this.sslEngine.getHandshakeStatus() == HandshakeStatus.FINISHED) {
            this.session.setSocketTimeout(timeout);
        }
    }

    @Override
    public void updateReadTime() {
        this.session.updateReadTime();
    }

    @Override
    public void updateWriteTime() {
        this.session.updateWriteTime();
    }

    @Override
    public long getLastReadTime() {
        return this.session.getLastReadTime();
    }

    @Override
    public long getLastWriteTime() {
        return this.session.getLastWriteTime();
    }

    @Override
    public long getLastEventTime() {
        return this.session.getLastEventTime();
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
        this.session.getLock().lock();
        try {
            final StringBuilder buffer = new StringBuilder();
            buffer.append(this.session);
            buffer.append("[");
            buffer.append(this.status);
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
            buffer.append("]");
            return buffer.toString();
        } finally {
            this.session.getLock().unlock();
        }
    }

}
