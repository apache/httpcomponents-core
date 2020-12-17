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

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.impl.nio.HttpConnectionEventHandler;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.reactor.ProtocolUpgradeHandler;
import org.apache.hc.core5.util.Args;

/**
 * Protocol upgrade handler that upgrades the underlying {@link ProtocolIOSession}
 * to HTTP/2 in case of a successful protocol negotiation.
 *
 * @since 5.2
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE_CONDITIONAL)
@Internal
public class ClientH2UpgradeHandler implements ProtocolUpgradeHandler {

    private final ClientH2StreamMultiplexerFactory http2StreamHandlerFactory;

    public ClientH2UpgradeHandler(final ClientH2StreamMultiplexerFactory http2StreamHandlerFactory) {
        this.http2StreamHandlerFactory = Args.notNull(http2StreamHandlerFactory, "HTTP/2 stream handler factory");
    }

    @Override
    public void upgrade(final ProtocolIOSession ioSession, final FutureCallback<ProtocolIOSession> callback) {
        final HttpConnectionEventHandler protocolNegotiator = new H2OnlyClientProtocolNegotiator(
                ioSession, http2StreamHandlerFactory, true, callback);
        ioSession.upgrade(protocolNegotiator);
        try {
            protocolNegotiator.connected(ioSession);
        } catch (final IOException ex) {
            protocolNegotiator.exception(ioSession, ex);
        }
    }

}
