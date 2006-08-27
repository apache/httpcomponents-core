/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 *
 *  Copyright 1999-2006 The Apache Software Foundation
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

package org.apache.http.nio.impl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;

import org.apache.http.io.CharArrayBuffer;
import org.apache.http.io.HttpDataTransmitter;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;

/**
 * Abstract data transmitter implementation based on <code>java.nio</code>.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 *
 * <!-- empty lines above to avoid 'svn diff' context problems -->
 * @version $Revision$
 * 
 * @since 4.0
 */
public abstract class NIOHttpDataTransmitter implements HttpDataTransmitter {

    private static final byte[] CRLF = new byte[] {HTTP.CR, HTTP.LF};

    private ByteBuffer buffer = null;

    private Charset charset = null;
    private CharsetEncoder charencoder = null;
    private CharBuffer chbuffer = null;

    protected void initBuffer(int buffersize) {
        this.buffer = ByteBuffer.allocateDirect(buffersize);
        this.charset = Charset.forName("US-ASCII");
        this.charencoder = createCharEncoder();
        this.chbuffer = CharBuffer.allocate(1024);
    }
    
    public synchronized void reset(final HttpParams params) {
        this.charset = Charset.forName(HttpProtocolParams.getHttpElementCharset(params)); 
        this.charencoder = createCharEncoder();
    }

    private void doFlushBuffer() throws IOException {
        this.buffer.flip();
        flushBuffer(this.buffer);
        this.buffer.compact();
    }
    
    protected abstract void flushBuffer(ByteBuffer src) throws IOException;
    
    public synchronized void flush() throws IOException {
        this.buffer.flip();
        while (this.buffer.hasRemaining()) {
            flushBuffer(this.buffer);
        }
        this.buffer.clear();
    }
    
    private CharsetEncoder createCharEncoder() {
        CharsetEncoder charencoder = this.charset.newEncoder();
        charencoder.onMalformedInput(CodingErrorAction.REPLACE); 
        charencoder.onUnmappableCharacter(CodingErrorAction.REPLACE); 
        return charencoder; 
    }
    
    public synchronized void write(final byte[] b, int off, int len) throws IOException {
        if (b == null) {
            return;
        }
        int remaining = len;
        while (remaining > 0) {
            if (this.buffer.hasRemaining()) {
                int chunk = len;
                if (chunk > remaining) {
                    chunk = remaining;
                }
                if (chunk > this.buffer.remaining()) {
                    chunk = this.buffer.remaining();
                }
                this.buffer.put(b, off, chunk);
                off += chunk;
                remaining -= chunk; 
            } else {
                doFlushBuffer();
            }
        }
    }

    public void write(final byte[] b) throws IOException {
        if (b == null) {
            return;
        }
        write(b, 0, b.length);
    }

    private void writeCRLF() throws IOException {
        write(CRLF);
    }

    public synchronized void write(int b) throws IOException {
        if (!this.buffer.hasRemaining()) {
            doFlushBuffer();
        }
        this.buffer.put((byte)b);
    }

    public synchronized void writeLine(final CharArrayBuffer buffer) throws IOException {
        if (buffer == null) {
            return;
        }
        // Do not bother if the buffer is empty
        if (buffer.length() > 0 ) {
        	this.charencoder.reset();
            // transfer the string in small chunks
            int remaining = buffer.length();
            int offset = 0;
            while (remaining > 0) {
                int l = this.chbuffer.remaining();
                boolean eol = false;
                if (remaining < l) {
                    l = remaining;
                    // terminate the encoding process
                    eol = true;
                }
                this.chbuffer.put(buffer.buffer(), offset, offset + l);
                this.chbuffer.flip();
                
                boolean retry = true;
                while (retry) {
                    CoderResult result = this.charencoder.encode(this.chbuffer, this.buffer, eol);
                    if (result.isOverflow()) {
                        doFlushBuffer();
                    }
                    retry = !result.isUnderflow();
                }
                
                this.chbuffer.compact();
                offset += l;
                remaining -= l;
            }
            // flush the encoder
            boolean retry = true;
            while (retry) {
                CoderResult result = this.charencoder.flush(this.buffer);
                if (result.isOverflow()) {
                    doFlushBuffer();
                }
                retry = !result.isUnderflow();
            }
        }
        writeCRLF();
    }

    public void writeLine(final String s) throws IOException {
        if (s == null) {
            return;
        }
        if (s.length() > 0) {
            CharArrayBuffer tmp = new CharArrayBuffer(s.length());
            tmp.append(s);
            writeLine(tmp);
        } else {
            write(CRLF);
        }
    }
    
}
