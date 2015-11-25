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

package org.apache.hc.core5.http.impl.nio;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;

import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.ContentLengthStrategy;
import org.apache.hc.core5.http.HttpConnectionMetrics;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpMessage;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.TrailerSupplier;
import org.apache.hc.core5.http.config.MessageConstraints;
import org.apache.hc.core5.http.impl.HttpConnectionMetricsImpl;
import org.apache.hc.core5.http.impl.IncomingHttpEntity;
import org.apache.hc.core5.http.impl.io.HttpTransportMetricsImpl;
import org.apache.hc.core5.http.nio.ContentDecoder;
import org.apache.hc.core5.http.nio.ContentEncoder;
import org.apache.hc.core5.http.nio.NHttpConnection;
import org.apache.hc.core5.http.nio.SessionInputBuffer;
import org.apache.hc.core5.http.nio.SessionOutputBuffer;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.reactor.EventMask;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.SessionBufferStatus;
import org.apache.hc.core5.reactor.SocketAccessor;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.ByteBufferAllocator;
import org.apache.hc.core5.util.NetUtils;

class NHttpConnectionBase implements NHttpConnection, SessionBufferStatus, SocketAccessor {

    final SessionInputBufferImpl inbuf;
    final SessionOutputBufferImpl outbuf;
    final int fragmentSizeHint;
    final MessageConstraints constraints;

    final HttpTransportMetricsImpl inTransportMetrics;
    final HttpTransportMetricsImpl outTransportMetrics;
    final HttpConnectionMetricsImpl connMetrics;

    volatile HttpContext context;
    volatile IOSession session;
    volatile ContentDecoder contentDecoder;
    volatile boolean hasBufferedInput;
    volatile ContentEncoder contentEncoder;
    volatile boolean hasBufferedOutput;
    volatile HttpRequest request;
    volatile HttpResponse response;

    volatile int status;

    NHttpConnectionBase(
            final IOSession session,
            final int buffersize,
            final int fragmentSizeHint,
            final ByteBufferAllocator allocator,
            final CharsetDecoder chardecoder,
            final CharsetEncoder charencoder,
            final MessageConstraints constraints) {
        Args.notNull(session, "I/O session");
        Args.positive(buffersize, "Buffer size");
        int linebuffersize = buffersize;
        if (linebuffersize > 512) {
            linebuffersize = 512;
        }
        this.inbuf = new SessionInputBufferImpl(buffersize, linebuffersize, chardecoder, allocator);
        this.outbuf = new SessionOutputBufferImpl(buffersize, linebuffersize, charencoder, allocator);
        this.fragmentSizeHint = fragmentSizeHint >= 0 ? fragmentSizeHint : buffersize;
        this.inTransportMetrics = new HttpTransportMetricsImpl();
        this.outTransportMetrics = new HttpTransportMetricsImpl();
        this.connMetrics = new HttpConnectionMetricsImpl(this.inTransportMetrics, this.outTransportMetrics);
        this.constraints = constraints != null ? constraints : MessageConstraints.DEFAULT;

        setSession(session);
        this.status = ACTIVE;
    }

    NHttpConnectionBase(
            final IOSession session,
            final int buffersize,
            final int fragmentSizeHint,
            final ByteBufferAllocator allocator,
            final CharsetDecoder chardecoder,
            final CharsetEncoder charencoder) {
        this(session, buffersize, fragmentSizeHint, allocator, chardecoder, charencoder, null);
    }

    private void setSession(final IOSession session) {
        this.session = session;
        this.context = new SessionHttpContext(this.session);
        this.session.setBufferStatus(this);
    }

    /**
     * Binds the connection to a different {@link IOSession}. This may be necessary
     * when the underlying I/O session gets upgraded with SSL/TLS encryption.
     *
     * @since 4.2
     */
    protected void bind(final IOSession session) {
        Args.notNull(session, "I/O session");
        setSession(session);
    }

    @Override
    public int getStatus() {
        return this.status;
    }

    @Override
    public HttpContext getContext() {
        return this.context;
    }

    @Override
    public HttpRequest getHttpRequest() {
        return this.request;
    }

    @Override
    public HttpResponse getHttpResponse() {
        return this.response;
    }

    @Override
    public void requestInput() {
        this.session.setEvent(EventMask.READ);
    }

    @Override
    public void requestOutput() {
        this.session.setEvent(EventMask.WRITE);
    }

    @Override
    public void suspendInput() {
        this.session.clearEvent(EventMask.READ);
    }

    @Override
    public void suspendOutput() {
        this.session.clearEvent(EventMask.WRITE);
    }

    HttpEntity createIncomingEntity(
            final HttpMessage message,
            final long len) throws HttpException {
        return new IncomingHttpEntity(
                null,
                len >= 0 ? len : -1, len == ContentLengthStrategy.CHUNKED,
                message.getFirstHeader(HttpHeaders.CONTENT_TYPE),
                message.getFirstHeader(HttpHeaders.CONTENT_ENCODING));
    }

    /**
     * Factory method for {@link ContentDecoder} instances.
     *
     * @param len content length, if known, {@link ContentLengthStrategy#CHUNKED} or
     *   {@link ContentLengthStrategy#UNDEFINED}, if unknown.
     * @param channel the session channel.
     * @param buffer the session buffer.
     * @param metrics transport metrics.
     *
     * @return content decoder.
     *
     * @since 4.1
     */
    protected ContentDecoder createContentDecoder(
            final long len,
            final ReadableByteChannel channel,
            final SessionInputBuffer buffer,
            final HttpTransportMetricsImpl metrics) {
        if (len >= 0) {
            return new LengthDelimitedDecoder(channel, buffer, metrics, len);
        } else if (len == ContentLengthStrategy.CHUNKED) {
            return new ChunkDecoder(channel, buffer, this.constraints, metrics);
        } else {
            return new IdentityDecoder(channel, buffer, metrics);
        }
    }

    /**
     * Factory method for {@link ContentEncoder} instances.
     *
     * @param len content length, if known, {@link ContentLengthStrategy#CHUNKED} or
     *   {@link ContentLengthStrategy#UNDEFINED}, if unknown.
     * @param channel the session channel.
     * @param buffer the session buffer.
     * @param metrics transport metrics.
     *
     * @param trailers
     * @return content encoder.
     *
     * @since 4.1
     */
    protected ContentEncoder createContentEncoder(
            final long len,
            final WritableByteChannel channel,
            final SessionOutputBuffer buffer,
            final HttpTransportMetricsImpl metrics,
            final TrailerSupplier trailers) {
        if (len >= 0) {
            return new LengthDelimitedEncoder(channel, buffer, metrics, len, this.fragmentSizeHint);
        } else if (len == ContentLengthStrategy.CHUNKED) {
            return new ChunkEncoder(channel, buffer, metrics, this.fragmentSizeHint, trailers);
        } else {
            return new IdentityEncoder(channel, buffer, metrics, this.fragmentSizeHint);
        }
    }

    @Override
    public boolean hasBufferedInput() {
        return this.hasBufferedInput;
    }

    @Override
    public boolean hasBufferedOutput() {
        return this.hasBufferedOutput;
    }

    /**
     * Assets if the connection is still open.
     *
     * @throws ConnectionClosedException in case the connection has already
     *   been closed.
     */
    protected void assertNotClosed() throws ConnectionClosedException {
        if (this.status != ACTIVE) {
            throw new ConnectionClosedException("Connection is closed");
        }
    }

    @Override
    public void close() throws IOException {
        if (this.status != ACTIVE) {
            return;
        }
        this.status = CLOSING;
        if (this.outbuf.hasData()) {
            this.session.setEvent(EventMask.WRITE);
        } else {
            this.session.close();
            this.status = CLOSED;
        }
    }

    @Override
    public boolean isOpen() {
        return this.status == ACTIVE && !this.session.isClosed();
    }

    @Override
    public boolean isDataAvailable() {
        return this.session.hasBufferedInput();
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return this.session.getRemoteAddress();
    }

    @Override
    public SocketAddress getLocalAddress() {
        return this.session.getLocalAddress();
    }

    @Override
    public void setSocketTimeout(final int timeout) {
        this.session.setSocketTimeout(timeout);
    }

    @Override
    public int getSocketTimeout() {
        return this.session.getSocketTimeout();
    }

    @Override
    public void shutdown() throws IOException {
        this.status = CLOSED;
        this.session.shutdown();
    }

    @Override
    public HttpConnectionMetrics getMetrics() {
        return this.connMetrics;
    }

    @Override
    public String toString() {
        final SocketAddress remoteAddress = this.session.getRemoteAddress();
        final SocketAddress localAddress = this.session.getLocalAddress();
        if (remoteAddress != null && localAddress != null) {
            final StringBuilder buffer = new StringBuilder();
            NetUtils.formatAddress(buffer, localAddress);
            buffer.append("<->");
            NetUtils.formatAddress(buffer, remoteAddress);
            return buffer.toString();
        }
        return "[Not bound]";
    }

    @Override
    public Socket getSocket() {
        if (this.session instanceof SocketAccessor) {
            return ((SocketAccessor) this.session).getSocket();
        }
        return null;
    }

}
