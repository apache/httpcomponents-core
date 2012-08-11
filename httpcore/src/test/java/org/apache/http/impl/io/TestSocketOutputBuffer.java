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

package org.apache.http.impl.io;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.Charset;

import org.apache.http.Consts;
import org.apache.http.util.CharArrayBuffer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TestSocketOutputBuffer {

    private SocketOutputBuffer sob;

    @Mock private Socket socket;
    @Mock private OutputStream os;
    private byte[] b;
    private CharArrayBuffer cb;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(socket.getOutputStream()).thenReturn(os);
    }

    private void create(int buffSize, int arraySize, Charset charset, int minChunkLimit) throws Exception {
        b = new byte[arraySize];
        cb = new CharArrayBuffer(arraySize);

        sob = SocketOutputBuffer.create(socket, buffSize, charset, minChunkLimit, null, null);
    }

    private void create(int buffSize, int arraySize, int minChunkLimit) throws Exception {
        create(buffSize, arraySize, null, minChunkLimit);
    }

    @Test
    public void testInvalidConstruction() throws Exception {
        try {
            SocketOutputBuffer.create(null, 0);
            Assert.fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException ex) {
        }
    }

    @Test
    public void testWriteByteArrayOffLenDirectToStream1() throws Exception {
        create(2048, 2048, 1024);

        sob.write(b, 0, b.length);
        verify(os, times(1)).write(any(byte[].class), eq(0), eq(b.length));
    }

    @Test
    public void testWriteByteArrayOffLenDirectToStream2() throws Exception {
        create(1024, 2048, 2048);

        sob.write(b, 0, b.length);
        verify(os, times(1)).write(any(byte[].class), eq(0), eq(b.length));
    }

    @Test
    public void testWriteByteArrayOffLenToBuffer() throws Exception {
        create(2048, 2048, 2048);

        sob.write(b, 0, b.length);
        verify(os, times(0)).write(any(byte[].class), eq(0), eq(b.length));
    }

    @Test
    public void testWriteByteArrayDirectToStream1() throws Exception {
        create(2048, 2048, 1024);

        sob.write(b);
        verify(os, times(1)).write(any(byte[].class), eq(0), eq(b.length));
    }

    @Test
    public void testWriteByteArrayDirectToStream2() throws Exception {
        create(1024, 2048, 2048);

        sob.write(b);
        verify(os, times(1)).write(any(byte[].class), eq(0), eq(b.length));
    }

    @Test
    public void testWriteByteArrayToBuffer() throws Exception {
        create(2048, 2048, 2048);

        sob.write(b);
        verify(os, times(0)).write(any(byte[].class), eq(0), eq(b.length));
    }

    @Test
    public void testWriteLineString() throws Exception {
        create(2048, 2048, 2048);

        sob.writeLine("test");
    }

    @Test
    public void testWriteLineStringEncode() throws Exception {
        create(2048, 2048, Consts.UTF_8, 2048);

        sob.writeLine("test");
    }

    @Test
    public void testWriteLineEmptyString() throws Exception {
        create(2048, 2048, 2048);

        sob.writeLine("");
    }

    @Test
    public void testWriteLineEmptyStringEncode() throws Exception {
        create(2048, 2048, Consts.UTF_8, 2048);

        sob.writeLine("");
    }

    @Test
    public void testWriteLineNullString() throws Exception {
        create(2048, 2048, 2048);

        sob.writeLine((String)null);
    }

    @Test
    public void testWriteLineNullStringEncode() throws Exception {
        create(2048, 2048, Consts.UTF_8, 2048);

        sob.writeLine((String)null);
    }

    @Test
    public void testWriteLineCharArrayBuffer() throws Exception {
        create(2048, 2048, 2048);

        sob.writeLine(cb);
    }

    @Test
    public void testWriteLineCharArrayBufferEncode() throws Exception {
        create(2048, 2048, Consts.UTF_8, 2048);

        sob.writeLine(cb);
    }

    @Test
    public void testWriteLineEmptyCharArrayBuffer() throws Exception {
        create(2048, 0, 2048);

        sob.writeLine(cb);
    }

    @Test
    public void testWriteLineEmptyCharArrayBufferEncode() throws Exception {
        create(2048, 0, Consts.UTF_8, 2048);

        sob.writeLine(cb);
    }

}
