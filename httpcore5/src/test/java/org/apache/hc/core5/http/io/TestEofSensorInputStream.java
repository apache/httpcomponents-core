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
package org.apache.hc.core5.http.io;

import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@SuppressWarnings({"boxing","static-access"}) // test code
class TestEofSensorInputStream {

    private InputStream inStream;
    private EofSensorWatcher eofwatcher;
    private EofSensorInputStream eofstream;

    @BeforeEach
    void setup() {
        inStream = Mockito.mock(InputStream.class);
        eofwatcher = Mockito.mock(EofSensorWatcher.class);
        eofstream = new EofSensorInputStream(inStream, eofwatcher);
    }

    @Test
    void testClose() throws Exception {
        Mockito.when(eofwatcher.streamClosed(Mockito.any())).thenReturn(Boolean.TRUE);

        eofstream.close();

        Assertions.assertTrue(eofstream.isSelfClosed());
        Assertions.assertNull(eofstream.getWrappedStream());

        Mockito.verify(inStream, Mockito.times(1)).close();
        Mockito.verify(eofwatcher).streamClosed(inStream);

        eofstream.close();
    }

    @Test
    void testCloseIOError() throws Exception {
        Mockito.when(eofwatcher.streamClosed(Mockito.any())).thenThrow(new IOException());

        Assertions.assertThrows(IOException.class, () -> eofstream.close());
        Assertions.assertTrue(eofstream.isSelfClosed());
        Assertions.assertNull(eofstream.getWrappedStream());

        Mockito.verify(eofwatcher).streamClosed(inStream);
    }

    @Test
    void testAbortConnection() throws Exception {
        Mockito.when(eofwatcher.streamAbort(Mockito.any())).thenReturn(Boolean.TRUE);

        eofstream.abort();

        Assertions.assertTrue(eofstream.isSelfClosed());
        Assertions.assertNull(eofstream.getWrappedStream());

        Mockito.verify(inStream, Mockito.times(1)).close();
        Mockito.verify(eofwatcher).streamAbort(inStream);

        eofstream.abort();
    }

    @Test
    void testAbortConnectionIOError() throws Exception {
        Mockito.when(eofwatcher.streamAbort(Mockito.any())).thenThrow(new IOException());

        Assertions.assertThrows(IOException.class, () -> eofstream.abort());
        Assertions.assertTrue(eofstream.isSelfClosed());
        Assertions.assertNull(eofstream.getWrappedStream());

        Mockito.verify(eofwatcher).streamAbort(inStream);
    }

    @Test
    void testRead() throws Exception {
        Mockito.when(eofwatcher.eofDetected(Mockito.any())).thenReturn(Boolean.TRUE);
        Mockito.when(inStream.read()).thenReturn(0, -1);

        Assertions.assertEquals(0, eofstream.read());

        Assertions.assertFalse(eofstream.isSelfClosed());
        Assertions.assertNotNull(eofstream.getWrappedStream());

        Mockito.verify(eofwatcher, Mockito.never()).eofDetected(inStream);

        Assertions.assertEquals(-1, eofstream.read());

        Assertions.assertFalse(eofstream.isSelfClosed());
        Assertions.assertNull(eofstream.getWrappedStream());

        Mockito.verify(inStream, Mockito.times(1)).close();
        Mockito.verify(eofwatcher).eofDetected(inStream);

        Assertions.assertEquals(-1, eofstream.read());
    }

    @Test
    void testReadIOError() throws Exception {
        Mockito.when(eofwatcher.eofDetected(Mockito.any())).thenReturn(Boolean.TRUE);
        Mockito.when(inStream.read()).thenThrow(new IOException());

        Assertions.assertThrows(IOException.class, () -> eofstream.read());
        Assertions.assertFalse(eofstream.isSelfClosed());
        Assertions.assertNull(eofstream.getWrappedStream());

        Mockito.verify(eofwatcher).streamAbort(inStream);
    }

    @Test
    void testReadByteArray() throws Exception {
        Mockito.when(eofwatcher.eofDetected(Mockito.any())).thenReturn(Boolean.TRUE);
        Mockito.when(inStream.read(Mockito.any(), Mockito.anyInt(), Mockito.anyInt()))
            .thenReturn(1, -1);

        final byte[] tmp = new byte[1];

        Assertions.assertEquals(1, eofstream.read(tmp));

        Assertions.assertFalse(eofstream.isSelfClosed());
        Assertions.assertNotNull(eofstream.getWrappedStream());

        Mockito.verify(eofwatcher, Mockito.never()).eofDetected(inStream);

        Assertions.assertEquals(-1, eofstream.read(tmp));

        Assertions.assertFalse(eofstream.isSelfClosed());
        Assertions.assertNull(eofstream.getWrappedStream());

        Mockito.verify(inStream, Mockito.times(1)).close();
        Mockito.verify(eofwatcher).eofDetected(inStream);

        Assertions.assertEquals(-1, eofstream.read(tmp));
    }

    @Test
    void testReadByteArrayIOError() throws Exception {
        Mockito.when(eofwatcher.eofDetected(Mockito.any())).thenReturn(Boolean.TRUE);
        Mockito.when(inStream.read(Mockito.any(), Mockito.anyInt(), Mockito.anyInt()))
            .thenThrow(new IOException());

        final byte[] tmp = new byte[1];
        Assertions.assertThrows(IOException.class, () -> eofstream.read(tmp));
        Assertions.assertFalse(eofstream.isSelfClosed());
        Assertions.assertNull(eofstream.getWrappedStream());

        Mockito.verify(eofwatcher).streamAbort(inStream);
    }

    @Test
    void testReadAfterAbort() throws Exception {
        Mockito.when(eofwatcher.streamAbort(Mockito.any())).thenReturn(Boolean.TRUE);

        eofstream.abort();

        Assertions.assertThrows(IOException.class, () -> eofstream.read());
        final byte[] tmp = new byte[1];
        Assertions.assertThrows(IOException.class, () -> eofstream.read(tmp));
    }

}
