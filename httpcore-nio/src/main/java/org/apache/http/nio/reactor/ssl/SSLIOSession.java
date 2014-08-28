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

package org.apache.http.nio.reactor.ssl;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;

import org.apache.http.HttpHost;
import org.apache.http.annotation.ThreadSafe;
import org.apache.http.nio.reactor.EventMask;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.reactor.SessionBufferStatus;
import org.apache.http.nio.reactor.SocketAccessor;
import org.apache.http.util.Args;
import org.apache.http.util.Asserts;

/**
 * {@code SSLIOSession} is a decorator class intended to transparently extend
 * an {@link IOSession} with transport layer security capabilities based on
 * the SSL/TLS protocol.
 * <p>
 * The resultant instance of {@code SSLIOSession} must be added to the original
 * I/O session as an attribute with the {@link #SESSION_KEY} key.
 * <pre>
 *  SSLContext sslcontext = SSLContext.getInstance("SSL");
 *  sslcontext.init(null, null, null);
 *  SSLIOSession sslsession = new SSLIOSession(
 *      iosession, SSLMode.CLIENT, sslcontext, null);
 *  iosession.setAttribute(SSLIOSession.SESSION_KEY, sslsession);
 * </pre>
 *
 * @since 4.2
 */
@ThreadSafe
public class SSLIOSession implements IOSession, SessionBufferStatus, SocketAccessor {

    /**
     * Name of the context attribute key, which can be used to obtain the
     * SSL session.
     */
    public static final String SESSION_KEY = "http.session.ssl";

    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);

    private final IOSession session;
    private final SSLEngine sslEngine;
    private final SSLBuffer inEncrypted;
    private final SSLBuffer outEncrypted;
    private final SSLBuffer inPlain;
    private final SSLBuffer outPlain;
    private final InternalByteChannel channel;
    private final SSLSetupHandler handler;

    private int appEventMask;
    private SessionBufferStatus appBufferStatus;

    private boolean endOfStream;
    private volatile SSLMode sslMode;
    private volatile int status;
    private volatile boolean initialized;

    /**
     * Creates new instance of {@code SSLIOSession} class. The instances created uses a
     * {@link PermanentSSLBufferManagementStrategy} to manage its buffers.
     *
     * @param session I/O session to be decorated with the TLS/SSL capabilities.
     * @param sslMode SSL mode (client or server)
     * @param host original host (applicable in client mode only)
     * @param sslContext SSL context to use for this I/O session.
     * @param handler optional SSL setup handler. May be {@code null}.
     *
     * @since 4.4
     */
    public SSLIOSession(
            final IOSession session,
            final SSLMode sslMode,
            final HttpHost host,
            final SSLContext sslContext,
            final SSLSetupHandler handler) {
        this(session, sslMode, host, sslContext, handler, new PermanentSSLBufferManagementStrategy());
    }

    /**
     * Creates new instance of {@code SSLIOSession} class.
     *
     * @param session I/O session to be decorated with the TLS/SSL capabilities.
     * @param sslMode SSL mode (client or server)
     * @param host original host (applicable in client mode only)
     * @param sslContext SSL context to use for this I/O session.
     * @param handler optional SSL setup handler. May be {@code null}.
     * @param bufferManagementStrategy buffer management strategy
     */
    public SSLIOSession(
            final IOSession session,
            final SSLMode sslMode,
            final HttpHost host,
            final SSLContext sslContext,
            final SSLSetupHandler handler,
            final SSLBufferManagementStrategy bufferManagementStrategy) {
        super();
        Args.notNull(session, "IO session");
        Args.notNull(sslContext, "SSL context");
        Args.notNull(bufferManagementStrategy, "Buffer management strategy");
        this.session = session;
        this.sslMode = sslMode;
        this.appEventMask = session.getEventMask();
        this.channel = new InternalByteChannel();
        this.handler = handler;

        // Override the status buffer interface
        this.session.setBufferStatus(this);

        if (this.sslMode == SSLMode.CLIENT && host != null) {
            this.sslEngine = sslContext.createSSLEngine(host.getHostName(), host.getPort());
        } else {
            this.sslEngine = sslContext.createSSLEngine();
        }

        // Allocate buffers for network (encrypted) data
        final int netBuffersize = this.sslEngine.getSession().getPacketBufferSize();
        this.inEncrypted = bufferManagementStrategy.constructBuffer(netBuffersize);
        this.outEncrypted = bufferManagementStrategy.constructBuffer(netBuffersize);

        // Allocate buffers for application (unencrypted) data
        final int appBuffersize = this.sslEngine.getSession().getApplicationBufferSize();
        this.inPlain = bufferManagementStrategy.constructBuffer(appBuffersize);
        this.outPlain = bufferManagementStrategy.constructBuffer(appBuffersize);
    }

    /**
     * Creates new instance of {@code SSLIOSession} class.
     *
     * @param session I/O session to be decorated with the TLS/SSL capabilities.
     * @param sslMode SSL mode (client or server)
     * @param sslContext SSL context to use for this I/O session.
     * @param handler optional SSL setup handler. May be {@code null}.
     */
    public SSLIOSession(
            final IOSession session,
            final SSLMode sslMode,
            final SSLContext sslContext,
            final SSLSetupHandler handler) {
        this(session, sslMode, null, sslContext, handler);
    }

    protected SSLSetupHandler getSSLSetupHandler() {
        return this.handler;
    }

    /**
     * Returns {@code true} is the session has been fully initialized,
     * {@code false} otherwise.
     */
    public boolean isInitialized() {
        return this.initialized;
    }

    /**
     * Initializes the session in the given {@link SSLMode}. This method
     * invokes the {@link SSLSetupHandler#initalize(SSLEngine)} callback
     * if an instance of {@link SSLSetupHandler} was specified at
     * the construction time.
     *
     * @deprecated (4.3) SSL mode must be set at construction time.
     */
    @Deprecated
    public synchronized void initialize(final SSLMode sslMode) throws SSLException {
        this.sslMode = sslMode;
        initialize();
    }

    /**
     * Initializes the session. This method invokes the {@link
     * SSLSetupHandler#initalize(SSLEngine)} callback if an instance of
     * {@link SSLSetupHandler} was specified at the construction time.
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
        if (this.handler != null) {
            this.handler.initalize(this.sslEngine);
        }
        this.initialized = true;
        this.sslEngine.beginHandshake();

        this.inEncrypted.release();
        this.outEncrypted.release();
        this.inPlain.release();
        this.outPlain.release();

        doHandshake();
    }

    public synchronized SSLSession getSSLSession() {
        return this.sslEngine.getSession();
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
                ByteBuffer outPlainBuf = this.outPlain.acquire();
                final ByteBuffer outEncryptedBuf = this.outEncrypted.acquire();

                // Perform operations
                outPlainBuf.flip();
                result = doWrap(outPlainBuf, outEncryptedBuf);
                outPlainBuf.compact();

                // Release outPlain if empty
                if (outPlainBuf.position() == 0) {
                    this.outPlain.release();
                    outPlainBuf = null;
                }


                if (result.getStatus() != Status.OK) {
                    handshaking = false;
                }
                break;
            case NEED_UNWRAP:
                // Process incoming handshake data

                // Acquire buffers
                ByteBuffer inEncryptedBuf = this.inEncrypted.acquire();
                ByteBuffer inPlainBuf = this.inPlain.acquire();

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
                        inEncryptedBuf = null;
                    }
                }

                if (this.status >= IOSession.CLOSING) {
                    this.inPlain.release();
                    inPlainBuf = null;
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
            if (this.handler != null) {
                this.handler.verify(this.session, this.sslEngine.getSession());
            }
        }
    }

    private void updateEventMask() {
        // Graceful session termination
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
        outEncryptedBuf.flip();
        final int bytesWritten = this.session.channel().write(outEncryptedBuf);
        outEncryptedBuf.compact();

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
                    throw new SSLException("Input buffer is full");
                }
                if (result.getStatus() == Status.OK) {
                    decrypted = true;
                } else {
                    break;
                }
                if (result.getHandshakeStatus() != HandshakeStatus.NOT_HANDSHAKING) {
                    break;
                }
                if (this.endOfStream) {
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
        return (this.appEventMask & SelectionKey.OP_READ) > 0
            && (this.inPlain.hasData()
                    || (this.appBufferStatus != null && this.appBufferStatus.hasBufferedInput())
                    || (this.endOfStream && this.status == ACTIVE));
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

    private synchronized int writePlain(final ByteBuffer src) throws SSLException {
        Args.notNull(src, "Byte buffer");
        if (this.status != ACTIVE) {
            return -1;
        }
        if (this.outPlain.hasData()) {
            // Acquire buffers
            ByteBuffer outPlainBuf = this.outPlain.acquire();
            final ByteBuffer outEncryptedBuf = this.outEncrypted.acquire();

            // Perform operations
            outPlainBuf.flip();
            doWrap(outPlainBuf, outEncryptedBuf);
            outPlainBuf.compact();

            // Release outPlain if empty
            if (outPlainBuf.position() == 0) {
                this.outPlain.release();
                outPlainBuf = null;
            }
        }
        if (!this.outPlain.hasData()) {
            final ByteBuffer outEncryptedBuf = this.outEncrypted.acquire();
            final SSLEngineResult result = doWrap(src, outEncryptedBuf);
            if (result.getStatus() == Status.CLOSED) {
                this.status = CLOSED;
            }
            return result.bytesConsumed();
        } else {
            return 0;
        }
    }

    private synchronized int readPlain(final ByteBuffer dst) {
        Args.notNull(dst, "Byte buffer");
        if (this.inPlain.hasData()) {
            // Acquire buffer
            ByteBuffer inPlainBuf = this.inPlain.acquire();

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
                inPlainBuf = null;
            }
            return n;
        } else {
            if (this.endOfStream) {
                return -1;
            } else {
                return 0;
            }
        }
    }

    @Override
    public synchronized void close() {
        if (this.status >= CLOSING) {
            return;
        }

        // deactivating buffers in here causes failed tests

        this.status = CLOSING;
        this.sslEngine.closeOutbound();
        updateEventMask();
    }

    @Override
    public synchronized void shutdown() {
        if (this.status == CLOSED) {
            return;
        }

        this.inEncrypted.release();
        this.outEncrypted.release();
        this.inPlain.release();
        this.outPlain.release();

        this.status = CLOSED;
        this.session.shutdown();
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
    public synchronized boolean hasBufferedInput() {
        return (this.appBufferStatus != null && this.appBufferStatus.hasBufferedInput())
            || this.inEncrypted.hasData()
            || this.inPlain.hasData();
    }

    @Override
    public synchronized boolean hasBufferedOutput() {
        return (this.appBufferStatus != null && this.appBufferStatus.hasBufferedOutput())
            || this.outEncrypted.hasData()
            || this.outPlain.hasData();
    }

    @Override
    public synchronized void setBufferStatus(final SessionBufferStatus status) {
        this.appBufferStatus = status;
    }

    @Override
    public Object getAttribute(final String name) {
        return this.session.getAttribute(name);
    }

    @Override
    public Object removeAttribute(final String name) {
        return this.session.removeAttribute(name);
    }

    @Override
    public void setAttribute(final String name, final Object obj) {
        this.session.setAttribute(name, obj);
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

    @Override
    public Socket getSocket(){
        if (this.session instanceof SocketAccessor){
            return ((SocketAccessor) this.session).getSocket();
        } else {
            return null;
        }
    }

    private class InternalByteChannel implements ByteChannel {

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

    }

}
