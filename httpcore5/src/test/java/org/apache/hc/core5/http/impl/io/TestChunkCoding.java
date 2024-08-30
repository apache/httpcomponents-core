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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestChunkCoding {

    private final static String CHUNKED_INPUT
        = "10;key=\"value\"\r\n1234567890123456\r\n5\r\n12345\r\n0\r\nFooter1: abcde\r\nFooter2: fghij\r\n";

    private final static String CHUNKED_RESULT
        = "123456789012345612345";

    @Test
    void testChunkedInputStreamLargeBuffer() throws IOException {
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(CHUNKED_INPUT.getBytes(StandardCharsets.US_ASCII));
        final ChunkedInputStream in = new ChunkedInputStream(inBuffer, inputStream);
        final byte[] buffer = new byte[300];
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        int len;
        while ((len = in.read(buffer)) > 0) {
            out.write(buffer, 0, len);
        }
        Assertions.assertEquals(-1, in.read(buffer));
        Assertions.assertEquals(-1, in.read(buffer));

        in.close();

        final String result = new String(out.toByteArray(), StandardCharsets.US_ASCII);
        Assertions.assertEquals(CHUNKED_RESULT, result);

        final Header[] footers = in.getFooters();
        Assertions.assertNotNull(footers);
        Assertions.assertEquals(2, footers.length);
        Assertions.assertEquals("Footer1", footers[0].getName());
        Assertions.assertEquals("abcde", footers[0].getValue());
        Assertions.assertEquals("Footer2", footers[1].getName());
        Assertions.assertEquals("fghij", footers[1].getValue());
    }

    //Test for when buffer is smaller than chunk size.
    @Test
    void testChunkedInputStreamSmallBuffer() throws IOException {
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(CHUNKED_INPUT.getBytes(StandardCharsets.US_ASCII));
        final ChunkedInputStream in = new ChunkedInputStream(inBuffer, inputStream);

        final byte[] buffer = new byte[7];
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        int len;
        while ((len = in.read(buffer)) > 0) {
            out.write(buffer, 0, len);
        }
        Assertions.assertEquals(-1, in.read(buffer));
        Assertions.assertEquals(-1, in.read(buffer));

        in.close();

        final Header[] footers = in.getFooters();
        Assertions.assertNotNull(footers);
        Assertions.assertEquals(2, footers.length);
        Assertions.assertEquals("Footer1", footers[0].getName());
        Assertions.assertEquals("abcde", footers[0].getValue());
        Assertions.assertEquals("Footer2", footers[1].getName());
        Assertions.assertEquals("fghij", footers[1].getValue());
    }

    // One byte read
    @Test
    void testChunkedInputStreamOneByteRead() throws IOException {
        final String s = "5\r\n01234\r\n5\r\n56789\r\n0\r\n";
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        try (ChunkedInputStream in = new ChunkedInputStream(inBuffer, inputStream)) {
            int ch;
            int i = '0';
            while ((ch = in.read()) != -1) {
                Assertions.assertEquals(i, ch);
                i++;
            }
            Assertions.assertEquals(-1, in.read());
            Assertions.assertEquals(-1, in.read());
        }
    }

    @Test
    void testAvailable() throws IOException {
        final String s = "5\r\n12345\r\n0\r\n";
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        try (ChunkedInputStream in = new ChunkedInputStream(inBuffer, inputStream)) {
            Assertions.assertEquals(0, in.available());
            in.read();
            Assertions.assertEquals(4, in.available());
        }
    }

    @Test
    void testChunkedInputStreamClose() throws IOException {
        final String s = "5\r\n01234\r\n5\r\n56789\r\n0\r\n";
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        final ChunkedInputStream in = new ChunkedInputStream(inBuffer, inputStream);
        in.close();
        in.close();
        Assertions.assertThrows(StreamClosedException.class, () -> in.read());
        final byte[] tmp = new byte[10];
        Assertions.assertThrows(StreamClosedException.class, () -> in.read(tmp));
        Assertions.assertThrows(StreamClosedException.class, () -> in.read(tmp, 0, tmp.length));
    }

    // Missing closing chunk
    @Test
    void testChunkedInputStreamNoClosingChunk() throws IOException {
        final String s = "5\r\n01234\r\n";
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        try (ChunkedInputStream in = new ChunkedInputStream(inBuffer, inputStream)) {
            final byte[] tmp = new byte[5];
            Assertions.assertEquals(5, in.read(tmp));
            Assertions.assertThrows(ConnectionClosedException.class, () -> in.read());
            Assertions.assertThrows(ConnectionClosedException.class, () -> in.close());
        }
    }

    // Truncated stream (missing closing CRLF)
    @Test
    void testCorruptChunkedInputStreamTruncatedCRLF() throws IOException {
        final String s = "5\r\n01234";
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        try (ChunkedInputStream in = new ChunkedInputStream(inBuffer, inputStream)) {
            final byte[] tmp = new byte[5];
            Assertions.assertEquals(5, in.read(tmp));
            Assertions.assertThrows(MalformedChunkCodingException.class, () -> in.read());
        }
    }

    // Missing \r\n at the end of the first chunk
    @Test
    void testCorruptChunkedInputStreamMissingCRLF() throws IOException {
        final String s = "5\r\n012345\r\n56789\r\n0\r\n";
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        try (ChunkedInputStream in = new ChunkedInputStream(inBuffer, inputStream)) {
            final byte[] buffer = new byte[300];
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            Assertions.assertThrows(MalformedChunkCodingException.class, () -> {
                int len;
                while ((len = in.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                }
            });
        }
    }

    // Missing LF
    @Test
    void testCorruptChunkedInputStreamMissingLF() throws IOException {
        final String s = "5\r01234\r\n5\r\n56789\r\n0\r\n";
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        try (ChunkedInputStream in = new ChunkedInputStream(inBuffer, inputStream)) {
            Assertions.assertThrows(MalformedChunkCodingException.class, in::read);
        }
    }

    // Invalid chunk size
    @Test
    void testCorruptChunkedInputStreamInvalidSize() throws IOException {
        final String s = "whatever\r\n01234\r\n5\r\n56789\r\n0\r\n";
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        try (ChunkedInputStream in = new ChunkedInputStream(inBuffer, inputStream)) {
            Assertions.assertThrows(MalformedChunkCodingException.class, in::read);
        }
    }

    // Negative chunk size
    @Test
    void testCorruptChunkedInputStreamNegativeSize() throws IOException {
        final String s = "-5\r\n01234\r\n5\r\n56789\r\n0\r\n";
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        try (ChunkedInputStream in = new ChunkedInputStream(inBuffer, inputStream)) {
            Assertions.assertThrows(MalformedChunkCodingException.class, in::read);
        }
    }

    // Truncated chunk
    @Test
    void testCorruptChunkedInputStreamTruncatedChunk() throws IOException {
        final String s = "3\r\n12";
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        try (ChunkedInputStream in = new ChunkedInputStream(inBuffer, inputStream)) {
            final byte[] buffer = new byte[300];
            Assertions.assertEquals(2, in.read(buffer));
            Assertions.assertThrows(MalformedChunkCodingException.class, () -> in.read(buffer));
        }
    }

    // Invalid footer
    @Test
    void testCorruptChunkedInputStreamInvalidFooter() throws IOException {
        final String s = "1\r\n0\r\n0\r\nstuff\r\n";
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        try (ChunkedInputStream in = new ChunkedInputStream(inBuffer, inputStream)) {
            in.read();
            Assertions.assertThrows(MalformedChunkCodingException.class, in::read);
        }
    }

    @Test
    void testCorruptChunkedInputStreamClose() throws IOException {
        final String s = "whatever\r\n01234\r\n5\r\n56789\r\n0\r\n";
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        try (final ChunkedInputStream in = new ChunkedInputStream(inBuffer, inputStream)) {
            Assertions.assertThrows(MalformedChunkCodingException.class, in::read);
        }
    }

    @Test
    void testEmptyChunkedInputStream() throws IOException {
        final String s = "0\r\n";
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        try (ChunkedInputStream in = new ChunkedInputStream(inBuffer, inputStream)) {
            final byte[] buffer = new byte[300];
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            int len;
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
            Assertions.assertEquals(0, out.size());
        }
    }

    @Test
    void testTooLongChunkHeader() throws IOException {
        final String s = "5; and some very looooong commend\r\n12345\r\n0\r\n";
        final SessionInputBuffer inBuffer1 = new SessionInputBufferImpl(16);
        final byte[] buffer = new byte[300];
        final ByteArrayInputStream inputStream1 = new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        try (final ChunkedInputStream in1 = new ChunkedInputStream(inBuffer1, inputStream1)) {
            Assertions.assertEquals(5, in1.read(buffer));
        }

        final SessionInputBuffer inBuffer2 = new SessionInputBufferImpl(16, 10);
        final ByteArrayInputStream inputStream2 = new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        final ChunkedInputStream in2 = new ChunkedInputStream(inBuffer2, inputStream2);
        Assertions.assertThrows(MessageConstraintException.class, () -> in2.read(buffer));
        // close() would throw here
    }

    @Test
    void testChunkedOutputStreamClose() throws IOException {
        final SessionOutputBuffer outbuffer = new SessionOutputBufferImpl(16);
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final ChunkedOutputStream out = new ChunkedOutputStream(outbuffer, outputStream, 2048);
        out.close();
        out.close();
        Assertions.assertThrows(IOException.class, () -> out.write(new byte[] {1,2,3}));
        Assertions.assertThrows(IOException.class, () -> out.write(1));
    }

    @Test
    void testChunkedConsistence() throws IOException {
        final String input = "76126;27823abcd;:q38a-\nkjc\rk%1ad\tkh/asdui\r\njkh+?\\suweb";
        final SessionOutputBuffer outbuffer = new SessionOutputBufferImpl(16);
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ChunkedOutputStream out = new ChunkedOutputStream(outbuffer, outputStream, 2048)) {
            out.write(input.getBytes(StandardCharsets.US_ASCII));
            out.flush();
            out.close();
        }
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        try (ChunkedInputStream in = new ChunkedInputStream(inBuffer, inputStream)) {

            final byte[] d = new byte[10];
            final ByteArrayOutputStream result = new ByteArrayOutputStream();
            int len = 0;
            while ((len = in.read(d)) > 0) {
                result.write(d, 0, len);
            }

            final String output = new String(result.toByteArray(), StandardCharsets.US_ASCII);
            Assertions.assertEquals(input, output);
        }
    }

    @Test
    void testChunkedOutputStreamWithTrailers() throws IOException {
        final SessionOutputBuffer outbuffer = new SessionOutputBufferImpl(16);
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ChunkedOutputStream out = new ChunkedOutputStream(outbuffer, outputStream, 2, () -> Arrays.asList(
                new BasicHeader("E", ""),
                new BasicHeader("Y", "Z"))
        )) {
            out.write('x');
            out.finish();
        }

        final String content = new String(outputStream.toByteArray(), StandardCharsets.US_ASCII);
        Assertions.assertEquals("1\r\nx\r\n0\r\nE: \r\nY: Z\r\n\r\n", content);
    }

    @Test
    void testChunkedOutputStream() throws IOException {
        final SessionOutputBuffer outbuffer = new SessionOutputBufferImpl(16);
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ChunkedOutputStream out = new ChunkedOutputStream(outbuffer, outputStream, 2)) {
            out.write('1');
            out.write('2');
            out.write('3');
            out.write('4');
            out.finish();
        }

        final String content = new String(outputStream.toByteArray(), StandardCharsets.US_ASCII);
        Assertions.assertEquals("2\r\n12\r\n2\r\n34\r\n0\r\n\r\n", content);
    }

    @Test
    void testChunkedOutputStreamLargeChunk() throws IOException {
        final SessionOutputBuffer outbuffer = new SessionOutputBufferImpl(16);
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ChunkedOutputStream out = new ChunkedOutputStream(outbuffer, outputStream, 2)) {
            out.write(new byte[] { '1', '2', '3', '4' });
            out.finish();
        }

        final String content = new String(outputStream.toByteArray(), StandardCharsets.US_ASCII);
        Assertions.assertEquals("4\r\n1234\r\n0\r\n\r\n", content);
    }

    @Test
    void testChunkedOutputStreamSmallChunk() throws IOException {
        final SessionOutputBuffer outbuffer = new SessionOutputBufferImpl(16);
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ChunkedOutputStream out = new ChunkedOutputStream(outbuffer, outputStream, 2)) {
            out.write('1');
            out.finish();
        }

        final String content = new String(outputStream.toByteArray(), StandardCharsets.US_ASCII);
        Assertions.assertEquals("1\r\n1\r\n0\r\n\r\n", content);
    }

    @Test
    void testResumeOnSocketTimeoutInData() throws IOException {
        final String s = "5\r\n01234\r\n5\r\n5\0006789\r\na\r\n0123\000456789\r\n0\r\n";
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16);
        try (TimeoutByteArrayInputStream inputStream = new TimeoutByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
                ChunkedInputStream in = new ChunkedInputStream(inBuffer, inputStream)) {

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
            Assertions.assertEquals(20, bytesRead);
            Assertions.assertEquals(2, timeouts);
        }
    }

    @Test
    void testResumeOnSocketTimeoutInChunk() throws IOException {
        final String s = "5\000\r\000\n\00001234\r\n\0005\r\n56789\r\na\r\n0123456789\r\n\0000\r\n";
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16);
        try (TimeoutByteArrayInputStream inputStream = new TimeoutByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
                ChunkedInputStream in = new ChunkedInputStream(inBuffer, inputStream)) {

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
            Assertions.assertEquals(20, bytesRead);
            Assertions.assertEquals(5, timeouts);
        }
    }

    // Test for when buffer is larger than chunk size
    @Test
    void testHugeChunk() throws IOException {

        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream("1234567890abcdef\r\n01234567".getBytes(
                StandardCharsets.US_ASCII));
        final ChunkedInputStream in = new ChunkedInputStream(inBuffer, inputStream);

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < 8; ++i) {
            out.write(in.read());
        }

        final String result = new String(out.toByteArray(), StandardCharsets.US_ASCII);
        Assertions.assertEquals("01234567", result);
    }

}

