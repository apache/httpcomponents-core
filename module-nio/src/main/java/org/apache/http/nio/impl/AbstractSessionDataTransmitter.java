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
import org.apache.http.io.HttpDataTransmitter;
import org.apache.http.params.HttpParams;
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
public abstract class AbstractSessionDataTransmitter implements HttpDataTransmitter {

    private static final byte[] CRLF = new byte[] {HTTP.CR, HTTP.LF};

    private SessionOutputBuffer buffer = null;

    public AbstractSessionDataTransmitter(final SessionOutputBuffer buffer) {
        super();
        if (buffer == null) {
            throw new IllegalArgumentException("Session output buffer may not be null");
        }
        this.buffer = buffer;
    }
    
    protected SessionOutputBuffer getBuffer() {
        return this.buffer;
    }

    public void reset(final HttpParams params) {
        this.buffer.reset(params);
    }

    protected abstract void flushBuffer() throws IOException;
    
    public  void flush() throws IOException {
        while (this.buffer.hasData()) {
            flushBuffer();
        }
    }
    
    public void write(final byte[] b, int off, int len) throws IOException {
        this.buffer.write(b, off, len);
    }

    public void write(final byte[] b) throws IOException {
        this.buffer.write(b);
    }

    public void write(int b) throws IOException {
        this.buffer.write(b);
    }

    public void writeLine(final CharArrayBuffer buffer) throws IOException {
        this.buffer.writeLine(buffer);
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