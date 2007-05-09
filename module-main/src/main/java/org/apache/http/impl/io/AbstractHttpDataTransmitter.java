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

package org.apache.http.impl.io;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.http.io.HttpDataTransmitter;
import org.apache.http.io.HttpTransportMetrics;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.ByteArrayBuffer;
import org.apache.http.util.CharArrayBuffer;

/**
 * Abstract base class for data transmitters using traditional IO.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 */
public abstract class AbstractHttpDataTransmitter 
    implements HttpDataTransmitter, HttpTransportMetrics {

    private static final byte[] CRLF = new byte[] {HTTP.CR, HTTP.LF};
    
    private static int MAX_CHUNK = 256;
    
    private OutputStream outstream;
    private ByteArrayBuffer buffer;
        
    private String charset = HTTP.US_ASCII;
    private boolean ascii = true;
    
    private long bytesTransferred = 0;
    
    protected void init(final OutputStream outstream, int buffersize) {
        if (outstream == null) {
            throw new IllegalArgumentException("Input stream may not be null");
        }
        if (buffersize <= 0) {
            throw new IllegalArgumentException("Buffer size may not be negative or zero");
        }
        this.outstream = outstream;
        this.buffer = new ByteArrayBuffer(buffersize);
    }
    
    protected void flushBuffer() throws IOException {
        if (this.buffer.length() > 0) {
            this.outstream.write(this.buffer.buffer(), 0, this.buffer.length());
            this.bytesTransferred += this.buffer.length();
            this.buffer.clear();
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
        // Do not want to buffer largish chunks
        // if the byte array is larger then MAX_CHUNK
        // write it directly to the output stream
        if (len > MAX_CHUNK || len > this.buffer.capacity()) {
            // flush the buffer
            flushBuffer();
            // write directly to the out stream
            this.outstream.write(b, off, len);
            this.bytesTransferred += len;
        } else {
            // Do not let the buffer grow unnecessarily
            int freecapacity = this.buffer.capacity() - this.buffer.length();
            if (len > freecapacity) {
                // flush the buffer
                flushBuffer();
            }
            // buffer
            this.buffer.append(b, off, len);
        }
    }
    
    public void write(final byte[] b) throws IOException {
        if (b == null) {
            return;
        }
        write(b, 0, b.length);
    }
    
    public void write(int b) throws IOException {
        if (this.buffer.isFull()) {
            flushBuffer();
        }
        this.buffer.append(b);
    }
    
    public void writeLine(final String s) throws IOException {
        if (s == null) {
            return;
        }
        if (s.length() > 0) {
            write(s.getBytes(this.charset));
        }
        write(CRLF);
    }
    
    public void writeLine(final CharArrayBuffer s) throws IOException {
        if (s == null) {
            return;
        }
        if (this.ascii) {
            int off = 0;
            int remaining = s.length();
            while (remaining > 0) {
                int chunk = this.buffer.capacity() - this.buffer.length();
                chunk = Math.min(chunk, remaining);
                if (chunk > 0) {
                    this.buffer.append(s, off, chunk);
                }
                if (this.buffer.isFull()) {
                    flushBuffer();
                }
                off += chunk;
                remaining -= chunk;
            }
        } else {
            // This is VERY memory inefficient, BUT since non-ASCII charsets are 
            // NOT meant to be used anyway, there's no point optimizing it
            byte[] tmp = s.toString().getBytes(this.charset);
            write(tmp);
        }
        write(CRLF);
    }
    
    public void reset(final HttpParams params) {
        this.charset = HttpProtocolParams.getHttpElementCharset(params); 
        this.ascii = this.charset.equalsIgnoreCase(HTTP.US_ASCII)
                     || this.charset.equalsIgnoreCase(HTTP.ASCII);
    }
    
    public long getBytesTransferred() {
        return this.bytesTransferred;
    }
    
    public void resetCounts() {
        this.bytesTransferred = 0;
    }
    
}
