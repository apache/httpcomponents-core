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
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.nio.ClientHttp1StreamDuplexerFactory;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.reactor.EndpointParameters;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Timeout;

/**
 * {@link ClientHttpProtocolNegotiator} factory.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE_CONDITIONAL)
@Internal
public class ClientHttpProtocolNegotiatorFactory implements IOEventHandlerFactory {

    private final ClientHttp1StreamDuplexerFactory http1StreamHandlerFactory;
    private final ClientH2StreamMultiplexerFactory http2StreamHandlerFactory;
    private final HttpVersionPolicy versionPolicy;
    private final TlsStrategy tlsStrategy;
    private final Timeout handshakeTimeout;

    public ClientHttpProtocolNegotiatorFactory(
            final ClientHttp1StreamDuplexerFactory http1StreamHandlerFactory,
            final ClientH2StreamMultiplexerFactory http2StreamHandlerFactory,
            final HttpVersionPolicy versionPolicy,
            final TlsStrategy tlsStrategy,
            final Timeout handshakeTimeout) {
        this.http1StreamHandlerFactory = Args.notNull(http1StreamHandlerFactory, "HTTP/1.1 stream handler factory");
        this.http2StreamHandlerFactory = Args.notNull(http2StreamHandlerFactory, "HTTP/2 stream handler factory");
        this.versionPolicy = versionPolicy != null ? versionPolicy : HttpVersionPolicy.NEGOTIATE;
        this.tlsStrategy = tlsStrategy;
        this.handshakeTimeout = handshakeTimeout;
    }

    @Override
    public ClientHttpProtocolNegotiator createHandler(final ProtocolIOSession ioSession, final Object attachment) {
        HttpVersionPolicy endpointPolicy = versionPolicy;
        if (attachment instanceof EndpointParameters) {
            final EndpointParameters params = (EndpointParameters) attachment;
            if (tlsStrategy != null && URIScheme.HTTPS.same(params.getScheme())) {
                tlsStrategy.upgrade(ioSession, params, params.getAttachment(), handshakeTimeout, null);
            }
            if (params.getAttachment() instanceof HttpVersionPolicy) {
                endpointPolicy = (HttpVersionPolicy) params.getAttachment();
            }
        }
        return new ClientHttpProtocolNegotiator(
                ioSession,
                http1StreamHandlerFactory,
                http2StreamHandlerFactory,
                endpointPolicy);
    }

}
