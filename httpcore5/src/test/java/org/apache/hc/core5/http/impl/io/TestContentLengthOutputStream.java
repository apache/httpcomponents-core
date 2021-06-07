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

package org.apache.hc.core5.http.impl.io;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

import org.apache.hc.core5.http.StreamClosedException;
import org.apache.hc.core5.http.io.SessionOutputBuffer;
import org.junit.Assert;
import org.junit.Test;

public class TestContentLengthOutputStream {

    @Test
    public void testBasics() throws Exception {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final SessionOutputBuffer outbuffer = new SessionOutputBufferImpl(16);
        final OutputStream out = new ContentLengthOutputStream(outbuffer, outputStream, 15L);

        final byte[] tmp = new byte[10];
        out.write(tmp, 0, 10);
        out.write(1);
        out.write(tmp, 0, 10);
        out.write(tmp, 0, 10);
        out.write(tmp);
        out.write(1);
        out.write(2);
        out.flush();
        out.close();
        final byte[] data = outputStream.toByteArray();
        Assert.assertEquals(15, data.length);
    }

    @Test
    public void testClose() throws Exception {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final SessionOutputBuffer outbuffer = new SessionOutputBufferImpl(16);
        final OutputStream out = new ContentLengthOutputStream(outbuffer, outputStream, 15L);
        out.close();
        out.close();
        final byte[] tmp = new byte[10];
        Assert.assertThrows(StreamClosedException.class, () -> out.write(tmp));
        Assert.assertThrows(StreamClosedException.class, () -> out.write(1));
    }

}

