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
import java.io.InterruptedIOException;
import java.io.OutputStream;

import org.apache.http.ConnectionClosedException;
import org.apache.http.Consts;
import org.apache.http.Header;
import org.apache.http.MalformedChunkCodingException;
import org.apache.http.MessageConstraintException;
import org.apache.http.TruncatedChunkException;
import org.apache.http.config.MessageConstraints;
import org.apache.http.impl.SessionInputBufferMock;
import org.apache.http.impl.SessionOutputBufferMock;
import org.apache.http.io.SessionInputBuffer;
import org.junit.Assert;
import org.junit.Test;

public class TestChunkCoding {

    @Test
    public void testConstructors() throws Exception {
        try {
            new ChunkedInputStream((SessionInputBuffer)null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
        new MalformedChunkCodingException();
        new MalformedChunkCodingException("");
    }

    private final static String CHUNKED_INPUT
        = "10;key=\"value\"\r\n1234567890123456\r\n5\r\n12345\r\n0\r\nFooter1: abcde\r\nFooter2: fghij\r\n";

    private final static String CHUNKED_RESULT
        = "123456789012345612345";

    // Test for when buffer is larger than chunk size
    @Test
    public void testChunkedInputStreamLargeBuffer() throws IOException {
        final ChunkedInputStream in = new ChunkedInputStream(
                new SessionInputBufferMock(CHUNKED_INPUT, Consts.ISO_8859_1));
        final byte[] buffer = new byte[300];
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        int len;
        while ((len = in.read(buffer)) > 0) {
            out.write(buffer, 0, len);
        }
        Assert.assertEquals(-1, in.read(buffer));
        Assert.assertEquals(-1, in.read(buffer));

        in.close();

        final String result = new String(out.toByteArray(), Consts.ISO_8859_1);
        Assert.assertEquals(result, CHUNKED_RESULT);

        final Header[] footers = in.getFooters();
        Assert.assertNotNull(footers);
        Assert.assertEquals(2, footers.length);
        Assert.assertEquals("Footer1", footers[0].getName());
        Assert.assertEquals("abcde", footers[0].getValue());
        Assert.assertEquals("Footer2", footers[1].getName());
        Assert.assertEquals("fghij", footers[1].getValue());
    }

    //Test for when buffer is smaller than chunk size.
    @Test
    public void testChunkedInputStreamSmallBuffer() throws IOException {
        final ChunkedInputStream in = new ChunkedInputStream(
                new SessionInputBufferMock(CHUNKED_INPUT, Consts.ISO_8859_1));

        final byte[] buffer = new byte[7];
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        int len;
        while ((len = in.read(buffer)) > 0) {
            out.write(buffer, 0, len);
        }
        Assert.assertEquals(-1, in.read(buffer));
        Assert.assertEquals(-1, in.read(buffer));

        in.close();

        final Header[] footers = in.getFooters();
        Assert.assertNotNull(footers);
        Assert.assertEquals(2, footers.length);
        Assert.assertEquals("Footer1", footers[0].getName());
        Assert.assertEquals("abcde", footers[0].getValue());
        Assert.assertEquals("Footer2", footers[1].getName());
        Assert.assertEquals("fghij", footers[1].getValue());
    }

    // One byte read
    @Test
    public void testChunkedInputStreamOneByteRead() throws IOException {
        final String s = "5\r\n01234\r\n5\r\n56789\r\n0\r\n";
        final ChunkedInputStream in = new ChunkedInputStream(
                new SessionInputBufferMock(s, Consts.ISO_8859_1));
        int ch;
        int i = '0';
        while ((ch = in.read()) != -1) {
            Assert.assertEquals(i, ch);
            i++;
        }
        Assert.assertEquals(-1, in.read());
        Assert.assertEquals(-1, in.read());

        in.close();
    }

    @Test
    public void testAvailable() throws IOException {
        final String s = "5\r\n12345\r\n0\r\n";
        final ChunkedInputStream in = new ChunkedInputStream(
                new SessionInputBufferMock(s, Consts.ISO_8859_1));
        Assert.assertEquals(0, in.available());
        in.read();
        Assert.assertEquals(4, in.available());
        in.close();
    }

    @Test
    public void testChunkedInputStreamClose() throws IOException {
        final String s = "5\r\n01234\r\n5\r\n56789\r\n0\r\n";
        final ChunkedInputStream in = new ChunkedInputStream(
                new SessionInputBufferMock(s, Consts.ISO_8859_1));
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
    }

    @Test
    public void testChunkedOutputStreamClose() throws IOException {
        final ChunkedOutputStream out = new ChunkedOutputStream(
                2048, new SessionOutputBufferMock());
        out.close();
        out.close();
        try {
            out.write(new byte[] {1,2,3});
            Assert.fail("IOException should have been thrown");
        } catch (final IOException ex) {
            // expected
        }
        try {
            out.write(1);
            Assert.fail("IOException should have been thrown");
        } catch (final IOException ex) {
            // expected
        }
    }

    // Missing closing chunk
    @Test(expected=ConnectionClosedException.class)
    public void testChunkedInputStreamNoClosingChunk() throws IOException {
        final String s = "5\r\n01234\r\n";
        final ChunkedInputStream in = new ChunkedInputStream(
                new SessionInputBufferMock(s, Consts.ISO_8859_1));
        final byte[] tmp = new byte[5];
        Assert.assertEquals(5, in.read(tmp));
        in.read();
        in.close();
    }

    // Truncated stream (missing closing CRLF)
    @Test(expected=MalformedChunkCodingException.class)
    public void testCorruptChunkedInputStreamTruncatedCRLF() throws IOException {
        final String s = "5\r\n01234";
        final ChunkedInputStream in = new ChunkedInputStream(
                new SessionInputBufferMock(s, Consts.ISO_8859_1));
        final byte[] tmp = new byte[5];
        Assert.assertEquals(5, in.read(tmp));
        in.read();
        in.close();
    }

    // Missing \r\n at the end of the first chunk
    @Test(expected=MalformedChunkCodingException.class)
    public void testCorruptChunkedInputStreamMissingCRLF() throws IOException {
        final String s = "5\r\n012345\r\n56789\r\n0\r\n";
        final InputStream in = new ChunkedInputStream(
                new SessionInputBufferMock(s, Consts.ISO_8859_1));
        final byte[] buffer = new byte[300];
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        int len;
        while ((len = in.read(buffer)) > 0) {
            out.write(buffer, 0, len);
        }
        in.close();
    }

    // Missing LF
    @Test(expected=MalformedChunkCodingException.class)
    public void testCorruptChunkedInputStreamMissingLF() throws IOException {
        final String s = "5\r01234\r\n5\r\n56789\r\n0\r\n";
        final InputStream in = new ChunkedInputStream(
                new SessionInputBufferMock(s, Consts.ISO_8859_1));
        in.read();
        in.close();
    }

    // Invalid chunk size
    @Test(expected = MalformedChunkCodingException.class)
    public void testCorruptChunkedInputStreamInvalidSize() throws IOException {
        final String s = "whatever\r\n01234\r\n5\r\n56789\r\n0\r\n";
        final InputStream in = new ChunkedInputStream(
                new SessionInputBufferMock(s, Consts.ISO_8859_1));
        in.read();
        in.close();
    }

    // Negative chunk size
    @Test(expected = MalformedChunkCodingException.class)
    public void testCorruptChunkedInputStreamNegativeSize() throws IOException {
        final String s = "-5\r\n01234\r\n5\r\n56789\r\n0\r\n";
        final InputStream in = new ChunkedInputStream(
                new SessionInputBufferMock(s, Consts.ISO_8859_1));
        in.read();
        in.close();
    }

    // Truncated chunk
    @Test(expected = TruncatedChunkException.class)
    public void testCorruptChunkedInputStreamTruncatedChunk() throws IOException {
        final String s = "3\r\n12";
        final InputStream in = new ChunkedInputStream(
                new SessionInputBufferMock(s, Consts.ISO_8859_1));
        final byte[] buffer = new byte[300];
        Assert.assertEquals(2, in.read(buffer));
        in.read(buffer);
        in.close();
    }

    // Invalid footer
    @Test(expected = MalformedChunkCodingException.class)
    public void testCorruptChunkedInputStreamInvalidFooter() throws IOException {
        final String s = "1\r\n0\r\n0\r\nstuff\r\n";
        final InputStream in = new ChunkedInputStream(
                new SessionInputBufferMock(s, Consts.ISO_8859_1));
        in.read();
        in.read();
        in.close();
    }

    @Test
    public void testCorruptChunkedInputStreamClose() throws IOException {
        final String s = "whatever\r\n01234\r\n5\r\n56789\r\n0\r\n";
        final InputStream in = new ChunkedInputStream(
                new SessionInputBufferMock(s, Consts.ISO_8859_1));
        try {
            in.read();
            Assert.fail("MalformedChunkCodingException expected");
        } catch (final MalformedChunkCodingException ex) {
        }
        in.close();
    }

    @Test
    public void testEmptyChunkedInputStream() throws IOException {
        final String input = "0\r\n";
        final InputStream in = new ChunkedInputStream(
                new SessionInputBufferMock(input, Consts.ISO_8859_1));
        final byte[] buffer = new byte[300];
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        int len;
        while ((len = in.read(buffer)) > 0) {
            out.write(buffer, 0, len);
        }
        Assert.assertEquals(0, out.size());
        in.close();
    }

    @Test
    public void testTooLongChunkHeader() throws IOException {
        final String input = "5; and some very looooong commend\r\n12345\r\n0\r\n";
        final InputStream in1 = new ChunkedInputStream(
                new SessionInputBufferMock(input, MessageConstraints.DEFAULT, Consts.ISO_8859_1));
        final byte[] buffer = new byte[300];
        Assert.assertEquals(5, in1.read(buffer));
        in1.close();

        final InputStream in2 = new ChunkedInputStream(
                new SessionInputBufferMock(input, MessageConstraints.lineLen(10), Consts.ISO_8859_1));
        try {
            in2.read(buffer);
            Assert.fail("MessageConstraintException expected");
        } catch (final MessageConstraintException ex) {
        } finally {
            try {
                in2.close();
            } catch (final MessageConstraintException ex) {
            }
        }
    }

    @Test
    public void testChunkedConsistence() throws IOException {
        final String input = "76126;27823abcd;:q38a-\nkjc\rk%1ad\tkh/asdui\r\njkh+?\\suweb";
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        final OutputStream out = new ChunkedOutputStream(2048, new SessionOutputBufferMock(buffer));
        out.write(input.getBytes(Consts.ISO_8859_1));
        out.flush();
        out.close();
        out.close();
        buffer.close();
        final InputStream in = new ChunkedInputStream(new SessionInputBufferMock(buffer.toByteArray()));

        final byte[] d = new byte[10];
        final ByteArrayOutputStream result = new ByteArrayOutputStream();
        int len = 0;
        while ((len = in.read(d)) > 0) {
            result.write(d, 0, len);
        }

        final String output = new String(result.toByteArray(), Consts.ISO_8859_1);
        Assert.assertEquals(input, output);
        in.close();
}

    @Test
    public void testChunkedOutputStream() throws IOException {
        final SessionOutputBufferMock buffer = new SessionOutputBufferMock();
        final ChunkedOutputStream out = new ChunkedOutputStream(2, buffer);
        out.write('1');
        out.write('2');
        out.write('3');
        out.write('4');
        out.finish();
        out.close();

        final byte [] rawdata =  buffer.getData();

        Assert.assertEquals(19, rawdata.length);
        Assert.assertEquals('2', rawdata[0]);
        Assert.assertEquals('\r', rawdata[1]);
        Assert.assertEquals('\n', rawdata[2]);
        Assert.assertEquals('1', rawdata[3]);
        Assert.assertEquals('2', rawdata[4]);
        Assert.assertEquals('\r', rawdata[5]);
        Assert.assertEquals('\n', rawdata[6]);
        Assert.assertEquals('2', rawdata[7]);
        Assert.assertEquals('\r', rawdata[8]);
        Assert.assertEquals('\n', rawdata[9]);
        Assert.assertEquals('3', rawdata[10]);
        Assert.assertEquals('4', rawdata[11]);
        Assert.assertEquals('\r', rawdata[12]);
        Assert.assertEquals('\n', rawdata[13]);
        Assert.assertEquals('0', rawdata[14]);
        Assert.assertEquals('\r', rawdata[15]);
        Assert.assertEquals('\n', rawdata[16]);
        Assert.assertEquals('\r', rawdata[17]);
        Assert.assertEquals('\n', rawdata[18]);
    }

    @Test
    public void testChunkedOutputStreamLargeChunk() throws IOException {
        final SessionOutputBufferMock buffer = new SessionOutputBufferMock();
        final ChunkedOutputStream out = new ChunkedOutputStream(2, buffer);
        out.write(new byte[] {'1', '2', '3', '4'});
        out.finish();
        out.close();

        final byte [] rawdata =  buffer.getData();

        Assert.assertEquals(14, rawdata.length);
        Assert.assertEquals('4', rawdata[0]);
        Assert.assertEquals('\r', rawdata[1]);
        Assert.assertEquals('\n', rawdata[2]);
        Assert.assertEquals('1', rawdata[3]);
        Assert.assertEquals('2', rawdata[4]);
        Assert.assertEquals('3', rawdata[5]);
        Assert.assertEquals('4', rawdata[6]);
        Assert.assertEquals('\r', rawdata[7]);
        Assert.assertEquals('\n', rawdata[8]);
        Assert.assertEquals('0', rawdata[9]);
        Assert.assertEquals('\r', rawdata[10]);
        Assert.assertEquals('\n', rawdata[11]);
        Assert.assertEquals('\r', rawdata[12]);
        Assert.assertEquals('\n', rawdata[13]);
    }

    @Test
    public void testChunkedOutputStreamSmallChunk() throws IOException {
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        final ChunkedOutputStream out = new ChunkedOutputStream(2, new SessionOutputBufferMock(buffer));
        out.write('1');
        out.finish();
        out.close();

        final byte [] rawdata =  buffer.toByteArray();

        Assert.assertEquals(11, rawdata.length);
        Assert.assertEquals('1', rawdata[0]);
        Assert.assertEquals('\r', rawdata[1]);
        Assert.assertEquals('\n', rawdata[2]);
        Assert.assertEquals('1', rawdata[3]);
        Assert.assertEquals('\r', rawdata[4]);
        Assert.assertEquals('\n', rawdata[5]);
        Assert.assertEquals('0', rawdata[6]);
        Assert.assertEquals('\r', rawdata[7]);
        Assert.assertEquals('\n', rawdata[8]);
        Assert.assertEquals('\r', rawdata[9]);
        Assert.assertEquals('\n', rawdata[10]);
    }

    @Test
    public void testResumeOnSocketTimeoutInData() throws IOException {
        final String s = "5\r\n01234\r\n5\r\n5\0006789\r\na\r\n0123\000456789\r\n0\r\n";
        final SessionInputBuffer sessbuf = new SessionInputBufferMock(
                new TimeoutByteArrayInputStream(s.getBytes(Consts.ISO_8859_1)), 16);
        final InputStream in = new ChunkedInputStream(sessbuf);

        final byte[] tmp = new byte[3];

        int bytesRead = 0;
        int timeouts = 0;

        int i = 0;
        while (i != -1) {
            try {
                i = in.read(tmp);
                if (i > 0) {
                    bytesRead += i;
                }
            } catch (final InterruptedIOException ex) {
                timeouts++;
            }
        }
        Assert.assertEquals(20, bytesRead);
        Assert.assertEquals(2, timeouts);
        in.close();
}

    @Test
    public void testResumeOnSocketTimeoutInChunk() throws IOException {
        final String s = "5\000\r\000\n\00001234\r\n\0005\r\n56789\r\na\r\n0123456789\r\n\0000\r\n";
        final SessionInputBuffer sessbuf = new SessionInputBufferMock(
                new TimeoutByteArrayInputStream(s.getBytes(Consts.ISO_8859_1)), 16);
        final InputStream in = new ChunkedInputStream(sessbuf);

        final byte[] tmp = new byte[3];

        int bytesRead = 0;
        int timeouts = 0;

        int i = 0;
        while (i != -1) {
            try {
                i = in.read(tmp);
                if (i > 0) {
                    bytesRead += i;
                }
            } catch (final InterruptedIOException ex) {
                timeouts++;
            }
        }
        Assert.assertEquals(20, bytesRead);
        Assert.assertEquals(5, timeouts);
        in.close();
}

    // Test for when buffer is larger than chunk size
    @Test
    public void testHugeChunk() throws IOException {
        final ChunkedInputStream in = new ChunkedInputStream(
                new SessionInputBufferMock("1234567890abcdef\r\n01234567", Consts.ISO_8859_1));
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < 8; ++i) {
            out.write(in.read());
        }

        final String result = new String(out.toByteArray(), Consts.ISO_8859_1);
        Assert.assertEquals("01234567", result);
    }

}

