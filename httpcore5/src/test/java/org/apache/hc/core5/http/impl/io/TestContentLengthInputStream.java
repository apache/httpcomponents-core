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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.StreamClosedException;
import org.apache.hc.core5.http.io.SessionInputBuffer;
import org.junit.Assert;
import org.junit.Test;

public class TestContentLengthInputStream {

    @Test
    public void testBasics() throws IOException {
        final String s = "1234567890123456";
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.ISO_8859_1));
        final SessionInputBuffer inbuffer = new SessionInputBufferImpl(16);
        final InputStream in = new ContentLengthInputStream(inbuffer, inputStream, 10L);
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        final byte[] buffer = new byte[50];
        int len = in.read(buffer, 0, 2);
        outputStream.write(buffer, 0, len);
        len = in.read(buffer);
        outputStream.write(buffer, 0, len);

        final String result = new String(outputStream.toByteArray(), StandardCharsets.ISO_8859_1);
        Assert.assertEquals(result, "1234567890");
        in.close();
    }

    @Test
    public void testSkip() throws IOException {
        final ByteArrayInputStream inputStream1 = new ByteArrayInputStream(new byte[20]);
        final SessionInputBuffer inbuffer1 = new SessionInputBufferImpl(16);
        final InputStream in1 = new ContentLengthInputStream(inbuffer1, inputStream1, 10L);
        Assert.assertEquals(10, in1.skip(10));
        Assert.assertTrue(in1.read() == -1);
        in1.close();

        final ByteArrayInputStream inputStream2 = new ByteArrayInputStream(new byte[20]);
        final SessionInputBuffer inbuffer2 = new SessionInputBufferImpl(16);
        final InputStream in2 = new ContentLengthInputStream(inbuffer2, inputStream2, 10L);
        in2.read();
        Assert.assertEquals(9, in2.skip(10));
        Assert.assertTrue(in2.read() == -1);
        in2.close();

        final ByteArrayInputStream inputStream3 = new ByteArrayInputStream(new byte[20]);
        final SessionInputBuffer inbuffer3 = new SessionInputBufferImpl(16);
        final InputStream in3 = new ContentLengthInputStream(inbuffer3, inputStream3, 2L);
        in3.read();
        in3.read();
        Assert.assertTrue(in3.skip(10) <= 0);
        Assert.assertTrue(in3.skip(-1) == 0);
        Assert.assertTrue(in3.read() == -1);
        in3.close();

        final ByteArrayInputStream inputStream4 = new ByteArrayInputStream(new byte[20]);
        final SessionInputBuffer inbuffer4 = new SessionInputBufferImpl(16);
        final InputStream in4 = new ContentLengthInputStream(inbuffer4, inputStream4, 10L);
        Assert.assertEquals(5,in4.skip(5));
        Assert.assertEquals(5, in4.read(new byte[20]));
        in4.close();
    }

    @Test
    public void testAvailable() throws IOException {
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(new byte[] {1, 2, 3});
        final SessionInputBuffer inbuffer = new SessionInputBufferImpl(16);
        final InputStream in = new ContentLengthInputStream(inbuffer, inputStream, 3L);
        Assert.assertEquals(0, in.available());
        in.read();
        Assert.assertEquals(2, in.available());
        in.close();
    }

    @Test
    public void testClose() throws IOException {
        final String s = "1234567890123456-";
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.ISO_8859_1));
        final SessionInputBuffer inbuffer = new SessionInputBufferImpl(16);
        final InputStream in = new ContentLengthInputStream(inbuffer, inputStream, 16L);

        in.close();
        in.close();
        try {
            in.read();
            Assert.fail("StreamClosedException expected");
        } catch (final StreamClosedException expected) {
        }
        final byte[] tmp = new byte[10];
        try {
            in.read(tmp);
            Assert.fail("StreamClosedException expected");
        } catch (final StreamClosedException expected) {
        }
        try {
            in.read(tmp, 0, tmp.length);
            Assert.fail("StreamClosedException expected");
        } catch (final StreamClosedException expected) {
        }
        Assert.assertEquals('-', inbuffer.read(inputStream));
    }

    @Test
    public void testTruncatedContent() throws IOException {
        final String s = "1234567890123456";
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.ISO_8859_1));
        final SessionInputBuffer inbuffer = new SessionInputBufferImpl(16);
        final InputStream in = new ContentLengthInputStream(inbuffer, inputStream, 32L);

        final byte[] tmp = new byte[32];
        final int byteRead = in.read(tmp);
        Assert.assertEquals(16, byteRead);
        try {
            in.read(tmp);
            Assert.fail("ConnectionClosedException should have been closed");
        } catch (final ConnectionClosedException ex) {
        }
        try {
            in.read();
            Assert.fail("ConnectionClosedException should have been closed");
        } catch (final ConnectionClosedException ex) {
        }
        try {
            in.close();
            Assert.fail("ConnectionClosedException should have been closed");
        } catch (final ConnectionClosedException ex) {
        }
    }

}

