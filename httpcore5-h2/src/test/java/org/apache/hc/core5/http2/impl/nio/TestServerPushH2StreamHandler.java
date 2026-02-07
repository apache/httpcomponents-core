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

import java.nio.ByteBuffer;

import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.impl.BasicHttpConnectionMetrics;
import org.apache.hc.core5.http.impl.BasicHttpTransportMetrics;
import org.apache.hc.core5.http.nio.AsyncPushProducer;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TestServerPushH2StreamHandler {

    private ServerPushH2StreamHandler newHandler() {
        final H2StreamChannel channel = Mockito.mock(H2StreamChannel.class);
        final HttpProcessor httpProcessor = Mockito.mock(HttpProcessor.class);
        final BasicHttpConnectionMetrics metrics = new BasicHttpConnectionMetrics(
                new BasicHttpTransportMetrics(), new BasicHttpTransportMetrics());
        final AsyncPushProducer pushProducer = Mockito.mock(AsyncPushProducer.class);
        Mockito.when(pushProducer.available()).thenReturn(0);
        return new ServerPushH2StreamHandler(channel, httpProcessor, metrics, pushProducer, HttpCoreContext.create());
    }

    @Test
    void defaults() {
        final ServerPushH2StreamHandler handler = newHandler();
        Assertions.assertNull(handler.getPushHandlerFactory());
        Assertions.assertTrue(handler.isOutputReady());
    }

    @Test
    void consumesAreRejected() {
        final ServerPushH2StreamHandler handler = newHandler();
        Assertions.assertThrows(ProtocolException.class, () -> handler.consumePromise(null));
        Assertions.assertThrows(ProtocolException.class, () -> handler.consumeHeader(null, true));
        Assertions.assertThrows(ProtocolException.class, () -> handler.consumeData(ByteBuffer.allocate(0), true));
    }

}
