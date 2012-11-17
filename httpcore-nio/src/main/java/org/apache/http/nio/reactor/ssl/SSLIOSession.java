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

import org.apache.http.annotation.ThreadSafe;
import org.apache.http.nio.reactor.EventMask;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.reactor.SocketAccessor;
import org.apache.http.nio.reactor.SessionBufferStatus;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;

/**
 * <tt>SSLIOSession</tt> is a decorator class intended to transparently extend
 * an {@link IOSession} with transport layer security capabilities based on
 * the SSL/TLS protocol.
 * <p/>
 * The resultant instance of <tt>SSLIOSession</tt> must be added to the original
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

    private final IOSession session;
    private final SSLMode defaultMode;
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
    private volatile boolean initialized;

    /**
     * Creates new instance of <tt>SSLIOSession</tt> class.
     *
     * @param session I/O session to be decorated with the TLS/SSL capabilities.
     * @param defaultMode default mode (client or server)
     * @param sslContext SSL context to use for this I/O session.
     * @param handler optional SSL setup handler. May be <code>null</code>.
     */
    public SSLIOSession(
            final IOSession session,
            final SSLMode defaultMode,
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
        this.defaultMode = defaultMode;
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

    protected SSLSetupHandler getSSLSetupHandler() {
        return this.handler;
    }

    /**
     * Returns <code>true</code> is the session has been fully initialized,
     * <code>false</code> otherwise.
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
     * @param mode mode of operation (client or server).
     * @throws SSLException in case of a SSL protocol exception.
     * @throws IllegalStateException if the session has already been initialized.
     */
    public synchronized void initialize(final SSLMode mode) throws SSLException {
        if (this.initialized) {
            throw new IllegalStateException("SSL I/O session already initialized");
        }
        if (this.status >= IOSession.CLOSING) {
            return;
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
            this.handler.initalize(this.sslEngine);
        }
        this.initialized = true;
        this.sslEngine.beginHandshake();
        doHandshake();
    }

    /**
     * Initializes the session in the default operation mode. This method
     * invokes the {@link SSLSetupHandler#initalize(SSLEngine)} callback
     * if an instance of {@link SSLSetupHandler} was specified at
     * the construction time.
     *
     * @throws SSLException in case of a SSL protocol exception.
     * @throws IllegalStateException if the session has already been initialized.
     */
    public void initialize() throws SSLException {
        initialize(this.defaultMode);
    }

    public synchronized SSLSession getSSLSession() {
        return this.sslEngine.getSession();
    }

    // A works-around for exception handling craziness in Sun/Oracle's SSLEngine
    // implementation.
    //
    // sun.security.pkcs11.wrapper.PKCS11Exception is re-thrown as
    // plain RuntimeException in sun.security.ssl.Handshaker#checkThrown
    private SSLException convert(final RuntimeException ex) throws SSLException {
        Throwable cause = ex.getCause();
        if (cause == null) {
            cause = ex;
        }
        return new SSLException(cause);
    }

    private SSLEngineResult doWrap(final ByteBuffer src, final ByteBuffer dst) throws SSLException {
        try {
            return this.sslEngine.wrap(src, dst);
        } catch (RuntimeException ex) {
            throw convert(ex);
        }
    }

    private SSLEngineResult doUnwrap(final ByteBuffer src, final ByteBuffer dst) throws SSLException {
        try {
            return this.sslEngine.unwrap(src, dst);
        } catch (RuntimeException ex) {
            throw convert(ex);
        }
    }

    private void doRunTask() throws SSLException {
        try {
            Runnable r = this.sslEngine.getDelegatedTask();
            if (r != null) {
                r.run();
            }
        } catch (RuntimeException ex) {
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
                this.outPlain.flip();
                result = doWrap(this.outPlain, this.outEncrypted);
                this.outPlain.compact();
                if (result.getStatus() != Status.OK) {
                    handshaking = false;
                }
                break;
            case NEED_UNWRAP:
                // Process incoming handshake data
                this.inEncrypted.flip();
                result = doUnwrap(this.inEncrypted, this.inPlain);
                this.inEncrypted.compact();
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
        if (this.status == CLOSING && this.sslEngine.isOutboundDone()
                && (this.endOfStream || this.sslEngine.isInboundDone())) {
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
        if (this.endOfStream) {
            return -1;
        }
        return this.session.channel().read(this.inEncrypted);
    }

    private boolean decryptData() throws SSLException {
        boolean decrypted = false;
        while (this.inEncrypted.position() > 0) {
            this.inEncrypted.flip();
            SSLEngineResult result = doUnwrap(this.inEncrypted, this.inPlain);
            this.inEncrypted.compact();
            if (result.getStatus() == Status.OK) {
                decrypted = true;
            } else {
                break;
            }
            if (result.getHandshakeStatus() != HandshakeStatus.NOT_HANDSHAKING) {
                break;
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
        int bytesRead = receiveEncryptedData();
        if (bytesRead == -1) {
            this.endOfStream = true;
        }
        doHandshake();
        HandshakeStatus status = this.sslEngine.getHandshakeStatus();
        if (status == HandshakeStatus.NOT_HANDSHAKING || status == HandshakeStatus.FINISHED) {
            decryptData();
        }
        // Some decrypted data is available or at the end of stream
        return (this.appEventMask & SelectionKey.OP_READ) > 0
            && (this.inPlain.position() > 0
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
        if (src == null) {
            throw new IllegalArgumentException("Byte buffer may not be null");
        }
        if (this.status != ACTIVE) {
            return -1;
        }
        if (this.outPlain.position() > 0) {
            this.outPlain.flip();
            doWrap(this.outPlain, this.outEncrypted);
            this.outPlain.compact();
        }
        if (this.outPlain.position() == 0) {
            SSLEngineResult result = doWrap(src, this.outEncrypted);
            if (result.getStatus() == Status.CLOSED) {
                this.status = CLOSED;
            }
            return result.bytesConsumed();
        } else {
            return 0;
        }
    }

    private synchronized int readPlain(final ByteBuffer dst) {
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

    public synchronized void close() {
        if (this.status >= CLOSING) {
            return;
        }
        this.status = CLOSING;
        this.sslEngine.closeOutbound();
        updateEventMask();
    }

    public synchronized void shutdown() {
        if (this.status == CLOSED) {
            return;
        }
        this.status = CLOSED;
        this.session.shutdown();
    }

    public int getStatus() {
        return this.status;
    }

    public boolean isClosed() {
        return this.status >= CLOSING || this.session.isClosed();
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
        StringBuilder buffer = new StringBuilder();
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
        buffer.append(this.sslEngine.getHandshakeStatus());
        if (this.endOfStream) {
            buffer.append("][EOF][");
        }
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
    
    public Socket getSocket(){
    	if (this.session instanceof SocketAccessor){
    		return ((SocketAccessor) this.session).getSocket();
    	} else {
    		return null;
    	}
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
