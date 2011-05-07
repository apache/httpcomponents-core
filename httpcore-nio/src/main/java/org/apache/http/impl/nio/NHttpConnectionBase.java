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

package org.apache.http.impl.nio;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import org.apache.http.ConnectionClosedException;
import org.apache.http.Header;
import org.apache.http.HttpConnectionMetrics;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpInetConnection;
import org.apache.http.HttpMessage;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.entity.ContentLengthStrategy;
import org.apache.http.impl.HttpConnectionMetricsImpl;
import org.apache.http.impl.entity.LaxContentLengthStrategy;
import org.apache.http.impl.entity.StrictContentLengthStrategy;
import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.NHttpConnection;
import org.apache.http.impl.nio.codecs.ChunkDecoder;
import org.apache.http.impl.nio.codecs.ChunkEncoder;
import org.apache.http.impl.nio.codecs.IdentityDecoder;
import org.apache.http.impl.nio.codecs.IdentityEncoder;
import org.apache.http.impl.nio.codecs.LengthDelimitedDecoder;
import org.apache.http.impl.nio.codecs.LengthDelimitedEncoder;
import org.apache.http.impl.nio.reactor.SessionInputBufferImpl;
import org.apache.http.impl.nio.reactor.SessionOutputBufferImpl;
import org.apache.http.io.HttpTransportMetrics;
import org.apache.http.nio.reactor.EventMask;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.reactor.SessionBufferStatus;
import org.apache.http.nio.reactor.SessionInputBuffer;
import org.apache.http.nio.reactor.SessionOutputBuffer;
import org.apache.http.nio.util.ByteBufferAllocator;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;

/**
 * This class serves as a base for all {@link NHttpConnection} implementations
 * and implements functionality common to both client and server
 * HTTP connections.
 * <p>
 * The following parameters can be used to customize the behavior of this
 * class:
 * <ul>
 *  <li>{@link org.apache.http.params.CoreProtocolPNames#HTTP_ELEMENT_CHARSET}</li>
 *  <li>{@link org.apache.http.params.CoreConnectionPNames#SOCKET_BUFFER_SIZE}</li>
 * </ul>
 *
 * @since 4.0
 */
public class NHttpConnectionBase
        implements NHttpConnection, HttpInetConnection, SessionBufferStatus {

    protected final ContentLengthStrategy incomingContentStrategy;
    protected final ContentLengthStrategy outgoingContentStrategy;

    protected final SessionInputBufferImpl inbuf;
    protected final SessionOutputBufferImpl outbuf;

    protected final HttpTransportMetricsImpl inTransportMetrics;
    protected final HttpTransportMetricsImpl outTransportMetrics;
    protected final HttpConnectionMetricsImpl connMetrics;

    protected HttpContext context;
    protected IOSession session;
    protected SocketAddress remote;
    protected volatile ContentDecoder contentDecoder;
    protected volatile boolean hasBufferedInput;
    protected volatile ContentEncoder contentEncoder;
    protected volatile boolean hasBufferedOutput;
    protected volatile HttpRequest request;
    protected volatile HttpResponse response;

    protected volatile int status;

    /**
     * Creates a new instance of this class given the underlying I/O session.
     *
     * @param session the underlying I/O session.
     * @param allocator byte buffer allocator.
     * @param params HTTP parameters.
     */
    public NHttpConnectionBase(
            final IOSession session,
            final ByteBufferAllocator allocator,
            final HttpParams params) {
        super();
        if (session == null) {
            throw new IllegalArgumentException("I/O session may not be null");
        }
        if (params == null) {
            throw new IllegalArgumentException("HTTP params may not be null");
        }

        int buffersize = HttpConnectionParams.getSocketBufferSize(params);
        int linebuffersize = buffersize;
        if (linebuffersize > 512) {
            linebuffersize = 512;
        }

        this.inbuf = new SessionInputBufferImpl(buffersize, linebuffersize, allocator, params);
        this.outbuf = new SessionOutputBufferImpl(buffersize, linebuffersize, allocator, params);

        this.incomingContentStrategy = new LaxContentLengthStrategy();
        this.outgoingContentStrategy = new StrictContentLengthStrategy();

        this.inTransportMetrics = createTransportMetrics();
        this.outTransportMetrics = createTransportMetrics();
        this.connMetrics = createConnectionMetrics(
                this.inTransportMetrics,
                this.outTransportMetrics);

        setSession(session);
        this.status = ACTIVE;
    }

    private void setSession(final IOSession session) {
        this.session = session;
        this.context = new SessionHttpContext(this.session);
        this.session.setBufferStatus(this);
        this.remote = this.session.getRemoteAddress();
    }

    /**
     * @since 4.1
     */
    protected HttpTransportMetricsImpl createTransportMetrics() {
        return new HttpTransportMetricsImpl();
    }

    /**
     * @since 4.1
     */
    protected HttpConnectionMetricsImpl createConnectionMetrics(
            final HttpTransportMetrics inTransportMetric,
            final HttpTransportMetrics outTransportMetric) {
        return new HttpConnectionMetricsImpl(inTransportMetric, outTransportMetric);
    }

    public int getStatus() {
        return this.status;
    }

    public HttpContext getContext() {
        return this.context;
    }

    public HttpRequest getHttpRequest() {
        return this.request;
    }

    public HttpResponse getHttpResponse() {
        return this.response;
    }

    public void requestInput() {
        this.session.setEvent(EventMask.READ);
    }

    public void requestOutput() {
        this.session.setEvent(EventMask.WRITE);
    }

    public void suspendInput() {
        this.session.clearEvent(EventMask.READ);
    }

    public void suspendOutput() {
        this.session.clearEvent(EventMask.WRITE);
    }

    /**
     * Initializes a specific {@link ContentDecoder} implementation based on the
     * properties of the given {@link HttpMessage} and generates an instance of
     * {@link HttpEntity} matching the properties of the content decoder.
     *
     * @param message the HTTP message.
     * @return HTTP entity.
     * @throws HttpException in case of an HTTP protocol violation.
     */
    protected HttpEntity prepareDecoder(final HttpMessage message) throws HttpException {
        BasicHttpEntity entity = new BasicHttpEntity();
        long len = this.incomingContentStrategy.determineLength(message);
        this.contentDecoder = createContentDecoder(
                len,
                this.session.channel(),
                this.inbuf,
                this.inTransportMetrics);
        if (len == ContentLengthStrategy.CHUNKED) {
            entity.setChunked(true);
            entity.setContentLength(-1);
        } else if (len == ContentLengthStrategy.IDENTITY) {
            entity.setChunked(false);
            entity.setContentLength(-1);
        } else {
            entity.setChunked(false);
            entity.setContentLength(len);
        }

        Header contentTypeHeader = message.getFirstHeader(HTTP.CONTENT_TYPE);
        if (contentTypeHeader != null) {
            entity.setContentType(contentTypeHeader);
        }
        Header contentEncodingHeader = message.getFirstHeader(HTTP.CONTENT_ENCODING);
        if (contentEncodingHeader != null) {
            entity.setContentEncoding(contentEncodingHeader);
        }
        return entity;
    }

    /**
     * Factory method for {@link ContentDecoder} instances.
     *
     * @param len content length, if known, {@link ContentLengthStrategy#CHUNKED} or
     *   {@link ContentLengthStrategy#IDENTITY}, if unknown.
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
        if (len == ContentLengthStrategy.CHUNKED) {
            return new ChunkDecoder(channel, buffer, metrics);
        } else if (len == ContentLengthStrategy.IDENTITY) {
            return new IdentityDecoder(channel, buffer, metrics);
        } else {
            return new LengthDelimitedDecoder(channel, buffer, metrics, len);
        }
    }

    /**
     * Initializes a specific {@link ContentEncoder} implementation based on the
     * properties of the given {@link HttpMessage}.
     *
     * @param message the HTTP message.
     * @throws HttpException in case of an HTTP protocol violation.
     */
    protected void prepareEncoder(final HttpMessage message) throws HttpException {
        long len = this.outgoingContentStrategy.determineLength(message);
        this.contentEncoder = createContentEncoder(
                len,
                this.session.channel(),
                this.outbuf,
                this.outTransportMetrics);
    }

    /**
     * Factory method for {@link ContentEncoder} instances.
     *
     * @param len content length, if known, {@link ContentLengthStrategy#CHUNKED} or
     *   {@link ContentLengthStrategy#IDENTITY}, if unknown.
     * @param channel the session channel.
     * @param buffer the session buffer.
     * @param metrics transport metrics.
     *
     * @return content encoder.
     *
     * @since 4.1
     */
    protected ContentEncoder createContentEncoder(
            final long len,
            final WritableByteChannel channel,
            final SessionOutputBuffer buffer,
            final HttpTransportMetricsImpl metrics) {
        if (len == ContentLengthStrategy.CHUNKED) {
            return new ChunkEncoder(channel, buffer, metrics);
        } else if (len == ContentLengthStrategy.IDENTITY) {
            return new IdentityEncoder(channel, buffer, metrics);
        } else {
            return new LengthDelimitedEncoder(channel, buffer, metrics, len);
        }
    }

    public boolean hasBufferedInput() {
        return this.hasBufferedInput;
    }

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

    public boolean isOpen() {
        return this.status == ACTIVE && !this.session.isClosed();
    }

    public boolean isStale() {
        return this.session.isClosed();
    }

    public InetAddress getLocalAddress() {
        SocketAddress address = this.session.getLocalAddress();
        if (address instanceof InetSocketAddress) {
            return ((InetSocketAddress) address).getAddress();
        } else {
            return null;
        }
    }

    public int getLocalPort() {
        SocketAddress address = this.session.getLocalAddress();
        if (address instanceof InetSocketAddress) {
            return ((InetSocketAddress) address).getPort();
        } else {
            return -1;
        }
    }

    public InetAddress getRemoteAddress() {
        SocketAddress address = this.session.getRemoteAddress();
        if (address instanceof InetSocketAddress) {
            return ((InetSocketAddress) address).getAddress();
        } else {
            return null;
        }
    }

    public int getRemotePort() {
        SocketAddress address = this.session.getRemoteAddress();
        if (address instanceof InetSocketAddress) {
            return ((InetSocketAddress) address).getPort();
        } else {
            return -1;
        }
    }

    public void setSocketTimeout(int timeout) {
        this.session.setSocketTimeout(timeout);
    }

    public int getSocketTimeout() {
        return this.session.getSocketTimeout();
    }

    public void shutdown() throws IOException {
        this.status = CLOSED;
        this.session.shutdown();
    }

    public HttpConnectionMetrics getMetrics() {
        return this.connMetrics;
    }

    @Override
    public String toString() {
        StringBuffer buffer = new StringBuffer();
        buffer.append("[");
        buffer.append(this.remote);
        if (this.status == CLOSED) {
            buffer.append("(closed)");
        }
        buffer.append("]");
        return buffer.toString();
    }

}
