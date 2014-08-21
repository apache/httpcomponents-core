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

package org.apache.http.impl.nio.reactor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;

import org.apache.http.MessageConstraintException;
import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.config.MessageConstraints;
import org.apache.http.nio.reactor.SessionInputBuffer;
import org.apache.http.nio.util.ByteBufferAllocator;
import org.apache.http.nio.util.ExpandableBuffer;
import org.apache.http.nio.util.HeapByteBufferAllocator;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.Args;
import org.apache.http.util.CharArrayBuffer;
import org.apache.http.util.CharsetUtils;

/**
 * Default implementation of {@link SessionInputBuffer} based on
 * the {@link ExpandableBuffer} class.
 *
 * @since 4.0
 */
@SuppressWarnings("deprecation")
@NotThreadSafe
public class SessionInputBufferImpl extends ExpandableBuffer implements SessionInputBuffer {

    private final CharsetDecoder chardecoder;
    private final MessageConstraints constraints;
    private final int lineBuffersize;

    private CharBuffer charbuffer;

    /**
     *  Creates SessionInputBufferImpl instance.
     *
     * @param buffersize input buffer size
     * @param lineBuffersize buffer size for line operations. Has effect only if
     *   {@code chardecoder} is not {@code null}.
     * @param chardecoder chardecoder to be used for decoding HTTP protocol elements.
     *   If {@code null} simple type cast will be used for byte to char conversion.
     * @param constraints Message constraints. If {@code null}
     *   {@link MessageConstraints#DEFAULT} will be used.
     * @param allocator memory allocator.
     *   If {@code null} {@link HeapByteBufferAllocator#INSTANCE} will be used.
     *
     * @since 4.4
     */
    public SessionInputBufferImpl(
            final int buffersize,
            final int lineBuffersize,
            final MessageConstraints constraints,
            final CharsetDecoder chardecoder,
            final ByteBufferAllocator allocator) {
        super(buffersize, allocator != null ? allocator : HeapByteBufferAllocator.INSTANCE);
        this.lineBuffersize = Args.positive(lineBuffersize, "Line buffer size");
        this.constraints = constraints != null ? constraints : MessageConstraints.DEFAULT;
        this.chardecoder = chardecoder;
    }

    /**
     *  Creates SessionInputBufferImpl instance.
     *
     * @param buffersize input buffer size
     * @param lineBuffersize buffer size for line operations. Has effect only if
     *   {@code chardecoder} is not {@code null}.
     * @param chardecoder chardecoder to be used for decoding HTTP protocol elements.
     *   If {@code null} simple type cast will be used for byte to char conversion.
     * @param allocator memory allocator.
     *   If {@code null} {@link HeapByteBufferAllocator#INSTANCE} will be used.
     *
     * @since 4.3
     */
    public SessionInputBufferImpl(
            final int buffersize,
            final int lineBuffersize,
            final CharsetDecoder chardecoder,
            final ByteBufferAllocator allocator) {
        this(buffersize, lineBuffersize, null, chardecoder, allocator);
    }

    /**
     * @deprecated (4.3) use
     *   {@link SessionInputBufferImpl#SessionInputBufferImpl(int, int, CharsetDecoder,
     *     ByteBufferAllocator)}
     */
    @Deprecated
    public SessionInputBufferImpl(
            final int buffersize,
            final int lineBuffersize,
            final ByteBufferAllocator allocator,
            final HttpParams params) {
        super(buffersize, allocator);
        this.lineBuffersize = Args.positive(lineBuffersize, "Line buffer size");
        final String charsetName = (String) params.getParameter(CoreProtocolPNames.HTTP_ELEMENT_CHARSET);
        final Charset charset = CharsetUtils.lookup(charsetName);
        if (charset != null) {
            this.chardecoder = charset.newDecoder();
            final CodingErrorAction a1 = (CodingErrorAction) params.getParameter(
                    CoreProtocolPNames.HTTP_MALFORMED_INPUT_ACTION);
            this.chardecoder.onMalformedInput(a1 != null ? a1 : CodingErrorAction.REPORT);
            final CodingErrorAction a2 = (CodingErrorAction) params.getParameter(
                    CoreProtocolPNames.HTTP_UNMAPPABLE_INPUT_ACTION);
            this.chardecoder.onUnmappableCharacter(a2 != null? a2 : CodingErrorAction.REPORT);
        } else {
            this.chardecoder = null;
        }
        this.constraints = MessageConstraints.DEFAULT;
    }

    /**
     * @deprecated (4.3) use
     *   {@link SessionInputBufferImpl#SessionInputBufferImpl(int, int, Charset)}
     */
    @Deprecated
    public SessionInputBufferImpl(
            final int buffersize,
            final int linebuffersize,
            final HttpParams params) {
        this(buffersize, linebuffersize, HeapByteBufferAllocator.INSTANCE, params);
    }

    /**
     * @since 4.3
     */
    public SessionInputBufferImpl(
            final int buffersize,
            final int lineBuffersize,
            final Charset charset) {
        this(buffersize, lineBuffersize, null,
                charset != null ? charset.newDecoder() : null, HeapByteBufferAllocator.INSTANCE);
    }

    /**
     * @since 4.3
     */
    public SessionInputBufferImpl(
            final int buffersize,
            final int lineBuffersize,
            final MessageConstraints constraints,
            final Charset charset) {
        this(buffersize, lineBuffersize, constraints,
                charset != null ? charset.newDecoder() : null, HeapByteBufferAllocator.INSTANCE);
    }

    /**
     * @since 4.3
     */
    public SessionInputBufferImpl(
            final int buffersize,
            final int lineBuffersize) {
        this(buffersize, lineBuffersize, null, null, HeapByteBufferAllocator.INSTANCE);
    }

    /**
     * @since 4.3
     */
    public SessionInputBufferImpl(final int buffersize) {
        this(buffersize, 256, null, null, HeapByteBufferAllocator.INSTANCE);
    }

    @Override
    public int fill(final ReadableByteChannel channel) throws IOException {
        Args.notNull(channel, "Channel");
        setInputMode();
        if (!this.buffer.hasRemaining()) {
            expand();
        }
        return channel.read(this.buffer);
    }

    @Override
    public int read() {
        setOutputMode();
        return this.buffer.get() & 0xff;
    }

    @Override
    public int read(final ByteBuffer dst, final int maxLen) {
        if (dst == null) {
            return 0;
        }
        setOutputMode();
        final int len = Math.min(dst.remaining(), maxLen);
        final int chunk = Math.min(this.buffer.remaining(), len);
        if (this.buffer.remaining() > chunk) {
            final int oldLimit = this.buffer.limit();
            final int newLimit = this.buffer.position() + chunk;
            this.buffer.limit(newLimit);
            dst.put(this.buffer);
            this.buffer.limit(oldLimit);
            return len;
        } else {
            dst.put(this.buffer);
        }
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
        if (this.buffer.remaining() > maxLen) {
            final int oldLimit = this.buffer.limit();
            final int newLimit = oldLimit - (this.buffer.remaining() - maxLen);
            this.buffer.limit(newLimit);
            bytesRead = dst.write(this.buffer);
            this.buffer.limit(oldLimit);
        } else {
            bytesRead = dst.write(this.buffer);
        }
        return bytesRead;
    }

    @Override
    public int read(final WritableByteChannel dst) throws IOException {
        if (dst == null) {
            return 0;
        }
        setOutputMode();
        return dst.write(this.buffer);
    }

    @Override
    public boolean readLine(
            final CharArrayBuffer linebuffer,
            final boolean endOfStream) throws CharacterCodingException {

        setOutputMode();
        // See if there is LF char present in the buffer
        int pos = -1;
        for (int i = this.buffer.position(); i < this.buffer.limit(); i++) {
            final int b = this.buffer.get(i);
            if (b == HTTP.LF) {
                pos = i + 1;
                break;
            }
        }

        final int maxLineLen = this.constraints.getMaxLineLength();
        if (maxLineLen > 0) {
            final int currentLen = (pos > 0 ? pos : this.buffer.limit()) - this.buffer.position();
            if (currentLen >= maxLineLen) {
                throw new MessageConstraintException("Maximum line length limit exceeded");
            }
        }

        if (pos == -1) {
            if (endOfStream && this.buffer.hasRemaining()) {
                // No more data. Get the rest
                pos = this.buffer.limit();
            } else {
                // Either no complete line present in the buffer
                // or no more data is expected
                return false;
            }
        }
        final int origLimit = this.buffer.limit();
        this.buffer.limit(pos);

        final int requiredCapacity = this.buffer.limit() - this.buffer.position();
        // Ensure capacity of len assuming ASCII as the most likely charset
        linebuffer.ensureCapacity(requiredCapacity);

        if (this.chardecoder == null) {
            if (this.buffer.hasArray()) {
                final byte[] b = this.buffer.array();
                final int off = this.buffer.position();
                final int len = this.buffer.remaining();
                linebuffer.append(b, off, len);
                this.buffer.position(off + len);
            } else {
                while (this.buffer.hasRemaining()) {
                    linebuffer.append((char) (this.buffer.get() & 0xff));
                }
            }
        } else {
            if (this.charbuffer == null) {
                this.charbuffer = CharBuffer.allocate(this.lineBuffersize);
            }
            this.chardecoder.reset();

            for (;;) {
                final CoderResult result = this.chardecoder.decode(
                        this.buffer,
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
        this.buffer.limit(origLimit);

        // discard LF if found
        int l = linebuffer.length();
        if (l > 0) {
            if (linebuffer.charAt(l - 1) == HTTP.LF) {
                l--;
                linebuffer.setLength(l);
            }
            // discard CR if found
            if (l > 0) {
                if (linebuffer.charAt(l - 1) == HTTP.CR) {
                    l--;
                    linebuffer.setLength(l);
                }
            }
        }
        return true;
    }

    @Override
    public String readLine(final boolean endOfStream) throws CharacterCodingException {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        final boolean found = readLine(buffer, endOfStream);
        if (found) {
            return buffer.toString();
        } else {
            return null;
        }
    }

}
