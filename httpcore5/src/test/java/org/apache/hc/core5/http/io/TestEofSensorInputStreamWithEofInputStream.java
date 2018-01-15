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

import java.io.InputStream;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestEofSensorInputStreamWithEofInputStream {

    private EofInputStream instream;
    private EofSensorWatcher eofwatcher;
    private EofSensorInputStream eofstream;

    @Before
    public void setup() throws Exception {
        instream = Mockito.mock(EofInputStream.class);
        eofwatcher = Mockito.mock(EofSensorWatcher.class);
        eofstream = new EofSensorInputStream(instream, eofwatcher);
    }

    @Test
    public void testReadAtEof() throws Exception {
        Mockito.when(instream.read()).thenReturn(0);
        Mockito.when(instream.atEof()).thenReturn(true);
        Mockito.when(eofwatcher.eofDetected(Mockito.<InputStream>any())).thenReturn(Boolean.TRUE);

        Assert.assertEquals(0, eofstream.read());
        Assert.assertNull(eofstream.getWrappedStream());
        Mockito.verify(instream, Mockito.times(1)).close();
        Mockito.verify(eofwatcher).eofDetected(instream);
    }

    @Test
    public void testReadNotAtEof() throws Exception {
        Mockito.when(instream.read()).thenReturn(0);
        Mockito.when(instream.atEof()).thenReturn(false);
        Mockito.when(eofwatcher.eofDetected(Mockito.<InputStream>any())).thenReturn(Boolean.TRUE);

        Assert.assertEquals(0, eofstream.read());
        Assert.assertNotNull(eofstream.getWrappedStream());
        Mockito.verify(instream, Mockito.never()).close();
        Mockito.verify(eofwatcher, Mockito.never()).eofDetected(instream);
    }

    @Test
    public void testReadByteArrayAtEof() throws Exception {
        Mockito.when(instream.read(Mockito.<byte []>any(), Mockito.anyInt(), Mockito.anyInt()))
            .thenReturn(1);
        Mockito.when(instream.atEof()).thenReturn(true);
        Mockito.when(eofwatcher.eofDetected(Mockito.<InputStream>any())).thenReturn(Boolean.TRUE);

        final byte[] tmp = new byte[1];

        Assert.assertEquals(1, eofstream.read(tmp));
        Assert.assertNull(eofstream.getWrappedStream());
        Mockito.verify(instream, Mockito.times(1)).close();
        Mockito.verify(eofwatcher).eofDetected(instream);
    }

    @Test
    public void testReadByteArrayNotAtEof() throws Exception {
        Mockito.when(instream.read(Mockito.<byte []>any(), Mockito.anyInt(), Mockito.anyInt()))
            .thenReturn(1);
        Mockito.when(instream.atEof()).thenReturn(false);

        final byte[] tmp = new byte[1];

        Assert.assertEquals(1, eofstream.read(tmp));
        Assert.assertNotNull(eofstream.getWrappedStream());
        Mockito.verify(instream, Mockito.never()).close();
        Mockito.verify(eofwatcher, Mockito.never()).eofDetected(instream);
    }
}
