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

package org.apache.hc.core5.http2.impl.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.impl.nio.BufferedData;
import org.apache.hc.core5.http2.ssl.ApplicationProtocol;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TextUtils;

/**
 * I/O event handler for events fired by {@link ProtocolIOSession} that implements
 * client side of the HTTP/2 protocol negotiation handshake always forcing the choice
 * of HTTP/2.
 *
 * @since 5.2
 */
@Internal
public class ClientH2PrefaceHandler extends PrefaceHandlerBase {

    // PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n
    final static byte[] PREFACE = new byte[] {
            0x50, 0x52, 0x49, 0x20, 0x2a, 0x20, 0x48, 0x54, 0x54, 0x50,
            0x2f, 0x32, 0x2e, 0x30, 0x0d, 0x0a, 0x0d, 0x0a, 0x53, 0x4d,
            0x0d, 0x0a, 0x0d, 0x0a};

    private final ClientH2StreamMultiplexerFactory http2StreamHandlerFactory;
    private final boolean strictALPNHandshake;
    private final AtomicBoolean initialized;

    private volatile ByteBuffer preface;
    private volatile BufferedData inBuf;

    public ClientH2PrefaceHandler(
            final ProtocolIOSession ioSession,
            final ClientH2StreamMultiplexerFactory http2StreamHandlerFactory,
            final boolean strictALPNHandshake) {
        this(ioSession, http2StreamHandlerFactory, strictALPNHandshake, null);
    }

    /**
     * @since 5.1
     */
    public ClientH2PrefaceHandler(
            final ProtocolIOSession ioSession,
            final ClientH2StreamMultiplexerFactory http2StreamHandlerFactory,
            final boolean strictALPNHandshake,
            final FutureCallback<ProtocolIOSession> resultCallback) {
        super(ioSession, resultCallback);
        this.http2StreamHandlerFactory = Args.notNull(http2StreamHandlerFactory, "HTTP/2 stream handler factory");
        this.strictALPNHandshake = strictALPNHandshake;
        this.initialized = new AtomicBoolean();
    }

    private void initialize() throws IOException {
        final TlsDetails tlsDetails = ioSession.getTlsDetails();
        if (tlsDetails != null) {
            final String applicationProtocol = tlsDetails.getApplicationProtocol();
            if (TextUtils.isEmpty(applicationProtocol)) {
                if (strictALPNHandshake) {
                    throw new ProtocolNegotiationException("ALPN: missing application protocol");
                }
            } else {
                if (!ApplicationProtocol.HTTP_2.id.equals(applicationProtocol)) {
                    throw new ProtocolNegotiationException("ALPN: unexpected application protocol '" + applicationProtocol + "'");
                }
            }
        }
        this.preface = ByteBuffer.wrap(PREFACE);
        ioSession.setEvent(SelectionKey.OP_WRITE);
    }

    private void writeOutPreface(final IOSession session) throws IOException  {
        if (preface.hasRemaining()) {
            session.write(preface);
        }
        if (!preface.hasRemaining()) {
            session.clearEvent(SelectionKey.OP_WRITE);
            final ByteBuffer data = inBuf != null ? inBuf.data() : null;
            startProtocol(new ClientH2IOEventHandler(http2StreamHandlerFactory.create(ioSession)), data);
            if (inBuf != null) {
                inBuf.clear();
            }
            preface = null;
        }
    }

    @Override
    public void connected(final IOSession session) throws IOException {
        if (initialized.compareAndSet(false, true)) {
            initialize();
        }
    }

    @Override
    public void outputReady(final IOSession session) throws IOException {
        if (initialized.compareAndSet(false, true)) {
            initialize();
        }
        if (preface != null) {
            writeOutPreface(session);
        } else {
            throw new ProtocolNegotiationException("Unexpected output");
        }
    }

    @Override
    public void inputReady(final IOSession session, final ByteBuffer src) throws IOException {
        if (src != null) {
            if (inBuf == null) {
                inBuf = BufferedData.allocate(src.remaining());
            }
            inBuf.put(src);
        }
        if (preface != null) {
            writeOutPreface(session);
        } else {
            throw new ProtocolNegotiationException("Unexpected input");
        }
    }

    @Override
    public String toString() {
        return getClass().getName();
    }

}
