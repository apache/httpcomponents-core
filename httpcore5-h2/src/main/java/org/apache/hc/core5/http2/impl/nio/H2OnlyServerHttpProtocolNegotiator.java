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

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.impl.nio.BufferedData;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.util.Args;

/**
 * I/O event handler for events fired by {@link ProtocolIOSession} that implements
 * server side of the HTTP/2 protocol negotiation handshake.
 *
 * @since 5.1
 */
@Internal
public class H2OnlyServerHttpProtocolNegotiator extends ProtocolNegotiatorBase {

    final static byte[] PREFACE = ClientHttpProtocolNegotiator.PREFACE;

    private final ServerH2StreamMultiplexerFactory http2StreamHandlerFactory;
    private final BufferedData inBuf;

    public H2OnlyServerHttpProtocolNegotiator(
            final ProtocolIOSession ioSession,
            final ServerH2StreamMultiplexerFactory http2StreamHandlerFactory) {
        this(ioSession, http2StreamHandlerFactory, null);
    }

    public H2OnlyServerHttpProtocolNegotiator(
            final ProtocolIOSession ioSession,
            final ServerH2StreamMultiplexerFactory http2StreamHandlerFactory,
            final FutureCallback<ProtocolIOSession> resultCallback) {
        super(ioSession, resultCallback);
        this.http2StreamHandlerFactory = Args.notNull(http2StreamHandlerFactory, "HTTP/2 stream handler factory");
        this.inBuf = BufferedData.allocate(1024);
    }

    @Override
    public void connected(final IOSession session) throws IOException {
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
            for (int i = 0; i < PREFACE.length; i++) {
                if (data.get() != PREFACE[i]) {
                    throw new ProtocolNegotiationException("Unexpected HTTP/2 preface");
                }
            }
            startProtocol(new ServerH2IOEventHandler(http2StreamHandlerFactory.create(ioSession)), data.hasRemaining() ? data : null);
        } else {
            if (endOfStream) {
                throw new ConnectionClosedException();
            }
        }
    }

    @Override
    public void outputReady(final IOSession session) throws IOException {
    }

    @Override
    public String toString() {
        return getClass().getName();
    }

}
