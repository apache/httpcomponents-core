/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
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

package org.apache.http.io;

import java.io.IOException;
import java.io.OutputStream;

/**
 * <p>This class implements chunked transfer coding as described in the 
 * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.6">Section 3.6.1</a> 
 * of <a href="http://www.w3.org/Protocols/rfc2616/rfc2616.txt">RFC 2616</a>. 
 * Writes are buffered to an internal buffer (2048 default size). Chunks are guaranteed 
 * to be at least as large as the buffer size (except for the last chunk).</p>
 * 
 * <h>3.6.1 Chunked Transfer Coding</h>
 * <p>
 * The chunked encoding modifies the body of a message in order to transfer it as a series 
 * of chunks, each with its own size indicator, followed by an OPTIONAL trailer containing 
 * entity-header fields. This allows dynamically produced content to be transferred along 
 * with the information necessary for the recipient to verify that it has received the full 
 * message.
 * </p>
 * <pre>
 *  Chunked-Body   = *chunk
 *                   last-chunk
 *                   trailer
 *                   CRLF
 *
 *  chunk          = chunk-size [ chunk-extension ] CRLF
 *                   chunk-data CRLF
 *  chunk-size     = 1*HEX
 *  last-chunk     = 1*("0") [ chunk-extension ] CRLF
 *
 *  chunk-extension= *( ";" chunk-ext-name [ "=" chunk-ext-val ] )
 *  chunk-ext-name = token
 *  chunk-ext-val  = token | quoted-string
 *  chunk-data     = chunk-size(OCTET)
 *  trailer        = *(entity-header CRLF)
 * </pre>
 * <p>
 * The chunk-size field is a string of hex digits indicating the size of the chunk. The 
 * chunked encoding is ended by any chunk whose size is zero, followed by the trailer, 
 * which is terminated by an empty line.
 * </p>
 * <p>
 * The trailer allows the sender to include additional HTTP header fields at the end 
 * of the message. The Trailer header field can be used to indicate which header fields 
 * are included in a trailer (see section 14.40).
 * </p>
 * <p>
 * A server using chunked transfer-coding in a response MUST NOT use the trailer for any 
 * header fields unless at least one of the following is true:
 * </p>
 * <p>
 * a)the request included a TE header field that indicates "trailers" is acceptable in 
 * the transfer-coding of the response, as described in section 14.39; or,
 * </p>
 * <p>
 * b)the server is the origin server for the response, the trailer fields consist entirely 
 * of optional metadata, and the recipient could use the message (in a manner acceptable 
 * to the origin server) without receiving this metadata. In other words, the origin server 
 * is willing to accept the possibility that the trailer fields might be silently discarded 
 * along the path to the client.
 * </p>
 * <p>
 * This requirement prevents an interoperability failure when the message is being received 
 * by an HTTP/1.1 (or later) proxy and forwarded to an HTTP/1.0 recipient. It avoids a 
 * situation where compliance with the protocol would have necessitated a possibly infinite 
 * buffer on the proxy. 
 * </p>
 * 
 * @author Mohammad Rezaei, Goldman, Sachs & Co.
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 */
public class ChunkedOutputStream extends OutputStream {

    // ----------------------------------------------------- Instance Variables
    private final HttpDataTransmitter out;

    private byte[] cache;

    private int cachePosition = 0;

    private boolean wroteLastChunk = false;

    /** True if the stream is closed. */
    private boolean closed = false;
    
    // ----------------------------------------------------------- Constructors
    /**
     * Wraps a stream and chunks the output.
     * @param out the transmitter to wrap
     * @param bufferSize minimum chunk size (excluding last chunk)
     * @throws IOException
     * 
     * @since 3.0
     */
    public ChunkedOutputStream(final HttpDataTransmitter out, int bufferSize)
            throws IOException {
        super();
        this.cache = new byte[bufferSize];
        this.out = out;
    }

    /**
     * Wraps a data transmitter and chunks the output. The default buffer size of 2048 was 
     * chosen because the chunk overhead is less than 0.5%
     * @param datatransmitter the transmitter to wrap
     * @throws IOException
     */
    public ChunkedOutputStream(final HttpDataTransmitter datatransmitter) 
            throws IOException {
        this(datatransmitter, 2048);
    }

    // ----------------------------------------------------------- Internal methods
    /**
     * Writes the cache out onto the underlying stream
     * @throws IOException
     * 
     * @since 3.0
     */
    protected void flushCache() throws IOException {
        if (this.cachePosition > 0) {
            this.out.writeLine(Integer.toHexString(this.cachePosition));
            this.out.write(this.cache, 0, this.cachePosition);
            this.out.writeLine("");
            this.cachePosition = 0;
        }
    }

    /**
     * Writes the cache and bufferToAppend to the underlying stream
     * as one large chunk
     * @param bufferToAppend
     * @param off
     * @param len
     * @throws IOException
     * 
     * @since 3.0
     */
    protected void flushCacheWithAppend(byte bufferToAppend[], int off, int len) throws IOException {
        this.out.writeLine(Integer.toHexString(this.cachePosition + len));
        this.out.write(this.cache, 0, this.cachePosition);
        this.out.write(bufferToAppend, off, len);
        this.out.writeLine("");
        this.cachePosition = 0;
    }

    protected void writeClosingChunk() throws IOException {
        // Write the final chunk.
        this.out.writeLine("0");
        this.out.writeLine("");
    }

    // ----------------------------------------------------------- Public Methods
    /**
     * Must be called to ensure the internal cache is flushed and the closing chunk is written.
     * @throws IOException
     * 
     * @since 3.0
     */
    public void finish() throws IOException {
        if (!this.wroteLastChunk) {
            flushCache();
            writeClosingChunk();
            this.wroteLastChunk = true;
        }
    }

    // -------------------------------------------- OutputStream Methods
    public void write(int b) throws IOException {
        if (this.closed) {
            throw new IOException("Attempted write to closed stream.");
        }
        this.cache[this.cachePosition] = (byte) b;
        this.cachePosition++;
        if (this.cachePosition == this.cache.length) flushCache();
    }

    /**
     * Writes the array. If the array does not fit within the buffer, it is
     * not split, but rather written out as one large chunk.
     * @param b
     * @throws IOException
     * 
     * @since 3.0
     */
    public void write(byte b[]) throws IOException {
        write(b, 0, b.length);
    }

    public void write(byte src[], int off, int len) throws IOException {
        if (this.closed) {
            throw new IOException("Attempted write to closed stream.");
        }
        if (len >= this.cache.length - this.cachePosition) {
            flushCacheWithAppend(src, off, len);
        } else {
            System.arraycopy(src, off, cache, this.cachePosition, len);
            this.cachePosition += len;
        }
    }

    /**
     * Flushes the underlying stream, but leaves the internal buffer alone.
     * @throws IOException
     */
    public void flush() throws IOException {
        this.out.flush();
    }

    /**
     * Finishes writing to the underlying stream, but does NOT close the underlying stream.
     * @throws IOException
     */
    public void close() throws IOException {
        if (!this.closed) {
            this.closed = true;
            finish();
            this.out.flush();
        }
    }
}
