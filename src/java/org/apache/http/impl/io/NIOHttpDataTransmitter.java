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
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;

import org.apache.http.io.HttpDataTransmitter;
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
public abstract class NIOHttpDataTransmitter implements HttpDataTransmitter {

    private static final int CR = 13;
    private static final int LF = 10;
    private static final byte[] CRLF = new byte[] {CR, LF};

    private ByteBuffer buffer = null;

    private Charset charset = null;

    protected void initBuffer(int buffersize) {
        if (buffersize < 2048) {
            buffersize = 2048;
        }
        this.buffer = ByteBuffer.allocateDirect(buffersize);
        this.charset = Charset.forName("US-ASCII");
    }
    
    public void reset(final HttpParams params) {
        this.charset = Charset.forName(HttpProtocolParams.getHttpElementCharset(params)); 
    }

    protected abstract void writeToChannel(ByteBuffer src) throws IOException;
    
    protected void flushBuffer() throws IOException {
        this.buffer.flip();
        writeToChannel(this.buffer);
        this.buffer.compact();
    }
    
    public void flush() throws IOException {
        this.buffer.flip();
        while (this.buffer.hasRemaining()) {
            writeToChannel(this.buffer);
        }
        this.buffer.clear();
    }
    
    private CharsetEncoder createCharEncoder() {
        CharsetEncoder charencoder = this.charset.newEncoder();
        charencoder.onMalformedInput(CodingErrorAction.REPLACE); 
        charencoder.onUnmappableCharacter(CodingErrorAction.REPLACE); 
        return charencoder; 
    }
    
    private void write(
            final CharsetEncoder charencoder, 
            final CharBuffer charbuffer, 
            boolean endOfInput) throws IOException {
        boolean retry = true;
        while (retry) {
            CoderResult result = charencoder.encode(charbuffer, this.buffer, endOfInput);
            if (result.isOverflow()) {
                flushBuffer();
            }
            retry = !result.isUnderflow();
        }
    }

    public void write(final byte[] b, int off, int len) throws IOException {
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
                flushBuffer();
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

    public void write(int b) throws IOException {
        if (!this.buffer.hasRemaining()) {
            flushBuffer();
        }
        this.buffer.put((byte)b);
    }

    private void flushCharEncoder(final CharsetEncoder charencoder) throws IOException {
        boolean retry = true;
        while (retry) {
            CoderResult result = charencoder.flush(this.buffer);
            if (result.isOverflow()) {
                flushBuffer();
            }
            retry = !result.isUnderflow();
        }
    }
    
    public void writeLine(final String s) throws IOException {
        if (s == null) {
            return;
        }
        // Do not bother if the string is empty
        if (s.length() > 0 ) {
            CharsetEncoder charencoder = createCharEncoder();
            CharBuffer tmp = CharBuffer.allocate(128);
            // transfer the string in small chunks
            int remaining = s.length();
            int offset = 0;
            while (remaining > 0) {
                int l = tmp.remaining();
                boolean eol = false;
                if (remaining < l) {
                    l = remaining;
                    // terminate the encoding process
                    eol = true;
                }
                tmp.put(s, offset, offset + l);
                tmp.flip();
                write(charencoder, tmp, eol);
                tmp.compact();
                offset += l;
                remaining -= l;
            }
            // flush the encoder
            flushCharEncoder(charencoder);
        }
        writeCRLF();
    }

}
