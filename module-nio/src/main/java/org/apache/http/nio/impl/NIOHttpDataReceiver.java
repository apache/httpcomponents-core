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

import org.apache.http.io.CharArrayBuffer;
import org.apache.http.io.HttpDataReceiver;
import org.apache.http.params.HttpParams;

/**
 * Abstract data receiver implementation based on <code>java.nio</code>.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 *
 * <!-- empty lines above to avoid 'svn diff' context problems -->
 * @version $Revision$
 * 
 * @since 4.0
 */
public abstract class NIOHttpDataReceiver implements HttpDataReceiver {

    private SessionInputBuffer buffer = null;
    
    protected NIOHttpDataReceiver(final SessionInputBuffer buffer) {
        super();
        if (buffer == null) {
            throw new IllegalArgumentException("Session input buffer may not be null");
        }
        this.buffer = buffer;
    }
    
    protected SessionInputBuffer getBuffer() {
        return this.buffer;
    }

    public void reset(final HttpParams params) {
        this.buffer.reset(params);
    }

    protected abstract int waitForData() throws IOException;
    
    public int read(final byte[] b, int off, int len) throws IOException {
        if (b == null) {
            return 0;
        }
        int noRead = 0;
        if (!this.buffer.hasData()) {
            noRead = waitForData();
            if (noRead == -1) {
                return -1; 
            }
        }
        return this.buffer.read(b, off, len);
    }
    
    public int read(final byte[] b) throws IOException {
        if (b == null) {
            return 0;
        }
        return read(b, 0, b.length);
    }
    
    public int read() throws IOException {
        int noRead = 0;
        if (!this.buffer.hasData()) {
            noRead = waitForData();
            if (noRead == -1) {
                return -1; 
            }
        }
        return this.buffer.read() & 0xff;
    }
    
    public int readLine(final CharArrayBuffer charbuffer) throws IOException {
        if (charbuffer == null) {
            throw new IllegalArgumentException("Char array buffer may not be null");
        }
        int len = charbuffer.length();
        int noRead = 0;
        boolean endOfStream = false;
        while (!this.buffer.readLine(charbuffer, endOfStream)) {
            if (endOfStream) {
                break;
            }
            noRead = waitForData();
            if (noRead == -1) {
                endOfStream = true; 
            }
        }
        int total = charbuffer.length() - len;
        if (total == 0 && endOfStream) {
            return -1;
        } else 
            return total;
    }
    
    public String readLine() throws IOException {
        CharArrayBuffer charbuffer = new CharArrayBuffer(64);
        int l = readLine(charbuffer);
        if (l != -1) {
            return charbuffer.toString();
        } else {
            return null;
        }
    }
        
}
