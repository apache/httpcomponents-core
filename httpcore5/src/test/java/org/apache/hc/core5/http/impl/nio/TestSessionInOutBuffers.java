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

package org.apache.hc.core5.http.impl.nio;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.http.MessageConstraintException;
import org.apache.hc.core5.http.nio.SessionInputBuffer;
import org.apache.hc.core5.http.nio.SessionOutputBuffer;
import org.apache.hc.core5.util.CharArrayBuffer;
import org.junit.Assert;
import org.junit.Test;

/**
 * Simple tests for {@link SessionInputBuffer} and {@link SessionOutputBuffer}.
 */
public class TestSessionInOutBuffers {

    private static WritableByteChannel newChannel(final ByteArrayOutputStream outStream) {
        return Channels.newChannel(outStream);
    }

    private static ReadableByteChannel newChannel(final byte[] bytes) {
        return Channels.newChannel(new ByteArrayInputStream(bytes));
    }

    private static ReadableByteChannel newChannel(final String s, final Charset charset) {
        return Channels.newChannel(new ByteArrayInputStream(s.getBytes(charset)));
    }

    private static ReadableByteChannel newChannel(final String s) {
        return newChannel(s, StandardCharsets.US_ASCII);
    }

    @Test
    public void testReadLineChunks() throws Exception {
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(16, 16);

        ReadableByteChannel channel = newChannel("One\r\nTwo\r\nThree");

        inbuf.fill(channel);

        final CharArrayBuffer line = new CharArrayBuffer(64);

        line.clear();
        Assert.assertTrue(inbuf.readLine(line, false));
        Assert.assertEquals("One", line.toString());

        line.clear();
        Assert.assertTrue(inbuf.readLine(line, false));
        Assert.assertEquals("Two", line.toString());

        line.clear();
        Assert.assertFalse(inbuf.readLine(line, false));

        channel = newChannel("\r\nFour");
        inbuf.fill(channel);

        line.clear();
        Assert.assertTrue(inbuf.readLine(line, false));
        Assert.assertEquals("Three", line.toString());

        inbuf.fill(channel);

        line.clear();
        Assert.assertTrue(inbuf.readLine(line, true));
        Assert.assertEquals("Four", line.toString());

        line.clear();
        Assert.assertFalse(inbuf.readLine(line, true));
    }

    @Test
    public void testLineLimit() throws Exception {
        final String s = "LoooooooooooooooooooooooooOOOOOOOOOOOOOOOOOOoooooooooooooooooooooong line\r\n";
        final CharArrayBuffer line = new CharArrayBuffer(64);
        final SessionInputBuffer inbuf1 = new SessionInputBufferImpl(128, 128);
        final ReadableByteChannel channel1 = newChannel(s);
        inbuf1.fill(channel1);

        Assert.assertTrue(inbuf1.readLine(line, false));

        line.clear();
        final SessionInputBuffer inbuf2 = new SessionInputBufferImpl(128, 128, 10);
        final ReadableByteChannel channel2 = newChannel(s);
        inbuf2.fill(channel2);
        Assert.assertThrows(MessageConstraintException.class, () -> inbuf2.readLine(line, false));
    }

    @Test
    public void testLineLimitBufferFull() throws Exception {
        final String s = "LoooooooooooooooooooooooooOOOOOOOOOOOOOOOOOOoooooooooooooooooooooong line\r\n";
        final CharArrayBuffer line = new CharArrayBuffer(64);
        final SessionInputBuffer inbuf1 = new SessionInputBufferImpl(32, 32);
        final ReadableByteChannel channel1 = newChannel(s);
        inbuf1.fill(channel1);

        Assert.assertFalse(inbuf1.readLine(line, false));

        line.clear();
        final SessionInputBuffer inbuf2 = new SessionInputBufferImpl(32, 32,10);
        final ReadableByteChannel channel2 = newChannel(s);
        inbuf2.fill(channel2);
        Assert.assertThrows(MessageConstraintException.class, () -> inbuf2.readLine(line, false));
    }

    @Test
    public void testWriteLineChunks() throws Exception {
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(16, 16);
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(16, 16, 0);

        ReadableByteChannel inChannel = newChannel("One\r\nTwo\r\nThree");

        inbuf.fill(inChannel);

        final CharArrayBuffer line = new CharArrayBuffer(64);

        line.clear();
        Assert.assertTrue(inbuf.readLine(line, false));
        Assert.assertEquals("One", line.toString());

        outbuf.writeLine(line);

        line.clear();
        Assert.assertTrue(inbuf.readLine(line, false));
        Assert.assertEquals("Two", line.toString());

        outbuf.writeLine(line);

        line.clear();
        Assert.assertFalse(inbuf.readLine(line, false));

        inChannel = newChannel("\r\nFour");
        inbuf.fill(inChannel);

        line.clear();
        Assert.assertTrue(inbuf.readLine(line, false));
        Assert.assertEquals("Three", line.toString());

        outbuf.writeLine(line);

        inbuf.fill(inChannel);

        line.clear();
        Assert.assertTrue(inbuf.readLine(line, true));
        Assert.assertEquals("Four", line.toString());

        outbuf.writeLine(line);

        line.clear();
        Assert.assertFalse(inbuf.readLine(line, true));

        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        final WritableByteChannel outChannel = newChannel(outStream);
        outbuf.flush(outChannel);

        final String s = new String(outStream.toByteArray(), StandardCharsets.US_ASCII);
        Assert.assertEquals("One\r\nTwo\r\nThree\r\nFour\r\n", s);
    }

    @Test
    public void testBasicReadWriteLine() throws Exception {

        final String[] teststrs = new String[5];
        teststrs[0] = "Hello";
        teststrs[1] = "This string should be much longer than the size of the line buffer " +
                "which is only 16 bytes for this test";
        final StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < 15; i++) {
            buffer.append("123456789 ");
        }
        buffer.append("and stuff like that");
        teststrs[2] = buffer.toString();
        teststrs[3] = "";
        teststrs[4] = "And goodbye";

        final CharArrayBuffer chbuffer = new CharArrayBuffer(32);
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 16);
        for (final String teststr : teststrs) {
            chbuffer.clear();
            chbuffer.append(teststr);
            outbuf.writeLine(chbuffer);
        }
        //this write operation should have no effect
        outbuf.writeLine(null);

        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        final WritableByteChannel outChannel = newChannel(outStream);
        outbuf.flush(outChannel);

        final ReadableByteChannel channel = newChannel(outStream.toByteArray());

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 16, 0);
        inbuf.fill(channel);

        for (final String teststr : teststrs) {
            chbuffer.clear();
            inbuf.readLine(chbuffer, true);
            Assert.assertEquals(teststr, chbuffer.toString());
        }
        chbuffer.clear();
        Assert.assertFalse(inbuf.readLine(chbuffer, true));
        chbuffer.clear();
        Assert.assertFalse(inbuf.readLine(chbuffer, true));
    }

    @Test
    public void testComplexReadWriteLine() throws Exception {
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 16);
        outbuf.write(ByteBuffer.wrap(new byte[] {'a', '\n'}));
        outbuf.write(ByteBuffer.wrap(new byte[] {'\r', '\n'}));
        outbuf.write(ByteBuffer.wrap(new byte[] {'\r', '\r', '\n'}));
        outbuf.write(ByteBuffer.wrap(new byte[] {'\n'}));

        final StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < 14; i++) {
            buffer.append("a");
        }
        final String s1 = buffer.toString();
        buffer.append("\r\n");
        outbuf.write(ByteBuffer.wrap(buffer.toString().getBytes(StandardCharsets.US_ASCII)));

        buffer.setLength(0);
        for (int i = 0; i < 15; i++) {
            buffer.append("a");
        }
        final String s2 = buffer.toString();
        buffer.append("\r\n");
        outbuf.write(ByteBuffer.wrap(buffer.toString().getBytes(StandardCharsets.US_ASCII)));

        buffer.setLength(0);
        for (int i = 0; i < 16; i++) {
            buffer.append("a");
        }
        final String s3 = buffer.toString();
        buffer.append("\r\n");
        outbuf.write(ByteBuffer.wrap(buffer.toString().getBytes(StandardCharsets.US_ASCII)));

        outbuf.write(ByteBuffer.wrap(new byte[] {'a'}));

        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        final WritableByteChannel outChannel = newChannel(outStream);
        outbuf.flush(outChannel);

        final ReadableByteChannel channel = newChannel(outStream.toByteArray());

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 16, 0);
        inbuf.fill(channel);

        final CharArrayBuffer chbuffer = new CharArrayBuffer(32);
        chbuffer.clear();
        inbuf.readLine(chbuffer, true);
        Assert.assertEquals("a", chbuffer.toString());
        chbuffer.clear();
        inbuf.readLine(chbuffer, true);
        Assert.assertEquals("", chbuffer.toString());
        chbuffer.clear();
        inbuf.readLine(chbuffer, true);
        Assert.assertEquals("\r", chbuffer.toString());
        chbuffer.clear();
        inbuf.readLine(chbuffer, true);
        Assert.assertEquals("", chbuffer.toString());
        chbuffer.clear();
        inbuf.readLine(chbuffer, true);
        Assert.assertEquals(s1, chbuffer.toString());
        chbuffer.clear();
        inbuf.readLine(chbuffer, true);
        Assert.assertEquals(s2, chbuffer.toString());
        chbuffer.clear();
        inbuf.readLine(chbuffer, true);
        Assert.assertEquals(s3, chbuffer.toString());
        chbuffer.clear();
        inbuf.readLine(chbuffer, true);
        Assert.assertEquals("a", chbuffer.toString());
        chbuffer.clear();
        inbuf.readLine(chbuffer, true);
        Assert.assertFalse(inbuf.readLine(chbuffer, true));
        chbuffer.clear();
        inbuf.readLine(chbuffer, true);
        Assert.assertFalse(inbuf.readLine(chbuffer, true));
    }

    @Test
    public void testReadOneByte() throws Exception {
        // make the buffer larger than that of transmitter
        final byte[] out = new byte[40];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte)('0' + i);
        }
        final ReadableByteChannel channel = newChannel(out);
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(16, 16, 0);
        while (inbuf.fill(channel) > 0) {
        }

        final byte[] in = new byte[40];
        for (int i = 0; i < in.length; i++) {
            in[i] = (byte)inbuf.read();
        }
        for (int i = 0; i < out.length; i++) {
            Assert.assertEquals(out[i], in[i]);
        }
    }

    @Test
    public void testReadByteBuffer() throws Exception {
        final byte[] pattern = "0123456789ABCDEF".getBytes(StandardCharsets.US_ASCII);
        final ReadableByteChannel channel = newChannel(pattern);
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(4096, 1024, 0);
        while (inbuf.fill(channel) > 0) {
        }
        final ByteBuffer dst = ByteBuffer.allocate(10);
        Assert.assertEquals(10, inbuf.read(dst));
        dst.flip();
        Assert.assertEquals(dst, ByteBuffer.wrap(pattern, 0, 10));
        dst.clear();
        Assert.assertEquals(6, inbuf.read(dst));
        dst.flip();
        Assert.assertEquals(dst, ByteBuffer.wrap(pattern, 10, 6));
    }

    @Test
    public void testReadByteBufferWithMaxLen() throws Exception {
        final byte[] pattern = "0123456789ABCDEF".getBytes(StandardCharsets.US_ASCII);
        final ReadableByteChannel channel = newChannel(pattern);
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(4096, 1024, 0);
        while (inbuf.fill(channel) > 0) {
        }
        final ByteBuffer dst = ByteBuffer.allocate(16);
        Assert.assertEquals(10, inbuf.read(dst, 10));
        dst.flip();
        Assert.assertEquals(dst, ByteBuffer.wrap(pattern, 0, 10));
        dst.clear();
        Assert.assertEquals(3, inbuf.read(dst, 3));
        dst.flip();
        Assert.assertEquals(dst, ByteBuffer.wrap(pattern, 10, 3));
        Assert.assertEquals(3, inbuf.read(dst, 20));
        dst.flip();
        Assert.assertEquals(dst, ByteBuffer.wrap(pattern, 13, 3));
    }

    @Test
    public void testReadToChannel() throws Exception {
        final byte[] pattern = "0123456789ABCDEF".getBytes(StandardCharsets.US_ASCII);
        final ReadableByteChannel channel = newChannel(pattern);
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(4096, 1024, 0);
        while (inbuf.fill(channel) > 0) {
        }

        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        final WritableByteChannel dst = newChannel(outStream);

        Assert.assertEquals(16, inbuf.read(dst));
        Assert.assertEquals(ByteBuffer.wrap(pattern), ByteBuffer.wrap(outStream.toByteArray()));
    }

    @Test
    public void testReadToChannelWithMaxLen() throws Exception {
        final byte[] pattern = "0123456789ABCDEF".getBytes(StandardCharsets.US_ASCII);
        final ReadableByteChannel channel = newChannel(pattern);
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(4096, 1024, 0);
        while (inbuf.fill(channel) > 0) {
        }

        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        final WritableByteChannel dst = newChannel(outStream);

        Assert.assertEquals(10, inbuf.read(dst, 10));
        Assert.assertEquals(3, inbuf.read(dst, 3));
        Assert.assertEquals(3, inbuf.read(dst, 10));
        Assert.assertEquals(ByteBuffer.wrap(pattern), ByteBuffer.wrap(outStream.toByteArray()));
    }

    @Test
    public void testWriteByteBuffer() throws Exception {
        final byte[] pattern = "0123456789ABCDEF0123456789ABCDEF".getBytes(StandardCharsets.US_ASCII);

        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(4096, 1024);
        final ReadableByteChannel src = newChannel(pattern);
        outbuf.write(src);

        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        final WritableByteChannel channel = newChannel(outStream);
        while (outbuf.flush(channel) > 0) {
        }
        Assert.assertEquals(ByteBuffer.wrap(pattern), ByteBuffer.wrap(outStream.toByteArray()));
    }

    @Test
    public void testWriteFromChannel() throws Exception {
        final byte[] pattern = "0123456789ABCDEF0123456789ABCDEF".getBytes(StandardCharsets.US_ASCII);

        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(4096, 1024);
        outbuf.write(ByteBuffer.wrap(pattern, 0, 16));
        outbuf.write(ByteBuffer.wrap(pattern, 16, 10));
        outbuf.write(ByteBuffer.wrap(pattern, 26, 6));

        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        final WritableByteChannel channel = newChannel(outStream);
        while (outbuf.flush(channel) > 0) {
        }
        Assert.assertEquals(ByteBuffer.wrap(pattern), ByteBuffer.wrap(outStream.toByteArray()));
    }

    static final int SWISS_GERMAN_HELLO [] = {
        0x47, 0x72, 0xFC, 0x65, 0x7A, 0x69, 0x5F, 0x7A, 0xE4, 0x6D, 0xE4
    };

    static final int RUSSIAN_HELLO [] = {
        0x412, 0x441, 0x435, 0x43C, 0x5F, 0x43F, 0x440, 0x438,
        0x432, 0x435, 0x442
    };

    private static String constructString(final int [] unicodeChars) {
        final StringBuilder buffer = new StringBuilder();
        if (unicodeChars != null) {
            for (final int unicodeChar : unicodeChars) {
                buffer.append((char)unicodeChar);
            }
        }
        return buffer.toString();
    }

    @Test
    public void testMultibyteCodedReadWriteLine() throws Exception {
        final String s1 = constructString(SWISS_GERMAN_HELLO);
        final String s2 = constructString(RUSSIAN_HELLO);
        final String s3 = "Like hello and stuff";

        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 16,
                StandardCharsets.UTF_8.newEncoder());

        final CharArrayBuffer chbuffer = new CharArrayBuffer(32);
        for (int i = 0; i < 10; i++) {
            chbuffer.clear();
            chbuffer.append(s1);
            outbuf.writeLine(chbuffer);
            chbuffer.clear();
            chbuffer.append(s2);
            outbuf.writeLine(chbuffer);
            chbuffer.clear();
            chbuffer.append(s3);
            outbuf.writeLine(chbuffer);
        }

        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        final WritableByteChannel outChannel = newChannel(outStream);
        outbuf.flush(outChannel);

        final byte[] tmp = outStream.toByteArray();

        final ReadableByteChannel channel = newChannel(tmp);
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(16, 16, 0,
                StandardCharsets.UTF_8.newDecoder());

        while (inbuf.fill(channel) > 0) {
        }

        for (int i = 0; i < 10; i++) {
            chbuffer.clear();
            inbuf.readLine(chbuffer, true);
            Assert.assertEquals(s1, chbuffer.toString());
            chbuffer.clear();
            inbuf.readLine(chbuffer, true);
            Assert.assertEquals(s2, chbuffer.toString());
            chbuffer.clear();
            inbuf.readLine(chbuffer, true);
            Assert.assertEquals(s3, chbuffer.toString());
        }
    }

    @Test
    public void testInputMatchesBufferLength() throws Exception {
        final String s1 = "abcde";
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 5);
        final CharArrayBuffer chbuffer = new CharArrayBuffer(16);
        chbuffer.append(s1);
        outbuf.writeLine(chbuffer);
    }

    @Test
    public void testMalformedInputActionReport() throws Exception {
        final String s = constructString(SWISS_GERMAN_HELLO);
        final byte[] tmp = s.getBytes(StandardCharsets.ISO_8859_1);

        final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
        decoder.onMalformedInput(CodingErrorAction.REPORT);
        decoder.onUnmappableCharacter(CodingErrorAction.IGNORE);
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(16, 16, 0, decoder);
        final ReadableByteChannel channel = newChannel(tmp);
        while (inbuf.fill(channel) > 0) {
        }
        final CharArrayBuffer chbuffer = new CharArrayBuffer(16);
        Assert.assertThrows(CharacterCodingException.class, () ->
                inbuf.readLine(chbuffer, true));
    }

    @Test
    public void testMalformedInputActionIgnore() throws Exception {
        final String s = constructString(SWISS_GERMAN_HELLO);
        final byte[] tmp = s.getBytes(StandardCharsets.ISO_8859_1);

        final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
        decoder.onMalformedInput(CodingErrorAction.IGNORE);
        decoder.onUnmappableCharacter(CodingErrorAction.IGNORE);
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(16, 16, 0, decoder);
        final ReadableByteChannel channel = newChannel(tmp);
        while (inbuf.fill(channel) > 0) {
        }
        final CharArrayBuffer chbuffer = new CharArrayBuffer(16);
        inbuf.readLine(chbuffer, true);
        Assert.assertEquals("Grezi_zm", chbuffer.toString());
    }

    @Test
    public void testMalformedInputActionReplace() throws Exception {
        final String s = constructString(SWISS_GERMAN_HELLO);
        final byte[] tmp = s.getBytes(StandardCharsets.ISO_8859_1);

        final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
        decoder.onMalformedInput(CodingErrorAction.REPLACE);
        decoder.onUnmappableCharacter(CodingErrorAction.IGNORE);
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(16, 16, 0, decoder);
        final ReadableByteChannel channel = newChannel(tmp);
        while (inbuf.fill(channel) > 0) {
        }
        final CharArrayBuffer chbuffer = new CharArrayBuffer(16);
        inbuf.readLine(chbuffer, true);
        Assert.assertEquals("Gr\ufffdezi_z\ufffdm\ufffd", chbuffer.toString());
    }

    @Test
    public void testUnmappableInputActionReport() throws Exception {
        final String s = "This text contains a circumflex \u0302!!!";
        final CharsetEncoder encoder = StandardCharsets.ISO_8859_1.newEncoder();
        encoder.onMalformedInput(CodingErrorAction.IGNORE);
        encoder.onUnmappableCharacter(CodingErrorAction.REPORT);
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 16, encoder);
        final CharArrayBuffer chbuffer = new CharArrayBuffer(16);
        chbuffer.append(s);
        Assert.assertThrows(CharacterCodingException.class, () ->
                outbuf.writeLine(chbuffer));
    }

    @Test
    public void testUnmappableInputActionIgnore() throws Exception {
        final String s = "This text contains a circumflex \u0302!!!";
        final CharsetEncoder encoder = StandardCharsets.ISO_8859_1.newEncoder();
        encoder.onMalformedInput(CodingErrorAction.IGNORE);
        encoder.onUnmappableCharacter(CodingErrorAction.IGNORE);
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 16, encoder);
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final WritableByteChannel channel = newChannel(baos);
        final CharArrayBuffer chbuffer = new CharArrayBuffer(16);
        chbuffer.append(s);
        outbuf.writeLine(chbuffer);
        outbuf.flush(channel);

        final String result = new String(baos.toByteArray(), StandardCharsets.US_ASCII);
        Assert.assertEquals("This text contains a circumflex !!!\r\n", result);
    }

    @Test
    public void testUnmappableInputActionReplace() throws Exception {
        final String s = "This text contains a circumflex \u0302 !!!";
        final CharsetEncoder encoder = StandardCharsets.ISO_8859_1.newEncoder();
        encoder.onMalformedInput(CodingErrorAction.IGNORE);
        encoder.onUnmappableCharacter(CodingErrorAction.REPLACE);
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 16, encoder);
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final WritableByteChannel channel = newChannel(baos);
        final CharArrayBuffer chbuffer = new CharArrayBuffer(16);
        chbuffer.append(s);
        outbuf.writeLine(chbuffer);
        outbuf.flush(channel);

        final String result = new String(baos.toByteArray(), StandardCharsets.US_ASCII);
        Assert.assertEquals("This text contains a circumflex ? !!!\r\n", result);
    }

}
