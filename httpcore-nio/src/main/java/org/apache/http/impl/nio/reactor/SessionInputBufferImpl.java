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

import org.apache.http.Consts;
import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.nio.reactor.SessionInputBuffer;
import org.apache.http.nio.util.ByteBufferAllocator;
import org.apache.http.nio.util.ExpandableBuffer;
import org.apache.http.nio.util.HeapByteBufferAllocator;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.Args;
import org.apache.http.util.CharArrayBuffer;

/**
 * Default implementation of {@link SessionInputBuffer} based on
 * the {@link ExpandableBuffer} class.
 *
 * @since 4.0
 */
@NotThreadSafe
public class SessionInputBufferImpl extends ExpandableBuffer implements SessionInputBuffer {

    private CharBuffer charbuffer = null;
    private Charset charset = null;
    private CharsetDecoder chardecoder = null;

    /**
     *  Creates SessionInputBufferImpl instance.
     *
     * @param buffersize input buffer size
     * @param linebuffersize buffer size for line operations
     * @param charset charset to be used for decoding HTTP protocol elements.
     *   If <code>null</code> US-ASCII will be used.
     * @param malformedCharAction action to perform upon receiving a malformed input.
     *   If <code>null</code> {@link CodingErrorAction#REPORT} will be used.
     * @param unmappableCharAction action to perform upon receiving an unmappable input.
     *   If <code>null</code> {@link CodingErrorAction#REPORT}  will be used.
     * @param allocator memory allocator.
     *   If <code>null</code> {@link HeapByteBufferAllocator#INSTANCE} will be used.
     *
     * @since 4.3
     */
    public SessionInputBufferImpl(
            int buffersize,
            int linebuffersize,
            final Charset charset,
            final CodingErrorAction malformedCharAction,
            final CodingErrorAction unmappableCharAction,
            final ByteBufferAllocator allocator) {
        super(buffersize, allocator != null ? allocator : HeapByteBufferAllocator.INSTANCE);
        this.charbuffer = CharBuffer.allocate(linebuffersize);
        this.charset = charset != null ? charset : Consts.ASCII;
        this.chardecoder = this.charset.newDecoder();
        this.chardecoder.onMalformedInput(malformedCharAction != null ? malformedCharAction :
            CodingErrorAction.REPORT);
        this.chardecoder.onUnmappableCharacter(unmappableCharAction != null? unmappableCharAction :
            CodingErrorAction.REPORT);
    }

    /**
     * @deprecated (4.3) use
     *   {@link SessionInputBufferImpl#SessionInputBufferImpl(int, int, Charset, CodingErrorAction, CodingErrorAction, ByteBufferAllocator)}
     */
    @Deprecated
    public SessionInputBufferImpl(
            int buffersize,
            int linebuffersize,
            final ByteBufferAllocator allocator,
            final HttpParams params) {
        super(buffersize, allocator);
        this.charbuffer = CharBuffer.allocate(linebuffersize);
        String charset = (String) params.getParameter(CoreProtocolPNames.HTTP_ELEMENT_CHARSET);
        this.charset = charset != null ? Charset.forName(charset) : Consts.ASCII;
        this.chardecoder = this.charset.newDecoder();
        CodingErrorAction a1 = (CodingErrorAction) params.getParameter(
                CoreProtocolPNames.HTTP_MALFORMED_INPUT_ACTION);
        this.chardecoder.onMalformedInput(a1 != null ? a1 : CodingErrorAction.REPORT);
        CodingErrorAction a2 = (CodingErrorAction) params.getParameter(
                CoreProtocolPNames.HTTP_UNMAPPABLE_INPUT_ACTION);
        this.chardecoder.onUnmappableCharacter(a2 != null? a2 : CodingErrorAction.REPORT);
    }

    /**
     * @deprecated (4.3) use
     *   {@link SessionInputBufferImpl#SessionInputBufferImpl(int, int, Charset, CodingErrorAction, CodingErrorAction, ByteBufferAllocator)}
     */
    @Deprecated
    public SessionInputBufferImpl(
            int buffersize,
            int linebuffersize,
            final HttpParams params) {
        this(buffersize, linebuffersize, HeapByteBufferAllocator.INSTANCE, params);
    }

    /**
     * @since 4.3
     */
    public SessionInputBufferImpl(
            int buffersize,
            int linebuffersize) {
        this(buffersize, linebuffersize, null, null, null, null);
    }

    /**
     * @since 4.3
     */
    public SessionInputBufferImpl(
            int buffersize,
            int linebuffersize,
            final Charset charset,
            final CodingErrorAction malformedCharAction,
            final CodingErrorAction unmappableCharAction) {
        this(buffersize, linebuffersize, charset, malformedCharAction, unmappableCharAction, null);
    }

    public int fill(final ReadableByteChannel channel) throws IOException {
        Args.notNull(channel, "Channel");
        setInputMode();
        if (!this.buffer.hasRemaining()) {
            expand();
        }
        int readNo = channel.read(this.buffer);
        return readNo;
    }

    public int read() {
        setOutputMode();
        return this.buffer.get() & 0xff;
    }

    public int read(final ByteBuffer dst, int maxLen) {
        if (dst == null) {
            return 0;
        }
        setOutputMode();
        int len = Math.min(dst.remaining(), maxLen);
        int chunk = Math.min(this.buffer.remaining(), len);
        for (int i = 0; i < chunk; i++) {
            dst.put(this.buffer.get());
        }
        return chunk;
    }

    public int read(final ByteBuffer dst) {
        if (dst == null) {
            return 0;
        }
        return read(dst, dst.remaining());
    }

    public int read(final WritableByteChannel dst, int maxLen) throws IOException {
        if (dst == null) {
            return 0;
        }
        setOutputMode();
        int bytesRead;
        if (this.buffer.remaining() > maxLen) {
            int oldLimit = this.buffer.limit();
            int newLimit = oldLimit - (this.buffer.remaining() - maxLen);
            this.buffer.limit(newLimit);
            bytesRead = dst.write(this.buffer);
            this.buffer.limit(oldLimit);
        } else {
            bytesRead = dst.write(this.buffer);
        }
        return bytesRead;
    }

    public int read(final WritableByteChannel dst) throws IOException {
        if (dst == null) {
            return 0;
        }
        setOutputMode();
        return dst.write(this.buffer);
    }

    public boolean readLine(
            final CharArrayBuffer linebuffer,
            boolean endOfStream) throws CharacterCodingException {

        setOutputMode();
        // See if there is LF char present in the buffer
        int pos = -1;
        boolean hasLine = false;
        for (int i = this.buffer.position(); i < this.buffer.limit(); i++) {
            int b = this.buffer.get(i);
            if (b == HTTP.LF) {
                hasLine = true;
                pos = i + 1;
                break;
            }
        }
        if (!hasLine) {
            if (endOfStream && this.buffer.hasRemaining()) {
                // No more data. Get the rest
                pos = this.buffer.limit();
            } else {
                // Either no complete line present in the buffer
                // or no more data is expected
                return false;
            }
        }
        int origLimit = this.buffer.limit();
        this.buffer.limit(pos);

        int len = this.buffer.limit() - this.buffer.position();
        // Ensure capacity of len assuming ASCII as the most likely charset
        linebuffer.ensureCapacity(len);

        this.chardecoder.reset();

        for (;;) {
            CoderResult result = this.chardecoder.decode(
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
        this.buffer.limit(origLimit);

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

    public String readLine(boolean endOfStream) throws CharacterCodingException {
        CharArrayBuffer charbuffer = new CharArrayBuffer(64);
        boolean found = readLine(charbuffer, endOfStream);
        if (found) {
            return charbuffer.toString();
        } else {
            return null;
        }
    }

}
