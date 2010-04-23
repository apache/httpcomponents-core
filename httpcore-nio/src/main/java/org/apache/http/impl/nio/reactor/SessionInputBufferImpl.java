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

import org.apache.http.nio.reactor.SessionInputBuffer;
import org.apache.http.nio.util.ByteBufferAllocator;
import org.apache.http.nio.util.ExpandableBuffer;
import org.apache.http.nio.util.HeapByteBufferAllocator;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.CharArrayBuffer;

/**
 * Default implementation of {@link SessionInputBuffer} based on
 * the {@link ExpandableBuffer} class.
 * <p>
 * The following parameters can be used to customize the behavior of this
 * class:
 * <ul>
 *  <li>{@link org.apache.http.params.CoreProtocolPNames#HTTP_ELEMENT_CHARSET}</li>
 * </ul>
 *
 * @since 4.0
 */
public class SessionInputBufferImpl extends ExpandableBuffer implements SessionInputBuffer {

    private CharBuffer charbuffer = null;
    private Charset charset = null;
    private CharsetDecoder chardecoder = null;

    public SessionInputBufferImpl(
            int buffersize,
            int linebuffersize,
            final ByteBufferAllocator allocator,
            final HttpParams params) {
        super(buffersize, allocator);
        this.charbuffer = CharBuffer.allocate(linebuffersize);
        this.charset = Charset.forName(HttpProtocolParams.getHttpElementCharset(params));
        this.chardecoder = this.charset.newDecoder();
    }

    public SessionInputBufferImpl(
            int buffersize,
            int linebuffersize,
            final HttpParams params) {
        this(buffersize, linebuffersize, new HeapByteBufferAllocator(), params);
    }

    public int fill(final ReadableByteChannel channel) throws IOException {
        if (channel == null) {
            throw new IllegalArgumentException("Channel may not be null");
        }
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
