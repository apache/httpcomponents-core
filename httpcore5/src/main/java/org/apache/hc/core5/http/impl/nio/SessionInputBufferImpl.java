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
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;

import org.apache.hc.core5.http.Chars;
import org.apache.hc.core5.http.MessageConstraintException;
import org.apache.hc.core5.http.nio.SessionInputBuffer;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.CharArrayBuffer;

/**
 * Default implementation of {@link SessionInputBuffer} based on
 * the {@link ExpandableBuffer} class.
 *
 * @since 4.0
 */
public class SessionInputBufferImpl extends ExpandableBuffer implements SessionInputBuffer {

    private final CharsetDecoder chardecoder;
    private final int lineBuffersize;
    private final int maxLineLen;

    private CharBuffer charbuffer;

    /**
     *  Creates SessionInputBufferImpl instance.
     *
     * @param buffersize input buffer size
     * @param lineBuffersize buffer size for line operations. Has effect only if
     *   {@code chardecoder} is not {@code null}.
     * @param chardecoder chardecoder to be used for decoding HTTP protocol elements.
     *   If {@code null} simple type cast will be used for byte to char conversion.
     * @param maxLineLen maximum line length.
     *
     * @since 4.4
     */
    public SessionInputBufferImpl(
            final int buffersize,
            final int lineBuffersize,
            final int maxLineLen,
            final CharsetDecoder chardecoder) {
        super(buffersize);
        this.lineBuffersize = Args.positive(lineBuffersize, "Line buffer size");
        this.maxLineLen = maxLineLen > 0 ? maxLineLen : 0;
        this.chardecoder = chardecoder;
    }

    /**
     * @since 4.3
     */
    public SessionInputBufferImpl(
            final int buffersize,
            final int lineBuffersize,
            final int maxLineLen,
            final Charset charset) {
        this(buffersize, lineBuffersize, maxLineLen, charset != null ? charset.newDecoder() : null);
    }

    /**
     * @since 4.3
     */
    public SessionInputBufferImpl(
            final int buffersize,
            final int lineBuffersize,
            final int maxLineLen) {
        this(buffersize, lineBuffersize, maxLineLen, (CharsetDecoder) null);
    }

    /**
     * @since 4.3
     */
    public SessionInputBufferImpl(
            final int buffersize,
            final int lineBuffersize) {
        this(buffersize, lineBuffersize, 0, (CharsetDecoder) null);
    }

    /**
     * @since 4.3
     */
    public SessionInputBufferImpl(final int buffersize) {
        this(buffersize, 256);
    }

    public void put(final ByteBuffer src) {
        if (src != null && src.hasRemaining()) {
            setInputMode();
            ensureCapacity(src.remaining());
            buffer().put(src);
        }
    }

    @Override
    public int fill(final ReadableByteChannel channel) throws IOException {
        Args.notNull(channel, "Channel");
        setInputMode();
        if (!buffer().hasRemaining()) {
            expand();
        }
        return channel.read(buffer());
    }

    @Override
    public int read() {
        setOutputMode();
        return buffer().get() & 0xff;
    }

    @Override
    public int read(final ByteBuffer dst, final int maxLen) {
        if (dst == null) {
            return 0;
        }
        setOutputMode();
        final int len = Math.min(dst.remaining(), maxLen);
        final int chunk = Math.min(buffer().remaining(), len);
        if (buffer().remaining() > chunk) {
            final int oldLimit = buffer().limit();
            final int newLimit = buffer().position() + chunk;
            buffer().limit(newLimit);
            dst.put(buffer());
            buffer().limit(oldLimit);
            return len;
        }
        dst.put(buffer());
        return chunk;
    }

    @Override
    public int read(final ByteBuffer dst) {
        if (dst == null) {
            return 0;
        }
        return read(dst, dst.remaining());
    }

    @Override
    public int read(final WritableByteChannel dst, final int maxLen) throws IOException {
        if (dst == null) {
            return 0;
        }
        setOutputMode();
        final int bytesRead;
        if (buffer().remaining() > maxLen) {
            final int oldLimit = buffer().limit();
            final int newLimit = oldLimit - (buffer().remaining() - maxLen);
            buffer().limit(newLimit);
            bytesRead = dst.write(buffer());
            buffer().limit(oldLimit);
        } else {
            bytesRead = dst.write(buffer());
        }
        return bytesRead;
    }

    @Override
    public int read(final WritableByteChannel dst) throws IOException {
        if (dst == null) {
            return 0;
        }
        setOutputMode();
        return dst.write(buffer());
    }

    @Override
    public boolean readLine(
            final CharArrayBuffer linebuffer,
            final boolean endOfStream) throws IOException {

        setOutputMode();
        // See if there is LF char present in the buffer
        int pos = -1;
        for (int i = buffer().position(); i < buffer().limit(); i++) {
            final int b = buffer().get(i);
            if (b == Chars.LF) {
                pos = i + 1;
                break;
            }
        }

        if (this.maxLineLen > 0) {
            final int currentLen = (pos > 0 ? pos : buffer().limit()) - buffer().position();
            if (currentLen >= this.maxLineLen) {
                throw new MessageConstraintException("Maximum line length limit exceeded");
            }
        }

        if (pos == -1) {
            if (endOfStream && buffer().hasRemaining()) {
                // No more data. Get the rest
                pos = buffer().limit();
            } else {
                // Either no complete line present in the buffer
                // or no more data is expected
                return false;
            }
        }
        final int origLimit = buffer().limit();
        buffer().limit(pos);

        final int requiredCapacity = buffer().limit() - buffer().position();
        // Ensure capacity of len assuming ASCII as the most likely charset
        linebuffer.ensureCapacity(requiredCapacity);

        if (this.chardecoder == null) {
            if (buffer().hasArray()) {
                final byte[] b = buffer().array();
                final int off = buffer().position();
                final int len = buffer().remaining();
                linebuffer.append(b, off, len);
                buffer().position(off + len);
            } else {
                while (buffer().hasRemaining()) {
                    linebuffer.append((char) (buffer().get() & 0xff));
                }
            }
        } else {
            if (this.charbuffer == null) {
                this.charbuffer = CharBuffer.allocate(this.lineBuffersize);
            }
            this.chardecoder.reset();

            for (;;) {
                final CoderResult result = this.chardecoder.decode(
                        buffer(),
                        this.charbuffer,
                        true);
                if (result.isError()) {
                    result.throwException();
                }
                if (result.isOverflow()) {
                    this.charbuffer.flip();
                    linebuffer.append(
                            this.charbuffer.array(),
                            this.charbuffer.position(),
                            this.charbuffer.remaining());
                    this.charbuffer.clear();
                }
                if (result.isUnderflow()) {
                    break;
                }
            }

            // flush the decoder
            this.chardecoder.flush(this.charbuffer);
            this.charbuffer.flip();
            // append the decoded content to the line buffer
            if (this.charbuffer.hasRemaining()) {
                linebuffer.append(
                        this.charbuffer.array(),
                        this.charbuffer.position(),
                        this.charbuffer.remaining());
            }

        }
        buffer().limit(origLimit);

        // discard LF if found
        int l = linebuffer.length();
        if (l > 0) {
            if (linebuffer.charAt(l - 1) == Chars.LF) {
                l--;
                linebuffer.setLength(l);
            }
            // discard CR if found
            if (l > 0 && linebuffer.charAt(l - 1) == Chars.CR) {
                l--;
                linebuffer.setLength(l);
            }
        }
        return true;
    }

}
