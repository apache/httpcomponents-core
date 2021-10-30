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

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.frame.DefaultFrameFactory;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.util.Args;

/**
 * {@link ServerH2StreamMultiplexer} factory.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE_CONDITIONAL)
@Internal
public final class ServerH2StreamMultiplexerFactory {

    private final HttpProcessor httpProcessor;
    private final HandlerFactory<AsyncServerExchangeHandler> exchangeHandlerFactory;
    private final H2Config h2Config;
    private final CharCodingConfig charCodingConfig;
    private final H2StreamListener streamListener;

    public ServerH2StreamMultiplexerFactory(
            final HttpProcessor httpProcessor,
            final HandlerFactory<AsyncServerExchangeHandler> exchangeHandlerFactory,
            final H2Config h2Config,
            final CharCodingConfig charCodingConfig,
            final H2StreamListener streamListener) {
        this.httpProcessor = Args.notNull(httpProcessor, "HTTP processor");
        this.exchangeHandlerFactory = Args.notNull(exchangeHandlerFactory, "Exchange handler factory");
        this.h2Config = h2Config != null ? h2Config : H2Config.DEFAULT;
        this.charCodingConfig = charCodingConfig != null ? charCodingConfig : CharCodingConfig.DEFAULT;
        this.streamListener = streamListener;
    }

    public ServerH2StreamMultiplexer create(final ProtocolIOSession ioSession) {
        return new ServerH2StreamMultiplexer(
                ioSession,
                DefaultFrameFactory.INSTANCE,
                httpProcessor,
                exchangeHandlerFactory,
                charCodingConfig,
                h2Config,
                streamListener);
    }

    /**
     * Create a new {@link Builder}.
     *
     * @since 5.2
     */
    public static Builder builder() {
        return new ServerH2StreamMultiplexerFactory.Builder();
    }
    /**
     * Builder for {@link ServerH2StreamMultiplexerFactory}.
     *
     * @since 5.2
     */
    public static final class Builder {
        private HttpProcessor httpProcessor;
        private HandlerFactory<AsyncServerExchangeHandler> exchangeHandlerFactory;
        private H2Config h2Config;
        private CharCodingConfig charCodingConfig;
        private H2StreamListener streamListener;

        private Builder() {}

        public Builder withHttpProcessor(final HttpProcessor httpProcessor){
            this.httpProcessor = httpProcessor;
            return this;
        }

        public Builder withExchangeHandlerFactory(final HandlerFactory<AsyncServerExchangeHandler> exchangeHandlerFactory){
            this.exchangeHandlerFactory = exchangeHandlerFactory;
            return this;
        }

        public Builder withH2Config(final H2Config h2Config){
            this.h2Config = h2Config;
            return this;
        }

        public Builder withCharCodingConfig(final CharCodingConfig charCodingConfig){
            this.charCodingConfig = charCodingConfig;
            return this;
        }

        public Builder withH2StreamListener(final H2StreamListener streamListener){
            this.streamListener = streamListener;
            return this;
        }

        public ServerH2StreamMultiplexerFactory build(){
            return new ServerH2StreamMultiplexerFactory(
                    httpProcessor,
                    exchangeHandlerFactory,
                    h2Config,
                    charCodingConfig,
                    streamListener);
        }
    }

}
