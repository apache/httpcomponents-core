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
import org.apache.hc.core5.http.impl.nio.ClientHttp1IOEventHandler;
import org.apache.hc.core5.http.impl.nio.ClientHttp1StreamDuplexerFactory;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.reactor.ProtocolUpgradeHandler;
import org.apache.hc.core5.util.Args;

/**
 * Protocol upgrade handler that upgrades the underlying {@link ProtocolIOSession}
 * to HTTP/1.1 in case of a successful protocol negotiation or as a default fall-back.
 *
 * @since 5.2
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE_CONDITIONAL)
@Internal
public class ClientHttp1UpgradeHandler implements ProtocolUpgradeHandler {

    private final ClientHttp1StreamDuplexerFactory http1StreamHandlerFactory;

    public ClientHttp1UpgradeHandler(final ClientHttp1StreamDuplexerFactory http1StreamHandlerFactory) {
        this.http1StreamHandlerFactory = Args.notNull(http1StreamHandlerFactory, "HTTP/1.1 stream handler factory");
    }

    @Override
    public void upgrade(final ProtocolIOSession ioSession, final FutureCallback<ProtocolIOSession> callback) {
        final ClientHttp1IOEventHandler eventHandler = new ClientHttp1IOEventHandler(http1StreamHandlerFactory.create(ioSession));
        ioSession.upgrade(eventHandler);
        try {
            eventHandler.connected(ioSession);
            if (callback != null) {
                callback.completed(ioSession);
            }
        } catch (final IOException ex) {
            eventHandler.exception(ioSession, ex);
        }
    }

}
