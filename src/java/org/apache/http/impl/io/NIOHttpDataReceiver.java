/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 *
 *  Copyright 1999-2004 The Apache Software Foundation
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

package org.apache.http.impl.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;

import org.apache.http.io.HttpDataReceiver;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;

/**
 * <p>
 * </p>
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 * 
 * @since 4.0
 */
public abstract class NIOHttpDataReceiver implements HttpDataReceiver {

    private static final int CR = 13;
    private static final int LF = 10;
    
    private ByteBuffer buffer = null;
    
    private Charset charset = null;
    private CharsetDecoder chardecoder = null;
    private CharBuffer chbuffer = null;
    
    protected void initBuffer(int buffersize) {
        this.buffer = ByteBuffer.allocateDirect(buffersize);
        this.buffer.flip();
        this.charset = Charset.forName("US-ASCII");
        this.chardecoder = createCharDecoder();
        this.chbuffer = CharBuffer.allocate(1024);
    }

    public void reset(final HttpParams params) {
        this.charset = Charset.forName(HttpProtocolParams.getHttpElementCharset(params)); 
        this.chardecoder = createCharDecoder();
    }

    private CharsetDecoder createCharDecoder() {
        CharsetDecoder chardecoder = this.charset.newDecoder();
        chardecoder.onMalformedInput(CodingErrorAction.REPLACE); 
        chardecoder.onUnmappableCharacter(CodingErrorAction.REPLACE);
        return chardecoder;
    }
    
    protected abstract int readFromChannel(ByteBuffer dst) throws IOException;
    
    protected int fillBuffer() throws IOException {
        this.buffer.compact();
        int i = readFromChannel(this.buffer);
        this.buffer.flip();
        return i;
    }

    protected boolean hasDataInBuffer() {
        return this.buffer.hasRemaining();
    }
    
    public int read(final byte[] b, int off, int len) throws IOException {
        if (b == null) {
            return 0;
        }
        int noRead = 0;
        if (!this.buffer.hasRemaining()) {
            noRead = fillBuffer();
            if (noRead == -1) {
                return -1; 
            }
        }
        int chunk = len;
        if (chunk > this.buffer.remaining()) {
            chunk = this.buffer.remaining();
        }
        this.buffer.get(b, off, chunk);
        return chunk;
    }
    
    public int read(final byte[] b) throws IOException {
        if (b == null) {
            return 0;
        }
        return read(b, 0, b.length);
    }
    
    public int read() throws IOException {
        int noRead = 0;
        if (!this.buffer.hasRemaining()) {
            noRead = fillBuffer();
            if (noRead == -1) {
                return -1; 
            }
        }
        int b = this.buffer.get();
        if (b < 0) {
            b = 256 + b;
        }
        return b;
    }
    
    private int locateLF() {
        for (int i = this.buffer.position(); i < this.buffer.limit(); i++) {
            int b = this.buffer.get(i);
            if (b == LF) {
                return i;
            }
        }
        return -1;
    }
    
    public String readLine() throws IOException {
        int noRead = 0;
        this.chbuffer.clear();
        this.chardecoder.reset();
        StringBuffer line = new StringBuffer(); 
        boolean retry = true;
        while (retry) {
            // attempt to find end of line (LF)
            int i = locateLF(); 
            if (i != -1) {
                // end of line found. 
                retry = false;
                // read up to the end of line
                int origLimit = this.buffer.limit();
                this.buffer.limit(i + 1);
                for (;;) {
                    CoderResult result = this.chardecoder.decode(
                    		this.buffer, this.chbuffer, true);
                    if (result.isOverflow()) {
                        this.chbuffer.flip();
                        line.append(this.chbuffer.array(), 
                        		this.chbuffer.position(), this.chbuffer.remaining());
                        this.chbuffer.clear();
                    }
                    if (result.isUnderflow()) {
                        break;
                    }
                }
                this.buffer.limit(origLimit);
            } else {
                // end of line not found
                if (this.buffer.hasRemaining()) {
                    // decode the entire buffer content
                    this.chardecoder.decode(this.buffer, this.chbuffer, false);
                }
                // discard the decoded content
                noRead = fillBuffer();
                if (noRead == -1) {
                    retry = false;
                    // terminate the decoding process
                    this.chardecoder.decode(this.buffer, this.chbuffer, true);
                }
            }
            // append the decoded content to the line buffer
            this.chbuffer.flip();
            if (this.chbuffer.hasRemaining()) {
                line.append(this.chbuffer.array(), 
                		this.chbuffer.position(), this.chbuffer.remaining());
            }
            this.chbuffer.clear();
        }
        // flush the decoder
        this.chardecoder.flush(this.chbuffer);
        this.chbuffer.flip();
        // append the decoded content to the line buffer
        if (this.chbuffer.hasRemaining()) {
            line.append(this.chbuffer.array(), 
            		this.chbuffer.position(), this.chbuffer.remaining());
        }
        if (noRead == -1 && line.length() == 0) {
            // indicate the end of stream
            return null;
        }
        // discard LF if found
        int l = line.length(); 
        if (l > 0) {
            if (line.charAt(l - 1) == LF) {
                l--;
                line.setLength(l);
            }
            // discard CR if found
            if (l > 0) {
                if (line.charAt(l - 1) == CR) {
                    l--;
                    line.setLength(l);
                }
            }
        }
        return line.toString();
    }
    
}
