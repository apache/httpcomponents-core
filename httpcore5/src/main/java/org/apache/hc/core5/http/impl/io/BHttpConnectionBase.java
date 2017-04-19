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
import org.apache.hc.core5.http.config.H1Config;
import org.apache.hc.core5.http.impl.BasicEndpointDetails;
import org.apache.hc.core5.http.impl.BasicHttpConnectionMetrics;
import org.apache.hc.core5.http.impl.BasicHttpTransportMetrics;
import org.apache.hc.core5.http.io.BHttpConnection;
import org.apache.hc.core5.http.io.SessionInputBuffer;
import org.apache.hc.core5.http.io.SessionOutputBuffer;
import org.apache.hc.core5.io.ShutdownType;
import org.apache.hc.core5.net.InetAddressUtils;
import org.apache.hc.core5.util.Args;

class BHttpConnectionBase implements BHttpConnection {

    final H1Config h1Config;
    final SessionInputBufferImpl inbuffer;
    final SessionOutputBufferImpl outbuffer;
    final BasicHttpConnectionMetrics connMetrics;
    final AtomicReference<SocketHolder> socketHolderRef;

    volatile ProtocolVersion version;
    volatile EndpointDetails endpointDetails;

    BHttpConnectionBase(
            final H1Config h1Config,
            final CharsetDecoder chardecoder,
            final CharsetEncoder charencoder) {
        this.h1Config = h1Config != null ? h1Config : H1Config.DEFAULT;
        final BasicHttpTransportMetrics inTransportMetrics = new BasicHttpTransportMetrics();
        final BasicHttpTransportMetrics outTransportMetrics = new BasicHttpTransportMetrics();
        this.inbuffer = new SessionInputBufferImpl(inTransportMetrics,
                this.h1Config.getBufferSize(), -1,
                this.h1Config.getMaxLineLength(), chardecoder);
        this.outbuffer = new SessionOutputBufferImpl(outTransportMetrics,
                this.h1Config.getBufferSize(),
                this.h1Config.getChunkSizeHint(), charencoder);
        this.connMetrics = new BasicHttpConnectionMetrics(inTransportMetrics, outTransportMetrics);
        this.socketHolderRef = new AtomicReference<>();
    }

    protected SocketHolder ensureOpen() throws IOException {
        final SocketHolder socketHolder = this.socketHolderRef.get();
        if (socketHolder == null) {
            throw new ConnectionClosedException("Connection is closed");
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
            final int chunkSizeHint = h1Config.getChunkSizeHint() >= 0 ? h1Config.getChunkSizeHint() : 2048;
            return new ChunkedOutputStream(buffer, outputStream, chunkSizeHint, trailers);
        } else {
            return new IdentityOutputStream(buffer, outputStream);
        }
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
            return new ChunkedInputStream(buffer, inputStream, this.h1Config);
        } else {
            return new IdentityInputStream(buffer, inputStream);
        }
    }

    HttpEntity createIncomingEntity(
            final HttpMessage message,
            final SessionInputBuffer inbuffer,
            final InputStream inputStream,
            final long len) {
        return new IncomingHttpEntity(
                createContentInputStream(len, inbuffer, inputStream),
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
    public void setSocketTimeout(final int timeout) {
        final SocketHolder socketHolder = this.socketHolderRef.get();
        if (socketHolder != null) {
            try {
                socketHolder.getSocket().setSoTimeout(timeout);
            } catch (final SocketException ignore) {
                // It is not quite clear from the Sun's documentation if there are any
                // other legitimate cases for a socket exception to be thrown when setting
                // SO_TIMEOUT besides the socket being already closed
            }
        }
    }

    @Override
    public int getSocketTimeout() {
        final SocketHolder socketHolder = this.socketHolderRef.get();
        if (socketHolder != null) {
            try {
                return socketHolder.getSocket().getSoTimeout();
            } catch (final SocketException ignore) {
                return -1;
            }
        }
        return -1;
    }

    @Override
    public void shutdown(final ShutdownType shutdownType) {
        final SocketHolder socketHolder = this.socketHolderRef.getAndSet(null);
        if (socketHolder != null) {
            final Socket socket = socketHolder.getSocket();
            try {
                if (shutdownType == ShutdownType.IMMEDIATE) {
                    // force abortive close (RST)
                    socket.setSoLinger(true, 0);
                }
            } catch (final IOException ignore) {
            } finally {
                try {
                    socket.close();
                } catch (IOException ignore) {
                }
            }
        }
    }

    @Override
    public void close() throws IOException {
        final SocketHolder socketHolder = this.socketHolderRef.getAndSet(null);
        if (socketHolder != null) {
            try (final Socket socket = socketHolder.getSocket()) {
                this.inbuffer.clear();
                this.outbuffer.flush(socketHolder.getOutputStream());
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
            }
        }
    }

    private int fillInputBuffer(final int timeout) throws IOException {
        final SocketHolder socketHolder = ensureOpen();
        final Socket socket = socketHolder.getSocket();
        final int oldtimeout = socket.getSoTimeout();
        try {
            socket.setSoTimeout(timeout);
            return this.inbuffer.fillBuffer(socketHolder.getInputStream());
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
    public boolean isStale() throws IOException {
        if (!isOpen()) {
            return true;
        }
        try {
            final int bytesRead = fillInputBuffer(1);
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
        } else {
            return null;
        }
    }

    @Override
    public EndpointDetails getEndpointDetails() {
        if (endpointDetails == null) {
            final SocketHolder socketHolder = this.socketHolderRef.get();
            if (socketHolder != null) {
                final Socket socket = socketHolder.getSocket();
                endpointDetails = new BasicEndpointDetails(socket.getRemoteSocketAddress(), socket.getLocalSocketAddress(), this.connMetrics);
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
