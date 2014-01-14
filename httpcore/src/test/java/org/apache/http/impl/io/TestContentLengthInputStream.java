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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.http.ConnectionClosedException;
import org.apache.http.Consts;
import org.apache.http.impl.SessionInputBufferMock;
import org.apache.http.io.SessionInputBuffer;
import org.junit.Assert;
import org.junit.Test;

public class TestContentLengthInputStream {

    @Test
    public void testConstructors() throws Exception {
        final ContentLengthInputStream in = new ContentLengthInputStream(
                new SessionInputBufferMock(new byte[] {}), 0);
        in.close();
        try {
            new ContentLengthInputStream(null, 10);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
        try {
            new ContentLengthInputStream(new SessionInputBufferMock(new byte[] {}), -10);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testBasics() throws IOException {
        final String correct = "1234567890123456";
        final InputStream in = new ContentLengthInputStream(
                new SessionInputBufferMock(correct, Consts.ISO_8859_1), 10L);
        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        final byte[] buffer = new byte[50];
        int len = in.read(buffer, 0, 2);
        out.write(buffer, 0, len);
        len = in.read(buffer);
        out.write(buffer, 0, len);

        final String result = new String(out.toByteArray(), Consts.ISO_8859_1);
        Assert.assertEquals(result, "1234567890");
        in.close();
    }

    @Test
    public void testSkip() throws IOException {
        InputStream in = new ContentLengthInputStream(new SessionInputBufferMock(new byte[20]), 10L);
        Assert.assertEquals(10, in.skip(10));
        Assert.assertTrue(in.read() == -1);

        in = new ContentLengthInputStream(new SessionInputBufferMock(new byte[20]), 10L);
        in.read();
        Assert.assertEquals(9, in.skip(10));
        Assert.assertTrue(in.read() == -1);
        in.close();

        in = new ContentLengthInputStream(new SessionInputBufferMock(new byte[20]), 2L);
        in.read();
        in.read();
        Assert.assertTrue(in.skip(10) <= 0);
        Assert.assertTrue(in.skip(-1) == 0);
        Assert.assertTrue(in.read() == -1);
        in.close();

        in = new ContentLengthInputStream(new SessionInputBufferMock(new byte[20]), 10L);
        Assert.assertEquals(5,in.skip(5));
        Assert.assertEquals(5, in.read(new byte[20]));
        in.close();
    }

    @Test
    public void testAvailable() throws IOException {
        final InputStream in = new ContentLengthInputStream(
                new SessionInputBufferMock(new byte[] {1, 2, 3}), 3L);
        Assert.assertEquals(0, in.available());
        in.read();
        Assert.assertEquals(2, in.available());
        in.close();
    }

    @Test
    public void testClose() throws IOException {
        final String correct = "1234567890123456-";
        final SessionInputBuffer inbuffer = new SessionInputBufferMock(correct, Consts.ISO_8859_1);
        final InputStream in = new ContentLengthInputStream(inbuffer, 16L);
        in.close();
        in.close();
        try {
            in.read();
            Assert.fail("IOException should have been thrown");
        } catch (final IOException ex) {
            // expected
        }
        final byte[] tmp = new byte[10];
        try {
            in.read(tmp);
            Assert.fail("IOException should have been thrown");
        } catch (final IOException ex) {
            // expected
        }
        try {
            in.read(tmp, 0, tmp.length);
            Assert.fail("IOException should have been thrown");
        } catch (final IOException ex) {
            // expected
        }
        Assert.assertEquals('-', inbuffer.read());
    }

    @Test
    public void testTruncatedContent() throws IOException {
        final String correct = "1234567890123456";
        final SessionInputBuffer inbuffer = new SessionInputBufferMock(correct, Consts.ISO_8859_1);
        final InputStream in = new ContentLengthInputStream(inbuffer, 32L);
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

