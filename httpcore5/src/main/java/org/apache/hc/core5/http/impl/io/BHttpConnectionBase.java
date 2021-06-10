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
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.ContentLengthStrategy;
import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpMessage;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.BasicEndpointDetails;
import org.apache.hc.core5.http.impl.BasicHttpConnectionMetrics;
import org.apache.hc.core5.http.impl.BasicHttpTransportMetrics;
import org.apache.hc.core5.http.io.BHttpConnection;
import org.apache.hc.core5.http.io.SessionInputBuffer;
import org.apache.hc.core5.http.io.SessionOutputBuffer;
import org.apache.hc.core5.http.io.entity.EmptyInputStream;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.io.Closer;
import org.apache.hc.core5.net.InetAddressUtils;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Timeout;

class BHttpConnectionBase implements BHttpConnection {

    private static final Timeout STALE_CHECK_TIMEOUT = Timeout.ofMilliseconds(1);
    final Http1Config http1Config;
    final SessionInputBufferImpl inBuffer;
    final SessionOutputBufferImpl outbuffer;
    final BasicHttpConnectionMetrics connMetrics;
    final AtomicReference<SocketHolder> socketHolderRef;
    // Lazily initialized chunked request buffer provided to ChunkedOutputStream.
    private byte[] chunkedRequestBuffer;

    volatile ProtocolVersion version;
    volatile EndpointDetails endpointDetails;

    BHttpConnectionBase(
            final Http1Config http1Config,
            final CharsetDecoder charDecoder,
            final CharsetEncoder charEncoder) {
        this.http1Config = http1Config != null ? http1Config : Http1Config.DEFAULT;
        final BasicHttpTransportMetrics inTransportMetrics = new BasicHttpTransportMetrics();
        final BasicHttpTransportMetrics outTransportMetrics = new BasicHttpTransportMetrics();
        this.inBuffer = new SessionInputBufferImpl(inTransportMetrics,
                this.http1Config.getBufferSize(), -1,
                this.http1Config.getMaxLineLength(), charDecoder);
        this.outbuffer = new SessionOutputBufferImpl(outTransportMetrics,
                this.http1Config.getBufferSize(),
                this.http1Config.getChunkSizeHint(), charEncoder);
        this.connMetrics = new BasicHttpConnectionMetrics(inTransportMetrics, outTransportMetrics);
        this.socketHolderRef = new AtomicReference<>();
    }

    protected SocketHolder ensureOpen() throws IOException {
        final SocketHolder socketHolder = this.socketHolderRef.get();
        if (socketHolder == null) {
            throw new ConnectionClosedException();
        }
        return socketHolder;
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
        bind(new SocketHolder(socket));
    }

    protected void bind(final SocketHolder socketHolder) throws IOException {
        Args.notNull(socketHolder, "Socket holder");
        this.socketHolderRef.set(socketHolder);
        this.endpointDetails = null;
    }

    @Override
    public boolean isOpen() {
        return this.socketHolderRef.get() != null;
    }

    /**
     * @since 5.0
     */
    @Override
    public ProtocolVersion getProtocolVersion() {
        return this.version;
    }

    protected SocketHolder getSocketHolder() {
        return this.socketHolderRef.get();
    }

    protected OutputStream createContentOutputStream(
            final long len,
            final SessionOutputBuffer buffer,
            final OutputStream outputStream,
            final Supplier<List<? extends Header>> trailers) {
        if (len >= 0) {
            return new ContentLengthOutputStream(buffer, outputStream, len);
        } else if (len == ContentLengthStrategy.CHUNKED) {
            return new ChunkedOutputStream(buffer, outputStream, getChunkedRequestBuffer(), trailers);
        } else {
            return new IdentityOutputStream(buffer, outputStream);
        }
    }

    private byte[] getChunkedRequestBuffer() {
        if (chunkedRequestBuffer == null) {
            final int chunkSizeHint = this.http1Config.getChunkSizeHint();
            chunkedRequestBuffer = new byte[chunkSizeHint > 0 ? chunkSizeHint : 8192];
        }
        return chunkedRequestBuffer;
    }

    protected InputStream createContentInputStream(
            final long len,
            final SessionInputBuffer buffer,
            final InputStream inputStream) {
        if (len > 0) {
            return new ContentLengthInputStream(buffer, inputStream, len);
        } else if (len == 0) {
            return EmptyInputStream.INSTANCE;
        } else if (len == ContentLengthStrategy.CHUNKED) {
            return new ChunkedInputStream(buffer, inputStream, this.http1Config);
        } else {
            return new IdentityInputStream(buffer, inputStream);
        }
    }

    HttpEntity createIncomingEntity(
            final HttpMessage message,
            final SessionInputBuffer inBuffer,
            final InputStream inputStream,
            final long len) {
        return new IncomingHttpEntity(
                createContentInputStream(len, inBuffer, inputStream),
                len >= 0 ? len : -1, len == ContentLengthStrategy.CHUNKED,
                message.getFirstHeader(HttpHeaders.CONTENT_TYPE),
                message.getFirstHeader(HttpHeaders.CONTENT_ENCODING));
    }

    @Override
    public SocketAddress getRemoteAddress() {
        final SocketHolder socketHolder = this.socketHolderRef.get();
        return socketHolder != null ? socketHolder.getSocket().getRemoteSocketAddress() : null;
    }

    @Override
    public SocketAddress getLocalAddress() {
        final SocketHolder socketHolder = this.socketHolderRef.get();
        return socketHolder != null ? socketHolder.getSocket().getLocalSocketAddress() : null;
    }

    @Override
    public void setSocketTimeout(final Timeout timeout) {
        final SocketHolder socketHolder = this.socketHolderRef.get();
        if (socketHolder != null) {
            try {
                socketHolder.getSocket().setSoTimeout(Timeout.defaultsToDisabled(timeout).toMillisecondsIntBound());
            } catch (final SocketException ignore) {
                // It is not quite clear from the Sun's documentation if there are any
                // other legitimate cases for a socket exception to be thrown when setting
                // SO_TIMEOUT besides the socket being already closed
            }
        }
    }

    @Override
    public Timeout getSocketTimeout() {
        final SocketHolder socketHolder = this.socketHolderRef.get();
        if (socketHolder != null) {
            try {
                return Timeout.ofMilliseconds(socketHolder.getSocket().getSoTimeout());
            } catch (final SocketException ignore) {
            }
        }
        return Timeout.DISABLED;
    }

    @Override
    public void close(final CloseMode closeMode) {
        final SocketHolder socketHolder = this.socketHolderRef.getAndSet(null);
        if (socketHolder != null) {
            final Socket socket = socketHolder.getSocket();
            try {
                if (closeMode == CloseMode.IMMEDIATE) {
                    // force abortive close (RST)
                    socket.setSoLinger(true, 0);
                }
            } catch (final IOException ignore) {
            } finally {
                Closer.closeQuietly(socket);
            }
        }
    }

    @Override
    public void close() throws IOException {
        final SocketHolder socketHolder = this.socketHolderRef.getAndSet(null);
        if (socketHolder != null) {
            try (final Socket socket = socketHolder.getSocket()) {
                this.inBuffer.clear();
                this.outbuffer.flush(socketHolder.getOutputStream());
            }
        }
    }

    private int fillInputBuffer(final Timeout timeout) throws IOException {
        final SocketHolder socketHolder = ensureOpen();
        final Socket socket = socketHolder.getSocket();
        final int oldtimeout = socket.getSoTimeout();
        try {
            socket.setSoTimeout(timeout.toMillisecondsIntBound());
            return this.inBuffer.fillBuffer(socketHolder.getInputStream());
        } finally {
            socket.setSoTimeout(oldtimeout);
        }
    }

    protected boolean awaitInput(final Timeout timeout) throws IOException {
        if (this.inBuffer.hasBufferedData()) {
            return true;
        }
        fillInputBuffer(timeout);
        return this.inBuffer.hasBufferedData();
    }

    @Override
    public boolean isDataAvailable(final Timeout timeout) throws IOException {
        ensureOpen();
        try {
            return awaitInput(timeout);
        } catch (final SocketTimeoutException ex) {
            return false;
        }
    }

    @Override
    public boolean isStale() throws IOException {
        if (!isOpen()) {
            return true;
        }
        try {
            final int bytesRead = fillInputBuffer(STALE_CHECK_TIMEOUT);
            return bytesRead < 0;
        } catch (final SocketTimeoutException ex) {
            return false;
        } catch (final SocketException ex) {
            return true;
        }
    }

    @Override
    public void flush() throws IOException {
        final SocketHolder socketHolder = ensureOpen();
        this.outbuffer.flush(socketHolder.getOutputStream());
    }

    protected void incrementRequestCount() {
        this.connMetrics.incrementRequestCount();
    }

    protected void incrementResponseCount() {
        this.connMetrics.incrementResponseCount();
    }

    @Override
    public SSLSession getSSLSession() {
        final SocketHolder socketHolder = this.socketHolderRef.get();
        if (socketHolder != null) {
            final Socket socket = socketHolder.getSocket();
            return socket instanceof SSLSocket ? ((SSLSocket) socket).getSession() : null;
        }
        return null;
    }

    @Override
    public EndpointDetails getEndpointDetails() {
        if (endpointDetails == null) {
            final SocketHolder socketHolder = this.socketHolderRef.get();
            if (socketHolder != null) {
                @SuppressWarnings("resource")
                final Socket socket = socketHolder.getSocket();
                Timeout socketTimeout;
                try {
                    socketTimeout = Timeout.ofMilliseconds(socket.getSoTimeout());
                } catch (final SocketException e) {
                    socketTimeout = Timeout.DISABLED;
                }
                endpointDetails = new BasicEndpointDetails(
                        socket.getRemoteSocketAddress(),
                        socket.getLocalSocketAddress(),
                        this.connMetrics,
                        socketTimeout);
            }
        }
        return endpointDetails;
    }

    @Override
    public String toString() {
        final SocketHolder socketHolder = this.socketHolderRef.get();
        if (socketHolder != null) {
            final Socket socket = socketHolder.getSocket();
            final StringBuilder buffer = new StringBuilder();
            final SocketAddress remoteAddress = socket.getRemoteSocketAddress();
            final SocketAddress localAddress = socket.getLocalSocketAddress();
            if (remoteAddress != null && localAddress != null) {
                InetAddressUtils.formatAddress(buffer, localAddress);
                buffer.append("<->");
                InetAddressUtils.formatAddress(buffer, remoteAddress);
            }
            return buffer.toString();
        }
        return "[Not bound]";
    }

}
