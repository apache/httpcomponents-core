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
import org.apache.hc.core5.http.impl.nio.ClientHttp1IOEventHandler;
import org.apache.hc.core5.http.impl.nio.ClientHttp1StreamDuplexerFactory;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.ssl.ApplicationProtocol;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.hc.core5.util.Args;

/**
 * I/O event handler for events fired by {@link ProtocolIOSession} that implements
 * client side of the HTTP/2 protocol negotiation handshake
 * based on {@link HttpVersionPolicy} configuration.
 *
 * @since 5.0
 */
@Internal
public class ClientHttpProtocolNegotiator extends ProtocolNegotiatorBase {

    // PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n
    final static byte[] PREFACE = new byte[] {
            0x50, 0x52, 0x49, 0x20, 0x2a, 0x20, 0x48, 0x54, 0x54, 0x50,
            0x2f, 0x32, 0x2e, 0x30, 0x0d, 0x0a, 0x0d, 0x0a, 0x53, 0x4d,
            0x0d, 0x0a, 0x0d, 0x0a};

    private final ClientHttp1StreamDuplexerFactory http1StreamHandlerFactory;
    private final ClientH2StreamMultiplexerFactory http2StreamHandlerFactory;
    private final HttpVersionPolicy versionPolicy;
    private final AtomicBoolean initialized;

    private volatile ByteBuffer preface;
    private volatile BufferedData inBuf;

    public ClientHttpProtocolNegotiator(
            final ProtocolIOSession ioSession,
            final ClientHttp1StreamDuplexerFactory http1StreamHandlerFactory,
            final ClientH2StreamMultiplexerFactory http2StreamHandlerFactory,
            final HttpVersionPolicy versionPolicy) {
        this(ioSession, http1StreamHandlerFactory, http2StreamHandlerFactory, versionPolicy, null);
    }

    /**
     * @since 5.1
     */
    public ClientHttpProtocolNegotiator(
            final ProtocolIOSession ioSession,
            final ClientHttp1StreamDuplexerFactory http1StreamHandlerFactory,
            final ClientH2StreamMultiplexerFactory http2StreamHandlerFactory,
            final HttpVersionPolicy versionPolicy,
            final FutureCallback<ProtocolIOSession> resultCallback) {
        super(ioSession, resultCallback);
        this.http1StreamHandlerFactory = Args.notNull(http1StreamHandlerFactory, "HTTP/1.1 stream handler factory");
        this.http2StreamHandlerFactory = Args.notNull(http2StreamHandlerFactory, "HTTP/2 stream handler factory");
        this.versionPolicy = versionPolicy != null ? versionPolicy : HttpVersionPolicy.NEGOTIATE;
        this.initialized = new AtomicBoolean();
    }

    private void startHttp1() throws IOException {
        final ByteBuffer data = inBuf != null ? inBuf.data() : null;
        startProtocol(new ClientHttp1IOEventHandler(http1StreamHandlerFactory.create(ioSession)), data);
        ioSession.registerProtocol(ApplicationProtocol.HTTP_2.id, new ClientH2UpgradeHandler(http2StreamHandlerFactory));
        if (inBuf != null) {
            inBuf.clear();
        }
    }

    private void startHttp2() throws IOException {
        final ByteBuffer data = inBuf != null ? inBuf.data() : null;
        startProtocol(new ClientH2IOEventHandler(http2StreamHandlerFactory.create(ioSession)), data);
        if (inBuf != null) {
            inBuf.clear();
        }
    }

    private void initialize() throws IOException {
        switch (versionPolicy) {
            case NEGOTIATE:
                final TlsDetails tlsDetails = ioSession.getTlsDetails();
                if (tlsDetails != null) {
                    if (ApplicationProtocol.HTTP_2.id.equals(tlsDetails.getApplicationProtocol())) {
                        // Proceed with the H2 preface
                        preface = ByteBuffer.wrap(PREFACE);
                    }
                }
                break;
            case FORCE_HTTP_2:
                preface = ByteBuffer.wrap(PREFACE);
                break;
        }
        if (preface == null) {
            startHttp1();
        } else {
            ioSession.setEvent(SelectionKey.OP_WRITE);
        }
    }

    private void writeOutPreface(final IOSession session) throws IOException {
        if (preface.hasRemaining()) {
            session.write(preface);
        }
        if (!preface.hasRemaining()) {
            session.clearEvent(SelectionKey.OP_WRITE);
            startHttp2();
            preface = null;
        }
    }

    @Override
    public void connected(final IOSession session) throws IOException {
        if (initialized.compareAndSet(false, true)) {
            initialize();
        }
        if (preface != null) {
            writeOutPreface(session);
        }
    }

    @Override
    public void inputReady(final IOSession session, final ByteBuffer src) throws IOException  {
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
    public String toString() {
        return getClass().getName() + "/" + versionPolicy;
    }

}
