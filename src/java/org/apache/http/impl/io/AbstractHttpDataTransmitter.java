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

package org.apache.http.impl.io;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.http.io.HttpDataTransmitter;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;

/**
 * <p>Old IO Compatibility wrapper</p>
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 */
public abstract class AbstractHttpDataTransmitter implements HttpDataTransmitter {

    private static final int CR = 13;
    private static final int LF = 10;
    private static final byte[] CRLF = new byte[] {CR, LF};

    private OutputStream outstream;
    private byte[] buffer;
    private int bufferlen;
        
    private String charset = "US-ASCII";
    
    protected void init(final OutputStream outstream, int buffersize) {
        if (outstream == null) {
            throw new IllegalArgumentException("Input stream may not be null");
        }
        if (buffersize <= 0) {
            throw new IllegalArgumentException("Buffer size may not be negative or zero");
        }
        this.outstream = outstream;
        this.buffer = new byte[buffersize];
        this.bufferlen = 0;
    }
    
    protected void flushBuffer() throws IOException {
        if (this.bufferlen > 0) {
            this.outstream.write(this.buffer, 0, this.bufferlen);
            this.bufferlen = 0;
        }
    }
    
    public void flush() throws IOException {
        flushBuffer();
        this.outstream.flush();
    }
    
    public void write(final byte[] b, int off, int len) throws IOException {
        if (b == null) {
            return;
        }
        int freecapacity = this.buffer.length - this.bufferlen;
        if (len > freecapacity) {
            // flush the buffer
            flushBuffer();
            freecapacity = this.buffer.length; 
        }
        if (len > freecapacity) {
            // still does not fit, write directly to the out stream
            this.outstream.write(b, off, len);
        } else {
            // buffer
            System.arraycopy(b, off, this.buffer, this.bufferlen, len);
            this.bufferlen += len;
        }
    }
    
    public void write(final byte[] b) throws IOException {
        if (b == null) {
            return;
        }
        write(b, 0, b.length);
    }
    
    public void write(int b) throws IOException {
        if (this.bufferlen == this.buffer.length) {
            flushBuffer();
        }
        this.buffer[this.bufferlen++] = (byte)b;
    }
    
    public void writeLine(final String s) throws IOException {
        if (s == null) {
            return;
        }
        write(s.getBytes(this.charset));
        write(CRLF);
    }
    
    public void reset(final HttpParams params) {
        this.charset = HttpProtocolParams.getHttpElementCharset(params); 
    }
    
}
