/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 *
 *  Copyright 2002-2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
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
import java.io.InputStream;

import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.util.ExceptionUtils;
import org.apache.http.util.NumUtils;

/**
 * <p>This class implements chunked transfer coding as described in the 
 * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.6">Section 3.6.1</a> 
 * of <a href="http://www.w3.org/Protocols/rfc2616/rfc2616.txt">RFC 2616</a>. 
 * It transparently coalesces chunks of a HTTP stream that uses chunked transfer coding.</p>
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
 * <p>
 * Note that this class NEVER closes the underlying stream, even when close
 * gets called.  Instead, it will read until the "end" of its chunking on close,
 * which allows for the seamless invocation of subsequent HTTP 1.1 calls, while
 * not requiring the client to remember to read the entire contents of the
 * response.
 * </p>
 *
 * @author Ortwin Glueck
 * @author Sean C. Sullivan
 * @author Martin Elwin
 * @author Eric Johnson
 * @author <a href="mailto:mbowler@GargoyleSoftware.com">Mike Bowler</a>
 * @author Michael Becke
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @since 2.0
 *
 */
public class ChunkedInputStream extends InputStream {

    /** The data receiver that we're wrapping */
    private HttpDataReceiver in;

    private final CharArrayBuffer buffer;
    
    /** The chunk size */
    private int chunkSize;

    /** The current position within the current chunk */
    private int pos;

    /** True if we'are at the beginning of stream */
    private boolean bof = true;

    /** True if we've reached the end of stream */
    private boolean eof = false;

    /** True if this stream is closed */
    private boolean closed = false;
    
    private Header[] footers = new Header[] {};

    public ChunkedInputStream(final HttpDataReceiver in) {
        super();
    	if (in == null) {
    		throw new IllegalArgumentException("InputStream parameter may not be null");
    	}
        this.in = in;
        this.pos = 0;
        this.buffer = new CharArrayBuffer(16);
    }

    /**
     * <p> Returns all the data in a chunked stream in coalesced form. A chunk
     * is followed by a CRLF. The method returns -1 as soon as a chunksize of 0
     * is detected.</p>
     * 
     * <p> Trailer headers are read automcatically at the end of the stream and
     * can be obtained with the getResponseFooters() method.</p>
     *
     * @return -1 of the end of the stream has been reached or the next data
     * byte
     * @throws IOException If an IO problem occurs
     * 
     * @see HttpMethod#getResponseFooters()
     */
    public int read() throws IOException {
        if (this.closed) {
            throw new IOException("Attempted read from closed stream.");
        }
        if (this.eof) {
            return -1;
        } 
        if (this.pos >= this.chunkSize) {
            nextChunk();
            if (this.eof) { 
                return -1;
            }
        }
        pos++;
        return in.read();
    }

    /**
     * Read some bytes from the stream.
     * @param b The byte array that will hold the contents from the stream.
     * @param off The offset into the byte array at which bytes will start to be
     * placed.
     * @param len the maximum number of bytes that can be returned.
     * @return The number of bytes returned or -1 if the end of stream has been
     * reached.
     * @see java.io.InputStream#read(byte[], int, int)
     * @throws IOException if an IO problem occurs.
     */
    public int read (byte[] b, int off, int len) throws IOException {

        if (closed) {
            throw new IOException("Attempted read from closed stream.");
        }

        if (eof) { 
            return -1;
        }
        if (pos >= chunkSize) {
            nextChunk();
            if (eof) { 
                return -1;
            }
        }
        len = Math.min(len, chunkSize - pos);
        int count = in.read(b, off, len);
        pos += count;
        return count;
    }

    /**
     * Read some bytes from the stream.
     * @param b The byte array that will hold the contents from the stream.
     * @return The number of bytes returned or -1 if the end of stream has been
     * reached.
     * @see java.io.InputStream#read(byte[])
     * @throws IOException if an IO problem occurs.
     */
    public int read (byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    /**
     * Read the next chunk.
     * @throws IOException If an IO error occurs.
     */
    private void nextChunk() throws IOException {
        chunkSize = getChunkSize();
        bof = false;
        pos = 0;
        if (chunkSize == 0) {
            eof = true;
            parseTrailerHeaders();
        }
    }

    /**
     * Expects the stream to start with a chunksize in hex with optional
     * comments after a semicolon. The line must end with a CRLF: "a3; some
     * comment\r\n" Positions the stream at the start of the next line.
     *
     * @param in The new input stream.
     * @param required <tt>true<tt/> if a valid chunk must be present,
     *                 <tt>false<tt/> otherwise.
     * 
     * @return the chunk size as integer
     * 
     * @throws IOException when the chunk size could not be parsed
     */
    private int getChunkSize() throws IOException {
    	// skip CRLF
    	if (!bof) {
            int cr = in.read();
            int lf = in.read();
            if ((cr != '\r') || (lf != '\n')) { 
                throw new MalformedChunkCodingException(
                    "CRLF expected at end of chunk");
            }
        }
        //parse data
        this.buffer.clear();
        int i = this.in.readLine(this.buffer);
        if (i == -1) {
            throw new MalformedChunkCodingException(
            		"Chunked stream ended unexpectedly");
        }
        int separator = this.buffer.indexOf(';');
        if (separator < 0) {
            separator = this.buffer.length();
        }
        try {
            return NumUtils.parseUnsignedHexInt(this.buffer, 0, separator);
        } catch (NumberFormatException e) {
            throw new MalformedChunkCodingException("Bad chunk header");
        }
    }

    /**
     * Reads and stores the Trailer headers.
     * @throws IOException If an IO problem occurs
     */
    private void parseTrailerHeaders() throws IOException {
        try {
            this.footers = Header.parseAll(in);
        } catch (HttpException e) {
            IOException ioe = new MalformedChunkCodingException("Invalid footer: " 
                    + e.getMessage());
            ExceptionUtils.initCause(ioe, e); 
            throw ioe;
        }
    }

    /**
     * Upon close, this reads the remainder of the chunked message,
     * leaving the underlying socket at a position to start reading the
     * next response without scanning.
     * @throws IOException If an IO problem occurs.
     */
    public void close() throws IOException {
        if (!closed) {
            try {
                if (!eof) {
                    exhaustInputStream(this);
                }
            } finally {
                eof = true;
                closed = true;
            }
        }
    }

    public Header[] getFooters() {
        return this.footers;
    }
    
    /**
     * Exhaust an input stream, reading until EOF has been encountered.
     *
     * <p>Note that this function is intended as a non-public utility.
     * This is a little weird, but it seemed silly to make a utility
     * class for this one function, so instead it is just static and
     * shared that way.</p>
     *
     * @param inStream The {@link InputStream} to exhaust.
     * @throws IOException If an IO problem occurs
     */
    static void exhaustInputStream(final InputStream inStream) throws IOException {
        // read and discard the remainder of the message
        byte buffer[] = new byte[1024];
        while (inStream.read(buffer) >= 0) {
            ;
        }
    }

}
