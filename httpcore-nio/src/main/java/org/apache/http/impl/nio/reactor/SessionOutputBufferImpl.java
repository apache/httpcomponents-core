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
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;

import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.nio.reactor.SessionOutputBuffer;
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
 * Default implementation of {@link SessionOutputBuffer} based on
 * the {@link ExpandableBuffer} class.
 *
 * @since 4.0
 */
@SuppressWarnings("deprecation")
@NotThreadSafe
public class SessionOutputBufferImpl extends ExpandableBuffer implements SessionOutputBuffer {

    private static final byte[] CRLF = new byte[] {HTTP.CR, HTTP.LF};

    private final CharsetEncoder charencoder;
    private final int lineBuffersize;

    private CharBuffer charbuffer;

    /**
     *  Creates SessionOutputBufferImpl instance.
     *
     * @param buffersize input buffer size
     * @param lineBuffersize buffer size for line operations. Has effect only if
     *   {@code charencoder} is not {@code null}.
     * @param charencoder charencoder to be used for encoding HTTP protocol elements.
     *   If {@code null} simple type cast will be used for char to byte conversion.
     * @param allocator memory allocator.
     *   If {@code null} {@link HeapByteBufferAllocator#INSTANCE} will be used.
     *
     * @since 4.3
     */
    public SessionOutputBufferImpl(
            final int buffersize,
            final int lineBuffersize,
            final CharsetEncoder charencoder,
            final ByteBufferAllocator allocator) {
        super(buffersize, allocator != null ? allocator : HeapByteBufferAllocator.INSTANCE);
        this.lineBuffersize = Args.positive(lineBuffersize, "Line buffer size");
        this.charencoder = charencoder;
    }

    /**
     * @deprecated (4.3) use
     *   {@link SessionOutputBufferImpl#SessionOutputBufferImpl(int, int, CharsetEncoder,
     *     ByteBufferAllocator)}
     */
    @Deprecated
    public SessionOutputBufferImpl(
            final int buffersize,
            final int lineBuffersize,
            final ByteBufferAllocator allocator,
            final HttpParams params) {
        super(buffersize, allocator);
        this.lineBuffersize = Args.positive(lineBuffersize, "Line buffer size");
        final String charsetName = (String) params.getParameter(CoreProtocolPNames.HTTP_ELEMENT_CHARSET);
        final Charset charset = CharsetUtils.lookup(charsetName);
        if (charset != null) {
            this.charencoder = charset.newEncoder();
            final CodingErrorAction a1 = (CodingErrorAction) params.getParameter(
                    CoreProtocolPNames.HTTP_MALFORMED_INPUT_ACTION);
            this.charencoder.onMalformedInput(a1 != null ? a1 : CodingErrorAction.REPORT);
            final CodingErrorAction a2 = (CodingErrorAction) params.getParameter(
                    CoreProtocolPNames.HTTP_UNMAPPABLE_INPUT_ACTION);
            this.charencoder.onUnmappableCharacter(a2 != null? a2 : CodingErrorAction.REPORT);
        } else {
            this.charencoder = null;
        }
    }

    /**
     * @deprecated (4.3) use
     *   {@link SessionOutputBufferImpl#SessionOutputBufferImpl(int, int, Charset)}
     */
    @Deprecated
    public SessionOutputBufferImpl(
            final int buffersize,
            final int linebuffersize,
            final HttpParams params) {
        this(buffersize, linebuffersize, HeapByteBufferAllocator.INSTANCE, params);
    }

    /**
     * @since 4.3
     */
    public SessionOutputBufferImpl(final int buffersize) {
        this(buffersize, 256, null, HeapByteBufferAllocator.INSTANCE);
    }

    /**
     * @since 4.3
     */
    public SessionOutputBufferImpl(
            final int buffersize,
            final int linebuffersize,
            final Charset charset) {
        this(buffersize, linebuffersize,
                charset != null ? charset.newEncoder() : null, HeapByteBufferAllocator.INSTANCE);
    }

    /**
     * @since 4.3
     */
    public SessionOutputBufferImpl(
            final int buffersize,
            final int linebuffersize) {
        this(buffersize, linebuffersize, null, HeapByteBufferAllocator.INSTANCE);
    }

    public void reset(final HttpParams params) {
        clear();
    }

    @Override
    public int flush(final WritableByteChannel channel) throws IOException {
        Args.notNull(channel, "Channel");
        setOutputMode();
        return channel.write(this.buffer);
    }

    @Override
    public void write(final ByteBuffer src) {
        if (src == null) {
            return;
        }
        setInputMode();
        final int requiredCapacity = this.buffer.position() + src.remaining();
        ensureCapacity(requiredCapacity);
        this.buffer.put(src);
    }

    @Override
    public void write(final ReadableByteChannel src) throws IOException {
        if (src == null) {
            return;
        }
        setInputMode();
        src.read(this.buffer);
    }

    private void write(final byte[] b) {
        if (b == null) {
            return;
        }
        setInputMode();
        final int off = 0;
        final int len = b.length;
        final int requiredCapacity = this.buffer.position() + len;
        ensureCapacity(requiredCapacity);
        this.buffer.put(b, off, len);
    }

    private void writeCRLF() {
        write(CRLF);
    }

    @Override
    public void writeLine(final CharArrayBuffer linebuffer) throws CharacterCodingException {
        if (linebuffer == null) {
            return;
        }
        setInputMode();
        // Do not bother if the buffer is empty
        if (linebuffer.length() > 0 ) {
            if (this.charencoder == null) {
                final int requiredCapacity = this.buffer.position() + linebuffer.length();
                ensureCapacity(requiredCapacity);
                if (this.buffer.hasArray()) {
                    final byte[] b = this.buffer.array();
                    final int len = linebuffer.length();
                    final int off = this.buffer.position();
                    for (int i = 0; i < len; i++) {
                        b[off + i]  = (byte) linebuffer.charAt(i);
                    }
                    this.buffer.position(off + len);
                } else {
                    for (int i = 0; i < linebuffer.length(); i++) {
                        this.buffer.put((byte) linebuffer.charAt(i));
                    }
                }
            } else {
                if (this.charbuffer == null) {
                    this.charbuffer = CharBuffer.allocate(this.lineBuffersize);
                }
                this.charencoder.reset();
                // transfer the string in small chunks
                int remaining = linebuffer.length();
                int offset = 0;
                while (remaining > 0) {
                    int l = this.charbuffer.remaining();
                    boolean eol = false;
                    if (remaining <= l) {
                        l = remaining;
                        // terminate the encoding process
                        eol = true;
                    }
                    this.charbuffer.put(linebuffer.buffer(), offset, l);
                    this.charbuffer.flip();

                    boolean retry = true;
                    while (retry) {
                        final CoderResult result = this.charencoder.encode(this.charbuffer, this.buffer, eol);
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
                    final CoderResult result = this.charencoder.flush(this.buffer);
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

    @Override
    public void writeLine(final String s) throws IOException {
        if (s == null) {
            return;
        }
        if (s.length() > 0) {
            final CharArrayBuffer tmp = new CharArrayBuffer(s.length());
            tmp.append(s);
            writeLine(tmp);
        } else {
            write(CRLF);
        }
    }

}
