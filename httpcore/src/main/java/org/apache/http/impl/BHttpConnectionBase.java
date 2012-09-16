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

package org.apache.http.impl;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;

import org.apache.http.Consts;
import org.apache.http.HttpConnection;
import org.apache.http.HttpConnectionMetrics;
import org.apache.http.HttpInetConnection;
import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.impl.io.SessionInputBufferImpl;
import org.apache.http.impl.io.SessionOutputBufferImpl;
import org.apache.http.io.HttpTransportMetrics;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.util.Args;
import org.apache.http.util.Asserts;
import org.apache.http.util.CharsetUtils;

/**
 * Implementation of a client-side HTTP connection that can be bound to an
 * arbitrary {@link Socket} for receiving data from and transmitting data to
 * a remote server.
 * <p>
 * The following parameters can be used to customize the behavior of this
 * class:
 * <ul>
 *  <li>{@link org.apache.http.params.CoreProtocolPNames#HTTP_ELEMENT_CHARSET}</li>
 *  <li>{@link org.apache.http.params.CoreProtocolPNames#HTTP_MALFORMED_INPUT_ACTION}</li>
 *  <li>{@link org.apache.http.params.CoreProtocolPNames#HTTP_UNMAPPABLE_INPUT_ACTION}</li>
 *  <li>{@link org.apache.http.params.CoreConnectionPNames#SOCKET_BUFFER_SIZE}</li>
 *  <li>{@link org.apache.http.params.CoreConnectionPNames#MAX_LINE_LENGTH}</li>
 *  <li>{@link org.apache.http.params.CoreConnectionPNames#MAX_HEADER_COUNT}</li>
 *  <li>{@link org.apache.http.params.CoreConnectionPNames#MIN_CHUNK_LIMIT}</li>
 * </ul>
 *
 * @since 4.0
 */
@NotThreadSafe
public class BHttpConnectionBase implements HttpConnection, HttpInetConnection {

    private final SessionInputBufferImpl inbuffer;
    private final SessionOutputBufferImpl outbuffer;
    private final HttpTransportMetricsImpl inTransportMetrics;
    private final HttpTransportMetricsImpl outTransportMetrics;
    private final HttpConnectionMetricsImpl connMetrics;

    private volatile boolean open;
    private volatile Socket socket = null;

    public BHttpConnectionBase(final HttpParams params) {
        super();
        Args.notNull(params, "HTTP parameters");
        int buffersize = params.getIntParameter(CoreConnectionPNames.SOCKET_BUFFER_SIZE, -1);
        if (buffersize <= 0) {
            buffersize = 4096;
        }
        int maxLineLen = params.getIntParameter(CoreConnectionPNames.MAX_LINE_LENGTH, -1);
        int minChunkLimit = params.getIntParameter(CoreConnectionPNames.MIN_CHUNK_LIMIT, -1);
        CharsetDecoder decoder = null;
        CharsetEncoder encoder = null;
        Charset charset = CharsetUtils.lookup(
                (String) params.getParameter(CoreProtocolPNames.HTTP_ELEMENT_CHARSET));
        if (charset != null) {
            charset = Consts.ASCII;
            decoder = charset.newDecoder();
            encoder = charset.newEncoder();
            CodingErrorAction malformedCharAction = (CodingErrorAction) params.getParameter(
                    CoreProtocolPNames.HTTP_MALFORMED_INPUT_ACTION);
            CodingErrorAction unmappableCharAction = (CodingErrorAction) params.getParameter(
                    CoreProtocolPNames.HTTP_UNMAPPABLE_INPUT_ACTION);
            decoder.onMalformedInput(malformedCharAction);
            decoder.onUnmappableCharacter(unmappableCharAction);
            encoder.onMalformedInput(malformedCharAction);
            encoder.onUnmappableCharacter(unmappableCharAction);
        }
        this.inTransportMetrics = createTransportMetrics();
        this.outTransportMetrics = createTransportMetrics();
        this.inbuffer = new SessionInputBufferImpl(
                this.inTransportMetrics, buffersize, maxLineLen, minChunkLimit, decoder);
        this.outbuffer = new SessionOutputBufferImpl(
                this.outTransportMetrics, buffersize, minChunkLimit, encoder);
        this.connMetrics = createConnectionMetrics(
                this.inTransportMetrics,
                this.outTransportMetrics);
    }

    protected void assertNotOpen() {
        Asserts.check(!this.open, "Connection is already open");
    }

    protected void assertOpen() {
        Asserts.check(this.open, "Connection is not open");
    }

    /**
     * Binds this connection to the given {@link Socket}. This socket will be
     * used by the connection to send and receive data.
     * <p/>
     * After this method's execution the connection status will be reported
     * as open and the {@link #isOpen()} will return <code>true</code>.
     *
     * @param socket the socket.
     * @throws IOException in case of an I/O error.
     */
    protected void bind(final Socket socket) throws IOException {
        Args.notNull(socket, "Socket");
        this.socket = socket;
        this.open = true;
        this.inbuffer.bind(socket.getInputStream());
        this.outbuffer.bind(socket.getOutputStream());
    }

    public boolean isOpen() {
        return this.open;
    }

    protected Socket getSocket() {
        return this.socket;
    }

    protected HttpTransportMetricsImpl createTransportMetrics() {
        return new HttpTransportMetricsImpl();
    }

    protected HttpConnectionMetricsImpl createConnectionMetrics(
            final HttpTransportMetrics inTransportMetric,
            final HttpTransportMetrics outTransportMetric) {
        return new HttpConnectionMetricsImpl(inTransportMetric, outTransportMetric);
    }

    public InetAddress getLocalAddress() {
        if (this.socket != null) {
            return this.socket.getLocalAddress();
        } else {
            return null;
        }
    }

    public int getLocalPort() {
        if (this.socket != null) {
            return this.socket.getLocalPort();
        } else {
            return -1;
        }
    }

    public InetAddress getRemoteAddress() {
        if (this.socket != null) {
            return this.socket.getInetAddress();
        } else {
            return null;
        }
    }

    public int getRemotePort() {
        if (this.socket != null) {
            return this.socket.getPort();
        } else {
            return -1;
        }
    }

    public void setSocketTimeout(int timeout) {
        assertOpen();
        if (this.socket != null) {
            try {
                this.socket.setSoTimeout(timeout);
            } catch (SocketException ignore) {
                // It is not quite clear from the Sun's documentation if there are any
                // other legitimate cases for a socket exception to be thrown when setting
                // SO_TIMEOUT besides the socket being already closed
            }
        }
    }

    public int getSocketTimeout() {
        if (this.socket != null) {
            try {
                return this.socket.getSoTimeout();
            } catch (SocketException ignore) {
                return -1;
            }
        } else {
            return -1;
        }
    }

    public void shutdown() throws IOException {
        this.open = false;
        Socket tmpsocket = this.socket;
        if (tmpsocket != null) {
            tmpsocket.close();
        }
    }

    public void close() throws IOException {
        if (!this.open) {
            return;
        }
        this.open = false;
        Socket sock = this.socket;
        try {
            this.outbuffer.flush();
            try {
                try {
                    sock.shutdownOutput();
                } catch (IOException ignore) {
                }
                try {
                    sock.shutdownInput();
                } catch (IOException ignore) {
                }
            } catch (UnsupportedOperationException ignore) {
                // if one isn't supported, the other one isn't either
            }
        } finally {
            sock.close();
        }
    }

    public boolean isStale() {
        if (!isOpen()) {
            return true;
        }
        if (this.inbuffer.hasBufferedData()) {
            return false;
        }
        try {
            int oldtimeout = this.socket.getSoTimeout();
            try {
                this.socket.setSoTimeout(1);
                int i = this.inbuffer.fillBuffer();
                return i == -1;
            } catch (SocketTimeoutException ex) {
                throw ex;
            } finally {
                this.socket.setSoTimeout(oldtimeout);
            }
        } catch (SocketTimeoutException ex) {
            return false;
        } catch (IOException ex) {
            return true;
        }
    }

    public HttpConnectionMetrics getMetrics() {
        return this.connMetrics;
    }

    private static void formatAddress(final StringBuilder buffer, final SocketAddress socketAddress) {
        if (socketAddress instanceof InetSocketAddress) {
            InetSocketAddress addr = ((InetSocketAddress) socketAddress);
            buffer.append(addr.getAddress() != null ? addr.getAddress().getHostAddress() :
                addr.getAddress())
            .append(':')
            .append(addr.getPort());
        } else {
            buffer.append(socketAddress);
        }
    }

    @Override
    public String toString() {
        if (this.socket != null) {
            StringBuilder buffer = new StringBuilder();
            SocketAddress remoteAddress = this.socket.getRemoteSocketAddress();
            SocketAddress localAddress = this.socket.getLocalSocketAddress();
            if (remoteAddress != null && localAddress != null) {
                formatAddress(buffer, localAddress);
                buffer.append("<->");
                formatAddress(buffer, remoteAddress);
            }
            return buffer.toString();
        } else {
            return super.toString();
        }
    }

}
