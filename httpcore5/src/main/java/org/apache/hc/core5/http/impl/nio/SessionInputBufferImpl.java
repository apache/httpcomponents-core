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

class SessionInputBufferImpl extends ExpandableBuffer implements SessionInputBuffer {

    private final CharsetDecoder charDecoder;
    private final int lineBuffersize;
    private final int maxLineLen;

    private CharBuffer charbuffer;

    /**
     *  Creates SessionInputBufferImpl instance.
     *
     * @param bufferSize input buffer size
     * @param lineBuffersize buffer size for line operations. Has effect only if
     *   {@code charDecoder} is not {@code null}.
     * @param charDecoder charDecoder to be used for decoding HTTP protocol elements.
     *   If {@code null} simple type cast will be used for byte to char conversion.
     * @param maxLineLen maximum line length.
     *
     * @since 4.4
     */
    public SessionInputBufferImpl(
            final int bufferSize,
            final int lineBuffersize,
            final int maxLineLen,
            final CharsetDecoder charDecoder) {
        super(bufferSize);
        this.lineBuffersize = Args.positive(lineBuffersize, "Line buffer size");
        this.maxLineLen = maxLineLen > 0 ? maxLineLen : 0;
        this.charDecoder = charDecoder;
    }

    /**
     * @since 4.3
     */
    public SessionInputBufferImpl(
            final int bufferSize,
            final int lineBuffersize,
            final int maxLineLen,
            final Charset charset) {
        this(bufferSize, lineBuffersize, maxLineLen, charset != null ? charset.newDecoder() : null);
    }

    /**
     * @since 4.3
     */
    public SessionInputBufferImpl(
            final int bufferSize,
            final int lineBuffersize,
            final int maxLineLen) {
        this(bufferSize, lineBuffersize, maxLineLen, (CharsetDecoder) null);
    }

    /**
     * @since 4.3
     */
    public SessionInputBufferImpl(
            final int bufferSize,
            final int lineBuffersize) {
        this(bufferSize, lineBuffersize, 0, (CharsetDecoder) null);
    }

    /**
     * @since 4.3
     */
    public SessionInputBufferImpl(final int bufferSize) {
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

    public void put(final ByteBuffer src) {
        if (src != null && src.hasRemaining()) {
            setInputMode();
            ensureAdjustedCapacity(buffer().position() + src.remaining());
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
            final CharArrayBuffer lineBuffer,
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
        lineBuffer.ensureCapacity(requiredCapacity);

        if (this.charDecoder == null) {
            if (buffer().hasArray()) {
                final byte[] b = buffer().array();
                final int off = buffer().position();
                final int len = buffer().remaining();
                lineBuffer.append(b, buffer().arrayOffset() + off, len);
                buffer().position(off + len);
            } else {
                while (buffer().hasRemaining()) {
                    lineBuffer.append((char) (buffer().get() & 0xff));
                }
            }
        } else {
            if (this.charbuffer == null) {
                this.charbuffer = CharBuffer.allocate(this.lineBuffersize);
            }
            this.charDecoder.reset();

            for (;;) {
                final CoderResult result = this.charDecoder.decode(
                        buffer(),
                        this.charbuffer,
                        true);
                if (result.isError()) {
                    result.throwException();
                }
                if (result.isOverflow()) {
                    this.charbuffer.flip();
                    lineBuffer.append(
                            this.charbuffer.array(),
                            this.charbuffer.arrayOffset() + this.charbuffer.position(),
                            this.charbuffer.remaining());
                    this.charbuffer.clear();
                }
                if (result.isUnderflow()) {
                    break;
                }
            }

            // flush the decoder
            this.charDecoder.flush(this.charbuffer);
            this.charbuffer.flip();
            // append the decoded content to the line buffer
            if (this.charbuffer.hasRemaining()) {
                lineBuffer.append(
                        this.charbuffer.array(),
                        this.charbuffer.arrayOffset() + this.charbuffer.position(),
                        this.charbuffer.remaining());
            }

        }
        buffer().limit(origLimit);

        // discard LF if found
        int l = lineBuffer.length();
        if (l > 0) {
            if (lineBuffer.charAt(l - 1) == Chars.LF) {
                l--;
                lineBuffer.setLength(l);
            }
            // discard CR if found
            if (l > 0 && lineBuffer.charAt(l - 1) == Chars.CR) {
                l--;
                lineBuffer.setLength(l);
            }
        }
        return true;
    }

}
