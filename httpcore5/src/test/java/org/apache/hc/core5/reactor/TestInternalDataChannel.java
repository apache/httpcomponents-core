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
package org.apache.hc.core5.reactor;

import java.nio.channels.SelectionKey;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@SuppressWarnings("resource")
class TestInternalDataChannel {

    @Test
    void ioEventsNotifyHandlerAndListener() throws Exception {
        final IOSession session = Mockito.mock(IOSession.class);
        final IOEventHandler handler = Mockito.mock(IOEventHandler.class);
        final IOSessionListener listener = Mockito.mock(IOSessionListener.class);
        Mockito.when(session.getHandler()).thenReturn(handler);
        Mockito.when(session.getEventMask()).thenReturn(0);

        final InternalDataChannel channel = new InternalDataChannel(session, null, null, listener);
        channel.upgrade(handler);
        try {
            channel.onIOEvent(SelectionKey.OP_CONNECT | SelectionKey.OP_READ | SelectionKey.OP_WRITE);

            Mockito.verify(session).clearEvent(SelectionKey.OP_CONNECT);
            Mockito.verify(session).updateReadTime();
            Mockito.verify(session).updateWriteTime();
            Mockito.verify(listener).connected(session);
            Mockito.verify(listener).inputReady(session);
            Mockito.verify(listener).outputReady(session);
            Mockito.verify(handler).connected(session);
            Mockito.verify(handler).inputReady(Mockito.eq(session), Mockito.isNull());
            Mockito.verify(handler).outputReady(session);
        } finally {
            channel.close(org.apache.hc.core5.io.CloseMode.IMMEDIATE);
        }
    }

    @Test
    void timeoutNotifiesHandlerAndListener() throws Exception {
        final IOSession session = Mockito.mock(IOSession.class);
        final IOEventHandler handler = Mockito.mock(IOEventHandler.class);
        final IOSessionListener listener = Mockito.mock(IOSessionListener.class);
        Mockito.when(session.getHandler()).thenReturn(handler);

        final InternalDataChannel channel = new InternalDataChannel(session, null, null, listener);
        channel.upgrade(handler);
        try {
            channel.onTimeout(Timeout.ofSeconds(1));

            Mockito.verify(listener).timeout(session);
            Mockito.verify(handler).timeout(session, Timeout.ofSeconds(1));
        } finally {
            channel.close(org.apache.hc.core5.io.CloseMode.IMMEDIATE);
        }
    }

    @Test
    void registerAndSwitchProtocol() {
        final IOSession session = Mockito.mock(IOSession.class);
        final InternalDataChannel channel = new InternalDataChannel(session, null, null, null);
        final ProtocolUpgradeHandler upgradeHandler = Mockito.mock(ProtocolUpgradeHandler.class);
        try {
            channel.registerProtocol("h2", upgradeHandler);
            channel.switchProtocol("H2", null);

            Mockito.verify(upgradeHandler).upgrade(Mockito.eq(channel), Mockito.isNull());
            Assertions.assertThrows(IllegalStateException.class, () -> channel.switchProtocol("unknown", null));
        } finally {
            channel.close(org.apache.hc.core5.io.CloseMode.IMMEDIATE);
        }
    }

    @Test
    void closeImmediateDelegatesToSession() {
        final IOSession session = Mockito.mock(IOSession.class);
        final InternalDataChannel channel = new InternalDataChannel(session, null, null, null);
        try {
            channel.close(org.apache.hc.core5.io.CloseMode.IMMEDIATE);

            Mockito.verify(session).close(org.apache.hc.core5.io.CloseMode.IMMEDIATE);
        } finally {
            channel.close(org.apache.hc.core5.io.CloseMode.IMMEDIATE);
        }
    }

    @Test
    void exceptionNotifiesListenerAndHandler() {
        final IOSession session = Mockito.mock(IOSession.class);
        final IOEventHandler handler = Mockito.mock(IOEventHandler.class);
        final IOSessionListener listener = Mockito.mock(IOSessionListener.class);
        Mockito.when(session.getHandler()).thenReturn(handler);

        final InternalDataChannel channel = new InternalDataChannel(session, null, null, listener);
        final RuntimeException ex = new RuntimeException("boom");
        try {
            channel.onException(ex);

            Mockito.verify(listener).exception(session, ex);
            Mockito.verify(handler).exception(session, ex);
        } finally {
            channel.close(org.apache.hc.core5.io.CloseMode.IMMEDIATE);
        }
    }

    @Test
    void upgradeHandlerMissingThrows() {
        final IOSession session = Mockito.mock(IOSession.class);
        final InternalDataChannel channel = new InternalDataChannel(session, null, null, null);
        try {
            @SuppressWarnings("unchecked")
            final FutureCallback<ProtocolIOSession> callback =
                    (FutureCallback<ProtocolIOSession>) Mockito.mock(FutureCallback.class);
            Assertions.assertThrows(IllegalStateException.class, () -> channel.switchProtocol("h2", callback));
        } finally {
            channel.close(org.apache.hc.core5.io.CloseMode.IMMEDIATE);
        }
    }

}
