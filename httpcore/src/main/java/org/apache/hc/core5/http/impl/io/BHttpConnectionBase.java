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

package org.apache.hc.core5.http.impl.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.ContentLengthStrategy;
import org.apache.hc.core5.http.HttpConnectionMetrics;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpMessage;
import org.apache.hc.core5.http.TrailerSupplier;
import org.apache.hc.core5.http.config.MessageConstraints;
import org.apache.hc.core5.http.impl.HttpConnectionMetricsImpl;
import org.apache.hc.core5.http.impl.IncomingHttpEntity;
import org.apache.hc.core5.http.io.BHttpConnection;
import org.apache.hc.core5.http.io.SessionInputBuffer;
import org.apache.hc.core5.http.io.SessionOutputBuffer;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.NetUtils;

class BHttpConnectionBase implements BHttpConnection {

    final SessionInputBufferImpl inbuffer;
    final SessionOutputBufferImpl outbuffer;
    final MessageConstraints messageConstraints;
    final HttpConnectionMetricsImpl connMetrics;
    final AtomicReference<Socket> socketHolder;

    BHttpConnectionBase(
            final int buffersize,
            final int fragmentSizeHint,
            final CharsetDecoder chardecoder,
            final CharsetEncoder charencoder,
            final MessageConstraints messageConstraints) {
        super();
        Args.positive(buffersize, "Buffer size");
        final HttpTransportMetricsImpl inTransportMetrics = new HttpTransportMetricsImpl();
        final HttpTransportMetricsImpl outTransportMetrics = new HttpTransportMetricsImpl();
        this.inbuffer = new SessionInputBufferImpl(inTransportMetrics, buffersize, -1,
                messageConstraints != null ? messageConstraints : MessageConstraints.DEFAULT, chardecoder);
        this.outbuffer = new SessionOutputBufferImpl(outTransportMetrics, buffersize, fragmentSizeHint,
                charencoder);
        this.messageConstraints = messageConstraints;
        this.connMetrics = new HttpConnectionMetricsImpl(inTransportMetrics, outTransportMetrics);
        this.socketHolder = new AtomicReference<>();
    }

    protected void ensureOpen() throws IOException {
        final Socket socket = this.socketHolder.get();
        if (socket == null) {
            throw new ConnectionClosedException("Connection is closed");
        }
        if (!this.inbuffer.isBound()) {
            this.inbuffer.bind(getSocketInputStream(socket));
        }
        if (!this.outbuffer.isBound()) {
            this.outbuffer.bind(getSocketOutputStream(socket));
        }
    }

    protected InputStream getSocketInputStream(final Socket socket) throws IOException {
        return socket.getInputStream();
    }

    protected OutputStream getSocketOutputStream(final Socket socket) throws IOException {
        return socket.getOutputStream();
    }

    /**
     * Binds this connection to the given {@link Socket}. This socket will be
     * used by the connection to send and receive data.
     * <p>
     * After this method's execution the connection status will be reported
     * as open and the {@link #isOpen()} will return {@code true}.
     *
     * @param socket the socket.
     * @throws IOException in case of an I/O error.
     */
    protected void bind(final Socket socket) throws IOException {
        Args.notNull(socket, "Socket");
        this.socketHolder.set(socket);
        this.inbuffer.bind(null);
        this.outbuffer.bind(null);
    }

    @Override
    public boolean isOpen() {
        return this.socketHolder.get() != null;
    }

    Socket getSocket() {
        return this.socketHolder.get();
    }

    protected OutputStream createContentOutputStream(
            final long len,
            final SessionOutputBuffer outbuffer,
            final TrailerSupplier trailers) {
        if (len >= 0) {
            return new ContentLengthOutputStream(outbuffer, len);
        } else if (len == ContentLengthStrategy.CHUNKED) {
            return new ChunkedOutputStream(2048, outbuffer, trailers);
        } else {
            return new IdentityOutputStream(outbuffer);
        }
    }

    protected InputStream createContentInputStream(
            final long len,
            final SessionInputBuffer inbuffer) {
        if (len > 0) {
            return new ContentLengthInputStream(inbuffer, len);
        } else if (len == 0) {
            return EmptyInputStream.INSTANCE;
        } else if (len == ContentLengthStrategy.CHUNKED) {
            return new ChunkedInputStream(inbuffer, this.messageConstraints);
        } else {
            return new IdentityInputStream(inbuffer);
        }
    }

    HttpEntity createIncomingEntity(
            final HttpMessage message,
            final SessionInputBuffer inbuffer,
            final long len) throws HttpException {
        return new IncomingHttpEntity(
                createContentInputStream(len, inbuffer),
                len >= 0 ? len : -1, len == ContentLengthStrategy.CHUNKED,
                message.getFirstHeader(HttpHeaders.CONTENT_TYPE),
                message.getFirstHeader(HttpHeaders.CONTENT_ENCODING));
    }

    @Override
    public SocketAddress getRemoteAddress() {
        final Socket socket = this.socketHolder.get();
        return socket != null ? socket.getRemoteSocketAddress() : null;
    }

    @Override
    public SocketAddress getLocalAddress() {
        final Socket socket = this.socketHolder.get();
        return socket != null ? socket.getLocalSocketAddress() : null;
    }

    @Override
    public void setSocketTimeout(final int timeout) {
        final Socket socket = this.socketHolder.get();
        if (socket != null) {
            try {
                socket.setSoTimeout(timeout);
            } catch (final SocketException ignore) {
                // It is not quite clear from the Sun's documentation if there are any
                // other legitimate cases for a socket exception to be thrown when setting
                // SO_TIMEOUT besides the socket being already closed
            }
        }
    }

    @Override
    public int getSocketTimeout() {
        final Socket socket = this.socketHolder.get();
        if (socket != null) {
            try {
                return socket.getSoTimeout();
            } catch (final SocketException ignore) {
                return -1;
            }
        }
        return -1;
    }

    @Override
    public void shutdown() throws IOException {
        final Socket socket = this.socketHolder.getAndSet(null);
        if (socket != null) {
            // force abortive close (RST)
            try {
                socket.setSoLinger(true, 0);
            } catch (final IOException ex) {
            } finally {
                socket.close();
            }
        }
    }

    @Override
    public void close() throws IOException {
        final Socket socket = this.socketHolder.getAndSet(null);
        if (socket != null) {
            try {
                this.inbuffer.clear();
                this.outbuffer.flush();
                try {
                    try {
                        socket.shutdownOutput();
                    } catch (final IOException ignore) {
                    }
                    try {
                        socket.shutdownInput();
                    } catch (final IOException ignore) {
                    }
                } catch (final UnsupportedOperationException ignore) {
                    // if one isn't supported, the other one isn't either
                }
            } finally {
                socket.close();
            }
        }
    }

    private int fillInputBuffer(final int timeout) throws IOException {
        final Socket socket = this.socketHolder.get();
        final int oldtimeout = socket.getSoTimeout();
        try {
            socket.setSoTimeout(timeout);
            return this.inbuffer.fillBuffer();
        } finally {
            socket.setSoTimeout(oldtimeout);
        }
    }

    protected boolean awaitInput(final int timeout) throws IOException {
        if (this.inbuffer.hasBufferedData()) {
            return true;
        }
        fillInputBuffer(timeout);
        return this.inbuffer.hasBufferedData();
    }

    @Override
    public boolean isDataAvailable(final int timeout) throws IOException {
        ensureOpen();
        try {
            return awaitInput(timeout);
        } catch (final SocketTimeoutException ex) {
            return false;
        }
    }

    @Override
    public boolean isStale() {
        if (!isOpen()) {
            return true;
        }
        try {
            final int bytesRead = fillInputBuffer(1);
            return bytesRead < 0;
        } catch (final SocketTimeoutException ex) {
            return false;
        } catch (final IOException ex) {
            return true;
        }
    }

    @Override
    public void flush() throws IOException {
        ensureOpen();
        this.outbuffer.flush();
    }

    protected void incrementRequestCount() {
        this.connMetrics.incrementRequestCount();
    }

    protected void incrementResponseCount() {
        this.connMetrics.incrementResponseCount();
    }

    @Override
    public HttpConnectionMetrics getMetrics() {
        return this.connMetrics;
    }

    @Override
    public String toString() {
        final Socket socket = this.socketHolder.get();
        if (socket != null) {
            final StringBuilder buffer = new StringBuilder();
            final SocketAddress remoteAddress = socket.getRemoteSocketAddress();
            final SocketAddress localAddress = socket.getLocalSocketAddress();
            if (remoteAddress != null && localAddress != null) {
                NetUtils.formatAddress(buffer, localAddress);
                buffer.append("<->");
                NetUtils.formatAddress(buffer, remoteAddress);
            }
            return buffer.toString();
        }
        return "[Not bound]";
    }

}
