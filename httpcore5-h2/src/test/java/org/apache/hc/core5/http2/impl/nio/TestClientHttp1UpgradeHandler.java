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

import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.nio.ClientHttp1StreamDuplexerFactory;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TestClientHttp1UpgradeHandler {

    @Test
    void upgradeCreatesHandlerAndCompletesCallback() {
        final HttpProcessor httpProcessor = Mockito.mock(HttpProcessor.class);
        final ClientHttp1StreamDuplexerFactory factory = new ClientHttp1StreamDuplexerFactory(
                httpProcessor, Http1Config.DEFAULT, CharCodingConfig.DEFAULT);

        final ProtocolIOSession ioSession = Mockito.mock(ProtocolIOSession.class);
        Mockito.when(ioSession.poll()).thenReturn(null);

        final ClientHttp1UpgradeHandler handler = new ClientHttp1UpgradeHandler(factory);
        final AtomicReference<ProtocolIOSession> completed = new AtomicReference<>();

        handler.upgrade(ioSession, new FutureCallback<ProtocolIOSession>() {
            @Override
            public void completed(final ProtocolIOSession result) {
                completed.set(result);
            }

            @Override
            public void failed(final Exception ex) {
            }

            @Override
            public void cancelled() {
            }
        });

        Assertions.assertSame(ioSession, completed.get());
        Mockito.verify(ioSession).upgrade(Mockito.any());
    }

}
