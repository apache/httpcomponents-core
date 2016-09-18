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
import java.nio.charset.Charset;

import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.frame.DefaultFrameFactory;
import org.apache.hc.core5.http2.frame.FrameFactory;
import org.apache.hc.core5.http2.frame.StreamIdGenerator;
import org.apache.hc.core5.http2.nio.AsyncPushConsumer;
import org.apache.hc.core5.http2.nio.HandlerFactory;
import org.apache.hc.core5.reactor.IOSession;

/**
 * Client side HTTP/2 stream multiplexer.
 *
 * @since 5.0
 */
public class ClientHttp2StreamMultiplexer extends AbstractHttp2StreamMultiplexer {

    private final HandlerFactory<AsyncPushConsumer> pushHandlerFactory;

    public ClientHttp2StreamMultiplexer(
            final IOSession ioSession,
            final FrameFactory frameFactory,
            final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
            final Charset charset,
            final H2Config h2Config,
            final Http2StreamListener streamListener) {
        super(Mode.CLIENT, ioSession, frameFactory, StreamIdGenerator.ODD, charset, h2Config, streamListener);
        this.pushHandlerFactory = pushHandlerFactory;
    }

    public ClientHttp2StreamMultiplexer(
            final IOSession ioSession,
            final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
            final Charset charset,
            final H2Config h2Config) {
        this(ioSession, DefaultFrameFactory.INSTANCE, pushHandlerFactory, charset, h2Config, null);
    }

    public ClientHttp2StreamMultiplexer(
            final IOSession ioSession,
            final Charset charset,
            final H2Config h2Config) {
        this(ioSession, null, charset, h2Config);
    }

    @Override
    Http2StreamHandler createRemotelyInitiatedStream(final Http2StreamChannel channel) throws IOException {
        return new ClientPushHttp2StreamHandler(channel, pushHandlerFactory);
    }

}

