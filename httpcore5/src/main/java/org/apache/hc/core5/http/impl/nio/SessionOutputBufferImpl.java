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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;

import org.apache.hc.core5.http.Chars;
import org.apache.hc.core5.http.nio.SessionOutputBuffer;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.CharArrayBuffer;

class SessionOutputBufferImpl extends ExpandableBuffer implements SessionOutputBuffer {

    private static final byte[] CRLF = new byte[] {Chars.CR, Chars.LF};

    private final CharsetEncoder charEncoder;
    private final int lineBuffersize;

    private CharBuffer charbuffer;

    /**
     *  Creates SessionOutputBufferImpl instance.
     *
     * @param bufferSize input buffer size
     * @param lineBuffersize buffer size for line operations. Has effect only if
     *   {@code charEncoder} is not {@code null}.
     * @param charEncoder charEncoder to be used for encoding HTTP protocol elements.
     *   If {@code null} simple type cast will be used for char to byte conversion.
     *
     * @since 4.3
     */
    public SessionOutputBufferImpl(
            final int bufferSize,
            final int lineBuffersize,
            final CharsetEncoder charEncoder) {
        super(bufferSize);
        this.lineBuffersize = Args.positive(lineBuffersize, "Line buffer size");
        this.charEncoder = charEncoder;
    }

    /**
     * @since 4.3
     */
    public SessionOutputBufferImpl(
            final int bufferSize,
            final int lineBufferSize,
            final Charset charset) {
        this(bufferSize, lineBufferSize, charset != null ? charset.newEncoder() : null);
    }

    /**
     * @since 4.3
     */
    public SessionOutputBufferImpl(
            final int bufferSize,
            final int lineBufferSize) {
        this(bufferSize, lineBufferSize, (CharsetEncoder) null);
    }

    /**
     * @since 4.3
     */
    public SessionOutputBufferImpl(final int bufferSize) {
        this(bufferSize, 256);
    }

    @Override
    public int length() {
        return super.length();
    }

    @Override
    public boolean hasData() {
        return super.hasData();
    }

    @Override
    public int capacity() {
        return super.capacity();
    }

    @Override
    public int flush(final WritableByteChannel channel) throws IOException {
        Args.notNull(channel, "Channel");
        setOutputMode();
        return channel.write(buffer());
    }

    @Override
    public void write(final ByteBuffer src) {
        if (src == null) {
            return;
        }
        setInputMode();
        ensureAdjustedCapacity(buffer().position() + src.remaining());
        buffer().put(src);
    }

    @Override
    public void write(final ReadableByteChannel src) throws IOException {
        if (src == null) {
            return;
        }
        setInputMode();
        src.read(buffer());
    }

    private void write(final byte[] b) {
        if (b == null) {
            return;
        }
        setInputMode();
        final int off = 0;
        final int len = b.length;
        final int requiredCapacity = buffer().position() + len;
        ensureAdjustedCapacity(requiredCapacity);
        buffer().put(b, off, len);
    }

    private void writeCRLF() {
        write(CRLF);
    }

    @Override
    public void writeLine(final CharArrayBuffer lineBuffer) throws CharacterCodingException {
        if (lineBuffer == null) {
            return;
        }
        setInputMode();
        // Do not bother if the buffer is empty
        if (lineBuffer.length() > 0 ) {
            if (this.charEncoder == null) {
                final int requiredCapacity = buffer().position() + lineBuffer.length();
                ensureCapacity(requiredCapacity);
                if (buffer().hasArray()) {
                    final byte[] b = buffer().array();
                    final int len = lineBuffer.length();
                    final int off = buffer().position();
                    final int arrayOffset = buffer().arrayOffset();
                    for (int i = 0; i < len; i++) {
                        b[arrayOffset + off + i]  = (byte) lineBuffer.charAt(i);
                    }
                    buffer().position(off + len);
                } else {
                    for (int i = 0; i < lineBuffer.length(); i++) {
                        buffer().put((byte) lineBuffer.charAt(i));
                    }
                }
            } else {
                if (this.charbuffer == null) {
                    this.charbuffer = CharBuffer.allocate(this.lineBuffersize);
                }
                this.charEncoder.reset();
                // transfer the string in small chunks
                int remaining = lineBuffer.length();
                int offset = 0;
                while (remaining > 0) {
                    int l = this.charbuffer.remaining();
                    boolean eol = false;
                    if (remaining <= l) {
                        l = remaining;
                        // terminate the encoding process
                        eol = true;
                    }
                    this.charbuffer.put(lineBuffer.array(), offset, l);
                    this.charbuffer.flip();

                    boolean retry = true;
                    while (retry) {
                        final CoderResult result = this.charEncoder.encode(this.charbuffer, buffer(), eol);
                        if (result.isError()) {
                            result.throwException();
                        }
                        if (result.isOverflow()) {
                            expand();
                        }
                        retry = !result.isUnderflow();
                    }
                    this.charbuffer.compact();
                    offset += l;
                    remaining -= l;
                }
                // flush the encoder
                boolean retry = true;
                while (retry) {
                    final CoderResult result = this.charEncoder.flush(buffer());
                    if (result.isError()) {
                        result.throwException();
                    }
                    if (result.isOverflow()) {
                        expand();
                    }
                    retry = !result.isUnderflow();
                }
            }
        }
        writeCRLF();
    }

}
