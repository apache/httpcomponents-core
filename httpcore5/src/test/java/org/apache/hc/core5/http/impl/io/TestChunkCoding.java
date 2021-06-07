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
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.MalformedChunkCodingException;
import org.apache.hc.core5.http.MessageConstraintException;
import org.apache.hc.core5.http.StreamClosedException;
import org.apache.hc.core5.http.io.SessionInputBuffer;
import org.apache.hc.core5.http.io.SessionOutputBuffer;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.Assert;
import org.junit.Test;

public class TestChunkCoding {

    private final static String CHUNKED_INPUT
        = "10;key=\"value\"\r\n1234567890123456\r\n5\r\n12345\r\n0\r\nFooter1: abcde\r\nFooter2: fghij\r\n";

    private final static String CHUNKED_RESULT
        = "123456789012345612345";

    @Test
    public void testChunkedInputStreamLargeBuffer() throws IOException {
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(CHUNKED_INPUT.getBytes(StandardCharsets.ISO_8859_1));
        final ChunkedInputStream in = new ChunkedInputStream(inBuffer, inputStream);
        final byte[] buffer = new byte[300];
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        int len;
        while ((len = in.read(buffer)) > 0) {
            out.write(buffer, 0, len);
        }
        Assert.assertEquals(-1, in.read(buffer));
        Assert.assertEquals(-1, in.read(buffer));

        in.close();

        final String result = new String(out.toByteArray(), StandardCharsets.ISO_8859_1);
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
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(CHUNKED_INPUT.getBytes(StandardCharsets.ISO_8859_1));
        final ChunkedInputStream in = new ChunkedInputStream(inBuffer, inputStream);

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
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.ISO_8859_1));
        final ChunkedInputStream in = new ChunkedInputStream(inBuffer, inputStream);
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
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.ISO_8859_1));
        final ChunkedInputStream in = new ChunkedInputStream(inBuffer, inputStream);
        Assert.assertEquals(0, in.available());
        in.read();
        Assert.assertEquals(4, in.available());
        in.close();
    }

    @Test
    public void testChunkedInputStreamClose() throws IOException {
        final String s = "5\r\n01234\r\n5\r\n56789\r\n0\r\n";
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.ISO_8859_1));
        final ChunkedInputStream in = new ChunkedInputStream(inBuffer, inputStream);
        in.close();
        in.close();
        Assert.assertThrows(StreamClosedException.class, () -> in.read());
        final byte[] tmp = new byte[10];
        Assert.assertThrows(StreamClosedException.class, () -> in.read(tmp));
        Assert.assertThrows(StreamClosedException.class, () -> in.read(tmp, 0, tmp.length));
    }

    // Missing closing chunk
    @Test
    public void testChunkedInputStreamNoClosingChunk() throws IOException {
        final String s = "5\r\n01234\r\n";
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.ISO_8859_1));
        final ChunkedInputStream in = new ChunkedInputStream(inBuffer, inputStream);
        final byte[] tmp = new byte[5];
        Assert.assertEquals(5, in.read(tmp));
        Assert.assertThrows(ConnectionClosedException.class, () -> in.read());
        Assert.assertThrows(ConnectionClosedException.class, () -> in.close());
    }

    // Truncated stream (missing closing CRLF)
    @Test
    public void testCorruptChunkedInputStreamTruncatedCRLF() throws IOException {
        final String s = "5\r\n01234";
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.ISO_8859_1));
        final ChunkedInputStream in = new ChunkedInputStream(inBuffer, inputStream);
        final byte[] tmp = new byte[5];
        Assert.assertEquals(5, in.read(tmp));
        Assert.assertThrows(MalformedChunkCodingException.class, () -> in.read());
        in.close();
    }

    // Missing \r\n at the end of the first chunk
    @Test
    public void testCorruptChunkedInputStreamMissingCRLF() throws IOException {
        final String s = "5\r\n012345\r\n56789\r\n0\r\n";
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.ISO_8859_1));
        final ChunkedInputStream in = new ChunkedInputStream(inBuffer, inputStream);
        final byte[] buffer = new byte[300];
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        Assert.assertThrows(MalformedChunkCodingException.class, () -> {
            int len;
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
        });
        in.close();
    }

    // Missing LF
    @Test
    public void testCorruptChunkedInputStreamMissingLF() throws IOException {
        final String s = "5\r01234\r\n5\r\n56789\r\n0\r\n";
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.ISO_8859_1));
        final ChunkedInputStream in = new ChunkedInputStream(inBuffer, inputStream);
        Assert.assertThrows(MalformedChunkCodingException.class, in::read);
        in.close();
    }

    // Invalid chunk size
    @Test
    public void testCorruptChunkedInputStreamInvalidSize() throws IOException {
        final String s = "whatever\r\n01234\r\n5\r\n56789\r\n0\r\n";
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.ISO_8859_1));
        final ChunkedInputStream in = new ChunkedInputStream(inBuffer, inputStream);
        Assert.assertThrows(MalformedChunkCodingException.class, in::read);
        in.close();
    }

    // Negative chunk size
    @Test
    public void testCorruptChunkedInputStreamNegativeSize() throws IOException {
        final String s = "-5\r\n01234\r\n5\r\n56789\r\n0\r\n";
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.ISO_8859_1));
        final ChunkedInputStream in = new ChunkedInputStream(inBuffer, inputStream);
        Assert.assertThrows(MalformedChunkCodingException.class, in::read);
        in.close();
    }

    // Truncated chunk
    @Test
    public void testCorruptChunkedInputStreamTruncatedChunk() throws IOException {
        final String s = "3\r\n12";
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.ISO_8859_1));
        final ChunkedInputStream in = new ChunkedInputStream(inBuffer, inputStream);
        final byte[] buffer = new byte[300];
        Assert.assertEquals(2, in.read(buffer));
        Assert.assertThrows(MalformedChunkCodingException.class, () -> in.read(buffer));
        in.close();
    }

    // Invalid footer
    @Test
    public void testCorruptChunkedInputStreamInvalidFooter() throws IOException {
        final String s = "1\r\n0\r\n0\r\nstuff\r\n";
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.ISO_8859_1));
        final ChunkedInputStream in = new ChunkedInputStream(inBuffer, inputStream);
        in.read();
        Assert.assertThrows(MalformedChunkCodingException.class, in::read);
        in.close();
    }

    @Test
    public void testCorruptChunkedInputStreamClose() throws IOException {
        final String s = "whatever\r\n01234\r\n5\r\n56789\r\n0\r\n";
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.ISO_8859_1));
        final ChunkedInputStream in = new ChunkedInputStream(inBuffer, inputStream);
        Assert.assertThrows(MalformedChunkCodingException.class, in::read);
    }

    @Test
    public void testEmptyChunkedInputStream() throws IOException {
        final String s = "0\r\n";
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.ISO_8859_1));
        final ChunkedInputStream in = new ChunkedInputStream(inBuffer, inputStream);
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
        final String s = "5; and some very looooong commend\r\n12345\r\n0\r\n";
        final SessionInputBuffer inBuffer1 = new SessionInputBufferImpl(16);
        final ByteArrayInputStream inputStream1 = new ByteArrayInputStream(s.getBytes(StandardCharsets.ISO_8859_1));
        final ChunkedInputStream in1 = new ChunkedInputStream(inBuffer1, inputStream1);
        final byte[] buffer = new byte[300];
        Assert.assertEquals(5, in1.read(buffer));
        in1.close();

        final SessionInputBuffer inBuffer2 = new SessionInputBufferImpl(16, 10);
        final ByteArrayInputStream inputStream2 = new ByteArrayInputStream(s.getBytes(StandardCharsets.ISO_8859_1));
        final ChunkedInputStream in2 = new ChunkedInputStream(inBuffer2, inputStream2);
        Assert.assertThrows(MessageConstraintException.class, () -> in2.read(buffer));
    }

    @Test
    public void testChunkedOutputStreamClose() throws IOException {
        final SessionOutputBuffer outbuffer = new SessionOutputBufferImpl(16);
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final ChunkedOutputStream out = new ChunkedOutputStream(outbuffer, outputStream, 2048);
        out.close();
        out.close();
        Assert.assertThrows(IOException.class, () -> out.write(new byte[] {1,2,3}));
        Assert.assertThrows(IOException.class, () -> out.write(1));
    }

    @Test
    public void testChunkedConsistence() throws IOException {
        final String input = "76126;27823abcd;:q38a-\nkjc\rk%1ad\tkh/asdui\r\njkh+?\\suweb";
        final SessionOutputBuffer outbuffer = new SessionOutputBufferImpl(16);
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final ChunkedOutputStream out = new ChunkedOutputStream(outbuffer, outputStream, 2048);
        out.write(input.getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
        out.close();
        out.close();
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        final ChunkedInputStream in = new ChunkedInputStream(inBuffer, inputStream);

        final byte[] d = new byte[10];
        final ByteArrayOutputStream result = new ByteArrayOutputStream();
        int len = 0;
        while ((len = in.read(d)) > 0) {
            result.write(d, 0, len);
        }

        final String output = new String(result.toByteArray(), StandardCharsets.ISO_8859_1);
        Assert.assertEquals(input, output);
        in.close();
    }

    @Test
    public void testChunkedOutputStreamWithTrailers() throws IOException {
        final SessionOutputBuffer outbuffer = new SessionOutputBufferImpl(16);
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final ChunkedOutputStream out = new ChunkedOutputStream(outbuffer, outputStream, 2, () -> Arrays.asList(
                new BasicHeader("E", ""),
                new BasicHeader("Y", "Z"))
        );
        out.write('x');
        out.finish();
        out.close();

        final String content = new String(outputStream.toByteArray(), StandardCharsets.US_ASCII);
        Assert.assertEquals("1\r\nx\r\n0\r\nE: \r\nY: Z\r\n\r\n", content);
    }

    @Test
    public void testChunkedOutputStream() throws IOException {
        final SessionOutputBuffer outbuffer = new SessionOutputBufferImpl(16);
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final ChunkedOutputStream out = new ChunkedOutputStream(outbuffer, outputStream, 2);
        out.write('1');
        out.write('2');
        out.write('3');
        out.write('4');
        out.finish();
        out.close();

        final String content = new String(outputStream.toByteArray(), StandardCharsets.US_ASCII);
        Assert.assertEquals("2\r\n12\r\n2\r\n34\r\n0\r\n\r\n", content);
    }

    @Test
    public void testChunkedOutputStreamLargeChunk() throws IOException {
        final SessionOutputBuffer outbuffer = new SessionOutputBufferImpl(16);
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final ChunkedOutputStream out = new ChunkedOutputStream(outbuffer, outputStream, 2);
        out.write(new byte[] {'1', '2', '3', '4'});
        out.finish();
        out.close();

        final String content = new String(outputStream.toByteArray(), StandardCharsets.US_ASCII);
        Assert.assertEquals("4\r\n1234\r\n0\r\n\r\n", content);
    }

    @Test
    public void testChunkedOutputStreamSmallChunk() throws IOException {
        final SessionOutputBuffer outbuffer = new SessionOutputBufferImpl(16);
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final ChunkedOutputStream out = new ChunkedOutputStream(outbuffer, outputStream, 2);
        out.write('1');
        out.finish();
        out.close();

        final String content = new String(outputStream.toByteArray(), StandardCharsets.US_ASCII);
        Assert.assertEquals("1\r\n1\r\n0\r\n\r\n", content);
    }

    @Test
    public void testResumeOnSocketTimeoutInData() throws IOException {
        final String s = "5\r\n01234\r\n5\r\n5\0006789\r\na\r\n0123\000456789\r\n0\r\n";
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16);
        final TimeoutByteArrayInputStream inputStream = new TimeoutByteArrayInputStream(s.getBytes(StandardCharsets.ISO_8859_1));
        final ChunkedInputStream in = new ChunkedInputStream(inBuffer, inputStream);

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
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16);
        final TimeoutByteArrayInputStream inputStream = new TimeoutByteArrayInputStream(s.getBytes(StandardCharsets.ISO_8859_1));
        final ChunkedInputStream in = new ChunkedInputStream(inBuffer, inputStream);

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

        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream("1234567890abcdef\r\n01234567".getBytes(
                StandardCharsets.ISO_8859_1));
        final ChunkedInputStream in = new ChunkedInputStream(inBuffer, inputStream);

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < 8; ++i) {
            out.write(in.read());
        }

        final String result = new String(out.toByteArray(), StandardCharsets.ISO_8859_1);
        Assert.assertEquals("01234567", result);
    }

}

