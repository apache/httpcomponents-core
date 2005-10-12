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
import java.io.InputStream;

import org.apache.http.io.HttpDataReceiver;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.util.ByteArrayBuffer;
import org.apache.http.util.EncodingUtil;

/**
 * <p>Classic IO Compatibility wrapper</p>
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 */
public abstract class AbstractHttpDataReceiver implements HttpDataReceiver {

    private static final int CR = 13;
    private static final int LF = 10;
    
    private InputStream instream;
    private byte[] buffer;
    private int bufferpos;
    private int bufferlen;
    
    private ByteArrayBuffer linebuffer = null;
    
    private String charset = "US-ASCII";
    
    protected void init(final InputStream instream, int buffersize) {
        if (instream == null) {
            throw new IllegalArgumentException("Input stream may not be null");
        }
        if (buffersize <= 0) {
            throw new IllegalArgumentException("Buffer size may not be negative or zero");
        }
        this.instream = instream;
        this.buffer = new byte[buffersize];
        this.bufferpos = 0;
        this.bufferlen = 0;
        this.linebuffer = new ByteArrayBuffer(buffersize);
    }
    
    protected int fillBuffer() throws IOException {
    	// compact the buffer if necessary
    	if (this.bufferpos > 0) {
    		int len = this.bufferlen - this.bufferpos;
            if (len > 0) {
                System.arraycopy(this.buffer, this.bufferpos, this.buffer, 0, len);
            }
        	this.bufferpos = 0;
        	this.bufferlen = len;
    	}
    	int l;
    	int off = this.bufferlen;
    	int len = this.buffer.length - off;
    	l = this.instream.read(this.buffer, off, len);
    	if (l == -1) {
    		return -1;
    	} else {
        	this.bufferlen = off + l;
        	return l;
    	}
    }

    protected boolean hasBufferedData() {
        return this.bufferpos < this.bufferlen;
    }
    
    public boolean isDataAvailable(int timeout) throws IOException {
        return hasBufferedData();
    }
    
    public int read() throws IOException {
        int noRead = 0;
    	while (!hasBufferedData()) {
            noRead = fillBuffer();
    		if (noRead == -1) {
    			return -1;
    		}
    	}
        int b = this.buffer[this.bufferpos++];
        if (b < 0) {
            b = 256 + b;
        }
        return b;
    }
    
    public int read(final byte[] b, int off, int len) throws IOException {
        if (b == null) {
            return 0;
        }
        int noRead = 0;
        while (!hasBufferedData()) {
            noRead = fillBuffer();
            if (noRead == -1) {
                return -1;
            }
        }
    	int chunk = this.bufferlen - this.bufferpos;
    	if (chunk > len) {
    		chunk = len;
    	}
    	System.arraycopy(this.buffer, this.bufferpos, b, off, chunk);
    	this.bufferpos += chunk;
    	return chunk;
    }
    
    public int read(final byte[] b) throws IOException {
        if (b == null) {
            return 0;
        }
        return read(b, 0, b.length);
    }
    
    private int locateLF() {
        for (int i = this.bufferpos; i < this.bufferlen; i++) {
            if (this.buffer[i] == LF) {
                return i;
            }
        }
        return -1;
    }
    
    public String readLine() throws IOException {
    	this.linebuffer.clear();
    	int noRead = 0;
        boolean retry = true;
        while (retry) {
            // attempt to find end of line (LF)
            int i = locateLF();
            if (i != -1) {
                // end of line found. 
                if (this.linebuffer.isEmpty()) {
                    // the entire line is preset in the read buffer
                    return lineFromReadBuffer(i);   
                }
                retry = false;
                int len = i + 1 - this.bufferpos;
                this.linebuffer.append(this.buffer, this.bufferpos, len);
                this.bufferpos = i + 1;
            } else {
                // end of line not found
                if (hasBufferedData()) {
                    int len = this.bufferlen - this.bufferpos;
                    this.linebuffer.append(this.buffer, this.bufferpos, len);
                    this.bufferpos = this.bufferlen;
                }
                noRead = fillBuffer();
                if (noRead == -1) {
                    retry = false;
                    if (hasBufferedData()) {
                        int len = this.bufferlen - this.bufferpos;
                        this.linebuffer.append(this.buffer, this.bufferpos, len);
                        this.bufferpos = this.bufferlen;
                    }
                }
            }
        }
        if (noRead == -1 && this.linebuffer.isEmpty()) {
            // indicate the end of stream
            return null;
        }
        return lineFromLineBuffer();
    }
    
    private String lineFromLineBuffer() {
        // discard LF if found
        int l = this.linebuffer.length(); 
        if (l > 0) {
            if (this.linebuffer.byteAt(l - 1) == LF) {
                l--;
                this.linebuffer.setLength(l);
            }
            // discard CR if found
            if (l > 0) {
                if (this.linebuffer.byteAt(l - 1) == CR) {
                    l--;
                    this.linebuffer.setLength(l);
                }
            }
        }
        return EncodingUtil.getString(
                this.linebuffer.getBuffer(), 0, this.linebuffer.length(), this.charset);
    }
    
    private String lineFromReadBuffer(int pos) {
        int off = this.bufferpos;
        int len;
        this.bufferpos = pos + 1;
        if (pos > 0 && this.buffer[pos - 1] == CR) {
            // skip CR if found
            pos--;
        }
        len = pos - off;
        return EncodingUtil.getString(this.buffer, off, len, this.charset);
    }
    
    public void reset(final HttpParams params) {
        this.charset = HttpProtocolParams.getHttpElementCharset(params);
    }
    
}
