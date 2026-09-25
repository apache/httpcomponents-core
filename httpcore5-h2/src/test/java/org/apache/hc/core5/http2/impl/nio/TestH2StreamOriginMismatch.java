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

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

class TestH2StreamOriginMismatch {

    @Test
    void localOriginRejectionDoesNotResetIdleStream() throws Exception {
        final H2StreamChannel channel = Mockito.mock(H2StreamChannel.class);
        Mockito.when(channel.getInputWindow()).thenReturn(new AtomicInteger(65535));
        Mockito.when(channel.getOutputWindow()).thenReturn(new AtomicInteger(65535));
        final H2StreamHandler handler = Mockito.mock(H2StreamHandler.class);
        final H2OriginMismatchException mismatch = new H2OriginMismatchException("not advertised");
        Mockito.doThrow(mismatch).when(handler).produceOutput();
        final H2Stream stream = new H2Stream(channel, handler, null);
        stream.activate();

        stream.produceOutput();

        Mockito.verify(channel).markLocalClosed();
        Mockito.verify(channel, Mockito.never()).localReset(ArgumentMatchers.anyInt());
        Mockito.verify(handler).failed(mismatch);
        Mockito.verify(handler).releaseResources();
    }
}
