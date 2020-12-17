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
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.nio.BufferedData;
import org.apache.hc.core5.http.impl.nio.ServerHttp1IOEventHandler;
import org.apache.hc.core5.http.impl.nio.ServerHttp1StreamDuplexer;
import org.apache.hc.core5.http.impl.nio.ServerHttp1StreamDuplexerFactory;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.ssl.ApplicationProtocol;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.hc.core5.util.Args;

/**
 * I/O event handler for events fired by {@link ProtocolIOSession} that implements
 * server side of the HTTP/2 protocol negotiation handshake
 * based on {@link HttpVersionPolicy} configuration.
 *
 * @since 5.0
 */
@Internal
public class ServerHttpProtocolNegotiator extends ProtocolNegotiatorBase {

    final static byte[] PREFACE = ClientHttpProtocolNegotiator.PREFACE;

    private final ServerHttp1StreamDuplexerFactory http1StreamHandlerFactory;
    private final ServerH2StreamMultiplexerFactory http2StreamHandlerFactory;
    private final HttpVersionPolicy versionPolicy;
    private final BufferedData inBuf;
    private final AtomicBoolean initialized;

    private volatile boolean expectValidH2Preface;

    public ServerHttpProtocolNegotiator(
            final ProtocolIOSession ioSession,
            final ServerHttp1StreamDuplexerFactory http1StreamHandlerFactory,
            final ServerH2StreamMultiplexerFactory http2StreamHandlerFactory,
            final HttpVersionPolicy versionPolicy) {
        this(ioSession, http1StreamHandlerFactory, http2StreamHandlerFactory, versionPolicy, null);
    }

    /**
     * @since 5.1
     */
    public ServerHttpProtocolNegotiator(
            final ProtocolIOSession ioSession,
            final ServerHttp1StreamDuplexerFactory http1StreamHandlerFactory,
            final ServerH2StreamMultiplexerFactory http2StreamHandlerFactory,
            final HttpVersionPolicy versionPolicy,
            final FutureCallback<ProtocolIOSession> resultCallback) {
        super(ioSession, resultCallback);
        this.http1StreamHandlerFactory = Args.notNull(http1StreamHandlerFactory, "HTTP/1.1 stream handler factory");
        this.http2StreamHandlerFactory = Args.notNull(http2StreamHandlerFactory, "HTTP/2 stream handler factory");
        this.versionPolicy = versionPolicy != null ? versionPolicy : HttpVersionPolicy.NEGOTIATE;
        this.inBuf = BufferedData.allocate(1024);
        this.initialized = new AtomicBoolean();
    }

    private void startHttp1(final TlsDetails tlsDetails, final ByteBuffer data) throws IOException {
        final ServerHttp1StreamDuplexer http1StreamHandler = http1StreamHandlerFactory.create(
                tlsDetails != null ? URIScheme.HTTPS.id : URIScheme.HTTP.id,
                ioSession);
        startProtocol(new ServerHttp1IOEventHandler(http1StreamHandler), data);
        ioSession.registerProtocol(ApplicationProtocol.HTTP_2.id, new ServerH2UpgradeHandler(http2StreamHandlerFactory));
    }

    private void startHttp2(final ByteBuffer data) throws IOException {
        startProtocol(new ServerH2IOEventHandler(http2StreamHandlerFactory.create(ioSession)), data);
    }

    private void initialize() throws IOException {
        final TlsDetails tlsDetails = ioSession.getTlsDetails();
        switch (versionPolicy) {
            case NEGOTIATE:
                if (tlsDetails != null &&
                        ApplicationProtocol.HTTP_2.id.equals(tlsDetails.getApplicationProtocol())) {
                    expectValidH2Preface = true;
                }
                break;
            case FORCE_HTTP_2:
                if (tlsDetails == null ||
                        !ApplicationProtocol.HTTP_1_1.id.equals(tlsDetails.getApplicationProtocol())) {
                    expectValidH2Preface = true;
                }
                break;
            case FORCE_HTTP_1:
                startHttp1(tlsDetails, null);
                break;
        }
    }

    @Override
    public void connected(final IOSession session) throws IOException {
        if (initialized.compareAndSet(false, true)) {
            initialize();
        }
    }

    @Override
    public void inputReady(final IOSession session, final ByteBuffer src) throws IOException {
        if (src != null) {
            inBuf.put(src);
        }
        boolean endOfStream = false;
        if (inBuf.length() < PREFACE.length) {
            final int bytesRead = inBuf.readFrom(session);
            if (bytesRead == -1) {
                endOfStream = true;
            }
        }
        final ByteBuffer data = inBuf.data();
        if (data.remaining() >= PREFACE.length) {
            boolean validH2Preface = true;
            for (int i = 0; i < PREFACE.length; i++) {
                if (data.get() != PREFACE[i]) {
                    if (expectValidH2Preface) {
                        throw new ProtocolNegotiationException("Unexpected HTTP/2 preface");
                    }
                    validH2Preface = false;
                }
            }
            if (validH2Preface) {
                startHttp2(data.hasRemaining() ? data : null);
            } else {
                data.rewind();
                startHttp1(ioSession.getTlsDetails(), data);
            }
        } else {
            if (endOfStream) {
                throw new ConnectionClosedException();
            }
        }
    }

    @Override
    public void outputReady(final IOSession session) throws IOException {
        if (initialized.compareAndSet(false, true)) {
            initialize();
        }
    }

    @Override
    public String toString() {
        return getClass().getName() + "/" + versionPolicy;
    }

}
